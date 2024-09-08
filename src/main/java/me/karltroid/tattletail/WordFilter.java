package me.karltroid.tattletail;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.karltroid.tattletail.hooks.CoreProtectHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.UUID;

import static me.karltroid.tattletail.Tattletail.Staff.Admin;
import static me.karltroid.tattletail.Tattletail.Staff.Mod;

public class WordFilter implements Listener {

    static List<String> voldemortWords = new ArrayList<>();
    static List<String> badWords = new ArrayList<>();

    WordFilter() {
        updateWordFilters();
    }

    public void updateWordFilters() {
        voldemortWords = Tattletail.getInstance().getConfig().getStringList("voldemortWords");
        badWords = Tattletail.getInstance().getConfig().getStringList("voldemortWords");
        badWords.addAll(Tattletail.getInstance().getConfig().getStringList("badWords"));
    }

    public boolean containsVoldemortWord(String s) {
        return containsFilteredWord(voldemortWords, s);
    }

    public boolean containsBadWord(String s) {
        return containsFilteredWord(badWords, s);
    }

    boolean containsFilteredWord(List<String> filter, String s) {
        String sCopy = s;
        s = filterText(filter, s);

        return !sCopy.equals(s);
    }

    public static String filterText(List<String> filter, String msg) {
        for (String s : filter) {
            String[] pparse = new String[2];
            pparse[0] = " ";
            pparse[1] = " ";
            StringTokenizer st = new StringTokenizer(s, ",");
            int t = 0;
            while (st.hasMoreTokens()) {
                if (t < 2) {
                    pparse[t++] = st.nextToken();
                }
            }
            // Case insensitive replacement
            String newMsg = msg.replaceAll("(?i)" + pparse[0], pparse[1]);

            // Update the message
            msg = newMsg;
        }

        return msg;
    }

    @EventHandler
    void blockBadPlayerNames(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        if (containsVoldemortWord(playerName)) {
            Tattletail.banPlayer(player, false, "Username contains a racist or other foul word/phrase.");
        }
    }

    @EventHandler
    void filterChatMessages(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component chatMessageComponent = event.originalMessage();
        String rawChatMessage = LegacyComponentSerializer.legacyAmpersand().serialize(chatMessageComponent);

        if (containsBadWord(rawChatMessage)) {
            Tattletail.getInstance().alertStaff(Tattletail.Staff.Mod, ChatColor.RED + "" + ChatColor.BOLD + player.getName() + " Unfiltered: " + ChatColor.RED + rawChatMessage);
            if (containsVoldemortWord(rawChatMessage)) {
                Bukkit.getScheduler().runTask(Tattletail.getInstance(), () -> {
                    Tattletail.banPlayer(player, true, "Sent message containing a racist or other foul word/phrase.");
                });
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    void alertAndFilterChangedSigns(SignChangeEvent event) {

        Player signChanger = event.getPlayer();
        Block signBlock = event.getBlock();

        StringBuilder oldSignText = new StringBuilder();
        for (String line : ((Sign)signBlock.getState()).getLines()) {
            oldSignText.append(line).append(" ");
        }

        StringBuilder newSignText = new StringBuilder();
        for (String line : event.getLines()) {
            newSignText.append(line).append(" ");
        }

        // they didn't change anything, exit.
        if (oldSignText.toString().contentEquals(newSignText)) return;

        // check for any filtered words, if found cancel and ban if REALLY bad then exit.
        if (containsBadWord(newSignText.toString())) {
            event.setCancelled(true);
            signChanger.sendMessage(ChatColor.RED + "Sign change cancelled, contains a filtered word or phrase.");
            Tattletail.getInstance().alertStaff(Mod, ChatColor.RED + "" + ChatColor.BOLD + signChanger.getName() + ChatColor.RED + " wrote a filtered word on a sign. (change cancelled)" + ChatColor.GRAY + " [" + signBlock.getX() + " " + signBlock.getY() + " " + signBlock.getZ() + (signBlock.getWorld().getName().contains("_nether") ? " (nether)]" : "]\n╚ BEFORE: " + ChatColor.RED + "" + ChatColor.ITALIC + oldSignText + ChatColor.GRAY + "\n╚ AFTER: " + ChatColor.RED + "" + ChatColor.ITALIC + newSignText));

            if (containsVoldemortWord(newSignText.toString())) {
                Tattletail.banPlayer(signChanger, true, "Wrote a message on a sign containing a racist or other foul word/phrase.");
            }
            return;
        }

        if (Tattletail.isNotMonitored(signChanger.getUniqueId()) && Tattletail.isOldPlayer(signChanger.getUniqueId())) return;
        if (Tattletail.ignoreLocations.contains(signBlock.getLocation())) return;

        OfflinePlayer signOwner = CoreProtectHook.getWhoOwnsBlock(signBlock);
        if (signOwner == null) return;
        UUID chestOwnerUUID = signOwner.getUniqueId();
        UUID signChangerUUID = signChanger.getUniqueId();
        if (Tattletail.ignorePlayers(signChangerUUID, chestOwnerUUID) || chestOwnerUUID.equals(signChangerUUID)) return;

        Tattletail.getInstance().alertStaff(Mod, ChatColor.RED + "" + ChatColor.BOLD + signChanger.getName() + ChatColor.RED + " changed " + ChatColor.BOLD + signOwner.getName() + "'s sign" + ChatColor.GRAY + " [" + signBlock.getX() + " " + signBlock.getY() + " " + signBlock.getZ() + (signBlock.getWorld().getName().contains("_nether") ? " (nether)]" : "]\n╚ BEFORE: " + ChatColor.RED + "" + ChatColor.ITALIC + oldSignText + ChatColor.GRAY + "\n╚ AFTER: " + ChatColor.RED + "" + ChatColor.ITALIC + newSignText));
    }
}