# test-prd003-preferences.ps1
# Smoke tests for PRD-003: User-Level Model Preferences
#
# Prerequisites:
#   - Docker (nginx + MySQL) running:  docker compose up -d openrouter-proxy openrouter-mysql
#   - Spring Boot running:             run-app.bat
#   - A test user registered (or reuse admin) with an OpenRouter API key saved
#
# Usage:
#   .\test-prd003-preferences.ps1
#   .\test-prd003-preferences.ps1 -BaseUrl "http://localhost:8080" -UserEmail "test@example.com" -UserPassword "Test@2026!"

param(
    [string]$BaseUrl      = "http://localhost:8080",
    [string]$AdminEmail   = "admin@openrouter.local",
    [string]$AdminPass    = "Admin@2026!",
    [string]$UserEmail    = "prd003test@example.com",
    [string]$UserPassword = "Test@2026!"
)

$ErrorActionPreference = "Stop"

# ── Helpers ───────────────────────────────────────────────────────────────────

function Invoke-Api {
    param($Method, $Path, $Body = $null, $Token = $null, [int]$ExpectStatus = 200)
    $headers = @{ "Content-Type" = "application/json" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $uri = "$BaseUrl$Path"
    try {
        $params = @{ Method = $Method; Uri = $uri; Headers = $headers; UseBasicParsing = $true }
        if ($Body) { $params["Body"] = ($Body | ConvertTo-Json -Compress) }
        $resp = Invoke-WebRequest @params
        return @{ Status = [int]$resp.StatusCode; Body = ($resp.Content | ConvertFrom-Json) }
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        $content = $null
        try { $content = $_.ErrorDetails.Message | ConvertFrom-Json } catch {}
        return @{ Status = $status; Body = $content }
    }
}

function Pass { param($msg) Write-Host "  [PASS] $msg" -ForegroundColor Green }
function Fail { param($msg) Write-Host "  [FAIL] $msg" -ForegroundColor Red; $script:failures++ }
function Section { param($msg) Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

$script:failures = 0

# ── Setup: ensure test user exists ────────────────────────────────────────────

Section "Setup"

# Register test user (ignore 409 if already exists)
$reg = Invoke-Api -Method POST -Path "/api/auth/register" -Body @{ email = $UserEmail; password = $UserPassword }
if ($reg.Status -eq 200 -or $reg.Status -eq 201) {
    Write-Host "  Registered test user: $UserEmail"
} elseif ($reg.Status -eq 409) {
    Write-Host "  Test user already exists: $UserEmail"
} else {
    Fail "Register failed: $($reg.Status)"
}

# Login test user
$login = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email = $UserEmail; password = $UserPassword }
if ($login.Status -ne 200) { Fail "User login failed: $($login.Status)"; exit 1 }
$userToken = $login.Body.token
Write-Host "  User logged in."

# Login admin
$adminLogin = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email = $AdminEmail; password = $AdminPass }
if ($adminLogin.Status -ne 200) { Fail "Admin login failed: $($adminLogin.Status)"; exit 1 }
$adminToken = $adminLogin.Body.token
Write-Host "  Admin logged in."

# ── Smoke 1: New user sees all admin-enabled models ───────────────────────────

Section "Smoke 1: New user — no preference rows → all admin-enabled returned"

$r = Invoke-Api -Method GET -Path "/api/user/models" -Token $userToken
if ($r.Status -ne 200) { Fail "GET /api/user/models returned $($r.Status)"; exit 1 }

$models = $r.Body.models
$totalAdminEnabled = $r.Body.totalAdminEnabled
$totalUserEnabled  = $r.Body.totalUserEnabled

if ($models.Count -gt 0) { Pass "models list non-empty ($($models.Count) entries)" } else { Fail "models list is empty" }
if ($totalAdminEnabled -gt 0) { Pass "totalAdminEnabled=$totalAdminEnabled" } else { Fail "totalAdminEnabled=0, expected >0" }
if ($totalUserEnabled -eq $totalAdminEnabled) { Pass "totalUserEnabled=$totalUserEnabled equals totalAdminEnabled (default all-on)" } else { Fail "totalUserEnabled=$totalUserEnabled != totalAdminEnabled=$totalAdminEnabled for new user" }

