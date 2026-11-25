"""Exercise artifact checks with synthetic ZIP files, not a compiled Android app."""
import io
from pathlib import Path
import runpy
import struct
import tempfile
import unittest
import zipfile

VERIFY = runpy.run_path(str(Path(__file__).with_name("verify-apk.py")))


class ContentsTest(unittest.TestCase):
    def make_apk(self, path, *, missing_library=None, extra_abi=False, maps_descriptor=None, wrong_python=False):
        header = bytearray(20)
        header[:6] = b"\x7fELF\x02\x01"
        header[16:18] = (3).to_bytes(2, "little")
        header[18:20] = (183).to_bytes(2, "little")
        sdk_data = io.BytesIO()
        with zipfile.ZipFile(sdk_data, "w") as sdk:
            for name in ("base_plugin.pyc", "hook_utils.pyc", "_sdk_version.pyc"):
                sdk.writestr(name, bytes.fromhex("cb0d0d0a" if wrong_python else "a70d0d0a") + b"fixture")
        with zipfile.ZipFile(path, "w") as apk:
            apk.writestr("AndroidManifest.xml", b"fixture")
            apk.writestr("resources.arsc", b"fixture")
            apk.writestr("classes.dex", b"dex\n035\0" + (maps_descriptor or b""))
            apk.writestr("assets/chaquopy/stdlib-common.imy", b"fixture")
            apk.writestr("assets/plugins_pysdk/v.txt", b"1.0")
            apk.writestr("assets/plugins_pysdk/sdk-arm64-v8a.zip", sdk_data.getvalue())
            for library in VERIFY["REQUIRED_LIBRARIES"] - {missing_library}:
                apk.writestr("lib/arm64-v8a/" + library, header)
            if extra_abi:
                apk.writestr("lib/x86_64/libextra.so", header)

    def test_full_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.apk"
            self.make_apk(path)
            self.assertEqual(VERIFY["check_contents"](path, "arm64")["abi"], "arm64-v8a")

    def test_reject_incomplete_or_wrong_contents(self):
        cases = (
            *({"missing_library": name} for name in sorted(VERIFY["REQUIRED_LIBRARIES"])),
            {"extra_abi": True}, {"wrong_python": True},
            {"maps_descriptor": b"Lcom/google/android/gms/maps/GoogleMap;"},
            {"maps_descriptor": b"Lcom/google/android/gms/maps/model/LatLng;"},
            {"maps_descriptor": b"Lorg/telegram/messenger/GoogleMapsProvider;"},
        )
        with tempfile.TemporaryDirectory() as directory:
            for index, options in enumerate(cases):
                with self.subTest(options=options):
                    path = Path(directory) / f"fixture-{index}.apk"
                    self.make_apk(path, **options)
                    with self.assertRaises(ValueError):
                        VERIFY["check_contents"](path, "arm64")


