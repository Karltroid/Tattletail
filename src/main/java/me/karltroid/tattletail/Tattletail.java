package me.karltroid.tattletail;

import com.google.common.collect.ImmutableSet;
import me.karltroid.tattletail.autobans.Arsonist;
import me.karltroid.tattletail.autobans.DogKiller;
import me.karltroid.tattletail.commands.JoinAgeCommand;
import me.karltroid.tattletail.commands.TattletailCommand;
import me.karltroid.tattletail.hooks.CoreProtectHook;
import me.karltroid.tattletail.hooks.DiscordSRVHook;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.modernbeta.modernbeta.ModernBeta;
import org.modernbeta.modernbeta.blocks.chests.ChestInstance;
import org.modernbeta.modernbeta.blocks.chests.FatChests;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static me.karltroid.tattletail.Tattletail.Staff.Admin;
import static me.karltroid.tattletail.Tattletail.Staff.Mod;

public final class Tattletail extends JavaPlugin implements Listener
{
    static Tattletail main;
    private FileConfiguration config;
    static int oldPlayerHourAge; // how old the player needs to be in hours on the server to stop being tracked to avoid admin spam
    UUID lastSuspect = null, lastInnocent = null;
    Material lastBlock = null, lastItem = null;
    public boolean discordSRVInstalled = false;
    CoreProtectHook coreProtectHook;

    public static List<UUID[]> ignorePlayerCombos = new ArrayList<>();
    public static List<Location> ignoreLocations = new ArrayList<>();
    public static List<UUID> watchPlayers = new ArrayList<>();

    public static HashMap<String, UUID> cachedUUIDs = new HashMap<>();

    public static final Set<Material> IGNORE_TYPES = ImmutableSet.of( // all item types that shouldn't be protected unless special
            Material.OAK_LEAVES, Material.OAK_LOG, Material.OAK_SAPLING, Material.SPRUCE_LEAVES, Material.SPRUCE_LOG, Material.SPRUCE_SAPLING, Material.BIRCH_LEAVES,
            Material.BIRCH_LOG, Material.BIRCH_SAPLING, Material.DARK_OAK_LEAVES, Material.DARK_OAK_LOG, Material.DARK_OAK_SAPLING, Material.JUNGLE_LEAVES, Material.JUNGLE_LOG, Material.JUNGLE_SAPLING,
            Material.ACACIA_LEAVES, Material.ACACIA_LOG, Material.ACACIA_SAPLING, Material.MANGROVE_LEAVES, Material.MANGROVE_LOG, Material.GRASS_BLOCK,
            Material.SHORT_GRASS, Material.DIRT, Material.GRAVEL, Material.SAND, Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.SUGAR_CANE,
            Material.BEETROOTS, Material.TALL_GRASS, Material.NETHERRACK, Material.SWEET_BERRY_BUSH, Material.WARPED_FUNGUS, Material.CRIMSON_FUNGUS, Material.TORCH, Material.WALL_TORCH, Material.CACTUS
    );

    private static final Set<Material> GRIEF_BLOCK_TYPES = ImmutableSet.of(
            Material.TNT
    );

    private static final Set<Material> GRIEF_ITEM_TYPES = ImmutableSet.of(
            Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.TNT_MINECART, Material.LAVA_BUCKET
    );

    static boolean testMode = false;
    public boolean modernBetaInstalled = false;

    DogKiller dogKiller = new DogKiller();
    Arsonist arsonist = new Arsonist();
    WordFilter wordFilter;

    public enum Staff {
        Admin,
        Mod
    }

