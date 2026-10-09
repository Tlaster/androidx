# AndroidX storage on Windows Native

This fork develops experimental `mingwX64` support for the storage versions used
by Flare. It is not an official AndroidX distribution. Experimental Maven snapshots use the
`moe.tlaster.androidx` namespace.

## Release baseline

The Git branch starts at the official Room 3.0.3 release commit, preserving the
original AndroidX Git history and GitHub fork relationship.

| Component | Version | Official release snapshot |
| --- | --- | --- |
| Room | 3.0.3 | `8d7131f5c528528bee5dc6942aebc7fe90463551` |
| SQLite | 2.7.1 | `7bbade5b385ef86084f186d9ec7874cd27316545` |
| DataStore Core/Okio | 1.3.0-alpha11 | `e4bd62f853853bf3522ed15681c58ef28b09ed44` |

These snapshots are linked from the official release notes for
[Room 3.0.3](https://developer.android.com/jetpack/androidx/releases/room3#3.0.3),
[SQLite 2.7.1](https://developer.android.com/jetpack/androidx/releases/sqlite#2.7.1),
and [DataStore alpha11](https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0-alpha11),
all released on September 9, 2026. Flare declares SQLite 2.7.0, but Room 3.0.3's
published dependencies require SQLite 2.7.1.

The SQLite source tree in the Room release snapshot is identical to the SQLite
2.7.1 snapshot; its version is aligned here. DataStore Core/Okio production sources
and non-instrumented tests are aligned to their own release snapshot, retaining the
Room release branch's build logic and Android instrumentation test layout.
Other DataStore modules are outside this version alignment and port.

## Initial scope

- `room3-common`, `room3-runtime`, `room3-paging`
- `sqlite`, `sqlite-async`, `sqlite-framework`, `sqlite-bundled`
- `datastore-core`, `datastore-core-okio`

Room uses Windows byte-range file locks with Unicode paths. Existing POSIX locks
remain shared by Unix targets. As in Room 3.0.3 upstream, callers must create the
parent directory before opening a file database.

Windows applications should use `BundledSQLiteDriver`; `NativeSQLiteDriver`
requires linking SQLite with `sqlite3_load_extension` available. DataStore uses
`OkioStorage`, with Okio 3.18.2 on Windows for Unicode paths. Preferences DataStore
and multiprocess DataStore are outside this initial port.

## Build and CI

Use the focused Playground:

```sh
cd playground-projects/storage-playground
export OUT_DIR=/absolute/path/to/build-output
./gradlew :room3:room3-runtime:compileKotlinMingwX64 \
  :datastore:datastore-core-okio:compileKotlinMingwX64
```

Use JDK 21 and an Android SDK with the NDK version in `buildSrc/ndk.gradle`.
Gradle downloads the release branch's Kotlin/Native toolchain and cross-compilation
dependencies. The original release branch toolchain is retained.

[MinGW storage CI](.github/workflows/mingw-storage.yml) checks the Windows-disabled
configuration, compiles all nine libraries on Linux, links native test executables,
runs existing Linux regression tests, then executes the binaries on Windows.
Coverage includes existing Room runtime and DataStore core tests, Windows file-lock
contention and cleanup, and a KSP-generated database with persistence, migration,
rollback and Flow updates. The integration tests also cover Unicode paths,
DataStore concurrent updates, failed updates and reopening the file.

CI uploads unpacked KLIBs, test executables and test logs. These are development
outputs, not a complete Maven publication. Windows runtime support must be judged
from the Windows job, not compilation alone. Network-share locking, process-crash
recovery and Flare integration need further validation before production use.

## Snapshot artifacts

The nine libraries publish under `moe.tlaster.androidx.room3`,
`moe.tlaster.androidx.sqlite`, and `moe.tlaster.androidx.datastore`, retaining their
artifact names. Versions are `3.0.3-mingw-SNAPSHOT`, `2.7.1-mingw-SNAPSHOT`, and
`1.3.0-alpha11-mingw-SNAPSHOT`, respectively.

Add the [Central Portal snapshot repository](https://central.sonatype.org/publish/publish-portal-snapshots/):

```kotlin
repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
        content { includeGroupByRegex("moe\\.tlaster\\.androidx\\..*") }
    }
    google()
    mavenCentral()
}
```

In a MinGW source set:

```kotlin
implementation("moe.tlaster.androidx.room3:room3-runtime:3.0.3-mingw-SNAPSHOT")
implementation("moe.tlaster.androidx.room3:room3-paging:3.0.3-mingw-SNAPSHOT")
implementation("moe.tlaster.androidx.sqlite:sqlite-bundled:2.7.1-mingw-SNAPSHOT")
implementation("moe.tlaster.androidx.datastore:datastore-core-okio:1.3.0-alpha11-mingw-SNAPSHOT")
```

Use the official `androidx.room3:room3-compiler:3.0.3` with KSP. These snapshots
include common metadata and `mingwX64` only; other platform binaries are not
published under this namespace. Avoid combining upstream and fork storage
libraries on the same target because their Kotlin packages are identical.
Snapshots can be replaced and are subject to Sonatype's retention policy.

The [snapshot workflow](.github/workflows/mingw-snapshot.yml) runs manually or
when a `mingw-snapshot-*` tag is pushed. It stages all 18 root/target publications,
checks metadata and internal coordinates, compiles an independent Maven consumer,
and runs its Unicode database/DataStore test on Windows before uploading.
Publishing uses only `OSSRH_USERNAME` and `OSSRH_PASSWORD`, containing a
**Central Portal user token**; the namespace must have snapshots enabled. Like
[mfm-multiplatform](https://github.com/Tlaster/mfm-multiplatform/blob/master/.github/workflows/ci.yml),
these snapshots are unsigned. Signing secrets are reserved for future release
publication and are not passed to this workflow.

To validate publication locally without credentials:

```sh
cd playground-projects/storage-playground
./gradlew -I ../../development/mingw/publish.init.gradle stageMingwSnapshot \
  -Pandroidx.enabled.kmp.target.platforms=-MAC,-LINUX,-ANDROID_NATIVE,-JS,-WASM \
  --no-configuration-cache --no-configure-on-demand
python3 ../../development/mingw/verify_publications.py \
  "$OUT_DIR/storage-playground/build/mingw-snapshot-repository"
```

The publishing overlay is opt-in and leaves normal AndroidX build coordinates
unchanged. It omits AndroidX API-history documentation, which would otherwise
compile every platform. The workflow also disables unpublished native targets
when generating common metadata. Release publication is not configured.
