package com.example.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["ean", "inventoryId"]),
        Index(value = ["inventoryId"])
    ]
)
data class ProductEntity(
    val ean: String,
    val inventoryId: String = "",
    val sap: String?,
    val descricao: String,
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0
)

@Entity(tableName = "operators")
data class OperatorEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val cpf: String?,
    val senhaPreenchedores: String?
)

@Entity(tableName = "sectors", primaryKeys = ["id", "inventoryId"])
data class SectorEntity(
    val id: String,
    val inventoryId: String,
    val name: String
)

@Entity(tableName = "sections", primaryKeys = ["code", "sectorId", "inventoryId"])
data class SectionEntity(
    val code: String,
    val sectorId: String,
    val inventoryId: String,
    val status: String,
    val countedByOperatorId: String? = null,
    val countedByOperatorName: String? = null,
    val isPendingSync: Boolean = false,
    val syncErrorMessage: String? = null
)

@Entity(tableName = "inventories")
data class InventoryEntity(
    @PrimaryKey
    val id: String,
    val status: String,
    val nome: String? = null,
    val subNome: String? = null,
    val filial: String? = null,
    val allowMultiplication: Boolean = true,
    val onlyRegisteredProducts: Boolean = false,
    val confirmCountWithPartial: Boolean = false,
    val controleLote: Boolean = false,
    val controleValidade: Boolean = false,
    val controlePalete: Boolean = false
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val payloadJson: String // We will serialize CollectPayload to JSON
)

@Entity(tableName = "local_coletas")
data class LocalColetaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val inventoryId: String,
    val sectorId: String,
    val sectionCode: String,
    val operatorId: String,
    val operatorName: String,
    val ean: String,
    val sap: String? = null,
    val descricao: String,
    val quantidade: Double,
    val timestamp: String,
    val lote: String? = null,
    val validade: String? = null,
    val pallet: String? = null,
    val isTransmitted: Boolean = false
)