class LauncherManifestTest(unittest.TestCase):
    # aapt dump xmltree text format. Not a compiled manifest/runtime fixture.
    TREE = '''N: android=http://schemas.android.com/apk/res/android
  E: manifest (line=1)
    A: package="org.icegram.messenger" (Raw: "org.icegram.messenger")
    E: application (line=2)
      E: activity (line=3)
        A: android:name(0x01010003)="org.telegram.ui.LaunchActivity" (Raw: "org.telegram.ui.LaunchActivity")
      E: activity-alias (line=4)
        A: android:name(0x01010003)="org.icegram.messenger.DefaultIcon" (Raw: "org.icegram.messenger.DefaultIcon")
        A: android:enabled(0x0101000e)=(type 0x12)0xffffffff
        A: android:exported(0x01010010)=(type 0x12)0xffffffff
        A: android:targetActivity(0x01010202)="org.telegram.ui.LaunchActivity" (Raw: "org.telegram.ui.LaunchActivity")
        E: intent-filter (line=5)
          E: action (line=6)
            A: android:name(0x01010003)="android.intent.action.MAIN" (Raw: "android.intent.action.MAIN")
          E: category (line=7)
            A: android:name(0x01010003)="android.intent.category.LAUNCHER" (Raw: "android.intent.category.LAUNCHER")
'''

    def test_enabled_alias_and_target(self):
        result = VERIFY["check_launcher_manifest"](self.TREE)
        self.assertEqual(result["alias"], "org.icegram.messenger.DefaultIcon")
        self.assertTrue(result["main_launcher_filter"])
        VERIFY["check_launcher_manifest"](self.TREE.replace("0xffffffff", "0x1"))

    def test_invalid_launchers_rejected(self):
        cases = [
            self.TREE.replace("org.icegram.messenger.DefaultIcon", "org.other.DefaultIcon"),
            self.TREE.replace("android:enabled(0x0101000e)=(type 0x12)0xffffffff",
                              "android:enabled(0x0101000e)=(type 0x12)0x0"),
            self.TREE.replace("android:exported(0x01010010)=(type 0x12)0xffffffff",
                              "android:exported(0x01010010)=(type 0x12)0x0"),
            self.TREE.replace('android:targetActivity(0x01010202)="org.telegram.ui.LaunchActivity"',
                              'android:targetActivity(0x01010202)="org.other.Activity"'),
            self.TREE.replace('android:name(0x01010003)="org.telegram.ui.LaunchActivity"',
                              'android:name(0x01010003)="org.other.Activity"'),
            self.TREE.replace("android.intent.action.MAIN", "android.intent.action.VIEW"),
            self.TREE.replace("android.intent.category.LAUNCHER", "android.intent.category.DEFAULT"),
            self.TREE.replace("          E: category", "        E: intent-filter (line=7)\n          E: category"),
            self.TREE.replace("      E: activity (line=3)",
                              "      A: android:enabled(0x0101000e)=(type 0x12)0x0\n      E: activity (line=3)"),
            self.TREE.replace("      E: activity-alias (line=4)",
                              "        A: android:enabled(0x0101000e)=(type 0x12)0x0\n      E: activity-alias (line=4)"),
            self.TREE.replace("(type 0x12)0xffffffff", "@0x7f010001"),
            "",
        ]
        alias = self.TREE[self.TREE.index("      E: activity-alias"):]
        cases += [self.TREE + alias,
                  self.TREE + alias.replace("org.icegram.messenger.DefaultIcon", "org.other.Alias")]
        for index, manifest in enumerate(cases):
            with self.subTest(index=index), self.assertRaises(ValueError):
                VERIFY["check_launcher_manifest"](manifest)


