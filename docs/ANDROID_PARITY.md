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

Leyenda: ✅ portado · 🟡 parcial · ⬜ pendiente

## Sesión

| Web | Android | Estado |
|---|---|---|
| Login correo + contraseña | `ui/auth/LoginScreen` | ✅ |
| Registro (alta de cuenta) | — | ⬜ |
| Login con Google (OAuth redirect) | — | ⬜ |
| Sesión persistente (cookie 30 días) | `SessionCookieJar` | ✅ |
| Cerrar sesión | `ui/settings` | ✅ |
| Cambiar contraseña | `ui/settings/ChangePasswordScreen` | ✅ |
| Dispositivos / revocar sesiones | `ui/settings` | ✅ |

## Panel (dashboard)

| Web | Android | Estado |
|---|---|---|
| Patrimonio + efectivo + inversiones | `ui/dashboard` | ✅ |
| Selector de periodo (`PeriodPicker`) | `components/PeriodPicker` | 🟡 faltan mes/día/rango |
| Lista de carteras con saldo | `ui/dashboard` | ✅ |
| Donas «por cartera» y «por inversión» | `ui/dashboard/Widgets` | ✅ |
| Aviso de tipos de cambio faltantes | `ui/dashboard` | ✅ |
| Gráfica de flujo (`FlowChart`, `getSpendingTrends`) | `ui/dashboard` | ✅ |
| Widget desglose por categoría (dona gasto + ingreso) | `ui/dashboard/Widgets` | 🟡 falta el modal de detalle |
| Widget de presupuestos («límite de gasto») | `ui/dashboard/Widgets` | ✅ |
| Widget de metas (anillo + barras) | `ui/dashboard/Widgets` | ✅ |
| Widget de suscripciones | `ui/dashboard/Widgets` | ✅ |
| Ocultar saldos (`PrivacyToggle`) | `ui/settings` + todas las cifras | ✅ |

## Carteras

| Web | Android | Estado |
|---|---|---|
| Lista con apartados anidados | `ui/wallets` | ✅ |
| Reordenar (`reorderWallets`) | — | ⬜ |
| Detalle de cartera + sus movimientos | — | ⬜ |
| Crear / editar cartera | `WalletFormSheet` | ✅ |
| Archivar / eliminar | mantener presionada la tarjeta | ✅ |
| Convertir meta en cartera | — | ⬜ |
| Panel de tarjeta de crédito (corte, por pagar, utilización) | — | ⬜ |
| Planes MSI: previsualizar, crear, borrar | — | ⬜ |
| Skins / colores de cartera | `ui/wallets/WalletSkins` | ✅ catálogo + `grad:` |

## Movimientos

| Web | Android | Estado |
|---|---|---|
| Lista | `ui/transactions` | ✅ |
| Filtros (cartera, tipo, periodo) | `ui/transactions` | ✅ (falta por categoría) |
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
| Lista + valuación | `ui/investments` | ✅ |
| Resumen de portafolio (2×2 + dona) | `ui/investments` | ✅ |
| Detalle + proyección | `ui/investments` | ✅ (proyección: sólo el valor final) |
| Crear / editar (catálogo, calculadoras, Banxico) | — | ⬜ |
| Cerrar / eliminar | `ui/investments` | ✅ |
| Movimientos: alta | `InvestmentSheets` | 🟡 falta editar/borrar |
| Snapshots manuales | `InvestmentSheets` | ✅ |
| Simulador + resolver aportación | — | ⬜ |

## Planeación

| Web | Android | Estado |
|---|---|---|
| Metas: lista, crear, editar, aportar/liberar, borrar | `ui/goals` | ✅ (falta reordenar y plan con fecha) |
| Presupuestos: lista, fijar, editar, borrar | `ui/budgets` | ✅ |
| Suscripciones: lista, crear, editar, pagar, pausar, borrar | `ui/subscriptions` | ✅ |
| Categorías: lista, crear, renombrar, borrar/ocultar | `ui/categories` | ✅ (falta restaurar y reordenar) |

## Ajustes

| Web | Android | Estado |
|---|---|---|
| Versión / acerca de | `ui/settings` | ✅ |
| Idioma (es/en) | `ui/settings` + `ProvideAppLocale` | ✅ |
| Tema claro / oscuro / auto | `ui/settings` | ✅ |
| Apariencia: acento, fondo, tipografía, logo, ícono | — | ⬜ |
| Zona horaria | `ui/settings` | ✅ |
| Formato de reloj 12/24 h | `ui/settings` + lista de movimientos | ✅ |
| Categorías de cartera | `ui/settings` | ✅ (solo lectura, igual que la web) |
| Monedas y tipos de cambio | `ui/settings/CurrenciesScreen` | 🟡 ver y refrescar; falta fijar a mano |
| Novedades (changelog in-app) | `WhatsNewScreen` (asset **generado**) | ✅ |
| Aviso de versión nueva | — | ⬜ (ver plan de actualización) |

## Transversal

| Web | Android | Estado |
|---|---|---|
| i18n es/en (`src/i18n/`) | `strings_i18n.xml` **generado** | ✅ |
| Ocultar saldos (preferencia) | `ui/settings` | ✅ |
| Caché offline de lectura | `JsonCache` | ✅ |
| Banner «sin conexión» | `OfflineNotice` | ✅ |
| Formato de dinero por moneda | `Money.kt` | ✅ |
| Paleta y tipografía «neon glass» | `ui/theme` | ✅ |
