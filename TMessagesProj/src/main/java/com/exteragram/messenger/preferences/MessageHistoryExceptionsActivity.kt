/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.preferences

import android.os.Bundle
import android.view.View
import org.icegram.messenger.IcegramConfig
import org.telegram.messenger.DialogObject
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.DialogsActivity
import java.util.ArrayList

class MessageHistoryExceptionsActivity : BasePreferencesActivity() {
    private val dialogs = ArrayList<Long>()

    override fun getTitle(): String = getString(R.string.IcegramHistoryExceptions)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        dialogs.clear()
        dialogs.addAll(IcegramConfig.getMessageHistoryExcludedDialogs(currentAccount).sorted())
        items.add(uButton(1, getString(R.string.IcegramHistoryExceptionAdd), R.drawable.msg_add))
        items.add(UItem.asShadow(getString(R.string.IcegramHistoryExceptionsInfo)))
        dialogs.forEachIndexed { index, dialogId ->
            items.add(uButton(100 + index,
                MessagesController.getInstance(currentAccount).getPeerName(dialogId),
                subtext = getString(R.string.IcegramHistoryExceptionResume)))
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
                        IcegramConfig.setMessageHistoryExcluded(currentAccount, key.dialogId, true)
                        MessagesStorage.getInstance(currentAccount).clearMessageHistoryForDialog(key.dialogId, null)
                    }
                }
                fragment.finishFragment()
                listView.adapter?.update(true)
                true
            }
            presentFragment(selector)
        } else if (item.id - 100 in dialogs.indices) {
            val dialogId = dialogs[item.id - 100]
            showDialog(AlertDialog.Builder(context, resourceProvider).apply {
                setTitle(getString(R.string.IcegramHistoryExceptionResume))
                setMessage(getString(R.string.IcegramHistoryExceptionResumeInfo))
                setNegativeButton(getString(R.string.Cancel), null)
                setPositiveButton(getString(R.string.OK)) { _, _ ->
                    IcegramConfig.setMessageHistoryExcluded(currentAccount, dialogId, false)
                    listView.adapter?.update(true)
                }
            }.create())
        }
    }
}
