package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem

object WhatsAppHelper {

    /**
     * Builds a formatted Arabic WhatsApp message with Markdown emphasis and emojis.
     */
    fun formatWhatsAppInvoiceMessage(
        invoice: Invoice,
        items: List<InvoiceItem>,
        storeName: String,
        storePhone: String = "",
        storeAddress: String = "",
        currency: String = "الريال اليمني",
        clientBalance: Double? = null,
        showVatAndSubtotal: Boolean = false
    ): String {
        val sb = StringBuilder()
        sb.append("🏥 *").append(storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }).append("*\n")
        if (storePhone.isNotBlank()) {
            sb.append("📞 هاتف: ").append(storePhone).append("\n")
        }
        if (storeAddress.isNotBlank()) {
            sb.append("📍 العنوان: ").append(storeAddress).append("\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("🧾 *تفاصيل الفاتورة*\n")
        sb.append("• *رقم الفاتورة:* ").append(invoice.invoiceNumber).append("\n")
        sb.append("• *العميل:* ").append(invoice.clientName).append("\n")
        sb.append("• *التاريخ:* ").append(DateTimeUtils.formatDateTime12h(invoice.date)).append("\n")
        sb.append("• *النوع:* ").append(if (invoice.isQuickInvoice) "فاتورة سريعة ⚡" else "فاتورة مفصلة 📦").append("\n")
        sb.append("• *الحالة:* ").append(if (invoice.isDraft) "مسودة" else "نهائية مكتملة ✅").append("\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")

        if (invoice.isQuickInvoice) {
            sb.append("📝 *البيان / الوصف:*\n")
            sb.append(invoice.description?.ifBlank { "شراء مستلزمات طبية وأدوية" } ?: "شراء مستلزمات طبية وأدوية").append("\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        } else if (items.isNotEmpty()) {
            sb.append("📦 *الأصناف والبنود:*\n")
            items.forEachIndexed { index, item ->
                val totalFormatted = FormatUtils.formatAmount(item.totalPrice)
                val unitFormatted = FormatUtils.formatAmount(item.unitPrice)
                sb.append("${index + 1}. *${item.itemName}*\n")
                sb.append("   ▫️ الكمية: ${item.quantity} × $unitFormatted = *$totalFormatted $currency*\n")
            }
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            val subtotal = items.sumOf { it.totalPrice }
            if (showVatAndSubtotal) {
                sb.append("💵 *المجموع الفرعي:* ").append("${FormatUtils.formatAmount(subtotal)} $currency").append("\n")
            }
            if (invoice.discount > 0) {
                sb.append("🏷️ *الخصم الممنوح:* -").append("${FormatUtils.formatAmount(invoice.discount)} $currency").append("\n")
            }
            if (showVatAndSubtotal && invoice.taxRate > 0) {
                val taxAmt = (subtotal - invoice.discount) * (invoice.taxRate / 100.0)
                sb.append("📊 *الضريبة (%${invoice.taxRate}):* ").append("${FormatUtils.formatAmount(taxAmt)} $currency").append("\n")
            }
        }

        sb.append("💰 *المبلغ الإجمالي للفاتورة:* ").append("${FormatUtils.formatAmount(invoice.totalAmount)} $currency").append("\n")
        
        if (clientBalance != null) {
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            if (clientBalance > 0) {
                sb.append("⚠️ *إجمالي الرصيد المتبقي المستحق:* ").append("${FormatUtils.formatAmount(clientBalance)} $currency").append("\n")
            } else if (clientBalance == 0.0) {
                sb.append("✅ *الرصيد خالص بالكامل*\n")
            } else {
                sb.append("🟢 *رصيدكم الدائن:* ").append("${FormatUtils.formatAmount(-clientBalance)} $currency").append("\n")
            }
        }

        if (!invoice.notes.isNullOrBlank()) {
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("📌 *ملاحظات:* ").append(invoice.notes).append("\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("✨ *شكراً لتعاملكم معنا ونرحب بكم دائماً!* ✨")
        return sb.toString()
    }

    /**
     * Cleans up telephone numbers and opens WhatsApp directly to chat with the recipient.
     */
    fun sendWhatsAppMessage(context: Context, phone: String?, messageText: String) {
        try {
            var cleanPhone = phone?.filter { it.isDigit() || it == '+' } ?: ""
            if (cleanPhone.startsWith("+")) {
                cleanPhone = cleanPhone.substring(1)
            } else if (cleanPhone.startsWith("00")) {
                cleanPhone = cleanPhone.substring(2)
            }

            // If Yemeni number without international code (e.g. 7xxxxxxxx or 07xxxxxxxx)
            if (cleanPhone.startsWith("07") && cleanPhone.length == 10) {
                cleanPhone = "967" + cleanPhone.substring(1)
            } else if (cleanPhone.startsWith("7") && cleanPhone.length == 9) {
                cleanPhone = "967$cleanPhone"
            } else if (cleanPhone.startsWith("05") && cleanPhone.length == 10) { // Saudi local
                cleanPhone = "966" + cleanPhone.substring(1)
            }

            val encodedMsg = Uri.encode(messageText)
            val uri = if (cleanPhone.isNotBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=$encodedMsg")
            }

            // Attempt standard WhatsApp package first
            val whatsappIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(whatsappIntent)
            } catch (e: Exception) {
                // Try WhatsApp Business package
                try {
                    val businessIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.whatsapp.w4b")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(businessIntent)
                } catch (e2: Exception) {
                    // Fallback to browser or generic handler
                    try {
                        val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(genericIntent)
                    } catch (e3: Exception) {
                        // Ultimate fallback: ACTION_SEND intent chooser
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, messageText)
                            type = "text/plain"
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "إرسال الفاتورة عبر:"))
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح تطبيق الواتس آب: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
