package ru.course.roguelike.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.course.roguelike.shared.model.PlayerPose
import ru.course.roguelike.shared.model.TileType
import ru.course.roguelike.shared.render.MiniMapProjection
import ru.course.roguelike.shared.render.RgbImageSampler
import ru.course.roguelike.shared.render.SceneRenderConfig
import ru.course.roguelike.shared.render.TextureMapping

class MiniMapProjectionTest {
    @Test
    fun `entity ahead of player with yaw zero is forward on minimap`() {
        val pose = PlayerPose(5f, 5f, yaw = 0f)
        val point = MiniMapProjection.worldToMinimap(pose, 7f, 5f)
        assertTrue(point.forward > 0f)
        assertEquals(0f, point.right, 0.01f)
    }

    @Test
    fun `entity to the right with yaw zero appears on minimap right`() {
        val pose = PlayerPose(5f, 5f, yaw = 0f)
        val point = MiniMapProjection.worldToMinimap(pose, 5f, 7f)
        assertTrue(point.right > 0f)
        assertEquals(0f, point.forward, 0.01f)
    }

    @Test
    fun `entity ahead when facing north maps to forward not mirrored backward`() {
        val pose = PlayerPose(5f, 5f, yaw = Math.PI.toFloat() / 2f)
        val point = MiniMapProjection.worldToMinimap(pose, 5f, 8f)
        assertTrue(point.forward > 0f, "expected mob ahead, not behind")
        assertEquals(0f, point.right, 0.05f)
    }

    @Test
    fun `visibility radius excludes far entities`() {
        val pose = PlayerPose(0f, 0f, yaw = 0f)
        val near = MiniMapProjection.worldToMinimap(pose, 2f, 0f)
        val far = MiniMapProjection.worldToMinimap(pose, 10f, 0f)
        assertTrue(MiniMapProjection.isVisible(near, 3f))
        assertFalse(MiniMapProjection.isVisible(far, 3f))
    }
}

class TextureMappingTest {
    @Test
    fun `wall texture u wraps along wall coordinate`() {
        val hit = TextureMapping.wallHitCoord(
            side = 0,
            perpDistance = 2f,
            rayDirX = 1f,
            rayDirY = 0f,
            posX = 1.5f,
            posY = 1.2f,
        )
        val u = TextureMapping.wallFracU(hit)
        assertTrue(u in 0f..1f)
    }

    @Test
    fun `floor uv tiles with world coordinates`() {
        val (u, v) = TextureMapping.floorUv(2.5f, 3.5f)
        assertEquals(0.5f, u, 0.01f)
        assertEquals(0.5f, v, 0.01f)
    }

    @Test
    fun `libgdx pixel unpack uses RRGGBBAA format`() {
        val packed = (128 shl 24) or (100 shl 16) or (50 shl 8) or 255
        val sample = RgbImageSampler.fromLibGdxPixel(packed)
        assertEquals(0x806432, sample.rgb)
        assertEquals(255, sample.alpha)
    }

    @Test
    fun `libgdx pixel pack writes opaque alpha in low byte`() {
        val packed = RgbImageSampler.toLibGdxPixel(0x806432)
        assertEquals(255, packed and 0xFF)
        assertEquals(0x806432, RgbImageSampler.fromLibGdxPixel(packed).rgb)
    }

    @Test
    fun `aarrggbb encoding would corrupt colors and transparency`() {
        val packed = (128 shl 24) or (100 shl 16) or (50 shl 8) or 255
        val wrongAlpha = (packed ushr 24) and 0xFF
        val wrongBlue = packed and 0xFF
        assertEquals(128, wrongAlpha)
        assertEquals(255, wrongBlue)
        assertNotEquals(wrongAlpha, RgbImageSampler.fromLibGdxPixel(packed).alpha)
    }

