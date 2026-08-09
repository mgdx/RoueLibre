# Contribuer à Roue Libre

Merci de l'intérêt que vous portez au projet. Toute contribution est
bienvenue : traduction, correction, relecture, portage vers une autre
agglomération.

Les échanges se font en français, le code en anglais.

## Avant de commencer

Lisez [`SPEC.md`](SPEC.md). C'est le cahier des charges complet et la source de
vérité du projet. Plusieurs décisions qui paraissent arbitraires y sont
justifiées — l'absence de Jetpack Compose, le refus des points d'intérêt
commerciaux sur la carte, la granularité au numéro de voirie. Si une
proposition contredit le `SPEC.md`, ouvrez d'abord une *issue* pour en
discuter : ces décisions ont été prises après réflexion, pas par défaut.

## Traduire l'application

C'est la contribution la plus utile si vous ne codez pas.

L'application est écrite en français. Toutes les chaînes vivent dans un seul
fichier : [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml).

1. **Créez le dossier de votre langue** : `app/src/main/res/values-<code>/`,
   où `<code>` est le code ISO 639-1 — `en`, `nl`, `de`, `es`…
   Pour une variante régionale, `values-pt-rBR`.
2. **Copiez-y `strings.xml`** et traduisez le contenu des balises, jamais leur
   attribut `name`.
3. **Vérifiez la mise en page.** Passez le système dans votre langue et
   parcourez chaque écran. L'allemand et le néerlandais allongent notablement
   les libellés ; c'est là que les mises en page cassent.

### Règles à respecter

**Les placeholders sont positionnels et gardent leur numéro.** `%1$s` reste
`%1$s`, mais son emplacement dans la phrase peut changer :

```xml
<!-- français -->
<string name="freshness_fresh">Mis à jour %1$s</string>
<!-- une langue où l'ordre diffère -->
<string name="freshness_fresh">%1$s aktualisiert</string>
```

Ne concaténez jamais deux chaînes dans le code : l'ordre des mots change d'une
langue à l'autre.

**Les pluriels passent par `<plurals>`, et les catégories varient.** Le
français en utilise deux (`one`, `other`), le polonais quatre, l'arabe six.
Fournissez celles de votre langue, listées dans les
[règles de pluriel CLDR](https://cldr.unicode.org/index/cldr-spec/plural-rules).

**Attention à la catégorie `one` en français.** Elle couvre 0 et 1 : on écrit
« 0 vélo », au singulier. Toutes les langues ne font pas ce choix.

**Les apostrophes s'échappent** : `l\'instant`, jamais `l'instant`.

**Les commentaires au-dessus des chaînes vous sont destinés.** Ils précisent ce
que remplace chaque placeholder et dans quel contexte la phrase apparaît.
Conservez-les, complétez-les si une ambiguïté vous a fait hésiter.

**Le ton.** Phrases courtes, voix active, aucun jargon. Un message d'erreur dit
ce qui s'est passé et quoi faire, sans s'excuser ni rester vague. Un écran vide
invite à agir, il ne constate pas. Une action porte le même nom du bouton
jusqu'à la confirmation.

**Le vocabulaire reste générique** : « station », « vélo », « réseau ». Le nom
d'un réseau particulier n'apparaît que dans la configuration de ville.

## Contribuer au code

### Mettre en place

```bash
git clone https://github.com/mgdx/RoueLibre.git
cd RoueLibre
./gradlew test
```

Il faut un JDK 17 ou plus et le SDK Android. Aucune clé ni aucun compte n'est
nécessaire.

### Avant d'ouvrir une pull request

```bash
./gradlew ktlintFormat            # formate
./gradlew test lint ktlintCheck   # doit passer sans le moindre avertissement
```

L'analyse statique ne tolère aucun avertissement. Ce n'est pas une coquetterie :
le projet est destiné à être audité par des relecteurs F-Droid, et un
avertissement laissé traîner en cache un autre.

### Ce que la relecture regardera

- **La séparation des couches.** La logique métier va dans `:core`, qui n'a le
  droit à aucun import Android. Le compilateur le vérifie ; c'est ce qui rend
  cette logique testable sur la JVM sans émulateur.
- **Aucune chaîne en dur**, ni dans le Kotlin, ni dans un layout. Aucune
  couleur ni taille en dur non plus : tout passe par les ressources.
- **Les commentaires expliquent le *pourquoi*.** Un commentaire qui paraphrase
  le code est du bruit qui se périmera. En revanche, documentez systématiquement
  les choix non évidents, les compromis acceptés, les contournements de
  limitations de bibliothèques, et chaque coefficient de l'algorithme
  d'itinéraire.
- **Du KDoc** sur tout ce qui est public : rôle, paramètres, valeur de retour,
  cas d'erreur.
- **Des tests.** Obligatoires sur l'algorithme de trajet, l'analyse des flux
  GBFS, la résolution d'adresses et l'interpolation des numéros. Toute
  correction de bogue s'accompagne du test qui l'aurait détectée.
- **Des erreurs explicites.** Des types de résultat, pas des exceptions
  silencieuses, et pour chaque échec un message français qui dit quoi faire.
- **Pas de code mort, pas d'abstraction anticipée.** Les seules généralisations
  demandées sont celles du portage vers une autre ville.

### Commits

Atomiques, avec un message qui décrit l'**intention** plutôt que la
manipulation. « Corrige la station de départ choisie quand deux stations sont à
égalité de temps » vaut mieux que « modifie RouteFinder.kt ».

### Ajouter une dépendance

Elle doit être :

1. **justifiée dans le `README.md`**, avec la raison de ce choix plutôt qu'un
   autre ;
2. **compatible GPLv3** — vérifiez avant d'intégrer ;
3. **exempte de tout service Google** : ni Play Services, ni Firebase, ni Maps
   SDK, ni ML Kit, ni Crashlytics. L'application doit fonctionner sur LineageOS
   sans GApps ;
4. **exempte de télémétrie.** L'analyse Exodus Privacy ne doit détecter aucun
   pisteur.

Si l'une de ces contraintes empêche une fonctionnalité que vous jugez utile,
dites-le dans une *issue* et proposez une alternative — ne la contournez pas en
silence.

## Signaler un bogue

Indiquez le modèle et la version d'Android, ce que vous attendiez, ce qui s'est
produit, et comment le reproduire. Si le problème concerne une station ou une
adresse précise, nommez-la : c'est souvent la donnée elle-même qui est en
cause, et la distinction se fait vite.

Aucun journal n'est envoyé nulle part par l'application. Si vous en joignez un,
c'est votre décision — relisez-le avant, une trace peut contenir une adresse
que vous avez cherchée.

## Licence

En contribuant, vous acceptez que votre travail soit publié sous
[GPLv3](LICENSE), comme le reste du projet.
