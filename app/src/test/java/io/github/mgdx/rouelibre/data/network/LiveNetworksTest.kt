package io.github.mgdx.rouelibre.data.network

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.gbfs.GbfsParser
import io.github.mgdx.rouelibre.core.network.RedirectScope
import io.github.mgdx.rouelibre.data.datasets.DatasetDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * The redirection rule against the real hosts of every network served
 * (SPEC §4.1).
 *
 * The unit tests say the rule does what it says; only the real hosts say
 * whether what it says lets them through. This one goes out to every city's
 * discovery document and every feed it announces, to every data release's
 * manifest and the first byte of its first file, and to the catalogue —
 * through the application's own client, with the label each call carries in
 * the application.
 *
 * **It fails only on a refused redirection**, the one thing it is here to find.
 * A server down or answering 500 is the producer's day, not this rule's, and is
 * only written into the summary — Fifteen answers 500 for Auxerre and Metz at
 * the time of writing.
 *
 * Skipped unless asked for, since it calls three hundred hosts:
 * `./gradlew :app:testDebugUnitTest -PliveNetworks --tests '*LiveNetworksTest*'`,
 * or `-PliveNetworks=aachen,velov` for a few cities. The summary is printed and
 * written to `app/build/live-networks.txt`.
 */
class LiveNetworksTest {

    private val client = roueLibreHttpClient(::jvmRegistrableDomain)

    @Test
    fun `no real host is refused a redirection it needs`() = runBlocking {
        val asked = System.getProperty("rouelibre.liveNetworks")
        assumeTrue("live check not asked for (-PliveNetworks)", asked != null)
        val only = asked.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        val config = File(System.getProperty("rouelibre.config")!!)
        val cities = config.resolve("cities").listFiles { file -> file.extension == "json" }!!
            .sortedBy { it.name }
            .filter { only.isEmpty() || it.nameWithoutExtension in only }
            .map { it.nameWithoutExtension to Json.parseToJsonElement(it.readText()).jsonObject }

        // Sixteen at a time: three thousand requests one after the other take
        // a quarter of an hour, and no single host is asked more than its own.
        val io = Dispatchers.IO.limitedParallelism(PARALLEL_CALLS)
        val networks = cities.map { (id, city) -> async(io) { checkNetwork(id, city) } }
        val manifests = cities.mapNotNull { (_, city) -> city.text("dataRelease", "manifestUrl") }
            .distinct()
            .map { url -> async(io) { checkManifest(url) } }
        val catalogue = async(io) {
            val url = Json.parseToJsonElement(config.resolve("catalogue.json").readText())
                .jsonObject.text("catalogueUrl")
            if (url ==
                null
            ) {
                emptyList()
            } else {
                listOf(call("catalogue", url, RedirectScope.PUBLICATION))
            }
        }
        val results = (networks + manifests + catalogue).awaitAll().flatten()

        val refused = results.filter { it.outcome is Verdict.Refused }
        val failed = results.filter { it.outcome is Verdict.Failed }
        val summary = buildString {
            appendLine("Live redirection check, ${cities.size} cities, ${results.size} requests")
            appendLine("  answered: ${results.size - refused.size - failed.size}")
            appendLine("  refused redirections: ${refused.size}")
            refused.forEach { appendLine("    ${it.subject} ${it.url} -> ${it.outcome}") }
            appendLine("  other failures (reported only): ${failed.size}")
            failed.forEach { appendLine("    ${it.subject} ${it.url} -> ${it.outcome}") }
        }
        println(summary)
        File("build/live-networks.txt").writeText(summary)
        assertTrue("redirections refused:\n$summary", refused.isEmpty())
    }

    private suspend fun checkNetwork(id: String, city: JsonObject): List<Checked> {
        val discoveryUrl = city.text("gbfs", "discoveryUrl") ?: return emptyList()
        val source = GbfsRemoteSource(
            client = client,
            parser = GbfsParser(),
            userAgent = USER_AGENT,
            unnamedStationLabel = { "" },
            ioDispatcher = Dispatchers.IO,
        )
        val discovery = when (val outcome = source.fetchDiscovery(discoveryUrl)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return listOf(Checked(id, discoveryUrl, verdictOf(outcome.error)))
        }
        return listOf(Checked(id, discoveryUrl, Verdict.Answered)) +
            discovery.feedUrlsByName.values.distinct().map { call(id, it, RedirectScope.GBFS) }
    }

    private suspend fun checkManifest(url: String): List<Checked> {
        val manifest = when (
            val outcome = DatasetDownloader(client, USER_AGENT, Dispatchers.IO).fetchManifest(url)
        ) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return listOf(Checked("manifest", url, verdictOf(outcome.error)))
        }
        val first = manifest.datasets.firstOrNull()?.files?.firstOrNull()
            ?: return listOf(Checked("manifest", url, Verdict.Answered))
        return listOf(
            Checked("manifest", url, Verdict.Answered),
            call("data file", first.url, RedirectScope.PUBLICATION, range = "bytes=0-0"),
        )
    }

    /** One GET with [scope]'s label, the body left unread. */
    private fun call(
        subject: String,
        url: String,
        scope: RedirectScope,
        range: String? = null,
    ): Checked {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .tag(RedirectScope::class.java, scope)
            .apply { if (range != null) header("Range", range) }
            .build()
        val verdict = try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Verdict.Answered
                } else {
                    Verdict.Failed(
                        "HTTP ${response.code}",
                    )
                }
            }
        } catch (error: RefusedRedirectException) {
            Verdict.Refused(error.targetHost)
        } catch (error: IOException) {
            Verdict.Failed(error.toString())
        }
        return Checked(subject, url, verdict)
    }

    /**
     * A refusal reaches a caller as an untrusted server; so does a bad
     * certificate, which the detail tells apart.
     */
    private fun verdictOf(error: DataError): Verdict =
        if (error is DataError.UntrustedServer && error.detail.startsWith("redirect towards")) {
            Verdict.Refused(error.detail)
        } else {
            Verdict.Failed(error.toString())
        }

    private fun JsonObject.text(vararg path: String): String? {
        var node: JsonObject = this
        for (key in path.dropLast(1)) node = node[key] as? JsonObject ?: return null
        return node[path.last()]?.jsonPrimitive?.content
    }

    private data class Checked(val subject: String, val url: String, val outcome: Verdict)

    private sealed interface Verdict {
        data object Answered : Verdict
        data class Refused(val target: String) : Verdict
        data class Failed(val reason: String) : Verdict
    }

    private companion object {
        const val USER_AGENT = "RoueLibre/live-check (+https://github.com/mgdx/RoueLibre)"
        const val PARALLEL_CALLS = 16
    }
}
