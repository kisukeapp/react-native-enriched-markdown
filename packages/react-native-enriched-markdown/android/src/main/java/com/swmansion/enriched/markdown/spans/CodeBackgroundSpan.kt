package com.swmansion.enriched.markdown.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import com.swmansion.enriched.markdown.styles.StyleConfig
import kotlin.math.max
import kotlin.math.min

class CodeBackgroundSpan(
  private val styleConfig: StyleConfig,
) : LineBackgroundSpan {
  companion object {
    private const val BORDER_WIDTH = 1.0f

    private val sharedBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sharedBorderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = BORDER_WIDTH
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
      }
  }

  // Reusable drawing objects per instance
  private val rect = RectF()
  private val path = Path()

  override fun drawBackground(
    canvas: Canvas,
    p: Paint,
    left: Int,
    right: Int,
    top: Int,
    baseline: Int,
    bottom: Int,
    text: CharSequence,
    start: Int,
    end: Int,
    lineNum: Int,
  ) {
    if (text !is Spanned) return

    val spanStart = text.getSpanStart(this)
    val spanEnd = text.getSpanEnd(this)
    if (spanStart !in 0 until spanEnd) return

    // Each laid-out line is a cloned inline box. Measuring the actual fragment
    // is important: extending a wrapped span to the TextView's right edge makes
    // short path fragments look like full-width code blocks.
    val fragmentStart = max(spanStart, start)
    val fragmentEnd = min(spanEnd, end)
    val finalBottom = adjustBottomForMargin(text, end, bottom)
    val leadingMargin = leadingMarginAt(text, start)
    val codeStyle = styleConfig.codeStyle
    val startX = getHorizontalOffset(text, start, end, fragmentStart, p, leadingMargin) + left
    val endX = getHorizontalOffset(text, start, end, fragmentEnd, p, leadingMargin) + left
    val horizontalPadding = codeStyle.paddingHorizontal
    val verticalPadding = codeStyle.paddingVertical
    rect.set(
      min(startX, endX) - horizontalPadding,
      top.toFloat() - verticalPadding,
      max(startX, endX) + horizontalPadding,
      finalBottom.toFloat() + verticalPadding,
    )

    sharedBackgroundPaint.color = codeStyle.backgroundColor
    sharedBorderPaint.color = codeStyle.borderColor
    drawFragment(canvas, codeStyle.borderRadius)
  }

  /**
   * Returns the x position of [index] relative to the line's left edge, including any
   * leading margin. The measuring StaticLayout is built from a subSequence that keeps
   * all spans, so LeadingMarginSpans (lists, blockquotes) are already applied to
   * getPrimaryHorizontal; adding the margin again on top would shift the background
   * right by the indent. The margin is only added explicitly in the early-return case,
   * where no layout is built.
   */
  private fun getHorizontalOffset(
    text: CharSequence,
    lineStart: Int,
    lineEnd: Int,
    index: Int,
    paint: Paint,
    leadingMargin: Int,
  ): Float {
    if (index <= lineStart) return leadingMargin.toFloat()
    val lineText = text.subSequence(lineStart, lineEnd)
    val textPaint = paint as? TextPaint ?: TextPaint(paint)
    val layout = StaticLayout.Builder.obtain(lineText, 0, lineText.length, textPaint, 10000).build()
    return layout.getPrimaryHorizontal(index - lineStart)
  }

  private fun drawFragment(
    canvas: Canvas,
    radius: Float,
  ) {
    path.reset()
    path.addRoundRect(rect, radius, radius, Path.Direction.CW)
    canvas.drawPath(path, sharedBackgroundPaint)
    canvas.drawPath(path, sharedBorderPaint)
  }

  private fun leadingMarginAt(
    text: Spanned,
    lineStart: Int,
  ): Int {
    if (lineStart >= text.length) return 0
    val spans = text.getSpans(lineStart, lineStart + 1, LeadingMarginSpan::class.java)
    var margin = 0
    for (span in spans) {
      margin += span.getLeadingMargin(false)
    }
    return margin
  }

  private fun adjustBottomForMargin(
    text: Spanned,
    lineEnd: Int,
    bottom: Int,
  ): Int {
    if (lineEnd <= 0 || lineEnd > text.length || text[lineEnd - 1] != '\n') return bottom
    val marginSpans = text.getSpans(lineEnd - 1, lineEnd, MarginBottomSpan::class.java)
    var adjusted = bottom
    for (span in marginSpans) {
      if (text.getSpanEnd(span) == lineEnd) adjusted -= span.marginBottom.toInt()
    }
    return adjusted
  }
}
