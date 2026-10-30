# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Jetty 12 (EE10) wrapper that bundles the NetXMS web management console (NXMC) into a single runnable fat JAR with built-in HTTPS support. The entire application is a single Kotlin file (`src/main/kotlin/org/netxms/web/Launcher.kt`).

## Build & Run

```bash
./gradlew build                              # build fat JAR
./gradlew clean build -Pversion=3.10.320-285 # with version override (for *DistTar tasks)
./gradlew test                               # JUnit 6

# run (WAR bundled at build time or specified via --war)
java -jar build/libs/launcher.jar
java -jar build/libs/launcher.jar --httpsPort 8443 --httpPort 0 --keystore server.pkcs12 --keystorePassword NXMC_KEYSTORE_PASSWD
```

Platform distribution tarballs: `solarisDistTar`, `macosx_aarch64DistTar`, `aixDistTar` (DistZip tasks are disabled).

## WAR Bundling

Place `nxmc.war` (or `api.war`) in the project root before building — it gets unpacked into `/META-INF/webapps/nxmc` inside the fat JAR. Without a bundled WAR, pass `--war <path>` at runtime.

## Key Constraints

- **Jetty 12.1 (EE10)** — uses `jakarta.servlet` namespace via `jetty-ee10-webapp`.
- **JVM target: Java 17** (set via `jvmToolchain` in build.gradle).
- **Kotlin 2.4** with official code style (`gradle.properties`).

## Architecture

Single entry point `LauncherKt.main()` that:
1. Parses CLI args via Clikt
2. Configures SLF4J-Simple logging
3. Creates Jetty `Server` with HTTP and/or HTTPS connectors
4. Applies security headers customizer (CSP, HSTS, X-Frame-Options, X-Content-Type-Options)
5. Installs `GenericErrorHandler` on server and context (error bodies carry only the status and its standard reason, details go to the log)
6. Sets up `WebAppContext` from bundled WAR or `--war` path
7. Wraps handler chain: `MethodFilterHandler` (GET/HEAD/POST only) → `CompressionHandler` (gzip) → `WebAppContext`
8. Starts server

Keystore password resolution: if the `--keystorePassword` value matches an environment variable name, the env var's value is used instead.

## Dependencies

Clikt, SLF4J (api + simple), Jetty 12 (server, ee10-servlet, ee10-webapp, compression-server, compression-gzip), Hoplite (core + toml — config parsing). JUnit 6 for tests. Kotlin stdlib is auto-included by Kotlin 2.x plugin. Versions are defined at the top of `build.gradle`.

## Gotchas

- Toolchain provisioning uses the foojay resolver plugin (`settings.gradle`) — required so Gradle can download JDK 17 when it isn't locally installed (auto-provisioning without a toolchain repo fails in Gradle 10).
- **Groovy DSL in Gradle 10** will require assignment syntax (`group = 'x'` not `group 'x'`).
