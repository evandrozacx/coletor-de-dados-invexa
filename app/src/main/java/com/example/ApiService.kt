package com.example

import android.content.Context
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Interface legada mantida para retrocompatibilidade
interface ApiService : InvexaApiService

object RetrofitClient {
    fun getApiService(context: Context): InvexaApiService {
        val prefs = context.getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("server_url", ApiClient.getBaseUrl()) ?: ApiClient.getBaseUrl()
        if (url.isNotBlank() && url != ApiClient.getBaseUrl()) {
            ApiClient.setBaseUrl(url)
        }
        val tenant = prefs.getString("tenant_id", "default_tenant") ?: "default_tenant"
        if (tenant != ApiClient.getTenantToken()) {
            ApiClient.updateTenantToken(tenant)
        }
        return ApiClient.apiService
    }
}

