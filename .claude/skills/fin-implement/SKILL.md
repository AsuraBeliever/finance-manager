---
name: fin-implement
description: Paso 3 de fin-flow. Implementa una tarea de Jira FIN en su rama, en web y en Android, siguiendo el CLAUDE.md de Finanzas. También aplica los cambios que piden fin-review o un FALLA de fin-dev-test.
argument-hint: FIN-n [hallazgos de la revisión o de dev]
---

# fin-implement — el código

Tarea y contexto: $ARGUMENTS. Lee la tarea (`getJiraIssue`): el «Hecho cuando» es el contrato.

## Reglas del proyecto (de CLAUDE.md — bloquean siempre)
- **El dinero solo se calcula en Rust**: puro en `crates/finanzas-core`, orquestación/SQL en
  `worker/src/handlers/`. El frontend (`src/`) y Kotlin (`Money.kt`) solo formatean.
- Dinero en centavos `i64`, tipos de cambio en micros, tasas en basis points, fechas de negocio
  TEXT `YYYY-MM-DD`. Nada de centavos×micros en SQL (i128 en Rust).
- serde `rename_all = "camelCase"` en todo lo que cruza HTTP; API `POST /api/rpc/<comando>`.
- D1 no tiene BEGIN/COMMIT: escrituras multi-statement en `db.batch()`.
- Scoping multiusuario por `user_id` (o JOIN al padre) en toda consulta nueva.
- Esquema: solo con migración **nueva** en `worker/migrations/NNNN_*.sql` + `docs/DATA_MODEL.md`.
- Strings de UI solo en `src/i18n/` (`es.ts` canónico + `en.ts`), dentro de componentes; después
  `npm run gen:android-strings` y se commitean los `strings_i18n.xml`.
- **Paridad:** todo cambio de producto, en la web **y** en `android/` en esta misma rama, con su
  entrada en `docs/ANDROID_PARITY.md`. Si no se puede portar, no se sigue: se avisa a `fin-flow`.
- El service worker nunca cachea `/api/*`. PBKDF2 solo vía SubtleCrypto.
- Calculadora de inversión nueva: `InvestmentCalculator` + registry + tests + `CalcContext` en ambos
  loaders + form (`docs/INVESTMENTS.md`).
- Nunca tocar `~/.local/share/com.aseth.finanzas/finanzas.db`. No subir wrangler de 4.60.0.

## Cómo
1. Localiza la causa o el punto de entrada. Si es un bug, primero el test que lo reproduce, y míralo fallar.
2. El cambio más pequeño que cumpla el «Hecho cuando», imitando el estilo del fichero que tocas
   (código y comentarios en inglés).
3. Tests de unidad para toda lógica nueva o corregida, junto a los existentes. Fórmulas financieras
   con valores de referencia calculados a mano (y el cálculo en el comentario del test).
4. Android: misma pantalla, mismo comportamiento, mismo RPC; la web es el plano
   (`docs/ANDROID_PARITY.md` §Paridad visual).
5. Decisión de arquitectura → entrada nueva en `docs/DECISIONS.md`. Comando RPC nuevo → catálogo en
   `docs/ARCHITECTURE.md`.

## Compilar y formatear
    cargo fmt --all
    cargo clippy --workspace
    cargo check -p finanzas-worker --target wasm32-unknown-unknown
    npm run build
    cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug

## Commits
Conventional Commits; tipo y ámbito en inglés, resumen en español (`CLAUDE.md` §Commits). Cada
commit compila. Pie:

    <tipo>(<ámbito>): <resumen en español>

    <por qué, no qué>

    Refs: FIN-n
    Co-Authored-By: Claude <modelo> <noreply@anthropic.com>

Varios commits si son pasos lógicos distintos. Nada de «WIP». El bump de versión y el changelog
**no** van aquí: los pone `fin-release`.
