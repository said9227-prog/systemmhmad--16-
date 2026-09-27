package com.example.domain.report

import com.example.data.model.Client
import com.example.data.model.Installment
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.Item
import com.example.data.model.Payment
import com.example.data.model.ProductReturn
import com.example.data.model.SupplierCompany

object ReportEngine {

    fun generateReport(
        dateRange: DateRange,
        selectedCurrency: String, // "ALL" or specific
        invoices: List<Invoice>,
        invoiceItems: List<InvoiceItem>,
        payments: List<Payment>,
        clients: List<Client>,
        items: List<Item>,
        installments: List<Installment>,
        suppliers: List<SupplierCompany>,
        returns: List<ProductReturn> = emptyList()
    ): ReportData {
        val currencies = if (selectedCurrency == "كل العملات") {
            invoices.map { it.currency }
                .plus(payments.map { it.currency })
                .plus(returns.map { it.currency })
                .distinct()
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("الريال اليمني") }
        } else {
            listOf(selectedCurrency)
        }

        val reports = currencies.map { currency ->
            calculateForCurrency(
                currency = currency,
                dateRange = dateRange,
                invoices = invoices,
                invoiceItems = invoiceItems,
                payments = payments,
                clients = clients,
                items = items,
                installments = installments,
                suppliers = suppliers,
                returns = returns
            )
        }