    @Override
    public void onEnable()
    {
        main = this; // set singleton instance of the plugin

        wordFilter = new WordFilter();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(dogKiller, this);
        getServer().getPluginManager().registerEvents(arsonist, this);
        getServer().getPluginManager().registerEvents(wordFilter, this);

        if (Bukkit.getServer().getPluginManager().getPlugin("ModernBeta") != null) {
            modernBetaInstalled = true;
        }

        if (Bukkit.getServer().getPluginManager().getPlugin("DiscordSRV") != null)
        {
            discordSRVInstalled = true;
            DiscordSRVHook.register();
        }

        coreProtectHook = new CoreProtectHook();

        PluginCommand joinAgeCommand = getCommand("joinage");
        PluginCommand tattletailCommands = getCommand("tattletail");
        if (joinAgeCommand != null) joinAgeCommand.setExecutor(new JoinAgeCommand());
        if (joinAgeCommand != null) tattletailCommands.setExecutor(new TattletailCommand());

        config = loadConfigFile("config.yml");

        oldPlayerHourAge = config.getInt("OldPlayerHourAge", 24);

        Database.createTables();
        Database.loadIgnoredLocations();
        Database.loadIgnoredPlayers();
        Database.loadMonitoredPlayers();
    }

    @Override
    public void onDisable()
    {
        if (Bukkit.getServer().getPluginManager().getPlugin("DiscordSRV") != null)
            DiscordSRVHook.unregister();

        Database.saveIgnoredLocations();
        Database.saveIgnoredPlayers();
        Database.saveMonitoredPlayers();
        CoreProtectHook.closeCoreProtectConnection();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        Player pyromaniac = event.getPlayer();
        UUID pyromaniacUUID = pyromaniac.getUniqueId();
        if(isNotMonitored(pyromaniacUUID) && isOldPlayer(pyromaniacUUID)) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack itemUsed = event.getItem();
        if(itemUsed == null || !GRIEF_ITEM_TYPES.contains(event.getItem().getType())) return;

        if (lastSuspect == pyromaniacUUID && lastItem == itemUsed.getType()) return;

        lastSuspect = pyromaniacUUID;
        lastItem = itemUsed.getType();

        Location pyromaniacLocation = pyromaniac.getLocation();
        alertStaff(Mod, ChatColor.RED + "" + ChatColor.BOLD + pyromaniac.getName() + ChatColor.RED + " used a " + ChatColor.BOLD + itemUsed.getType().name().toLowerCase().replaceAll("_", " ")  + ChatColor.GRAY + " [" + pyromaniacLocation.getBlockX() + " " + pyromaniacLocation.getBlockY() + " " + pyromaniacLocation.getBlockZ() + (pyromaniac.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event)
    {
        Player blockPlacer = event.getPlayer();
        if(isNotMonitored(blockPlacer.getUniqueId()) && isOldPlayer(blockPlacer.getUniqueId())) return;

        Block block = event.getBlock();
        Material blockType = block.getType();

        if (!GRIEF_BLOCK_TYPES.contains(blockType)) return;

        if (lastSuspect == blockPlacer.getUniqueId() && lastBlock == blockType) return;

        lastSuspect = blockPlacer.getUniqueId();
        lastBlock = blockType;

        alertStaff(Mod, ChatColor.RED + "" + ChatColor.BOLD + blockPlacer.getName() + ChatColor.RED + " placed a " + ChatColor.BOLD + blockType.name().toLowerCase().replaceAll("_", " ") + ChatColor.GRAY + " [" + block.getX() + " " + block.getY() + " " + block.getZ() + (block.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event)
    {
        Block block = event.getBlock();
        Material blockType = block.getType();
        if ((blockType.equals(Material.STONE) && block.getY() <= 63) || IGNORE_TYPES.contains(block.getType())) return;

        Player blockBreaker = event.getPlayer();
        UUID blockBreakerUUID = blockBreaker.getUniqueId();
        if(isNotMonitored(blockBreakerUUID) && isOldPlayer(blockBreakerUUID)) return;

        OfflinePlayer placedBy = CoreProtectHook.getWhoOwnsBlock(block);
        if (placedBy == null) return;
        UUID placedByUUID = placedBy.getUniqueId();

        if (ignorePlayers(blockBreakerUUID,  placedByUUID)) return;
        if (lastSuspect == blockBreakerUUID && lastInnocent == placedByUUID && lastBlock == block.getType()) return;

        lastSuspect = blockBreaker.getUniqueId();
        lastInnocent = placedByUUID;
        lastBlock = block.getType();

        alertStaff(Mod, ChatColor.RED + "" + ChatColor.BOLD + blockBreaker.getName() + ChatColor.RED + " broke a " + ChatColor.BOLD + block.getType().name().toLowerCase().replaceAll("_", " ") + ChatColor.RED + " placed by " + ChatColor.BOLD + placedBy.getName() + ChatColor.GRAY + " [" + block.getX() + " " + block.getY() + " " + block.getZ() + (block.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
    }

    @EventHandler
    public void onChestTake(InventoryClickEvent event)
    {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || clickedInventory.getType().equals(InventoryType.PLAYER) || clickedInventory.getType().equals(InventoryType.CRAFTING)) return;

        Player thief = (Player) event.getWhoClicked();
        if (isNotMonitored(thief.getUniqueId()) && isOldPlayer(thief.getUniqueId())) return;

        ItemStack itemStolen = event.getCurrentItem();
        if (itemStolen == null || itemStolen.getType() == Material.AIR) return;

        Block containerBlock;
        if (modernBetaInstalled) {
            ChestInstance chestInstance = ModernBeta.getInstance().getFatChests().getPlayersChestInstance(thief);
            if (chestInstance == null) return;

            containerBlock = chestInstance.getLocation().getBlock();
        }
        else
        {
            if (event.getInventory().getHolder() instanceof Container container)
                containerBlock = container.getBlock();
            else return;
        }

        if (ignoreLocations.contains(containerBlock.getLocation())) return;

        OfflinePlayer chestOwner = CoreProtectHook.getWhoOwnsBlock(containerBlock);
        if (chestOwner == null) return;
        UUID chestOwnerUUID = chestOwner.getUniqueId();
        UUID theifUUID = thief.getUniqueId();
        if (ignorePlayers(theifUUID, chestOwnerUUID)) return;

        String itemStolenName = itemStolen.getType().name().toLowerCase().replaceAll("_", " ");

        if (lastSuspect == theifUUID && lastInnocent == chestOwnerUUID && lastItem == itemStolen.getType()) return;

        lastSuspect = theifUUID;
        lastInnocent = chestOwnerUUID;
        lastItem = itemStolen.getType();

        alertStaff(Admin, ChatColor.RED + "" + ChatColor.BOLD + thief.getName() + ChatColor.RED + " took " + ChatColor.BOLD + itemStolen.getAmount() + " " + itemStolenName + ChatColor.RED +  " from " + ChatColor.BOLD + chestOwner.getName() + "'s " + (modernBetaInstalled && FatChests.isChestType(containerBlock.getType()) ? "chest" : containerBlock.getType().name().toLowerCase().replaceAll("_", " ")) + ChatColor.GRAY + " [" + containerBlock.getX() + " " + containerBlock.getY() + " " + containerBlock.getZ() + (containerBlock.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
    }

    public static boolean isOldPlayer(UUID playerUUID)
    {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return false;
        long firstPlayedTimeMillis = player.getFirstPlayed();
        if (firstPlayedTimeMillis <= 0) return false;

        long playerAge = System.currentTimeMillis() - firstPlayedTimeMillis;
        long playerAgeHours = TimeUnit.MILLISECONDS.toHours(playerAge);

        return playerAgeHours > oldPlayerHourAge && !testMode;
    }

    public static boolean isNotMonitored(UUID playerUUID)
    {
        return !watchPlayers.contains(playerUUID);
    }

    public void alertStaff(Staff staffType, String alertMessage)
    {
        // alert admins and mods (if requested) on discord
        if (discordSRVInstalled) {
            DiscordSRVHook.sendMessage(DiscordSRVHook.DISCORD_ADMIN_CHANNEL, alertMessage);
            if (staffType.equals(Mod))
                DiscordSRVHook.sendMessage(DiscordSRVHook.DISCORD_MOD_CHANNEL, alertMessage);
        }

        // alert admins and mods (if requested) in Minecraft
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (player.hasPermission("tattletail.admin"))
                player.sendMessage(alertMessage);
            else if (staffType.equals(Mod) && player.hasPermission("tattletail.mod"))
                player.sendMessage(alertMessage);
        }
    }

    private FileConfiguration loadConfigFile(String fileName) {
        File configFile = new File(getDataFolder(), fileName);
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource(fileName, false);
        }

        return YamlConfiguration.loadConfiguration(configFile);
    }

    public static boolean ignorePlayers(UUID player1UUID, UUID player2UUID)
    {
        if (player1UUID == player2UUID) return true; // never alert for someone modifying their own stuff

        UUID[] ignoredPlayer = getIgnoredPlayer(player1UUID, player2UUID);
        return ignoredPlayer != null; // not null means it was found, so ignore them
    }

    public static UUID[] getIgnoredPlayer(UUID player1UUID, UUID player2UUID) {
        List<UUID[]> invalidsFound = new ArrayList<>();

        for (UUID[] ignorePlayerCombo : ignorePlayerCombos)
        {
            if (ignorePlayerCombo.length != 2)
            {
                log("Invalid ignorePlayerCombo, removing");
                invalidsFound.add(ignorePlayerCombo);
                continue;
            }

            // return true to ignore if this player combo is found in the ignore list
            if ((ignorePlayerCombo[0].equals(player1UUID) && ignorePlayerCombo[1].equals(player2UUID)) ||
                (ignorePlayerCombo[0].equals(player2UUID) && ignorePlayerCombo[1].equals(player1UUID))) {
                return ignorePlayerCombo;
            }
        }

        // remove any invalid combos if found
        if (!invalidsFound.isEmpty())
            ignorePlayerCombos.removeAll(invalidsFound);

        return null;
    }

    public static void log(String message)
    {
        main.getLogger().info(message);
    }

    public FileConfiguration getPluginConfig() { return config; }
    public static Tattletail getInstance(){ return main; }

    public static void banPlayer(Player banPlayer, boolean publicBan, String reason) {

        // don't auto ban staff
        if (banPlayer.hasPermission("tattletail.admin") || banPlayer.hasPermission("tattletail.mod"))
            return;

        // don't auto ban players while staff are online
        for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
            if (otherPlayer.hasPermission("tattletail.admin"))
                return;
        }

        Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "ban " + (publicBan ? "-p " : "") + banPlayer.getName() + " " + (isOldPlayer(banPlayer.getUniqueId()) ? "30m" : "4h") + " [TattletailAutoBan] " + reason + " When an admin is available they will look at your logs and make a finalized punishment. If this was an accident, misunderstanding and the bot made a mistake, please make a ticket on our Discord, https://discord.modernbeta.org");
    }

    public static OfflinePlayer getOfflinePlayer(String name) {
        // first see if player is online, if so no need to do anything extra
        Player onlinePlayer = Bukkit.getPlayer(name);
        if (onlinePlayer != null)
            return onlinePlayer;

        // if offline, first see if players UUID has already been searched this instance and grab it from here
        UUID cachedUUID = Tattletail.cachedUUIDs.get(name);
        if (cachedUUID != null)
            return Bukkit.getOfflinePlayer(cachedUUID);

        // if not, search for the player's info and cache it for next time
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        if (offlinePlayer.getName() == null) return null;
        Tattletail.cachedUUIDs.put(offlinePlayer.getName(), offlinePlayer.getUniqueId());
        return offlinePlayer;
    }
}