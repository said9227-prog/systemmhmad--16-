package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.data.model.StoreSettings
import com.example.domain.report.ReportData
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportPdfManager {

    fun exportFinancialReportToPdf(
        context: Context,
        reportData: ReportData,
        settings: StoreSettings
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK; textAlign = Paint.Align.CENTER }
        val subtitlePaint = Paint().apply { textSize = 12f; color = Color.DKGRAY; textAlign = Paint.Align.CENTER }
        val headerPaint = Paint().apply { textSize = 14f; isFakeBoldText = true; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val textPaint = Paint().apply { textSize = 12f; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        var y = 50f
        
        fun checkNewPage(neededHeight: Float = 40f) {
            if (y + neededHeight > 800f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        // Title
        canvas.drawText("التقرير المالي والإحصائي", 595f / 2f, y, titlePaint)
        y += 20f
        canvas.drawText(settings.storeName, 595f / 2f, y, titlePaint)
        y += 30f
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateRangeStr = "الفترة: من ${sdf.format(Date(reportData.dateRange.start))} إلى ${sdf.format(Date(reportData.dateRange.end))}"
        canvas.drawText(dateRangeStr, 595f / 2f, y, subtitlePaint)
        y += 20f
        canvas.drawText("العملة: ${reportData.selectedCurrency}", 595f / 2f, y, subtitlePaint)
        y += 30f

        canvas.drawLine(50f, y, 545f, y, linePaint)
        y += 30f

        // For each currency
        reportData.currencyReports.forEach { cr ->
            checkNewPage(200f)
            
            canvas.drawText("--- عملة التقرير: ${cr.currency} ---", 545f, y, headerPaint)
            y += 25f

            // Sales
            canvas.drawText("ملخص المبيعات:", 545f, y, headerPaint)
            y += 20f
            canvas.drawText("إجمالي المبيعات: ${FormatUtils.formatAmount(cr.salesReport.totalSales)}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("مرتجعات المبيعات: ${FormatUtils.formatAmount(cr.salesReport.totalReturns)}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("صافي المبيعات: ${FormatUtils.formatAmount(cr.salesReport.netSales)}", 545f, y, textPaint)
            y += 30f

            // Purchases & Suppliers
            canvas.drawText("تقرير المشتريات والموردين:", 545f, y, headerPaint)
            y += 20f
            canvas.drawText("إجمالي المشتريات: ${FormatUtils.formatAmount(cr.purchaseReport.totalPurchases)}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("مرتجعات المشتريات: ${FormatUtils.formatAmount(cr.returnsReport.totalPurchaseReturns)}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("صافي المشتريات: ${FormatUtils.formatAmount(cr.purchaseReport.netPurchases)}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("عدد الموردين النشطين: ${cr.supplierReport.supplierCount}", 545f, y, textPaint)
            y += 30f

            // Customers
            canvas.drawText("تقرير العملاء والديون:", 545f, y, headerPaint)
            y += 20f
            canvas.drawText("العملاء المتفاعلون: ${cr.customerReport.activeCustomersCount}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("ديون غير مسددة بالكامل: ${cr.debtAgingReport.totalInvoices} فاتورة", 545f, y, textPaint)
            y += 20f
            canvas.drawText("مستحق اليوم: ${FormatUtils.formatAmount(cr.debtAgingReport.currentDue)}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("متأخر أكثر من 60 يوم: ${FormatUtils.formatAmount(cr.debtAgingReport.overdue60Plus)}", 545f, y, textPaint)
            y += 30f
            
            // Separator
            canvas.drawLine(50f, y, 545f, y, linePaint)
            y += 30f
        }

        // Inventory is not currency specific, print once at the end
        if (reportData.currencyReports.isNotEmpty()) {
            val inv = reportData.currencyReports.first().inventoryReport
            checkNewPage(150f)
            canvas.drawText("ملخص المخزون:", 545f, y, headerPaint)
            y += 20f
            canvas.drawText("إجمالي الأصناف: ${inv.totalItemsCount}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("الأصناف النافدة: ${inv.outOfStockItemsCount}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("إجمالي القطع المتوفرة: ${inv.totalQuantity}", 545f, y, textPaint)
        }

        pdfDocument.finishPage(page)

        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "تقارير ${settings.storeName}")
            if (!appDir.exists()) appDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(appDir, "تقرير_مالي_$timestamp.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }

    fun exportMultiClientReportToPdf(
        context: Context,
        selectedClients: List<com.example.data.model.Client>,
        invoices: List<com.example.data.model.Invoice>,
        payments: List<com.example.data.model.Payment>,
        returns: List<com.example.data.model.ProductReturn>,
        settings: StoreSettings,
        startDate: Long? = null,
        endDate: Long? = null
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true; color = Color.BLACK; textAlign = Paint.Align.CENTER }
        val subtitlePaint = Paint().apply { textSize = 11f; color = Color.DKGRAY; textAlign = Paint.Align.CENTER }
        val sectionPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; color = Color.rgb(15, 23, 42); textAlign = Paint.Align.RIGHT }
        val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val textPaint = Paint().apply { textSize = 9f; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeSdf = SimpleDateFormat("yyyy-MM-dd | hh:mm a", Locale.getDefault())

        var y = 45f

        fun checkNewPage(neededHeight: Float = 35f) {
            if (y + neededHeight > 800f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 45f
            }
        }

        // Title Header
        canvas.drawText("تقرير تفصيلي شامل للعملاء المحددين", 595f / 2f, y, titlePaint)
        y += 20f
        canvas.drawText("المتجر: ${settings.storeName}", 595f / 2f, y, subtitlePaint)
        y += 18f

        val periodStr = if (startDate != null && endDate != null) {
            "الفترة: من ${sdf.format(Date(startDate))} إلى ${sdf.format(Date(endDate))}"
        } else {
            "جميع المعاملات التاريخية"
        }
        canvas.drawText("$periodStr | عدد العملاء: ${selectedClients.size}", 595f / 2f, y, subtitlePaint)
        y += 25f

        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 20f

        // Process each selected client
        selectedClients.forEachIndexed { index, client ->
            checkNewPage(120f)

            // Client Header Block
            canvas.drawText("${index + 1}. كشف حساب العميل: ${client.name}", 555f, y, sectionPaint)
            y += 16f
            val clientMeta = "الهاتف: ${client.phone.ifEmpty { "غير مسجل" }} | الرصيد الحالي: ${FormatUtils.formatAmount(client.balance)} ${settings.currency}"
            canvas.drawText(clientMeta, 555f, y, textPaint)
            y += 18f

            // Filter & Build Ledger Entries
            val clientInvs = invoices.filter { it.clientId == client.id && !it.isDraft }
            val clientPays = payments.filter { it.clientId == client.id }
            val clientRets = returns.filter { it.clientId == client.id }

            data class TempEntry(
                val date: Long,
                val type: String,
                val label: String,
                val debit: Double,
                val credit: Double
            )

            val rawEntries = mutableListOf<TempEntry>()

            clientInvs.forEach { inv ->
                rawEntries.add(
                    TempEntry(
                        date = inv.date,
                        type = "فاتورة بيع",
                        label = "فاتورة #${inv.invoiceNumber}${if (!inv.notes.isNullOrBlank()) " (${inv.notes})" else ""}",
                        debit = inv.totalAmount,
                        credit = 0.0
                    )
                )
            }

            clientPays.forEach { pay ->
                rawEntries.add(
                    TempEntry(
                        date = pay.date,
                        type = "دفعة سداد",
                        label = "سداد (${pay.paymentMethod})${if (!pay.voucherNumber.isNullOrBlank()) " سند #${pay.voucherNumber}" else ""}",
                        debit = 0.0,
                        credit = pay.amount
                    )
                )
            }

            clientRets.forEach { ret ->
                val isAccountCredit = ret.settlementType != "استرداد نقدي"
                rawEntries.add(
                    TempEntry(
                        date = ret.date,
                        type = "مرتجع",
                        label = "مرتجع #${ret.returnNumber} (${ret.settlementType})",
                        debit = 0.0,
                        credit = if (isAccountCredit) ret.totalAmount else 0.0
                    )
                )
            }

            var sortedEntries = rawEntries.sortedBy { it.date }
            if (startDate != null && endDate != null) {
                sortedEntries = sortedEntries.filter { it.date in startDate..endDate }
            }

            // Table Columns
            val colDateX = 140f
            val colTypeX = 210f
            val colLabelX = 360f
            val colDebitX = 425f
            val colCreditX = 485f
            val colBalX = 555f

            // Table Header
            canvas.drawText("التاريخ والوقت", colDateX, y, headerPaint)
            canvas.drawText("العملية", colTypeX, y, headerPaint)
            canvas.drawText("البيان التفصيلي", colLabelX, y, headerPaint)
            canvas.drawText("دين (+)", colDebitX, y, headerPaint)
            canvas.drawText("سداد (-)", colCreditX, y, headerPaint)
            canvas.drawText("الرصيد", colBalX, y, headerPaint)
            y += 8f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 14f

            if (sortedEntries.isEmpty()) {
                canvas.drawText("لا توجد عمليات مسجلة لهذا العميل خلال الفترة المحددة.", 555f, y, textPaint)
                y += 20f
            } else {
                var runningBal = 0.0
                sortedEntries.forEach { entry ->
                    checkNewPage(18f)
                    runningBal += (entry.debit - entry.credit)

                    val timeStr = try { timeSdf.format(Date(entry.date)) } catch (_: Exception) { "" }
                    val labelTrunc = if (entry.label.length > 25) entry.label.take(24) + "…" else entry.label

                    canvas.drawText(timeStr, colDateX, y, textPaint)
                    canvas.drawText(entry.type, colTypeX, y, textPaint)
                    canvas.drawText(labelTrunc, colLabelX, y, textPaint)
                    canvas.drawText(if (entry.debit > 0) FormatUtils.formatAmount(entry.debit) else "-", colDebitX, y, textPaint)
                    canvas.drawText(if (entry.credit > 0) FormatUtils.formatAmount(entry.credit) else "-", colCreditX, y, textPaint)
                    canvas.drawText(FormatUtils.formatAmount(runningBal), colBalX, y, textPaint)

                    y += 16f
                }
            }

            y += 12f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 22f
        }

        pdfDocument.finishPage(page)

        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "تقارير ${settings.storeName}")
            if (!appDir.exists()) appDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(appDir, "تقرير_عملاء_مخصص_$timestamp.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }
}
