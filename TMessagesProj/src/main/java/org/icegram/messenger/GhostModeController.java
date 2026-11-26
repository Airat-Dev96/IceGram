/* icegram additions. Licensed under GPL-3.0-or-later. */
package org.icegram.messenger;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/** Filters outgoing presence and read-receipt requests while keeping local reads intact. */
public final class GhostModeController {
    private GhostModeController() {
    }

    public static boolean shouldSuppressRequest(TLObject request) {
        if (!IcegramConfig.isGhostModeEnabled()) {
            return false;
        }

        if (request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readSavedHistory) {
            return true;
        }

        if (request instanceof TLRPC.TL_messages_setTyping) {
            TLRPC.SendMessageAction action = ((TLRPC.TL_messages_setTyping) request).action;
            return !(action instanceof TLRPC.TL_sendMessageCancelAction);
        }

        if (request instanceof TLRPC.TL_messages_setEncryptedTyping) {
            return ((TLRPC.TL_messages_setEncryptedTyping) request).typing;
        }

        return false;
    }

    public static void setEnabled(boolean enabled) {
        if (IcegramConfig.isGhostModeEnabled() == enabled) {
            return;
        }
        IcegramConfig.setGhostModeEnabled(enabled);
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            final int accountId = account;
            if (UserConfig.getInstance(accountId).isClientActivated()) {
                Utilities.stageQueue.postRunnable(() ->
                        AccountInstance.getInstance(accountId).getMessagesController().onGhostModeChanged());
            }
        }
    }
}
