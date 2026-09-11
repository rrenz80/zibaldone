package it.zibaldone.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import it.zibaldone.app.ui.BoardScreen
import it.zibaldone.app.util.LocalePreference
import it.zibaldone.app.view.BoardViewModel

class MainActivity : ComponentActivity() {
    private val boardViewModel: BoardViewModel by viewModels()

    /**
     * Applies the in-app language choice before any resource is resolved.
     *
     * The board survives the `recreate()` that follows a language change:
     * the ViewModel is retained by the activity's ViewModelStore exactly
     * as it is across a configuration change.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePreference.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BoardScreen(viewModel = boardViewModel)
                }
            }
        }
    }
}
