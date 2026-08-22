package pow.crimson2.gamestart;

import org.bukkit.entity.Player;

/** Persistent per-player preference for the session timeline's building and break bars. */
public final class SessionBossBarPreference {
    private static final String DISABLED_TAG = "pow_session_bossbar_disabled";

    private SessionBossBarPreference() { }

    public static boolean isEnabled(Player player) {
        return player != null && !player.getScoreboardTags().contains(DISABLED_TAG);
    }

    public static void setEnabled(Player player, boolean enabled) {
        if (enabled) player.removeScoreboardTag(DISABLED_TAG);
        else player.addScoreboardTag(DISABLED_TAG);
    }
}
