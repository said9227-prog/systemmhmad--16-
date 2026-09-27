package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object FormatUtils {
    private val decimalSymbols = DecimalFormatSymbols(Locale.US)
    private val cleanFormat = DecimalFormat("#,##0.##", decimalSymbols)
    private val standardFormat = DecimalFormat("0.##", decimalSymbols)

    /**
     * Formats a number cleanly with thousand separators.
     * e.g. 170000.0 -> "170,000"
     * e.g. 12000.50 -> "12,000.5"
     * e.g. 0.0 -> "0"
     */
    fun formatAmount(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        return cleanFormat.format(value)
    }

    fun formatPlain(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        return standardFormat.format(value)
    }

    fun formatAmount(value: Float): String = formatAmount(value.toDouble())
    fun formatAmount(value: Number): String = formatAmount(value.toDouble())
    fun formatAmount(value: Int): String = formatAmount(value.toDouble())
    fun formatAmount(value: Long): String = formatAmount(value.toDouble())

    fun formatWithCurrency(value: Double, currency: String = "﷼"): String {
        return "${formatAmount(value)} $currency"
    }

    fun formatWithCurrency(value: Float, currency: String = "﷼"): String {
        return "${formatAmount(value)} $currency"
    }
}

// Global extension functions for convenient access across Jetpack Compose UI
fun Double.toCleanString(): String = FormatUtils.formatAmount(this)
fun Float.toCleanString(): String = FormatUtils.formatAmount(this)
fun Number.toCleanString(): String = FormatUtils.formatAmount(this)
