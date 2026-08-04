package com.example.data

import kotlinx.coroutines.flow.Flow

class OtpRepository(private val otpDao: OtpDao) {
    val allAccounts: Flow<List<OtpAccount>> = otpDao.getAllAccounts()

    suspend fun insert(account: OtpAccount) {
        otpDao.insertAccount(account)
    }

    suspend fun update(account: OtpAccount) {
        otpDao.updateAccount(account)
    }

    suspend fun delete(account: OtpAccount) {
        otpDao.deleteAccount(account)
    }

    suspend fun deleteById(id: Int) {
        otpDao.deleteAccountById(id)
    }
}
