# Paridad web ↔ Android

**Regla (no negociable):** web y Android son la misma app. Todo cambio de
producto se implementa en las dos superficies **en el mismo branch**, y este
archivo se actualiza en el mismo commit. Si una feature no se puede portar en el
momento, no se mergea.

Cuando las dos difieren en un detalle no especificado, **la web es la
implementación de referencia**.

Los textos de Android **se generan** desde `src/i18n/` con
`npm run gen:android-strings` → `android/app/src/main/res/values*/strings_i18n.xml`,
y el changelog desde `src/lib/changelog.ts` → `android/app/src/main/assets/changelog.json`.
Nunca escribas una cadena a mano en un composable: agrégala en `es.ts` + `en.ts`,
regenera y commitea. El generador falla si las dos lenguas no tienen las mismas
llaves.

## Paridad visual: la web es el plano, no la inspiración

No basta con que la feature exista: **el APK tiene que verse igual que la web a
ancho de teléfono.** El usuario usa las dos con la misma cuenta y no debe notar
que cambió de superficie.

Cómo se verifica (es la única forma que ha funcionado):

1. `cd worker && npx wrangler dev --ip 0.0.0.0` y compilar el APK con
   `-PbrokeApiBase=http://<ip-lan>:8787`, para que las dos vean los mismos datos.
2. Capturar la web con Playwright al **tamaño exacto del teléfono** (el S25U de
   pruebas reporta 720×1560 con densidad 280 → viewport 411×891 y
   `deviceScaleFactor` 1.75), y el APK con `adb exec-out screencap -p`.
3. Pegar las dos imágenes lado a lado y comparar. Las diferencias de verdad
   (una cifra distinta, un control que falta, otro glifo) sólo se ven así.

Cosas que salieron de hacer esto y conviene no repetir:

- **Los íconos de Material no son los de Lucide.** Son primos, no el mismo
  dibujo. Los glifos que usa la web viven en `ui/components/LucideIcons.kt`.
- **Nada de controles con cara de Material**: el `PickerField` es la caja del
  `<select>` de la web con su etiqueta encima, no un `OutlinedTextField` con
  etiqueta flotante.
- **Los nombres semilla se traducen** con `seedName()` en toda pantalla que los
  muestre; sin eso salen en español dentro de la app en inglés.
- **Las gráficas también se copian, no se reinventan.** recharts trae decisiones
  que se notan de inmediato si Android no las repite: la escala vertical arranca
  en cero (dominio `[0, 'auto']`), los cortes del eje se redondean al medio de su
  magnitud (23.5k → 25k, 27.4k → 30k) y la rejilla es punteada `3 3`. Están en
  `components/SimCharts.kt`.
- **Cambiar de pestaña no vuelve a cargar.** La web guarda cada respuesta en
  TanStack Query y repinta al instante mientras revalida; en Compose la pantalla
  se destruye al salir de su pestaña, así que sin un caché propio cada regreso
  era un spinner de dos segundos. Eso es `data/QueryCache.kt` + `loadSynced`.
  Toda lectura nueva va por ahí, con su propia llave.
- **Un botón que se encoge no se encoge más allá de su palabra más larga.** En
  flexbox eso es gratis (`min-content`); en Compose hay que pedirlo con
  `Modifier.width(IntrinsicSize.Min)`. Repartir el ancho con fracciones de
  `weight` lo aparenta hasta que una traducción no cabe y el texto sale cortado.
- Medidas compartidas: tarjetas `rounded-2xl` (16 dp), márgenes de página
  16 dp, botón primario `rounded-lg` px16/py8 sobre `accentDim`, encabezado con
  regla de 4×28 y título de 30 sp cuyas acciones **envuelven**.
- **El margen de página es 16 dp en TODAS las pantallas.** Media docena se
  habían quedado en 20 dp, que a ojo no se nota pero deja cada tarjeta 8 dp más
  angosta que en la web — suficiente para que un nombre de suscripción acabara
  en «Spotify T…». La forma de detectarlo es medir el borde de la tarjeta en las
  dos capturas: tiene que caer en el mismo pixel.
