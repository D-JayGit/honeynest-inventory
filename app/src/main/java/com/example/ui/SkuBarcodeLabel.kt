package com.example.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.data.InventoryItem
import com.example.data.ProductColorCatalog
import com.example.data.ProductSizeCatalog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun SkuBarcodeLabelDialog(item: InventoryItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copies by remember(item.id) { mutableStateOf("1") }
    val excelExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    writeFlashLabelWorkbook(output, listOf(item to (copies.toIntOrNull() ?: 1)))
                } ?: error("Could not open the selected file")
            }.isSuccess
            Toast.makeText(
                context,
                if (saved) "Excel label data saved. Import it from FlashLabel Pro → Excel import." else "Could not save the Excel label file.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SKU barcode label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("FlashLabel prints these values using its own label template:", style = MaterialTheme.typography.bodySmall)
                Text("Name: ${item.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Color: ${ProductColorCatalog.displayLabel(item.colorCode, item.colorName)}")
                Text("Size: ${ProductSizeCatalog.displayLabel(item.sizeCode)}")
                Text("MRP: ₹" + String.format(Locale.getDefault(), "%.2f", labelMrp(item)))
                Text("Barcode data: ${item.sku.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = copies,
                    onValueChange = { copies = it.filter(Char::isDigit).take(3) },
                    label = { Text("Number of labels") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Export this Excel file, then import it in FlashLabel Pro. Bind the label's text fields to Name, Color, Size, MRP, and Details; set its Code 128 barcode to the Barcode field.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !item.sku.isNullOrBlank() && (copies.toIntOrNull() ?: 0) > 0,
                onClick = {
                    excelExportLauncher.launch("Honeynest-label-data.xlsx")
                }
            ) { Text("Export Excel for FlashLabel") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun BulkSkuBarcodeLabelDialog(items: List<InventoryItem>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val printableItems = items.filter { !it.sku.isNullOrBlank() }
    val quantities = remember { mutableStateMapOf<Long, Int>() }
    val selectedCount = quantities.values.sum()
    val excelExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    val labelRows = items.mapNotNull { product -> quantities[product.id]?.let { product to it } }
                    writeFlashLabelWorkbook(output, labelRows)
                } ?: error("Could not open the selected file")
            }.isSuccess
            Toast.makeText(
                context,
                if (saved) "Excel label data saved. Import it from FlashLabel Pro → Excel import." else "Could not save the Excel label file.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk print SKU labels") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Excel export creates one data row per label. In a 50 × 25 mm FlashLabel template, bind text to Name, Color, Size, MRP, and Details; set Code 128 to the Barcode field, then import the workbook.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (quantities.size == printableItems.size) quantities.clear()
                        else printableItems.forEach { quantities[it.id] = maxOf(quantities[it.id] ?: 0, 1) }
                    }) {
                        Text(if (quantities.size == printableItems.size) "Clear all" else "Select all")
                    }
                    Text("$selectedCount labels", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
                }
                LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(printableItems, key = { it.id }) { item ->
                        val quantity = quantities[item.id] ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = quantity > 0, onCheckedChange = { checked ->
                                if (checked) quantities[item.id] = 1 else quantities.remove(item.id)
                            })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("₹" + String.format(Locale.getDefault(), "%.2f", item.sellingPrice) + " · " + (item.sku ?: "SKU pending"), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            if (quantity > 0) {
                                IconButton(onClick = { if (quantity <= 1) quantities.remove(item.id) else quantities[item.id] = quantity - 1 }) {
                                    Text("−")
                                }
                                Text(quantity.toString())
                                IconButton(onClick = { quantities[item.id] = quantity + 1 }) {
                                    Text("+")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedCount > 0,
                onClick = {
                    excelExportLauncher.launch("Honeynest-bulk-label-data.xlsx")
                }
            ) { Text("Export Excel ($selectedCount labels)") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private fun writeFlashLabelWorkbook(output: OutputStream, labelQuantities: List<Pair<InventoryItem, Int>>) {
    require(labelQuantities.isNotEmpty() && labelQuantities.all { it.first.sku.isNullOrBlank().not() && it.second > 0 })
    val workbook = ZipOutputStream(output)

    fun addEntry(name: String, content: String) {
        workbook.putNextEntry(ZipEntry(name))
        workbook.write(content.toByteArray(Charsets.UTF_8))
        workbook.closeEntry()
    }

    addEntry(
        "[Content_Types].xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
    )
    addEntry(
        "_rels/.rels",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    )
    addEntry(
        "xl/workbook.xml",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Labels" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
    )
    addEntry(
        "xl/_rels/workbook.xml.rels",
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
    )

    workbook.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
    fun xmlText(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
    fun cell(reference: String, value: String): String =
        "<c r=\"$reference\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlText(value)}</t></is></c>"
    val headers = listOf("Name", "Color", "Size", "MRP", "Barcode", "SKU", "Details")
    val columns = listOf("A", "B", "C", "D", "E", "F", "G")
    workbook.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>".toByteArray(Charsets.UTF_8))
    workbook.write("<row r=\"1\">".toByteArray(Charsets.UTF_8))
    headers.forEachIndexed { index, header -> workbook.write(cell("${columns[index]}1", header).toByteArray(Charsets.UTF_8)) }
    workbook.write("</row>".toByteArray(Charsets.UTF_8))

    var rowNumber = 2
    labelQuantities.forEach { (item, quantity) ->
        val rowValues = listOf(
            item.name,
            ProductColorCatalog.displayLabel(item.colorCode, item.colorName).takeUnless { item.colorCode.equals("NA", true) }.orEmpty(),
            ProductSizeCatalog.displayLabel(item.sizeCode).takeUnless { item.sizeCode.equals("NA", true) }.orEmpty(),
            String.format(Locale.US, if (labelMrp(item) % 1.0 == 0.0) "%.0f" else "%.2f", labelMrp(item)),
            item.sku.orEmpty(),
            item.sku.orEmpty(),
            item.description
        )
        repeat(quantity) {
            workbook.write("<row r=\"$rowNumber\">".toByteArray(Charsets.UTF_8))
            rowValues.forEachIndexed { index, value ->
                workbook.write(cell("${columns[index]}$rowNumber", value).toByteArray(Charsets.UTF_8))
            }
            workbook.write("</row>".toByteArray(Charsets.UTF_8))
            rowNumber++
        }
    }
    workbook.write("</sheetData></worksheet>".toByteArray(Charsets.UTF_8))
    workbook.closeEntry()
    workbook.finish()
}

private fun shareSkuLabelPdf(context: Context, labelQuantities: List<Pair<InventoryItem, Int>>) {
    if (labelQuantities.isEmpty() || labelQuantities.any { it.first.sku.isNullOrBlank() || it.second <= 0 }) return

    try {
        val outputDirectory = File(context.cacheDir, "labels").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val pdfFile = File(outputDirectory, "Honeynest-labels-$timestamp.pdf")
        val pdf = PdfDocument()
        var pageNumber = 0
        labelQuantities.forEach { (item, quantity) ->
            repeat(quantity) {
                pageNumber += 1
                // 50 × 25 mm is approximately 142 × 71 points at 72 dpi.
                val pageInfo = PdfDocument.PageInfo.Builder(142, 71, pageNumber).create()
                val page = pdf.startPage(pageInfo)
                page.canvas.save()
                page.canvas.scale(pageInfo.pageWidth / 400f, pageInfo.pageHeight / 200f)
                drawSkuLabelContent(
                    page.canvas,
                    item.name,
                    item.sku.orEmpty(),
                    labelMrp(item),
                    item.sizeCode,
                    item.description
                )
                page.canvas.restore()
                pdf.finishPage(page)
            }
        }
        FileOutputStream(pdfFile).use { pdf.writeTo(it) }
        pdf.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, pdfFile.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Open labels with"))
    } catch (error: Exception) {
        Toast.makeText(context, "Could not prepare the label PDF: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
    }
}

private fun labelMrp(item: InventoryItem): Double =
    if (item.listPrice > 0.0) item.listPrice else item.sellingPrice

private fun createSkuLabelBitmap(
    productName: String,
    sku: String,
    mrp: Double,
    size: String,
    details: String
): Bitmap {
    val labelWidth = 400
    val labelHeight = 200
    val bitmap = Bitmap.createBitmap(labelWidth, labelHeight, Bitmap.Config.ARGB_8888)
    drawSkuLabelContent(Canvas(bitmap), productName, sku, mrp, size, details)
    return bitmap
}

private fun drawSkuLabelContent(
    canvas: Canvas,
    productName: String,
    sku: String,
    mrp: Double,
    size: String,
    details: String
) {
    val labelWidth = 400
    val labelHeight = 200
    canvas.drawColor(android.graphics.Color.WHITE)
    val borderInset = 12f
    canvas.drawRect(
        borderInset,
        borderInset,
        labelWidth - borderInset,
        labelHeight - borderInset,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
    )

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 22f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val sizePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 21f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 17f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
    }
    val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 17f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        textAlign = Paint.Align.RIGHT
    }
    val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 14f
        typeface = android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    val padding = 18f
    val right = labelWidth - padding
    val sizeText = size.trim().takeUnless { it.isBlank() || it.equals("NA", ignoreCase = true) }
        ?.let(ProductSizeCatalog::displayLabel)
        .orEmpty()
    if (sizeText.isNotBlank()) canvas.drawText(sizeText, right, 34f, sizePaint)

    val nameWords = productName.trim().ifBlank { "Inventory item" }.split(Regex("\\s+"))
    val firstLineMaxWidth = if (sizeText.isBlank()) labelWidth - padding * 2 else {
        labelWidth - padding * 2 - sizePaint.measureText(sizeText) - 10f
    }
    val firstLine = StringBuilder()
    var wordIndex = 0
    while (wordIndex < nameWords.size) {
        val candidate = if (firstLine.isEmpty()) nameWords[wordIndex] else "$firstLine ${nameWords[wordIndex]}"
        if (titlePaint.measureText(candidate) > firstLineMaxWidth) break
        if (firstLine.isNotEmpty()) firstLine.append(' ')
        firstLine.append(nameWords[wordIndex])
        wordIndex++
    }
    if (firstLine.isEmpty() && wordIndex < nameWords.size) {
        firstLine.append(ellipsizeLabelText(nameWords[wordIndex], titlePaint, firstLineMaxWidth))
        wordIndex++
    }
    canvas.drawText(firstLine.toString(), padding, 34f, titlePaint)

    val secondLine = nameWords.drop(wordIndex).joinToString(" ")
    if (secondLine.isNotBlank()) {
        canvas.drawText(ellipsizeLabelText(secondLine, titlePaint, labelWidth - padding * 2), padding, 59f, titlePaint)
    }

    val metadata = details.trim()
    val mrpText = "MRP: ₹" + String.format(Locale.US, if (mrp % 1.0 == 0.0) "%.0f/-" else "%.2f/-", mrp)
    val metadataMaxWidth = labelWidth - padding * 2 - pricePaint.measureText(mrpText) - 10f
    if (metadata.isNotBlank()) {
        canvas.drawText(ellipsizeLabelText(metadata, detailPaint, metadataMaxWidth), padding, 82f, detailPaint)
    }
    canvas.drawText(mrpText, right, 82f, pricePaint)

    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.MARGIN, 0)
    }
    val matrix = Code128Writer().encode(sku, BarcodeFormat.CODE_128, 1, 1, hints)
    val quietZoneModules = 10f
    val availableWidth = labelWidth - padding * 2
    val moduleWidth = availableWidth / (matrix.width + quietZoneModules * 2)
    val barcodeLeft = (labelWidth - availableWidth) / 2f + quietZoneModules * moduleWidth
    val barcodeTop = 94f
    val barcodeHeight = 67f
    val barcodePaint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = false
        isFilterBitmap = false
    }
    var x = 0
    while (x < matrix.width) {
        if (!matrix[x, 0]) {
            x++
            continue
        }
        val start = x
        while (x < matrix.width && matrix[x, 0]) x++
        canvas.drawRect(
            barcodeLeft + start * moduleWidth,
            barcodeTop,
            barcodeLeft + x * moduleWidth,
            barcodeTop + barcodeHeight,
            barcodePaint
        )
    }
    canvas.drawText(sku, labelWidth / 2f, 181f, codePaint)
}

private fun ellipsizeLabelText(text: String, paint: Paint, maxWidth: Float): String {
    var result = text.trim()
    while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) {
        result = result.dropLast(1)
    }
    return if (result.length < text.trim().length) "$result…" else result
}
