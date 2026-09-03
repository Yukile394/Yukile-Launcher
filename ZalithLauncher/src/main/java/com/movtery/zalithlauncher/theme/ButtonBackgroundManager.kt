package com.movtery.zalithlauncher.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject

/** Bir butonun arka planı butonu tamamen mi kaplasın yoksa içine mi sığsın. */
enum class BackgroundFitMode { COVER, CONTAIN }

/**
 * Tek bir [ThemedButtonView] için kayıtlı özel arka plan yapılandırması.
 *
 * @param uri            Görsel veya GIF dosyasının content:// / file:// URI'si. null ise özel arka plan yok.
 * @param opacity        0f (tamamen saydam) .. 1f (tam opak)
 * @param fitMode        COVER: butonu tamamen kapla ve gerekirse kırp. CONTAIN: sığdır, taşırma.
 * @param offsetXPercent Görselin yatay konum kaydırması, -1f..1f (CONTAIN modunda anlamlı)
 * @param offsetYPercent Görselin dikey konum kaydırması, -1f..1f
 * @param scale          Ek ölçekleme çarpanı, 0.5f..2f arası önerilir
 */
data class ButtonBackgroundConfig(
    val uri: String? = null,
    val opacity: Float = 1f,
    val fitMode: BackgroundFitMode = BackgroundFitMode.COVER,
    val offsetXPercent: Float = 0f,
    val offsetYPercent: Float = 0f,
    val scale: Float = 1f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uri", uri)
        put("opacity", opacity)
        put("fitMode", fitMode.name)
        put("offsetX", offsetXPercent)
        put("offsetY", offsetYPercent)
        put("scale", scale)
    }

    companion object {
        val NONE = ButtonBackgroundConfig()

        fun fromJson(json: JSONObject): ButtonBackgroundConfig = ButtonBackgroundConfig(
            uri = json.optString("uri").takeIf { it.isNotBlank() && it != "null" },
            opacity = json.optDouble("opacity", 1.0).toFloat(),
            fitMode = runCatching { BackgroundFitMode.valueOf(json.optString("fitMode", "COVER")) }
                .getOrDefault(BackgroundFitMode.COVER),
            offsetXPercent = json.optDouble("offsetX", 0.0).toFloat(),
            offsetYPercent = json.optDouble("offsetY", 0.0).toFloat(),
            scale = json.optDouble("scale", 1.0).toFloat(),
        )
    }
}

/**
 * Her biri kendi `buttonKey`'i ile tanımlanan butonların özel arka plan
 * ayarlarını (GIF/görsel + opaklık + sığdırma + konum/ölçek) kalıcı olarak
 * saklar. Uygulama her açıldığında ThemedButtonView kendi anahtarıyla
 * buradan okuyup otomatik uygular.
 */
object ButtonBackgroundManager {

    private const val PREFS_NAME = "button_background_prefs"
    private lateinit var prefs: SharedPreferences
    private val cache = mutableMapOf<String, ButtonBackgroundConfig>()
    private val listeners = mutableMapOf<String, MutableList<(ButtonBackgroundConfig) -> Unit>>()
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        initialized = true
    }

    private fun requireInit() {
        check(initialized) { "ButtonBackgroundManager.init(context) çağrılmadan kullanılamaz." }
    }

    fun get(buttonKey: String): ButtonBackgroundConfig {
        requireInit()
        cache[buttonKey]?.let { return it }
        val raw = prefs.getString(buttonKey, null) ?: return ButtonBackgroundConfig.NONE
        val config = runCatching { ButtonBackgroundConfig.fromJson(JSONObject(raw)) }
            .getOrDefault(ButtonBackgroundConfig.NONE)
        cache[buttonKey] = config
        return config
    }

    fun set(buttonKey: String, config: ButtonBackgroundConfig) {
        requireInit()
        cache[buttonKey] = config
        prefs.edit { putString(buttonKey, config.toJson().toString()) }
        listeners[buttonKey]?.forEach { it(config) }
    }

    fun clear(buttonKey: String) = set(buttonKey, ButtonBackgroundConfig.NONE)

    fun addListener(buttonKey: String, listener: (ButtonBackgroundConfig) -> Unit) {
        listeners.getOrPut(buttonKey) { mutableListOf() }.add(listener)
    }

    fun removeListener(buttonKey: String, listener: (ButtonBackgroundConfig) -> Unit) {
        listeners[buttonKey]?.remove(listener)
    }

    /** Tüm kayıtlı buton anahtarlarını döner — ayarlar ekranındaki liste için. */
    fun allKeys(): Set<String> {
        requireInit()
        return prefs.all.keys
    }
}
