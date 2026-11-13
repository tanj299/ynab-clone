package com.jaysontan.ynabclone

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaysontan.ynabclone.data.AccountEntity
import com.jaysontan.ynabclone.data.CategoryEntity
import com.jaysontan.ynabclone.ui.BudgetUiState
import com.jaysontan.ynabclone.ui.BudgetViewModel
import com.jaysontan.ynabclone.ui.CategoryBudgetItem
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToLong

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                ZeroBudgetApp()
            }
        }
    }
}

fun formatCents(cents: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    return formatter.format(cents / 100.0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroBudgetApp(vm: BudgetViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddTxSheet by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryBudgetItem?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Budget"
                            1 -> "Accounts"
                            else -> "Transactions"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showAddCategoryDialog = true }) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Add Category")
                        }
                    } else if (selectedTab == 1) {
                        IconButton(onClick = { showAddAccountDialog = true }) {
                            Icon(Icons.Rounded.AddCard, contentDescription = "Add Account")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.PieChart, contentDescription = "Budget") },
                    label = { Text("Budget") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.AccountBalance, contentDescription = "Accounts") },
                    label = { Text("Accounts") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.ReceiptLong, contentDescription = "Transactions") },
                    label = { Text("Activity") }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddTxSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = "Add") },
                text = { Text("Transaction") }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> BudgetScreen(state = state, onCategoryClick = { editingCategory = it })
                1 -> AccountsScreen(state = state)
                2 -> TransactionsScreen(state = state, onDelete = { vm.deleteTransaction(it) })
            }
        }
    }

    if (showAddTxSheet) {
        AddTransactionSheet(
            accounts = state.accounts,
            categories = state.allCategories,
            onDismiss = { showAddTxSheet = false },
            onSave = { accId, catId, payee, cents ->
                vm.addTransaction(accId, catId, payee, cents)
                showAddTxSheet = false
            }
        )
    }

    editingCategory?.let { item ->
        AssignMoneyDialog(
            item = item,
            readyToAssignCents = state.readyToAssignCents,
            onDismiss = { editingCategory = null },
            onSave = { newAssigned ->
                vm.assignMoney(item.category.id, newAssigned)
                editingCategory = null
            }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onSave = { group, name ->
                vm.addCategory(group, name)
                showAddCategoryDialog = false
            }
        )
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAddAccountDialog = false },
            onSave = { name, type, cents ->
                vm.addAccount(name, type, cents)
                showAddAccountDialog = false
            }
        )
    }
}

@Composable
fun BudgetScreen(state: BudgetUiState, onCategoryClick: (CategoryBudgetItem) -> Unit) {
    val rtaPositive = state.readyToAssignCents >= 0
    val bannerColor = if (rtaPositive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val bannerTextColor = if (rtaPositive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = bannerColor),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (rtaPositive) "Ready to Assign" else "Overassigned",
                        style = MaterialTheme.typography.labelLarge,
                        color = bannerTextColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = formatCents(state.readyToAssignCents),
                        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.ExtraBold,
                        color = bannerTextColor
                    )
                }
            }
        }

        state.categoryGroups.forEach { (groupName, items) ->
            item {
                Text(
                    text = groupName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                )
            }
            items(items) { item ->
                val pillColor = when {
                    item.availableCents < 0 -> MaterialTheme.colorScheme.errorContainer
                    item.availableCents > 0 -> Color(0xFF1B5E20).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val pillTextColor = when {
                    item.availableCents < 0 -> MaterialTheme.colorScheme.onErrorContainer
                    item.availableCents > 0 -> Color(0xFF2E7D32)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().clickable { onCategoryClick(item) },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Assigned:   •  Spent: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillColor)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = formatCents(item.availableCents),
                                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = pillTextColor
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
fun AccountsScreen(state: BudgetUiState) {
    val netWorth = state.accounts.sumOf { it.balanceCents }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Total Cash / Net Balance", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = formatCents(netWorth),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        items(state.accounts) { account ->
            ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(account.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = formatCents(account.balanceCents),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionsScreen(state: BudgetUiState, onDelete: (com.jaysontan.ynabclone.data.TransactionEntity) -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val catMap = remember(state.allCategories) { state.allCategories.associateBy { it.id } }
    val accMap = remember(state.accounts) { state.accounts.associateBy { it.id } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.transactions) { tx ->
            val catName = tx.categoryId?.let { catMap[it]?.name } ?: "Ready to Assign (Inflow)"
            val accName = accMap[tx.accountId]?.name ?: "Account"
            val isOutflow = tx.amountCents < 0
            ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tx.payee, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            " •  • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatCents(tx.amountCents),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = if (isOutflow) MaterialTheme.colorScheme.onSurface else Color(0xFF2E7D32)
                        )
                        IconButton(onClick = { onDelete(tx) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (Long, Long?, String, Long) -> Unit
) {
    var isOutflow by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }
    var accExpanded by remember { mutableStateOf(false) }
    var catExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Log Transaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(selected = isOutflow, onClick = { isOutflow = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text("Outflow (-)")
                }
                SegmentedButton(selected = !isOutflow, onClick = { isOutflow = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text("Inflow (+)")
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount ($)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = payee,
                onValueChange = { payee = it },
                label = { Text("Payee (e.g., Trader Joe's, Paycheck)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            ExposedDropdownMenuBox(expanded = accExpanded, onExpandedChange = { accExpanded = it }) {
                OutlinedTextField(
                    value = selectedAccount?.name ?: "Select Account",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(text = { Text(acc.name) }, onClick = { selectedAccount = acc; accExpanded = false })
                    }
                }
            }

            if (isOutflow) {
                ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "Select Category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(": ") }, onClick = { selectedCategory = cat; catExpanded = false })
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val rawCents = ((amountText.toDoubleOrNull() ?: 0.0) * 100).roundToLong()
                    val acc = selectedAccount
                    if (rawCents > 0 && acc != null) {
                        val signedCents = if (isOutflow) -rawCents else rawCents
                        val catId = if (isOutflow) selectedCategory?.id else null
                        onSave(acc.id, catId, payee, signedCents)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Save Transaction")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AssignMoneyDialog(
    item: CategoryBudgetItem,
    readyToAssignCents: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var assignedText by remember { mutableStateOf(String.format(Locale.US, "%.2f", item.category.assignedCents / 100.0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign to ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ready to Assign: ", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = assignedText,
                    onValueChange = { assignedText = it },
                    label = { Text("Total Assigned ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = ((assignedText.toDoubleOrNull() ?: 0.0) * 100).roundToLong()
                onSave(cents)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddCategoryDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var group by remember { mutableStateOf("Everyday Living") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("Category Group") }, singleLine = true)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Category Name") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(group, name) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddAccountDialog(onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Checking") }
    var balanceText by remember { mutableStateOf("0.00") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Account Name") }, singleLine = true)
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (Checking, Savings, Credit)") }, singleLine = true)
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Starting Balance ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val cents = ((balanceText.toDoubleOrNull() ?: 0.0) * 100).roundToLong()
                    onSave(name, type, cents)
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}