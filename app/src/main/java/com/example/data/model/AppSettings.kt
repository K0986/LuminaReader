package com.example.data.model

/**
 * Reading settings and reader personalization.
 */
data class AppSettings(
    val theme: ReaderTheme = ReaderTheme.SEPIA,
    val fontFamily: ReaderFont = ReaderFont.SERIF,
    val fontSizeSp: Float = 18f,
    val lineSpacingMultiplier: Float = 1.4f,
    val marginPaddingDp: Int = 16,
    val textAlignJustify: Boolean = true,
    val pageTurnAnimation: PageAnimation = PageAnimation.SLIDE,
    val readingMode: ReadingMode = ReadingMode.PAGINATED,
    val ttsVoice: String = "",
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val brightness: Float = -1f, // -1f = system default, 0.05 to 1.0
    val nightLightWarmth: Float = 0.0f, // 0.0 to 0.5 warm amber tint
    val keepScreenOn: Boolean = true,
    val volumeKeyPageTurn: Boolean = false,
    val twoPageSpreadTablet: Boolean = true,
    val appLockEnabled: Boolean = false,
    val appLockPin: String = "",
    val dailyReadingGoalMinutes: Int = 20
)

enum class ReaderTheme(val title: String, val bgHex: Long, val textHex: Long) {
    LIGHT("Light", 0xFFFAF8F5, 0xFF1A1A1A),
    SEPIA("Sepia", 0xFFF5EEDB, 0xFF3D2E1E),
    DARK("Night", 0xFF1E222A, 0xFFE2E4E9),
    OLED("OLED Black", 0xFF000000, 0xFFD1D5DB)
}

enum class ReaderFont(val displayName: String) {
    SERIF("Literata Serif"),
    SANS("Modern Sans"),
    DYSLEXIC("OpenDyslexic"),
    MONO("Monospace")
}

enum class PageAnimation(val displayName: String) {
    SLIDE("Slide"),
    FADE("Fade"),
    NONE("Instant")
}

enum class ReadingMode(val displayName: String) {
    PAGINATED("Paginated (Page Flip)"),
    CONTINUOUS_SCROLL("Continuous Scroll")
}
