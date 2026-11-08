package com.hidsquid.refreshpaper.epd

import android.content.Context
import android.view.View
import android.widget.Toast
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.ui.ChoiceDialog
import com.hidsquid.refreshpaper.ui.ChoiceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class EpdModeDialogController(
    private val context: Context,
    private val epdController: EPDDisplayModeController,
    private val coroutineScope: CoroutineScope,
) {
    fun show(onSaved: () -> Unit) {
        val choiceDialog = ChoiceDialog(
            context,
            R.layout.dialog_epd_display_mode,
            listOf(
                ChoiceItem(EPDDisplayModeController.MODE_MINIMIZE_AFTERIMAGE, R.id.item1, R.id.check1),
                ChoiceItem(EPDDisplayModeController.MODE_NORMAL, R.id.item3, R.id.check3),
            ),
            uncheckedVisibility = View.GONE,
            bringCheckedToFront = true,
        )

        choiceDialog.dialog.setOnShowListener {
            coroutineScope.launch {
                val raw = epdController.getDisplayMode()
                val mode = epdController.normalize(raw)
                choiceDialog.setChecked(mode)
            }
        }

        choiceDialog.show { sysMode ->
            coroutineScope.launch {
                val ok = epdController.setDisplayMode(sysMode)
                if (!ok) {
                    Toast.makeText(
                        context,
                        "display_mode setting failed (system permission/allowlist check needed)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val raw = epdController.getDisplayMode()
                val modeNow = epdController.normalize(raw)
                setChecked(modeNow)
                onSaved()
                dismiss()
            }
        }
    }

    fun loadSelectedModeLabel(onLoaded: (String) -> Unit) {
        coroutineScope.launch {
            val raw = epdController.getDisplayMode()
            val mode = epdController.normalize(raw)
            onLoaded(
                context.getString(
                    if (mode == EPDDisplayModeController.MODE_MINIMIZE_AFTERIMAGE) {
                        R.string.setting_value_display_mode_minimize_afterimage
                    } else {
                        R.string.setting_value_display_mode_normal
                    }
                )
            )
        }
    }
}
