package quevedo.leandro.madeeasy.swipetorefresh.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.annotation.RequiresApi
import androidx.core.graphics.minus
import quevedo.leandro.madeeasy.swipetorefresh.delegate.Callback
import quevedo.leandro.madeeasy.swipetorefresh.helper.FlingHelper
import quevedo.leandro.madeeasy.swipetorefresh.utils.clamp
import quevedo.leandro.madeeasy.swipetorefresh.utils.power
import java.lang.Math.abs
import kotlin.math.max

@Suppress("SameParameterValue", "ReplaceJavaStaticMethodWithKotlinAnalog", "unused")
@SuppressLint("ClickableViewAccessibility")
class SwipeToRefreshLayout : ViewGroup {

	// region State variables
	private var initialPoint = PointF(0f, 0f)
	private var lastPoint = initialPoint

	private var onRefreshListener: (() -> Unit)? = null

	private val flingHelper: FlingHelper by lazy {
		FlingHelper(flingVelocity, DecelerateInterpolator(2f))
	}

	private var isDragging = false
	private var _isRefreshing = false
	private var isFlinging = false
	// endregion

	/***
	 * Determine whether the layout is refreshing
	 *
	 * Setting this variable will animate the layout's state
	 ***/
	var isRefreshing
		get() = _isRefreshing
		set(value) {
			if (this._isRefreshing == value) return

			if (this._isRefreshing && !value)// If it is refreshing, but set to idle, animate the collapse of the loader
				animateFlingCollapse()
			else if (!this._isRefreshing && value)// If it is idle, but set to refresh, animate the expansion of the loader
				animateFlingExpand()

			this._isRefreshing = value
		}

	// region Configuration values
	private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
	private val flingVelocity = 250L// In ms
	// endregion

	// region Children view variables
	private var maxDragHeight = 0
	private var maxContentHeight = 0

	private val loaderView get() = getChildAt(0)
	private val contentView get() = getChildAt(1)
	// endregion

	// region Constructors
	constructor(context: Context) : super(context)
	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
	@RequiresApi(Build.VERSION_CODES.LOLLIPOP) constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
	// endregion

	init {
		// Assures that the child views were provided
		if (childCount != 2) {
			throw Exception("LoaderView and ContentView not found. Did you forgot to place 2 views as child of the SwipeToRefreshLayout?")
		}
	}

	// region Children measurement and placement

