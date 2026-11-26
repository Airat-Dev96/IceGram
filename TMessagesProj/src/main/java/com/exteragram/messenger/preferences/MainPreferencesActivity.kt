/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.preferences

import android.view.View
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class MainPreferencesActivity : BasePreferencesActivity() {
    private enum class Item {
        EXTERAGRAM,
        AYUGRAM,
        ICEGRAM
    }

    override fun getTitle(): String = getString(R.string.Preferences)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.Categories)))
        items.add(uButton(Item.EXTERAGRAM, getString(R.string.ExteraGramSettings), R.drawable.extera_outline))
        items.add(UItem.asShadow())
        items.add(uButton(Item.AYUGRAM, getString(R.string.AyugramSettings), R.drawable.msg_settings))
        items.add(UItem.asShadow())
        items.add(uButton(Item.ICEGRAM, getString(R.string.IcegramSettings), R.drawable.msg_settings))
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            Item.EXTERAGRAM.ordinal + 1 -> presentFragment(ExteraGramPreferencesActivity())
            Item.AYUGRAM.ordinal + 1 -> presentFragment(AyugramPreferencesActivity())
            Item.ICEGRAM.ordinal + 1 -> presentFragment(IcegramFeaturesPreferencesActivity())
        }
    }
}
