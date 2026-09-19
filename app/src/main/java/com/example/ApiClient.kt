package com.example

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Altere para a URL do seu servidor (ex: "https://seu-dominio.com/")
    private var baseUrl = "https://ais-pre-xsa5q4sp75rasnv4bcblyd-465092240318.us-west2.run.app/"
    
    // Identificador da conta / tenant (Se houver login, passe o token do usuário)
    private var tenantToken: String = "default_tenant"

    fun updateTenantToken(token: String) {
        this.tenantToken = token
    }

    fun getTenantToken(): String = tenantToken

    fun setBaseUrl(url: String) {
        var cleanUrl = url.trim()
        if (cleanUrl.isNotEmpty()) {
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            cleanUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
            this.baseUrl = cleanUrl
            rebuildService()
        }
    }

    fun getBaseUrl(): String = baseUrl

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-tenant-id", tenantToken)
                    .addHeader("Authorization", "Bearer $tenantToken")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private var _apiService: InvexaApiService? = null

    val apiService: InvexaApiService
        get() {
            if (_apiService == null) {
                rebuildService()
            }
            return _apiService!!
        }

    private fun rebuildService() {
        _apiService = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InvexaApiService::class.java)
    }
}
