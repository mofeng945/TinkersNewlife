# 星象仪 7 张月相子模型（moon_1..7）生成器
# 基模型 planetarium.json（月相 0 = 满月）已手写；本脚本只补它 overrides 指向的 7 个子模型。
# 每个子模型 = parent item/generated + layer0 指向对应月相贴图（与「心」的 shade 模型同构）
$dir = Join-Path $PSScriptRoot '..\src\main\resources\assets\tinkersnewlife\models\item'
$dir = [System.IO.Path]::GetFullPath($dir)
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

for ($i = 1; $i -le 7; $i++) {
  $path = Join-Path $dir ("planetarium_moon_{0}.json" -f $i)
  $json = @"
{
    "parent": "minecraft:item/generated",
    "textures": {
        "layer0": "tinkersnewlife:item/planetarium/moon_$i"
    }
}
"@
  # 统一 LF 行尾 + 无 BOM（与仓库其它 JSON 一致）
  $text = ($json -replace "`r`n", "`n")
  [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
  Write-Host ("写出 planetarium_moon_$i.json")
}
Write-Host "完成：7 个子模型"
