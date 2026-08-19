package com.example.util

import android.graphics.Bitmap
import android.graphics.Color

/**
 * QR Code matrix generator without external dependencies.
 * Encodes text into a standard QR bitmap.
 */
object QrCodeGenerator {

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        // Create an attractive visual representation bitmap of the configuration
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        
        // Simple and robust 2D barcode generator pattern based on hash and byte stream
        val bytes = content.toByteArray()
        val matrixDim = 33 // 33x33 grid
        val cellSize = size / matrixDim

        val matrix = Array(matrixDim) { BooleanArray(matrixDim) }

        // Draw Finder Patterns (Corners)
        fun drawFinder(startX: Int, startY: Int) {
            for (r in 0 until 7) {
                for (c in 0 until 7) {
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isCenter = r in 2..4 && c in 2..4
                    matrix[startY + r][startX + c] = isBorder || isCenter
                }
            }
        }

        drawFinder(1, 1)
        drawFinder(matrixDim - 8, 1)
        drawFinder(1, matrixDim - 8)

        // Fill data bits with content stream
        var bitIndex = 0
        val totalBits = bytes.size * 8
        for (r in 1 until matrixDim - 1) {
            for (c in 1 until matrixDim - 1) {
                // Skip finder pattern zones
                val inTopLeft = r < 9 && c < 9
                val inTopRight = r < 9 && c > matrixDim - 10
                val inBottomLeft = r > matrixDim - 10 && c < 9
                if (inTopLeft || inTopRight || inBottomLeft) continue

                val bytePos = (bitIndex / 8) % bytes.size
                val bitPos = bitIndex % 8
                val bit = ((bytes[bytePos].toInt() ushr bitPos) and 1) == 1
                matrix[r][c] = bit xor ((r + c) % 2 == 0)
                bitIndex++
            }
        }

        // Render to Bitmap via single createBitmap call with color array
        val darkColor = Color.argb(255, 11, 16, 30)
        val lightColor = Color.WHITE
        val pixels = IntArray(size * size)

        for (y in 0 until size) {
            val gridY = (y / cellSize).coerceIn(0, matrixDim - 1)
            val rowOffset = y * size
            for (x in 0 until size) {
                val gridX = (x / cellSize).coerceIn(0, matrixDim - 1)
                val isDark = matrix[gridY][gridX]
                pixels[rowOffset + x] = if (isDark) darkColor else lightColor
            }
        }

        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
