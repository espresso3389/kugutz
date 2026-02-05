package jp.espresso3389.kugutz.db

import android.content.Context
import androidx.room.Room
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

object DbProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }
    }

    private fun build(context: Context): AppDatabase {
        SQLiteDatabase.loadLibs(context)
        val passphrase = SqlcipherPassphraseManager(context).getOrCreatePassphrase()
        val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app.db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
    }
}
