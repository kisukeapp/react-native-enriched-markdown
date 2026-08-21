package com.swmansion.enriched.markdown.styles

import com.facebook.react.bridge.ReadableMap

data class CodeStyle(
  val fontFamily: String,
  val fontSize: Float,
  val color: Int,
  val backgroundColor: Int,
  val borderColor: Int,
  val borderRadius: Float,
  val paddingHorizontal: Float,
  val paddingVertical: Float,
) {
  companion object {
    fun fromReadableMap(
      map: ReadableMap,
      parser: StyleParser,
    ): CodeStyle {
      val fontFamily = parser.parseString(map, "fontFamily")
      val fontSizeRaw = parser.parseOptionalDouble(map, "fontSize").toFloat()
      val fontSize = if (fontSizeRaw > 0) parser.toPixelFromSP(fontSizeRaw) else 0f
      val color = parser.parseColor(map, "color")
      val backgroundColor = parser.parseColor(map, "backgroundColor")
      val borderColor = parser.parseColor(map, "borderColor")
      val borderRadius = parser.toPixelFromDIP(map.getDouble("borderRadius").toFloat())
      val paddingHorizontal = parser.toPixelFromDIP(map.getDouble("paddingHorizontal").toFloat())
      val paddingVertical = parser.toPixelFromDIP(map.getDouble("paddingVertical").toFloat())
      return CodeStyle(
        fontFamily,
        fontSize,
        color,
        backgroundColor,
        borderColor,
        borderRadius,
        paddingHorizontal,
        paddingVertical,
      )
    }
  }
}
