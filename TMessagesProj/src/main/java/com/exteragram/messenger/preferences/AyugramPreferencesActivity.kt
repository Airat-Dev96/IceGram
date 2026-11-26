/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.preferences

import android.view.View
import org.icegram.messenger.GhostModeController
import org.icegram.messenger.IcegramConfig
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class AyugramPreferencesActivity : BasePreferencesActivity() {
    private enum class Item {
        GHOST_MODE,
        MESSAGE_HISTORY_ENABLED,
        MESSAGE_HISTORY;

        val id: Int = ordinal + 1
    }

    override fun getTitle(): String = getString(R.string.AyugramSettings)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.AyugramPrivacy)))
        items.add(uCheck(Item.GHOST_MODE, getString(R.string.AyugramGhostMode),
            IcegramConfig.isGhostModeEnabled(), getString(R.string.AyugramGhostModeInfo)))
        items.add(uCheck(Item.MESSAGE_HISTORY_ENABLED, getString(R.string.AyugramMessageHistoryEnabled),
            IcegramConfig.isMessageHistoryEnabled(), getString(R.string.AyugramMessageHistoryEnabledInfo)))
        items.add(uButton(Item.MESSAGE_HISTORY, getString(R.string.AyugramMessageHistory),
            subtext = getString(R.string.AyugramMessageHistoryInfo)))
        items.add(UItem.asShadow(getString(R.string.AyugramSettingsIntro)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            Item.GHOST_MODE.id -> toggleBooleanSettingAndRefresh(item) { GhostModeController.setEnabled(it) }
            Item.MESSAGE_HISTORY_ENABLED.id -> toggleBooleanSettingAndRefresh(item) { IcegramConfig.setMessageHistoryEnabled(it) }
            Item.MESSAGE_HISTORY.id -> presentFragment(MessageHistoryActivity())
        }
    }
}
