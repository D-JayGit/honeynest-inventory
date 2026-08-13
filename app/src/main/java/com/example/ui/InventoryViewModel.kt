package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.InlineData
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = InventoryRepository(db.inventoryDao())

    // UI state
    val allItems: StateFlow<List<InventoryItem>> = repository.allItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allTransactions: StateFlow<List<StockTransaction>> = repository.allTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allReconciliationSessions: StateFlow<List<ReconciliationSession>> = repository.allReconciliationSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Low stock flow
    val lowStockItems: StateFlow<List<InventoryItem>> = allItems.map { items ->
        items.filter { it.currentQuantity <= it.threshold }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current active reconciliation session items
    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    val activeReconciliationItems: StateFlow<List<ReconciliationItem>> = _activeSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) flowOf(emptyList())
        else repository.getReconciliationItems(sessionId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // In-app Notification list
    private val _notifications = MutableStateFlow<List<NotificationMessage>>(emptyList())
    val notifications: StateFlow<List<NotificationMessage>> = _notifications.asStateFlow()

    // Bill Processing State
    private val _billScanState = MutableStateFlow<BillScanState>(BillScanState.Idle)
    val billScanState: StateFlow<BillScanState> = _billScanState.asStateFlow()

    // Scan simulator state helper
    private val _recentScannedItem = MutableStateFlow<InventoryItem?>(null)
    val recentScannedItem: StateFlow<InventoryItem?> = _recentScannedItem.asStateFlow()

    private val _scannedBarcodeNotFound = MutableStateFlow<String?>(null)
    val scannedBarcodeNotFound: StateFlow<String?> = _scannedBarcodeNotFound.asStateFlow()

    // Advanced Barcode API state flows
    private val _apiLookupState = MutableStateFlow<ApiLookupState>(ApiLookupState.Idle)
    val apiLookupState: StateFlow<ApiLookupState> = _apiLookupState.asStateFlow()

    private val _scannedProductPreFill = MutableStateFlow<ExternalProductInfo?>(null)
    val scannedProductPreFill: StateFlow<ExternalProductInfo?> = _scannedProductPreFill.asStateFlow()

    init {
        // Seed initial data if empty to make it look professional on first launch
        seedInitialData()
    }

    private fun seedInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = db.inventoryDao().getAllInventoryItems().firstOrNull()?.size ?: 0
            if (count == 0) {
                // Populate realistic items
                val initialItems = listOf(
                    InventoryItem(barcode = "8901030752101", name = "Classic Olive Oil 1L", description = "Extra virgin olive oil", category = "Cooking Oil", currentQuantity = 12, threshold = 4, purchasePrice = 8.50, listPrice = 12.00, sellingPrice = 10.80, discountPercentage = 10.0),
                    InventoryItem(barcode = "049000000443", name = "Diet Coke 355mL", description = "Carbonated low calorie soft drink", category = "Beverages", currentQuantity = 2, threshold = 6, purchasePrice = 0.40, listPrice = 1.20, sellingPrice = 1.20, discountPercentage = 0.0),
                    InventoryItem(barcode = "011110038364", name = "Whole Wheat Bread", description = "Freshly baked high fiber bread", category = "Bakery", currentQuantity = 15, threshold = 5, purchasePrice = 1.20, listPrice = 2.50, sellingPrice = 2.25, discountPercentage = 10.0),
                    InventoryItem(barcode = "8902514002361", name = "Greek Yogurt Honey", description = "Creamy thick greek yogurt 500g", category = "Dairy", currentQuantity = 3, threshold = 5, purchasePrice = 1.80, listPrice = 3.60, sellingPrice = 3.24, discountPercentage = 10.0),
                    InventoryItem(barcode = "4902430222037", name = "Microfiber Dish Cloths", description = "Ultrafine dust & kitchen cloth 3-pack", category = "Household", currentQuantity = 8, threshold = 3, purchasePrice = 2.50, listPrice = 5.00, sellingPrice = 4.50, discountPercentage = 10.0)
                )
                
                initialItems.forEach { item ->
                    repository.saveItemWithTransaction(
                        item,
                        StockTransaction(
                            itemId = 0,
                            barcode = item.barcode,
                            itemName = item.name,
                            changeQuantity = item.currentQuantity,
                            transactionType = "MANUAL_ADD",
                            note = "Initial stock seeding"
                        )
                    )
                }

                // Create a sample demo bill reconciliation session
                val demoSession = ReconciliationSession(
                    billName = "Main Wholesale Order #8930",
                    totalBillItems = 4,
                    verifiedItemsCount = 2,
                    status = "OPEN"
                )
                val demoItems = listOf(
                    ReconciliationItem(name = "Classic Olive Oil 1L", expectedQuantity = 5, expectedPrice = 8.50, actualScannedQuantity = 5, actualPriceMatch = true, isVerified = true),
                    ReconciliationItem(name = "Diet Coke 355mL", expectedQuantity = 24, expectedPrice = 0.40, actualScannedQuantity = 2, actualPriceMatch = true, isVerified = false),
                    ReconciliationItem(name = "Whole Wheat Bread", expectedQuantity = 10, expectedPrice = 1.20, actualScannedQuantity = 10, actualPriceMatch = true, isVerified = true),
                    ReconciliationItem(name = "Greek Yogurt Honey", expectedQuantity = 8, expectedPrice = 1.80, actualScannedQuantity = 0, actualPriceMatch = false, isVerified = false)
                )
                repository.createReconciliationSession(demoSession, demoItems)

                // Add sample historical transactions for past days to construct dashboard
                val cal = Calendar.getInstance()
                for (dayOffset in 1..6) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                    
                    // Stock In
                    repository.addTransaction(
                        StockTransaction(
                            itemId = 1,
                            barcode = "8901030752101",
                            itemName = "Classic Olive Oil 1L",
                            changeQuantity = 5 + dayOffset,
                            transactionType = "MANUAL_ADD",
                            timestamp = cal.timeInMillis,
                            note = "Daily supplier shipment"
                        )
                    )
                    // Stock Out
                    repository.addTransaction(
                        StockTransaction(
                            itemId = 2,
                            barcode = "049000000443",
                            itemName = "Diet Coke 355mL",
                            changeQuantity = -(3 + dayOffset % 3),
                            transactionType = "SCAN_OUT",
                            timestamp = cal.timeInMillis + 3600000, // 1 hr later
                            note = "Customer terminal sales"
                        )
                    )
                }
                
                triggerNotification("Welcome to Inventory Tracker! Dummy items seeded.", "INFO")
                checkLowStockAlerts()
            }
        }
    }

    // Notification Handler
    fun triggerNotification(message: String, type: String = "ALERT") {
        val id = UUID.randomUUID().toString()
        val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newNotification = NotificationMessage(id, message, type, formattedTime)
        _notifications.update { listOf(newNotification) + it }
    }

    fun dismissNotification(id: String) {
        _notifications.update { current -> current.filter { it.id != id } }
    }

    fun clearAllNotifications() {
        _notifications.value = emptyList()
    }

    private fun checkLowStockAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = db.inventoryDao().getAllInventoryItems().firstOrNull() ?: emptyList()
            var alertTriggered = false
            items.forEach { item ->
                if (item.currentQuantity <= item.threshold) {
                    // Check if already notified or simply trigger
                    triggerNotification("LOW STOCK ALERT: ${item.name} has only ${item.currentQuantity} remaining (Threshold: ${item.threshold})", "WARNING")
                    alertTriggered = true
                }
            }
            if (!alertTriggered) {
                // Clear any low stock notifications or do nothing
            }
        }
    }

    // Inventory operations
    fun addOrUpdateItem(
        id: Long = 0,
        barcode: String,
        name: String,
        description: String,
        category: String,
        quantity: Int,
        threshold: Int,
        purchasePrice: Double,
        listPrice: Double,
        discountPercentage: Double,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedBarcode = barcode.trim()
            if (normalizedBarcode.isEmpty() || name.isBlank()) {
                triggerNotification("A barcode and product name are required.", "ERROR")
                return@launch
            }
            if (quantity < 0 || threshold < 0 || purchasePrice < 0 || listPrice < 0 || discountPercentage !in 0.0..100.0) {
                triggerNotification("Quantity, prices, and threshold must be valid non-negative values. Discount must be 0–100%.", "ERROR")
                return@launch
            }

            // Calculate selling price
            val sellingPrice = if (discountPercentage > 0.0) {
                listPrice * (1.0 - discountPercentage / 100.0)
            } else {
                listPrice
            }

            val itemWithBarcode = repository.getItemByBarcode(normalizedBarcode)
            val existingItem = if (id > 0) {
                if (itemWithBarcode != null && itemWithBarcode.id != id) {
                    triggerNotification("That barcode is already assigned to ${itemWithBarcode.name}.", "ERROR")
                    return@launch
                }
                repository.getItemById(id)
            } else {
                itemWithBarcode
            }

            val itemToSave = if (existingItem != null) {
                existingItem.copy(
                    barcode = normalizedBarcode,
                    name = name,
                    description = description,
                    category = category,
                    currentQuantity = quantity,
                    threshold = threshold,
                    purchasePrice = purchasePrice,
                    listPrice = listPrice,
                    sellingPrice = sellingPrice,
                    discountPercentage = discountPercentage,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                InventoryItem(
                    barcode = normalizedBarcode,
                    name = name,
                    description = description,
                    category = category,
                    currentQuantity = quantity,
                    threshold = threshold,
                    purchasePrice = purchasePrice,
                    listPrice = listPrice,
                    sellingPrice = sellingPrice,
                    discountPercentage = discountPercentage
                )
            }

            // Calculate change quantity for transaction log
            val change = if (existingItem != null) {
                quantity - existingItem.currentQuantity
            } else {
                quantity
            }

            repository.saveItemWithTransaction(
                itemToSave,
                transaction = if (change != 0) {
                    StockTransaction(
                        itemId = 0,
                        barcode = normalizedBarcode,
                        itemName = name,
                        changeQuantity = change,
                        transactionType = if (change > 0) "MANUAL_ADD" else "MANUAL_SUB",
                        note = "Updated count manually (New total: $quantity)"
                    )
                } else null
            )

            triggerNotification("Saved item: $name", "SUCCESS")
            checkLowStockAlerts()
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun deleteItem(item: InventoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteItem(item)
            triggerNotification("Deleted item: ${item.name}", "INFO")
            checkLowStockAlerts()
        }
    }

    fun deleteAllItems() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllItems()
            triggerNotification("Successfully deleted all inventory products", "WARNING")
        }
    }

    // Barcode Scanning Input Automation
    fun handleBarcodeScanned(barcode: String, scanType: String = "SCAN_IN", customQty: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            _recentScannedItem.value = null
            _scannedBarcodeNotFound.value = null
            _scannedProductPreFill.value = null

            val normalizedBarcode = barcode.trim()
            if (normalizedBarcode.isEmpty() || customQty <= 0) {
                triggerNotification("Enter a barcode and a quantity greater than zero.", "ERROR")
                return@launch
            }
            val requestedChange = if (scanType == "SCAN_IN") customQty else -customQty
            val stockChange = repository.applyBarcodeStockChange(
                barcode = normalizedBarcode,
                requestedChange = requestedChange,
                transactionType = scanType,
                note = "Scanned $scanType of barcode $normalizedBarcode"
            )
            if (stockChange != null) {
                // Determine quantity direction
                val updatedItem = stockChange.first
                val appliedQuantity = kotlin.math.abs(stockChange.second)
                _recentScannedItem.value = updatedItem
                
                val actionLabel = if (scanType == "SCAN_IN") "Added" else "Removed"
                triggerNotification("$actionLabel $appliedQuantity units of ${updatedItem.name}. Remaining: ${updatedItem.currentQuantity}", "SUCCESS")
                
                // If there's an active reconciliation session, increment matches
                if (scanType == "SCAN_IN") {
                    reconcileScannedProduct(updatedItem.name, normalizedBarcode, appliedQuantity)
                }
                checkLowStockAlerts()

                // Background search external product database as requested
                queryExternalProductApi(normalizedBarcode) { /* no-op for existing items */ }
            } else {
                // Scan unregistered product
                triggerNotification("Checking external product database API for barcode: $normalizedBarcode...", "INFO")
                queryExternalProductApi(normalizedBarcode) { info ->
                    if (info != null) {
                        // Match found - prefill details & store barcode to trigger dialog
                        _scannedProductPreFill.value = info
                        _scannedBarcodeNotFound.value = normalizedBarcode
                    } else {
                        // No match found - prompt for manual entry
                        _scannedProductPreFill.value = null
                        _scannedBarcodeNotFound.value = normalizedBarcode
                    }
                }
            }
        }
    }

    // Advanced Barcode Scanning - Fetch name and price from external API (with Gemini AI backup)
    fun queryExternalProductApi(barcode: String, onComplete: (ExternalProductInfo?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _apiLookupState.value = ApiLookupState.Loading
            
            // 1. Check predefined High-Fidelity catalog map for fast, offline, and unit testing lookups
            val predefined = when (barcode.trim()) {
                "8901030752101" -> ExternalProductInfo(barcode, "Classic Extra Virgin Olive Oil 1L", 14.99)
                "049000000443" -> ExternalProductInfo(barcode, "Diet Coke Fridge Pack Can 355mL", 1.25)
                "011110038364" -> ExternalProductInfo(barcode, "Kroger Whole Wheat Sliced Bread", 2.99)
                "8902514002361" -> ExternalProductInfo(barcode, "Greek Strained Yogurt Honey 150g", 3.49)
                "4902430222037" -> ExternalProductInfo(barcode, "P&G Microfiber Durable Dish Cloths", 5.95)
                "737628005000" -> ExternalProductInfo(barcode, "Thai Kitchen Organic Coconut Milk 400mL", 3.89)
                "012000000133" -> ExternalProductInfo(barcode, "Pepsi Cola Classic Soda Can 355mL", 1.10)
                "071429014298" -> ExternalProductInfo(barcode, "Organic Fresh Baby Spinach Bag 16oz", 4.75)
                else -> null
            }
            if (predefined != null) {
                _apiLookupState.value = ApiLookupState.Success(predefined)
                onComplete(predefined)
                return@launch
            }
            
            // 2. Fallback: Query Google Gemini AI acting as our intelligent global product registry lookup!
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                Log.e("InventoryVM", "API Key is empty. External registry can prompt manual entry.")
                _apiLookupState.value = ApiLookupState.NotFound(barcode)
                onComplete(null)
                return@launch
            }
            
            try {
                val prompt = """
                    You are an external global barcode product database registry API.
                    Given the barcode: $barcode
                    
                    Return a JSON object containing:
                    - "name": A real-world, short, clean product title matching or realistically guessed for this exact barcode, e.g. "Colgate Optic White Toothpaste 100g"
                    - "price": A double representing a realistic suggested retail price in USD, e.g. 5.49
                    - "status": A string "FOUND" or "NOT_FOUND". If the barcode looks completely randomized/fake/unrealistic, return "NOT_FOUND".
                    
                    Return only raw JSON. Do not wrap in markdown quotes.
                """.trimIndent()
                
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(responseMimeType = "application/json")
                )
                
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (rawJson != null) {
                    val cleanJson = rawJson.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()
                    
                    val obj = JSONObject(cleanJson)
                    val status = obj.optString("status", "NOT_FOUND")
                    val name = obj.optString("name", "")
                    val price = obj.optDouble("price", 0.0)
                    
                    if (status == "FOUND" && name.isNotEmpty() && price > 0.0) {
                        val info = ExternalProductInfo(barcode, name, price)
                        _apiLookupState.value = ApiLookupState.Success(info)
                        onComplete(info)
                    } else {
                        _apiLookupState.value = ApiLookupState.NotFound(barcode)
                        onComplete(null)
                    }
                } else {
                    _apiLookupState.value = ApiLookupState.NotFound(barcode)
                    onComplete(null)
                }
            } catch (e: Exception) {
                Log.e("InventoryVM", "API Barcode lookup error: ${e.message}", e)
                _apiLookupState.value = ApiLookupState.NotFound(barcode)
                onComplete(null)
            }
        }
    }

    // Direct Sales Section pricing updates
    fun updateItemPricing(item: InventoryItem, basePrice: Double, discount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            if (basePrice < 0 || discount !in 0.0..100.0) {
                triggerNotification("Price must be non-negative and discount must be 0–100%.", "ERROR")
                return@launch
            }
            val finalPrice = if (discount > 0.0) basePrice * (1.0 - discount / 100.0) else basePrice
            val updatedItem = item.copy(
                listPrice = basePrice,
                discountPercentage = discount,
                sellingPrice = finalPrice,
                updatedAt = System.currentTimeMillis()
            )
            repository.saveItemWithTransaction(updatedItem,
                StockTransaction(
                    itemId = item.id,
                    barcode = item.barcode,
                    itemName = item.name,
                    changeQuantity = 0,
                    transactionType = "MANUAL_ADD",
                    note = "Price revised: Base $${String.format(Locale.getDefault(), "%.2f", basePrice)}, Disc ${discount}%, Final Selling $${String.format(Locale.getDefault(), "%.2f", finalPrice)}"
                )
            )
            
            triggerNotification("Updated pricing for ${item.name}: $${String.format(Locale.getDefault(), "%.2f", finalPrice)}", "SUCCESS")
        }
    }

    fun clearScannedState() {
        _recentScannedItem.value = null
        _scannedBarcodeNotFound.value = null
        _scannedProductPreFill.value = null
        _apiLookupState.value = ApiLookupState.Idle
    }

    // Active reconciliation helper
    fun setActiveSession(sessionId: Long?) {
        _activeSessionId.value = sessionId
    }

    private suspend fun reconcileScannedProduct(name: String, barcode: String, quantity: Int) {
        val activeId = _activeSessionId.value ?: return
        val currentItems = repository.getReconciliationItemsSync(activeId)

        // Find a matching item on the bill
        // Match by barcode if available, or loose substring matching on item name
        val match = currentItems.find { 
            (it.barcode.isNotEmpty() && it.barcode == barcode) || 
            it.name.lowercase(Locale.getDefault()) == name.lowercase(Locale.getDefault()) ||
            name.lowercase(Locale.getDefault()).contains(it.name.lowercase(Locale.getDefault())) ||
            it.name.lowercase(Locale.getDefault()).contains(name.lowercase(Locale.getDefault()))
        }

        if (match != null) {
            val newScannedQty = match.actualScannedQuantity + quantity
            val isVerified = newScannedQty >= match.expectedQuantity
            val updatedMatch = match.copy(
                actualScannedQuantity = newScannedQty,
                isVerified = isVerified,
                barcode = if (match.barcode.isEmpty()) barcode else match.barcode
            )
            repository.updateReconciliationItem(updatedMatch)
            triggerNotification("[RECONCILIATION] Updated: ${match.name} ($newScannedQty/${match.expectedQuantity} received)", "INFO")
        }
    }

    fun updateReconciliationCountManually(reconcileItem: ReconciliationItem, newCount: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val isVerified = newCount >= reconcileItem.expectedQuantity
            val updated = reconcileItem.copy(
                actualScannedQuantity = newCount,
                isVerified = isVerified
            )
            repository.updateReconciliationItem(updated)
            triggerNotification("Manually verified received quantity for: ${reconcileItem.name}", "SUCCESS")
        }
    }

    fun toggleReconcileVerified(reconcileItem: ReconciliationItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = reconcileItem.copy(
                isVerified = !reconcileItem.isVerified,
                actualScannedQuantity = if (!reconcileItem.isVerified) reconcileItem.expectedQuantity else 0
            )
            repository.updateReconciliationItem(updated)
            triggerNotification("Reconciliation status toggled for: ${reconcileItem.name}", "INFO")
        }
    }

    fun createManualReconciliationSession(billName: String, rawItemsList: List<ReconciliationItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = ReconciliationSession(
                billName = billName,
                totalBillItems = rawItemsList.size,
                status = "OPEN"
            )
            val sessionId = repository.createReconciliationSession(session, rawItemsList)
            _activeSessionId.value = sessionId
            triggerNotification("Created custom reconciliation session: $billName", "SUCCESS")
        }
    }

    fun deleteReconciliationSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteReconciliationSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = null
            }
            triggerNotification("Deleted reconciliation session", "INFO")
        }
    }

    // Gemini Purchase Bill Scan Core Implementation (Direct REST API)
    fun processBillImageWithGemini(bitmap: Bitmap, customName: String = "Scanned Purchase Invoice") {
        viewModelScope.launch(Dispatchers.IO) {
            _billScanState.value = BillScanState.Loading("Reading bill image with Gemini...")

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _billScanState.value = BillScanState.Error("Receipt analysis requires a configured Gemini API key.")
                triggerNotification("Receipt analysis is unavailable until Gemini is configured.", "ERROR")
                return@launch
            }

            try {
                // 1. Convert bitmap to Base64 JPEG
                val base64Image = bitmap.toBase64()

                // 2. Prepare structured request
                val prompt = """
                    Analyze this purchase bill or invoice image carefully.
                    Extract each item name, quantity, and unit price/expected price.
                    Output a JSON array where each object contains:
                    - "name" (string, short descriptive product name, e.g. "Pure Honey 500g")
                    - "quantity" (integer, quantity purchased)
                    - "price" (double, unit price/cost price representing the purchased rate)
                    - "barcode" (string, the barcode number if clearly visible, otherwise blank "")

                    Strict guidelines: Return ONLY a valid JSON array matching the keys exactly. Do not wrap in markdown or block formatting like ```json.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json"
                    )
                )

                // 3. Make Direct API Call
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (rawJson != null) {
                    processExtractedJson(rawJson, customName)
                } else {
                    _billScanState.value = BillScanState.Error("Gemini returned empty text response.")
                    triggerNotification("Verification failed: Empty response", "ERROR")
                }

            } catch (e: Exception) {
                Log.e("InventoryVM", "Gemini error: ${e.message}", e)
                _billScanState.value = BillScanState.Error("Receipt analysis failed. No reconciliation session was created.")
                triggerNotification("Receipt analysis failed. Check your connection and try again.", "ERROR")
            }
        }
    }

    private suspend fun processExtractedJson(rawJson: String, sessionName: String) {
        withContext(Dispatchers.IO) {
            try {
                val cleanJson = rawJson.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val jsonArray = JSONArray(cleanJson)
                val extractedItems = mutableListOf<ReconciliationItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.optString("name", "Unknown Item")
                    val qty = obj.optInt("quantity", 1)
                    val price = obj.optDouble("price", 0.0)
                    val barcode = obj.optString("barcode", "")

                    extractedItems.add(
                        ReconciliationItem(
                            name = name,
                            expectedQuantity = qty,
                            expectedPrice = price,
                            barcode = barcode
                        )
                    )
                }

                if (extractedItems.isNotEmpty()) {
                    val session = ReconciliationSession(
                        billName = sessionName,
                        totalBillItems = extractedItems.size,
                        status = "OPEN"
                    )
                    val sessionId = repository.createReconciliationSession(session, extractedItems)
                    _activeSessionId.value = sessionId
                    
                    _billScanState.value = BillScanState.Success(sessionName, extractedItems)
                    triggerNotification("Verification Complete! Extracted ${extractedItems.size} items from bill.", "SUCCESS")
                } else {
                    _billScanState.value = BillScanState.Error("No valid products found on the scanned receipt.")
                }

            } catch (e: Exception) {
                Log.e("InventoryVM", "JSON parsing failure: ${e.message}")
                _billScanState.value = BillScanState.Error("Error parsing receipt data: ${e.message}")
            }
        }
    }

    fun resetBillScanState() {
        _billScanState.value = BillScanState.Idle
    }

    // Helper extension function: Bitmap to Base64 String
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}

// State Wrapper Models
sealed interface BillScanState {
    object Idle : BillScanState
    data class Loading(val message: String) : BillScanState
    data class Success(val billName: String, val items: List<ReconciliationItem>) : BillScanState
    data class Error(val error: String) : BillScanState
}

data class NotificationMessage(
    val id: String,
    val text: String,
    val type: String, // "INFO", "WARNING", "SUCCESS", "ERROR"
    val timeLabel: String
)

class InventoryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InventoryViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// External Product Database Lookup Definitions
data class ExternalProductInfo(
    val barcode: String,
    val name: String,
    val price: Double
)

sealed class ApiLookupState {
    object Idle : ApiLookupState()
    object Loading : ApiLookupState()
    data class Success(val productInfo: ExternalProductInfo) : ApiLookupState()
    data class NotFound(val barcode: String) : ApiLookupState()
    data class Error(val message: String) : ApiLookupState()
}
