package quevedo.leandro.madeeasy.swipetorefresh.helper

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.animation.Interpolator
import quevedo.leandro.madeeasy.swipetorefresh.delegate.Callback
import quevedo.leandro.madeeasy.swipetorefresh.delegate.OnStepCallback

/***
 * Helper class to handle the animation logic of the Fling behaviour
 ***/
class FlingHelper(private val duration: Long, private val interpolator: Interpolator) {

	private var currentAnimation: ValueAnimator? = null

	/** Utility function to animate a layout fling **/
	fun animate(from: Float, to: Float, onStep: OnStepCallback, onStart: Callback? = null, onEnd: Callback? = null) {
		// Doesn't animate when there is no distance to slide
		if (from == to) {
			onEnd?.invoke()
			return
		}

		this.currentAnimation = ValueAnimator.ofFloat(from, to).apply {
			duration = this@FlingHelper.duration
			interpolator = this@FlingHelper.interpolator

			addUpdateListener { animator ->
				onStep.invoke(animator.animatedValue as Float)
			}

			addListener(object : Animator.AnimatorListener {
				override fun onAnimationStart(animation: Animator?) {
					onStart?.invoke()
				}

				override fun onAnimationEnd(animation: Animator?) {
					onEnd?.invoke()

					currentAnimation = null
				}

				override fun onAnimationCancel(animation: Animator?) {

				}

				override fun onAnimationRepeat(animation: Animator?) {

				}

			})

			start()
		}

	}

	fun cancel() {
		this.currentAnimation?.cancel()
		this.currentAnimation = null
	}

}
