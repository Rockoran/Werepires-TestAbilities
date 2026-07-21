package pow.crimson2.thralls;

import java.util.UUID;
import org.bukkit.Location;

/**
 * Holds the runtime bond state for a single player (the thrall side).
 * Persisted fields are written to thralls_data.yml by ThrallDataManager.
 */
public class ThrallBondData {

    // ─── Persisted ────────────────────────────────────────────────────────────

    /** Primary master UUID */
    private UUID primaryMaster;
    /** Secondary master UUID (dual-master system) */
    private UUID secondaryMaster;

    /** Bottles drunk from primary master */
    private int bottlesM1;
    /** Bottles drunk from secondary master */
    private int bottlesM2;

    /** Stage toward primary master (0-3) */
    private int stageM1;
    /** Stage toward secondary master (0-3) */
    private int stageM2;

    /** Seconds until withdrawal triggers (countdown). Reset on blood drink. */
    private int withdrawLeft;

    /** Timer-mode: seconds until next dose is allowed (0 = ready) */
    private int doseLeft;

    /** True if the Stage 3 tag-offer GUI is pending player choice */
    private boolean tagOfferPending;

    /** Master UUID that triggered the tag offer (for notification) */
    private UUID tagOfferMaster;

    /** Cached player username for YAML display */
    private String username;

    // ─── Runtime-only (not persisted) ─────────────────────────────────────────

    /** Cooldown (seconds) before another withdrawal pulse fires */
    private int withdrawPulseCd;
    /** Whether the 2-minute withdrawal warning has been sent */
    private boolean withdrawWarned;
    /** Whether the 10-second final warning has been sent */
    private boolean withdrawFinalWarned;
    /** Number of withdrawal pulses elapsed (for deductAfterFirst mode) */
    private int withdrawPulseCount;
    /** Flip-flop flag for alternating deduction mode */
    private boolean withdrawDeductNext;

    /** Separation-anxiety pulse cooldown counter (seconds) */
    private int farPulseCd;

    /** Seconds the player is frozen by /thrall stay */
    private int stayTime;
    /** Location the player must stay at */
    private Location stayLoc;

    /** Current roleplay command text from master */
    private String cmdText;
    /** Seconds until cmd expires */
    private int cmdLeft;

    /** Force-offer: who the blood belongs to */
    private UUID offerOwner;
    /** Force-offer: the vampire who sent the offer */
    private UUID offerVamp;
    /** Force-offer: seconds until it expires */
    private int offerLeft;
    /** Prevents double-clicks processing the offer twice */
    private boolean offerLock;

    /** Boss bar display mode: "off" | "withdraw" | "dose" | "unthral" */
    private String barMode = "off";
    /** Whether the boss bar is currently visible */
    private boolean barVisible;

    /** Tag-offer lock prevents double-clicks */
    private boolean tagOfferLock;

    // ─── Cooldowns (seconds remaining) ───────────────────────────────────────

    private int cdForce;
    private int cdStay;
    private int cdPunish;
    private int cdCommand;
    private int cdLocate;
    private int cdBlood;

    // ─── Blood taste (v1.42) runtime ─────────────────────────────────────────

    /** UUID of the locked-in blood taste target */
    private UUID bloodTasteTarget;
    /** Countdown to blood taste firing (seconds) */
    private int bloodTastePrepTimer;
    /** Per-vampire cooldown (seconds) between taste messages */
    private int bloodTasteCd;

    // ─── Locate tracker ──────────────────────────────────────────────────────

    private UUID locateTarget;
    private int locateLeft;   // seconds remaining
    private int locatePulse;  // seconds until next ping

    // =========================================================================
    // Getters / Setters
    // =========================================================================

    public UUID getPrimaryMaster()               { return primaryMaster; }
    public void setPrimaryMaster(UUID v)         { this.primaryMaster = v; }

    public UUID getSecondaryMaster()             { return secondaryMaster; }
    public void setSecondaryMaster(UUID v)       { this.secondaryMaster = v; }

    public int getBottlesM1()                    { return bottlesM1; }
    public void setBottlesM1(int v)              { this.bottlesM1 = v; }

    public int getBottlesM2()                    { return bottlesM2; }
    public void setBottlesM2(int v)              { this.bottlesM2 = v; }

    public int getStageM1()                      { return stageM1; }
    public void setStageM1(int v)                { this.stageM1 = v; }

    public int getStageM2()                      { return stageM2; }
    public void setStageM2(int v)                { this.stageM2 = v; }

    public int getWithdrawLeft()                 { return withdrawLeft; }
    public void setWithdrawLeft(int v)           { this.withdrawLeft = v; }

    public int getDoseLeft()                     { return doseLeft; }
    public void setDoseLeft(int v)               { this.doseLeft = v; }

    public boolean isTagOfferPending()           { return tagOfferPending; }
    public void setTagOfferPending(boolean v)    { this.tagOfferPending = v; }

    public UUID getTagOfferMaster()              { return tagOfferMaster; }
    public void setTagOfferMaster(UUID v)        { this.tagOfferMaster = v; }

    public String getUsername()                  { return username; }
    public void setUsername(String v)            { this.username = v; }

    public int getWithdrawPulseCd()              { return withdrawPulseCd; }
    public void setWithdrawPulseCd(int v)        { this.withdrawPulseCd = v; }

