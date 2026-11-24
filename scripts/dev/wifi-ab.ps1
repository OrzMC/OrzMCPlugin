# #616 实验 B：临时禁用 Wi-Fi 12 分钟（自动恢复），期间容器/插件只能走有线
# 由 WSL 侧以 elevated 方式启动（需要 UAC 确认一次）。
$log = Join-Path $env:TEMP 'wifi-ab.log'
function Log($m) { "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m | Out-File -Append -Encoding utf8 $log }

Log "开始：禁用 Wi-Fi（12 分钟窗口，容器将只能走有线）"
try {
    Disable-NetAdapter -Name 'Wi-Fi' -Confirm:$false -ErrorAction Stop
    Log "Wi-Fi 已禁用"
} catch {
    Log "禁用失败: $($_.Exception.Message)"
    exit 1
}
Start-Sleep -Seconds 720
Log "窗口结束：恢复 Wi-Fi"
try {
    Enable-NetAdapter -Name 'Wi-Fi' -Confirm:$false -ErrorAction Stop
    Log "Wi-Fi 已恢复"
} catch {
    Log "恢复失败（请手动开启 Wi-Fi）: $($_.Exception.Message)"
}
