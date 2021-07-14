package quevedo.leandro.madeeasy.swipetorefresh.utils

import kotlin.math.pow

/***
 * Utility function to calculate the power of a given Float number
 * @see Math.pow
 *
 * Same as [Math.pow] but deals with [Float] numbers instead
 ***/
internal fun power(x: Float, exp: Float) = x.toDouble().pow(exp.toDouble()).toFloat()

/***
 * Utility function to crop a given number into a given range
 *
 * @param x The value to be cropped
 * @param min The minimum of which [x] should be
 * @param max The maximum of which [x] should be
 *
 * @return [x] within [min] and [max]
 ***/
internal fun clamp(x: Float, min: Float, max: Float) = when {
	x < min -> min
	x > max -> max
	else -> x
}