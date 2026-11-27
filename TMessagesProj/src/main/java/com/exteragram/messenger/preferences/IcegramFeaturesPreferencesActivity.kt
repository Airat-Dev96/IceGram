/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.preferences

import android.view.View
import com.exteragram.messenger.DividerStyle
import com.exteragram.messenger.GlassOutlineStyle
import com.exteragram.messenger.HeaderBlurStyle
import com.exteragram.messenger.hidePhoneNumber
import com.exteragram.messenger.newLoadingStyle
import com.exteragram.messenger.newSliderStyle
import com.exteragram.messenger.newSwitchStyle
import com.exteragram.messenger.newChatHeaderStyle
import com.exteragram.messenger.newNavigationBarStyle
import com.exteragram.messenger.newFabStyle
import com.exteragram.messenger.newProfileActionsStyle
import com.exteragram.messenger.newPlayerStyle
import com.exteragram.messenger.gooeyAvatarAnimation
import com.exteragram.messenger.glassMessageMenu
import com.exteragram.messenger.glassOutlineStyle
import com.exteragram.messenger.headerBlurStyle
import com.exteragram.messenger.dividerStyle
import com.exteragram.messenger.sectionRadius
import com.exteragram.messenger.squareFab
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.ui.PluginsActivity
import com.exteragram.messenger.showSaveMessageButton
import org.icegram.messenger.IcegramConfig
import org.icegram.messenger.GhostModeController
import org.telegram.messenger.LiteMode
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.DataSettingsActivity
import org.telegram.ui.LiteModeSettingsActivity
import java.util.ArrayList

class IcegramFeaturesPreferencesActivity : BasePreferencesActivity() {
    private enum class Item {
        ULTRA_ECONOMY, ICE_PRESET, CLASSIC_INTERFACE, COMPACT_CACHE, LAZY_WIDGETS, ANIMATIONS, DATA,
        HIDE_PHONE, SAVE_BUTTON,
        MESSAGE_HISTORY_ENABLED, MESSAGE_HISTORY, HISTORY_EXCEPTIONS,
        GHOST_QUICK, GHOST_OPTIONS, GHOST_EXCEPTIONS, READ_ON_SEND, SILENT_SEND,
        AUTO_PROXY,
        UPSTREAM, PLUGINS, HIDE_CHANNEL_SPONSORED;

        val id: Int = ordinal + 1
    }

