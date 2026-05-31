package ru.course.roguelike.client

import ru.course.roguelike.shared.model.SessionPhase

fun parseSessionPhase(raw: String): SessionPhase =
    runCatching { SessionPhase.valueOf(raw) }.getOrDefault(SessionPhase.EXPLORATION)
