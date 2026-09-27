package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.database.AppDatabase
import com.example.data.model.StoreSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExportEngine {

    private fun getExportDirectory(context: Context): File {
        val dir = File(context.cacheDir, "exports")
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

    // ==========================================
    // 1. CSV EXPORT (With UTF-8 BOM for Excel)
    // ==========================================
    suspend fun exportToCsv(
        context: Context,
        db: AppDatabase,
        selectedCategories: Set<String>,
        startDate: Long? = null,
        endDate: Long? = null,
        settings: StoreSettings
    ): File = withContext(Dispatchers.IO) {
        val exportDir = getExportDirectory(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val csvFile = File(exportDir, "تقرير_البيانات_$timeStamp.csv")

        val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

        val sb = StringBuilder()
        // Add UTF-8 BOM so Excel opens Arabic correctly
        sb.append("\uFEFF")

        sb.append("نظام ${settings.storeName} - تصدير البيانات المجدولة\n")
        sb.append("تاريخ التصدير,${dateFmt.format(Date())}\n")
        if (startDate != null && endDate != null) {
            sb.append("الفترة من,${dateFmt.format(Date(startDate))},إلى,${dateFmt.format(Date(endDate))}\n")
        }
        sb.append("\n")

        // 1. Clients
        if (selectedCategories.contains("clients")) {
            val clients = db.clientDao().getAllClients()
            sb.append("--- كشف حسابات العملاء ---\n")
            sb.append("كود العميل,اسم العميل,الهاتف,المدينة,الرصيد الحالي,الحد الائتماني,التصنيف,ملاحظات\n")
            clients.forEach { c ->
                sb.append(
                    listOf(
                        escapeCsv(c.customerId),
                        escapeCsv(c.name),
                        escapeCsv(c.phone),
                        escapeCsv(c.city),
                        c.balance.toString(),
                        c.creditLimit.toString(),
                        escapeCsv(c.classification),
                        escapeCsv(c.notes)
                    ).joinToString(",")
                ).append("\n")
            }
            sb.append("\n")
        }

        // 2. Invoices
        if (selectedCategories.contains("invoices")) {
            var invoices = db.invoiceDao().getAllInvoices()
            if (startDate != null && endDate != null) {
                invoices = invoices.filter { it.date in startDate..endDate }
            }
            sb.append("--- سجل المبيعات والفواتير ---\n")
            sb.append("رقم الفاتورة,تاريخ الفاتورة,اسم العميل,إجمالي الفاتورة,المدفوع,المتبقي,العملة,ملاحظات\n")
            invoices.forEach { inv ->
                sb.append(
                    listOf(
                        escapeCsv(inv.invoiceNumber),
                        dateFmt.format(Date(inv.date)),
                        escapeCsv(inv.clientName),
                        inv.totalAmount.toString(),
                        inv.paidAmount.toString(),
                        inv.remainingAmount.toString(),
                        escapeCsv(inv.currency),
                        escapeCsv(inv.notes ?: "")
                    ).joinToString(",")
                ).append("\n")
            }
            sb.append("\n")
        }

        // 3. Payments
        if (selectedCategories.contains("payments")) {
            var payments = db.paymentDao().getAllPayments()
            if (startDate != null && endDate != null) {
                payments = payments.filter { it.date in startDate..endDate }
            }
            sb.append("--- سجل سندات القبض والدفعات ---\n")
            sb.append("رقم السند,تاريخ السند,معرف العميل,المبلغ,طريقة الدفع,اسم المحصل,ملاحظات\n")
            payments.forEach { p ->
                sb.append(
                    listOf(
                        escapeCsv(p.voucherNumber ?: p.id.toString()),
                        dateFmt.format(Date(p.date)),
                        p.clientId.toString(),
                        p.amount.toString(),
                        escapeCsv(p.paymentMethod),
                        escapeCsv(p.collectorName ?: ""),
                        escapeCsv(p.notes ?: "")
                    ).joinToString(",")
                ).append("\n")
            }
            sb.append("\n")
        }

        // 4. Installments
        if (selectedCategories.contains("installments")) {
            var installments = db.installmentDao().getAllInstallments()
            if (startDate != null && endDate != null) {
                installments = installments.filter { it.dueDate in startDate..endDate }
            }
            sb.append("--- سجل الأقساط ---\n")
            sb.append("العميل,مبلغ القسط,تاريخ الاستحقاق,المسدد,حالة السداد,التكرار,ملاحظات\n")
            installments.forEach { inst ->
                sb.append(
                    listOf(
                        escapeCsv(inst.clientName),
                        inst.amount.toString(),
                        dateFmt.format(Date(inst.dueDate)),
                        inst.paidAmount.toString(),
                        if (inst.isPaid) "مسدد" else "غير مسدد",
                        escapeCsv(inst.recurrence),
                        escapeCsv(inst.notes)
                    ).joinToString(",")
                ).append("\n")
            }
            sb.append("\n")
        }

        // 5. Items
        if (selectedCategories.contains("items")) {
            val items = db.itemDao().getAllItems()
            sb.append("--- دليل الأصناف والمخزون ---\n")
            sb.append("اسم الصنف,الباركود,التصنيف,الوحدة,سعر الشراء,سعر البيع,الكمية المتوفرة\n")
            items.forEach { item ->
                sb.append(
                    listOf(
                        escapeCsv(item.name),
                        escapeCsv(item.barcode),
                        escapeCsv(item.category),
                        escapeCsv(item.unit),
                        item.purchasePrice.toString(),
                        item.sellingPrice.toString(),
                        item.quantity.toString()
                    ).joinToString(",")
                ).append("\n")
            }
            sb.append("\n")
        }

        // 6. Suppliers
        if (selectedCategories.contains("supplierCompanies")) {
            val suppliers = db.supplierCompanyDao().getAllCompanies()
            sb.append("--- دليل الشركات والموردين ---\n")
            sb.append("اسم الشركة,الهاتف,العنوان,ملاحظات\n")
            suppliers.forEach { s ->
                sb.append(
                    listOf(
                        escapeCsv(s.name),
                        escapeCsv(s.phone),
                        escapeCsv(s.address),
                        escapeCsv(s.notes)
                    ).joinToString(",")
                ).append("\n")
            }
            sb.append("\n")
        }

        // 7. Product Returns
        if (selectedCategories.contains("productReturns") || selectedCategories.contains("invoices")) {
            var returns = db.productReturnDao().getAllReturns()
            if (startDate != null && endDate != null) {
                returns = returns.filter { it.date in startDate..endDate }
            }
            sb.append("--- سجل المرتجعات ---\n")
            sb.append("رقم المرتجع,النوع,رقم الفاتورة الأصلية,اسم العميل,الإجمالي,نوع التسوية,التاريخ,ملاحظات\n")
            returns.forEach { r ->
                sb.append(
                    listOf(
                        escapeCsv(r.returnNumber),
                        escapeCsv(r.type),
                        escapeCsv(r.invoiceNumber ?: ""),
                        escapeCsv(r.clientName ?: ""),
                        r.totalAmount.toString(),
                        escapeCsv(r.settlementType),
                        dateFmt.format(Date(r.date)),
                        escapeCsv(r.notes ?: "")
                    ).joinToString(",")
                ).append("\n")
            }
            sb.append("\n")
        }

        FileOutputStream(csvFile).use { fos ->
            fos.write(sb.toString().toByteArray(Charsets.UTF_8))
        }

        csvFile
    }

    // ==========================================
    // 2. TXT EXPORT (Structured, Clean Arabic)
    // ==========================================
    suspend fun exportToTxt(
        context: Context,
        db: AppDatabase,
        selectedCategories: Set<String>,
        startDate: Long? = null,
        endDate: Long? = null,
        settings: StoreSettings
    ): File = withContext(Dispatchers.IO) {
        val exportDir = getExportDirectory(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val txtFile = File(exportDir, "تقرير_شامل_$timeStamp.txt")

        val dateFmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

        val sb = StringBuilder()
        sb.append("====================================================\n")
        sb.append("       ${settings.storeName} - تقرير الأعمال الشامل     \n")
        sb.append("====================================================\n")
        sb.append("تاريخ الإنشاء: ${dateFmt.format(Date())}\n")
        sb.append("العملة المعتمدة: ${settings.currency}\n")
        if (startDate != null && endDate != null) {
            val df = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            sb.append("نطاق التقرير: من ${df.format(Date(startDate))} إلى ${df.format(Date(endDate))}\n")
        }
        sb.append("----------------------------------------------------\n\n")

        // Clients
        if (selectedCategories.contains("clients")) {
            val clients = db.clientDao().getAllClients()
            sb.append("■ كشف حسابات العملاء (إجمالي ${clients.size} عميل):\n")
            sb.append("----------------------------------------------------\n")
            var totalDebt = 0.0
            clients.forEachIndexed { i, c ->
                totalDebt += c.balance
                sb.append("${i + 1}. ${c.name} | الهاتف: ${c.phone.ifBlank { "غير متوفر" }} | الرصيد: ${c.balance} ${settings.currency} | المدينة: ${c.city}\n")
            }
            sb.append(">>> إجمالي أرصدة العملاء: $totalDebt ${settings.currency}\n\n")
        }

        // Invoices
        if (selectedCategories.contains("invoices")) {
            var invoices = db.invoiceDao().getAllInvoices()
            if (startDate != null && endDate != null) {
                invoices = invoices.filter { it.date in startDate..endDate }
            }
            sb.append("■ فواتير المبيعات (إجمالي ${invoices.size} فاتورة):\n")
            sb.append("----------------------------------------------------\n")
            val totalInvoices = invoices.sumOf { it.totalAmount }
            val totalPaid = invoices.sumOf { it.paidAmount }
            val totalRemaining = invoices.sumOf { it.remainingAmount }
            invoices.forEachIndexed { i, inv ->
                val d = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(inv.date))
                sb.append("${i + 1}. [${inv.invoiceNumber}] - $d | العميل: ${inv.clientName} | الإجمالي: ${inv.totalAmount} | المدفوع: ${inv.paidAmount} | المتبقي: ${inv.remainingAmount}\n")
            }
            sb.append(">>> إجمالي الفواتير: $totalInvoices | إجمالي المحصل: $totalPaid | إجمالي المتبقي: $totalRemaining\n\n")
        }

        // Payments
        if (selectedCategories.contains("payments")) {
            var payments = db.paymentDao().getAllPayments()
            if (startDate != null && endDate != null) {
                payments = payments.filter { it.date in startDate..endDate }
            }
            sb.append("■ سندات القبض والتحصيلات (إجمالي ${payments.size} سند):\n")
            sb.append("----------------------------------------------------\n")
            val sumPayments = payments.sumOf { it.amount }
            payments.forEachIndexed { i, p ->
                val d = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(p.date))
                sb.append("${i + 1}. سند #${p.voucherNumber ?: p.id} - $d | المبلغ: ${p.amount} ${p.currency} | طريقة الدفع: ${p.paymentMethod}\n")
            }
            sb.append(">>> إجمالي السندات المحصلة: $sumPayments ${settings.currency}\n\n")
        }

        // Installments
        if (selectedCategories.contains("installments")) {
            var installments = db.installmentDao().getAllInstallments()
            if (startDate != null && endDate != null) {
                installments = installments.filter { it.dueDate in startDate..endDate }
            }
            sb.append("■ جدول الأقساط (إجمالي ${installments.size} قسط):\n")
            sb.append("----------------------------------------------------\n")
            installments.forEachIndexed { i, inst ->
                val d = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(inst.dueDate))
                val st = if (inst.isPaid) "✓ مسدد" else "⏳ غير مسدد"
                sb.append("${i + 1}. العميل: ${inst.clientName} | المبلغ: ${inst.amount} | الاستحقاق: $d | الحالة: $st\n")
            }
            sb.append("\n")
        }

        // Items
        if (selectedCategories.contains("items")) {
            val items = db.itemDao().getAllItems()
            sb.append("■ قائمة الأصناف والمخزون (إجمالي ${items.size} صنف):\n")
            sb.append("----------------------------------------------------\n")
            items.forEachIndexed { i, item ->
                sb.append("${i + 1}. ${item.name} | تصنيف: ${item.category} | الكمية: ${item.quantity} ${item.unit} | سعر البيع: ${item.sellingPrice}\n")
            }
            sb.append("\n")
        }

        // Suppliers
        if (selectedCategories.contains("supplierCompanies")) {
            val suppliers = db.supplierCompanyDao().getAllCompanies()
            sb.append("■ قائمة الموردين والشركات (إجمالي ${suppliers.size} شركة):\n")
            sb.append("----------------------------------------------------\n")
            suppliers.forEachIndexed { i, s ->
                sb.append("${i + 1}. ${s.name} | الهاتف: ${s.phone} | العنوان: ${s.address}\n")
            }
            sb.append("\n")
        }

        sb.append("====================================================\n")
        sb.append("نهاية التقرير - تم الإنشاء عبر تطبيق حسابات العملاء برو\n")

        FileOutputStream(txtFile).use { fos ->
            fos.write(sb.toString().toByteArray(Charsets.UTF_8))
        }

        txtFile
    }

    // ==========================================
    // 3. PDF EXPORT (Multi-page Tabular PDF)
    // ==========================================
    suspend fun exportToPdf(
        context: Context,
        db: AppDatabase,
        selectedCategories: Set<String>,
        startDate: Long? = null,
        endDate: Long? = null,
        settings: StoreSettings
    ): File = withContext(Dispatchers.IO) {
        val exportDir = getExportDirectory(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pdfFile = File(exportDir, "تقرير_مالي_شامل_$timeStamp.pdf")

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK; textAlign = Paint.Align.CENTER }
        val subtitlePaint = Paint().apply { textSize = 11f; color = Color.DKGRAY; textAlign = Paint.Align.CENTER }
        val sectionHeaderPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; color = Color.parseColor("#1E3A8A"); textAlign = Paint.Align.RIGHT }
        val tableHeaderPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val textPaint = Paint().apply { textSize = 9f; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        var y = 50f

        fun checkNewPage(needed: Float = 40f) {
            if (y + needed > 800f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        // Header Title
        canvas.drawText("التقرير الإداري والمالي الشامل", 595f / 2f, y, titlePaint)
        y += 22f
        canvas.drawText(settings.storeName, 595f / 2f, y, titlePaint)
        y += 20f

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var dateRangeStr = "تاريخ التصدير: ${dateFmt.format(Date())} | العملة: ${settings.currency}"
        if (startDate != null && endDate != null) {
            dateRangeStr += " | الفترة: من ${dateFmt.format(Date(startDate))} إلى ${dateFmt.format(Date(endDate))}"
        }
        canvas.drawText(dateRangeStr, 595f / 2f, y, subtitlePaint)
        y += 25f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 25f

        // 1. Clients
        if (selectedCategories.contains("clients")) {
            val clients = db.clientDao().getAllClients()
            checkNewPage(60f)
            canvas.drawText("■ كشف حسابات العملاء (${clients.size} عميل)", 555f, y, sectionHeaderPaint)
            y += 18f

            // Table Header
            canvas.drawText("اسم العميل", 555f, y, tableHeaderPaint)
            canvas.drawText("الهاتف", 400f, y, tableHeaderPaint)
            canvas.drawText("المدينة", 280f, y, tableHeaderPaint)
            canvas.drawText("الرصيد (${settings.currency})", 150f, y, tableHeaderPaint)
            y += 12f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 16f

            clients.take(100).forEach { c ->
                checkNewPage(20f)
                canvas.drawText(c.name.take(24), 555f, y, textPaint)
                canvas.drawText(c.phone.ifBlank { "-" }, 400f, y, textPaint)
                canvas.drawText(c.city.ifBlank { "-" }, 280f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "%.2f", c.balance), 150f, y, textPaint)
                y += 16f
            }
            y += 15f
        }

        // 2. Invoices
        if (selectedCategories.contains("invoices")) {
            var invoices = db.invoiceDao().getAllInvoices()
            if (startDate != null && endDate != null) {
                invoices = invoices.filter { it.date in startDate..endDate }
            }
            checkNewPage(60f)
            canvas.drawText("■ فواتير المبيعات (${invoices.size} فاتورة)", 555f, y, sectionHeaderPaint)
            y += 18f

            canvas.drawText("رقم الفاتورة", 555f, y, tableHeaderPaint)
            canvas.drawText("التاريخ", 430f, y, tableHeaderPaint)
            canvas.drawText("العميل", 320f, y, tableHeaderPaint)
            canvas.drawText("الإجمالي", 200f, y, tableHeaderPaint)
            canvas.drawText("المتبقي", 100f, y, tableHeaderPaint)
            y += 12f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 16f

            invoices.take(100).forEach { inv ->
                checkNewPage(20f)
                val d = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(inv.date))
                canvas.drawText(inv.invoiceNumber, 555f, y, textPaint)
                canvas.drawText(d, 430f, y, textPaint)
                canvas.drawText(inv.clientName.take(18), 320f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "%.2f", inv.totalAmount), 200f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "%.2f", inv.remainingAmount), 100f, y, textPaint)
                y += 16f
            }
            y += 15f
        }

        // 3. Payments
        if (selectedCategories.contains("payments")) {
            var payments = db.paymentDao().getAllPayments()
            if (startDate != null && endDate != null) {
                payments = payments.filter { it.date in startDate..endDate }
            }
            checkNewPage(60f)
            canvas.drawText("■ سندات القبض والدفعات (${payments.size} سند)", 555f, y, sectionHeaderPaint)
            y += 18f

            canvas.drawText("رقم السند", 555f, y, tableHeaderPaint)
            canvas.drawText("التاريخ", 430f, y, tableHeaderPaint)
            canvas.drawText("المبلغ", 300f, y, tableHeaderPaint)
            canvas.drawText("طريقة الدفع", 150f, y, tableHeaderPaint)
            y += 12f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 16f

            payments.take(100).forEach { p ->
                checkNewPage(20f)
                val d = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(p.date))
                canvas.drawText(p.voucherNumber ?: p.id.toString(), 555f, y, textPaint)
                canvas.drawText(d, 430f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "%.2f", p.amount), 300f, y, textPaint)
                canvas.drawText(p.paymentMethod, 150f, y, textPaint)
                y += 16f
            }
            y += 15f
        }

        // 4. Installments
        if (selectedCategories.contains("installments")) {
            var installments = db.installmentDao().getAllInstallments()
            if (startDate != null && endDate != null) {
                installments = installments.filter { it.dueDate in startDate..endDate }
            }
            checkNewPage(60f)
            canvas.drawText("■ الأقساط المجدولة (${installments.size} قسط)", 555f, y, sectionHeaderPaint)
            y += 18f

            canvas.drawText("العميل", 555f, y, tableHeaderPaint)
            canvas.drawText("المبلغ", 420f, y, tableHeaderPaint)
            canvas.drawText("تاريخ الاستحقاق", 280f, y, tableHeaderPaint)
            canvas.drawText("الحالة", 150f, y, tableHeaderPaint)
            y += 12f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 16f

            installments.take(100).forEach { inst ->
                checkNewPage(20f)
                val d = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(inst.dueDate))
                canvas.drawText(inst.clientName.take(18), 555f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "%.2f", inst.amount), 420f, y, textPaint)
                canvas.drawText(d, 280f, y, textPaint)
                canvas.drawText(if (inst.isPaid) "مسدد" else "مستحق", 150f, y, textPaint)
                y += 16f
            }
            y += 15f
        }

        pdfDocument.finishPage(page)

        FileOutputStream(pdfFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        pdfFile
    }
}
