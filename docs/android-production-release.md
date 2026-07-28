# Battly Mobile: producción y publicación

Esta guía deja el repositorio listo para GitHub sin subir secretos y separa claramente pruebas de producción.

La versión preparada por este documento es Battly Mobile `2.0.6` (`versionCode 10000013`).

## Variantes

- `debug`: `com.tecnobros.battlylauncher.debug`, Firebase de pruebas, firma debug local de Android.
- `release`: `com.tecnobros.battlylauncher`, Firebase de producción, sin R8 y sin firma de subida obligatoria.
- `gplay`: `com.tecnobros.battlylauncher`, Firebase de producción, sin R8 y con firma de subida obligatoria.
- `proguard` y `proguardNoDebug`: heredan de debug, usan Firebase de pruebas y conservan R8 solo para validación interna.

## Archivos Que No Se Suben

Estos archivos quedan ignorados por `.gitignore`:

- `keystore.properties`
- `release-secrets/`
- `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`
- `**/google-services.json`
- `.env*`
- APK/AAB y capturas/dumps generados durante QA

Las plantillas publicables son:

- `app_pojavlauncher/src/debug/google-services.example.json`
- `app_pojavlauncher/src/release/google-services.example.json`
- `app_pojavlauncher/src/gplay/google-services.example.json`

## Configuración Local

Firebase real:

```text
app_pojavlauncher/src/debug/google-services.json
app_pojavlauncher/src/release/google-services.json
app_pojavlauncher/src/gplay/google-services.json
```

`proguard` y `proguardNoDebug` heredan de `debug`, así que usan el `google-services.json`
de pruebas salvo que se cree un source-set específico.

OAuth/Firebase debug:

```text
Package name: com.tecnobros.battlylauncher.debug
SHA1 debug: 17:D6:F8:A1:A3:8E:B2:EF:B7:B2:C7:A7:75:99:9C:F4:0D:46:84:10
OAuth client ID predeterminado: 490291541450-0ocq9cfvovh2ft45a9kgsvf12cos4emm.apps.googleusercontent.com
```

OAuth/Firebase release y Play Store:

```text
Package name: com.tecnobros.battlylauncher
OAuth Android client ID: 490291541450-r17e3frigvot7b8a2lhu32k2dp8eqq28.apps.googleusercontent.com
Usa el SHA1/SHA-256 de la firma de subida o de App Signing de Play Console.
No reutilices el cliente OAuth debug para release.
```

Los IDs son identificadores públicos, no secretos. Pueden sustituirse sin modificar el repositorio:

```text
BATTLY_GOOGLE_DEBUG_CLIENT_ID
BATTLY_GOOGLE_RELEASE_CLIENT_ID
```

Firma de subida:

```properties
storeFile=release-secrets/battly-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

También se puede firmar con variables de entorno:

```text
GPLAY_KEYSTORE_FILE
GPLAY_KEYSTORE_PASSWORD
GPLAY_KEY_ALIAS
GPLAY_KEY_PASSWORD
```

## Secrets De GitHub

Configura estos secrets en el repositorio:

```text
FIREBASE_DEBUG_GOOGLE_SERVICES_JSON_B64
FIREBASE_PROD_GOOGLE_SERVICES_JSON_B64
GPLAY_KEYSTORE_B64
GPLAY_KEYSTORE_PASSWORD
GPLAY_KEY_ALIAS
GPLAY_KEY_PASSWORD
GPLAY_SERVICE_JSON
CURSEFORGE_API_KEY
```

En local, CurseForge se lee desde `CURSEFORGE_API_KEY` o desde `curseforge_key.txt`.
Ambos caminos quedan fuera de Git.

Para convertir archivos a base64 desde PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app_pojavlauncher/src/debug/google-services.json"))
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app_pojavlauncher/src/gplay/google-services.json"))
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-secrets/battly-upload.jks"))
```

## BattlyWorlds

BattlyWorlds está desactivado por defecto mientras Google Play revisa el acceso a VPNService. La propiedad `battlyWorldsEnabled` controla a la vez la lógica, el servicio VPN y el permiso `FOREGROUND_SERVICE_CONNECTED_DEVICE`. Para una build autorizada usa `./gradlew assembleRelease -PbattlyWorldsEnabled=true`; sin esa propiedad, el servicio y el permiso no aparecen en el manifest fusionado.

## Builds

Debug local:

```powershell
.\gradlew :app_pojavlauncher:assembleDebug --console=plain
adb install .\app_pojavlauncher\build\outputs\apk\debug\app_pojavlauncher-debug.apk
```

Play Store:

```powershell
.\gradlew :app_pojavlauncher:bundleGplay --console=plain
```

Artefacto:

```text
app_pojavlauncher/build/outputs/bundle/gplay/app_pojavlauncher-gplay.aab
```

GitHub Releases:

```powershell
.\gradlew :app_pojavlauncher:assembleGplay --console=plain
```

Artefacto:

```text
app_pojavlauncher/build/outputs/apk/gplay/app_pojavlauncher-gplay.apk
```

## Verificación 16 KB