        return ReportData(
            selectedCurrency = selectedCurrency,
            dateRange = dateRange,
            currencyReports = reports
        )
    }

    private fun calculateForCurrency(
        currency: String,
        dateRange: DateRange,
        invoices: List<Invoice>,
        invoiceItems: List<InvoiceItem>,
        payments: List<Payment>,
        clients: List<Client>,
        items: List<Item>,
        installments: List<Installment>,
        suppliers: List<SupplierCompany>,
        returns: List<ProductReturn> = emptyList()
    ): CurrencyReport {
        // 1. Filter by currency and date range
        val periodInvoices = invoices.filter {
            it.currency == currency && !it.isDraft && it.date in dateRange.start..dateRange.end
        }
        val draftInvoices = invoices.filter {
             it.currency == currency && it.isDraft && it.date in dateRange.start..dateRange.end
        }
        
        val periodPayments = payments.filter {
            it.currency == currency && it.date in dateRange.start..dateRange.end
        }
        
        val periodReturns = returns.filter {
            it.currency == currency && it.date in dateRange.start..dateRange.end
        }
        val totalSalesReturns = periodReturns.filter { it.type == "CUSTOMER" }.sumOf { it.totalAmount }
        val totalPurchaseReturns = periodReturns.filter { it.type == "PURCHASE" }.sumOf { it.totalAmount }

        // For Debt Aging, we need ALL unpaid invoices for this currency (not just in period)
        val allUnpaidInvoices = invoices.filter {
            it.currency == currency && !it.isDraft && it.remainingAmount > 0
        }

        // Installments for this currency
        val currencyInstallments = installments.filter { it.currency == currency }
        val periodInstallments = currencyInstallments.filter { it.dueDate in dateRange.start..dateRange.end }
        
        val now = System.currentTimeMillis()

        // --- SALES REPORT ---
        val totalSales = periodInvoices.sumOf { it.totalAmount }
        val netSales = (totalSales - totalSalesReturns).coerceAtLeast(0.0)
        val totalPayments = periodPayments.sumOf { it.amount }
        val totalRemaining = periodInvoices.sumOf { it.remainingAmount }
        val invoiceCount = periodInvoices.size
        val averageInvoiceValue = if (invoiceCount > 0) totalSales / invoiceCount else 0.0
        
        val fullyPaidCount = periodInvoices.count { it.remainingAmount <= 0.0 && it.totalAmount > 0.0 }
        val partiallyPaidCount = periodInvoices.count { it.paidAmount > 0.0 && it.remainingAmount > 0.0 }
        val unpaidCount = periodInvoices.count { it.paidAmount <= 0.0 && it.totalAmount > 0.0 }
        val draftCount = draftInvoices.size

        val activeCustomersCount = periodInvoices.map { it.clientId }.distinct().size
        
        val topCustomerEntry = periodInvoices.groupBy { it.clientName }
            .mapValues { entry -> entry.value.sumOf { it.totalAmount } }
            .maxByOrNull { it.value }
            
        val topCustomerName = topCustomerEntry?.key ?: "-"
        val topCustomerAmount = topCustomerEntry?.value ?: 0.0

        // Items sold in this period (for this currency's invoices)
        val periodInvoiceIds = periodInvoices.map { it.id }.toSet()
        val periodInvoiceItems = invoiceItems.filter { it.invoiceId in periodInvoiceIds }
        
        val topItemEntry = periodInvoiceItems.groupBy { it.itemName }
            .mapValues { entry -> entry.value.sumOf { it.totalPrice } }
            .maxByOrNull { it.value }
            
        val topItemName = topItemEntry?.key ?: "-"
        val topItemAmount = topItemEntry?.value ?: 0.0

        val salesReport = SalesReport(
            totalSales = totalSales,
            totalReturns = totalSalesReturns,
            netSales = netSales,
            totalPayments = totalPayments,
            totalRemaining = totalRemaining,
            invoiceCount = invoiceCount,
            averageInvoiceValue = averageInvoiceValue,
            fullyPaidCount = fullyPaidCount,
            partiallyPaidCount = partiallyPaidCount,
            unpaidCount = unpaidCount,
            draftCount = draftCount,
            activeCustomersCount = activeCustomersCount,
            topCustomerName = topCustomerName,
            topCustomerAmount = topCustomerAmount,
            topItemName = topItemName,
            topItemAmount = topItemAmount
        )

        // --- CUSTOMER REPORT ---
        val dueInstallments = periodInstallments.filter { !it.isPaid }.sumOf { it.amount - it.paidAmount }
        val overdueInstallments = currencyInstallments.filter { !it.isPaid && it.dueDate < now }.sumOf { it.amount - it.paidAmount }
        
        var debtorsCount = 0
        var creditorsCount = 0
        var neutralCount = 0
        
        // This calculates across ALL clients to answer "العملاء المدينون" based on current balance
        clients.forEach { client ->
            when {
                client.balance > 0 -> debtorsCount++
                client.balance < 0 -> creditorsCount++
                else -> neutralCount++
            }
        }

        val customerReport = CustomerReport(
            activeCustomersCount = activeCustomersCount,
            totalReceivables = totalSales,
            totalReceipts = totalPayments,
            totalRemaining = totalRemaining,
            totalDueInstallments = dueInstallments,
            totalOverdueInstallments = overdueInstallments,
            debtorsCount = debtorsCount,
            creditorsCount = creditorsCount,
            neutralCount = neutralCount
        )

        // --- INVENTORY REPORT ---
        // Inventory is not currency-specific typically, but we output it once per report
        val totalItemsCount = items.size
        val availableItemsCount = items.count { it.quantity > it.minQuantityAlert }
        val lowStockItemsCount = items.count { it.quantity <= it.minQuantityAlert && it.quantity > 0 }
        val outOfStockItemsCount = items.count { it.quantity <= 0 }
        val totalQuantity = items.sumOf { it.quantity }
        val inventoryValue = items.sumOf { it.quantity * it.purchasePrice }
        val soldQuantityPeriod = periodInvoiceItems.sumOf { it.quantity }

        val inventoryReport = InventoryReport(
            totalItemsCount = totalItemsCount,
            availableItemsCount = availableItemsCount,
            lowStockItemsCount = lowStockItemsCount,
            outOfStockItemsCount = outOfStockItemsCount,
            totalQuantity = totalQuantity,
            inventoryValue = inventoryValue,
            soldQuantityPeriod = soldQuantityPeriod
        )

        // --- COLLECTION REPORT ---
        val collectionRatio = if (totalSales > 0) (totalPayments / totalSales) * 100.0 else 0.0
        val collectionReport = CollectionReport(
            totalDue = totalSales,
            totalCollected = totalPayments,
            totalRemaining = totalRemaining,
            collectionRatio = if (collectionRatio.isNaN() || collectionRatio.isInfinite()) 0.0 else collectionRatio,
            dueInstallments = dueInstallments,
            overdueInstallments = overdueInstallments
        )

        // --- DEBT AGING REPORT ---
        var currentDue = 0.0
        var overdue1_7 = 0.0
        var overdue8_30 = 0.0
        var overdue31_60 = 0.0
        var overdue60Plus = 0.0
        val agingCustomerIds = mutableSetOf<Int>()
        
        val MILLIS_IN_DAY = 24 * 60 * 60 * 1000L
        
        allUnpaidInvoices.forEach { inv ->
            agingCustomerIds.add(inv.clientId)
            val diffDays = (now - inv.date) / MILLIS_IN_DAY
            when {
                diffDays <= 0 -> currentDue += inv.remainingAmount
                diffDays in 1..7 -> overdue1_7 += inv.remainingAmount
                diffDays in 8..30 -> overdue8_30 += inv.remainingAmount
                diffDays in 31..60 -> overdue31_60 += inv.remainingAmount
                else -> overdue60Plus += inv.remainingAmount
            }
        }

        val debtAgingReport = DebtAgingReport(
            currentDue = currentDue,
            overdue1_7 = overdue1_7,
            overdue8_30 = overdue8_30,
            overdue31_60 = overdue31_60,
            overdue60Plus = overdue60Plus,
            totalInvoices = allUnpaidInvoices.size,
            totalCustomers = agingCustomerIds.size
        )

        // --- FINANCIAL ANALYSIS ---
        val financialAnalysis = FinancialAnalysis(
            totalSales = totalSales,
            netSales = netSales,
            totalReceipts = totalPayments,
            accountsReceivable = allUnpaidInvoices.sumOf { it.remainingAmount },
            cashFlow = totalPayments // Cash flow is just receipts since payments to suppliers are not supported
        )

        // --- PURCHASE & SUPPLIER REPORT ---
        val periodItems = items.filter { it.purchaseDate in dateRange.start..dateRange.end }
        val totalPurchases = periodItems.sumOf { it.quantity * it.purchasePrice }
        val netPurchases = (totalPurchases - totalPurchaseReturns).coerceAtLeast(0.0)
        val purchaseInvoiceCount = periodItems.size
        
        val uniqueSuppliersCount = items.map { if (it.supplierType == "شركة") it.supplierCompanyName else it.individualSupplierName }
            .filter { it.isNotBlank() }
            .distinct()
            .size
            .coerceAtLeast(suppliers.size)

        val purchaseReport = PurchaseReport(
            isSupported = true,
            totalPurchases = totalPurchases,
            netPurchases = netPurchases,
            totalPayments = totalPurchases,
            totalRemaining = 0.0,
            invoiceCount = purchaseInvoiceCount,
            supplierCount = uniqueSuppliersCount
        )

        val supplierReport = SupplierReport(
            isSupported = true,
            supplierCount = uniqueSuppliersCount
        )

        return CurrencyReport(
            currency = currency,
            salesReport = salesReport,
            purchaseReport = purchaseReport,
            returnsReport = ReturnsReport(
                isSupported = true,
                totalSalesReturns = totalSalesReturns,
                totalPurchaseReturns = totalPurchaseReturns
            ),
            customerReport = customerReport,
            supplierReport = supplierReport,
            inventoryReport = inventoryReport,
            collectionReport = collectionReport,
            debtAgingReport = debtAgingReport,
            financialAnalysis = financialAnalysis
        )
    }
}
