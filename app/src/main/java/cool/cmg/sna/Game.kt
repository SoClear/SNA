package cool.cmg.sna

import android.content.Context
import android.util.Log
import cool.cmg.sna.accessibility.click
import cool.cmg.sna.capture.MediaProjectionService
import cool.cmg.sna.opencv.gray
import cool.cmg.sna.opencv.match
import cool.cmg.sna.opencv.minMaxLoc
import cool.cmg.sna.opencv.resizeHeightWithAspectRatio
import cool.cmg.sna.opencv.toRectCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import kotlin.math.roundToInt

class Game(context: Context) {
    // 对话框
    private val templateDialog = Utils.loadResource(context, R.drawable.cv_dialog).gray()
    private val templateNowStartGame =
        Utils.loadResource(context, R.drawable.cv_now_start_game).gray()
    private val templatePlayWithFairy =
        Utils.loadResource(context, R.drawable.cv_play_with_fairy).gray()
    private val templateOkPuling = Utils.loadResource(context, R.drawable.cv_ok_puling).gray()

    // 空圈
    private val templateEmptyCirclesDay =
        Utils.loadResource(context, R.drawable.cv_empty_circles_day).gray()
    private val templateEmptyCirclesNight =
        Utils.loadResource(context, R.drawable.cv_empty_circles_night).gray()

    // 提示
    private val templatePromptUp = Utils.loadResource(context, R.drawable.cv_prompt_up).gray()
    private val templatePromptDown = Utils.loadResource(context, R.drawable.cv_prompt_down).gray()
    private val templatePromptLeft = Utils.loadResource(context, R.drawable.cv_prompt_left).gray()
    private val templatePromptRight = Utils.loadResource(context, R.drawable.cv_prompt_right).gray()
    private val templatePromptSpace = Utils.loadResource(context, R.drawable.cv_prompt_space).gray()

    // 按钮
    val templateButtonUp = Utils.loadResource(context, R.drawable.cv_button_up).gray()
    val templateButtonDown = Utils.loadResource(context, R.drawable.cv_button_down).gray()
    val templateButtonLeft = Utils.loadResource(context, R.drawable.cv_button_left).gray()
    val templateButtonRight = Utils.loadResource(context, R.drawable.cv_button_right).gray()
    val templateButtonSpace = Utils.loadResource(context, R.drawable.cv_button_space).gray()

    val templateYellowBar = Utils.loadResource(context, R.drawable.cv_yellow_bar).gray()
    val templateAgain = Utils.loadResource(context, R.drawable.cv_again).gray()

    private var isButtonInitialized = false
    private lateinit var buttonUp: Point
    private lateinit var buttonDown: Point
    private lateinit var buttonLeft: Point
    private lateinit var buttonRight: Point
    private lateinit var buttonSpace: Point

    enum class Prompt {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        SPACE
    }

    private val TAG = "Game"

    suspend fun start() = withContext(Dispatchers.Default) {
        startDialogs()
        while (isActive){
            loop()
        }
    }

    suspend fun startDialogs() {
        match(templateDialog, timeout = 600_000L)?.run {
            delay(500)
            // 对话
            click()
            delay(2000)
            // 跟着节奏，摇摆起来
            click()
        }
        // 现在就开始游戏吧！
        match(templateNowStartGame)?.click()
        match(templatePlayWithFairy)?.run {
            // 我想和心愿精灵共舞。
            click()
            delay(2000)
            // 总分达到120分（点2次是暖暖bug!）
            click(2)
            delay(2000)
            // 没问题的话，请支付100噗灵玩游戏吧。
            click()
        }
    }

    suspend fun locateEmptyCircles(): Triple<Point, Int, Int> {
        val widthDay = templateEmptyCirclesDay.width()
        val heightDay = templateEmptyCirclesDay.height()
        val widthNight = templateEmptyCirclesNight.width()
        val heightNight = templateEmptyCirclesNight.height()
        while (true) {
            val dayCircles = matchOnce(templateEmptyCirclesDay, pointIn720p = true)
            if (dayCircles !== null) {
                return Triple(dayCircles, widthDay, heightDay)
            }
            delay(20)
            val nightCircles = matchOnce(templateEmptyCirclesNight, pointIn720p = true)
            if (nightCircles !== null) {
                return Triple(nightCircles, widthNight, heightNight)
            }
            delay(20)
        }
    }

    suspend fun getSinglePrompt(roi: Rect): Prompt {
        while (true) {
            val up = matchOnce(templatePromptUp, roi)
            if (up != null) {
                return Prompt.UP
            }
            delay(100)
            val down = matchOnce(templatePromptDown, roi)
            if (down != null) {
                return Prompt.DOWN
            }
            delay(100)
            val left = matchOnce(templatePromptLeft, roi)
            if (left != null) {
                return Prompt.LEFT
            }
            delay(100)
            val right = matchOnce(templatePromptRight, roi)
            if (right != null) {
                return Prompt.RIGHT
            }
            delay(100)
            val space = matchOnce(templatePromptSpace, roi)
            if (space != null) {
                return Prompt.SPACE
            }
            delay(100)
        }
    }

