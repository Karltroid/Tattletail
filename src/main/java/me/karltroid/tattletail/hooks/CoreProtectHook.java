package me.karltroid.tattletail.hooks;

import me.karltroid.tattletail.Tattletail;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.modernbeta.modernbeta.ModernBeta;

import java.util.List;
import java.util.UUID;

public class CoreProtectHook {
    static CoreProtectAPI coreProtect;

    public CoreProtectHook() {
        coreProtect = getCoreProtect();
        if (coreProtect == null) // Ensure we have access to the API
            Tattletail.log("CoreProtect NOT INSTALLED!");
    }

    private CoreProtectAPI getCoreProtect()
    {
        Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("CoreProtect");

        // Check that CoreProtect is loaded
        if (!(plugin instanceof CoreProtect)) return null;

        // Check that the API is enabled
        CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
        if (!CoreProtect.isEnabled()) return null;

        // Check that a compatible version of the API is loaded
        if (CoreProtect.APIVersion() < 9) return null;

        return CoreProtect;
    }

    public static OfflinePlayer getWhoOwnsBlock(Block block) {
        List<String[]> lookupResult = coreProtect.blockLookup(block, 0);
        if (lookupResult.isEmpty()) return null;

        CoreProtectAPI.ParseResult result = null;
        for (int i = lookupResult.size() - 1; i >= 0; i--) // loop from the beginning
        {
            result = coreProtect.parseResult(lookupResult.get(i));

            if (result.getActionId() == 1 && result.getBlockData().getMaterial() == block.getType()) break;

            if (i == 0)
                return null;
        }

        if (result.getPlayer().toCharArray()[0] == '#') return null;

        // first see if player is online, if so no need to do anything extra
        Player onlinePlayer = Bukkit.getPlayer(result.getPlayer());
        if (onlinePlayer != null)
            return onlinePlayer;

        // if offline, first see if players UUID has already been searched this instance and grab it from here
        UUID cachedUUID = Tattletail.cachedUUIDs.get(result.getPlayer());
        if (cachedUUID != null)
            return Bukkit.getOfflinePlayer(cachedUUID);

        // if not, search for the players info and cache it for next time
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(result.getPlayer());
        if (offlinePlayer.getName() == null) return null;
        Tattletail.cachedUUIDs.put(offlinePlayer.getName(), offlinePlayer.getUniqueId());
        return offlinePlayer;
    }
}
