package com.elsco.mindwaymaths.network

import com.elsco.mindwaymaths.BuildConfig
import com.elsco.mindwaymaths.data.remote.FirebaseProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface PdfApi {
    @Streaming @GET("v1/pdfs/{id}") suspend fun open(@Path("id") id: String): retrofit2.Response<ResponseBody>
}
@Singleton class NetworkClient @Inject constructor(private val firebase: FirebaseProvider) {
    private fun tokens(force: Boolean): Pair<String, String> = runBlocking {
        withTimeout(12_000) {
            val user = firebase.auth.currentUser ?: throw IOException("Authentication required")
            val id = user.getIdToken(force).await().token ?: throw IOException("Authentication required")
            val check = firebase.appCheck.getAppCheckToken(force).await().token
            id to check
        }
    }
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain ->
            val request = chain.request()
            if (!request.url.isHttps || request.url.host != BuildConfig.BACKEND_URL.toHttpUrl().host) throw IOException("Untrusted backend")
            val uid = firebase.auth.currentUser?.uid ?: throw IOException("Authentication required")
            fun authenticated(force: Boolean): Request {
                val (id, check) = try { tokens(force) } catch (e: Exception) { throw IOException("Authentication unavailable", e) }
                if (firebase.auth.currentUser?.uid != uid) throw IOException("Account changed")
                return request.newBuilder().header("Authorization", "Bearer $id").header("X-Firebase-AppCheck", check).build()
            }
            var response = chain.proceed(authenticated(false))
            if (response.code == 401 && request.method == "GET") {
                response.close()
                response = chain.proceed(authenticated(true))
            }
            if (firebase.auth.currentUser?.uid != uid) { response.close(); throw IOException("Account changed") }
            response
        }.build()
    val pdfApi: PdfApi = Retrofit.Builder().baseUrl(BuildConfig.BACKEND_URL).client(client).build().create(PdfApi::class.java)
}
