package me.karltroid.tattletail.hooks;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import me.karltroid.tattletail.Tattletail;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public class DiscordSRVHook
{
    private static final DiscordSRVHook instance = new DiscordSRVHook();
    public static TextChannel discordAdminBroadcastTextChannel;
    public static TextChannel discordModBroadcastTextChannel;
    private DiscordSRVHook() {}

    @Subscribe
    public void discordReadyEvent(DiscordReadyEvent event)
    {
        discordAdminBroadcastTextChannel = DiscordSRV.getPlugin().getJda().getTextChannelById(Tattletail.getInstance().getPluginConfig().getString("DiscordAdminLogChannelID", "1143007010049232896"));
        discordModBroadcastTextChannel = DiscordSRV.getPlugin().getJda().getTextChannelById(Tattletail.getInstance().getPluginConfig().getString("DiscordModLogChannelID", "1143007010049232896"));
        if (discordAdminBroadcastTextChannel != null)
            Bukkit.getLogger().info("DiscordSRV Ready For TattleTail");
        else
            Bukkit.getLogger().warning("DiscordSRV couldn't find channel for PremiumManager");
    }


    public static void sendMessage(TextChannel textChannel, String message)
    {
        if (textChannel != null)
            textChannel.sendMessage(ChatColor.stripColor(message)).complete();
    }

    public static void register()
    {
        DiscordSRV.api.subscribe(instance);
    }

    public static void unregister()
    {
        DiscordSRV.api.unsubscribe(instance);
    }
}

