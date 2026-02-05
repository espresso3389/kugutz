package jp.espresso3389.kugutz.perm

import android.content.Context
import jp.espresso3389.kugutz.db.CredentialEntity
import jp.espresso3389.kugutz.db.DbProvider

class CredentialStore(context: Context) {
    private val dao = DbProvider.get(context).credentialDao()

    fun set(name: String, value: String) {
        dao.upsert(
            CredentialEntity(
                name = name,
                value = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun get(name: String): CredentialEntity? {
        return dao.getByName(name)
    }

    fun delete(name: String) {
        dao.deleteByName(name)
    }

    fun list(): List<CredentialEntity> {
        return dao.listAll()
    }
}
