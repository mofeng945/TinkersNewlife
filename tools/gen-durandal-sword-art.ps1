# 杜兰达尔之剑 物品贴图生成脚本（重画成 Minecraft 像素风）
#
# 输出：textures/item/durandal_sword.png（默认 64×64；把 $SIZE 改成 32 就能出 32×32）
# 用法：powershell -ExecutionPolicy Bypass -File tools\gen-durandal-sword-art.ps1
#
# 原贴图是 256×256 的写实向黑剑，实测：
#   · 主轴斜率 44.2°（柄头在左下 (6,245)，剑尖在右上 (251,7)，基本就是 45° 对角线）
#   · 沿轴分段：柄头 0~7% / 握把 7~12% / 护手 12~25%（最宽约 105px = 画布 41%）/ 剑身 25~78% / 剑尖 78~100%
#   · 剑身是火焰波浪刃（边缘有起伏），护手是十字 + 尖刺的造型
# 本脚本按同样的斜率与分段重画，但用像素画做法：有限调色板 + 1px 描边 + 左上受光。
#
# 坐标系统一用"64 格基准"，实际像素 = 基准 × ($SIZE / 64)，所以 32/64 都能出图。

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$SIZE = 64          # ← 改成 32 即出 32×32
$U = $SIZE / 64.0   # 1 个"基准单位"= 多少像素

$canvas = New-Object System.Drawing.Bitmap $SIZE, $SIZE
for ($y = 0; $y -lt $SIZE; $y++) { for ($x = 0; $x -lt $SIZE; $x++) { $canvas.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0)) } }

# ============ 调色板（暗钢 + 冷高光，贴合原图"黑剑"基调） ============
function C([string]$hex) {
    return [System.Drawing.Color]::FromArgb(255,
        [Convert]::ToInt32($hex.Substring(1, 2), 16),
        [Convert]::ToInt32($hex.Substring(3, 2), 16),
        [Convert]::ToInt32($hex.Substring(5, 2), 16))
}
$OUT     = C '#0B0A10'   # 描边
$BLADE_D = C '#25252E'   # 剑身 暗部（右下）
$BLADE_M = C '#3B3B47'   # 剑身 中间调
$BLADE_L = C '#5D5D6D'   # 剑身 亮部（左上）
$BLADE_H = C '#9C9CB0'   # 刃口高光
$FULLER  = C '#191921'   # 血槽
$GUARD_D = C '#1E1E27'
$GUARD_M = C '#494957'
$GUARD_H = C '#8A8A9E'
$GRIP_D  = C '#15151B'
$GRIP_M  = C '#31313B'
$GEM     = C '#6E45A8'   # 护手中心宝石（呼应咒术主题的紫）
$GEM_H   = C '#C4A0EE'

$px = @{}
function Set-Px([int]$x, [int]$y, $color) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $SIZE -or $y -ge $SIZE) { return }
    $px["$x,$y"] = $color
}

# 轴线：柄头左下 → 剑尖右上，45°（与原图 44.2° 一致）
$pommel = @(7.0, 57.0)
$tip    = @(58.0, 6.0)
$ax = $tip[0] - $pommel[0]
$ay = $tip[1] - $pommel[1]
$LEN = [Math]::Sqrt($ax * $ax + $ay * $ay)
$ux = $ax / $LEN; $uy = $ay / $LEN      # 沿轴单位向量
$nx = -$uy;       $ny = $ux             # 垂直单位向量（负值 = 左上侧）

# 尖刺判定：把 (pt, ps) 转到尖刺自身坐标，看是否落在锥形刺内
function In-Spike([double]$pt, [double]$ps, [double]$angDeg, [double]$len, [double]$halfW) {
    $ca = [Math]::Cos($angDeg * [Math]::PI / 180.0)
    $sa = [Math]::Sin($angDeg * [Math]::PI / 180.0)
    $along = $pt * $ca + $ps * $sa
    $across = -$pt * $sa + $ps * $ca
    if ($along -lt 0 -or $along -gt $len) { return $false }
    $w = $halfW * (1.0 - 0.85 * ($along / $len))
    return [Math]::Abs($across) -le $w
}

$guardPt = 0.205 * $LEN      # 护手中心（沿轴像素位置）

