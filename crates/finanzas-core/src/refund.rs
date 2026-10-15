//! Refunds: money that comes back for an expense. A refund adds to its wallet's
//! balance like income does, but it is never counted as income — it nets
//! against the spending of its (expense) category, in the period it lands.
//! Pure logic, tested natively; the worker sums the rows from D1 and calls this.

use crate::error::{AppError, AppResult};

/// How much of an expense can still be refunded: its amount minus what earlier
/// refunds already gave back (never below zero).
pub fn remaining_refundable(expense_cents: i64, refunded_cents: i64) -> i64 {
    (expense_cents - refunded_cents).max(0)
}

/// Checks a refund linked to an expense doesn't give back more than was spent.
/// `refunded_cents` is what OTHER refunds of the same expense already returned
/// (when editing a refund, exclude the one being edited).
pub fn validate_refund_amount(
    amount_cents: i64,
    expense_cents: i64,
    refunded_cents: i64,
) -> AppResult<()> {
    if amount_cents <= 0 {
        return Err(AppError::InvalidInput("el monto debe ser positivo".into()));
    }
    if amount_cents > remaining_refundable(expense_cents, refunded_cents) {
        return Err(AppError::InvalidInput(
            "el reembolso no puede ser mayor a lo que falta por reembolsar del gasto".into(),
        ));
    }
    Ok(())
}

/// Net spending of a category (or of everything) over a period: expenses minus
/// the refunds that landed in that same period. A refund can arrive in a later
/// period than its expense, so the raw difference may go negative; spending is
/// reported as zero then, never as a negative amount.
pub fn net_spending(expense_cents: i64, refund_cents: i64) -> i64 {
    (expense_cents - refund_cents).max(0)
}

/// Total spending across categories: each category's expenses net of its own
/// refunds, floored at zero, then added up. A refund only cancels spending of
/// its own category — a refund landing where nothing was spent this period
/// doesn't eat into other categories — so the total always equals the sum of
/// the per-category figures the breakdown and the budgets show.
pub fn net_spending_total(by_category: impl IntoIterator<Item = (i64, i64)>) -> i64 {
    by_category
        .into_iter()
        .map(|(expense, refund)| net_spending(expense, refund))
        .sum()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn remaining_after_partial_refunds() {
        // $1,250.00 spent, $400.00 already refunded → $850.00 left.
        assert_eq!(remaining_refundable(125_000, 40_000), 85_000);
        assert_eq!(remaining_refundable(125_000, 0), 125_000);
        // fully refunded (or over-refunded legacy data) → nothing left
        assert_eq!(remaining_refundable(125_000, 125_000), 0);
        assert_eq!(remaining_refundable(125_000, 130_000), 0);
    }

    #[test]
    fn refund_amount_must_fit_what_is_left() {
        // $500.00 expense with $200.00 back: up to $300.00 still allowed.
        assert!(validate_refund_amount(30_000, 50_000, 20_000).is_ok());
        assert!(validate_refund_amount(1, 50_000, 20_000).is_ok());
        assert!(validate_refund_amount(30_001, 50_000, 20_000).is_err());
        // full refund in one go
        assert!(validate_refund_amount(50_000, 50_000, 0).is_ok());
        // nothing left
        assert!(validate_refund_amount(1, 50_000, 50_000).is_err());
        // non-positive amounts
        assert!(validate_refund_amount(0, 50_000, 0).is_err());
        assert!(validate_refund_amount(-100, 50_000, 0).is_err());
    }

    #[test]
    fn net_spending_subtracts_and_floors_at_zero() {
        // Comida: $3,200.00 spent, $450.50 refunded → $2,749.50 net.
        assert_eq!(net_spending(320_000, 45_050), 274_950);
        assert_eq!(net_spending(320_000, 0), 320_000);
        // refund of last month's purchase lands in a month with less spending
        assert_eq!(net_spending(10_000, 25_000), 0);
        // refund only, nothing spent this month
        assert_eq!(net_spending(0, 25_000), 0);
    }

    #[test]
    fn total_floors_each_category_on_its_own() {
        // Comida $1,000.00 − $300.00 refund = $700.00; Salud only has a $50.00
        // refund this month → $0.00, not −$50.00 off the total; MSI $402.73.
        let total = net_spending_total([(100_000, 30_000), (0, 5_000), (40_273, 0)]);
        assert_eq!(total, 110_273);
        assert_eq!(net_spending_total(std::iter::empty()), 0);
    }
}
