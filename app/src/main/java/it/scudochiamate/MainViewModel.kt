package it.scudochiamate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import it.scudochiamate.database.AppDatabase
import it.scudochiamate.database.BlockedCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).blockedCallDao()

    val allCalls: LiveData<List<BlockedCall>> = dao.getAllLive()
    val totalCount: LiveData<Int>   = dao.countLive()
    val foreignCount: LiveData<Int> = dao.countForeignLive()
    val spamCount: LiveData<Int>    = dao.countSpamLive()

    fun clearHistory() {
        CoroutineScope(Dispatchers.IO).launch { dao.deleteAll() }
    }

    fun deleteCall(call: BlockedCall) {
        CoroutineScope(Dispatchers.IO).launch { dao.delete(call) }
    }
}
