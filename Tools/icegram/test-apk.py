"""Exercise artifact checks with synthetic ZIP files, not a compiled Android app."""
import io
from pathlib import Path
import runpy
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
            {"missing_library": "libexterahook.so"},
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


if __name__ == "__main__":
    unittest.main()
