<#
  学生社团招新面试叫号系统 - 后端 API 回归测试
  运行前提：后端 8080 已启动、MySQL 已就绪、前端 5173 dev server 可代理
  用法：pwsh -File .\tests\api-test.ps1
  说明：测试会清空 interview_system 库中的考生数据（使用 delete-all 密码），请勿对生产数据运行
#>

$ErrorActionPreference = 'Continue'
$BASE = 'http://localhost:5173/api/candidates'
$script:passed = 0
$script:failed = 0
$script:results = @()
$TOKEN = $null

function Api {
  param([string]$Method, [string]$Path, [string]$Json = $null, [string]$Token = $null)
  $cargs = @('-s', '-X', $Method, '-H', 'Accept: application/json')
  if ($Token) { $cargs += @('-H', "Authorization: Bearer $Token") }
  if ($Json) {
    $tmp = Join-Path $env:TEMP ('req_' + [guid]::NewGuid().ToString('N') + '.json')
    [IO.File]::WriteAllText($tmp, $Json, (New-Object Text.UTF8Encoding $false))
    $cargs += @('-H', 'Content-Type: application/json', '--data-binary', "@$tmp")
  }
  $out = & curl.exe @cargs "$BASE$Path" 2>$null
  if ($Json) { Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue }
  return $out
}

function Check {
  param([string]$Name, [bool]$Cond, [string]$Detail = '')
  if ($Cond) { $script:passed++; $script:results += "PASS`t$Name" }
  else { $script:failed++; $script:results += "FAIL`t$Name`t$Detail" }
  Write-Host ("{0}  {1}" -f $(if ($Cond) { '[PASS]' } else { '[FAIL]' }), $Name)
}

function Field {
  param([string]$Json, [string]$Key)
  try { $o = $Json | ConvertFrom-Json -ErrorAction Stop; return $o.$Key } catch { return $null }
}

# ============ 0. 准备：清空测试数据 ============
$r = Api 'POST' '/delete-all' '{"password":"change-me"}'
$delOk = Field $r 'success'
Check 'T0 清空测试数据（delete-all 正确密码）' ($delOk -eq $true) $r

# ============ 1. 预报名校验 ============
$stu1 = '{"name":"测试甲","gender":"男","className":"软件技术1班","phone":"13800000001","firstDepartment":"部门A","secondDepartment":"部门B","acceptAdjust":"是"}'
$r = Api 'POST' '/pre-register/public' $stu1
$c1 = $r | ConvertFrom-Json
Check 'T1.1 公开预报名成功' ($c1.status -eq 'pre_registered' -and $c1.id) $r
$id1 = $c1.id

$r = Api 'POST' '/pre-register/public' $stu1
Check 'T1.2 重复手机号被拒绝' ((Field $r 'error') -eq $true) $r

$badPhone = '{"name":"测试乙","gender":"女","className":"软件技术1班","phone":"12345","firstDepartment":"部门A","acceptAdjust":"是"}'
$r = Api 'POST' '/pre-register/public' $badPhone
Check 'T1.3 非法手机号被拒绝' ((Field $r 'error') -eq $true) $r

$noName = '{"name":"","gender":"男","className":"软件技术1班","phone":"13800000002","firstDepartment":"部门A","acceptAdjust":"是"}'
$r = Api 'POST' '/pre-register/public' $noName
Check 'T1.4 缺少姓名被拒绝' ((Field $r 'error') -eq $true) $r

# ============ 2. 公开查询 / 签到 ============
$r = Api 'POST' '/public/search-by-name' '{"name":"测试甲"}'
$hits = @($r | ConvertFrom-Json)
Check 'T2.1 按姓名公开查询命中' ($hits.Count -ge 1 -and $hits[0].id -eq $id1) $r

$r = Api 'POST' '/public/check-in' "{`"candidateId`":`"$id1`"}"
$c = $r | ConvertFrom-Json
Check 'T2.2 公开签到成功并进入等待' ($c.status -eq 'waiting' -and $null -ne $c.globalNumber) $r

