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
    let status = match e {
        AppError::InvalidInput(_) => 400,
        AppError::Unauthorized(_) => 401,
        AppError::NotFound(_) => 404,
        AppError::Db(_) | AppError::Internal(_) => 500,
    };
    Ok(Response::from_json(&serde_json::json!({ "error": e.to_string() }))?.with_status(status))
}

/// Serialize a handler result as the HTTP response.
pub fn json_response<T: serde::Serialize>(r: AppResult<T>) -> worker::Result<Response> {
    match r {
        Ok(v) => Response::from_json(&v),
        Err(e) => error_response(&e),
    }
}
