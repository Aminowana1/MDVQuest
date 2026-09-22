package com.mdvcraft.mdvquest.service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.mdvcraft.mdvquest.MDVQuestPlugin;
import com.mdvcraft.mdvquest.model.ObjectiveDefinition;
import com.mdvcraft.mdvquest.util.ColorUtil;

public class ActionBarManager {

  final MDVQuestPlugin plugin;

  private final Map<UUID, BukkitTask> actionbarTasks = new HashMap<>();

  public ActionBarManager(MDVQuestPlugin plugin) {
    this.plugin = plugin;
  }

  public void sendProgressActionbar(Player player, ObjectiveDefinition objective, long progress) {
    int durationSeconds = 2;
    int durationTicks = 20 * durationSeconds;

    if (!plugin.getConfig().getBoolean("performance.progress-actionbar", true))
      return;

    UUID uuid = player.getUniqueId();

    // Cancel any existing ActionBar task for this player.
    BukkitTask previousTask = actionbarTasks.remove(uuid);
    if (previousTask != null)
      previousTask.cancel();

    String text = ColorUtil.color(
        "&e" + objective.displayName()
            + " &f" + progress
            + "&7/&f" + objective.amount());

    BukkitTask task = Bukkit.getScheduler().runTaskTimer(
        plugin,
        new Runnable() {
          private int ticks = 0;

          @Override
          public void run() {
            if (!player.isOnline() || ticks >= durationTicks) {
              BukkitTask currentTask = actionbarTasks.remove(uuid);
              if (currentTask != null)
                currentTask.cancel();
              return;
            }

            sendActionBar(player, text);
            ticks++;
          }
        },
        0L,
        1L);

    actionbarTasks.put(uuid, task);
  }

  private void sendActionBar(Player player, String text) {
    try {
      Class<?> serializerClass = Class.forName(
          "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");

      Object serializer = serializerClass
          .getMethod("legacySection")
          .invoke(null);

      Object component = serializerClass
          .getMethod("deserialize", String.class)
          .invoke(serializer, text);

      Class<?> componentClass = Class.forName(
          "net.kyori.adventure.text.Component");

      player.getClass()
          .getMethod("sendActionBar", componentClass)
          .invoke(player, component);

      return;
    } catch (Throwable ignored) {
    }

    try {
      Method method = player.getClass()
          .getMethod("sendActionBar", String.class);

      method.invoke(player, text);
    } catch (Throwable ignored) {
    }
  }

  public void unloadPlayer(Player player) {
    UUID uuid = player.getUniqueId();

    BukkitTask task = actionbarTasks.remove(uuid);
    if (task != null)
      task.cancel();
  }
}