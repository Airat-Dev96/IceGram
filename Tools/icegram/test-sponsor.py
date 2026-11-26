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


if __name__ == "__main__":
    main()
