package com.example.data

import java.util.Locale

data class ProductSizeOption(val code: String, val label: String)

object ProductSizeCatalog {
    val options = listOf(
        ProductSizeOption("NB", "New Born"),
        ProductSizeOption("03M", "0-3 Months"),
        ProductSizeOption("36M", "3-6 Months"),
        ProductSizeOption("69M", "6-9 Months"),
        ProductSizeOption("912M", "9-12 Months"),
        ProductSizeOption("1218M", "12-18 Months"),
        ProductSizeOption("1824M", "18-24 Months"),
        ProductSizeOption("12Y", "1 - 2 Years"),
        ProductSizeOption("23Y", "2 - 3 Years"),
        ProductSizeOption("NA", "No size")
    )

    private val byCode = options.associateBy { it.code }

    fun displayLabel(sizeCode: String): String {
        val normalized = codeForInput(sizeCode) ?: sizeCode.trim().uppercase(Locale.ROOT)
        return byCode[normalized]?.label ?: when (normalized) {
            "03" -> "0-3 Months"
            "36" -> "3-6 Months"
            "69" -> "6-9 Months"
            "912" -> "9-12 Months"
            "1218" -> "12-18 Months"
            "1824" -> "18-24 Months"
            "12" -> "1 - 2 Years"
            "23" -> "2 - 3 Years"
            else -> normalized
        }
    }

    /** Converts a readable size range, or an existing compact code, into the stored SKU component. */
    fun codeForInput(value: String): String? {
        val compact = value.trim().uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")
        val code = when (compact) {
            "", "NA", "NOSIZE", "NOTAPPLICABLE" -> "NA"
            "NB", "NEWBORN" -> "NB"
            "03", "03M", "03MONTH", "03MONTHS" -> "03M"
            "36", "36M", "36MONTH", "36MONTHS" -> "36M"
            "69", "69M", "69MONTH", "69MONTHS" -> "69M"
            "912", "912M", "912MONTH", "912MONTHS" -> "912M"
            "1218", "1218M", "1218MONTH", "1218MONTHS" -> "1218M"
            "1824", "1824M", "1824MONTH", "1824MONTHS" -> "1824M"
            "12", "12Y", "12YEAR", "12YEARS" -> "12Y"
            "23", "23Y", "23YEAR", "23YEARS" -> "23Y"
            else -> compact
        }
        return code.takeIf { it.matches(Regex("^[A-Z0-9]{1,4}$")) }
    }
}
