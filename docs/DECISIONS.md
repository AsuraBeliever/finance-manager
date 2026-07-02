# Decisiones de arquitectura (ADR-lite)

Una entrada por decisión. No se borran: si una decisión se revierte, se agrega una entrada nueva que la supersede.

## 2026-06-10 — rusqlite en Rust en lugar de tauri-plugin-sql

La lógica de negocio (agregación de saldos, atomicidad de transferencias, matemáticas de inversiones) vive en un solo lugar tipado. Con `tauri-plugin-sql` el frontend escribiría SQL crudo sin tipos, las transacciones multi-statement serían incómodas y las fórmulas se duplicarían en TS. Con rusqlite: modelos serde tipados, `BEGIN/COMMIT` reales, aritmética exacta `i64`, y fórmulas testeables con `cargo test`. El feature `bundled` compila SQLite dentro del binario y evita dependencias del sistema en Arch.

## 2026-06-10 — Motor de inversiones en Rust, no en TS

Misma razón: única fuente de verdad junto a los datos, control exacto de redondeo, testeable con cargo. El frontend nunca calcula dinero; recibe centavos y series listas.

## 2026-06-10 — Dinero en centavos, tipos de cambio en micros, tasas en basis points

Evita errores de punto flotante en almacenamiento y aritmética. Los flotantes solo aparecen transitoriamente dentro de las fórmulas de interés (inevitable por las potencias) y el resultado se redondea a centavos una sola vez.

## 2026-06-10 — Saldo calculado, nunca almacenado

`saldo = initial_balance_cents + Σ(transacciones con signo)`. Elimina toda posibilidad de desincronización entre saldo y historial; SQLite agrega miles de filas sin problema a esta escala.

## 2026-06-10 — Transferencias como 2 filas ligadas por transfer_group_id

Permite cross-currency naturalmente (cada pierna en la moneda de su cartera) y simplifica las consultas por cartera. La atomicidad la garantiza la transacción SQL al insertar/borrar.

## 2026-06-10 — Fechas de negocio date-only (YYYY-MM-DD)

Para finanzas personales la hora no aporta; simplifica agrupaciones por día/mes y evita problemas de zona horaria.

## 2026-06-10 — createHashRouter en lugar de history routing

La app de producción se sirve desde el protocolo custom de Tauri, donde no hay servidor que reescriba rutas. Hash routing funciona idéntico en dev y producción sin configuración extra.

## 2026-06-10 — TanStack Query sin estado global adicional

Todo el estado relevante es estado de servidor (la DB). Invalidación de queries tras cada mutación reemplaza cualquier store manual. Se reevaluará si aparece estado puramente de UI compartido.

## 2026-06-10 — Tipos de cambio automáticos vía open.er-api.com (no Banxico SIE)

El usuario pidió que el tipo de cambio se calcule solo. Banxico SIE es la fuente oficial del FIX pero requiere registrar un token y solo cubre pocas series; open.er-api.com es gratuito, sin llave, cubre 160+ monedas contra MXN en una sola petición y se actualiza a diario — suficiente para valuar carteras personales. La petición vive en Rust (reqwest, rustls) como el resto de la lógica de dinero: el quote llega como "unidades de CUR por 1 MXN" y se invierte a micros (`1e6 / quote`). Se consulta al arrancar la app (se omite si ya hay tasa 'api' con menos de 6 horas) y bajo demanda desde Ajustes; sin red se conserva la última tasa guardada. La edición manual permanece como override: la fila más reciente en `exchange_rates` gana, sin importar `source`.

## 2026-06-10 — Movimientos de inversión con valuación por posición