    suspend fun getAllPrompts(emptyCirclesLocation: Triple<Point, Int, Int>): List<Prompt> {
        val (center, width, height) = emptyCirclesLocation
        val topLeftY = center.y - height / 2.0
        val bottomRightY = center.y + height / 2.0

        val roi0 = Rect(
            Point(center.x - width / 2.0, topLeftY),
            Point(center.x - width / 4.0, bottomRightY)
        )
        val roi1 = Rect(
            Point(center.x - width / 4.0, topLeftY),
            Point(center.x, bottomRightY)
        )
        val roi2 = Rect(
            Point(center.x, topLeftY),
            Point(center.x + width / 4.0, bottomRightY)
        )
        val roi3 = Rect(
            Point(center.x + width / 4.0, topLeftY),
            Point(center.x + width / 2.0, bottomRightY)
        )
        return listOf(
            getSinglePrompt(roi0),
            getSinglePrompt(roi1),
            getSinglePrompt(roi2),
            getSinglePrompt(roi3),
        )
    }

    suspend fun initButtons() {
        if (isButtonInitialized) return
        match(templateButtonUp)?.let { buttonUp = it }
        match(templateButtonDown)?.let { buttonDown = it }
        match(templateButtonLeft)?.let { buttonLeft = it }
        match(templateButtonRight)?.let { buttonRight = it }
        match(templateButtonSpace)?.let { buttonSpace = it }
        isButtonInitialized = true
    }

    suspend fun clickButton(button: Prompt) = when (button) {
        Prompt.UP -> buttonUp.click()
        Prompt.DOWN -> buttonDown.click()
        Prompt.LEFT -> buttonLeft.click()
        Prompt.RIGHT -> buttonRight.click()
        Prompt.SPACE -> buttonSpace.click()
    }

    suspend fun loop() {
        match(templateOkPuling)?.run {
            // 没问题，给你噗灵。
            click()
            delay(2000)
            // 你之前的最好成绩是分，这次也要加油啊
            click()
            delay(100)
        }
        val emptyCirclesLocation = locateEmptyCircles()
        initButtons()
        repeat(5) {
            val allPrompts = getAllPrompts(emptyCirclesLocation)
            match(templateYellowBar, interval = 0L)
            delay(firstDelay)
            clickButton(allPrompts[0])
            delay(interval)
            clickButton(allPrompts[1])
            delay(interval)
            clickButton(allPrompts[2])
            delay(interval)
            clickButton(allPrompts[3])
        }
        delay(loopDelay)
        // 恭喜你，完美通关！
        clickButton(Prompt.SPACE)
        delay(2000)
        // 点击空白区域继续
        clickButton(Prompt.SPACE)
        delay(1000)
        // 要不要再挑战一次
        clickButton(Prompt.SPACE)
        // 是的，我想再挑战一次！
        match(templateAgain)?.click()
        delay(2000)
        // 总分达到120分
        clickButton(Prompt.SPACE)
        delay(2000)
        // 没问题的话，请支付100噗灵玩游戏吧。
        clickButton(Prompt.SPACE)
    }

    fun matchOnce(
        template: Mat,
        roi: Rect? = null,
        mask: Mat? = null,
        threshold: Double = Companion.threshold,
        pointIn720p: Boolean = false,
    ): Point? {
        return MediaProjectionService.INSTANCE?.takeScreenshot { screenshot ->
            val ratio = screenshot.height() / 720.0
            screenshot
                .resizeHeightWithAspectRatio(720.0)
                .let { if (roi == null) it else it.submat(roi) }
                .gray()
                .runCatching { match(template, mask) }
                .getOrNull()
                ?.minMaxLoc()
                ?.takeIf { it.maxVal >= threshold }
                ?.let {
                    val center = it.maxLoc.toRectCenter(template.width(), template.height())
                    val ratio = if (pointIn720p) 1.0 else ratio
                    Point(center.x * ratio, center.y * ratio)
                }
        }
    }

    suspend fun match(
        template: Mat,
        roi: Rect? = null,
        mask: Mat? = null,
        timeout: Long = 5000L,
        interval: Long = 100L,
        threshold: Double = Companion.threshold,
        pointIn720p: Boolean = false,
    ): Point? = withTimeoutOrNull(timeout) {
        while (isActive) {
            val maxLoc =  matchOnce(template, roi, mask, threshold, pointIn720p)
            if (maxLoc == null) {
                delay(interval)
            } else {
                return@withTimeoutOrNull maxLoc
            }
        }
        null
    }

    suspend fun Point.click(times: Int = 1) {
        MainService.instance?.click(x.roundToInt(), y.roundToInt(), times)
    }

    companion object {
        var firstDelay = 1460L
        var interval = 1090L
        var loopDelay = 15_000L
        var threshold = 0.85
    }
}
