package ru.course.roguelike.shared.render

/**
 * Вертикальная проекция для raycast-сцены.
 *
 * Горизонт ([SceneRenderConfig.horizonY]) — только pitch.
 * Линия пола на дистанции [perpDistance] совпадает с инверсией [Raycaster.floorDistance]:
 * стены, спрайты на z=0 и текстура пола используют одну плоскость.
 */
object CameraProjection {
    /** Высота камеры над текущим полом (прыжок / лифт). */
    fun viewerHeight(localHeight: Float): Float = localHeight.coerceAtLeast(0f)

    /**
     * Экранная Y точки пола (мировой z=0) на перпендикулярной дистанции [perpDistance],
     * когда камера на высоте [viewerHeightAboveFloor] над полом.
     *
     * При [viewerHeightAboveFloor]=0 на дистанции d: pitchHorizon + screenHeight/(2d).
     */
    fun worldFloorScreenY(
        pitchHorizonY: Float,
        screenHeight: Int,
        perpDistance: Float,
        viewerHeightAboveFloor: Float = 0f,
    ): Float {
        val dist = perpDistance.coerceAtLeast(0.05f)
        return pitchHorizonY + screenHeight * (0.5f + viewerHeightAboveFloor.coerceAtLeast(0f)) / dist
    }

    /** Низ стены / колонны на линии пола (та же плоскость, что и floor cast). */
    fun projectWallSpan(
        pitchHorizonY: Float,
        lineHeight: Float,
        wallHeight: Float,
        screenHeight: Int,
        perpDistance: Float,
        viewerHeightAboveFloor: Float,
    ): Pair<Float, Float> {
        val wallBottomY = worldFloorScreenY(
            pitchHorizonY,
            screenHeight,
            perpDistance,
            viewerHeightAboveFloor,
        )
        val wallTopY = wallBottomY - lineHeight * wallHeight
        val maxY = screenHeight.toFloat()
        val drawStart = minOf(wallTopY, wallBottomY).coerceIn(0f, maxY)
        val drawEnd = maxOf(wallTopY, wallBottomY).coerceIn(0f, maxY)
        return drawStart to drawEnd
    }

    /** Спрайт с ногами/центром на [spriteWorldZ]; [viewerHeight] — высота камеры над полом яруса. */
    fun projectSpriteSpan(
        pitchHorizonY: Float,
        spriteHeight: Int,
        screenHeight: Int,
        perpDistance: Float,
        viewerHeight: Float,
        spriteWorldZ: Float = 0f,
    ): Pair<Int, Int> {
        val floorY = worldFloorScreenY(
            pitchHorizonY,
            screenHeight,
            perpDistance,
            viewerHeight - spriteWorldZ,
        ).coerceIn(0f, screenHeight.toFloat())
        val drawEndY = floorY.toInt().coerceIn(1, screenHeight)
        val drawStartY = (floorY - spriteHeight).toInt().coerceIn(0, drawEndY - 1)
        return drawStartY to drawEndY
    }
}
