# ============================================================
# test-models.ps1 - Tests each model against the nginx proxy
# Usage: .\test-models.ps1
# ============================================================

# Calls nginx proxy directly (bypasses Spring Boot whitelist) for testing
$ProxyUrl = "http://localhost:8081/api/v1/chat/completions"

$Models = @(
    "openrouter/owl-alpha",
    "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
    "poolside/laguna-xs.2:free",
    "poolside/laguna-m.1:free",
    "deepseek/deepseek-v4-flash:free",
    "moonshotai/kimi-k2.6:free",
    "google/gemma-4-26b-a4b-it:free",
    "google/gemma-4-31b-it:free",
    "google/lyria-3-pro-preview",
    "google/lyria-3-clip-preview",
    "nvidia/nemotron-3-super-120b-a12b:free",
    "minimax/minimax-m2.5:free",
    "openrouter/free",
    "liquid/lfm-2.5-1.2b-thinking:free",
    "liquid/lfm-2.5-1.2b-instruct:free",
    "nvidia/nemotron-3-nano-30b-a3b:free",
    "nvidia/nemotron-nano-12b-v2-vl:free",
    "qwen/qwen3-next-80b-a3b-instruct:free",
    "nvidia/nemotron-nano-9b-v2:free",
    "openai/gpt-oss-120b:free",
    "openai/gpt-oss-20b:free",
    "z-ai/glm-4.5-air:free",
    "qwen/qwen3-coder:free",
    "cognitivecomputations/dolphin-mistral-24b-venice-edition:free",
    "meta-llama/llama-3.3-70b-instruct:free",
    "meta-llama/llama-3.2-3b-instruct:free",
    "nousresearch/hermes-3-llama-3.1-405b:free"
)

$Results = @()

foreach ($Model in $Models) {
    $Body = @{
        model    = $Model
        messages = @(@{ role = "user"; content = "Hi" })
    } | ConvertTo-Json -Depth 5

    try {
        $Response = Invoke-RestMethod `
            -Uri $ProxyUrl `
            -Method POST `
            -ContentType "application/json" `
            -Body $Body `
            -ErrorAction Stop

        $Results += [PSCustomObject]@{ Model = $Model; Status = "OK" }
        Write-Host "OK   $Model" -ForegroundColor Green

    } catch {
        $StatusCode = $_.Exception.Response.StatusCode.value__
        $Reason = "unknown"
        try {
            $Stream = $_.Exception.Response.GetResponseStream()
            $Reader = New-Object System.IO.StreamReader($Stream)
            $Raw = $Reader.ReadToEnd()
            $Parsed = $Raw | ConvertFrom-Json
            if ($Parsed.error.message) {
                $Reason = $Parsed.error.message
            } elseif ($Parsed.message) {
                $Reason = $Parsed.message
            }
        } catch {}

        $Results += [PSCustomObject]@{ Model = $Model; Status = "FAIL ($StatusCode): $Reason" }
        Write-Host "FAIL $Model" -ForegroundColor Red
        Write-Host "     $Reason" -ForegroundColor DarkRed
    }

    Start-Sleep -Milliseconds 500
}

Write-Host ""
Write-Host "===== SUMMARY =====" -ForegroundColor Cyan
$Results | Format-Table -AutoSize -Wrap
