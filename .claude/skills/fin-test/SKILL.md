---
name: fin-test
description: Paso 4 de fin-flow. Prueba un cambio de Finanzas por partida doble, backend (fmt, clippy, tests de Rust, wasm check, tsc/build, tests y build del APK, migraciones) y uso real en la web (Playwright contra wrangler dev) y en el emulador Android, con evidencia revisada. Deja el informe en Jira.
argument-hint: FIN-n
---

# fin-test — backend y uso real

Tarea: $ARGUMENTS. Cada criterio del «Hecho cuando» acaba con una evidencia.
`OUT=<scratchpad>/fin-test/FIN-n` para capturas y logs (no van al repo).

## A. Backend — todo en verde o no se sigue
    cargo fmt --all -- --check
    cargo clippy --workspace
    cargo test --workspace
    cargo check -p finanzas-worker --target wasm32-unknown-unknown
    npm run build
    cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew testDebugUnitTest assembleDebug
Si hay migración nueva: `cd worker && npx wrangler d1 migrations apply finanzas --local` sin error.
Si cambió `src/i18n/`: `npm run gen:android-strings` no deja diff sin commitear.
Un test que falla se arregla volviendo a `fin-implement`. No se desactiva ni se salta.

## B. Uso real
Procesos largos con `run_in_background`.

**Web.** `npm run build` y luego `cd worker && npx wrangler dev` (http://localhost:8787; necesita
`worker/.dev.vars`). `wrangler dev` sirve el `dist/` que leyó al arrancar: si recompilas, reinícialo.
Lleva la app al estado de cada criterio con Playwright (`node scripts/parity-shots.mjs <pantallas>`
con `OUT=$OUT/web`, o un script propio en el scratchpad con la cuenta `alan@test.mx` / `test1234`),
o `curl` a `/api/rpc/<comando>` con la cookie de sesión para lo que no se ve.

**Android.** Emulador `emulator-5554` (normalmente ya abierto; `adb devices`). APK contra el worker local:

    cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug -PbrokeApiBase=http://10.0.2.2:8787
    adb install -r app/build/outputs/apk/debug/app-debug.apk

Navega con `adb shell uiautomator dump` (bounds) + `adb shell input tap`, nunca a ciegas. Captura con
`adb exec-out screencap -p > $OUT/android-<n>.png` y la web al mismo tamaño (`VIEWPORT`/`SCALE` de
`parity-shots.mjs`) para compararlas lado a lado. `adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime"` vacío.

**Abre cada captura con Read y mírala de verdad**: que exista no prueba nada. Consola del navegador
sin errores. Lo que no se pueda comprobar se declara **no verificado**.

Regresión: el camino crítico que no puede romperse nunca — entrar, panel con saldos, registrar un
gasto en una cartera y ver el saldo actualizado, en web y APK.

## C. Informe
Comentario en Jira y el mismo texto en `$OUT/informe.md`, que `fin-pr` copia al PR:

    **Pruebas — <sha corto>**
    Backend: fmt ✅ · clippy ✅ · cargo test ✅ (<n>, <n> nuevos) · wasm ✅ · tsc/build ✅ · gradle test+build ✅ · migraciones ✅/n.a.
    Uso real (wrangler dev local + emulator-5554):
    1. <criterio 1> → web ✅ <lo que se vio, con el dato> · APK ✅ <lo mismo>
    Regresión: ✅
    Errores en consola/logcat: 0
    No verificable: <lo que sea, o «nada»>

Si algo sale ❌, no hay informe final: vuelve a `fin-implement`.