Google exige que las apps con código nativo soporten tamaños de página de 16 KB.

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\android\verify-native-16kb.ps1 -Path .\app_pojavlauncher\build\outputs\apk\gplay\app_pojavlauncher-gplay.apk
powershell -ExecutionPolicy Bypass -File .\tools\android\verify-native-16kb.ps1 -Path .\app_pojavlauncher\build\outputs\bundle\gplay\app_pojavlauncher-gplay.aab -SkipZipAlign
python .\tools\android\elf_16k_page_align.py --zip .\app_pojavlauncher\build\outputs\bundle\gplay\app_pojavlauncher-gplay.aab
```

Si falla una librería precompilada, hay que recompilarla con NDK r28+ y:

```text
-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
```

La configuración de publicación de Battly Mobile 2.0.6 mantiene estas garantías y debe validarse con los comandos de la sección anterior antes de crear cada tag:

- `.\gradlew :app_pojavlauncher:assembleGplay :app_pojavlauncher:bundleGplay --console=plain` genera y firma correctamente el APK para GitHub y el AAB para Google Play.
- El AAB contiene `arm64-v8a` y `armeabi-v7a` en `base/lib` y no empaqueta el runtime Java 8. Los assets LWJGL conservan sus cuatro arquitecturas porque se extraen en tiempo de ejecución.
- `verify-native-16kb.ps1` valida correctamente todas las librerías de `base/lib` y las librerías LWJGL extraíbles incluidas como assets.
- El AAB firmado verificado pesa aproximadamente 115 MB y está listo para la validación de Google Play.

Las publicaciones salen de una rama con un commit raíz limpio y nunca deben mezclar el historial importado. La rama local antigua `v3_openjdk` conserva un `app_pojavlauncher/upload.jks` histórico; esa clave debe considerarse comprometida y rotarse en Google Play Console. El branch limpio elimina el fichero y su historial de la referencia pública, manteniendo la rama antigua únicamente como respaldo local.

Fuentes principales de los binarios pendientes:

- `app_pojavlauncher/src/main/jniLibs/**`
- `MobileGlues/MobileGlues-cpp/**`
- `Terracotta`/EasyTier nativo incluido en el AAR
- Dependencias nativas de Maven como `bytehook`

El workflow de GitHub ejecuta esta verificación para bloquear una publicación de Play si vuelve a aparecer un binario no compatible.

Detalles del control 16 KB:

- `gplay` limita las librerías Android de `base/lib` a `arm64-v8a` y `armeabi-v7a`. Los binarios LWJGL de assets se mantienen para no alterar el mecanismo de extracción del launcher y también se verifican a 16 KB.
- `libjnidispatch.so` y `libunpack200.so` se compilan desde JNA 5.13.0 y OpenJDK 8 con NDK r28.2, sin dependencias C++ externas y con segmentos `PT_LOAD` alineados a `0x4000`.
- La aplicación compila con `compileSdk` y `targetSdk` 36 y AGP 8.11.1. Las variantes de publicación no usan R8 ni reducción de recursos para evitar regresiones en clases cargadas mediante reflexión, XML, Gson, JNI y eventos débiles.
- R8 se conserva únicamente en `proguard` y `proguardNoDebug`, que actúan como variantes internas de compatibilidad y nunca deben publicarse.
- `tools/android/elf_16k_page_align.py` realinea los segmentos `PT_LOAD`, actualiza offsets de cabeceras y secciones ELF y establece `p_align` en `0x4000` antes del empaquetado.
- La tarea se ejecuta tanto sobre las librerías fusionadas como sobre los assets nativos extraíbles. También se actualizan los marcadores de LWJGL para forzar su nueva extracción en instalaciones existentes.
- ByteHook se actualiza a `1.1.1`, que sustituye el precompilado anterior usado por la aplicación.
- No debe introducirse ningún `.so` después de `fixGplayNativeLibPageSize`; el workflow vuelve a inspeccionar el artefacto final y bloquea la publicación si detecta una regresión.

## MobileGlues

Battly Mobile 2.0.3 integra MobileGlues 1.3.5 desde el submódulo oficial. El
renderer se compila como `libmobileglues.so` y forma parte del APK/AAB firmado,
por lo que una instalación 2.0.0 no puede sustituirlo mediante una descarga
remota sin actualizar la aplicación.

El parche reproducible de Battly se encuentra en
`patches/mobileglues/1.3.5-battly.patch` y se aplica automáticamente antes de
compilar el módulo. Mantiene la alineación ELF de 16 KB y evita un cierre si un
driver devuelve un `GL_VERSION` nulo.

El ajuste de SDL se conserva igualmente como parche versionado en
`patches/sdl/3.2.20-battly.patch`. Gradle aplica ambos parches de forma
idempotente antes de compilar; los submódulos pueden quedar modificados
internamente después de una build, pero ningún artefacto depende de commits
locales o privados.

No se incluye el APK de MobileGlues Plugin ni su cambio a
`MANAGE_EXTERNAL_STORAGE`; Battly utiliza únicamente el renderer nativo y
mantiene el almacenamiento privado de la aplicación.

## Calidad De Código

El control de release usa Android Lint con `abortOnError = true`. Los avisos heredados permanecen visibles en `app_pojavlauncher/build/reports/lint-results-debug.html` y no se silencian como errores.

## Checklist Antes De Publicar

1. `rg --hidden --glob '!**/.git/**' --glob '!**/build/**' --glob '!MobileGlues/**' --glob '!app_pojavlauncher/src/main/jni/SDL/**' -n "BEGIN PRIVATE KEY|storePassword|keyPassword|client_secret|FIREBASE_SERVICE_ACCOUNT" .`
2. `git status --short` y confirmar que los secretos aparecen ignorados, no como cambios.
3. `.\gradlew :app_pojavlauncher:assembleDebug --console=plain`
4. `adb install .\app_pojavlauncher\build\outputs\apk\debug\app_pojavlauncher-debug.apk`
5. `.\gradlew :app_pojavlauncher:bundleGplay --console=plain`
6. Verificar 16 KB con el script anterior.
7. Confirmar que `versionName` es `2.0.3-beta` y `versionCode` es `10000009` en el manifest fusionado o con `apkanalyzer`.
