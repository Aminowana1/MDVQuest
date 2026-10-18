package com.mdvcraft.mdvquest;

import com.mdvcraft.mdvquest.command.MDVQuestCommand;
import com.mdvcraft.mdvquest.gui.QuestMenuManager;
import com.mdvcraft.mdvquest.gui.ChatPromptManager;
import com.mdvcraft.mdvquest.gui.QuestEditorManager;
import com.mdvcraft.mdvquest.hook.MDVSocialHook;
import com.mdvcraft.mdvquest.hook.MMOItemsHook;
import com.mdvcraft.mdvquest.hook.MythicMobsHook;
import com.mdvcraft.mdvquest.hook.MythicItemsHook;
import com.mdvcraft.mdvquest.hook.PlaceholderHook;
import com.mdvcraft.mdvquest.listener.GameplayListener;
import com.mdvcraft.mdvquest.listener.CraftingProgressListener;
import com.mdvcraft.mdvquest.service.AccessService;
import com.mdvcraft.mdvquest.service.ActionBarManager;
import com.mdvcraft.mdvquest.service.DeliveryService;
import com.mdvcraft.mdvquest.service.ExampleRewardSanitizer;
import com.mdvcraft.mdvquest.service.IntegrationService;
import com.mdvcraft.mdvquest.service.MDVSocialMenuInstaller;
import com.mdvcraft.mdvquest.service.PlacedBlockService;
import com.mdvcraft.mdvquest.service.ProgressService;
import com.mdvcraft.mdvquest.service.QuestRegistry;
import com.mdvcraft.mdvquest.service.QuestYamlService;
import com.mdvcraft.mdvquest.service.RewardService;
import com.mdvcraft.mdvquest.service.RotationService;
import com.mdvcraft.mdvquest.storage.QuestDatabase;
import com.mdvcraft.mdvquest.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MDVQuestPlugin extends JavaPlugin {
    private QuestDatabase database;
    private QuestRegistry registry;
    private RotationService rotationService;
    private ProgressService progressService;
    private PlacedBlockService placedBlockService;
    private AccessService accessService;
    private RewardService rewardService;
    private DeliveryService deliveryService;
    private QuestMenuManager menuManager;
    private ChatPromptManager promptManager;
    private QuestEditorManager editorManager;
    private QuestYamlService yamlService;
    private IntegrationService integrationService;
    private final MDVSocialHook socialHook = new MDVSocialHook();
    private final MMOItemsHook mmoItemsHook = new MMOItemsHook();
    private final MythicMobsHook mythicMobsHook = new MythicMobsHook();
    private final MythicItemsHook mythicItemsHook = new MythicItemsHook();
    private final PlaceholderHook placeholderHook = new PlaceholderHook();
    private ExecutorService databaseExecutor;
    private BukkitTask flushTask;
    private BukkitTask rotationTask;
    public ActionBarManager actionBarManager = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateLegacyMenuConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        saveResourceIfMissing("families.yml");
        saveResourceIfMissing("missions/examples.yml");
        new ExampleRewardSanitizer(this).run();
        actionBarManager = new ActionBarManager(this);

        databaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MDVQuest-Database");
            thread.setDaemon(true);
            return thread;
        });

        try {
            database = new QuestDatabase(this);
            database.open();
            registry = new QuestRegistry(this);
            int definitions = registry.reload();
            rotationService = new RotationService(this, registry, database);
            rotationService.initialize();
            progressService = new ProgressService(this, registry, rotationService, database);
            placedBlockService = new PlacedBlockService(this, registry, database);
            placedBlockService.initialize();
            accessService = new AccessService(this);
            rewardService = new RewardService(this, progressService, database, mmoItemsHook, mythicItemsHook,
                    accessService);
            deliveryService = new DeliveryService(this, progressService, mmoItemsHook);
            menuManager = new QuestMenuManager(this, rotationService, progressService, rewardService, deliveryService,
                    accessService);
            promptManager = new ChatPromptManager(this);
            yamlService = new QuestYamlService(this);
            editorManager = new QuestEditorManager(this, promptManager, yamlService, rewardService, mmoItemsHook,
                    mythicItemsHook);
            integrationService = new IntegrationService(this, progressService, mmoItemsHook);

            Bukkit.getPluginManager().registerEvents(
                    new GameplayListener(this, progressService, placedBlockService, mmoItemsHook), this);
            Bukkit.getPluginManager().registerEvents(new CraftingProgressListener(this, progressService), this);
            Bukkit.getPluginManager().registerEvents(menuManager, this);
            Bukkit.getPluginManager().registerEvents(promptManager, this);
            Bukkit.getPluginManager().registerEvents(editorManager, this);
            integrationService.register();

            MDVQuestCommand executor = new MDVQuestCommand(this);
            PluginCommand command = getCommand("mdvquest");
            if (command != null) {
                command.setExecutor(executor);
                command.setTabCompleter(executor);
            }

            for (Player player : Bukkit.getOnlinePlayers())
                progressService.preload(player);
            scheduleTasks();
            new MDVSocialMenuInstaller(this).installIfNeeded();
            getLogger().info("MDVQuest " + getDescription().getVersion() + " habilitado. Definiciones: " + definitions
                    + ", activas: " + rotationService.activeInstances().size());
        } catch (Exception ex) {
            getLogger().severe("No se pudo iniciar MDVQuest: " + ex.getMessage());
            ex.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (menuManager != null)
            menuManager.shutdown();
        if (flushTask != null)
            flushTask.cancel();
        if (rotationTask != null)
            rotationTask.cancel();
        if (progressService != null)
            progressService.flushAll();
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
            try {
                databaseExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (database != null)
            database.close();
    }

    private void scheduleTasks() {
        long flushTicks = Math.max(5L, getConfig().getLong("database.flush-interval-seconds", 30L)) * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimer(this, progressService::flushOnline, flushTicks, flushTicks);

        long cleanupTicks = Math.max(10L, getConfig().getLong("database.cleanup-interval-seconds", 60L)) * 20L;
        rotationTask = Bukkit.getScheduler().runTaskTimer(this, this::synchronizeRotations, 20L, cleanupTicks);
    }

    /**
     * Sincroniza vencimientos, nuevo roll e índice/caché en una sola operación.
     * También se invoca antes de abrir el menú para que el cambio de día sea
     * visible
     * inmediatamente, sin esperar al siguiente intervalo de mantenimiento.
     */
    public boolean synchronizeRotations() {
        if (database == null || rotationService == null || progressService == null)
            return false;
        long now = System.currentTimeMillis();
        try {
            // La limpieza va antes del refresh: primero desaparecen aceptaciones,
            // progreso, claims y víctimas del ciclo vencido; después se genera el
            // nuevo roll. Así el cupo se libera únicamente por cambio de ciclo.
            int cleaned = database.cleanupExpired(now);
            boolean changed = rotationService.refresh(now);
            if (changed || cleaned > 0)
                progressService.rebuildIndex();
            if (cleaned > 0)
                database.incrementalVacuum();
            return changed || cleaned > 0;
        } catch (SQLException ex) {
            getLogger().severe("Error actualizando rotaciones: " + ex.getMessage());
            return false;
        }
    }

    public void reloadPlugin() {
        progressService.flushAll();
        reloadConfig();
        migrateLegacyMenuConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        new ExampleRewardSanitizer(this).run();
        registry.reload();
        actionBarManager = new ActionBarManager(this);
        if (menuManager != null)
            menuManager.reloadBedrockMenus();

        try {
            rotationService.initialize();
            progressService.rebuildIndex();
            if (flushTask != null)
                flushTask.cancel();
            if (rotationTask != null)
                rotationTask.cancel();
            scheduleTasks();
            new MDVSocialMenuInstaller(this).installIfNeeded();
        } catch (SQLException ex) {
            getLogger().severe("No se pudieron recargar las rotaciones: " + ex.getMessage());
        }
    }

    public boolean forceRotate(String rotationId) {
        try {
            progressService.flushAll();
            Set<String> invalidatedIds = rotationService.instanceIdsForRotation(rotationId);
            boolean result = rotationService.forceRotate(rotationId, System.currentTimeMillis());
            if (result) {
                int affectedPlayers = progressService.invalidateInstances(invalidatedIds);
                progressService.rebuildIndex();
                database.cleanupExpired(System.currentTimeMillis());
                if (affectedPlayers > 0) {
                    getLogger().info("Reroll " + rotationId + ": caché antigua invalidada para "
                            + affectedPlayers + " jugador(es).");
                }
            }
            return result;
        } catch (SQLException ex) {
            getLogger().severe("No se pudo forzar la rotacion: " + ex.getMessage());
            return false;
        }
    }

    public RotationService.ForceResult forceMission(String missionId) {
        try {
            RotationService.ForceResult result = rotationService.forceMission(missionId, System.currentTimeMillis());
            if (result.status() == RotationService.ForceStatus.ADDED) {
                progressService.rebuildIndex();
            }
            return result;
        } catch (SQLException ex) {
            getLogger().severe("No se pudo forzar la mision: " + ex.getMessage());
            return new RotationService.ForceResult(RotationService.ForceStatus.DATABASE_ERROR, null);
        }
    }

    public int forceRotateAll() {
        int rotated = 0;
        progressService.flushAll();
        Set<String> invalidatedIds = rotationService.knownInstanceIds();
        long now = System.currentTimeMillis();
        try {
            for (var rotation : registry.rotations()) {
                if (!rotation.enabled())
                    continue;
                if (rotationService.forceRotate(rotation.id(), now + rotated))
                    rotated++;
            }
        } catch (SQLException ex) {
            getLogger().severe("No se pudieron forzar todas las rotaciones: " + ex.getMessage());
        }

        // Incluso si una rotación posterior falla, las que ya fueron regeneradas no
        // deben conservar en memoria aceptaciones del roll anterior.
        if (rotated > 0) {
            int affectedPlayers = progressService.invalidateInstances(invalidatedIds);
            progressService.rebuildIndex();
            try {
                database.cleanupExpired(System.currentTimeMillis());
            } catch (SQLException ex) {
                getLogger().warning("No se pudo completar la limpieza posterior al reroll global: " + ex.getMessage());
            }
            if (affectedPlayers > 0) {
                getLogger().info("Reroll global: caché antigua invalidada para "
                        + affectedPlayers + " jugador(es).");
            }
        }
        return rotated;
    }

    public void runDatabaseAsync(Runnable task) {
        if (databaseExecutor == null || databaseExecutor.isShutdown())
            return;
        databaseExecutor.execute(task);
    }

    public String prefix() {
        return ColorUtil.color(getConfig().getString("messages.prefix", "&6[MDVQuest] &f"));
    }

    public void message(CommandSender sender, String key, Map<String, String> replacements) {
        String value = getConfig().getString("messages." + key, key);
        if (value == null || value.isBlank())
            return;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            value = value.replace("%" + replacement.getKey() + "%", replacement.getValue());
        }
        sender.sendMessage(prefix() + ColorUtil.color(value));
    }

    private void saveResourceIfMissing(String path) {
        File file = new File(getDataFolder(), path);
        if (file.exists())
            return;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();
        saveResource(path, false);
    }

    /**
     * 1.3.0 separa el menú público en VIEW_ONLY e INTERACTIVE. Para no perder
     * ninguna personalización previa, la primera carga copia las antiguas
     * secciones menus.main/detail/page-buttons a las dos nuevas variantes.
     */
    private void migrateLegacyMenuConfig() {
        boolean changed = false;
        boolean viewerMigrated = copySectionIfMissing("menus.main", "menus.viewer.main");
        changed |= viewerMigrated;
        if (viewerMigrated) {
            // La vista pública mantiene el diseño anterior, pero elimina cualquier
            // instrucción de click y redirige entregas/reclamaciones al NPC.
            getConfig().set("menus.viewer.main.mission-lore.details", "");
            getConfig().set("menus.viewer.main.mission-lore.completed",
                    "&aMisión completada. &7Visita al encargado para reclamarla.");
            getConfig().set("menus.viewer.main.mission-lore.delivery-pending",
                    "&eEntrega los objetos con el encargado de misiones.");
        }
        changed |= copySectionIfMissing("menus.main", "menus.interactive.main");
        changed |= copySectionIfMissing("menus.page-buttons", "menus.viewer.page-buttons");
        changed |= copySectionIfMissing("menus.page-buttons", "menus.interactive.page-buttons");
        changed |= copySectionIfMissing("menus.detail", "menus.interactive.detail");
        changed |= copyValueIfMissing("menus.back-command", "menus.viewer.back-command");
        changed |= copyValueIfMissing("menus.back-command", "menus.interactive.back-command");
        changed |= migrateLegacyCompletionMessage();
        changed |= migrateContractMenuConfig();
        changed |= migrateQuest142Config();
        if (changed)
            saveConfig();
    }

    /**
     * Migra únicamente los textos predeterminados anteriores a 1.4.2. Las
     * personalizaciones del servidor se conservan intactas.
     */
    private boolean migrateQuest142Config() {
        boolean changed = false;
        String oldLocked = "&b● Necesitas el rango &f%rank% &bpara reclamar la recompensa de esta misión.";
        String newLocked = "&b● Necesitas el rango &f%rank% &bpara aceptar y reclamar esta misión.";
        for (String mode : java.util.List.of("viewer", "interactive")) {
            String path = "menus." + mode + ".main.access.locked-line";
            if (oldLocked.equals(getConfig().getString(path))) {
                getConfig().set(path, newLocked);
                changed = true;
            }
        }

        String rankPath = "messages.mission-rank-required";
        String oldRankMessage = "&cNecesitas el rango &f%rank% &cpara reclamar esta recompensa.";
        if (oldRankMessage.equals(getConfig().getString(rankPath))) {
            getConfig().set(rankPath,
                    "&cNecesitas el rango &f%rank% &cpara aceptar o reclamar esta misión.");
            changed = true;
        }

        String acceptedPath = "menus.interactive.main.mission-lore.accepted-controls";
        String oldAccepted = "&eClick izquierdo: ver detalles. &cShift + click derecho: cancelar.";
        if (oldAccepted.equals(getConfig().getString(acceptedPath))) {
            getConfig().set(acceptedPath, java.util.List.of(
                    "&eClick izquierdo: ver detalles.",
                    "&cShift + click derecho: cancelar."));
            changed = true;
        }

        String availablePath = "menus.interactive.main.mission-lore.available-controls";
        String oldAvailable = "&eClick izquierdo: ver detalles. &aClick derecho: aceptar contrato.";
        if (oldAvailable.equals(getConfig().getString(availablePath))) {
            getConfig().set(availablePath, java.util.List.of(
                    "&eClick izquierdo: ver detalles.",
                    "&aClick derecho: aceptar contrato."));
            changed = true;
        }
        return changed;
    }

    /**
     * Añade los placeholders visuales de 1.4.0 sin reemplazar diseños
     * personalizados.
     */
    private boolean migrateContractMenuConfig() {
        boolean changed = false;
        for (String mode : java.util.List.of("viewer", "interactive")) {
            String titlePath = "menus." + mode + ".main.title";
            String title = getConfig().getString(titlePath, "");
            if ("&8Misiones".equals(title)) {
                getConfig().set(titlePath, "&8Misiones &7(%accepted%/%limit%)");
                changed = true;
            }
            for (String category : java.util.List.of("one-day", "two-three-days", "four-six-days", "seven-days")) {
                String lorePath = "menus." + mode + ".main.categories." + category + ".lore";
                java.util.List<String> lore = new java.util.ArrayList<>(getConfig().getStringList(lorePath));
                boolean present = lore.stream()
                        .anyMatch(line -> line.contains("%accepted%") || line.contains("%limit%"));
                if (!present && !lore.isEmpty()) {
                    lore.add(0, "&7Contratos aceptados: &f%accepted%/%limit%");
                    getConfig().set(lorePath, lore);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean migrateLegacyCompletionMessage() {
        if (!getConfig().contains("messages.mission-completed", true))
            return false;
        String current = getConfig().getString("messages.mission-completed", "");
        String normalized = current == null ? "" : current.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.contains("abre &f/misiones") && !normalized.contains("abre /misiones"))
            return false;
        getConfig().set("messages.mission-completed",
                "&a&lMision completada: &f%mission% &7Visita al encargado de misiones para reclamarla antes de que expire.");
        return true;
    }

    private boolean copySectionIfMissing(String sourcePath, String targetPath) {
        if (getConfig().contains(targetPath, true))
            return false;
        ConfigurationSection source = getConfig().getConfigurationSection(sourcePath);
        if (source == null)
            return false;
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key))
                continue;
            getConfig().set(targetPath + "." + key, source.get(key));
        }
        return true;
    }

    private boolean copyValueIfMissing(String sourcePath, String targetPath) {
        if (getConfig().contains(targetPath, true) || !getConfig().contains(sourcePath, true))
            return false;
        getConfig().set(targetPath, getConfig().get(sourcePath));
        return true;
    }

    public QuestDatabase getDatabase() {
        return database;
    }

    public QuestRegistry getRegistry() {
        return registry;
    }

    public RotationService getRotationService() {
        return rotationService;
    }

    public ProgressService getProgressService() {
        return progressService;
    }

    public QuestMenuManager getMenuManager() {
        return menuManager;
    }

    public QuestEditorManager getEditorManager() {
        return editorManager;
    }

    public QuestYamlService getYamlService() {
        return yamlService;
    }

    public AccessService getAccessService() {
        return accessService;
    }

    public MDVSocialHook getSocialHook() {
        return socialHook;
    }

    public MMOItemsHook getMmoItemsHook() {
        return mmoItemsHook;
    }

    public MythicMobsHook getMythicMobsHook() {
        return mythicMobsHook;
    }

    public MythicItemsHook getMythicItemsHook() {
        return mythicItemsHook;
    }

    public PlaceholderHook getPlaceholderHook() {
        return placeholderHook;
    }
}
