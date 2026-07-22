# Changelog

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
