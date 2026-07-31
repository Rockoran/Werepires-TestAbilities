package pow.crimson2.thralls;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reads all thrall-system configuration values from the main plugin config.yml.
 * Owned by ThrallManager; other thrall classes obtain it via thrallManager.getThrallConfig().
 */
public class ThrallConfig {

    private final FileConfiguration config;

    public ThrallConfig(JavaPlugin plugin) {
        this.config = plugin.getConfig();
    }

    // =========================================================================
    // General
    // =========================================================================

    public int getThrallMaxThralls() {
        return config.getInt("thrall.max-thralls", 3);
    }

    public int getThrallGlobalCap() {
        return config.getInt("thrall.global-cap", 0);
    }

    public boolean isThrallDualMasterEnabled() {
        return config.getBoolean("thrall.dual-master-enabled", true);
    }

    public boolean isThrallAutoUnthrallEnabled() {
        return config.getBoolean("thrall.auto-unthrall-enabled", true);
    }

    public String getThrallStageMode() {
        return config.getString("thrall.stage-mode", "bottles");
    }

    // =========================================================================
    // Bottles thresholds
    // =========================================================================

    public int getThrallBottlesStage1() {
        return config.getInt("thrall.bottles.stage1", 1);
    }

    public int getThrallBottlesStage2() {
        return config.getInt("thrall.bottles.stage2", 3);
    }

    public int getThrallBottlesStage3() {
        return config.getInt("thrall.bottles.stage3", 6);
    }

    // =========================================================================
    // Timer mode
    // =========================================================================

    public int getThrallTimerDoseMinutes() {
        return config.getInt("thrall.timer.dose-minutes", 60);
    }

    // =========================================================================
    // Command success chances
    // =========================================================================

    public int getThrallChanceStage1() {
        return config.getInt("thrall.chance-stage1", 50);
    }

    public int getThrallChanceStage2() {
        return config.getInt("thrall.chance-stage2", 80);
    }

    public int getThrallSecondaryChanceStage1() {
        return config.getInt("thrall.secondary-chance-stage1", 25);
    }

    public int getThrallSecondaryChanceStage2() {
        return config.getInt("thrall.secondary-chance-stage2", 50);
    }

    public int getThrallSecondaryChanceStage3() {
        return config.getInt("thrall.secondary-chance-stage3", 75);
    }

    // =========================================================================
    // Blood draw
    // =========================================================================

    public boolean isThrallBloodDrawEnabled() {
        return config.getBoolean("thrall.blood-draw.enabled", true);
    }

    public boolean isThrallBloodDrawStage1() {
        return config.getBoolean("thrall.blood-draw.stage1", false);
    }

    public boolean isThrallBloodDrawStage2() {
        return config.getBoolean("thrall.blood-draw.stage2", true);
    }

    public boolean isThrallBloodDrawStage3() {
        return config.getBoolean("thrall.blood-draw.stage3", true);
    }

    public double getThrallBloodDrawDamageHearts() {
        return config.getDouble("thrall.blood-draw.damage-hearts", 2.0);
    }

    public int getThrallBloodDrawCooldownSeconds() {
        return config.getInt("thrall.blood-draw.cooldown-seconds", 30);
    }

    public boolean isThrallAllowProfilesInBloodBottles() {
        return config.getBoolean("thrall.blood-draw.profiles-in-bottles", true);
    }

    // =========================================================================
    // Withdrawal
    // =========================================================================

    public boolean isThrallWithdrawalEnabled() {
        return config.getBoolean("thrall.withdrawal.enabled", true);
    }

    public int getThrallWithdrawSeconds() {
        return config.getInt("thrall.withdrawal.seconds", 3600);
    }

    public int getThrallWithdrawWarnSeconds() {
        return config.getInt("thrall.withdrawal.warn-seconds", 120);
    }

    public int getThrallWithdrawFinalWarnSeconds() {
        return config.getInt("thrall.withdrawal.final-warn-seconds", 10);
    }

    public int getThrallWithdrawPulseCooldownSeconds() {
        return config.getInt("thrall.withdrawal.pulse-cooldown-seconds", 60);
    }

    public int getThrallWithdrawPoisonSeconds() {
        return config.getInt("thrall.withdrawal.poison-seconds", 8);
    }

    public String getThrallWithdrawDeductMode() {
        return config.getString("thrall.withdrawal.deduct-mode", "none");
    }

    public String getThrallWithdrawDeductType() {
        return config.getString("thrall.withdrawal.deduct-type", "bottle");
    }

    public int getThrallWithdrawDeductAmount() {
        return config.getInt("thrall.withdrawal.deduct-amount", 1);
    }

    // =========================================================================
    // Separation anxiety
    // =========================================================================

    public boolean isThrallSepAnxietyEnabled() {
        return config.getBoolean("thrall.sep-anxiety.enabled", true);
    }

    public double getThrallSepRange() {
        return config.getDouble("thrall.sep-anxiety.range", 150.0);
    }

    public int getThrallSepPulseSeconds() {
        return config.getInt("thrall.sep-anxiety.pulse-seconds", 30);
    }

