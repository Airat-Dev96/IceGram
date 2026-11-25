"""Write private CI inputs without logging their values. Standard library only."""
import base64
import json
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REQUIRED = (
    "TELEGRAM_API_ID", "TELEGRAM_API_HASH", "GOOGLE_SERVICES_JSON_BASE64",
    "ICEGRAM_KEYSTORE_BASE64", "ICEGRAM_STORE_PASSWORD", "ICEGRAM_KEY_PASSWORD",
    "ICEGRAM_KEY_ALIAS",
)


def main():
    missing = [key for key in REQUIRED if not os.environ.get(key)]
    if missing:
        raise SystemExit("Configure repository Actions secrets: " + ", ".join(missing))
    if not re.fullmatch(r"[1-9][0-9]*", os.environ["TELEGRAM_API_ID"]):
        raise SystemExit("TELEGRAM_API_ID must be a positive integer")
    if not re.fullmatch(r"[0-9a-fA-F]{32}", os.environ["TELEGRAM_API_HASH"]):
        raise SystemExit("TELEGRAM_API_HASH must be your 32-digit hexadecimal hash")

    try:
        google_bytes = base64.b64decode(os.environ["GOOGLE_SERVICES_JSON_BASE64"], validate=True)
        google = json.loads(google_bytes)
        packages = {
            client.get("client_info", {}).get("android_client_info", {}).get("package_name")
            for client in google.get("client", [])
        }
        if "org.icegram.messenger" not in packages:
            raise ValueError("Wrong Firebase package")
        keystore = base64.b64decode(os.environ["ICEGRAM_KEYSTORE_BASE64"], validate=True)
        if not keystore:
            raise ValueError("Empty keystore")
    except (ValueError, TypeError, KeyError):
        raise SystemExit("Invalid base64/JSON configuration; Firebase must include org.icegram.messenger") from None

    # java.util.Properties consumes backslash escapes, including Unicode escapes.
    def property_escape(value):
        escaped = []
        for char in value:
            if char in "\\ =:#!":
                escaped.append("\\" + char)
            elif ord(char) < 32 or ord(char) > 126:
                for index in range(0, len(char.encode("utf-16-be")), 2):
                    unit = char.encode("utf-16-be")[index:index + 2]
                    escaped.append("\\u" + unit.hex())
            else:
                escaped.append(char)
        return "".join(escaped)

    properties = {
        "APP_ID": os.environ["TELEGRAM_API_ID"],
        "APP_HASH": '"' + os.environ["TELEGRAM_API_HASH"] + '"',
        "SIGNING_KEY_STORE_PASSWORD": os.environ["ICEGRAM_STORE_PASSWORD"],
        "SIGNING_KEY_PASSWORD": os.environ["ICEGRAM_KEY_PASSWORD"],
        "SIGNING_KEY_ALIAS": os.environ["ICEGRAM_KEY_ALIAS"],
    }
    destinations = [
        (ROOT / "API_KEYS", ("\n".join(k + "=" + property_escape(v) for k, v in properties.items()) + "\n").encode()),
        (ROOT / "TMessagesProj/google-services.json", google_bytes),
        (ROOT / "TMessagesProj/config/icegram.jks", keystore),
    ]
    if any(path.exists() for path, _ in destinations):
        raise SystemExit("Refusing to overwrite existing private build files")
    for path, content in destinations:
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("xb") as file:
            os.chmod(path, 0o600)
            file.write(content)
    print("Private build configuration prepared; values are not printed.")


if __name__ == "__main__":
    main()
