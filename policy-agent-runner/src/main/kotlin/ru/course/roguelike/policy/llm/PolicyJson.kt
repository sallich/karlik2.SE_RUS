package ru.course.roguelike.policy.llm

import kotlinx.serialization.json.Json
import ru.course.roguelike.policy.dsl.AgentPolicy

object PolicyJson {
    val codec: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
    }

    fun encode(policy: AgentPolicy): String = codec.encodeToString(policy)

    fun extractJsonObject(text: String): String? = extractJson(text)

    fun parse(text: String): AgentPolicy? {
        val trimmed = extractJson(text) ?: return null
        val patch = runCatching { codec.decodeFromString<PolicyPatchRequest>(trimmed) }.getOrNull()
        if (patch != null && patch.patch.isNotEmpty()) return null
        return runCatching { codec.decodeFromString<AgentPolicy>(trimmed) }.getOrNull()
    }

    fun parsePatch(text: String): PolicyPatchRequest? {
        val trimmed = extractJson(text) ?: return null
        val patch = runCatching { codec.decodeFromString<PolicyPatchRequest>(trimmed) }.getOrNull()
        if (patch != null && patch.patch.isNotEmpty()) return patch
        val full = runCatching { codec.decodeFromString<AgentPolicy>(trimmed) }.getOrNull()
        return full?.let {
            PolicyPatchRequest(
                version = it.version,
                phase = it.phase,
                objective = it.objective,
                params = it.params,
                patch = it.rules,
                notes = it.notes,
            )
        }
    }

    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }
}
