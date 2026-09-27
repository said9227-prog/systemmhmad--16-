package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.ui.viewmodel.ActivityItem
import com.example.ui.viewmodel.ActivityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class AppRepository(private val db: AppDatabase) {

    private val clientDao = db.clientDao()
    private val itemDao = db.itemDao()
    private val invoiceDao = db.invoiceDao()
    private val paymentDao = db.paymentDao()
    private val auditLogDao = db.auditLogDao()
    private val storeSettingsDao = db.storeSettingsDao()
    private val itemCategoryDao = db.itemCategoryDao()
    private val itemUnitDao = db.itemUnitDao()
    private val supplierCompanyDao = db.supplierCompanyDao()
    private val backupHistoryDao = db.backupHistoryDao()
    private val archivedAuditLogDao = db.archivedAuditLogDao()
    private val auditOperationDao = db.auditOperationDao()
    private val productReturnDao = db.productReturnDao()

    fun getDatabase(): AppDatabase = db

    // --- Audit Log Helper ---
    suspend fun insertLog(operationType: String, tableName: String, details: String) {
        withContext(Dispatchers.IO) {
            auditLogDao.insertLog(
                AuditLog(
                    operationType = operationType,
                    tableName = tableName,
                    details = details
                )
            )
        }
    }

    // --- Audit Log Advanced Queries & Management ---
    fun getActiveLogsCountFlow(): Flow<Int> = auditLogDao.getActiveLogsCountFlow()
    suspend fun getActiveLogsCount(): Int = withContext(Dispatchers.IO) { auditLogDao.getActiveLogsCount() }
    suspend fun getOldestLogTimestamp(): Long? = withContext(Dispatchers.IO) { auditLogDao.getOldestLogTimestamp() }
    suspend fun getNewestLogTimestamp(): Long? = withContext(Dispatchers.IO) { auditLogDao.getNewestLogTimestamp() }
    suspend fun getMonthlyAuditSummaries(): List<MonthAuditSummary> = withContext(Dispatchers.IO) { auditLogDao.getMonthlyAuditSummaries() }
    suspend fun getLogsPaged(limit: Int, offset: Int): List<AuditLog> = withContext(Dispatchers.IO) { auditLogDao.getLogsPaged(limit, offset) }
    suspend fun searchActiveLogs(query: String, limit: Int, offset: Int): List<AuditLog> = withContext(Dispatchers.IO) { auditLogDao.searchLogs(query, limit, offset) }

    // --- Archived Audit Logs ---
    fun getArchivedLogsCountFlow(): Flow<Int> = archivedAuditLogDao.getArchivedCountFlow()
    suspend fun getArchivedLogsCount(): Int = withContext(Dispatchers.IO) { archivedAuditLogDao.getArchivedCount() }
    suspend fun getArchivedLogsPaged(limit: Int, offset: Int): List<ArchivedAuditLog> = withContext(Dispatchers.IO) { archivedAuditLogDao.getArchivedLogsPaged(limit, offset) }
    suspend fun searchArchivedLogs(query: String, limit: Int, offset: Int): List<ArchivedAuditLog> = withContext(Dispatchers.IO) { archivedAuditLogDao.searchArchivedLogs(query, limit, offset) }

    // --- Audit Operations History (Audit the Audit System) ---
    fun getAllAuditOperationsFlow(): Flow<List<AuditManagementOperation>> = auditOperationDao.getAllOperationsFlow()
    suspend fun insertAuditOperation(op: AuditManagementOperation): Long = withContext(Dispatchers.IO) { auditOperationDao.insertOperation(op) }
    suspend fun updateAuditOperation(op: AuditManagementOperation) = withContext(Dispatchers.IO) { auditOperationDao.updateOperation(op) }

    // --- Paging Support ---
    fun getAllClientsPaged(pageSize: Int = 50): Flow<androidx.paging.PagingData<Client>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { clientDao.getAllClientsPagingSource() }
        ).flow
    }

    fun searchClientsPaged(query: String, pageSize: Int = 50): Flow<androidx.paging.PagingData<Client>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { clientDao.searchClientsPagingSource("%$query%") }
        ).flow
    }

    fun getAllItemsPaged(pageSize: Int = 50): Flow<androidx.paging.PagingData<Item>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { itemDao.getAllItemsPagingSource() }
        ).flow
    }

    fun searchItemsPaged(query: String, pageSize: Int = 50): Flow<androidx.paging.PagingData<Item>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { itemDao.searchItemsPagingSource("%$query%") }
        ).flow
    }

    fun getAllInvoicesPaged(pageSize: Int = 50): Flow<androidx.paging.PagingData<Invoice>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { invoiceDao.getAllInvoicesPagingSource() }
        ).flow
    }

    fun getInvoicesByClientPaged(clientId: Int, pageSize: Int = 50): Flow<androidx.paging.PagingData<Invoice>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { invoiceDao.getInvoicesByClientPagingSource(clientId) }
        ).flow
    }

    // --- Dashboard Aggregations (Optimized) ---
    suspend fun getDashboardTotals(currency: String, start: Long, end: Long): Triple<Double, Double, Int> = withContext(Dispatchers.IO) {
        val sales = invoiceDao.getTotalSales(currency, start, end)
        val receipts = paymentDao.getTotalReceipts(currency, start, end)
        val count = invoiceDao.getInvoicesCount(currency, start, end)
        Triple(sales, receipts, count)
    }

    suspend fun getRecentActivities(limit: Int = 20): List<ActivityItem> = withContext(Dispatchers.IO) {
        val invs = invoiceDao.getRecentInvoices(limit)
        val pays = paymentDao.getRecentPayments(limit)
        
        val activities = mutableListOf<ActivityItem>()
        invs.forEach { inv ->
            activities.add(ActivityItem(
                id = inv.id,
                type = ActivityType.INVOICE,
                title = if (inv.isQuickInvoice) "فاتورة سريعة" else "فاتورة مفصلة",
                subtitle = "رقم: ${inv.invoiceNumber} | العميل: ${inv.clientName}",
                amount = inv.totalAmount,
                date = inv.date,
                isDraft = inv.isDraft,
                referenceId = inv.id
            ))
        }
        pays.forEach { pay ->
            val clientName = clientDao.getClientById(pay.clientId)?.name ?: "عميل غير معروف"
            activities.add(ActivityItem(
                id = pay.id,
                type = ActivityType.PAYMENT,
                title = "دفعة مستلمة (${pay.currency})",
                subtitle = "العميل: $clientName | طريقة الدفع: ${pay.paymentMethod}",
                amount = pay.amount,
                date = pay.date,
                isDraft = false,
                referenceId = pay.id
            ))
        }
        activities.sortedByDescending { it.date }.take(limit)
    }

    suspend fun getPaymentsCount(currency: String, start: Long, end: Long): Int = withContext(Dispatchers.IO) {
        paymentDao.getPaymentsCount(currency, start, end)
    }

    suspend fun getClientsCount(): Int = withContext(Dispatchers.IO) {
        clientDao.getClientsCount()
    }

    suspend fun getItemsCount(): Int = withContext(Dispatchers.IO) {
        itemDao.getItemsCount()
    }

    suspend fun getTopDebtors(limit: Int): List<Client> = withContext(Dispatchers.IO) {
        clientDao.getTopDebtors(limit)
    }

    suspend fun searchInvoices(query: String): List<Invoice> = withContext(Dispatchers.IO) {
        invoiceDao.searchInvoices("%$query%")
    }

    // --- Clients ---
    fun getAllClientsFlow(): Flow<List<Client>> = clientDao.getAllClientsFlow()
    suspend fun getAllClients(): List<Client> = withContext(Dispatchers.IO) { clientDao.getAllClients() }
    fun getClientByIdFlow(id: Int): Flow<Client?> = clientDao.getClientByIdFlow(id)
    suspend fun getClientById(id: Int): Client? = withContext(Dispatchers.IO) { clientDao.getClientById(id) }

    fun searchClients(query: String): Flow<List<Client>> = clientDao.searchClients(query)
    
    suspend fun insertClient(client: Client): Long = withContext(Dispatchers.IO) {
        val id = clientDao.insertClient(client)
        insertLog("إضافة", "العملاء", "تم إضافة العميل: ${client.name}")
        id
    }

    suspend fun updateClient(client: Client) = withContext(Dispatchers.IO) {
        clientDao.updateClient(client)
        insertLog("تعديل", "العملاء", "تم تعديل بيانات العميل: ${client.name}")
    }

    suspend fun deleteClient(client: Client) = withContext(Dispatchers.IO) {
        clientDao.deleteClient(client)
        insertLog("حذف", "العملاء", "تم حذف العميل: ${client.name}")
    }

    suspend fun recalculateClientBalance(clientId: Int) = withContext(Dispatchers.IO) {
        val client = clientDao.getClientById(clientId) ?: return@withContext
        val totalInvoiced = invoiceDao.getClientTotalInvoiced(clientId)
        val totalPaid = paymentDao.getClientTotalPaid(clientId)
        val totalReturnsCredited = productReturnDao.getClientTotalReturnsCredited(clientId)

        val isDebit = client.balanceType == "مدين" || client.balanceType == "عليه لنا"
        val calculatedBalance = (if (isDebit) client.initialBalance else -client.initialBalance) + totalInvoiced - totalPaid - totalReturnsCredited

        if (client.balance != calculatedBalance) {
            val updatedClient = client.copy(balance = calculatedBalance)
            clientDao.updateClient(updatedClient)
        }
    }

    // --- Items ---
    fun getAllItemsFlow(): Flow<List<Item>> = itemDao.getAllItemsFlow()
    suspend fun getAllItems(): List<Item> = withContext(Dispatchers.IO) { itemDao.getAllItems() }
    suspend fun getItemById(id: Int): Item? = withContext(Dispatchers.IO) { itemDao.getItemById(id) }
    suspend fun getItemByBarcode(barcode: String): Item? = withContext(Dispatchers.IO) { itemDao.getItemByBarcode(barcode) }

    suspend fun insertItem(item: Item): Long = withContext(Dispatchers.IO) {
        val id = itemDao.insertItem(item)
        insertLog("إضافة", "الأصناف", "تم إضافة الصنف: ${item.name}")
        id
    }

    suspend fun updateItem(item: Item) = withContext(Dispatchers.IO) {
        itemDao.updateItem(item)
        insertLog("تعديل", "الأصناف", "تم تعديل الصنف: ${item.name}")
    }

    suspend fun deleteItem(item: Item) = withContext(Dispatchers.IO) {
        itemDao.deleteItem(item)
        insertLog("حذف", "الأصناف", "تم حذف الصنف: ${item.name}")
    }

    fun searchItems(query: String): Flow<List<Item>> = itemDao.searchItems("%$query%")
    suspend fun autocompleteItems(query: String): List<Item> = withContext(Dispatchers.IO) { itemDao.autocompleteItems("%$query%") }
    suspend fun getItemByName(name: String): Item? = withContext(Dispatchers.IO) { itemDao.getItemByName(name) }
    suspend fun getItemByNameAndCompany(name: String, companyName: String): Item? = withContext(Dispatchers.IO) { itemDao.getItemByNameAndCompany(name, companyName) }

    // --- Invoices ---
    fun getAllInvoicesFlow(): Flow<List<Invoice>> = invoiceDao.getAllInvoicesFlow()
    fun getInvoiceByIdFlow(id: Int): Flow<Invoice?> = invoiceDao.getInvoiceByIdFlow(id)
    suspend fun getInvoiceById(id: Int): Invoice? = withContext(Dispatchers.IO) { invoiceDao.getInvoiceById(id) }
    fun getInvoicesByClientFlow(clientId: Int): Flow<List<Invoice>> = invoiceDao.getInvoicesByClientFlow(clientId)

    suspend fun insertInvoice(invoice: Invoice, items: List<InvoiceItem>): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val invoiceId = invoiceDao.insertInvoice(invoice).toInt()
            
            // Delete old items if updating (in case id is specified, though Room update is usually separate)
            invoiceDao.deleteInvoiceItemsByInvoiceId(invoiceId)
            
            // Insert invoice items
            items.forEach { item ->
                invoiceDao.insertInvoiceItem(item.copy(invoiceId = invoiceId))
                
                // Update stock quantities if item is stored in inventory
                item.itemId?.let { storedItemId ->
                    itemDao.getItemById(storedItemId)?.let { storedItem ->
                        val newQty = (storedItem.quantity - item.quantity).coerceAtLeast(0)
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            // Recalculate client's balance
            recalculateClientBalance(invoice.clientId)
            
            val typeStr = if (invoice.isQuickInvoice) "سريعة" else "مفصلة"
            insertLog("إضافة", "الفواتير", "تم إنشاء فاتورة $typeStr رقم: ${invoice.invoiceNumber} للعميل: ${invoice.clientName}")
            if (invoice.isCreditOverride) {
                insertLog(
                    "تجاوز ائتماني",
                    "الائتمان",
                    "⚠️ تم منح تجاوز استثنائي للحد الائتماني بمبلغ ${invoice.overrideAmount} للفاتورة ${invoice.invoiceNumber} للعميل: ${invoice.clientName} بواسطة: ${invoice.overrideAuthorizer ?: "الإدارة"}. السبب: ${invoice.overrideReason ?: "غير محدد"}"
                )
            }
            invoiceId.toLong()
        }
    }

    suspend fun deleteInvoice(invoice: Invoice) = withContext(Dispatchers.IO) {
        db.withTransaction {
            // Restore inventory stock before deletion
            val items = invoiceDao.getInvoiceItems(invoice.id)
            items.forEach { item ->
                item.itemId?.let { storedItemId ->
                    itemDao.getItemById(storedItemId)?.let { storedItem ->
                        val newQty = storedItem.quantity + item.quantity
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            invoiceDao.deleteInvoiceItemsByInvoiceId(invoice.id)
            invoiceDao.deleteInvoice(invoice)
            recalculateClientBalance(invoice.clientId)
            insertLog("حذف", "الفواتير", "تم حذف الفاتورة رقم: ${invoice.invoiceNumber} للعميل: ${invoice.clientName}")
        }
    }

    fun getAllInvoiceItemsFlow(): Flow<List<InvoiceItem>> = invoiceDao.getAllInvoiceItemsFlow()
    fun getInvoiceItemsFlow(invoiceId: Int): Flow<List<InvoiceItem>> = invoiceDao.getInvoiceItemsFlow(invoiceId)
    suspend fun getInvoiceItems(invoiceId: Int): List<InvoiceItem> = withContext(Dispatchers.IO) { invoiceDao.getInvoiceItems(invoiceId) }
    suspend fun getAllInvoiceItems(): List<InvoiceItem> = withContext(Dispatchers.IO) { invoiceDao.getAllInvoiceItems() }
    suspend fun getInvoiceItemsForInvoices(invoiceIds: List<Int>): List<InvoiceItem> = withContext(Dispatchers.IO) {
        if (invoiceIds.isEmpty()) emptyList()
        else {
            invoiceIds.chunked(500).flatMap { chunk ->
                invoiceDao.getInvoiceItemsForInvoices(chunk)
            }
        }
    }

    // --- Payments ---
    fun getAllPaymentsFlow(): Flow<List<Payment>> = paymentDao.getAllPaymentsFlow()
    fun getPaymentsByClientFlow(clientId: Int): Flow<List<Payment>> = paymentDao.getPaymentsByClientFlow(clientId)
    suspend fun getPaymentsByClient(clientId: Int): List<Payment> = withContext(Dispatchers.IO) { paymentDao.getPaymentsByClient(clientId) }

    suspend fun insertPayment(payment: Payment): Long = withContext(Dispatchers.IO) {
        val id = paymentDao.insertPayment(payment)
        recalculateClientBalance(payment.clientId)
        val client = clientDao.getClientById(payment.clientId)
        insertLog("إضافة", "المدفوعات", "تم تسجيل دفعة بقيمة: ${payment.amount} للعميل: ${client?.name ?: "غير معروف"}")
        id
    }

    suspend fun deletePayment(payment: Payment) = withContext(Dispatchers.IO) {
        paymentDao.deletePayment(payment)
        recalculateClientBalance(payment.clientId)
        val client = clientDao.getClientById(payment.clientId)
        insertLog("حذف", "المدفوعات", "تم حذف دفعة بقيمة: ${payment.amount} للعميل: ${client?.name ?: "غير معروف"}")
    }

    suspend fun updatePayment(payment: Payment) = withContext(Dispatchers.IO) {
        paymentDao.updatePayment(payment)
        recalculateClientBalance(payment.clientId)
        val client = clientDao.getClientById(payment.clientId)
        insertLog("تعديل", "المدفوعات", "تم تعديل دفعة للعميل: ${client?.name ?: "غير معروف"}")
    }

    // --- Categories ---
    fun getAllCategoriesFlow(): Flow<List<ItemCategory>> = itemCategoryDao.getAllCategoriesFlow()
    suspend fun getAllCategories(): List<ItemCategory> = withContext(Dispatchers.IO) { itemCategoryDao.getAllCategories() }
    suspend fun insertCategory(category: ItemCategory): Long = withContext(Dispatchers.IO) { itemCategoryDao.insertCategory(category) }
    suspend fun deleteCategory(category: ItemCategory) = withContext(Dispatchers.IO) { itemCategoryDao.deleteCategory(category) }

    // --- Units ---
    fun getAllUnitsFlow(): Flow<List<ItemUnit>> = itemUnitDao.getAllUnitsFlow()
    suspend fun getAllUnits(): List<ItemUnit> = withContext(Dispatchers.IO) { itemUnitDao.getAllUnits() }
    suspend fun insertUnit(unit: ItemUnit): Long = withContext(Dispatchers.IO) { itemUnitDao.insertUnit(unit) }
    suspend fun deleteUnit(unit: ItemUnit) = withContext(Dispatchers.IO) { itemUnitDao.deleteUnit(unit) }

    // --- Supplier Companies ---
    fun getAllCompaniesFlow(): Flow<List<SupplierCompany>> = supplierCompanyDao.getAllCompaniesFlow()
    suspend fun getAllCompanies(): List<SupplierCompany> = withContext(Dispatchers.IO) { supplierCompanyDao.getAllCompanies() }
    suspend fun insertCompany(company: SupplierCompany): Long = withContext(Dispatchers.IO) {
        val id = supplierCompanyDao.insertCompany(company)
        insertLog("إضافة", "الموردين", "تم إضافة شركة التوريد: ${company.name}")
        id
    }
    suspend fun updateCompany(company: SupplierCompany) = withContext(Dispatchers.IO) {
        supplierCompanyDao.updateCompany(company)
        insertLog("تعديل", "الموردين", "تم تعديل شركة التوريد: ${company.name}")
    }
    suspend fun deleteCompany(company: SupplierCompany) = withContext(Dispatchers.IO) {
        supplierCompanyDao.deleteCompany(company)
        insertLog("حذف", "الموردين", "تم حذف شركة التوريد: ${company.name}")
    }
    fun searchCompanies(query: String): Flow<List<SupplierCompany>> = supplierCompanyDao.searchCompanies("%$query%")


    // --- Item Purchase History ---
    suspend fun insertItemPurchaseHistory(history: ItemPurchaseHistory): Long = db.itemPurchaseHistoryDao().insertPurchaseHistory(history)
    fun getPurchaseHistoryForItem(itemId: Int) = db.itemPurchaseHistoryDao().getPurchaseHistoryForItem(itemId)
    suspend fun getPurchaseHistoryForItemSync(itemId: Int) = db.itemPurchaseHistoryDao().getPurchaseHistoryForItemSync(itemId)
    suspend fun deletePurchaseHistoryForItem(itemId: Int) = db.itemPurchaseHistoryDao().deletePurchaseHistoryForItem(itemId)

    // --- Audit Logs ---
    fun getAllLogsFlow(): Flow<List<AuditLog>> = auditLogDao.getAllLogsFlow()

    // --- Store Settings ---
    fun getSettingsFlow(): Flow<StoreSettings?> = storeSettingsDao.getSettingsFlow()
    suspend fun getSettings(): StoreSettings? = withContext(Dispatchers.IO) { storeSettingsDao.getSettings() }
    
    suspend fun saveSettings(settings: StoreSettings) = withContext(Dispatchers.IO) {
        storeSettingsDao.insertOrUpdateSettings(settings)
        insertLog("تعديل", "الإعدادات", "تم تحديث إعدادات التطبيق والمتجر")
    }

    // --- Installments ---
    private val installmentDao = db.installmentDao()

    fun getAllInstallmentsFlow(): Flow<List<Installment>> = installmentDao.getAllInstallmentsFlow()
    fun getInstallmentsByClientFlow(clientId: Int): Flow<List<Installment>> = installmentDao.getInstallmentsByClientFlow(clientId)
    suspend fun getAllInstallments(): List<Installment> = withContext(Dispatchers.IO) { installmentDao.getAllInstallments() }

    suspend fun insertInstallment(installment: Installment): Long = withContext(Dispatchers.IO) {
        val id = installmentDao.insertInstallment(installment)
        insertLog("إضافة", "الأقساط", "تم إضافة قسط للعميل: ${installment.clientName} بمبلغ: ${installment.amount}")
        id
    }

    suspend fun updateInstallment(installment: Installment) = withContext(Dispatchers.IO) {
        installmentDao.updateInstallment(installment)
        insertLog("تعديل", "الأقساط", "تم تعديل قسط للعميل: ${installment.clientName}")
    }

    suspend fun deleteInstallment(installment: Installment) = withContext(Dispatchers.IO) {
        installmentDao.deleteInstallment(installment)
        insertLog("حذف", "الأقساط", "تم حذف قسط للعميل: ${installment.clientName}")
    }

    // --- Installment Reminders ---
    private val installmentReminderDao = db.installmentReminderDao()

    fun getAllRemindersFlow(): Flow<List<InstallmentReminder>> = installmentReminderDao.getAllRemindersFlow()
    fun getUnhandledRemindersFlow(): Flow<List<InstallmentReminder>> = installmentReminderDao.getUnhandledRemindersFlow()
    suspend fun getUnhandledReminders(): List<InstallmentReminder> = withContext(Dispatchers.IO) {
        installmentReminderDao.getUnhandledReminders()
    }
    suspend fun getReminderById(id: Int): InstallmentReminder? = withContext(Dispatchers.IO) {
        installmentReminderDao.getReminderById(id)
    }
    suspend fun insertReminder(reminder: InstallmentReminder): Long = withContext(Dispatchers.IO) {
        val id = installmentReminderDao.insertReminder(reminder)
        val typeLabel = if (reminder.reminderType == InstallmentReminderType.GENERAL_INSTALLMENT) "تنبيه أقساط عام" else "تنبيه قسط للعميل: ${reminder.customerName}"
        insertLog("إضافة", "تنبيهات الأقساط", "تم إنشاء $typeLabel")
        id
    }
    suspend fun updateReminder(reminder: InstallmentReminder) = withContext(Dispatchers.IO) {
        installmentReminderDao.updateReminder(reminder)
    }
    suspend fun deleteReminder(reminder: InstallmentReminder) = withContext(Dispatchers.IO) {
        installmentReminderDao.deleteReminder(reminder)
        insertLog("حذف", "تنبيهات الأقساط", "تم حذف التنبيه رقم: ${reminder.id}")
    }
    suspend fun markReminderHandled(id: Int, handledAt: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        installmentReminderDao.markReminderHandled(id, handledAt)
    }
    suspend fun markReminderDisplayed(id: Int) = withContext(Dispatchers.IO) {
        installmentReminderDao.markReminderDisplayed(id)
    }
    suspend fun cancelRemindersForInstallment(installmentId: Int) = withContext(Dispatchers.IO) {
        installmentReminderDao.cancelRemindersForInstallment(installmentId)
    }

    // --- Backup History ---
    fun getAllBackupHistoryFlow(): Flow<List<BackupHistory>> = backupHistoryDao.getAllHistory()
    
    suspend fun insertBackupHistory(history: BackupHistory): Long = withContext(Dispatchers.IO) {
        backupHistoryDao.insert(history)
    }

    suspend fun enforceBackupRetentionPolicy(retentionCount: Int) = withContext(Dispatchers.IO) {
        backupHistoryDao.enforceRetentionPolicy(retentionCount)
    }

    // --- Product Returns (نظام المرتجعات) ---
    fun getAllReturnsFlow(): Flow<List<ProductReturn>> = productReturnDao.getAllReturnsFlow()
    suspend fun getAllReturns(): List<ProductReturn> = withContext(Dispatchers.IO) { productReturnDao.getAllReturns() }
    fun getReturnsByTypeFlow(type: String): Flow<List<ProductReturn>> = productReturnDao.getReturnsByTypeFlow(type)
    fun getReturnsByClientFlow(clientId: Int): Flow<List<ProductReturn>> = productReturnDao.getReturnsByClientFlow(clientId)
    suspend fun getReturnsByClient(clientId: Int): List<ProductReturn> = withContext(Dispatchers.IO) { productReturnDao.getReturnsByClient(clientId) }
    fun getReturnsByInvoiceIdFlow(invoiceId: Int): Flow<List<ProductReturn>> = productReturnDao.getReturnsByInvoiceIdFlow(invoiceId)
    suspend fun getReturnsByInvoiceId(invoiceId: Int): List<ProductReturn> = withContext(Dispatchers.IO) { productReturnDao.getReturnsByInvoiceId(invoiceId) }
    fun getReturnByIdFlow(id: Int): Flow<ProductReturn?> = productReturnDao.getReturnByIdFlow(id)
    suspend fun getReturnById(id: Int): ProductReturn? = withContext(Dispatchers.IO) { productReturnDao.getReturnById(id) }

    fun getAllReturnItemsFlow(): Flow<List<ProductReturnItem>> = productReturnDao.getAllReturnItemsFlow()
    fun getReturnItemsFlow(returnId: Int): Flow<List<ProductReturnItem>> = productReturnDao.getReturnItemsFlow(returnId)
    suspend fun getReturnItems(returnId: Int): List<ProductReturnItem> = withContext(Dispatchers.IO) { productReturnDao.getReturnItems(returnId) }
    suspend fun getAllReturnItems(): List<ProductReturnItem> = withContext(Dispatchers.IO) { productReturnDao.getAllReturnItems() }

    suspend fun saveCustomerReturn(
        productReturn: ProductReturn,
        items: List<ProductReturnItem>
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val returnId = productReturnDao.insertReturn(productReturn).toInt()

            items.forEach { item ->
                productReturnDao.insertReturnItem(item.copy(returnId = returnId))

                // If condition is "صالح للبيع", return item back to resellable stock
                if (item.itemCondition == "صالح للبيع" && item.itemId != null) {
                    itemDao.getItemById(item.itemId)?.let { storedItem ->
                        val newQty = storedItem.quantity + item.quantity
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            // Recalculate client's balance if client is attached
            productReturn.clientId?.let { clientId ->
                recalculateClientBalance(clientId)
            }

            insertLog(
                "إضافة",
                "المرتجعات",
                "تم تسجيل مرتجع مبيعات رقم ${productReturn.returnNumber} للعميل: ${productReturn.clientName} بقيمة: ${productReturn.totalAmount} ${productReturn.currency}"
            )

            returnId.toLong()
        }
    }

    suspend fun savePurchaseReturn(
        productReturn: ProductReturn,
        items: List<ProductReturnItem>
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val returnId = productReturnDao.insertReturn(productReturn).toInt()

            items.forEach { item ->
                productReturnDao.insertReturnItem(item.copy(returnId = returnId))

                // For purchase return, deduct stock from inventory (cannot be negative)
                if (item.itemId != null) {
                    itemDao.getItemById(item.itemId)?.let { storedItem ->
                        val newQty = (storedItem.quantity - item.quantity).coerceAtLeast(0)
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            insertLog(
                "إضافة",
                "المرتجعات",
                "تم تسجيل مرتجع مشتريات رقم ${productReturn.returnNumber} للمورد: ${productReturn.supplierName} بقيمة: ${productReturn.totalAmount} ${productReturn.currency}"
            )

            returnId.toLong()
        }
    }

    suspend fun deleteReturn(productReturn: ProductReturn) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val items = productReturnDao.getReturnItems(productReturn.id)

            // Revert inventory changes safely
            items.forEach { item ->
                if (item.itemId != null) {
                    itemDao.getItemById(item.itemId)?.let { storedItem ->
                        val newQty = if (productReturn.type == "CUSTOMER") {
                            // If it was customer return and was resellable, reverse addition by deducting
                            if (item.itemCondition == "صالح للبيع") {
                                (storedItem.quantity - item.quantity).coerceAtLeast(0)
                            } else storedItem.quantity
                        } else {
                            // If it was purchase return, reverse deduction by adding back to stock
                            storedItem.quantity + item.quantity
                        }
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            productReturnDao.deleteReturnItemsByReturnId(productReturn.id)
            productReturnDao.deleteReturn(productReturn)

            // Recalculate client balance if it was a customer return
            if (productReturn.type == "CUSTOMER" && productReturn.clientId != null) {
                recalculateClientBalance(productReturn.clientId)
            }

            insertLog(
                "حذف",
                "المرتجعات",
                "تم حذف سند المرتجع رقم: ${productReturn.returnNumber} بقيمة: ${productReturn.totalAmount}"
            )
        }
    }
}
