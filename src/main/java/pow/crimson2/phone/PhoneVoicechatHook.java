package pow.crimson2.phone;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import pow.crimson2.VampireSMPPlugin;

public final class PhoneVoicechatHook {
    private PhoneVoicechatHook() {}
    public static boolean register(VampireSMPPlugin plugin, PhoneManager manager) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) return false;
        PhoneVoicechatPlugin calls = new PhoneVoicechatPlugin(plugin, manager);
        service.registerPlugin(calls);
        manager.setCallService(calls);
        return true;
    }
}
