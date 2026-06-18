use finanzas_core::error::{AppError, AppResult};
use worker::Response;

/// worker::Error → AppError (the orphan rule forbids a From impl here, since
/// both types are foreign; handlers use `.map_err(db_err)`).
pub fn db_err(e: worker::Error) -> AppError {
    AppError::Db(e.to_string())
}

/// AppError → HTTP response. The body shape `{"error": "..."}` is what the
/// frontend rpc helper rejects with, mirroring the Tauri string contract.
pub fn error_response(e: &AppError) -> worker::Result<Response> {
    // Db/Internal carry raw driver/dependency detail (schema names, upstream
    // failures) — log it server-side (visible via `wrangler tail`) but never
    // return it to the client. The other variants are intentionally user-facing.
    let (status, body) = match e {
        AppError::InvalidInput(m) => (400, m.clone()),
        AppError::Unauthorized(m) => (401, m.clone()),
        AppError::NotFound(_) => (404, e.to_string()),
        AppError::TooManyRequests(m) => (429, m.clone()),
        AppError::Db(_) | AppError::Internal(_) => {
            worker::console_error!("internal error: {e}");
            (500, "error interno".to_string())
        }
    };
    Ok(Response::from_json(&serde_json::json!({ "error": body }))?.with_status(status))
}

/// Serialize a handler result as the HTTP response.
pub fn json_response<T: serde::Serialize>(r: AppResult<T>) -> worker::Result<Response> {
    match r {
        Ok(v) => Response::from_json(&v),
        Err(e) => error_response(&e),
    }
}
