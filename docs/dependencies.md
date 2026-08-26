# Dependencies, and why each one

`SPEC.md` §4 requires justifying every addition. Nothing enters without a
reason, and there is no analytics, crash-reporting or advertising library under
any pretext.

Every one of them must also be GPLv3-compatible, free of any Google service and
free of telemetry — the procedure for adding one is in
[`CONTRIBUTING.md`](../CONTRIBUTING.md#adding-a-dependency).

| Dependency | Role | Why it rather than another |
|---|---|---|
| **OkHttp** | HTTP requests | Three GET requests do not justify Retrofit. OkHttp alone suffices and weighs less. |
| **kotlinx.serialization** | reading JSON | Generation at compile time, so no reflection and no R8 rules to maintain — unlike Gson or Moshi. |
| **Room** | station cache | Required by `SPEC.md` §8. Brings reactive streams and compile-time query checking. |
| **DataStore** | settings | A few isolated values; Room would be out of proportion. |
| **Coroutines** | asynchrony | The language's standard. |
| **Material Components** | interface base | Proven accessible components. None of its default colours survives. |
| **AndroidX** *(core, appcompat, fragment, lifecycle, recyclerview, swiperefreshlayout, constraintlayout)* | interface building blocks | The base of an XML-view application. |
| **MapLibre Native** | offline vector map | The project's only native dependency, and its only accepted departure from the size constraint: it is the price of offline operation. Reads the MBTiles straight from disk, without a tile server. BSD-2-Clause, minSdk 23. Shipped as the **`android-sdk-opengl` artefact**: since MapLibre 13 the default `android-sdk` renders through Vulkan only and marks Vulkan 1.0 as a required manifest feature, which would refuse installation on API 26+ devices without it; the OpenGL ES artefact is the same engine, same version, without that requirement — and its native library is about two megabytes lighter per ABI. |
| **BRouter** | offline route computation | A proven engine, cycling-oriented, with configurable profiles. Integrated as a **Git submodule** pinned to a tag: the `org.btools:brouter-core` Maven artifact one finds mentioned is published nowhere. MIT, GPLv3-compatible, licence notice kept in the application's legal notices. |
| **Atkinson Hyperlegible** | body typeface | Drawn by the Braille Institute for low vision: 0 is distinct from O, 1 from l. For an application read while walking, that is functional. SIL OFL. |
| **Bricolage Grotesque** | figures typeface | Bike counts are the central information; they deserve letters recognisable from a distance. Frozen into two static instances of 91 kB. SIL OFL. |

## Outside the APK

The data generation tools are not shipped to anybody's phone, but they decide
what the downloaded datasets contain, so they are pinned like the rest:
`osmium-tool`, `tippecanoe`, `fontTools`, and the map creator from
[BRouter](https://github.com/abrensch/brouter) (MIT), whose version is pinned
and whose archive is verified by SHA-256 digest.
