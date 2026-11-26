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
        if (request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readSavedHistory) {
            return IcegramConfig.areReadReceiptsHidden();
        }

        if (request instanceof TLRPC.TL_messages_setTyping) {
            if (!IcegramConfig.isTypingHidden()) {
                return false;
            }
            TLRPC.SendMessageAction action = ((TLRPC.TL_messages_setTyping) request).action;
            return !(action instanceof TLRPC.TL_sendMessageCancelAction);
        }

        if (request instanceof TLRPC.TL_messages_setEncryptedTyping) {
            return IcegramConfig.isTypingHidden() && ((TLRPC.TL_messages_setEncryptedTyping) request).typing;
        }

        return false;
    }

    public static void setEnabled(boolean enabled) {
        IcegramConfig.setGhostModeEnabled(enabled);
        refreshPresence();
    }

    public static void setOnlineHidden(boolean enabled) {
        if (IcegramConfig.isOnlineHidden() == enabled) {
            return;
        }
        IcegramConfig.setOnlineHidden(enabled);
        refreshPresence();
    }

    public static void setTypingHidden(boolean enabled) {
        IcegramConfig.setTypingHidden(enabled);
    }

    public static void setReadReceiptsHidden(boolean enabled) {
        IcegramConfig.setReadReceiptsHidden(enabled);
    }

    private static void refreshPresence() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            final int accountId = account;
            if (UserConfig.getInstance(accountId).isClientActivated()) {
                Utilities.stageQueue.postRunnable(() ->
                        AccountInstance.getInstance(accountId).getMessagesController().onGhostModeChanged());
            }
        }
    }
}
