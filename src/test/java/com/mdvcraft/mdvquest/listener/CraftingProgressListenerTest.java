package com.mdvcraft.mdvquest.listener;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.ObjectiveType;
import com.mdvcraft.mdvquest.service.ProgressService;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class CraftingProgressListenerTest {
    private MDVQuestPlugin plugin;
    private ProgressService progress;
    private Player player;
    private CraftingInventory inventory;
    private CraftingProgressListener listener;
    private MockedStatic<Bukkit> bukkit;
    private final List<Runnable> scheduled = new ArrayList<>();
    private final Map<Material, Material> materials = new EnumMap<>(Material.class);
    private ItemStack[] matrix;

    @BeforeEach
    void setUp() {
        plugin = mock(MDVQuestPlugin.class);
        progress = mock(ProgressService.class);
        player = mock(Player.class);
        inventory = mock(CraftingInventory.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(inventory.getMatrix()).thenAnswer(ignored -> matrix.clone());
        matrix = pickaxeMatrix(64);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            scheduled.add(invocation.getArgument(1, Runnable.class));
            return mock(BukkitTask.class);
        });
        listener = new CraftingProgressListener(plugin, progress);
    }

    @AfterEach
    void closeBukkitMock() {
        if (bukkit != null)
            bukkit.close();
    }

    @Test
    void waitsForSuccessfulIngredientConsumptionBeforeReporting() {
        click(craft(Material.IRON_PICKAXE, 1));

        verifyNoInteractions(progress);
        matrix = pickaxeMatrix(63);
        tick();

        assertReport(Material.IRON_PICKAXE, 1, 1);
    }

    @Test
    void snapshotsIngredientAmountsBeforeOriginalStacksAreMutated() {
        AtomicInteger amount = new AtomicInteger(64);
        ItemStack ingredient = mock(ItemStack.class);
        // Resolve this mock before starting another Mockito stubbing.
        Material ingredientMaterial = serverlessMaterial(Material.OAK_LOG);
        when(ingredient.getType()).thenReturn(ingredientMaterial);
        when(ingredient.getAmount()).thenAnswer(ignored -> amount.get());
        when(ingredient.clone()).thenAnswer(ignored -> stack(Material.OAK_LOG, amount.get()));
        matrix = new ItemStack[] {ingredient, null, null, null};
        click(craft(Material.OAK_PLANKS, 4));

        amount.set(62);
        tick();

        assertReport(Material.OAK_PLANKS, 8, 2);
    }

    @Test
    void fullInventoryNeverGrantsProgressEvenAfterRepeatedShiftClicks() {
        for (int attempt = 0; attempt < 3; attempt++)
            click(craft(Material.IRON_PICKAXE, 1));

        tick();

        verifyNoInteractions(progress);
    }

    @Test
    void countsOnlyTwoPickaxesWhenInventoryHasSpaceForTwo() {
        click(craft(Material.IRON_PICKAXE, 1));
        matrix = pickaxeMatrix(62);

        tick();

        assertReport(Material.IRON_PICKAXE, 2, 2);
    }

    @Test
    void countsAll64PickaxesWhenAllFiveIngredientStacksAreConsumed() {
        click(craft(Material.IRON_PICKAXE, 1));
        matrix = new ItemStack[9];

        tick();

        assertReport(Material.IRON_PICKAXE, 64, 64);
    }

    @Test
    void usesLeastConsumedIngredientToAvoidCountingUnrelatedSlotChanges() {
        click(craft(Material.IRON_PICKAXE, 1));
        matrix = pickaxeMatrix(62);
        matrix[0] = stack(Material.IRON_INGOT, 40);

        tick();

        assertReport(Material.IRON_PICKAXE, 2, 2);
    }

    @Test
    void multipliesSuccessfulOperationsByActualResultStackAmount() {
        matrix = new ItemStack[] {stack(Material.OAK_LOG, 16), null, null, null};
        click(craft(Material.OAK_PLANKS, 4));
        matrix = new ItemStack[] {stack(Material.OAK_LOG, 13), null, null, null};

        tick();

        assertReport(Material.OAK_PLANKS, 12, 3);
    }

    @Test
    void unchangedOriginalBookDoesNotLimitConsumedBlankBooks() {
        ItemStack original = stack(Material.WRITTEN_BOOK, 1);
        matrix = new ItemStack[] {original, stack(Material.WRITABLE_BOOK, 64), null, null};
        click(craft(Material.WRITTEN_BOOK, 1));
        matrix = new ItemStack[] {original, stack(Material.WRITABLE_BOOK, 62), null, null};

        tick();

        assertReport(Material.WRITTEN_BOOK, 2, 2);
    }

    @Test
    void unchangedOriginalBannerDoesNotLimitConsumedBlankBanners() {
        ItemStack original = stack(Material.WHITE_BANNER, 1);
        matrix = new ItemStack[] {original, stack(Material.WHITE_BANNER, 16), null, null};
        click(craft(Material.WHITE_BANNER, 1));
        matrix = new ItemStack[] {original, stack(Material.WHITE_BANNER, 14), null, null};

        tick();

        assertReport(Material.WHITE_BANNER, 2, 2);
    }

    @Test
    void emptyBucketRemaindersStillCountAsConsumedMilkIngredients() {
        matrix = new ItemStack[] {
                stack(Material.MILK_BUCKET, 1), stack(Material.MILK_BUCKET, 1), stack(Material.MILK_BUCKET, 1),
                stack(Material.SUGAR, 64), stack(Material.EGG, 16), stack(Material.SUGAR, 64),
                stack(Material.WHEAT, 64), stack(Material.WHEAT, 64), stack(Material.WHEAT, 64)
        };
        click(craft(Material.CAKE, 1));
        matrix = new ItemStack[] {
                stack(Material.BUCKET, 1), stack(Material.BUCKET, 1), stack(Material.BUCKET, 1),
                stack(Material.SUGAR, 63), stack(Material.EGG, 15), stack(Material.SUGAR, 63),
                stack(Material.WHEAT, 63), stack(Material.WHEAT, 63), stack(Material.WHEAT, 63)
        };

        tick();

        assertReport(Material.CAKE, 1, 1);
    }

    @Test
    void regularClickCanCountOnlyOneOperation() {
        CraftItemEvent event = craft(Material.IRON_PICKAXE, 1);
        when(event.isShiftClick()).thenReturn(false);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        click(event);
        matrix = pickaxeMatrix(62);

        tick();

        assertReport(Material.IRON_PICKAXE, 1, 1);
    }

    @Test
    void usesCurrentOutputInsteadOfRecipePreview() {
        CraftItemEvent event = craft(Material.IRON_PICKAXE, 1);
        ItemStack actualOutput = stack(Material.DIAMOND_PICKAXE, 2);
        when(event.getCurrentItem()).thenReturn(actualOutput);
        click(event);
        matrix = pickaxeMatrix(63);

        tick();

        assertReport(Material.DIAMOND_PICKAXE, 2, 1);
    }

    @Test
    void noOpCursorClickCannotGrantProgress() {
        CraftItemEvent event = craft(Material.IRON_PICKAXE, 1);
        when(event.getAction()).thenReturn(InventoryAction.NOTHING);
        when(event.isShiftClick()).thenReturn(false);
        click(event);
        matrix = new ItemStack[9];

        tick();

        verifyNoInteractions(progress);
    }

    @Test
    void cancellationAfterMonitorHandlerPreventsProgress() {
        CraftItemEvent event = craft(Material.IRON_PICKAXE, 1);
        click(event);
        when(event.isCancelled()).thenReturn(true);
        matrix = pickaxeMatrix(63);

        tick();

        verifyNoInteractions(progress);
    }

    @Test
    void alreadyCancelledCraftIsIgnored() {
        CraftItemEvent event = craft(Material.IRON_PICKAXE, 1);
        when(event.isCancelled()).thenReturn(true);
        click(event);
        matrix = new ItemStack[9];

        tick();

        verifyNoInteractions(progress);
    }

    @Test
    void customMdvRecetasRecipesRemainExcluded() {
        CraftItemEvent event = craft(Material.IRON_PICKAXE, 1);
        when(((Keyed) event.getRecipe()).getKey()).thenReturn(new NamespacedKey("mdvrecetas", "pickaxe"));
        click(event);
        matrix = new ItemStack[9];

        tick();

        verifyNoInteractions(progress);
    }

    @Test
    void missingCurrentOutputIsIgnored() {
        CraftItemEvent event = craft(Material.IRON_PICKAXE, 1);
        when(event.getCurrentItem()).thenReturn(null);
        click(event);
        matrix = new ItemStack[9];

        tick();

        verifyNoInteractions(progress);
    }

    @Test
    void airCurrentOutputIsIgnored() {
        CraftItemEvent event = craft(Material.IRON_PICKAXE, 1);
        ItemStack air = stack(Material.AIR, 1);
        when(event.getCurrentItem()).thenReturn(air);
        click(event);
        matrix = new ItemStack[9];

        tick();

        verifyNoInteractions(progress);
    }

    @Test
    void consecutiveCraftsInOneTickAreReportedIndividuallyAndOnlyOnce() {
        click(craft(Material.IRON_PICKAXE, 1));
        matrix = pickaxeMatrix(63);
        click(craft(Material.IRON_PICKAXE, 1));

        assertReport(Material.IRON_PICKAXE, 1, 1);
        clearInvocations(progress);
        matrix = pickaxeMatrix(61);
        tick();

        assertReport(Material.IRON_PICKAXE, 2, 2);
    }

    @Test
    void oldScheduledCallbackCannotConsumeLaterCraftTransaction() {
        click(craft(Material.IRON_PICKAXE, 1));
        matrix = pickaxeMatrix(63);
        click(craft(Material.IRON_PICKAXE, 1));
        assertReport(Material.IRON_PICKAXE, 1, 1);
        clearInvocations(progress);

        assertEquals(2, scheduled.size());
        scheduled.get(0).run();
        verifyNoInteractions(progress);
        matrix = pickaxeMatrix(62);
        scheduled.get(1).run();

        assertReport(Material.IRON_PICKAXE, 1, 1);
    }

    @Test
    void manualIngredientRemovalAfterFailedCraftCannotCreateProgress() {
        click(craft(Material.IRON_PICKAXE, 1));
        InventoryClickEvent removal = mock(InventoryClickEvent.class);
        when(removal.getWhoClicked()).thenReturn(player);
        when(removal.getInventory()).thenReturn(inventory);

        listener.onInventoryClick(removal);
        matrix = new ItemStack[9];
        tick();

        verifyNoInteractions(progress);
    }

    @Test
    void dragAfterFailedCraftCannotCreateProgress() {
        click(craft(Material.IRON_PICKAXE, 1));
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(player);
        when(drag.getInventory()).thenReturn(inventory);

        listener.onInventoryDrag(drag);
        matrix = new ItemStack[9];
        tick();

        verifyNoInteractions(progress);
    }

    @Test
    void closeFlushesBeforeServerReturnsIngredientsToPlayer() {
        click(craft(Material.IRON_PICKAXE, 1));
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        when(close.getInventory()).thenReturn(inventory);

        listener.onInventoryClose(close);
        matrix = new ItemStack[9];
        tick();

        verifyNoInteractions(progress);
    }

    @Test
    void successfulCraftIsPreservedWhenInventoryClosesBeforeNextTick() {
        click(craft(Material.IRON_PICKAXE, 1));
        matrix = pickaxeMatrix(62);
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(player);
        when(close.getInventory()).thenReturn(inventory);

        listener.onInventoryClose(close);
        matrix = new ItemStack[9];
        tick();

        assertReport(Material.IRON_PICKAXE, 2, 2);
    }

    @Test
    void recipeBookRefillCannotHideSuccessfulCraftConsumption() {
        click(craft(Material.IRON_PICKAXE, 1));
        matrix = pickaxeMatrix(62);
        PlayerRecipeBookClickEvent book = mock(PlayerRecipeBookClickEvent.class);
        when(book.getPlayer()).thenReturn(player);

        listener.onRecipeBookClick(book);
        matrix = pickaxeMatrix(64);
        tick();

        assertReport(Material.IRON_PICKAXE, 2, 2);
    }

    @Test
    void quitFlushesSuccessfulCraftBeforePlayerProgressIsUnloaded() {
        click(craft(Material.IRON_PICKAXE, 1));
        matrix = pickaxeMatrix(63);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);

        listener.onQuit(quit);
        matrix = new ItemStack[9];
        tick();

        assertReport(Material.IRON_PICKAXE, 1, 1);
    }

    private void click(CraftItemEvent event) {
        // Bukkit dispatches the superclass listener at LOWEST before the craft MONITOR.
        listener.onInventoryClick(event);
        listener.onCraft(event);
    }

    private CraftItemEvent craft(Material material, int resultAmount) {
        Recipe recipe = mock(Recipe.class, withSettings().extraInterfaces(Keyed.class));
        ItemStack recipeResult = stack(material, resultAmount);
        ItemStack currentOutput = stack(material, resultAmount);
        when(((Keyed) recipe).getKey()).thenReturn(new NamespacedKey("minecraft", material.name().toLowerCase(java.util.Locale.ROOT)));
        when(recipe.getResult()).thenReturn(recipeResult);
        CraftItemEvent event = mock(CraftItemEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getRecipe()).thenReturn(recipe);
        when(event.getCurrentItem()).thenReturn(currentOutput);
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.isShiftClick()).thenReturn(true);
        return event;
    }

    private ItemStack[] pickaxeMatrix(int amount) {
        return new ItemStack[] {
                stack(Material.IRON_INGOT, amount), stack(Material.IRON_INGOT, amount), stack(Material.IRON_INGOT, amount),
                null, stack(Material.STICK, amount), null,
                null, stack(Material.STICK, amount), null
        };
    }

    private ItemStack stack(Material material, int amount) {
        ItemStack item = mock(ItemStack.class);
        Material safeMaterial = serverlessMaterial(material);
        when(item.getType()).thenReturn(safeMaterial);
        when(item.getAmount()).thenReturn(amount);
        when(item.clone()).thenReturn(item);
        when(item.isSimilar(any(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack other = invocation.getArgument(0);
            return other != null && other.getType() == safeMaterial;
        });
        return item;
    }

    private Material serverlessMaterial(Material material) {
        // Paper's real isAir() consults live server registries. Keep only that
        // platform boundary mocked while retaining the material's actual name.
        return materials.computeIfAbsent(material, real -> {
            Material copy = spy(real);
            doReturn(real == Material.AIR || real == Material.CAVE_AIR || real == Material.VOID_AIR)
                    .when(copy).isAir();
            return copy;
        });
    }

    private void tick() {
        List<Runnable> pending = List.copyOf(scheduled);
        scheduled.clear();
        pending.forEach(Runnable::run);
    }

    private void assertReport(Material material, long produced, int operations) {
        verify(progress).report(player, ObjectiveType.CRAFT_VANILLA_ITEM, material.name(), produced,
                Map.of("craft-operations", String.valueOf(operations)));
        verifyNoMoreInteractions(progress);
    }
}
