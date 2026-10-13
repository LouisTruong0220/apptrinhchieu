<#
    CÀI APP LÊN ROBOT NOVA qua cáp USB (cắm ở ĐẦU robot) — dùng adb 1.0.41 của Android SDK.
    ⚠ KHÔNG dùng C:\Windows\adb.exe (bản PUDU 1.0.39) — hai bản giết tiến trình của nhau.

    .\tools\cai-len-robot.ps1                 # dựng app → cài APK → mở app từ RobotOS Home
    .\tools\cai-len-robot.ps1 -KhongBuild     # chỉ cài APK đang có
    .\tools\cai-len-robot.ps1 -LayKhach       # kéo danh sách khách đã đăng ký về may tính (CSV + JSONL)
    .\tools\cai-len-robot.ps1 -DayGiong       # chỉ đẩy MP3 giọng mới lên thẻ nhớ, không cài lại APK
    .\tools\cai-len-robot.ps1 -Log            # xem log app 25 giây
#>
param([switch]$KhongBuild, [switch]$LayKhach, [switch]$DayGiong, [switch]$DayTelegram, [switch]$Log)
$ErrorActionPreference = "Stop"
$App = Split-Path -Parent $PSScriptRoot
# adb 1.0.41 của Android SDK — ưu tiên ANDROID_HOME (SDK có thể không nằm ở %LOCALAPPDATA%)
$adb = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk", "D:\Android\Sdk") |
       Where-Object { $_ } | ForEach-Object { Join-Path $_ "platform-tools\adb.exe" } |
       Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }
$goi = "vn.roboworld.congnghiep"
$sd = "/sdcard/Android/data/$goi/files"
if (-not (Test-Path $adb)) { throw "Khong thay adb 1.0.41: $adb" }
# ⚠ KHÔNG adb kill-server ở đây: daemon adb mới sinh ra giữ ống xuất của script, script "chạy mãi" tới khi daemon chết.
$tb = & $adb devices | Select-String "device$"
if (-not $tb) { throw "Khong thay robot. Kiem tra cap USB o DAU robot, adb devices" }
Write-Host "Robot: $tb" -ForegroundColor Cyan
# Mở app xong app cũ (nếu đang chạy) phải dừng hẳn để nạp APK + telegram.json mới
if (-not $LayKhach -and -not $DayGiong -and -not $DayTelegram -and -not $Log) { cmd /c "`"$adb`" shell am force-stop $goi >nul 2>&1" }

if ($LayKhach) {
    $ra = Join-Path $App "khach-hang-tai-ve"; New-Item -ItemType Directory -Force $ra | Out-Null
    & $adb pull "$sd/khach-hang/khach-hang.csv" "$ra\khach-hang.csv"
    & $adb pull "$sd/khach-hang/khach-hang.jsonl" "$ra\khach-hang.jsonl"
    $n = (Get-Content "$ra\khach-hang.jsonl" -ErrorAction SilentlyContinue | Measure-Object -Line).Lines
    Write-Host "Da tai ve $n khach -> $ra\khach-hang.csv" -ForegroundColor Green; return
}
if ($DayTelegram) {
    & $adb push (Join-Path $App "cau-hinh\telegram.json") "$sd/telegram.json"
    Write-Host "Da day telegram.json — mo lai app de nap" -ForegroundColor Green; return
}
if ($Log) { & $adb logcat -c; & $adb logcat -v time CNApp:D CNCau:D CNGiong:D CNKhach:D RobotHelper:D CNWeb:D "*:S" -T 1 | Select-Object -First 400; return }
if ($DayGiong) {
    & $adb shell mkdir -p "$sd/giong"
    Get-ChildItem (Join-Path $App "giong\mp3\*.mp3") | ForEach-Object { & $adb push $_.FullName "$sd/giong/" | Out-Null }
    Write-Host "Da day giong len $sd/giong" -ForegroundColor Green; return
}

if (-not $KhongBuild) {
    Push-Location $App
    python dung-app.py; if ($LASTEXITCODE -ne 0) { Pop-Location; throw "dung-app.py loi" }
    & (Join-Path $App "tools\build-apk.ps1"); if ($LASTEXITCODE -ne 0) { Pop-Location; throw "build loi" }
    Pop-Location
}
$apk = Join-Path $App "android\app\build\outputs\apk\debug\app-debug.apk"
Write-Host "`nCai $([math]::Round((Get-Item $apk).Length/1MB,1)) MB ..." -ForegroundColor Cyan
& $adb install -r -d $apk
& $adb shell mkdir -p "$sd/khach-hang"
# Mở app TỪ ROBOTOS HOME: am start thì RobotApi trả handleApiDisabled (không đi được).
# Đưa Home ra trước rồi chạm vào icon app bằng uiautomator theo TÊN, không theo toạ độ.
cmd /c "`"$adb`" shell am start -n com.ainirobot.home/.LaunchActivity >nul 2>&1"
Start-Sleep 2
cmd /c "`"$adb`" shell uiautomator dump /sdcard/_ui.xml >nul 2>&1"; cmd /c "`"$adb`" shell cat /sdcard/_ui.xml > `"$env:TEMP\_ui.xml`" 2>nul"
$xml = Get-Content "$env:TEMP\_ui.xml" -Raw -ErrorAction SilentlyContinue
if ($xml -match 'text="Roboworld[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2); $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    & $adb shell input tap $x $y; Write-Host "Da mo app tu Home ($x,$y)" -ForegroundColor Green
} else {
    Write-Host "Khong tim thay icon app tren Home — vuot/mo tay app 'Roboworld — Tuan le Cong nghiep' tu RobotOS Home" -ForegroundColor Yellow
}
Write-Host "Xem log: .\tools\cai-len-robot.ps1 -Log"
