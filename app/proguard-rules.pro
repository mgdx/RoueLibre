# R8 rules for the release build.
#
# The principle is to add none of them without a reason: every keep rule is
# code R8 gives up removing, so kilobytes of APK and a size constraint
# (SPEC §2, C4) held a little less firmly.
#
# The same principle asks that a rule which has stopped being needed be taken
# out, and a rule that never was never be written. What follows the reasoning
# of a library is the library's business: kotlinx.serialization, OkHttp and
# MapLibre each ship their own rules inside their artefact, and R8 reads them
# with ours. A copy kept here does not make the build safer; it makes it look
# as though something depended on it.

# Nothing is kept here for kotlinx.serialization. It used to be: the generated
# serialisers are found again by reflection on the companion's static field,
# and R8 pruned them, so parsing the GBFS feeds failed at run time in the
# release build alone. Since version 1.9 the library carries those very rules
# itself, word for word and wider, and ours matched them without adding
# anything — removing the twenty-four lines left the APK identical to the byte.

# Nothing is kept here for the custom views inflated from XML: aapt2 reads the
# layouts and writes one keep rule per class they name, our own views included.
# A blanket rule on the (Context, AttributeSet) constructor would match every
# view of every library instead, and R8 would stop removing the ones no layout
# ever names — MotionLayout, Slider, TabLayout and their kin.

# Nothing is kept here for OkHttp's optional platforms either — Conscrypt,
# BouncyCastle, the internal platform classes. OkHttp declares those warnings
# quiet in its own artefact. OpenJSSE is not even that: OkHttp 5 dropped it,
# and the rule this file carried named a package no longer referenced anywhere.

# BRouter reads its own version out of its package, in a static initialiser:
# `OsmTrack.class.getPackage().getImplementationVersion()`. R8 moves every
# class into the root package, where Android's `getPackage()` answers null —
# so that initialiser throws, and with it every route computation, in the
# release build alone. Keeping the package name is enough; the classes
# themselves stay renamed and shrunk.
#
# Only `btools.router` is imported here, so the rule looks wider than it needs
# to be. Narrowing it to that one package was tried and **costs 908 bytes**:
# the rest of BRouter then lands in the root package, where the names R8 has
# to invent are longer than the prefix it saved. The wildcard is measured, not
# lazy.
-keeppackagenames btools.**

# The traces in a bug report must stay readable. The mapping file is not
# published; only the line numbers are. Measured, and worth saying because the
# opposite is the intuition: dropping these three lines makes the APK 6,328
# bytes *larger*, R8 giving up its compact line tables along with them.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