- **El marco puede coincidir y el interior no.** La tarjeta de patrimonio caía
  en el pixel exacto por fuera y por dentro tenía 20 dp de relleno donde la web
  usa `p-6` (24), la cifra a 40 sp donde la web usa `text-4xl` (36) y el
  interletrado del eyebrow en dos etiquetas que la web deja casi sin tracking.
  Medir el renglón, no el borde: la altura y la posición de cada bloque de
  texto dentro de la tarjeta.
- **El relleno de tarjeta no es uno solo.** La web mezcla `p-4`, `p-5` y `p-6`;
  `GlassCard` trae 20 dp (el `p-5` de la mayoría) y las excepciones lo pasan.
- **La manija de arrastre flota en la esquina, no ocupa lugar.** En la web es
  `absolute right-2.5 top-2.5` sobre la tarjeta, y el encabezado deja esa
  esquina libre con `pr-7`. Meterla en el renglón del título subía 9 px todo lo
  de abajo, en cada widget del panel. El encabezado también trae siempre su
  `mb-4`; no lo pone cada widget por su cuenta.
- **El interlineado de Compose no es el del navegador.** Con el `lineHeight` de
  Material, una etiqueta chica se sienta ~8 px más abajo que en la web, y ese
  desfase se hereda hacia abajo. Donde importe, fijarlo.
- **Lo que no cabe, la web lo encoge; no lo corta.** El centro del anillo de una
  meta pasa por `FitText`: si «$2,000.00» no entra en el hueco, baja de tamaño.
  Con `maxLines = 1` y sin más, Compose lo cortaba en «$2,000.».
- **Nunca anidar `verticalScroll` dentro de una hoja que ya desplaza.** Compose
  mide el de adentro con altura infinita y lanza en tiempo de ejecución: eso
  tumbaba la app entera al abrir «Novedades», que es justo lo primero que se ve
  después de actualizar.
- **Una captura tomada demasiado pronto miente.** El `ResponsiveContainer` de
  recharts dibuja la gráfica con el tamaño que midió, y si se le fotografía a
  media maquetación sale aplastada — una dona completa parecía media dona, y
  «arreglarla» habría roto la que estaba bien. Ante una diferencia de forma,
  volver a capturar antes de creerla.

**Estado del barrido:** cerrado. Se comparó pantalla por pantalla, en oscuro y
en claro, con capturas medidas; el método y el orden que se siguió quedan en
[`ANDROID_PARITY_SWEEP.md`](ANDROID_PARITY_SWEEP.md) por si hay que repetirlo.

## Lo que el barrido visual no alcanzó a ver

Aquel barrido comparaba **pantallas**, y por eso dio todo por bueno: lo que se
le escapó fueron controles que no estaban y acciones que no existían, no
pixeles. Un segundo barrido **funcional** (2026-09-20) los encontró cruzando
tres cosas contra el código:

1. Los 77 comandos de `worker/src/rpc.rs` contra los que llama cada cliente.
2. Los campos que cada formulario manda en el cuerpo del comando.
3. Las 625 llaves de `strings_i18n.xml` contra las que el Kotlin referencia de
   verdad (`grep -rhoE 'R\.string\.[a-z0-9_]+'`). Eran 128 sin usar; quitando
   las legítimamente web-only (`install_*`, los `aria-label`) y las muertas en
   el propio diccionario, cada una de las que quedaba era un hueco.

**Esa tercera comprobación es la que conviene repetir**, y es barata. Una llave
generada que nadie usa es, casi siempre, algo que la web dice y el APK no.

Lo peor que salió no era una feature faltante sino **pérdida de datos**:
`update_wallet` reescribe los cuatro campos de tarjeta en cada guardado, y el
APK no los mandaba, así que renombrar una tarjeta desde el teléfono le borraba
el día de corte, el límite y la anualidad — y con el corte en NULL el panel de
la tarjeta desaparecía. La regla que sale de ahí: **un comando que reescribe
todo obliga al cliente a mandar todo**, incluso lo que esa pantalla no edita.

Dos cosas más que sólo se ven probando, no mirando:

- **El diálogo de un formulario necesita tope de altura.** La web le pone
  `max-h-[90dvh]` y el cuerpo desplaza por dentro; el `Column` de Compose no
  tenía tope, así que en cuanto el formulario creció (el de tarjeta) el par
  Cancelar/Guardar quedó por debajo de la pantalla, inalcanzable por mucho que
  se desplazara. Está en `FormSheet`.
