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
    private TextChannel discordBroadcastTextChannel;
    private DiscordSRVHook() {}

    @Subscribe
    public void discordReadyEvent(DiscordReadyEvent event)
    {
        discordBroadcastTextChannel = DiscordSRV.getPlugin().getJda().getTextChannelById(Tattletail.getInstance().getPluginConfig().getString("DiscordLogChannelID", "1143007010049232896"));
        Bukkit.getLogger().info("DiscordSRV Ready For TattleTail");
    }


    public static void sendMessage(String message)
    {
        if (instance.discordBroadcastTextChannel != null)
        {
            instance.discordBroadcastTextChannel.sendMessage(ChatColor.stripColor(message)).complete();
        }

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

