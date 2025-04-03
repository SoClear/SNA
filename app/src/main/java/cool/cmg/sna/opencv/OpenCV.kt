package cool.cmg.sna.opencv

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfRect
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.Objdetect

// 获取灰度图，改变原始Mat
fun Mat.gray(): Mat = apply {
    if (channels() != 1) {
        Imgproc.cvtColor(this, this, Imgproc.COLOR_BGR2GRAY)
    }
}


// 获取灰度图，不改变原始Mat
fun Mat.grayed(): Mat = if (channels() == 1) {
    clone()
} else {
    Mat().also { Imgproc.cvtColor(this, it, Imgproc.COLOR_BGR2GRAY) }
}


// 匹配模板，改变原始Mat
fun Mat.match(template: Mat, mask: Mat? = null, method: Int = Imgproc.TM_CCOEFF_NORMED): Mat = apply {
    if (mask == null) {
        Imgproc.matchTemplate(this, template, this, method)
    } else {
        Imgproc.matchTemplate(this, template, this, method, mask)
    }
}


// 匹配模板，不改变原始Mat
fun Mat.matched(template: Mat, mask: Mat? = null, method: Int = Imgproc.TM_CCOEFF_NORMED): Mat = Mat().also {
    if (mask == null) {
        Imgproc.matchTemplate(this, template, it, method)
    } else {
        Imgproc.matchTemplate(this, template, it, method, mask)
    }
}


// 最大最小值
fun Mat.minMaxLoc(): Core.MinMaxLocResult = Core.minMaxLoc(this)


// 阈值，改变原始Mat
fun Mat.threshold(threshold: Double, maxVal: Double = 255.0, type: Int = Imgproc.THRESH_BINARY): Mat = apply {
    Imgproc.threshold(this, this, threshold, maxVal, type)
}


// 阈值，不改变原始Mat
fun Mat.thresholded(threshold: Double, maxVal: Double = 255.0, type: Int = Imgproc.THRESH_BINARY): Mat = Mat().also {
    Imgproc.threshold(this, it, threshold, maxVal, type)
}


// 非零点
fun Mat.findNonZero(): MatOfPoint = MatOfPoint().also { Core.findNonZero(this, it) }


// 轮廓结果
data class ContourResult(val contourList: List<MatOfPoint>, val hierarchy: Mat)


// 查找轮廓
fun Mat.findContours(
    mode: Int = Imgproc.RETR_LIST, method: Int = Imgproc.CHAIN_APPROX_SIMPLE, offset: Point? = null
): ContourResult {
    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()
    if (offset == null) {
        Imgproc.findContours(this, contours, hierarchy, mode, method)
    } else {
        Imgproc.findContours(this, contours, hierarchy, mode, method, offset)
    }
    return ContourResult(contours, hierarchy)
}


// Point 转 Rect
fun Point.toRect(width: Int, height: Int): Rect = Rect(x.toInt(), y.toInt(), width, height)


// MatOfPoint 转 MatOfRect
fun MatOfPoint.toMatOfRect(width: Int, height: Int): MatOfRect =
    toArray().map { it.toRect(width, height) }
        .let { rects -> MatOfRect().apply { fromList(rects) } }


// Point 转 Rect的中心坐标
fun Point.toRectCenter(width: Int, height: Int): Point = Point(x + width / 2.0, y + height / 2.0)


// Rect的中心坐标
fun Rect.center(): Point = Point(x + width / 2.0, y + height / 2.0)


// 矩形分组结果
data class GroupResult(val rectList: List<Rect>, val weights: List<Int>)


// 矩形分组，改变原始MatOfRect
fun MatOfRect.group(threshold: Int = 1, eps: Double = 0.2): GroupResult {
    val weights = MatOfInt()
    Objdetect.groupRectangles(this, weights, threshold, eps)
    return GroupResult(toList(), weights.toList())
}


// 矩形分组，不改变原始MatOfRect
fun MatOfRect.grouped(threshold: Int = 1, eps: Double = 0.2): GroupResult {
    return MatOfRect(clone()).group(threshold, eps)
}


// 缩放，改变原始Mat
fun Mat.resize(size: Size, fx: Double = 0.0, fy: Double = 0.0, interpolation: Int = Imgproc.INTER_LINEAR) = apply {
    Imgproc.resize(this, this, size, fx, fy, interpolation)
}


// 缩放，不改变原始Mat
fun Mat.resized(size: Size, fx: Double = 0.0, fy: Double = 0.0, interpolation: Int = Imgproc.INTER_LINEAR): Mat =
    Mat().also { Imgproc.resize(this, it, size, fx, fy, interpolation) }


// 缩放到指定宽度
fun Mat.resizeWidthWithAspectRatio(width: Double, interpolation: Int = Imgproc.INTER_LINEAR) =
    resize(Size(width, width * height() / width()), interpolation = interpolation)


// 缩放到指定高度
fun Mat.resizeHeightWithAspectRatio(height: Double, interpolation: Int = Imgproc.INTER_LINEAR) =
    resize(Size(height * width() / height(), height), interpolation = interpolation)


// 画矩形，改变原始Mat
fun Mat.draw(
    rect: Rect,
    scalar: Scalar = Scalar(0.0, 255.0, 0.0),
    thickness: Int = 2,
    lineType: Int = Imgproc.LINE_8
): Mat = apply { Imgproc.rectangle(this, rect, scalar, thickness, lineType) }


// 画矩形，返回自身。会改变mat
fun Rect.drawOn(
    mat: Mat,
    scalar: Scalar = Scalar(0.0, 255.0, 0.0),
    thickness: Int = 2,
    lineType: Int = Imgproc.LINE_8
): Rect = apply { mat.draw(this, scalar, thickness, lineType) }


// 漫水填充
fun Mat.floodFill(startingPoint: Point, maxDiff: Double, newValue: Double): Mat = apply {
    Mat().use { mask ->
        Imgproc.floodFill(
            this,
            mask,
            startingPoint,
            Scalar(newValue),
            Rect(),
            Scalar(maxDiff),
            Scalar(maxDiff),
            Imgproc.FLOODFILL_FIXED_RANGE
        )
    }
}

// 释放Mat
inline fun <T : Mat, R> T.use(block: (T) -> R): R = try {
    block(this)
} finally {
    try {
        release()
    } catch (_: Exception) {
    }
}
