use serde::Serialize;

#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("database error: {0}")]
    Db(#[from] rusqlite::Error),

    #[error("{0}")]
    InvalidInput(String),

    #[error("{0} not found")]
    NotFound(&'static str),

    #[error("{0}")]
    Internal(String),
}

// Tauri commands need errors that serialize across the IPC bridge;
// the frontend shows the message string as-is.
impl Serialize for AppError {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(&self.to_string())
    }
}

pub type AppResult<T> = Result<T, AppError>;
