# Finanzas — finanzas personales en la nube (Cloudflare Workers + D1 + PWA)

App de carteras, transacciones e inversiones. MXN principal, multi-moneda,
multiusuario (registro con código de invitación). El frontend es una PWA
(iPhone vía Safari → «Agregar a pantalla de inicio»); el escritorio es un
shell Tauri que carga la URL desplegada. UI en español (es-MX), código y
comentarios en inglés.

<!-- dev-flow:begin S2 -->
> Este archivo es el contrato de trabajo: se lee al inicio de cada sesión.
> El proceso de cada cambio está en «Flujo de desarrollo», más abajo.

## Reparto de roles

| Rol | Quién | Alcance |
|---|---|---|
| Responsable técnico | Claude | Arquitectura, código, ramas, versiones, commits, PRs, revisión, merge, releases, deploy, CI, testing (web + emulador), Jira y documentación |
| Cliente / Product owner | Alan (AsuraBeliever) | Prueba el producto, reporta qué falta y qué falla, decide prioridades de producto |

El cliente no gestiona git, Jira, el build ni el deploy. Si algo técnico requiere su
decisión, se le presenta como opciones de producto, no como detalles de implementación.
Cualquier cambio que pida entra por la skill `fin-flow`.
<!-- dev-flow:end S2 -->

## Comandos

```sh
npm run build                                # tsc + vite build → dist/ (el worker lo sirve)
cargo test --workspace                       # tests (fórmulas + parsers, nativos en finanzas-core)
cargo check -p finanzas-worker --target wasm32-unknown-unknown   # chequeo rápido del worker
cargo clippy --workspace && cargo fmt --all

# desarrollo local (app completa en http://localhost:8787)
cd worker && npx wrangler d1 migrations apply finanzas --local
cd worker && npx wrangler dev                # requiere npm run build previo; INVITE_CODE en worker/.dev.vars
                                             # wrangler está clavado a 4.60.0 en package.json: en 4.118.0
                                             # el dev local se arrastra (dashboard 8-16s) y workerd se muere
                                             # al cargar la app. No subir la versión sin probar esto.
npm run dev                                  # (opcional) Vite con HMR; /api se proxea a :8787

# producción
cd worker && npx wrangler deploy             # publica worker + dist/
cd worker && npx wrangler tail               # logs (cron, errores)
npm run tauri dev                            # shell de escritorio

# Android (app NATIVA Kotlin/Compose contra el mismo /api/rpc) — ver android/README.md
cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug
                                             # JDK 17-21 (el 26 no le sirve a AGP 8.7);
                                             # assembleRelease firma con $BROKE_KEYSTORE
```

## Arquitectura (resumen — detalle en docs/ARCHITECTURE.md)

- **Toda la lógica de dinero vive en Rust**: pura en `crates/finanzas-core`
  (calculadoras con `CalcContext`, parsers de mercado; tests nativos) y
  orquestación/SQL en `worker/src/handlers/`. El frontend NUNCA calcula
  dinero: solo renderiza lo que regresa `src/lib/api.ts` (RPC fetch) +
  TanStack Query.
- API RPC-style: `POST /api/rpc/<comando>` con cuerpos camelCase; auth en
  `/api/auth/*` (sesiones por cookie HttpOnly; PBKDF2 vía SubtleCrypto nativo,
  NUNCA un crate de hashing puro — límite de 10ms CPU del free tier).
- **D1 no tiene BEGIN/COMMIT**: toda escritura multi-statement va en
  `db.batch()` (transferencias = 2 filas con mismo `transfer_group_id`).
- Saldo de cartera = calculado (initial + Σ transacciones), nunca almacenado.
- Scoping multiusuario: `user_id` en wallets/investments/transaction_categories
  (NULL = seed); transactions/snapshots/movements por JOIN al padre; settings
  PK `(user_id, key)` con usuario 0 = cachés globales.
- Datos de mercado por cron trigger diario (Banxico, open.er-api, BONDDIA,
  CoinGecko) — ver `worker/src/market.rs`.
- Router: `createHashRouter` (funciona igual en web y Tauri).
- **Escritorio = web**: el shell Tauri carga la URL desplegada; `wrangler deploy`
  actualiza web + iPhone + escritorio a la vez (no se recompila el binario salvo
  cambios nativos en `src-tauri/`). Aviso de versión nueva in-app vía
  `registerType: "prompt"` + `src/features/update/UpdateBanner.tsx`.