- **El generador hay que correrlo en cada release.** `changelog.json` venía sin
  la entrada de 2.39.1 porque aquel release tocó `src/lib/changelog.ts` y no
  regeneró el asset: «Novedades» nunca mostró esa versión en el teléfono.

## Tercer barrido (2026-09-21): medir, no mirar

Los dos barridos anteriores cerraron en «se ve igual». Este se hizo midiendo
cada pantalla con las dos superficies sobre **la misma cuenta** —el APK del
emulador apuntando a `10.0.2.2:8787`, y Playwright con la cookie de sesión que
el APK guarda en `files/datastore/broke_session.preferences_pb`— y a la métrica
exacta del emulador (448×997 dp @3× = 1344×2991). Salieron 17 diferencias que
el ojo no había cazado. Lo que conviene recordar:

- **`Typography()` sin `bodySmall` ni `labelMedium` deja Roboto.** Material
  rellena lo que no declares, y `FieldLabel`/`FieldHint` los usan: todas las
  etiquetas y pistas de todos los formularios salían en otra fuente y con la
  tracking de Material. Si se agrega un rol tipográfico, decláralo en
  `BrokeTypography`, no sólo en `rememberBrokeTypography`.
- **En un teléfono los controles de la web miden 16 px, no 14.** `index.css`
  los sube con `@media (pointer: coarse)` para que Safari no haga zoom al
  enfocar. Copiar `text-sm` dejaba cada valor 14% más chico que en el navegador.
  La excepción es la caja de fecha, que es un `<button>` y sí se queda en 14.
- **`enabled = false` no significa «no se escribe», significa «apagado».** La
  fecha y la hora se veían como placeholder por eso; lo que hacía falta era
  `readOnly` y el color del texto puesto a mano.
- **Una barra de recharts mide su banda, no un ancho fijo.** La banda es el
  área de trazo entre el número de cubos, y `barCategoryGap` deja el 10% libre
  **a cada lado**. Y el área de trazo no llega a los bordes de la tarjeta:
  `<YAxis width={40}>` más el `margin` de 5 por lado. Con 5 dp fijos el APK
  dibujaba pelos donde la web dibujaba columnas.
- **Un helper compartido no es el que usan las pantallas.** `ConfirmDialog`
  estaba bien copiado y casi nadie lo llamaba: había 17 `AlertDialog` sueltos.
  Al cambiar un componente de chrome, hay que barrer quién lo usa de verdad.
- **`SpaceBetween` no reserva el `gap`.** El encabezado de la web envuelve
  cuando título + 12 px + acciones no caben; un `FlowRow` con `SpaceBetween`
  sólo mide los dos bloques, así que Movimientos quedaba en un renglón aquí y
  en dos allá. El truco es llevar el hueco como padding del primer bloque.
- **Los estados vacíos son una tarjeta, no un par de textos**, y hay que
  comprobar que existan: el detalle de cartera sin movimientos no dibujaba
  nada, porque el `if (isNotEmpty)` no tenía `else`.
- **`rounded-sm` de Tailwind v4 son 4 px**, no 2. Toda la escala está corrida
  respecto de v3.
- **El signo de un delta se decide en `>= 0`, no en `> 0`.** Una ganancia de
  cero se escribe `+$0.00` en la web.
- **Un color copiado puede estar copiado al revés.** En el detalle de inversión
  el «Rendimiento» iba en acento y la línea de proyección en verde; el APK los
  tenía intercambiados. Comparar valores RGB, no impresiones.
- **El tema era el único ajuste de cuenta que el teléfono no compartía.** La
  web hace `setSetting("theme", …)` e hidrata al entrar; ahora `AppearanceSync`
  también.

## Cuarto barrido (2026-09-22): paridad de métrica

El tercero cerró «sin diferencias que se noten». Este fue a por las que **no**
se notan: se sacó del navegador el estilo calculado de cada rol de texto
(`getComputedStyle` a ancho de teléfono) y se comparó contra el ancho que
predicen los propios binarios de fuente con fontTools. Lo que salió:

- **La fuente es la misma** — Hanken Grotesk 3.013 y Sora 2.000, `upm` 1000,
  las dos variables. Google sirve un solo archivo por familia para todos los
  pesos, y es el que está en `res/font/`. Cualquier diferencia de ancho, por
  tanto, no es la fuente.
