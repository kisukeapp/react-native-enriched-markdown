package com.swmansion.enriched.markdown.utils.text

internal data class StreamingTextRange(
  val start: Int,
  val end: Int,
  val delayMs: Long,
)

/**
 * Plans desktop-style word cadence for an append-only rendered-text suffix.
 *
 * Wire and render batches are deliberately not presentation units. A large
 * native render may expose many words at once, but each word receives its own
 * stable start time. When a producer outruns the visual timeline we accelerate
 * rather than accumulating an unbounded animation backlog.
 */
internal class StreamingTextCadence {
  private var nextSegmentStartAtMs = 0L

  fun schedule(
    text: CharSequence,
    start: Int,
    end: Int,
    nowMs: Long,
  ): List<StreamingTextRange> {
    if (start < 0 || end <= start || end > text.length) return emptyList()

    val ranges = segmentWords(text, start, end)
    return ranges.map { range ->
      val segmentStart = maxOf(nextSegmentStartAtMs, nowMs)
      val delay = maxOf(segmentStart - nowMs, 0L)
      nextSegmentStartAtMs =
        segmentStart +
        if (delay < MAX_DELAY_MS) SEGMENT_DELAY_MS else ACCELERATED_SEGMENT_DELAY_MS
      StreamingTextRange(range.first, range.last + 1, delay)
    }
  }

  fun reset() {
    nextSegmentStartAtMs = 0L
  }

  private fun segmentWords(
    text: CharSequence,
    start: Int,
    end: Int,
  ): List<IntRange> {
    val wordStarts = mutableListOf<Int>()
    var index = start
    while (index < end) {
      while (index < end && !text[index].isLetterOrDigit()) index += 1
      if (index >= end) break
      wordStarts += index
      while (index < end && text[index].isLetterOrDigit()) index += 1
    }
    if (wordStarts.isEmpty()) return listOf(start until end)

    return wordStarts.mapIndexed { wordIndex, wordStart ->
      val segmentStart = if (wordIndex == 0) start else wordStart
      val segmentEnd = wordStarts.getOrNull(wordIndex + 1) ?: end
      segmentStart until segmentEnd
    }
  }

  private companion object {
    const val SEGMENT_DELAY_MS = 24L
    const val ACCELERATED_SEGMENT_DELAY_MS = 6L
    const val MAX_DELAY_MS = 800L
  }
}
