# MDVQuest 1.4.7 — Merge de las correcciones

Combina la corrección de crafteos de la versión 1.4.6 con el cambio de la versión aportada que elimina el spam de consola.

- Las misiones `CRAFT_VANILLA_ITEM` verifican los ingredientes consumidos: si el inventario lleno impide fabricar, no se suma progreso; con espacio parcial, se cuentan las operaciones realizadas.
- Se elimina el mensaje `Displaying new progress action bar for ...` que aparecía en consola por cada actualización de progreso.
- Se conserva la barra de progreso visible para el jugador.

## Instalación

1. Detener el servidor.
2. Reemplazar el JAR anterior por `MDVQuest-1.4.7.jar` en `plugins/`, dejando una sola versión de MDVQuest.
3. Iniciar el servidor.

No requiere cambiar la configuración, las misiones ni la base de datos.

## Validación

El proyecto conserva las 26 pruebas automatizadas de regresión de crafteo, que se ejecutan con Java 21 y `mvn clean package`. Las pruebas simulan los eventos; queda por comprobar la integración dentro de Minecraft con los otros plugins del servidor.
