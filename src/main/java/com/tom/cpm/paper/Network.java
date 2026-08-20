package com.tom.cpm.paper;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import net.kyori.adventure.text.Component;

import com.tom.cpm.shared.config.PlayerData;
import com.tom.cpm.shared.io.FastByteArrayInputStream;
import com.tom.cpm.shared.network.NetH.ServerNetH;
import com.tom.cpm.shared.network.NetHandler;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class Network implements PluginMessageListener, Listener {
	private final org.bukkit.plugin.java.JavaPlugin plugin;
	public NetHandler<String, Player, Meta> netHandler;
	private final Map<UUID, Meta> playerData = new ConcurrentHashMap<>();

	public Network(org.bukkit.plugin.java.JavaPlugin plugin) {
		this.plugin = plugin;
		try {
			netHandler = new NetHandler<>((k, v) -> k + ":" + v);
			netHandler.setSendPacketDirect((pl, pck, dt) -> pl.owner.sendPluginMessage(plugin, pck, dt), this::sendToAllTrackingAndSelf);
			netHandler.setGetPlayerUUID(Player::getUniqueId);
			netHandler.setFindTracking((p, f) -> {
				for (Player pl : p.getTrackedBy()) {
					f.accept(pl);
				}
			});
			netHandler.setSendChat((pl, msg) -> pl.sendMessage(msg.<Component>remap()));
			netHandler.setExecutor(() -> Runnable::run);
			netHandler.setGetNet(this::getMetadata);
			netHandler.setGetPlayer(n -> n.owner);
			netHandler.setGetPlayerId(Player::getEntityId);
			netHandler.setGetOnlinePlayers(Bukkit::getOnlinePlayers);
			netHandler.setKickPlayer((p, m) -> p.getScheduler().run(plugin, t -> p.kick(m.remap()), null));
			netHandler.setGetPlayerAnimGetters((t, u) -> {
				Meta m = getMetadata(t);
				u.updated = true;
				u.creativeFlying = m.creativeFlying;
				u.falling = m.falling;
				u.health = m.health;
				u.air = m.air;
				u.hunger = m.hunger;
				u.inMenu = m.inMenu;
				if (m.tickTask == null || m.tickTask.isCancelled())
					t.getScheduler().runDelayed(plugin, __ -> schedulePlayerTick(t), null, 1L);
			});
			netHandler.addScaler(new AttributeScaler());
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public void register() {
		netHandler.registerOut(c -> Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, c));
		netHandler.registerIn(c -> Bukkit.getMessenger().registerIncomingPluginChannel(plugin, c, this));
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
			synchronized(netHandler) {
				netHandler.tick();
			}
		}, 1, 1);
	}

	private void sendToAllTrackingAndSelf(Player player, String packet, byte[] data) {
		player.getScheduler().run(plugin, task -> {
			for (Player pl : player.getTrackedBy()) {
				if (getMetadata(pl).cpm$hasMod()) {
					pl.sendPluginMessage(plugin, packet, data);
				}
			}
			if (getMetadata(player).cpm$hasMod()) {
				player.sendPluginMessage(plugin, packet, data);
			}
		}, null);
	}

	@Override
	public void onPluginMessageReceived(String name, Player player, byte[] packet) {
		player.getScheduler().run(plugin, task -> {
			synchronized (netHandler) {
				netHandler.receiveServer(name, new FastByteArrayInputStream(packet), getMetadata(player));
			}
		}, null);
	}

	private void schedulePlayerTick(Player player) {
		Meta mt = getMetadata(player);
		if (mt.tickTask != null && !mt.tickTask.isCancelled()) {
			mt.tickTask.cancel();
		}

		mt.tickTask = player.getScheduler().runAtFixedRate(plugin, task -> {
			mt.creativeFlying = player.isFlying();
			mt.falling = player.getFallDistance();
			mt.health = (float) (player.getHealth() / player.getAttribute(AttributeScaler.MAX_HEALTH).getValue());
			mt.air = Math.max(player.getRemainingAir() / (float) player.getMaximumAir(), 0);
			mt.hunger = player.getFoodLevel() / 20f;
			mt.inMenu = player.getOpenInventory() != null;
		}, null, 1, 1);
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent evt) {
		Player p = evt.getPlayer();
		try {
			Method addChn = p.getClass().getMethod("addChannel", String.class);
			netHandler.registerOut(c -> addChn.invoke(p, c));
		} catch (Exception e) {
			e.printStackTrace();
		}
		p.getScheduler().runDelayed(plugin, task -> {
			synchronized (netHandler) {
				netHandler.onJoin(p);
			}
		}, null, 20L);//delayed to fix add player packets not arriving
		schedulePlayerTick(p);
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent evt) {
		Meta mt = playerData.remove(evt.getPlayer().getUniqueId());
		if (mt != null && mt.tickTask != null && !mt.tickTask.isCancelled()) {
			mt.tickTask.cancel();
		}
	}

	@EventHandler
	public void onPlayerTrackEntity(PlayerTrackEntityEvent evt) {
		Player player = evt.getPlayer();
		if (evt.getEntity() instanceof Player p) {
			player.getScheduler().runDelayed(plugin, task -> {
				netHandler.sendPlayerData(player, p);
			}, null, 10);//delayed to fix add player packets not arriving
		}
	}

	@EventHandler
	public void onPlayerStatisticIncrement(PlayerStatisticIncrementEvent event) {
		if (event.getStatistic() == Statistic.JUMP) {
			Player p = event.getPlayer();
			p.getScheduler().run(plugin, t -> {
				synchronized (netHandler) {
					netHandler.onJump(p);
				}
			}, null);
		}
	}

	public Meta getMetadata(Player player) {
		return playerData.computeIfAbsent(player.getUniqueId(), k -> {
			Meta mt = new Meta(player);
			mt.cpm$setEncodedModelData(new PlayerData());
			return mt;
		});
	}

	public static class Meta implements ServerNetH {
		private final Player owner;
		private boolean hasMod;
		private PlayerData data;

		public volatile boolean creativeFlying;
		public volatile float falling, health, air, hunger;
		public volatile boolean inMenu;
		public ScheduledTask tickTask;

		public Meta(Player owner) {
			this.owner = owner;
		}

		@Override
		public boolean cpm$hasMod() {
			return hasMod;
		}

		@Override
		public void cpm$setHasMod(boolean v) {
			hasMod = v;
		}

		@Override
		public PlayerData cpm$getEncodedModelData() {
			return data;
		}

		@Override
		public void cpm$setEncodedModelData(PlayerData data) {
			this.data = data;
		}
	}
}
