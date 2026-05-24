package pow.crimson2.utils;

import org.bukkit.enchantments.Enchantment;

public class LeveledEnchantment {
  public final Enchantment enchantment;
  public final int level;

  public LeveledEnchantment(Enchantment enchantment, int level) {
    this.enchantment = enchantment;
    this.level = level;
  }

  @Override
  public String toString() {
    return this.enchantment.toString() + " " + this.level;
  }
}