- **Android es la excepción**: `android/` es una app NATIVA (Kotlin + Compose)
  que dibuja sus propias pantallas y consume el mismo `/api/rpc` con la misma
  cookie de sesión. El backend NO tiene endpoints propios para Android.
  Detalle en `android/README.md`.
- 🔴 **REGLA DE PARIDAD (no negociable, pedida por el usuario 2026-08-26):
  web y Android son la MISMA app.** Todo lo que existe en la web existe en el
  APK, y TODO cambio de producto de aquí en adelante se implementa en las dos
  superficies **en el mismo commit/branch**. Nunca dejar Android atrás «para
  después»: si una feature no se puede portar en el momento, no se mergea. Un
  release sin su cambio de Android no está terminado. Checklist de paridad y
  estado: `docs/ANDROID_PARITY.md` — actualizarlo en cada feature.
- En Android el dinero tampoco se calcula en el cliente: `Money.kt` sólo formatea
  centavos. Si aparece aritmética de dinero en Kotlin, va mal — eso es de
  `finanzas-core`.
- El service worker de la PWA JAMÁS cachea `/api/*`.
- No multiplicar centavos×micros en SQL (números D1 → JS f64): esa aritmética
  va en Rust con i128 intermedio.

## Convenciones

- **Dinero**: centavos enteros `i64`. **Tipos de cambio**: micros. **Tasas**: basis points. **Fechas de negocio**: TEXT `YYYY-MM-DD`.
- serde `rename_all = "camelCase"` en todo lo que cruza HTTP.
- Strings de UI SOLO en `src/i18n/` (bilingüe es/en); nada hardcodeado en componentes. `es.ts` es la forma canónica (`esDict`); al agregar una clave, agrégala también en `en.ts`. Los componentes importan `es` (un proxy al idioma activo) y usan `es.x`; el idioma se cambia en Ajustes (`src/i18n/store.ts`) y el router se remonta al cambiar. OJO: textos a nivel de módulo (fuera de un componente) quedan congelados al idioma inicial — defínelos dentro del componente.
- **Cambios de esquema**: solo vía migración nueva en `worker/migrations/*.sql` + actualizar `docs/DATA_MODEL.md`.
- **Calculadoras de inversión nuevas**: implementar `InvestmentCalculator` en finanzas-core + registry + tests + cargar su `CalcContext` en ambos loaders (worker y src-tauri) + form; guía en `docs/INVESTMENTS.md`.
- **Toda feature nueva se entrega en web Y en Android**, en el mismo branch, con
  su entrada en `docs/ANDROID_PARITY.md`. Ver la regla de paridad arriba.
- Git: conventional commits. TODO cambio de producto (cualquier `feat`, o una serie
  de commits relacionados) se desarrolla en branch `feat/<nombre>` y llega a `main`
  SOLO vía release: commit `chore(release)` (bump en `package.json` + entrada en
  `src/lib/changelog.ts`) + merge `--no-ff` + tag semver. Directo a `main` únicamente
  un `fix`/`chore`/`docs` trivial y aislado (un commit); un `feat` directo a `main`
  NUNCA. Solo se deploya un tag: si `main` tiene trabajo sin taguear, primero se
  corta el release. Regla de oro: `main` siempre = último release.
- Tests de fórmulas financieras obligatorios, con valores de referencia calculados a mano.

<!-- dev-flow:begin S3 -->
## Flujo de desarrollo (Claude es el dev)

Se adapta al modelo de git de arriba: **no hay `develop`**. La rama de trabajo es la
integración, se prueba entera antes del release y llega a `main` solo con él.

### Reglas del flujo

Se suman a las reglas de Arquitectura y Convenciones, que mandan sobre todo lo de aquí.

1. Todo cambio, por pequeño que sea, entra por `fin-flow` y tiene ticket en Jira FIN.
   También el trabajo que no es de producto (docs, chores, CI). Esto sustituye al «directo a
   `main`» de §Convenciones: un fix/chore/docs trivial también va por PR (`fin-merge` si no
   cambia el producto).
2. Nada se mergea a `main` sin el comentario **APROBADO** de `fin-review` posterior al último push.
   Única excepción: el commit `chore(release)` que `fin-release` empuja justo antes del merge
   (solo versión y changelog).
3. Un cambio de producto (`feat`/`fix`) solo se mergea dentro de `fin-release`, y solo si
   `fin-dev-test` dio **PASA** sobre el sha exacto de la rama. Todo cambio termina publicado
   en el mismo flujo (tag + deploy + APK), salvo que el cliente pida esperar.
