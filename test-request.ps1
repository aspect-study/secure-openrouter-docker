# ============================================================
# test-request.ps1 — smoke test against the local proxy (Windows PowerShell)
# Usage: .\test-request.ps1
# ============================================================

$Model = if ($env:DEFAULT_MODEL) { $env:DEFAULT_MODEL } else { "nvidia/nemotron-nano-9b-v2:free" }
$ProxyUrl = "http://localhost:8081/api/v1/chat/completions"

Write-Host "Testing proxy at: $ProxyUrl"
Write-Host "Model: $Model"
Write-Host "---"

$Body = @{
    model    = $Model
    messages = @(
        @{ role = "user"; content = "Say hello in one sentence." }
    )
} | ConvertTo-Json -Depth 5

try {
    $Response = Invoke-RestMethod -Uri $ProxyUrl `
        -Method POST `
        -ContentType "application/json" `
        -Body $Body
    $Response | ConvertTo-Json -Depth 10
} catch {
    Write-Error "Request failed: $_"
}
