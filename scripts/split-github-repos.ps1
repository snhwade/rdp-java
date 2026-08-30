# 多仓库拆分与 GitHub 推送
#
# 目标结构（账号 snhwade）：
#   risk-decision-admin-console    管理控制台前端
#   risk-decision-services         Java 微服务后端
#   risk-decision-data-engine      数据引擎（Flink 等旁路计算作业）
#   risk-decision-ai-training      Python AI 训练/评分
#   risk-decision-commons          公共 Java 库
#
# 用法：
#   powershell -File scripts/split-github-repos.ps1              # 仅本地拆分
#   powershell -File scripts/split-github-repos.ps1 -Push        # 拆分并推送到 GitHub（需 gh auth login）
#
param(
    [string]$ExportRoot = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) "rdp-repos"),
    [string]$GithubOwner = "snhwade",
    [switch]$Push,
    [switch]$Private = $false,
    [switch]$ForcePush
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$AiRoot = Join-Path (Split-Path -Parent $Root) "ai-training-service"
if (-not (Test-Path $AiRoot)) {
    $AiRoot = Join-Path $Root "..\ai-training-service" | Resolve-Path -ErrorAction SilentlyContinue
}

$ExcludeDirs = @("target", "node_modules", ".idea", ".pytest_cache", ".vite", "dist", "coverage", ".service-logs")

function Copy-Tree {
    param([string]$Source, [string]$Dest, [string[]]$ExtraExclude = @())
    if (-not (Test-Path $Source)) {
        throw "Source not found: $Source"
    }
    New-Item -ItemType Directory -Force -Path $Dest | Out-Null
    $xd = ($ExcludeDirs + $ExtraExclude) | ForEach-Object { "/XD", $_ }
    & robocopy $Source $Dest /E /NFL /NDL /NJH /NJS /nc /ns /np @xd /XF *.class | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy failed: $Source -> $Dest (exit $LASTEXITCODE)" }
}

$ReadmeDir = Join-Path $PSScriptRoot "readmes"

$RepoCatalog = @(
    @{ Folder = "risk-decision-commons";       Readme = "risk-decision-commons.md" }
    @{ Folder = "risk-decision-services";      Readme = "risk-decision-services.md" }
    @{ Folder = "risk-decision-data-engine"; Readme = "risk-decision-data-engine.md" }
    @{ Folder = "risk-decision-ai-training";   Readme = "risk-decision-ai-training.md" }
    @{ Folder = "risk-decision-admin-console"; Readme = "risk-decision-admin-console.md" }
)

function Install-RepoReadme {
    param([string]$Path, [string]$ReadmeFile)
    $src = Join-Path $ReadmeDir $ReadmeFile
    if (-not (Test-Path $src)) { throw "README template not found: $src" }
    Copy-Item $src (Join-Path $Path "README.md") -Force
}

function Init-GitRepo {
    param([string]$Path, [string]$Message = "Initial commit")
    Push-Location $Path
    try {
        if (Test-Path ".git") {
            Remove-Item -Recurse -Force ".git"
        }
        git init -b main | Out-Null
        git add -A
        git commit -m $Message | Out-Null
    } finally {
        Pop-Location
    }
}

function Push-GithubRepo {
    param([string]$Path, [string]$RepoName, [switch]$Force)
    $gh = "$env:USERPROFILE\tools\gh\bin\gh.exe"
    if (-not (Test-Path $gh)) {
        $ghCmd = Get-Command gh -ErrorAction SilentlyContinue
        if ($ghCmd) { $gh = $ghCmd.Source } else { throw "GitHub CLI (gh) not found. Run: gh auth login" }
    }
    & $gh auth status 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Not logged in to GitHub. Run: gh auth login" }
    & $gh auth setup-git 2>&1 | Out-Null

    Push-Location $Path
    try {
        $full = "$GithubOwner/$RepoName"
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & $gh repo view $full 2>$null | Out-Null
        $repoExists = ($LASTEXITCODE -eq 0)
        $ErrorActionPreference = $prevEap
        if (-not $repoExists) {
            $vis = if ($Private) { "--private" } else { "--public" }
            & $gh repo create $full $vis --source=. --remote=origin | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "gh repo create failed for $full" }
        } else {
            $ErrorActionPreference = "Continue"
            $originUrl = git remote get-url origin 2>$null
            $ErrorActionPreference = $prevEap
            if (-not $originUrl) {
                git remote add origin "https://github.com/$full.git"
            }
        }
        $pushArgs = @("-u", "origin", "main")
        if ($Force) { $pushArgs = @("--force") + $pushArgs }
        git push @pushArgs
        if ($LASTEXITCODE -ne 0) { throw "git push failed for $full" }
    } finally {
        Pop-Location
    }
    Write-Host "  pushed: https://github.com/$GithubOwner/$RepoName"
}

