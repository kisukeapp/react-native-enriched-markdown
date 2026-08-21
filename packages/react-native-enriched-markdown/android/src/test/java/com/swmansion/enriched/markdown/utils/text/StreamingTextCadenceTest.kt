package com.swmansion.enriched.markdown.utils.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTextCadenceTest {
  @Test
  fun `large render batches remain word paced`() {
    val cadence = StreamingTextCadence()
    val text = "one two three four"

    val scheduled = cadence.schedule(text, 0, text.length, 1_000)

    assertEquals(listOf("one ", "two ", "three ", "four"), scheduled.map { text.substring(it.start, it.end) })
    assertEquals(listOf(0L, 24L, 48L, 72L), scheduled.map { it.delayMs })
  }

  @Test
  fun `successive native renders share one visual timeline`() {
    val cadence = StreamingTextCadence()
    val first = "one two "
    val second = "one two three four"

    val initial = cadence.schedule(first, 0, first.length, 1_000)
    val appended = cadence.schedule(second, first.length, second.length, 1_010)

    assertEquals(listOf(0L, 24L), initial.map { it.delayMs })
    assertEquals(listOf(38L, 62L), appended.map { it.delayMs })
  }

  @Test
  fun `equal and stale suffixes never replay visible words`() {
    val cadence = StreamingTextCadence()
    val text = "one two"

    cadence.schedule(text, 0, text.length, 1_000)

    assertEquals(emptyList<StreamingTextRange>(), cadence.schedule(text, text.length, text.length, 1_010))
    assertEquals(emptyList<StreamingTextRange>(), cadence.schedule(text, 6, 4, 1_010))
  }

  @Test
  fun `an extreme producer backlog accelerates without merging words`() {
    val cadence = StreamingTextCadence()
    val text = (1..100).joinToString(" ") { "word$it" }

    val scheduled = cadence.schedule(text, 0, text.length, 1_000)

    assertEquals(100, scheduled.size)
    assertTrue(scheduled.zipWithNext().all { (left, right) -> right.delayMs > left.delayMs })
    assertTrue(scheduled.last().delayMs < 1_300)
  }

  @Test
  fun `reset starts a new message at zero delay`() {
    val cadence = StreamingTextCadence()
    cadence.schedule("one two", 0, 7, 1_000)
    cadence.reset()

    assertEquals(0L, cadence.schedule("next", 0, 4, 2_000).single().delayMs)
  }
}
