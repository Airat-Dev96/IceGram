/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.preferences

import android.view.View
import com.exteragram.messenger.proxy.AutoProxyController
import com.exteragram.messenger.proxy.AutoProxyScheduler
import org.icegram.messenger.IcegramConfig
import org.telegram.messenger.LocaleController.formatString
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class AutoProxyPreferencesActivity : BasePreferencesActivity() {
    private enum class Item {
        ENABLED,
        INTERVAL,
        COUNT,
        REMOVE_DEAD,
        AUTO_SWITCH,
        MAX_PING,
        HIDE_SPONSOR,
        REFRESH
    }

    private val intervalOptions = intArrayOf(1, 3, 5, 12, 24)
    private val countOptions = intArrayOf(10, 25, 50, 100)
    private val maxPingOptions = intArrayOf(300, 500, 800, 1200, 2000)
    private var refreshInProgress = false

    override fun getTitle(): String = getString(R.string.IcegramAutoProxy)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.IcegramAutoProxy)))
        items.add(UItem.asShadow(getString(R.string.IcegramAutoProxyInfo)))
        items.add(uCheck(
            Item.ENABLED,
            getString(R.string.IcegramAutoProxyEnabled),
            IcegramConfig.isAutoProxyEnabled(),
            getString(R.string.IcegramAutoProxyEnabledInfo),
            linkAlias = "autoProxyEnabled"
        ))
        items.add(uButton(
            Item.REFRESH,
            getString(R.string.IcegramAutoProxyRefresh),
            value = if (refreshInProgress) getString(R.string.IcegramAutoProxyRefreshing) else null,
            linkAlias = "autoProxyRefresh"
        ))
        items.add(uButton(
            Item.INTERVAL,
            getString(R.string.IcegramAutoProxyInterval),
            value = intervalLabel(IcegramConfig.getAutoProxyIntervalHours()),
            linkAlias = "autoProxyInterval"
        ))
        items.add(uButton(
            Item.COUNT,
            getString(R.string.IcegramAutoProxyCount),
            value = countLabel(IcegramConfig.getAutoProxyCount()),
            linkAlias = "autoProxyCount"
        ))
        items.add(uCheck(
            Item.REMOVE_DEAD,
            getString(R.string.IcegramAutoProxyRemoveDead),
            IcegramConfig.isAutoProxyRemoveDeadEnabled(),
            getString(R.string.IcegramAutoProxyRemoveDeadInfo),
            linkAlias = "autoProxyRemoveDead"
        ))
        items.add(uCheck(
            Item.AUTO_SWITCH,
            getString(R.string.IcegramAutoProxyAutoSwitch),
            IcegramConfig.isAutoProxyAutoSwitchEnabled(),
            getString(R.string.IcegramAutoProxyAutoSwitchInfo),
            linkAlias = "autoProxyAutoSwitch"
        ))
        items.add(uButton(
            Item.MAX_PING,
            getString(R.string.IcegramAutoProxyMaxPing),
            value = pingLabel(IcegramConfig.getAutoProxyMaxPing()),
            linkAlias = "autoProxyMaxPing"
        ))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.IcegramProxyPrivacy)))
        items.add(uCheck(
            Item.HIDE_SPONSOR,
            getString(R.string.IcegramHideProxySponsor),
            IcegramConfig.isHideProxySponsorEnabled(),
            getString(R.string.IcegramHideProxySponsorInfo),
            linkAlias = "hideProxySponsor"
        ))
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            Item.ENABLED.ordinal + 1 -> toggleBooleanSettingAndRefresh(item) {
                IcegramConfig.setAutoProxyEnabled(it)
                AutoProxyScheduler.sync()
            }
            Item.REFRESH.ordinal + 1 -> refreshNow()
            Item.INTERVAL.ordinal + 1 -> showListDialog(
                item,
                intervalOptions.map(::intervalLabel).toTypedArray(),
                getString(R.string.IcegramAutoProxyInterval),
                intervalOptions.indexOf(IcegramConfig.getAutoProxyIntervalHours())
            ) {
                IcegramConfig.setAutoProxyIntervalHours(intervalOptions[it])
                AutoProxyScheduler.sync()
            }
            Item.COUNT.ordinal + 1 -> showListDialog(
                item,
                countOptions.map(::countLabel).toTypedArray(),
                getString(R.string.IcegramAutoProxyCount),
                countOptions.indexOf(IcegramConfig.getAutoProxyCount())
            ) { IcegramConfig.setAutoProxyCount(countOptions[it]) }
            Item.REMOVE_DEAD.ordinal + 1 -> toggleBooleanSettingAndRefresh(item) { IcegramConfig.setAutoProxyRemoveDeadEnabled(it) }
            Item.AUTO_SWITCH.ordinal + 1 -> toggleBooleanSettingAndRefresh(item) { IcegramConfig.setAutoProxyAutoSwitchEnabled(it) }
            Item.MAX_PING.ordinal + 1 -> showListDialog(
                item,
                maxPingOptions.map(::pingLabel).toTypedArray(),
                getString(R.string.IcegramAutoProxyMaxPing),
                maxPingOptions.indexOf(IcegramConfig.getAutoProxyMaxPing())
            ) { IcegramConfig.setAutoProxyMaxPing(maxPingOptions[it]) }
            Item.HIDE_SPONSOR.ordinal + 1 -> toggleBooleanSettingAndRefresh(item) {
                IcegramConfig.setHideProxySponsorEnabled(it)
                for (account in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
                    if (UserConfig.getInstance(account).isClientActivated) {
                        MessagesController.getInstance(account).checkPromoInfo(true)
                    }
                }
            }
        }
    }

    private fun intervalLabel(hours: Int): String =
        formatString(R.string.IcegramAutoProxyHours, hours)

    private fun countLabel(count: Int): String =
        formatString(R.string.IcegramAutoProxyCountValue, count)

    private fun pingLabel(ping: Int): String =
        formatString(R.string.IcegramAutoProxyPingValue, ping)

    private fun refreshNow() {
        if (refreshInProgress) return
        refreshInProgress = true
        listView.adapter?.update(true)
        AutoProxyController.getInstance().refresh { result ->
            refreshInProgress = false
            if (parentActivity == null) return@refresh
            listView.adapter?.update(true)

            val message = if (!result.sourceAvailable) {
                getString(R.string.IcegramAutoProxySourceUnavailable)
            } else {
                formatString(
                    R.string.IcegramAutoProxyRefreshResult,
                    result.discovered,
                    result.checked,
                    result.available,
                    result.removed,
                    getString(if (result.switched) R.string.IcegramAutoProxySwitched else R.string.IcegramAutoProxyNotSwitched)
                )
            }
            org.telegram.ui.Components.BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.info, message)
                .show()
        }
    }
}
