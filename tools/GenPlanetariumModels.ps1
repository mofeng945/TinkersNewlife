# 星象仪 7 张"星象图子模型"生成器（两层结构：layer0 = 底盘 / layer1 = 星象图）
# 基模型 planetarium.json（月相 0）已手写；本脚本只补它 overrides 指向的 7 个子模型。
# ⚠ 只有 layer1 的路径随月相变 layer0 恒为 base —— "底盘不变"在文件层面也是真的
$dir = Join-Path $PSScriptRoot '..\src\main\resources\assets\tinkersnewlife\models\item'
$dir = [System.IO.Path]::GetFullPath($dir)
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

for ($i = 1; $i -le 7; $i++) {
  $path = Join-Path $dir ("planetarium_star_{0}.json" -f $i)
  $json = @"
{
    "parent": "minecraft:item/generated",
    "textures": {
        "layer0": "tinkersnewlife:item/planetarium/base",
        "layer1": "tinkersnewlife:item/planetarium/star_$i"
    }
}
"@
  # 统一 LF 行尾 + 无 BOM（与仓库其它 JSON 一致）
  $text = ($json -replace "`r`n", "`n")
  [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
  Write-Host ("写出 planetarium_star_$i.json")
}
Write-Host "完成：7 个星象图子模型（底盘 layer0 全部指向 base）"
