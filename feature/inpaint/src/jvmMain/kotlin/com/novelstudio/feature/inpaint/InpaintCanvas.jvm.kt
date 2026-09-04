package com.novelstudio.feature.inpaint

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

internal actual fun renderMaskToPng(
    strokes: List<Stroke>,
    width: Int,
    height: Int,
): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.color = java.awt.Color(0, 0, 0, 0)
        g.fillRect(0, 0, width, height)
        // 遮罩区域填充为白色，橡皮擦区域保持透明
        for (stroke in strokes) {
            if (!stroke.erase) {
                g.color = java.awt.Color(255, 255, 255, 200)
                g.stroke = java.awt.BasicStroke(stroke.radius * 2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
            } else {
                g.composite = java.awt.AlphaComposite.Clear
                g.stroke = java.awt.BasicStroke(stroke.radius * 2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
            }
        }
    } finally {
        g.dispose()
    }
    return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}
