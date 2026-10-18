package com.mdvcraft.mdvquest.listener;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import com.mdvcraft.mdvquest.service.PlacedBlockService;
import com.mdvcraft.mdvquest.service.ProgressService;
import com.mdvcraft.mdvquest.util.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class GameplayListener implements Listener {
    private final MDVQuestPlugin plugin;
    private final ProgressService progress;
    private final PlacedBlockService placedBlocks;
    private final MMOItemsHook mmoItems;
    private final NamespacedKey playerDroppedKey;
    private final Set<BerryHarvestKey> pendingBerryHarvests = new HashSet<>();

    public GameplayListener(MDVQuestPlugin plugin, ProgressService progress, PlacedBlockService placedBlocks,
            MMOItemsHook mmoItems) {
        this.plugin = plugin;
        this.progress = progress;
        this.placedBlocks = placedBlocks;
        this.mmoItems = mmoItems;
        this.playerDroppedKey = new NamespacedKey(plugin, "player-dropped-item");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        progress.preload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        progress.unload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        placedBlocks.placed(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        boolean wasPlaced = placedBlocks.consumeIfPlaced(event.getBlock());
        Player player = event.getPlayer();

        Map<String, String> data = new HashMap<>();
        data.put("natural", Boolean.toString(!wasPlaced));
        progress.report(player, ObjectiveType.MINE_BLOCK, material.name(), 1L, data);

        if (Tag.LOGS.isTagged(material)) {
            progress.report(player, ObjectiveType.CUT_LOG, material.name(), 1L, data);
        }

        if (isCrop(material)) {
            data.put("mature", Boolean.toString(isMatureCrop(event)));
            progress.report(player, ObjectiveType.HARVEST_CROP, material.name(), 1L, data);
        }
    }

    private boolean isCrop(Material material) {
        return material == Material.NETHER_WART || material == Material.WHEAT || material == Material.CARROTS
                || material == Material.POTATOES || material == Material.BEETROOTS;
    }

    private boolean isMatureCrop(BlockBreakEvent event) {
        if (!(event.getBlock().getBlockData() instanceof Ageable ageable))
            return false;
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    /**
     * Las bayas dulces se cosechan sin romper el arbusto. Solo se informa progreso
     * cuando el jugador hace clic derecho sobre un SWEET_BERRY_BUSH completamente
     * maduro y, al terminar la interacción vanilla, la edad del bloque disminuyó.
     * Romper el arbusto nunca pasa por este flujo y no cuenta como cosecha.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSweetBerryHarvest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SWEET_BERRY_BUSH)
            return;
        if (!(block.getBlockData() instanceof Ageable before))
            return;
        if (before.getAge() < before.getMaximumAge())
            return;

        Player player = event.getPlayer();
        int beforeAge = before.getAge();
        boolean natural = !placedBlocks.isPlaced(block);
        BerryHarvestKey key = new BerryHarvestKey(player.getUniqueId(), BlockPosition.of(block));
        if (!pendingBerryHarvests.add(key))
            return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!player.isOnline())
                    return;
                if (block.getType() != Material.SWEET_BERRY_BUSH)
                    return;
                if (!(block.getBlockData() instanceof Ageable after))
                    return;
                if (after.getAge() >= beforeAge)
                    return;

                progress.report(player, ObjectiveType.HARVEST_CROP, Material.SWEET_BERRY_BUSH.name(), 1L,
                        Map.of(
                                "natural", Boolean.toString(natural),
                                "mature", "true",
                                "harvest-method", "RIGHT_CLICK"));
            } finally {
                pendingBerryHarvests.remove(key);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(placedBlocks::remove);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(placedBlocks::remove);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null)
            return;
        if (dead instanceof Player victim) {
            if (!plugin.getConfig().getBoolean("anti-exploit.pvp.require-direct-killer", true)
                    || isDirectPlayerKill(killer, victim)) {
                progress.reportPlayerKill(killer, victim);
            }
            return;
        }

        Optional<String> mythicId = plugin.getMythicMobsHook().mythicId(dead);
        if (mythicId.isPresent()) {
            progress.reportMythicKill(killer, dead.getUniqueId(), mythicId.get());
        } else {
            progress.report(killer, ObjectiveType.KILL_VANILLA_MOB, dead.getType().name(), 1L);
            if (dead instanceof Monster) {
                progress.report(killer, ObjectiveType.KILL_ANY_HOSTILE_MOB, dead.getType().name(), 1L);
            }
        }
    }

    private boolean isDirectPlayerKill(Player killer, Player victim) {
        if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent damage))
            return false;
        Entity damager = damage.getDamager();
        if (damager instanceof Player player)
            return player.getUniqueId().equals(killer.getUniqueId());
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player shooter) {
                return shooter.getUniqueId().equals(killer.getUniqueId());
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Optional<MMOItemsHook.Identity> identity = mmoItems.identify(item);
        if (identity.isPresent()) {
            progress.report(event.getPlayer(), ObjectiveType.USE_CONSUMABLE, identity.get().combined(), 1L,
                    Map.of("mmo-type", identity.get().type(), "mmo-id", identity.get().id(), "source",
                            "CONSUME_EVENT"));
        } else {
            progress.report(event.getPlayer(), ObjectiveType.USE_CONSUMABLE, item.getType().name(), 1L);
        }
    }

    /**
     * Cubre consumibles MMOItems instantaneos que se usan con click derecho y no
     * disparan
     * PlayerItemConsumeEvent. Solo progresa si el stack realmente disminuyo o
     * desaparecio.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMmoConsumableInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        EquipmentSlot hand = event.getHand();
        if (hand == null)
            return;
        ItemStack before = event.getItem();
        Optional<MMOItemsHook.Identity> identity = mmoItems.identify(before);
        if (identity.isEmpty() || !identity.get().type().equals("CONSUMABLE"))
            return;
        int beforeAmount = before == null ? 0 : before.getAmount();
        MMOItemsHook.Identity expected = identity.get();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!event.getPlayer().isOnline())
                return;
            ItemStack after = hand == EquipmentSlot.OFF_HAND
                    ? event.getPlayer().getInventory().getItemInOffHand()
                    : event.getPlayer().getInventory().getItemInMainHand();
            Optional<MMOItemsHook.Identity> afterIdentity = mmoItems.identify(after);
            boolean sameItem = afterIdentity.isPresent() && afterIdentity.get().equals(expected);
            int afterAmount = after == null || after.getType().isAir() ? 0 : after.getAmount();
            if (sameItem && afterAmount >= beforeAmount)
                return;
            progress.report(event.getPlayer(), ObjectiveType.USE_CONSUMABLE, expected.combined(), 1L,
                    Map.of("mmo-type", expected.type(), "mmo-id", expected.id(), "source", "MMOITEMS_INTERACT"));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        event.getItemDrop().getPersistentDataContainer().set(playerDroppedKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (event.getItem().getPersistentDataContainer().has(playerDroppedKey, PersistentDataType.BYTE))
            return;
        ItemStack item = event.getItem().getItemStack();
        Optional<MMOItemsHook.Identity> identity = mmoItems.identify(item);
        if (identity.isEmpty())
            return;
        int pickedUp = Math.max(0, item.getAmount() - event.getRemaining());
        if (pickedUp <= 0)
            return;
        progress.report(player, ObjectiveType.OBTAIN_MMOITEM, identity.get().combined(), pickedUp,
                Map.of("mmo-type", identity.get().type(), "mmo-id", identity.get().id(), "source", "PICKUP"));
    }

    private record BerryHarvestKey(UUID playerId, BlockPosition position) {
    }
}
