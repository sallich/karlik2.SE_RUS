package ru.course.roguelike.client

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Roguelike 2.5D (MVP)")
        setWindowedMode(1280, 720)
        useVsync(true)
    }
    Lwjgl3Application(RoguelikeGame(), config)
}
