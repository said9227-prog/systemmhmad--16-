package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.database.AppDatabase
import com.example.data.model.AuditLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object AuditExportManager {

    private fun getExportsDirectory(context: Context): File {
        val dir = File(context.cacheDir, "audit_exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun escapeCsv(text: String): String {
        return if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else {
            text
        }
    }

    /**
     * Exports audit logs to CSV with UTF-8 BOM for Arabic support in Excel.
     */
    suspend fun exportToCsv(
        context: Context,
        db: AppDatabase,
        startTime: Long? = null,
        endTime: Long? = null,
        storeName: String,
        periodDescription: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val auditLogDao = db.auditLogDao()
            val logs = if (startTime != null && endTime != null) {
                auditLogDao.getLogsBetween(startTime, endTime)
            } else {
                auditLogDao.getAllLogs()
            }

            if (logs.isEmpty()) {
                return@withContext Result.failure(Exception("لا توجد سجلات لتصديرها."))
            }

            val exportDir = getExportsDirectory(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(exportDir, "سجل_العمليات_$timeStamp.csv")

            val dateTimeFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

            val sb = StringBuilder()
            // UTF-8 BOM
            sb.append("\uFEFF")
            sb.append("نظام $storeName - تقرير سجل عمليات النظام (Audit Log)\n")
            sb.append("تاريخ التصدير,${dateTimeFmt.format(Date())}\n")
            sb.append("الفترة,$periodDescription\n")
            sb.append("إجمالي السجلات,${logs.size}\n\n")

            // Headers
            sb.append("م,المعرف,التاريخ والوقت,نوع العملية,القسم / الجدول,التفاصيل\n")

            logs.forEachIndexed { index, log ->
                sb.append("${index + 1},")
                sb.append("${log.id},")
                sb.append("${dateTimeFmt.format(Date(log.timestamp))},")
                sb.append("${escapeCsv(log.operationType)},")
                sb.append("${escapeCsv(log.tableName)},")
                sb.append("${escapeCsv(log.details)}\n")
            }

            FileOutputStream(file).use { fos ->
                fos.write(sb.toString().toByteArray(Charsets.UTF_8))
            }

            if (file.exists() && file.length() > 0) {
                Result.success(file)
            } else {
                Result.failure(Exception("فشل إنشاء ملف التصدير CSV."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports audit logs to structured plain text report.
     */
    suspend fun exportToTxt(
        context: Context,
        db: AppDatabase,
        startTime: Long? = null,
        endTime: Long? = null,
        storeName: String,
        periodDescription: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val auditLogDao = db.auditLogDao()
            val logs = if (startTime != null && endTime != null) {
                auditLogDao.getLogsBetween(startTime, endTime)
            } else {
                auditLogDao.getAllLogs()
            }

            if (logs.isEmpty()) {
                return@withContext Result.failure(Exception("لا توجد سجلات لتصديرها."))
            }

            val exportDir = getExportsDirectory(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(exportDir, "سجل_العمليات_$timeStamp.txt")

            val dateTimeFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

            val sb = StringBuilder()
            sb.append("=================================================================\n")
            sb.append("             نظام $storeName             \n")
            sb.append("             تقرير سجل عمليات النظام (Audit Log)             \n")
            sb.append("=================================================================\n")
            sb.append("تاريخ التصدير: ${dateTimeFmt.format(Date())}\n")
            sb.append("الفترة: $periodDescription\n")
            sb.append("إجمالي السجلات: ${logs.size}\n")
            sb.append("=================================================================\n\n")

            logs.forEachIndexed { index, log ->
                sb.append("[${index + 1}] المعرف: #${log.id} | ${dateTimeFmt.format(Date(log.timestamp))}\n")
                sb.append("العملية: ${log.operationType} | القسم: ${log.tableName}\n")
                sb.append("التفاصيل: ${log.details}\n")
                sb.append("-----------------------------------------------------------------\n")
            }

            FileOutputStream(file).use { fos ->
                fos.write(sb.toString().toByteArray(Charsets.UTF_8))
            }

            if (file.exists() && file.length() > 0) {
                Result.success(file)
            } else {
                Result.failure(Exception("فشل إنشاء ملف التصدير TXT."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports audit logs to multi-page PDF document.
     */
    suspend fun exportToPdf(
        context: Context,
        db: AppDatabase,
        startTime: Long? = null,
        endTime: Long? = null,
        storeName: String,
        periodDescription: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val auditLogDao = db.auditLogDao()
            val logs = if (startTime != null && endTime != null) {
                auditLogDao.getLogsBetween(startTime, endTime)
            } else {
                auditLogDao.getAllLogs()
            }

            if (logs.isEmpty()) {
                return@withContext Result.failure(Exception("لا توجد سجلات لتصديرها."))
            }

            val exportDir = getExportsDirectory(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(exportDir, "سجل_العمليات_$timeStamp.pdf")

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas

            val titlePaint = Paint().apply {
                color = Color.parseColor("#1E3A8A")
                textSize = 15f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }

            val headerPaint = Paint().apply {
                color = Color.parseColor("#374151")
                textSize = 10f
                textAlign = Paint.Align.RIGHT
            }

            val rowPaint = Paint().apply {
                color = Color.parseColor("#111827")
                textSize = 8.5f
                textAlign = Paint.Align.RIGHT
            }

            val subPaint = Paint().apply {
                color = Color.parseColor("#6B7280")
                textSize = 7.5f
                textAlign = Paint.Align.RIGHT
            }

            val linePaint = Paint().apply {
                color = Color.parseColor("#E5E7EB")
                strokeWidth = 1f
            }

            val dateTimeFmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            var yPos = 40f
            var pageNumber = 1

            // Draw header function
            fun drawPageHeader(pNum: Int) {
                canvas.drawText("تقرير سجل عمليات النظام - $storeName", 297f, yPos, titlePaint)
                yPos += 20f
                canvas.drawText("الفترة: $periodDescription | إجمالي السجلات: ${logs.size} | الصفحة: $pNum", 550f, yPos, headerPaint)
                yPos += 15f
                canvas.drawLine(45f, yPos, 550f, yPos, linePaint)
                yPos += 15f
            }

            drawPageHeader(pageNumber)

            for ((idx, log) in logs.withIndex()) {
                if (yPos > 790f) {
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    val nextInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    currentPage = pdfDocument.startPage(nextInfo)
                    canvas = currentPage.canvas
                    yPos = 40f
                    drawPageHeader(pageNumber)
                }

                val rowHeader = "#${log.id} - [${log.operationType}] (${log.tableName}) - ${dateTimeFmt.format(Date(log.timestamp))}"
                canvas.drawText(rowHeader, 550f, yPos, rowPaint)
                yPos += 12f

                // Wrap or truncate details if long
                val truncatedDetails = if (log.details.length > 95) log.details.take(92) + "..." else log.details
                canvas.drawText(truncatedDetails, 550f, yPos, subPaint)
                yPos += 12f

                canvas.drawLine(45f, yPos, 550f, yPos, linePaint)
                yPos += 10f
            }

            pdfDocument.finishPage(currentPage)

            FileOutputStream(file).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()

            if (file.exists() && file.length() > 0) {
                Result.success(file)
            } else {
                Result.failure(Exception("فشل إنشاء ملف التصدير PDF."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
