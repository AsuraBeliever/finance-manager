//! Thin D1 helpers: typed query execution + JsValue binding.
//!
//! D1 numbers cross the JS boundary as f64 — exact only below 2^53. Cents,
//! micros and bps are orders of magnitude below that; never multiply money
//! units inside SQL (that arithmetic stays in Rust, as on desktop).

use chrono::NaiveDate;
use finanzas_core::error::{AppError, AppResult};
use serde::de::DeserializeOwned;
use wasm_bindgen::prelude::wasm_bindgen;
use wasm_bindgen::JsValue;
use worker::{D1Database, D1PreparedStatement, D1Result};

pub use crate::error::db_err;

// ---- parameter binding ----

pub trait ToJs {
    fn to_js(&self) -> JsValue;
}

impl ToJs for i64 {
    fn to_js(&self) -> JsValue {
        JsValue::from_f64(*self as f64)
    }
}
impl ToJs for f64 {
    fn to_js(&self) -> JsValue {
        JsValue::from_f64(*self)
    }
}
impl ToJs for bool {
    fn to_js(&self) -> JsValue {
        // SQLite stores integers; avoid JS true/false surprises
        JsValue::from_f64(if *self { 1.0 } else { 0.0 })
    }
}
impl ToJs for str {
    fn to_js(&self) -> JsValue {
        JsValue::from_str(self)
    }
}
impl ToJs for &str {
    fn to_js(&self) -> JsValue {
        JsValue::from_str(self)
    }
}
impl ToJs for String {
    fn to_js(&self) -> JsValue {
        JsValue::from_str(self)
    }
}
impl<T: ToJs> ToJs for Option<T> {
    fn to_js(&self) -> JsValue {
        match self {
            Some(v) => v.to_js(),
            None => JsValue::NULL,
        }
    }
}
impl<T: ToJs + ?Sized> ToJs for &T {
    fn to_js(&self) -> JsValue {
        (**self).to_js()
    }
}

/// Build a `Vec<JsValue>` of bind parameters.
#[macro_export]
macro_rules! jsv {
    ($($e:expr),* $(,)?) => {
        vec![$($crate::db::ToJs::to_js(&$e)),*]
    };
}

// ---- query helpers ----

pub fn stmt(db: &D1Database, sql: &str, params: Vec<JsValue>) -> AppResult<D1PreparedStatement> {
    db.prepare(sql).bind(&params).map_err(db_err)
}

pub async fn all<T: DeserializeOwned>(
    db: &D1Database,
    sql: &str,
    params: Vec<JsValue>,
) -> AppResult<Vec<T>> {
    stmt(db, sql, params)?
        .all()
        .await
        .map_err(db_err)?
        .results::<T>()
        .map_err(db_err)
}

pub async fn first<T: DeserializeOwned>(
    db: &D1Database,
    sql: &str,
    params: Vec<JsValue>,
) -> AppResult<Option<T>> {
    stmt(db, sql, params)?.first::<T>(None).await.map_err(db_err)
}

pub async fn exec(db: &D1Database, sql: &str, params: Vec<JsValue>) -> AppResult<D1Result> {
    stmt(db, sql, params)?.run().await.map_err(db_err)
}

/// Atomic multi-statement write. D1 has no interactive BEGIN/COMMIT — a batch
/// IS the transaction (all statements roll back if any fails).
pub async fn batch(
    db: &D1Database,
    stmts: Vec<D1PreparedStatement>,
) -> AppResult<Vec<D1Result>> {
    db.batch(stmts).await.map_err(db_err)
}

/// Batch in slices to keep each D1 batch call a sane size. Atomicity is per
/// slice — fine for idempotent upserts (rate history), NOT for transfers.
pub async fn batch_chunks(
    db: &D1Database,
    stmts: Vec<D1PreparedStatement>,
    per_batch: usize,
) -> AppResult<()> {
    let mut stmts = stmts;
    while !stmts.is_empty() {
        let rest = stmts.split_off(stmts.len().min(per_batch));
        db.batch(stmts).await.map_err(db_err)?;
        stmts = rest;
    }
    Ok(())
}

pub fn changes(r: &D1Result) -> i64 {
    r.meta()
        .ok()
        .flatten()
        .and_then(|m| m.changes)
        .unwrap_or(0) as i64
}

pub fn last_row_id(r: &D1Result) -> AppResult<i64> {
    r.meta()
        .ok()
        .flatten()
        .and_then(|m| m.last_row_id)
        .map(|v| v as i64)
        .ok_or_else(|| AppError::Internal("no se obtuvo el id insertado".into()))
}

// ---- scalar row shapes ----

#[derive(serde::Deserialize)]
pub struct CountRow {
    pub n: i64,
}

#[derive(serde::Deserialize)]
pub struct ValueRow {
    pub value: String,
}

// ---- misc ----

/// Business "today" in Mexico City (UTC-6 year-round; DST abolished in 2022).
/// SQL `datetime('now')` defaults stay UTC, same as on desktop.
pub fn today_mx() -> NaiveDate {
    let ms = worker::Date::now().as_millis() as i64;
    chrono::DateTime::from_timestamp_millis(ms - 6 * 3600 * 1000)
        .expect("valid timestamp")
        .date_naive()
}

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = crypto, js_name = getRandomValues)]
    fn js_get_random_values(buf: js_sys::Uint8Array) -> js_sys::Uint8Array;
}

pub fn random_bytes(n: usize) -> Vec<u8> {
    let arr = js_sys::Uint8Array::new_with_length(n as u32);
    let arr = js_get_random_values(arr);
    let mut buf = vec![0u8; n];
    arr.copy_to(&mut buf);
    buf
}

/// UUID-v4-shaped id for transfer_group_id (matches the desktop format).
pub fn new_group_id() -> String {
    let mut b = random_bytes(16);
    b[6] = (b[6] & 0x0f) | 0x40; // version 4
    b[8] = (b[8] & 0x3f) | 0x80; // RFC 4122 variant
    let h = hex::encode(b);
    format!(
        "{}-{}-{}-{}-{}",
        &h[0..8],
        &h[8..12],
        &h[12..16],
        &h[16..20],
        &h[20..32]
    )
}

pub fn sha256_hex(data: &[u8]) -> String {
    use sha2::{Digest, Sha256};
    hex::encode(Sha256::digest(data))
}
