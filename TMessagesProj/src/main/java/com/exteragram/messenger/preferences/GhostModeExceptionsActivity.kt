/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.preferences

import android.os.Bundle
import android.view.View
import org.icegram.messenger.IcegramConfig
import org.telegram.messenger.DialogObject
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.DialogsActivity
import java.util.ArrayList

class GhostModeExceptionsActivity : BasePreferencesActivity() {
    private val dialogs = ArrayList<Long>()

    override fun getTitle(): String = getString(R.string.IcegramGhostExceptions)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        dialogs.clear()
        dialogs.addAll(IcegramConfig.getGhostModeExcludedDialogs(currentAccount).sorted())
        items.add(uButton(1, getString(R.string.IcegramGhostExceptionAdd), R.drawable.msg_add))
        items.add(UItem.asShadow(getString(R.string.IcegramGhostExceptionsInfo)))
        dialogs.forEachIndexed { index, dialogId ->
            items.add(uButton(100 + index,
                MessagesController.getInstance(currentAccount).getPeerName(dialogId),
                subtext = getString(R.string.IcegramGhostExceptionResume)))
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id == 1) {
            val selector = DialogsActivity(Bundle().apply {
                putBoolean("onlySelect", true)
                putBoolean("checkCanWrite", false)
            })
            selector.setDelegate { fragment, selected, _, _, _, _, _, _ ->
                selected.forEach { key ->
                    if (!DialogObject.isEncryptedDialog(key.dialogId)) {
                        IcegramConfig.setGhostModeExcluded(currentAccount, key.dialogId, true)
                    }
                }
                fragment.finishFragment()
                listView.adapter?.update(true)
                true
            }
            presentFragment(selector)
        } else if (item.id - 100 in dialogs.indices) {
            val dialogId = dialogs[item.id - 100]
            IcegramConfig.setGhostModeExcluded(currentAccount, dialogId, false)
            listView.adapter?.update(true)
        }
    }
}
