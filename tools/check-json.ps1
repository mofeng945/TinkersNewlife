# Strict JSON validation for src/main/resources (run before committing).
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\check-json.ps1
#
# NOTE: this launcher is intentionally PURE ASCII. All explanation, including why a
# strict parser is required and what the extra semantic checks are, lives in
# tools/check-json-strict.js (UTF-8, no BOM).
#
# Background (short): PowerShell's own JSON parsers are too lax -- they accept
# unquoted keys, which Mantle/Gson rejects, silently voiding a whole file. So we
# shell out to Node's JSON.parse. The strict checker additionally verifies that
# every minecraft:crafting_shaped recipe fits in 3x3 -- Create raises
# ShapedRecipe.setCraftingSize, so an oversized recipe LOADS fine and only fails
# later as an easy-to-miss JEI error, leaving the item uncraftable.
#
# Empty-key blockstates ("variants": { "": {...} }) are legal Minecraft JSON but
# the strict parser rejects them; they are explicitly skipped, as before.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$res  = Join-Path $root 'src\main\resources'
$js   = Join-Path $PSScriptRoot 'check-json-strict.js'

$node = Get-Command node -ErrorAction SilentlyContinue
if ($node -and (Test-Path -LiteralPath $js)) {
    & node $js $res
    exit $LASTEXITCODE
}

Write-Host '!! node.exe or tools\check-json-strict.js not found -- falling back to the lax parser.';
Write-Host '!! (illegal JSON may slip through, and the 3x3 recipe check will NOT run.)';
Add-Type -AssemblyName System.Web.Extensions
$bad = 0; $checked = 0; $skipped = 0
$ser = New-Object System.Web.Script.Serialization.JavaScriptSerializer
Get-ChildItem -Recurse -File -Path $res -Filter '*.json' | ForEach-Object {
    $rel = $_.FullName.Replace($res + '\', '')
    $raw = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
    $checked++
    try { $ser.DeserializeObject($raw) | Out-Null }
    catch {
        if ($raw -match '"variants"\s*:\s*\{\s*""') { $skipped++; return }
        Write-Host ('  BAD  ' + $rel + '  ->  ' + $_.Exception.Message)
        $bad++
    }
}
Write-Host ('checked ' + $checked + ', bad ' + $bad + ', skipped(empty-key blockstates) ' + $skipped)
if ($bad -gt 0) { exit 1 }
exit 0
