<#
  补充验证：一面评分/意向部门是否随考生带入二面队列（二面资料备选）
  前提：后端 8080 已启动、PostgreSQL 就绪；会清空考生数据
#>
$ErrorActionPreference = 'Continue'
$BASE = 'http://127.0.0.1:8080/api/candidates'
$script:passed = 0; $script:failed = 0

function Api {
  param([string]$Method, [string]$Path, [string]$Json = $null)
  $cargs = @('-s', '-X', $Method, '-H', 'Accept: application/json')
  if ($Json) {
    $tmp = Join-Path $env:TEMP ('vreq_' + [guid]::NewGuid().ToString('N') + '.json')
    [IO.File]::WriteAllText($tmp, $Json, (New-Object Text.UTF8Encoding $false))
    $cargs += @('-H', 'Content-Type: application/json', '--data-binary', "@$tmp")
  }
  $out = & curl.exe @cargs "$BASE$Path" 2>$null
  if ($Json) { Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue }
  return $out
}
function Check { param([string]$Name, [bool]$Cond, [string]$Detail = '')
  if ($Cond) { $script:passed++; Write-Host "[PASS] $Name" } else { $script:failed++; Write-Host "[FAIL] $Name  $Detail" }
}

# 清库
Api 'POST' '/delete-all' '{"password":"change-me"}' | Out-Null

# 1. 预报名（意向部门 A/B）
$stu = '{"name":"二面资料验证","gender":"男","className":"软件技术1班","phone":"13900000001","firstDepartment":"部门A","secondDepartment":"部门B","acceptAdjust":"是"}'
$c = (Api 'POST' '/pre-register/public' $stu) | ConvertFrom-Json
$id = $c.id
Check '预报名成功' ($null -ne $id -and $id -ne '')

# 2. 签到 + 分组
Api 'POST' '/public/check-in' "{`"candidateId`":`"$id`"}" | Out-Null
Api 'POST' '/assign-group' "{`"candidateId`":`"$id`",`"groupName`":`"A`",`"operatorName`":`"admin`"}" | Out-Null

# 3. 叫号 + 三位面试官打分（打满 3 人自动完成一面）
Api 'PATCH' "/$id/status" '{"status":"interviewing","interviewerName":"面试官A1"}' | Out-Null
Api 'POST' "/$id/score" '{"score":8.5,"interviewerName":"面试官A1","judgeComment":"表达清晰"}' | Out-Null
Api 'POST' "/$id/score" '{"score":7.0,"interviewerName":"面试官A2","judgeComment":"逻辑较好"}' | Out-Null
$third = (Api 'POST' "/$id/score" '{"score":8.0,"interviewerName":"面试官A3","judgeComment":"潜力较好"}') | ConvertFrom-Json
Check '三人打满后一面自动完成' ($third.status -eq 'first_completed') ($third | ConvertTo-Json -Compress)

# 5. 移入二面 + 分配二面 E 组
$mv = (Api 'POST' '/second/move-to' "{`"candidateId`":`"$id`",`"operatorName`":`"admin`"}") | ConvertFrom-Json
Check '移入二面待面试' ($mv.status -eq 'second_waiting') ($mv | ConvertTo-Json -Compress)
$ag = (Api 'POST' '/second/assign-group' "{`"candidateId`":`"$id`",`"groupName`":`"E`",`"operatorName`":`"admin`"}") | ConvertFrom-Json
Check '分配二面 E 组' ($ag.groupName -eq 'E') ($ag | ConvertTo-Json -Compress)

# 6. 二面队列应带一面资料
$q = (Api 'GET' '/second/queue') | ConvertFrom-Json
$mine = @($q.waiting) | Where-Object { $_.id -eq $id } | Select-Object -First 1
Check '考生出现在二面等待队列' ($null -ne $mine)
if ($mine) {
  Check '二面队列带回一面平均分' ($null -ne $mine.averageScore) ("avg=" + $mine.averageScore)
  Check '二面队列带回一面评分明细(3条)' (@($mine.scores).Count -eq 3) ("scores=" + @($mine.scores).Count)
  Check '二面队列带回第一意向部门' ($mine.firstDepartment -eq '部门A') ($mine.firstDepartment)
  Check '二面队列带回第二意向部门' ($mine.secondDepartment -eq '部门B') ($mine.secondDepartment)
  $names = @($mine.scores | ForEach-Object { $_.interviewerName }) -join ','
  Check '评分明细含三位面试官姓名' ($names -match '面试官A1' -and $names -match '面试官A2' -and $names -match '面试官A3') $names
}

Write-Host ""
Write-Host "通过 $($script:passed) / $($script:passed + $script:failed)，失败 $($script:failed)"