4. Nunca se reescribe historia publicada (`main`, tags). Nunca `push --force` a ellas.
5. Nunca se abre un PR con pruebas en rojo ni se mergea con checks en rojo.
6. Árbol sucio de origen desconocido: se para y se pregunta. Nada de `stash`, `reset` ni
   `checkout --` sobre trabajo ajeno.

### Ramas

| Rama | Uso |
|---|---|
| `main` | Siempre = último release. Solo llega vía PR mergeado con merge commit. |
| `feat/fin-<n>-<slug>` | Feature nueva (lleva su release) |
| `fix/fin-<n>-<slug>` | Corrección de producto (lleva su release, PATCH) |
| `chore/fin-<n>-<slug>` · `docs/…` · `refactor/…` | Sin cambio visible: entran con `fin-merge`, sin release |

Slug en español, corto, como las ramas históricas (`feat/hora-cobro-automatico`).

### Commits

Conventional Commits; tipo y ámbito en inglés (`transactions`, `subscriptions`, `android`,
`worker`, `core`, `i18n`…), resumen en español, como en `git log`. Cada commit compila.
Nada de «WIP» en ramas que se empujan. Pie:

    Refs: FIN-<n>
    Co-Authored-By: Claude <modelo> <noreply@anthropic.com>

Gate antes de commitear: `cargo fmt --all`.

### PRs, revisión y merge

- Base `main`; título = Conventional Commit; cuerpo con «Qué cambia / Por qué / Cómo /
  Pruebas / Jira» (plantilla en `.github/pull_request_template.md`).
- GitHub no deja aprobar un PR propio: la aprobación es el comentario **APROBADO** de `fin-review`
  (subagente independiente).
- Merge con merge commit (`gh pr merge --merge`). Producto → lo hace `fin-release`, asunto
  `Merge <rama> — X.Y.Z` como los históricos; chore/docs → `fin-merge`, asunto
  `Merge <rama> (#<nº>)`, sin tag (sale con el siguiente release).
