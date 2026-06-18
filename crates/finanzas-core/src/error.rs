use serde::Serialize;

#[derive(Debug, thiserror::Error)]
pub enum AppError {
    // Stored as a message string so the core stays free of storage deps;
    // each backend (rusqlite, D1) converts its own error type into this.
    #[error("database error: {0}")]
    Db(String),

    #[error("{0}")]
    InvalidInput(String),

    #[error("{0} not found")]
    NotFound(&'static str),

    // No authenticated session / bad credentials; maps to HTTP 401.
    #[error("{0}")]
    Unauthorized(String),

    // Too many requests in a time window (auth throttle); maps to HTTP 429.
    #[error("{0}")]
    TooManyRequests(String),

    #[error("{0}")]
    Internal(String),
}

// Commands need errors that serialize across the IPC/HTTP boundary;
// the frontend shows the message string as-is.
impl Serialize for AppError {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(&self.to_string())
    }
}

#[cfg(feature = "rusqlite")]
impl From<rusqlite::Error> for AppError {
    fn from(e: rusqlite::Error) -> Self {
        AppError::Db(e.to_string())
    }
}

pub type AppResult<T> = Result<T, AppError>;
