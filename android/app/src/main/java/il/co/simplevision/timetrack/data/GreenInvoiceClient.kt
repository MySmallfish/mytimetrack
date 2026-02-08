package il.co.simplevision.timetrack.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class GreenInvoiceClient(
    private val apiKey: String,
    private val apiSecret: String,
    private val baseUrl: String = "https://api.greeninvoice.co.il/api/v1",
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    suspend fun createProformaInvoice(
        subject: String,
        itemDescription: String,
        quantity: Double,
        unitPrice: Double,
        vatType: VatType,
        clientId: String,
        date: LocalDate,
    ): String? {
        val token = fetchToken()
        val payload = GreenInvoiceDocumentRequest(
            description = subject,
            client = GreenInvoiceClientReference(id = clientId, accountingKey = null),
            type = 300,
            date = date.format(DATE_FORMAT),
            lang = "en",
            currency = "USD",
            vatType = vatType.rawValue,
            income = listOf(
                GreenInvoiceIncomeItem(
                    description = itemDescription,
                    quantity = quantity,
                    price = unitPrice,
                    currency = "USD",
                    currencyRate = 1.0,
                    vatType = vatType.rawValue,
                ),
            ),
        )

        val url = "$baseUrl/documents"
        val req = Request.Builder()
            .url(url)
            .post(json.encodeToString(payload).toRequestBody(JSON))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw GreenInvoiceError.Server(res.code, body.ifBlank { null })
                runCatching { json.decodeFromString(GreenInvoiceResponse.serializer(), body) }.getOrNull()
                    ?.let { it.id ?: it.documentId ?: it.number }
            }
        }
    }

    suspend fun fetchClientId(accountingKey: String): String? {
        val trimmed = accountingKey.trim()
        if (trimmed.isEmpty()) return null
        val token = fetchToken()
        return fetchClientIdInternal(trimmed, token)
    }

    private suspend fun fetchToken(): String {
        val payload = GreenInvoiceAuthRequest(id = apiKey, secret = apiSecret)
        val url = "$baseUrl/account/token"
        val req = Request.Builder()
            .url(url)
            .post(json.encodeToString(payload).toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw GreenInvoiceError.Server(res.code, body.ifBlank { null })

                val decoded = runCatching { json.decodeFromString(GreenInvoiceAuthResponse.serializer(), body) }.getOrNull()
                decoded?.tokenValue()?.let { return@use it }

                extractTokenFromJson(body)?.let { return@use it }

                throw GreenInvoiceError.MissingToken
            }
        }
    }

    private suspend fun fetchClientIdInternal(accountingKey: String, token: String): String? {
        val queryKeys = listOf("accountingKey", "search", "query", "q")
        val postPayloads: List<Map<String, Any?>> = listOf(
            mapOf("search" to accountingKey),
            mapOf("query" to accountingKey),
            mapOf("q" to accountingKey),
            mapOf("accountingKey" to accountingKey),
            mapOf("accountingNumber" to accountingKey),
            mapOf("customerKey" to accountingKey),
            mapOf("externalId" to accountingKey),
            mapOf("filter" to mapOf("accountingKey" to accountingKey)),
            mapOf("client" to mapOf("accountingKey" to accountingKey)),
        )

        var lastError: Throwable? = null
        var hadResponse = false

        for (payload in postPayloads) {
            try {
                val result = fetchClientId(
                    path = "clients/search",
                    token = token,
                    method = "POST",
                    query = null,
                    payload = payload,
                    accountingKey = accountingKey,
                )
                hadResponse = true
                if (result != null) return result
            } catch (t: Throwable) {
                if (t is GreenInvoiceError.Server && t.statusCode == 405) continue
                lastError = t
            }
        }

        for (queryKey in queryKeys) {
            try {
                val result = fetchClientId(
                    path = "clients/search",
                    token = token,
                    method = "GET",
                    query = mapOf(queryKey to accountingKey),
                    payload = null,
                    accountingKey = accountingKey,
                )
                hadResponse = true
                if (result != null) return result
            } catch (t: Throwable) {
                if (t is GreenInvoiceError.Server && t.statusCode == 405) continue
                lastError = t
            }
        }

        for (queryKey in queryKeys) {
            try {
                val result = fetchClientId(
                    path = "clients",
                    token = token,
                    method = "GET",
                    query = mapOf(queryKey to accountingKey),
                    payload = null,
                    accountingKey = accountingKey,
                )
                hadResponse = true
                if (result != null) return result
            } catch (t: Throwable) {
                if (t is GreenInvoiceError.Server && t.statusCode == 405) continue
                lastError = t
            }
        }

        if (hadResponse) return null
        if (lastError != null) throw lastError
        return null
    }

    private suspend fun fetchClientId(
        path: String,
        token: String,
        method: String,
        query: Map<String, String>?,
        payload: Map<String, Any?>?,
        accountingKey: String,
    ): String? {
        val url = buildUrl("$baseUrl/$path", query)
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")

        if (method == "POST") {
            val bodyJson = mapToJsonString(payload ?: emptyMap())
            val body = bodyJson.toRequestBody(JSON)
            builder.post(body).header("Content-Type", "application/json")
        } else {
            builder.get()
        }

        val req = builder.build()
        return withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw GreenInvoiceError.Server(res.code, body.ifBlank { null })
                extractClientId(body, accountingKey)
            }
        }
    }

    private fun buildUrl(base: String, query: Map<String, String>?): String {
        if (query.isNullOrEmpty()) return base
        val encoded = query.entries.joinToString("&") { (k, v) ->
            val ek = URLEncoder.encode(k, StandardCharsets.UTF_8.name())
            val ev = URLEncoder.encode(v, StandardCharsets.UTF_8.name())
            "$ek=$ev"
        }
        return "$base?$encoded"
    }

    private fun mapToJsonString(map: Map<String, Any?>): String {
        val el = anyToJsonElement(map)
        return json.encodeToString(JsonElement.serializer(), el)
    }

    private fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> {
                val content = LinkedHashMap<String, JsonElement>()
                value.entries.forEach { (k, v) ->
                    val key = k?.toString() ?: return@forEach
                    content[key] = anyToJsonElement(v)
                }
                JsonObject(content)
            }
            is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }
    }

    private fun extractClientId(body: String, accountingKey: String): String? {
        val normalizedKey = accountingKey.trim().lowercase()
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null

        fun stringValue(obj: JsonObject, keys: List<String>): String? {
            for (key in keys) {
                val el = obj[key] ?: continue
                if (el is JsonPrimitive) {
                    if (el.isString) {
                        val s = el.content
                        if (s.isNotBlank()) return s
                    } else {
                        val n = el.content
                        if (n.isNotBlank()) return n
                    }
                }
            }
            return null
        }

        fun findClientId(clients: List<JsonObject>): String? {
            val matchKeys = listOf(
                "accountingKey", "accountingCode", "accountingId", "accountingNumber",
                "customerKey", "clientKey", "externalId", "externalID", "key", "code",
            )
            for (client in clients) {
                for (key in matchKeys) {
                    val value = stringValue(client, listOf(key))?.lowercase() ?: continue
                    if (value == normalizedKey) {
                        return stringValue(client, listOf("id", "clientId", "clientID", "_id"))
                    }
                }
            }
            if (clients.size == 1) {
                return stringValue(clients[0], listOf("id", "clientId", "clientID", "_id"))
            }
            return null
        }

        if (root is JsonObject) {
            stringValue(root, listOf("id", "clientId", "clientID", "_id"))?.let { return it }
            (root["client"] as? JsonObject)?.let { client ->
                stringValue(client, listOf("id", "clientId", "clientID", "_id"))?.let { return it }
            }
            for (key in listOf("items", "data", "clients", "results")) {
                val el = root[key] ?: continue
                if (el is JsonArray) {
                    val clients = el.mapNotNull { it as? JsonObject }
                    findClientId(clients)?.let { return it }
                }
            }
            return null
        }

        if (root is JsonArray) {
            val clients = root.mapNotNull { it as? JsonObject }
            return findClientId(clients)
        }

        return null
    }

    private fun extractTokenFromJson(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        fun findToken(el: JsonElement): String? {
            if (el is JsonObject) {
                for (key in listOf("token", "access_token", "accessToken")) {
                    val v = el[key]
                    if (v is JsonPrimitive && v.content.isNotBlank()) return v.content
                }
                val nested = el["data"]
                if (nested != null) return findToken(nested)
            }
            return null
        }
        return findToken(root)
    }

    companion object {
        val sandboxBaseUrl: String = "https://sandbox.d.greeninvoice.co.il/api/v1"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}

