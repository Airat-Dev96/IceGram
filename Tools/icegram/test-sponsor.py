"""Compile the actual sponsor visibility predicates with lightweight fixtures."""
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    position = opening + 1
    while depth:
        if source[position] == "{":
            depth += 1
        elif source[position] == "}":
            depth -= 1
        position += 1
    return source[start:position]


def main():
    if not shutil.which("javac") or not shutil.which("java"):
        raise SystemExit("JDK 17 is required for sponsor visibility tests")
    source = (JAVA / "org/telegram/messenger/MessagesController.java").read_text()
    hidden = method(source, "private boolean isHiddenProxySponsorDialog(")
    visible = method(source, "public boolean isPromoDialog(")
    fixture = """public class SponsorPolicyTest {
    static final int PROMO_TYPE_PROXY=0, PROMO_TYPE_PSA=1, PROMO_TYPE_OTHER=2;
    int promoDialogType;
    long promoDialogId;
    boolean isLeftPromoChannel;
    Dialog promoDialog;
    static class Dialog { long id; Dialog(long value) { id=value; } }
    static class IcegramConfig {
        static boolean enabled=true;
        static boolean isHideProxySponsorEnabled() { return enabled; }
    }
    static void check(boolean result) { if (!result) throw new AssertionError("Sponsor policy"); }
    public static void main(String[] args) {
        SponsorPolicyTest policy=new SponsorPolicyTest();
        policy.promoDialogType=PROMO_TYPE_PROXY;
        policy.promoDialogId=-123;
        policy.promoDialog=new Dialog(-123);
        check(policy.isHiddenProxySponsorDialog(-123));
        check(!policy.isHiddenProxySponsorDialog(-456));
        check(!policy.isPromoDialog(-123, false));
        policy.promoDialogId=0;
        check(!policy.isHiddenProxySponsorDialog(0));
        policy.promoDialogId=-123;
        for (int type : new int[] {PROMO_TYPE_PSA, PROMO_TYPE_OTHER}) {
            policy.promoDialogType=type;
            check(!policy.isHiddenProxySponsorDialog(-123));
            check(policy.isPromoDialog(-123, false));
            check(!policy.isPromoDialog(-123, true));
            policy.isLeftPromoChannel=true;
            check(policy.isPromoDialog(-123, true));
            policy.isLeftPromoChannel=false;
        }
        policy.promoDialogType=PROMO_TYPE_PROXY;
        IcegramConfig.enabled=false;
        check(!policy.isHiddenProxySponsorDialog(-123));
        check(policy.isPromoDialog(-123, false));
        check(!policy.isPromoDialog(-456, false));
        policy.promoDialog=null;
        check(!policy.isPromoDialog(-123, false));
        System.out.println("PASS: real proxy sponsor / PSA / other visibility predicates");
    }
""" + hidden + "\n" + visible + "\n}"
    with tempfile.TemporaryDirectory(prefix="icegram-sponsor-") as directory:
        path = Path(directory) / "SponsorPolicyTest.java"
        path.write_text(fixture)
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", directory, str(path)], check=True)
        subprocess.run(["java", "-cp", directory, "SponsorPolicyTest"], check=True)
    assert "hiddenProxySponsor = res.proxy && IcegramConfig.isHideProxySponsorEnabled()" in source
    assert "if (!hiddenProxySponsor)" in source
    assert source.count("isHiddenProxySponsorDialog(") > 1
    assert "setProxySettings" not in hidden + visible
    config = (JAVA / "org/icegram/messenger/IcegramConfig.java").read_text()
    assert 'getBoolean("hide_proxy_sponsor", true)' in config
    print("PASS: sponsor defaults and RPC/list integration; proxy transport unchanged")

    channel_setting = 'getBoolean("hide_channel_sponsored_messages", false)'
    assert channel_setting in config
    assert '"hide_channel_sponsored_messages"' in config
    preferences = (JAVA / "com/exteragram/messenger/preferences/IcegramFeaturesPreferencesActivity.kt").read_text()
    assert "Item.HIDE_CHANNEL_SPONSORED" in preferences
    assert "IcegramConfig.isHideChannelSponsoredMessagesEnabled()" in preferences

    controller_method = method(source, "public SponsoredMessagesInfo getSponsoredMessages(long dialogId)")
    assert controller_method.index("isHideChannelSponsoredMessagesEnabled()") < controller_method.index("new TLRPC.TL_messages_getSponsoredMessages()")
    assert "return null;" in controller_method[:controller_method.index("new TLRPC.TL_messages_getSponsoredMessages()")]

    chat_activity = (JAVA / "org/telegram/ui/ChatActivity.java").read_text()
    add_ads = method(chat_activity, "private void addSponsoredMessages(boolean animated)")
    assert add_ads.index("isHideChannelSponsoredMessagesEnabled()") < add_ads.index("getSponsoredMessages(dialog_id)")
    assert "sponsoredMessagesAdded = true;" in add_ads
    assert "!IcegramConfig.isHideChannelSponsoredMessagesEnabled()" in chat_activity

    video_ads = (JAVA / "org/telegram/messenger/video/VideoAds.java").read_text()
    for signature in ("private void init(BulletinFactory bulletinFactory)", "private void load()",
                      "private void schedule()", "private void show()"):
        body = method(video_ads, signature)
        assert "isHideChannelSponsoredMessagesEnabled()" in body, signature
    load = method(video_ads, "private void load()")
    assert load.index("isHideChannelSponsoredMessagesEnabled()") < load.index("new TLRPC.TL_messages_getSponsoredMessages()")
    assert "cache.ads.clear();" in video_ads
    print("PASS: channel/bot sponsored requests and cached video ads are gated; PSA/proxy paths remain separate")


if __name__ == "__main__":
    main()
