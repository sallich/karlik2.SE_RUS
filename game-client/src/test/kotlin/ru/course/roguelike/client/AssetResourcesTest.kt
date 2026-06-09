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
            AssetPaths.BOSS,
            AssetPaths.PLAYER,
            AssetPaths.PISTOL,
            AssetPaths.SHOTGUN,
            AssetPaths.PISTOL_AMMO,
            AssetPaths.SHOTGUN_AMMO,
            AssetPaths.FIRST_AID,
            AssetPaths.PISTOL_FIRST,
            AssetPaths.SHOTGUN_FIRST,
            AssetPaths.FIRE_WALL,
            AssetPaths.SOUND_HIT,
            AssetPaths.SOUND_AMBIENT,
        ).forEach { path ->
            assertNotNull(javaClass.classLoader.getResource(path), "missing resource: $path")
        }
    }
}
