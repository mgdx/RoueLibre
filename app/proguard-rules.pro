# R8 rules for the release build.
#
# The principle is to add none of them without a reason: every keep rule is
# code R8 gives up removing, so kilobytes of APK and a size constraint
# (SPEC §2, C4) held a little less firmly.

# kotlinx.serialization generates its serialisers at compile time and finds
# them again by reflection on the companion's static field. Without these
# rules, R8 prunes them and parsing the GBFS feeds fails at run time — in
# release only, which is the worst moment to find out.
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

# Custom views are instantiated by reflection from the inflated XML.
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# BRouter reads its own version out of its package, in a static initialiser:
# `OsmTrack.class.getPackage().getImplementationVersion()`. R8 moves every
# class into the root package, where Android's `getPackage()` answers null —
# so that initialiser throws, and with it every route computation, in the
# release build alone. Keeping the package name is enough; the classes
# themselves stay renamed and shrunk.
-keeppackagenames btools.**

# OkHttp references optional classes absent from Android; these warnings are
# expected and of no consequence.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# The traces in a bug report must stay readable. The mapping file is not
# published; only the line numbers are.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