Write-Host "========================================"
Write-Host "  Split risk-decision-platform -> 5 repos"
Write-Host "  Export: $ExportRoot"
Write-Host "========================================"

if (Test-Path $ExportRoot) {
    Write-Host "Cleaning $ExportRoot ..."
    Remove-Item -Recurse -Force $ExportRoot
}
New-Item -ItemType Directory -Force -Path $ExportRoot | Out-Null

# --- 1. risk-decision-commons ---
Write-Host "`n==> risk-decision-commons"
$commonsDir = Join-Path $ExportRoot "risk-decision-commons"
Copy-Tree (Join-Path $Root "commons-core") (Join-Path $commonsDir "commons-core")
Copy-Item (Join-Path $Root "pom.xml") (Join-Path $commonsDir "pom.xml")
$commonsPom = Get-Content (Join-Path $commonsDir "pom.xml") -Raw
$commonsPom = $commonsPom -replace "<artifactId>risk-decision-platform</artifactId>", "<artifactId>rdp-commons</artifactId>"
$commonsPom = $commonsPom -replace "<name>risk-decision-platform</name>", "<name>rdp-commons</name>"
$commonsModules = @'
    <modules>
        <module>commons-core</module>
    </modules>
'@
$commonsPom = $commonsPom -replace '(?s)<modules>.*?</modules>', $commonsModules
Set-Content (Join-Path $commonsDir "pom.xml") $commonsPom -Encoding UTF8
$ccPom = Get-Content (Join-Path $commonsDir "commons-core\pom.xml") -Raw
$ccPom = $ccPom -replace "risk-decision-platform", "rdp-commons"
Set-Content (Join-Path $commonsDir "commons-core\pom.xml") $ccPom -Encoding UTF8
Copy-Item (Join-Path $Root ".gitignore") (Join-Path $commonsDir ".gitignore") -ErrorAction SilentlyContinue
Install-RepoReadme $commonsDir "risk-decision-commons.md"

# --- 2. risk-decision-services ---
Write-Host "`n==> risk-decision-services"
$javaDir = Join-Path $ExportRoot "risk-decision-services"
$javaModules = @(
    "admin-bff", "decision-gateway", "rule-config-service", "rule-decision-engine",
    "indicator-store-service", "screening-service", "merchant-rating-service"
)
foreach ($m in $javaModules) {
    Copy-Tree (Join-Path $Root $m) (Join-Path $javaDir $m)
}
foreach ($extra in @("docs", "scripts", "deploy")) {
    if (Test-Path (Join-Path $Root $extra)) {
        Copy-Tree (Join-Path $Root $extra) (Join-Path $javaDir $extra)
    }
}
Copy-Item (Join-Path $Root "pom.xml") (Join-Path $javaDir "pom.xml")
Copy-Item (Join-Path $Root ".gitignore") (Join-Path $javaDir ".gitignore") -ErrorAction SilentlyContinue
$javaPom = Get-Content (Join-Path $javaDir "pom.xml") -Raw
$javaPom = $javaPom -replace "<artifactId>risk-decision-platform</artifactId>", "<artifactId>rdp-java</artifactId>"
$javaPom = $javaPom -replace "<name>risk-decision-platform</name>", "<name>rdp-java</name>"
$javaModulesXml = @'
    <modules>
        <module>decision-gateway</module>
        <module>rule-config-service</module>
        <module>rule-decision-engine</module>
        <module>indicator-store-service</module>
        <module>screening-service</module>
        <module>merchant-rating-service</module>
        <module>admin-bff</module>
    </modules>