- **`tabular-nums`.** La web lo escribe en 64 lugares — toda cifra que imprime.
  Las cifras tabulares de Sora son bastante más anchas que las
  proporcionales: el patrimonio salía 50 px corto. Va en `fontFeatureSettings
  = "tnum"`, y como sólo cambia dígitos es inocuo donde no los hay. El helper
  es `TextStyle.tabular()`; se aplica donde el texto viene de `formatMoney`,
  `formatDelta` o `maskIfHidden`, que es exactamente donde la web lo pone.
- **Tailwind trae un `line-height` con cada `text-*`, y Compose no.** Peor:
  `.copy(fontSize = …)` **no** reescala el `lineHeight`, así que un rol usado
  a otro tamaño lo arrastra mal. Están todos anotados en `Type.kt` con el
  número que reporta el navegador.
- **El título de página es `text-[1.9rem]`, o sea 30.4 px, no 30.**
- **`tracking-tight` (−0.025em) va en varios títulos**, y Android **redondea el
  letter-spacing a pixel entero**: a 30.4 sp los −2.28 px por hueco caían a −3
  y el título quedaba ~5 px más apretado que en flexbox. En un encabezado que
  está a dos pixeles de envolver eso decide si son una línea o dos. `TrackingTight`
  lleva el valor que aterriza donde el navegador **después** del redondeo.
- **Las micro-etiquetas no son todas del mismo peso.** `.eyebrow` es 500; las
  `uppercase tracking-wide` sueltas («AT THE END», «PERIOD START») son 400. A
  11 px el peso es toda la diferencia: se veía 24% más tinta.
- **El motivo de la tarjeta lleva `strokeWidth={1.25}`**, no el 2 de Lucide, y
  desborda 8 px a la derecha (`-right-2`). Con el trazo por defecto y 2 dp de
  desborde el mismo glifo de 150 px se leía como uno más grande y más gordo.

**Lo que queda, y no es de la app:** el rasterizador. Chromium pone ~10-15%
más tinta que Skia en texto claro sobre fondo oscuro, así que la caja de tinta
medida sale 2-3% más ancha en el navegador aunque el trazado sea el mismo —
mismo glifo, misma posición de arranque, mismo avance. Se comprueba contando
pixeles encendidos de la misma cadena: el ancho de caja difiere, la densidad
no. No hay ajuste de la app que lo iguale.

Diferencias conocidas que se dejaron a propósito:

- En **Apariencia**, «Se reescala a 128 px…» cae al lado del botón en el APK y
  debajo en la web. Misma regla de acomodo (`flex-wrap` ↔ `FlowRow`); difieren
  por unos pixeles de ancho de glifo justo en el punto de corte.
- El **desglose por categoría** se desplaza por dentro en la web cuando la lista
  es muy larga; en el APK la tarjeta crece. Para cualquier lista que quepa en
  pantalla se ven igual, y una columna desplazable ahí dentro es justo lo que
  tumba la app (ver arriba).
- **Las pistas (`FieldHint`) van ~4 px CSS más arriba que en la web**, que les
  da `mt-1`. El desfase se acumula dentro de un bloque largo (unos 18 px CSS al
  final del de tarjeta de crédito). Está así en **todos** los formularios del
  APK desde el primer barrido; corregirlo es una línea en `FieldHint`, pero
  mueve las 18 pantallas que ya se firmaron, así que se deja hasta que haya
  ganas de volver a medirlas todas.
- **Los pips vacíos de un plan MSI casi no se ven**, porque el hueco y el fondo
  del renglón son los dos `surface-overlay`. Es un rasgo de la web, reproducido
  a propósito: se comprobó con captura lado a lado.

Leyenda: ✅ portado · 🟡 parcial · ⬜ pendiente

## Sesión

| Web | Android | Estado |
|---|---|---|
| Login correo + contraseña | `ui/auth/LoginScreen` | ✅ |
| Registro (alta de cuenta) | `ui/auth/LoginScreen` | ✅ |
| Login con Google | `ui/auth/GoogleSignIn` + `/api/auth/google/token` | ✅ |
| Sesión persistente (cookie 30 días) | `SessionCookieJar` | ✅ |
| Cerrar sesión | `ui/settings` | ✅ |
| Cambiar contraseña | `ui/settings/ChangePasswordScreen` | ✅ |
| Dispositivos / revocar sesiones | `ui/settings` | ✅ |

