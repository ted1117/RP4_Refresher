package com.hidsquid.refreshpaper.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes

/** A choice row already defined in the dialog's XML layout. */
data class ChoiceItem<T>(val value: T, @IdRes val itemId: Int, @IdRes val checkId: Int)

/** Wires an XML single-choice dialog: check marks and click handling. The layout stays in XML. */
class ChoiceDialog<T>(
    context: Context,
    @LayoutRes layoutRes: Int,
    private val items: List<ChoiceItem<T>>,
    private val uncheckedVisibility: Int = View.INVISIBLE,
    private val bringCheckedToFront: Boolean = false,
) {
    /** Inflated layout; callers may adjust it (e.g. set a title) before [show]. */
    val view: View = LayoutInflater.from(context).inflate(layoutRes, null)

    val dialog: AlertDialog = AlertDialog.Builder(context)
        .setView(view)
        .setCancelable(true)
        .create()

    private val checkViews = items.map { view.findViewById<ImageView>(it.checkId) }

    /** Shows the check for [value] and hides the others; null clears all. */
    fun setChecked(value: T?) {
        items.forEachIndexed { index, item ->
            val isSelected = item.value == value
            val check = checkViews[index]
            check.visibility = if (isSelected) View.VISIBLE else uncheckedVisibility
            if (isSelected && bringCheckedToFront) check.bringToFront()
        }
    }

    fun dismiss() = dialog.dismiss()

    /** Wires row clicks to [onSelect] and shows the dialog. */
    fun show(onSelect: ChoiceDialog<T>.(T) -> Unit) {
        items.forEach { item ->
            view.findViewById<View>(item.itemId).setOnClickListener { onSelect(item.value) }
        }
        dialog.show()
    }
}