- **No hay CI en PRs todavía** ([FIN-4](https://alansethmanjarrez.atlassian.net/browse/FIN-4)):
  mientras tanto la puerta es el informe de `fin-test` + `fin-dev-test` con todo en verde.

### Versionado y releases

Semver en `package.json` (de ahí salen web, Tauri y `versionCode`/`versionName` del APK).
`feat` → MINOR, `fix` → PATCH, breaking → MAJOR (se confirma con el cliente). Release = commit
`chore(release): X.Y.Z — <resumen>` en la rama (bump + entrada bilingüe en `src/lib/changelog.ts`
+ `npm run gen:android-strings` para `android/app/src/main/assets/changelog.json`
+ `docs/ANDROID_PARITY.md`) → merge del PR → tag `vX.Y.Z` → `git push origin main vX.Y.Z` →
`npm run build` + `cd worker && npx wrangler deploy` → workflow «Release apps» del tag en verde
con el `.apk` adjunto. Antes de tagear: `git ls-remote --tags origin vX.Y.Z` vacío.

«`main` = último release» admite merges de `fin-merge` (chore/docs/ci) sin tag entre releases;
nunca un `feat`/`fix` sin tag.

- **Dev** = no hay entorno remoto: `wrangler dev` local (http://localhost:8787, D1 local
  migrada) con el build de la rama + APK debug contra él en el emulador.
- **Producción** = https://finanzas.aseth.workers.dev (web, iPhone y escritorio a la vez) +
  APK firmado en el GitHub Release del tag.

### Testing

Cada criterio del «Hecho cuando» del ticket acaba con una evidencia concreta; lo que no se
pueda comprobar se declara «no verificado», nunca se da por bueno.

- **Backend:** `cargo fmt --all -- --check`, `cargo clippy --workspace`, `cargo test --workspace`,
  `cargo check -p finanzas-worker --target wasm32-unknown-unknown`, `npm run build`,
  `cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew testDebugUnitTest assembleDebug`,
  y migraciones nuevas aplicadas con `npx wrangler d1 migrations apply finanzas --local`.
- **Uso real (web):** `wrangler dev` en segundo plano + Playwright (`node scripts/parity-shots.mjs
  <pantallas>`, cuenta `alan@test.mx` / `test1234`) o `curl` a `/api/rpc/<comando>`.
- **Uso real (Android):** APK debug con `-PbrokeApiBase=http://10.0.2.2:8787` en el emulador
  (`emulator-5554`), navegación con `adb shell uiautomator dump` + `input tap` y `screencap`,
  lado a lado con la captura web al mismo tamaño. `logcat` sin `FATAL EXCEPTION`.
- Cada captura se abre con Read y se mira. El celular del cliente no se toca salvo que lo pida.

### Jira

- Sitio: https://alansethmanjarrez.atlassian.net — proyecto **Finanzas**, clave `FIN`
- `cloudId`: `f04e906b-0ec4-4966-b049-acd498427118`
- Acceso: MCP de Atlassian. Si pide autenticación, el cliente la hace con `/mcp`.
- accountId del cliente: `70121:f22207c0-2104-4fb2-97e6-8f23667e6a63`

| Estado | id transición | Significa |
|---|---|---|
| Por hacer | `11` | Pendiente |
| En curso | `21` | Claude trabaja en ella. Con etiqueta `en-revision`: PR abierto, en revisión/prueba en dev |
| Listo | `31` | Publicada en un release (chore/docs: mergeada) |

- Tipos: `Historia` = lo que el cliente nota; `Tarea` = el resto; `Error` = fallo de producto.
- Épicas: `Mantenimiento continuo` (FIN-2) para bugs/chores/infra; una `Release vX.Y.Z` solo si
  se agrupan varias tareas en un release.
- Etiquetas: ámbitos (`web`, `android`, `backend`, `core`, `ci`, `docs`…), `en-revision`,
  `decision-cliente`.
- Responsable: sin asignar. Al cliente solo lo que necesita **su** decisión, con `decision-cliente`.
- Jira dice qué y en qué estado; el detalle técnico vive en `docs/`.

| # | Paso | Skill |
|---|---|---|
| 1 | Ticket con «Hecho cuando» | `fin-ticket` |
| 2 | Rama desde `main`, ticket En curso | `fin-branch` |
| 3 | Código + tests + commits (web **y** Android) | `fin-implement` |
| 4 | Pruebas backend + uso real, informe | `fin-test` |
| 5 | Push + PR contra `main`, etiqueta `en-revision` | `fin-pr` |
| 6 | Revisión independiente → APROBADO / CAMBIOS | `fin-review` |
| 7 | Prueba completa en dev (local) → PASA / FALLA | `fin-dev-test` |
| 8 | Release: bump + changelog + merge + tag + deploy + APK, ticket Listo | `fin-release` |
| 7' | (chore/docs) merge sin release, ticket Listo | `fin-merge` |
| — | «¿Cómo vamos?» | `fin-status` |

### Inicio de sesión

Al empezar: `project = FIN AND statusCategory != Done ORDER BY status, priority DESC`,
comentarios nuevos del cliente y `gh pr list --state open`. Si una sesión quedó a medias,
el estado del ticket, su etiqueta y la rama dicen en qué paso retomar.
<!-- dev-flow:end S3 -->

## Estado actual

- **v2.0.0 (feat/mobile)**: migración a la nube — PWA + Workers (workers-rs) +
  D1 multiusuario. La DB local `~/.local/share/com.aseth.finanzas/finanzas.db`
  es respaldo de solo lectura post-migración: NUNCA borrarla ni escribirla.
  Migración de datos: `scripts/migrate_to_d1.py` (checksums antes/después).
- Antes — v1.6.x escritorio: BONDDIA con precio oficial + serie histórica,
  criptomonedas (CoinGecko), catálogo de inversiones con tasas Banxico sin
  token, DateInput/ConfirmDialog propios (WebKitGTK), tipos de cambio
  automáticos. Historia completa en `docs/DECISIONS.md` y `docs/PLAN.md`.
- Trabajo futuro: ver `docs/ROADMAP.md`.

## Docs

`docs/PLAN.md` (plan + checklist) · `docs/ARCHITECTURE.md` (capas, catálogo de comandos RPC, dev local) · `docs/DATA_MODEL.md` (SQL canónico de D1) · `docs/INVESTMENTS.md` (fórmulas: Nu cajita ACT/360 compuesto diario, CETES ACT/360 + ISR) · `docs/ROADMAP.md` · `docs/DECISIONS.md` (ADRs) · `docs/ANDROID_PARITY.md` (paridad web↔APK) · `docs/ANDROID_PARITY_SWEEP.md` (plan del barrido visual pendiente).
