package com.example.data

import java.util.Locale

data class ProductColorOption(val code: String, val label: String)

object ProductColorCatalog {
    val options = listOf(
        ProductColorOption("BK", "Black"),
        ProductColorOption("BL", "Blue"),
        ProductColorOption("BR", "Brown"),
        ProductColorOption("CR", "Cream"),
        ProductColorOption("GN", "Green"),
        ProductColorOption("GY", "Grey"),
        ProductColorOption("MR", "Maroon"),
        ProductColorOption("MU", "Multicolor"),
        ProductColorOption("NV", "Navy"),
        ProductColorOption("OR", "Orange"),
        ProductColorOption("PK", "Pink"),
        ProductColorOption("PU", "Purple"),
        ProductColorOption("RD", "Red"),
        ProductColorOption("WH", "White"),
        ProductColorOption("YL", "Yellow"),
        ProductColorOption("NA", "Not specified")
    )

    private val byCode = options.associateBy { it.code }
    private val byLabel = options.associateBy { it.label.uppercase(Locale.ROOT) }

    fun displayLabel(colorCode: String, customName: String = ""): String {
        if (customName.isNotBlank()) return customName
        val normalized = colorCode.trim().uppercase(Locale.ROOT)
        return byCode[normalized]?.label ?: normalized
    }

    fun isKnownCode(colorCode: String): Boolean = colorCode.trim().uppercase(Locale.ROOT) in byCode

    fun codeForInput(value: String): String? {
        val normalized = value.trim().uppercase(Locale.ROOT)
        return byCode[normalized]?.code ?: byLabel[normalized]?.code
    }
}
