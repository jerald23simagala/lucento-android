package com.lucento.app.domain.backup

enum class BackupFailure {
    /** The text is not JSON. */
    MALFORMED,

    /** JSON schemaVersion is newer than this app. */
    UNSUPPORTED_VERSION,

    /** JSON parsed, but the backup is incomplete, inconsistent, or carries fields that must not be restored. */
    INVALID,

    /** The file is larger than the restore limit, so it was refused before parsing. */
    TOO_LARGE,
}

/**
 * A backup that was not applied. [message] is safe to show: it does not include file contents,
 * amounts, or secrets.
 */
class BackupFormatException(
    val failure: BackupFailure,
    message: String,
) : Exception(message)
