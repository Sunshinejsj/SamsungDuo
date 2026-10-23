# 本地发布：构建 release APK 并上传到 GitHub Release（在线更新的数据源）。
#
# 用法：
#   .\release.ps1 -Version 9.4                  # 构建 + 打 tag + 建 Release + 传 APK
#   .\release.ps1 -Version 9.4 -DryRun          # 只构建并核对版本，不碰 GitHub
#   .\release.ps1 -Version 9.4 -NotesFile .\notes.md
#   .\release.ps1 -Version 9.4 -VersionCode 940 # 版本号规则不够用时手工指定
#
# 版本号规则（★ v85 重写，上游那套 major*10000 已废弃）：
#   versionName 原样写进 APK 与 tag（tag = "v" + versionName）；
#   versionCode 默认 = major*10 + minor（9.2 -> 92，与 app/build.gradle.kts 里
#   checked-in 的默认值同一套）。minor >= 10 时会算重（9.10 与 10.0 都是 100），
#   这种情况必须用 -VersionCode 手工指定。
#
# 发完 app 里的「检查更新」就能看到这一版：updater 读
#   https://api.github.com/repos/Sunshinejsj/SamsungDuo/releases/latest
# 所以 Release 必须是**正式版**（不能是 draft / prerelease，那个接口不返回它们），
# 且必须带上 .apk 资源。
#
# ★ 发布只走本脚本：GitHub Actions 建的 Release 署名是 github-actions[bot]，
#   发布页显示的是机器人而不是仓库主人；本脚本用仓库主人自己的凭据发布，
#   署名才是主人。.github/workflows/build.yml 因此只做构建校验、不再发布。
#
# 前置条件（踩过的坑）：
#   * PowerShell 里要有 git：$env:PATH = "<PortableGit mingw64\bin>;$env:PATH"
#     （脚本内部用裸 git 取凭据、打 tag、推 tag）
#   * 走代理时同样要把 http_proxy / https_proxy 传进本会话
#
# 凭据：复用 git credential（首次 push / 发布会弹浏览器登录一次 GitHub）。
# 前提：git remote origin 已指向 https://github.com/Sunshinejsj/SamsungDuo.git

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    # Release 说明（Markdown）。不传则只有标题，GitHub 显示为无正文。
    [string]$Notes,

    # 与 -Notes 二选一：从文件读取说明，便于写多行中文。
    [string]$NotesFile,

    # 跳过上传与打 tag，只产出 dist\SamsungDuo-<version>.apk 并核对版本。
    [switch]$DryRun,

    # 已存在同名 Release 时覆盖它（默认报错停下，避免覆盖错版本）。
    [switch]$Force,

    # versionCode 手工指定（版本号规则不够用时）。
    [int]$VersionCode = 0
)

$ErrorActionPreference = 'Stop'

$Root = $PSScriptRoot
$Repo = 'Sunshinejsj/SamsungDuo'
$Dist = Join-Path $Root 'dist'

# 原生程序把进度和警告写到 stderr（git 的 "Everything up-to-date"、gradle 的弃用提示）。
# 在 $ErrorActionPreference='Stop' 下，一旦把 stderr 并进管道，这些就变成终止错误，
# 会把一个成功的步骤当成失败中止整个发布。所以这里临时放宽，只用 exit code 判成败。
function Invoke-Native {
    param([string]$Exe, [string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Exe @Arguments 2>&1
        return [pscustomobject]@{
            Output   = (@($output | ForEach-Object { "$_" }) -join "`n").Trim()
            ExitCode = $LASTEXITCODE
        }
    } finally {
        $ErrorActionPreference = $previous
    }
}

# ---- 版本号 -----------------------------------------------------------------

if ($Version -notmatch '^\d+(\.\d+){1,2}$') {
    throw "版本号至少要是 major.minor（收到 '$Version'）"
}
$parts = @($Version.Split('.') | ForEach-Object { [int]$_ })
$major = $parts[0]
$minor = $parts[1]

if ($VersionCode -le 0) {
    if ($minor -ge 10) {
        throw "minor >= 10 时默认规则会算重（9.10 与 10.0 同码），请用 -VersionCode 手工指定"
    }
    $VersionCode = $major * 10 + $minor
}

$tag   = "v$Version"
$asset = "SamsungDuo-$Version.apk"

Write-Host "==> SamsungDuo $Version (versionCode $VersionCode), tag $tag" -ForegroundColor Cyan

# ---- 构建 -------------------------------------------------------------------
# 本机构建链：系统 JDK 21 + 系统 Android SDK + gradle wrapper（--offline，
# 依赖 .tools/maven 里 vendored 的 libxposed 制品）。与 .github/workflows/build.yml
# 里"push 一个 v* tag 让 GitHub 自己构建"那条路径二选一，不冲突。

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:ANDROID_HOME = 'C:\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'C:\Android\Sdk'

$build = Invoke-Native (Join-Path $Root 'gradlew.bat') @(
    ':app:assembleRelease', '--offline', '--console=plain',
    "-PsamsungDuoVersionName=$Version",
    "-PsamsungDuoVersionCode=$VersionCode"
)
$build.Output -split "`n" |
    Where-Object { $_ -match '^BUILD |^FAILURE|error:' } |
    ForEach-Object { Write-Host "    $_" }
if ($build.ExitCode -ne 0) { throw "Gradle 构建失败（exit $($build.ExitCode)）" }

# 产物命名：build.gradle.kts 把 release APK 重命名为 SamsungDuo-{versionName}.apk
# （见 app/build.gradle.kts 的 androidComponents 命名规则），旧名 app-release.apk
# 只作兜底，否则会误判"没有找到构建产物"。
$built = Join-Path $Root "app\build\outputs\apk\release\SamsungDuo-$Version.apk"
if (-not (Test-Path $built)) {
    $built = Join-Path $Root 'app\build\outputs\apk\release\app-release.apk'
}
if (-not (Test-Path $built)) { throw "没有找到构建产物：$built" }

New-Item -ItemType Directory -Force -Path $Dist | Out-Null
$target = Join-Path $Dist $asset
Copy-Item $built $target -Force

# 构建出来的是什么版本，以 APK 自己为准 —— 版本注入写错时这里会立刻暴露。
$aapt = Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Filter aapt2.exe -Recurse |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $aapt) { throw "找不到 aapt2.exe（$env:ANDROID_HOME\build-tools\*）" }
$badgingRun = Invoke-Native $aapt.FullName @('dump', 'badging', $target)
$badging = [string]($badgingRun.Output -split "`n" |
    Where-Object { $_ -match '^package:' } | Select-Object -First 1)
