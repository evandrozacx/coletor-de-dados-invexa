package com.example

import retrofit2.Response
import retrofit2.http.*

interface InvexaApiService {

    // 1. Baixar lista de Operadores/Colaboradores autorizados
    @GET("api/operators")
    suspend fun getOperators(
        @Header("x-tenant-id") tenantId: String = ApiClient.getTenantToken()
    ): Response<List<Operator>>

    // 2. Baixar lista de Dispositivos/Terminais cadastrados
    @GET("api/devices")
    suspend fun getDevices(): Response<List<Map<String, Any>>>

    // 3. Baixar dados dos Inventários (Produtos, Setores e Endereços)
    @GET("api/inventories")
    suspend fun getInventories(
        @Header("x-tenant-id") tenantId: String = ApiClient.getTenantToken()
    ): Response<List<InventoryDto>>

    @GET("api/inventories/{id}")
    suspend fun getInventory(
        @Path("id") inventoryId: String,
        @Header("x-tenant-id") tenantId: String = ApiClient.getTenantToken()
    ): Response<Any>

    @Streaming
    @GET("api/inventories/{id}/products")
    suspend fun getInventoryProducts(
        @Path("id") inventoryId: String,
        @Query("limit") limit: Int? = null,
        @Query("per_page") perPage: Int? = null,
        @Query("perPage") perPageAlt: Int? = null,
        @Query("all") all: Boolean? = null,
        @Query("page") page: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("skip") skip: Int? = null,
        @Query("take") take: Int? = null,
        @Query("page_number") pageNumber: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sector_id") sectorId: String? = null,
        @Query("sectorId") sectorIdAlt: String? = null,
        @Header("x-tenant-id") tenantId: String = ApiClient.getTenantToken()
    ): Response<okhttp3.ResponseBody>

    @Streaming
    @GET("api/inventories/{id}/products")
    suspend fun getInventoryProductsMap(
        @Path("id") inventoryId: String,
        @QueryMap options: Map<String, String>,
        @Header("x-tenant-id") tenantId: String = ApiClient.getTenantToken()
    ): Response<okhttp3.ResponseBody>

    // 4. Enviar bipe/coleta realizada pelo operador
    @POST("api/collect")
    suspend fun sendCollectionItem(
        @Body request: CollectRequest
    ): Response<CollectResponse>

    @POST("api/collect")
    suspend fun sendColeta(
        @Header("x-tenant-id") tenantId: String = ApiClient.getTenantToken(),
        @Body payload: ColetaPayload
    ): Response<CollectResponse>

    // Endpoint legado de sincronização geral do banco
    @GET("api/db")
    suspend fun getDatabase(): Response<DatabaseResponse>

    // Transmissão em lote de seção fechada
    @POST("api/collect")
    suspend fun transmitCollection(
        @Body payload: CollectPayload
    ): Response<Any>
}
