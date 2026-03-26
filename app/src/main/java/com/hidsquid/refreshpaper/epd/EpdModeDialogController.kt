package com.hidsquid.refreshpaper.epd

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import com.hidsquid.refreshpaper.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class EpdModeDialogController(
    private val context: Context,
    private val epdController: EPDDisplayModeController,
    private val coroutineScope: CoroutineScope,
) {
    fun show(onSaved: () -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_epd_display_mode, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val itemMin = dialogView.findViewById<RelativeLayout>(R.id.item1)
        val itemNormal = dialogView.findViewById<RelativeLayout>(R.id.item3)
        val checkMin = dialogView.findViewById<ImageView>(R.id.check1)
        val checkNormal = dialogView.findViewById<ImageView>(R.id.check3)

        fun setChecked(mode: Int) {
            checkMin.visibility =
                if (mode == EPDDisplayModeController.MODE_MINIMIZE_AFTERIMAGE) View.VISIBLE else View.GONE
            checkNormal.visibility =
                if (mode == EPDDisplayModeController.MODE_NORMAL) View.VISIBLE else View.GONE
            checkMin.bringToFront()
            checkNormal.bringToFront()
        }

        dialog.setOnShowListener {
            coroutineScope.launch {
                val raw = epdController.getDisplayMode()
                val mode = epdController.normalize(raw)
                setChecked(mode)
            }
        }

        val onClick = View.OnClickListener { view ->
            val sysMode = when (view.id) {
                R.id.item1 -> EPDDisplayModeController.MODE_MINIMIZE_AFTERIMAGE
                R.id.item3 -> EPDDisplayModeController.MODE_NORMAL
                else -> EPDDisplayModeController.MODE_NORMAL
            }

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
                dialog.dismiss()
            }
        }

        itemMin.setOnClickListener(onClick)
        itemNormal.setOnClickListener(onClick)

        dialog.show()
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
