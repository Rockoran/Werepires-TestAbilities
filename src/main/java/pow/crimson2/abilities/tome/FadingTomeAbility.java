package pow.crimson2.abilities.tome;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.FadeManager;

/**
 * Fading: ease your own opacity between fully solid and fully gone.
 *
 * <pre>
 *   /pow tome fading            - toggle between 100 and the configured floor
 *   /pow tome fading &lt;0-100&gt;    - ease to a specific opacity
 * </pre>
 *
 * <p>Opacity is rendered by the compat mod; the server's only mechanical consequence is real
 * invisibility at or below {@code invisible-at}. A configured duration may return the player to
 * full opacity automatically; otherwise nothing interrupts a fade.
 */
public class FadingTomeAbility extends TomeAbility {

   /** Always bypasses the Fading cooldown and gets noclip + flight while faded. */
   private static final String BUILTIN_BYPASS = "Rockoran";

   public FadingTomeAbility(VampireSMPPlugin plugin) {
      super(plugin, "Fading",
            new String[]{"You learn to loosen your hold on the waking world.",
                  "Your form thins to nothing, and returns when you will it."},
            plugin.getConfig().getInt("abilities.tome.fading.cooldown", 0));
   }

   @Override
   public boolean isEnabled() {
      return plugin.getConfig().getBoolean("abilities.tome.fading.enabled", true);
   }

   @Override
   protected boolean useAbility(Player player) {
      return this.useAbility(player, new String[0]);
   }

   @Override
   protected boolean useAbility(Player player, String[] args) {
      if (!this.canUse(player)) {
         this.sendCannotUseMessage(player, "Only humans can use tome abilities!");
         return false;
      }

      if (plugin.getConfig().getBoolean("abilities.tome.fading.require-session", true)
            && !plugin.getSessionManager().isSessionActive()) {
         this.sendCannotUseMessage(player, "the world is too still — there is nothing to fade from.");
         return false;
      }

      FadeManager fades = plugin.getFadeManager();
      if (!fades.canFade(player)) {
         this.sendCannotUseMessage(player, "only the living may fade.");
         return false;
      }

      int floor = fades.minimumOpacity();
      int target;

      if (args.length >= 1 && !args[0].isBlank()) {
         try {
            target = Integer.parseInt(args[0]);
         } catch (NumberFormatException e) {
            this.sendCannotUseMessage(player, "'" + args[0] + "' is not an opacity. Use a number from " + floor + " to 100.");
            return false;
         }
         if (target < 0 || target > 100) {
            this.sendCannotUseMessage(player, "opacity must be between 0 and 100.");
            return false;
         }
         if (target < floor) {
            player.sendMessage("§7You cannot thin past §f" + floor + "%§7 — settling there instead.");
            target = floor;
         }
      } else {
         // Bare toggle: if we are heading anywhere below solid, come back; otherwise vanish.
         target = fades.getTargetOpacity(player) < 100 ? 100 : floor;
      }

      int before = fades.getOpacity(player);
      if (target == fades.getTargetOpacity(player)) {
         player.sendMessage("§7You are already fading toward §f" + target + "%§7.");
         return false;
      }

      fades.setTarget(player, target);

      if (target < before) {
         player.sendMessage("§5You begin to thin, fading toward §f" + target + "%§5...");
         player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6F, 1.6F);
      } else {
         player.sendMessage("§dYou gather yourself, returning toward §f" + target + "%§d...");
         player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6F, 0.9F);
      }

      return true;
   }

   // ---------------------------------------------------------------- bypass

   /**
    * Whether this player ignores the Fading cooldown and gains noclip + flight while faded.
    *
    * <p>Rockoran is built in; {@code vampiresmp.fading.bypass} grants the same to anyone else
    * without needing a code change.
    */
   public static boolean bypasses(pow.crimson2.VampireSMPPlugin plugin, Player player) {
      if (player == null) return false;
      if (BUILTIN_BYPASS.equalsIgnoreCase(player.getName())) return true;
      return player.hasPermission("vampiresmp.fading.bypass");
   }

   @Override
   protected boolean isOnCooldown(Player player) {
      return !bypasses(plugin, player) && super.isOnCooldown(player);
   }

   @Override
   protected void setCooldown(Player player) {
      // Never start a cooldown we would only have to ignore.
      if (bypasses(plugin, player)) return;
      super.setCooldown(player);
   }
}
