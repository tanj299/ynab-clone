package com.jaysontan.ynabclone.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM persons ORDER BY id ASC")
    fun getAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM categories ORDER BY groupName ASC, id ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertPerson(person: PersonEntity): Long

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    @Query("DELETE FROM accounts WHERE personId = :personId")
    suspend fun deleteAccountsByPerson(personId: Long)

    @Query("DELETE FROM transactions WHERE personId = :personId")
    suspend fun deleteTransactionsByPerson(personId: Long)

    @Insert
    suspend fun insertAccount(account: AccountEntity): Long

    @Query("UPDATE accounts SET balanceCents = balanceCents + :deltaCents WHERE id = :accountId")
    suspend fun adjustAccountBalance(accountId: Long, deltaCents: Long)

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("UPDATE categories SET assignedCents = :assignedCents WHERE id = :categoryId")
    suspend fun updateCategoryAssigned(categoryId: Long, assignedCents: Long)

    @Insert
    suspend fun insertTransaction(tx: TransactionEntity): Long

    @Delete
    suspend fun deleteTransaction(tx: TransactionEntity)

    @Query("SELECT COUNT(*) FROM persons")
    suspend fun getPersonCount(): Int
}