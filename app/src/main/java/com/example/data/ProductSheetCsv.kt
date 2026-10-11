package com.example.data

data class ProductSheetPreview(
    val products: List<InventoryItem>,
    val errors: List<String>,
    val rowCount: Int
)

data class ProductSheetImportResult(
    val importedCount: Int,
    val errors: List<String>
)

/** Parses a CSV exported from Google Sheets using the app's product template. */
fun parseProductSheetCsv(csv: String, existingItems: List<InventoryItem>): ProductSheetPreview {
    val records = parseCsvRecords(csv.removePrefix("\uFEFF"))
        .filterNot { row -> row.all { it.isBlank() } }
    if (records.isEmpty()) return ProductSheetPreview(emptyList(), listOf("The selected file is empty."), 0)

    val headers = records.first().map { header ->
        when (header.trim().lowercase().replace(' ', '_')) {
            "product_name" -> "name"
            "mrp" -> "list_price"
            "stock", "initial_stock" -> "quantity"
            "size_code", "age_size", "size_label" -> "size"
            else -> header.trim().lowercase().replace(' ', '_')
        }
    }
    val nameIndex = headers.indexOf("name")
    if (nameIndex < 0) {
        return ProductSheetPreview(emptyList(), listOf("Missing required column: name."), records.size - 1)
    }

    val barcodeIndex = headers.indexOf("barcode")
    val existingBarcodes = existingItems
        .flatMap { listOf(it.barcode, it.sku.orEmpty()) }
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .toMutableSet()
    val products = mutableListOf<InventoryItem>()
    val errors = mutableListOf<String>()

    records.drop(1).forEachIndexed { rowIndex, row ->
        val rowNumber = rowIndex + 2
        fun value(column: String, default: String = ""): String {
            val index = headers.indexOf(column)
            return if (index >= 0) row.getOrNull(index)?.trim().orEmpty().ifBlank { default } else default
        }
        fun parseInt(column: String, default: Int): Int? = value(column, default.toString()).toIntOrNull()
        fun parsePrice(column: String, default: Double): Double? = value(column, default.toString())
            .replace("₹", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace(",", "")
            .trim()
            .toDoubleOrNull()

        val name = row.getOrNull(nameIndex)?.trim().orEmpty()
        val barcode = if (barcodeIndex >= 0) row.getOrNull(barcodeIndex)?.trim().orEmpty() else ""
        val productCode = SkuGenerator.normalizeCode(value("product_code", "GN"))
        val colorCode = SkuGenerator.normalizeCode(value("color_code", "NA"))
        val sizeInput = value("size", value("size_code", "NA"))
        val sizeCode = ProductSizeCatalog.codeForInput(sizeInput) ?: SkuGenerator.normalizeCode(sizeInput)
        val quantity = parseInt("quantity", 0)
        val threshold = parseInt("threshold", 5)
        val purchasePrice = parsePrice("purchase_price", 0.0)
        val listPrice = parsePrice("list_price", 0.0)
        val discount = parsePrice("discount_percentage", 0.0)

        val rowErrors = mutableListOf<String>()
        if (name.isBlank()) rowErrors += "product name is blank"
        if (quantity == null || quantity < 0) rowErrors += "quantity must be a non-negative whole number"
        if (threshold == null || threshold < 0) rowErrors += "threshold must be a non-negative whole number"
        if (purchasePrice == null || !purchasePrice.isFinite() || purchasePrice < 0.0) rowErrors += "purchase_price must be a non-negative number"
        if (listPrice == null || !listPrice.isFinite() || listPrice < 0.0) rowErrors += "mrp must be a non-negative number"
        if (discount == null || !discount.isFinite() || discount !in 0.0..100.0) rowErrors += "discount_percentage must be from 0 to 100"
        if (!ProductColorCatalog.isKnownCode(colorCode) && value("color_name").isBlank()) {
            rowErrors += "custom colors need a color_name"
        }
        if (!SkuGenerator.isValidComponents(productCode, colorCode, sizeCode)) {
            rowErrors += "product_code and color_code need 2 letters/numbers; size must be New Born, an age range through 2–3 Years, or a 1–4 character code"
        }
        if (barcode.isNotBlank() && barcode.lowercase() in existingBarcodes) {
            rowErrors += "barcode is already used in inventory or repeated in this file"
        }

        if (rowErrors.isNotEmpty()) {
            errors += "Row $rowNumber: ${rowErrors.joinToString("; ")}."
            return@forEachIndexed
        }
        if (barcode.isNotBlank()) existingBarcodes += barcode.lowercase()

        val sellingPrice = listPrice!! * (1.0 - discount!! / 100.0)
        products += InventoryItem(
            barcode = barcode,
            name = name,
            description = value("description"),
            category = value("category"),
            currentQuantity = quantity!!,
            threshold = threshold!!,
            purchasePrice = purchasePrice!!,
            sellingPrice = sellingPrice,
            listPrice = listPrice!!,
            discountPercentage = discount!!,
            productCode = productCode,
            colorCode = colorCode,
            colorName = value("color_name"),
            sizeCode = sizeCode
        )
    }

    if (products.isEmpty() && errors.isEmpty()) errors += "No product rows were found below the header."
    return ProductSheetPreview(products, errors, records.size - 1)
}

private fun parseCsvRecords(csv: String): List<List<String>> {
    val records = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var index = 0

    while (index < csv.length) {
        val char = csv[index]
        when {
            char == '"' && quoted && index + 1 < csv.length && csv[index + 1] == '"' -> {
                cell.append('"')
                index++
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                row += cell.toString()
                cell.setLength(0)
            }
            (char == '\n' || char == '\r') && !quoted -> {
                row += cell.toString()
                cell.setLength(0)
                records += row
                row = mutableListOf()
                if (char == '\r' && index + 1 < csv.length && csv[index + 1] == '\n') index++
            }
            else -> cell.append(char)
        }
        index++
    }
    if (quoted) throw IllegalArgumentException("A quoted cell was not closed.")
    if (cell.isNotEmpty() || row.isNotEmpty()) {
        row += cell.toString()
        records += row
    }
    return records
}
