package me.karltroid.tattletail.hooks;

import me.karltroid.tattletail.Tattletail;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.config.ConfigHandler;
import net.coreprotect.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.modernbeta.modernbeta.ModernBeta;
import org.modernbeta.modernbeta.blocks.chests.FatChests;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CoreProtectHook {
    static CoreProtectAPI coreProtect;
    static Connection coreProtectDatabase;

    public CoreProtectHook() {
        coreProtect = getCoreProtect();
        if (coreProtect == null) // Ensure we have access to the API
            Tattletail.log("CoreProtect NOT INSTALLED!");

        coreProtectDatabase = Database.getConnection(true, 0);
    }

    public static void closeCoreProtectConnection() {
        try {
            coreProtectDatabase.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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

            if (result.getActionId() == 1 &&
                (result.getBlockData().getMaterial().equals(block.getType()) ||
                (Tattletail.getInstance().modernBetaInstalled && FatChests.isChestType(block.getType()))))
                break;

            if (i == 0)
                return null;
        }

        String blockOwnerName = result.getPlayer();
        if (blockOwnerName.toCharArray()[0] == '#') return null;

        OfflinePlayer blockOwner = Tattletail.getOfflinePlayer(blockOwnerName);
        if (blockOwner == null) {
            // player changed their name, get this usernames last associated UUID from CoreProtect
            UUID blockOwnerUUID = getCoreProtectUserUUID(blockOwnerName);
            if (blockOwnerUUID == null) return null;
            blockOwner = Bukkit.getOfflinePlayer(blockOwnerUUID);
            updateCoreProtectUsername(blockOwnerUUID.toString(), blockOwner.getName());
            return blockOwner;
        }

        return Tattletail.getOfflinePlayer(result.getPlayer());
    }

    static UUID getCoreProtectUserUUID(String username) {

        UUID uuid = null;

        try {
            // Prepare the query to retrieve the user's ID based on the username
            String query = "SELECT uuid as id FROM " + ConfigHandler.prefix + "user WHERE LOWER(user) = ? LIMIT 0, 1";
            PreparedStatement preparedStmt = coreProtectDatabase.prepareStatement(query);
            preparedStmt.setString(1, username.toLowerCase(Locale.ROOT));
            ResultSet rs = preparedStmt.executeQuery();

            // Get the user's ID if it exists
            if (rs.next()) {
                uuid = UUID.fromString(rs.getString("uuid"));
            }

            rs.close();
            preparedStmt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return uuid;
    }

    static void updateCoreProtectUsername(String uuid, String newUsername) {
        try {
            // Update the user table with the new username
            PreparedStatement preparedStmt = coreProtectDatabase.prepareStatement("UPDATE " + ConfigHandler.prefix + "user SET user = ? WHERE uuid = ?");
            preparedStmt.setString(1, newUsername);
            preparedStmt.setString(2, uuid);
            preparedStmt.executeUpdate();
            preparedStmt.close();

            // log the change in the username log if necessary
            preparedStmt = coreProtectDatabase.prepareStatement("INSERT INTO " + ConfigHandler.prefix + "username_log (time, uuid, user) VALUES (?, ?, ?)");
            preparedStmt.setInt(1, (int) (System.currentTimeMillis() / 1000)); // Log current time
            preparedStmt.setString(2, uuid);
            preparedStmt.setString(3, newUsername);
            preparedStmt.executeUpdate();
            preparedStmt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
