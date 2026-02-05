package jp.espresso3389.kugutz.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PermissionEntity::class, CredentialEntity::class, SshKeyEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun permissionDao(): PermissionDao
    abstract fun credentialDao(): CredentialDao
    abstract fun sshKeyDao(): SshKeyDao
}
