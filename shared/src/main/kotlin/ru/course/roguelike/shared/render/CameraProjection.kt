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

    /** Экранная Y точки на мировой высоте [worldZ] при камере на [cameraHeightAboveFloor] над полом. */
    fun worldZScreenY(
        pitchHorizonY: Float,
        screenHeight: Int,
        perpDistance: Float,
        cameraHeightAboveFloor: Float,
        worldZ: Float,
    ): Float {
        val dist = perpDistance.coerceAtLeast(0.05f)
        return pitchHorizonY + screenHeight * (0.5f + cameraHeightAboveFloor - worldZ) / dist
    }

    /**
     * Вертикальная грань стены/колонны: низ на z=0, верх на z=[wallHeight].
     * При прыжке/лифте низ может уйти за нижний край — оставляем видимую полосу боковины.
     */
    fun projectWallSpan(
        pitchHorizonY: Float,
        lineHeight: Float,
        wallHeight: Float,
        screenHeight: Int,
        perpDistance: Float,
        viewerHeightAboveFloor: Float,
    ): Pair<Float, Float> {
        val wallBottomY = worldZScreenY(
            pitchHorizonY,
            screenHeight,
            perpDistance,
            viewerHeightAboveFloor,
            worldZ = 0f,
        )
        val wallTopY = worldZScreenY(
            pitchHorizonY,
            screenHeight,
            perpDistance,
            viewerHeightAboveFloor,
            worldZ = wallHeight,
        )
        val rawTop = minOf(wallTopY, wallBottomY)
        val rawBottom = maxOf(wallTopY, wallBottomY)
        val maxY = screenHeight.toFloat()

        if (rawBottom <= 0f || rawTop > maxY) {
            return maxY to maxY
        }

        var drawStart = rawTop.coerceAtLeast(0f)
        var drawEnd = rawBottom.coerceAtMost(maxY)

        if (drawEnd - drawStart < 0.5f) {
            val band = (lineHeight * wallHeight).coerceIn(2f, maxY * 0.4f)
            drawStart = if (rawTop < maxY - 0.5f) {
                rawTop.coerceAtLeast(0f)
            } else {
                (maxY - band).coerceAtLeast(0f)
            }
            drawEnd = maxY
        }
        return drawStart to drawEnd
    }

    /**
     * Спрайт в мире: ноги на [spriteWorldZ], камера на [cameraHeightAboveFloor] над полом.
     * Смещение камеры синхронизирует наземные спрайты с плоскостью пола при прыжке.
     */
    fun projectSpriteSpan(
        pitchHorizonY: Float,
        spriteHeight: Int,
        screenHeight: Int,
        perpDistance: Float,
        spriteWorldZ: Float = 0f,
        cameraHeightAboveFloor: Float = 0f,
    ): Pair<Int, Int> {
        val dist = perpDistance.coerceAtLeast(0.05f)
        val floorY = (
            pitchHorizonY +
                screenHeight * (0.5f + cameraHeightAboveFloor - spriteWorldZ) / dist
            ).coerceIn(0f, screenHeight.toFloat())
        val drawEndY = floorY.toInt().coerceIn(1, screenHeight)
        val drawStartY = (floorY - spriteHeight).toInt().coerceIn(0, drawEndY - 1)
        return drawStartY to drawEndY
    }
}
