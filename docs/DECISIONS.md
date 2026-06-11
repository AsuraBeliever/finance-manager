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
