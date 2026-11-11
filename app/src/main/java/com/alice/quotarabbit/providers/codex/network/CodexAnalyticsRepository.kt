package com.alice.quotarabbit.providers.codex.network

import android.content.Context
import com.alice.quotarabbit.core.network.ProviderHttpClient
import com.alice.quotarabbit.data.secure.FileSecureSessionStore
import com.alice.quotarabbit.domain.model.ProviderAccount
import com.alice.quotarabbit.providers.codex.session.AndroidKeystoreCodexSessionCipher
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.*

data class AnalyticsDay(val date: String, val input: Long?, val cache: Long?, val output: Long?,
                        val credits: Double?, val turns: Long?, val modelTurns: Map<String, Long> = emptyMap())
data class AnalyticsModel(val model: String, val credits: Double?, val turns: Long?)
data class AnalyticsClient(val client: String, val input: Long?, val cache: Long?, val output: Long?)
data class AnalyticsFeature(val label: String, val usage: Double)
data class AnalyticsReport(val days: List<AnalyticsDay>, val models: List<AnalyticsModel>,
                           val clients: List<AnalyticsClient>, val features: List<AnalyticsFeature>,
                           val pluginMetricsPresent: Boolean, val partial: Boolean,
                           val featureDays: Map<String, Map<String, Double>> = emptyMap(),
                           val modelCreditsDays: Map<String, Map<String, Double>> = emptyMap())
sealed interface AnalyticsResult {
    data class Loaded(val report: AnalyticsReport) : AnalyticsResult
    data class Error(val message: String) : AnalyticsResult
}

