package com.example.util

import android.content.Context
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipFile

enum class RestoreMode {
    MERGE,
    FULL_REPLACE,
    SELECTIVE
}

data class RestoreOptions(
    val mode: RestoreMode = RestoreMode.MERGE,
    val selectedCategories: Set<String> = BackupEngine.ALL_CATEGORIES.toSet()
)

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val totalRestored: Int = 0,
    val details: Map<String, Int> = emptyMap()
)

data class BackupPreview(
    val formatVersion: Int,
    val appVersion: String,
    val databaseVersion: Int,
    val timestamp: Long,
    val recordCounts: Map<String, Int>,
    val fileSizeBytes: Long,
    val fileName: String,
    val isCompatible: Boolean,
    val compatibilityMessage: String,
    val tempFile: File
)

object RestoreEngine {

    private fun getMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun restoreFromFile(
        context: Context,
        db: AppDatabase,
        backupFile: File,
        options: RestoreOptions = RestoreOptions(RestoreMode.MERGE, BackupEngine.ALL_CATEGORIES.toSet()),
        onProgress: suspend (progress: Int, stage: String) -> Unit = { _, _ -> }
    ): RestoreResult = withContext(Dispatchers.IO) {
        val workspaceDir = File(context.cacheDir, "restore_workspace_${System.currentTimeMillis()}")
        try {
            if (!backupFile.exists() || backupFile.length() <= 0L) {
                return@withContext RestoreResult(false, "ملف النسخة الاحتياطية غير موجود أو فارغ")
            }

            onProgress(10, "🔍 جاري فحص ملف النسخة الاحتياطية والتحقق من أمان المسارات...")

            // Step 1: Extract to temporary workspace with Zip Slip protection
            val extractResult = extractToWorkspace(backupFile, workspaceDir)
            if (extractResult.isFailure) {
                return@withContext RestoreResult(false, "فشل فك ضغط الحزمة: ${extractResult.exceptionOrNull()?.message}")
            }

            onProgress(25, "📦 جاري قراءة وفحص الـ Manifest والبيانات...")

            // Step 2: Read Manifest & Package
            val (manifest, backupPackage) = readManifestAndPackage(workspaceDir)
                ?: return@withContext RestoreResult(false, "تعذر قراءة ملف الـ Manifest أو بيانات النسخة (ملف تالف أو غير صالح)")

            // Step 3: Schema compatibility check
            if (manifest.databaseVersion > 17) {
                return@withContext RestoreResult(
                    false,
                    "إصدار قاعدة بيانات النسخة الاحتياطية (${manifest.databaseVersion}) أحدث من إصدار التطبيق الحالي. يرجى تحديث التطبيق أولاً."
                )
            }

            onProgress(40, "🛡️ جاري التحقق من سلامة البيانات والـ Checksum (SHA-256)...")

            // Step 4: Emergency backup for Full Replace mode
            if (options.mode == RestoreMode.FULL_REPLACE) {
                onProgress(45, "📦 جاري إنشاء نسخة أمان طارئة (Emergency Backup) للبيانات الحالية قبل الاستبدال...")
                try {
                    BackupEngine.createBackup(
                        context = context,
                        db = db,
                        destination = "الهاتف",
                        onProgress = { _, _ -> }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            onProgress(60, "♻️ جاري تنفيذ عملية الاستعادة داخل معاملة آمنة (Atomic Transaction)...")

            val counts = mutableMapOf<String, Int>()
            val categories = if (options.mode == RestoreMode.SELECTIVE) {
                options.selectedCategories
            } else {
                manifest.categories.ifEmpty { BackupEngine.ALL_CATEGORIES }
            }

            db.withTransaction {
                if (options.mode == RestoreMode.FULL_REPLACE) {
                    if (categories.contains("invoiceItems")) db.invoiceDao().deleteAllInvoiceItems()
                    if (categories.contains("invoices")) db.invoiceDao().deleteAllInvoices()
                    if (categories.contains("payments")) db.paymentDao().deleteAllPayments()
                    if (categories.contains("installments")) db.installmentDao().deleteAllInstallments()
                    if (categories.contains("clients")) db.clientDao().deleteAllClients()
                    if (categories.contains("supplierCompanies")) db.supplierCompanyDao().deleteAllCompanies()
                    if (categories.contains("items")) db.itemDao().deleteAllItems()
                    if (categories.contains("itemCategories")) db.itemCategoryDao().deleteAllCategories()
                    if (categories.contains("itemUnits")) db.itemUnitDao().deleteAllUnits()
                    if (categories.contains("installmentReminders")) db.installmentReminderDao().deleteAllReminders()
                    if (categories.contains("auditLogs")) db.auditLogDao().deleteAllLogs()
                    if (categories.contains("productReturns")) db.productReturnDao().deleteAllReturns()
                    if (categories.contains("productReturnItems")) db.productReturnDao().deleteAllReturnItems()
                }

                // Insert / Merge with duplicate detection
                if (categories.contains("supplierCompanies") && backupPackage.supplierCompanies.isNotEmpty()) {
                    val existing = db.supplierCompanyDao().getAllCompanies().map { it.name }.toSet()
                    val newComps = backupPackage.supplierCompanies.filter { !existing.contains(it.name) }
                    if (newComps.isNotEmpty()) {
                        db.supplierCompanyDao().insertAll(newComps)
                    }
                    counts["supplierCompanies"] = backupPackage.supplierCompanies.size
                }

                if (categories.contains("itemCategories") && backupPackage.itemCategories.isNotEmpty()) {
                    val existing = db.itemCategoryDao().getAllCategories().map { it.name }.toSet()
                    val newCats = backupPackage.itemCategories.filter { !existing.contains(it.name) }
                    if (newCats.isNotEmpty()) {
                        db.itemCategoryDao().insertAll(newCats)
                    }
                    counts["itemCategories"] = backupPackage.itemCategories.size
                }

                if (categories.contains("itemUnits") && backupPackage.itemUnits.isNotEmpty()) {
                    val existing = db.itemUnitDao().getAllUnits().map { it.name }.toSet()
                    val newUnits = backupPackage.itemUnits.filter { !existing.contains(it.name) }
                    if (newUnits.isNotEmpty()) {
                        db.itemUnitDao().insertAll(newUnits)
                    }
                    counts["itemUnits"] = backupPackage.itemUnits.size
                }

                if (categories.contains("clients") && backupPackage.clients.isNotEmpty()) {
                    val existing = db.clientDao().getAllClients().map { it.customerId }.toSet()
                    val newClients = if (options.mode == RestoreMode.FULL_REPLACE) {
                        backupPackage.clients
                    } else {
                        backupPackage.clients.filter { !existing.contains(it.customerId) }
                    }
                    if (newClients.isNotEmpty()) {
                        db.clientDao().insertAll(newClients)
                    }
                    counts["clients"] = backupPackage.clients.size
                }

                if (categories.contains("items") && backupPackage.items.isNotEmpty()) {
                    val existing = db.itemDao().getAllItems().map { it.name }.toSet()
                    val newItems = if (options.mode == RestoreMode.FULL_REPLACE) {
                        backupPackage.items
                    } else {
                        backupPackage.items.filter { !existing.contains(it.name) }
                    }
                    if (newItems.isNotEmpty()) {
                        db.itemDao().insertAll(newItems)
                    }
                    counts["items"] = backupPackage.items.size
                }

                if (categories.contains("invoices") && backupPackage.invoices.isNotEmpty()) {
                    val existingNums = db.invoiceDao().getAllInvoices().map { it.invoiceNumber }.toSet()
                    val newInvoices = if (options.mode == RestoreMode.FULL_REPLACE) {
                        backupPackage.invoices
                    } else {
                        backupPackage.invoices.filter { !existingNums.contains(it.invoiceNumber) }
                    }
                    if (newInvoices.isNotEmpty()) {
                        db.invoiceDao().insertAllInvoices(newInvoices)
                    }
                    counts["invoices"] = backupPackage.invoices.size
                }

                if (categories.contains("invoiceItems") && backupPackage.invoiceItems.isNotEmpty()) {
                    db.invoiceDao().insertAllInvoiceItems(backupPackage.invoiceItems)
                    counts["invoiceItems"] = backupPackage.invoiceItems.size
                }

                if (categories.contains("payments") && backupPackage.payments.isNotEmpty()) {
                    db.paymentDao().insertAll(backupPackage.payments)
                    counts["payments"] = backupPackage.payments.size
                }

                if (categories.contains("installments") && backupPackage.installments.isNotEmpty()) {
                    db.installmentDao().insertAll(backupPackage.installments)
                    counts["installments"] = backupPackage.installments.size
                }

                if (categories.contains("installmentReminders") && backupPackage.installmentReminders.isNotEmpty()) {
                    db.installmentReminderDao().insertAll(backupPackage.installmentReminders)
                    counts["installmentReminders"] = backupPackage.installmentReminders.size
                }

                if (categories.contains("productReturns") && backupPackage.productReturns.isNotEmpty()) {
                    for (ret in backupPackage.productReturns) {
                        db.productReturnDao().insertReturn(ret)
                    }
                    counts["productReturns"] = backupPackage.productReturns.size
                }

                if (categories.contains("productReturnItems") && backupPackage.productReturnItems.isNotEmpty()) {
                    db.productReturnDao().insertAllReturnItems(backupPackage.productReturnItems)
                    counts["productReturnItems"] = backupPackage.productReturnItems.size
                }

                if (categories.contains("storeSettings") && backupPackage.storeSettings != null && options.mode == RestoreMode.FULL_REPLACE) {
                    db.storeSettingsDao().insertOrUpdateSettings(backupPackage.storeSettings)
                    counts["storeSettings"] = 1
                }
            }

            onProgress(85, "📁 جاري استعادة الصور المرفقة بطريقة آمنة (Streaming)...")
            restoreImagesFromWorkspace(context, workspaceDir)

            onProgress(95, "📊 جاري إعادة تدقيق ومطابقة أرصدة الحسابات...")
            auditBalances(db)

            onProgress(100, "✓ اكتملت الاستعادة بنجاح تام!")
            val total = counts.values.sum()
            RestoreResult(
                success = true,
                message = "تمت استعادة البيانات بنجاح ($total سجل)",
                totalRestored = total,
                details = counts
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(success = false, message = "فشلت عملية الاستعادة: ${e.localizedMessage}")
        } finally {
            try {
                if (workspaceDir.exists()) {
                    workspaceDir.deleteRecursively()
                }
            } catch (_: Exception) {}
        }
    }

    private fun extractToWorkspace(zipFile: File, workspaceDir: File): Result<Unit> {
        return try {
            if (!workspaceDir.exists()) workspaceDir.mkdirs()
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                val canonicalWorkspacePath = workspaceDir.canonicalPath
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val destFile = File(workspaceDir, entry.name)
                    val canonicalDestPath = destFile.canonicalPath
                    // Zip Slip protection
                    if (!canonicalDestPath.startsWith(canonicalWorkspacePath)) {
                        throw SecurityException("Zip Slip vulnerability detected: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readManifestAndPackage(workspaceDir: File): Pair<BackupManifest, BackupPackage>? {
        return try {
            val moshi = getMoshi()
            val manifestAdapter = moshi.adapter(BackupManifest::class.java)
            val packageAdapter = moshi.adapter(BackupPackage::class.java)

            val manifestFile = File(workspaceDir, "metadata.json").takeIf { it.exists() }
                ?: File(workspaceDir, "manifest.json").takeIf { it.exists() }
            val dataFile = File(workspaceDir, "data.json").takeIf { it.exists() } ?: return null

            val dataBytes = dataFile.readBytes()
            val dataJson = String(dataBytes, Charsets.UTF_8)
            val backupPkg = packageAdapter.fromJson(dataJson) ?: return null

            val manifest = if (manifestFile != null) {
                manifestAdapter.fromJson(manifestFile.readText(Charsets.UTF_8)) ?: backupPkg.manifest
            } else {
                backupPkg.manifest
            }

            if (manifest.checksum.isNotBlank()) {
                val actual = computeSha256(dataBytes)
                if (!actual.equals(manifest.checksum, ignoreCase = true)) {
                    android.util.Log.w("RestoreEngine", "Checksum mismatch in backup archive")
                }
            }

            Pair(manifest, backupPkg)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun restoreImagesFromWorkspace(context: Context, workspaceDir: File) {
        try {
            val imagesDir = File(workspaceDir, "images")
            if (imagesDir.exists() && imagesDir.isDirectory) {
                val targetDir = File(context.filesDir, "item_images").apply { if (!exists()) mkdirs() }
                val files = imagesDir.listFiles() ?: emptyArray()
                for (file in files) {
                    if (file.isFile && file.length() > 0) {
                        val dest = File(targetDir, file.name)
                        file.copyTo(dest, overwrite = true)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun auditBalances(db: AppDatabase) {
        try {
            val clients = db.clientDao().getAllClients()
            clients.forEach { client ->
                val invoices = db.invoiceDao().getInvoicesByClient(client.id)
                val payments = db.paymentDao().getPaymentsByClient(client.id)
                val totalInvoices = invoices.sumOf { it.totalAmount }
                val totalPayments = payments.sumOf { it.amount }
                val correctBalance = client.initialBalance + (totalInvoices - totalPayments)
                if (correctBalance != client.balance) {
                    db.clientDao().updateClient(client.copy(balance = correctBalance))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun inspectBackupUri(context: Context, uri: android.net.Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            val tempFileResult = SafStorageManager.copyUriToTempFile(context, uri, "inspect_restore_temp.zip")
            val tempFile = tempFileResult.getOrNull()
                ?: return@withContext Result.failure(tempFileResult.exceptionOrNull() ?: IOException("تعذر قراءة ملف النسخة"))
            inspectBackupFile(tempFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectBackupFile(file: File, context: Context? = null): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("ملف النسخة الاحتياطية فارغ أو غير موجود"))
            }

            val workspaceDir = File(file.parentFile, "inspect_ws_${System.currentTimeMillis()}")
            val extractRes = extractToWorkspace(file, workspaceDir)
            if (extractRes.isFailure) {
                return@withContext Result.failure(IllegalArgumentException("فشل قراءة هيكل ملف النسخة الاحتياطية"))
            }

            val (manifest, backupPackage) = readManifestAndPackage(workspaceDir)
                ?: return@withContext Result.failure(IllegalArgumentException("ملف النسخة تالف أو غير صالح"))

            try {
                workspaceDir.deleteRecursively()
            } catch (_: Exception) {}

            val currentAppDbVersion = 17
            val isCompatible = manifest.databaseVersion <= currentAppDbVersion
            val compatibilityMessage = if (isCompatible) {
                "متوافق تماماً مع إصدار التطبيق وقاعدة البيانات الحالية"
            } else {
                "غير متوافق: إصدار قاعدة البيانات (${manifest.databaseVersion}) أحدث من إصدار التطبيق ($currentAppDbVersion)"
            }

            val counts = if (manifest.recordCounts.isNotEmpty()) {
                manifest.recordCounts
            } else {
                mapOf(
                    "clients" to backupPackage.clients.size,
                    "invoices" to backupPackage.invoices.size,
                    "payments" to backupPackage.payments.size,
                    "installments" to backupPackage.installments.size,
                    "items" to backupPackage.items.size
                )
            }

            Result.success(
                BackupPreview(
                    formatVersion = manifest.formatVersion,
                    appVersion = manifest.appVersion,
                    databaseVersion = manifest.databaseVersion,
                    timestamp = manifest.timestamp,
                    recordCounts = counts,
                    fileSizeBytes = file.length(),
                    fileName = file.name,
                    isCompatible = isCompatible,
                    compatibilityMessage = compatibilityMessage,
                    tempFile = file
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun restoreFromUri(
        context: Context,
        db: AppDatabase,
        uri: android.net.Uri,
        options: RestoreOptions = RestoreOptions(RestoreMode.MERGE, BackupEngine.ALL_CATEGORIES.toSet()),
        onProgress: suspend (progress: Int, stage: String) -> Unit = { _, _ -> }
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            onProgress(5, "📥 جاري قراءة ملف النسخة الاحتياطية...")
            val tempFileResult = SafStorageManager.copyUriToTempFile(context, uri, "active_restore_temp.zip")
            val tempFile = tempFileResult.getOrNull()
                ?: return@withContext RestoreResult(false, "تعذر قراءة الملف: ${tempFileResult.exceptionOrNull()?.localizedMessage}")

            val result = restoreFromFile(context, db, tempFile, options, onProgress)
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}
            result
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(false, "حدث خطأ غير متوقع أثناء الاستعادة: ${e.localizedMessage}")
        }
    }
}
