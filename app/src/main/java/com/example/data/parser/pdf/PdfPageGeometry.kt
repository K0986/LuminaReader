package com.example.data.parser.pdf

/**
 * Where a PDF page ends up inside its container once it has been scaled to fit, plus the scale
 * factor that maps PDF points to pixels.
 */
data class PdfFitBox(
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
    val scale: Float
)

/**
 * Pure mapping between PDF page coordinates and the on-screen box the page is drawn into.
 *
 * The reader draws a page with `ContentScale.Fit` inside a container, so the page is centred and
 * uniformly scaled. Touch handling and overlay drawing both happen *inside* the zoom/pan layer,
 * which means this letterboxing transform is the only conversion we need.
 */
object PdfPageGeometry {

    fun fit(
        page: PdfPageSize,
        containerWidthPx: Float,
        containerHeightPx: Float
    ): PdfFitBox? {
        if (page.widthPt <= 0f || page.heightPt <= 0f) return null
        if (containerWidthPx <= 0f || containerHeightPx <= 0f) return null

        val scale = minOf(containerWidthPx / page.widthPt, containerHeightPx / page.heightPt)
        val width = page.widthPt * scale
        val height = page.heightPt * scale
        return PdfFitBox(
            offsetX = (containerWidthPx - width) / 2f,
            offsetY = (containerHeightPx - height) / 2f,
            width = width,
            height = height,
            scale = scale
        )
    }

    /** Converts a touch position inside the container to a point on the PDF page. */
    fun viewToPdf(x: Float, y: Float, fit: PdfFitBox): PdfPoint =
        PdfPoint(
            x = (x - fit.offsetX) / fit.scale,
            y = (y - fit.offsetY) / fit.scale
        )

    /** Converts a rectangle on the PDF page to container coordinates for drawing overlays. */
    fun pdfToView(rect: PdfRect, fit: PdfFitBox): PdfRect =
        PdfRect(
            left = rect.left * fit.scale + fit.offsetX,
            top = rect.top * fit.scale + fit.offsetY,
            right = rect.right * fit.scale + fit.offsetX,
            bottom = rect.bottom * fit.scale + fit.offsetY
        )

    /** True when the touch landed on the page itself rather than on the letterboxing. */
    fun isInsidePage(x: Float, y: Float, fit: PdfFitBox): Boolean =
        x >= fit.offsetX && x <= fit.offsetX + fit.width &&
            y >= fit.offsetY && y <= fit.offsetY + fit.height

    /**
     * Clamps a pan offset so a zoomed page can never be dragged away from the viewport.
     *
     * At scale `s` the page overflows the container by `(s - 1) * size / 2` on each side, which is
     * exactly how far the user is allowed to drag.
     */
    fun clampPan(
        panX: Float,
        panY: Float,
        scale: Float,
        containerWidthPx: Float,
        containerHeightPx: Float
    ): PdfPoint {
        if (scale <= 1f) return PdfPoint(0f, 0f)
        val maxX = (scale - 1f) * containerWidthPx / 2f
        val maxY = (scale - 1f) * containerHeightPx / 2f
        return PdfPoint(
            x = panX.coerceIn(-maxX, maxX),
            y = panY.coerceIn(-maxY, maxY)
        )
    }
}
