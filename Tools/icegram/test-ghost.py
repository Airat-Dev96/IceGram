"""Compile the actual RPC policy with minimal transport fixtures; no Android build."""
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"


def main():
    if not shutil.which("javac") or not shutil.which("java"):
        raise SystemExit("JDK 17 is required for Ghost Mode policy tests")
    controller = JAVA / "org/icegram/messenger/GhostModeController.java"
    rpc_types = set(re.findall(r"TLRPC\.(TL_\w+)", controller.read_text()))
    rpc_types.update(("TL_messages_readEncryptedHistory", "TL_messages_receivedQueue",
                      "TL_messages_sendEncryptedService"))
    action_types = {"TL_sendMessageCancelAction"}
    declarations = []
    for name in sorted(rpc_types):
        parent = "SendMessageAction" if name in action_types else "TLObject"
        declarations.append(f"public static class {name} extends {parent} {{ "
                            "public boolean silent, typing; public SendMessageAction action; "
                            "public InputPeer peer, parent_peer; public InputChannel channel; "
                            "public long icegramDialogId; }")
    sources = {
        "org/telegram/tgnet/TLObject.java": "package org.telegram.tgnet; public class TLObject {}",
        "org/telegram/tgnet/TLRPC.java": "package org.telegram.tgnet; public class TLRPC { "
            "public static class SendMessageAction extends TLObject {} "
            "public static class InputPeer { public long user_id, chat_id, channel_id; } "
            "public static class InputChannel { public long channel_id; } " + " ".join(declarations) + " }",
        "org/telegram/messenger/DialogObject.java": "package org.telegram.messenger; public class DialogObject { "
            "public static long getPeerDialogId(org.telegram.tgnet.TLRPC.InputPeer peer) { "
            "if (peer == null) return 0; if (peer.user_id != 0) return peer.user_id; "
            "if (peer.chat_id != 0) return -peer.chat_id; return -peer.channel_id; } "
            "public static boolean isEncryptedDialog(long dialogId) { return (dialogId & 0x4000000000000000L) != 0; } }",
        "org/telegram/tgnet/tl/TL_account.java": "package org.telegram.tgnet.tl; "
            "public class TL_account { public static class updateStatus extends org.telegram.tgnet.TLObject { public boolean offline; } }",
        "org/telegram/tgnet/tl/TL_stories.java": "package org.telegram.tgnet.tl; public class TL_stories { "
            "public static class TL_stories_readStories extends org.telegram.tgnet.TLObject { public org.telegram.tgnet.TLRPC.InputPeer peer; } "
            "public static class TL_stories_incrementStoryViews extends org.telegram.tgnet.TLObject { public org.telegram.tgnet.TLRPC.InputPeer peer; } }",
        "org/telegram/messenger/UserConfig.java": "package org.telegram.messenger; public class UserConfig { "
            "public static final int MAX_ACCOUNT_COUNT=2; public static UserConfig getInstance(int account) { return new UserConfig(); } "
            "public boolean isClientActivated() { return true; } }",
        "org/telegram/messenger/Utilities.java": "package org.telegram.messenger; public class Utilities { "
            "public static final Queue stageQueue = new Queue(); public static class Queue { public void postRunnable(Runnable action) { action.run(); } } }",
        "org/telegram/messenger/AccountInstance.java": "package org.telegram.messenger; public class AccountInstance { "
            "public static AccountInstance getInstance(int account) { return new AccountInstance(); } "
            "public Controller getMessagesController() { return new Controller(); } public static class Controller { "
            "public void onGhostModeChanged() {} public void discardStaleGhostReadTasks() {} } }",
        "org/icegram/messenger/IcegramConfig.java": """package org.icegram.messenger;
public class IcegramConfig {
    private static boolean reads, typing, online, stories, silent;
    private static int exceptedAccount = -1;
    private static long exceptedDialog;
    public static boolean areReadReceiptsHidden() { return reads; }
    public static boolean isGhostModeDisabledForDialog(int account, long dialogId) {
        return account == exceptedAccount && dialogId == exceptedDialog;
    }
    public static void setException(int account, long dialogId) {
        exceptedAccount = account; exceptedDialog = dialogId;
    }
    public static boolean isTypingHidden() { return typing; }
    public static boolean isOnlineHidden() { return online; }
    public static boolean areStoryViewsHidden() { return stories; }
    public static boolean isSilentSendEnabled() { return silent; }
    public static void setReadReceiptsHidden(boolean value) { reads=value; }
    public static void setTypingHidden(boolean value) { typing=value; }
    public static void setOnlineHidden(boolean value) { online=value; }
    public static void setStoryViewsHidden(boolean value) { stories=value; }
    public static void setSilentSendEnabled(boolean value) { silent=value; }
    public static void setGhostModeEnabled(boolean value) { reads=typing=online=value; }
} """,
        "GhostModeTest.java": """import org.icegram.messenger.GhostModeController;
import org.icegram.messenger.IcegramConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;
public class GhostModeTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Ghost policy check " + checks);
    }
    public static void main(String[] args) {
        TLObject read = new TLRPC.TL_messages_readHistory();
        TL_stories.TL_stories_readStories storyRead = new TL_stories.TL_stories_readStories();
        storyRead.peer = new TLRPC.InputPeer();
        storyRead.peer.user_id = 42;
        TLObject story = storyRead;
        long original = GhostModeController.captureReadReceiptToken();
        check(GhostModeController.isReadReceiptTokenValid(original));
        check(!GhostModeController.shouldSuppressRequest(read));
        TLRPC.TL_messages_readHistory chatRead = new TLRPC.TL_messages_readHistory();
        chatRead.peer = new TLRPC.InputPeer();
        chatRead.peer.user_id = 42;
        GhostModeController.setReadReceiptsHidden(true);
        check(GhostModeController.shouldSuppressRequest(0, chatRead));
        IcegramConfig.setException(0, 42);
        check(!GhostModeController.shouldSuppressRequest(0, chatRead));
        GhostModeController.setStoryViewsHidden(true);
        check(!GhostModeController.shouldSuppressRequest(0, story));
        check(GhostModeController.shouldSuppressRequest(1, story));
        long storyExceptionToken = GhostModeController.captureStoryViewToken(0, story);
        check(!GhostModeController.shouldDiscardQueuedReceipt(0, story, -1, storyExceptionToken));
        check(GhostModeController.shouldDiscardQueuedReceipt(1, story, -1,
            GhostModeController.captureStoryViewToken(1, story)));
        GhostModeController.setStoryViewsHidden(false);
        check(GhostModeController.shouldSuppressRequest(1, chatRead));
        long exceptionToken = GhostModeController.captureReadReceiptToken(0, 42);
        check(GhostModeController.isReadReceiptTokenValid(0, 42, exceptionToken));
        check(GhostModeController.shouldDiscardQueuedReceipt(1, chatRead,
            GhostModeController.captureReadReceiptToken(1, 42), 0));
        TLRPC.TL_messages_readMessageContents contentRead = new TLRPC.TL_messages_readMessageContents();
        contentRead.icegramDialogId = 42;
        check(!GhostModeController.shouldSuppressRequest(0, contentRead));
        check(GhostModeController.shouldSuppressRequest(1, contentRead));
        TLRPC.TL_channels_readHistory channelRead = new TLRPC.TL_channels_readHistory();
        channelRead.channel = new TLRPC.InputChannel();
        channelRead.channel.channel_id = 77;
        check(GhostModeController.getReadDialogId(channelRead) == -77);
        GhostModeController.setReadReceiptsHidden(false);
        GhostModeController.setReadReceiptsHidden(true);
        long hidden = GhostModeController.captureReadReceiptToken();
        check(!GhostModeController.isReadReceiptTokenValid(original));
        check(GhostModeController.shouldSuppressRequest(read));
        check(!GhostModeController.shouldSuppressRequest(story));
        GhostModeController.setReadReceiptsHidden(false);
        check(!GhostModeController.isReadReceiptTokenValid(hidden));
        check(!GhostModeController.isReadReceiptTokenValid(original));
        long fresh = GhostModeController.captureReadReceiptToken();
        GhostModeController.setReadReceiptsHidden(false);
        GhostModeController.setTypingHidden(true);
        check(GhostModeController.isReadReceiptTokenValid(fresh));
        check(GhostModeController.shouldDiscardQueuedReceipt(read, original, 0));
        check(!GhostModeController.shouldDiscardQueuedReceipt(read, fresh, 0));
        long oldStory = GhostModeController.captureStoryViewToken();
        GhostModeController.setStoryViewsHidden(true);
        long hiddenStory = GhostModeController.captureStoryViewToken();
        GhostModeController.setStoryViewsHidden(false);
        check(GhostModeController.shouldDiscardQueuedReceipt(story, fresh, oldStory));
        check(GhostModeController.shouldDiscardQueuedReceipt(story, fresh, hiddenStory));
        check(GhostModeController.isReadReceiptTokenValid(fresh));
        GhostModeController.setEnabled(true);
        check(GhostModeController.isEnabled());
        TLObject[] reads = { new TLRPC.TL_messages_readHistory(), new TLRPC.TL_channels_readHistory(),
            new TLRPC.TL_messages_readMessageContents(), new TLRPC.TL_channels_readMessageContents(),
            new TLRPC.TL_messages_readDiscussion(), new TLRPC.TL_messages_readSavedHistory(),
            new TLRPC.TL_messages_readMentions(), new TLRPC.TL_messages_readReactions(),
            new TLRPC.TL_messages_readPollVotes(), story, new TL_stories.TL_stories_incrementStoryViews() };
        for (TLObject request : reads) check(GhostModeController.shouldSuppressRequest(request));
        TLRPC.TL_messages_setTyping typing = new TLRPC.TL_messages_setTyping();
        typing.peer = new TLRPC.InputPeer();
        typing.peer.user_id = 42;
        typing.action = new TLRPC.SendMessageAction();
        check(!GhostModeController.shouldSuppressRequest(0, typing));
        check(GhostModeController.shouldSuppressRequest(1, typing));
        check(GhostModeController.shouldSuppressRequest(typing));
        typing.action = new TLRPC.TL_sendMessageCancelAction();
        check(!GhostModeController.shouldSuppressRequest(typing));
        TLRPC.TL_messages_setEncryptedTyping secretTyping = new TLRPC.TL_messages_setEncryptedTyping();
        check(!GhostModeController.shouldSuppressRequest(secretTyping));
        secretTyping.typing = true;
        check(!GhostModeController.shouldSuppressRequest(secretTyping));
        for (TLObject service : new TLObject[] {new TLRPC.TL_messages_readEncryptedHistory(),
                new TLRPC.TL_messages_receivedQueue(), new TLRPC.TL_messages_sendEncryptedService()}) {
            check(!GhostModeController.shouldSuppressRequest(service));
            check(!GhostModeController.shouldDiscardQueuedReceipt(service, -1, -1));
        }
        TL_account.updateStatus status = new TL_account.updateStatus();
        GhostModeController.applyRequestPrivacy(status);
        check(status.offline);
        check(!GhostModeController.shouldSuppressRequest(status));
        TLRPC.TL_messages_sendMessage send = new TLRPC.TL_messages_sendMessage();
        GhostModeController.applyRequestPrivacy(send);
        check(!send.silent);
        IcegramConfig.setSilentSendEnabled(true);
        GhostModeController.applyRequestPrivacy(send);
        check(send.silent);
        check(!GhostModeController.shouldDiscardQueuedReceipt(send, -1, -1));
        GhostModeController.setEnabled(false);
        check(!GhostModeController.isEnabled());
        check(IcegramConfig.isSilentSendEnabled());
        long disabled = GhostModeController.captureReadReceiptToken();
        check(GhostModeController.isReadReceiptTokenValid(disabled));
        for (TLObject request : reads) check(!GhostModeController.shouldSuppressRequest(request));
        System.out.println("PASS: " + checks + " Ghost Mode policy checks");
    }
} """,
    }
    with tempfile.TemporaryDirectory(prefix="icegram-ghost-") as directory:
        temporary = Path(directory)
        paths = []
        for name, source in sources.items():
            path = temporary / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
            paths.append(str(path))
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", directory, str(controller), *paths], check=True)
        subprocess.run(["java", "-cp", directory, "GhostModeTest"], check=True)

    network = (JAVA / "org/telegram/tgnet/ConnectionsManager.java").read_text()
    gate = network[network.index("if (!allowGhostReadReceipt && (GhostModeController.shouldSuppressRequest(currentAccount, object)"):]
    gate = gate[:gate.index("NativeByteBuffer buffer")]
    assert gate.count("onComplete.run(null, ghostModeError)") == 1
    assert gate.count("onCompleteTimestamp.run(null, ghostModeError, 0)") == 1
    assert "Utilities.stageQueue.postRunnable" in gate
    messages = (JAVA / "org/telegram/messenger/MessagesController.java").read_text()
    assert "currentReadTask.ghostReadToken = ghostReadToken" in messages
    assert "isReadReceiptTokenValid(currentAccount, task.dialogId, task.ghostReadToken)" in messages
    assert "isReadReceiptTokenValid(currentAccount, dialogId, ghostReadToken)" in messages
    assert "discardStaleGhostReadTasks" in messages
    assert "sendRequestForGhostRead" in network
    config = (JAVA / "org/icegram/messenger/IcegramConfig.java").read_text()
    assert 'getBoolean("icegram_read_on_send", false)' in config
    assert 'UserConfig.getInstance(account).getClientUserId()' in config
    assert 'getStringSet(ghostExceptionsKey(account)' in config
    assert 'DialogObject.isEncryptedDialog(dialogId)' in config
    chat_activity = (JAVA / "org/telegram/ui/ChatActivity.java").read_text()
    assert "scheduleDate == 0 && IcegramConfig.isReadOnSendEnabled()" in chat_activity
    assert chat_activity.count("sendReadReceiptForGhostInteract(dialog_id, getLatestIncomingMessageId())") == 2
    latest_incoming = chat_activity[chat_activity.index("private int getLatestIncomingMessageId()") :]
    latest_incoming = latest_incoming[:latest_incoming.index("private void processDeletedMessages")]
    assert "message.getDialogId() == dialog_id" in latest_incoming
    assert "if (id == ghost_read_now)" in chat_activity
    assert "if (currentEncryptedChat == null && !isReport())" in chat_activity
    assert "DialogObject.isEncryptedDialog(dialogId)" in messages
    assert "sendRequestForGhostRead(request" in messages
    drawer = (JAVA / "com/exteragram/messenger/drawer/MainMenuHelper.java").read_text()
    assert "GhostModeController.isEnabled() ? R.drawable.ic_ghost_on : R.drawable.ic_ghost_off" in drawer
    assert "() -> GhostModeController.setEnabled(!GhostModeController.isEnabled())" in drawer
    layout = (JAVA / "com/exteragram/messenger/drawer/MainMenuLayout.kt").read_text()
    assert "GHOST(108)" in layout and "add(MainMenuItem.GHOST.id)" in layout
    exceptions = (JAVA / "com/exteragram/messenger/preferences/GhostModeExceptionsActivity.kt").read_text()
    assert "getGhostModeExcludedDialogs(currentAccount)" in exceptions
    assert "setGhostModeExcluded(currentAccount, key.dialogId, true)" in exceptions
    assert "DialogObject.isEncryptedDialog(key.dialogId)" in exceptions
    tlrpc = (JAVA / "org/telegram/tgnet/TLRPC.java").read_text()
    read_contents = tlrpc[tlrpc.index("public static class TL_messages_readMessageContents"):]
    assert read_contents.count("icegramDialogId") == 1
    assert "sendRequestForGhostRead(TLObject object" in network
    assert "instanceof TLRPC.TL_messages_readHistory" in network
    assert "instanceof TLRPC.TL_channels_readHistory" in network
    assert "allowGhostReadReceipt" in network
    assert "allow-next" not in network
    assert "captureStoryViewToken(currentAccount, object)" in network
    print("PASS: callback and delayed-read integration guards")


if __name__ == "__main__":
    main()
