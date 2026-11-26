"""Exercise the production history SQL and verify privacy/queue integration."""
import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java"


def main():
    storage = (JAVA / "org/telegram/messenger/MessagesStorage.java").read_text()
    config = (JAVA / "org/icegram/messenger/IcegramConfig.java").read_text()
    schema = re.search(r'"(CREATE TABLE icegram_message_history\([^"]+)"', storage).group(1)
    insert = re.search(r'"(INSERT INTO icegram_message_history\([^"]+)"', storage).group(1)
    prune = re.search(r'"(DELETE FROM icegram_message_history WHERE id NOT IN [^"]+)"', storage).group(1)
    limit = int(re.search(r'ICEGRAM_MESSAGE_HISTORY_MAX_ENTRIES = (\d+)', storage).group(1))
    prune = prune + str(limit) + ")"
    remove_dialog = re.search(r'"(DELETE FROM icegram_message_history WHERE dialog_id = \?)"', storage).group(1)
    with sqlite3.connect(":memory:") as database:
        database.execute(schema)
        database.executemany(insert, [(index % 2 + 10, index, 1, index, 1, f"version-{index}")
                                      for index in range(limit + 25)])
        database.execute(prune)
        assert database.execute("SELECT COUNT(*), MIN(mid), MAX(mid) FROM icegram_message_history").fetchone() == (limit, 25, limit + 24)
        database.execute(remove_dialog, (10,))
        assert database.execute("SELECT COUNT(*) FROM icegram_message_history WHERE dialog_id=10").fetchone()[0] == 0
        assert database.execute("SELECT COUNT(*) FROM icegram_message_history WHERE dialog_id=11").fetchone()[0] == limit // 2
        database.execute("DELETE FROM icegram_message_history")
        database.executemany(insert, [(10, 50, 1, 2, 1, "first"), (10, 50, 1, 3, 1, "second"),
                                      (10, 50, 1, 4, 2, "last before deletion")])
        versions = database.execute("SELECT event_type, text FROM icegram_message_history ORDER BY changed_at DESC, id DESC").fetchall()
        assert versions == [(2, "last before deletion"), (1, "second"), (1, "first")]
    assert "!IcegramConfig.isMessageHistoryExcluded(currentAccount, dialogId)" in storage
    assert "new ArrayList<>(messageIds)" in storage
    assert "scheduleMessageHistoryPrune();" in storage
    assert "if (messageHistoryPruneScheduled)" in storage
    assert "getClientUserId()" in config[config.index("private static String historyExceptionsKey"):]
    assert 'new HashSet<>(preferences().getStringSet(key, Collections.emptySet()))' in config
    assert "MessageObject.getDialogId(message), oldMessage" in storage
    print("PASS: history retention, per-chat clear, version ordering, and privacy integration")


if __name__ == "__main__":
    main()
