package org.lsposed.lspatch.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.lsposed.lspatch.ui.theme.LSPTheme
import java.util.Locale

class LanguageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LSPTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LanguageSelectionScreen(onLanguageSelected = { languageCode ->
                        setLocale(languageCode)
                        restartApplication()
                    })
                }
            }
        }
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        resources.configuration.setLocale(locale)
        resources.updateConfiguration(resources.configuration, resources.displayMetrics)
    }

    private fun restartApplication() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }
}

@Composable
fun LanguageSelectionScreen(onLanguageSelected: (String) -> Unit) {
    val languages = listOf(
        Pair("zh-rCN", "简体中文"),
        Pair("zh-rTW", "正體中文"),
        Pair("zh-rHK", "繁體中文"),
        Pair("en", "English"),
        Pair("de", "Deutsch"),
        Pair("fr", "Français"),
        Pair("es", "Español"),
        Pair("it", "Italiano"),
        Pair("ru", "Русский"),
        Pair("pt", "Português"),
        Pair("ar", "العربية"),
        Pair("hi", "हिन्दी"),
        Pair("in", "Bahasa Indonesia"),
        Pair("vi", "Tiếng Việt"),
        Pair("tr", "Türkçe")
    )

@Composable
fun LanguageSelectionScreen(
    languages: List<Pair<String, String>>,
    onLanguageSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = stringResource(id = R.string.language),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn {
            items(languages) { (code, name) ->
                Text(
                    text = name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLanguageSelected(code) }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