for ($y = 0; $y -lt $SIZE; $y++) {
    for ($x = 0; $x -lt $SIZE; $x++) {
        $cx = $x + 0.5; $cy = $y + 0.5
        $dx = $cx - $pommel[0]; $dy = $cy - $pommel[1]
        $t = ($dx * $ux + $dy * $uy) / $LEN       # 0 = 柄头, 1 = 剑尖
        $s = ($dx * $nx + $dy * $ny) / $U         # 基准单位，正值偏右下
        if ($t -lt -0.02 -or $t -gt 1.02) { continue }
        $pt = $t * $LEN                           # 沿轴（像素）
        $ps = $s * $U                             # 垂直（像素）
        $color = $null

        # ---- 剑身：火焰波浪刃 + 血槽 + 左上刃口高光 ----
        if ($t -ge 0.250 -and $t -le 1.0) {
            $hw = (2.35 + 0.85 * [Math]::Sin($t * 32.0)) * $U
            if ($t -gt 0.80) { $hw = $hw * (1.0 - ($t - 0.80) / 0.20) }
            if ([Math]::Abs($ps) -le $hw) {
                if ([Math]::Abs($ps) -le 0.55 * $U) { $color = $FULLER }
                elseif ($ps -lt -1.55 * $U) { $color = $BLADE_H }
                elseif ($ps -lt -0.35 * $U) { $color = $BLADE_L }
                elseif ($ps -lt 1.0 * $U) { $color = $BLADE_M }
                else { $color = $BLADE_D }
            }
        }

        # ---- 护手：横杆（两端收尖）+ 四根斜刺 + 两根纵向短刺 + 中心宝石 ----
        if ($null -eq $color) {
            $dAlong = [Math]::Abs($pt - $guardPt)
            $barHW = 11.6 * $U
            $barThick = 2.0 * $U * (1.0 - [Math]::Pow([Math]::Min(1.0, [Math]::Abs($ps) / $barHW), 3.0))
            $inBar = ($dAlong -le [Math]::Max(0.6, $barThick)) -and ([Math]::Abs($ps) -le $barHW)
            $inSpike = $false
            if (-not $inBar) {
                foreach ($ang in @(32.0, -32.0, 148.0, -148.0)) {
                    if (In-Spike ($pt - $guardPt) $ps $ang (16.5 * $U) (1.95 * $U)) { $inSpike = $true; break }
                }
            }
            if (-not $inBar -and -not $inSpike) {
                foreach ($ang in @(90.0, -90.0)) {
                    if (In-Spike ($pt - $guardPt) $ps $ang (9.5 * $U) (1.5 * $U)) { $inSpike = $true; break }
                }
            }
            if ($inBar -or $inSpike) {
                if ([Math]::Abs($ps) -lt 1.4 * $U -and $dAlong -lt 2.1 * $U) { $color = $GEM }
                elseif ($ps -lt -0.3 * $U) { $color = $GUARD_H }
                elseif ($inSpike -and ([Math]::Abs($ps) -gt 7.5 * $U)) { $color = $GUARD_D }
                else { $color = $GUARD_M }
            }
        }

        # ---- 握把（缠绕纹）+ 柄头 ----
        if ($null -eq $color) {
            if ($t -ge 0.045 -and $t -lt 0.115) {
                $hw = (1.7 + 0.35 * [Math]::Sin($pt * 1.1)) * $U
                if ([Math]::Abs($ps) -le $hw) {
                    $color = if (([Math]::Floor($pt / 2.6) % 2) -eq 0) { $GRIP_M } else { $GRIP_D }
                }
            }
            elseif ($t -ge -0.02 -and $t -lt 0.045) {
                $hw = (2.6 - 12.0 * [Math]::Abs($t - 0.012)) * $U
                if ($hw -gt 0 -and [Math]::Abs($ps) -le $hw) {
                    $color = if ($ps -lt -0.6 * $U) { $GUARD_H } else { $GRIP_D }
                }
            }
        }

        if ($color) { Set-Px $x $y $color }
    }
}

# 宝石高光（左上一点）
$gx = [int]($pommel[0] + $ux * ($guardPt - 1.0) + $nx * (-1.0))
$gy = [int]($pommel[1] + $uy * ($guardPt - 1.0) + $ny * (-1.0))
Set-Px $gx $gy $GEM_H

# ============ 描边：先收集、后落笔（边扫边写会自己扩散成一片） ============
$edge = New-Object System.Collections.Generic.List[string]
for ($y = 0; $y -lt $SIZE; $y++) {
    for ($x = 0; $x -lt $SIZE; $x++) {
        if ($px.ContainsKey("$x,$y")) { continue }
        $near = $false
        foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
            $ex = $x + $d[0]; $ey = $y + $d[1]
            if ($px.ContainsKey("$ex,$ey")) { $near = $true; break }
        }
        if ($near) { $edge.Add("$x,$y") }
    }
}
foreach ($k in $edge) { $p2 = $k -split ','; Set-Px ([int]$p2[0]) ([int]$p2[1]) $OUT }

for ($y = 0; $y -lt $SIZE; $y++) {
    for ($x = 0; $x -lt $SIZE; $x++) {
        if ($px.ContainsKey("$x,$y")) { $canvas.SetPixel($x, $y, $px["$x,$y"]) }
    }
}
$out = Join-Path $outDir 'durandal_sword.png'
$canvas.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()
Write-Host ("generated " + $out + " (" + $SIZE + "x" + $SIZE + ")")