$r = Api 'POST' '/public/check-in' "{`"candidateId`":`"$id1`"}"
Check 'T2.3 重复签到被拒绝' ((Field $r 'error') -eq $true) $r

# ============ 3. 报名第二个考生 + 分组 ============
$stu2 = '{"name":"测试乙","gender":"女","className":"软件技术2班","phone":"13800000003","firstDepartment":"部门A","secondDepartment":"部门C","acceptAdjust":"否"}'
$r = Api 'POST' '/pre-register/public' $stu2
$c2 = $r | ConvertFrom-Json
$id2 = $c2.id
$r = Api 'POST' '/public/check-in' "{`"candidateId`":`"$id2`"}"
Check 'T3.1 第二名考生报名并签到' ($id2 -and ((Field $r 'status') -eq 'waiting')) $r

$r = Api 'POST' '/assign-group' "{`"candidateId`":`"$id1`",`"groupName`":`"A`",`"operatorName`":`"admin`"}"
$c = $r | ConvertFrom-Json
Check 'T3.2 分配 A 组并生成组内序号' ($c.groupName -eq 'A' -and $c.groupNumber -eq 1) $r

$r = Api 'POST' '/assign-group' "{`"candidateId`":`"$id2`",`"groupName`":`"A`",`"operatorName`":`"admin`"}"
$c = $r | ConvertFrom-Json
Check 'T3.3 组内序号递增' ($c.groupNumber -eq 2) $r

$r = Api 'GET' '/group/A?interviewerName=%E9%9D%A2%E8%AF%95%E5%AE%98A1'
$q = @($r | ConvertFrom-Json)
Check 'T3.4 分组队列返回 2 人且按序号升序' ($q.Count -eq 2 -and $q[0].groupNumber -eq 1) $r

# ============ 4. 叫号 + 同组并发约束 ============
$r = Api 'PATCH' "/$id1/status" '{"status":"interviewing","interviewerName":"面试官A1"}'
Check 'T4.1 叫号进入面试中' ((Field $r 'status') -eq 'interviewing') $r

$r = Api 'PATCH' "/$id2/status" '{"status":"interviewing","interviewerName":"面试官A1"}'
Check 'T4.2 同组第二人叫号被并发约束拒绝' ((Field $r 'error') -eq $true) $r

$r = Api 'PATCH' "/$id1/status" '{"status":"second_waiting","interviewerName":"面试官A1"}'
Check 'T4.3 非法状态流转被拒绝（状态机白名单）' ((Field $r 'error') -eq $true) $r

# ============ 5. 打分（三人制，打满自动结束）============
$r = Api 'POST' "/$id1/score" '{"score":8.5,"interviewerName":"面试官A1","judgeComment":"表达清晰"}'
$c = $r | ConvertFrom-Json
Check 'T5.1 面试官A1 打分 8.5 成功' ($c.scoredCount -eq 1) $r
Check 'T5.2 未满 3 人仍为面试中' ($c.status -eq 'interviewing') $r

$r = Api 'POST' "/$id1/score" '{"score":11,"interviewerName":"面试官A2"}'
Check 'T5.3 超过 10 分的评分被拒绝' ((Field $r 'error') -eq $true) $r

$r = Api 'POST' "/$id1/score" '{"score":7.0,"interviewerName":"面试官A2","judgeComment":"逻辑较好"}'
$c = $r | ConvertFrom-Json
Check 'T5.4 面试官A2 打分 7.0，累计 2 人评分' ($c.scoredCount -eq 2) $r
Check 'T5.5 两人仍未自动结束' ($c.status -eq 'interviewing') $r

$r = Api 'POST' "/$id1/score" '{"score":8.0,"interviewerName":"面试官A1"}'
$c = $r | ConvertFrom-Json
Check 'T5.6 同一面试官重复打分走更新（不新增记录）' ($c.scoredCount -eq 2) $r

