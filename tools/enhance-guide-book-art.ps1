# 新生神秘学编年史（guide_book）贴图**加细节**（不重画）
#
# 原则：**完全沿用作者原图**——轮廓一个像素不改、配色布局不改（黑封面 / 红十字符印 / 灰色书页边），
#       只做"加法"：
#         · 轮廓明暗：左上受光的边缘提亮、右下边缘压暗 → 立体感（原图边缘是平的黑）
#         · 红色符印：按亮度归到 4 档红色阶，并给受光边补一档亮红 → 符印更"挺"
#         · 灰色书页：受光边再提亮一点，与封面拉开层次
#
# 输出：textures/item/guide_book.png（16×16，覆盖原文件）
# 用法：powershell -ExecutionPolicy Bypass -File tools\enhance-guide-book-art.ps1
#
# 源图备份：tools/art-src/guide_book_original.png（作者原版，随时可还原）

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$texDir = Join-Path $root 'src\main\resources\assets\tinkersnewlife\textures\item'
$outPath = Join-Path $texDir 'guide_book.png'
$backupDir = Join-Path $root 'tools\art-src'
$backup = Join-Path $backupDir 'guide_book_original.png'
if (-not (Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir -Force | Out-Null }
if (-not (Test-Path $backup)) { Copy-Item $outPath $backup -Force; Write-Host ("已备份原图 -> " + $backup) }

$pf = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
$src = [System.Drawing.Bitmap]::new($backup)          # 永远以作者原图为源，脚本可重复执行
$W = $src.Width; $H = $src.Height
$d = $src.LockBits((New-Object System.Drawing.Rectangle 0, 0, $W, $H), [System.Drawing.Imaging.ImageLockMode]::ReadOnly, $pf)
$st = $d.Stride
$sb = [byte[]]::new($st * $H)
[System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $sb, 0, $sb.Length)
$src.UnlockBits($d); $src.Dispose()

$out = [System.Drawing.Bitmap]::new($W, $H, $pf)
$od = $out.LockBits((New-Object System.Drawing.Rectangle 0, 0, $W, $H), [System.Drawing.Imaging.ImageLockMode]::WriteOnly, $pf)
$ob = [byte[]]::new($st * $H)

function Get-A([int]$x, [int]$y) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $W -or $y -ge $H) { return 0 }
    return $sb[($y * $st + $x * 4 + 3)]
}
function Clamp255([double]$v) { if ($v -lt 0) { return 0 } elseif ($v -gt 255) { return 255 } else { return [int][Math]::Round($v) } }

# 红色阶（暗 -> 亮）：作者原图的红是 #320000/#5F0000/#7E0000/#B20000，这里细分成 5 档
$RED_RAMP = @(0x32, 0x5F, 0x8A, 0xB2, 0xD8)

$changed = 0
for ($y = 0; $y -lt $H; $y++) {
    for ($x = 0; $x -lt $W; $x++) {
        $i = $y * $st + $x * 4
        $o = $y * $st + $x * 4
        if ($sb[($i + 3)] -eq 0) { $ob[($o + 3)] = 0; continue }
        $bb = [int]$sb[$i]; $gg = [int]$sb[($i + 1)]; $rr = [int]$sb[($i + 2)]

        $isRed = ($rr -gt ($gg + 30)) -and ($rr -gt ($bb + 30))
        $isGrey = (-not $isRed) -and ($rr -gt 60)          # 书页那种浅灰

        $upLeft = ((Get-A $x ($y - 1)) -eq 0) -or ((Get-A ($x - 1) $y) -eq 0)
        $downRight = ((Get-A $x ($y + 1)) -eq 0) -or ((Get-A ($x + 1) $y) -eq 0)

        if ($isRed) {
            # 红色符印：只加"立体边"——受光侧 +0x16 红、背光侧 x0.80，红本身的层次由作者原图决定
            $nr = $rr; $ng = $gg; $nb = $bb
            if ($upLeft) { $nr = Clamp255 ($rr + 0x16); $ng = Clamp255 ($gg + 6); $nb = Clamp255 ($bb + 6) }
            elseif ($downRight) { $nr = Clamp255 ($rr * 0.80); $ng = Clamp255 ($gg * 0.80); $nb = Clamp255 ($bb * 0.80) }
        }
        else {
            # 封面 / 书页：极轻的边缘明暗（暗部才提，浅灰页只提一点点），避免改变整体观感
            $nr = $rr; $ng = $gg; $nb = $bb
            if ($upLeft -and -not $downRight) {
                $lift = if ($isGrey) { 8.0 } else { 16.0 }
                $nr = Clamp255 ($rr + $lift); $ng = Clamp255 ($gg + $lift); $nb = Clamp255 ($bb + $lift)
            }
            elseif ($downRight -and -not $upLeft) {
                $nr = Clamp255 ($rr * 0.86); $ng = Clamp255 ($gg * 0.86); $nb = Clamp255 ($bb * 0.86)
            }
        }

        if ($nr -ne $rr -or $ng -ne $gg -or $nb -ne $bb) { $changed++ }
        $ob[$o] = [byte]$nb; $ob[($o + 1)] = [byte]$ng; $ob[($o + 2)] = [byte]$nr; $ob[($o + 3)] = 255
    }
}
[System.Runtime.InteropServices.Marshal]::Copy($ob, 0, $od.Scan0, $ob.Length)
$out.UnlockBits($od)
$out.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$out.Dispose()
Write-Host ("generated " + $outPath + " (" + $W + "x" + $H + ", 只改动了 " + $changed + " 个像素的明暗，轮廓与配色未变)")
