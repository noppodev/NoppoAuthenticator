package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OtpDao {
    @Query("SELECT * FROM otp_accounts ORDER BY isFavorite DESC, createdTimestamp DESC")
    fun getAllAccounts(): Flow<List<OtpAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: OtpAccount)

    @Update
    suspend fun updateAccount(account: OtpAccount)

    @Delete
    suspend fun deleteAccount(account: OtpAccount)

    @Query("DELETE FROM otp_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Int)
}
