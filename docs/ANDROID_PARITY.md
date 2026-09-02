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
- **Un botón que se encoge no se encoge más allá de su palabra más larga.** En
  flexbox eso es gratis (`min-content`); en Compose hay que pedirlo con
  `Modifier.width(IntrinsicSize.Min)`. Repartir el ancho con fracciones de
  `weight` lo aparenta hasta que una traducción no cabe y el texto sale cortado.
- Medidas compartidas: tarjetas `rounded-2xl` (16 dp), márgenes de página
  16 dp, botón primario `rounded-lg` px16/py8 sobre `accentDim`, encabezado con
  regla de 4×28 y título de 30 sp cuyas acciones **envuelven**.

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
| Selector de periodo (`PeriodPicker`) | `components/PeriodPicker` | ✅ |
| Lista de carteras con saldo | `ui/dashboard` | ✅ |
| Donas «por cartera» y «por inversión» | `ui/dashboard/Widgets` | ✅ |
| Aviso de tipos de cambio faltantes | `ui/dashboard` | ✅ |
| Gráfica de flujo (`FlowChart`, `getSpendingTrends`) | `ui/dashboard` | ✅ |
| Widget «ingresos vs gastos» del periodo (`FlowRangeWidget`) | `ui/dashboard` (`FlowRangeCard`) | ✅ |
| Widget desglose por categoría (dona gasto + ingreso) | `ui/dashboard/Widgets` + `CategoryDetailDialog` | ✅ |
| Widget de presupuestos («límite de gasto») | `ui/dashboard/Widgets` | ✅ |
| Widget de metas (anillo + barras) | `ui/dashboard/Widgets` | ✅ |
| Widget de suscripciones | `ui/dashboard/Widgets` | ✅ |
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
| Archivar / eliminar | mantener presionada la tarjeta | ✅ |
| Convertir meta en cartera | `ui/goals` (mantener presionada la meta) | ✅ |
| Panel de tarjeta de crédito (corte, por pagar, utilización) | `WalletDetailScreen` | ✅ |
| Planes MSI | `WalletDetailScreen` + `MsiPlanSheet` | ✅ |
| Skins / colores de cartera | `ui/wallets/WalletSkins` | ✅ catálogo + `grad:` |

## Movimientos

| Web | Android | Estado |
|---|---|---|
| Lista (con las dos patas de una transferencia plegadas en un renglón) | `ui/transactions` | ✅ |
| Filtros (cartera, tipo, categoría, periodo) | `ui/transactions` | ✅ |
| Total de lo filtrado (`sumTransactions`) | `ui/transactions` | ✅ |
| Alta de ingreso | `TransactionFormSheet` | ✅ |
| Alta de gasto | `TransactionFormSheet` | ✅ |
| Alta de transferencia (incl. multimoneda) | `TransactionFormSheet` | ✅ |
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
| Crear / editar desde el catálogo (tasa Banxico en vivo) | `NewInvestmentSheet` | ✅ |
| Cerrar / eliminar | `ui/investments` | ✅ |
| Movimientos: alta, edición y borrado | `InvestmentSheets` | ✅ |
| Snapshots manuales | `InvestmentSheets` | ✅ |
| Simulador (proyección · meta · comparar) | `SimulatorScreen` | ✅ los tres modos con sus mismos campos, cifras y gráficas |
| Gráfica de crecimiento (aportado + intereses apilados) | `components/SimCharts` (`StackedAreaChart`) | ✅ |
| Gráfica de comparación (una línea por instrumento) | `components/SimCharts` (`MultiLineChart`) | ✅ tasas de CETES y BONDDIA precargadas del catálogo |

## Planeación

| Web | Android | Estado |
|---|---|---|
| Metas: lista, crear, editar, aportar/liberar, borrar, reordenar, plan con fecha | `ui/goals` | ✅ |
| Presupuestos: lista, fijar, editar, borrar | `ui/budgets` | ✅ |
| Suscripciones: lista, crear, editar, pagar, pausar, borrar | `ui/subscriptions` | ✅ |
| Categorías: lista, crear, renombrar, borrar/ocultar, restaurar, reordenar | `ui/categories` | ✅ |

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
| Novedades (changelog in-app) | `WhatsNewScreen` (asset **generado**) | ✅ |
| Aviso de versión nueva | `components/UpdateNotice` | ✅ avisa; instalar el APK sigue siendo manual |

## Transversal

| Web | Android | Estado |
|---|---|---|
| i18n es/en (`src/i18n/`) | `strings_i18n.xml` **generado** | ✅ |
| Ocultar saldos (preferencia) | `ui/settings` | ✅ |
| Caché offline de lectura | `JsonCache` | ✅ |
| Banner «sin conexión» | `OfflineNotice` | ✅ |
| Formato de dinero por moneda | `Money.kt` | ✅ |
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
