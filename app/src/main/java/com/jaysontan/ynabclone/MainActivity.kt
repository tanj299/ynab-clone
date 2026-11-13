package com.jaysontan.ynabclone

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.jaysontan.ynabclone.data.PersonEntity
import com.jaysontan.ynabclone.data.TransactionEntity
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
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddTxSheet by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryBudgetItem?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showManagePeopleDialog by remember { mutableStateOf(false) }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(vm.buildCsvContent().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "CSV exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Budget"
                            1 -> "Accounts"
                            else -> "Activity"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showManagePeopleDialog = true }) {
                        Icon(Icons.Rounded.Group, contentDescription = "Manage People")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val scopeName = state.persons.find { it.id == state.selectedPersonId }?.name ?: "Household"
                        csvExportLauncher.launch("ZeroBudget__.csv")
                    }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Export CSV")
                    }
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
                    icon = { Icon(Icons.Rounded.ReceiptLong, contentDescription = "Activity") },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PersonFilterRow(
                persons = state.persons,
                selectedPersonId = state.selectedPersonId,
                onSelectPerson = { vm.selectPerson(it) },
                onManagePeople = { showManagePeopleDialog = true }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> BudgetScreen(state = state, onCategoryClick = { editingCategory = it })
                    1 -> AccountsScreen(state = state)
                    2 -> TransactionsScreen(state = state, onDelete = { vm.deleteTransaction(it) })
                }
            }
        }
    }

    if (showAddTxSheet) {
        AddTransactionSheet(
            persons = state.persons,
            accounts = state.allAccounts,
            categories = state.allCategories,
            defaultPersonId = state.selectedPersonId,
            onDismiss = { showAddTxSheet = false },
            onSave = { personId, accId, catId, payee, cents ->
                vm.addTransaction(personId, accId, catId, payee, cents)
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
            persons = state.persons,
            defaultPersonId = state.selectedPersonId,
            onDismiss = { showAddAccountDialog = false },
            onSave = { personId, name, type, cents ->
                vm.addAccount(personId, name, type, cents)
                showAddAccountDialog = false
            }
        )
    }

    if (showManagePeopleDialog) {
        ManagePeopleDialog(
            persons = state.persons,
            accounts = state.allAccounts,
            onAddPerson = { vm.addPerson(it) },
            onDeletePerson = { vm.deletePerson(it) },
            onDismiss = { showManagePeopleDialog = false }
        )
    }
}

