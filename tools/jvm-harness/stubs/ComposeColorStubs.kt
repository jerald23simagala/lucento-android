// JVM-harness stand-ins for the small part of Compose used by theme/Color.kt (Color, @Immutable, Material 3
// colour schemes), so the palette and ThemeContrastTest compile and run without the Android SDK. Not shipped.
package androidx.compose.ui.graphics

@JvmInline
value class Color(val argb: Long) {
    val alpha: Float get() = ((argb shr 24) and 0xFF) / 255f
    val red: Float get() = ((argb shr 16) and 0xFF) / 255f
    val green: Float get() = ((argb shr 8) and 0xFF) / 255f
    val blue: Float get() = (argb and 0xFF) / 255f

    companion object {
        val White = Color(0xFFFFFFFF)
        val Black = Color(0xFF000000)
    }
}
