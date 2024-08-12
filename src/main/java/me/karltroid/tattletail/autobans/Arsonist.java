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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class Arsonist implements Listener {

    HashMap<Player, Integer> arsonistLikelihood = new HashMap<>();
    HashMap<Block, BukkitRunnable> toBeCheckedFires = new HashMap<>();

    List<Material> manMadeBurnables = new ArrayList<>(Arrays.asList(
            Material.OAK_PLANKS, Material.WHITE_WOOL, Material.BLACK_WOOL, Material.BLUE_WOOL, Material.BROWN_WOOL, Material.GREEN_WOOL, Material.CYAN_WOOL, Material.GRAY_WOOL, Material.LIGHT_BLUE_WOOL, Material.LIGHT_GRAY_WOOL, Material.LIME_WOOL, Material.MAGENTA_WOOL, Material.ORANGE_WOOL, Material.PINK_WOOL, Material.PURPLE_WOOL, Material.RED_WOOL, Material.YELLOW_WOOL
    ));

    void addToArsonistLikelihood(Player player) {
        int newLikelihood = arsonistLikelihood.merge(player, 1, Integer::sum);

        if (newLikelihood >= 5 && (!Tattletail.isOldPlayer(player.getUniqueId()) || !Tattletail.isNotMonitored(player.getUniqueId()))) {
            arsonistLikelihood.remove(player);
            Tattletail.banPlayer(player, "Setting builds on fire that aren't yours repeatedly.");
        }
    }

    @EventHandler
    void onFirePlaced(BlockPlaceEvent event) {

        Block fireBlock = event.getBlockPlaced();

        // get if fire was placed
        if (!fireBlock.getType().equals(Material.FIRE)) {
            return;
        }

        Player potentialArsonist = event.getPlayer();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    // Skip the center block (the block itself)
                    if (x == 0 && y == 0 && z == 0) continue;

                    // Get the neighboring block and check if it's a burnable material
                    Block neighboringBlock = fireBlock.getRelative(x, y, z);
                    if (!manMadeBurnables.contains(neighboringBlock.getType())) continue;

                    OfflinePlayer blockOwner = CoreProtectHook.getWhoOwnsBlock(neighboringBlock);
                    if (blockOwner == null || blockOwner.getUniqueId().equals(potentialArsonist.getUniqueId()) || Tattletail.ignorePlayers(potentialArsonist.getUniqueId(), blockOwner.getUniqueId())) continue;

                    // check some ticks later if the fire block still exists
                    BukkitRunnable checkFireTask = new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (fireBlock.getType().equals(Material.FIRE)) {
                                addToArsonistLikelihood(potentialArsonist);
                                Tattletail.getInstance().alertAdmins(ChatColor.RED + "" + ChatColor.BOLD + potentialArsonist.getName() + ChatColor.RED + " is burning down " + ChatColor.RED + "" + ChatColor.BOLD + blockOwner.getName() + "'s" + ChatColor.RED + " build! " + ChatColor.GRAY + " [" + fireBlock.getX() + " " + fireBlock.getY() + " " + fireBlock.getZ() + (fireBlock.getWorld().getName().contains("_nether") ? " (nether)]" : "]"));
                            }
                            // Remove from the HashMap after checking
                            toBeCheckedFires.remove(fireBlock);
                        }
                    };
                    checkFireTask.runTaskLater(Tattletail.getInstance(), 50);
                    toBeCheckedFires.put(fireBlock, checkFireTask);
                    return; // stop search, we gottem 8)
                }
            }
        }

    }

}
