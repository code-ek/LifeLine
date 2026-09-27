package com.lifeline.app.core.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** LifeLine logo: a heart crossed by a heartbeat line. Drawn as strokes so it can be tinted. */
val LifeLineIcon: ImageVector
    get() {
        cached?.let { return it }
        val stroke = SolidColor(Color.Black)
        return ImageVector.Builder(
            name = "LifeLineIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(HEART),
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round
            )
            addPath(
                pathData = addPathNodes(PULSE),
                stroke = stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build().also { cached = it }
    }

private const val HEART =
    "M12,20.5l-1.3,-1.2C5.9,15 2.8,12.1 2.8,8.6 2.8,5.8 5,3.6 7.8,3.6c1.6,0 3.1,0.7 4.2,1.9 " +
        "1.1,-1.2 2.6,-1.9 4.2,-1.9 2.8,0 5,2.2 5,5 0,3.5 -3.1,6.4 -7.9,10.7L12,20.5z"
private const val PULSE = "M3.6,11.6H8.2L9.6,8.7L11.8,15L13.6,10.1L14.8,11.6H20.4"

private var cached: ImageVector? = null
