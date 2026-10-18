# 1.4.6

- Corrige el progreso de `CRAFT_VANILLA_ITEM` al hacer shift-click con el inventario lleno o con espacio limitado.
- Cuenta las operaciones confirmadas por los ingredientes consumidos, en vez del máximo teórico de la receta.
- Valida cada clic antes de mover ingredientes, usar el libro de recetas o cerrar la mesa; no duplica progreso entre clics del mismo tick.
- Respeta eventos cancelados, cantidades de salida y misiones que cuentan operaciones en lugar de objetos.
- Sin cambios de configuración, misiones ni esquema SQLite.

## 1.4.5

- Añade Forms nativos de Floodgate para el visualizador de contratos aceptados.
- Añade Form nativo para el NPC interactivo: aceptar, entregar, reclamar y cancelar.
- Añade `MenusBedrock/quest_viewer.yml` y `MenusBedrock/quest_npc.yml` editables.
- Mantiene las GUIs Java sin cambios.

## 1.4.4
- `HARVEST_CROP` ahora cuenta la cosecha de `SWEET_BERRY_BUSH` mediante clic derecho sobre un arbusto completamente maduro.
- La interacción se valida en el siguiente tick: solo progresa si el bloque sigue siendo un arbusto de bayas y su edad realmente disminuyó.
- Romper el arbusto no cuenta como cosecha y los clics duplicados de ambas manos quedan deduplicados.
- Se conserva el control `natural-only` consultando si el arbusto fue colocado por un jugador.
- Sin cambios de configuración, YAML de misiones ni esquema SQLite.

## 1.4.3
- Corregido el estado fantasma después de `/mdvquest reroll <rotación|all> confirmar`: la caché de aceptación, progreso, reclamación y escrituras pendientes se invalida para las instancias eliminadas.
- Los rerolls dentro del mismo ciclo generan IDs de instancia únicos, incluso cuando vuelve a salir la misma definición.
- Al abrir `/quest` o el menú interactivo se sincronizan primero vencimientos y rotaciones, evitando esperar hasta 60 segundos al mantenimiento periódico.
- Sin cambios de configuración, YAML de misiones ni esquema SQLite.

## 1.4.2
- Las misiones VIP ya no pueden aceptarse sin su permiso de acceso; la validación se realiza también en el servicio y no solo visualmente.
- En el menú interactivo del NPC, el click derecho sobre una misión aceptada entrega todos sus objetivos de entrega pendientes sin abrir detalles.
- La instrucción de entrega aparece únicamente en contratos aceptados con al menos un objetivo de entrega pendiente.
- El brillo de contrato en curso fuerza `enchantment_glint_override`, corrigiendo iconos personalizados como cofres que anulaban el encantamiento visual.
- Las instrucciones para ver detalles, aceptar y cancelar se muestran en líneas separadas; los `config.yml` antiguos con una sola línea siguen siendo compatibles.

## 1.4.1

- Los contratos expirados eliminan aceptación, progreso, reclamación y víctimas PvP únicas.
- La limpieza de ciclos vencidos se ejecuta antes de generar el nuevo roll y también al iniciar el servidor.
- Las misiones reclamadas siguen ocupando su cupo hasta que termina o se regenera la rotación.
- Una misión reclamada no puede cancelarse para recuperar el cupo anticipadamente.
- Las instancias vencidas se ocultan inmediatamente de menús, conteos y búsquedas internas.
- Sin cambios de esquema SQLite ni edición manual de configuración.

# Changelog

## 1.3.0

- El menú público de `/mdvquest`, `/quest`, `/quests`, `/misiones` y MDVSocial pasa a ser una vista de solo consulta: muestra progreso, objetivos, recompensas y expiración, pero no abre detalles, no entrega objetos y no reclama premios.
- Añadido un menú interactivo independiente que conserva el comportamiento completo anterior: detalles, entregas y reclamación.
- Añadido `/mdvquest npc [jugador]` para abrir el menú interactivo completo; desde consola/Citizens requiere indicar el jugador.
- Añadido el permiso `mdvquest.admin.open-interactive`, heredado por `mdvquest.admin`.
- Las dos variantes del menú usan configuraciones separadas en `menus.viewer` y `menus.interactive`, incluyendo títulos, tamaño, relleno, slots, categorías, separadores, estados, lore, paginación y botón de regreso.
- El detalle interactivo se personaliza en `menus.interactive.detail`.
- El catálogo administrativo incorpora un filtro cíclico por archivo YAML en el slot 46. General/todos los YAML siempre es la primera opción; clic izquierdo avanza, clic derecho retrocede y Shift-click reinicia el filtro.
- El filtro por YAML se combina con duración y página, y se conserva al visualizar, editar, cancelar o regresar.
- Al guardar o mover una misión, el catálogo se abre filtrado por su YAML de destino.
- Migración automática no destructiva: las antiguas secciones `menus.main`, `menus.detail`, `menus.page-buttons` y `menus.back-command` se copian a las nuevas variantes sin borrar personalizaciones.
- El mensaje antiguo que invitaba a reclamar con `/misiones` se actualiza al encargado de misiones cuando todavía conserva el texto predeterminado anterior.
- No hay cambios de esquema en SQLite ni migraciones de misiones o familias.

