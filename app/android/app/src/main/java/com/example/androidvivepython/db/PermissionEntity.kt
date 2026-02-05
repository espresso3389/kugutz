package jp.espresso3389.kugutz.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "permissions")
data class PermissionEntity(
    @PrimaryKey val id: String,
    val tool: String,
    val detail: String,
    val scope: String,
    val status: String,
    val createdAt: Long
)
