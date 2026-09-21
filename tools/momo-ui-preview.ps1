# MomoMerchant dialogue screen offline preview.
# Mirrors the exact layout math + nine-patch slicing used by MomoTalkScreen.java,
# so layout problems can be caught WITHOUT launching the game.
# Usage: pwsh -File tools\momo-ui-preview.ps1 [outPng] [guiW] [guiH] [scale]
param(
    [string]$Out = "$env:TEMP\momo_layout_preview.png",
    [int]$GuiW = 854,
    [int]$GuiH = 480,
    [int]$Scale = 2,
    [string]$Screen = "talk",   # talk | menu
    [int]$Favor = 0             # 决定立绘表情（表与 MomoArt.exprForFavor 一致 ✓）
)
Add-Type -AssemblyName System.Drawing

$texDir = Join-Path $PSScriptRoot "..\src\main\resources\assets\tinkersnewlife\textures\gui\momo"
$bub = [System.Drawing.Image]::FromFile((Join-Path $texDir "bubble_momo.png"))
$opt = [System.Drawing.Image]::FromFile((Join-Path $texDir "bubble_option.png"))

# --- constants copied from MomoArt.java ---
$PAD = 9; $NINE = 19; $RADIUS = 9; $ROW_H = 24; $TEX_W = 363; $TEX_H = 800
$PORTRAIT_H_RATIO = 0.98; $PORTRAIT_W_RATIO = 0.50; $CROP_FRAC = 0.95; $FACE_IN_TEX = 165.0 / 800.0
# 好感档 -> 默认表情（MomoArt.GREET_EXPR ✓）
$GREET = @(0, 0, 1, 2, 1, 1); $TIERS = @(0, 10, 20, 30, 40, 50)
$EXPR_FILES = @("momo_normal", "momo_happy", "momo_blush", "momo_awkward", "momo_surprised", "momo_disgust")
$best = 0
for ($i = 0; $i -lt 6; $i++) { if ($Favor -ge $TIERS[$i]) { $best = $i } }
$exprIdx = $GREET[$best]
if ($Favor -lt 0) { $exprIdx = 5 }   # 负好感 = 嫌恶（与 MomoArt.exprForFavor 一致 ✓）
$por = [System.Drawing.Image]::FromFile((Join-Path $texDir ($EXPR_FILES[$exprIdx] + ".png")))

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
$brushMid = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 80, 80, 80))

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
$cropH = [int][Math]::Round($TEX_H * $CROP_FRAC)
$portraitH = [int]($GuiH * $PORTRAIT_H_RATIO)
$portraitW = [int][Math]::Round($TEX_W * ($portraitH / [double]$cropH))
$cap = [int]($GuiW * $PORTRAIT_W_RATIO)
if ($portraitW -gt $cap) {
    $portraitW = $cap
    $portraitH = [int][Math]::Round($cropH * ($portraitW / [double]$TEX_W))
}
$faceY = ($GuiH - $portraitH) + [int][Math]::Round($portraitH * (($TEX_H * $FACE_IN_TEX) / [double]$cropH))
$panelX = 24
$panelW = [Math]::Max(200, $GuiW - $portraitW - 48)
$greetH = 1 * 10 + $PAD * 2
$listY = [Math]::Max(10, $faceY - [int]($greetH / 2))
$rowsTop = $listY + $greetH + 10
$listH = [Math]::Max(72, $GuiH - $rowsTop - 44)
$backX = $panelX; $backY = $GuiH - 34

# portrait: bottom aligned, right side (both screens ✓)
$px = $W - (($portraitW + 16) * $Scale); $py = $H - ($portraitH * $Scale)
$g.DrawImage($por, (New-Object System.Drawing.Rectangle($px, $py, ($portraitW * $Scale), ($portraitH * $Scale))), 0, 0, $TEX_W, $cropH, [System.Drawing.GraphicsUnit]::Pixel)

if ($Screen -eq "menu") {
    # ---- MomoMenuScreen 布局 ✓ ----
    $PANEL_W = 190; $PANEL_H = 158; $BTN_H = 22; $GAP = 8
    $availW = [Math]::Max($PANEL_W + 8, $GuiW - $portraitW - 24)
    $mx = [int](($availW - $PANEL_W) / 2); $my = [int](($GuiH - $PANEL_H) / 2)
    Nine $bub ($mx * $Scale) ($my * $Scale) ($PANEL_W * $Scale) ($PANEL_H * $Scale)
    $g.DrawString("墨默", $font, $brushDark, (($mx + 14) * $Scale), (($my + 12) * $Scale))
    $g.DrawString("好感度 $Favor", $font, $brushMid, (($mx + 14) * $Scale), (($my + 26) * $Scale))
    $g.FillRectangle((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(90, 140, 127, 99))),
        (($mx + 12) * $Scale), (($my + 38) * $Scale), (($PANEL_W - 24) * $Scale), (1 * $Scale))
    $labels = @("对话", "交易", "雇佣")
    for ($i = 0; $i -lt 3; $i++) {
        $bx = $mx + 16; $bw = $PANEL_W - 32; $by = $my + 42 + $i * ($BTN_H + $GAP)
        Nine $opt ($bx * $Scale) ($by * $Scale) ($bw * $Scale) ($BTN_H * $Scale)
        $g.DrawString($labels[$i], $font, $brushDark, (($bx + ($bw - 32) / 2) * $Scale), (($by + 7) * $Scale))
    }
    $kx = $mx + $PANEL_W - 56; $ky = $my + $PANEL_H - 26
    Nine $opt ($kx * $Scale) ($ky * $Scale) (44 * $Scale) (18 * $Scale)
    $g.DrawString("回退", $font, $brushDark, (($kx + 22 - 16) * $Scale), (($ky + 5) * $Scale))
    $panelW = $PANEL_W
} else {
    # ---- MomoTalkScreen 布局 ✓ ----
    $headH = 1 * 10 + $PAD * 2
    $headY = $listY
    Nine $bub ($panelX * $Scale) ($headY * $Scale) ($panelW * $Scale) ($headH * $Scale)
    $g.DrawString("今天有什么收获？又想聊聊天吗？", $font, $brushDark, (($panelX + $PAD) * $Scale), (($headY + $PAD) * $Scale))
    $tailTop = $listY
    $tailY = [Math]::Max($tailTop + 8, [Math]::Min($tailTop + $headH - 8, $faceY))
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
        $ry = $rowsTop + $i * $ROW_H
        Nine $opt ($panelX * $Scale) ($ry * $Scale) ($panelW * $Scale) (($ROW_H - 4) * $Scale)
        $g.DrawString($qs[$i], $font, $brushDark, (($panelX + $PAD) * $Scale), (($ry + 6) * $Scale))
    }
    $g.DrawString("滚轮翻动（1/10）", $font, $brushLight, ($panelX * $Scale), ([Math]::Min($GuiH - 46, $rowsTop + 10 * $ROW_H + 2) * $Scale))

    # back / close button
    Nine $opt ($backX * $Scale) ($backY * $Scale) (90 * $Scale) (20 * $Scale)
    $g.DrawString("关闭", $font, $brushDark, (($backX + 45 - 16) * $Scale), (($backY + 6) * $Scale))
}

$g.Dispose()
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose(); $bub.Dispose(); $opt.Dispose(); $por.Dispose()
"saved: $Out"
"screen=$Screen favor=$Favor expr=$($EXPR_FILES[$exprIdx]) gui={0}x{1} scale={2} portrait={3}x{4} panelW={5}" -f $GuiW, $GuiH, $Scale, $portraitW, $portraitH, $panelW
