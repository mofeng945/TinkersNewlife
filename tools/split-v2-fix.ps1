<#
  §1268 修拆分版：给三个新包各加 @Mod 入口 ＋ 补齐 mods.toml 字段。
  ⚠ 为什么必须：mods.toml 写的是 modLoader="javafml"，而这种包**必须**有 @Mod 类。
     §1267 大爆炸版"一加载就断、日志停在 Found 25 dependencies 之后"的头号嫌疑就是这个。
#>
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

$mods = @(
    @{ dir = 'cursed';      cls = 'TinkersNewlifeCursed';      id = 'tinkersnewlife_cursed';      sub = 'cursed';      cn = '匠魂新生·咒术';     en = "Tinker's Newlife: Cursed" },
    @{ dir = 'imagination'; cls = 'TinkersNewlifeImagination'; id = 'tinkersnewlife_imagination'; sub = 'imagination'; cn = '匠魂新生·奇想';     en = "Tinker's Newlife: Imagination" },
    @{ dir = 'apostle';     cls = 'TinkersNewlifeApostle';     id = 'tinkersnewlife_apostle';     sub = 'apostle';     cn = '匠魂新生·使徒增强'; en = "Tinker's Newlife: Apostle" }
)

foreach ($m in $mods) {
    # ── ① @Mod 入口类
    $dir = Join-Path $m.dir ('src\main\java\com\mofengbaizhi\tinkersnewlife\' + $m.sub)
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $java = @"
package com.mofengbaizhi.tinkersnewlife.$($m.sub);

import net.minecraftforge.fml.common.Mod;

/**
 * ⭐⭐⭐⭐ §1268 <b>$($m.cn) 的 `@Mod` 入口</b> ✗✗
 *
 * <p>⚠ 为什么**必须**有 ✗：⭐ `mods.toml` 里写的是 ⭐ `modLoader="javafml"` ✓
 * ⭐ 而这种包 ⭐ **必须有一个 `@Mod` 类** ✓ ⇒ ⭐ 三个新包原来一个都没有 ✓
 * ⇒ ⭐ 这就是 §1267「⭐ 大爆炸版一加载就断 ✗ ⭐ 日志停在 `Found 25 dependencies…` 之后一行都没有」的
 * ⭐ **头号嫌疑** ✓ ✓。
 *
 * <p>⭐ 本类**不做任何注册** ✗ —— ⭐ 所有 `DeferredRegister` 仍由 `common` 侧各 hub 的静态块
 * 挂到 **mod 总线**上 ✓（⭐ 命名空间仍是 `tinkersnewlife` ✗ ⭐ 用户拍板保留 ✓）
 * ⇒ ⭐ 三个新包运行时提供的只是**额外的类** ✓（⭐ 所以它们与 `common` **必须同时存在** ✓）。
 *
 * <p>⚠ 本包自己的 `@SubscribeEvent` 由 ⭐ `@Mod.EventBusSubscriber` 自动挂 ✓ ⭐ 不用手写 ✓。
 */
@Mod($($m.cls).MOD_ID)
public final class $($m.cls) {

    /** ⭐ 本包的 mod id ✓（⭐ 必须与 `mods.toml` 的 `[[mods]] modId` 一字不差 ✓） */
    public static final String MOD_ID = "$($m.id)";

    public $($m.cls)() {
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[$($m.cn)] 已加载 ✓");
    }
}
"@
    [System.IO.File]::WriteAllText((Join-Path $repo ($dir + '\' + $m.cls + '.java')), $java, (New-Object System.Text.UTF8Encoding($false)))

    # ── ② 重写 mods.toml（★补齐 Forge 需要的 description／authors ✓）
    $toml = @"
modLoader="javafml"
loaderVersion="[47,)"
license="All Rights Reserved"

[[mods]]
modId="$($m.id)"
version="`${mod_version}"
displayName="$($m.cn)"
authors="$($mod_authors)"
description='''$($m.en)'''

[[dependencies.$($m.id)]]
    modId="forge"
    mandatory=true
    versionRange="[47,)"
    ordering="NONE"
    side="BOTH"

[[dependencies.$($m.id)]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.20.1,1.21)"
    ordering="NONE"
    side="BOTH"

[[dependencies.$($m.id)]]
    modId="tinkersnewlife"
    mandatory=true
    versionRange="[1.0.1,2.0.0)"
    ordering="AFTER"
    side="BOTH"
"@
    [System.IO.File]::WriteAllText((Join-Path $repo ($m.dir + '\src\main\resources\META-INF\mods.toml')), $toml, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host ('  ✓ ' + $m.cls + ' ＋ mods.toml（★补 description／authors／tinkersnewlife 依赖 ✓）')
}

Write-Host '=== 构建 ==='
cmd /c ".\gradlew.bat build --console=plain > tools\_tmp_fix.txt 2>&1"
$B = [System.IO.File]::ReadAllLines((Resolve-Path 'tools\_tmp_fix.txt').Path, [System.Text.Encoding]::GetEncoding(936))
$ok = @($B | Where-Object { $_ -match 'BUILD SUCCESSFUL' }).Count -gt 0
$errs = @($B | Where-Object { $_ -match '\.java:\d+:' -and $_ -match ([char]0x9519 + [char]0x8bef) })
Write-Host ("  BUILD SUCCESSFUL = $ok   compile errors = $($errs.Count)")
for ($i = 0; $i -lt [Math]::Min(6, $errs.Count); $i++) { '  ' + $errs[$i].Trim().Substring(0, [Math]::Min(150, $errs[$i].Trim().Length)) }
Remove-Item tools\_tmp_fix.txt -Force -ErrorAction SilentlyContinue
if (-not $ok) { exit 1 }

Write-Host '=== 核对三个新包 ==='
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($m in $mods) {
    $f = Get-ChildItem (Join-Path $m.dir 'build\libs') -File -Filter '*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $f) { Write-Host ("  X $($m.dir) no jar"); continue }
    $z = [System.IO.Compression.ZipFile]::OpenRead($f.FullName)
    $hasMod = @($z.Entries | Where-Object { $_.FullName -match ($m.cls + '\.class$') }).Count
    $className = $m.cls
    $cm = @($z.Entries | Where-Object { $_.FullName -match ('tinkersnewlife/' + $m.sub + '/' + $className + '\.class$') }).Count
    $e = $z.Entries | Where-Object { $_.FullName -eq 'META-INF/mods.toml' }
    $sr = New-Object System.IO.StreamReader($e.Open()); $txt = $sr.ReadToEnd(); $sr.Close()
    $z.Dispose()
    $hasDesc = [bool]($txt -match 'description=')
    $hasAuthors = [bool]($txt -match 'authors=')
    Write-Host ("  " + $m.dir.PadRight(13) + $f.Length.ToString().PadLeft(9) + " B   @Mod=" + $cm + "   description=" + $hasDesc + "   authors=" + $hasAuthors)
}
exit 0
