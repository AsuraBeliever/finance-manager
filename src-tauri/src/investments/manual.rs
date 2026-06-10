//! Manual valuation: the user records the current value as snapshots.
//! value_at returns the latest snapshot on or before `as_of`, falling back to
//! the principal when there are none. params: {}

use chrono::NaiveDate;
use rusqlite::Connection;

use super::InvestmentCalculator;
use crate::error::AppResult;
use crate::models::Investment;

pub struct Manual;

impl InvestmentCalculator for Manual {
    fn id(&self) -> &'static str {
        "manual"
    }

    fn value_at(&self, inv: &Investment, conn: &Connection, as_of: NaiveDate) -> AppResult<i64> {
        let value: Option<i64> = conn
            .query_row(
                "SELECT value_cents FROM investment_snapshots
                 WHERE investment_id = ?1 AND as_of <= ?2
                 ORDER BY as_of DESC, id DESC LIMIT 1",
                rusqlite::params![inv.id, as_of.format("%Y-%m-%d").to_string()],
                |r| r.get(0),
            )
            .map(Some)
            .or_else(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => Ok(None),
                other => Err(other),
            })?;
        Ok(value.unwrap_or(inv.principal_cents))
    }

    fn maturity_date(&self, _inv: &Investment) -> Option<NaiveDate> {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::open_in_memory;
    use crate::investments::test_investment;
    use rusqlite::params;

    fn snapshot(conn: &Connection, inv_id: i64, value_cents: i64, as_of: &str) {
        // satisfy the FK: the row must exist in investments
        conn.execute(
            "INSERT OR IGNORE INTO investments (id, name, calculator, principal_cents, start_date)
             VALUES (?1, 'test', 'manual', 1000, '2026-01-01')",
            [inv_id],
        )
        .unwrap();
        conn.execute(
            "INSERT INTO investment_snapshots (investment_id, value_cents, as_of)
             VALUES (?1, ?2, ?3)",
            params![inv_id, value_cents, as_of],
        )
        .unwrap();
    }

    #[test]
    fn falls_back_to_principal_without_snapshots() {
        let conn = open_in_memory();
        let inv = test_investment("manual", 500_000, "{}");
        let date = NaiveDate::from_ymd_opt(2026, 6, 1).unwrap();
        assert_eq!(Manual.value_at(&inv, &conn, date).unwrap(), 500_000);
    }

    #[test]
    fn returns_latest_snapshot_on_or_before_date() {
        let conn = open_in_memory();
        let inv = test_investment("manual", 500_000, "{}");
        snapshot(&conn, inv.id, 510_000, "2026-02-01");
        snapshot(&conn, inv.id, 530_000, "2026-04-01");
        snapshot(&conn, inv.id, 550_000, "2026-08-01"); // future relative to as_of

        let date = NaiveDate::from_ymd_opt(2026, 6, 1).unwrap();
        assert_eq!(Manual.value_at(&inv, &conn, date).unwrap(), 530_000);
    }
}