$r = Api 'POST' "/$id1/score" '{"score":8.0,"interviewerName":"面试官A3","judgeComment":"潜力较好"}'
$c = $r | ConvertFrom-Json
Check 'T6.1 第三人打满 3 人后一面自动完成' ($c.status -eq 'first_completed') $r
Check 'T6.2 总分按平均分×10 取整（8.0/7.0/8.0→77）' ($c.scoreTotal -eq 77) ("scoreTotal=" + $c.scoreTotal)

# ============ 6b. 批量三席位打分 ============
$r = Api 'PATCH' "/$id2/status" '{"status":"interviewing","interviewerName":"面试官A1"}'
Check 'T6b.1 第二名考生叫号进入面试中' ((Field $r 'status') -eq 'interviewing') $r

$batch = '{"operatorName":"admin","scores":[{"interviewerName":"面试官A1","score":9.0,"judgeComment":"反应快"},{"interviewerName":"面试官A2","score":8.0,"judgeComment":"表达稳"},{"interviewerName":"面试官A3","score":8.5,"judgeComment":"态度好"}]}'
$r = Api 'POST' "/$id2/scores" $batch
$c = $r | ConvertFrom-Json
Check 'T6b.2 三席位批量打分成功且自动完成' ($c.status -eq 'first_completed' -and $c.scoredCount -eq 3) $r

$badBatch = '{"operatorName":"admin","scores":[{"interviewerName":"面试官B1","score":8.0}]}'
$r = Api 'POST' "/$id2/scores" $badBatch
Check 'T6b.3 非本组评分人被拒绝' ((Field $r 'error') -eq $true) $r

$r = Api 'GET' '/stats'
$s = $r | ConvertFrom-Json
Check 'T6.4 统计接口返回 total/completed/averageScore' (($null -ne $s.total) -and ($null -ne $s.completed)) $r

$r = Api 'GET' '/group-counts'
$gc = @($r | ConvertFrom-Json)
Check 'T6.5 组别统计接口返回数组' ($gc.Count -ge 1) $r

$r = Api 'GET' '/display/big-screen'
Check 'T6.6 大屏数据接口可返回' ((Field $r 'error') -ne $true) ($r.Substring(0, [Math]::Min(80, $r.Length)))

# ============ 7. 二面全流程 ============
$r = Api 'POST' '/second/move-to' "{`"candidateId`":`"$id1`",`"operatorName`":`"admin`"}"
Check 'T7.1 一面完成者移入二面' ((Field $r 'status') -eq 'second_waiting') $r

$r = Api 'POST' '/second/assign-group' "{`"candidateId`":`"$id1`",`"groupName`":`"E`",`"operatorName`":`"admin`"}"
Check 'T7.2 分配二面 E 组' ((Field $r 'groupName') -eq 'E') $r

$r = Api 'POST' '/second/check-in' "{`"candidateId`":`"$id1`"}"
Check 'T7.3 二面签到成功' ((Field $r 'error') -ne $true) $r

$r = Api 'POST' '/second/start' "{`"candidateId`":`"$id1`",`"interviewerName`":`"面试官E1`"}"
Check 'T7.4 二面叫号进入二面中' ((Field $r 'status') -eq 'second_interviewing') $r

$r = Api 'POST' '/second/start' "{`"candidateId`":`"$id1`",`"interviewerName`":`"面试官E2`"}"
Check 'T7.5 重复叫号被拒绝（全局仅一人二面中）' ((Field $r 'error') -eq $true) $r

$r = Api 'POST' '/second/decision' "{`"candidateId`":`"$id1`",`"interviewerName`":`"面试官E1`",`"decision`":`"admitted`",`"score`":9,`"comment`":`"综合表现优秀`"}"
Check 'T7.6 录取未指定部门被拒绝' ((Field $r 'error') -eq $true) $r

