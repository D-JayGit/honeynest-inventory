package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.InventoryItem
import com.example.data.ProductSheetImportResult
import com.example.data.ProductSheetPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PRODUCTS_TEMPLATE =
    "barcode,name,description,category,quantity,threshold,purchase_price,mrp,discount_percentage,product_code,color_code,color_name,size\n"

@Composable
fun BulkProductSheetActions(
    onPreviewCsv: (String, (ProductSheetPreview) -> Unit) -> Unit,
    onImportProducts: (List<InventoryItem>, (ProductSheetImportResult) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<ProductSheetPreview?>(null) }
    var result by remember { mutableStateOf<ProductSheetImportResult?>(null) }
    var isReading by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    val templateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { writeTemplate(context, uri) }
                Toast.makeText(
                    context,
                    if (saved) "Template saved. Open it in Google Sheets, fill the rows, then export as CSV." else "Could not save the template.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isReading = true
            scope.launch {
                val contents = withContext(Dispatchers.IO) { readCsv(context, uri) }
                if (contents == null) {
                    isReading = false
                    Toast.makeText(context, "Could not read that CSV file.", Toast.LENGTH_LONG).show()
                } else {
                    onPreviewCsv(contents) { parsed ->
                        isReading = false
                        preview = parsed
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { templateLauncher.launch("honeynest_products_template.csv") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Download Google Sheets template") }
        Button(
            onClick = { csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) },
            enabled = !isReading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isReading) "Reading…" else "Import CSV from Google Sheets") }
    }

    preview?.let { parsed ->
        AlertDialog(
            onDismissRequest = { if (!isImporting) preview = null },
            title = { Text("Review product import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${parsed.products.size} valid products from ${parsed.rowCount} rows.")
                    Text("Rows with errors will be skipped. Blank barcodes are allowed; the app generates a barcode and SKU. Format barcode cells as plain text to preserve leading zeros.")
                    if (parsed.errors.isNotEmpty()) {
                        Text("Skipped rows", fontWeight = FontWeight.SemiBold)
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            items(parsed.errors.take(30)) { error -> Text("• $error") }
                            if (parsed.errors.size > 30) {
                                item { Text("And ${parsed.errors.size - 30} more row errors…") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = parsed.products.isNotEmpty() && !isImporting,
                    onClick = {
                        isImporting = true
                        onImportProducts(parsed.products) { imported ->
                            isImporting = false
                            preview = null
                            result = imported.copy(errors = parsed.errors + imported.errors)
                        }
                    }
                ) { Text(if (isImporting) "Importing…" else "Import ${parsed.products.size} products") }
            },
            dismissButton = {
                TextButton(onClick = { preview = null }, enabled = !isImporting) { Text("Cancel") }
            }
        )
    }

    result?.let { importResult ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("Import finished") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Imported ${importResult.importedCount} products.")
                    if (importResult.errors.isNotEmpty()) {
                        Text("${importResult.errors.size} rows were skipped:", fontWeight = FontWeight.SemiBold)
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            items(importResult.errors.take(30)) { error -> Text("• $error") }
                            if (importResult.errors.size > 30) {
                                item { Text("And ${importResult.errors.size - 30} more row errors…") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { result = null }) { Text("Done") } }
        )
    }
}

private fun readCsv(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
} catch (_: Exception) {
    null
}

private fun writeTemplate(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(PRODUCTS_TEMPLATE) } != null
} catch (_: Exception) {
    false
}
