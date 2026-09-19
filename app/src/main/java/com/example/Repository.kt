package com.example

import android.content.Context
import retrofit2.Response
import com.example.db.AppDatabase
import com.example.db.InventoryEntity
import com.example.db.OperatorEntity
import com.example.db.ProductEntity
import com.example.db.SectorEntity
import com.example.db.SectionEntity
import com.example.db.LocalColetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.appDao()
    private val api: InvexaApiService
        get() = RetrofitClient.getApiService(context)

    private fun isInventoryInProgress(status: String?): Boolean {
        if (status.isNullOrBlank()) return true // default to true if no status to avoid missing inventories
        val normalized = status.trim().uppercase().replace(" ", "_").replace("-", "_")
        return normalized == "EM_ANDAMENTO" || normalized == "ANDAMENTO" || normalized == "IN_PROGRESS" || normalized == "ABERTO" || normalized == "ATIVO" || normalized == "ACTIVE" || normalized == "OPEN"
    }

    suspend fun syncDatabase(): Boolean = withContext(Dispatchers.IO) {
        try {
            // First fetch Operators from /api/operators endpoint if available
            try {
                val opResponse = api.getOperators()
                if (opResponse.isSuccessful) {
                    val ops = opResponse.body() ?: emptyList()
                    val operatorEntities = ops.map {
                        OperatorEntity(
                            id = it.id,
                            name = it.nomeCompleto,
                            cpf = it.cpf,
                            senhaPreenchedores = it.senhaPreenchedores
                        )
                    }
                    if (operatorEntities.isNotEmpty()) {
                        dao.clearOperators()
                        dao.insertOperators(operatorEntities)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var data: DatabaseResponse? = try {
                val dbRes = api.getDatabase()
                if (dbRes.isSuccessful) dbRes.body() else null
            } catch (e: Exception) { null }

            if (data == null) {
                // Fallback to /api/inventories if /api/db is not available (like in newer versions)
                try {
                    val invRes = api.getInventories()
                    if (invRes.isSuccessful) {
                        data = DatabaseResponse(inventories = invRes.body())
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (data != null) {
                // Save operators from DB response if operators table empty or as additional source
                val operators = data.operators?.map { 
                    OperatorEntity(
                        id = it.id,
                        name = it.name ?: it.nomeCompleto ?: "Desconhecido",
                        cpf = it.cpf,
                        senhaPreenchedores = it.senhaPreenchedores
                    )
                } ?: emptyList()
                if (operators.isNotEmpty()) {
                    dao.insertOperators(operators)
                }

                // Filter inventories: only those in progress ('EM_ANDAMENTO')
                val inProgressInventories = data.inventories?.filter { inv ->
                    isInventoryInProgress(inv.status)
                } ?: emptyList()

                val inventories = inProgressInventories.map { inv ->
                    val maps = listOf(inv.configuracaoOperacional, inv.configuracaoOperacionalSnake, inv.config, inv.settings)
                    val filialVal = inv.filial ?: inv.subNome ?: inv.subNomeSnake ?: inv.empresa ?: inv.unidade
                        ?: getStringFromConfig(maps, listOf("filial", "subNome", "sub_nome", "empresa", "unidade", "branch", "unit", "store", "loja"))
                    InventoryEntity(
                        id = inv.id,
                        status = inv.status ?: "EM_ANDAMENTO",
                        nome = inv.nome,
                        subNome = inv.subNome ?: inv.subNomeSnake,
                        filial = filialVal,
                        allowMultiplication = getBooleanFromConfig(maps, listOf("allowMultiplication", "allow_multiplication", "multiplicador", "permitirMultiplicacao", "permitir_multiplicacao", "habilitaMultiplicador", "habilita_multiplicador", "multiplication"), true),
                        onlyRegisteredProducts = getBooleanFromConfig(maps, listOf("onlyRegisteredProducts", "only_registered_products", "somenteProdutosCadastrados", "somente_produtos_cadastrados", "apenasCadastrados", "apenas_cadastrados", "validarProdutos", "validar_produtos"), false),
                        confirmCountWithPartial = getBooleanFromConfig(maps, listOf("confirmCountWithPartial", "confirm_count_with_partial", "confirmacaoContagemParcial", "confirmacao_contagem_parcial", "contagemCega", "contagem_cega"), false),
                        controleLote = getBooleanFromConfig(maps, listOf("controleLote", "controle_lote", "loteAtivo", "lote_ativo", "pedirLote", "pedir_lote", "batchControl", "batch_control", "lote"), false),
                        controleValidade = getBooleanFromConfig(maps, listOf("controleValidade", "controle_validade", "validadeAtiva", "validade_ativa", "pedirValidade", "pedir_validade", "expirationControl", "expiration_control", "validade"), false),
                        controlePalete = getBooleanFromConfig(maps, listOf("controlePalete", "controle_palete", "palletControl", "pallet_control", "pedirPalete", "pedir_palete", "pallet", "palete"), false)
                    )
                }
                dao.clearInventories()
                dao.insertInventories(inventories)

                val allSectors = mutableListOf<SectorEntity>()
                val serverSections = mutableListOf<SectionEntity>()
                val allProducts = mutableListOf<ProductEntity>()
                
                for (inv in inProgressInventories) {
                    inv.sectors?.forEach { s ->
                        allSectors.add(SectorEntity(s.id, inv.id, s.name ?: "Desconhecido"))
                        s.sections?.forEach { sec ->
                            serverSections.add(SectionEntity(sec.code, s.id, inv.id, sec.status))
                        }
                    }
                    inv.products?.forEach { p ->
                        allProducts.add(ProductEntity(p.ean, inv.id, p.sap, p.descricao ?: "Desconhecido"))
                    }
                }
                
                dao.clearSectors()
                dao.insertSectors(allSectors)

                // MERGE: Preserve local sections that are pending sync
                val pendingSections = dao.getAllPendingSections()
                val pendingMap = pendingSections.associateBy { "${it.inventoryId}-${it.sectorId}-${it.code}" }
                
                val mergedSections = serverSections.map { serverSec ->
                    val key = "${serverSec.inventoryId}-${serverSec.sectorId}-${serverSec.code}"
                    pendingMap[key] ?: serverSec
                }
                
                dao.clearSections() // Clear old ones since we saved pending sync items in memory
                dao.insertSections(mergedSections)

                // Skip the slow automatic product sync loop. 
                // Products should be downloaded explicitly via the "Baixar Catálogo" button 
                // in InventorySelectionScreen for better performance.

                true
            } else {
                false
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            System.gc()
            false
        }
    }

    suspend fun sendPendingSyncQueue(): Int = withContext(Dispatchers.IO) {
        var sentCount = 0
        val pendingItems = dao.getAllSyncQueueItems()
        val gson = com.google.gson.Gson()
        val random = java.util.Random()

        for (item in pendingItems) {
            try {
                // Micro jitter/interval (150ms to 250ms) to prevent server concurrency bottleneck with 50-60 collectors
                val delayMs = 150L + random.nextInt(100)
                kotlinx.coroutines.delay(delayMs)

                // Try as CollectRequest first
                if (item.payloadJson.contains("\"operatorCpf\"") || item.payloadJson.contains("\"contagemNum\"")) {
                    val req = gson.fromJson(item.payloadJson, CollectRequest::class.java)
                    val response = api.sendCollectionItem(req)
                    if (response.isSuccessful) {
                        dao.deleteSyncQueueItem(item.id)
                        sentCount++
                    }
                } else {
                    val payload = gson.fromJson(item.payloadJson, CollectPayload::class.java)
                    val response = api.transmitCollection(payload)
                    if (response.isSuccessful) {
                        dao.deleteSyncQueueItem(item.id)
                        dao.updateSectionStatus(
                            code = payload.sectionCode,
                            sectorId = payload.sectorId,
                            inventoryId = payload.inventoryId,
                            status = "CONTADO",
                            operatorId = payload.operatorId,
                            operatorName = payload.operatorName,
                            isPendingSync = false,
                            syncErrorMessage = null
                        )
                        sentCount++
                    } else {
                        val errorBody = try { response.errorBody()?.string() } catch(e: Exception) { null }
                        val parsedMsg = try {
                            if (!errorBody.isNullOrBlank()) {
                                val json = org.json.JSONObject(errorBody)
                                if (json.has("message")) json.getString("message")
                                else if (json.has("error")) json.getString("error")
                                else errorBody
                            } else null
                        } catch(e: Exception) { errorBody }

                        val reason = if (!parsedMsg.isNullOrBlank()) {
                            parsedMsg
                        } else if (response.code() in listOf(400, 403, 409, 422)) {
                            "Transmissão bloqueada: a seção já consta preenchida/fechada no painel web. Solicite ao coordenador a reabertura e limpeza da seção para liberar o envio."
                        } else {
                            "Instabilidade ao conectar com o servidor (HTTP ${response.code()}). Verifique a rede e tente atualizar novamente."
                        }

                        dao.updateSectionPendingSync(
                            code = payload.sectionCode,
                            sectorId = payload.sectorId,
                            inventoryId = payload.inventoryId,
                            isPendingSync = true,
                            syncErrorMessage = reason
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Do not delete item from queue on connection drop / timeout
                try {
                    if (!item.payloadJson.contains("\"operatorCpf\"")) {
                        val payload = gson.fromJson(item.payloadJson, CollectPayload::class.java)
                        dao.updateSectionPendingSync(
                            code = payload.sectionCode,
                            sectorId = payload.sectorId,
                            inventoryId = payload.inventoryId,
                            isPendingSync = true,
                            syncErrorMessage = "Instabilidade ao conectar com o servidor (${e.localizedMessage ?: "Sem resposta"}). Verifique a rede e tente atualizar novamente."
                        )
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
        sentCount
    }

    suspend fun getProduct(ean: String, inventoryId: String = ""): ProductEntity? = withContext(Dispatchers.IO) {
        val cleanEan = ean.trim()
        var prod = dao.getProductByEan(cleanEan, inventoryId)
        if (prod == null && cleanEan.startsWith("0")) {
            val noZero = cleanEan.trimStart('0')
            if (noZero.isNotEmpty()) {
                prod = dao.getProductByEan(noZero, inventoryId)
            }
        }
        prod
    }

    suspend fun getOperator(id: String): OperatorEntity? = withContext(Dispatchers.IO) {
        dao.getOperatorById(id)
    }

    suspend fun findOperator(login: String): OperatorEntity? = withContext(Dispatchers.IO) {
        dao.findOperator(login)
    }

    suspend fun getOperatorsList(): List<OperatorEntity> = withContext(Dispatchers.IO) {
        dao.getAllOperators()
    }

    suspend fun getActiveInventories(): List<InventoryEntity> = withContext(Dispatchers.IO) {
        dao.getActiveInventories()
    }

    suspend fun getInventoryById(id: String): InventoryEntity? = withContext(Dispatchers.IO) {
        dao.getInventoryById(id)
    }

    suspend fun clearProductsForInventory(inventoryId: String) = withContext(Dispatchers.IO) {
        dao.clearProductsByInventoryId(inventoryId)
    }

    suspend fun getProductCountByInventory(inventoryId: String): Int = withContext(Dispatchers.IO) {
        dao.getProductCountByInventory(inventoryId)
    }

    suspend fun downloadProductsForInventory(
        inventoryId: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Limpa produtos antigos deste inventário para zerar a base local do inventário selecionado
            dao.clearProductsByInventoryId(inventoryId)

            var totalInserted = 0
            val chunk = mutableListOf<ProductEntity>()
            val CHUNK_SIZE = 5000

            var page = 1
            var offset = 0
            var hasMorePages = true
            var serverTotalItems: Int? = null

            val eanKeys = listOf("ean", "barcode", "gtin", "codigobarras", "codigo_barras", "cod_barras", "codigodebarras")
            val sapKeys = listOf("sap", "sku", "codigo", "codsap", "codigo_sap", "cod_sap", "cod")
            val descKeys = listOf("descricao", "description", "nome", "nomeproduto", "productname", "desc", "produto", "title")

            var firstEanPrevPage: String? = null
            val pageSizeExpected = 5000

            data class PagingStrategy(
                val name: String,
                val buildParams: (page: Int, offset: Int, size: Int) -> Map<String, String>
            )

            val candidateStrategies = listOf(
                PagingStrategy("page_limit") { p, _, s -> mapOf("page" to p.toString(), "limit" to s.toString()) },
                PagingStrategy("page_pageSize") { p, _, s -> mapOf("page" to p.toString(), "pageSize" to s.toString()) },
                PagingStrategy("offset_limit") { _, off, s -> mapOf("offset" to off.toString(), "limit" to s.toString()) },
                PagingStrategy("skip_take") { _, off, s -> mapOf("skip" to off.toString(), "take" to s.toString()) },
                PagingStrategy("page_per_page") { p, _, s -> mapOf("page" to p.toString(), "per_page" to s.toString()) },
                PagingStrategy("page_only") { p, _, _ -> mapOf("page" to p.toString()) },
                PagingStrategy("no_params") { _, _, _ -> emptyMap() }
            )

            var selectedStrategy: PagingStrategy? = null
            var initialResponse: Response<okhttp3.ResponseBody>? = null
            var lastHttpCode: Int? = null

            var lastErrorBody: String? = null
            var lastAttemptedUrl: String? = null

            for (strat in candidateStrategies) {
                try {
                    val params = strat.buildParams(1, 0, pageSizeExpected)
                    val res = api.getInventoryProductsMap(inventoryId, params)
                    lastAttemptedUrl = res.raw().request.url.toString()
                    if (res.isSuccessful && res.body() != null) {
                        selectedStrategy = strat
                        initialResponse = res
                        break
                    } else {
                        lastHttpCode = res.code()
                        try {
                            lastErrorBody = res.errorBody()?.string()
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    lastErrorBody = e.message
                }
            }

            if (selectedStrategy == null || initialResponse == null) {
                val serverBase = ApiClient.getBaseUrl()
                val errorSnippet = if (!lastErrorBody.isNullOrBlank()) {
                    val clean = lastErrorBody.take(400).replace("\n", " ").trim()
                    "\n\nDetalhe retornado pelo servidor:\n$clean"
                } else ""

                val errorMsg = if (lastHttpCode == 502) {
                    "Erro 502 (Bad Gateway) ao consultar o servidor.\n\n" +
                    "URL chamada: ${lastAttemptedUrl ?: "$serverBase/api/inventories/$inventoryId/products"}\n\n" +
                    "Causas comuns do Erro 502 no Backend:\n" +
                    "1. Verifique se o Coletor está apontando para o Servidor correto na tela de Configurações (ex: IP 177.153.67.9 vs URL do Cloud Run).\n" +
                    "2. O servidor Web pode ter sofrido um crash (erro no código Node.js/banco de dados). Verifique os logs do servidor Web.\n" +
                    "3. O banco de dados no servidor Web precisa de um índice na coluna do inventário para não travar na consulta de 1,5 milhão de itens.$errorSnippet"
                } else if (lastHttpCode != null) {
                    "Falha ao consultar API de produtos (HTTP $lastHttpCode).\nURL: ${lastAttemptedUrl ?: "$serverBase/api/inventories/$inventoryId/products"}$errorSnippet"
                } else {
                    "Falha de conexão com o servidor Web ($serverBase).\nVerifique se o servidor está online e acessível.$errorSnippet"
                }
                return@withContext Pair(false, errorMsg)
            }

            while (hasMorePages && page <= 50000) {
                var response: Response<okhttp3.ResponseBody>? = null

                if (page == 1) {
                    response = initialResponse
                } else {
                    var attempt = 0
                    var success = false

                    while (attempt < 3 && !success) {
                        attempt++
                        try {
                            val params = selectedStrategy.buildParams(page, offset, pageSizeExpected)
                            response = api.getInventoryProductsMap(inventoryId, params)
                            if (response.isSuccessful && response.body() != null) {
                                success = true
                            } else {
                                if (attempt < 3) kotlinx.coroutines.delay(1000L * attempt)
                            }
                        } catch (e: Exception) {
                            if (attempt < 3) kotlinx.coroutines.delay(1000L * attempt)
                        }
                    }

                    if (response == null || !response.isSuccessful) {
                        if (chunk.isNotEmpty()) {
                            dao.insertProducts(chunk)
                            chunk.clear()
                        }
                        break
                    }
                }

                val body = response?.body()
                if (body == null) {
                    if (page == 1) return@withContext Pair(false, "Resposta vazia do servidor.")
                    else break
                }

                var pageTotalItems = 0
                var firstEanThisPage: String? = null

                val inputStream = body.byteStream()
                java.io.InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    val jsonReader = com.google.gson.stream.JsonReader(reader)
                    jsonReader.isLenient = true

                    try {
                        val firstToken = jsonReader.peek()
                        if (firstToken == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
                            jsonReader.beginObject()
                            while (jsonReader.hasNext()) {
                                val name = jsonReader.nextName().lowercase()
                                val token = jsonReader.peek()
                                if (token == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                                    jsonReader.beginArray()
                                    while (jsonReader.hasNext()) {
                                        if (jsonReader.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
                                            jsonReader.beginObject()
                                            var ean: String? = null
                                            var sap: String? = null
                                            var descricao: String? = null

                                            while (jsonReader.hasNext()) {
                                                val propName = jsonReader.nextName()
                                                if (jsonReader.peek() == com.google.gson.stream.JsonToken.NULL) {
                                                    jsonReader.nextNull()
                                                    continue
                                                }

                                                val value = when (jsonReader.peek()) {
                                                    com.google.gson.stream.JsonToken.STRING -> jsonReader.nextString()
                                                    com.google.gson.stream.JsonToken.NUMBER -> jsonReader.nextString()
                                                    com.google.gson.stream.JsonToken.BOOLEAN -> jsonReader.nextBoolean().toString()
                                                    else -> {
                                                        jsonReader.skipValue()
                                                        continue
                                                    }
                                                }

                                                val lowerName = propName.lowercase()
                                                if (ean == null && eanKeys.contains(lowerName)) {
                                                    if (value.isNotBlank()) ean = value
                                                } else if (sap == null && sapKeys.contains(lowerName)) {
                                                    if (value.isNotBlank()) sap = value
                                                } else if (descricao == null && descKeys.contains(lowerName)) {
                                                    if (value.isNotBlank()) descricao = value
                                                }
                                            }
                                            jsonReader.endObject()

                                            val finalEan = ean ?: sap
                                            if (finalEan != null) {
                                                if (firstEanThisPage == null) {
                                                    firstEanThisPage = finalEan
                                                    if (page > 1 && firstEanThisPage == firstEanPrevPage) {
                                                        hasMorePages = false
                                                        break
                                                    }
                                                }

                                                pageTotalItems++
                                                chunk.add(
                                                    ProductEntity(
                                                        ean = finalEan,
                                                        inventoryId = inventoryId,
                                                        sap = sap,
                                                        descricao = descricao ?: "Sem Descrição"
                                                    )
                                                )
                                                totalInserted++

                                                if (totalInserted % 2000 == 0) {
                                                    val percent = serverTotalItems?.let { total ->
                                                        if (total > totalInserted) ((totalInserted * 100L) / total).toInt().coerceIn(0, 99)
                                                        else -1
                                                    } ?: -1
                                                    onProgress(percent, totalInserted)
                                                }

                                                if (chunk.size >= CHUNK_SIZE) {
                                                    dao.insertProducts(chunk)
                                                    chunk.clear()
                                                }
                                            }
                                        } else {
                                            jsonReader.skipValue()
                                        }
                                    }
                                    if (jsonReader.peek() == com.google.gson.stream.JsonToken.END_ARRAY) {
                                        jsonReader.endArray()
                                    }
                                } else if (token == com.google.gson.stream.JsonToken.NUMBER || token == com.google.gson.stream.JsonToken.STRING) {
                                    if (name == "total" || name == "total_records" || name == "total_items" || name == "count" || name == "totalrecords") {
                                        try {
                                            serverTotalItems = jsonReader.nextString().toIntOrNull()
                                        } catch (_: Exception) {
                                            jsonReader.skipValue()
                                        }
                                    } else {
                                        jsonReader.skipValue()
                                    }
                                } else {
                                    jsonReader.skipValue()
                                }
                            }
                            if (jsonReader.peek() == com.google.gson.stream.JsonToken.END_OBJECT) {
                                jsonReader.endObject()
                            }
                        } else if (firstToken == com.google.gson.stream.JsonToken.BEGIN_ARRAY) {
                            jsonReader.beginArray()
                            while (jsonReader.hasNext()) {
                                if (jsonReader.peek() == com.google.gson.stream.JsonToken.BEGIN_OBJECT) {
                                    jsonReader.beginObject()
                                    var ean: String? = null
                                    var sap: String? = null
                                    var descricao: String? = null

                                    while (jsonReader.hasNext()) {
                                        val propName = jsonReader.nextName()
                                        if (jsonReader.peek() == com.google.gson.stream.JsonToken.NULL) {
                                            jsonReader.nextNull()
                                            continue
                                        }

                                        val value = when (jsonReader.peek()) {
                                            com.google.gson.stream.JsonToken.STRING -> jsonReader.nextString()
                                            com.google.gson.stream.JsonToken.NUMBER -> jsonReader.nextString()
                                            com.google.gson.stream.JsonToken.BOOLEAN -> jsonReader.nextBoolean().toString()
                                            else -> {
                                                jsonReader.skipValue()
                                                continue
                                            }
                                        }

                                        val lowerName = propName.lowercase()
                                        if (ean == null && eanKeys.contains(lowerName)) {
                                            if (value.isNotBlank()) ean = value
                                        } else if (sap == null && sapKeys.contains(lowerName)) {
                                            if (value.isNotBlank()) sap = value
                                        } else if (descricao == null && descKeys.contains(lowerName)) {
                                            if (value.isNotBlank()) descricao = value
                                        }
                                    }
                                    jsonReader.endObject()

                                    val finalEan = ean ?: sap
                                    if (finalEan != null) {
                                        if (firstEanThisPage == null) {
                                            firstEanThisPage = finalEan
                                            if (page > 1 && firstEanThisPage == firstEanPrevPage) {
                                                hasMorePages = false
                                                break
                                            }
                                        }

                                        pageTotalItems++
                                        chunk.add(
                                            ProductEntity(
                                                ean = finalEan,
                                                inventoryId = inventoryId,
                                                sap = sap,
                                                descricao = descricao ?: "Sem Descrição"
                                            )
                                        )
                                        totalInserted++

                                        if (totalInserted % 2000 == 0) {
                                            val percent = serverTotalItems?.let { total ->
                                                if (total > totalInserted) ((totalInserted * 100L) / total).toInt().coerceIn(0, 99)
                                                else -1
                                            } ?: -1
                                            onProgress(percent, totalInserted)
                                        }

                                        if (chunk.size >= CHUNK_SIZE) {
                                            dao.insertProducts(chunk)
                                            chunk.clear()
                                        }
                                    }
                                } else {
                                    jsonReader.skipValue()
                                }
                            }
                            if (jsonReader.peek() == com.google.gson.stream.JsonToken.END_ARRAY) {
                                jsonReader.endArray()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Insere lote restante da página no Room apenas se a página não foi descartada por repetição
                if (chunk.isNotEmpty()) {
                    if (hasMorePages) {
                        dao.insertProducts(chunk)
                    }
                    chunk.clear()
                }

                // Atualiza progresso da interface
                val percent = serverTotalItems?.let { total ->
                    if (total > totalInserted) ((totalInserted * 100L) / total).toInt().coerceIn(0, 99)
                    else -1
                } ?: -1
                onProgress(percent, totalInserted)

                // Verificações de encerramento da paginação
                if (pageTotalItems == 0 || !hasMorePages) {
                    hasMorePages = false
                    break
                }

                // 1. Se a estratégia usada não envia parâmetros de paginação ("no_params"),
                // significa que o servidor entregou toda a base de uma única vez (ex: 118k itens em lote único).
                if (selectedStrategy.name == "no_params") {
                    hasMorePages = false
                    break
                }

                // 2. Se o servidor informou o total geral de registros e já atingimos o total
                if (serverTotalItems != null && totalInserted >= serverTotalItems) {
                    hasMorePages = false
                    break
                }

                // 3. Se não temos o total do servidor e a página retornou menos itens do que 100 (fim da lista)
                if (serverTotalItems == null && pageTotalItems < 100 && page > 1) {
                    hasMorePages = false
                    break
                }

                firstEanPrevPage = firstEanThisPage
                page++
                offset += pageTotalItems
            }

            // Garante que nenhum item remanescente fique fora do banco
            if (chunk.isNotEmpty()) {
                dao.insertProducts(chunk)
                chunk.clear()
            }

            val finalCount = dao.getProductCountByInventory(inventoryId)
            if (finalCount > 0) {
                Pair(true, "$finalCount produto(s) baixado(s) e armazenado(s) com sucesso!")
            } else {
                Pair(false, "Nenhum produto encontrado para o inventário $inventoryId.")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            System.gc()
            Pair(false, "Erro ao baixar produtos: ${t.localizedMessage ?: "Falha de conexão"}")
        }
    }

    suspend fun getSectors(): List<SectorEntity> = withContext(Dispatchers.IO) {
        dao.getAllSectors()
    }

    suspend fun getSectorsForInventory(inventoryId: String): List<SectorEntity> = withContext(Dispatchers.IO) {
        dao.getSectorsByInventory(inventoryId)
    }

    suspend fun getSectionsForInventory(inventoryId: String): List<SectionEntity> = withContext(Dispatchers.IO) {
        dao.getSectionsByInventory(inventoryId)
    }

    suspend fun updateSectionStatus(code: String, sectorId: String, inventoryId: String, status: String, operatorId: String?, operatorName: String?, isPendingSync: Boolean = false, syncErrorMessage: String? = null) = withContext(Dispatchers.IO) {
        dao.updateSectionStatus(code, sectorId, inventoryId, status, operatorId, operatorName, isPendingSync, syncErrorMessage)
    }

    suspend fun updateSectionPendingSync(code: String, sectorId: String, inventoryId: String, isPendingSync: Boolean, syncErrorMessage: String? = null) = withContext(Dispatchers.IO) {
        dao.updateSectionPendingSync(code, sectorId, inventoryId, isPendingSync, syncErrorMessage)
    }

    suspend fun saveLocalColetas(
        inventoryId: String,
        sectorId: String,
        sectionCode: String,
        operatorId: String,
        operatorName: String,
        items: List<ColetaItem>
    ) = withContext(Dispatchers.IO) {
        dao.deleteLocalColetas(inventoryId, sectorId, sectionCode, operatorId)
        val entities = items.map { item ->
            LocalColetaEntity(
                inventoryId = inventoryId,
                sectorId = sectorId,
                sectionCode = sectionCode,
                operatorId = operatorId,
                operatorName = operatorName,
                ean = item.ean,
                sap = item.sap,
                descricao = item.descricao,
                quantidade = item.quantidade,
                timestamp = item.timestamp,
                lote = item.lote,
                validade = item.validade,
                pallet = item.pallet,
                isTransmitted = item.isTransmitted
            )
        }
        dao.insertLocalColetas(entities)
    }

    suspend fun getLocalColetas(
        inventoryId: String,
        sectorId: String,
        sectionCode: String,
        operatorId: String
    ): List<LocalColetaEntity> = withContext(Dispatchers.IO) {
        dao.getLocalColetas(inventoryId, sectorId, sectionCode, operatorId)
    }

    private fun getBooleanFromConfig(maps: List<Map<String, Any>?>, keys: List<String>, defaultValue: Boolean): Boolean {
        for (map in maps) {
            if (map == null) continue
            for (key in keys) {
                val value = map[key] ?: map[key.lowercase()] ?: map[key.uppercase()] ?: map[key.replace("_", "")] ?: map[key.replace("_", "").lowercase()]
                if (value != null) {
                    if (value is Boolean) return value
                    if (value is String) {
                        return value.equals("true", ignoreCase = true) || value == "1" || value.equals("sim", ignoreCase = true) || value.equals("yes", ignoreCase = true)
                    }
                    if (value is Number) {
                        return value.toInt() == 1
                    }
                }
            }
        }
        return defaultValue
    }

    private fun getStringFromConfig(maps: List<Map<String, Any>?>, keys: List<String>): String? {
        for (map in maps) {
            if (map == null) continue
            for (key in keys) {
                val value = map[key] ?: map[key.lowercase()] ?: map[key.uppercase()]
                if (value != null && value is String && value.isNotBlank()) {
                    return value
                }
            }
        }
        return null
    }

    suspend fun importProductsFromUri(
        context: android.content.Context,
        inventoryId: String,
        uri: android.net.Uri,
        onProgress: (Int, Int) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            dao.clearProductsByInventoryId(inventoryId)

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Pair(false, "Não foi possível abrir o arquivo selecionado no aparelho.")

            var totalInserted = 0
            val chunk = mutableListOf<ProductEntity>()
            val CHUNK_SIZE = 5000

            var eanCol = -1
            var sapCol = -1
            var descCol = -1
            var delimiter = ';'

            val eanKeys = listOf("ean", "barcode", "gtin", "codigobarras", "codigo_barras", "cod_barras", "codigodebarras")
            val sapKeys = listOf("sap", "sku", "codigo", "codsap", "codigo_sap", "cod_sap", "cod")
            val descKeys = listOf("descricao", "description", "nome", "nomeproduto", "productname", "desc", "produto", "title")

            java.io.BufferedReader(java.io.InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var lineIndex = 0

                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    lineIndex++

                    if (lineIndex == 1) {
                        delimiter = when {
                            line.contains(";") -> ';'
                            line.contains("\t") -> '\t'
                            line.contains("|") -> '|'
                            else -> ','
                        }
                    }

                    val parts = line.split(delimiter).map { it.trim().trim('"') }
                    if (parts.isEmpty()) continue

                    if (lineIndex == 1) {
                        var foundHeader = false
                        parts.forEachIndexed { index, part ->
                            val lower = part.lowercase()
                            if (eanKeys.contains(lower)) { eanCol = index; foundHeader = true }
                            else if (sapKeys.contains(lower)) { sapCol = index; foundHeader = true }
                            else if (descKeys.contains(lower)) { descCol = index; foundHeader = true }
                        }
                        if (foundHeader) {
                            continue
                        } else {
                            if (parts.isNotEmpty()) {
                                for (i in parts.indices) {
                                    val valStr = parts[i]
                                    if (eanCol == -1 && valStr.all { it.isDigit() } && valStr.length in 8..14) {
                                        eanCol = i
                                    } else if (descCol == -1 && valStr.length > 15) {
                                        descCol = i
                                    } else if (sapCol == -1 && valStr.length in 1..15) {
                                        sapCol = i
                                    }
                                }
                            }
                        }
                    }

                    val eanIdx = if (eanCol != -1) eanCol else 0
                    val sapIdx = if (sapCol != -1) sapCol else (if (parts.size > 1 && eanIdx != 1) 1 else -1)
                    val descIdx = if (descCol != -1) descCol else (if (parts.size > 2) 2 else -1)

                    val ean = if (eanIdx in parts.indices) parts[eanIdx] else continue
                    if (ean.isBlank()) continue

                    val sap = if (sapIdx in parts.indices) parts[sapIdx] else null
                    val desc = if (descIdx in parts.indices) parts[descIdx] else "Sem Descrição"

                    chunk.add(
                        ProductEntity(
                            ean = ean,
                            inventoryId = inventoryId,
                            sap = sap,
                            descricao = if (!desc.isNullOrBlank()) desc else "Sem Descrição"
                        )
                    )
                    totalInserted++

                    if (totalInserted % 2000 == 0) {
                        onProgress(-1, totalInserted)
                    }

                    if (chunk.size >= CHUNK_SIZE) {
                        dao.insertProducts(chunk)
                        chunk.clear()
                    }
                }

                if (chunk.isNotEmpty()) {
                    dao.insertProducts(chunk)
                    chunk.clear()
                }
            }

            onProgress(-1, totalInserted)

            val finalCount = dao.getProductCountByInventory(inventoryId)
            if (finalCount > 0) {
                Pair(true, "$finalCount produto(s) importado(s) com sucesso a partir do arquivo local!")
            } else {
                Pair(false, "Nenhum produto válido encontrado no arquivo selecionado.")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            Pair(false, "Erro ao ler arquivo local: ${t.localizedMessage ?: "Formato de arquivo inválido"}")
        }
    }
}
