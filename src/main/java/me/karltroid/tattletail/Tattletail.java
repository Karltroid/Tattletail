package me.karltroid.tattletail;

import com.google.common.collect.ImmutableSet;
import me.karltroid.tattletail.hooks.DiscordSRVHook;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.modernbeta.modernbeta.ModernBeta;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class Tattletail extends JavaPlugin implements Listener
{
    static Tattletail main;
    private FileConfiguration config;
    int oldPlayerHourAge; // how old the player needs to be in hours on the server to stop being tracked to avoid admin spam
    UUID lastSuspect = null, lastInnocent = null;
    Material lastBlock = null, lastItem = null;
    ModernBeta modernBeta;
    boolean discordSRVInstalled = false;

    public List<UUID[]> ignorePlayerCombos = new ArrayList<>();
    public List<Location> ignoreLocations = new ArrayList<>();

    private static final Set<Material> IGNORE_TYPES = ImmutableSet.of( // all item types that shouldn't be protected unless special
            Material.OAK_LEAVES, Material.OAK_LOG, Material.OAK_SAPLING, Material.SPRUCE_LEAVES, Material.SPRUCE_LOG, Material.SPRUCE_SAPLING, Material.BIRCH_LEAVES,
            Material.BIRCH_LOG, Material.BIRCH_SAPLING, Material.DARK_OAK_LEAVES, Material.DARK_OAK_LOG, Material.DARK_OAK_SAPLING, Material.JUNGLE_LEAVES, Material.JUNGLE_LOG, Material.JUNGLE_SAPLING,
            Material.ACACIA_LEAVES, Material.ACACIA_LOG, Material.ACACIA_SAPLING, Material.MANGROVE_LEAVES, Material.MANGROVE_LOG, Material.GRASS_BLOCK,
            Material.SHORT_GRASS, Material.DIRT, Material.GRAVEL, Material.SAND, Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.SUGAR_CANE,
            Material.BEETROOTS, Material.TALL_GRASS, Material.NETHERRACK, Material.SWEET_BERRY_BUSH, Material.WARPED_FUNGUS, Material.CRIMSON_FUNGUS
    );

    private static final Set<Material> GRIEF_BLOCK_TYPES = ImmutableSet.of(
            Material.TNT
    );

    private static final Set<Material> GRIEF_ITEM_TYPES = ImmutableSet.of(
            Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.TNT_MINECART, Material.LAVA_BUCKET
    );


    boolean testMode = false;
    boolean modernBetaInstalled = false;

    CoreProtectAPI coreProtect;
    DogKiller dogKiller = new DogKiller();

    @Override
    public void onEnable()
    {
        main = this; // set singleton instance of the plugin

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(dogKiller, this);

        coreProtect = getCoreProtect();
        if (coreProtect != null) // Ensure we have access to the API
            System.out.println("CoreProtect API found!");

        if (Bukkit.getServer().getPluginManager().getPlugin("ModernBeta") != null)
        {
            modernBeta = ModernBeta.main;
            modernBetaInstalled = true;
        }


        if (Bukkit.getServer().getPluginManager().getPlugin("DiscordSRV") != null)
        {
            discordSRVInstalled = true;
            DiscordSRVHook.register();
        }

        PluginCommand joinAgeCommand = getCommand("joinage");
        PluginCommand tattletailCommands = getCommand("tattletail");
        if (joinAgeCommand != null) joinAgeCommand.setExecutor(new JoinAgeCommand());
        if (joinAgeCommand != null) tattletailCommands.setExecutor(new TattletailCommands());

        config = loadConfigFile("config.yml");

        oldPlayerHourAge = config.getInt("OldPlayerHourAge", 24);

        Database.createTables();
        Database.loadIgnoredLocations();
        Database.loadIgnoredPlayers();

        for (UUID[] uuid : Tattletail.getInstance().ignorePlayerCombos)
            Tattletail.log(uuid[0].toString() + " - " + uuid[1].toString());

        for (Location location : Tattletail.getInstance().ignoreLocations)
            Tattletail.log(location.getWorld().getName() + " > " + location.getX() + "," + location.getY() + "," + location.getZ());
    }

    @Override
    public void onDisable()
    {
        if (Bukkit.getServer().getPluginManager().getPlugin("DiscordSRV") != null)
            DiscordSRVHook.unregister();

        Database.saveIgnoredLocations();
        Database.saveIgnoredPlayers();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        Player pyromaniac = event.getPlayer();
        if(isOldPlayer(pyromaniac.getUniqueId())) return;

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack itemUsed = event.getItem();
        if(itemUsed == null || !GRIEF_ITEM_TYPES.contains(event.getItem().getType())) return;

        if (lastSuspect == pyromaniac.getUniqueId() && lastItem == itemUsed.getType()) return;

        lastSuspect = pyromaniac.getUniqueId();
        lastItem = itemUsed.getType();

        Location pyromaniacLocation = pyromaniac.getLocation();
        alertAdmins(ChatColor.RED + "" + ChatColor.BOLD + pyromaniac.getName() + ChatColor.RED + " used a " + ChatColor.BOLD + itemUsed.getType().name().toLowerCase().replaceAll("_", " ")  + ChatColor.GRAY + " [" + pyromaniacLocation.getBlockX() + " " + pyromaniacLocation.getBlockY() + " " + pyromaniacLocation.getBlockZ() + (pyromaniac.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event)
    {
        Player blockPlacer = event.getPlayer();
        if(isOldPlayer(blockPlacer.getUniqueId())) return;

        Block block = event.getBlock();
        Material blockType = block.getType();

        if (!GRIEF_BLOCK_TYPES.contains(blockType)) return;

        if (lastSuspect == blockPlacer.getUniqueId() && lastBlock == blockType) return;

        lastSuspect = blockPlacer.getUniqueId();
        lastBlock = blockType;

        alertAdmins(ChatColor.RED + "" + ChatColor.BOLD + blockPlacer.getName() + ChatColor.RED + " placed a " + ChatColor.BOLD + blockType.name().toLowerCase().replaceAll("_", " ") + ChatColor.GRAY + " [" + block.getX() + " " + block.getY() + " " + block.getZ() + (block.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event)
    {
        Block block = event.getBlock();
        if (IGNORE_TYPES.contains(block.getType()) || ignoreLocations.contains(block.getLocation())) return;

        Player blockBreaker = event.getPlayer();
        if(isOldPlayer(blockBreaker.getUniqueId())) return;

        List<String[]> lookupResult = coreProtect.blockLookup(block, 0);
        if (lookupResult.isEmpty()) return;

        CoreProtectAPI.ParseResult result = null;
        for (int i = lookupResult.size() - 1; i >= 0; i--) // loop from the beginning
        {
            result = coreProtect.parseResult(lookupResult.get(i));

            if (result.getActionId() == 1 && result.getBlockData().getMaterial() == block.getType()) break;

            if (i == 0)
                return;
        }

        OfflinePlayer placedBy = this.getServer().getOfflinePlayer(result.getPlayer());
        if (placedBy.getName() == null || placedBy.getName().toCharArray()[0] == '#') return;
        if (ignorePlayers(blockBreaker.getUniqueId(), placedBy.getUniqueId())) return;
        if (lastSuspect == blockBreaker.getUniqueId() && lastInnocent == placedBy.getUniqueId() && lastBlock == block.getType()) return;

        lastSuspect = blockBreaker.getUniqueId();
        lastInnocent = placedBy.getUniqueId();
        lastBlock = block.getType();

        alertAdmins(ChatColor.RED + "" + ChatColor.BOLD + blockBreaker.getName() + ChatColor.RED + " broke a " + ChatColor.BOLD + block.getType().name().toLowerCase().replaceAll("_", " ") + ChatColor.RED + " placed by " + ChatColor.BOLD + placedBy.getName() + ChatColor.GRAY + " [" + block.getX() + " " + block.getY() + " " + block.getZ() + (block.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
    }

    @EventHandler
    public void onChestTake(InventoryClickEvent event)
    {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || clickedInventory.getType() == InventoryType.PLAYER) return;

        Player thief = (Player) event.getWhoClicked();
        if (isOldPlayer(thief.getUniqueId())) return;

        ItemStack itemStolen = event.getCurrentItem();
        if (itemStolen == null || itemStolen.getType() == Material.AIR) return;

        Block containerBlock;
        if (modernBeta != null) containerBlock = thief.getTargetBlock(null, 5);
        else
        {
            if (event.getInventory().getHolder() instanceof Container container)
                containerBlock = container.getBlock();
            else return;
        }

        if (ignoreLocations.contains(containerBlock.getLocation())) return;

        List<String[]> lookupResult = coreProtect.blockLookup(containerBlock, 0);

        if (modernBeta != null && modernBeta.getFatChests().isDoubleChest(containerBlock.getType()))
        {
            List<Block> neighboringChests = modernBeta.getFatChests().getAdjacentChests(containerBlock);
            if (!neighboringChests.isEmpty())
            {
                Block neighboringContainerBlock = neighboringChests.get(0);
                lookupResult.addAll(coreProtect.blockLookup(neighboringContainerBlock, 0));
            }
        }

        if (lookupResult.isEmpty()) return;

        CoreProtectAPI.ParseResult result = null;
        for (int i = lookupResult.size() - 1; i >= 0; i--)
        {
            result = coreProtect.parseResult(lookupResult.get(i));
            if (result == null) continue;

            Material resultMaterial = result.getBlockData() != null ? result.getBlockData().getMaterial() : null;
            if (resultMaterial == null) continue;

            if (result.getActionId() == 1 && resultMaterial == containerBlock.getType()) break;

            if (i == 0) return;
        }

        if (result == null) return;

        OfflinePlayer chestOwner = this.getServer().getOfflinePlayer(result.getPlayer());
        if (ignorePlayers(thief.getUniqueId(), chestOwner.getUniqueId())) return;

        String itemStolenName = itemStolen.getType().name().toLowerCase().replaceAll("_", " ");

        if (lastSuspect == thief.getUniqueId() && lastInnocent == chestOwner.getUniqueId() && lastItem == itemStolen.getType()) return;

        lastSuspect = thief.getUniqueId();
        lastInnocent = chestOwner.getUniqueId();
        lastItem = itemStolen.getType();

        alertAdmins(ChatColor.RED + "" + ChatColor.BOLD + thief.getName() + ChatColor.RED + " took " + ChatColor.BOLD + itemStolen.getAmount() + " " + itemStolenName + ChatColor.RED +  " from " + ChatColor.BOLD + chestOwner.getName() + "'s " + (isModernBetaChest(containerBlock) ? "chest" : result.getBlockData().getMaterial().name().toLowerCase().replaceAll("_", " ")) + ChatColor.GRAY + " [" + containerBlock.getX() + " " + containerBlock.getY() + " " + containerBlock.getZ() + (containerBlock.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
    }

    boolean isModernBetaChest(Block block)
    {
        if (!modernBetaInstalled) return false;

        Material blockType = block.getType();
        return switch (blockType) {
            case BROWN_SHULKER_BOX, BLACK_SHULKER_BOX, WHITE_SHULKER_BOX -> true;
            default -> false;
        };
    }

    boolean isOldPlayer(UUID playerUUID)
    {
        OfflinePlayer player = this.getServer().getOfflinePlayer(playerUUID);
        if (!player.hasPlayedBefore()) return false;

        long playerAge = System.currentTimeMillis() - player.getFirstPlayed();
        long playerAgeHours = TimeUnit.MILLISECONDS.toHours(playerAge);

        return playerAgeHours > oldPlayerHourAge && !testMode;
    }

    void alertAdmins(String alertMessage)
    {
        if (discordSRVInstalled)
            DiscordSRVHook.sendMessage(alertMessage);

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (player.hasPermission("tattletail.admin"))
                player.sendMessage(alertMessage);
        }
    }

    private CoreProtectAPI getCoreProtect()
    {
        Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");

        // Check that CoreProtect is loaded
        if (!(plugin instanceof CoreProtect)) return null;

        // Check that the API is enabled
        CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
        if (!CoreProtect.isEnabled()) return null;

        // Check that a compatible version of the API is loaded
        if (CoreProtect.APIVersion() < 9) return null;

        return CoreProtect;
    }

    private FileConfiguration loadConfigFile(String fileName) {
        File configFile = new File(getDataFolder(), fileName);
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource(fileName, false);
        }

        return YamlConfiguration.loadConfiguration(configFile);
    }

    private boolean ignorePlayers(UUID player1UUID, UUID player2UUID)
    {
        if (player1UUID == player2UUID) return true; // never alert for someone modifying their own stuff

        UUID[] ignoredPlayer = getIgnoredPlayer(player1UUID, player2UUID);
        return ignoredPlayer != null; // not null means it was found, so ignore them
    }

    public UUID[] getIgnoredPlayer(UUID player1UUID, UUID player2UUID) {
        List<UUID[]> invalidsFound = new ArrayList<>();

        for (UUID[] ignorePlayerCombo : ignorePlayerCombos)
        {
            if (ignorePlayerCombo.length != 2)
            {
                getLogger().info("Invalid ignorePlayerCombo, removing");
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
}