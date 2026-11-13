package com.jaysontan.ynabclone.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // Checking, Savings, Credit Card, Cash
    val balanceCents: Long
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupName: String,
    val name: String,
    val assignedCents: Long = 0
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val categoryId: Long?, // null = Ready to Assign (Inflow)
    val payee: String,
    val memo: String = "",
    val amountCents: Long, // Negative = outflow, Positive = inflow
    val timestamp: Long = System.currentTimeMillis()
)