$allEffective = ($models | Where-Object { $_.adminEnabled -eq $true } | ForEach-Object { $_.effectivelyEnabled }) -notcontains $false
if ($allEffective) { Pass "All admin-enabled models are effectivelyEnabled for new user" } else { Fail "Some admin-enabled model is not effectivelyEnabled for new user" }

# Pick a model to toggle (first admin-enabled)
$targetModel = $models | Where-Object { $_.adminEnabled -eq $true } | Select-Object -First 1
Write-Host "  Using model: $($targetModel.modelId) (id=$($targetModel.id))"

# ── Smoke 2: Toggle a model off ───────────────────────────────────────────────

Section "Smoke 2: Toggle model off → excluded from effectivelyEnabled"

$t1 = Invoke-Api -Method PUT -Path "/api/user/models/$($targetModel.id)/toggle" -Token $userToken
if ($t1.Status -ne 200) { Fail "First toggle returned $($t1.Status)"; exit 1 }
if ($t1.Body.userEnabled -eq $false) { Pass "userEnabled=false after first toggle" } else { Fail "Expected userEnabled=false, got $($t1.Body.userEnabled)" }
if ($t1.Body.effectivelyEnabled -eq $false) { Pass "effectivelyEnabled=false after first toggle" } else { Fail "Expected effectivelyEnabled=false" }

# Verify list reflects the toggle
$r2 = Invoke-Api -Method GET -Path "/api/user/models" -Token $userToken
$disabledEntry = $r2.Body.models | Where-Object { $_.id -eq $targetModel.id }
if ($disabledEntry.effectivelyEnabled -eq $false) { Pass "GET /api/user/models reflects disabled state" } else { Fail "Model still appears effectivelyEnabled after toggle" }
if ($r2.Body.totalUserEnabled -eq ($totalUserEnabled - 1)) { Pass "totalUserEnabled decremented by 1" } else { Fail "totalUserEnabled=$($r2.Body.totalUserEnabled), expected $($totalUserEnabled - 1)" }

# ── Smoke 3: Toggle idempotent (toggle twice → restored) ─────────────────────

Section "Smoke 3: Toggle twice → original state restored"

$t2 = Invoke-Api -Method PUT -Path "/api/user/models/$($targetModel.id)/toggle" -Token $userToken
if ($t2.Status -ne 200) { Fail "Second toggle returned $($t2.Status)" }
if ($t2.Body.userEnabled -eq $true) { Pass "userEnabled=true after second toggle (restored)" } else { Fail "Expected userEnabled=true after second toggle, got $($t2.Body.userEnabled)" }

$r3 = Invoke-Api -Method GET -Path "/api/user/models" -Token $userToken
if ($r3.Body.totalUserEnabled -eq $totalUserEnabled) { Pass "totalUserEnabled restored to original ($totalUserEnabled)" } else { Fail "totalUserEnabled=$($r3.Body.totalUserEnabled), expected $totalUserEnabled" }

# ── Smoke 4: Admin disables a model → disappears for user ────────────────────

Section "Smoke 4: Admin disables model → user no longer sees it as effectivelyEnabled"

# Admin toggles the model off via the admin endpoint
$adminToggle = Invoke-Api -Method PUT -Path "/api/admin/models/$($targetModel.id)/toggle" -Token $adminToken
if ($adminToggle.Status -ne 200) { Fail "Admin toggle returned $($adminToggle.Status)"; exit 1 }
Write-Host "  Admin toggled model. New admin-enabled state: $($adminToggle.Body.enabled)"

