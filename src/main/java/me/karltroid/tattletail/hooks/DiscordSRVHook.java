package me.karltroid.tattletail.hooks;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import me.karltroid.tattletail.Tattletail;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class DiscordSRVHook
{
    private static final DiscordSRVHook instance = new DiscordSRVHook();
    public static final String DISCORD_ADMIN_CHANNEL = "tattletail-admin";
    public static final String DISCORD_MOD_CHANNEL = "tattletail-mod";
    public static TextChannel discordAdminBroadcastTextChannel;
    public static TextChannel discordModBroadcastTextChannel;
    private DiscordSRVHook() {}

    @Subscribe
    public void discordReadyEvent(DiscordReadyEvent event) {
        if (textChannelFound(DISCORD_ADMIN_CHANNEL) && textChannelFound(DISCORD_MOD_CHANNEL))
            Bukkit.getLogger().info("DiscordSRV Ready For TattleTail");
        else {
            Tattletail.getInstance().discordSRVInstalled = false;
            DiscordSRV.api.unsubscribe(instance);
            Bukkit.getLogger().warning("DiscordSRV couldn't find textchannel, disabling connection");
        }
    }

    private boolean textChannelFound(String textChannel) {
        return DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(textChannel) != null;
    }

    public static void sendMessage(String textChannel, String message) {
        new BukkitRunnable() {
            @Override
            public void run() {
                TextChannel destinationChannel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(textChannel);
                DiscordUtil.sendMessage(destinationChannel, MessageUtil.strip(message));
            }
        }.runTaskAsynchronously(Tattletail.getInstance());
    }

    public static void register() {
        DiscordSRV.api.subscribe(instance);
    }

    public static void unregister() {
        DiscordSRV.api.unsubscribe(instance);
    }
}