El usuario aporta a CETES/cajitas varias veces y también retira; un solo `principal` no lo modela. Se agregó `investment_movements` (deposit/withdrawal con fecha) y la valuación cambió a "por posición": cada monto crece con el factor de la calculadora desde su propia fecha, y un retiro resta su monto multiplicado por el mismo factor desde la fecha del retiro (deja de generar rendimiento exactamente ahí). El rendimiento se reporta contra el aportado neto (principal + aportaciones − retiros), capturando rendimiento realizado y no realizado. Para CETES se añadió el modo `reinvest` (posición rodante que capitaliza cada plazo, sin vencimiento) porque cetesdirecto con depósitos recurrentes reinvierte al vencer; el ISR en ese modo se aproxima prorrateado sobre el capital aportado. Las inversiones `manual` quedan fuera (su valor viene de snapshots). Limitación conocida y aceptada: una sola tasa para toda la posición — las tasas históricas por periodo siguen en el roadmap.

## 2026-06-10 — Tasas de CETES/BONDDIA desde Banxico SIE (con token del usuario)

El usuario no quiere capturar tasas a mano. La API SIE de Banxico publica la tasa de la última subasta de CETES por plazo (SF43936/SF43939/SF43942/SF43945 para 28/91/182/364 días) y la tasa objetivo (SF61745), que sirve de referencia para BONDDIA porque el fondo sigue la tasa de fondeo gubernamental (≈ tasa objetivo). SIE requiere token gratuito: se guarda en la tabla `settings` (key `banxico_token`) y se captura en Ajustes. El formulario de inversiones ofrece "usar tasa de Banxico" para cetes (según plazo) y fixed_rate (objetivo). No se eligió scrapear cetesdirecto: no tiene API pública estable.

## 2026-06-10 — Widgets nativos de WebKitGTK reemplazados (date picker y confirm)

El `<input type="date">` de WebKitGTK congela la ventana hasta que pierde el foco, y `window.confirm` muestra un diálogo del sistema con encabezado "JavaScript - tauri://localhost" ajeno al tema. Se reemplazaron por componentes propios: `DateInput` (popover de calendario con date-fns, lunes primero, soporte de fecha mínima) y `ConfirmDialog` (modal del tema). Regla: no usar widgets nativos del navegador para interacciones — siempre componentes del design system.

## 2026-06-10 — Tasas de Banxico sin token (supersede parcialmente la decisión anterior)

El usuario no quería ni siquiera pegar un token. Se descubrió que el endpoint que alimenta las gráficas del propio sitio de Banxico, `SieInternet/consultaSerieGrafica.do?s=<serie>,<cuadro>,<n>&versionSerie=LA-MAS-RECIENTE`, regresa JSON `{titulo, valores: [[fecha, valor]]}` sin autenticación (centinela -989898.0 = sin dato). Contextos verificados: SF43936,CF107,5 · SF43939,CF107,9 · SF43942,CF107,13 · SF43945,CF107,17 · SF61745,CF101,2. Ahora es la fuente primaria; la API SIE oficial con token quedó como fallback opcional (el endpoint público es interno del sitio y podría cambiar sin aviso — por eso no se eliminó el camino con token). La generación automática del token se descartó: requiere formulario web.

## 2026-06-10 — Catálogo de inversiones con tasas precargadas

Al crear una inversión, el primer paso es un catálogo de productos conocidos (CETES 28/91/182/364, BONDDIA, Nu Cajita, tasa fija, manual) con la tasa actual ya puesta cuando existe fuente pública (Banxico vía el endpoint sin token). Elegir un producto precarga calculadora, plazo, tasa y demás parámetros. Nu Cajita va sin tasa automática: nu.com.mx es una SPA que carga la tasa por JS y Nu no publica API — el usuario la captura de la app de Nu. El catálogo degrada con gracia: si Banxico no responde, los productos aparecen sin tasa y se captura a mano. Los nombres/descripciones viven en el i18n del frontend; el backend solo regresa ids, params y tasas.

## 2026-06-10 — BONDDIA con serie histórica de tasas (corrige subvaluación)

El usuario comparó contra cetesdirecto: la app decía $6,564.73 y su cuenta real $6,824.75. Causa: valuar todo el historial con la tasa actual (6.5%) cuando sus depósitos de 2023-2025 rindieron a tasas de 10-11%. La calculadora `bonddia` compone día a día sobre la serie histórica de la tasa objetivo (cacheada en `rate_history` desde el endpoint público de Banxico, que regresa la serie completa). `fixed_rate` queda para instrumentos de tasa realmente fija.

