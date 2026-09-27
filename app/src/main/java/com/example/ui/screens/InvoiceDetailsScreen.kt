package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.ui.viewmodel.AppViewModel
import com.example.util.FormatUtils
import com.example.util.ShareManager
import com.example.util.WhatsAppHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailsScreen(
    viewModel: AppViewModel,
    invoiceId: Int,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    val context = LocalContext.current
    val settings by viewModel.storeSettings.collectAsState()
    val clientsList by viewModel.clients.collectAsState()

    var invoice by remember { mutableStateOf<Invoice?>(null) }
    var invoiceItems by remember { mutableStateOf<List<InvoiceItem>>(emptyList()) }

    var showSmsConfirmDialog by remember { mutableStateOf(false) }
    var smsClientPhone by remember { mutableStateOf("") }
    var smsClientName by remember { mutableStateOf("") }
    var smsMessageContent by remember { mutableStateOf("") }

    // Fetch invoice on startup
    LaunchedEffect(invoiceId) {
        val inv = viewModel.getInvoiceById(invoiceId)
        if (inv != null) {
            invoice = inv
            viewModel.getInvoiceItemsFlow(invoiceId).collect {
                invoiceItems = it
            }
        }
    }

    if (invoice == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentInvoice = invoice!!
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(currentInvoice.date))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Unified Share action (PDF + Summary)
                IconButton(
                    onClick = {
                        val client = clientsList.find { it.id == currentInvoice.clientId }
                        ShareManager.shareInvoice(
                            context = context,
                            invoice = currentInvoice,
                            items = invoiceItems,
                            settings = settings,
                            client = client,
                            shareAsPdf = true
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "مشاركة الفاتورة",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Share as PDF directly
                IconButton(
                    onClick = {
                        val client = clientsList.find { it.id == currentInvoice.clientId }
                        ShareManager.shareInvoice(
                            context = context,
                            invoice = currentInvoice,
                            items = invoiceItems,
                            settings = settings,
                            client = client,
                            shareAsPdf = true
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "مشاركة PDF",
                        tint = Color(0xFFDC2626)
                    )
                }

                // Print action
                IconButton(
                    onClick = {
                        Toast.makeText(context, "جاري إرسال الفاتورة لبروتوكول الطباعة المباشر...", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.Print, contentDescription = "طباعة الفاتورة")
                }

                // Send SMS action
                IconButton(
                    onClick = {
                        val client = clientsList.find { it.id == currentInvoice.clientId }
                        if (client != null) {
                            smsClientPhone = client.phone
                            smsClientName = client.name
                            val addedAmount = currentInvoice.totalAmount
                            val remBalance = client.balance
                            val detailsText = if (currentInvoice.isQuickInvoice) {
                                currentInvoice.notes?.ifBlank { "شراء بضاعة" } ?: "شراء بضاعة"
                            } else {
                                val summary = invoiceItems.joinToString(", ") { "${it.itemName} (x${it.quantity})" }
                                if (summary.length > 80) summary.take(77) + "..." else summary
                            }
                            smsMessageContent = "عميلنا العزيز ${client.name}، تم إصدار فاتورة رقم: ${currentInvoice.invoiceNumber} بقيمة: ${FormatUtils.formatAmount(addedAmount)} ${settings.currency}. التفاصيل: $detailsText. الرصيد المتبقي الإجمالي المستحق: ${FormatUtils.formatAmount(remBalance)} ${settings.currency}. شكراً لتعاملكم معنا - ${settings.storeName}"
                            showSmsConfirmDialog = true
                        } else {
                            Toast.makeText(context, "فشل تحديد العميل للفاتورة!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = "إرسال كـ SMS",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Printable Bill Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(2.dp, RoundedCornerShape(10.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Store/Merchant Header Info
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = settings.storeName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        if (settings.storePhone.isNotEmpty()) {
                            Text(
                                text = "الهاتف: ${settings.storePhone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (settings.storeAddress.isNotEmpty()) {
                            Text(
                                text = settings.storeAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                    }
                }

                // Customer and Date Metadata
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("رقم الفاتورة:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(currentInvoice.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("العميل:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(currentInvoice.clientName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("التاريخ والوقت:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(formattedDate, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("النوع:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(if (currentInvoice.isQuickInvoice) "فاتورة سريعة ⚡" else "فاتورة مفصلة بالأصناف 📦", fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الحالة:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (currentInvoice.isDraft) Color(0xFFFEF3C7) else Color(0xFFECFDF5)
                                ),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (currentInvoice.isDraft) "مسودة" else "نهائية مكتملة",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentInvoice.isDraft) Color(0xFFD97706) else Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                    }
                }

                // Credit Limit Override Notice if applicable
                if (currentInvoice.isCreditOverride) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                                    Text("تم إصدار الفاتورة بتجاوز استثنائي للحد الائتماني", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFD32F2F))
                                }
                                Text("المسؤول المصرح: ${currentInvoice.overrideAuthorizer ?: "الإدارة"}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("سبب التجاوز: ${currentInvoice.overrideReason ?: "غير محدد"}", fontSize = 11.sp, color = Color.DarkGray)
                                if (currentInvoice.overrideAmount > 0) {
                                    Text("مقدار التجاوز: +${FormatUtils.formatAmount(currentInvoice.overrideAmount)} ${currentInvoice.currency.ifBlank { settings.currency }}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                                }
                            }
                        }
                    }
                }

                // Billing Content Table
                if (currentInvoice.isQuickInvoice) {
                    item {
                        Column {
                            Text("وصف الخدمات المذكورة:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = currentInvoice.description ?: "لا يوجد وصف محدد.",
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    // Itemized Table Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            Text("#", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text("اسم الصنف", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("الكمية", modifier = Modifier.width(50.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text("الوحدة", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
                            Text("الإجمالي", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.End)
                        }
                    }

                    // Table items
                    itemsIndexed(invoiceItems) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Text("${index + 1}", modifier = Modifier.width(30.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text(item.itemName, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            Text("${item.quantity}", modifier = Modifier.width(50.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text(FormatUtils.formatAmount(item.unitPrice), modifier = Modifier.width(70.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text(FormatUtils.formatAmount(item.totalPrice), modifier = Modifier.width(80.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                        }
                    }
                }

                // Billing Calculations Total panel
                item {
                    Divider()
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!currentInvoice.isQuickInvoice) {
                            val subtotal = invoiceItems.sumOf { it.totalPrice }
                            val taxAmt = (subtotal - currentInvoice.discount) * (currentInvoice.taxRate / 100.0)

                            if (settings.showVatAndSubtotal) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("المجموع الفرعي:", fontSize = 12.sp)
                                    Text("${FormatUtils.formatAmount(subtotal)} ${settings.currency}", fontSize = 12.sp)
                                }
                            }
                            if (currentInvoice.discount > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الخصم الممنوح:", fontSize = 12.sp, color = Color(0xFFDC2626))
                                    Text("-${FormatUtils.formatAmount(currentInvoice.discount)} ${settings.currency}", fontSize = 12.sp, color = Color(0xFFDC2626))
                                }
                            }
                            if (settings.showVatAndSubtotal && currentInvoice.taxRate > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ضريبة القيمة المضافة (${currentInvoice.taxRate}%):", fontSize = 12.sp)
                                    Text("${FormatUtils.formatAmount(taxAmt)} ${settings.currency}", fontSize = 12.sp)
                                }
                            }
                            if (settings.showVatAndSubtotal || currentInvoice.discount > 0) {
                                Divider(color = Color.LightGray.copy(alpha = 0.5f))
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المجموع الإجمالي الكلي:", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Text("${FormatUtils.formatAmount(currentInvoice.totalAmount)} ${settings.currency}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Invoice notes block
                if (!currentInvoice.notes.isNullOrBlank()) {
                    item {
                        Column {
                            Text("شروط وأحكام الفاتورة / الملاحظات:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(currentInvoice.notes ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Prominent Unified Share Action Button
        val client = clientsList.find { it.id == currentInvoice.clientId }
        Button(
            onClick = {
                ShareManager.shareInvoice(
                    context = context,
                    invoice = currentInvoice,
                    items = invoiceItems,
                    settings = settings,
                    client = client,
                    shareAsPdf = true
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "مشاركة",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "📤 مشاركة الفاتورة (PDF ومفصل)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // SMS Confirmation Dialog with Permission request flow
    if (showSmsConfirmDialog) {
        val phoneNumber = smsClientPhone
        val smsMessage = smsMessageContent

        var permissionGranted by remember {
            mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.SEND_SMS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
        }

        val smsPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            permissionGranted = isGranted
            if (isGranted) {
                val sent = sendDirectSms(context, phoneNumber, smsMessage)
                if (sent) {
                    Toast.makeText(context, "تم إرسال رسالة كشف الفاتورة بنجاح!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "فشل إرسال الرسالة المباشرة، جاري فتح التطبيق الافتراضي...", Toast.LENGTH_LONG).show()
                    sendSmsViaIntent(context, phoneNumber, smsMessage)
                }
            } else {
                Toast.makeText(context, "تم رفض الصلاحية. جاري فتح تطبيق الرسائل لإرسالها يدوياً...", Toast.LENGTH_LONG).show()
                sendSmsViaIntent(context, phoneNumber, smsMessage)
            }
            showSmsConfirmDialog = false
        }

        AlertDialog(
            onDismissRequest = { showSmsConfirmDialog = false },
            title = { Text("إرسال تفاصيل الفاتورة عبر SMS", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هل ترغب في إرسال تفاصيل الفاتورة والمال المتبقي للعميل عبر رسالة SMS؟", fontSize = 14.sp)
                    Text("سيتم إرسال الرسالة إلى: ${phoneNumber.ifEmpty { "لا يوجد رقم مسجل!" }}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(modifier = Modifier.padding(8.dp)) {
                            Text(smsMessage, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (phoneNumber.isBlank()) {
                            Toast.makeText(context, "لا يمكن الإرسال، رقم هاتف العميل فارغ!", Toast.LENGTH_SHORT).show()
                        } else {
                            sendSmsViaIntent(context, phoneNumber, smsMessage)
                        }
                        showSmsConfirmDialog = false
                    }
                ) {
                    Text("إرسال عبر تطبيق الرسائل", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsConfirmDialog = false }) {
                    Text("إلغاء وتخطي", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

// Build a beautiful share invoice plain text message
fun buildShareTextReceipt(invoice: Invoice, items: List<InvoiceItem>, storeName: String, currency: String, showVatAndSubtotal: Boolean = false): String {
    val sBuilder = java.lang.StringBuilder()
    sBuilder.append("🧾 فاتورة حساب صادرة من: $storeName\n")
    sBuilder.append("-----------------------------\n")
    sBuilder.append("رقم الفاتورة: ${invoice.invoiceNumber}\n")
    sBuilder.append("العميل: ${invoice.clientName}\n")
    sBuilder.append("التاريخ: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(invoice.date))}\n")
    sBuilder.append("-----------------------------\n")

    if (invoice.isQuickInvoice) {
        sBuilder.append("الوصف: ${invoice.description}\n")
    } else {
        sBuilder.append("الأصناف:\n")
        items.forEachIndexed { idx, item ->
            sBuilder.append("${idx + 1}. ${item.itemName} | الكمية: ${item.quantity} | السعر: ${item.unitPrice} -> الإجمالي: ${item.totalPrice} $currency\n")
        }
        sBuilder.append("-----------------------------\n")
        val subtotal = items.sumOf { it.totalPrice }
        if (showVatAndSubtotal) {
            sBuilder.append("المجموع الفرعي: $subtotal $currency\n")
        }
        if (invoice.discount > 0) sBuilder.append("الخصم المالي: -${invoice.discount} $currency\n")
        if (showVatAndSubtotal && invoice.taxRate > 0) sBuilder.append("الضريبة المضافة: %${invoice.taxRate}\n")
    }

    sBuilder.append("-----------------------------\n")
    sBuilder.append("إجمالي الفاتورة المطلق: ${invoice.totalAmount} $currency\n")
    sBuilder.append("-----------------------------\n")
    sBuilder.append("شكراً لتعاملكم معنا!")
    return sBuilder.toString()
}
