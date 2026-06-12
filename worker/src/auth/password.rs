//! Password hashing via the runtime's native SubtleCrypto (PBKDF2-HMAC-SHA256).
//!
//! Workers free tier allows ~10ms CPU per request; a pure-Rust PBKDF2 in WASM
//! would blow through that, while `crypto.subtle.deriveBits` runs native.
//! workerd caps PBKDF2 at 100,000 iterations.
//!
//! Stored format (PHC-style, iterations per hash so the cost is tunable
//! without invalidating existing hashes):
//!   pbkdf2-sha256$<iterations>$<salt_hex>$<hash_hex>

use finanzas_core::error::{AppError, AppResult};
use js_sys::{Array, Object, Promise, Reflect, Uint8Array};
use wasm_bindgen::prelude::wasm_bindgen;
use wasm_bindgen::JsValue;
use wasm_bindgen_futures::JsFuture;

use crate::db::random_bytes;

pub const DEFAULT_ITERATIONS: u32 = 100_000;

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = ["crypto", "subtle"], js_name = importKey)]
    fn js_import_key(
        format: &str,
        key_data: Uint8Array,
        algorithm: &JsValue,
        extractable: bool,
        usages: &JsValue,
    ) -> Promise;

    #[wasm_bindgen(js_namespace = ["crypto", "subtle"], js_name = deriveBits)]
    fn js_derive_bits(algorithm: &JsValue, key: &JsValue, length: u32) -> Promise;
}

fn js_err(e: JsValue) -> AppError {
    AppError::Internal(format!("error de crypto: {e:?}"))
}

fn set(obj: &Object, key: &str, value: &JsValue) -> AppResult<()> {
    Reflect::set(obj, &JsValue::from_str(key), value).map_err(js_err)?;
    Ok(())
}

async fn pbkdf2_sha256(password: &str, salt: &[u8], iterations: u32) -> AppResult<Vec<u8>> {
    let key_data = Uint8Array::from(password.as_bytes());
    let usages: JsValue = Array::of1(&JsValue::from_str("deriveBits")).into();
    let key = JsFuture::from(js_import_key(
        "raw",
        key_data,
        &JsValue::from_str("PBKDF2"),
        false,
        &usages,
    ))
    .await
    .map_err(js_err)?;

    let algo = Object::new();
    set(&algo, "name", &JsValue::from_str("PBKDF2"))?;
    set(&algo, "hash", &JsValue::from_str("SHA-256"))?;
    set(&algo, "salt", &Uint8Array::from(salt).into())?;
    set(&algo, "iterations", &JsValue::from_f64(iterations as f64))?;

    let bits = JsFuture::from(js_derive_bits(&algo.into(), &key, 256))
        .await
        .map_err(js_err)?;
    Ok(Uint8Array::new(&bits).to_vec())
}

pub async fn hash_password(password: &str, iterations: u32) -> AppResult<String> {
    let salt = random_bytes(16);
    let dk = pbkdf2_sha256(password, &salt, iterations).await?;
    Ok(format!(
        "pbkdf2-sha256${iterations}${}${}",
        hex::encode(&salt),
        hex::encode(&dk)
    ))
}

pub async fn verify_password(password: &str, stored: &str) -> AppResult<bool> {
    let parts: Vec<&str> = stored.split('$').collect();
    let ["pbkdf2-sha256", iters, salt_hex, hash_hex] = parts.as_slice() else {
        // unknown format (e.g. the system sentinel '!'): never matches
        return Ok(false);
    };
    let iterations: u32 = iters
        .parse()
        .map_err(|_| AppError::Internal("hash almacenado corrupto".into()))?;
    let salt = hex::decode(salt_hex).map_err(|_| AppError::Internal("hash corrupto".into()))?;
    let expected = hex::decode(hash_hex).map_err(|_| AppError::Internal("hash corrupto".into()))?;
    let derived = pbkdf2_sha256(password, &salt, iterations).await?;
    // constant-time comparison
    if derived.len() != expected.len() {
        return Ok(false);
    }
    let mut diff = 0u8;
    for (a, b) in derived.iter().zip(expected.iter()) {
        diff |= a ^ b;
    }
    Ok(diff == 0)
}
