package com.example

import android.content.Context
import com.example.db.AppDatabase
import com.example.db.SyncQueueEntity
import com.google.gson.Gson
import java.time.Instant

/**
 * Funções auxiliares baseadas nos exemplos de integração do sistema Invexa.
 */

// A) Autenticar Operador no Coletor Móvel
suspend fun autenticarOperador(context: Context, cpfOuNomeInformado: String, senha4Digitos: String): Operator? {
    try {
        val response = RetrofitClient.getApiService(context).getOperators()
        if (response.isSuccessful) {
            val operadores = response.body() ?: emptyList()
            val loginClean = cpfOuNomeInformado.trim()
            val loginDigits = loginClean.replace("\\D".toRegex(), "")
            
            // Procura o operador por CPF, ID ou Nome Completo
            val op = operadores.find { item ->
                val itemCpfDigits = item.cpf.replace("\\D".toRegex(), "")
                (loginDigits.isNotEmpty() && itemCpfDigits == loginDigits) ||
                item.id.equals(loginClean, ignoreCase = true) ||
                item.cpf.equals(loginClean, ignoreCase = true) ||
                item.nomeCompleto.equals(loginClean, ignoreCase = true) ||
                item.nomeCompleto.lowercase().contains(loginClean.lowercase())
            }
            
            if (op != null && (op.senhaPreenchedores.isBlank() || op.senhaPreenchedores == senha4Digitos)) {
                return op // Login autorizado
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null // Login falhou
}

// C) Validação local de login por seleção de operador
fun validarLoginColetor(
    operadorSelecionado: Operator,
    senhaDigitada: String
): Boolean {
    // Valida a senha de 4 dígitos com o operador selecionado pelo nome
    return operadorSelecionado.senhaPreenchedores == senhaDigitada.trim()
}

// B) Transmitir Bipe em Tempo Real (ou Fila Offline Sync)
suspend fun enviarBipe(
    inventoryId: String,
    operador: Operator,
    deviceId: String,
    sectorId: String,
    sectionCode: String,
    contagemNum: Int,
    barcode: String,
    quantidade: Double,
    lote: String? = null,
    validade: String? = null,
    context: Context? = null
): Boolean {
    val request = CollectRequest(
        inventoryId = inventoryId,
        operatorCpf = operador.cpf,
        operatorName = operador.nomeCompleto,
        deviceId = deviceId,
        sectorId = sectorId,
        sectionCode = sectionCode,
        contagemNum = contagemNum,
        barcode = barcode,
        quantidade = quantidade,
        timestamp = Instant.now().toString(),
        lote = lote,
        validade = validade
    )

    try {
        val apiService = if (context != null) RetrofitClient.getApiService(context) else ApiClient.apiService
        val response = apiService.sendCollectionItem(request)
        if (response.isSuccessful && response.body()?.success == true) {
            return true // Bipe gravado com sucesso no servidor
        }
    } catch (e: Exception) {
        // Em caso de falha de conexão, salve em banco de dados SQLite local (Room) para sincronizar quando houver sinal
        e.printStackTrace()
        if (context != null) {
            try {
                val db = AppDatabase.getDatabase(context)
                val gson = Gson()
                db.appDao().insertSyncQueueItem(
                    SyncQueueEntity(payloadJson = gson.toJson(request))
                )
            } catch (dbEx: Exception) {
                dbEx.printStackTrace()
            }
        }
    }
    return false
}
