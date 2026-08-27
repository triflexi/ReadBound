package app.readbound.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.readbound.ReaderApplication

class WebDavSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = (applicationContext as ReaderApplication).container.sync.sync()
        return if (sync.isSuccess) Result.success() else Result.retry()
    }
}
