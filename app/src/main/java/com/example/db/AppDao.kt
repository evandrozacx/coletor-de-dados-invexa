package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE inventoryId = :inventoryId")
    suspend fun clearProductsByInventoryId(inventoryId: String)

    @Query("SELECT * FROM products WHERE (ean = :ean OR sap = :ean OR TRIM(ean) = TRIM(:ean)) AND (inventoryId = :inventoryId OR inventoryId = '' OR :inventoryId = '') LIMIT 1")
    suspend fun getProductByEan(ean: String, inventoryId: String): ProductEntity?

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("SELECT COUNT(*) FROM products WHERE inventoryId = :inventoryId")
    suspend fun getProductCountByInventory(inventoryId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperators(operators: List<OperatorEntity>)

    @Query("SELECT * FROM operators")
    suspend fun getAllOperators(): List<OperatorEntity>

    @Query("SELECT * FROM operators WHERE id = :id LIMIT 1")
    suspend fun getOperatorById(id: String): OperatorEntity?

    @Query("SELECT * FROM operators WHERE id = :login OR cpf = :login OR REPLACE(REPLACE(cpf, '.', ''), '-', '') = :login OR LOWER(name) = LOWER(:login) OR LOWER(name) LIKE '%' || LOWER(:login) || '%' LIMIT 1")
    suspend fun findOperator(login: String): OperatorEntity?

    @Query("DELETE FROM operators")
    suspend fun clearOperators()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSectors(sectors: List<SectorEntity>)

    @Query("SELECT * FROM sectors")
    suspend fun getAllSectors(): List<SectorEntity>

    @Query("SELECT * FROM sectors WHERE inventoryId = :inventoryId")
    suspend fun getSectorsByInventory(inventoryId: String): List<SectorEntity>

    @Query("DELETE FROM sectors")
    suspend fun clearSectors()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<SectionEntity>)

    @Query("SELECT * FROM sections WHERE inventoryId = :inventoryId")
    suspend fun getSectionsByInventory(inventoryId: String): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE sectorId = :sectorId AND inventoryId = :inventoryId")
    suspend fun getSectionsBySector(sectorId: String, inventoryId: String): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE isPendingSync = 1")
    suspend fun getAllPendingSections(): List<SectionEntity>

    @Query("UPDATE sections SET status = :status, countedByOperatorId = :operatorId, countedByOperatorName = :operatorName, isPendingSync = :isPendingSync, syncErrorMessage = :syncErrorMessage WHERE code = :code AND sectorId = :sectorId AND inventoryId = :inventoryId")
    suspend fun updateSectionStatus(code: String, sectorId: String, inventoryId: String, status: String, operatorId: String?, operatorName: String?, isPendingSync: Boolean = false, syncErrorMessage: String? = null)

    @Query("UPDATE sections SET isPendingSync = :isPendingSync, syncErrorMessage = :syncErrorMessage WHERE code = :code AND sectorId = :sectorId AND inventoryId = :inventoryId")
    suspend fun updateSectionPendingSync(code: String, sectorId: String, inventoryId: String, isPendingSync: Boolean, syncErrorMessage: String? = null)

    @Query("DELETE FROM sections")
    suspend fun clearSections()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventories(inventories: List<InventoryEntity>)

    @Query("SELECT * FROM inventories WHERE UPPER(REPLACE(REPLACE(TRIM(status), ' ', '_'), '-', '_')) IN ('EM_ANDAMENTO', 'ANDAMENTO', 'IN_PROGRESS', 'ABERTO', 'ATIVO', 'ACTIVE', 'OPEN')")
    suspend fun getActiveInventories(): List<InventoryEntity>

    @Query("DELETE FROM inventories")
    suspend fun clearInventories()

    @Query("SELECT * FROM inventories WHERE id = :id LIMIT 1")
    suspend fun getInventoryById(id: String): InventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncQueueItem(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue")
    suspend fun getAllSyncQueueItems(): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteSyncQueueItem(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalColetas(items: List<LocalColetaEntity>)

    @Query("DELETE FROM local_coletas WHERE inventoryId = :inventoryId AND sectorId = :sectorId AND sectionCode = :sectionCode AND operatorId = :operatorId")
    suspend fun deleteLocalColetas(inventoryId: String, sectorId: String, sectionCode: String, operatorId: String)

    @Query("SELECT * FROM local_coletas WHERE inventoryId = :inventoryId AND sectorId = :sectorId AND sectionCode = :sectionCode AND operatorId = :operatorId ORDER BY id DESC")
    suspend fun getLocalColetas(inventoryId: String, sectorId: String, sectionCode: String, operatorId: String): List<LocalColetaEntity>
}