## 2026-06-10 — Criptomonedas como inversión por cantidad + precio CoinGecko

Las cripto no caben en el modelo de centavos de las carteras (BTC necesita 8 decimales). Se modelan como inversión: params guardan `symbol` y `quantity_e8` (entero exacto), el valor sale del último precio en MXN cacheado de CoinGecko (API gratuita sin llave, `simple/price`), y los movimientos registran el MXN de compras/ventas para el rendimiento. Se muestra también el equivalente en USD vía el tipo de cambio ya automático.

## 2026-06-10 — Ajustes sin configuración manual de mercado

Se eliminaron la edición manual de tipos de cambio y el token de Banxico de Ajustes: todo dato de mercado (fx, tasas, precios cripto) se obtiene y refresca solo. Menos superficie de UI, cero configuración.

## 2026-06-10 — Field sin <label> (bug de WebKitGTK)

Clic en "elegir mes y año" del calendario no hacía nada en la app (en Chromium sí funcionaba). WebKitGTK re-despacha clics dentro de un <label> a su primer descendiente etiquetable — el botón que abre/cierra el popover — anulando el cambio de vista. `Field` ahora envuelve en <div>+<span>; regla: no anidar controles compuestos en labels implícitos.

## 2026-06-11 — BONDDIA anclado al precio oficial del título (modo exacto)

La simulación con tasa histórica + spread queda a ±centavos del valor real porque el fondo invierte en títulos enteros y deja remanentes sin rendimiento — imposible de replicar al centavo con cualquier fórmula. Solución definitiva: cetesdirecto publica el precio oficial diario del título (bonddia.html; requiere User-Agent de navegador). Si la inversión guarda `titulos` (y opcionalmente `remanentes_cents`), el valor = títulos × precio + remanentes — idéntico a cetesdirecto por construcción, sin desfase. El precio se cachea en settings ('bonddia_price') y se refresca al arrancar. Sin títulos capturados (o sin precio en caché) se usa la estimación histórica como antes. El usuario actualiza títulos al comprar/vender (los ve en su app).

## 2026-06-12 — App en la nube: PWA + Cloudflare Workers (workers-rs) + D1

El usuario quiere ver la app en su iPhone «cuando y donde quiera» con sistema de cuentas. Decisiones encadenadas:

