# Battly Mobile: producción y publicación

Esta guía deja el repositorio listo para GitHub sin subir secretos y separa claramente pruebas de producción.

## Variantes

- `debug`: `com.tecnobros.battlylauncher.debug`, Firebase de pruebas, firma debug local de Android.
- `release`: `com.tecnobros.battlylauncher`, Firebase de producción, sin firma de subida obligatoria.
- `gplay`: `com.tecnobros.battlylauncher`, Firebase de producción, firma de subida obligatoria.
- `proguard` y `proguardNoDebug`: heredan de debug y usan Firebase de pruebas.

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
```

OAuth/Firebase release y Play Store:

```text
Package name: com.tecnobros.battlylauncher
OAuth Android client ID: 490291541450-r17e3frigvot7b8a2lhu32k2dp8eqq28.apps.googleusercontent.com
Usa el SHA1/SHA-256 de la firma de subida o de App Signing de Play Console.
No reutilices el cliente OAuth debug para release.
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

BattlyWorlds está temporalmente desactivado en la app mientras se resuelve el acceso a `VPNService` con Google Play. El código permanece incluido para poder reactivarlo, pero la funcionalidad queda bloqueada con `BattlyWorldsFeature.ENABLED = false` y la declaración del servicio VPN no se publica en el manifest final.

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
```

Si falla una librería precompilada, hay que recompilarla con NDK r27+ y:

```text
-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
```

Estado comprobado el 2026-06-05:

- `.\gradlew :app_pojavlauncher:bundleGplay --console=plain` genera y firma el AAB correctamente.
- La verificación 16 KB todavía falla por librerías ELF precompiladas con `LOAD Align 0x1000`.
- No se debe subir el AAB a Play hasta reemplazar o recompilar esas librerías.

Fuentes principales de los binarios pendientes:

- `app_pojavlauncher/src/main/jniLibs/**`
- `MobileGlues/src/main/cpp/libraries/**/libspirv-cross-c-shared.so`
- `Terracotta`/EasyTier nativo incluido en el AAR
- Dependencias nativas de Maven como `bytehook`

El workflow de GitHub ejecuta esta verificación para bloquear una publicación de Play si vuelve a aparecer un binario no compatible.

## Checklist Antes De Publicar

1. `rg --hidden --glob '!**/.git/**' --glob '!**/build/**' --glob '!MobileGlues/**' --glob '!app_pojavlauncher/src/main/jni/SDL/**' -n "BEGIN PRIVATE KEY|storePassword|keyPassword|client_secret|FIREBASE_SERVICE_ACCOUNT" .`
2. `git status --short` y confirmar que los secretos aparecen ignorados, no como cambios.
3. `.\gradlew :app_pojavlauncher:assembleDebug --console=plain`
4. `adb install .\app_pojavlauncher\build\outputs\apk\debug\app_pojavlauncher-debug.apk`
5. `.\gradlew :app_pojavlauncher:bundleGplay --console=plain`
6. Verificar 16 KB con el script anterior.
