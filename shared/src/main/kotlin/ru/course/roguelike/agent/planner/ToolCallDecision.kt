package ru.course.roguelike.agent.planner

import kotlinx.serialization.json.JsonElement

data class ToolCallDecision(
    val id: String? = null,
    val tool: String,
    val arguments: Map<String, JsonElement>,
)