- **PWA instalable, no app nativa iOS**: compilar Tauri para iOS exige una Mac con Xcode (la máquina del usuario es Linux) y cuenta Apple Developer de paga. La PWA se instala desde Safari («Agregar a pantalla de inicio») con ícono y pantalla completa. iOS ignora los íconos del manifest: se requieren `apple-touch-icon` + metas `apple-mobile-web-app-*` en index.html.
- **Hosting en Cloudflare Workers + D1 (free tier)** sobre Cloudflare Tunnel/VPS: el usuario pidió gratis y eligió disponibilidad total (la PC apagada no afecta) a cambio de reescribir la capa de servidor/DB. Límites verificados: 100k req/día, 10ms CPU/request, WASM ≤ 3MB gzip, D1 5GB y 5M lecturas/día — sobrado para uso personal/familiar.
- **Backend sigue en Rust** vía workers-rs compilado a WASM (no TypeScript): la convención «toda la lógica de dinero vive en Rust» se mantiene. La lógica pura se extrajo a `crates/finanzas-core` (sin deps de almacenamiento): el trait `InvestmentCalculator` ahora recibe un `CalcContext` precargado en lugar de `&Connection`, y los parsers de mercado (Banxico/BONDDIA/fx) son funciones puras compartidas. Los tests corren nativos con `cargo test --workspace`.
- **API RPC-style** (`POST /api/rpc/<comando>` con los mismos cuerpos camelCase que usaba el puente Tauri): `src/lib/api.ts` solo cambió de transporte; cero cambios en componentes.
- **D1 no tiene BEGIN/COMMIT interactivo**: toda escritura multi-statement (transferencias de 2 filas, borrado de cartera en cascada) usa `db.batch()`, que sí es transaccional/atómico.
- **Multiusuario real con código de invitación**: `users` + `user_id` en wallets/investments/transaction_categories (NULL = seed del sistema); transactions/snapshots/movements se escopan por JOIN al padre. `settings` pasa a PK `(user_id, key)`; el usuario sistema (id 0) guarda cachés globales como `bonddia_price`. El registro exige el secret `INVITE_CODE` para que una URL pública no acumule cuentas ajenas.
- **Contraseñas con PBKDF2-SHA256 vía SubtleCrypto nativo** (no crate Rust puro: quemaría los 10ms de CPU del free tier; workerd además limita PBKDF2 a 100k iteraciones). Formato PHC con iteraciones por hash, tunables sin invalidar hashes. Sesiones: cookie HttpOnly/Secure/SameSite=Lax; D1 guarda solo el SHA-256 del token; expiración deslizante de 30 días; check de Origin en mutaciones.
- **Refresco de mercado por cron trigger diario** (07:00 UTC ≈ 01:00 CDMX) en lugar del fetch al arrancar; mismos proveedores, fallos silenciosos (`wrangler tail` los muestra).
- **El escritorio queda como shell**: la ventana Tauri carga la URL desplegada; `finanzas.db` local se migró una sola vez (scripts/migrate_to_d1.py, checksums verificados) y se conserva como respaldo de solo lectura.

## 2026-06-12 — Cuenta: dispositivos con sesión y cambio de contraseña

`sessions` ganó `user_agent` y `last_seen_at` (refrescado con throttle de ~1h junto a la expiración deslizante, para no escribir en cada request). Endpoints: `sessions` (lista con `current`), `revoke_session` (la actual no se puede revocar ahí — eso es logout), `revoke_other_sessions` y `change_password` (exige la contraseña actual y revoca todas las demás sesiones — higiene estándar tras cambiar credenciales). El User-Agent se parsea en el frontend con un mapeo mínimo (iPhone/Android/Windows/Mac/Linux × navegador); el shell de escritorio (WebKitGTK) se reporta como «Linux · App de escritorio».

## 2026-06-12 — Offline: caché persistida + outbox append-only (no local-first)

El usuario quería usar la PWA sin internet. Se descartó replicar la base al dispositivo (local-first con sync bidireccional): enorme, frágil y contradice que todo el cálculo de dinero vive en el servidor. En su lugar, dos piezas deliberadamente simples: (1) la caché de TanStack Query se persiste en localStorage — sin red la app abre con los últimos datos y un banner visible; el service worker sigue sin cachear `/api/*` (la regla prohíbe datos financieros viejos servidos en silencio, no un snapshot etiquetado); (2) una cola outbox SOLO para capturas (`add_income`/`add_expense`/`add_transfer`) — al ser inserciones puras no hay conflictos posibles; cada item lleva un `clientId` y el servidor es idempotente sobre `transactions.client_id` (índice único parcial), así que un reenvío tras una respuesta perdida no duplica jamás. Los pendientes se muestran aparte de la lista real y no tocan saldos hasta sincronizarse. Ediciones/borrados offline quedaron explícitamente fuera (requerirían resolución de conflictos real).

## 2026-06-13 — Login con Google (OAuth server-side, abierto)

