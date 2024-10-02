package me.karltroid.tattletail.autobans;

import me.karltroid.tattletail.Tattletail;
import me.karltroid.tattletail.hooks.CoreProtectHook;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

import static me.karltroid.tattletail.Tattletail.Staff.Mod;

public class Arsonist implements Listener {

    HashMap<Player, Integer> arsonistLikelihood = new HashMap<>();
    HashMap<Block, BukkitRunnable> toBeCheckedFires = new HashMap<>();
    HashMap<Block, BukkitRunnable> toBeCheckedLavas = new HashMap<>();

    List<Material> manMadeBurnables = new ArrayList<>(Arrays.asList(
            Material.OAK_PLANKS, Material.OAK_FENCE, Material.OAK_STAIRS, Material.WHITE_WOOL, Material.BLACK_WOOL, Material.BLUE_WOOL, Material.BROWN_WOOL, Material.GREEN_WOOL, Material.CYAN_WOOL, Material.GRAY_WOOL, Material.LIGHT_BLUE_WOOL, Material.LIGHT_GRAY_WOOL, Material.LIME_WOOL, Material.MAGENTA_WOOL, Material.ORANGE_WOOL, Material.PINK_WOOL, Material.PURPLE_WOOL, Material.RED_WOOL, Material.YELLOW_WOOL
    ));

    boolean addToArsonistLikelihood(Player player, int increase) {
        int newLikelihood = arsonistLikelihood.merge(player, increase, Integer::sum);

        if ((newLikelihood >= 5 && (!Tattletail.isOldPlayer(player.getUniqueId()) || !Tattletail.isNotMonitored(player.getUniqueId()))) || (newLikelihood >= 15 && Tattletail.isOldPlayer(player.getUniqueId()))) {
            arsonistLikelihood.remove(player);
            return true;
        }

        return false;
    }

    @EventHandler
    void onFirePlaced(BlockPlaceEvent event) {

        Block fireBlock = event.getBlockPlaced();

        // get if fire was placed
        if (!fireBlock.getType().equals(Material.FIRE)) {
            return;
        }

        Player potentialArsonist = event.getPlayer();
        OfflinePlayer playerBeingGriefed = getWhoArsonIsBeingCommittedAgainst(fireBlock, potentialArsonist);
        if (playerBeingGriefed == null) return;

        // check some ticks later if the fire block still exists
        BukkitRunnable checkFireTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (fireBlock.getType().equals(Material.FIRE)) {
                    if (addToArsonistLikelihood(potentialArsonist, 1))
                        Tattletail.banPlayer(potentialArsonist, true, "Setting builds on fire that aren't yours repeatedly.");
                    Tattletail.getInstance().alertStaff(Mod, ChatColor.RED + "" + ChatColor.BOLD + potentialArsonist.getName() + ChatColor.RED + " is burning down " + ChatColor.RED + "" + ChatColor.BOLD + playerBeingGriefed.getName() + "'s" + ChatColor.RED + " build! " + ChatColor.GRAY + " [" + fireBlock.getX() + " " + fireBlock.getY() + " " + fireBlock.getZ() + (fireBlock.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
                }
                // Remove from the HashMap after checking
                toBeCheckedFires.remove(fireBlock);
            }
        };
        checkFireTask.runTaskLater(Tattletail.getInstance(), 50);
        toBeCheckedFires.put(fireBlock, checkFireTask);
    }

    @EventHandler
    void onLavaPlaced(PlayerBucketEmptyEvent event) {

        Block lavaBlock = event.getBlock();

        // get if fire was placed
        if(!event.getBucket().equals(Material.LAVA_BUCKET)) {
            return;
        }

        Player potentialArsonist = event.getPlayer();
        OfflinePlayer playerBeingGriefed = getWhoArsonIsBeingCommittedAgainst(lavaBlock, potentialArsonist);
        if (playerBeingGriefed == null) return;

        // check some ticks later if the fire block still exists
        BukkitRunnable checkLavaTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (lavaBlock.getType().equals(Material.LAVA)) {
                    if (addToArsonistLikelihood(potentialArsonist, 2))
                        Tattletail.banPlayer(potentialArsonist, true, "Dumping lava on builds that aren't yours repeatedly.");
                    Tattletail.getInstance().alertStaff(Mod, ChatColor.RED + "" + ChatColor.BOLD + potentialArsonist.getName() + ChatColor.RED + " is dumping lava on " + ChatColor.RED + "" + ChatColor.BOLD + playerBeingGriefed.getName() + "'s" + ChatColor.RED + " build! " + ChatColor.GRAY + " [" + lavaBlock.getX() + " " + lavaBlock.getY() + " " + lavaBlock.getZ() + (lavaBlock.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
                }
                // Remove from the HashMap after checking
                toBeCheckedLavas.remove(lavaBlock);
            }
        };
        checkLavaTask.runTaskLater(Tattletail.getInstance(), 50);
        toBeCheckedLavas.put(lavaBlock, checkLavaTask);
    }

    @EventHandler
    void onTNTPrimed(TNTPrimeEvent event) {
        Block tntBlock = event.getBlock();
        if (!(event.getPrimingEntity() instanceof Player player)) return;
        
        OfflinePlayer playerBeingGriefed = getWhoArsonIsBeingCommittedAgainst(tntBlock, player);
        if (playerBeingGriefed == null) return;

        if (addToArsonistLikelihood(player, 3))
            Tattletail.banPlayer(player, true, "Exploding builds that aren't yours repeatedly.");
        Tattletail.getInstance().alertStaff(Mod, ChatColor.RED + "" + ChatColor.BOLD + player.getName() + ChatColor.RED + " is exploding " + ChatColor.RED + "" + ChatColor.BOLD + playerBeingGriefed.getName() + "'s" + ChatColor.RED + " build! " + ChatColor.GRAY + " [" + tntBlock.getX() + " " + tntBlock.getY() + " " + tntBlock.getZ() + (tntBlock.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));

    }

    private OfflinePlayer getWhoArsonIsBeingCommittedAgainst(Block arsonBlock, Player potentialArsonist) {

        // 99.99% of the time lava placed underground is legit gameplay
        if (arsonBlock.getType().equals(Material.LAVA) && arsonBlock.getY() <= 63) return null;

        UUID potentialArsonistUUID = potentialArsonist.getUniqueId();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    // Skip the center block (the block itself)
                    if (x == 0 && y == 0 && z == 0) continue;

                    // Get the neighboring block and check if it's a burnable material
                    Block neighboringBlock = arsonBlock.getRelative(x, y, z);
                    if (Tattletail.IGNORE_TYPES.contains(neighboringBlock.getType())) continue;
                    if (arsonBlock.getType().equals(Material.FIRE) && !manMadeBurnables.contains(neighboringBlock.getType())) continue;

                    OfflinePlayer blockOwner = CoreProtectHook.getWhoOwnsBlock(neighboringBlock);
                    if (blockOwner == null) continue;
                    UUID blockOwnerUUID = blockOwner.getUniqueId();
                    if (blockOwnerUUID.equals(potentialArsonistUUID) || Tattletail.ignorePlayers(potentialArsonistUUID, blockOwnerUUID)) continue;
                    
                    return blockOwner;
                }
            }
        }

        return null;
    }

}
