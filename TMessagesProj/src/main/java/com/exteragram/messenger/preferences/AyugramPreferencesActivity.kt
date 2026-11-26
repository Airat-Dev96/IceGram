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
        HIDE_ONLINE,
        HIDE_TYPING,
        HIDE_READ_RECEIPTS
    }

    override fun getTitle(): String = getString(R.string.AyugramSettings)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.AyugramGhostMode)))
        items.add(UItem.asShadow(getString(R.string.AyugramGhostModeInfo)))
        items.add(uCheck(Item.HIDE_ONLINE, getString(R.string.IcegramHideOnline),
            IcegramConfig.isOnlineHidden(), getString(R.string.IcegramHideOnlineInfo)))
        items.add(uCheck(Item.HIDE_TYPING, getString(R.string.IcegramHideTyping),
            IcegramConfig.isTypingHidden(), getString(R.string.IcegramHideTypingInfo)))
        items.add(uCheck(Item.HIDE_READ_RECEIPTS, getString(R.string.IcegramHideReadReceipts),
            IcegramConfig.areReadReceiptsHidden(), getString(R.string.IcegramHideReadReceiptsInfo)))
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            Item.HIDE_ONLINE.ordinal + 1 -> toggleBooleanSettingAndRefresh(item) { GhostModeController.setOnlineHidden(it) }
            Item.HIDE_TYPING.ordinal + 1 -> toggleBooleanSettingAndRefresh(item) { GhostModeController.setTypingHidden(it) }
            Item.HIDE_READ_RECEIPTS.ordinal + 1 -> toggleBooleanSettingAndRefresh(item) { GhostModeController.setReadReceiptsHidden(it) }
        }
    }
}