    @Test
    fun `typical sprite pixels stay visible with correct alpha`() {
        val packed = (200 shl 24) or (80 shl 16) or (20 shl 8) or 255
        val sample = RgbImageSampler.fromLibGdxPixel(packed)
        val sampler = RgbImageSampler(1, 1, intArrayOf(sample.rgb), intArrayOf(sample.alpha))
        assertTrue(sample.alpha >= 128)
        assertTrue(sampler.isVisible(sample, chromaKey = true))
    }

    @Test
    fun `rgb sampler returns configured pixel`() {
        val sampler = RgbImageSampler.solid(4, 4, 0xFF0000)
        assertEquals(0xFF0000, sampler.sampleU(0.1f, 0.1f))
        assertEquals(0xFF0000, sampler.sampleColumnU(0.5f, 1, 0, 2).rgb)
    }

    @Test
    fun `chroma key hides black sprite background`() {
        val sampler = RgbImageSampler(
            width = 1,
            height = 1,
            pixels = intArrayOf(0x000000),
            alphas = intArrayOf(255),
        )
        val sample = sampler.samplePixel(0f, 0f)
        assertTrue(sampler.isVisible(sample, chromaKey = false))
        assertFalse(sampler.isVisible(sample, chromaKey = true))
    }

    @Test
    fun `chroma key keeps dark colored sprite pixels`() {
        val sampler = RgbImageSampler(
            width = 1,
            height = 1,
            pixels = intArrayOf(0x001020),
            alphas = intArrayOf(255),
        )
        val sample = sampler.samplePixel(0f, 0f)
        assertTrue(sampler.isVisible(sample, chromaKey = true))
    }

    @Test
    fun `wrong channel order would tint everything red`() {
        val packed = (90 shl 24) or (60 shl 16) or (40 shl 8) or 255
        val sample = RgbImageSampler.fromLibGdxPixel(packed)
        assertEquals(90, (sample.rgb shr 16) and 0xFF)
        assertEquals(255, sample.alpha)
    }

    @Test
    fun `distance shading darkens rgb`() {
        val shaded = TextureMapping.shadeRgb(0xFFFFFF, distance = 8f)
        assertTrue(shaded < 0xFFFFFF)
    }

    @Test
    fun `pitch shifts horizon up and down`() {
        val center = SceneRenderConfig.horizonY(360, pitch = 0f)
        val lookUp = SceneRenderConfig.horizonY(360, pitch = 0.5f)
        val lookDown = SceneRenderConfig.horizonY(360, pitch = -0.5f)
        assertEquals(180f, center, 0.01f)
        assertTrue(lookUp > center, "look up should lower horizon line on screen (larger Y)")
        assertTrue(lookDown < center, "look down should raise horizon line on screen (smaller Y)")
    }

    @Test
    fun `wall texture u unwraps across tile boundary`() {
        val u0 = TextureMapping.continuousWallU(0.95f, wallUOffset = 0)
        val u1 = TextureMapping.continuousWallU(0.05f, wallUOffset = 1)
        assertTrue(u1 > u0, "continuous U must not jump backward at tile seam")
        assertEquals(0.95f, TextureMapping.wallTextureUClamped(u0), 0.02f)
        assertEquals(0.05f, TextureMapping.wallTextureUClamped(u1), 0.02f)
    }

    @Test
    fun `wall v repeats along projected wall height`() {
        val wallStart = 50f
        val distance = 5f
        val viewHeight = 200
        val lineHeight = viewHeight / distance
        val v0 = TextureMapping.wallTextureV(50, wallStart, distance, viewHeight, TileType.WALL)
        val v1 = TextureMapping.wallTextureV((50 + lineHeight).toInt(), wallStart, distance, viewHeight, TileType.WALL)
        assertEquals(v0, v1, 0.02f)
    }

    @Test
    fun `column wall uses lower atlas row`() {
        assertTrue(
            TextureMapping.wallAtlasRowBase(TileType.COLUMN) >
                TextureMapping.wallAtlasRowBase(TileType.WALL),
        )
    }
}
