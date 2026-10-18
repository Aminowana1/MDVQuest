# MDVQuest 1.4.6 — Corrección del progreso al craftear

Las misiones `CRAFT_VANILLA_ITEM` verifican los ingredientes realmente consumidos después del clic. Antes se contaba el máximo que permitían los ingredientes, aunque el inventario no tuviera espacio.

- Inventario lleno y ningún objeto fabricado: **0 de progreso**.
- Espacio para dos picotas y dos fabricadas: **2 de progreso**.
- Recetas que producen varios objetos: se conserva la cantidad por operación.
- Los eventos cancelados no cuentan y los clics rápidos no duplican progreso.
- Cerrar la mesa, mover ingredientes o usar el libro de recetas antes del siguiente tick no genera progreso falso.

## Instalación

1. Detener el servidor.
2. Reemplazar el JAR anterior de MDVQuest por `MDVQuest-1.4.6.jar` en `plugins/`, dejando una sola versión del plugin.
3. Iniciar el servidor.

No requiere cambiar la configuración, las misiones ni la base de datos. No modifica el progreso que ya estuviera guardado.

## Validación

Se incluyen pruebas automatizadas de regresión para el contador y su ciclo de eventos. Se ejecutan con Java 21 y `mvn clean package`.

La comprobación final dentro de Minecraft debe incluir una misión de picotas con inventario lleno, con dos espacios libres y con espacio suficiente. Estas pruebas automatizadas simulan los eventos; no sustituyen esa prueba en el servidor con sus otros plugins.
