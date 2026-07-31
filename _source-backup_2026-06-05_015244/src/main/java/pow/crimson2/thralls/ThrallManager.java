package pow.crimson2.thralls;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.VampireManager;

/**
 * Core manager for the Thrall blood-bond system.
 * Holds all runtime bond state, runs the per-second ticker,
 * and exposes all bond/profile/cooldown operations to other classes.
 */
@SuppressWarnings("UnstableApiUsage")
public class ThrallManager {

    private final VampireSMPPlugin plugin;
    private final ThrallConfig config;
    private final VampireManager vampireManager;
    private final ThrallDataManager dataManager;
    private final BloodBottleManager bloodBottleManager;

    /** Runtime bond data keyed by thrall UUID */
    private final Map<UUID, ThrallBondData> bonds = new HashMap<>();
    /** Runtime profile data keyed by player UUID */
    private final Map<UUID, ThrallProfile> profiles = new HashMap<>();
    /** Boss bars keyed by thrall UUID */
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    private BukkitTask tickTask;

    private final Random random = new Random();

    public ThrallManager(VampireSMPPlugin plugin) {
        this.plugin     = plugin;
        this.config     = new ThrallConfig(plugin);
        this.vampireManager = plugin.getVampireManager();
        this.dataManager = new ThrallDataManager(plugin, this);
        this.bloodBottleManager = new BloodBottleManager(plugin, this);
        dataManager.loadAll();
        startTickTask();
        plugin.logInfo("ThrallManager initialized.");
    }

    // =========================================================================
    // Vampire detection helpers
    // =========================================================================

    public boolean isVampire(Player p) {
        return vampireManager.isVampire(p);
    }

    public boolean isTier2Vamp(Player p) {
        return p.getScoreboardTags().contains("vampire_stage2")
            || p.getScoreboardTags().contains("vampire_stage3");
    }

    public boolean canDrawBlood(Player p) {
        if (config.isThrallBloodDrawStage1() && p.getScoreboardTags().contains("vampire_stage1")) return true;
        if (config.isThrallBloodDrawStage2() && p.getScoreboardTags().contains("vampire_stage2")) return true;
        if (config.isThrallBloodDrawStage3() && p.getScoreboardTags().contains("vampire_stage3")) return true;
        return false;
    }

    /** True if the player has ONLY the "vampire" tag (no stage tags) — thrall vampire. */
    public boolean isThrallVampire(Player p) {
        if (!p.getScoreboardTags().contains("vampire")) return false;
        if (p.getScoreboardTags().contains("vampire_stage1")) return false;
        if (p.getScoreboardTags().contains("vampire_stage2")) return false;
        if (p.getScoreboardTags().contains("vampire_stage3")) return false;
        return true;
    }

    // =========================================================================
    // Bond state accessors
    // =========================================================================

    public ThrallBondData getBond(UUID uuid) {
        return bonds.get(uuid);
    }

    public boolean isThrall(Player p) {
        ThrallBondData d = bonds.get(p.getUniqueId());
        return d != null && d.hasBond();
    }

    public int getEffectiveStage(Player p) {
        ThrallBondData d = bonds.get(p.getUniqueId());
        return d == null ? 0 : d.getEffectiveStage();
    }

