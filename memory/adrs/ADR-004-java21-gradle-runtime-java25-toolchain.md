# ADR-004: Run Gradle on Java 21, compile app with Java 25 toolchain

**Date:** 2026-05-29
**Status:** Accepted

## Context

Gradle 8.14 supports Java 24 as the maximum runtime JVM. Java 25 as the Gradle daemon JVM causes build failures. However, the application must be compiled with Java 25 to use Java 25 language features and produce Java 25 bytecode.

## Decision

- Run the Gradle daemon on Java 21 (LTS)
- Use Gradle's toolchain feature to compile source code with Java 25

```groovy
// build.gradle
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

## How it works

Gradle's Java toolchain feature is separate from the Gradle runtime JVM. When `languageVersion = 25` is specified, Gradle:
1. Detects the Java 25 JDK at `C:\Program Files\Java\jdk-25`
2. Uses it for `javac` (compilation) and `java` (test execution)
3. The Gradle daemon itself continues running on Java 21

The compiled bytecode is Java 25 (class file version 69). The application runs on Java 25 at runtime.

## Result

- `gradlew.bat --version` → Launcher JVM: Java 21
- `javac` during build → Java 25
- `java -jar openrouter-gateway.jar` → requires Java 25 JRE

## Practical implication

Always run `switch-java-version.bat 21` before running Gradle commands.
Always run `switch-java-version.bat 25` before running the JAR directly.

`run-app.bat` handles this automatically.

## When to revisit

When a Gradle version officially supports Java 25 as the runtime JVM, update `gradle-wrapper.properties` and remove the Java 21 requirement from `run-app.bat` and `switch-java-version.bat` calls.
