package com.example

import com.google.gson.annotations.SerializedName

// Modelos para Autenticação / Carga de Operadores (APIs Invexa)
data class Operator(
    @SerializedName("id") val id: String,
    @SerializedName("cpf") val cpf: String,
    @SerializedName("nomeCompleto") val nomeCompleto: String,
    @SerializedName("senhaPreenchedores") val senhaPreenchedores: String, // Senha de 4 dígitos
    @SerializedName("companyId") val companyId: String
)

// Modelo de envio de bipe / coleta realizada pelo operador
data class CollectRequest(
    @SerializedName("inventoryId") val inventoryId: String,
    @SerializedName("operatorCpf") val operatorCpf: String,
    @SerializedName("operatorName") val operatorName: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("sectorId") val sectorId: String,
    @SerializedName("sectionCode") val sectionCode: String,
    @SerializedName("contagemNum") val contagemNum: Int, // 1 para 1ª Contagem, 2 para 2ª Contagem
    @SerializedName("barcode") val barcode: String,
    @SerializedName("quantidade") val quantidade: Double,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("lote") val lote: String? = null,
    @SerializedName("validade") val validade: String? = null
)

// Resposta do servidor ao registrar bipe
data class CollectResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("item") val item: Any? = null
)

// Payload alternativo de coleta Invexa
data class ColetaPayload(
    @SerializedName("inventoryId") val inventoryId: String,
    @SerializedName("sectorId") val sectorId: String,
    @SerializedName("sectionCode") val sectionCode: String,
    @SerializedName("operatorId") val operatorId: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("barcode") val barcode: String,
    @SerializedName("quantidade") val quantidade: Double,
    @SerializedName("lote") val lote: String? = null,
    @SerializedName("validade") val validade: String? = null,
    @SerializedName("numPaletes") val numPaletes: Int? = null,
    @SerializedName("idContagem") val idContagem: String = "1"
)

// Modelos para Receber o Banco de Dados
data class DatabaseResponse(
    val inventories: List<InventoryDto>? = null,
    val operators: List<OperatorDto>? = null,
    val devices: List<DeviceDto>? = null
)

data class InventoryDto(
    val id: String,
    val status: String,
    @SerializedName("nome")
    val nome: String? = null,
    @SerializedName("subNome")
    val subNome: String? = null,
    @SerializedName("sub_nome")
    val subNomeSnake: String? = null,
    @SerializedName("filial")
    val filial: String? = null,
    @SerializedName("empresa")
    val empresa: String? = null,
    @SerializedName("unidade")
    val unidade: String? = null,
    val sectors: List<SectorDto>? = null,
    val products: List<ProductDto>? = null,
    @SerializedName("configuracaoOperacional")
    val configuracaoOperacional: Map<String, Any>? = null,
    @SerializedName("configuracao_operacional")
    val configuracaoOperacionalSnake: Map<String, Any>? = null,
    @SerializedName("config")
    val config: Map<String, Any>? = null,
    @SerializedName("settings")
    val settings: Map<String, Any>? = null
)

data class SectorDto(
    val id: String,
    @SerializedName("nome")
    val name: String? = null,
    val sections: List<SectionDto>? = null
)

data class SectionDto(
    val code: String,
    val status: String
)

data class ProductDto(
    val ean: String,
    val sap: String? = null,
    val descricao: String? = null
)

data class OperatorDto(
    val id: String,
    val nomeCompleto: String? = null,
    val name: String? = null,
    val cpf: String? = null,
    val senhaPreenchedores: String? = null
)

data class DeviceDto(
    val id: String,
    val name: String? = null
)

// Modelos para Envio (Coleta)
data class CollectPayload(
    val inventoryId: String,
    val sectorId: String,
    val sectionCode: String,
    val operatorId: String,
    val operatorName: String,
    val collectorNumber: String,
    val startTime: String,
    val endTime: String,
    val items: List<ColetaItem>
)

data class ColetaItem(
    val ean: String,
    val sap: String? = null,
    val descricao: String,
    val quantidade: Double,
    val timestamp: String,
    val operatorId: String,
    val operatorName: String,
    // Campos opcionais
    val lote: String? = null,
    val validade: String? = null,
    val pallet: String? = null,
    val isTransmitted: Boolean = false
)
