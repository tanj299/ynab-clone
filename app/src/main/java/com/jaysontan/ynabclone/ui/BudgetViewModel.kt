package com.jaysontan.ynabclone.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jaysontan.ynabclone.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class CategoryBudgetItem(
    val category: CategoryEntity,
    val activityCents: Long,
    val availableCents: Long,
    val underfundedCents: Long
)

data class ChartSlice(
    val label: String,
    val amountCents: Long,
    val percentage: Float
)

data class BudgetUiState(
    val persons: List<PersonEntity> = emptyList(),
    val selectedPersonId: Long? = null,
    val allAccounts: List<AccountEntity> = emptyList(),
    val visibleAccounts: List<AccountEntity> = emptyList(),
    val categoryGroups: Map<String, List<CategoryBudgetItem>> = emptyMap(),
    val allCategories: List<CategoryEntity> = emptyList(),
    val allTransactions: List<TransactionEntity> = emptyList(),
    val visibleTransactions: List<TransactionEntity> = emptyList(),
    val readyToAssignCents: Long = 0L,
    val totalHouseholdCashCents: Long = 0L,
    val totalUnderfundedCents: Long = 0L,
    val totalOverspentCents: Long = 0L,
    val ageOfMoneyDays: Int = 0,
    val categorySpendSlices: List<ChartSlice> = emptyList(),
    val personSpendSlices: List<ChartSlice> = emptyList()
)

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).budgetDao()
    private val _selectedPersonId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            if (dao.getPersonCount() == 0) {
                val jaysonId = dao.insertPerson(PersonEntity(name = "Jayson"))
                val karyeeId = dao.insertPerson(PersonEntity(name = "KarYee"))

                val jaysonChecking = dao.insertAccount(AccountEntity(personId = jaysonId, name = "Jayson Checking", type = "Checking", balanceCents = 250000))
                val karyeeChecking = dao.insertAccount(AccountEntity(personId = karyeeId, name = "KarYee Checking", type = "Checking", balanceCents = 220000))
                dao.insertAccount(AccountEntity(personId = jaysonId, name = "Joint High-Yield Savings", type = "Savings", balanceCents = 500000))

                dao.insertCategory(CategoryEntity(groupName = "Fixed Bills", name = "Mortgage / Rent", assignedCents = 280000, targetCents = 280000))
                dao.insertCategory(CategoryEntity(groupName = "Fixed Bills", name = "Utilities & Internet", assignedCents = 25000, targetCents = 25000))
                val grocId = dao.insertCategory(CategoryEntity(groupName = "Everyday Living", name = "Groceries", assignedCents = 70000, targetCents = 80000))
                val diningId = dao.insertCategory(CategoryEntity(groupName = "Everyday Living", name = "Dining Out", assignedCents = 30000, targetCents = 35000))
                val transitId = dao.insertCategory(CategoryEntity(groupName = "Everyday Living", name = "Subway & Transit", assignedCents = 15000, targetCents = 15000))
                dao.insertCategory(CategoryEntity(groupName = "Goals", name = "Emergency Fund", assignedCents = 500000, targetCents = 500000))

                val now = System.currentTimeMillis()
                val dayMs = TimeUnit.DAYS.toMillis(1)

                dao.insertTransaction(TransactionEntity(personId = jaysonId, accountId = jaysonChecking, categoryId = null, payee = "Payroll Deposit", amountCents = 320000, timestamp = now - 18 * dayMs))
                dao.insertTransaction(TransactionEntity(personId = jaysonId, accountId = jaysonChecking, categoryId = grocId, payee = "Whole Foods", amountCents = -14250, timestamp = now - 3 * dayMs))
                dao.adjustAccountBalance(jaysonChecking, -14250)

                dao.insertTransaction(TransactionEntity(personId = karyeeId, accountId = karyeeChecking, categoryId = diningId, payee = "Xiang Hotpot", amountCents = -8600, timestamp = now - 2 * dayMs))
                dao.adjustAccountBalance(karyeeChecking, -8600)

                dao.insertTransaction(TransactionEntity(personId = jaysonId, accountId = jaysonChecking, categoryId = transitId, payee = "OMNY MTA", amountCents = -3400, timestamp = now - 1 * dayMs))
                dao.adjustAccountBalance(jaysonChecking, -3400)
            }
        }
    }

    val uiState: StateFlow<BudgetUiState> = combine(
        dao.getAllPersons(),
        dao.getAllAccounts(),
        dao.getAllCategories(),
        dao.getAllTransactions(),
        _selectedPersonId
    ) { persons, accounts, categories, transactions, selectedPersonId ->
        val visibleAccounts = if (selectedPersonId == null) accounts else accounts.filter { it.personId == selectedPersonId }
        val visibleTransactions = if (selectedPersonId == null) transactions else transactions.filter { it.personId == selectedPersonId }

        val totalHouseholdCashCents = accounts.sumOf { it.balanceCents }
        val totalAssignedCents = categories.sumOf { it.assignedCents }
        val totalCategorizedNetCents = transactions.filter { it.categoryId != null }.sumOf { it.amountCents }

        val readyToAssign = totalHouseholdCashCents - totalAssignedCents + totalCategorizedNetCents

        val items = categories.map { cat ->
            val activity = visibleTransactions.filter { it.categoryId == cat.id }.sumOf { it.amountCents }
            val allActivity = transactions.filter { it.categoryId == cat.id }.sumOf { it.amountCents }
            val underfunded = if (cat.targetCents > cat.assignedCents) cat.targetCents - cat.assignedCents else 0L
            CategoryBudgetItem(
                category = cat,
                activityCents = activity,
                availableCents = if (selectedPersonId == null) cat.assignedCents + allActivity else activity,
                underfundedCents = underfunded
            )
        }

        val totalUnderfunded = items.sumOf { it.underfundedCents }
        val totalOverspent = items.filter { it.availableCents < 0 }.sumOf { abs(it.availableCents) }

        // Age of Money (YNAB-style buffer metric based on recent inflow timestamps vs outflows)
        val inflows = transactions.filter { it.amountCents > 0 }.sortedBy { it.timestamp }
        val outflows = transactions.filter { it.amountCents < 0 }.sortedByDescending { it.timestamp }.take(10)
        val ageOfMoney = if (inflows.isNotEmpty() && outflows.isNotEmpty()) {
            val oldestInflowTs = inflows.first().timestamp
            val avgOutflowTs = outflows.map { it.timestamp }.average().toLong()
            TimeUnit.MILLISECONDS.toDays((avgOutflowTs - oldestInflowTs).coerceAtLeast(0L)).toInt().coerceAtLeast(1)
        } else {
            0
        }

        // Pie Chart 1: Spending by Category (for visible scope)
        val catMap = categories.associateBy { it.id }
        val outflowsByCat = visibleTransactions
            .filter { it.amountCents < 0 && it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { entry -> entry.value.sumOf { abs(it.amountCents) } }
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }

        val totalOutflowSum = outflowsByCat.sumOf { it.second }.coerceAtLeast(1L)
        val categorySpendSlices = outflowsByCat.map { (catId, cents) ->
            ChartSlice(
                label = catMap[catId]?.name ?: "Other",
                amountCents = cents,
                percentage = (cents.toFloat() / totalOutflowSum.toFloat()) * 100f
            )
        }

        // Pie Chart 2: Household Spending by Person
        val personMap = persons.associateBy { it.id }
        val outflowsByPerson = transactions
            .filter { it.amountCents < 0 }
            .groupBy { it.personId }
            .mapValues { entry -> entry.value.sumOf { abs(it.amountCents) } }
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }

        val totalPersonOutflow = outflowsByPerson.sumOf { it.second }.coerceAtLeast(1L)
        val personSpendSlices = outflowsByPerson.map { (pId, cents) ->
            ChartSlice(
                label = personMap[pId]?.name ?: "Member",
                amountCents = cents,
                percentage = (cents.toFloat() / totalPersonOutflow.toFloat()) * 100f
            )
        }

        BudgetUiState(
            persons = persons,
            selectedPersonId = selectedPersonId,
            allAccounts = accounts,
            visibleAccounts = visibleAccounts,
            categoryGroups = items.groupBy { it.category.groupName },
            allCategories = categories,
            allTransactions = transactions,
            visibleTransactions = visibleTransactions,
            readyToAssignCents = readyToAssign,
            totalHouseholdCashCents = totalHouseholdCashCents,
            totalUnderfundedCents = totalUnderfunded,
            totalOverspentCents = totalOverspent,
            ageOfMoneyDays = ageOfMoney,
            categorySpendSlices = categorySpendSlices,
            personSpendSlices = personSpendSlices
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    fun selectPerson(personId: Long?) {
        _selectedPersonId.value = personId
    }

    fun addPerson(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) dao.insertPerson(PersonEntity(name = name.trim()))
        }
    }

    fun deletePerson(person: PersonEntity) {
        viewModelScope.launch {
            if (_selectedPersonId.value == person.id) _selectedPersonId.value = null
            dao.deleteTransactionsByPerson(person.id)
            dao.deleteAccountsByPerson(person.id)
            dao.deletePerson(person)
        }
    }

    fun updateCategoryBudget(categoryId: Long, newAssignedCents: Long, newTargetCents: Long) {
        viewModelScope.launch {
            dao.updateCategoryBudget(categoryId, newAssignedCents, newTargetCents)
        }
    }

    fun moveMoney(fromCategoryId: Long, toCategoryId: Long, amountCents: Long) {
        if (fromCategoryId == toCategoryId || amountCents <= 0) return
        viewModelScope.launch {
            dao.adjustCategoryAssigned(fromCategoryId, -amountCents)
            dao.adjustCategoryAssigned(toCategoryId, amountCents)
        }
    }

    fun coverAllOverspending() {
        viewModelScope.launch {
            val state = uiState.value
            var rta = state.readyToAssignCents
            if (rta <= 0) return@launch
            state.categoryGroups.values.flatten().filter { it.availableCents < 0 }.forEach { item ->
                val needed = abs(item.availableCents)
                val cover = needed.coerceAtMost(rta)
                if (cover > 0) {
                    dao.adjustCategoryAssigned(item.category.id, cover)
                    rta -= cover
                }
            }
        }
    }

    fun fundUnderfundedTargets() {
        viewModelScope.launch {
            val state = uiState.value
            var rta = state.readyToAssignCents
            if (rta <= 0) return@launch
            state.categoryGroups.values.flatten().filter { it.underfundedCents > 0 }.forEach { item ->
                val fund = item.underfundedCents.coerceAtMost(rta)
                if (fund > 0) {
                    dao.adjustCategoryAssigned(item.category.id, fund)
                    rta -= fund
                }
            }
        }
    }

    fun addCategory(groupName: String, name: String, targetCents: Long = 0L) {
        viewModelScope.launch {
            dao.insertCategory(CategoryEntity(groupName = groupName.ifBlank { "General" }, name = name, targetCents = targetCents))
        }
    }

    fun addAccount(personId: Long, name: String, type: String, initialBalanceCents: Long) {
        viewModelScope.launch {
            dao.insertAccount(AccountEntity(personId = personId, name = name, type = type, balanceCents = initialBalanceCents))
        }
    }

    fun addTransaction(personId: Long, accountId: Long, categoryId: Long?, payee: String, amountCents: Long) {
        viewModelScope.launch {
            dao.insertTransaction(
                TransactionEntity(
                    personId = personId,
                    accountId = accountId,
                    categoryId = categoryId,
                    payee = payee.ifBlank { "Manual Entry" },
                    amountCents = amountCents
                )
            )
            dao.adjustAccountBalance(accountId, amountCents)
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            dao.deleteTransaction(tx)
            dao.adjustAccountBalance(tx.accountId, -tx.amountCents)
        }
    }

    fun buildCsvContent(): String {
        val state = uiState.value
        val personMap = state.persons.associateBy { it.id }
        val accountMap = state.allAccounts.associateBy { it.id }
        val categoryMap = state.allCategories.associateBy { it.id }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val sb = StringBuilder()
        sb.appendLine("Date,Person,Account,Category Group,Category,Payee,Type,Amount")

        state.visibleTransactions.forEach { tx ->
            val date = dateFmt.format(Date(tx.timestamp))
            val person = escapeCsv(personMap[tx.personId]?.name ?: "Unknown")
            val account = escapeCsv(accountMap[tx.accountId]?.name ?: "Unknown")
            val cat = tx.categoryId?.let { categoryMap[it] }
            val catGroup = escapeCsv(cat?.groupName ?: "Inflow")
            val catName = escapeCsv(cat?.name ?: "Ready to Assign")
            val payee = escapeCsv(tx.payee)
            val type = if (tx.amountCents < 0) "Outflow" else "Inflow"
            val amount = String.format(Locale.US, "%.2f", tx.amountCents / 100.0)
            sb.appendLine(",,,,,,,")
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            "\"\""
        } else {
            escaped
        }
    }
}