---
name: fin-release
description: Paso 8 de fin-flow. Publica un release de Finanzas cuando fin-dev-test dio PASA - commit chore(release) con bump y changelog bilingüe en la rama, merge del PR a main, tag vX.Y.Z, push, wrangler deploy, workflow «Release apps» en verde con el APK, y cierra en Jira lo publicado. También a mano, cuando el cliente diga "saca el release", "publica", "tira el release".
argument-hint: [X.Y.Z]
---

# fin-release — de la rama probada a producción

«Saca el release» = el ciclo completo **sin pedir visto bueno aparte**: commit, merge, tag, push a
GitHub, deploy y APK. Solo se verifica antes y se informa al final.

## 1. Puertas (todas, o no hay release)
1. Hay un PR abierto contra `main` con **APROBADO** de `fin-review` posterior al último push de
   código. El commit `chore(release)` de esta misma skill (paso 3) es la única excepción: solo trae
   versión y changelog, y se empuja después del APROBADO.
2. Hay un informe de `fin-dev-test` con **PASA** cuyo sha es **exactamente** `HEAD` de la rama
   (`git rev-parse --short HEAD`, igual a `origin/<rama>`). Si la rama se movió después, o no hay
   informe (release a mano), corre `fin-dev-test` primero. Lo que llega a `main` es lo que se probó.
3. `main` no tiene **producto** sin publicar: `git log --format=%s $(git describe --tags --abbrev=0 origin/main)..origin/main`
   solo puede traer merges de `fin-merge` y commits `chore`/`docs`/`ci`/`refactor` (salen en este
   release sin más). Un `feat`/`fix` ahí es producto sin release: para y averigua de dónde salió.
4. El cambio incluye su parte de Android y su entrada en `docs/ANDROID_PARITY.md` (regla de paridad).

## 2. Versión
La indicada, o la siguiente según `CLAUDE.md` §Versionado mirando `git log main..HEAD`: algún
`BREAKING CHANGE`/`!` → MAJOR (**se le confirma al cliente** antes de publicar); algún `feat` →
MINOR; si no → PATCH. Comprueba que el tag está libre: `git ls-remote --tags origin vX.Y.Z` vacío
y `git tag -l vX.Y.Z` vacío.

## 3. Commit de release (en la rama)
Solo metadatos; si hace falta tocar código, no es un release, es otra ronda de `fin-implement`.
- `package.json` → `"version": "X.Y.Z"` (de ahí salen Tauri y `versionCode`/`versionName` del APK).
- `src/lib/changelog.ts` → entrada nueva **arriba** con `version`, `date` (hoy, `YYYY-MM-DD`), `es`
  y `en`: lo que nota el usuario, en el tono de las entradas anteriores, terminando en
  «En la web y en la app de Android.» cuando aplica.
- `npm run gen:android-strings` → regenera `android/app/src/main/assets/changelog.json` (las
  «Novedades» del APK). Comprueba que trae `"version": "X.Y.Z"`; sin esto el APK sale sin la
  versión nueva (pasó en 2.39.1, `docs/ANDROID_PARITY.md`).
- `docs/ANDROID_PARITY.md` al día. `CLAUDE.md` §Estado actual solo si cambia algo de fondo.
- `npm run build` en verde, y:

      git add package.json src/lib/changelog.ts android/app/src/main/assets/changelog.json docs/
      git commit -m "chore(release): X.Y.Z — <resumen corto>" -m "Refs: FIN-n" -m "Co-Authored-By: Claude <modelo> <noreply@anthropic.com>"
      git push

El diff de este commit contra el sha probado es solo versión y docs, así que no invalida el PASA.

## 4. Merge, tag y push
    gh pr merge <nº> --merge --delete-branch --subject "Merge <rama> — X.Y.Z" --body "Refs: FIN-n"
    git checkout main && git pull --ff-only origin main
    git tag vX.Y.Z && git push origin vX.Y.Z
    git branch -d <rama> 2>/dev/null || true

Nunca dejes un tag solo en local.

## 5. Deploy y artefactos
    npm run build
    cd worker && npx wrangler deploy
Comprueba producción: https://finanzas.aseth.workers.dev responde y sirve el bundle nuevo (el
changelog in-app muestra X.Y.Z, o el `index.html` servido trae los hashes de `dist/`).

El tag dispara el workflow **«Release apps»** (Tauri Linux/Windows + APK firmado):
`gh run list --workflow "Release apps" -L 1 --json databaseId,headBranch` (el de `vX.Y.Z`) →
`gh run watch <id> --exit-status` con `run_in_background`. Al acabar, `gh release view vX.Y.Z`
debe listar `Broke_X.Y.Z_android.apk`. Si falla: arréglalo en el momento (`gh run view <id>
--log-failed`); el tag no se borra ni se mueve — si el arreglo necesita código, sale como `X.Y.(Z+1)`.

## 6. Jira
Tareas publicadas (las de los commits de `main` desde el tag anterior, por su `Refs:`) → quitar
`en-revision`, transición `31` (Listo), comentario `Publicado en vX.Y.Z: https://github.com/AsuraBeliever/finance-manager/releases/tag/vX.Y.Z`.
Si se usó una épica `Release vX.Y.Z` y ya no tiene tareas abiertas, ciérrala.

## 7. Informe
Al cliente: qué trae la versión en lenguaje de producto, que ya está en la web/iPhone/escritorio y
el APK en el release, con el enlace. Si se llamó desde `fin-flow`, devuelve
`RELEASE: vX.Y.Z <url>` y el informe lo da el orquestador.
