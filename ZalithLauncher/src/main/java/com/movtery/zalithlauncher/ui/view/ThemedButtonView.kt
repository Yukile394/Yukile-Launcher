package com.movtery.zalithlauncher.ui.view

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.withStyledAttributes
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.theme.BackgroundFitMode
import com.movtery.zalithlauncher.theme.ButtonBackgroundConfig
import com.movtery.zalithlauncher.theme.ButtonBackgroundManager
import com.movtery.zalithlauncher.theme.ColorSet
import com.movtery.zalithlauncher.theme.ThemeManager

/**
 * Uygulama genelinde kullanılacak standart buton kabı.
 *
 * - Tema renklerini (arka plan / kenarlık / metin / seçili durum) [ThemeManager]'dan
 *   otomatik okur ve canlı olarak günceller.
 * - `buttonKey` verilirse [ButtonBackgroundManager]'da kayıtlı özel GIF/görsel
 *   arka planı otomatik yükler; opaklık, kaplama modu (COVER/CONTAIN), konum
 *   ve ölçek ayarlarına uyar.
 * - İçerik (ikon, metin) normal şekilde XML'de çocuk view olarak tanımlanır;
 *   bu sınıf sadece arka planı ve dış görünümü yönetir, içeriğe dokunmaz.
 *
 * XML kullanımı:
 *   <com.movtery.zalithlauncher.ui.view.ThemedButtonView
 *       android:layout_width="wrap_content"
 *       android:layout_height="56dp"
 *       app:buttonKey="home_play_button"
 *       app:cornerRadiusDp="16"
 *       app:supportsSelectedState="true">
 *       <TextView .../>
 *   </com.movtery.zalithlauncher.ui.view.ThemedButtonView>
 */
class ThemedButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var buttonKey: String? = null
    private var supportsSelectedState: Boolean = false
    private var cornerRadiusDp: Float = 12f
    private var isSelected2 = false

    private val backgroundImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val colorOverlay = ColorDrawable()

    private val themeListener: (ColorSet) -> Unit = { applyThemeColors(it) }
    private var backgroundListener: ((ButtonBackgroundConfig) -> Unit)? = null

    init {
        context.withStyledAttributes(attrs, R.styleable.ThemedButtonView) {
            buttonKey = getString(R.styleable.ThemedButtonView_buttonKey)
            supportsSelectedState = getBoolean(R.styleable.ThemedButtonView_supportsSelectedState, false)
            cornerRadiusDp = getFloat(R.styleable.ThemedButtonView_cornerRadiusDp, 12f)
        }

        // Arka plan görseli her zaman en altta, içerik view'ları XML sırasına göre üstünde durur.
        addView(backgroundImageView, 0)
        this.background = colorOverlay

        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radiusPx = cornerRadiusDp * resources.displayMetrics.density
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }

        isClickable = true
        isFocusable = true

        applyThemeColors(ThemeManager.current)
        buttonKey?.let { key ->
            applyBackgroundConfig(ButtonBackgroundManager.get(key))
            val listener: (ButtonBackgroundConfig) -> Unit = { applyBackgroundConfig(it) }
            backgroundListener = listener
            ButtonBackgroundManager.addListener(key, listener)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeManager.addListener(themeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ThemeManager.removeListener(themeListener)
        backgroundListener?.let { buttonKey?.let { key -> ButtonBackgroundManager.removeListener(key, it) } }
    }

    /** Bu butonu programatik olarak "seçili sekme" durumuna al/çıkar. */
    fun setButtonSelected(selected: Boolean) {
        isSelected2 = selected
        applyThemeColors(ThemeManager.current)
    }

    private fun applyThemeColors(set: ColorSet) {
        colorOverlay.color = when {
            isSelected2 && supportsSelectedState -> set.selectedColor
            else -> set.buttonColor
        }
        // Kenarlık: StateListAnimator yerine basit elevation + tint tercih edildi,
        // ince bir kenarlık ImageView'in üstüne çizilmek isteniyorsa
        // GradientDrawable + setStroke burada eklenebilir (ekran bazlı redesign adımında).
    }

    private fun applyBackgroundConfig(config: ButtonBackgroundConfig) {
        if (config.uri == null) {
            backgroundImageView.setImageDrawable(null)
            backgroundImageView.alpha = 0f
            return
        }
        backgroundImageView.scaleType = when (config.fitMode) {
            BackgroundFitMode.COVER -> ImageView.ScaleType.CENTER_CROP
            BackgroundFitMode.CONTAIN -> ImageView.ScaleType.FIT_CENTER
        }
        backgroundImageView.alpha = config.opacity.coerceIn(0f, 1f)
        backgroundImageView.scaleX = config.scale
        backgroundImageView.scaleY = config.scale
        backgroundImageView.translationX = config.offsetXPercent * width
        backgroundImageView.translationY = config.offsetYPercent * height

        Glide.with(context)
            .load(config.uri)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(backgroundImageView)
    }
}
