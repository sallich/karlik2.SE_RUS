package ru.course.roguelike.client.render

import com.badlogic.gdx.graphics.Pixmap
import ru.course.roguelike.shared.render.RgbImageSampler

internal class PixelFrameBuffer(
    private val width: Int,
    private val height: Int,
) {
    private val pixels = IntArray(width * height)

    fun clear(rgb: Int) {
        pixels.fill(rgb)
    }

    fun set(x: Int, y: Int, rgb: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        pixels[y * width + x] = rgb
    }

    fun fillRect(x: Int, y: Int, w: Int, h: Int, rgb: Int) {
        if (w <= 0 || h <= 0) return
        val x0 = x.coerceIn(0, width)
        val y0 = y.coerceIn(0, height)
        val x1 = (x + w).coerceIn(0, width)
        val y1 = (y + h).coerceIn(0, height)
        for (row in y0 until y1) {
            val offset = row * width
            for (col in x0 until x1) {
                pixels[offset + col] = rgb
            }
        }
    }

    fun fillRow(y: Int, rgb: Int) {
        if (y !in 0 until height) return
        val offset = y * width
        pixels.fill(rgb, offset, offset + width)
    }

    fun flushTo(pixmap: Pixmap) {
        val intBuffer = pixmap.pixels.asIntBuffer()
        intBuffer.position(0)
        for (rgb in pixels) {
            intBuffer.put(RgbImageSampler.toLibGdxPixel(rgb))
        }
        intBuffer.rewind()
    }
}