El usuario pidió «Continuar con Google» además de email+contraseña. Se implementó OAuth 2.0 Authorization Code en el Worker (`/api/auth/google/{start,callback}`): `start` setea una cookie `oauth_state` (CSRF) y redirige a Google; `callback` valida el state, intercambia el code por el `id_token` directamente contra el token endpoint de Google usando el client_secret. Como el token llega por TLS directo de ese endpoint, **no se verifica la firma del JWT** (guía oficial de Google para OIDC server-side) — solo se decodifica el payload para `sub`/`email`/`email_verified`. El usuario eligió **Google abierto** (sin código de invitación): un email verificado desconocido crea cuenta; un email ya existente vincula Google a la cuenta de contraseña (seguro porque Google verifica el correo). `users.google_sub` (UNIQUE parcial); los usuarios solo-Google guardan `password_hash = '!'` (sentinela; `verify_password` ya lo rechaza). `GOOGLE_CLIENT_ID` como var y `GOOGLE_CLIENT_SECRET` como secret; el `redirect_uri` se deriva del host de la petición para servir igual en prod y localhost. El registro por email+contraseña conserva su gate de invitación.

## 2026-06-13 — Escritorio = web (un deploy actualiza todo) + aviso de actualización in-app

El usuario notó que su app de escritorio «no estaba al día» y pidió como regla que todo cambio quede en escritorio y web, con detección de actualizaciones desde la propia app (sin comandos). Aclaración clave: desde v2.0.0 el escritorio es un shell Tauri que carga la URL desplegada, así que **no hay dos versiones** — `wrangler deploy` actualiza web, PWA de iPhone y escritorio a la vez; el «no estaba al día» era caché del service worker/WebView, no falta de recompilar (el binario Tauri solo se recompila ante cambios nativos de src-tauri/, que está dormido). Para el aviso: la PWA pasó de `registerType: 'autoUpdate'` a `'prompt'` y `src/features/update/UpdateBanner.tsx` (hook `useRegisterSW` de vite-plugin-pwa) muestra «Hay una nueva versión — Actualizar»; el botón activa el SW nuevo y recarga. Se re-chequea cada hora y al enfocar para que el shell de escritorio (siempre abierto) note versiones sin recargar a mano. Verificado E2E: desplegar una versión nueva hace aparecer la barra sola.

## 2026-06-13 — La sesión se revalida al cargar (refetchOnMount), no staleTime Infinity

Bug encontrado al integrar Google: tras el redirect de OAuth la app caía en login aun con sesión válida. Causa: la query `["me"]` tenía `staleTime: Infinity` y se persiste (offline v2.1.0); estando en login se guardaba `me = null` y la app nunca re-consultaba `/me` (ni con F5). El login con contraseña se salvaba porque escribe el usuario en caché por JS (`setQueryData`), pero OAuth hace un redirect completo sin continuidad JS. Fix: `refetchOnMount: 'always'` — siempre revalida contra el servidor al cargar; la caché persistida sigue dando render instantáneo offline y se conserva si la red falla. (También se corrigió en google.rs emitir un solo `Set-Cookie` por redirect: Cloudflare une varios en un header inválido.)

## 2026-07-01 — Tarjetas de crédito: deuda = saldo negativo, MSI como cargos del cron

El usuario pidió tarjetas de crédito «útiles» (no usa tarjetas, así que la mecánica MX se diseñó aquí: corte, ~20 días para pagar sin intereses, pago mínimo vs saldo al corte, utilización, MSI). Decisiones: (1) **sin tipo nuevo de cartera** — `credit_cut_day` es el discriminador; los gastos vuelven el saldo negativo (deuda = −saldo) y pagar la tarjeta es una transferencia normal, así el modelo de saldos calculados no cambia. (2) **Todo lo derivado es puro** en `finanzas-core::credit` (cortes con clamp a fin de mes, fecha límite, calendario MSI) y `get_credit_card_summary` solo ensambla. (3) **MSI no es una transacción**: el cron diario postea un gasto por mensualidad en cada corte (`client_id = msi:<plan>:<n>`, mismo esquema idempotente que wallet_yield; categoría reservada «Meses sin intereses» como la de Metas), de modo que la deuda refleja lo facturado —igual que el estado de cuenta— y lo no facturado resta crédito disponible y suma a la utilización, que es como lo miden los bancos. Al crear un plan con fecha pasada, las mensualidades ya vencidas se postean de inmediato. Verificado E2E local (números a mano + capturas claro/oscuro).
