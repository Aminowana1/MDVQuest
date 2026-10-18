#!/usr/bin/env bash
set -euo pipefail

JAR="${1:-$(find target -maxdepth 1 -type f -name 'MDVQuest-*.jar' ! -name '*sources*' | sort | head -n 1)}"
if [[ ! -f "$JAR" ]]; then
  echo "No existe el JAR: $JAR" >&2
  exit 1
fi

# El plugin nunca debe empaquetar copias/stubs de Bukkit o Paper.
if jar tf "$JAR" | grep -Eq '^(org/bukkit|io/papermc/paper)/'; then
  echo "El JAR contiene clases Bukkit/Paper empaquetadas. Revisa las dependencias y el shade." >&2
  exit 1
fi

DUMP="$(mktemp)"
trap 'rm -f "$DUMP"' EXIT

javap -classpath "$JAR" -verbose \
  com.mdvcraft.mdvquest.gui.QuestMenuManager \
  com.mdvcraft.mdvquest.gui.QuestEditorManager \
  com.mdvcraft.mdvquest.hook.MDVSocialHook \
  com.mdvcraft.mdvquest.listener.GameplayListener \
  com.mdvcraft.mdvquest.listener.CraftingProgressListener \
  com.mdvcraft.mdvquest.service.RewardService \
  com.mdvcraft.mdvquest.util.ItemUtil > "$DUMP"

# Firmas que solo aparecen al compilar contra stubs incorrectos.
forbidden=(
  'openInventory:(Lorg/bukkit/inventory/Inventory;)V'
  'setItemMeta:(Lorg/bukkit/inventory/meta/ItemMeta;)V'
  'InventoryCloseEvent.getPlayer:()Ljava/lang/Object;'
  'getWhoClicked:()Ljava/lang/Object;'
  'Projectile.getShooter:()Ljava/lang/Object;'
  'CraftItemEvent.getInventory:()Ljava/lang/Object;'
  'CraftItemEvent.getInventory:()Lorg/bukkit/inventory/Inventory;'
)

for signature in "${forbidden[@]}"; do
  if grep -Fq "$signature" "$DUMP"; then
    echo "Firma Bukkit incompatible detectada: $signature" >&2
    exit 1
  fi
done

# Firmas reales usadas por Paper/Purpur 1.21.6.
# CraftItemEvent sobrescribe getInventory() con retorno covariante CraftingInventory.
required=(
  'openInventory:(Lorg/bukkit/inventory/Inventory;)Lorg/bukkit/inventory/InventoryView;'
  'setItemMeta:(Lorg/bukkit/inventory/meta/ItemMeta;)Z'
  'InventoryCloseEvent.getPlayer:()Lorg/bukkit/entity/HumanEntity;'
  'getWhoClicked:()Lorg/bukkit/entity/HumanEntity;'
  'Projectile.getShooter:()Lorg/bukkit/projectiles/ProjectileSource;'
  'CraftItemEvent.getInventory:()Lorg/bukkit/inventory/CraftingInventory;'
)

for signature in "${required[@]}"; do
  if ! grep -Fq "$signature" "$DUMP"; then
    echo "No se encontro la firma oficial esperada: $signature" >&2
    exit 1
  fi
done

echo "Bytecode verificado: firmas compatibles con Bukkit/Paper/Purpur 1.21.6."
