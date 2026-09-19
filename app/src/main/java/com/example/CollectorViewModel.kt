package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.AppDatabase
import com.example.db.SyncQueueEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class CollectorViewModel(application: Application) : AndroidViewModel(application) {
    val scannedItems = mutableStateListOf<ColetaItem>()
    
    var currentSectionCode = "0001"
    var operatorName: String
        get() = _operatorName
        set(value) {
            _operatorName = value
            setLastOperatorName(value)
        }
    private var _operatorName = ""

    fun getLastOperatorName(): String {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_operator_name", "") ?: ""
    }

    init {
        _operatorName = getLastOperatorName()
        ApiClient.setBaseUrl(getServerUrl())
        ApiClient.updateTenantToken(getTenantId())
    }

    private fun setLastOperatorName(name: String) {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_operator_name", name).apply()
    }
    var operatorId = ""
    var operatorCpf = ""
    var inventoryId = ""
    var sectorId = ""

    fun getServerUrl(): String {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        var url = prefs.getString("server_url", "https://ais-pre-xsa5q4sp75rasnv4bcblyd-465092240318.us-west2.run.app/") ?: ""
        if (url.isBlank()) {
            url = "https://ais-pre-xsa5q4sp75rasnv4bcblyd-465092240318.us-west2.run.app/"
            prefs.edit().putString("server_url", url).apply()
        }
        return url
    }

    fun setServerUrl(url: String) {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("server_url", url).apply()
        ApiClient.setBaseUrl(url)
    }

    fun getTenantId(): String {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        return prefs.getString("tenant_id", "default_tenant") ?: "default_tenant"
    }

    fun setTenantId(tenantId: String) {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("tenant_id", tenantId).apply()
        ApiClient.updateTenantToken(tenantId)
    }

    fun getAllowMultiplication(): Boolean {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("allow_multiplication", false)
    }

    fun setAllowMultiplication(value: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("allow_multiplication", value).apply()
    }

    fun getOnlyRegisteredProducts(): Boolean {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("only_registered_products", true)
    }

    fun setOnlyRegisteredProducts(value: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("only_registered_products", value).apply()
    }

    fun getKeyboardIsAlphanumeric(): Boolean {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("keyboard_is_alphanumeric", false)
    }

    fun setKeyboardIsAlphanumeric(value: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("keyboard_is_alphanumeric", value).apply()
    }

    fun getConfirmCountWithPartial(): Boolean {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("confirm_count_with_partial", false)
    }

    fun setConfirmCountWithPartial(value: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("confirm_count_with_partial", value).apply()
    }

    fun getCollectorNumber(): String {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        return prefs.getString("collector_number", "01") ?: "01"
    }

    fun setCollectorNumber(number: String) {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        val formatted = number.trim().ifBlank { "01" }
        prefs.edit().putString("collector_number", formatted).apply()
    }

    fun getScannerTimeoutMs(): Long {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("scanner_timeout_ms", 200L)
    }

    fun setScannerTimeoutMs(value: Long) {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("scanner_timeout_ms", value).apply()
    }

    private val _isDarkMode = MutableStateFlow(getSavedIsDarkMode())
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun getSavedIsDarkMode(): Boolean {
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_dark_mode", true)
    }

    fun toggleTheme() {
        setDarkMode(!_isDarkMode.value)
    }

    fun setDarkMode(isDark: Boolean) {
        _isDarkMode.value = isDark
        val prefs = getApplication<Application>().getSharedPreferences("coletor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_dark_mode", isDark).apply()
    }

    private val _scanEvent = MutableSharedFlow<Boolean>()
    val scanEvent: SharedFlow<Boolean> = _scanEvent.asSharedFlow()

    private val _controleLote = MutableStateFlow(false)
    val controleLote: StateFlow<Boolean> = _controleLote.asStateFlow()

    private val _controleValidade = MutableStateFlow(false)
    val controleValidade: StateFlow<Boolean> = _controleValidade.asStateFlow()

    private val _controlePalete = MutableStateFlow(false)
    val controlePalete: StateFlow<Boolean> = _controlePalete.asStateFlow()

    fun getControleLote(): Boolean = _controleLote.value
    fun getControleValidade(): Boolean = _controleValidade.value
    fun getControlePalete(): Boolean = _controlePalete.value

    fun selectInventory(id: String) {
        inventoryId = id
        viewModelScope.launch {
            val inv = repository.getInventoryById(id)
            if (inv != null) {
                // Do not override local user preferences with API defaults
                // setAllowMultiplication(inv.allowMultiplication)
                // setOnlyRegisteredProducts(inv.onlyRegisteredProducts)
                // setConfirmCountWithPartial(inv.confirmCountWithPartial)
                _controleLote.value = inv.controleLote
                _controleValidade.value = inv.controleValidade
                _controlePalete.value = inv.controlePalete
            }
        }
    }
    
    private val repository = Repository(application)
    private val dao = AppDatabase.getDatabase(application).appDao()
    private val gson = Gson()
    val networkMonitor = NetworkMonitor(application)

    private val _isOnline = MutableStateFlow(networkMonitor.isCurrentlyConnected())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        // Monitor network connectivity in real time
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _isOnline.value = online
            }
        }

        // Automatically sync on startup or login
        viewModelScope.launch {
            if (!_isSyncing.value && _isOnline.value) {
                syncDataSuspend()
            }
        }
    }

    suspend fun syncDataSuspend(): Boolean {
        if (_isSyncing.value) return false
        _isSyncing.value = true
        _syncMessage.value = "Sincronizando dados..."
        return try {
            val sentPending = repository.sendPendingSyncQueue()
            val success = repository.syncDatabase()
            _syncMessage.value = if (success) {
                if (sentPending > 0) "Sincronizado! $sentPending transmissão(ões) enviada(s)."
                else "Sincronização concluída com sucesso!"
            } else {
                "Falha na sincronização com o servidor."
            }
            success
        } catch (e: Exception) {
            _syncMessage.value = "Erro ao sincronizar: ${e.localizedMessage}"
            false
        } finally {
            _isSyncing.value = false
        }
    }

    fun syncData(onComplete: ((Boolean) -> Unit)? = null) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            val success = syncDataSuspend()
            onComplete?.invoke(success)
        }
    }

    fun loadScannedItems() {
        viewModelScope.launch {
            val localItems = withContext(Dispatchers.IO) {
                repository.getLocalColetas(inventoryId, sectorId, currentSectionCode, operatorId)
            }
            scannedItems.clear()
            scannedItems.addAll(localItems.map { entity ->
                ColetaItem(
                    ean = entity.ean,
                    sap = entity.sap,
                    descricao = entity.descricao,
                    quantidade = entity.quantidade,
                    timestamp = entity.timestamp,
                    operatorId = entity.operatorId,
                    operatorName = entity.operatorName,
                    lote = entity.lote,
                    validade = entity.validade,
                    pallet = entity.pallet,
                    isTransmitted = entity.isTransmitted
                )
            })
        }
    }

    private fun saveCurrentItemsToDb() {
        val currentItemsList = scannedItems.toList()
        viewModelScope.launch {
            repository.saveLocalColetas(
                inventoryId = inventoryId,
                sectorId = sectorId,
                sectionCode = currentSectionCode,
                operatorId = operatorId,
                operatorName = operatorName,
                items = currentItemsList
            )
        }
    }

    fun removeItem(item: ColetaItem) {
        scannedItems.remove(item)
        saveCurrentItemsToDb()
    }

    fun clearScannedItems() {
        scannedItems.clear()
        saveCurrentItemsToDb()
    }

    private fun currentTimestampString(minusSeconds: Long = 0): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.now().minusSeconds(minusSeconds).toString()
        } else {
            val date = java.util.Date(System.currentTimeMillis() - (minusSeconds * 1000))
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(date)
        }
    }

    fun addBarcode(ean: String, qty: Double, contagemNum: Int = 1, lote: String? = null, validade: String? = null, pallet: String? = null) {
        viewModelScope.launch {
            val product = repository.getProduct(ean, inventoryId)
            
            if (getOnlyRegisteredProducts() && product == null) {
                _scanEvent.emit(false)
                return@launch
            }

            scannedItems.add(0, ColetaItem(
                ean = ean,
                sap = product?.sap,
                descricao = product?.descricao ?: "PRODUTO NÃO CADASTRADO",
                quantidade = qty,
                timestamp = currentTimestampString(),
                operatorId = operatorId,
                operatorName = operatorName,
                lote = lote,
                validade = validade,
                pallet = pallet
            ))
            saveCurrentItemsToDb()
            _scanEvent.emit(true)
        }
    }

    suspend fun login(login: String, passwordEntered: String): Int {
        // Tenta autenticar operador via API Invexa online primeiro
        val onlineOp = autenticarOperador(getApplication(), login, passwordEntered)
        if (onlineOp != null) {
            operatorId = onlineOp.id
            operatorName = onlineOp.nomeCompleto
            operatorCpf = onlineOp.cpf
            return 0
        }

        // Fallback para o banco local Room
        val op = repository.findOperator(login)
        if (op == null) {
            return 1 // Operador não encontrado
        }
        val expectedPassword = op.senhaPreenchedores
        if (!expectedPassword.isNullOrBlank() && expectedPassword != passwordEntered) {
            return 2 // Senha incorreta
        }
        operatorId = op.id
        operatorName = op.name
        operatorCpf = op.cpf ?: login
        return 0
    }

    suspend fun getActiveInventories(): List<com.example.db.InventoryEntity> {
        return repository.getActiveInventories()
    }

    private val _downloadProgress = MutableStateFlow(-1)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()
    
    private val _downloadItemCount = MutableStateFlow(0)
    val downloadItemCount: StateFlow<Int> = _downloadItemCount.asStateFlow()

    suspend fun downloadProductsForInventory(invId: String): Pair<Boolean, String> {
        _downloadProgress.value = 0
        _downloadItemCount.value = 0
        val res = repository.downloadProductsForInventory(invId) { percent, items ->
            _downloadProgress.value = percent
            _downloadItemCount.value = items
        }
        _downloadProgress.value = -1
        return res
    }

    suspend fun importProductsFromUri(context: Context, invId: String, uri: Uri): Pair<Boolean, String> {
        _downloadProgress.value = -1
        _downloadItemCount.value = 0
        val res = repository.importProductsFromUri(context, invId, uri) { percent, items ->
            _downloadProgress.value = percent
            _downloadItemCount.value = items
        }
        _downloadProgress.value = -1
        return res
    }

    suspend fun clearProductsForInventory(invId: String) {
        repository.clearProductsForInventory(invId)
    }

    suspend fun getProductCountForInventory(invId: String): Int {
        return repository.getProductCountByInventory(invId)
    }

    suspend fun getOperatorsList(): List<com.example.db.OperatorEntity> {
        return repository.getOperatorsList()
    }

    suspend fun getActiveSectors(): List<com.example.db.SectorEntity> {
        return repository.getSectors()
    }

    suspend fun getSectorsForCurrentInventory(): List<com.example.db.SectorEntity> {
        return repository.getSectorsForInventory(inventoryId)
    }

    suspend fun getSectionsForCurrentInventory(): List<com.example.db.SectionEntity> {
        return repository.getSectionsForInventory(inventoryId)
    }

    suspend fun transmitSection(): Pair<Boolean, String?> {
        if (_isTransmitting.value) {
            return Pair(false, "Transmissão já em andamento...")
        }
        if (inventoryId.isBlank() || sectorId.isBlank() || currentSectionCode.isBlank()) {
            return Pair(false, "Erro: Inventário, Setor ou Seção não foram selecionados.")
        }

        // Filtra apenas os novos produtos contados que ainda não foram transmitidos
        val itemsToSend = scannedItems.filter { !it.isTransmitted }
        if (itemsToSend.isEmpty()) {
            return Pair(false, "Nenhum produto novo nesta seção para transmitir.")
        }

        _isTransmitting.value = true
        try {
            val payload = CollectPayload(
                inventoryId = inventoryId,
                sectorId = sectorId,
                sectionCode = currentSectionCode,
                operatorId = operatorId,
                operatorName = operatorName,
                collectorNumber = getCollectorNumber(),
                startTime = currentTimestampString(120),
                endTime = currentTimestampString(),
                items = itemsToSend
            )
            
            android.util.Log.d("INVEXA_POST", "Transmitindo para Servidor - InventoryId: ${payload.inventoryId}, Section: ${payload.sectionCode}, Operator: ${payload.operatorId}, Novos Itens: ${itemsToSend.size}")

            var sentOnline = false
            var errorMessage: String? = null

            try {
                // Tenta enviar online
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.getApiService(getApplication()).transmitCollection(payload)
                }
                if (response.isSuccessful) {
                    // Sucesso online: Marca os itens enviados como transmitidos
                    scannedItems.forEachIndexed { index, item ->
                        if (!item.isTransmitted) {
                            scannedItems[index] = item.copy(isTransmitted = true)
                        }
                    }
                    saveCurrentItemsToDb()
                    sentOnline = true
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

                    val codeStr = "HTTP ${response.code()}"
                    errorMessage = if (!parsedMsg.isNullOrBlank()) "$codeStr: $parsedMsg" else "$codeStr: Servidor não aceitou a transmissão."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Falha de conexão com o servidor (${e.localizedMessage ?: "Servidor/Internet offline"})"
            }
            
            // Se não foi transmitido online (sem internet OU conflito no servidor), salva offline com segurança
            if (!sentOnline) {
                saveOffline(payload)
            }
            
            // Atualiza localmente o status da seção
            withContext(Dispatchers.IO) {
                repository.updateSectionStatus(
                    code = currentSectionCode,
                    sectorId = sectorId,
                    inventoryId = inventoryId,
                    status = "CONTADO",
                    operatorId = operatorId,
                    operatorName = operatorName,
                    isPendingSync = !sentOnline,
                    syncErrorMessage = if (sentOnline) null else errorMessage
                )
            }

            if (sentOnline) {
                return Pair(true, "Transmitido e recebido com sucesso no servidor!")
            } else {
                return Pair(false, errorMessage ?: "Não foi possível conectar ao servidor (salvo apenas localmente).")
            }
        } finally {
            _isTransmitting.value = false
        }
    }

    suspend fun reopenSection(sectionCode: String, targetSectorId: String, passwordEntered: String): Boolean {
        return withContext(Dispatchers.IO) {
            val sections = repository.getSectionsForInventory(inventoryId)
            val section = sections.find { it.code == sectionCode && it.sectorId == targetSectorId } ?: return@withContext false
            val countedByOpId = section.countedByOperatorId ?: operatorId // Fallback to current operator ID if null
            
            val op = repository.getOperator(countedByOpId) ?: return@withContext false
            val expectedPassword = op.senhaPreenchedores ?: ""
            if (expectedPassword.trim() == passwordEntered.trim()) {
                repository.updateSectionStatus(sectionCode, targetSectorId, inventoryId, "PENDENTE", null, null)
                true
            } else {
                false
            }
        }
    }

    suspend fun exportSectionToUri(context: Context, uri: Uri): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val itemsList = scannedItems.toList()
            
            val exportItems = itemsList.map { item ->
                val qty: Any = if (item.quantidade % 1.0 == 0.0) {
                    item.quantidade.toInt()
                } else {
                    item.quantidade
                }
                mapOf(
                    "ean" to item.ean,
                    "sap" to item.sap,
                    "descricao" to item.descricao,
                    "quantidade" to qty
                )
            }

            val payload = mapOf(
                "operatorId" to operatorId,
                "operatorName" to operatorName,
                "collectorNumber" to getCollectorNumber(),
                "startTime" to (if (itemsList.isNotEmpty()) itemsList.last().timestamp else currentTimestampString(120)),
                "endTime" to (if (itemsList.isNotEmpty()) itemsList.first().timestamp else currentTimestampString()),
                "items" to exportItems
            )

            val jsonString = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(payload)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            Pair(true, "Arquivo da Seção $currentSectionCode exportado com sucesso!\n\nEste arquivo contém todos os dados da seção no formato exato requerido pelo painel para importação manual.")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Erro ao exportar arquivo da seção: ${e.localizedMessage}")
        }
    }

    private suspend fun saveOffline(payload: CollectPayload) = withContext(Dispatchers.IO) {
        val json = gson.toJson(payload)
        dao.insertSyncQueueItem(SyncQueueEntity(payloadJson = json))
        
        withContext(Dispatchers.Main) {
            scannedItems.forEachIndexed { index, item ->
                if (!item.isTransmitted) {
                    scannedItems[index] = item.copy(isTransmitted = true)
                }
            }
            saveCurrentItemsToDb()
        }
    }
}

