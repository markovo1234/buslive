package com.buslive.app.domain.model

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** User-facing error kinds. UI maps them to strings + retry buttons. */
sealed class AppError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    class NoInternet(cause: Throwable? = null) : AppError("no internet", cause)
    class ApiDown(cause: Throwable? = null) : AppError("api down", cause)
    class NotFound(message: String = "not found") : AppError(message)
    class Unknown(cause: Throwable? = null) : AppError(cause?.message, cause)

    companion object {
        fun from(t: Throwable): AppError = when (t) {
            is AppError -> t
            is UnknownHostException -> NoInternet(t)
            is SocketTimeoutException -> ApiDown(t)
            is HttpException -> if (t.code() >= 500 || t.code() == 429) ApiDown(t) else Unknown(t)
            is IOException -> NoInternet(t)
            else -> Unknown(t)
        }
    }
}

/** runCatching that never swallows coroutine cancellation and maps errors to [AppError]. */
inline fun <T> appRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(AppError.from(e))
}
