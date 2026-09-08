package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppSettings
import com.example.data.model.PageAnimation
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderTheme
import com.example.data.model.ReadingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lumina_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val themeName = prefs.getString("theme", ReaderTheme.SEPIA.name) ?: ReaderTheme.SEPIA.name
        val fontName = prefs.getString("font", ReaderFont.SERIF.name) ?: ReaderFont.SERIF.name
        val animName = prefs.getString("animation", PageAnimation.SLIDE.name) ?: PageAnimation.SLIDE.name
        val modeName = prefs.getString("reading_mode", ReadingMode.PAGINATED.name) ?: ReadingMode.PAGINATED.name

        return AppSettings(
            theme = try { ReaderTheme.valueOf(themeName) } catch (e: Exception) { ReaderTheme.SEPIA },
            fontFamily = try { ReaderFont.valueOf(fontName) } catch (e: Exception) { ReaderFont.SERIF },
            fontSizeSp = prefs.getFloat("font_size", 18f),
            lineSpacingMultiplier = prefs.getFloat("line_spacing", 1.4f),
            marginPaddingDp = prefs.getInt("margin_padding", 16),
            textAlignJustify = prefs.getBoolean("text_align_justify", true),
            pageTurnAnimation = try { PageAnimation.valueOf(animName) } catch (e: Exception) { PageAnimation.SLIDE },
            readingMode = try { ReadingMode.valueOf(modeName) } catch (e: Exception) { ReadingMode.PAGINATED },
            ttsVoice = prefs.getString("tts_voice", "") ?: "",
            ttsSpeed = prefs.getFloat("tts_speed", 1.0f),
            ttsPitch = prefs.getFloat("tts_pitch", 1.0f),
            brightness = prefs.getFloat("brightness", -1f),
            nightLightWarmth = prefs.getFloat("night_light_warmth", 0.0f),
            keepScreenOn = prefs.getBoolean("keep_screen_on", true),
            volumeKeyPageTurn = prefs.getBoolean("volume_key_page_turn", false),
            twoPageSpreadTablet = prefs.getBoolean("two_page_spread", true),
            appLockEnabled = prefs.getBoolean("app_lock_enabled", false),
            appLockPin = prefs.getString("app_lock_pin", "") ?: "",
            dailyReadingGoalMinutes = prefs.getInt("reading_goal_minutes", 20)
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        prefs.edit().apply {
            putString("theme", newSettings.theme.name)
            putString("font", newSettings.fontFamily.name)
            putFloat("font_size", newSettings.fontSizeSp)
            putFloat("line_spacing", newSettings.lineSpacingMultiplier)
            putInt("margin_padding", newSettings.marginPaddingDp)
            putBoolean("text_align_justify", newSettings.textAlignJustify)
            putString("animation", newSettings.pageTurnAnimation.name)
            putString("reading_mode", newSettings.readingMode.name)
            putString("tts_voice", newSettings.ttsVoice)
            putFloat("tts_speed", newSettings.ttsSpeed)
            putFloat("tts_pitch", newSettings.ttsPitch)
            putFloat("brightness", newSettings.brightness)
            putFloat("night_light_warmth", newSettings.nightLightWarmth)
            putBoolean("keep_screen_on", newSettings.keepScreenOn)
            putBoolean("volume_key_page_turn", newSettings.volumeKeyPageTurn)
            putBoolean("two_page_spread", newSettings.twoPageSpreadTablet)
            putBoolean("app_lock_enabled", newSettings.appLockEnabled)
            putString("app_lock_pin", newSettings.appLockPin)
            putInt("reading_goal_minutes", newSettings.dailyReadingGoalMinutes)
            apply()
        }
    }
}