## 1.2.4

- Corregida la cuadrícula del editor de recompensas: los slots 9–44 quedan realmente vacíos aunque MDVSocial rellene el inventario con paneles.
- Los paneles decorativos ya no pueden moverse ni guardarse accidentalmente como recompensas.
- Shift-click desde el inventario del administrador mueve objetos únicamente a los slots editables de recompensas.
- Shift-click sobre una recompensa existente la elimina del borrador; sobre una plantilla real la devuelve al inventario.
- Se mantienen libres los clicks normales, click derecho y arrastre dentro de la cuadrícula para mover, dividir, combinar y modificar cantidades.
- Las recompensas existentes continúan siendo vistas virtuales para impedir que el editor permita obtener objetos gratis.
- No hay cambios de config, YAML de misiones ni SQLite.

## 1.2.3

- Corregido el editor de recompensas físicas: las recompensas existentes ya no aparecen bloqueadas en el slot 0.
- La cuadrícula editable usa los slots 9–44 y representa el resultado final de recompensas de objetos.
- Se pueden mover objetos, modificar cantidades, añadir plantillas desde el inventario y eliminar entradas con Shift + clic derecho.
- Guardar reemplaza las recompensas físicas actuales en lugar de añadir duplicados; experiencia y comandos permanecen intactos.
- Los objetos usados como plantilla se devuelven al administrador y las vistas virtuales no pueden extraerse como ítems reales.
- Corregido el movimiento entre archivos YAML cuando el ID original contenía guiones bajos u otra variante normalizada.
- Guardar una misión existente elimina copias duplicadas del mismo ID normalizado en todos los YAML y usa restauración transaccional si alguna escritura falla.
- Sin cambios en SQLite, rotaciones ni progreso de jugadores.

## 1.2.2

- Las misiones VIP completadas sin permiso de reclamación ya no muestran el mensaje final que invita a cobrar ni reproducen el sonido de confirmación.
- Añadido `/mdvquest force <id-de-mision>` (`/mdvquest forzar`) para insertar una misión concreta en el ciclo global actual sin reroll, sin eliminar otras misiones y sin borrar progreso.
- El comando `force` admite también definiciones deshabilitadas para facilitar pruebas; la rotación de la misión debe existir y estar habilitada.
- Añadido permiso `mdvquest.admin.force` y autocompletado de IDs de misión.
- El estado completado pendiente de reclamar usa por defecto `LIME_STAINED_GLASS_PANE` en lugar de lana verde.
- Rediseño pequeño del menú principal: columna separadora configurable en los slots 10/19/28/37 con panel marrón o morado según la categoría seleccionada.
- Las misiones del catálogo principal ahora ocupan por defecto los slots 11–17, 20–26, 29–35 y 38–44.
- Orden del catálogo principal: primero misiones normales, luego VIP1 y después VIP2.
- Añadidas líneas configurables para identificar misiones VIP desbloqueadas en el lore del catálogo principal.
- El workflow de GitHub Actions ahora detecta automáticamente el JAR generado y el nombre del artefacto, para que no tengas que editar `.github/workflows/build.yml` en cada versión nueva.
- No hay cambios de esquema en SQLite ni migraciones destructivas.

## 1.2.0

