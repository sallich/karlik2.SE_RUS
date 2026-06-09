package ru.course.roguelike.client

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.render.AssetPaths

class AssetResourcesTest {
    @Test
    fun `shared assets are packaged on the client classpath`() {
        listOf(
            AssetPaths.WALLS,
            AssetPaths.FLOOR,
            AssetPaths.SKY,
            AssetPaths.LAVA,
            AssetPaths.DOOR,
            AssetPaths.KEY,
            AssetPaths.BLAST,
            AssetPaths.MELEE_MOB,
            AssetPaths.RANGED_MOB,
            AssetPaths.PLAYER,
            AssetPaths.SOUND_HIT,
            AssetPaths.SOUND_AMBIENT,
        ).forEach { path ->
            assertNotNull(javaClass.classLoader.getResource(path), "missing resource: $path")
        }
    }
}
