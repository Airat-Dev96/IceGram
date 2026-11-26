/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.preferences

import android.view.View
import org.icegram.messenger.MessageHistoryEntry
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class MessageHistoryActivity : BasePreferencesActivity() {
    private enum class Item {
        PREVIOUS,
        NEXT,
        CLEAR;

        val id: Int = ordinal + 1
    }

    private companion object {
        const val PAGE_SIZE = 25
        const val ENTRY_ID_START = 100
    }

    private val entries = ArrayList<MessageHistoryEntry>()
    private var page = 0
    private var hasNext = false
    private var loading = false

    override fun getTitle(): String = getString(R.string.AyugramMessageHistory)

    override fun onResume() {
        super.onResume()
        loadPage()
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.AyugramMessageHistory)))
        if (loading) {
            items.add(UItem.asShadow(getString(R.string.AyugramMessageHistoryLoading)))
            return
        }
        if (entries.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.AyugramMessageHistoryEmpty)))
        } else {
            entries.forEachIndexed { index, entry ->
                val type = if (entry.type == MessageHistoryEntry.TYPE_EDIT) {
                    getString(R.string.AyugramMessageHistoryEdited)
                } else {
                    getString(R.string.AyugramMessageHistoryDeleted)
                }
                val peer = MessagesController.getInstance(currentAccount).getPeerName(entry.dialogId)
                val title = LocaleController.formatString(R.string.AyugramMessageHistoryEntryTitle, type, peer)
                val subtitle = LocaleController.formatString(
                    R.string.AyugramMessageHistoryEntryInfo,
                    entry.messageId,
                    LocaleController.formatDateTime(entry.changedAt.toLong(), true)
                )
                items.add(uButton(ENTRY_ID_START + index, title, subtext = subtitle))
            }
        }

        if (page > 0) {
            items.add(uButton(Item.PREVIOUS, getString(R.string.AyugramMessageHistoryPrevious)))
        }
        if (hasNext) {
            items.add(uButton(Item.NEXT, getString(R.string.AyugramMessageHistoryNext)))
        }
        if (page > 0 || !entries.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.formatString(R.string.AyugramMessageHistoryPage, page + 1)))
            items.add(uButton(Item.CLEAR, getString(R.string.AyugramMessageHistoryClear), red = true))
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when {
            item.id == Item.PREVIOUS.id && page > 0 -> {
                page--
                loadPage()
            }
            item.id == Item.NEXT.id && hasNext -> {
                page++
                loadPage()
            }
            item.id == Item.CLEAR.id -> showClearConfirmation()
            item.id >= ENTRY_ID_START -> {
                val index = item.id - ENTRY_ID_START
                if (index in entries.indices) {
                    showEntry(entries[index])
                }
            }
        }
    }

    private fun loadPage() {
        if (loading) return
        loading = true
        listView.adapter?.update(true)
        MessagesStorage.getInstance(currentAccount).loadMessageHistoryPage(page * PAGE_SIZE, PAGE_SIZE + 1) { result ->
            entries.clear()
            entries.addAll(result.take(PAGE_SIZE))
            hasNext = result.size > PAGE_SIZE
            loading = false
            listView.adapter?.update(true)
        }
    }

    private fun showEntry(entry: MessageHistoryEntry) {
        val type = if (entry.type == MessageHistoryEntry.TYPE_EDIT) {
            getString(R.string.AyugramMessageHistoryEdited)
        } else {
            getString(R.string.AyugramMessageHistoryDeleted)
        }
        val peer = MessagesController.getInstance(currentAccount).getPeerName(entry.dialogId)
        val details = LocaleController.formatString(
            R.string.AyugramMessageHistoryEntryDetails,
            type,
            peer,
            entry.messageId,
            LocaleController.formatDateTime(entry.messageDate.toLong(), true),
            LocaleController.formatDateTime(entry.changedAt.toLong(), true),
            entry.text
        )
        showDialog(AlertDialog.Builder(context, resourceProvider).apply {
            setTitle(getString(R.string.AyugramMessageHistoryDetails))
            setMessage(details)
            setPositiveButton(getString(R.string.AyugramMessageHistoryClose), null)
        }.create())
    }

    private fun showClearConfirmation() {
        showDialog(AlertDialog.Builder(context, resourceProvider).apply {
            setTitle(getString(R.string.AyugramMessageHistoryClear))
            setMessage(getString(R.string.AyugramMessageHistoryClearConfirm))
            setNegativeButton(getString(R.string.AyugramMessageHistoryCancel), null)
            setPositiveButton(getString(R.string.AyugramMessageHistoryClear)) { _, _ ->
                MessagesStorage.getInstance(currentAccount).clearMessageHistory {
                    page = 0
                    hasNext = false
                    loadPage()
                }
            }
        }.create())
    }
}
