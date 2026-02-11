# Replace kotlinx-cli with Clikt

Replace kotlinx-cli argument parsing with Clikt 5.0.2 in the single-file launcher.

- Files involved:
  - Modify: `build.gradle`
  - Modify: `src/main/kotlin/org/netxms/web/Launcher.kt`
  - Modify: `CLAUDE.md`
- Related patterns: single-file architecture, top-level main() entry point
- Dependencies: `com.github.ajalt.clikt:clikt:5.0.2` (replaces `kotlinx-cli-jvm:0.3.5`)

## Approach

- **Testing approach**: manual (no existing tests for CLI parsing; project has zero test files currently)
- The migration is straightforward since all code is in one file
- Clikt's built-in `versionOption()` replaces the manual `--version` boolean flag logic
- Complete each task fully before moving to the next

## Task 1 - Swap dependency in build.gradle

**Files:**
- Modify: `build.gradle`

- [x] Replace `org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.5` with `com.github.ajalt.clikt:clikt:5.0.2`
- [x] Run `./gradlew dependencies` to verify resolution

## Task 2 - Rewrite CLI parsing in Launcher.kt

**Files:**
- Modify: `src/main/kotlin/org/netxms/web/Launcher.kt`

- [x] Create a `Launcher` class extending `CliktCommand` with `name = "netxms-web-launcher"`
- [x] Convert all `parser.option()` calls to Clikt `option()` delegates with appropriate types (`int()`, `flag()`, `default()`)
- [x] Use Clikt's `versionOption()` in the init block, reading from `/version.properties` resource
- [x] Move the keystore password env-var resolution and `startServer()` call into the `override fun run()` method
- [x] Update `main()` to `fun main(args: Array<String>) = Launcher().main(args)`
- [x] Remove all kotlinx-cli imports
- [x] Preserve `--disableSni` as a `flag()` (boolean flag, false by default)
- [x] Keep `--war` and `--accessLog` as nullable string options (no default)
- [x] Run `./gradlew build` to verify compilation and fat JAR assembly

## Task 3 - Update documentation

**Files:**
- Modify: `CLAUDE.md`

- [x] Replace kotlinx-cli references with Clikt in the Dependencies section
- [x] Remove the kotlinx-cli gotcha entry (obsolete version warning no longer applies)

## Verification

- [x] Run `java -jar build/libs/launcher.jar --version` and confirm version output
- [x] Run `java -jar build/libs/launcher.jar --help` and confirm help text
- [x] Run `./gradlew build` with clean build to verify fat JAR

## Wrap-up

- [x] Update CLAUDE.md if internal patterns changed
- [x] Move this plan to `docs/plans/completed/`
