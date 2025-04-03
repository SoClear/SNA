package cool.cmg.sna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import cool.cmg.sna.ui.MainScreen
import cool.cmg.sna.ui.theme.SNATheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        setContent {
            SNATheme {
                MainScreen()
            }
        }
        super.onCreate(savedInstanceState)
    }
}