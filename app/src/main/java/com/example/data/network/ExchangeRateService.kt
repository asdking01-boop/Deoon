package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

class ExchangeRateService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches the live USD -> ILS exchange rate from Open Exchange Rate API (free public JSON endpoint).
     * Never throws to UI; returns Result.
     */
    suspend fun fetchUsdToIlsRate(): Result<BigDecimal> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://open.er-api.com/v6/latest/USD")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}")
                }
                val body = response.body?.string() ?: error("Empty response")
                val json = JSONObject(body)
                val rates = json.getJSONObject("rates")
                val ilsDouble = rates.getDouble("ILS")
                BigDecimal.valueOf(ilsDouble).setScale(4, RoundingMode.HALF_UP)
            }
        }
    }
}
