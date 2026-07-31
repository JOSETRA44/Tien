package com.tien.core.data.repository

import com.tien.core.core.result.AppResult
import com.tien.core.core.result.DataError
import com.tien.core.data.nativedb.NativeConnection
import com.tien.core.data.nativedb.NativeStatus

/**
 * Turns the native layer's `long` return codes into typed results.
 *
 * The native contract is: negative values are `DbStatus` codes, anything else is
 * payload (a new rowid, or an affected-row count).
 */
internal fun NativeConnection.toResult(code: Long): AppResult<Long> =
    if (!NativeStatus.isError(code)) {
        AppResult.Success(code)
    } else {
        AppResult.Failure(toDataError(code, ::lastError))
    }

/** Same as [toResult] but discards the payload, for fire-and-forget mutations. */
internal fun NativeConnection.toUnitResult(code: Long): AppResult<Unit> =
    if (!NativeStatus.isError(code)) {
        AppResult.Success(Unit)
    } else {
        AppResult.Failure(toDataError(code, ::lastError))
    }

/**
 * `lastError` is passed lazily: reading it crosses the JNI boundary, and on the
 * happy path there is nothing to report.
 */
private fun toDataError(code: Long, lastError: () -> String): DataError = when (code) {
    NativeStatus.ERROR_NOT_FOUND -> DataError.NotFound
    NativeStatus.ERROR_CONFLICT -> DataError.Conflict
    NativeStatus.ERROR_CLOSED -> DataError.Unavailable
    NativeStatus.ERROR_INVALID -> DataError.Validation(DataError.Validation.Field.TITLE)
    else -> DataError.Unknown(lastError())
}

/** Result used when the connection could not be opened at all. */
internal fun unavailable(): AppResult<Nothing> = AppResult.Failure(DataError.Unavailable)
