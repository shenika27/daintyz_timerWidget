package com.daintyz.timerwidget.ui

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.daintyz.timerwidget.data.TimerPreferences

object AppLanguage {
    const val KOREAN = "ko"
    const val ENGLISH = "en"

    fun applySaved(context: Context) {
        TimerPreferences.get(context).appLanguageTag()?.let { tag ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalize(tag)))
        }
    }

    fun currentLanguageTag(context: Context): String {
        TimerPreferences.get(context).appLanguageTag()?.let { return normalize(it) }
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return if (locale.language == ENGLISH) ENGLISH else KOREAN
    }

    fun settingTitle(context: Context): String =
        if (currentLanguageTag(context) == ENGLISH) "Language" else "언어설정"

    /** 우측 버튼에 표시할 현재 선택된 언어 라벨(각 언어의 자국어 표기). */
    fun currentLabel(context: Context): String =
        if (currentLanguageTag(context) == ENGLISH) "English" else "한국어"

    fun select(context: Context, languageTag: String) {
        val normalized = normalize(languageTag)
        TimerPreferences.get(context).setAppLanguageTag(normalized)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized))
    }

    private fun normalize(tag: String): String =
        if (tag.startsWith(ENGLISH, ignoreCase = true)) ENGLISH else KOREAN
}
