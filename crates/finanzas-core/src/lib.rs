//! Pure financial logic: models, errors and investment calculators.
//! No storage or platform dependencies — the desktop app (rusqlite) and the
//! Cloudflare Worker (D1) load data into `investments::CalcContext` and call
//! the calculators from here.

pub mod budget;
pub mod credit;
pub mod error;
pub mod goals;
pub mod investments;
pub mod market;
pub mod models;
pub mod period;
pub mod subscription;
pub mod wallet_yield;
