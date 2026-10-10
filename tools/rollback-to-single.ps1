# Rollback: restore the single-jar "known good" build (branch f8f074fa).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools\rollback-to-single.ps1
$ErrorActionPreference = 'Continue'   # git 会往 stderr 写进度 ⇒ 不能用 Stop（★上一版就是这里中断的 ✓）
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
Write-Host '[rollback] switching working tree to the single-jar known-good commit f8f074fa ...'
git checkout f8f074fa -- . 2>&1 | Out-Null
git -c advice.detachedHead=false checkout f8f074fa 2>&1 | Out-Null
git checkout f8f074fa 2>&1 | Select-Object -Last 1
Write-Host '[rollback] removing the three split jars from both instances ...'
foreach ($d in @('D:\tex\.minecraft\versions\1.20.1-Forge_47.4.26\mods', 'D:\tex\.minecraft\versions\[NL]NewLifestyle V0.1.7\mods'.Replace(' V0.1.7', '崭新世界 V0.1.7'))) {
    if (-not (Test-Path -LiteralPath $d)) { continue }
    foreach ($n in @('tinkersnewlife_cursed', 'tinkersnewlife_imagination', 'tinkersnewlife_apostle')) {
        Get-ChildItem -LiteralPath $d -Filter ($n + '-*.jar') -ErrorAction SilentlyContinue | ForEach-Object {
            Remove-Item -LiteralPath $_.FullName -Force
            Write-Host ('  removed ' + $_.Name)
        }
    }
}
Write-Host '[rollback] deploying the single jar ...'
powershell -NoProfile -ExecutionPolicy Bypass -File tools\deploy.ps1 -IncludeNL
Write-Host '[rollback] done. The split work stays on branch split-v2.'