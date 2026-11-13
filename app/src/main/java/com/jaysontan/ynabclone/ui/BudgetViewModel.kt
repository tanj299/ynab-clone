package com.jaysontan.ynabclone.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jaysontan.ynabclone.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CategoryBudgetItem(
    val category: CategoryEntity,
    val activityCents: Long,
    val availableCents: Long
)

data class BudgetUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categoryGroups: Map<String, List<CategoryBudgetItem>> = emptyMap(),
    val allCategories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val readyToAssignCents: Long = 0L
)

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).budgetDao()

    init {
        viewModelScope.launch {
            if (dao.getAccountCount() == 0) {
                val checkingId = dao.insertAccount(AccountEntity(name = "Checking", type = "Checking", balanceCents = 250000))
                dao.insertAccount(AccountEntity(name = "High-Yield Savings", type = "Savings", balanceCents = 500000))
                dao.insertCategory(CategoryEntity(groupName = "Fixed Bills", name = "Mortgage / Rent", assignedCents = 200000))
                dao.insertCategory(CategoryEntity(groupName = "Fixed Bills", name = "Utilities & Internet", assignedCents = 25000))
                val grocId = dao.insertCategory(CategoryEntity(groupName = "Everyday Living", name = "Groceries", assignedCents = 60000))
                dao.insertCategory(CategoryEntity(groupName = "Everyday Living", name = "Dining Out", assignedCents = 20000))
                dao.insertCategory(CategoryEntity(groupName = "Goals", name = "Emergency Fund", assignedCents = 400000))
                dao.insertTransaction(TransactionEntity(accountId = checkingId, categoryId = grocId, payee = "Whole Foods", amountCents = -8450))
                dao.adjustAccountBalance(checkingId, -8450)
            }
        }
    }

    val uiState: StateFlow<BudgetUiState> = combine(
        dao.getAllAccounts(),
        dao.getAllCategories(),
        dao.getAllTransactions()
    ) { accounts, categories, transactions ->
        val totalCashCents = accounts.sumOf { it.balanceCents }
        val totalAssignedCents = categories.sumOf { it.assignedCents }
        val totalOutflowsCents = transactions.filter { it.categoryId != null }.sumOf { it.amountCents }

        // Ready to Assign = Total Cash - Total Assigned - Categorized Net Outflows
        val readyToAssign = totalCashCents - totalAssignedCents + totalOutflowsCents

        val items = categories.map { cat ->
            val activity = transactions.filter { it.categoryId == cat.id }.sumOf { it.amountCents }
            CategoryBudgetItem(
                category = cat,
                activityCents = activity,
                availableCents = cat.assignedCents + activity
            )
        }

        BudgetUiState(
            accounts = accounts,
            categoryGroups = items.groupBy { it.category.groupName },
            allCategories = categories,
            transactions = transactions,
            readyToAssignCents = readyToAssign
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

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

    fun addAccount(name: String, type: String, initialBalanceCents: Long) {
        viewModelScope.launch {
            dao.insertAccount(AccountEntity(name = name, type = type, balanceCents = initialBalanceCents))
        }
    }

    fun addTransaction(accountId: Long, categoryId: Long?, payee: String, amountCents: Long) {
        viewModelScope.launch {
            dao.insertTransaction(
                TransactionEntity(
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
}