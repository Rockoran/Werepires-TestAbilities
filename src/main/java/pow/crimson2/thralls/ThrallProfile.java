package pow.crimson2.thralls;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the character profile data for a single player.
 * Persisted to thralls_profiles.yml by ThrallDataManager.
 */
public class ThrallProfile {

    /** Active profile index (0 = none / use real name). 1-based. */
    private int activeIndex;

    /** Ordered list of profile entries. Index 0 = profile 1. */
    private final List<ProfileEntry> entries = new ArrayList<>();

    // ─── Thrall preference (from /playersetup) ───────────────────────────────
    /** Per-profile thrall preference key: "open","owner","both","no","try" */
    private final List<String> thrallPrefs = new ArrayList<>();

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public int getActiveIndex()        { return activeIndex; }
    public void setActiveIndex(int v)  { this.activeIndex = v; }

    public List<ProfileEntry> getEntries() { return entries; }

    /** 1-based count of profiles. */
    public int getCount() { return entries.size(); }

    /** Get the active ProfileEntry, or null if no active profile. */
    public ProfileEntry getActiveEntry() {
        if (activeIndex <= 0 || activeIndex > entries.size()) return null;
        return entries.get(activeIndex - 1);
    }

    /** Active character name, or null if no active profile. */
    public String getActiveName() {
        ProfileEntry e = getActiveEntry();
        return e != null ? e.getName() : null;
    }

    /** Active blood taste lore, or null if no active profile. */
    public String getActiveTaste() {
        ProfileEntry e = getActiveEntry();
        return e != null ? e.getTaste() : null;
    }

    /** Create a new profile and append it. Returns its 1-based index. */
    public int addProfile(String name, String taste) {
        entries.add(new ProfileEntry(name, taste));
        return entries.size(); // 1-based
    }

    /**
     * Delete the profile at 1-based index.
     * Shifts higher-indexed profiles down.
     * Returns false if index is out of range.
     */
    public boolean deleteProfile(int idx) {
        if (idx < 1 || idx > entries.size()) return false;
        entries.remove(idx - 1);
        // Also remove matching thrallpref entry if it exists
        if (thrallPrefs.size() >= idx) {
            thrallPrefs.remove(idx - 1);
        }
        return true;
    }

    /** Get the profile entry at a 1-based index, or null if out of range. */
    public ProfileEntry getEntry(int idx) {
        if (idx < 1 || idx > entries.size()) return null;
        return entries.get(idx - 1);
    }

    /** Set thrall preference for a 1-based profile index (padded with null). */
    public void setThrallPref(int idx, String pref) {
        while (thrallPrefs.size() < idx) thrallPrefs.add(null);
        thrallPrefs.set(idx - 1, pref);
    }

    /** Get thrall preference for a 1-based index (null if not set). */
    public String getThrallPref(int idx) {
        if (idx < 1 || idx > thrallPrefs.size()) return null;
        return thrallPrefs.get(idx - 1);
    }

    /** Thrall preference for the currently active profile (null if none). */
    public String getActiveThrallPref() {
        return getThrallPref(activeIndex);
    }

    // ─── ProfileEntry inner class ─────────────────────────────────────────────

    public static class ProfileEntry {
        private String name;
        private String taste;

        public ProfileEntry(String name, String taste) {
            this.name = name;
            this.taste = taste;
        }

        public String getName()        { return name; }
        public void setName(String v)  { this.name = v; }

        public String getTaste()       { return taste; }
        public void setTaste(String v) { this.taste = v; }
    }
}
