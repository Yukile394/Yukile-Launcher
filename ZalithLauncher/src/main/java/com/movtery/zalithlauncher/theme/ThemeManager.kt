package com.movtery.zalithlauncher.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Kullanıcının seçtiği tüm arayüz renklerini kalıcı olarak saklar ve
 * uygulama genelinde tek referans noktası olarak sunar.
 *
 * Ana arayüz renkleri ve oyun-içi buton renkleri AYRI iki [ColorSet]
 * olarak tutulur — biri değişince diğeri etkilenmez (istenen davranış).
 *
 * Kullanım:
 *   ThemeManager.init(context)               // Application.onCreate() içinde bir kez
 *   ThemeManager.current.accentPrimary        // okuma
 *   ThemeManager.update { it.copy(accentPrimary = newColor) }   // yazma + kalıcı kayıt
 *   ThemeManager.addListener { colorSet -> ... }                // canlı UI güncellemesi
 */
object ThemeManager {

    private const val PREFS_NAME = "theme_prefs"

    private const val KEY_PREFIX_MAIN = "main_"
    private const val KEY_PREFIX_INGAME = "ingame_"

    private lateinit var prefs: SharedPreferences

    /** Ana arayüz için o an aktif renk seti. */
    var current: ColorSet = ColorSet.defaultMain()
        private set

    /** Oyun-içi HUD/buton renkleri için o an aktif renk seti (ana temadan bağımsız). */
    var currentInGame: ColorSet = ColorSet.defaultInGame()
        private set

    private val listeners = mutableListOf<(ColorSet) -> Unit>()
    private val inGameListeners = mutableListOf<(ColorSet) -> Unit>()

    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        current = loadColorSet(KEY_PREFIX_MAIN, ColorSet.defaultMain())
        currentInGame = loadColorSet(KEY_PREFIX_INGAME, ColorSet.defaultInGame())
        initialized = true
    }

    private fun requireInit() {
        check(initialized) { "ThemeManager.init(context) çağrılmadan kullanılamaz." }
    }

    // --- Ana arayüz teması ---

    fun update(mutator: (ColorSet) -> ColorSet) {
        requireInit()
        current = mutator(current)
        saveColorSet(KEY_PREFIX_MAIN, current)
        listeners.forEach { it(current) }
    }

    fun addListener(listener: (ColorSet) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ColorSet) -> Unit) {
        listeners.remove(listener)
    }

    fun resetMainToDefault() {
        requireInit()
        current = ColorSet.defaultMain()
        saveColorSet(KEY_PREFIX_MAIN, current)
        listeners.forEach { it(current) }
    }

    // --- Oyun-içi tema ---

    fun updateInGame(mutator: (ColorSet) -> ColorSet) {
        requireInit()
        currentInGame = mutator(currentInGame)
        saveColorSet(KEY_PREFIX_INGAME, currentInGame)
        inGameListeners.forEach { it(currentInGame) }
    }

    fun addInGameListener(listener: (ColorSet) -> Unit) {
        inGameListeners.add(listener)
    }

    fun removeInGameListener(listener: (ColorSet) -> Unit) {
        inGameListeners.remove(listener)
    }

    fun resetInGameToDefault() {
        requireInit()
        currentInGame = ColorSet.defaultInGame()
        saveColorSet(KEY_PREFIX_INGAME, currentInGame)
        inGameListeners.forEach { it(currentInGame) }
    }

    // --- Kalıcılık ---

    private fun loadColorSet(prefix: String, fallback: ColorSet): ColorSet {
        if (!prefs.contains(prefix + "primary")) return fallback
        return ColorSet(
            primary = prefs.getInt(prefix + "primary", fallback.primary),
            secondary = prefs.getInt(prefix + "secondary", fallback.secondary),
            buttonColor = prefs.getInt(prefix + "buttonColor", fallback.buttonColor),
            buttonTextColor = prefs.getInt(prefix + "buttonTextColor", fallback.buttonTextColor),
            borderColor = prefs.getInt(prefix + "borderColor", fallback.borderColor),
            backgroundColor = prefs.getInt(prefix + "backgroundColor", fallback.backgroundColor),
            accentColor = prefs.getInt(prefix + "accentColor", fallback.accentColor),
            hoverColor = prefs.getInt(prefix + "hoverColor", fallback.hoverColor),
            selectedColor = prefs.getInt(prefix + "selectedColor", fallback.selectedColor),
        )
    }

    private fun saveColorSet(prefix: String, set: ColorSet) {
        prefs.edit {
            putInt(prefix + "primary", set.primary)
            putInt(prefix + "secondary", set.secondary)
            putInt(prefix + "buttonColor", set.buttonColor)
            putInt(prefix + "buttonTextColor", set.buttonTextColor)
            putInt(prefix + "borderColor", set.borderColor)
            putInt(prefix + "backgroundColor", set.backgroundColor)
            putInt(prefix + "accentColor", set.accentColor)
            putInt(prefix + "hoverColor", set.hoverColor)
            putInt(prefix + "selectedColor", set.selectedColor)
        }
    }
}

/**
 * İstenen 9 özelleştirilebilir renk kanalını tutan değişmez veri sınıfı.
 * "Ana renk / ikincil renk / buton rengi / buton yazı rengi / kenarlık rengi /
 *  arka plan rengi / vurgu rengi / hover-dokunma rengi / seçili buton rengi"
 */
data class ColorSet(
    val primary: Int,
    val secondary: Int,
    val buttonColor: Int,
    val buttonTextColor: Int,
    val borderColor: Int,
    val backgroundColor: Int,
    val accentColor: Int,
    val hoverColor: Int,
    val selectedColor: Int,
) {
    companion object {
        fun defaultMain() = ColorSet(
            primary = 0xFF5B8DEF.toInt(),
            secondary = 0xFFEFA65B.toInt(),
            buttonColor = 0xFF171B21.toInt(),
            buttonTextColor = 0xFFF4F5F7.toInt(),
            borderColor = 0xFF2A3039.toInt(),
            backgroundColor = 0xFF0B0D10.toInt(),
            accentColor = 0xFF5B8DEF.toInt(),
            hoverColor = 0xFF1D222A.toInt(),
            selectedColor = 0xFF5B8DEF.toInt(),
        )

        /** Oyun-içi butonlar için varsayılan: yarı saydam, HUD üstünde okunaklı. */
        fun defaultInGame() = ColorSet(
            primary = 0x66000000,
            secondary = 0x66000000,
            buttonColor = 0x66000000,
            buttonTextColor = 0xFFFFFFFF.toInt(),
            borderColor = 0x33FFFFFF,
            backgroundColor = 0x00000000,
            accentColor = 0xFF5B8DEF.toInt(),
            hoverColor = 0x99000000.toInt(),
            selectedColor = 0xFF5B8DEF.toInt(),
        )
    }
}