## Panel (dashboard)

| Web | Android | Estado |
|---|---|---|
| Patrimonio + efectivo + inversiones | `ui/dashboard` | ✅ |
| Subtotal por moneda bajo el patrimonio (>1 divisa) | `ui/dashboard` (`NetWorthCard`) | ✅ marca las que no tienen tipo de cambio |
| Estado vacío (sin carteras ni inversiones) | `ui/dashboard` | ✅ |
| Selector de periodo (`PeriodPicker`) | `components/PeriodPicker` | ✅ |
| Lista de carteras con saldo | `ui/dashboard` | ✅ |
| Donas «por cartera» y «por inversión» | `ui/dashboard/Widgets` | ✅ |
| Aviso de tipos de cambio faltantes | `ui/dashboard` | ✅ |
| Gráfica de flujo (`FlowChart`, `getSpendingTrends`) | `ui/dashboard` | ✅ |
| Widget «ingresos vs gastos» del periodo (`FlowRangeWidget`) | `ui/dashboard` (`FlowRangeCard`) | ✅ |
| Widget desglose por categoría (dona gasto + ingreso) | `ui/dashboard/Widgets` + `CategoryDetailDialog` | ✅ |
| Widget de presupuestos («límite de gasto») | `ui/dashboard/Widgets` | ✅ |
| Widget de metas (anillo + barras) | `ui/dashboard/Widgets` | ✅ |
| Widget de suscripciones (sólo lo cobrado en el periodo) | `ui/dashboard/Widgets` | ✅ |
| «Ver todo» de cada widget lleva a su pantalla | `ui/dashboard/Widgets` | ✅ |
| Reordenar widgets + «Restablecer vista» | `ui/dashboard` ↔ `components/DashboardGrid` | ✅ mismo ajuste `dashboardOrder` |
| Ocultar saldos (`PrivacyToggle`) | `ui/settings` + todas las cifras | ✅ |

## Carteras

| Web | Android | Estado |
|---|---|---|
| Lista con apartados anidados | `ui/wallets` | ✅ |
| Reordenar (`reorderWallets`) | `components/Reorder` (asa de arrastre) | ✅ |
| Detalle de cartera + sus movimientos | `WalletDetailScreen` | ✅ |
| Crear / editar cartera | `WalletFormSheet` | ✅ |
| Rendimiento: tasa **y cadencia** (diario/semanal/quincenal/mensual) | `WalletFormSheet` | ✅ |
| Datos de tarjeta: corte, días para pagar, límite, anualidad | `WalletFormSheet` (`CreditCardSection`) | ✅ |
| «Deuda actual» en vez de «Saldo inicial» en una tarjeta | `WalletFormSheet` | ✅ el signo se voltea al guardar |
| Archivar / eliminar | mantener presionada la tarjeta | ✅ |
| Convertir meta en cartera | `ui/goals` (mantener presionada la meta) | ✅ |
| Panel de tarjeta de crédito (corte, por pagar, utilización) | `WalletDetailScreen` | ✅ |
| Planes MSI (avance, mensualidad, «Liquidado ✓», borrar con confirmación) | `WalletDetailScreen` + `MsiPlanSheet` | ✅ |
| Skins / colores de cartera | `ui/wallets/WalletSkins` | ✅ catálogo + `grad:` |

## Movimientos

