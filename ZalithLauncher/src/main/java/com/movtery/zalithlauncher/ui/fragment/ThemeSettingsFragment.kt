package com.movtery.zalithlauncher.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.theme.BackgroundFitMode
import com.movtery.zalithlauncher.theme.ButtonBackgroundConfig
import com.movtery.zalithlauncher.theme.ButtonBackgroundManager
import com.movtery.zalithlauncher.theme.ColorSet
import com.movtery.zalithlauncher.theme.ThemeManager
import net.kdt.pojavlaunch.colorselector.ColorSelector

/**
 * Ana arayüz ve oyun-içi butonların 9 renk kanalını, ve OYNA butonunun
 * özel GIF/foto arka planını (opaklık + kaplama modu) düzenleyen ekran.
 */
class ThemeSettingsFragment : BaseFragment(R.layout.fragment_theme_settings) {

    companion object {
        const val TAG = "ThemeSettingsFragment"
        private const val PLAY_BUTTON_KEY = "play_button"
    }

    private var editingInGame = false
    private var currentFitMode = BackgroundFitMode.COVER
    private lateinit var rowsContainer: LinearLayout
    private lateinit var playBgPreview: ImageView

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onImagePicked(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rowsContainer = view.findViewById(R.id.color_rows_container)
        playBgPreview = view.findViewById(R.id.play_bg_preview)

        val modeMainButton = view.findViewById<TextView>(R.id.mode_main_button)
        val modeInGameButton = view.findViewById<TextView>(R.id.mode_ingame_button)

        fun refreshModeButtons() {
            modeMainButton.setBackgroundResource(if (!editingInGame) R.drawable.background_item_selected else android.R.color.transparent)
            modeInGameButton.setBackgroundResource(if (editingInGame) R.drawable.background_item_selected else android.R.color.transparent)
            rebuildColorRows()
        }

        modeMainButton.setOnClickListener {
            editingInGame = false
            refreshModeButtons()
        }
        modeInGameButton.setOnClickListener {
            editingInGame = true
            refreshModeButtons()
        }

        view.findViewById<View>(R.id.reset_theme_button).setOnClickListener {
            if (editingInGame) ThemeManager.resetInGameToDefault() else ThemeManager.resetMainToDefault()
            rebuildColorRows()
        }

        view.findViewById<View>(R.id.pick_play_bg_button).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        val fitModeButton = view.findViewById<TextView>(R.id.fit_mode_button)
        fitModeButton.setOnClickListener {
            currentFitMode = if (currentFitMode == BackgroundFitMode.COVER) BackgroundFitMode.CONTAIN else BackgroundFitMode.COVER
            fitModeButton.setText(if (currentFitMode == BackgroundFitMode.COVER) R.string.theme_settings_fit_cover else R.string.theme_settings_fit_contain)
            saveCurrentPlayButtonConfig()
        }

        view.findViewById<View>(R.id.clear_play_bg_button).setOnClickListener {
            ButtonBackgroundManager.clear(PLAY_BUTTON_KEY)
            playBgPreview.setImageDrawable(null)
        }

        view.findViewById<SeekBar>(R.id.play_bg_opacity_seek).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playBgPreview.alpha = progress / 100f
                    saveCurrentPlayButtonConfig()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        loadExistingPlayButtonConfig()
        refreshModeButtons()
    }

    // --- Renk satırları ---

