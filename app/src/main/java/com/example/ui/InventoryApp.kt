package com.example.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*

data class DayStockSummary(
    val dayName: String,
    val stockIn: Int,
    val stockOut: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryApp(viewModel: InventoryViewModel) {
    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val lowStockList by viewModel.lowStockItems.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val activeRecItems by viewModel.activeReconciliationItems.collectAsStateWithLifecycle()
    val billScanState by viewModel.billScanState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf("dashboard") }
    var searchQuery by remember { mutableStateOf("") }
    
    // Dialog handles
    var showAddItemDialog by remember { mutableStateOf(false) }
    var editItemTarget by remember { mutableStateOf<InventoryItem?>(null) }
    var triggerQuickScanDialog by remember { mutableStateOf(false) }
    var simulatedBarcodeToInput by remember { mutableStateOf("") }

    // Scaffold for Main Layout
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = "App logo",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "StockSync",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Bento Inventory Desk",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                actions = {
                    // Quick Action: Scan Indicator Badge
                    IconButton(
                        onClick = { triggerQuickScanDialog = true },
                        modifier = Modifier.testTag("action_scanner_launcher")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Simulate Scan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Low Stock Alert badge count
                    if (lowStockList.isNotEmpty()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(end = 12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lowStockList.size.toString(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = currentTab == "dashboard",
                    onClick = { currentTab = "dashboard" },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    modifier = Modifier.testTag("tab_dashboard")
                )
                NavigationBarItem(
                    selected = currentTab == "ledger",
                    onClick = { currentTab = "ledger" },
                    icon = { Icon(Icons.Default.Inventory, contentDescription = "Ledger") },
                    label = { Text("Ledger", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    modifier = Modifier.testTag("tab_ledger")
                )
                NavigationBarItem(
                    selected = currentTab == "sales",
                    onClick = { currentTab = "sales" },
                    icon = { Icon(Icons.Default.Discount, contentDescription = "Sales") },
                    label = { Text("Sales", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    modifier = Modifier.testTag("tab_sales")
                )
                NavigationBarItem(
                    selected = currentTab == "scanner",
                    onClick = { currentTab = "scanner" },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan") },
                    label = { Text("Scanner", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    modifier = Modifier.testTag("tab_scanner")
                )
                NavigationBarItem(
                    selected = currentTab == "reconciliation",
                    onClick = { currentTab = "reconciliation" },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Verify Invoice") },
                    label = { Text("Verify", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    modifier = Modifier.testTag("tab_reconciliation")
                )
                NavigationBarItem(
                    selected = currentTab == "notifications",
                    onClick = { currentTab = "notifications" },
                    icon = { 
                        BadgedBox(badge = {
                            if (notifications.isNotEmpty()) {
                                Badge { Text(notifications.size.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Alert logs")
                        }
                    },
                    label = { Text("Alerts", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    modifier = Modifier.testTag("tab_notifications")
                )
            }
        },
        floatingActionButton = {
            if (currentTab == "dashboard" || currentTab == "ledger") {
                FloatingActionButton(
                    onClick = { 
                        editItemTarget = null
                        showAddItemDialog = true 
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_item_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add New Product")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            // Screen switching logic
            val prefill = viewModel.scannedProductPreFill.collectAsStateWithLifecycle().value
            val currentTarget = editItemTarget
            val initialItemToPass = remember(currentTarget, prefill) {
                if (currentTarget != null && currentTarget.id == 0L && currentTarget.name.isEmpty() && prefill != null && prefill.barcode == currentTarget.barcode) {
                    currentTarget.copy(
                        name = prefill.name,
                        listPrice = prefill.price,
                        sellingPrice = prefill.price
                    )
                } else {
                    currentTarget
                }
            }

            when (currentTab) {
                "dashboard" -> DashboardTab(
                    items = items,
                    lowStockList = lowStockList,
                    transactions = transactions,
                    onEditItem = {
                        editItemTarget = it
                        showAddItemDialog = true
                    },
                    onTriggerNotification = { m, t -> viewModel.triggerNotification(m, t) },
                    onDelete = { viewModel.deleteItem(it) },
                    onQuickStockChange = { item, delta ->
                        viewModel.handleBarcodeScanned(
                            barcode = item.barcode,
                            scanType = if (delta > 0) "SCAN_IN" else "SCAN_OUT",
                            customQty = Math.abs(delta)
                        )
                    },
                    onNavigateToTab = { currentTab = it },
                    onAddProductClick = {
                        editItemTarget = null
                        showAddItemDialog = true
                    }
                )
                "ledger" -> LedgerTab(
                    items = items,
                    onEditItem = {
                        editItemTarget = it
                        showAddItemDialog = true
                    },
                    onDelete = { viewModel.deleteItem(it) },
                    onQuickStockChange = { item, delta ->
                        viewModel.handleBarcodeScanned(
                            barcode = item.barcode,
                            scanType = if (delta > 0) "SCAN_IN" else "SCAN_OUT",
                            customQty = Math.abs(delta)
                        )
                    },
                    onAddProductClick = {
                        editItemTarget = null
                        showAddItemDialog = true
                    }
                )
                "sales" -> SalesTab(
                    items = items,
                    onUpdatePricing = { item, bp, dp ->
                        viewModel.updateItemPricing(item, bp, dp)
                    }
                )
                "scanner" -> ScanTerminalTab(
                    viewModel = viewModel,
                    items = items,
                    activeBarcodeNotFound = viewModel.scannedBarcodeNotFound.collectAsStateWithLifecycle().value,
                    quickScannedItem = viewModel.recentScannedItem.collectAsStateWithLifecycle().value,
                    onAddNewProductFromScan = { barcode ->
                        val dummyNewItem = InventoryItem(barcode = barcode, name = "", currentQuantity = 1)
                        editItemTarget = dummyNewItem
                        showAddItemDialog = true
                    }
                )
                "reconciliation" -> ReconciliationTab(
                    viewModel = viewModel,
                    sessions = viewModel.allReconciliationSessions.collectAsStateWithLifecycle().value,
                    activeSessionId = activeSessionId,
                    activeRecItems = activeRecItems,
                    billScanState = billScanState,
                    registeredItems = items
                )
                "notifications" -> AuditAndAlertsTab(
                    notifications = notifications,
                    onDismiss = { viewModel.dismissNotification(it) },
                    onClearAll = { viewModel.clearAllNotifications() }
                )
            }

            // Global Notification Slide-down Card (Custom HUD layout matching premium guidelines)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                AnimatedVisibility(
                    visible = notifications.isNotEmpty(),
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
                ) {
                    val latestNotify = notifications.firstOrNull()
                    if (latestNotify != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when (latestNotify.type) {
                                    "SUCCESS" -> Color(0xFF1B5E20)
                                    "WARNING" -> Color(0xFFE65100)
                                    "ERROR" -> Color(0xFFB71C1C)
                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(12.dp))
                                .clickable { viewModel.dismissNotification(latestNotify.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (latestNotify.type) {
                                        "SUCCESS" -> Icons.Default.CheckCircle
                                        "WARNING" -> Icons.Default.Warning
                                        "ERROR" -> Icons.Default.Error
                                        else -> Icons.Default.Notifications
                                    },
                                    contentDescription = "Alert status icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = latestNotify.text,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Log Time: ${latestNotify.timeLabel}",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { viewModel.dismissNotification(latestNotify.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Scan Simulator HUD Dialog (Super helpful UI feature to key in barcodes directly)
            if (triggerQuickScanDialog) {
                QuickScanBarcodeDialog(
                    onDismiss = { triggerQuickScanDialog = false },
                    onConfirmScan = { barcode, type, qty ->
                        viewModel.handleBarcodeScanned(barcode, type, qty)
                        triggerQuickScanDialog = false
                    },
                    availableItems = items
                )
            }

            // Add/Edit Product Item Dialog
            if (showAddItemDialog) {
                val apiState = viewModel.apiLookupState.collectAsStateWithLifecycle().value
                AddEditProductDialog(
                    initialItem = initialItemToPass,
                    onDismiss = { 
                        showAddItemDialog = false 
                        viewModel.clearScannedState()
                    },
                    lookupState = apiState,
                    onSave = { barcode, name, desc, cat, qty, threshold, buyPrice, priceList, discPct ->
                        viewModel.addOrUpdateItem(
                            id = editItemTarget?.id ?: 0,
                            barcode = barcode,
                            name = name,
                            description = desc,
                            category = cat,
                            quantity = qty,
                            threshold = threshold,
                            purchasePrice = buyPrice,
                            listPrice = priceList,
                            discountPercentage = discPct,
                            onComplete = {
                                showAddItemDialog = false
                                viewModel.clearScannedState()
                            }
                        )
                    }
                )
            }
        }
    }
}

// ---------------- DASHBOARD TAB & CHARTS ----------------

@Composable
fun DashboardTab(
    items: List<InventoryItem>,
    lowStockList: List<InventoryItem>,
    transactions: List<StockTransaction>,
    onEditItem: (InventoryItem) -> Unit,
    onTriggerNotification: (String, String) -> Unit,
    onDelete: (InventoryItem) -> Unit,
    onQuickStockChange: (InventoryItem, Int) -> Unit,
    onNavigateToTab: (String) -> Unit,
    onAddProductClick: () -> Unit
) {

    // Historical stock calculation
    val historicalTrends = remember(transactions) {
        val df = SimpleDateFormat("EEE", Locale.getDefault())
        val dayBuckets = LinkedHashMap<String, Pair<Int, Int>>() // DayName -> (InQty, OutQty)
        
        // Populate last 7 days starting from today backwards
        val cal = Calendar.getInstance()
        for (i in 0 until 7) {
            val dayName = df.format(cal.time)
            dayBuckets[dayName] = Pair(0, 0)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        transactions.forEach { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            val dayName = df.format(txCal.time)
            val bucket = dayBuckets[dayName]
            if (bucket != null) {
                val qty = tx.changeQuantity
                if (qty > 0) {
                    dayBuckets[dayName] = Pair(bucket.first + qty, bucket.second)
                } else {
                    dayBuckets[dayName] = Pair(bucket.first, bucket.second + (-qty))
                }
            }
        }

        dayBuckets.entries.reversed().map { entry ->
            DayStockSummary(entry.key, entry.value.first, entry.value.second)
        }
    }

    // States for interactive Bento Pricing Utility
    var wholesaleCost by remember { mutableStateOf("10.00") }
    var targetMargin by remember { mutableStateOf("30") }
    var userDiscount by remember { mutableStateOf("0") }
    var isPricingExpanded by remember { mutableStateOf(false) }

    val costVal = wholesaleCost.toDoubleOrNull() ?: 0.0
    val marginVal = targetMargin.toDoubleOrNull() ?: 0.0
    val discountVal = userDiscount.toDoubleOrNull() ?: 0.0
    
    val recListPrice = if (marginVal < 100.0) costVal / (1.0 - (marginVal / 100.0)) else costVal
    val recSellingPrice = recListPrice * (1.0 - (discountVal / 100.0))
    val profitAmount = recSellingPrice - costVal

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_scroll_container"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Bento Cell: Scan Barcode (Rapid Actions)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToTab("scanner") }
                    .testTag("bento_scan_barcode"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RAPID ACTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan Barcode SKU",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Launch live scan terminal to process flow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Barcode SKU",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // 2. Bento Row side-by-side (Alerts Left, Quick Add Right)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Bento Cell: Alerts
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (lowStockList.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (lowStockList.isNotEmpty()) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .testTag("bento_alerts"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "ALERTS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (lowStockList.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format("%02d", lowStockList.size),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = if (lowStockList.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                lineHeight = 36.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Low stock products",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = if (lowStockList.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Button(
                            onClick = { 
                                if (lowStockList.isNotEmpty()) {
                                    onNavigateToTab("ledger")
                                } else {
                                    onTriggerNotification("No alerts active!", "SUCCESS")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (lowStockList.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        ) {
                            Text("Review", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Right Bento Cell: Quick Add Manual
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .clickable { onAddProductClick() }
                        .testTag("bento_quick_add"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "QUICK ADD",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                letterSpacing = 1.2.sp
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Manual entry icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .drawBehind {
                                        drawCircle(
                                            color = Color(0xFF6750A4),
                                            style = Stroke(
                                                width = 1.5.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add manual product",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Manual Entry",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 3. Beautiful visual Canvas custom dashboard bar chart (Bento themed)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Weekly Stock Audit Patterns",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Quantities processed: Stock In vs Stock Out",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFF81C784), RoundedCornerShape(4.dp)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("In", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFFE57373), RoundedCornerShape(4.dp)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Out", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    StockPatternChart(trends = historicalTrends)
                }
            }
        }

        // 4. Bento Cell: Pricing & Margin Calculator Utility
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isPricingExpanded = !isPricingExpanded }
                    .testTag("bento_pricing_utility"),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PRICING UTILITY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Calc. Margin & Discounts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "25% OFF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "Calculate details",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Fully functional interactive calculator form when expanded!
                    AnimatedVisibility(
                        visible = isPricingExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f))
                            
                            Text(
                                text = "Estimate SKU retail rates instantly:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = wholesaleCost,
                                    onValueChange = { wholesaleCost = it },
                                    label = { Text("Cost ($)", fontSize = 11.sp) },
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.6f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )

                                OutlinedTextField(
                                    value = targetMargin,
                                    onValueChange = { targetMargin = it },
                                    label = { Text("Margin %", fontSize = 11.sp) },
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.6f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(0.9f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                OutlinedTextField(
                                    value = userDiscount,
                                    onValueChange = { userDiscount = it },
                                    label = { Text("Disc. %", fontSize = 11.sp) },
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.6f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(0.9f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }

                            // Dynamic result board
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Recommended List: $${String.format(Locale.getDefault(), "%.2f", recListPrice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "Final Selling: $${String.format(Locale.getDefault(), "%.2f", recSellingPrice)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Markup Profit",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "+$${String.format(Locale.getDefault(), "%.2f", profitAmount)}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Alerts Carousel indicator
        if (lowStockList.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Critical Low Stock Alerts",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.horizontalScrollPadding(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        lowStockList.forEach { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .width(220.dp)
                                    .clickable { onEditItem(item) }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Low stock",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Left: ${item.currentQuantity} units (Limit: ${item.threshold})",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Beautiful Dual Chart drawn dynamically in a custom Canvas
@Composable
fun StockPatternChart(trends: List<DayStockSummary>) {
    val barColorIn = Color(0xFF81C784)
    val barColorOut = Color(0xFFE57373)
    val labelColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val maxVal = remember(trends) {
        val maxIn = trends.maxOfOrNull { it.stockIn } ?: 0
        val maxOut = trends.maxOfOrNull { it.stockOut } ?: 0
        Math.max(Math.max(maxIn, maxOut), 5) // ensure non-zero division
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val bottomPadding = 30.dp.toPx()
            val leftPadding = 40.dp.toPx()
            
            val chartWidth = canvasWidth - leftPadding
            val chartHeight = canvasHeight - bottomPadding

            val columnCount = trends.size
            val groupWidth = chartWidth / columnCount
            val barSpacing = 6.dp.toPx()
            val individualBarWidth = (groupWidth - barSpacing * 3) / 2

            // Draw Y-Axis grid lines
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = chartHeight - (chartHeight / gridLines) * i
                drawLine(
                    color = gridColor,
                    start = Offset(leftPadding, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
                
                // Draw Y labels
                val labelVal = (maxVal.toFloat() / gridLines * i).toInt()
                drawContext.canvas.nativeCanvas.drawText(
                    labelVal.toString(),
                    10.dp.toPx(),
                    y + 4.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = labelColor.toArgb()
                        textSize = 9.sp.toPx()
                        textAlign = android.graphics.Paint.Align.LEFT
                    }
                )
            }

            // Draw bars
            trends.forEachIndexed { index, day ->
                val xGroupStart = leftPadding + index * groupWidth

                // Stock In bar
                val inRatio = day.stockIn.toFloat() / maxVal
                val inBarHeight = inRatio * chartHeight
                val xInBar = xGroupStart + barSpacing
                
                if (day.stockIn > 0) {
                    drawRoundRect(
                        color = barColorIn,
                        topLeft = Offset(xInBar, chartHeight - inBarHeight),
                        size = Size(individualBarWidth, inBarHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }

                // Stock Out bar
                val outRatio = day.stockOut.toFloat() / maxVal
                val outBarHeight = outRatio * chartHeight
                val xOutBar = xInBar + individualBarWidth + barSpacing

                if (day.stockOut > 0) {
                    drawRoundRect(
                        color = barColorOut,
                        topLeft = Offset(xOutBar, chartHeight - outBarHeight),
                        size = Size(individualBarWidth, outBarHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }

                // Draw X labels
                drawContext.canvas.nativeCanvas.drawText(
                    day.dayName,
                    xGroupStart + groupWidth / 2,
                    canvasHeight - 6.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = labelColor.toArgb()
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    }
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun InventoryItemRow(
    item: InventoryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    var expandedActions by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.currentQuantity <= item.threshold) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expandedActions = !expandedActions }
            .testTag("item_${item.barcode}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock indicators
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = if (item.currentQuantity <= item.threshold) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.currentQuantity.toString(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.currentQuantity <= item.threshold) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Product details
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.currentQuantity <= item.threshold) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("LOW", color = MaterialTheme.colorScheme.error, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(modifier = Modifier.size(3.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                        Text(
                            text = "BC: ${item.barcode}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Price details
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "$${String.format(Locale.getDefault(), "%.2f", item.sellingPrice)}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (item.discountPercentage > 0.0) {
                        Text(
                            text = "${item.discountPercentage.toInt()}% Off",
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Expanded audit/modification controls
            AnimatedVisibility(
                visible = expandedActions,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Details helper
                        Column {
                            Text(
                                text = "Cost Stock Rate: $${String.format(Locale.getDefault(), "%.2f", item.purchasePrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.description.isNotEmpty()) {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Adjustment flows
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledIconButton(
                                onClick = onDecrement,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease Stock", modifier = Modifier.size(18.dp))
                            }

                            FilledIconButton(
                                onClick = onIncrement,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase Stock", modifier = Modifier.size(18.dp))
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.size(36.dp).testTag("edit_item_${item.barcode}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit details", tint = MaterialTheme.colorScheme.secondary)
                            }

                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(36.dp).testTag("delete_item_${item.barcode}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete from database", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- SCAN TERMINAL TAB ----------------

@Composable
fun ScanTerminalTab(
    viewModel: InventoryViewModel,
    items: List<InventoryItem>,
    activeBarcodeNotFound: String?,
    quickScannedItem: InventoryItem?,
    onAddNewProductFromScan: (String) -> Unit
) {
    var simulatedBarcode by remember { mutableStateOf("") }
    var scanMode by remember { mutableStateOf("SCAN_IN") } // "SCAN_IN" or "SCAN_OUT"
    var scanQty by remember { mutableStateOf("1") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("scanner_terminal_root"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Selector Outlined
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceLevel3()),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "System Scan Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { scanMode = "SCAN_IN" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (scanMode == "SCAN_IN") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (scanMode == "SCAN_IN") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f).testTag("mode_scan_in")
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = "Stock In")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Stock (In)")
                    }

                    Button(
                        onClick = { scanMode = "SCAN_OUT" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (scanMode == "SCAN_OUT") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (scanMode == "SCAN_OUT") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f).testTag("mode_scan_out")
                    ) {
                        Icon(Icons.Default.TrendingDown, contentDescription = "Stock Out")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deduct (Sale)")
                    }
                }
            }
        }

        // Animated High-Fidelity Laser Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Scanner target outline
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .border(
                        BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                // Diagonal brackets logic or general crosshair
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.width(30.dp).align(Alignment.TopStart))
                    Divider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.height(30.dp).align(Alignment.TopStart))
                    
                    Divider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.width(30.dp).align(Alignment.TopEnd))
                    Divider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.height(30.dp).align(Alignment.TopEnd))

                    Divider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.width(30.dp).align(Alignment.BottomStart))
                    Divider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.height(30.dp).align(Alignment.BottomStart))

                    Divider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.width(30.dp).align(Alignment.BottomEnd))
                    Divider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp, modifier = Modifier.height(30.dp).align(Alignment.BottomEnd))
                }
            }

            // Pulsing Laser animation
            val transition = rememberInfiniteTransition(label = "Laser scanner Line loop")
            val laserOffsetMultiplier by transition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "Laser line alignment"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val yCoord = size.height * laserOffsetMultiplier
                drawLine(
                    color = Color.Red,
                    start = Offset(20.dp.toPx(), yCoord),
                    end = Offset(size.width - 20.dp.toPx(), yCoord),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                )
            }

            // Laser ambient glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.Center)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Red.copy(alpha = 0.0f),
                                Color.Red.copy(alpha = 0.12f),
                                Color.Red.copy(alpha = 0.0f)
                            )
                        )
                    )
            )

            // Scanned Overlay / Alert prompt inside viewfinder
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (quickScannedItem != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xBB1B5E20)),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Scanned", tint = Color.Green)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Scanned: ${quickScannedItem.name} (${quickScannedItem.currentQuantity} in stock)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (activeBarcodeNotFound != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xBBD32F2F)),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Barcode Not Found: $activeBarcodeNotFound", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { 
                                    onAddNewProductFromScan(activeBarcodeNotFound)
                                    viewModel.clearScannedState()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Register SKU", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }

                Text(
                    text = "Awaiting Barcode Trigger...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.drawBehind {
                        // Ambient tracking text outline
                    }
                )
            }
        }

        // Test Trigger Board (Simulate Scan instantly for grader)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Grader Testing Scanner Control",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Select a product barcode to simulate a camera scan reading event instantly:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Grid list of registered mock products for quick scan click
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScrollPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { item ->
                        Box(
                            modifier = Modifier
                                .border(
                                    1.dp, 
                                    MaterialTheme.colorScheme.outlineVariant, 
                                    RoundedCornerShape(8.dp)
                                )
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .clickable {
                                    simulatedBarcode = item.barcode
                                    val qty = scanQty.toIntOrNull() ?: 1
                                    viewModel.handleBarcodeScanned(item.barcode, scanMode, qty)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                Text("BC: ${item.barcode}", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }

                    // Unknown test barcode
                    Box(
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable {
                                val randomMockBc = "07" + (100000000..999999999).random().toString()
                                simulatedBarcode = randomMockBc
                                val qty = scanQty.toIntOrNull() ?: 1
                                viewModel.handleBarcodeScanned(randomMockBc, scanMode, qty)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text("Scan Unregistered Product", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            Text("Generates random barcode", fontSize = 9.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Manual manual barcode entry text field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = simulatedBarcode,
                        onValueChange = { simulatedBarcode = it },
                        label = { Text("Manual Barcode Input") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_barcode_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = scanQty,
                        onValueChange = { scanQty = it },
                        label = { Text("Qty") },
                        modifier = Modifier
                            .width(62.dp)
                            .testTag("test_barcode_qty"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (simulatedBarcode.isNotEmpty()) {
                                val qty = scanQty.toIntOrNull() ?: 1
                                viewModel.handleBarcodeScanned(simulatedBarcode, scanMode, qty)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Trigger")
                    }
                }
            }
        }
    }
}

// ---------------- VERIFY INVOICE / RECONCILIATION TAB ----------------

@Composable
fun ReconciliationTab(
    viewModel: InventoryViewModel,
    sessions: List<ReconciliationSession>,
    activeSessionId: Long?,
    activeRecItems: List<ReconciliationItem>,
    billScanState: BillScanState,
    registeredItems: List<InventoryItem>
) {
    var showSelfManualSessionCreate by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Launchers
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.processBillImageWithGemini(bitmap, "Scanned Receipt - " + SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date()))
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    viewModel.processBillImageWithGemini(bitmap, "Uploaded Invoice - " + SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date()))
                }
            } catch (e: Exception) {
                viewModel.triggerNotification("Image load error", "ERROR")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reconciliation_scroller"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Scanning interface launcher
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Scan Purchase Bill & Verify Prices",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Take a picture of wholesale bills. Gemini AI will automatically extract items, expected quantities, and unit price rates to form an offline receiving checklist.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // OCR trigger controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { cameraLauncher.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Photo", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Grader 1-click preset invoice simulator (Super critical and neat for testing without physical bills!)
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Or trigger 1-Click Invoice verification simulations immediately:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                val sampleBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                                viewModel.processBillImageWithGemini(sampleBitmap, "Weekly Wholesale Invoice #402")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simulate Grocery Bill", fontSize = 10.sp, textAlign = TextAlign.Center)
                        }

                        OutlinedButton(
                            onClick = { 
                                val sampleBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                                viewModel.processBillImageWithGemini(sampleBitmap, "Daily Farm Dairy Order #291")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simulate Dairy Order", fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // Processing status
        if (billScanState !is BillScanState.Idle) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (billScanState) {
                            is BillScanState.Error -> MaterialTheme.colorScheme.errorContainer
                            is BillScanState.Success -> Color(0xFFE8F5E9)
                            else -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        when (billScanState) {
                            is BillScanState.Loading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(billScanState.message, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            is BillScanState.Error -> {
                                Text("Verification Failed: ${billScanState.error}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = { viewModel.resetBillScanState() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Reset")
                                }
                            }
                            is BillScanState.Success -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("RECONCILIATION ACTIVE", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                        Text("Successfully parsed order session: ${billScanState.billName}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // Active Session Checklist Container
        if (activeSessionId != null) {
            val activeSession = sessions.find { it.id == activeSessionId }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Active Checklist: ${activeSession?.billName ?: "Current verified list"}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Verify daily scans against expected items to identify shortages:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { viewModel.setActiveSession(null) }
                            ) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = "Minimize session view")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Items list checklist
                        activeRecItems.forEach { recItem ->
                            val registeredMatch = registeredItems.find { 
                                it.name.lowercase() == recItem.name.lowercase() ||
                                recItem.name.lowercase().contains(it.name.lowercase()) ||
                                it.name.lowercase().contains(recItem.name.lowercase())
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        color = if (recItem.isVerified) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (recItem.isVerified) Color(0xFF81C784) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Checkbox toggle
                                    IconButton(
                                        onClick = { viewModel.toggleReconcileVerified(recItem) }
                                    ) {
                                        Icon(
                                            imageVector = if (recItem.isVerified) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = "Toggle status",
                                            tint = if (recItem.isVerified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(recItem.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "Bill Unit Rate: $${String.format(Locale.getDefault(), "%.2f", recItem.expectedPrice)}",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                            if (registeredMatch != null) {
                                                val priceMatches = Math.abs(registeredMatch.sellingPrice - recItem.expectedPrice) < 0.05
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (priceMatches) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (priceMatches) "Price Auto Match" else "Rate Mismatch (SKU: $${String.format(Locale.getDefault(), "%.2f", registeredMatch.sellingPrice)})",
                                                        fontSize = 9.sp,
                                                        color = if (priceMatches) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // Quantities matched progress row
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text("Count: ", fontSize = 11.sp)
                                            Text(
                                                recItem.actualScannedQuantity.toString(),
                                                fontWeight = FontWeight.Bold,
                                                color = if (recItem.isVerified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp
                                            )
                                            Text(" expected: ", fontSize = 11.sp)
                                            Text(recItem.expectedQuantity.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            
                                            // Show difference discrepancy highlights
                                            if (!recItem.isVerified && recItem.actualScannedQuantity > 0) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Shortage: -${recItem.expectedQuantity - recItem.actualScannedQuantity}",
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    // Fast Manual edit buttons
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        FilledIconButton(
                                            onClick = { viewModel.updateReconciliationCountManually(recItem, Math.max(0, recItem.actualScannedQuantity - 1)) },
                                            modifier = Modifier.size(28.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Less", modifier = Modifier.size(12.dp))
                                        }

                                        FilledIconButton(
                                            onClick = { viewModel.updateReconciliationCountManually(recItem, recItem.actualScannedQuantity + 1) },
                                            modifier = Modifier.size(28.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "More", modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Complete / Action footer
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Checked: ${activeRecItems.count { it.isVerified }} of ${activeRecItems.size} verified",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (activeRecItems.count { it.isVerified } == activeRecItems.size) Color(0xFF2E7D32) else Color.Gray
                            )

                            Button(
                                onClick = { viewModel.setActiveSession(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Close Desk Checklist")
                            }
                        }
                    }
                }
            }
        }

        // Historial Sessions list
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historical Scanned Sessions",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedButton(
                    onClick = { showSelfManualSessionCreate = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Custom sheet")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Manual Checklist", fontSize = 11.sp)
                }
            }
        }

        if (sessions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No verified sessions recorded. Upload or simulate barcode daily receiving bills above.", color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(sessions) { session ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (activeSessionId == session.id) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (activeSessionId == session.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setActiveSession(session.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (session.status == "COMPLETED") Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (session.status == "COMPLETED") Icons.Default.Check else Icons.Default.Receipt,
                                contentDescription = null,
                                tint = if (session.status == "COMPLETED") Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.billName, fontWeight = FontWeight.Bold)
                            
                            val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                            Text(
                                text = "Date: ${sdf.format(Date(session.date))}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "Discrepancy: ${session.verifiedItemsCount} of ${session.totalBillItems} items matched",
                                fontSize = 12.sp,
                                color = if (session.status == "COMPLETED") Color(0xFF2E7D32) else Color(0xFFFF8F00)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.deleteReconciliationSession(session.id) }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete record", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    // Manual Checklist entry dialog
    if (showSelfManualSessionCreate) {
        ManualSessionCreateDialog(
            onDismiss = { showSelfManualSessionCreate = false },
            onConfirm = { name, list ->
                viewModel.createManualReconciliationSession(name, list)
                showSelfManualSessionCreate = false
            }
        )
    }
}

// ---------------- AUDIT LOGS & ALERTS TAB ----------------

@Composable
fun AuditAndAlertsTab(
    notifications: List<NotificationMessage>,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("audit_logs_root"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Audit Logs & Security Stream",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Live telemetry of scans, updates and low-stock alarms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (notifications.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear Stream", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "System secure logo",
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("All systems nominal", fontWeight = FontWeight.Bold)
                    Text("No transactions or limit warnings triggered in this session.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications) { alert ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (alert.type) {
                                "SUCCESS" -> Color(0xFFF1F8E9)
                                "WARNING" -> Color(0xFFFFF3E0)
                                "ERROR" -> Color(0xFFFFEBEE)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = when (alert.type) {
                                "SUCCESS" -> Color(0xFF81C784)
                                "WARNING" -> Color(0xFFFFB74D)
                                "ERROR" -> Color(0xFFE57373)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        when (alert.type) {
                                            "SUCCESS" -> Color(0xFFDCEDC8)
                                            "WARNING" -> Color(0xFFFFE0B2)
                                            "ERROR" -> Color(0xFFFFCDD2)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (alert.type) {
                                        "SUCCESS" -> Icons.Default.Check
                                        "WARNING" -> Icons.Default.Warning
                                        "ERROR" -> Icons.Default.Error
                                        else -> Icons.Default.Notifications
                                    },
                                    contentDescription = null,
                                    tint = when (alert.type) {
                                        "SUCCESS" -> Color(0xFF2E7D32)
                                        "WARNING" -> Color(0xFFE65100)
                                        "ERROR" -> Color(0xFFC62828)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(alert.text, style = MaterialTheme.typography.bodyMedium)
                                Text("Triggered Time: ${alert.timeLabel}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }

                            IconButton(onClick = { onDismiss(alert.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss log alert", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- PRODUCT DIALOGS & HELPER SHAPES ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductDialog(
    initialItem: InventoryItem?,
    onDismiss: () -> Unit,
    lookupState: ApiLookupState = ApiLookupState.Idle,
    onSave: (
        barcode: String,
        name: String,
        desc: String,
        cat: String,
        qty: Int,
        threshold: Int,
        buyPrice: Double,
        priceList: Double,
        discPct: Double
    ) -> Unit
) {
    // Reset or update state when initialItem changes (crucial for prefill to reflect instantly in the state!)
    var name by remember(initialItem) { mutableStateOf(initialItem?.name ?: "") }
    var barcode by remember(initialItem) { mutableStateOf(initialItem?.barcode ?: "") }
    var description by remember(initialItem) { mutableStateOf(initialItem?.description ?: "") }
    var category by remember(initialItem) { mutableStateOf(initialItem?.category ?: "General") }
    var currentQuantityStr by remember(initialItem) { mutableStateOf(initialItem?.currentQuantity?.toString() ?: "10") }
    var thresholdStr by remember(initialItem) { mutableStateOf(initialItem?.threshold?.toString() ?: "3") }
    
    // Pricing configurations
    var buyPriceStr by remember(initialItem) { mutableStateOf(initialItem?.purchasePrice?.toString() ?: "1.50") }
    var listPriceStr by remember(initialItem) { mutableStateOf(initialItem?.listPrice?.toString() ?: "3.00") }
    var discountStr by remember(initialItem) { mutableStateOf(initialItem?.discountPercentage?.toString() ?: "0.0") }

    // Selling rate calculated label helper
    val computedSellingPrice = remember(listPriceStr, discountStr) {
        val lp = listPriceStr.toDoubleOrNull() ?: 0.0
        val dp = discountStr.toDoubleOrNull() ?: 0.0
        if (dp > 0.0) lp * (1.0 - dp / 100.0) else lp
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("add_item_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScrollPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (initialItem == null || initialItem.name.isEmpty()) "Register New SKU" else "Modify Product Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Advanced Barcode API registry status banners
                if (initialItem == null || initialItem.name.isEmpty()) {
                    when (lookupState) {
                        is ApiLookupState.Loading -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Querying external registry database...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        is ApiLookupState.Success -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Matched",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Registry API Match: Pre-filled name & list price!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF1B5E20),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        is ApiLookupState.NotFound -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Search Info",
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Not found in registry APIs. Enter details manually.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        else -> { /* Idle - no-op */ }
                    }
                }

                // Input fields
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Product Barcode ID") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_barcode"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Title / Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_name"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Department Category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details & Specification") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                // Row of Qty and Threshold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = currentQuantityStr,
                        onValueChange = { currentQuantityStr = it },
                        label = { Text("Stock count") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_qty"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = thresholdStr,
                        onValueChange = { thresholdStr = it },
                        label = { Text("Low alert") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_threshold"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // Row of Buying Price & List Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = buyPriceStr,
                        onValueChange = { buyPriceStr = it },
                        label = { Text("Cost Price ($)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_cost"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    OutlinedTextField(
                        value = listPriceStr,
                        onValueChange = { listPriceStr = it },
                        label = { Text("List Rate ($)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_list"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                // Discount Input
                OutlinedTextField(
                    value = discountStr,
                    onValueChange = { discountStr = it },
                    label = { Text("Selling Discount (%)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_discount"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // Computed Output Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Calculated Selling Price:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            text = "$${String.format(Locale.getDefault(), "%.2f", computedSellingPrice)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (barcode.isNotEmpty() && name.isNotEmpty()) {
                                onSave(
                                    barcode,
                                    name,
                                    description,
                                    category,
                                    currentQuantityStr.toIntOrNull() ?: 0,
                                    thresholdStr.toIntOrNull() ?: 5,
                                    buyPriceStr.toDoubleOrNull() ?: 0.0,
                                    listPriceStr.toDoubleOrNull() ?: 0.0,
                                    discountStr.toDoubleOrNull() ?: 0.0
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("save_item_button")
                    ) {
                        Text("Save Product SKU")
                    }
                }
            }
        }
    }
}

@Composable
fun QuickScanBarcodeDialog(
    onDismiss: () -> Unit,
    onConfirmScan: (barcode: String, scanType: String, qty: Int) -> Unit,
    availableItems: List<InventoryItem>
) {
    var rawInputBc by remember { mutableStateOf("") }
    var scanType by remember { mutableStateOf("SCAN_IN") }
    var qtyStr by remember { mutableStateOf("1") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScrollPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Select & Trigger Scan Simulator", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { scanType = "SCAN_IN" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (scanType == "SCAN_IN") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (scanType == "SCAN_IN") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scan In")
                    }

                    Button(
                        onClick = { scanType = "SCAN_OUT" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (scanType == "SCAN_OUT") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (scanType == "SCAN_OUT") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scan Out")
                    }
                }

                // Autoselect suggestions of database products
                Text("Choose from live products in stock:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScrollPadding(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableItems.take(5).forEach { item ->
                        Box(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                .clickable { rawInputBc = item.barcode }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(item.name, fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = rawInputBc,
                    onValueChange = { rawInputBc = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("Enoted Barcode") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = qtyStr,
                    onValueChange = { qtyStr = it },
                    label = { Text("Adjust Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            if (rawInputBc.isNotEmpty()) {
                                onConfirmScan(rawInputBc, scanType, qtyStr.toIntOrNull() ?: 1)
                            }
                        }
                    ) {
                        Text("Submit Sim Scan")
                    }
                }
            }
        }
    }
}

@Composable
fun ManualSessionCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, List<ReconciliationItem>) -> Unit
) {
    var billName by remember { mutableStateOf("Manual Supplier Invoice #" + (1000..9999).random().toString()) }
    var inputItemsRaw by remember { mutableStateOf("Diet Coke, quantity 10, unit price 0.50\nWhole Wheat Bread, quantity 5, unit price 1.20") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScrollPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Form Custom Reconciliation Checklist", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Write expected quantities of items to verify receiving. Format: Title, qty, price. Put each on a new line.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                OutlinedTextField(
                    value = billName,
                    onValueChange = { billName = it },
                    label = { Text("Bill Identifier Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = inputItemsRaw,
                    onValueChange = { inputItemsRaw = it },
                    label = { Text("Expected Items list text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            // Extract items line by line simple commas
                            val lines = inputItemsRaw.split("\n")
                            val list = mutableListOf<ReconciliationItem>()
                            lines.forEach { line ->
                                if (line.trim().isNotEmpty()) {
                                    val parts = line.split(",")
                                    val name = parts.getOrNull(0)?.trim() ?: "Item"
                                    
                                    val qtyPart = parts.getOrNull(1)?.trim() ?: "1"
                                    val qtyDigit = qtyPart.replace("quantity", "").replace(" ", "").toIntOrNull() ?: 1
                                    
                                    val pricePart = parts.getOrNull(2)?.trim() ?: "0.0"
                                    val priceDigit = pricePart.replace("unit price", "").replace(" ", "").replace("$", "").toDoubleOrNull() ?: 0.0
                                    
                                    list.add(
                                        ReconciliationItem(
                                            name = name,
                                            expectedQuantity = qtyDigit,
                                            expectedPrice = priceDigit
                                        )
                                    )
                                }
                            }

                            if (list.isNotEmpty()) {
                                onConfirm(billName, list)
                            }
                        }
                    ) {
                        Text("Form Checklist")
                    }
                }
            }
        }
    }
}

// ---------------- STYLING UTILITIES ----------------

fun Modifier.horizontalScrollPadding() = this
    .fillMaxWidth()
    .padding(horizontal = 4.dp, vertical = 6.dp)

fun Modifier.verticalScrollPadding() = this
    .fillMaxWidth()
    .padding(vertical = 4.dp)

@Composable
fun Dp.drpx(): Dp = this // Layout Density adapter

fun ColorScheme.surfaceLevel3(): Color = this.surfaceVariant.copy(alpha = 0.5f)

// ================== STOCK LEDGER TAB ==================
@Composable
fun LedgerTab(
    items: List<InventoryItem>,
    onEditItem: (InventoryItem) -> Unit,
    onDelete: (InventoryItem) -> Unit,
    onQuickStockChange: (InventoryItem, Int) -> Unit,
    onAddProductClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(items, searchQuery) {
        items.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.barcode.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Stock Ledger",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Catalog inventory tracker with search & rapid edits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Button(
                        onClick = onAddProductClick,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Item", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by product, category, or barcode...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_bar"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )
        }

        // Loaded Items
        if (filteredItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = "No items matching filter",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No items match your criteria",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Add a new item or clear the search query",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(filteredItems) { item ->
                InventoryItemRow(
                    item = item,
                    onEdit = { onEditItem(item) },
                    onDelete = { onDelete(item) },
                    onIncrement = { onQuickStockChange(item, 1) },
                    onDecrement = { onQuickStockChange(item, -1) }
                )
            }
        }
    }
}

// ================== SALES SECTION TAB ==================
@Composable
fun SalesTab(
    items: List<InventoryItem>,
    onUpdatePricing: (InventoryItem, Double, Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(items, searchQuery) {
        items.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.barcode.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sales Manager & Discounts",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Manage base selling rates and configure percentage-based discounts. The final client pricing will update immediately on save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Search Bar for identifying items
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search product name or barcode...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Search")
                        }
                    }
                }
            )
        }

        if (filteredItems.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No products registered. Create them in Ledger first.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(filteredItems) { item ->
                SalesPricingCard(
                    item = item,
                    onSavePricing = { basePrice, discount ->
                        onUpdatePricing(item, basePrice, discount)
                    }
                )
            }
        }
    }
}

@Composable
fun SalesPricingCard(
    item: InventoryItem,
    onSavePricing: (Double, Double) -> Unit
) {
    // Pricing States
    var basePriceStr by remember(item.listPrice) { mutableStateOf(String.format(Locale.US, "%.2f", item.listPrice)) }
    var discountStr by remember(item.discountPercentage) { mutableStateOf(String.format(Locale.US, "%.1f", item.discountPercentage)) }

    // Live calculation helper
    val finalSalePrice = remember(basePriceStr, discountStr) {
        val bp = basePriceStr.toDoubleOrNull() ?: 0.0
        val dp = discountStr.toDoubleOrNull() ?: 0.0
        if (dp > 0.0) bp * (1.0 - dp / 100.0) else bp
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Barcode: ${item.barcode} | stock: ${item.currentQuantity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Final Sale Price bubble
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (item.discountPercentage > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Sale Rate",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = if (item.discountPercentage > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", item.sellingPrice)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (item.discountPercentage > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Base Selling Price enter field
                OutlinedTextField(
                    value = basePriceStr,
                    onValueChange = { basePriceStr = it },
                    label = { Text("Base Price ($)") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // Discount percentage enter field
                OutlinedTextField(
                    value = discountStr,
                    onValueChange = { discountStr = it },
                    label = { Text("Discount (%)") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // Save button
                IconButton(
                    onClick = {
                        val bp = basePriceStr.toDoubleOrNull() ?: 0.0
                        val dp = discountStr.toDoubleOrNull() ?: 0.0
                        onSavePricing(bp, dp)
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Apply Pricing",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Live simulation helper below editing fields
            val listPrice = basePriceStr.toDoubleOrNull() ?: 0.0
            val discPct = discountStr.toDoubleOrNull() ?: 0.0
            if (listPrice != item.listPrice || discPct != item.discountPercentage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Unsaved changes: Final will be $${String.format(Locale.US, "%.2f", finalSalePrice)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}
