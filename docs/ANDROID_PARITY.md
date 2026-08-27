# Paridad web ↔ Android

**Regla (no negociable):** web y Android son la misma app. Todo cambio de
producto se implementa en las dos superficies **en el mismo branch**, y este
archivo se actualiza en el mismo commit. Si una feature no se puede portar en el
momento, no se mergea.

Cuando las dos difieren en un detalle no especificado, **la web es la
implementación de referencia**.

Los textos de Android **se generan** desde `src/i18n/` con
`npm run gen:android-strings` → `android/app/src/main/res/values*/strings_i18n.xml`.
Nunca escribas una cadena a mano en un composable: agrégala en `es.ts` + `en.ts`,
regenera y commitea. El generador falla si las dos lenguas no tienen las mismas
llaves.

Leyenda: ✅ portado · 🟡 parcial · ⬜ pendiente

## Sesión

| Web | Android | Estado |
|---|---|---|
| Login correo + contraseña | `ui/auth/LoginScreen` | ✅ |
| Registro (alta de cuenta) | — | ⬜ |
| Login con Google (OAuth redirect) | — | ⬜ |
| Sesión persistente (cookie 30 días) | `SessionCookieJar` | ✅ |
| Cerrar sesión | `ui/settings` | ✅ |
| Cambiar contraseña | — | ⬜ |
| Dispositivos / revocar sesiones | — | ⬜ |

## Panel (dashboard)

| Web | Android | Estado |
|---|---|---|
| Patrimonio + efectivo + inversiones | `ui/dashboard` | ✅ |
| Selector de periodo (`PeriodPicker`) | — | ⬜ |
| Lista de carteras con saldo | `ui/dashboard` | ✅ |
| Rebanadas de inversiones | `ui/dashboard` | ✅ |
| Aviso de tipos de cambio faltantes | `ui/dashboard` | ✅ |
| Gráfica de flujo (`FlowChart`, `getSpendingTrends`) | — | ⬜ |
| Widget desglose por categoría + modal de detalle | — | ⬜ |
| Widget de presupuestos | — | ⬜ |
| Widget de metas | — | ⬜ |
| Widget de suscripciones | — | ⬜ |
| Ocultar saldos (`PrivacyToggle`) | — | ⬜ |

## Carteras

| Web | Android | Estado |
|---|---|---|
| Lista con apartados anidados | `ui/wallets` | ✅ |
| Reordenar (`reorderWallets`) | — | ⬜ |
| Detalle de cartera + sus movimientos | — | ⬜ |
| Crear / editar cartera | — | ⬜ |
| Archivar / eliminar | — | ⬜ |
| Convertir meta en cartera | — | ⬜ |
| Panel de tarjeta de crédito (corte, por pagar, utilización) | — | ⬜ |
| Planes MSI: previsualizar, crear, borrar | — | ⬜ |
| Skins / colores de cartera | 🟡 solo color | 🟡 |

## Movimientos

| Web | Android | Estado |
|---|---|---|
| Lista | `ui/transactions` | ✅ |
| Filtros (cartera, tipo, categoría, periodo) | — | ⬜ |
| Total de lo filtrado (`sumTransactions`) | — | ⬜ |
| Alta de ingreso | `TransactionFormSheet` | ✅ |
| Alta de gasto | `TransactionFormSheet` | ✅ |
| Alta de transferencia (incl. multimoneda) | `TransactionFormSheet` | ✅ |
| Hora del movimiento (`occurredTime`) | — | ⬜ |
| Editar movimiento / transferencia | — | ⬜ |
| Borrar movimiento | mantener presionado | ✅ |
| Compra a MSI desde el alta | — | ⬜ |
| Aportación a meta desde la lista (editar / borrar) | — | ⬜ |
| Outbox offline (captura sin señal) | — | ⬜ |

## Inversiones

| Web | Android | Estado |
|---|---|---|
| Lista + valuación | — | ⬜ |
| Resumen de portafolio | — | ⬜ |
| Detalle + proyección | — | ⬜ |
| Crear / editar (catálogo, calculadoras, Banxico) | — | ⬜ |
| Cerrar / eliminar | — | ⬜ |
| Movimientos: alta, edición, borrado | — | ⬜ |
| Snapshots manuales | — | ⬜ |
| Simulador + resolver aportación | — | ⬜ |

## Planeación

| Web | Android | Estado |
|---|---|---|
| Metas: lista, crear, editar, aportar, usar, borrar, reordenar | — | ⬜ |
| Presupuestos: lista, fijar, borrar | — | ⬜ |
| Suscripciones: lista, crear, editar, pagar, activar, borrar | — | ⬜ |
| Categorías: lista, crear, editar, borrar, restaurar, reordenar | — | ⬜ |

## Ajustes

| Web | Android | Estado |
|---|---|---|
| Versión / acerca de | `ui/settings` | ✅ |
| Idioma (es/en) | `ui/settings` + `ProvideAppLocale` | ✅ |
| Tema claro / oscuro / auto | `ui/settings` | ✅ |
| Apariencia: acento, fondo, tipografía, logo, ícono | — | ⬜ |
| Zona horaria | — | ⬜ |
| Formato de reloj 12/24 h | — | ⬜ |
| Categorías de cartera | — | ⬜ |
| Monedas y tipos de cambio | — | ⬜ |
| Novedades (changelog in-app) | — | ⬜ |
| Aviso de versión nueva | — | ⬜ (ver plan de actualización) |

## Transversal

| Web | Android | Estado |
|---|---|---|
| i18n es/en (`src/i18n/`) | `strings_i18n.xml` **generado** | ✅ |
| Ocultar saldos (preferencia) | `ui/settings` | 🟡 se guarda, falta aplicarla en pantalla |
| Caché offline de lectura | `JsonCache` | ✅ |
| Banner «sin conexión» | `OfflineNotice` | ✅ |
| Formato de dinero por moneda | `Money.kt` | ✅ |
| Paleta y tipografía «neon glass» | `ui/theme` | ✅ |