    private fun rebuildColorRows() {
        rowsContainer.removeAllViews()
        val set = if (editingInGame) ThemeManager.currentInGame else ThemeManager.current

        addColorRow(getString(R.string.theme_color_primary), set.primary) { newColor ->
            applyChange { it.copy(primary = newColor) }
        }
        addColorRow(getString(R.string.theme_color_secondary), set.secondary) { newColor ->
            applyChange { it.copy(secondary = newColor) }
        }
        addColorRow(getString(R.string.theme_color_button), set.buttonColor) { newColor ->
            applyChange { it.copy(buttonColor = newColor) }
        }
        addColorRow(getString(R.string.theme_color_button_text), set.buttonTextColor) { newColor ->
            applyChange { it.copy(buttonTextColor = newColor) }
        }
        addColorRow(getString(R.string.theme_color_border), set.borderColor) { newColor ->
            applyChange { it.copy(borderColor = newColor) }
        }
        addColorRow(getString(R.string.theme_color_background), set.backgroundColor) { newColor ->
            applyChange { it.copy(backgroundColor = newColor) }
        }
        addColorRow(getString(R.string.theme_color_accent), set.accentColor) { newColor ->
            applyChange { it.copy(accentColor = newColor) }
        }
        addColorRow(getString(R.string.theme_color_hover), set.hoverColor) { newColor ->
            applyChange { it.copy(hoverColor = newColor) }
        }
        addColorRow(getString(R.string.theme_color_selected), set.selectedColor) { newColor ->
            applyChange { it.copy(selectedColor = newColor) }
        }
    }

    private fun applyChange(mutator: (ColorSet) -> ColorSet) {
        if (editingInGame) ThemeManager.updateInGame(mutator) else ThemeManager.update(mutator)
    }

    private fun addColorRow(label: String, color: Int, onPicked: (Int) -> Unit) {
        val context = requireContext()
        val density = resources.displayMetrics.density

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val labelView = TextView(context).apply {
            text = label
            setTextColor(resources.getColor(R.color.text_primary, null))
            textSize = 12.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val swatch = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
                setStroke((1.4f * density).toInt(), resources.getColor(R.color.surface_border, null))
            }
        }

        var currentColor = color
        row.setOnClickListener {
            showColorPickerDialog(currentColor) { picked ->
                currentColor = picked
                (swatch.background as android.graphics.drawable.GradientDrawable).setColor(picked)
                onPicked(picked)
            }
        }

        row.addView(labelView)
        row.addView(swatch)
        rowsContainer.addView(row)

        rowsContainer.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(resources.getColor(R.color.surface_border_subtle, null))
        })
    }

    private fun showColorPickerDialog(initialColor: Int, onConfirmed: (Int) -> Unit) {
        val context = requireContext()
        val container = android.widget.FrameLayout(context)
        val selector = ColorSelector(context, container, null)
        selector.show(initialColor)

        var pickedColor = initialColor
        selector.setColorSelectionListener { pickedColor = it }

        AlertDialog.Builder(context)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ -> onConfirmed(pickedColor) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- OYNA butonu arka planı ---

    private fun loadExistingPlayButtonConfig() {
        val config = ButtonBackgroundManager.get(PLAY_BUTTON_KEY)
        currentFitMode = config.fitMode
        view?.findViewById<TextView>(R.id.fit_mode_button)?.setText(
            if (currentFitMode == BackgroundFitMode.COVER) R.string.theme_settings_fit_cover else R.string.theme_settings_fit_contain
        )
        view?.findViewById<SeekBar>(R.id.play_bg_opacity_seek)?.progress = (config.opacity * 100).toInt()
        playBgPreview.alpha = config.opacity
        config.uri?.let {
            Glide.with(this).load(Uri.parse(it)).transition(DrawableTransitionOptions.withCrossFade()).into(playBgPreview)
        }
    }

    private fun onImagePicked(uri: Uri) {
        requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Glide.with(this).load(uri).transition(DrawableTransitionOptions.withCrossFade()).into(playBgPreview)
        saveCurrentPlayButtonConfig(uri.toString())
    }

    private fun saveCurrentPlayButtonConfig(uriOverride: String? = null) {
        val existing = ButtonBackgroundManager.get(PLAY_BUTTON_KEY)
        val opacity = (view?.findViewById<SeekBar>(R.id.play_bg_opacity_seek)?.progress ?: 100) / 100f
        ButtonBackgroundManager.set(
            PLAY_BUTTON_KEY,
            ButtonBackgroundConfig(
                uri = uriOverride ?: existing.uri,
                opacity = opacity,
                fitMode = currentFitMode,
            )
        )
    }
}

