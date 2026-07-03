# Roadmap post-v1

Ideas ordenadas por valor estimado. Ninguna está comprometida.

- ~~Tipo de cambio automático~~ — hecho en v1.1.0 con open.er-api.com (ver DECISIONS.md). Posible mejora futura: usar el FIX oficial de Banxico SIE con token.
- **Tasas históricas en cajitas**: tabla de cambios de tasa por inversión para que el cálculo de Nu use la tasa vigente en cada periodo en lugar de una sola tasa.
- **Transacciones recurrentes**: plantillas con frecuencia (quincenal, mensual) que generan transacciones pendientes de confirmar.
- **Presupuestos**: límite mensual por categoría de gasto con barra de progreso en el dashboard.
- **Export / import**: CSV por cartera y JSON completo (backup/restore manual).
- **Backups automáticos**: copia de `finanzas.db` rotada en `app_data_dir()/backups/`.
- **Vincular inversión a cartera**: al cerrar una inversión, generar automáticamente la transferencia del rendimiento a la cartera ligada (`linked_wallet_id`).
- **Reportes**: vista mensual/anual con tendencias por categoría.
- **CETES reinversión**: opción de reinvertir al vencimiento encadenando inversiones.
- **Motor de notificaciones** — fase campanita HECHA (2026-07-02, rama `feat/notificaciones`): cron 14:00 UTC evalúa reglas por usuario y llena la campanita in-app; Ajustes → Notificaciones con check maestro por categoría + reglas con parámetros, todo apagado por defecto; recordatorios por inversión (aportar cada X / resumen de rendimiento cada X). Pendiente: canal de **correo** (las prefs ya guardan `channels.email`; falta integrar proveedor de envío + `email_sent_at`) y quizá push (Web Push; iOS PWA 16.4+).
