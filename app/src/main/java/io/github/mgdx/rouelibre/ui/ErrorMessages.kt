package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.DataError

/**
 * Traduit un échec métier en phrase française destinée à l'utilisateur.
 *
 * C'est ici, et nulle part ailleurs, que les erreurs prennent des mots : le
 * module métier n'a pas le droit de contenir de texte affichable (SPEC §9), et
 * chaque échec doit dire ce qui s'est passé et quoi faire (SPEC §14).
 *
 * @return une phrase complète, prête à être affichée.
 */
fun DataError.toUserMessage(context: Context): String = when (this) {
    DataError.Offline -> context.getString(R.string.error_offline)
    DataError.Timeout -> context.getString(R.string.error_timeout)
    is DataError.ServerRefused ->
        context.getString(R.string.error_server_refused, statusCode)
    is DataError.MalformedResponse ->
        // Le détail technique reste dans la valeur, pour le journal et le
        // rapport de bogue ; l'utilisateur reçoit une consigne, pas une trace.
        context.getString(R.string.error_malformed)
    is DataError.FeedUnavailable ->
        context.getString(R.string.error_feed_unavailable, feedName)
    is DataError.UnsupportedFeedVersion ->
        context.getString(R.string.error_unsupported_version, version)
    is DataError.LocalStorageFailure ->
        context.getString(R.string.error_local_storage)
}