    public boolean isWithdrawWarned()            { return withdrawWarned; }
    public void setWithdrawWarned(boolean v)     { this.withdrawWarned = v; }

    public boolean isWithdrawFinalWarned()       { return withdrawFinalWarned; }
    public void setWithdrawFinalWarned(boolean v){ this.withdrawFinalWarned = v; }

    public int getWithdrawPulseCount()           { return withdrawPulseCount; }
    public void setWithdrawPulseCount(int v)     { this.withdrawPulseCount = v; }

    public boolean isWithdrawDeductNext()        { return withdrawDeductNext; }
    public void setWithdrawDeductNext(boolean v) { this.withdrawDeductNext = v; }

    public int getFarPulseCd()                   { return farPulseCd; }
    public void setFarPulseCd(int v)             { this.farPulseCd = v; }

    public int getStayTime()                     { return stayTime; }
    public void setStayTime(int v)               { this.stayTime = v; }

    public Location getStayLoc()                 { return stayLoc; }
    public void setStayLoc(Location v)           { this.stayLoc = v; }

    public String getCmdText()                   { return cmdText; }
    public void setCmdText(String v)             { this.cmdText = v; }

    public int getCmdLeft()                      { return cmdLeft; }
    public void setCmdLeft(int v)                { this.cmdLeft = v; }

    public UUID getOfferOwner()                  { return offerOwner; }
    public void setOfferOwner(UUID v)            { this.offerOwner = v; }

    public UUID getOfferVamp()                   { return offerVamp; }
    public void setOfferVamp(UUID v)             { this.offerVamp = v; }

    public int getOfferLeft()                    { return offerLeft; }
    public void setOfferLeft(int v)              { this.offerLeft = v; }

    public boolean isOfferLock()                 { return offerLock; }
    public void setOfferLock(boolean v)          { this.offerLock = v; }

    public String getBarMode()                   { return barMode; }
    public void setBarMode(String v)             { this.barMode = v; }

    public boolean isBarVisible()                { return barVisible; }
    public void setBarVisible(boolean v)         { this.barVisible = v; }

    public boolean isTagOfferLock()              { return tagOfferLock; }
    public void setTagOfferLock(boolean v)       { this.tagOfferLock = v; }

    public int getCdForce()                      { return cdForce; }
    public void setCdForce(int v)                { this.cdForce = v; }

    public int getCdStay()                       { return cdStay; }
    public void setCdStay(int v)                 { this.cdStay = v; }

    public int getCdPunish()                     { return cdPunish; }
    public void setCdPunish(int v)               { this.cdPunish = v; }

    public int getCdCommand()                    { return cdCommand; }
    public void setCdCommand(int v)              { this.cdCommand = v; }

    public int getCdLocate()                     { return cdLocate; }
    public void setCdLocate(int v)               { this.cdLocate = v; }

    public int getCdBlood()                      { return cdBlood; }
    public void setCdBlood(int v)                { this.cdBlood = v; }

    public UUID getBloodTasteTarget()            { return bloodTasteTarget; }
    public void setBloodTasteTarget(UUID v)      { this.bloodTasteTarget = v; }

    public int getBloodTastePrepTimer()          { return bloodTastePrepTimer; }
    public void setBloodTastePrepTimer(int v)    { this.bloodTastePrepTimer = v; }

    public int getBloodTasteCd()                 { return bloodTasteCd; }
    public void setBloodTasteCd(int v)           { this.bloodTasteCd = v; }

    public UUID getLocateTarget()                { return locateTarget; }
    public void setLocateTarget(UUID v)          { this.locateTarget = v; }

    public int getLocateLeft()                   { return locateLeft; }
    public void setLocateLeft(int v)             { this.locateLeft = v; }

    public int getLocatePulse()                  { return locatePulse; }
    public void setLocatePulse(int v)            { this.locatePulse = v; }

    // ─── Computed helpers ─────────────────────────────────────────────────────

    /** True if this player has any master bonded. */
    public boolean hasBond() {
        return primaryMaster != null;
    }

    /** The effective (highest) bond stage across both masters. */
    public int getEffectiveStage() {
        return Math.max(stageM1, stageM2);
    }

    /** Total bottles drunk from both masters. */
    public int getTotalBottles() {
        return bottlesM1 + bottlesM2;
    }

    /** True if masterId is either primary or secondary master. */
    public boolean isBoundTo(UUID masterId) {
        if (masterId == null) return false;
        return masterId.equals(primaryMaster) || masterId.equals(secondaryMaster);
    }

    /** True if masterId is the primary master. */
    public boolean isPrimaryMaster(UUID masterId) {
        return masterId != null && masterId.equals(primaryMaster);
    }

    /** Stage for a specific master (0 if not bonded). */
    public int getStageFor(UUID masterId) {
        if (masterId == null) return 0;
        if (masterId.equals(primaryMaster)) return stageM1;
        if (masterId.equals(secondaryMaster)) return stageM2;
        return 0;
    }

    /** Bottles drunk from a specific master. */
    public int getBottlesFrom(UUID masterId) {
        if (masterId == null) return 0;
        if (masterId.equals(primaryMaster)) return bottlesM1;
        if (masterId.equals(secondaryMaster)) return bottlesM2;
        return 0;
    }
}
