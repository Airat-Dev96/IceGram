"""Verify the actual release artifact; does not replace tests on an Android device."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "org.icegram.messenger"
ARCHITECTURES = {"arm64": ("arm64-v8a", 2, 183), "armv7": ("armeabi-v7a", 1, 40)}
REQUIRED_LIBRARIES = {
    "libtmessages.49.so", "libtwidget.so", "libpython3.11.so",
    "libexterahook.so", "liblsplant.so", "libshadowhook.so",
    "libextera_analyzer_loader.so", "libextera_analyzer.so",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def command(arguments):
    result = subprocess.run(arguments, capture_output=True, check=False)
    require(result.returncode == 0, Path(arguments[0]).name + " failed; output hidden")
    return result.stdout


def check_contents(path, architecture):
    abi, elf_class, machine = ARCHITECTURES[architecture]
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        require(len(names) == len(set(names)), "Duplicate APK ZIP entries")
        require(apk.testzip() is None, "APK ZIP checksum failure")
        require({"AndroidManifest.xml", "resources.arsc", "classes.dex"} <= set(names),
                "Missing Android manifest, resources or DEX")
        require(not any(Path(name).name in {"API_KEYS", "google-services.json", "signing-config.json"}
                        or name.endswith((".jks", ".keystore")) for name in names),
                "Private build input accidentally packaged")
        libraries = [name for name in names if name.startswith("lib/") and name.endswith(".so")]
        require({name.split("/")[1] for name in libraries} == {abi}, "Wrong or extra native ABI")
        require(REQUIRED_LIBRARIES <= {Path(name).name for name in libraries},
                "Missing full/plugin native libraries")
        for name in libraries:
            with apk.open(name) as library:
                header = library.read(20)
            require(len(header) == 20 and header[:4] == b"\x7fELF"
                    and header[4] == elf_class and header[5] == 1
                    and int.from_bytes(header[16:18], "little") == 3
                    and int.from_bytes(header[18:20], "little") == machine,
                    "Wrong ELF architecture: " + name)
        require(any(name.startswith("assets/chaquopy/") for name in names),
                "Missing embedded Python assets")
        sdk_name = f"assets/plugins_pysdk/sdk-{abi}.zip"
        require(sdk_name in names and "assets/plugins_pysdk/v.txt" in names,
                "Missing plugin Python SDK")
        sdk_archives = {name for name in names if re.fullmatch(r"assets/plugins_pysdk/sdk-.+\.zip", name)}
        require(sdk_archives == {sdk_name}, "Wrong or extra SDK ABI")
        with zipfile.ZipFile(io.BytesIO(apk.read(sdk_name))) as sdk:
            require(sdk.testzip() is None, "Plugin SDK ZIP checksum failure")
            require({"base_plugin.pyc", "hook_utils.pyc", "_sdk_version.pyc"} <= set(sdk.namelist()),
                    "Incomplete plugin SDK")
            for name in sdk.namelist():
                if name.endswith(".pyc"):
                    with sdk.open(name) as module:
                        magic = module.read(4)
                    require(magic == bytes.fromhex("a70d0d0a"), "Plugin bytecode is not Python 3.11")
        for name in names:
            if re.fullmatch(r"classes(?:\d+)?\.dex", name):
                dex = apk.read(name)
                require(dex.startswith(b"dex\n"), "Invalid DEX header")
                require(b"Lcom/google/android/gms/maps/" not in dex
                        and b"Lorg/telegram/messenger/GoogleMapsProvider;" not in dex,
                        "Google Maps class descriptor remains in DEX")
    return {"abi": abi, "native_libraries": len(libraries), "python_sdk": "3.11"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--abi", required=True, choices=ARCHITECTURES)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--certificate", type=Path, help="Expected signing certificate in DER format")
    parser.add_argument("--build-tools", type=Path)
    parser.add_argument("--report", type=Path, default=ROOT / "ICEGRAM_APK_VERIFICATION.json")
    args = parser.parse_args()
    if args.apk is None:
        candidates = list((ROOT / "TMessagesProj/build/outputs/apk").rglob("*.apk"))
        require(len(candidates) == 1, "Expected exactly one release APK")
        args.apk = candidates[0]
    if args.build_tools is None:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        require(bool(sdk), "Set ANDROID_HOME or --build-tools")
        args.build_tools = Path(sdk) / "build-tools/36.0.0"
    report = check_contents(args.apk, args.abi)
    badging = command([str(args.build_tools / "aapt"), "dump", "badging", str(args.apk)]).decode()
    require(re.search(r"^package: name='" + re.escape(PACKAGE) + r"'", badging, re.M),
            "Wrong application ID")
    require("application-label:'icegram'" in badging, "Wrong default application name")
    require("name='org.icegram.messenger.DefaultIcon'" in badging, "Wrong launcher alias")
    require("application-debuggable" not in badging, "APK is debuggable")
    manifest = command([str(args.build_tools / "aapt"), "dump", "xmltree", str(args.apk),
                        "AndroidManifest.xml"]).decode()
    require(not any(value in manifest for value in (
        "com.google.android.geo.API_KEY", "com.google.android.maps.v2.API_KEY", "MAPS_RECEIVE")),
        "Maps configuration remains in packaged manifest")
    signature = command([str(args.build_tools / "apksigner"), "verify", "--verbose", "--print-certs",
                         str(args.apk)]).decode()
    require(re.search(r"Verified using v2 scheme .*: true", signature), "No verified APK v2 signature")
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", signature, re.M)
    require(len(digests) == 1, "Expected exactly one signing certificate")
    if args.certificate:
        certificate = args.certificate.read_bytes()
    else:
        alias = os.environ.get("ICEGRAM_KEY_ALIAS")
        require(bool(alias) and bool(os.environ.get("ICEGRAM_STORE_PASSWORD")),
                "Expected certificate or signing environment required")
        certificate = command([
            "keytool", "-exportcert", "-keystore", str(ROOT / "TMessagesProj/config/icegram.jks"),
            "-storetype", "JKS", "-alias", alias, "-storepass:env", "ICEGRAM_STORE_PASSWORD",
        ])
    require(digests[0].lower() == hashlib.sha256(certificate).hexdigest(), "Unexpected signing identity")
    digest = hashlib.sha256()
    with args.apk.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    report.update({
        "apk": args.apk.name, "bytes": args.apk.stat().st_size, "sha256": digest.hexdigest(),
        "package": PACKAGE, "signer_sha256": digests[0].lower(), "signature_v2_verified": True,
        "source_revision": os.environ.get("GITHUB_SHA"), "device_tests_completed": False,
        "maps_check": "No Maps metadata or original Google Maps class descriptors found",
    })
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("PASS: release APK, signing identity, single ABI, full native libraries and Python SDK.")
    print("Device/runtime tests are still required. SHA-256: " + digest.hexdigest())


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        raise SystemExit("APK verification failed: " + str(error)) from None
