/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.preferences

import android.view.View
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class AyugramPreferencesActivity : BasePreferencesActivity() {
    override fun getTitle(): String = getString(R.string.AyugramSettings)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.AyugramSettings)))
        items.add(UItem.asShadow(getString(R.string.AyugramSettingsIntro)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) = Unit
}
