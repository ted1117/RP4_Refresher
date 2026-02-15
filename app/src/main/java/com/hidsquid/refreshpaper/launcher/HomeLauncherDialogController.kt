package com.hidsquid.refreshpaper.launcher

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import com.hidsquid.refreshpaper.R
import com.hidsquid.refreshpaper.SettingsRepository

class HomeLauncherDialogController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private data class LauncherOption(
        val label: String,
        val component: ComponentName,
    ) {
        val flattenedComponent: String
            get() = component.flattenToString()

        val packageName: String
            get() = component.packageName
    }

    fun show(onSaved: () -> Unit) {
        val options = getLauncherOptions()
        if (options.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_home_launcher_no_apps), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedComponent = settingsRepository.getHomeLauncherComponent()
        val checkedIndex = options.indexOfFirst { it.flattenedComponent == selectedComponent }
        var selectedIndex = if (checkedIndex >= 0) checkedIndex else 0

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_home_launcher, null)
        val itemContainer = dialogView.findViewById<LinearLayout>(R.id.launcherItemContainer)
        val checkViews = mutableListOf<ImageView>()

        fun setChecked(index: Int) {
            checkViews.forEachIndexed { i, view ->
                view.visibility = if (i == index) View.VISIBLE else View.INVISIBLE
                if (i == index) view.bringToFront()
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        options.forEachIndexed { index, option ->
            val itemView =
                LayoutInflater.from(context).inflate(R.layout.dialog_home_launcher_item, itemContainer, false)
            val itemRoot = itemView.findViewById<RelativeLayout>(R.id.itemRoot)
            val itemTitle = itemView.findViewById<TextView>(R.id.itemTitle)
            val itemCheck = itemView.findViewById<ImageView>(R.id.itemCheck)
            val itemDivider = itemView.findViewById<View>(R.id.itemDivider)

            itemTitle.text = option.label
            itemDivider.visibility = if (index == options.lastIndex) View.GONE else View.VISIBLE
            checkViews += itemCheck

            itemRoot.setOnClickListener {
                selectedIndex = index
                setChecked(selectedIndex)

                val saved = settingsRepository.setHomeLauncherComponent(option.flattenedComponent)
                if (!saved) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_home_launcher_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                onSaved()
                dialog.dismiss()
            }

            itemContainer.addView(itemView)
        }

        setChecked(selectedIndex)
        dialog.show()
        val widthPx = context.resources.getDimensionPixelSize(R.dimen.dialog_width)
        dialog.window?.setLayout(widthPx, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun getSelectedLauncherLabel(): String {
        val selected = settingsRepository.getHomeLauncherComponent()
        if (selected == SettingsRepository.DEFAULT_HOME_LAUNCHER_COMPONENT) {
            return context.getString(R.string.setting_desc_home_launcher_default)
        }

        return getLauncherOptions().firstOrNull { it.flattenedComponent == selected }?.label
            ?: ComponentName.unflattenFromString(selected)?.packageName
            ?: context.getString(R.string.setting_desc_home_launcher_default)
    }

    private fun getLauncherOptions(): List<LauncherOption> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfos: List<ResolveInfo> = context.packageManager.queryIntentActivities(intent, 0)

        return resolveInfos
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val component = ComponentName(activityInfo.packageName, activityInfo.name)
                val label = try {
                    info.loadLabel(context.packageManager)?.toString()
                } catch (_: Throwable) {
                    null
                }.orEmpty().ifBlank { activityInfo.packageName }
                LauncherOption(label = label, component = component)
            }
            .filterNot {
                it.packageName == "com.ridi.paper" ||
                    it.packageName == "com.android.settings" ||
                    it.label.contains("settings", ignoreCase = true) ||
                    it.label.contains("설정")
            }
            .distinctBy { it.flattenedComponent }
            .sortedBy { it.label.lowercase() }
    }
}
