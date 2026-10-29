---
name: fin-dev-test
description: Paso 7 de fin-flow. Prueba entero, en dev (wrangler dev local con D1 migrada + APK debug en el emulador), el sha exacto de la rama que se va a publicar, el cambio y todo lo demás, antes de que pueda llegar a main. Devuelve PASA o FALLA y deja el informe en Jira. También sirve a mano ("prueba la rama", "cómo está dev").
argument-hint: FIN-n
---

# fin-dev-test — probar en dev

`fin-test` probó mientras se desarrollaba; esto prueba **el sha que se va a publicar**, con la suite
completa y el camino crítico de punta a punta, y es la puerta para `main`. Este repo no tiene
`develop` ni entorno remoto de dev: **dev = build de producción de la rama servido por `wrangler dev`
local + APK debug contra él en el emulador**. Tarea: $ARGUMENTS.
`OUT=<scratchpad>/fin-dev-test/<sha corto>` para capturas y logs.

## 1. Llevar a dev
    git checkout <rama> && git status --porcelain     # vacío
    git fetch origin && git rev-parse HEAD origin/<rama>   # iguales: lo empujado es lo que se prueba
    git merge-base --is-ancestor origin/main HEAD      # la rama contiene main; si no, rebase + fin-test
    git rev-parse --short HEAD                          # el sha que se prueba (y el que se publicará)
1. Si hay checks de PR (FIN-4), en verde sobre **ese** sha. Rojo → **FALLA**.
2. Build limpio: `npm run build`; D1 local con todas las migraciones
   (`cd worker && npx wrangler d1 migrations apply finanzas --local`); `npx wrangler dev` en segundo
   plano, **arrancado después** del build. APK: `assembleDebug -PbrokeApiBase=http://10.0.2.2:8787`
   e `adb install -r`.
3. Comprueba que lo que corre es ese sha: el bundle de `dist/` es el recién compilado (hash de
   `index.html` igual al servido en http://localhost:8787) y el APK instalado es el recién
   compilado (`adb shell dumpsys package com.asura.finanzas.debug | grep lastUpdateTime`). Si no se puede
   comprobar, dilo en el informe.

## 2. Probar en dev
1. **Backend completo**, no solo lo tocado: `cargo fmt --all -- --check`, `cargo clippy --workspace`,
   `cargo test --workspace`, `cargo check -p finanzas-worker --target wasm32-unknown-unknown`,
   `npm run build`, `cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew testDebugUnitTest assembleDebug`.
2. **El cambio:** cada criterio del «Hecho cuando» de la tarea, otra vez, en web **y** APK.
3. **Regresión, de punta a punta, en web y APK:** entrar · panel con saldos · registrar un gasto y
   ver el saldo de la cartera actualizado · transferencia entre carteras · pantalla de inversiones
   carga · ajustes → cambiar idioma y volver. Si el cambio toca el cron o datos de mercado, además
   `curl` al RPC afectado.
4. `wrangler dev` sin errores en su salida; consola del navegador y `logcat` sin `FATAL EXCEPTION`.
Igual que en `fin-test`: cada captura se abre con Read y se mira; lo que no se pueda comprobar es
**no verificado**, y un criterio no verificado del «Hecho cuando» cuenta como FALLA.

## 3. Veredicto
**PASA** solo si todo lo anterior está ✅. Si no, **FALLA**. Comentario en la tarea de Jira y el
mismo texto en `$OUT/informe.md`, que `fin-release` copia al PR:

    **Prueba en dev — <sha corto> — PASA ✅ / FALLA ❌**
    Entorno: build de producción de <rama> en wrangler dev local + APK debug en emulator-5554
    Backend completo ✅ <n> tests · wasm ✅ · build ✅ · gradle ✅
    Cambio FIN-n: web ✅ · APK ✅
    Regresión: ✅
    Errores en logs: 0
    No verificable: <lo que sea, o «nada»>

Con **FALLA**: la tarea sigue *En curso*, se quita `en-revision` y se comenta el fallo con la
evidencia; `fin-flow` vuelve a `fin-implement`. **Nunca** se pasa a release.

## 4. Devuelve
`DEV: PASA <sha>` o `DEV: FALLA` + lista de fallos con su evidencia.