- Cantidad variable por rotación y pool mediante `min-missions` / `max-missions`.
- Tres selecciones globales: normal, VIP1 y VIP2.
- Pool normal: definiciones normales.
- Pool VIP1: definiciones normales restantes + definiciones VIP1.
- Pool VIP2: definiciones VIP1 restantes + definiciones VIP2.
- Sin porcentajes ni pesos por origen del pool; se conservan los pesos individuales existentes de cada misión.
- No se repite una misma definición entre pools durante el mismo ciclo.
- Todos los jugadores pueden ver y progresar todas las misiones activas.
- Las recompensas VIP requieren permiso al reclamar.
- Panel celeste para misiones VIP1 bloqueadas y panel amarillo para VIP2 bloqueadas.
- Selector Normal/VIP1/VIP2 dentro del editor visual.
- Migración automática de SQLite con la columna `access_pool`; instalaciones 1.1.x se conservan.
- `/mdvquest reroll <rotación|all> confirmar` con advertencia obligatoria.

## 1.1.1

- Eliminados el reloj de página y el botón de cerrar del menú público principal.
- La cabeza de volver ahora ocupa el slot 49 y ejecuta el comando configurado, por defecto `/social`.
- Flechas de página en los extremos inferiores; solo aparecen cuando existen más páginas y muestran `página/páginas`.
- Los libros de duración muestran progreso completado/total y el tiempo hasta la próxima rotación del grupo.
- Para grupos 2–3 y 4–6 días se calcula la rotación más próxima entre todas sus duraciones.
- Rediseñado el detalle: objetivos en slots 10–16, recompensas en 29–33 y marco verde configurable.
- Paginación independiente de recompensas en slots 45 y 53; volver en 49 restaura grupo y página anteriores.
- Eliminado el botón de cerrar y el indicador extra de reclamación del detalle.
- Todos los slots, materiales, nombres, lore, títulos y marco de ambos menús públicos son configurables.
- Los nombres de objetos vanilla se muestran con componentes traducibles del cliente.
- MMOItems y objetos MythicMobs/Crucible muestran el nombre efectivo configurado en el objeto real.
- Añadidos nombres configurables para la experiencia principal y profesiones MMOCore.
- Las recompensas de ejemplo quedaron limitadas a poca EXP principal y lingotes de hierro, sin dinero.
- Migración opcional y de una sola vez para sanear `missions/examples.yml` antiguo sin tocar otros YAML.
- El editor limita por defecto a siete objetivos para coincidir con los slots 10–16.

## 1.1.0

- Añadido editor visual completo de misiones con selector de 1 a 7 días reales.
- Añadido catálogo administrativo agrupado por duración.
- Clic izquierdo para visualizar una misión y clic derecho para editarla.
- Edición in-game de ID, nombre, lore, icono exacto, duración, peso, archivo YAML y estado habilitado.
- Añadido asistente por chat para todos los objetivos V1; `cancelar` vuelve sin guardar el paso.
- Permitidos varios objetivos, incluidos objetivos repetidos del mismo tipo.
- Añadida administración visual: clic izquierdo edita y clic derecho elimina.
- Añadido `KILL_ANY_HOSTILE_MOB` para monstruos vanilla y MythicMobs.
- Añadido editor visual de recompensas físicas mediante depósito de objetos.
- Identificación automática de recompensas vanilla, MMOItems y MythicMobs/Crucible.
- Añadidas recompensas estructuradas de experiencia principal y profesiones MMOCore.
- Añadidos `mythic-items`, `exact-items` y `experience` al formato YAML.
- Reclamación segura con validación previa de IDs, stacks y slots vacíos.
- Reserva de slots; cualquier objeto intruso colocado durante la entrega se expulsa y no reemplaza la recompensa.
- Bloqueo por jugador para impedir reclamaciones simultáneas en carrera.
- Rediseñado el menú público en categorías 1 día, 2–3 días, 4–6 días y 7 días.
- Añadido menú de detalle con objetivos, experiencia y recompensas paginadas.
- Las flechas aparecen únicamente cuando son necesarias.
- La misión completada sin reclamar usa lana verde; la reclamada usa tinte gris.
- Iconos y herramientas del menú ocultan atributos y tooltips vanilla.
- Añadida cabeza custom configurable para volver.
- Escrituras YAML atómicas y soporte para mover/renombrar misiones entre archivos.
- Se conserva compatibilidad con YAML y SQLite de 1.0.x.

## 1.0.3

- Corregida la firma de `CraftItemEvent#getInventory()` para Paper/Purpur 1.21.6.
- Compilación limpia contra `paper-api:1.21.6-R0.1-SNAPSHOT`.
- GitHub Actions con Java 21 y verificación de bytecode.

## 1.0.0

- Primera versión de MDVQuest V1.
