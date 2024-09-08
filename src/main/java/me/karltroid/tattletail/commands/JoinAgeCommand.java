package me.karltroid.tattletail.commands;
import me.karltroid.tattletail.Tattletail;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public final class JoinAgeCommand implements CommandExecutor
{
    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd, @NotNull String label, String[] args)
    {
        // Check if the command was typed correctly
        if (!cmd.getName().equalsIgnoreCase("joinage")) return false;

        // Check if the correct number of arguments was given
        if (args.length != 1)
        {
            sender.sendMessage(ChatColor.RED + "Usage: /joinage <playername>");
            return false;
        }

        // Get the player's join date and age
        OfflinePlayer player = Tattletail.getOfflinePlayer(args[0]);
        if (player.getFirstPlayed() <= 0)
        {
            sender.sendMessage(ChatColor.RED + "This player has never joined before.");
            return false;
        }

        long firstJoined = player.getFirstPlayed();
        Date joinDate = new Date(firstJoined);
        long playerAge = System.currentTimeMillis() - firstJoined;
        long playerAgeDays = TimeUnit.MILLISECONDS.toDays(playerAge);
        playerAge -= TimeUnit.DAYS.toMillis(playerAgeDays);
        long playerAgeHours = TimeUnit.MILLISECONDS.toHours(playerAge);
        playerAge -= TimeUnit.HOURS.toMillis(playerAgeHours);
        long playerAgeMinutes = TimeUnit.MILLISECONDS.toMinutes(playerAge);
        playerAge -= TimeUnit.MINUTES.toMillis(playerAgeMinutes);
        long playerAgeSeconds = TimeUnit.MILLISECONDS.toSeconds(playerAge);

        sender.sendMessage(ChatColor.GREEN + args[0] + " joined " + ChatColor.YELLOW + new SimpleDateFormat("MM/dd/yyyy").format(joinDate) + ChatColor.GREEN + " (" + ((playerAgeDays >= 1) ? playerAgeDays + "d " : "") + playerAgeHours + "h " + playerAgeMinutes + "m " + playerAgeSeconds + "s ago)");
        return true;
    }
}
