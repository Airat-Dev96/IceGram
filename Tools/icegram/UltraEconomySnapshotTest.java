import android.content.Context;
import android.content.SharedPreferences;
import org.icegram.messenger.UltraEconomySnapshot;
import org.telegram.messenger.ApplicationLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class UltraEconomySnapshotTest {
    private static int checks;

    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Snapshot check " + checks);
    }

    public static void main(String[] arguments) {
        SharedPreferences source = ApplicationLoader.applicationContext.getSharedPreferences("main", Context.MODE_PRIVATE);
        source.edit().putBoolean("boolean", true).putInt("integer", 73).putLong("long", 9876543210L)
                .putFloat("float", 0.25f).putString("string", "original")
                .putStringSet("set", Set.of("one", "two")).putString("unrelated", "before").commit();
        Map<String, ?> original = source.getAll();
        UltraEconomySnapshot snapshot = new UltraEconomySnapshot("test");
        check(!snapshot.isSaved());
        check(snapshot.capture(source, "boolean", "integer", "long", "float", "string", "set", "absent"));
        source.edit().putBoolean("boolean", false).putInt("integer", 0).putLong("long", 1L).putFloat("float", 1f)
                .putString("string", "override").putStringSet("set", Set.of("override"))
                .putString("absent", "new").putString("unrelated", "after").commit();
        check(snapshot.capture(source, "boolean", "integer"));
        check(snapshot.getBoolean("boolean", false));
        check(snapshot.getInt("integer", 0) == 73);
        check(snapshot.getString("string", "").equals("original"));
        snapshot = new UltraEconomySnapshot("test");
        check(snapshot.restore(source, false));
        check(snapshot.isSaved());
        for (String key : new String[]{"boolean", "integer", "long", "float", "string", "set"}) {
            check(original.get(key).equals(source.getAll().get(key)));
        }
        check(!source.getAll().containsKey("absent"));
        check(source.getString("unrelated", "").equals("after"));
        check(snapshot.restore(source, true));
        check(!snapshot.isSaved());
        check(snapshot.restore(source, true));
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("missing_preset", "serialized original preset");
        check(snapshot.capture(source, defaults, "missing_preset"));
        source.edit().putString("missing_preset", "disabled").commit();
        check(snapshot.getString("missing_preset", "").equals("serialized original preset"));
        check(new UltraEconomySnapshot("account_2").capture(source, "missing_preset"));
        check(snapshot.restore(source, true));
        check(!source.getAll().containsKey("missing_preset"));
        check(new UltraEconomySnapshot("account_2").getString("missing_preset", "").equals("disabled"));
        System.out.println("PASS: " + checks + " persistent snapshot/restoration checks");
    }
}
