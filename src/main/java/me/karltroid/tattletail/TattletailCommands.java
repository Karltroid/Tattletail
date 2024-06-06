package me.karltroid.tattletail;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class TattletailCommands implements CommandExecutor
{
    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd, @NotNull String label, String[] args)
    {
        if (sender instanceof Player player && !player.hasPermission("tattletail.admin"))
        {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use /tattletail");
            return false;
        }

        // Check if a sub command was given
        if (args.length == 0)
        {
            sender.sendMessage(ChatColor.RED + "No sub command given for /tattletail (ex: /tattletail ignore)");
            return false;
        }

        // get which sub command was given
        if (args[0].equalsIgnoreCase("ignoreplayers") || args[0].equalsIgnoreCase("unignoreplayers"))
        {
            return ignorePlayersCommand(sender, args);
        }
        else if (args[0].equalsIgnoreCase("ignoreblock") || args[0].equalsIgnoreCase("unignoreblock"))
        {
            return ignoreBlockCommand(sender, args);
        }

        // if we got to this point, a valid sub command was not given.
        sender.sendMessage(ChatColor.RED + "\"/tattletail " + args[0] + "\" is not a valid sub command.");
        return false;
    }

    private static boolean ignorePlayersCommand(@NotNull CommandSender sender, String[] args) {
        boolean unIgnore = args[0].equalsIgnoreCase("unignoreplayers");

        if (args.length != 3)
        {
            sender.sendMessage(ChatColor.RED + "Usage: /tattletail " + (unIgnore ? "unignoreplayers" : "ignoreplayers") +  " <player1name> <player2name>");
            return false;
        }

        // Get the player's join date and age
        OfflinePlayer player1 = Bukkit.getOfflinePlayer(args[1]);
        OfflinePlayer player2 = Bukkit.getOfflinePlayer(args[2]);

        UUID[] ignoredPlayer = Tattletail.main.getIgnoredPlayer(player1.getUniqueId(), player2.getUniqueId());

        if (unIgnore)
        {
            if (ignoredPlayer == null)
            {
                sender.sendMessage(ChatColor.RED + "These two players are already not being ignored.");
                return false;
            }
            else
            {
                Tattletail.main.ignorePlayerCombos.remove(ignoredPlayer);
                Tattletail.getInstance().alertAdmins(ChatColor.RED + "[-] " + ChatColor.GREEN + args[1] + " and " + args[2] + " will no longer be ignored when they steal or grief from each other. - set by " + getSenderName(sender));
                return true;
            }
        }
        else
        {
            if (ignoredPlayer != null)
            {
                sender.sendMessage(ChatColor.RED + "These two players are already being ignored.");
                return false;
            }
            else
            {
                Tattletail.main.ignorePlayerCombos.add(new UUID[]{player1.getUniqueId(), player2.getUniqueId()});
                Tattletail.getInstance().alertAdmins(ChatColor.GREEN + "[+] " + args[1] + " and " + args[2] + " will now be ignored when they steal or grief from each other. - set by " + getSenderName(sender));
                return true;
            }
        }
    }

    private static boolean ignoreBlockCommand(@NotNull CommandSender sender, String[] args) {
        World world;

        boolean unIgnore = args[0].equalsIgnoreCase("unignoreblock");

        if (!(sender instanceof Player))
        {
            // command run by console needs additional parameter of the world name
            if (args.length != 5)
            {
                sender.sendMessage(ChatColor.RED + "Usage: /tattletail " + (unIgnore ? "unignoreblock" : "ignoreblock") + " <x> <y> <z> <world>");
                return false;
            }

            world = Bukkit.getWorld(args[4]);
            if (world == null)
            {
                sender.sendMessage(ChatColor.RED + "This world does not exist.");
                return false;
            }
        }
        else
        {
            if (args.length != 4)
            {
                sender.sendMessage(ChatColor.RED + "Usage: /tattletail " + (unIgnore ? "unignoreblock" : "ignoreblock") + " <x> <y> <z>");
                return false;
            }
            world = ((Player) sender).getWorld();
        }

        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int z = Integer.parseInt(args[3]);

        Location location = new Location(world, x, y, z);
        if (unIgnore)
        {
            if (!Tattletail.main.ignoreLocations.contains(location))
            {
                sender.sendMessage(ChatColor.RED + "This block location is not being ignored already.");
                return false;
            }

            Tattletail.main.ignoreLocations.remove(location);
            Tattletail.getInstance().alertAdmins(ChatColor.RED + "[-] " + ChatColor.GREEN + "Tattletail alerts that occur at [" + x + "," + y + "," + z + "] will no longer be ignored - set by " + getSenderName(sender));
        }
        else
        {
            if (Tattletail.main.ignoreLocations.contains(location))
            {
                sender.sendMessage(ChatColor.RED + "This block location is already being ignored.");
                return false;
            }

            Tattletail.main.ignoreLocations.add(location);
            Tattletail.getInstance().alertAdmins(ChatColor.GREEN + "[+] Tattletail alerts that occur at [" + x + "," + y + "," + z + "] will now be ignored - set by " + getSenderName(sender));
        }

        return true;
    }

    static String getSenderName(CommandSender sender)
    {
        if (sender instanceof Player player)
            return player.getName();
        else
            return "CONSOLE";
    }
}