'@
$javaPom = $javaPom -replace '(?s)<modules>.*?</modules>', $javaModulesXml
if ($javaPom -notmatch 'commons-core') {
    $commonsDep = @'
            <dependency>
                <groupId>com.riskplatform</groupId>
                <artifactId>commons-core</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
'@
    $javaPom = $javaPom -replace '(?s)</dependencies>\s*</dependencyManagement>', $commonsDep
}
Set-Content (Join-Path $javaDir "pom.xml") $javaPom -Encoding UTF8
Get-ChildItem $javaDir -Recurse -Filter pom.xml | ForEach-Object {
    $childPom = Get-Content $_.FullName -Raw
    $childPom = $childPom -replace '<artifactId>risk-decision-platform</artifactId>', '<artifactId>rdp-java</artifactId>'
    Set-Content $_.FullName $childPom -Encoding UTF8
}
Install-RepoReadme $javaDir "risk-decision-services.md"

# --- 3. risk-decision-data-engine ---
Write-Host "`n==> risk-decision-data-engine"
$engineDir = Join-Path $ExportRoot "risk-decision-data-engine"
$moduleDir = Join-Path $engineDir "indicator-accumulation"
Copy-Tree (Join-Path $Root "indicator-accumulation") $moduleDir @("dependency-reduced-pom.xml")
Copy-Item (Join-Path $PSScriptRoot "templates\data-engine-parent.pom.xml") (Join-Path $engineDir "pom.xml") -Force
$childPom = Get-Content (Join-Path $moduleDir "pom.xml") -Raw
$childPom = $childPom -replace '<artifactId>risk-decision-platform</artifactId>', '<artifactId>risk-decision-data-engine</artifactId>'
Set-Content (Join-Path $moduleDir "pom.xml") $childPom -Encoding UTF8
Copy-Item (Join-Path $Root ".gitignore") (Join-Path $engineDir ".gitignore") -ErrorAction SilentlyContinue
Install-RepoReadme $engineDir "risk-decision-data-engine.md"

# --- 4. risk-decision-ai-training ---
Write-Host "`n==> risk-decision-ai-training"
$aiDir = Join-Path $ExportRoot "risk-decision-ai-training"
if (-not $AiRoot -or -not (Test-Path $AiRoot)) {
    Write-Warning "ai-training-service not found; skip rdp-ai-training"
} else {
    Copy-Tree $AiRoot $aiDir
    @"
# Python
__pycache__/
*.py[cod]
.venv/
venv/
.env
.env.*
.pytest_cache/
*.egg-info/
dist/
build/
"@ | Set-Content (Join-Path $aiDir ".gitignore") -Encoding UTF8
    Install-RepoReadme $aiDir "risk-decision-ai-training.md"
}

# --- 5. risk-decision-admin-console ---
Write-Host "`n==> risk-decision-admin-console"
$feDir = Join-Path $ExportRoot "risk-decision-admin-console"
Copy-Tree (Join-Path $Root "admin-console") $feDir
Install-RepoReadme $feDir "risk-decision-admin-console.md"

# --- Git init ---
Write-Host "`n==> Git init"
$repos = @("risk-decision-commons", "risk-decision-services", "risk-decision-data-engine", "risk-decision-admin-console")
if (Test-Path (Join-Path $ExportRoot "risk-decision-ai-training")) { $repos += "risk-decision-ai-training" }
foreach ($name in $repos) {
    $path = Join-Path $ExportRoot $name
    Write-Host "  init $name"
    Init-GitRepo $path "Initial commit: risk decision platform (neutral naming)"
}

# --- Push ---
if ($Push) {
    Write-Host "`n==> Push to GitHub ($GithubOwner)"
    foreach ($name in $repos) {
        Push-GithubRepo (Join-Path $ExportRoot $name) $name -Force
    }
} else {
    Write-Host "`nLocal split done. To push after 'gh auth login':"
    Write-Host "  powershell -File `"$PSScriptRoot\split-github-repos.ps1`" -Push"
}

Write-Host "`nRepos:"
Get-ChildItem $ExportRoot -Directory | ForEach-Object { Write-Host "  $($_.FullName)" }
