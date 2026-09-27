package com.example.ui.screens.returns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.model.ProductReturn
import com.example.data.model.ProductReturnItem
import com.example.ui.viewmodel.ReturnViewModel
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import com.example.util.exportReturnToPdf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnDetailsScreen(
    navController: NavController,
    viewModel: ReturnViewModel,
    returnId: Int
) {
    val context = LocalContext.current
    var productReturn by remember { mutableStateOf<ProductReturn?>(null) }
    var returnItems by remember { mutableStateOf<List<ProductReturnItem>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(returnId) {
        productReturn = viewModel.getReturnById(returnId)
        returnItems = viewModel.getReturnItemsList(returnId)
    }

    val generateShareText = {
        val pr = productReturn
        if (pr != null) {
            val isCustomer = pr.type == "CUSTOMER"
            val typeStr = if (isCustomer) "مرتجع مبيعات" else "مرتجع مشتريات"
            val sb = StringBuilder()
            sb.append("--- $typeStr ---\n")
            sb.append("رقم المرتجع: ${pr.returnNumber}\n")
            sb.append("التاريخ: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(pr.date))}\n")
            sb.append(if (isCustomer) "العميل: ${pr.clientName}\n" else "المورد: ${pr.supplierName}\n")
            if (pr.invoiceNumber.isNotEmpty()) sb.append("الفاتورة: ${pr.invoiceNumber}\n")
            sb.append("\nالأصناف:\n")
            returnItems.forEach { item ->
                sb.append("- ${item.itemName} (${item.quantity} × ${item.unitPrice}) = ${item.totalPrice}\n")
            }
            sb.append("\nالإجمالي: ${pr.totalAmount} ${pr.currency}\n")
            sb.toString()
        } else ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل المرتجع") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (productReturn != null) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "المزيد")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("مشاركة نصية") },
                                leadingIcon = { Icon(Icons.Default.Message, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, generateShareText())
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "مشاركة المرتجع عبر"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("مشاركة واتساب") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        setPackage("com.whatsapp")
                                        putExtra(Intent.EXTRA_TEXT, generateShareText())
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "الواتساب غير مثبت", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تصدير PDF") },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val pdfPath = exportReturnToPdf(context, productReturn!!, returnItems, "النظام المحاسبي")
                                    if (pdfPath != null) {
                                        Toast.makeText(context, "تم الحفظ: $pdfPath", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "فشل تصدير PDF", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف المرتجع", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (productReturn == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val pr = productReturn!!
            val isCustomer = pr.type == "CUSTOMER"
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pr.returnNumber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Surface(
                                    color = if (isCustomer) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = if (isCustomer) "مرتجع مبيعات" else "مرتجع مشتريات",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isCustomer) Color(0xFF2E7D32) else Color(0xFFE65100)
                                    )
                                }
                            }
                            
                            Divider()
                            
                            DetailRow("التاريخ:", dateFormat.format(Date(pr.date)))
                            if (isCustomer) {
                                DetailRow("العميل:", pr.clientName)
                            } else {
                                DetailRow("المورد:", pr.supplierName)
                            }
                            DetailRow("الفاتورة الأصلية:", if (pr.invoiceNumber.isNotEmpty()) pr.invoiceNumber else "غير محددة")
                            if (pr.reason.isNotBlank()) DetailRow("السبب:", pr.reason)
                            if (pr.notes.isNotBlank()) DetailRow("الملاحظات:", pr.notes)
                            
                            Divider()
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("إجمالي المرتجع:", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${pr.totalAmount} ${pr.currency}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                item {
                    Text("الأصناف المرتجعة (${returnItems.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                items(returnItems) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.itemName, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("الكمية: ${item.quantity} ${item.unit}", style = MaterialTheme.typography.bodySmall)
                                Text("سعر الوحدة: ${item.unitPrice}", style = MaterialTheme.typography.bodySmall)
                                if (isCustomer) {
                                    Text("الحالة: ${item.itemCondition}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text(
                                "${item.totalPrice}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && productReturn != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف هذا المرتجع؟ سيتم التراجع عن تأثيره على المخزون وحساب العميل/المورد.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReturn(productReturn!!)
                        showDeleteConfirm = false
                        navController.popBackStack()
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}