@Composable
fun PersonFilterRow(
    persons: List<PersonEntity>,
    selectedPersonId: Long?,
    onSelectPerson: (Long?) -> Unit,
    onManagePeople: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedPersonId == null,
            onClick = { onSelectPerson(null) },
            label = { Text("All (Household)") },
            leadingIcon = if (selectedPersonId == null) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null
        )
        persons.forEach { person ->
            val selected = selectedPersonId == person.id
            FilterChip(
                selected = selected,
                onClick = { onSelectPerson(person.id) },
                label = { Text(person.name) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
        AssistChip(
            onClick = onManagePeople,
            label = { Text("+ Person") }
        )
    }
}

@Composable
fun BudgetScreen(state: BudgetUiState, onCategoryClick: (CategoryBudgetItem) -> Unit) {
    val selectedPerson = state.persons.find { it.id == state.selectedPersonId }
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
                        text = if (rtaPositive) "Household Ready to Assign" else "Household Overassigned",
                        style = MaterialTheme.typography.labelLarge,
                        color = bannerTextColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = formatCents(state.readyToAssignCents),
                        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.ExtraBold,
                        color = bannerTextColor
                    )
                    if (selectedPerson != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Viewing 's spending activity",
                            style = MaterialTheme.typography.bodySmall,
                            color = bannerTextColor.copy(alpha = 0.85f)
                        )
                    }
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
                    selectedPerson != null -> MaterialTheme.colorScheme.secondaryContainer
                    item.availableCents < 0 -> MaterialTheme.colorScheme.errorContainer
                    item.availableCents > 0 -> Color(0xFF1B5E20).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val pillTextColor = when {
                    selectedPerson != null -> MaterialTheme.colorScheme.onSecondaryContainer
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
                                if (selectedPerson == null)
                                    "Assigned:   •  Spent: "
                                else
                                    "'s Activity: ",
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
    val selectedPerson = state.persons.find { it.id == state.selectedPersonId }
    val personMap = remember(state.persons) { state.persons.associateBy { it.id } }
    val visibleSum = state.visibleAccounts.sumOf { it.balanceCents }

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
                    Text(
                        text = if (selectedPerson == null) "Aggregated Household Total" else "'s Total Balance",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = formatCents(visibleSum),
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedPerson != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "All Household Combined: ",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        items(state.visibleAccounts) { account ->
            val ownerName = personMap[account.personId]?.name ?: "Unassigned"
            ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatCents(account.balanceCents),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
fun TransactionsScreen(state: BudgetUiState, onDelete: (TransactionEntity) -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val catMap = remember(state.allCategories) { state.allCategories.associateBy { it.id } }
    val accMap = remember(state.allAccounts) { state.allAccounts.associateBy { it.id } }
    val personMap = remember(state.persons) { state.persons.associateBy { it.id } }
    val netSum = state.visibleTransactions.sumOf { it.amountCents }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Net Activity ( txns)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = formatCents(netSum),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        items(state.visibleTransactions) { tx ->
            val catName = tx.categoryId?.let { catMap[it]?.name } ?: "Ready to Assign (Inflow)"
            val accName = accMap[tx.accountId]?.name ?: "Account"
            val personName = personMap[tx.personId]?.name ?: "Member"
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
                            " •  •  • ",
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
    persons: List<PersonEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    defaultPersonId: Long?,
    onDismiss: () -> Unit,
    onSave: (Long, Long, Long?, String, Long) -> Unit
) {
    var isOutflow by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var selectedPerson by remember {
        mutableStateOf(persons.find { it.id == defaultPersonId } ?: persons.firstOrNull())
    }
    val personAccounts = remember(selectedPerson, accounts) {
        accounts.filter { it.personId == selectedPerson?.id }.ifEmpty { accounts }
    }
    var selectedAccount by remember(personAccounts) { mutableStateOf(personAccounts.firstOrNull()) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }

    var personExpanded by remember { mutableStateOf(false) }
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

            ExposedDropdownMenuBox(expanded = personExpanded, onExpandedChange = { personExpanded = it }) {
                OutlinedTextField(
                    value = selectedPerson?.name ?: "Select Person",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Person") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = personExpanded, onDismissRequest = { personExpanded = false }) {
                    persons.forEach { person ->
                        DropdownMenuItem(text = { Text(person.name) }, onClick = { selectedPerson = person; personExpanded = false })
                    }
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
                    personAccounts.forEach { acc ->
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
                    val person = selectedPerson
                    val acc = selectedAccount
                    if (rawCents > 0 && person != null && acc != null) {
                        val signedCents = if (isOutflow) -rawCents else rawCents
                        val catId = if (isOutflow) selectedCategory?.id else null
                        onSave(person.id, acc.id, catId, payee, signedCents)
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
fun ManagePeopleDialog(
    persons: List<PersonEntity>,
    accounts: List<AccountEntity>,
    onAddPerson: (String) -> Unit,
    onDeletePerson: (PersonEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Household Members") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Add Person Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newName.isNotBlank()) {
                            onAddPerson(newName)
                            newName = ""
                        }
                    }) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Person")
                    }
                }

                persons.forEach { person ->
                    val personTotal = accounts.filter { it.personId == person.id }.sumOf { it.balanceCents }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(person.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Accounts Sum: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (persons.size > 1) {
                            IconButton(onClick = { onDeletePerson(person) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    persons: List<PersonEntity>,
    defaultPersonId: Long?,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, Long) -> Unit
) {
    var selectedPerson by remember {
        mutableStateOf(persons.find { it.id == defaultPersonId } ?: persons.firstOrNull())
    }
    var personExpanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Checking") }
    var balanceText by remember { mutableStateOf("0.00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = personExpanded, onExpandedChange = { personExpanded = it }) {
                    OutlinedTextField(
                        value = selectedPerson?.name ?: "Select Owner",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Owner") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = personExpanded, onDismissRequest = { personExpanded = false }) {
                        persons.forEach { person ->
                            DropdownMenuItem(text = { Text(person.name) }, onClick = { selectedPerson = person; personExpanded = false })
                        }
                    }
                }
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
                val owner = selectedPerson
                if (name.isNotBlank() && owner != null) {
                    val cents = ((balanceText.toDoubleOrNull() ?: 0.0) * 100).roundToLong()
                    onSave(owner.id, name, type, cents)
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}