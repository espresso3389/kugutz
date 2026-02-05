package jp.espresso3389.kugutz.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PermissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: PermissionEntity)

    @Query("SELECT * FROM permissions WHERE status = 'pending' ORDER BY createdAt")
    fun listPending(): List<PermissionEntity>

    @Query("SELECT * FROM permissions WHERE id = :id LIMIT 1")
    fun getById(id: String): PermissionEntity?

    @Update
    fun update(entity: PermissionEntity)
}
