package com.mdvcraft.mdvquest.listener;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import com.mdvcraft.mdvquest.service.ProgressService;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Counts completed crafts, rather than the number the ingredients could produce. */
public final class CraftingProgressListener implements Listener {
    private final MDVQuestPlugin plugin;
    private final ProgressService progress;
    private final Map<UUID, PendingCraft> pending = new HashMap<>();

    public CraftingProgressListener(MDVQuestPlugin plugin, ProgressService progress) {
        this.plugin = plugin;
        this.progress = progress;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (event.getAction() == InventoryAction.NOTHING)
            return;
        Recipe recipe = event.getRecipe();
        if (recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equalsIgnoreCase("mdvrecetas"))
            return;
        // The result slot also covers dynamic recipes and PrepareItemCraftEvent changes.
        ItemStack result = event.getCurrentItem();
        if (isEmpty(result))
            return;

        CraftingInventory inventory = event.getInventory();
        ItemStack[] before = inventory.getMatrix().clone();
        for (int slot = 0; slot < before.length; slot++) {
            if (before[slot] != null)
                before[slot] = before[slot].clone();
        }
        PendingCraft craft = new PendingCraft(event, player, inventory, before,
                result.getType(), result.getAmount());
        UUID playerId = player.getUniqueId();
        pending.put(playerId, craft);
        // CraftItemEvent runs before vanilla consumes ingredients. Check on the server thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pending.remove(playerId, craft))
                report(craft);
        });
    }

    // Settle the previous click before any subsequent action can move, refill or clear
    // its ingredients. These handlers must also run for cancelled interactions.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        flush(event.getWhoClicked().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        flush(event.getWhoClicked().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        flush(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        flush(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRecipeBookClick(PlayerRecipeBookClickEvent event) {
        flush(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        // GameplayListener unloads quest state at MONITOR.
        flush(event.getPlayer().getUniqueId());
    }

    private void flush(UUID playerId) {
        PendingCraft craft = pending.remove(playerId);
        if (craft != null)
            report(craft);
    }

    private void report(PendingCraft craft) {
        // A later listener may have cancelled the original event.
        if (craft.event().isCancelled())
            return;
        int operations = consumedOperations(craft.before(), craft.inventory().getMatrix());
        if (!craft.event().isShiftClick())
            operations = Math.min(operations, 1);
        if (operations <= 0)
            return;
        long produced = (long) craft.resultAmount() * operations;
        progress.report(craft.player(), ObjectiveType.CRAFT_VANILLA_ITEM, craft.resultType().name(), produced,
                Map.of("craft-operations", String.valueOf(operations)));
    }

    private static int consumedOperations(ItemStack[] before, ItemStack[] after) {
        if (before.length != after.length)
            return 0;
        int operations = Integer.MAX_VALUE;
        for (int slot = 0; slot < before.length; slot++) {
            ItemStack ingredient = before[slot];
            if (isEmpty(ingredient))
                continue;
            ItemStack remaining = after[slot];
            // Replaced ingredients include recipe remainders such as empty buckets.
            int remainingAmount = !isEmpty(remaining) && ingredient.isSimilar(remaining)
                    ? remaining.getAmount() : 0;
            int consumed = Math.max(0, ingredient.getAmount() - remainingAmount);
            // Book/banner copying returns the original unchanged. Only consumed
            // ingredients constrain the number of completed operations.
            if (consumed > 0)
                operations = Math.min(operations, consumed);
        }
        return operations == Integer.MAX_VALUE ? 0 : operations;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private record PendingCraft(CraftItemEvent event, Player player, CraftingInventory inventory,
            ItemStack[] before, Material resultType, int resultAmount) {
    }
}