if ($adminToggle.Body.enabled -eq $false) {
    # Model is now admin-disabled — verify user sees it as not effectivelyEnabled
    $r4 = Invoke-Api -Method GET -Path "/api/user/models" -Token $userToken
    $entry = $r4.Body.models | Where-Object { $_.id -eq $targetModel.id }
    if ($entry.adminEnabled -eq $false) { Pass "adminEnabled=false visible to user" } else { Fail "User still sees adminEnabled=true" }
    if ($entry.effectivelyEnabled -eq $false) { Pass "effectivelyEnabled=false (admin-disabled overrides user preference)" } else { Fail "effectivelyEnabled should be false for admin-disabled model" }

    # Smoke 5: User cannot toggle an admin-disabled model
    Section "Smoke 5: User cannot toggle admin-disabled model → 400"
    $badToggle = Invoke-Api -Method PUT -Path "/api/user/models/$($targetModel.id)/toggle" -Token $userToken
    if ($badToggle.Status -eq 400) { Pass "Toggle of admin-disabled model returns 400 as expected" } else { Fail "Expected 400, got $($badToggle.Status)" }

    # Restore: admin re-enables the model
    $restore = Invoke-Api -Method PUT -Path "/api/admin/models/$($targetModel.id)/toggle" -Token $adminToken
    Write-Host "  Admin restored model. enabled=$($restore.Body.enabled)"
} else {
    Write-Host "  Model was already disabled; admin toggle re-enabled it. Skipping admin-disable assertions."
    Write-Host "  Re-run after verifying model state."
}

# ── Smoke 6: Unknown model ID → 404 ──────────────────────────────────────────

Section "Smoke 6: Toggle unknown model_config id → 404"

$r5 = Invoke-Api -Method PUT -Path "/api/user/models/99999/toggle" -Token $userToken
if ($r5.Status -eq 404) { Pass "Toggle unknown id returns 404" } else { Fail "Expected 404, got $($r5.Status)" }

# ── Smoke 7: ROLE_ADMIN sees all admin-enabled models regardless of prefs ─────

Section "Smoke 7: ROLE_ADMIN sees all admin-enabled models (preference bypass)"

$adminModels = Invoke-Api -Method GET -Path "/api/user/models" -Token $adminToken
if ($adminModels.Status -ne 200) { Fail "Admin GET /api/user/models returned $($adminModels.Status)" }

$adminEffective = $adminModels.Body.models | Where-Object { $_.adminEnabled -eq $true }
$adminAllEffective = ($adminEffective | ForEach-Object { $_.effectivelyEnabled }) -notcontains $false
if ($adminAllEffective) { Pass "Admin sees all admin-enabled models as effectivelyEnabled" } else { Fail "Some admin-enabled model is not effectivelyEnabled for ROLE_ADMIN" }
if ($adminModels.Body.totalUserEnabled -eq $adminModels.Body.totalAdminEnabled) { Pass "Admin: totalUserEnabled == totalAdminEnabled (no preference filtering)" } else { Fail "Admin totalUserEnabled=$($adminModels.Body.totalUserEnabled) != totalAdminEnabled=$($adminModels.Body.totalAdminEnabled)" }

# ── Smoke 8: GET /api/user/models/{id}/status ────────────────────────────────

Section "Smoke 8: GET /api/user/models/{id}/status returns correct shape"

$status = Invoke-Api -Method GET -Path "/api/user/models/$($targetModel.id)/status" -Token $userToken
if ($status.Status -ne 200) { Fail "Status endpoint returned $($status.Status)" }
if ($null -ne $status.Body.modelId) { Pass "status.modelId present: $($status.Body.modelId)" } else { Fail "status.modelId missing" }
if ($null -ne $status.Body.adminEnabled) { Pass "status.adminEnabled present" } else { Fail "status.adminEnabled missing" }
if ($null -ne $status.Body.userEnabled) { Pass "status.userEnabled present" } else { Fail "status.userEnabled missing" }
if ($null -ne $status.Body.effectivelyEnabled) { Pass "status.effectivelyEnabled present" } else { Fail "status.effectivelyEnabled missing" }

# ── Summary ───────────────────────────────────────────────────────────────────

Write-Host ""
if ($script:failures -eq 0) {
    Write-Host "All smoke tests passed." -ForegroundColor Green
} else {
    Write-Host "$($script:failures) smoke test(s) FAILED." -ForegroundColor Red
    exit 1
}
