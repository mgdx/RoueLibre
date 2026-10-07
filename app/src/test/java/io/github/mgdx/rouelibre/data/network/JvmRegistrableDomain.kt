package io.github.mgdx.rouelibre.data.network

import okhttp3.HttpUrl
import java.util.Optional

/**
 * The registrable domain of [url]'s host, read from the JDK's own public suffix
 * list, or `null` where it has none.
 *
 * OkHttp reads its list from the APK's assets on Android, and a JVM test has no
 * APK: asked there, `topPrivateDomain()` throws. The JDK carries the same list,
 * from the same source, behind an internal class the test task opens to the
 * tests (`app/build.gradle.kts`). A guess such as "the last two labels" would
 * not do: it would call `bristol.co.uk` and `oxford.co.uk` one domain, and a
 * live check run on it would pass redirections the telephone refuses.
 */
internal fun jvmRegistrableDomain(url: HttpUrl): String? {
    val host = url.host
    // An address has no domain at all, which the JDK's lookup does not know.
    if (':' in host || host.all { it.isDigit() || it == '.' }) return null
    val found = FROM.invoke(null, host) as Optional<*>
    return found.map { NAME.invoke(it) as String }.orElse(null)
}

private val REGISTERED_DOMAIN: Class<*> = Class.forName("sun.security.util.RegisteredDomain")
private val FROM = REGISTERED_DOMAIN.getMethod("from", String::class.java)
private val NAME = REGISTERED_DOMAIN.getMethod("name")
