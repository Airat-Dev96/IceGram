"""Verify the actual release artifact; does not replace tests on an Android device."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "org.icegram.messenger"
ARCHITECTURES = {"arm64": ("arm64-v8a", 2, 183), "armv7": ("armeabi-v7a", 1, 40)}
REQUIRED_LIBRARIES = {
    "libtmessages.49.so", "libtwidget.so", "libpython3.11.so",
    "libc++_shared.so", "libshadowhook.so",
    "libextera_analyzer_loader.so", "libextera_analyzer.so",
}
EMBEDDED_HOOK_FUNCTIONS = {
    "ExteraHookJniOnLoad", "ExteraHookJniOnUnload",
    "_ZN7lsplant2v24InitEP7_JNIEnvRKNS0_8InitInfoE",
    "_ZN7lsplant2v24HookEP7_JNIEnvP8_jobjectS4_S4_",
    "_ZN7lsplant2v26UnHookEP7_JNIEnvP8_jobject",
    "_ZN7lsplant2v28IsHookedEP7_JNIEnvP8_jobject",
    "_ZN7lsplant2v210DeoptimizeEP7_JNIEnvP8_jobject",
    "_ZN7lsplant2v220MakeClassInheritableEP7_JNIEnvP7_jclass",
    "_ZN7lsplant2v214CollectJitCodeEv",
    "_ZN7lsplant2v218RedirectToOriginalEP7_JNIEnvP8_jobjectS4_S4_",
    "_ZN7lsplant2v28RetargetEP7_JNIEnvP8_jobjectS4_",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def command(arguments):
    result = subprocess.run(arguments, capture_output=True, check=False)
    require(result.returncode == 0, Path(arguments[0]).name + " failed; output hidden")
    return result.stdout


def elf_sections(data):
    """Read little-endian ELF section tables, for both supported Android ABIs."""
    require(len(data) >= 64 and data[:4] == b"\x7fELF" and data[5] == 1,
            "Invalid ELF section input")
    bits = data[4]
    require(bits in (1, 2), "Unsupported ELF class")
    offset = struct.unpack_from("<I" if bits == 1 else "<Q", data, 32 if bits == 1 else 40)[0]
    size, count, names_index = struct.unpack_from("<HHH", data, 46 if bits == 1 else 58)
    fmt = "<IIIIIIIIII" if bits == 1 else "<IIQQQQIIQQ"
    require(size == struct.calcsize(fmt) and count > 0 and names_index < count
            and offset + size * count <= len(data), "Invalid ELF section table")
    sections = [struct.unpack_from(fmt, data, offset + i * size) for i in range(count)]

    def section_bytes(section):
        start, length = section[4:6]
        require(start + length <= len(data), "ELF section outside file")
        return data[start:start + length]

    names = section_bytes(sections[names_index])
    result = {}
    for index, section in enumerate(sections):
        start = section[0]
        end = names.find(b"\0", start)
        require(start < len(names) and end >= start, "Invalid ELF section name")
        name = names[start:end].decode("ascii")
        if name:
            require(name not in result, "Duplicate ELF section name")
            result[name] = (index, section, b"" if section[1] == 8 else section_bytes(section))
    return bits, sections, result


def check_embedded_hook_binary(packaged, unstripped):
    bits, sections, original = elf_sections(unstripped)
    apk_bits, _, actual = elf_sections(packaged)
    require(bits == apk_bits, "Embedded hook ELF class mismatch")
    require(".symtab" in original and ".text" in original and ".rodata" in original,
            "Unstripped embedded hook symbol/code sections missing")
    # Stripping may remove symbols/debug info, but must not change executable or read-only data.
    hashes = {}
    for name, (_, header, content) in original.items():
        if header[2] & 2 and header[1] != 8:  # SHF_ALLOC, excluding SHT_NOBITS (.bss)
            require(name in actual and content == actual[name][2]
                    and all(header[index] == actual[name][1][index] for index in (1, 2, 3, 5)),
                    "Packaged embedded hook differs from checked native build: " + name)
            hashes[name] = hashlib.sha256(content).hexdigest()
    sym_header, symbols = original[".symtab"][1:]
    link = sym_header[6]
    require(link < len(sections) and sections[link][1] == 3, "Invalid ELF symbol string table")
    start, length = sections[link][4:6]
    require(start + length <= len(unstripped), "ELF symbol strings outside file")
    strings = unstripped[start:start + length]
    size = 16 if bits == 1 else 24
    require(sym_header[9] == size and len(symbols) % size == 0, "Invalid ELF symbol table")
    functions = set()
    for position in range(0, len(symbols), size):
        if bits == 1:
            name, value, length, info, _, section = struct.unpack_from("<IIIBBH", symbols, position)
        else:
            name, info, _, section, value, length = struct.unpack_from("<IBBHQQ", symbols, position)
        if info & 15 != 2 or not length or not (0 < section < len(sections)):
            continue
        code = sections[section]
        # A defined function must occupy real, allocated executable bytes.
        # ARM32 Thumb symbols have bit zero set in st_value.
        address = value & ~1 if bits == 1 else value
        if code[1] == 8 or code[2] & 6 != 6 or not (
                code[3] <= address and address + length <= code[3] + code[5]):
            continue
        end = strings.find(b"\0", name)
        require(name < len(strings) and end >= name, "Invalid ELF function name")
        functions.add(strings[name:end].decode("ascii"))
    missing = EMBEDDED_HOOK_FUNCTIONS - functions
    require(not missing, "Missing statically linked hook functions: " + ", ".join(sorted(missing)))
    require(bool(original[".text"][2]) and bool(original[".rodata"][2]), "Empty native code/data")
    return {"functions_verified": sorted(EMBEDDED_HOOK_FUNCTIONS), "allocated_section_sha256": hashes}


def check_embedded_hooks(path, architecture, native_dirs):
    abi = ARCHITECTURES[architecture][0]
    with zipfile.ZipFile(path) as apk:
        packaged = apk.read(f"lib/{abi}/libtmessages.49.so")
    candidates = sorted({candidate for directory in native_dirs if directory.exists()
                         for candidate in directory.rglob("libtmessages.49.so")
                         if candidate.parent.name == abi})
    errors = []
    for candidate in candidates:
        try:
            report = check_embedded_hook_binary(packaged, candidate.read_bytes())
            report["layout"] = "exteraHook and LSPlant statically linked into libtmessages.49.so"
            return report
        except ValueError as error:
            errors.append(str(error))
    raise ValueError("No matching verified unstripped hook binary (" + str(len(candidates))
                     + " candidates): " + ("; ".join(sorted(set(errors))) or "native build outputs absent"))


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
        missing = REQUIRED_LIBRARIES - {Path(name).name for name in libraries}
        require(not missing, "Missing full/plugin native libraries: " + ", ".join(sorted(missing)))
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
    parser.add_argument("--native-build-dir", type=Path, action="append",
                        help="Native build outputs with unstripped embedded hook symbols")
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
    report["embedded_hooks"] = check_embedded_hooks(args.apk, args.abi, args.native_build_dir or [
        ROOT / "TMessagesProj/.cxx", ROOT / "TMessagesProj/build/intermediates/cxx",
    ])
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
