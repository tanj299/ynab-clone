package com.jaysontan.ynabclone.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jaysontan.ynabclone.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class CategoryBudgetItem(
    val category: CategoryEntity,
    val activityCents: Long,
    val availableCents: Long
)

data class BudgetUiState(
    val persons: List<PersonEntity> = emptyList(),
    val selectedPersonId: Long? = null, // null = All (Aggregated Household)
    val allAccounts: List<AccountEntity> = emptyList(),
    val visibleAccounts: List<AccountEntity> = emptyList(),
    val categoryGroups: Map<String, List<CategoryBudgetItem>> = emptyMap(),
    val allCategories: List<CategoryEntity> = emptyList(),
    val allTransactions: List<TransactionEntity> = emptyList(),
    val visibleTransactions: List<TransactionEntity> = emptyList(),
    val readyToAssignCents: Long = 0L,
    val totalHouseholdCashCents: Long = 0L
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

                dao.insertCategory(CategoryEntity(groupName = "Fixed Bills", name = "Mortgage / Rent", assignedCents = 280000))
                dao.insertCategory(CategoryEntity(groupName = "Fixed Bills", name = "Utilities & Internet", assignedCents = 25000))
                val grocId = dao.insertCategory(CategoryEntity(groupName = "Everyday Living", name = "Groceries", assignedCents = 70000))
                val diningId = dao.insertCategory(CategoryEntity(groupName = "Everyday Living", name = "Dining Out", assignedCents = 30000))
                dao.insertCategory(CategoryEntity(groupName = "Goals", name = "Emergency Fund", assignedCents = 500000))

                dao.insertTransaction(TransactionEntity(personId = jaysonId, accountId = jaysonChecking, categoryId = grocId, payee = "Whole Foods", amountCents = -8450))
                dao.adjustAccountBalance(jaysonChecking, -8450)

                dao.insertTransaction(TransactionEntity(personId = karyeeId, accountId = karyeeChecking, categoryId = diningId, payee = "Xiang Hotpot", amountCents = -6200))
                dao.adjustAccountBalance(karyeeChecking, -6200)
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

        // Household-wide Ready to Assign
        val readyToAssign = totalHouseholdCashCents - totalAssignedCents + totalCategorizedNetCents

        val items = categories.map { cat ->
            // Activity reflects either aggregated household spending or the selected person's spending
            val activity = visibleTransactions.filter { it.categoryId == cat.id }.sumOf { it.amountCents }
            val allActivity = transactions.filter { it.categoryId == cat.id }.sumOf { it.amountCents }
            CategoryBudgetItem(
                category = cat,
                activityCents = activity,
                availableCents = if (selectedPersonId == null) cat.assignedCents + allActivity else activity
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
            totalHouseholdCashCents = totalHouseholdCashCents
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

    fun assignMoney(categoryId: Long, newAssignedCents: Long) {
        viewModelScope.launch {
            dao.updateCategoryAssigned(categoryId, newAssignedCents)
        }
    }

    fun addCategory(groupName: String, name: String) {
        viewModelScope.launch {
            dao.insertCategory(CategoryEntity(groupName = groupName.ifBlank { "General" }, name = name))
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