$r = Api 'POST' '/second/decision' "{`"candidateId`":`"$id1`",`"interviewerName`":`"面试官E1`",`"decision`":`"admitted`",`"finalDepartment`":`"部门X`",`"score`":9}"
Check 'T7.7 录取到非法部门被拒绝' ((Field $r 'error') -eq $true) $r

$r = Api 'POST' '/second/decision' "{`"candidateId`":`"$id1`",`"interviewerName`":`"面试官E1`",`"decision`":`"admitted`",`"finalDepartment`":`"部门B`",`"score`":9,`"comment`":`"综合表现优秀`"}"
$c = $r | ConvertFrom-Json
Check 'T7.8 二面录取成功并落到部门' ($c.status -eq 'final_admitted' -and $c.finalDepartment -eq '部门B') $r

$r = Api 'GET' '/second/stats'
Check 'T7.9 二面统计接口可返回' ((Field $r 'error') -ne $true) $r

$r = Api 'GET' '/second/queue'
Check 'T7.10 二面队列接口可返回' ((Field $r 'error') -ne $true) $r

# ============ 8. 大屏模式 / 备用开关 ============
$r = Api 'GET' '/display/mode?operatorName=admin'
Check 'T8.1 读取大屏显示模式' ((Field $r 'error') -ne $true) $r

$r = Api 'POST' '/display/toggle-mode' '{"operatorName":"admin","mode":"second"}'
Check 'T8.2 切换大屏到二面模式' ((Field $r 'mode') -eq 'second') $r

$r = Api 'POST' '/display/toggle-mode' '{"operatorName":"admin","mode":"first"}'
Check 'T8.3 大屏切回一面模式' ((Field $r 'mode') -eq 'first') $r

$r = Api 'GET' '/second/backup-status?operatorName=admin'
Check 'T8.4 读取备用二面官开关' ((Field $r 'error') -ne $true) $r

# ============ 9. 导出 / CSRF-none / 错误处理 ============
$tmpCsv = Join-Path $env:TEMP 'export_test.csv'
& curl.exe -s -o $tmpCsv "$BASE/export"
$bytes = [IO.File]::ReadAllBytes($tmpCsv)
$hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
$header = (Get-Content -LiteralPath $tmpCsv -Encoding utf8 -TotalCount 1)
$colCount = ($header -split ',').Count
Check 'T9.1 CSV 导出带 UTF-8 BOM' $hasBom ''
Check 'T9.2 CSV 表头为 17 列' ($colCount -eq 17) ("cols=" + $colCount)

$r = Api 'PATCH' "/$id1/status" '{"status":"not_a_status","interviewerName":"admin"}'
Check 'T9.3 非法状态值被拒绝' ((Field $r 'error') -eq $true) $r

$r = Api 'POST' '/delete-all' '{"password":"wrong"}'
Check 'T9.4 清空数据密码错误被拒绝' ((Field $r 'error') -eq $true) $r

$r = Api 'GET' '/nonexistent-endpoint'
Check 'T9.5 不存在的接口返回错误对象' ((Field $r 'error') -eq $true) $r

# ============ 10. 删除单条 ============
$r = Api 'DELETE' "/$id2"
Check 'T10.1 删除单个考生成功' ((Field $r 'success') -eq $true) $r

$r = Api 'GET' '/'
$all = $r | ConvertFrom-Json
Check 'T10.2 删除后列表总数减少' ($all.total -le 1) ("total=" + $all.total)

# ============ 汇总 ============
$total = $script:passed + $script:failed
$summary = @(
  '',
  "==== 测试汇总 ====",
  "通过：$($script:passed) / $total",
  "失败：$($script:failed)",
  ''
) + $script:results

$logPath = Join-Path $PSScriptRoot 'test-result.log'
[IO.File]::WriteAllText($logPath, ($summary -join "`r`n"), (New-Object Text.UTF8Encoding $false))
Write-Host ''
Write-Host "通过 $($script:passed) / $total，失败 $($script:failed)，报告：$logPath"