| Web | Android | Estado |
|---|---|---|
| Lista (con las dos patas de una transferencia plegadas en un renglón) | `ui/transactions` | ✅ |
| Filtros (cartera, tipo, categoría, periodo) | `ui/transactions` | ✅ |
| Total de lo filtrado (`sumTransactions`) | `ui/transactions` | ✅ con conteo y desglose por moneda |
| Aviso de lista truncada a 100 | `ui/transactions` + `WalletDetailScreen` | ✅ |
| «Sin resultados» distinto de «sin movimientos» | `ui/transactions` | ✅ |
| Alta de ingreso | `TransactionFormSheet` | ✅ |
| Alta de gasto | `TransactionFormSheet` | ✅ |
| Alta de transferencia (incl. multimoneda) | `TransactionFormSheet` | ✅ |
| Destino agrupado: «X y sus apartados» / «Otras carteras» | `TransactionFormSheet` + `PickerField(optionGroup)` | ✅ |
| Al pagar una tarjeta, cuánto debes y para cuándo | `TransactionFormSheet` | ✅ |
| Hora del movimiento (`occurredTime`) | `components/TimeField` | ✅ |
| Editar movimiento (incl. transferencias) | `TransactionEditSheet` | ✅ |
| Editar la pata de un movimiento de inversión desde la lista | `InvestmentLegEditSheet` | ✅ |
| Borrar movimiento | mantener presionado | ✅ |
| Compra a MSI desde el alta | `TransactionFormSheet` | ✅ |
| Aportación a meta desde la lista (editar / borrar) | `ApartadoEditSheet` | ✅ |
| Outbox offline (captura sin señal) | `data/Outbox` + `OutboxPanel` | ✅ |

## Inversiones

| Web | Android | Estado |
|---|---|---|
| Lista + valuación | `ui/investments` | ✅ |
| Resumen de portafolio (2×2 + dona) | `ui/investments` | ✅ |
| Detalle + proyección | `ui/investments` + `components/LineChart` | ✅ |
| What-if en la propia gráfica (aportación + cadencia + 3 cifras) | `InvestmentDetailScreen` | ✅ el botón ya no manda al simulador aparte |
| Cantidad y símbolo en una inversión de cripto | `InvestmentDetailScreen` | ✅ |
| Crear / editar desde el catálogo (tasa Banxico en vivo) | `NewInvestmentSheet` | ✅ |
| Cerrar / **reabrir** / eliminar (con confirmación) | `ui/investments` | ✅ un solo botón que alterna, como la web |
| Movimientos: alta, edición y borrado | `InvestmentSheets` | ✅ |
| Snapshots manuales | `InvestmentSheets` | ✅ |
| Simulador (proyección · meta · comparar) | `SimulatorScreen` | ✅ los tres modos con sus mismos campos, cifras y gráficas |
| Gráfica de crecimiento (aportado + intereses apilados) | `components/SimCharts` (`StackedAreaChart`) | ✅ |
| Gráfica de comparación (una línea por instrumento) | `components/SimCharts` (`MultiLineChart`) | ✅ tasas de CETES y BONDDIA precargadas del catálogo |

## Planeación

| Web | Android | Estado |
|---|---|---|
| Metas: lista, crear, editar, aportar/liberar, borrar, reordenar, plan con fecha | `ui/goals` | ✅ |
| Presupuestos: lista, fijar, editar, borrar | `ui/budgets` | ✅ borrar confirma |
| Suscripciones: lista, crear, editar, pagar, pausar, borrar | `ui/subscriptions` | ✅ borrar confirma |
| Categorías: lista, crear, renombrar, borrar/ocultar, restaurar, reordenar | `ui/categories` | ✅ ocultar/borrar confirma, con el mensaje que toca |

## Ajustes

| Web | Android | Estado |
|---|---|---|
| Versión / acerca de | `ui/settings` | ✅ |
| Idioma (es/en) | `ui/settings` + `ProvideAppLocale` | ✅ |
| Tema claro / oscuro / auto | `ui/settings` | ✅ |
| Apariencia: acento, fondo, tipografía, logo, ícono | `ui/settings/AppearanceScreen` | ✅ (sincronizada con la cuenta) |
| Zona horaria | `ui/settings` | ✅ |
| Formato de reloj 12/24 h | `ui/settings` + lista de movimientos | ✅ |
| Categorías de cartera | `ui/settings` | ✅ (solo lectura, igual que la web) |
| Monedas y tipos de cambio | `ui/settings/CurrenciesScreen` | ✅ ver, refrescar y fijar a mano — **la web no tiene esta pantalla** |
| Novedades (changelog in-app) | `WhatsNewScreen` (asset **generado**) | ✅ con enlace «Ver novedades» y estado vacío |
| Aviso de versión nueva | `components/UpdateNotice` | ✅ avisa; instalar el APK sigue siendo manual |

## Transversal

