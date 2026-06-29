package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Pixmap

internal class PixelFrameBuffer(
    private val width: Int,
    private val height: Int,
) {
    private val pixels = IntArray(width * height)
    private val libGdxPixels = IntArray(width * height)

    fun clear(rgb: Int) {
        val packed = packRgb(rgb)
        pixels.fill(rgb)
        libGdxPixels.fill(packed)
    }

    fun set(x: Int, y: Int, rgb: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val index = y * width + x
        pixels[index] = rgb
        libGdxPixels[index] = packRgb(rgb)
    }

    fun fillRect(x: Int, y: Int, w: Int, h: Int, rgb: Int) {
        if (w <= 0 || h <= 0) return
        val x0 = x.coerceIn(0, width)
        val y0 = y.coerceIn(0, height)
        val x1 = (x + w).coerceIn(0, width)
        val y1 = (y + h).coerceIn(0, height)
        val packed = packRgb(rgb)
        val span = x1 - x0
        if (span <= 0) return
        if (x0 == 0 && x1 == width) {
            for (row in y0 until y1) {
                val offset = row * width
                pixels.fill(rgb, offset, offset + width)
                libGdxPixels.fill(packed, offset, offset + width)
            }
            return
        }
        for (row in y0 until y1) {
            val offset = row * width
            for (col in x0 until x1) {
                val index = offset + col
                pixels[index] = rgb
                libGdxPixels[index] = packed
            }
        }
    }

    fun fillRow(y: Int, rgb: Int) {
        if (y !in 0 until height) return
        val offset = y * width
        val packed = packRgb(rgb)
        pixels.fill(rgb, offset, offset + width)
        libGdxPixels.fill(packed, offset, offset + width)
    }

    fun flushTo(pixmap: Pixmap) {
        pixmap.pixels.asIntBuffer().apply {
            position(0)
            put(libGdxPixels)
            rewind()
        }
    }

    private fun packRgb(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return (r shl 24) or (g shl 16) or (b shl 8) or 0xFF
    }
}