    override fun getTitle(): String = getString(R.string.IcegramSettings)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.IcegramPerformance)))
        items.add(uButton(Item.ULTRA_ECONOMY, getString(R.string.IcegramUltraEconomy),
            R.drawable.settings_power, subtext = getString(R.string.IcegramUltraEconomyInfo)))
        items.add(uButton(Item.ICE_PRESET, getString(R.string.IcegramApplyPreset),
            R.drawable.msg_settings, subtext = getString(R.string.IcegramPresetInfo)))
        items.add(uButton(Item.CLASSIC_INTERFACE, getString(R.string.IcegramClassicInterface),
            R.drawable.msg_theme, subtext = getString(R.string.IcegramClassicInterfaceInfo)))
        items.add(uCheck(Item.COMPACT_CACHE, getString(R.string.IcegramCompactCache),
            IcegramConfig.isCompactCacheEnabled(), getString(R.string.IcegramCompactCacheInfo)))
        items.add(uCheck(Item.LAZY_WIDGETS, getString(R.string.IcegramLazyWidgets),
            IcegramConfig.isLazyWidgetsEnabled(), getString(R.string.IcegramLazyWidgetsInfo)))
        items.add(uButton(Item.ANIMATIONS, getString(R.string.IcegramAnimationSettings), R.drawable.msg_settings))
        items.add(uButton(Item.DATA, getString(R.string.IcegramDataSettings), R.drawable.msg_media))
        items.add(UItem.asShadow(getString(R.string.IcegramNoMagic)))

        items.add(UItem.asHeader(getString(R.string.IcegramComfort)))
        items.add(uCheck(Item.HIDE_PHONE, getString(R.string.HidePhoneNumber), hidePhoneNumber,
            getString(R.string.IcegramHidePhoneInfo)))
        items.add(uCheck(Item.SAVE_BUTTON, getString(R.string.IcegramSaveButton), showSaveMessageButton,
            getString(R.string.IcegramSaveButtonInfo)))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.IcegramPrivacy)))
        items.add(uCheck(Item.GHOST_QUICK, getString(R.string.AyugramGhostMode),
            GhostModeController.isEnabled(), getString(R.string.IcegramGhostQuickInfo)))
        items.add(uButton(Item.GHOST_OPTIONS, getString(R.string.IcegramGhostOptions),
            R.drawable.msg_settings, subtext = getString(R.string.AyugramGhostModeInfo)))
        items.add(uButton(Item.GHOST_EXCEPTIONS, getString(R.string.IcegramGhostExceptions),
            R.drawable.msg_settings, subtext = getString(R.string.IcegramGhostExceptionsInfo)))
        items.add(uCheck(Item.READ_ON_SEND, getString(R.string.IcegramReadOnSend),
            IcegramConfig.isReadOnSendEnabled(), getString(R.string.IcegramReadOnSendInfo)))
        items.add(uCheck(Item.SILENT_SEND, getString(R.string.IcegramSilentSend),
            IcegramConfig.isSilentSendEnabled(), getString(R.string.IcegramSilentSendInfo)))
        items.add(uCheck(Item.HIDE_CHANNEL_SPONSORED, getString(R.string.IcegramHideChannelSponsored),
            IcegramConfig.isHideChannelSponsoredMessagesEnabled(), getString(R.string.IcegramHideChannelSponsoredInfo)))
        items.add(uCheck(Item.MESSAGE_HISTORY_ENABLED, getString(R.string.AyugramMessageHistoryEnabled),
            IcegramConfig.isMessageHistoryEnabled(), getString(R.string.AyugramMessageHistoryEnabledInfo)))
        items.add(uButton(Item.MESSAGE_HISTORY, getString(R.string.AyugramMessageHistory),
            subtext = getString(R.string.AyugramMessageHistoryInfo)))
        items.add(uButton(Item.HISTORY_EXCEPTIONS, getString(R.string.IcegramHistoryExceptions),
            subtext = getString(R.string.IcegramHistoryExceptionsInfo)))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.IcegramNetwork)))
        items.add(uButton(Item.AUTO_PROXY, getString(R.string.IcegramAutoProxy), R.drawable.proxy_check,
            subtext = getString(R.string.IcegramAutoProxyInfo), linkAlias = "autoProxy"))
        items.add(UItem.asShadow())

        if (PluginsController.isPluginEngineSupported()) {
            items.add(uButton(Item.PLUGINS, getString(R.string.Plugins), R.drawable.msg_plugins))
            items.add(UItem.asShadow(getString(R.string.IcegramPluginsKept)))
        }

        items.add(UItem.asHeader(getString(R.string.IcegramAbout)))
        items.add(uButton(Item.UPSTREAM, getString(R.string.IcegramAttribution),
            R.drawable.msg_info, subtext = IcegramConfig.UPSTREAM_URL))
        items.add(UItem.asShadow(getString(R.string.IcegramForkInfo)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            Item.ULTRA_ECONOMY.id -> presentFragment(UltraEconomyPreferencesActivity())
            Item.ICE_PRESET.id -> {
                LiteMode.setAllFlags(0)
                LiteMode.setPowerSaverFollowSystem(true)
                listView.adapter?.update(false)
            }
            Item.CLASSIC_INTERFACE.id -> {
                newLoadingStyle = false
                newSliderStyle = false
                newSwitchStyle = false
                newChatHeaderStyle = false
                newNavigationBarStyle = false
                newFabStyle = false
                newProfileActionsStyle = false
                newPlayerStyle = false
                gooeyAvatarAnimation = false
                glassMessageMenu = false
                glassOutlineStyle = GlassOutlineStyle.HIDDEN
                headerBlurStyle = HeaderBlurStyle.DEFAULT
                dividerStyle = DividerStyle.LINE
                sectionRadius = 4f
                squareFab = true
                LiteMode.setAllFlags(LiteMode.getValue(true) and LiteMode.FLAG_LIQUID_GLASS.inv() and LiteMode.FLAG_CHAT_BLUR.inv())
                listView.adapter?.update(false)
                showRestartBulletin()
            }
            Item.COMPACT_CACHE.id -> {
                toggleBooleanSettingAndRefresh(item) { IcegramConfig.setCompactCacheEnabled(it) }
                showRestartBulletin()
            }
            Item.LAZY_WIDGETS.id -> {
                toggleBooleanSettingAndRefresh(item) { IcegramConfig.setLazyWidgetsEnabled(it) }
                showRestartBulletin()
            }
            Item.ANIMATIONS.id -> presentFragment(LiteModeSettingsActivity())
            Item.DATA.id -> presentFragment(DataSettingsActivity())
            Item.HIDE_PHONE.id -> toggleBooleanSettingAndRefresh(item) { hidePhoneNumber = it }
            Item.SAVE_BUTTON.id -> toggleBooleanSettingAndRefresh(item) { showSaveMessageButton = it }
            Item.MESSAGE_HISTORY_ENABLED.id -> toggleBooleanSettingAndRefresh(item) { IcegramConfig.setMessageHistoryEnabled(it) }
            Item.MESSAGE_HISTORY.id -> presentFragment(MessageHistoryActivity())
            Item.HISTORY_EXCEPTIONS.id -> presentFragment(MessageHistoryExceptionsActivity())
            Item.GHOST_QUICK.id -> toggleBooleanSettingAndRefresh(item) { GhostModeController.setEnabled(it) }
            Item.GHOST_OPTIONS.id -> presentFragment(AyugramPreferencesActivity())
            Item.GHOST_EXCEPTIONS.id -> presentFragment(GhostModeExceptionsActivity())
            Item.READ_ON_SEND.id -> toggleBooleanSettingAndRefresh(item) { IcegramConfig.setReadOnSendEnabled(it) }
            Item.SILENT_SEND.id -> toggleBooleanSettingAndRefresh(item) { IcegramConfig.setSilentSendEnabled(it) }
            Item.HIDE_CHANNEL_SPONSORED.id -> toggleBooleanSettingAndRefresh(item) { IcegramConfig.setHideChannelSponsoredMessagesEnabled(it) }
            Item.AUTO_PROXY.id -> presentFragment(AutoProxyPreferencesActivity())
            Item.UPSTREAM.id -> Browser.openUrl(parentActivity, IcegramConfig.UPSTREAM_URL)
            Item.PLUGINS.id -> presentFragment(PluginsActivity())
        }
    }
}