	/** Update [loaderView] and [contentView] dimension measurements **/
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec)

		val width = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
		val height = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)

		contentView.measure(width, height)
		measureChild(loaderView, widthMeasureSpec, heightMeasureSpec)
	}

	/** Update inner views dimensions **/
	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		loaderView.layout(0, 0, measuredWidth, loaderView.measuredHeight)
		contentView.layout(0, 0, measuredWidth, measuredHeight)

		maxDragHeight = loaderView.height
		maxContentHeight = contentView.height
	}

	// endregion

	// region Touch handling

	/** Determine whether the component should interpret the touch as a vertical drag to refresh **/
	override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
		var handled = false
		e?.let { event ->
			val pointer = event.actionIndex
			val point = PointF(event.getX(pointer), event.getY(pointer))

			when {

				// Ignore touch events when disabled
				!isEnabled -> handled = false

				// Ignore touch events while refreshing
				_isRefreshing -> handled = false

				// Store the initial point as an anchor
				event.actionMasked == MotionEvent.ACTION_DOWN -> {
					initialPoint = point
				}

				// Calculates the moved distance and determine if it should start a dragging
				event.actionMasked == MotionEvent.ACTION_MOVE -> {
					val diff = point - initialPoint

					when {

						// If it is already dragging, just steal children focusability
						isDragging -> handled = true

						// Ignore events that were dragging upwards vertically
						diff.y < 0 -> handled = false

						// If it has been dragged more than the touchSlop
						// It was moved towards the Y axis
						// And the content view reached the scroll top
						diff.y >= touchSlop && abs(diff.y) > abs(diff.x) && isContentViewAtScrollTop() -> {
							// Start dragging
							isDragging = true

							handled = true
						}

					}
				}

			}
		}

		return handled
	}

	/** Handles touches and animate the movement **/
	override fun onTouchEvent(e: MotionEvent?): Boolean {
		e?.let { event ->
			val pointer = event.actionIndex
			val point = PointF(event.getX(pointer), event.getY(pointer))

			when (event.actionMasked) {

				MotionEvent.ACTION_MOVE -> {
					val diff = point - initialPoint

					// Define the threshold to consider the loader appearance
					val threshold = maxDragHeight * 1.7f

					var translation = max(diff.y, 0f)

					// When the distance overlaps the threshold point, then apply friction on it
					// The friction force being dynamic, meaning that the more drag distance, the more friction
					if (translation >= threshold) {
						// Calculate the scrollable area of the over scroll
						val scrollableArea = (maxContentHeight - threshold) / 2

						// Calculate the distance dragged after the threshold
						val overscroll = clamp(diff.y - threshold, 0f, scrollableArea / 2) // Important to crop about half of the screen, otherwise it starts to decrease the translationY value, making the views slide upwards while dragging downwards
						// Calculate the friction, using a inverted linear curve
						val friction = 1f - overscroll / scrollableArea
						// Store the new translation distance
						translation = threshold + overscroll * friction
					}

					setTranslation(translation)

					lastPoint = point
				}

				MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
					// Check the dragged distance
					val diff = lastPoint - initialPoint

					// Cancels the dragging
					isDragging = false

					// Check if it at least covers 170% of the loader height
					if (diff.y >= maxDragHeight * 1.7) {
						_isRefreshing = true
						animateFlingExpand()
					} else {
						animateFlingCollapse()
					}
				}

			}
		}

		return false
	}

	// endregion

	// region Utility functions
	/** Utility function to determine whether [contentView] has reached it's scroll top, therefore not being able to scroll upwards **/
	private fun isContentViewAtScrollTop() = !contentView.canScrollVertically(-1)

	/** Utility function to set both [contentView] and [loaderView] translationY, and automatically apply their respectively offsets **/
	private fun setTranslation(y: Float) {
		// Animate the loader
		val normalizedY = clamp(y, 0f, maxDragHeight.toFloat())
		loaderView.alpha = power(normalizedY / maxDragHeight.toFloat(), 2f)// Acceleration curve to control opacity
		loaderView.translationY = (power(normalizedY / maxDragHeight, 1.7f) * maxDragHeight) - maxDragHeight// Inverse acceleration curve to control translation

		// Animate the content view
		contentView.translationY = y
	}

	/** Utility function to animate the layout starting to refresh **/
	private fun animateFlingExpand() {
		val currentY = contentView.translationY
		val targetY = maxDragHeight.toFloat()

		this.flingHelper.animate(
			from = currentY,
			to = targetY,
			onStart = {
				isFlinging = true
			},
			onStep = this::setTranslation,
			onEnd = {
				isFlinging = false
				contentView.invalidate()

				// Calls the onRefreshListener callback, because the fling has expanded
				// Therefore a swipe to refresh was detected
				onRefreshListener?.invoke()
			}
		)
	}

	/** Utility function to animate the layout ending the refresh **/
	private fun animateFlingCollapse() {
		val currentY = contentView.translationY
		val targetY = 0f

		this.flingHelper.animate(
			from = currentY,
			to = targetY,
			onStart = {
				isFlinging = true
			},
			onStep = this::setTranslation,
			onEnd = {
				isFlinging = false
				contentView.invalidate()

				// If refreshing, cancels the refresh
				if (_isRefreshing) _isRefreshing = false
			}
		)
	}
	// endregion

	/***
	 * Sets the listener to be called whenever the layout starts to refresh
	 *
	 * Triggers whenever [isRefreshing] is set to 'true' as well
	 * @see isRefreshing
	 *
	 * @param listener The callback to be invoked
	 ***/
	fun doOnRefresh(listener: Callback) {
		this.onRefreshListener = listener
	}

}