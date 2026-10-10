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
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.data.InventoryItem
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale

@Composable
fun SkuBarcodeLabelDialog(item: InventoryItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copies by remember(item.id) { mutableStateOf("1") }
    val labelBitmap = remember(item.name, item.sku, item.listPrice, item.sellingPrice, item.sizeCode, item.description) {
        createSkuLabelBitmap(item.name, item.sku.orEmpty(), labelMrp(item), item.sizeCode, item.description)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SKU barcode label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    bitmap = labelBitmap.asImageBitmap(),
                    contentDescription = "Label with product name, price, and Code 128 SKU barcode",
                    modifier = Modifier.fillMaxWidth().height(132.dp),
                    contentScale = ContentScale.Fit
                )
                Text("SKU: ${item.sku.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = copies,
                    onValueChange = { copies = it.filter(Char::isDigit).take(3) },
                    label = { Text("Number of labels") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Creates a 50 × 25 mm PDF label for FlashLabel PRO with product name, size, details, MRP, and SKU barcode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = item.sku != null && (copies.toIntOrNull() ?: 0) > 0,
                onClick = {
                    shareSkuLabelPdf(context, listOf(item to (copies.toIntOrNull() ?: 1)))
                }
            ) { Text("Open PDF in printer app") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bulk print SKU labels") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose products and label quantities. A 50 × 25 mm PDF will contain one label per page.", style = MaterialTheme.typography.bodySmall)
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
                    val labels = items.mapNotNull { item -> quantities[item.id]?.let { item to it } }
                    onDismiss()
                    shareSkuLabelPdf(context, labels)
                }
            ) { Text("Create PDF ($selectedCount)") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
                val bitmap = createSkuLabelBitmap(
                    item.name,
                    item.sku.orEmpty(),
                    labelMrp(item),
                    item.sizeCode,
                    item.description
                )
                page.canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(0f, 0f, pageInfo.pageWidth.toFloat(), pageInfo.pageHeight.toFloat()),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                bitmap.recycle()
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
    // 400 × 200 pixels matches 50 × 25 mm at approximately 203 dpi.
    val labelWidth = 400
    val labelHeight = 200
    val bitmap = Bitmap.createBitmap(labelWidth, labelHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawRect(
        3f,
        3f,
        labelWidth - 3f,
        labelHeight - 3f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
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

    val padding = 12f
    val right = labelWidth - padding
    val sizeText = size.trim().takeUnless { it.isBlank() || it.equals("NA", ignoreCase = true) }.orEmpty()
    if (sizeText.isNotBlank()) canvas.drawText(sizeText, right, 25f, sizePaint)

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
    canvas.drawText(firstLine.toString(), padding, 25f, titlePaint)

    val secondLine = nameWords.drop(wordIndex).joinToString(" ")
    if (secondLine.isNotBlank()) {
        canvas.drawText(ellipsizeLabelText(secondLine, titlePaint, labelWidth - padding * 2), padding, 51f, titlePaint)
    }

    val metadata = details.trim()
    val mrpText = "MRP: ₹" + String.format(Locale.US, if (mrp % 1.0 == 0.0) "%.0f/-" else "%.2f/-", mrp)
    val metadataMaxWidth = labelWidth - padding * 2 - pricePaint.measureText(mrpText) - 10f
    if (metadata.isNotBlank()) {
        canvas.drawText(ellipsizeLabelText(metadata, detailPaint, metadataMaxWidth), padding, 82f, detailPaint)
    }
    canvas.drawText(mrpText, right, 82f, pricePaint)

    val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.MARGIN, 8)
    }
    val matrix = Code128Writer().encode(sku, BarcodeFormat.CODE_128, labelWidth - 28, 61, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val rowOffset = y * matrix.width
        for (x in 0 until matrix.width) {
            pixels[rowOffset + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    val barcodeBitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
    canvas.drawBitmap(barcodeBitmap, (labelWidth - matrix.width) / 2f, 94f, null)
    canvas.drawText(sku, labelWidth / 2f, 177f, codePaint)
    barcodeBitmap.recycle()
    return bitmap
}

private fun ellipsizeLabelText(text: String, paint: Paint, maxWidth: Float): String {
    var result = text.trim()
    while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) {
        result = result.dropLast(1)
    }
    return if (result.length < text.trim().length) "$result…" else result
}
