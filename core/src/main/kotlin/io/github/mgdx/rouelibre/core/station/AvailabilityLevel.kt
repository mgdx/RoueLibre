package io.github.mgdx.rouelibre.core.station

/**
 * Niveau de disponibilité, tel que l'indicateur le représente (SPEC §7.1).
 *
 * Le découpage n'est pas cosmétique : il reprend le raisonnement de fiabilité
 * du SPEC §6. Une station peut se vider — ou se remplir — pendant qu'on marche
 * vers elle, et la probabilité que cela arrive dépend beaucoup plus du
 * franchissement des premiers vélos que des suivants.
 */
public enum class AvailabilityLevel {
    /** Rien du tout. Le trajet proposé serait impossible. */
    None,

    /**
     * Un ou deux. Une station à un seul vélo peut être vide à l'arrivée : c'est
     * le cas que le SPEC §6 demande de pénaliser, et il doit se voir.
     */
    Low,

    /** De trois à cinq. Le risque devient faible sans être nul. */
    Medium,

    /** Six ou plus. Au-delà, un vélo de plus ne change plus la décision. */
    Good,
}

/** Seuil sous lequel une station est jugée à risque de se vider. */
private const val LOW_THRESHOLD = 3

/** Seuil au-delà duquel un compte supplémentaire ne change plus rien. */
private const val GOOD_THRESHOLD = 6

/**
 * Classe un nombre de vélos ou de places dans l'échelle de l'indicateur.
 *
 * Les mêmes seuils s'appliquent aux vélos et aux places : la question posée
 * est la même des deux côtés — « est-ce que ce sera encore vrai quand
 * j'arriverai ? ».
 *
 * @param count nombre de vélos disponibles ou de places libres.
 */
public fun availabilityLevelOf(count: Int): AvailabilityLevel = when {
    count <= 0 -> AvailabilityLevel.None
    count < LOW_THRESHOLD -> AvailabilityLevel.Low
    count < GOOD_THRESHOLD -> AvailabilityLevel.Medium
    else -> AvailabilityLevel.Good
}

/** Ce que l'utilisateur cherche : emprunter un vélo, ou en rendre un. */
public enum class AvailabilityMode {
    /** Compter les vélos empruntables. */
    Bikes,

    /** Compter les places libres. */
    Docks,
}

/**
 * Ce qu'il faut afficher pour une station, dans le mode demandé.
 *
 * @property count nombre à écrire dans l'indicateur, ou `null` si inconnu.
 * @property level niveau correspondant, ou `null` si inconnu.
 * @property isOutOfService la station ne rend pas le service demandé.
 * @property filledFraction part de la capacité occupée par ce compte, entre 0
 *   et 1, ou `null` si la capacité n'est pas publiée. Alimente l'arc de
 *   l'indicateur : le chiffre dit combien, l'arc dit sur combien.
 */
public data class AvailabilityDisplay(
    public val count: Int?,
    public val level: AvailabilityLevel?,
    public val isOutOfService: Boolean,
    public val filledFraction: Float?,
)

/**
 * Traduit l'état d'une station en ce que l'indicateur doit montrer.
 *
 * @param mode selon que l'utilisateur cherche un vélo ou une place.
 */
public fun StationWithAvailability.displayFor(mode: AvailabilityMode): AvailabilityDisplay {
    val current = availability
        ?: return AvailabilityDisplay(
            count = null,
            level = null,
            isOutOfService = false,
            filledFraction = null,
        )

    // « Hors service » vaut pour le service demandé, pas pour la station en
    // bloc : une station qui n'accepte plus de retour peut encore prêter.
    val serviceRefused = when (mode) {
        AvailabilityMode.Bikes -> !current.isInstalled || !current.isRenting
        AvailabilityMode.Docks -> !current.isInstalled || !current.isReturning
    }
    if (serviceRefused) {
        return AvailabilityDisplay(
            count = null,
            level = null,
            isOutOfService = true,
            filledFraction = null,
        )
    }

    val count = when (mode) {
        AvailabilityMode.Bikes -> current.bikesAvailable
        AvailabilityMode.Docks -> current.docksAvailable
    }
    // La capacité publiée est préférée à la somme vélos + places, qui varie
    // quand un vélo est en cours de retrait ; à défaut, la somme fait office.
    val total = station.capacity?.takeIf { it > 0 }
        ?: (current.bikesAvailable + current.docksAvailable).takeIf { it > 0 }

    return AvailabilityDisplay(
        count = count,
        level = availabilityLevelOf(count),
        isOutOfService = false,
        filledFraction = total?.let { (count.toFloat() / it).coerceIn(0f, 1f) },
    )
}
