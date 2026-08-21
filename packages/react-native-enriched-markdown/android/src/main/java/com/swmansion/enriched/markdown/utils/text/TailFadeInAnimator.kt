package com.swmansion.enriched.markdown.utils.text

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.SystemClock
import android.text.Spannable
import android.view.animation.PathInterpolator
import android.widget.TextView
import com.swmansion.enriched.markdown.spans.FadeInSpan
import com.swmansion.enriched.markdown.utils.common.isReducedMotionEnabled
import java.lang.ref.WeakReference

internal class TailFadeInAnimator(
  textView: TextView,
  private val cadence: StreamingTextCadence = StreamingTextCadence(),
) {
  private val viewRef = WeakReference(textView)

  private data class ActiveFade(
    val start: Int,
    val end: Int,
    val span: FadeInSpan,
    val animator: ValueAnimator,
  )

  private val activeAnimations = mutableMapOf<FadeInSpan, ActiveFade>()

  fun animate(
    tailStart: Int,
    tailEnd: Int,
  ) {
    if (tailEnd <= tailStart) return

    val textView = viewRef.get() ?: return
    val spannable = textView.text as? Spannable ?: return

    if (isReducedMotionEnabled(textView.context)) return

    retarget()
    val scheduled = cadence.schedule(spannable, tailStart, tailEnd, SystemClock.uptimeMillis())
    for (segment in scheduled) {
      val fadeSpan = FadeInSpan().apply { alpha = 0f }
      spannable.setSpan(fadeSpan, segment.start, segment.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

      val animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
          duration = FADE_DURATION_MS
          startDelay = segment.delayMs
          interpolator = FADE_INTERPOLATOR

          addUpdateListener { anim ->
            fadeSpan.alpha = anim.animatedValue as Float
            val active = activeAnimations[fadeSpan] ?: return@addUpdateListener
            attach(active)
            viewRef.get()?.invalidate()
          }

          addListener(
            object : AnimatorListenerAdapter() {
              override fun onAnimationEnd(animation: Animator) {
                cleanup(fadeSpan)
              }

              override fun onAnimationCancel(animation: Animator) {
                cleanup(fadeSpan)
              }
            },
          )
        }

      val active = ActiveFade(segment.start, segment.end, fadeSpan, animator)
      activeAnimations[fadeSpan] = active
      animator.start()
    }
  }

  /** Reattach in-flight word spans after a fresh native markdown render. */
  fun retarget() {
    activeAnimations.values.toList().forEach(::attach)
  }

  private fun attach(active: ActiveFade) {
    val spannable = viewRef.get()?.text as? Spannable ?: return
    if (active.start >= spannable.length || active.end > spannable.length) {
      active.animator.cancel()
      return
    }
    spannable.setSpan(active.span, active.start, active.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
  }

  private fun cleanup(span: FadeInSpan) {
    activeAnimations.remove(span)
    val spannable = viewRef.get()?.text as? Spannable ?: return
    span.alpha = 1f
    spannable.removeSpan(span)
  }

  // Cadence lifecycle is owned by the caller: a shared cadence must survive a
  // single view's teardown so sibling segments keep the message-wide pacing.
  fun cancelAll() {
    val animations = activeAnimations.values.map { it.animator }
    animations.forEach(ValueAnimator::cancel)
    activeAnimations.clear()
  }

  companion object {
    private const val FADE_DURATION_MS = 200L

    // Matches the desktop streaming fade curve: cubic-bezier(.37, .55, .86, .88).
    private val FADE_INTERPOLATOR = PathInterpolator(0.37f, 0.55f, 0.86f, 0.88f)
  }
}
