package com.tom.cpm.paper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import com.tom.cpm.shared.network.NetHandler.ScalerInterface;
import com.tom.cpm.shared.util.Log;
import com.tom.cpm.shared.util.ScalingOptions;

public class AttributeScaler implements ScalerInterface<Player, List<Attribute>> {
	private static final NamespacedKey CPM_ATTR_KEY = new NamespacedKey("cpm", "scaling");
	public static final Attribute MAX_HEALTH = AttributeScaler.getAttributeSafely("GENERIC_MAX_HEALTH");

	@Override
	public void setScale(List<Attribute> key, Player player, float value) {
		player.getScheduler().run(CPMPaperPlugin.getInstance(), task -> {
			key.forEach(a -> {
				AttributeInstance ai = player.getAttribute(a);
				if (ai != null) {
					ai.getModifiers().stream().filter(i -> i.getKey().equals(CPM_ATTR_KEY)).collect(Collectors.toList()).forEach(ai::removeModifier);
					if (Math.abs(value - 1) > 0.01f)
						ai.addModifier(new AttributeModifier(CPM_ATTR_KEY, value - 1, Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
				}
			});
		}, null);
	}

	public static Attribute getAttributeSafely(String name) {
		// 1.21.? renamed these fields
		try {
			return (Attribute) Attribute.class.getField(name).get(null);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			try {
				return (Attribute) Attribute.class.getField(name.substring(name.indexOf('_') + 1, name.length())).get(null);
			} catch (NoSuchFieldException | IllegalAccessException e2) {
				Log.info("Attribute not found, skipping: " + name);
				throw new RuntimeException("Attribute not found: " + name);
			}
		}
	}

	@Override
	public List<Attribute> toKey(ScalingOptions opt) {
		try {
			switch (opt) {
			case HEALTH:
				return Collections.singletonList(MAX_HEALTH);
			case ATTACK_DMG:
				return Collections.singletonList(getAttributeSafely("GENERIC_ATTACK_DAMAGE"));
			case ATTACK_KNOCKBACK:
				return Collections.singletonList(getAttributeSafely("GENERIC_ATTACK_KNOCKBACK"));
			case ATTACK_SPEED:
				return Collections.singletonList(getAttributeSafely("GENERIC_ATTACK_SPEED"));
			case DEFENSE:
				return Collections.singletonList(getAttributeSafely("GENERIC_ARMOR"));
			case FLIGHT_SPEED:
				return Collections.singletonList(getAttributeSafely("GENERIC_FLYING_SPEED"));
			case MOB_VISIBILITY:
				return Collections.singletonList(getAttributeSafely("GENERIC_FOLLOW_RANGE"));
			case MOTION:
				return Collections.singletonList(getAttributeSafely("GENERIC_MOVEMENT_SPEED"));
			case ENTITY:
				return Collections.singletonList(getAttributeSafely("GENERIC_SCALE"));
			case REACH:
				return Arrays.asList(
						getAttributeSafely("PLAYER_BLOCK_INTERACTION_RANGE"),
						getAttributeSafely("PLAYER_ENTITY_INTERACTION_RANGE")
						);
			case MINING_SPEED:
				return Collections.singletonList(getAttributeSafely("PLAYER_BLOCK_BREAK_SPEED"));
			case SAFE_FALL_DISTANCE:
				return Collections.singletonList(getAttributeSafely("GENERIC_SAFE_FALL_DISTANCE"));
			case JUMP_HEIGHT:
				return Collections.singletonList(getAttributeSafely("GENERIC_JUMP_STRENGTH"));
			case KNOCKBACK_RESIST:
				return Collections.singletonList(getAttributeSafely("GENERIC_KNOCKBACK_RESISTANCE"));
			case STEP_HEIGHT:
				return Collections.singletonList(getAttributeSafely("GENERIC_STEP_HEIGHT"));
			case THIRD_PERSON:
				return Collections.singletonList(getAttributeSafely("CAMERA_DISTANCE"));
			default:
				return null;
			}
		} catch (Throwable e) {
			return null;
		}
	}

	@Override
	public String getMethodName() {
		return ATTRIBUTE;
	}
}
