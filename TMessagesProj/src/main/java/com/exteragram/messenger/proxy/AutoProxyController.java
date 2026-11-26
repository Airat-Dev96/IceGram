/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.proxy;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;

import org.icegram.messenger.IcegramConfig;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.utils.proxy.ProxySettings;

import com.exteragram.messenger.utils.chats.ChatUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoProxyController {
    public static final String SOURCE_CHANNEL = "ProxyMTProto";
    public static final int MAX_PARALLEL_CHECKS = 5;
    private static final int SOURCE_MESSAGE_LIMIT = 100;
    private static final long SOURCE_TIMEOUT_MILLIS = 60_000L;
    private static final long CHECK_TIMEOUT_MILLIS = 15_000L;
    private static final long AUTO_SWITCH_THROTTLE_MILLIS = 5 * 60_000L;
    private static final String LAST_AUTO_SWITCH_TIME = "auto_proxy_last_switch_time";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:(?:https?://|tg://|tg:)[^\\s<>\"']+)", Pattern.CASE_INSENSITIVE);

    private static final AutoProxyController INSTANCE = new AutoProxyController();

    private final AtomicBoolean refreshInProgress = new AtomicBoolean();
    private long lastAutomaticSwitchTime = -1L;

    public interface Callback {
        void onComplete(Result result);
    }

    public static final class Result {
        public final boolean sourceAvailable;
        public final int discovered;
        public final int checked;
        public final int available;
        public final int removed;
        public final boolean switched;

        private Result(boolean sourceAvailable, int discovered, int checked, int available, int removed, boolean switched) {
            this.sourceAvailable = sourceAvailable;
            this.discovered = discovered;
            this.checked = checked;
            this.available = available;
            this.removed = removed;
            this.switched = switched;
        }
    }

    public static AutoProxyController getInstance() {
        return INSTANCE;
    }

    private AutoProxyController() {
    }

    public void refresh(Callback callback) {
        if (callback == null) {
            return;
        }
        if (!refreshInProgress.compareAndSet(false, true)) {
            callback.onComplete(new Result(false, 0, 0, 0, 0, false));
            return;
        }

        final int account = UserConfig.selectedAccount;
        AndroidUtilities.runOnUIThread(() -> {
            ProxyController controller = ProxyController.getInstance();
            SharedConfig.ProxyInfo initialProxy = controller.getCurrentProxy();
            boolean initiallyEnabled = controller.isProxyEnabled();
            new SourceLoader(account, (messages, error) -> {
                if (error != null || messages == null) {
                    refreshInProgress.set(false);
                    callback.onComplete(new Result(false, 0, 0, 0, 0, false));
                    return;
                }

                ArrayList<ProxySettings> settings = extractProxies(messages.messages, IcegramConfig.getAutoProxyCount());
                new CandidateChecker(account, settings, initialProxy, initiallyEnabled, callback).start();
            }).start();
        });
    }

    // Source resolution, request callbacks and proxy results are serialized on the UI thread.
    private static final class SourceLoader {
        private final int account;
        private final SourceCallback callback;
        private final Runnable timeout = this::onTimeout;
        private boolean completed;
        private int requestId;

        private SourceLoader(int account, SourceCallback callback) {
            this.account = account;
            this.callback = callback;
        }

        private void start() {
            AndroidUtilities.runOnUIThread(timeout, SOURCE_TIMEOUT_MILLIS);
            try {
                ChatUtils.getInstance(account).resolveChannel(SOURCE_CHANNEL, chat ->
                        AndroidUtilities.runOnUIThread(() -> onChannelResolved(chat)));
            } catch (Throwable e) {
                FileLog.e(e);
                complete(null, createError(500, "SOURCE_REQUEST_FAILED"));
            }
        }

        private void onChannelResolved(TLRPC.Chat chat) {
            if (completed) {
                return;
            }
            if (chat == null || chat.access_hash == 0L) {
                complete(null, createError(400, "SOURCE_CHANNEL_UNAVAILABLE"));
                return;
            }

            TLRPC.TL_messages_getHistory request = new TLRPC.TL_messages_getHistory();
            TLRPC.TL_inputPeerChannel peer = new TLRPC.TL_inputPeerChannel();
            peer.channel_id = chat.id;
            peer.access_hash = chat.access_hash;
            request.peer = peer;
            request.limit = SOURCE_MESSAGE_LIMIT;
            try {
                requestId = AccountInstance.getInstance(account).getConnectionsManager().sendRequest(request, (response, error) ->
                        AndroidUtilities.runOnUIThread(() -> {
                            if (error != null || !(response instanceof TLRPC.messages_Messages)) {
                                complete(null, error != null ? error : createError(500, "SOURCE_REQUEST_FAILED"));
                            } else {
                                complete((TLRPC.messages_Messages) response, null);
                            }
                        }));
            } catch (Throwable e) {
                FileLog.e(e);
                complete(null, createError(500, "SOURCE_REQUEST_FAILED"));
            }
        }

        private void onTimeout() {
            if (!completed && requestId != 0) {
                AccountInstance.getInstance(account).getConnectionsManager().cancelRequest(requestId, false);
            }
            complete(null, createError(408, "SOURCE_TIMEOUT"));
        }

        private void complete(TLRPC.messages_Messages messages, TLRPC.TL_error error) {
            if (completed) {
                return;
            }
            completed = true;
            AndroidUtilities.cancelRunOnUIThread(timeout);
            callback.onResult(messages, error);
        }
    }

    private static final class Candidate {
        private final SharedConfig.ProxyInfo result;
        private final SharedConfig.ProxyInfo storedAtStart;
        private boolean completed;
        private Runnable timeout;

        private Candidate(ProxySettings settings, SharedConfig.ProxyInfo storedAtStart) {
            result = new SharedConfig.ProxyInfo(settings);
            this.storedAtStart = storedAtStart;
        }
    }

    private final class CandidateChecker {
        private final int account;
        private final ArrayList<Candidate> candidates;
        private final int discovered;
        private final SharedConfig.ProxyInfo initialProxy;
        private final boolean initiallyEnabled;
        private final Callback callback;
        private int nextIndex;
        private int inFlight;
        private int checked;
        private int available;
        private boolean pumping;
        private boolean completed;

        private CandidateChecker(int account, ArrayList<ProxySettings> settings, SharedConfig.ProxyInfo initialProxy,
                                 boolean initiallyEnabled, Callback callback) {
            this.account = account;
            this.discovered = settings.size();
            this.initialProxy = initialProxy;
            this.initiallyEnabled = initiallyEnabled;
            this.callback = callback;

            ProxyController controller = ProxyController.getInstance();
            Map<String, SharedConfig.ProxyInfo> storedByKey = new LinkedHashMap<>();
            for (SharedConfig.ProxyInfo stored : controller.getProxyList()) {
                if (stored.settings.getType() != ProxySettings.Type.MTPROTO) {
                    continue;
                }
                String key = getProxyKey(stored.settings);
                if (key == null) {
                    continue;
                }
                // A manual duplicate always takes precedence over automatic metadata.
                if (!storedByKey.containsKey(key) || !controller.isAutomaticProxy(stored)) {
                    storedByKey.put(key, stored);
                }
            }

            Map<String, Candidate> unique = new LinkedHashMap<>();
            for (ProxySettings candidate : settings) {
                String key = getProxyKey(candidate);
                SharedConfig.ProxyInfo stored = storedByKey.get(key);
                if (stored == null || controller.isAutomaticProxy(stored)) {
                    unique.put(key, new Candidate(candidate, stored));
                }
            }
            // Previously collected proxies must also be checked, even if absent from recent posts.
            for (Map.Entry<String, SharedConfig.ProxyInfo> entry : storedByKey.entrySet()) {
                SharedConfig.ProxyInfo stored = entry.getValue();
                if (controller.isAutomaticProxy(stored) && !unique.containsKey(entry.getKey())) {
                    unique.put(entry.getKey(), new Candidate(stored.settings, stored));
                }
            }
            candidates = new ArrayList<>(unique.values());
        }

        private void start() {
            fillSlots();
        }

        private void fillSlots() {
            if (pumping || completed) {
                return;
            }
            // checkProxy may throw or call back synchronously; do not recurse or complete twice.
            pumping = true;
            try {
                while (inFlight < MAX_PARALLEL_CHECKS && nextIndex < candidates.size()) {
                    check(candidates.get(nextIndex++));
                }
            } finally {
                pumping = false;
            }

            if (inFlight == 0 && nextIndex >= candidates.size()) {
                completed = true;
                applyResults();
            }
        }

        private void check(Candidate candidate) {
            inFlight++;
            candidate.timeout = () -> finish(candidate, -1L);
            AndroidUtilities.runOnUIThread(candidate.timeout, CHECK_TIMEOUT_MILLIS);
            try {
                AccountInstance.getInstance(account).getConnectionsManager().checkProxy(candidate.result.settings, time ->
                        AndroidUtilities.runOnUIThread(() -> finish(candidate, time)));
            } catch (Throwable e) {
                FileLog.e(e);
                finish(candidate, -1L);
            }
        }

        private void finish(Candidate candidate, long ping) {
            if (candidate.completed) {
                return;
            }
            candidate.completed = true;
            AndroidUtilities.cancelRunOnUIThread(candidate.timeout);
            SharedConfig.ProxyInfo proxyInfo = candidate.result;
            proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
            proxyInfo.available = ping >= 0;
            proxyInfo.ping = Math.max(0L, ping);
            if (proxyInfo.available) {
                available++;
            }
            checked++;
            inFlight--;
            fillSlots();
        }

        private void applyResults() {
            ProxyController controller = ProxyController.getInstance();
            boolean removeDead = IcegramConfig.isAutoProxyRemoveDeadEnabled();
            int maxPing = IcegramConfig.getAutoProxyMaxPing();
            ArrayList<SharedConfig.ProxyInfo> toRemove = new ArrayList<>();
            SharedConfig.ProxyInfo best = null;
            for (Candidate candidate : candidates) {
                SharedConfig.ProxyInfo result = candidate.result;
                boolean healthy = result.available && result.ping <= maxPing;
                SharedConfig.ProxyInfo stored = candidate.storedAtStart;
                if (stored != null) {
                    // Never resurrect deleted proxies or overwrite manual edits made during the check.
                    if (!controller.getProxyList().contains(stored) || !controller.isAutomaticProxy(stored)
                            || !stored.settings.equals(result.settings)) {
                        continue;
                    }
                } else {
                    if (!result.available || (removeDead && !healthy)) {
                        continue;
                    }
                    // A proxy may have been added manually while this refresh was running.
                    stored = findStoredProxy(result.settings);
                    if (stored == null) {
                        stored = controller.addAutomaticProxy(result);
                    }
                    if (!controller.isAutomaticProxy(stored)) {
                        continue;
                    }
                }

                stored.available = result.available;
                stored.ping = result.ping;
                stored.availableCheckTime = result.availableCheckTime;
                // The checking flag belongs to any independent check started by the regular proxy UI.
                if (removeDead && !healthy) {
                    toRemove.add(stored);
                } else if (healthy && !stored.checking && (best == null || stored.ping < best.ping
                        || (stored.ping == best.ping && stored == controller.getCurrentProxy()))) {
                    best = stored;
                }
            }

            // Switch before deleting a bad current automatic proxy to avoid disabling a valid replacement.
            boolean switched = switchToBestAutomaticProxy(best, initialProxy, initiallyEnabled);
            int removed = 0;
            for (SharedConfig.ProxyInfo stored : toRemove) {
                if (controller.isAutomaticProxy(stored)) {
                    controller.deleteProxy(stored);
                    removed++;
                }
            }
            controller.saveProxyList();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            if (switched) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);
            }
            refreshInProgress.set(false);
            callback.onComplete(new Result(true, discovered, checked, available, removed, switched));
        }
    }

    private SharedConfig.ProxyInfo findStoredProxy(ProxySettings settings) {
        ProxyController controller = ProxyController.getInstance();
        String key = getProxyKey(settings);
        SharedConfig.ProxyInfo automatic = null;
        for (SharedConfig.ProxyInfo stored : controller.getProxyList()) {
            if (key != null && stored.settings.getType() == ProxySettings.Type.MTPROTO
                    && key.equals(getProxyKey(stored.settings))) {
                if (!controller.isAutomaticProxy(stored)) {
                    return stored;
                }
                automatic = stored;
            }
        }
        return automatic;
    }

    private boolean switchToBestAutomaticProxy(SharedConfig.ProxyInfo best, SharedConfig.ProxyInfo initialProxy,
                                              boolean initiallyEnabled) {
        if (!IcegramConfig.isAutoProxyAutoSwitchEnabled() || best == null) {
            return false;
        }

        ProxyController controller = ProxyController.getInstance();
        SharedConfig.ProxyInfo current = controller.getCurrentProxy();
        if ((current != null && !controller.isAutomaticProxy(current))
                || current != initialProxy || controller.isProxyEnabled() != initiallyEnabled
                || (current == best && controller.isProxyEnabled())) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        if (lastAutomaticSwitchTime >= 0L && now - lastAutomaticSwitchTime < AUTO_SWITCH_THROTTLE_MILLIS) {
            return false;
        }

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        long wallTime = System.currentTimeMillis();
        long persistedSwitchTime = preferences.getLong(LAST_AUTO_SWITCH_TIME, 0L);
        if (persistedSwitchTime > 0L && wallTime - persistedSwitchTime < AUTO_SWITCH_THROTTLE_MILLIS) {
            if (wallTime < persistedSwitchTime) {
                // Recover from a backwards clock adjustment without bypassing the cooldown.
                preferences.edit().putLong(LAST_AUTO_SWITCH_TIME, wallTime).apply();
            }
            return false;
        }

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("proxy_enabled", true);
        editor.putLong(LAST_AUTO_SWITCH_TIME, wallTime);
        best.settings.toSharedPreferences(editor);
        editor.apply();
        controller.setCurrentProxy(best);
        ConnectionsManager.setProxySettings(true, best.settings);
        lastAutomaticSwitchTime = now;
        return true;
    }

    private static ArrayList<ProxySettings> extractProxies(ArrayList<TLRPC.Message> messages, int limit) {
        LinkedHashMap<String, ProxySettings> unique = new LinkedHashMap<>();
        if (messages == null) {
            return new ArrayList<>();
        }
        for (TLRPC.Message message : messages) {
            if (message == null) {
                continue;
            }
            String text = message.message;
            collectTextLinks(text, unique, limit);
            if (message.entities != null && text != null) {
                for (TLRPC.MessageEntity entity : message.entities) {
                    if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                        collectLink(((TLRPC.TL_messageEntityTextUrl) entity).url, unique, limit);
                    } else if (entity instanceof TLRPC.TL_messageEntityUrl) {
                        int start = entity.offset;
                        int end = start + entity.length;
                        if (start >= 0 && end <= text.length() && start < end) {
                            collectLink(text.substring(start, end), unique, limit);
                        }
                    }
                }
            }
            if (message.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
                TLRPC.TL_replyInlineMarkup markup = (TLRPC.TL_replyInlineMarkup) message.reply_markup;
                for (TL_keyboard.KeyboardInlineButtonRow row : markup.rows) {
                    for (TL_keyboard.KeyboardInlineButton button : row.buttons) {
                        if (button.type instanceof TL_keyboard.TL_inlineButtonTypeUrl) {
                            collectLink(((TL_keyboard.TL_inlineButtonTypeUrl) button.type).url, unique, limit);
                        } else if (button.type instanceof TL_keyboard.TL_inlineButtonTypeUrlAuth) {
                            collectLink(((TL_keyboard.TL_inlineButtonTypeUrlAuth) button.type).url, unique, limit);
                        }
                    }
                }
            }
            if (unique.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static void collectTextLinks(String text, Map<String, ProxySettings> unique, int limit) {
        if (TextUtils.isEmpty(text) || unique.size() >= limit) {
            return;
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find() && unique.size() < limit) {
            collectLink(matcher.group(), unique, limit);
        }
    }

    private static void collectLink(String rawLink, Map<String, ProxySettings> unique, int limit) {
        if (TextUtils.isEmpty(rawLink) || unique.size() >= limit) {
            return;
        }
        String link = trimUrlPunctuation(rawLink.trim());
        try {
            ProxySettings settings = ProxySettings.fromUri(Uri.parse(link));
            if (settings == null || settings.getType() != ProxySettings.Type.MTPROTO
                    || TextUtils.isEmpty(settings.getSecret()) || !settings.isValid()) {
                return;
            }
            String key = getProxyKey(settings);
            if (!unique.containsKey(key)) {
                unique.put(key, settings);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String trimUrlPunctuation(String link) {
        while (!TextUtils.isEmpty(link)) {
            char last = link.charAt(link.length() - 1);
            if (last == '.' || last == ',' || last == '!' || last == '?' || last == ':'
                    || last == ';' || last == ')' || last == ']' || last == '}') {
                link = link.substring(0, link.length() - 1);
            } else {
                break;
            }
        }
        return link;
    }

    private static String getProxyKey(ProxySettings settings) {
        if (settings == null || TextUtils.isEmpty(settings.getAddress())) {
            return null;
        }
        return settings.getAddress().toLowerCase(Locale.US) + "\u0000"
                + settings.getPort() + "\u0000" + settings.getSecret();
    }

    private static TLRPC.TL_error createError(int code, String text) {
        TLRPC.TL_error error = new TLRPC.TL_error();
        error.code = code;
        error.text = text;
        return error;
    }

    private interface SourceCallback {
        void onResult(TLRPC.messages_Messages messages, TLRPC.TL_error error);
    }
}
