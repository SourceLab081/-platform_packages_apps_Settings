/*
 * SPDX-FileCopyrightText: 2024-2026 Lunaris AOSP
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.display.pulse

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment



class ColorPickerDialogFragment : DialogFragment() {

    private var initialColor: Color = Color(0xFF6750A4)
    private var onColorSelected: OnColorSelectedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Dialog_NoActionBar)
        
        arguments?.getString(ARG_COLOR_HEX)?.let { hex ->
            try {
                initialColor = Color(AndroidColor.parseColor("#$hex"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setContent {
                LunarisTheme {
                    ColorPickerDialog(
                        initialColor = initialColor,
                        onDismiss = { dismiss() },
                        onColorSelected = { color ->
                            onColorSelected?.onColorSelected(color.toArgb())
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    fun setOnColorSelectedListener(listener: OnColorSelectedListener) {
        onColorSelected = listener
    }

    fun interface OnColorSelectedListener {
        fun onColorSelected(colorArgb: Int)
    }

    companion object {
        private const val ARG_COLOR_HEX = "color_hex"
        const val TAG = "ColorPickerDialogFragment"

        @JvmStatic
        fun newInstance(colorHex: String): ColorPickerDialogFragment {
            return ColorPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_COLOR_HEX, colorHex)
                }
            }
        }
    }
}
