//! Pure financial logic: models, errors and investment calculators.
//! No storage or platform dependencies — the desktop app (rusqlite) and the
//! Cloudflare Worker (D1) load data into `investments::CalcContext` and call
//! the calculators from here.

pub mod error;
pub mod investments;
pub mod market;
pub mod models;
pub mod wallet_yield;
