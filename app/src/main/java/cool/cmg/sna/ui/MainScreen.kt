package cool.cmg.sna.ui

//import cool.cmg.sna.accessibility.AccessibilityGesture
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cool.cmg.sna.Game
import cool.cmg.sna.MainService
import cool.cmg.sna.capture.MediaProjectionActivityResultContract
import cool.cmg.sna.capture.MediaProjectionService
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                // 让 Column 占据可用宽度
                .fillMaxSize()
                // 添加一些顶部间距，避免太靠近状态栏
                .padding(innerPadding)
                // 添加滚动功能
                .verticalScroll(scrollState)
            ,
            horizontalAlignment = Alignment.CenterHorizontally // Column 内的元素水平居中
        ) {
            val context = LocalContext.current
            val sp = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
            var firstDelay by remember { mutableIntStateOf(sp.getInt("firstDelay", Game.firstDelay.toInt())) }

            Text(
                text = "从黄色进度条开始到第一次点击按钮的时间间隔（点我恢复默认）：$firstDelay ms",
                modifier = Modifier.clickable(onClick = {
                    firstDelay = 1460
                    Game.firstDelay = 1460L
                    sp.edit { putInt("firstDelay", firstDelay) }
                }).padding(8.dp)
            )
            IntSlider(
                value = firstDelay,
                onValueChange = { newValue ->
                    firstDelay = newValue // 更新状态
                },
                valueRange = 1300..2000,
                step = 1,
                modifier = Modifier.fillMaxWidth(0.9f).align(Alignment.CenterHorizontally), // 让 Slider 不要占满全宽
                onValueChangeFinished = {
                    Game.firstDelay = firstDelay.toLong()
                    sp.edit { putInt("firstDelay", firstDelay) }
                }
            )
            var interval by remember { mutableIntStateOf(sp.getInt("interval", Game.interval.toInt())) }
            Text(
                text = "每次点击按钮的时间间隔（点我恢复默认）：$interval ms",
                modifier = Modifier.clickable(onClick = {
                    interval = 1090
                    Game.interval = 1090L
                    sp.edit { putInt("interval", interval) }
                }).padding(8.dp)
            )
            IntSlider(
                value = interval,
                onValueChange = { newValue ->
                    interval = newValue // 更新状态
                },
                valueRange = 900..1500,
                step = 1,
                modifier = Modifier.fillMaxWidth(0.9f).align(Alignment.CenterHorizontally), // 让 Slider 不要占满全宽
                onValueChangeFinished = {
                    Game.interval = interval.toLong()
                    sp.edit { putInt("interval", interval) }
                }
            )
            var loopDelay by remember { mutableIntStateOf(sp.getInt("loopDelay", Game.loopDelay.toInt())) }
            Text(
                text = "每局最后一次点击按钮到点击“恭喜你，完美通关...”的时间间隔（点我恢复默认）：$loopDelay ms",
                modifier = Modifier.clickable(onClick = {
                    loopDelay = 15_000
                    Game.loopDelay = 15_000L
                    sp.edit { putInt("loopDelay", loopDelay) }
                }).padding(8.dp)
            )
            IntSlider(
                value = loopDelay,
                onValueChange = { newValue ->
                    loopDelay = newValue // 更新状态
                },
                valueRange = 10_000..30_000,
                step = 100,
                modifier = Modifier.fillMaxWidth(0.9f).align(Alignment.CenterHorizontally), // 让 Slider 不要占满全宽
                onValueChangeFinished = {
                    Game.loopDelay = loopDelay.toLong()
                    sp.edit { putInt("loopDelay", loopDelay) }
                }
            )
            var threshold by remember { mutableFloatStateOf(sp.getFloat("threshold", Game.threshold.toFloat())) }

            Text(
                text = "匹配阈值（点我恢复默认）：${"%.2f".format(threshold)}",
                modifier = Modifier.clickable(onClick = {
                    threshold = 0.85f
                    Game.threshold = 0.85
                    sp.edit { putFloat("threshold", threshold) }
                }).padding(8.dp)
            )
            Slider(
                value = threshold,
                onValueChange = {
                    threshold = it
                },
                valueRange = 0.75f..0.95f,
                modifier = Modifier.fillMaxWidth(0.9f).align(Alignment.CenterHorizontally),
                onValueChangeFinished = {
                    Game.threshold = threshold.toDouble()
                    sp.edit { putFloat("threshold", threshold) }
                }
            )

            val requestPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {
                if (isNotificationPermissionPermanentlyDenied(context as Activity)) {
                    Toast.makeText(context, "请开启通知权限", Toast.LENGTH_LONG).show()
                    context.openAppSettings()
                }
            }
            val mediaProjectionLauncher = rememberLauncherForActivityResult(
                MediaProjectionActivityResultContract()
            ) {
                if (it == null) return@rememberLauncherForActivityResult
                val intent = Intent().apply {
                    setClassName("com.papegames.infinitynikki", "com.epicgames.unreal.GameActivity")
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                MediaProjectionService.start(context, it)
            }
            // 第一个 Spacer，占据一半的剩余空间，将 Button 向下推
            Spacer(modifier = Modifier.weight(1f))
            // 1. 使用 collectAsState() 订阅 StateFlow
            //    当 projectionState 的值改变时，这个 Composable 会自动重组
            val currentProjectionInstance by MediaProjectionService.projectionState.collectAsStateWithLifecycle()

            // 2. 根据 StateFlow 的当前值判断服务是否在运行
            val isServiceRunning = currentProjectionInstance != null

            if (isServiceRunning) {
                Button(
                    onClick = {
                        MediaProjectionService.stop(context)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("停止辅助", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        NotificationManagerCompat.from(context).areNotificationsEnabled()
                        if (!context.hasNotificationPermission()) {
                            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@Button
                        }
                        if (!MainService.isEnabled(context)) {
                            Toast.makeText(context, "请开启无障碍权限", Toast.LENGTH_LONG).show()
                            context.startActivity(Intent(ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            return@Button
                        }
                        mediaProjectionLauncher.launch()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(text = "无限暖暖，启动！", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            }


            // 第二个 Spacer，占据另一半的剩余空间，将 Button 向上推
            // 因为两个 Spacer 的 weight 相同 (都是 1f)，它们会平分剩余空间
            Spacer(modifier = Modifier.weight(1f))

        }
    }
}

/**
 * 一个用于选择整数值的 Slider 组件。
 *
 * @param value 当前选择的整数值。
 * @param onValueChange 当值发生变化时回调，提供新的整数值。
 * @param valueRange 允许选择的整数范围 (IntRange)。
 * @param modifier 应用于此 Slider 的 Modifier。
 * @param enabled 控制此 Slider 是否启用。
 * @param step 移动的步长，必须大于 0。默认为 1。
 * @param onValueChangeFinished 当用户停止拖动时调用的可选回调。
 */
@Composable
fun IntSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    step: Int = 1,
    onValueChangeFinished: (() -> Unit)? = null
) {
    // 确保步长有效
    require(step > 0) { "Step must be positive." }
    require(!valueRange.isEmpty()) { "Value range must not be empty." }
    // Slider 内部使用 Float，进行转换
    val floatRange = remember(valueRange) {
        valueRange.first.toFloat()..valueRange.last.toFloat()
    }

    // 计算 Slider 的 steps 参数 (两个刻度之间的间隔数)
    // 如果步长为 1，则 steps = (总范围) - 1
    // 如果步长 > 1, steps = (总范围 / 步长) - 1 (整数除法可能不精确，需要小心)
    // 更精确的方式是计算有多少个步长间隔
    val stepsCount = if (valueRange.last > valueRange.first) {
        (valueRange.last - valueRange.first) / step
    } else {
        0
    }
    // steps 参数是间隔数，所以是步数 - 1。确保不为负。
    val sliderSteps = max(0, stepsCount - 1)

    Slider(
        value = value.toFloat(), // 将 Int 转换为 Float
        onValueChange = { newValueFloat ->
            // 将 Slider 返回的 Float 四舍五入为最接近的整数
            val newValueInt = newValueFloat.roundToInt()
            // 只有当计算出的整数值与当前值不同时才回调，避免不必要的回调
            if (newValueInt != value) {
                onValueChange(newValueInt)
            }
        },
        modifier = modifier,
        valueRange = floatRange, // 使用转换后的 Float 范围
        steps = sliderSteps,     // 使用计算出的步数间隔
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished
    )
}

private fun Context.hasNotificationPermission(): Boolean {
    // 低于 Android 13 的版本不需要运行时权限
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}

// 检查通知权限是否永久拒绝
fun isNotificationPermissionPermanentlyDenied(activity: Activity): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED
            && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
}

// 打开应用设置页面
private fun Context.openAppSettings() {
    val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", this@openAppSettings.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
