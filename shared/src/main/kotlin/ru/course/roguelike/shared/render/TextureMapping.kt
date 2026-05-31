package ru.course.roguelike.shared.render

import ru.course.roguelike.shared.model.TileType
import kotlin.math.floor

/** Пути ассетов (classpath game-client/resources/assets). */
object AssetPaths {
    const val WALLS = "textures/walls.png"
    const val FLOOR = "textures/floor.png"
    const val SKY = "textures/sky.bmp"
    const val LAVA = "textures/lava.png"
    const val DOOR = "textures/door.png"
    const val BLAST = "textures/blast.png"
    const val MELEE_MOB = "models/melee.png"
    const val RANGED_MOB = "models/ranged.png"
    const val PLAYER = "models/person.png"
    const val SOUND_HIT = "sounds/hit.mp3"
    const val SOUND_AMBIENT = "sounds/ambient.mp3"
}

/*
 * Клиент передаёт пиксели из Pixmap/Texture.
 */
class RgbImageSampler(
    private val width: Int,
    private val height: Int,
    private val pixels: IntArray,
    private val alphas: IntArray? = null,
) {
    init {
        require(pixels.size == width * height)
        if (alphas != null) require(alphas.size == width * height)
    }

    data class PixelSample(val rgb: Int, val alpha: Int)

    fun sampleU(u: Float, v: Float): Int = samplePixel(u, v).rgb

    fun samplePixel(u: Float, v: Float): PixelSample {
        if (width <= 0 || height <= 0) return PixelSample(0x808080, 255)
        val xf = (u.coerceIn(0f, 0.9999f) * width)
        val yf = (v.coerceIn(0f, 0.9999f) * height)
        val x = xf.toInt().coerceIn(0, width - 1)
        val y = yf.toInt().coerceIn(0, height - 1)
        val index = y * width + x
        val alpha = alphas?.get(index) ?: 255
        return PixelSample(pixels[index], alpha)
    }

    fun sampleColumnU(u: Float, screenRow: Int, top: Int, bottom: Int): PixelSample {
        if (bottom <= top) return PixelSample(0x808080, 255)
        val v = (screenRow - top).toFloat() / (bottom - top)
        return samplePixel(u, v)
    }

    /** Непрозрачность пикселя */
    fun isVisible(sample: PixelSample, chromaKey: Boolean = false): Boolean {
        if (sample.alpha < ALPHA_CUTOFF) return false
        if (!chromaKey) return true
        if (sample.alpha < 255) return true
        val r = (sample.rgb shr 16) and 0xFF
        val g = (sample.rgb shr 8) and 0xFF
        val b = sample.rgb and 0xFF
        return maxOf(r, g, b) > CHROMA_KEY_MAX
    }

    companion object {
        private const val ALPHA_CUTOFF = 128
        private const val CHROMA_KEY_MAX = 8

        /** LibGDX [com.badlogic.gdx.graphics.Pixmap] использует формат RRGGBBAA (не AARRGGBB). */
        fun fromLibGdxPixel(packed: Int): PixelSample {
            val r = (packed ushr 24) and 0xFF
            val g = (packed ushr 16) and 0xFF
            val b = (packed ushr 8) and 0xFF
            val a = packed and 0xFF
            return PixelSample((r shl 16) or (g shl 8) or b, a)
        }

        /** Запись пикселя в Pixmap: RRGGBBAA, alpha=255 по умолчанию (полностью непрозрачный). */
        fun toLibGdxPixel(rgb: Int, alpha: Int = 255): Int {
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            return (r shl 24) or (g shl 16) or (b shl 8) or (alpha and 0xFF)
        }

        fun solid(width: Int, height: Int, rgb: Int): RgbImageSampler =
            RgbImageSampler(width, height, IntArray(width * height) { rgb })
    }
}

object TextureMapping {
    /** Мировая координата точки попадания луча в плоскость стены (до frac). */
    fun wallHitCoord(
        side: Int,
        perpDistance: Float,
        rayDirX: Float,
        rayDirY: Float,
        posX: Float,
        posY: Float,
    ): Float {
        val hitX = posX + perpDistance * rayDirX
        val hitY = posY + perpDistance * rayDirY
        return if (side == 0) hitY else hitX
    }

    /** Дробная часть координаты внутри мирового тайла (0..1). */
    fun wallFracU(hitCoord: Float): Float = frac(hitCoord)

    /**
     * Непрерывная U с учётом перехода через границу тайла между соседними столбцами.
     * [wallUOffset] добавляется к frac-U, чтобы не было скачка 0.9 → 0.1.
     */
    fun continuousWallU(hitCoord: Float, wallUOffset: Int): Float = wallFracU(hitCoord) + wallUOffset

    fun wallTextureUClamped(continuousU: Float): Float = frac(continuousU * SceneRenderConfig.WALL_HORIZONTAL_REPEATS)

    /** UV пола по мировым координатам (тайлинг). */
    fun floorUv(floorX: Float, floorY: Float): Pair<Float, Float> =
        frac(floorX) to frac(floorY)

    fun frac(value: Float): Float = value - floor(value)

    fun wallAtlasRowBase(tile: TileType?): Float = when (tile) {
        TileType.COLUMN -> 1f / SceneRenderConfig.WALL_ATLAS_ROWS
        else -> 0f
    }

    fun wallAtlasRowSpan(tile: TileType?): Float = 1f / SceneRenderConfig.WALL_ATLAS_ROWS

    fun wallTextureV(
        screenRow: Int,
        wallStart: Float,
        perpDistance: Float,
        viewHeight: Int,
        tile: TileType?,
    ): Float {
        val rowBase = wallAtlasRowBase(tile)
        val rowSpan = wallAtlasRowSpan(tile)
        val lineHeight = viewHeight / perpDistance.coerceAtLeast(0.05f)
        val rel = (screenRow + 0.5f) - wallStart
        val tiledV = frac(rel / lineHeight * SceneRenderConfig.WALL_VERTICAL_REPEATS)
        return (rowBase + tiledV * rowSpan).coerceIn(0f, 0.9999f)
    }

    fun skyUv(col: Int, row: Int, viewWidth: Int, horizonInt: Int, yaw: Float): Pair<Float, Float> {
        val u = frac(col.toFloat() / viewWidth.coerceAtLeast(1) + yaw * SceneRenderConfig.SKY_YAW_SCALE)
        val v = row.toFloat() / horizonInt.coerceAtLeast(1)
        return u to v.coerceIn(0f, 0.9999f)
    }

    fun wallRowFor(tile: TileType?): Float = wallAtlasRowBase(tile)

    fun shadeRgb(
        rgb: Int,
        distance: Float,
        sideDarken: Float = 1f,
        shadeFactor: Float = SceneRenderConfig.DISTANCE_SHADE_FACTOR,
        shadeMin: Float = SceneRenderConfig.DISTANCE_SHADE_MIN,
    ): Int {
        val factor = (1f / (1f + distance * shadeFactor)).coerceIn(shadeMin, 1f) * sideDarken
        val r = (((rgb shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((rgb shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((rgb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    fun spriteColumnU(screenX: Int, left: Int, right: Int): Float {
        if (right <= left) return 0.5f
        return (screenX - left).toFloat() / (right - left)
    }
}
