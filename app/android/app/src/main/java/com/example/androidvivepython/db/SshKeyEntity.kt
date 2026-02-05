package jp.espresso3389.kugutz.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_keys")
data class SshKeyEntity(
    @PrimaryKey val fingerprint: String,
    val key: String,
    val label: String?,
    val expiresAt: Long?,
    val createdAt: Long
)
