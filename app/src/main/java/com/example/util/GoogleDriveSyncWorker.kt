package com.example.util

import android.content.Context
import androidx.work.*
import com.example.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class GoogleDriveSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME_INSTANT = "GoogleDriveInstantSync"
        const val WORK_NAME_PERIODIC = "GoogleDrivePeriodicSync"

        /**
         * Enqueues an instant one-time background sync when data modifications occur.
         * Guarantees network connectivity constraint and exponential retry backoff.
         */
        fun enqueueInstantSync(context: Context) {
            val driveManager = GoogleDriveBackupManager(context)
            if (!driveManager.isSyncEnabled()) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<GoogleDriveSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_INSTANT,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }

        /**
         * Enqueues periodic background sync with Google Drive.
         */
        fun enqueuePeriodicSync(context: Context, intervalDays: Long = 1L) {
            val driveManager = GoogleDriveBackupManager(context)
            if (!driveManager.isSyncEnabled()) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<GoogleDriveSyncWorker>(
                intervalDays, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
        }

        fun cancelSync(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME_INSTANT)
            wm.cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val driveManager = GoogleDriveBackupManager(context)
        if (!driveManager.isSyncEnabled()) {
            return@withContext Result.success()
        }

        val accountEmail = driveManager.getSavedAccountEmail()
        if (accountEmail.isNullOrBlank()) {
            return@withContext Result.failure()
        }

        try {
            BackupNotificationManager.showProgress(
                context,
                "جاري مزامنة ورفع البيانات إلى Google Drive...",
                50
            )

            val db = AppDatabase.getDatabase(context)
            val syncResult = driveManager.createAndUploadBackup(
                db = db,
                accountEmail = accountEmail,
                onProgress = { percent, stage ->
                    BackupNotificationManager.showProgress(context, stage, percent)
                }
            )

            if (syncResult.isSuccess) {
                BackupNotificationManager.showSuccess(
                    context,
                    "تمت المزامنة مع Google Drive بنجاح ✅",
                    syncResult.getOrDefault("تم حفظ وتحديث النسخة الاحتياطية في مجلد الحكيمي للأدوية والمستلزمات الطبية.")
                )
                Result.success()
            } else {
                val error = syncResult.exceptionOrNull()
                val isNetworkError = error?.message?.contains("NO_INTERNET", ignoreCase = true) == true ||
                        error?.message?.contains("timeout", ignoreCase = true) == true ||
                        error?.message?.contains("connection", ignoreCase = true) == true

                if (isNetworkError && runAttemptCount < 3) {
                    Result.retry()
                } else {
                    BackupNotificationManager.showError(
                        context,
                        "فشلت المزامنة مع Google Drive ❌",
                        error?.localizedMessage ?: "حدث خطأ أثناء مزامنة البيانات السحابية."
                    )
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                BackupNotificationManager.showError(
                    context,
                    "فشلت المزامنة مع Google Drive ❌",
                    e.localizedMessage ?: "حدث خطأ أثناء مزامنة البيانات السحابية."
                )
                Result.failure()
            }
        }
    }
}
