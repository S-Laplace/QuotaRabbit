package com.alice.quotarabbit.providers.codex.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexAnalyticsRepositoryTest {
    private fun obj(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject

    @Test
    fun featureBreakdownUsesThreadSourceAndNotPlatformSurface() {
        val daily = obj(
            """{"data":[{"date":"2026-10-21","totals":{"credits":60,"turns":3},
              "models":[{"model":"model-a","credits":60,"turns":3}]}]}"""
        )
        val breakdown = obj(
            """{"units":"percent","data":[{"date":"2026-10-21",
              "product_surface_usage_values":{"desktop_app":90,"work_web":10},
              "attribution":[
                {"thread_source":"user","value":56.1,"surface":"desktop_app","model":"model-a"},
                {"thread_source":"subagent","value":40.3,"surface":"desktop_app"},
                {"thread_source":"memory_consolidation","value":3.5},
                {"thread_source":"feature:thread_title","value":0.1}
              ]}]}"""
        )
        val actual = CodexAnalyticsRepository.parse(daily, breakdown, null, false)
        assertEquals(listOf("채팅", "서브에이전트", "메모리 업데이트", "채팅 제목"),
            actual.features.map { it.label })
        assertEquals(56.1, actual.features[0].usage, 0.00001)
        assertEquals(40.3, actual.features[1].usage, 0.00001)
        assertEquals(4, actual.features.size)
        assertTrue(actual.features.none { it.label.contains("desktop_app") ||
            it.label.contains("work_web") })
        assertEquals(3L, actual.models.first().turns)
        assertEquals(60.0, actual.models.first().credits ?: -1.0, 0.00001)
    }

    @Test
    fun groupFeaturesAcrossDaysIndependentOfSurfaceAndModel() {
        val daily = obj("""{"data":[{"date":"2026-10-21","totals":{"credits":10}},
            {"date":"2026-10-22","totals":{"credits":40}}]}""")
        val breakdown = obj("""{"units":"percent","data":[
          {"date":"2026-10-21","attribution":[
            {"thread_source":"user","value":8,"surface":"cli"},
            {"thread_source":"user","value":2,"surface":"desktop_app"}]},
          {"date":"2026-10-22","attribution":[
            {"thread_source":"subagent","value":5},
            {"thread_source":"user","value":15}]}]}""")
        val result = CodexAnalyticsRepository.parse(daily, breakdown, null, false)
        assertEquals(listOf("채팅", "서브에이전트"), result.features.map { it.label })
        assertEquals(25.0, result.features[0].usage, 0.00001)
        assertEquals(5.0, result.features[1].usage, 0.00001)
        assertEquals(10.0, result.featureDays["2026-10-21"]?.get("채팅") ?: -1.0, 0.00001)
        assertEquals(15.0, result.featureDays["2026-10-22"]?.get("채팅") ?: -1.0, 0.00001)
        assertEquals(5.0, result.featureDays["2026-10-22"]?.get("서브에이전트") ?: -1.0, 0.00001)
    }

    @Test
    fun breakdownSuppliesModelCreditsEvenWhenDailyUsageHasNoTodayRow() {
        val daily = obj("""{"data":[{"date":"2026-10-29","totals":{"turns":3}}]}""")
        val breakdown = obj("""{"data":[
          {"date":"2026-10-29","models":[{"model":"gpt-a","credits":4.0}]},
          {"date":"2026-10-30","models":[
            {"model":"gpt-a","credits":6.0},
            {"model":"gpt-b","credits":2.0}
          ],"attribution":[{"thread_source":"user","value":75.0},
                           {"thread_source":"subagent","value":25.0}]}
        ]}""")
        val report = CodexAnalyticsRepository.parse(daily, breakdown, null, false)
        assertEquals(6.0, report.modelCreditsDays["2026-10-30"]?.get("gpt-a") ?: -1.0, 0.0001)
        assertEquals(2.0, report.modelCreditsDays["2026-10-30"]?.get("gpt-b") ?: -1.0, 0.0001)
        assertEquals(10.0, report.models.first { it.model == "gpt-a" }.credits ?: -1.0, 0.0001)
        assertEquals(75.0, report.featureDays["2026-10-30"]?.get("채팅") ?: -1.0, 0.0001)
    }

    @Test
    fun singleDayRefreshFillsMissingTodayWithoutReplacingOlderDates() {
        val period = obj("""{"data":[
            {"date":"2026-10-29","models":[{"model":"gpt-a","credits":4}]},
            {"date":"2026-10-30","models":[],"attribution":[]}
        ]}""")
        val today = obj("""{"data":[{"date":"2026-10-30",
            "models":[{"model":"gpt-a","credits":3}],
            "attribution":[{"thread_source":"user","value":100}]}]}""")
        val combined = CodexAnalyticsRepository.mergeFreshToday(period, today, "2026-10-30")
        assertEquals(2, (combined["data"] as kotlinx.serialization.json.JsonArray).size)
        val report = CodexAnalyticsRepository.parse(obj("""{"data":[]}"""), combined, null, false)
        assertEquals(3.0, report.modelCreditsDays["2026-10-30"]?.get("gpt-a") ?: -1.0, 0.0001)
        assertEquals(100.0, report.featureDays["2026-10-30"]?.get("채팅") ?: -1.0, 0.0001)
        assertEquals(4.0, report.modelCreditsDays["2026-10-29"]?.get("gpt-a") ?: -1.0, 0.0001)
    }

    @Test
    fun singleDayEmptyResponseCannotWipeExistingRealTodayUsage() {
        val period = obj("""{"data":[{"date":"2026-10-30",
            "models":[{"model":"gpt-a","credits":4}]}]}""")
        val blank = obj("""{"data":[{"date":"2026-10-30","models":[]}]}""")
        assertEquals(period, CodexAnalyticsRepository.mergeFreshToday(period, blank, "2026-10-30"))
    }

    @Test
    fun platformOnlyBreakdownMustNotMasqueradeAsFeature() {
        val daily = obj("""{"data":[{"date":"2026-10-27","totals":{"credits":200}}]}""")
        val breakdown = obj("""{"units":"percent","data":[
          {"date":"2026-10-27","product_surface_usage_values":
          {"desktop_app":99.0,"web":1.0},"attribution":[]}]}""")
        val actual = CodexAnalyticsRepository.parse(daily, breakdown, null, false)
        assertTrue(actual.features.isEmpty())
        assertEquals(emptyMap<String, Double>(), actual.featureDays["2026-10-27"])
    }

    @Test
    fun invalidAttributionValuesAreIgnoredRatherThanMisclassified() {
        val daily = obj("""{"data":[{"date":"2026-10-27"}]}""")
        val breakdown = obj("""{"data":[{"date":"2026-10-27","attribution":[
          {"thread_source":"user","value":-5},
          {"thread_source":"subagent","value":"invalid"},
          {"surface":"desktop_app","value":100},
          {"thread_source":"memory_update","value":4},
          {"thread_source":{"feature":"thread_title"},"value":1}]}]}""")
        val actual = CodexAnalyticsRepository.parse(daily, breakdown, null, true)
        assertEquals(setOf("메모리 업데이트","채팅 제목"),
            actual.features.map { it.label }.toSet())
        assertTrue(actual.partial)
        assertFalse(actual.features.any { it.label.contains("desktop_app") })
    }
}
