package com.lifeline.app.translate

import com.lifeline.app.assistant.AiModel

/** A language the translator offers. [tag] is a BCP-47 tag used for speech and voices. */
data class Language(val tag: String, val name: String, val nativeName: String) {
    val label: String get() = if (name == nativeName) name else "$nativeName · $name"
}

object Languages {
    val all = listOf(
        Language("en-US", "English", "English"),
        Language("es-ES", "Spanish", "Español"),
        Language("fr-FR", "French", "Français"),
        Language("de-DE", "German", "Deutsch"),
        Language("ar", "Arabic", "العربية"),
        Language("zh-CN", "Chinese", "中文"),
        Language("ja-JP", "Japanese", "日本語"),
        Language("ko-KR", "Korean", "한국어"),
        Language("pt-BR", "Portuguese", "Português"),
        Language("it-IT", "Italian", "Italiano"),
        Language("ru-RU", "Russian", "Русский"),
        Language("uk-UA", "Ukrainian", "Українська"),
        Language("tr-TR", "Turkish", "Türkçe"),
        Language("hi-IN", "Hindi", "हिन्दी"),
        Language("bn-BD", "Bengali", "বাংলা"),
        Language("ur-PK", "Urdu", "اردو"),
        Language("ne-NP", "Nepali", "नेपाली"),
        Language("fa-IR", "Persian", "فارسی"),
        Language("id-ID", "Indonesian", "Bahasa Indonesia"),
        Language("vi-VN", "Vietnamese", "Tiếng Việt"),
        Language("th-TH", "Thai", "ไทย"),
        Language("fil-PH", "Filipino", "Filipino"),
        Language("sw-KE", "Swahili", "Kiswahili"),
    )

    /** Languages the small LFM 2.5 model was trained on; Gemma 4 covers all of [all]. */
    private val lfmTags = setOf("en-US", "ar", "zh-CN", "fr-FR", "de-DE", "ja-JP", "ko-KR", "es-ES")

    fun byTag(tag: String?): Language? = all.firstOrNull { it.tag == tag }

    /** True when [model] translates [language] reliably. */
    fun isWellSupported(model: AiModel?, language: Language): Boolean = when {
        model == null -> false
        model.id.startsWith("lfm") -> language.tag in lfmTags
        else -> true
    }
}
