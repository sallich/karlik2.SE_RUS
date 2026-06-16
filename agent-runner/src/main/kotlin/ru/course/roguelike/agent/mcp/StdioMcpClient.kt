package ru.course.roguelike.agent.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.course.roguelike.agent.config.AgentConfig
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.atomic.AtomicInteger

class StdioMcpClient(
    command: List<String>,
) : McpClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val requestId = AtomicInteger(0)
    private val process = ProcessBuilder(command)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    private val writer: BufferedWriter = process.outputStream.bufferedWriter()
    private val reader: BufferedReader = process.inputStream.bufferedReader()

    override suspend fun callTool(name: String, arguments: Map<String, JsonElement>): McpToolResult {
        val id = requestId.incrementAndGet()
        val request = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive("tools/call"))
            put(
                "params",
                buildJsonObject {
                    put("name", JsonPrimitive(name))
                    put("arguments", JsonObject(arguments))
                },
            )
        }
        synchronized(process) {
            writer.write(json.encodeToString(JsonObject.serializer(), request))
            writer.newLine()
            writer.flush()
            val line = reader.readLine() ?: return McpToolResult("MCP process closed", isError = true)
            return parseResponse(line)
        }
    }

    private fun parseResponse(line: String): McpToolResult {
        val root = json.parseToJsonElement(line).jsonObject
        root["error"]?.jsonObject?.let { err ->
            return McpToolResult(err["message"]?.jsonPrimitive?.content ?: "MCP error", isError = true)
        }
        val result = root["result"]?.jsonObject ?: return McpToolResult("Empty MCP result", isError = true)
        if (result["isError"]?.jsonPrimitive?.content == "true") {
            val text = result["content"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "Tool error"
            return McpToolResult(text, isError = true)
        }
        val text = result["content"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: line
        return McpToolResult(text, isError = false)
    }

    override fun close() {
        runCatching {
            writer.close()
            reader.close()
            process.destroy()
        }
    }

    companion object {
        fun fromConfig(config: AgentConfig): StdioMcpClient = StdioMcpClient(config.mcpCommand)
    }
}
