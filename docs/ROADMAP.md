# Roadmap post-v1

Ideas ordenadas por valor estimado. Ninguna está comprometida.

- **Tipo de cambio automático**: consumir la API SIE de Banxico (serie FIX) para poblar `exchange_rates` con `source = 'api'`, con fallback manual.
- **Tasas históricas en cajitas**: tabla de cambios de tasa por inversión para que el cálculo de Nu use la tasa vigente en cada periodo en lugar de una sola tasa.
- **Transacciones recurrentes**: plantillas con frecuencia (quincenal, mensual) que generan transacciones pendientes de confirmar.
- **Presupuestos**: límite mensual por categoría de gasto con barra de progreso en el dashboard.
- **Export / import**: CSV por cartera y JSON completo (backup/restore manual).
- **Backups automáticos**: copia de `finanzas.db` rotada en `app_data_dir()/backups/`.
- **Vincular inversión a cartera**: al cerrar una inversión, generar automáticamente la transferencia del rendimiento a la cartera ligada (`linked_wallet_id`).
- **Reportes**: vista mensual/anual con tendencias por categoría.
- **CETES reinversión**: opción de reinvertir al vencimiento encadenando inversiones.
