# Changelog

## 2.1.1 - 2026-08-18

- Corregido el flujo **Abrir modpack**: la instancia se selecciona, el modal se cierra y el inicio vuelve a mostrar la version correcta.
- Las advertencias de Sodium y memoria RAM se muestran en orden para que no se superpongan.
- Los cierres normales de Minecraft con codigo de salida `0` ya no se notifican como crashes.
- Mejorada la persistencia de las sesiones de Microsoft para evitar perfiles vacios y cierres de sesion inesperados.
- El gestor de skins indica si la skin activa utiliza el modelo clasico o slim.
- El aviso de Sodium pasa a ser informativo y no bloquea el inicio del juego.

## 2.1.0 - 2026-08-18

### Controles

- Nuevo asistente de configuracion para mandos PlayStation, Xbox y Nintendo Switch.
- Perfiles de mando por instancia, calibracion de sticks, zonas muertas y asignacion de movimiento, camara, gatillos, cruceta y desplazamiento.
- Previsualizaciones 3D animadas que reaccionan a los botones fisicos y se adaptan al tamaño de la pantalla.
- Imagen opcional del mando, teclado o raton dentro del juego, con posicion y tamaño personalizables.
- Contador de FPS revisado y ping real del servidor mientras el usuario esta conectado a una partida.

### Descargas y mantenimiento

- Sistema de descargas renovado con HTTP/2, conexiones reutilizables, reanudacion, reintentos y concurrencia configurable entre 16 y 100 archivos.
- Verificacion previa de archivos JAR, ZIP y AAR para evitar usar descargas incompletas o corruptas.
- Nuevo centro de mantenimiento para detectar y reparar componentes, runtimes e instalaciones dañadas.
- Al terminar de instalar un modpack, Battly permite abrir directamente la nueva instancia.

### Minecraft y graficos

- MobileGlues actualizado a 2.0.0 con sus nuevas opciones graficas y ajustes de compatibilidad.
- Seleccion mas precisa de Java, LWJGL y renderer segun la version de Minecraft y el dispositivo.
- Mejoras de entrada SDL/LWJGL para snapshots modernas, controles tactiles, tecla ESC y movimiento de camara.
- Mejor deteccion de mods incompatibles con explicaciones y soluciones dentro de una pantalla nativa.

### Launcher

- Nuevos paneles Battly para tareas, descargas, instalaciones y logs.
- Gestor de instancias y contenido ampliado con copias, restauracion, actualizacion de modpacks e iconos de mods instalados.
- Traducciones y textos heredados revisados en todos los idiomas incluidos.

## 2.0.9 - 2026-08-11

- Las skins de Battly funcionan en mas versiones, loaders e instancias, incluidas cuentas offline compatibles.
- El authlib de Battly se actualiza desde la API respetando la compatibilidad de cada plataforma.
- Corregidos instaladores de Forge y OptiFine que se quedaban bloqueados al 95 % pese a haber terminado.
- Mejoras en snapshots modernas: controles tactiles, color, ESC, FPS y seleccion de LWJGL.
- Los fondos personalizados aceptan correctamente imagenes, GIF y video.
- Battly Workspace muestra iconos del contenido instalado y anuncios nativos integrados en sus listas.
- La opcion de notch controla si Battly y Minecraft dibujan en toda la pantalla o respetan el area segura.

## 2.0.8 - 2026-08-02

- Restaurado el comportamiento estable de los controles tactiles en versiones modernas de Minecraft.
- Corregidos los botones superiores, la tecla ESC y la navegacion por menus.
- Las cuentas offline dejan de aparecer con ventajas de cola premium.
- Forge reutiliza instaladores descargados y completa correctamente su progreso.
- Mejorada la seleccion de LWJGL para Minecraft 26.x.
- Corregidas regresiones generales de compatibilidad introducidas en versiones anteriores.

## 2.0.7 - 2026-08-01

- Rediseñados los paneles de descargas, instalaciones y logs con el estilo de Battly.
- Los logs incorporan busqueda, filtros, contadores, seguimiento en directo y subida a mclo.gs.
- Mejorado el instalador de modpacks y modloaders, con validaciones y mensajes de finalizacion.
- Corregida la gestion de cuentas Microsoft sin crear perfiles de demostracion cuando no se posee Minecraft.
- Mejoradas las rutas SDL para snapshots modernas y la recuperacion de perfiles tras instalar contenido.

## 2.0.6 - 2026-07-29

- Retirada la minificacion R8 de las variantes de produccion tras detectar incompatibilidades con fragments, modelos y autenticadores cargados por reflexion.
- Recuperado el funcionamiento de Battly+, Battly Workspace y los distintos tipos de cuenta.
- Corregidos cierres derivados de clases eliminadas u ofuscadas durante el build.
- Version de estabilizacion preparada sin optimizaciones destructivas.

