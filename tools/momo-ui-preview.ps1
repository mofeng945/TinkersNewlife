# MomoMerchant dialogue screen offline preview.
# Mirrors the exact layout math + nine-patch slicing used by MomoTalkScreen.java,
# so layout problems can be caught WITHOUT launching the game.
# Usage: pwsh -File tools\momo-ui-preview.ps1 [outPng] [guiW] [guiH] [scale]
param(
    [string]$Out = "$env:TEMP\momo_layout_preview.png",
    [int]$GuiW = 854,
    [int]$GuiH = 480,
    [int]$Scale = 2
)
Add-Type -AssemblyName System.Drawing

$texDir = Join-Path $PSScriptRoot "..\src\main\resources\assets\tinkersnewlife\textures\gui\momo"
$bub = [System.Drawing.Image]::FromFile((Join-Path $texDir "bubble_momo.png"))
$opt = [System.Drawing.Image]::FromFile((Join-Path $texDir "bubble_option.png"))
$por = [System.Drawing.Image]::FromFile((Join-Path $texDir "momo_happy.png"))

# --- constants copied from MomoTalkScreen.java ---
$PAD = 9; $NINE = 19; $RADIUS = 9; $ROW_H = 24; $TEX_W = 363; $TEX_H = 800
$PORTRAIT_H_RATIO = 0.68; $PORTRAIT_W_RATIO = 0.30

$W = $GuiW * $Scale; $H = $GuiH * $Scale
$bmp = New-Object System.Drawing.Bitmap($W, $H)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(255, 34, 34, 38))
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::SingleBitPerPixelGridFit
$font = New-Object System.Drawing.Font("Microsoft YaHei", (10 * $Scale), [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$brushDark = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 32, 32, 32))
$brushLight = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 204, 204, 204))

function Nine($img, $x, $y, $w, $h) {
    $r = $RADIUS; $t = $NINE
    if ($w -lt $r * 2 + 2) { $w = $r * 2 + 2 }
    if ($h -lt $r * 2 + 2) { $h = $r * 2 + 2 }
    $mw = $w - $r * 2; $mh = $h - $r * 2
    $d = {
        param($dx, $dy, $dw, $dh, $sx, $sy, $sw, $sh)
        $script:g.DrawImage($img, (New-Object System.Drawing.Rectangle($dx, $dy, $dw, $dh)), $sx, $sy, $sw, $sh, [System.Drawing.GraphicsUnit]::Pixel)
    }
    & $d $x $y $r $r 0 0 $r $r
    & $d ($x + $w - $r) $y $r $r ($t - $r) 0 $r $r
    & $d $x ($y + $h - $r) $r $r 0 ($t - $r) $r $r
    & $d ($x + $w - $r) ($y + $h - $r) $r $r ($t - $r) ($t - $r) $r $r
    & $d ($x + $r) $y $mw $r $r 0 1 $r
    & $d ($x + $r) ($y + $h - $r) $mw $r $r ($t - $r) 1 $r
    & $d $x ($y + $r) $r $mh 0 $r $r 1
    & $d ($x + $w - $r) ($y + $r) $r $mh ($t - $r) $r $r 1
    & $d ($x + $r) ($y + $r) $mw $mh $r $r 1 1
}

# --- layout, same math as init() ---
$portraitH = [int]($GuiH * $PORTRAIT_H_RATIO)
$portraitW = [int][Math]::Round($TEX_W * ($portraitH / [double]$TEX_H))
$cap = [int]($GuiW * $PORTRAIT_W_RATIO)
if ($portraitW -gt $cap) {
    $portraitW = $cap
    $portraitH = [int][Math]::Round($TEX_H * ($portraitW / [double]$TEX_W))
}
$panelX = 24
$panelW = [Math]::Max(200, $GuiW - $portraitW - 48)
$listY = [int]($GuiH * 0.30)
$listH = [int]($GuiH * 0.58)
$backX = $panelX; $backY = $GuiH - 34

# portrait: bottom aligned, right side
$px = $W - (($portraitW + 16) * $Scale); $py = $H - ($portraitH * $Scale)
$g.DrawImage($por, (New-Object System.Drawing.Rectangle($px, $py, ($portraitW * $Scale), ($portraitH * $Scale))))

# greeting bubble (1 line) + tail
$headH = 1 * 10 + $PAD * 2
$headY = $listY - $headH - 12
Nine $bub ($panelX * $Scale) ($headY * $Scale) ($panelW * $Scale) ($headH * $Scale)
$g.DrawString("今天有什么收获？又想聊聊天吗？", $font, $brushDark, (($panelX + $PAD) * $Scale), (($headY + $PAD) * $Scale))
$tailY = $listY - 12 - $headH + [Math]::Max(10, [Math]::Min($headH, 60) / 2)
for ($i = 0; $i -lt 8; $i++) {
    $x0 = ($panelX + $panelW + $i) * $Scale
    $y0 = ($tailY - [int]((8 - $i) / 2)) * $Scale
    $y1 = ($tailY + [int]((8 - $i) / 2) + 1) * $Scale
    $g.FillRectangle((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 247, 243, 231))), $x0, $y0, (1 * $Scale), ($y1 - $y0))
}

# option rows
$qs = @("· 你是……？", "· 这里是什么地方？", "· 这个世界为什么和我想象中不太一样？", "· 关于你的穿着？",
    "· 你的喜好？", "· 关于这个世界？", "· 关于咒术？", "· 什么是高纬度存在？", "· 你的镰刀？")
for ($i = 0; $i -lt $qs.Count; $i++) {
    $ry = $listY + 6 + $i * $ROW_H
    Nine $opt ($panelX * $Scale) ($ry * $Scale) ($panelW * $Scale) (($ROW_H - 4) * $Scale)
    $g.DrawString($qs[$i], $font, $brushDark, (($panelX + $PAD) * $Scale), (($ry + 6) * $Scale))
}
$g.DrawString("滚轮翻动（1/10）", $font, $brushLight, ($panelX * $Scale), (($listY + $listH + 2) * $Scale))

# back / close button
Nine $opt ($backX * $Scale) ($backY * $Scale) (90 * $Scale) (20 * $Scale)
$g.DrawString("关闭", $font, $brushDark, (($backX + 45 - 16) * $Scale), (($backY + 6) * $Scale))

$g.Dispose()
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose(); $bub.Dispose(); $opt.Dispose(); $por.Dispose()
"saved: $Out"
"gui={0}x{1} scale={2} portrait={3}x{4} panelW={5}" -f $GuiW, $GuiH, $Scale, $portraitW, $portraitH, $panelW
