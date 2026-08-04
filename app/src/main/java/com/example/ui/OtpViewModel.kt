package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.OtpAccount
import com.example.data.OtpDatabase
import com.example.data.OtpRepository
import com.example.util.Base32
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.Locale

class OtpViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OtpRepository
    val allAccounts: StateFlow<List<OtpAccount>>
    
    val searchQuery = MutableStateFlow("")
    val currentTimeMs = MutableStateFlow(System.currentTimeMillis())

    init {
        val database = OtpDatabase.getDatabase(application)
        repository = OtpRepository(database.otpDao())
        
        allAccounts = repository.allAccounts
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Continuously update the time tick for correct TOTP countdowns
        viewModelScope.launch {
            while (true) {
                currentTimeMs.value = System.currentTimeMillis()
                delay(100) // Keep countdown bars smooth and reactive
            }
        }
    }

    val filteredAccounts: StateFlow<List<OtpAccount>> = combine(allAccounts, searchQuery) { accounts, query ->
        if (query.isBlank()) {
            accounts
        } else {
            val lowercaseQuery = query.trim().lowercase(Locale.getDefault())
            accounts.filter {
                it.issuer.lowercase(Locale.getDefault()).contains(lowercaseQuery) ||
                it.accountName.lowercase(Locale.getDefault()).contains(lowercaseQuery)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addAccount(
        accountName: String,
        secretKey: String,
        issuer: String,
        algo: String = "SHA1",
        digits: Int = 6,
        period: Int = 30,
        customColorIndex: Int = 0,
        type: String = "TOTP",
        counter: Long = 0L
    ) {
        viewModelScope.launch {
            // Normalize key before saving
            val cleanSecret = secretKey.replace(" ", "").replace("-", "")
            val account = OtpAccount(
                accountName = accountName.trim(),
                secretKey = cleanSecret,
                issuer = issuer.trim().ifEmpty { "Other" },
                algo = algo,
                digits = digits,
                period = period,
                customLabelColorIndex = customColorIndex,
                type = type,
                counter = counter
            )
            repository.insert(account)
        }
    }

    fun updateAccount(account: OtpAccount) {
        viewModelScope.launch {
            repository.update(account)
        }
    }

    fun deleteAccount(account: OtpAccount) {
        viewModelScope.launch {
            repository.delete(account)
        }
    }

    fun deleteAccountById(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun toggleFavorite(account: OtpAccount) {
        viewModelScope.launch {
            repository.update(account.copy(isFavorite = !account.isFavorite))
        }
    }

    fun incrementHotpCounter(account: OtpAccount) {
        viewModelScope.launch {
            repository.update(account.copy(counter = account.counter + 1))
        }
    }

    /**
     * Parse an otpauth:// URI and return prefill fields for manual review before saving
     */
    fun parseOtpUri(uriString: String): Map<String, String>? {
        return try {
            val cleaned = uriString.trim()
            if (!cleaned.startsWith("otpauth://", ignoreCase = true)) return null
            val parts = cleaned.substring(10).split("?", limit = 2)
            val path = parts[0]
            val params = if (parts.size > 1) parts[1] else ""

            val typeAndEntity = path.split("/", limit = 2)
            if (typeAndEntity.size < 2) return null
            val type = typeAndEntity[0].uppercase(Locale.US)
            if (type != "TOTP" && type != "HOTP") return null

            val entity = URLDecoder.decode(typeAndEntity[1], "UTF-8")
            val entityParts = entity.split(":", limit = 2)
            var issuerFromPath = ""
            var accountName = ""
            
            if (entityParts.size == 2) {
                issuerFromPath = entityParts[0].trim()
                accountName = entityParts[1].trim()
            } else {
                accountName = entity.trim()
            }

            val paramMap = mutableMapOf<String, String>()
            if (params.isNotEmpty()) {
                val queryPairs = params.split("&")
                for (pair in queryPairs) {
                    val idx = pair.indexOf("=")
                    if (idx > 0) {
                        val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8").lowercase(Locale.US)
                        val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                        paramMap[key] = value
                    }
                }
            }

            val finalIssuer = (paramMap["issuer"] ?: issuerFromPath).ifEmpty { "Other" }
            val secret = paramMap["secret"] ?: ""
            val algo = paramMap["algorithm"] ?: "SHA1"
            val digits = paramMap["digits"] ?: "6"
            val period = paramMap["period"] ?: "30"
            val counter = paramMap["counter"] ?: "0"

            mapOf(
                "issuer" to finalIssuer,
                "accountName" to accountName,
                "secret" to secret,
                "algo" to algo.uppercase(Locale.US),
                "digits" to digits,
                "period" to period,
                "type" to type,
                "counter" to counter
            )
        } catch (e: Exception) {
            Log.e("OtpViewModel", "Failed parsing OTP URI", e)
            null
        }
    }

    fun exportBackup(): String {
        return try {
            val list = allAccounts.value
            val sb = java.lang.StringBuilder()
            sb.append("[\n")
            list.forEachIndexed { index, item ->
                sb.append("  {\n")
                sb.append("    \"issuer\": \"${escapeJson(item.issuer)}\",\n")
                sb.append("    \"accountName\": \"${escapeJson(item.accountName)}\",\n")
                sb.append("    \"secretKey\": \"${escapeJson(item.secretKey)}\",\n")
                sb.append("    \"algo\": \"${item.algo}\",\n")
                sb.append("    \"digits\": ${item.digits},\n")
                sb.append("    \"period\": ${item.period},\n")
                sb.append("    \"colorIndex\": ${item.customLabelColorIndex},\n")
                sb.append("    \"isFavorite\": ${item.isFavorite},\n")
                sb.append("    \"type\": \"${item.type}\",\n")
                sb.append("    \"counter\": ${item.counter}\n")
                sb.append("  }")
                if (index < list.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("]")
            sb.toString()
        } catch (e: Exception) {
            Log.e("OtpViewModel", "Failed to export", e)
            ""
        }
    }

    fun importBackup(backupString: String): Result<Int> {
        return try {
            val json = backupString.trim()
            if (!json.startsWith("[") || !json.endsWith("]")) {
                return Result.failure(Exception("Invalid format (Must be valid JSON array)"))
            }
            
            // Hand-rolled solid regex-based JSON parser to keep it compile-safe and fully working without external dependencies
            val entryRegex = Regex("\\{[^\\}]+\\}")
            val matches = entryRegex.findAll(json)
            var count = 0
            
            for (match in matches) {
                val entryText = match.value
                val issuer = extractJsonValue(entryText, "issuer") ?: "Imported"
                val accountName = extractJsonValue(entryText, "accountName") ?: "Account"
                val secretKey = extractJsonValue(entryText, "secretKey") ?: continue
                val algo = extractJsonValue(entryText, "algo") ?: "SHA1"
                val digits = extractJsonValue(entryText, "digits")?.toIntOrNull() ?: 6
                val period = extractJsonValue(entryText, "period")?.toIntOrNull() ?: 30
                val colorIndex = extractJsonValue(entryText, "colorIndex")?.toIntOrNull() ?: 0
                val isFavorite = extractJsonValue(entryText, "isFavorite")?.toBoolean() ?: false
                val type = extractJsonValue(entryText, "type") ?: "TOTP"
                val counter = extractJsonValue(entryText, "counter")?.toLongOrNull() ?: 0L

                if (secretKey.isNotBlank()) {
                    viewModelScope.launch {
                        repository.insert(
                            OtpAccount(
                                accountName = accountName,
                                secretKey = secretKey.replace(" ", "").replace("-", ""),
                                issuer = issuer,
                                algo = algo,
                                digits = digits,
                                period = period,
                                customLabelColorIndex = colorIndex,
                                isFavorite = isFavorite,
                                type = type,
                                counter = counter
                            )
                        )
                    }
                    count++
                }
            }
            Result.success(count)
        } catch (e: Exception) {
            Log.e("OtpViewModel", "Failed to import", e)
            Result.failure(e)
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}]*))")
        val match = pattern.find(json) ?: return null
        return (match.groups[1]?.value ?: match.groups[2]?.value)?.trim()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OtpViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return OtpViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
