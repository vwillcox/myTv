package com.vwillcox.mytv.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, message: String) : IOException(message)

val JsonMedia = "application/json; charset=utf-8".toMediaType()

fun jsonBody(json: String): RequestBody = json.toRequestBody(JsonMedia)

const val HTTP_LOG = "myTvHttp"

/** Notes refused and unanswered requests in the device log: `adb logcat -s myTvHttp`. */
private val logFailures = Interceptor { chain ->
    val req = chain.request()
    // Without the query, which can carry a key.
    val what = "${req.method} ${req.url.host}${req.url.encodedPath}"
    try {
        chain.proceed(req).also { res -> if (res.code >= 400) Log.w(HTTP_LOG, "$what -> ${res.code}") }
    } catch (e: IOException) {
        if (!chain.call().isCanceled()) Log.w(HTTP_LOG, "$what failed: $e")
        throw e
    }
}

fun baseHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    // A screen of artwork is asked of one server at once, which resizes each picture before answering.
    .dispatcher(Dispatcher().apply { maxRequestsPerHost = 10 })
    .addInterceptor(logFailures)
    .build()

/** Runs a request off the main thread and returns the body, throwing [HttpException] on non-2xx. */
suspend fun OkHttpClient.fetch(request: Request): String = withContext(Dispatchers.IO) {
    newCall(request).execute().use { res ->
        val body = res.body.string()
        if (!res.isSuccessful) throw HttpException(res.code, "HTTP ${res.code}: ${body.take(200)}")
        body
    }
}

/** A short, human reason for a failure, for the screen. */
fun Throwable.friendly(): String = when (this) {
    is HttpException -> when (code) {
        401, 403 -> "The server turned the sign-in down"
        404 -> "Not found on the server"
        else -> "The server answered ${code}"
    }
    is UnknownHostException -> "That address can't be found"
    is ConnectException -> "Nothing answered at that address"
    is SocketTimeoutException -> "The server took too long to answer"
    else -> message ?: javaClass.simpleName
}
