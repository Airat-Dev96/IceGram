package com.exteragram.messenger.preferences

import android.view.View
import org.icegram.messenger.IcegramConfig
import org.icegram.messenger.UltraEconomyController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class UltraEconomyPreferencesActivity : BasePreferencesActivity() {
    private enum class Item {
        ENABLED;

        val id: Int = ordinal + 1
    }

    override fun getTitle(): String = getString(R.string.IcegramUltraEconomy)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(uCheck(Item.ENABLED, getTitle(), IcegramConfig.isUltraEconomyModeEnabled()))
        items.add(UItem.asShadow(getString(R.string.IcegramUltraEconomyInfo)))
        items.add(UItem.asHeader(getString(R.string.IcegramUltraOptions)))
        for (option in UltraEconomyController.Option.values()) {
            items.add(UItem.asCheck(100 + option.ordinal, getString(option.title), getString(option.description), true)
                .setChecked(UltraEconomyController.isSelected(option))
                .setEnabled(IcegramConfig.isUltraEconomyModeEnabled()))
        }
        items.add(UItem.asShadow(getString(R.string.IcegramUltraOptionsInfo)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id == Item.ENABLED.id) {
            IcegramConfig.setUltraEconomyModeEnabled(!IcegramConfig.isUltraEconomyModeEnabled())
        } else {
            val option = UltraEconomyController.Option.values().getOrNull(item.id - 100) ?: return
            if (!IcegramConfig.isUltraEconomyModeEnabled()) return
            UltraEconomyController.setSelected(option, !UltraEconomyController.isSelected(option))
        }
        listView.adapter?.update(false)
        parentLayout?.rebuildFragments(0)
    }
}
