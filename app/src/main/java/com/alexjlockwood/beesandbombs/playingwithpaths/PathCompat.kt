package com.alexjlockwood.beesandbombs.playingwithpaths

import android.graphics.Path
import android.graphics.PathMeasure
import android.os.Build
import androidx.annotation.FloatRange
import androidx.annotation.Size
import androidx.compose.ui.graphics.vector.Path

private const val MAX_NUM_POINTS = 100
private const val FRACTION_OFFSET = 0
private const val X_OFFSET = 1
private const val Y_OFFSET = 2
private const val NUM_COMPONENTS = 3

/** Implementation of [Path.approximate] for pre-O devices. */
@Size(multiple = 3)
fun approximate(path: Path, @FloatRange(from = 0.0) acceptableError: Float): FloatArray {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return path.approximate(acceptableError)
    }
    if (acceptableError < 0) {
        throw IllegalArgumentException("acceptableError must be greater than or equal to 0")
    }
    // Measure the total length the whole pathData.
    val measureForTotalLength = PathMeasure(path, false)
    var totalLength = 0f
    // The sum of the previous contour plus the current one. Using the sum here
    // because we want to directly subtract from it later.
    val summedContourLengths = mutableListOf<Float>()
    summedContourLengths.add(0f)
    do {
        val pathLength = measureForTotalLength.length
        totalLength += pathLength
        summedContourLengths.add(totalLength)
    } while (measureForTotalLength.nextContour())

    // Now determine how many sample points we need, and the step for next sample.
    val pathMeasure = PathMeasure(path, false)

    val numPoints = ((totalLength / acceptableError).toInt() + 1).coerceAtMost(MAX_NUM_POINTS)

    val coords = FloatArray(NUM_COMPONENTS * numPoints)
    val position = FloatArray(2)

    var contourIndex = 0
    val step = totalLength / (numPoints - 1)
    var cumulativeDistance = 0f

    // For each sample point, determine whether we need to move on to next contour.
    // After we find the right contour, then sample it using the current distance value minus
    // the previously sampled contours' total length.
    for (i in 0 until numPoints) {
        // The cumulative distance traveled minus the total length of the previous contours
        // (not including the current contour).
        val contourDistance = cumulativeDistance - summedContourLengths[contourIndex]
        pathMeasure.getPosTan(contourDistance, position, null)

        coords[i * NUM_COMPONENTS + FRACTION_OFFSET] = cumulativeDistance / totalLength
        coords[i * NUM_COMPONENTS + X_OFFSET] = position[0]
        coords[i * NUM_COMPONENTS + Y_OFFSET] = position[1]

        cumulativeDistance = (cumulativeDistance + step).coerceAtMost(totalLength)

        // Using a while statement is necessary in the rare case where step is greater than
        // the length a path contour.
        while (summedContourLengths[contourIndex + 1] < cumulativeDistance) {
            contourIndex++
            pathMeasure.nextContour()
        }
    }

    coords[(numPoints - 1) * NUM_COMPONENTS + FRACTION_OFFSET] = 1f
    return coords
}