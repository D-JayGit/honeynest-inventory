package com.example.data

import java.util.Locale
object SkuGenerator {
    private val twoCharacterCode = Regex("^[A-Z0-9]{2}$")
    private val sizeCodePattern = Regex("^[A-Z0-9]{1,4}$")

    fun normalizeCode(value: String): String =
        value.trim().uppercase(Locale.ROOT)

    fun isValidComponents(
        productCode: String,
        colorCode: String,
        sizeCode: String
    ): Boolean =
        twoCharacterCode.matches(normalizeCode(productCode)) &&
                twoCharacterCode.matches(normalizeCode(colorCode)) &&
                sizeCodePattern.matches(normalizeCode(sizeCode))

    fun preview(productCode: String, colorCode: String, sizeCode: String): String {
        val product = normalizeCode(productCode).ifBlank { "??" }
        val color = normalizeCode(colorCode).ifBlank { "??" }
        val size = normalizeCode(sizeCode).ifBlank { "?" }
        return "HN-$product-$color-$size-#####"
    }

    fun create(
        productCode: String,
        colorCode: String,
        sizeCode: String,
        itemId: Long
    ): String {
        require(isValidComponents(productCode, colorCode, sizeCode))
        require(itemId > 0)

        val sequence = itemId.toString().padStart(5, '0')
        return "HN-${normalizeCode(productCode)}-${normalizeCode(colorCode)}-${normalizeCode(sizeCode)}-$sequence"
    }
}