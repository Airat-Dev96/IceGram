/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.proxy

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import org.icegram.messenger.IcegramConfig
import org.telegram.messenger.FileLog
import kotlin.coroutines.resume

class AutoProxyWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!IcegramConfig.isAutoProxyEnabled() || org.icegram.messenger.UltraEconomyController.isBackgroundWorkPaused()) {
            return Result.success()
        }

        return try {
            val result = suspendCancellableCoroutine { continuation ->
                AutoProxyController.getInstance().refresh(true) { refreshResult ->
                    if (continuation.isActive) {
                        continuation.resume(refreshResult)
                    }
                }
            }
            if (result.sourceAvailable) Result.success() else Result.retry()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            FileLog.e(t)
            Result.retry()
        }
    }
}
