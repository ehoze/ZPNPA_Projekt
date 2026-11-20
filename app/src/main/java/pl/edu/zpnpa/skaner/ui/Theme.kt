package pl.edu.zpnpa.skaner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF265E51), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EDE3), onPrimaryContainer = Color(0xFF173D33),
    secondary = Color(0xFF53645D), background = Color(0xFFF8FAF7),
    surface = Color(0xFFF8FAF7), surfaceVariant = Color(0xFFE6ECE7),
    onSurface = Color(0xFF1B211D), onSurfaceVariant = Color(0xFF444E47),
    outline = Color(0xFF737D75), error = Color(0xFFBA1A1A),
)

@Composable
fun ScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