if (-not $badging) { throw "无法读取 $target 的包信息" }
Write-Host "    $badging"
if ($badging -notmatch "versionCode='$VersionCode'" -or $badging -notmatch "versionName='$Version'") {
    throw "APK 里的版本与预期不符，已中止发布"
}

Write-Host "==> 产物：$target" -ForegroundColor Green

if ($DryRun) {
    Write-Host '==> -DryRun：跳过 tag 与上传' -ForegroundColor Yellow
    return
}

# ---- 凭据 -------------------------------------------------------------------

function Get-GitHubToken {
    $tmp = Join-Path $env:TEMP ("sd_cred_" + [guid]::NewGuid().ToString('N') + '.txt')
    try {
        # git 只从 stdin 读协议行，PowerShell 管道塞不满它的期望，用 cmd 重定向。
        [System.IO.File]::WriteAllText($tmp, "protocol=https`nhost=github.com`n`n")
        $raw = (Invoke-Native 'cmd' @('/c', "git credential fill < `"$tmp`"")).Output
        $line = $raw -split "`n" | Where-Object { $_ -match '^password=' } | Select-Object -First 1
        if (-not $line) { throw "git credential 里没有 github.com 的凭据（先在本地 push 一次让 GCM 登录）" }
        return $line.Substring('password='.Length).Trim()
    } finally {
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}

$token = Get-GitHubToken
$headers = @{
    Authorization = "Bearer $token"
    Accept        = 'application/vnd.github+json'
    'User-Agent'  = 'SamsungDuo-Releaser'
}

# ---- 打 tag -----------------------------------------------------------------

$dirty = (Invoke-Native 'git' @('status', '--porcelain')).Output
if ($dirty) { throw "工作区有未提交改动，先 commit 再发布（否则 tag 里的代码和 APK 不一致）" }

$exists = Invoke-Native 'git' @('rev-parse', '-q', '--verify', "refs/tags/$tag")
if ($exists.ExitCode -eq 0) {
    if (-not $Force) { throw "tag $tag 已存在；确认要覆盖就加 -Force" }
    Invoke-Native 'git' @('tag', '-f', $tag) | Out-Null
} else {
    Invoke-Native 'git' @('tag', $tag) | Out-Null
}
Write-Host "==> tag $tag" -ForegroundColor Green

$push = Invoke-Native 'git' @('push', '-f', 'origin', "refs/tags/$tag")
if ($push.ExitCode -ne 0) {
    Write-Host $push.Output
    throw "推送 tag 失败"
}

# ---- 建 Release + 传 APK ----------------------------------------------------

$releaseUrl = "https://api.github.com/repos/$Repo/releases"
$existing = $null
try {
    $existing = Invoke-RestMethod -Headers $headers -Uri "$releaseUrl/tags/$tag" -Method Get -ErrorAction Stop
} catch {
    # 404 = 还没有这个 Release，正常
}
if ($existing -and -not $Force) {
    throw "Release $tag 已存在；确认要覆盖就加 -Force"
}

$body = @{
    tag_name   = $tag
    name       = "SamsungDuo $Version"
    body       = if ($NotesFile) { [System.IO.File]::ReadAllText($NotesFile, [System.Text.Encoding]::UTF8) } elseif ($Notes) { $Notes } else { "" }
    draft      = $false
    prerelease = $false   # ★ 必须是 false：updater 读的 releases/latest 不返回 prerelease
}
if ($existing) {
    $release = Invoke-RestMethod -Headers $headers -Uri $existing.url -Method Patch -Body ($body | ConvertTo-Json)
} else {
    $release = Invoke-RestMethod -Headers $headers -Uri $releaseUrl -Method Post -Body ($body | ConvertTo-Json)
}
Write-Host "==> Release：$($release.html_url)" -ForegroundColor Green

# 同名资源先删再传，否则 GitHub 报 422（-Force 覆盖的场景会踩到）
$release.assets | ForEach-Object {
    if ($_.name -eq $asset) {
        Invoke-RestMethod -Headers $headers -Uri $_.url -Method Delete | Out-Null
    }
}

$upload = Invoke-RestMethod -Headers $headers `
    -Uri "$($release.upload_url.Split('{')[0])?name=$asset" `
    -Method Post -InFile $target -ContentType 'application/vnd.android.package-archive'
Write-Host "==> 已上传 $($upload.name)（$([math]::Round($upload.size / 1MB, 2)) MB）" -ForegroundColor Green
Write-Host "==> 完成。手机上：设置 → 关于 → 检查更新。" -ForegroundColor Green
