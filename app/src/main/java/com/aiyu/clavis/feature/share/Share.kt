package com.aiyu.clavis.feature.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import com.aiyu.clavis.core.card.formatPan
import com.aiyu.clavis.core.model.CardRecord
import java.io.File
import java.util.UUID

enum class ShareStyle { CARD, MINIMAL }

data class ShareOptions(val includeCvv: Boolean = false, val includeHolder: Boolean = true, val style: ShareStyle = ShareStyle.CARD)

/** Theme colors resolved by the caller (renderer runs outside composition). */
data class ShareColors(val container: Int, val content: Int, val surface: Int, val onSurface: Int, val muted: Int)

/** Labels resolved from strings.xml by the caller. */
data class ShareLabels(val title: String, val networkMark: String, val validThru: String, val cvv: String, val holderLine: String?, val cvvLine: String?, val lines: List<String>)

/** cache/shared/ only, random file names, deleted after the share, on lock and on start (05-SHARE-AND-COPY.md). */
object ShareCache {
    fun dir(context: Context) = File(context.cacheDir, "shared")
    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}

/**
 * Draws the shared image: the card front (same design as the in-app surface) and, only when chosen, a back panel
 * with the CVV, stacked into one picture. 1080 px wide.
 */
object ShareRenderer {
    private const val W = 1080
    private const val PAD = 48f
    private const val CARD_W = W - 2 * PAD
    private const val CARD_H = CARD_W / 1.586f

    fun render(record: CardRecord, cvv: String?, options: ShareOptions, colors: ShareColors, labels: ShareLabels): Bitmap =
        if (options.style == ShareStyle.MINIMAL) minimal(colors, labels) else card(record, cvv.takeIf { options.includeCvv }, options, colors, labels)

    private fun card(record: CardRecord, cvv: String?, options: ShareOptions, colors: ShareColors, labels: ShareLabels): Bitmap {
        val height = (PAD * 2 + CARD_H + if (cvv != null) CARD_H + PAD else 0f).toInt()
        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(colors.surface)
        val front = RectF(PAD, PAD, PAD + CARD_W, PAD + CARD_H)
        cardBackground(c, front, colors)
        val text = paint(colors.content)
        // Title and network mark
        text.textSize = 44f; text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText(labels.title.take(28), front.left + 56, front.top + 92, text)
        text.textSize = 58f; text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC); text.textAlign = Paint.Align.RIGHT
        c.drawText(labels.networkMark, front.right - 56, front.top + 96, text)
        text.textAlign = Paint.Align.LEFT
        // Chip
        val chip = RectF(front.left + 56, front.top + 170, front.left + 56 + 110, front.top + 170 + 84)
        c.drawRoundRect(chip, 18f, 18f, paint(ColorUtils.blendARGB(colors.container, colors.content, 0.55f)))
        // Number
        text.textSize = 66f; text.typeface = Typeface.MONOSPACE; text.letterSpacing = 0.06f
        c.drawText(formatPan(record.pan, record.network), front.left + 56, front.top + 365, text)
        text.letterSpacing = 0f
        // Holder and expiry
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); text.textSize = 38f
        if (options.includeHolder) c.drawText(record.holderName.uppercase().take(26), front.left + 56, front.bottom - 60, text)
        text.textAlign = Paint.Align.RIGHT
        text.textSize = 24f; text.alpha = 200
        c.drawText(labels.validThru.uppercase(), front.right - 56, front.bottom - 112, text)
        text.alpha = 255; text.textSize = 44f
        c.drawText(record.expiry.short, front.right - 56, front.bottom - 60, text)
        text.textAlign = Paint.Align.LEFT

        if (cvv != null) {
            val back = RectF(PAD, front.bottom + PAD, PAD + CARD_W, front.bottom + PAD + CARD_H)
            cardBackground(c, back, colors)
            c.drawRect(back.left, back.top + 70, back.right, back.top + 170, paint(ColorUtils.blendARGB(colors.container, colors.onSurface, 0.7f)))
            val strip = RectF(back.left + 56, back.top + 240, back.right - 260, back.top + 330)
            c.drawRoundRect(strip, 10f, 10f, paint(ColorUtils.blendARGB(colors.container, colors.content, 0.8f)))
            val box = RectF(back.right - 240, back.top + 230, back.right - 56, back.top + 340)
            c.drawRoundRect(box, 16f, 16f, paint(colors.content))
            val cv = paint(colors.container); cv.textSize = 60f; cv.typeface = Typeface.MONOSPACE; cv.textAlign = Paint.Align.CENTER
            c.drawText(cvv, box.centerX(), box.centerY() + 21, cv)
            text.textSize = 32f; text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText(labels.cvv, back.right - 240, back.top + 400, text)
        }
        return bmp
    }

    private fun minimal(colors: ShareColors, labels: ShareLabels): Bitmap {
        val lineH = 72f
        val height = (PAD * 2 + 40 + lineH * labels.lines.size).toInt()
        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(colors.surface)
        c.drawRoundRect(RectF(PAD / 2, PAD / 2, W - PAD / 2, height - PAD / 2), 48f, 48f, paint(ColorUtils.blendARGB(colors.surface, colors.container, 0.12f)))
        val p = paint(colors.onSurface)
        p.textSize = 44f
        labels.lines.forEachIndexed { i, line ->
            p.typeface = if (i == 0) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.MONOSPACE
            c.drawText(line, PAD + 24, PAD + 70 + i * lineH, p)
        }
        return bmp
    }

    private fun cardBackground(c: Canvas, r: RectF, colors: ShareColors) {
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(r.left, r.top, r.right, r.bottom, colors.container, ColorUtils.blendARGB(colors.container, colors.content, 0.14f), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(r, 72f, 72f, g)
        c.save()
        c.clipRect(r)
        c.drawCircle(r.right + 10, r.top - 40, r.width() * 0.55f, paint(ColorUtils.setAlphaComponent(colors.content, 18)))
        c.restore()
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
}

object Sharer {
    /** Writes [image] to the share cache and opens the system share sheet with image + text, or text only. */
    fun share(context: Context, text: String, image: Bitmap?, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, text) }
        if (image != null) {
            val dir = ShareCache.dir(context).apply { mkdirs() }
            val file = File(dir, "clavis-card-${UUID.randomUUID()}.png")
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            intent.type = "image/png"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.clipData = ClipData.newRawUri(null, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            intent.type = "text/plain"
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
