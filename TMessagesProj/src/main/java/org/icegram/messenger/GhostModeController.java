/* icegram additions. Licensed under GPL-3.0-or-later. */
package org.icegram.messenger;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

import java.util.concurrent.atomic.AtomicLong;

/** Filters outgoing presence and read-receipt requests while keeping local reads intact. */
public final class GhostModeController {
    private static final AtomicLong readGeneration = new AtomicLong();
    private static final AtomicLong storyGeneration = new AtomicLong();

    private GhostModeController() {
    }

    public static boolean shouldSuppressRequest(TLObject request) {
        if (isCloudReadRequest(request)) {
            return IcegramConfig.areReadReceiptsHidden();
        }
        if (isStoryViewRequest(request)) {
            return IcegramConfig.areStoryViewsHidden();
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

    private static boolean isCloudReadRequest(TLObject request) {
        return request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readSavedHistory
                || request instanceof TLRPC.TL_messages_readMentions
                || request instanceof TLRPC.TL_messages_readReactions
                || request instanceof TLRPC.TL_messages_readPollVotes;
    }

    private static boolean isStoryViewRequest(TLObject request) {
        return request instanceof TL_stories.TL_stories_readStories
                || request instanceof TL_stories.TL_stories_incrementStoryViews;
    }

    public static long captureReadReceiptToken() {
        long generation = readGeneration.get();
        return IcegramConfig.areReadReceiptsHidden() ? -1 : generation;
    }

    public static long captureStoryViewToken() {
        long generation = storyGeneration.get();
        return IcegramConfig.areStoryViewsHidden() ? -1 : generation;
    }

    public static boolean isReadReceiptTokenValid(long token) {
        return token >= 0 && token == readGeneration.get() && !IcegramConfig.areReadReceiptsHidden();
    }

    public static boolean shouldDiscardQueuedReceipt(TLObject request, long readToken, long storyToken) {
        return isCloudReadRequest(request) && !isReadReceiptTokenValid(readToken)
                || isStoryViewRequest(request) && (storyToken < 0 || storyToken != storyGeneration.get()
                || IcegramConfig.areStoryViewsHidden());
    }

    public static void applyRequestPrivacy(TLObject request) {
        if (request instanceof TL_account.updateStatus && IcegramConfig.isOnlineHidden()) {
            ((TL_account.updateStatus) request).offline = true;
        }
        if (!IcegramConfig.isSilentSendEnabled()) {
            return;
        }
        if (request instanceof TLRPC.TL_messages_sendMessage) {
            ((TLRPC.TL_messages_sendMessage) request).silent = true;
        } else if (request instanceof TLRPC.TL_messages_sendMedia) {
            ((TLRPC.TL_messages_sendMedia) request).silent = true;
        } else if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            ((TLRPC.TL_messages_sendMultiMedia) request).silent = true;
        } else if (request instanceof TLRPC.TL_messages_forwardMessages) {
            ((TLRPC.TL_messages_forwardMessages) request).silent = true;
        } else if (request instanceof TLRPC.TL_messages_sendInlineBotResult) {
            ((TLRPC.TL_messages_sendInlineBotResult) request).silent = true;
        }
    }

    public static boolean isEnabled() {
        return IcegramConfig.isOnlineHidden() && IcegramConfig.isTypingHidden()
                && IcegramConfig.areReadReceiptsHidden() && IcegramConfig.areStoryViewsHidden();
    }

    public static void setEnabled(boolean enabled) {
        setReadReceiptsHidden(enabled);
        setStoryViewsHidden(enabled);
        setTypingHidden(enabled);
        setOnlineHidden(enabled);
        IcegramConfig.setGhostModeEnabled(enabled);
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
        if (IcegramConfig.areReadReceiptsHidden() == enabled) {
            return;
        }
        readGeneration.incrementAndGet();
        IcegramConfig.setReadReceiptsHidden(enabled);
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            final int accountId = account;
            if (UserConfig.getInstance(accountId).isClientActivated()) {
                Utilities.stageQueue.postRunnable(() -> AccountInstance.getInstance(accountId)
                        .getMessagesController().discardStaleGhostReadTasks());
            }
        }
    }

    public static void setStoryViewsHidden(boolean enabled) {
        if (IcegramConfig.areStoryViewsHidden() == enabled) {
            return;
        }
        storyGeneration.incrementAndGet();
        IcegramConfig.setStoryViewsHidden(enabled);
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
