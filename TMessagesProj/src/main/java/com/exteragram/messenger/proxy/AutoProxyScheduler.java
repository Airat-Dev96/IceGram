/* icegram additions. Licensed under GPL-3.0-or-later. */
package com.exteragram.messenger.proxy;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.icegram.messenger.IcegramConfig;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.concurrent.TimeUnit;

public final class AutoProxyScheduler {
    private static final String UNIQUE_WORK_NAME = "icegram_auto_proxy_refresh";

    private AutoProxyScheduler() {
    }

    public static void sync() {
        try {
            WorkManager workManager = WorkManager.getInstance(ApplicationLoader.applicationContext);
            if (!IcegramConfig.isAutoProxyEnabled()) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME);
                return;
            }

            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    AutoProxyWorker.class,
                    IcegramConfig.getAutoProxyIntervalHours(),
                    TimeUnit.HOURS)
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build();
            workManager.enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