## 2.0.5 - 2026-07-28

- Parche urgente para restaurar el inicio de sesion y las pantallas cargadas dinamicamente.
- Añadidas protecciones iniciales para clases utilizadas por Gson, fragments y reflection.
- Corregidos fallos al descargar contenido desde Battly Workspace.
- Reforzada la configuracion de build y firma de las variantes de produccion.

## 2.0.4 - 2026-07-26

- Mejor compatibilidad con versiones antiguas y nuevas de Minecraft, Forge, mods, shaders y snapshots.
- Pantalla nativa para explicar incompatibilidades de mods antes de que Fabric o Forge intenten abrir interfaces AWT/Swing.
- Nuevo sistema para analizar crashes, proponer soluciones y compartir logs mediante mclo.gs.
- Descargas concurrentes ampliadas y ajustables automaticamente.
- Corregidos controles tactiles, movimiento de camara, landscape y seleccion automatica de renderer.
- BattlyWorlds se mantuvo desactivado mediante un interruptor central hasta disponer de autorizacion de Google.

## 2.0.3-beta - 2026-07-22

- BattlyWorlds vuelve a estar disponible para crear salas privadas e invitar amigos.
- Restaurado el servicio VPN y su servicio en primer plano para mantener la conexion mientras Minecraft esta abierto.
- Conservado un interruptor central para poder desactivar BattlyWorlds rapidamente sin eliminar su codigo.
- La publicacion ya no depende de artefactos Java externos caducados; los runtimes se descargan bajo demanda desde Battly.
- La auditoria de librerias nativas de 16 KB funciona tanto en Windows como en los runners Linux de GitHub.
- Version beta actualizada a `2.0.3-beta` (`versionCode 10000009`).

## 2.0.2 - 2026-07-21

- Corregida la selección de instancias desde la lista de versiones del inicio.
- La papelera de cada instancia ya no bloquea el toque sobre la card.
- La instancia activa se mantiene sincronizada al recargar o eliminar perfiles.
- Corregidos cierres en Forge provocados por el inyector de skins offline y el gestor global de URL.
- El authlib de Battly deja de inyectarse en versiones modernas firmadas donde provocaba errores de firma.
- Corregida la prioridad de los nativos de LWJGL 3.3.3/3.4.1 y la selección automática de renderer en Minecraft moderno.
- Actualizado el stub del narrador para las versiones modernas que usan la nueva firma de inicialización.
- Preparado el inicio nativo con Google y el retorno de Discord al launcher; requiere desplegar las rutas de servidor incluidas.
- Build de publicación actualizado a `versionCode 10000007`.

## 2.0.1 - 2026-07-16

### Renderizado

- MobileGlues actualizado de 1.3.3 a 1.3.5 con correcciones para Sodium, texturas y shaders.
- Battly activa automáticamente la compatibilidad requerida por MobileGlues en Minecraft 26.3-snapshot-3 y versiones posteriores.
- Se mantienen las adaptaciones de Battly para páginas de memoria de 16 KB y drivers que no devuelven correctamente `GL_VERSION`.

### Minecraft y compatibilidad

- Selección automática de renderer revisada para mantener el juego en landscape y elegir una ruta compatible según GPU, versión de Minecraft y Java.
- Compatibilidad actualizada para versiones antiguas, Forge, snapshots modernas, LWJGL 3.3.3/3.4.1 y Battly Client.
- Diagnóstico de cierres mejorado, retorno al launcher tras finalizar Minecraft y mensajes útiles para mods incompatibles.
- Limpieza de la caché de skins al iniciar el juego y mejoras para skins offline persistentes.

### Instalación y contenido

- Descargas concurrentes adaptativas y gestión de runtimes desde el launcher; Java 8 se descarga después del onboarding en vez de incluirse en la aplicación.
- Centro de instancias y mundos con duplicado, exportación, copias, restauración y diagnóstico.
- Biblioteca para mods, resource packs, shaders y datapacks con acciones de activación, borrado y movimiento entre instancias.
- Resolución de dependencias reforzada y opción Forge + OptiFine durante la instalación.

### Battly

- Gestor de skins con previsualización, biblioteca y conexión con la cuenta Battly.
- Mejoras en Battly+, marketplace de controles, personalización, noticias y flujos de inicio de sesión.
- BattlyWorlds permanece temporalmente desactivado hasta disponer de autorización de `VPNService` en Google Play; su código no se ha eliminado.

### Calidad de la release

- Versión de aplicación actualizada a `2.0.1` (`versionCode 10000006`).
- Firebase, OAuth y firma separados entre debug y producción.
- Secretos locales excluidos de Git y CI endurecido para no ocultar fallos de publicación.
- Correcciones de traducciones y placeholders de formato que podían provocar cierres en algunos idiomas.
