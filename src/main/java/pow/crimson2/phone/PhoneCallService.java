package pow.crimson2.phone;

import org.bukkit.entity.Player;

public interface PhoneCallService {
    boolean available();
    void call(Player caller, String targetUuid);
    void callGroup(Player caller, int groupId);
    void answer(Player player);
    void decline(Player player);
    void hangup(Player player);
    void disconnect(Player player);
    void shutdown();

    PhoneCallService UNAVAILABLE = new PhoneCallService() {
        private void unavailable(Player player) { player.sendMessage("§cPhone calls require Simple Voice Chat."); }
        public boolean available() { return false; }
        public void call(Player p, String u) { unavailable(p); }
        public void callGroup(Player p, int i) { unavailable(p); }
        public void answer(Player p) { unavailable(p); }
        public void decline(Player p) { unavailable(p); }
        public void hangup(Player p) { unavailable(p); }
        public void disconnect(Player p) {}
        public void shutdown() {}
    };
}
