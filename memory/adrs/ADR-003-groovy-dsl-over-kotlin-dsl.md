# ADR-003: Use Groovy DSL (build.gradle) instead of Kotlin DSL (build.gradle.kts)

**Date:** 2026-05-29
**Status:** Accepted

## Context

Gradle supports two DSLs for build scripts: Groovy and Kotlin.
The project initially used Kotlin DSL (`build.gradle.kts`) as it is the modern recommended approach.

## Problem

Gradle 8.14's bundled Kotlin compiler (2.0.x) cannot parse Java 25's version string (`25.0.2`).
Error: `java.lang.IllegalArgumentException: 25.0.2` at `JavaVersion.parse()`.

Gradle 8.12 (Groovy DSL) also failed with: `Unsupported class file major version 69`
(Java 25 = class file major version 69, Groovy compiler in 8.12/8.14 doesn't support it).

## Decision

Switch to Groovy DSL (`build.gradle` + `settings.gradle`).

## Reasons

- Groovy DSL build scripts are interpreted differently — they don't go through the Kotlin compiler version parsing issue.
- Build logic is identical between Groovy and Kotlin DSL for our use case.
- Unblocks the build immediately without waiting for a Gradle release that supports Java 25 runtime.

## Trade-offs

- Kotlin DSL provides better IDE autocompletion and type safety — we lose that.
- If/when Gradle fully supports Java 25 as both runtime and Kotlin DSL target, consider migrating back.

## When to revisit

Check Gradle release notes when upgrading the wrapper. If Kotlin DSL works with Java 25 runtime, migrate by renaming `build.gradle` → `build.gradle.kts` and adjusting syntax.
