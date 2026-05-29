@echo off
:: ============================================================
:: run-app.bat — Load .env and start the Spring Boot app
:: Usage: run-app.bat
:: ============================================================

echo [INFO] Loading environment variables from .env...

:: Load each non-comment, non-empty line from .env as an env var
for /f "usebackq tokens=1,2 delims==" %%i in (`findstr /v "^#" .env`) do (
    if not "%%i"=="" if not "%%j"=="" set %%i=%%j
)

echo [INFO] Switching to Java 21 for Gradle runtime...
call switch-java-version.bat 21

echo [INFO] Starting Spring Boot application...
cd app
call gradlew.bat bootRun

cd ..