class EmbeddedHookTest(unittest.TestCase):
    def elf(self, *, bits=2, missing=None, undefined=None, text=b"machine-code",
            rodata=b"registration-tables", symbols=True, function_address=0x1000,
            text_flags=6):
        """Synthetic ELF section/symbol fixture, not executable native code."""
        functions = sorted(VERIFY["EMBEDDED_HOOK_FUNCTIONS"] - {missing})
        strings = b"\0"
        entries = b"\0" * (16 if bits == 1 else 24)
        for name in functions:
            offset = len(strings)
            strings += name.encode() + b"\0"
            section = 0 if name == undefined else 1
            entries += (struct.pack("<IIIBBH", offset, function_address, 4, 2, 0, section) if bits == 1
                        else struct.pack("<IBBHQQ", offset, 2, 0, section, function_address, 4))
        names = b"\0.text\0.rodata\0.strtab\0.symtab\0.shstrtab\0"
        records = [(0, 0, b"", 0, 0, 0), (1, text_flags, text, 0, 0, 0),
                   (1, 2, rodata, 0, 0, 0), (3, 0, strings, 0, 0, 0),
                   (2, 0, entries if symbols else b"", 3, 1, 16 if bits == 1 else 24),
                   (3, 0, names, 0, 0, 0)]
        section_names = ["", ".text", ".rodata", ".strtab", ".symtab", ".shstrtab"]
        data = bytearray(64)
        data[:6] = b"\x7fELF" + bytes([bits, 1])
        headers = []
        for name, (kind, flags, content, link, info, entry_size) in zip(section_names, records):
            offset = len(data)
            data.extend(content)
            name_offset = names.index(name.encode() + b"\0")
            headers.append((name_offset, kind, flags, 0x1000, offset, len(content), link, info, 1, entry_size))
        table_offset = len(data)
        fmt = "<IIIIIIIIII" if bits == 1 else "<IIQQQQIIQQ"
        for header in headers:
            data.extend(struct.pack(fmt, *header))
        struct.pack_into("<I" if bits == 1 else "<Q", data, 32 if bits == 1 else 40, table_offset)
        struct.pack_into("<HHH", data, 46 if bits == 1 else 58, struct.calcsize(fmt), len(headers), 5)
        return bytes(data)

    def test_static_hooks_and_stripped_code_match(self):
        for bits in (1, 2):
            with self.subTest(bits=bits):
                result = VERIFY["check_embedded_hook_binary"](self.elf(bits=bits, symbols=False), self.elf(bits=bits))
                self.assertEqual(set(result["functions_verified"]), VERIFY["EMBEDDED_HOOK_FUNCTIONS"])
                self.assertEqual(set(result["allocated_section_sha256"]), {".text", ".rodata"})

    def test_missing_or_undefined_hook_rejected(self):
        for bits in (1, 2):
            for name in VERIFY["EMBEDDED_HOOK_FUNCTIONS"]:
                for option in ("missing", "undefined"):
                    with self.subTest(bits=bits, name=name, option=option):
                        with self.assertRaisesRegex(ValueError, "Missing statically linked hook"):
                            VERIFY["check_embedded_hook_binary"](self.elf(bits=bits), self.elf(bits=bits, **{option: name}))

    def test_substitution_or_absent_symbols_rejected(self):
        for bits in (1, 2):
            for options in ({"text": b"replaced"}, {"rodata": b"replaced"}, {"symbols": False}):
                with self.subTest(bits=bits, options=options), self.assertRaises(ValueError):
                    native_options = {"symbols": False} if options.get("symbols") is False else {}
                    VERIFY["check_embedded_hook_binary"](
                        self.elf(bits=bits, **options), self.elf(bits=bits, **native_options))

    def test_symbols_must_cover_real_executable_bytes(self):
        for bits in (1, 2):
            for options in ({"function_address": 0x2000}, {"text_flags": 2}, {"text_flags": 4}):
                with self.subTest(bits=bits, options=options):
                    data = self.elf(bits=bits, **options)
                    with self.assertRaisesRegex(ValueError, "Missing statically linked hook"):
                        VERIFY["check_embedded_hook_binary"](data, data)
        thumb = self.elf(bits=1, function_address=0x1001)
        VERIFY["check_embedded_hook_binary"](thumb, thumb)

    def test_changed_section_metadata_rejected(self):
        for bits in (1, 2):
            with self.subTest(bits=bits), self.assertRaisesRegex(ValueError, "differs"):
                VERIFY["check_embedded_hook_binary"](
                    self.elf(bits=bits, text_flags=2), self.elf(bits=bits))

    def test_malformed_section_tables_rejected(self):
        for data in (b"", b"\x7fELF" + b"\0" * 60, self.elf()[:100], self.elf()[:-1]):
            with self.subTest(length=len(data)), self.assertRaises(ValueError):
                VERIFY["elf_sections"](data)

    def test_native_outputs_required(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "synthetic.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("lib/arm64-v8a/libtmessages.49.so", self.elf())
            with self.assertRaisesRegex(ValueError, "native build outputs absent"):
                VERIFY["check_embedded_hooks"](apk, "arm64", [Path(directory)])
            target = Path(directory) / "obj/arm64-v8a/libtmessages.49.so"
            target.parent.mkdir(parents=True)
            target.write_bytes(self.elf())
            result = VERIFY["check_embedded_hooks"](apk, "arm64", [Path(directory)])
            self.assertIn("statically linked", result["layout"])


if __name__ == "__main__":
    unittest.main()