@Serializable
private data class GreenInvoiceDocumentRequest(
    val description: String,
    val client: GreenInvoiceClientReference? = null,
    val type: Int,
    val date: String,
    val lang: String,
    val currency: String,
    val vatType: Int,
    val income: List<GreenInvoiceIncomeItem>,
)

@Serializable
private data class GreenInvoiceClientReference(
    val id: String? = null,
    val accountingKey: String? = null,
)

@Serializable
private data class GreenInvoiceIncomeItem(
    val description: String,
    val quantity: Double,
    val price: Double,
    val currency: String,
    val currencyRate: Double,
    val vatType: Int,
)

@Serializable
private data class GreenInvoiceAuthRequest(
    val id: String,
    val secret: String,
)

@Serializable
private data class GreenInvoiceAuthResponse(
    val token: String? = null,
    @SerialName("accessToken") val accessToken: String? = null,
    @SerialName("access_token") val accessTokenSnake: String? = null,
    val data: TokenContainer? = null,
) {
    @Serializable
    data class TokenContainer(
        val token: String? = null,
        @SerialName("accessToken") val accessToken: String? = null,
        @SerialName("access_token") val accessTokenSnake: String? = null,
    )

    fun tokenValue(): String? {
        return token
            ?: accessToken
            ?: accessTokenSnake
            ?: data?.token
            ?: data?.accessToken
            ?: data?.accessTokenSnake
    }
}

@Serializable
private data class GreenInvoiceResponse(
    val id: String? = null,
    val documentId: String? = null,
    val number: String? = null,
)

sealed class GreenInvoiceError(message: String) : Exception(message) {
    object InvalidResponse : GreenInvoiceError("Invalid response from GreenInvoice.")
    data class Server(val statusCode: Int, val body: String?) : GreenInvoiceError(
        if (!body.isNullOrBlank()) "GreenInvoice error $statusCode: $body" else "GreenInvoice error $statusCode.",
    )

    object MissingToken : GreenInvoiceError("Could not read an authentication token from GreenInvoice.")
}