| Web | Android | Estado |
|---|---|---|
| i18n es/en (`src/i18n/`) | `strings_i18n.xml` **generado** | ✅ |
| Ocultar saldos (preferencia) | `ui/settings` | ✅ |
| Caché offline de lectura | `JsonCache` | ✅ |
| Banner «sin conexión» | `OfflineNotice` | ✅ |
| Formato de dinero por moneda | `Money.kt` | ✅ con el espacio tras el código ISO, como `Intl` |
| Paleta y tipografía «neon glass» | `ui/theme` | ✅ |

## Nota: login con Google en Android

El flujo web es un redirect: `/api/auth/google/start` → Google → callback, que
deja la cookie de sesión **en el navegador**. En Android eso no sirve: en Custom
Tabs la cookie queda en el navegador y no en el cliente HTTP de la app, y en un
WebView embebido Google lo rechaza (`disallowed_useragent`).

La app usa el flujo nativo: **Credential Manager** obtiene un ID token de Google
y lo canjea por una sesión en `POST /api/auth/google/token`.

Dos cosas importantes de ese endpoint:

- El `serverClientId` que pide la app es el **client id de web**, a propósito:
  Google lo estampa como `aud` del token, y eso es exactamente lo que valida el
  worker. No es un secreto — identifica al proyecto.
- A diferencia del redirect (donde el token viene directo de Google por TLS y se
  puede confiar en él), aquí **el token lo manda el cliente**, así que el worker
  lo verifica contra Google (`tokeninfo`) y **exige que `aud` sea el nuestro**.
  Sin esa comprobación, cualquier token de Google emitido para cualquier app
  serviría para entrar como quien el token diga.

### Requisito de configuración (fuera del código)

En el mismo proyecto de Google Cloud donde vive el client id de web hay que
registrar dos clientes OAuth de tipo **Android**, o el sistema no entregará
credenciales:

| Variante | Paquete | SHA-1 |
|---|---|---|
| debug | `com.asura.finanzas.debug` | `E3:C3:70:45:8F:E7:5E:DD:B6:52:A7:63:EE:99:6C:5B:E4:7D:91:BA` |
| release | `com.asura.finanzas` | `62:23:1F:8B:76:3B:3D:39:47:0D:81:79:E3:5E:05:B4:29:98:6A:19` |

El código compila y funciona sin esto; lo único que pasa es que el botón falla
al pedir la credencial.

## Nota: el orden de los widgets del panel

El panel guarda **dos** ajustes por cuenta, porque son formas distintas:

- `dashboardLayout` — la rejilla del escritorio (columnas, tamaños), de
  react-grid-layout. Sólo la web la usa.
- `dashboardOrder` — la lista de llaves de la pila a ancho de teléfono. La
  escriben y la leen **las dos** superficies: la web a ancho < 768 px
  (`DashboardGrid`, con dnd-kit) y el APK (`ui/dashboard`, con
  `components/Reorder`). Reordenar en el teléfono se ve en la web y al revés.

Para que eso funcione, las llaves de los widgets tienen que ser **las mismas en
los dos clientes** (`networth`, `flow`, `budget`, `breakdownExpense`,
`breakdownIncome`, `goals`, `subscriptions`, `byWallet`, `byInvestment`,
`flowRange`), y en el mismo orden natural. Al agregar un widget hay que darlo de
alta en las dos con la misma llave: las que un cliente no conoce se conservan al
final del orden en vez de perderse. «Restablecer vista» limpia los dos ajustes.

## Diferencias que quedan a propósito

- **Instalar una actualización**: la web se recarga sola; el APK sólo avisa que
  hay una versión más nueva (`UpdateNotice`, comparando `appVersion` de
  `/version.json` contra `BuildConfig.VERSION_NAME`). Instalarlo es manual
  mientras el APK se distribuya a mano.
- **Pantalla de monedas**: existe en Android y **no** en la web. El comando
  `set_exchange_rate` ya existía en el worker y `setSetting`/`getSetting` lo
  exponen, pero ninguna vista web lo usa. Si se quiere paridad estricta, toca
  subir la pantalla a la web, no quitarla del teléfono.
- **Tipografías**: la web las carga de Google Fonts; Android usa
  *downloadable fonts* del mismo proveedor, así que la primera vez que se elige
  una pareja tarda un instante y mientras tanto se ven las fuentes empaquetadas.