    public int getThrallSepNauseaSeconds() {
        return config.getInt("thrall.sep-anxiety.nausea-seconds", 6);
    }

    public int getThrallSepWeaknessSeconds() {
        return config.getInt("thrall.sep-anxiety.weakness-seconds", 8);
    }

    public int getThrallSepSlownessSeconds() {
        return config.getInt("thrall.sep-anxiety.slowness-seconds", 8);
    }

    // =========================================================================
    // Boss bar
    // =========================================================================

    public boolean isThrallBossBarEnabled() {
        return config.getBoolean("thrall.boss-bar.enabled", true);
    }

    // =========================================================================
    // Force offer
    // =========================================================================

    public boolean isThrallForceEnabled() {
        return config.getBoolean("thrall.force.enabled", true);
    }

    public double getThrallForceRange() {
        return config.getDouble("thrall.force.range", 5.0);
    }

    public int getThrallForceOfferSeconds() {
        return config.getInt("thrall.force.offer-seconds", 30);
    }

    // =========================================================================
    // Holy water
    // =========================================================================

    public boolean isThrallHolyWaterEnabled() {
        return config.getBoolean("thrall.holy-water.enabled", true);
    }

    public boolean isThrallHolyWaterGradual() {
        return config.getBoolean("thrall.holy-water.gradual", true);
    }

    public String getThrallHolyWaterMode() {
        return config.getString("thrall.holy-water.mode", "bottle");
    }

    public int getThrallHolyWaterBottleDecrease() {
        return config.getInt("thrall.holy-water.bottle-decrease", 2);
    }

    public int getThrallHolyWaterStageDecrease() {
        return config.getInt("thrall.holy-water.stage-decrease", 1);
    }

    // =========================================================================
    // Stay
    // =========================================================================

    public boolean isThrallStayEnabled() {
        return config.getBoolean("thrall.stay.enabled", true);
    }

    public int getThrallStayMaxSeconds() {
        return config.getInt("thrall.stay.max-seconds", 60);
    }

    // =========================================================================
    // Punish
    // =========================================================================

    public boolean isThrallPunishEnabled() {
        return config.getBoolean("thrall.punish.enabled", true);
    }

    public double getThrallPunishRange() {
        return config.getDouble("thrall.punish.range", 10.0);
    }

    public int getThrallPunishDurationSeconds() {
        return config.getInt("thrall.punish.duration-seconds", 8);
    }

    // =========================================================================
    // Command
    // =========================================================================

    public boolean isThrallCommandEnabled() {
        return config.getBoolean("thrall.command.enabled", true);
    }

    public int getThrallCmdExpirySeconds() {
        return config.getInt("thrall.command.expiry-seconds", 120);
    }

    // =========================================================================
    // Locate
    // =========================================================================

    public boolean isThrallLocateEnabled() {
        return config.getBoolean("thrall.locate.enabled", true);
    }

    public int getThrallLocateTrackPulseSeconds() {
        return config.getInt("thrall.locate.track-pulse-seconds", 5);
    }

    // =========================================================================
    // Findmaster
    // =========================================================================

    public boolean isThrallFindmasterEnabled() {
        return config.getBoolean("thrall.findmaster.enabled", true);
    }

    // =========================================================================
    // Blood taste
    // =========================================================================

    public boolean isThrallBloodTasteEnabled() {
        return config.getBoolean("thrall.blood-taste.enabled", true);
    }

    public double getThrallBloodTasteRange() {
        return config.getDouble("thrall.blood-taste.range", 5.0);
    }

    public int getThrallBloodTasteCooldownSeconds() {
        return config.getInt("thrall.blood-taste.cooldown-seconds", 30);
    }

    public boolean isThrallBloodTasteShowName() {
        return config.getBoolean("thrall.blood-taste.show-name", true);
    }

    public String getThrallBloodTasteMode() {
        return config.getString("thrall.blood-taste.mode", "actionbar");
    }

    // =========================================================================
    // Tag offer
    // =========================================================================

    public boolean isThrallTagOfferEnabled() {
        return config.getBoolean("thrall.tag-offer.enabled", true);
    }

    public String getThrallTagOfferVampireTag() {
        return config.getString("thrall.tag-offer.vampire-tag", "CuredVampire");
    }

    // =========================================================================
    // Profiles
    // =========================================================================

    public int getThrallMaxProfiles() {
        return config.getInt("thrall.profiles.max", 5);
    }

    // =========================================================================
    // Command cooldowns
    // =========================================================================

    public int getThrallCdForceSeconds() {
        return config.getInt("thrall.cooldowns.force-seconds", 600);
    }

    public int getThrallCdStaySeconds() {
        return config.getInt("thrall.cooldowns.stay-seconds", 120);
    }

    public int getThrallCdPunishSeconds() {
        return config.getInt("thrall.cooldowns.punish-seconds", 60);
    }

    public int getThrallCdCommandSeconds() {
        return config.getInt("thrall.cooldowns.command-seconds", 30);
    }

    public int getThrallCdLocateSeconds() {
        return config.getInt("thrall.cooldowns.locate-seconds", 5);
    }
}
