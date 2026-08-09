# Règles R8 pour la compilation de release.
#
# Le principe est de n'en ajouter aucune sans raison : chaque règle de
# conservation est du code que R8 renonce à retirer, donc des kilooctets
# d'APK et une contrainte de taille (SPEC §2, C4) un peu moins tenue.

# kotlinx.serialization engendre ses sérialiseurs à la compilation et les
# retrouve par réflexion sur le champ statique du compagnon. Sans ces règles,
# R8 les élague et l'analyse des flux GBFS échoue à l'exécution — en release
# seulement, ce qui est le pire moment pour s'en apercevoir.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Les vues personnalisées sont instanciées par réflexion depuis le XML gonflé.
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# OkHttp référence des classes optionnelles absentes d'Android ; ces
# avertissements sont attendus et sans conséquence.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Les traces d'un rapport de bogue doivent rester lisibles. Le fichier de
# correspondance n'est pas publié ; seuls les numéros de ligne le sont.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