class CodexAnalyticsRepository(private val context: Context,
    private val client: ProviderHttpClient = ProviderHttpClient()) {
    suspend fun fetch(account: ProviderAccount, days: Int): AnalyticsResult {
        if (account.providerId.value != "codex" || days !in listOf(7, 30))
            return AnalyticsResult.Error("지원하지 않는 요청입니다.")
        val envelope = FileSecureSessionStore(context.filesDir.resolve("sessions"))
            .load("codex", account.localAccountId.value)
            ?: return AnalyticsResult.Error("Codex 로그인이 필요합니다.")
        val session = AndroidKeystoreCodexSessionCipher().decrypt(envelope).getOrNull()
            ?: return AnalyticsResult.Error("계정 인증을 확인할 수 없습니다.")
        if (session.accessToken.isBlank() ||
            (session.tokenExpiresAtEpochSeconds ?: 0) < System.currentTimeMillis()/1000 + 60)
            return AnalyticsResult.Error("토큰이 만료되었습니다. 계정을 새로고침하세요.")
        val headers = buildMap {
            put("Authorization", "Bearer " + session.accessToken)
            put("Accept", "application/json")
            put("User-Agent", CodexUsageClient.DEFAULT_USER_AGENT)
            (session.accountId ?: account.providerAccountId?.value)?.takeIf { it.isNotBlank() }
                ?.let { put("ChatGPT-Account-Id", it) }
        }
        // Align inclusive calendar dates with the device-local seven-day chart.
        val end = LocalDate.now(ZoneId.systemDefault()).plusDays(1)
        // Fetch one extra earlier day so an unreported today can be excluded while
        // still showing a complete 7- or 30-day window ending yesterday.
        val start = end.minusDays(days.toLong() + 1L)
        val query = "start_date=$start&end_date=$end&group_by=day"
        suspend fun read(path: String): Pair<Int, JsonObject?> {
            val response = client.get("https://chatgpt.com/backend-api/wham/" + path, headers)
            val json = if (response.statusCode == 200)
                runCatching { Json.parseToJsonElement(response.body) as? JsonObject }.getOrNull()
                else null
            return response.statusCode to json
        }
        return try {
            val baseline = read("usage").first
            if (baseline != 200) return AnalyticsResult.Error("인증 또는 할당량 조회 실패 ($baseline)")
            val (code, data) = read("analytics/daily-workspace-usage-counts?$query&workspace_user=true")
            if (code != 200 || data == null)
                return AnalyticsResult.Error("일별 사용량 조회 실패 ($code)")
            val (breakCode, breakdown) = read("usage/daily-token-usage-breakdown?$query&units=percent")
            val (pluginCode, plugins) = read("analytics/daily-plugin-usage-metrics?$query&workspace_user=true")
            // The multi-day API can lag behind a fresh, one-day request.
            // Only a verified positive single-day response may replace a blank day.
            val today = end.minusDays(1).toString()
            val todayQuery = "start_date=$today&end_date=$end&group_by=day"
            suspend fun refreshToday(existing: JsonObject?, path: String): JsonObject? {
                if (existing == null || hasPositiveDay(existing, today)) return existing
                val fresh = runCatching { read(path).second }.getOrNull()
                return mergeFreshToday(existing, fresh, today)
            }
            val freshDaily = refreshToday(data,
                "analytics/daily-workspace-usage-counts?$todayQuery&workspace_user=true")
            val freshBreakdown = if (breakCode == 200) refreshToday(breakdown,
                "usage/daily-token-usage-breakdown?$todayQuery&units=percent")
                else breakdown
            AnalyticsResult.Loaded(parse(freshDaily ?: data, freshBreakdown, plugins,
                breakCode != 200 || pluginCode != 200))
        } catch (_: Exception) {
            AnalyticsResult.Error("네트워크 또는 분석 API를 확인할 수 없습니다.")
        }
    }

    internal companion object {
        private fun rowHasUsage(row: JsonObject): Boolean {
            val totals = row["totals"] as? JsonObject
            if (totals?.values?.any { (it as? JsonPrimitive)?.doubleOrNull?.let { n -> n > 0 } == true } == true)
                return true
            if (row["models"].asItems().any {
                (it.double("credits") ?: 0.0) > 0.0 || (it.long("turns") ?: 0L) > 0L
            }) return true
            return row["attribution"].asItems().any { (it.double("value") ?: 0.0) > 0.0 }
        }

        internal fun hasPositiveDay(document: JsonObject, date: String): Boolean =
            document["data"].asItems().any { it.string("date") == date && rowHasUsage(it) }

        internal fun mergeFreshToday(
            original: JsonObject,
            refresh: JsonObject?,
            date: String,
        ): JsonObject {
            if (refresh == null || hasPositiveDay(original, date)) return original
            val fresh = refresh["data"].asItems()
                .firstOrNull { it.string("date") == date && rowHasUsage(it) }
                ?: return original
            val older = original["data"].asItems().filterNot { it.string("date") == date }
            return JsonObject(original + ("data" to JsonArray(older + fresh)))
        }

        internal fun parse(daily: JsonObject, breakdown: JsonObject?, plugins: JsonObject?,
            partial: Boolean): AnalyticsReport {
        val modelCreditsDays = linkedMapOf<String, Map<String, Double>>()
        val modelTurns = linkedMapOf<String, Long>()
        val clientTokens = linkedMapOf<String, Triple<Long, Long, Long>>()
        val dayList = mutableListOf<AnalyticsDay>()
        for (d in daily["data"].asItems()) {
            val date = d.string("date") ?: continue
            val total = d["totals"] as? JsonObject
            dayList += AnalyticsDay(date, total?.long("uncached_text_input_tokens"),
                total?.long("cached_text_input_tokens"), total?.long("text_output_tokens"),
                total?.double("credits"), total?.long("turns"), d["models"].asItems().mapNotNull { m ->
                val name = m.string("model") ?: return@mapNotNull null
                val turns = m.long("turns") ?: return@mapNotNull null
                name to turns
            }.toMap())
            val dayModelCredits = linkedMapOf<String, Double>()
            for (m in d["models"].asItems()) {
                val name = m.string("model") ?: continue
                m.double("credits")?.takeIf { it.isFinite() && it >= 0.0 }
                    ?.let { dayModelCredits[name] = (dayModelCredits[name] ?: 0.0) + it }
                m.long("turns")?.takeIf { it >= 0L }
                    ?.let { modelTurns[name] = (modelTurns[name] ?: 0L) + it }
            }
            if (dayModelCredits.isNotEmpty()) modelCreditsDays[date] = dayModelCredits
            for (c in d["clients"].asItems()) {
                val name = c.string("client_id") ?: continue
                val old = clientTokens[name] ?: Triple(0L,0L,0L)
                clientTokens[name] = Triple(
                    old.first + (c.long("uncached_text_input_tokens") ?: 0),
                    old.second + (c.long("cached_text_input_tokens") ?: 0),
                    old.third + (c.long("text_output_tokens") ?: 0))
            }
        }
        val clients = clientTokens.map { (name,t) ->
            AnalyticsClient(name,t.first,t.second,t.third)
        }.sortedByDescending { (it.input ?: 0L)+(it.cache ?: 0L)+(it.output ?: 0L) }
        // ChatGPT's "기능별" breakdown groups by attribution.thread_source,
        // not product_surface_usage_values (desktop_app, work_web, cli, ...).
        // Attribution.value is a relative usage metric, NOT a credit or token count.
        val featureTotals = linkedMapOf<String, Double>()
        val featureDays = linkedMapOf<String, Map<String, Double>>()
        for (d in breakdown?.get("data").asItems()) {
            val date = d.string("date") ?: continue
            val breakdownCredits = linkedMapOf<String, Double>()
            for (m in d["models"].asItems()) {
                val model = m.string("model") ?: continue
                val credits = m.double("credits")?.takeIf { it.isFinite() && it > 0.0 }
                    ?: continue
                breakdownCredits[model] = (breakdownCredits[model] ?: 0.0) + credits
            }
            // When daily counts lag, the breakdown can already provide per-model credits.
            // Never overwrite a positive daily value with an empty breakdown row.
            if (breakdownCredits.isNotEmpty()) modelCreditsDays[date] = breakdownCredits
            val entries = linkedMapOf<String, Double>()
            for (a in d["attribution"].asItems()) {
                val source = a.string("thread_source")
                    ?: (a["thread_source"] as? JsonObject)?.string("feature")
                        ?.let { "feature:$it" }
                    ?: continue
                val value = a.double("value") ?: continue
                if (!value.isFinite() || value < 0) continue
                val label = threadSourceLabel(source)
                entries[label] = (entries[label] ?: 0.0) + value
            }
            for ((label, amount) in entries) {
                featureTotals[label] = (featureTotals[label] ?: 0.0) + amount
            }
            featureDays[date] = entries
        }
        val modelCredits = linkedMapOf<String, Double>()
        modelCreditsDays.values.forEach { day ->
            day.forEach { (name, credits) ->
                modelCredits[name] = (modelCredits[name] ?: 0.0) + credits
            }
        }
        val models = (modelCredits.keys + modelTurns.keys).distinct().map {
            AnalyticsModel(it, modelCredits[it], modelTurns[it])
        }.sortedByDescending { it.credits ?: 0.0 }
        val features = featureTotals.map { AnalyticsFeature(it.key, it.value) }
            .filter { it.usage > 0.0 }
            .sortedByDescending { it.usage }
        val pluginAvailable = plugins?.get("data").asItems()
            .any { it["plugin_usage_overviews"] is JsonArray }
        return AnalyticsReport(dayList.sortedBy { it.date }, models, clients, features,
            pluginAvailable,partial,featureDays,modelCreditsDays)
    }

        internal fun threadSourceLabel(source: String): String {
            val normalized = source.trim().lowercase(java.util.Locale.ROOT)
                .removePrefix("feature:")
            return when (normalized) {
                "user", "chat" -> "채팅"
                "subagent", "sub_agent" -> "서브에이전트"
                "memory_consolidation", "memory_update", "memory" -> "메모리 업데이트"
                "thread_title", "chat_title", "title_generation" -> "채팅 제목"
                else -> {
                    val safe = normalized.takeIf {
                        it.matches(Regex("[a-z0-9_:-]{1,64}"))
                    }
                    if (safe == null) "기타 기능" else "기타 기능 · $safe"
                }
            }
        }
    }
}
private fun JsonElement?.asItems(): List<JsonObject> =
    (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull
private fun JsonObject.double(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull
