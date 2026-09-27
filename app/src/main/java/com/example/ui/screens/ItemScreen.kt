package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.util.scrollToTopOnFocus
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Item
import com.example.data.model.ItemPurchaseHistory
import com.example.ui.components.ImageViewerDialog
import com.example.ui.viewmodel.AppViewModel
import com.example.util.FormatUtils
import com.example.util.DateTimeUtils
import com.example.util.ImageUtils
import kotlinx.coroutines.launch

data class SupplierSummary(
    val name: String,
    val type: String,
    val itemCount: Int,
    val lowStockCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemScreen(
    viewModel: AppViewModel,
    initialBarcodeToSearch: String = "",
    initialShowAddDialog: Boolean = false,
    onNavigateToAddItem: () -> Unit = {},
    onNavigateToSupplier: (String) -> Unit = {},
    onNavigateToTopMovingItems: () -> Unit = {}
) {
    val itemsList by viewModel.items.collectAsState()
    val companiesList by viewModel.companies.collectAsState()
    val settings by viewModel.storeSettings.collectAsState()

    var searchQuery by remember { mutableStateOf(initialBarcodeToSearch) }
    var supplierSearchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("الكل") }
    var showOnlyLowStock by remember { mutableStateOf(false) }
    var showFiltersSection by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<Item?>(null) }
    var itemForHistory by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(initialShowAddDialog) {
        if (initialShowAddDialog) {
            onNavigateToAddItem()
        }
    }

    // Dynamic unique categories
    val categories = remember(itemsList) {
        val list = mutableListOf("الكل")
        list.addAll(itemsList.map { it.category }.filter { it.isNotBlank() }.distinct())
        list
    }

    // Dynamic unique suppliers summary with counts and low-stock indicators
    val suppliersSummary = remember(itemsList, companiesList) {
        val map = linkedMapOf<String, Triple<String, Int, Int>>() // name -> Triple(type, totalCount, lowStockCount)

        // Register companies from companies table
        companiesList.forEach { comp ->
            val trimmed = comp.name.trim()
            if (trimmed.isNotBlank()) {
                map[trimmed] = Triple("شركة", 0, 0)
            }
        }

        // Tally items
        itemsList.forEach { item ->
            val sName = if (item.supplierType == "شركة") item.supplierCompanyName.trim() else item.individualSupplierName.trim()
            val sType = if (item.supplierType == "شركة") "شركة" else "مورد فردي"
            if (sName.isNotBlank()) {
                val curr = map[sName]
                val newCount = (curr?.second ?: 0) + 1
                val isLow = if (item.quantity <= item.minQuantityAlert) 1 else 0
                val newLow = (curr?.third ?: 0) + isLow
                map[sName] = Triple(sType, newCount, newLow)
            }
        }

        map.map { (name, triple) ->
            SupplierSummary(
                name = name,
                type = triple.first,
                itemCount = triple.second,
                lowStockCount = triple.third
            )
        }.sortedWith(compareByDescending<SupplierSummary> { it.itemCount }.thenBy { it.name })
    }

    // Filter suppliers summary based on supplierSearchQuery
    val filteredSuppliersSummary = remember(suppliersSummary, supplierSearchQuery) {
        if (supplierSearchQuery.isBlank()) suppliersSummary
        else suppliersSummary.filter { it.name.contains(supplierSearchQuery, ignoreCase = true) }
    }

    // Filter items (shows all items from all companies/suppliers)
    val filteredItems = remember(itemsList, searchQuery, selectedCategoryFilter, showOnlyLowStock) {
        itemsList.filter { item ->
            val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) || 
                                item.barcode.contains(searchQuery) ||
                                item.category.contains(searchQuery, ignoreCase = true)
            
            val matchesCat = selectedCategoryFilter == "الكل" || 
                             item.category == selectedCategoryFilter
            
            val matchesLowStock = !showOnlyLowStock || 
                                  item.quantity <= item.minQuantityAlert

            matchesSearch && matchesCat && matchesLowStock
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Fixed Top Compact Search Bar & Filter Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث عن صنف، باركود، تصنيف...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("item_search_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // زر التصفية والفلاتر القابلة للطي
                IconButton(
                    onClick = { showFiltersSection = !showFiltersSection },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (showFiltersSection || selectedCategoryFilter != "الكل" || showOnlyLowStock)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .testTag("toggle_filters_btn")
                ) {
                    Icon(
                        imageVector = if (showFiltersSection) Icons.Default.FilterListOff else Icons.Default.FilterList,
                        contentDescription = "الفلاتر والتصنيفات",
                        tint = if (showFiltersSection || selectedCategoryFilter != "الكل" || showOnlyLowStock)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // الفلاتر والتصنيفات ثانوية وقابلة للطي حتى لا تلتهم الشاشة
            AnimatedVisibility(
                visible = showFiltersSection,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // شريط المخزون المنخفض ورابط الأكثر حركة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showOnlyLowStock,
                            onClick = { showOnlyLowStock = !showOnlyLowStock },
                            label = { Text("المخزون المنخفض / الناقص", fontSize = 11.sp) },
                            leadingIcon = {
                                if (showOnlyLowStock) Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                            }
                        )

                        TextButton(
                            onClick = onNavigateToTopMovingItems,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("🔥 الأكثر حركة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEA580C))
                        }
                    }

                    // شرائح التصنيفات
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategoryFilter == cat
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.clickable { selectedCategoryFilter = cat }
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // روابط الموردين والشركات
                    if (suppliersSummary.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("الشركات والموردون:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${suppliersSummary.size} مورد", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(suppliersSummary.take(6), key = { it.name }) { sup ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.clickable { onNavigateToSupplier(sup.name) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (sup.type == "شركة") Icons.Default.Business else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(sup.name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(4.dp))
                                        Text("(${sup.itemCount})", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // سطر ملخص الأصناف المدمج جداً
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الأصناف (${filteredItems.size})" + if (selectedCategoryFilter != "الكل") " • $selectedCategoryFilter" else "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedCategoryFilter != "الكل" || showOnlyLowStock || searchQuery.isNotBlank()) {
                    TextButton(
                        onClick = {
                            searchQuery = ""
                            selectedCategoryFilter = "الكل"
                            showOnlyLowStock = false
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("إعادة ضبط", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Inventory Items List
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Inventory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لم يتم العثور على أصناف تليق بالبحث",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        ItemCard(
                            item = item,
                            currency = settings.currency,
                            onEdit = { itemToEdit = item },
                            onDelete = { viewModel.deleteItem(item) },
                            onImageChanged = { newUri ->
                                viewModel.updateItem(item.copy(imageUri = newUri))
                            },
                            onShowHistory = { itemForHistory = item }
                        )
                    }
                }
            }
        }

        // Add Item Floating Button
        FloatingActionButton(
            onClick = { onNavigateToAddItem() },
            containerColor = Color(0xFFFFD700),
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .testTag("add_item_fab")
        ) {
            Icon(imageVector = Icons.Default.AddBox, contentDescription = "إضافة صنف")
        }

        // Add Dialog
        if (showAddDialog) {
            ItemFormDialog(
                title = "إضافة صنف جديد للمخازن",
                onDismiss = { showAddDialog = false },
                onSave = { name, barcode, category, purchasePrice, sellingPrice, quantity, minQty, imageUri ->
                    viewModel.addItem(name, barcode, category, purchasePrice, sellingPrice, quantity, minQty, imageUri)
                    showAddDialog = false
                }
            )
        }

        // Edit Dialog
        itemToEdit?.let { item ->
            ItemFormDialog(
                title = "تعديل بيانات الصنف",
                item = item,
                onDismiss = { itemToEdit = null },
                onSave = { name, barcode, category, purchasePrice, sellingPrice, quantity, minQty, imageUri ->
                    viewModel.updateItem(
                        item.copy(
                            name = name,
                            barcode = barcode,
                            category = category,
                            purchasePrice = purchasePrice,
                            sellingPrice = sellingPrice,
                            quantity = quantity,
                            minQuantityAlert = minQty,
                            imageUri = imageUri
                        )
                    )
                    itemToEdit = null
                }
            )
        }

        // History Dialog (Loaded on demand to prevent N+1 queries)
        itemForHistory?.let { selectedItem ->
            val history by viewModel.getItemPurchaseHistory(selectedItem.id).collectAsState(initial = emptyList())
            ItemHistoryDialog(
                item = selectedItem,
                history = history,
                currency = settings.currency,
                onDismiss = { itemForHistory = null }
            )
        }
    }
}

@Composable
fun ItemCard(
    item: Item,
    history: List<ItemPurchaseHistory> = emptyList(),
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImageChanged: ((String) -> Unit)? = null,
    onShowHistory: (() -> Unit)? = null
) {
    val isLowStock = item.quantity <= item.minQuantityAlert
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val saved = ImageUtils.saveImageSafely(context, uri)
                if (saved != null) {
                    onImageChanged?.invoke(saved)
                }
            }
        }
    }

    if (showFullImage && !item.imageUri.isNullOrBlank()) {
        ImageViewerDialog(
            imageUri = item.imageUri,
            title = item.name,
            onDismiss = { showFullImage = false },
            onRequestReplace = if (onImageChanged != null) {
                {
                    showFullImage = false
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            } else null
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (onShowHistory != null) {
                    onShowHistory()
                } else {
                    showHistoryDialog = true
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (isLowStock) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Thumbnail image or small icon
            if (!item.imageUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showFullImage = true }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.imageUri)
                            .size(100)
                            .crossfade(true)
                            .build(),
                        contentDescription = "صورة ${item.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isLowStock) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = if (isLowStock) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Middle: Name, Available Quantity, Selling Price
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isLowStock) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (item.quantity <= 0) "نفد" else "ناقص",
                                color = Color(0xFFDC2626),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Available Quantity (الكمية المتوفرة)
                    Text(
                        text = "المتوفر: ${item.quantity} ${item.unit}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLowStock) Color(0xFFDC2626) else Color(0xFF059669)
                    )

                    Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.outlineVariant)

                    // Selling Price (سعر البيع)
                    Text(
                        text = "البيع: ${FormatUtils.formatAmount(item.sellingPrice)} $currency",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Right: Quick Action Button (تعديل أو سحب) & Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // زر إجراء سريع: تعديل
                FilledTonalButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل", modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("تعديل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // خيارات إضافية (سجل الحركة، حذف، صورة)
                var showItemMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showItemMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "خيارات إضافية",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showItemMenu,
                        onDismissRequest = { showItemMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("سجل حركة وسحب الصنف") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showItemMenu = false
                                if (onShowHistory != null) onShowHistory() else showHistoryDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("تعديل بيانات الصنف") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showItemMenu = false
                                onEdit()
                            }
                        )
                        if (!item.imageUri.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("معاينة وتكبير الصورة") },
                                leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null) },
                                onClick = {
                                    showItemMenu = false
                                    showFullImage = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("حذف الصنف") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            onClick = {
                                showItemMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showHistoryDialog && onShowHistory == null) {
        ItemHistoryDialog(
            item = item,
            history = history,
            currency = currency,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemHistoryDialog(
    item: Item,
    history: List<ItemPurchaseHistory>,
    currency: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.name, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "المورد الحالي: ${if (item.supplierType == "شركة") item.supplierCompanyName else item.individualSupplierName}",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                if (history.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا يوجد سجل شراء مسبق لهذا الصنف.", color = Color.Gray)
                    }
                } else {
                    val stats = history.map { it.purchasePrice }
                    val maxPrice = stats.maxOrNull() ?: 0.0
                    val minPrice = stats.minOrNull() ?: 0.0
                    val avgPrice = if (stats.isNotEmpty()) stats.average() else 0.0

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Stats Box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("أعلى سعر", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(FormatUtils.formatAmount(maxPrice), fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("متوسط", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(FormatUtils.formatAmount(avgPrice), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("أقل سعر", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(FormatUtils.formatAmount(minPrice), fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val historyByCompany = history.groupBy { it.supplierCompanyName }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            historyByCompany.forEach { (companyName, companyHistory) ->
                                item {
                                    Text(
                                        text = if (companyName.isNotBlank()) "🏢 سجل الشراء من: $companyName" else "مورد غير معروف",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }

                                items(companyHistory) { record ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(DateTimeUtils.formatDateOnly(record.purchaseDate), fontSize = 12.sp)
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                        Text("${FormatUtils.formatAmount(record.purchasePrice)} $currency", fontWeight = FontWeight.Bold)
                                        Text("الكمية: ${record.quantity}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemFormDialog(
    title: String,
    item: Item? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, barcode: String, category: String, purchasePrice: Double, sellingPrice: Double, quantity: Int, minQty: Int, imageUri: String?) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var barcode by remember { mutableStateOf(item?.barcode ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "عام") }
    var purchasePriceStr by remember { mutableStateOf(item?.purchasePrice?.toString() ?: "0.0") }
    var sellingPriceStr by remember { mutableStateOf(item?.sellingPrice?.toString() ?: "0.0") }
    var quantityStr by remember { mutableStateOf(item?.quantity?.toString() ?: "0") }
    var minQtyStr by remember { mutableStateOf(item?.minQuantityAlert?.toString() ?: "5") }
    var imageUriStr by remember { mutableStateOf(item?.imageUri) }
    var isProcessingImage by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessingImage = true
                val saved = ImageUtils.saveImageSafely(context, uri)
                if (saved != null) {
                    imageUriStr = saved
                }
                isProcessingImage = false
            }
        }
    }

    if (showImageViewer && !imageUriStr.isNullOrBlank()) {
        ImageViewerDialog(
            imageUri = imageUriStr!!,
            title = if (name.isNotBlank()) name else "معاينة صورة الصنف",
            onDismiss = { showImageViewer = false },
            onRequestRemove = {
                imageUriStr = null
                showImageViewer = false
            },
            onRequestReplace = {
                showImageViewer = false
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }

    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Image Row in Edit Dialog
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isProcessingImage) {
                                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                } else if (!imageUriStr.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { showImageViewer = true }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(imageUriStr)
                                                .size(160)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "صورة الصنف",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 4.dp))
                                                .padding(2.dp)
                                        ) {
                                            Icon(Icons.Default.ZoomIn, contentDescription = "تكبير", tint = Color.White, modifier = Modifier.size(10.dp))
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = if (!imageUriStr.isNullOrBlank()) "صورة الصنف (موجودة)" else "صورة الصنف (اختياري)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!imageUriStr.isNullOrBlank()) {
                                        Text(
                                            text = "اضغط للمعاينة والتكبير",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { showImageViewer = true }
                                        )
                                    }
                                }
                            }

                            Row {
                                IconButton(
                                    onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                ) {
                                    Icon(
                                        imageVector = if (!imageUriStr.isNullOrBlank()) Icons.Default.Edit else Icons.Default.AddPhotoAlternate,
                                        contentDescription = "اختيار صورة",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (!imageUriStr.isNullOrBlank()) {
                                    IconButton(onClick = { imageUriStr = null }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف الصورة", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; isError = false },
                        label = { Text("اسم الصنف *") },
                        isError = isError,
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("الباركود (رقمي أو يدوي)") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("التصنيف / القسم") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it },
                            label = { Text("سعر الشراء") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f)
                        )
                        OutlinedTextField(
                            value = sellingPriceStr,
                            onValueChange = { sellingPriceStr = it },
                            label = { Text("سعر البيع") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it },
                            label = { Text("الكمية") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f)
                        )
                        OutlinedTextField(
                            value = minQtyStr,
                            onValueChange = { minQtyStr = it },
                            label = { Text("تنبيه النقص") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        isError = true
                    } else {
                        onSave(
                            name,
                            barcode,
                            category,
                            purchasePriceStr.toDoubleOrNull() ?: 0.0,
                            sellingPriceStr.toDoubleOrNull() ?: 0.0,
                            quantityStr.toIntOrNull() ?: 0,
                            minQtyStr.toIntOrNull() ?: 5,
                            imageUriStr
                        )
                    }
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
