package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import com.example.data.model.Client
import com.example.data.model.Invoice
import com.example.data.model.Payment
import com.example.data.model.StoreSettings
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility to format Arabic timestamp with 12h time (ص/م)
 */
fun formatPdfTimestamp(timeMillis: Long): String {
    val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val arabicDayName = when (dayOfWeek) {
        Calendar.SUNDAY -> "الأحد"
        Calendar.MONDAY -> "الإثنين"
        Calendar.TUESDAY -> "الثلاثاء"
        Calendar.WEDNESDAY -> "الأربعاء"
        Calendar.THURSDAY -> "الخميس"
        Calendar.FRIDAY -> "الجمعة"
        Calendar.SATURDAY -> "السبت"
        else -> ""
    }
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val hour12 = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val minuteStr = String.format("%02d", cal.get(Calendar.MINUTE))
    val amPmStr = if (cal.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"

    return "$arabicDayName ${dateFmt.format(cal.time)} | $hour12:$minuteStr $amPmStr"
}

/**
 * Exports a comprehensive PDF report containing all clients, their debt balances,
 * invoice counts, and payment counts into the device's public Download folder as:
 * `نسخة كشوفات العملاء.pdf`
 */
fun exportAllClientsStatementToPdf(
    context: Context,
    clientsList: List<Client>,
    invoicesList: List<Invoice>,
    paymentsList: List<Payment>,
    settings: StoreSettings
): File? {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size (595 x 842 pt)
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val titlePaint = Paint().apply {
        textSize = 18f
        isFakeBoldText = true
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
    }

    val subtitlePaint = Paint().apply {
        textSize = 12f
        isFakeBoldText = true
        color = Color.DKGRAY
        textAlign = Paint.Align.CENTER
    }

    val textPaint = Paint().apply {
        textSize = 10f
        color = Color.BLACK
        textAlign = Paint.Align.RIGHT
    }

    val headerPaint = Paint().apply {
        textSize = 10f
        isFakeBoldText = true
        color = Color.BLACK
        textAlign = Paint.Align.RIGHT
    }

    val linePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
    }

    var y = 45f

    // Header Title
    canvas.drawText("تقرير شـامل لجميع كشوفات العملاء والديون", 595f / 2f, y, titlePaint)
    y += 22f
    canvas.drawText("نسخة احتياطية رسمية - ${settings.storeName}", 595f / 2f, y, subtitlePaint)
    y += 25f

    val formattedTime = formatPdfTimestamp(System.currentTimeMillis())

    canvas.drawText("تاريخ التقرير والتحديث: $formattedTime", 545f, y, textPaint)
    y += 18f
    if (settings.storePhone.isNotBlank()) {
        canvas.drawText("هاتف المتجر: ${settings.storePhone}", 545f, y, textPaint)
        y += 18f
    }

    val totalDebts = clientsList.filter { it.balance > 0 }.sumOf { it.balance }
    val totalClientsCount = clientsList.size
    val totalInvoicesCount = invoicesList.size
    val totalPaymentsCount = paymentsList.size

    canvas.drawText(
        "عدد العملاء: $totalClientsCount | الفواتير: $totalInvoicesCount | الدفعات: $totalPaymentsCount",
        545f,
        y,
        textPaint
    )
    y += 18f

    val summaryPaint = Paint(textPaint).apply {
        isFakeBoldText = true
        color = Color.RED
    }
    canvas.drawText(
        "إجمالي الديون المستحقة على العملاء: ${FormatUtils.formatAmount(totalDebts)} ${settings.currency}",
        545f,
        y,
        summaryPaint
    )
    y += 22f

    canvas.drawLine(50f, y, 545f, y, linePaint)
    y += 20f

    // Table Column Headers (RTL format)
    val colNameX = 545f
    val colPhoneX = 390f
    val colInvX = 270f
    val colPayX = 180f
    val colBalX = 90f

    canvas.drawText("اسم العميل", colNameX, y, headerPaint)
    canvas.drawText("الهاتف", colPhoneX, y, headerPaint)
    canvas.drawText("الفواتير", colInvX, y, headerPaint)
    canvas.drawText("الدفعات", colPayX, y, headerPaint)
    canvas.drawText("الرصيد المتبقي", colBalX, y, headerPaint)

    y += 10f
    canvas.drawLine(50f, y, 545f, y, linePaint)
    y += 18f

    // Loop through clients
    val sortedClients = clientsList.sortedByDescending { it.balance }
    sortedClients.forEach { client ->
        val cInvoices = invoicesList.filter { it.clientId == client.id }
        val cPayments = paymentsList.filter { it.clientId == client.id }

        val clientDisplayName = if (client.name.length > 22) client.name.take(20) + ".." else client.name
        canvas.drawText(clientDisplayName, colNameX, y, textPaint)
        canvas.drawText(client.phone.ifEmpty { "-" }, colPhoneX, y, textPaint)
        canvas.drawText("${cInvoices.size} فاتورة", colInvX, y, textPaint)
        canvas.drawText("${cPayments.size} دفعة", colPayX, y, textPaint)

        val balPaint = Paint(textPaint).apply {
            isFakeBoldText = true
            color = when {
                client.balance > 0 -> Color.RED
                client.balance < 0 -> Color.parseColor("#059669")
                else -> Color.DKGRAY
            }
        }
        canvas.drawText("${FormatUtils.formatAmount(client.balance)} ${settings.currency}", colBalX, y, balPaint)

        y += 20f
        if (y > 800f) {
            // Canvas limit reached for page 1
            return@forEach
        }
    }

    pdfDocument.finishPage(page)

    // Destination File inside public Download folder
    var destinationFile: File? = null
    try {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        destinationFile = File(downloadDir, "نسخة كشوفات العملاء.pdf")
        val fos = FileOutputStream(destinationFile)
        pdfDocument.writeTo(fos)
        pdfDocument.close()
        fos.close()
    } catch (e: Exception) {
        e.printStackTrace()
        // Fallback to internal app files directory if public storage permission is restricted
        try {
            destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "نسخة كشوفات العملاء.pdf")
            val fos = FileOutputStream(destinationFile)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    return destinationFile
}

fun exportInvoiceToPdf(
    context: Context,
    invoice: Invoice,
    items: List<com.example.data.model.InvoiceItem>,
    storeName: String,
    client: Client?
): String? {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        val paint = Paint()
        var yPos = 50f
        val startX = 550f // Right aligned for Arabic
        
        // Headers
        paint.textSize = 24f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(storeName, startX, yPos, paint)
        yPos += 40f
        
        paint.textSize = 18f
        canvas.drawText("🧾 فاتورة مبيعات", startX, yPos, paint)
        yPos += 30f
        
        paint.textSize = 14f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas.drawText("رقم الفاتورة: ${invoice.invoiceNumber}", startX, yPos, paint)
        canvas.drawText("التاريخ: ${formatPdfTimestamp(invoice.date)}", startX - 250f, yPos, paint)
        yPos += 25f
        
        canvas.drawText("العملة: ${invoice.currency}", startX, yPos, paint)
        yPos += 30f
        
        canvas.drawText("العميل: ${invoice.clientName}", startX, yPos, paint)
        if (client != null) {
            canvas.drawText("رقم الهاتف: ${client.phone}", startX - 250f, yPos, paint)
        }
        yPos += 40f
        
        // Table Header
        paint.textSize = 14f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas.drawText("الصنف", 500f, yPos, paint)
        canvas.drawText("الكمية", 300f, yPos, paint)
        canvas.drawText("السعر", 200f, yPos, paint)
        canvas.drawText("الإجمالي", 100f, yPos, paint)
        yPos += 20f
        
        // Table Items
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        items.forEach { item ->
            canvas.drawText(item.itemName, 500f, yPos, paint)
            canvas.drawText(item.quantity.toString(), 300f, yPos, paint)
            canvas.drawText(FormatUtils.formatAmount(item.unitPrice), 200f, yPos, paint)
            canvas.drawText(FormatUtils.formatAmount(item.totalPrice), 100f, yPos, paint)
            yPos += 20f
        }
        yPos += 30f
        
        // Summary
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas.drawText("الإجمالي: ${FormatUtils.formatAmount(invoice.totalAmount)} ${invoice.currency}", startX, yPos, paint)
        yPos += 20f
        canvas.drawText("المدفوع: ${FormatUtils.formatAmount(invoice.paidAmount)} ${invoice.currency}", startX, yPos, paint)
        yPos += 20f
        canvas.drawText("المتبقي: ${FormatUtils.formatAmount(invoice.remainingAmount)} ${invoice.currency}", startX, yPos, paint)
        yPos += 40f
        
        if (client != null) {
            canvas.drawText("رصيد العميل الحالي: ${FormatUtils.formatAmount(client.balance)} ${invoice.currency}", startX, yPos, paint)
        }
        
        pdfDocument.finishPage(page)
        
        val dir = File(context.cacheDir, "shared_invoices")
        if (!dir.exists()) dir.mkdirs()
        
        val file = File(dir, "Invoice_${invoice.invoiceNumber}.pdf")
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        outputStream.close()
        
        return file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun exportReturnToPdf(
    context: Context,
    productReturn: com.example.data.model.ProductReturn,
    items: List<com.example.data.model.ProductReturnItem>,
    storeName: String
): String? {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
        }
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isFakeBoldText = true
        }

        var yPos = 50f
        val marginX = 40f
        val pageWidth = pageInfo.pageWidth.toFloat()

        // Store Name & Title
        canvas.drawText(storeName, pageWidth / 2, yPos, titlePaint)
        yPos += 40f
        val titleStr = if (productReturn.type == "CUSTOMER") "سند مرتجع مبيعات" else "سند مرتجع مشتريات"
        canvas.drawText(titleStr, pageWidth / 2, yPos, titlePaint)
        yPos += 40f

        // Return Info
        val isCustomer = productReturn.type == "CUSTOMER"
        val partyLabel = if (isCustomer) "العميل:" else "المورد:"
        val partyName = if (isCustomer) productReturn.clientName else productReturn.supplierName
        val dateStr = formatPdfTimestamp(productReturn.date)
        
        paint.textAlign = Paint.Align.RIGHT
        val rightMargin = pageWidth - marginX
        
        canvas.drawText("رقم المرتجع: ${productReturn.returnNumber}", rightMargin, yPos, paint)
        yPos += 25f
        canvas.drawText("التاريخ: $dateStr", rightMargin, yPos, paint)
        yPos += 25f
        canvas.drawText("$partyLabel $partyName", rightMargin, yPos, paint)
        yPos += 25f
        if (productReturn.invoiceNumber.isNotEmpty()) {
            canvas.drawText("الفاتورة الأصلية: ${productReturn.invoiceNumber}", rightMargin, yPos, paint)
            yPos += 25f
        }

        yPos += 20f
        
        // Table Header
        paint.textAlign = Paint.Align.LEFT
        val colStartX = floatArrayOf(
            marginX,
            marginX + 80f,
            marginX + 160f,
            rightMargin
        )

        canvas.drawText("الإجمالي", colStartX[0], yPos, headerPaint)
        canvas.drawText("السعر", colStartX[1], yPos, headerPaint)
        canvas.drawText("الكمية", colStartX[2], yPos, headerPaint)
        
        headerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الصنف", colStartX[3], yPos, headerPaint)
        
        yPos += 20f
        canvas.drawLine(marginX, yPos, rightMargin, yPos, paint)
        yPos += 20f

        // Table Rows
        paint.textAlign = Paint.Align.LEFT
        items.forEach { item ->
            canvas.drawText("${item.totalPrice}", colStartX[0], yPos, paint)
            canvas.drawText("${item.unitPrice}", colStartX[1], yPos, paint)
            canvas.drawText("${item.quantity}", colStartX[2], yPos, paint)
            
            val paintRight = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(item.itemName, colStartX[3], yPos, paintRight)
            yPos += 25f
        }

        yPos += 10f
        canvas.drawLine(marginX, yPos, rightMargin, yPos, paint)
        yPos += 30f

        // Total
        paint.textAlign = Paint.Align.RIGHT
        paint.isFakeBoldText = true
        paint.textSize = 18f
        canvas.drawText("الإجمالي الكلي: ${productReturn.totalAmount} ${productReturn.currency}", rightMargin, yPos, paint)

        pdfDocument.finishPage(page)

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val fileName = "Return_${productReturn.returnNumber}.pdf"
        val file = File(downloadsDir, fileName)
        val outputStream = FileOutputStream(file)
        
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        outputStream.close()
        
        return file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