    /** Count how many online players are thralls of the given master. */
    public int countThralls(UUID masterId) {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ThrallBondData d = bonds.get(p.getUniqueId());
            if (d != null && d.isBoundTo(masterId)) count++;
        }
        return count;
    }

    /** Count all online players currently in any thrall bond. */
    public int countAllThralls() {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ThrallBondData d = bonds.get(p.getUniqueId());
            if (d != null && d.hasBond()) count++;
        }
        return count;
    }

    // =========================================================================
    // Apply blood / bond formation  (mirrors vb_applyBlood)
    // =========================================================================

    public void applyBlood(Player thrall, UUID ownerId) {
        UUID thrallId = thrall.getUniqueId();

        // Vampires cannot be thralled
        if (isVampire(thrall)) {
            sendActionBar(thrall, "§8That blood tastes wrong to you.");
            return;
        }

        // Cannot bond to yourself
        if (thrallId.equals(ownerId)) {
            sendActionBar(thrall, "§8There is nothing new in that taste.");
            return;
        }

        ThrallBondData d = bonds.computeIfAbsent(thrallId, k -> new ThrallBondData());
        d.setUsername(thrall.getName());

        boolean isM1 = ownerId.equals(d.getPrimaryMaster());
        boolean isM2 = ownerId.equals(d.getSecondaryMaster());

        if (!isM1 && !isM2) {
            // New master — find a free slot
            if (d.getPrimaryMaster() == null) {
                // No masters yet — check caps
                if (config.getThrallGlobalCap() > 0 && countAllThralls() >= config.getThrallGlobalCap()) {
                    sendActionBar(thrall, "§4The world cannot hold more bonds. You feel nothing.");
                    addEffect(thrall, PotionEffectType.NAUSEA, 4, 0);
                    return;
                }
                if (countThralls(ownerId) >= config.getThrallMaxThralls()) {
                    sendActionBar(thrall, "§4They cannot claim more. You feel nothing.");
                    addEffect(thrall, PotionEffectType.NAUSEA, 4, 0);
                    return;
                }
                d.setPrimaryMaster(ownerId);
                d.setBottlesM1(0);
                resetWithdraw(d);
                isM1 = true;
            } else if (d.getSecondaryMaster() == null) {
                // One master — add second only if dual master enabled
                if (!config.isThrallDualMasterEnabled()) {
                    sendActionBar(thrall, "§4You are already bound. The taste changes nothing.");
                    addEffect(thrall, PotionEffectType.NAUSEA, 4, 0);
                    return;
                }
                if (countThralls(ownerId) >= config.getThrallMaxThralls()) {
                    sendActionBar(thrall, "§4They cannot claim more. You feel nothing.");
                    addEffect(thrall, PotionEffectType.NAUSEA, 4, 0);
                    return;
                }
                d.setSecondaryMaster(ownerId);
                d.setBottlesM2(0);
                resetWithdraw(d);
                sendActionBar(thrall, "§8A second presence threads into the bond...");
                isM2 = true;
            } else {
                // Already has 2 masters
                sendActionBar(thrall, "§4Two bonds already hold you. There is no room for more.");
                addEffect(thrall, PotionEffectType.NAUSEA, 4, 0);
                return;
            }
        }

        if ("bottles".equals(config.getThrallStageMode())) {
            applyBloodBottleMode(thrall, d, ownerId, isM1);
        } else {
            applyBloodTimerMode(thrall, d, ownerId);
        }

        dataManager.markBondDirty(thrallId);
    }

    private void applyBloodBottleMode(Player thrall, ThrallBondData d, UUID ownerId, boolean isM1) {
        int prevStage = d.getStageFor(ownerId);
        addBottleFor(d, ownerId);
        resetWithdraw(d);
        int newStage  = d.getStageFor(ownerId);
        int bottleCount = d.getBottlesFrom(ownerId);
        int maxBottles  = config.getThrallBottlesStage3();

        String masterLabel = d.isPrimaryMaster(ownerId) ? "§4Primary" : "§5Secondary";

        if (newStage > prevStage) {
            // Check if the other master's bond reached this stage first (familiar)
            UUID otherMaster = d.isPrimaryMaster(ownerId) ? d.getSecondaryMaster() : d.getPrimaryMaster();
            int otherStage = (otherMaster != null) ? d.getStageFor(otherMaster) : 0;
            boolean familiar = otherStage >= newStage;

            if (newStage == 1) {
                sendActionBar(thrall, familiar
                    ? "§8A familiar pull. The threads know this shape."
                    : "§4The first threads take root. You feel slow.");
                addEffect(thrall, PotionEffectType.SLOWNESS, 8, 4);
            } else if (newStage == 2) {
                sendActionBar(thrall, familiar
                    ? "§8Your blood already knows this grip. The tether settles in like an old scar."
                    : "§4The tether tightens. Something shifts.");
                addEffect(thrall, PotionEffectType.NAUSEA, 6, 0);
            } else if (newStage == 3) {
                sendActionBar(thrall, familiar
                    ? "§8The seal closes again. Somewhere deeper, something stirs in recognition."
                    : "§4Fully sealed. Something stirs within you.");
                if (config.isThrallTagOfferEnabled()) {
                    d.setTagOfferMaster(ownerId);
                    // Open tag offer after a short delay
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openTagOfferGui(thrall), 3L);
                }
            }
        } else {
            if (bottleCount >= maxBottles) {
                sendActionBar(thrall, "§8The bond with " + masterLabel + "§8 cannot deepen further.");
            } else {
                int remaining = maxBottles - bottleCount;
                sendActionBar(thrall, "§8Blood settles §8[" + masterLabel + "§8] §7(§f" + bottleCount
                    + "§7/§f" + maxBottles + "§7 — §f" + remaining + " more§7)");
            }
        }
    }

    private void applyBloodTimerMode(Player thrall, ThrallBondData d, UUID ownerId) {
        if (d.getDoseLeft() > 0) {
            sendActionBar(thrall, "§8It won't deepen again yet.");
            resetWithdraw(d);
            return;
        }
        int prevStage = d.getEffectiveStage();
        int newStage  = Math.min(3, prevStage + 1);
        setStageForAll(d, newStage);
        d.setDoseLeft(config.getThrallTimerDoseMinutes() * 60);
        resetWithdraw(d);

        if (newStage == 1) {
            sendActionBar(thrall, "§4First dose. The world tilts.");
            addEffect(thrall, PotionEffectType.SLOWNESS, 8, 4);
        } else if (newStage == 2) {
            sendActionBar(thrall, "§4Second dose. The tether tightens.");
            addEffect(thrall, PotionEffectType.NAUSEA, 6, 0);
        } else if (newStage == 3) {
            sendActionBar(thrall, "§4Third dose. Something inside you seals shut.");
            if (config.isThrallTagOfferEnabled()) {
                d.setTagOfferMaster(ownerId);
                Bukkit.getScheduler().runTaskLater(plugin, () -> openTagOfferGui(thrall), 3L);
            }
        }
    }

    /** Sets both stage fields to the same value (used for timer mode). */
    private void setStageForAll(ThrallBondData d, int stage) {
        if (d.getPrimaryMaster() != null)   d.setStageM1(stage);
        if (d.getSecondaryMaster() != null) d.setStageM2(stage);
    }

    // =========================================================================
    // addBottleFor  (mirrors vb_addBottleFor — auto-updates stage and may swap)
    // =========================================================================

    public void addBottleFor(ThrallBondData d, UUID masterId) {
        UUID thrallId = null; // We don't need it here — computed from d
        if (masterId.equals(d.getPrimaryMaster())) {
            int b = d.getBottlesM1() + 1;
            d.setBottlesM1(b);
            d.setStageM1(calcStageFromBottles(b));
        } else if (masterId.equals(d.getSecondaryMaster())) {
            int b = d.getBottlesM2() + 1;
            d.setBottlesM2(b);
            d.setStageM2(calcStageFromBottles(b));
            // Swap primary/secondary if secondary now has more bottles
            if (d.getBottlesM2() > d.getBottlesM1()) {
                UUID tmpO = d.getPrimaryMaster();
                int  tmpB = d.getBottlesM1();
                int  tmpS = d.getStageM1();
                d.setPrimaryMaster(d.getSecondaryMaster());
                d.setBottlesM1(d.getBottlesM2());
                d.setStageM1(d.getStageM2());
                d.setSecondaryMaster(tmpO);
                d.setBottlesM2(tmpB);
                d.setStageM2(tmpS);
            }
        }
    }

    private int calcStageFromBottles(int b) {
        if (b >= config.getThrallBottlesStage3()) return 3;
        if (b >= config.getThrallBottlesStage2()) return 2;
        if (b >= config.getThrallBottlesStage1()) return 1;
        return 0;
    }

    // =========================================================================
    // Holy water hit  (mirrors vb_holyWaterHit)
    // =========================================================================

    public void handleHolyWaterHit(Player thrall) {
        ThrallBondData d = bonds.get(thrall.getUniqueId());
        if (d == null || !d.hasBond()) return;

        if (!config.isThrallHolyWaterGradual()) {
            // Legacy: instant unthrall
            resetPlayer(thrall);
            sendActionBar(thrall, "§bHoly water breaks the bond.");
            return;
        }

        String hwMode = config.getThrallHolyWaterMode();
        if ("bottle".equals(hwMode)) {
            int dec = config.getThrallHolyWaterBottleDecrease();
            if (d.getPrimaryMaster() != null) {
                int b = Math.max(0, d.getBottlesM1() - dec);
                d.setBottlesM1(b);
                d.setStageM1(calcStageFromBottles(b));
            }
            if (d.getSecondaryMaster() != null) {
                int b = Math.max(0, d.getBottlesM2() - dec);
                d.setBottlesM2(b);
                d.setStageM2(calcStageFromBottles(b));
            }
            if (d.getBottlesM1() <= 0 && d.getBottlesM2() <= 0) {
                resetPlayer(thrall);
                sendActionBar(thrall, "§bThe holy water burns the last thread. The bond is broken.");
                return;
            }
        } else if ("stage".equals(hwMode)) {
            int dec = config.getThrallHolyWaterStageDecrease();
            if (d.getPrimaryMaster() != null) {
                int s = Math.max(0, d.getStageM1() - dec);
                d.setStageM1(s);
                d.setBottlesM1(bottlesForStage(s));
            }
            if (d.getSecondaryMaster() != null) {
                int s = Math.max(0, d.getStageM2() - dec);
                d.setStageM2(s);
                d.setBottlesM2(bottlesForStage(s));
            }
            if (d.getStageM1() <= 0 && d.getStageM2() <= 0) {
                resetPlayer(thrall);
                sendActionBar(thrall, "§bThe holy water burns the last thread. The bond is broken.");
                return;
            }
        }

        sendActionBar(thrall, "§bHoly water weakens the bond.");
        dataManager.markBondDirty(thrall.getUniqueId());
    }

    private int bottlesForStage(int s) {
        if (s >= 3) return config.getThrallBottlesStage3();
        if (s == 2) return config.getThrallBottlesStage2();
        if (s == 1) return config.getThrallBottlesStage1();
        return 0;
    }

    // =========================================================================
    // Full reset  (mirrors vb_resetPlayer)
    // =========================================================================

    public void resetPlayer(Player thrall) {
        UUID id = thrall.getUniqueId();
        hideBossBar(thrall);
        bonds.remove(id);
        dataManager.markBondDirty(id); // flush will delete the now-bondless file
    }

    public void resetPlayerSilent(Player thrall) {
        UUID id = thrall.getUniqueId();
        hideBossBar(thrall);
        bonds.remove(id);
    }

    /**
     * Wipe ALL thrall bonds — online and offline — and delete every bond file.
     * Used only on a full game initialization (not on normal session start/prime/resume).
     */
    public void wipeAllBonds() {
        for (Player p : Bukkit.getOnlinePlayers()) hideBossBar(p);
        int count = bonds.size();
        bonds.clear();
        dataManager.deleteAllBondFiles();
        plugin.logInfo("ThrallManager: wiped all thrall bonds (" + count + ") for game init.");
    }

    // =========================================================================
    // Withdrawal  (mirrors the every-1-second logic)
    // =========================================================================

    private void processWithdrawal(Player p, ThrallBondData d) {
        if (!config.isThrallWithdrawalEnabled()) return;
        if (!d.hasBond()) return;
        if (d.getEffectiveStage() < 1) return;

        int left = d.getWithdrawLeft();
        if (left < 0) left = 0;

        // Warn at 2 minutes
        if (left == config.getThrallWithdrawWarnSeconds() && !d.isWithdrawWarned()) {
            d.setWithdrawWarned(true);
            sendActionBar(p, "§eWithdrawal creeping in... §f(2 minutes)");
        }
        // Final warn at 10 seconds
        if (left == config.getThrallWithdrawFinalWarnSeconds() && !d.isWithdrawFinalWarned()) {
            d.setWithdrawFinalWarned(true);
            sendActionBar(p, "§cYour veins are about to burn... §f(10s)");
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 0.7f, 1.6f);
        }

        left--;
        if (left < 0) left = 0;
        d.setWithdrawLeft(left);

        if (left == 0) {
            // Pulse cycle
            if (d.getWithdrawPulseCd() <= 0) {
                d.setWithdrawPulseCd(config.getThrallWithdrawPulseCooldownSeconds());
                addEffect(p, PotionEffectType.POISON, config.getThrallWithdrawPoisonSeconds(), 0);
                sendActionBar(p, "§2Your veins ache for their blood.");
                processWithdrawDeduction(p, d);
            } else {
                d.setWithdrawPulseCd(d.getWithdrawPulseCd() - 1);
            }
        }
    }

    private void processWithdrawDeduction(Player p, ThrallBondData d) {
        String mode = config.getThrallWithdrawDeductMode();
        if ("none".equals(mode) || mode == null) return;

        int pc = d.getWithdrawPulseCount();
        d.setWithdrawPulseCount(pc + 1);

        boolean doDeduct;
        switch (mode) {
            case "deduct":
                doDeduct = true;
                break;
            case "deductAfterFirst":
                doDeduct = pc >= 1;
                break;
            case "alternating":
                doDeduct = d.isWithdrawDeductNext();
                d.setWithdrawDeductNext(!d.isWithdrawDeductNext());
                break;
            default:
                return;
        }
        if (!doDeduct) return;

        int dec = config.getThrallWithdrawDeductAmount();
        String type = config.getThrallWithdrawDeductType();

        if ("bottle".equals(type)) {
            if (d.getPrimaryMaster() != null) {
                int b = Math.max(0, d.getBottlesM1() - dec);
                d.setBottlesM1(b);
                d.setStageM1(calcStageFromBottles(b));
            }
            if (d.getSecondaryMaster() != null) {
                int b = Math.max(0, d.getBottlesM2() - dec);
                d.setBottlesM2(b);
                d.setStageM2(calcStageFromBottles(b));
            }
            if (d.getBottlesM1() <= 0 && d.getBottlesM2() <= 0) {
                resetPlayer(p);
                sendActionBar(p, "§bThe craving fades. The bond dissolves. You are free.");
                return;
            }
        } else if ("stage".equals(type)) {
            if (d.getPrimaryMaster() != null) {
                int s = Math.max(0, d.getStageM1() - dec);
                d.setStageM1(s);
                d.setBottlesM1(bottlesForStage(s));
            }
            if (d.getSecondaryMaster() != null) {
                int s = Math.max(0, d.getStageM2() - dec);
                d.setStageM2(s);
                d.setBottlesM2(bottlesForStage(s));
            }
            if (d.getStageM1() <= 0 && d.getStageM2() <= 0) {
                resetPlayer(p);
                sendActionBar(p, "§bThe craving fades. The bond dissolves. You are free.");
                return;
            }
        }

        sendActionBar(p, "§2The blood's hold on you weakens...");
        dataManager.markBondDirty(p.getUniqueId());
    }

    // =========================================================================
    // Reset withdrawal  (mirrors vb_resetWithdraw)
    // =========================================================================

    public void resetWithdraw(ThrallBondData d) {
        d.setWithdrawLeft(config.getThrallWithdrawSeconds());
        d.setWithdrawPulseCd(0);
        d.setWithdrawWarned(false);
        d.setWithdrawFinalWarned(false);
        d.setWithdrawPulseCount(0);
        d.setWithdrawDeductNext(false);
    }

    // =========================================================================
    // Separation Anxiety  (Stage 2+)
    // =========================================================================

    private void processSepAnxiety(Player thrall, ThrallBondData d) {
        if (!config.isThrallSepAnxietyEnabled()) return;
        if (d.getEffectiveStage() < 2) return;

        UUID masterId = d.getPrimaryMaster();
        if (masterId == null) return;
        Player master = Bukkit.getPlayer(masterId);
        if (master == null) return; // master offline — skip

        // A different world counts as "separated" (the tether stretches across realms).
        // Location.distance() throws across worlds, so only measure when co-located.
        if (thrall.getWorld().equals(master.getWorld())) {
            double dist = thrall.getLocation().distance(master.getLocation());
            if (dist < config.getThrallSepRange()) {
                d.setFarPulseCd(0); // reset so next trigger starts fresh
                return;
            }
        }

        // Decrement sep pulse cooldown
        int fpc = d.getFarPulseCd();
        if (fpc > 0) {
            d.setFarPulseCd(fpc - 2); // called every 2 seconds
            return;
        }

        // Pulse!
        d.setFarPulseCd(config.getThrallSepPulseSeconds());
        addEffect(thrall, PotionEffectType.NAUSEA,     config.getThrallSepNauseaSeconds(),    0);
        addEffect(thrall, PotionEffectType.WEAKNESS,   config.getThrallSepWeaknessSeconds(),  0);
        addEffect(thrall, PotionEffectType.SLOWNESS,   config.getThrallSepSlownessSeconds(),  0);
        sendActionBar(thrall, "§8The tether stretches thin...");
    }

    // =========================================================================
    // Boss bar
    // =========================================================================

    public void updateBossBar(Player p, ThrallBondData d) {
        if (!config.isThrallBossBarEnabled() || !d.hasBond()) {
            hideBossBar(p);
            return;
        }

        String mode = d.getBarMode();
        if ("off".equals(mode) || mode == null) {
            hideBossBar(p);
            return;
        }

        String title;
        double pct;

        if ("withdraw".equals(mode)) {
            int max  = config.getThrallWithdrawSeconds();
            int left = d.getWithdrawLeft();
            pct   = max > 0 ? (double) left / max : 0.0;
            title = "§4Withdrawal in: §f" + formatTime(left);
        } else if ("dose".equals(mode)) {
            if ("timer".equals(config.getThrallStageMode())) {
                int max  = config.getThrallTimerDoseMinutes() * 60;
                int left = d.getDoseLeft();
                pct   = left <= 0 ? 1.0 : 1.0 - ((double) left / max);
                title = left <= 0 ? "§aReady for next dose" : "§cNext dose in: §f" + formatTime(left);
            } else {
                int bottles = d.getTotalBottles();
                int max     = config.getThrallBottlesStage3();
                pct   = max > 0 ? (double) bottles / max : 0.0;
                title = "§4Bottles: §f" + bottles + " / " + max;
            }
        } else if ("unthral".equals(mode)) {
            int stage = d.getEffectiveStage();
            pct   = (double) stage / 3.0;
            title = "§8Bond Stage: §f" + stageName(stage);
        } else {
            hideBossBar(p);
            return;
        }

        pct = Math.max(0.0, Math.min(1.0, pct));
        BossBar bar = bossBars.computeIfAbsent(p.getUniqueId(), id -> {
            BossBar b = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID);
            b.addPlayer(p);
            return b;
        });
        bar.setTitle(title);
        bar.setProgress(pct);
        bar.setVisible(true);
        d.setBarVisible(true);
    }

    public void hideBossBar(Player p) {
        BossBar bar = bossBars.remove(p.getUniqueId());
        if (bar != null) {
            bar.removePlayer(p);
            bar.setVisible(false);
        }
        ThrallBondData d = bonds.get(p.getUniqueId());
        if (d != null) d.setBarVisible(false);
    }

    // =========================================================================
    // Force Offer GUI
    // =========================================================================

    public void openForceOfferGui(Player target) {
        Dialog dialog = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Blood Offer", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text("A vampire presents their blood. Your choice carries weight."))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Accept", NamedTextColor.GREEN),
                    Component.text("You yield to the bond."),
                    150,
                    DialogAction.customClick((view, aud) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            UUID playerId = target.getUniqueId();
                            ThrallBondData bd = bonds.get(playerId);
                            if (bd == null || bd.getOfferLeft() <= 0) {
                                sendActionBar(target, "§8The offer has expired.");
                                return;
                            }
                            if (bd.isOfferLock()) return;
                            bd.setOfferLock(true);
                            UUID ownerId = bd.getOfferOwner();
                            UUID vampId  = bd.getOfferVamp();
                            bd.setOfferOwner(null);
                            bd.setOfferVamp(null);
                            bd.setOfferLeft(0);
                            bd.setOfferLock(false);
                            if (ownerId == null || vampId == null) {
                                sendActionBar(target, "§8The presence is gone.");
                                return;
                            }
                            Player vamp = Bukkit.getPlayer(vampId);
                            if (vamp == null || !vamp.isOnline()) {
                                sendActionBar(target, "§8The presence is gone.");
                                return;
                            }
                            if (!target.getWorld().equals(vamp.getWorld())
                                || target.getLocation().distance(vamp.getLocation()) > config.getThrallForceRange()) {
                                sendActionBar(target, "§7Too far to accept.");
                                return;
                            }
                            ThrallBondData existing = bonds.get(playerId);
                            boolean alreadyBound = existing != null && existing.isBoundTo(ownerId);
                            if (!alreadyBound && countThralls(ownerId) >= config.getThrallMaxThralls()) {
                                sendActionBar(target, "§4They cannot hold more thralls.");
                                return;
                            }
                            if (!bloodBottleManager.takeOneBloodBottle(vamp)) {
                                sendActionBar(target, "§7They have no blood left.");
                                return;
                            }
                            sendActionBar(target, "§4You yield. The tether clicks into place.");
                            applyBlood(target, ownerId);
                        }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Decline", NamedTextColor.RED),
                    Component.text("Resist. For now."),
                    150,
                    DialogAction.customClick((view, aud) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            ThrallBondData bd = bonds.get(target.getUniqueId());
                            if (bd != null) {
                                bd.setOfferOwner(null);
                                bd.setOfferVamp(null);
                                bd.setOfferLeft(0);
                                bd.setOfferLock(false);
                            }
                            sendActionBar(target, "§8You resist. The pressure fades.");
                        }), ClickCallback.Options.builder().build()))
            )).build())
        );
        target.showDialog(dialog);
    }

    // =========================================================================
    // Tag Offer GUI  (Stage 3 threshold choice)
    // =========================================================================

    public void openTagOfferGui(Player target) {
        ThrallBondData d = bonds.get(target.getUniqueId());
        if (d == null) return;
        d.setTagOfferPending(true);
        d.setTagOfferLock(false);

        Dialog dia = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("A Threshold", NamedTextColor.DARK_RED))
                .body(List.of(DialogBody.plainMessage(Component.text("You have been fully claimed. The bond has sealed. What do you choose to be?"))))
                .build())
            .type(DialogType.multiAction(List.of(
                ActionButton.create(
                    Component.text("Remain Human", NamedTextColor.WHITE),
                    Component.text("You keep your humanity. The bond holds, but you stay who you are."),
                    220,
                    DialogAction.customClick((view, aud) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            ThrallBondData bd = bonds.get(target.getUniqueId());
                            if (bd == null || !bd.isTagOfferPending()) return;
                            if (bd.isTagOfferLock()) return;
                            bd.setTagOfferLock(true);
                            UUID masterUuid = bd.getTagOfferMaster();
                            bd.setTagOfferPending(false);
                            bd.setTagOfferLock(false);
                            bd.setTagOfferMaster(null);
                            sendActionBar(target, "§8You hold to your humanity. The bond does not change what you are.");
                            if (masterUuid != null) {
                                Player master = Bukkit.getPlayer(masterUuid);
                                if (master != null && master.isOnline())
                                    sendActionBar(master, "§7" + target.getName() + " chose to remain human.");
                            }
                        }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Swear to Vampirism", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Receive the vampire tag: " + config.getThrallTagOfferVampireTag()),
                    220,
                    DialogAction.customClick((view, aud) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            ThrallBondData bd = bonds.get(target.getUniqueId());
                            if (bd == null || !bd.isTagOfferPending()) return;
                            if (bd.isTagOfferLock()) return;
                            bd.setTagOfferLock(true);
                            UUID masterUuid = bd.getTagOfferMaster();
                            bd.setTagOfferPending(false);
                            bd.setTagOfferLock(false);
                            bd.setTagOfferMaster(null);
                            String tag = config.getThrallTagOfferVampireTag();
                            target.addScoreboardTag(tag);
                            if ("CuredVampire".equals(tag)) {
                                target.removeScoreboardTag("vampire");
                                target.addScoreboardTag("human");
                            }
                            sendActionBar(target, "§8The last of your old self dissolves. You are something else now.");
                            if (masterUuid != null) {
                                Player master = Bukkit.getPlayer(masterUuid);
                                if (master != null && master.isOnline())
                                    sendActionBar(master, "§5" + target.getName() + " has sworn to vampirism.");
                            }
                        }), ClickCallback.Options.builder().build()))
            )).build())
        );
        target.showDialog(dia);
    }

    // =========================================================================
    // Success roll  (mirrors vb_rollForMaster)
    // =========================================================================

    public boolean rollForMaster(Player thrall, UUID masterId) {
        ThrallBondData d = bonds.get(thrall.getUniqueId());
        if (d == null) return false;
        int stage = d.getStageFor(masterId);
        boolean isPrimary = d.isPrimaryMaster(masterId);
        int pct;
        if (isPrimary) {
            if (stage >= 3) return true;
            pct = (stage == 2) ? config.getThrallChanceStage2() : config.getThrallChanceStage1();
        } else {
            if (stage >= 3) pct = config.getThrallSecondaryChanceStage3();
            else if (stage == 2) pct = config.getThrallSecondaryChanceStage2();
            else if (stage == 1) pct = config.getThrallSecondaryChanceStage1();
            else return false;
        }
        if (pct >= 100) return true;
        return random.nextInt(100) + 1 <= pct;
    }

    // =========================================================================
    // Punish  (mirrors vb_punishEffect)
    // =========================================================================

    public void punishEffect(Player target, String effect, int durationSeconds) {
        switch (effect.toLowerCase()) {
            case "nausea":
                addEffect(target, PotionEffectType.NAUSEA, durationSeconds, 1);
                sendActionBar(target, "§4Your stomach turns under the bond.");
                break;
            case "blindness":
                addEffect(target, PotionEffectType.BLINDNESS, durationSeconds, 0);
                sendActionBar(target, "§4Darkness presses at the edges.");
                break;
            case "weakness":
                addEffect(target, PotionEffectType.WEAKNESS, durationSeconds, 2);
                sendActionBar(target, "§4Your limbs feel like stone.");
                break;
            case "slowness":
                addEffect(target, PotionEffectType.SLOWNESS, durationSeconds, 3);
                sendActionBar(target, "§4Your feet drag against your will.");
                break;
            case "hunger":
                addEffect(target, PotionEffectType.HUNGER, durationSeconds, 2);
                sendActionBar(target, "§4A hollow ache gnaws at you.");
                break;
            case "poison":
                addEffect(target, PotionEffectType.POISON, durationSeconds, 1);
                sendActionBar(target, "§4Something burns through your veins.");
                break;
        }
    }

    // =========================================================================
    // Profile management
    // =========================================================================

    public ThrallProfile getOrCreateProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, k -> new ThrallProfile());
    }

    public ThrallProfile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    public void saveProfiles() {
        dataManager.saveAllProfiles();
    }

    // =========================================================================
    // Cooldown helpers
    // =========================================================================

    public boolean isOnCooldown(String key, UUID uuid) {
        ThrallBondData d = bonds.get(uuid);
        if (d == null) {
            // Master-side cooldowns stored separately when sender is vampire (not a thrall)
            // We need a master-side cooldown map too
            return masterCooldowns.getOrDefault(uuid + ":" + key, 0) > 0;
        }
        return getCdValue(key, d) > 0;
    }

    public boolean isOnCooldown(String key, Player p) {
        return isOnCooldown(key, p.getUniqueId());
    }

    public void setCooldown(String key, Player p) {
        setCooldown(key, p.getUniqueId());
    }

    public void setCooldown(String key, UUID uuid) {
        int secs = getCooldownSeconds(key);
        masterCooldowns.put(uuid + ":" + key, secs);
    }

    public void clearCooldowns(Player p) {
        UUID u = p.getUniqueId();
        masterCooldowns.entrySet().removeIf(e -> e.getKey().startsWith(u + ":"));
        ThrallBondData d = bonds.get(u);
        if (d != null) {
            d.setCdForce(0); d.setCdStay(0); d.setCdPunish(0);
            d.setCdCommand(0); d.setCdLocate(0); d.setCdBlood(0);
        }
    }

    /** Master-side cooldowns (vampires issuing commands) — stored separately from bond data */
    private final Map<String, Integer> masterCooldowns = new HashMap<>();

    private int getCooldownSeconds(String key) {
        switch (key) {
            case "force":   return config.getThrallCdForceSeconds();
            case "stay":    return config.getThrallCdStaySeconds();
            case "punish":  return config.getThrallCdPunishSeconds();
            case "command": return config.getThrallCdCommandSeconds();
            case "locate":  return config.getThrallCdLocateSeconds();
            case "blood":   return config.getThrallBloodDrawCooldownSeconds();
            default:        return 60;
        }
    }

    private int getCdValue(String key, ThrallBondData d) {
        switch (key) {
            case "blood": return d.getCdBlood();
            default:      return 0;
        }
    }

    // =========================================================================
    // Per-second tick task
    // =========================================================================

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            int sepAnxietyCounter = 0;
            int autoUnthrallCounter = 0;
            int autosaveCounter = 0;
            int flushCounter = 0;

            @Override
            public void run() {
                sepAnxietyCounter++;
                autoUnthrallCounter++;
                autosaveCounter++;
                flushCounter++;

                for (Player p : Bukkit.getOnlinePlayers()) {
                  try {
                    UUID id = p.getUniqueId();

                    // ── Master-side cooldowns ──────────────────────────────
                    tickMasterCooldowns(id);

                    // ── Thrall-side per-second logic ───────────────────────
                    ThrallBondData d = bonds.get(id);
                    if (d != null) {
                        // Blood draw cooldown
                        if (d.getCdBlood() > 0) d.setCdBlood(d.getCdBlood() - 1);

                        // Force offer countdown
                        if (d.getOfferLeft() > 0) {
                            d.setOfferLeft(d.getOfferLeft() - 1);
                            if (d.getOfferLeft() <= 0) {
                                d.setOfferOwner(null);
                                d.setOfferVamp(null);
                                d.setOfferLeft(0);
                                d.setOfferLock(false);
                                sendActionBar(p, "§8The offer dissolves.");
                            }
                        }

                        // Stay timer
                        if (d.getStayTime() > 0) {
                            d.setStayTime(d.getStayTime() - 1);
                            if (d.getStayTime() <= 0) {
                                d.setStayTime(0);
                                d.setStayLoc(null);
                                sendActionBar(p, "§7You are free to move.");
                            }
                        }

                        // Command expiry
                        if (d.getCmdLeft() > 0) {
                            d.setCmdLeft(d.getCmdLeft() - 1);
                            if (d.getCmdLeft() <= 0) {
                                d.setCmdText(null);
                                d.setCmdLeft(0);
                            }
                        }

                        // Timer-mode dose countdown
                        if ("timer".equals(config.getThrallStageMode()) && d.getDoseLeft() > 0) {
                            d.setDoseLeft(d.getDoseLeft() - 1);
                        }

                        // Withdrawal
                        if (d.hasBond()) {
                            processWithdrawal(p, d);
                        }

                        // Boss bar
                        updateBossBar(p, d);

                        // Blood taste cooldown
                        if (d.getBloodTasteCd() > 0) {
                            d.setBloodTasteCd(d.getBloodTasteCd() - 1);
                        }

                        // Blood taste prep timer
                        if (d.getBloodTastePrepTimer() > 0 && config.isThrallBloodTasteEnabled()) {
                            tickBloodTaste(p, d);
                        } else if (config.isThrallBloodTasteEnabled()) {
                            // Check if we should start a prep
                            checkBloodTasteStart(p, d);
                        }

                        // Locate tracker (per 2 seconds handled by counter below)
                    }
                  } catch (Exception ex) {
                    plugin.getLogger().warning("[Thrall] per-second tick error for "
                        + p.getName() + ": " + ex);
                  }
                }

                // ── Every 2 seconds ────────────────────────────────────────
                if (sepAnxietyCounter >= 2) {
                    sepAnxietyCounter = 0;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        try {
                            ThrallBondData d = bonds.get(p.getUniqueId());
                            if (d != null && d.hasBond()) {
                                processSepAnxiety(p, d);
                                tickLocateTracker(p, d);
                            }
                        } catch (Exception ex) {
                            plugin.getLogger().warning("[Thrall] sep/locate tick error for "
                                + p.getName() + ": " + ex);
                        }
                    }
                    // Auto-unthrall check
                    if (config.isThrallAutoUnthrallEnabled()) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            ThrallBondData d = bonds.get(p.getUniqueId());
                            if (d != null && d.hasBond() && isVampire(p)) {
                                resetPlayer(p);
                                sendActionBar(p, "§8Your new nature severs the old chains.");
                            }
                        }
                    }
                }

                // ── Every 20s: flush changed bond files (incremental, async) ─
                if (flushCounter >= 20) {
                    flushCounter = 0;
                    dataManager.flushDirtyBonds();
                }

                // ── Every 5 minutes: full snapshot (catches withdraw drift) ──
                if (autosaveCounter >= 300) {
                    autosaveCounter = 0;
                    dataManager.markAllBondsDirty();
                    dataManager.flushDirtyBonds();
                    dataManager.saveAllProfiles();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tickMasterCooldowns(UUID id) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Integer> e : masterCooldowns.entrySet()) {
            if (!e.getKey().startsWith(id + ":")) continue;
            int v = e.getValue() - 1;
            if (v <= 0) toRemove.add(e.getKey());
            else masterCooldowns.put(e.getKey(), v);
        }
        toRemove.forEach(masterCooldowns::remove);
    }

    // =========================================================================
    // Blood taste  (v1.42)
    // =========================================================================

    private void checkBloodTasteStart(Player vamp, ThrallBondData d) {
        if (!isVampire(vamp)) return;
        if (!vamp.isSneaking()) return;
        if (d.getBloodTasteCd() > 0) return;

        Player target = findNearestTarget(vamp);
        if (target == null) return;

        d.setBloodTasteTarget(target.getUniqueId());
        d.setBloodTastePrepTimer(5);
        sendActionBar(vamp, "§4Preparing to feed...");
    }

    private void tickBloodTaste(Player vamp, ThrallBondData d) {
        if (!vamp.isSneaking()) {
            d.setBloodTasteTarget(null);
            d.setBloodTastePrepTimer(0);
            return;
        }
        UUID tid = d.getBloodTasteTarget();
        if (tid == null) { d.setBloodTastePrepTimer(0); return; }
        Player target = Bukkit.getPlayer(tid);
        if (target == null) { d.setBloodTasteTarget(null); d.setBloodTastePrepTimer(0); return; }

        double range = config.getThrallBloodTasteRange();
        if (!vamp.getWorld().equals(target.getWorld())
            || vamp.getLocation().distance(target.getLocation()) > range) {
            d.setBloodTasteTarget(null);
            d.setBloodTastePrepTimer(0);
            sendActionBar(vamp, "§7Target moved away.");
            return;
        }

        int timer = d.getBloodTastePrepTimer() - 1;
        d.setBloodTastePrepTimer(timer);
        if (timer > 0) {
            sendActionBar(vamp, "§4Feeding in §c" + timer + "§4...");
            return;
        }

        // Countdown complete — fire taste
        d.setBloodTasteTarget(null);
        fireBloodTaste(vamp, d, target);
    }

    private void fireBloodTaste(Player vamp, ThrallBondData d, Player target) {
        if (d.getBloodTasteCd() > 0) return;
        ThrallProfile tp = profiles.get(target.getUniqueId());
        if (tp == null) return;
        int activeIdx = tp.getActiveIndex();
        if (activeIdx <= 0) return;
        ThrallProfile.ProfileEntry entry = tp.getEntry(activeIdx);
        if (entry == null) return;
        String taste = entry.getTaste();
        if (taste == null) return;

        d.setBloodTasteCd(config.getThrallBloodTasteCooldownSeconds());
        String msg;
        if (config.isThrallBloodTasteShowName()) {
            String name = entry.getName() != null ? entry.getName() : target.getName();
            msg = "§4§o" + name + "§4§o's blood — " + taste;
        } else {
            msg = "§4§o" + taste;
        }

        if ("chat".equals(config.getThrallBloodTasteMode())) {
            vamp.sendMessage(msg);
        } else {
            sendActionBar(vamp, msg);
        }
    }

    private Player findNearestTarget(Player vamp) {
        double range = config.getThrallBloodTasteRange();
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : vamp.getWorld().getPlayers()) {
            if (p.equals(vamp)) continue;
            double dist = p.getLocation().distance(vamp.getLocation());
            if (dist <= range && dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }

    // =========================================================================
    // Locate tracker tick
    // =========================================================================

    private void tickLocateTracker(Player p, ThrallBondData d) {
        if (!config.isThrallLocateEnabled()) return;
        if (d.getLocateLeft() <= 0) return;

        d.setLocateLeft(d.getLocateLeft() - 2);
        if (d.getLocateLeft() <= 0) {
            d.setLocateLeft(0);
            d.setLocateTarget(null);
            d.setLocatePulse(0);
            return;
        }

        int pulse = d.getLocatePulse() - 2;
        if (pulse <= 0) {
            pulse = config.getThrallLocateTrackPulseSeconds();
            d.setLocatePulse(pulse);
            UUID tid = d.getLocateTarget();
            if (tid == null) return;
            Player target = Bukkit.getPlayer(tid);
            if (target == null) {
                sendActionBar(p, "§8[Thrall] §7Target: §cOffline.");
            } else if (!p.getWorld().equals(target.getWorld())) {
                sendActionBar(p, "§8[Thrall] §7" + target.getName() + ": §cDifferent world.");
            } else {
                int dist = (int) p.getLocation().distance(target.getLocation());
                String dir = directionText(p.getLocation(), target.getLocation());
                sendActionBar(p, "§8[Thrall] §7" + target.getName() + ": §f" + dist + "§7 blocks (§f" + dir + "§7)");
            }
        } else {
            d.setLocatePulse(pulse);
        }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    public void sendActionBar(Player p, String msg) {
        p.sendActionBar(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg)[0]);
    }

    public static String stageName(int n) {
        if (n <= 0) return "Unbound";
        if (n == 1) return "Stage I";
        if (n == 2) return "Stage II";
        return "Stage III";
    }

    public static String formatTime(int sec) {
        if (sec < 0) sec = 0;
        int h = sec / 3600;
        int rem = sec - h * 3600;
        int m = rem / 60;
        int s = rem - m * 60;
        if (h > 0) {
            return h + ":" + String.format("%02d", m) + ":" + String.format("%02d", s);
        }
        return m + ":" + String.format("%02d", s);
    }

    public static String directionText(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        String ns = dz >= 0 ? "S" : "N";
        String ew = dx >= 0 ? "E" : "W";
        return ns + ew;
    }

    private void addEffect(Player p, PotionEffectType type, int durationSeconds, int amplifier) {
        p.addPotionEffect(new PotionEffect(type, durationSeconds * 20, amplifier, false, true));
    }

    // =========================================================================
    // Getters for sub-systems
    // =========================================================================

    public ThrallDataManager getDataManager()            { return dataManager; }
    public BloodBottleManager getBloodBottleManager()    { return bloodBottleManager; }
    public ThrallConfig getThrallConfig()                { return config; }
    public Map<UUID, ThrallBondData> getBonds()          { return bonds; }
    public Map<UUID, ThrallProfile> getProfiles()        { return profiles; }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        dataManager.saveAll();
        dataManager.saveAllProfiles();
        // Remove all boss bars
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        bossBars.clear();
        plugin.logInfo("ThrallManager shut down.");
    }
}
