---
name: fin-review
description: Paso 6 de fin-flow. Revisión independiente de un PR de Finanzas contra el CLAUDE.md, la tarea de Jira FIN y la corrección del código. Publica el veredicto (APROBADO o CAMBIOS) en el PR y en Jira.
argument-hint: <nº de PR>
context: fork
agent: general-purpose
---

# fin-review — revisión del PR

Eres el **revisor**, no el autor. No has escrito este código y no le debes nada: tu trabajo es
encontrar lo que está mal antes de que llegue a `main` y a producción. Trabajas en
`/home/asura/Documents/Personal-Projects/finanzas`. Lee primero `CLAUDE.md`.

## 1. Contexto
`gh pr view <nº> --json title,body,headRefName,commits,files` y `gh pr diff <nº>`. Lee la tarea de
Jira (cloudId `f04e906b-0ec4-4966-b049-acd498427118`): el «Hecho cuando» es lo que el PR promete.
Lee los ficheros cambiados **enteros** y lo que los llama.

## 2. Qué revisar, por gravedad
1. **Reglas duras del proyecto** — bloquean siempre:
   - dinero calculado fuera de Rust (en `src/` o en Kotlin), o aritmética centavos×micros en SQL;
   - montos que no son centavos `i64` / tasas no en bps / fechas que no son `YYYY-MM-DD`;
   - escritura multi-statement fuera de `db.batch()`; consulta sin scoping por `user_id`;
   - esquema cambiado sin migración nueva (o editando una aplicada) o sin `docs/DATA_MODEL.md`;
   - string de UI fuera de `src/i18n/`, clave sin su par en `en.ts`, o `strings_i18n.xml` sin regenerar;
   - **cambio de producto en la web sin su equivalente en `android/`** (o al revés), o sin entrada
     en `docs/ANDROID_PARITY.md`;
   - service worker que cachea `/api/*`; hashing que no sea SubtleCrypto; wrangler ≠ 4.60.0.
2. **Corrección:** bugs reales con escenario concreto (entrada → resultado incorrecto). Atención a
   zonas horarias (Ciudad de México), redondeo de centavos, multi-moneda y fechas de corte.
3. **¿Cumple la tarea?** Cada criterio tiene evidencia en «Pruebas», en web y APK.
4. **Tests:** la lógica nueva tiene test, y el test fallaría sin el arreglo. Fórmulas con valores
   calculados a mano.
5. **Convenciones:** commits con `Refs: FIN-n` y Conventional, docs actualizados (ARCHITECTURE para
   RPC nuevos, DECISIONS si hay decisión). El PR **no** trae bump de versión ni changelog: eso es de
   `fin-release` (salvo que sea el propio commit `chore(release)` de esa skill).
6. Riesgos del stack: migración que rompe datos existentes en D1 de producción, límite de 10 ms de
   CPU del worker (free tier), cron, permisos/sesión en `/api/auth/*`, firma del APK.

Comprueba cada sospecha antes de reportarla (lee el código, corre el test). Lo que no puedas
comprobar no es un hallazgo. Los gustos de estilo que el formatter no exige tampoco.

## 3. Veredicto
**APROBADO** si no hay hallazgos de 1-4 y 6. **CAMBIOS** si hay al menos uno.
`gh pr review <nº> --comment --body-file <f>`:

    ## Revisión: APROBADO ✅   (o: CAMBIOS ❌)
    **Bloqueantes**
    1. `ruta:línea` — defecto — escenario — arreglo propuesto
    **Menores (no bloquean)**
    **Comprobado:** …

Copia el veredicto como comentario en la tarea de Jira.

## 4. Devuelve
`VEREDICTO: APROBADO` o `VEREDICTO: CAMBIOS` + bloqueantes numerados.
