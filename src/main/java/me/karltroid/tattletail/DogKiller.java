package me.karltroid.tattletail;

import org.bukkit.Bukkit;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashSet;
import java.util.Set;

public class DogKiller implements Listener
{
    Set<Player> dogAbusers = new HashSet<>();

    @EventHandler
    void addPotentialDogKillersToList(EntityDamageByEntityEvent event)
    {
        // only looking for sitting dogs
        if (!(event.getEntity() instanceof Wolf wolf) || !wolf.isTamed()) return;

        // exit if the damager isn't player or is player but owner
        if (!(event.getDamager() instanceof Player abuser) || abuser.hasPermission("tattletail.admin")) return;
        if (wolf.getOwner() == null || wolf.getOwner().getUniqueId().equals(abuser.getUniqueId())) return;

        // add to list if the wolf was innocent
        if (wolf.getTarget() == null || wolf.isSitting())
            dogAbusers.add(abuser);
    }

    @EventHandler
    void onPlayerKillingDog(EntityDeathEvent event)
    {
        // exit if wolf is standing, only looking for sitting dogs
        if (!(event.getEntity() instanceof Wolf wolf) || !wolf.isTamed()) return;

        // exit if the killer or owner is unknown or the owner is killing their own dog
        Player killer = wolf.getKiller();
        AnimalTamer owner = wolf.getOwner();
        if (killer == null || owner == null || killer.getUniqueId().equals(wolf.getOwner().getUniqueId()) || killer.hasPermission("tattletail.admin")) return;

        // banish them!
        if (dogAbusers.contains(killer))
        {
            dogAbusers.remove(killer);
            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "ban " + killer.getName() + " " + (Tattletail.getInstance().isOldPlayer(killer.getUniqueId()) ? "1h" : "8h") + " [TattletailAutoBan] Killed an innocent dog, you monster. When an admin is available they will look into this and make a finalized punishment. If this was an accident please make a ticket on our Discord, https://discord.modernbeta.org");
        }
    }
}
