[CmdletBinding()]
param(
    [string]$KeystorePath = "$env:USERPROFILE\.android\debug.keystore",
    [string]$KeyAlias = "androiddebugkey",
    [string]$AndroidSdk,
    [switch]$PromptPassword
)
$ErrorActionPreference = "Stop"
$unsignedApk = Join-Path $PSScriptRoot "CoveRecipe-1.5.2-unsigned.apk"
$finalApk = Join-Path $PSScriptRoot "CoveRecipe-1.5.2.apk"
$expectedCert = "76013fa323f763be8c7caa569dee23dfd27ab1af745fa0dd064c8b64f2f06385"
$hashFile = Join-Path $PSScriptRoot "SHA256SUMS.txt"
$hashRecord = [string](Get-Content -LiteralPath $hashFile -TotalCount 1)
if ($hashRecord -notmatch '^([A-Fa-f0-9]{64})\s+\*?CoveRecipe-1\.5\.2-unsigned\.apk$') {
    throw "Unexpected checksum file. Extract all files from the build artifact together."
}
$expectedApkHash = $Matches[1]
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $unsignedApk).Hash -ne $expectedApkHash) { throw "Unsigned APK checksum mismatch." }
if (Test-Path -LiteralPath $finalApk) { throw "Output already exists: $finalApk. Move it before signing again." }
if (!(Test-Path -LiteralPath $KeystorePath -PathType Leaf)) { throw "Original keystore missing at $KeystorePath. Use -KeystorePath with the original file." }
$useDefaultPassword = !$PromptPassword -and ($KeystorePath -eq "$env:USERPROFILE\.android\debug.keystore") -and ($KeyAlias -eq "androiddebugkey")
$roots = @($AndroidSdk, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")
$directory = Get-Item -LiteralPath $PSScriptRoot
while ($directory) {
    $roots += Join-Path $directory.FullName ".tools\sdk"
    $directory = $directory.Parent
}
$signer = $null
foreach ($sdk in ($roots | Where-Object { $_ } | Select-Object -Unique)) {
    $buildTools = Join-Path $sdk "build-tools"
    if (!(Test-Path -LiteralPath $buildTools -PathType Container)) { continue }
    foreach ($version in (Get-ChildItem -LiteralPath $buildTools -Directory |
            Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending)) {
        $candidate = Join-Path $version.FullName "apksigner.bat"
        if (Test-Path -LiteralPath $candidate) { $signer = $candidate; break }
    }
    if ($signer) { break }
}
if (!$signer) { $signer = (Get-Command apksigner.bat -ErrorAction SilentlyContinue).Source }
if (!$signer) { throw "Android SDK Build Tools missing. Pass -AndroidSdk with the SDK directory used for the original build." }
if (!$env:JAVA_HOME -and !(Get-Command java.exe -ErrorAction SilentlyContinue)) {
    $studioJava = Join-Path $env:ProgramFiles "Android\Android Studio\jbr"
    if (Test-Path -LiteralPath (Join-Path $studioJava "bin\java.exe")) { $env:JAVA_HOME = $studioJava }
}
$tempApk = Join-Path $PSScriptRoot ("CoveRecipe-signing-" + [Guid]::NewGuid().ToString("N") + ".apk")
try {
    $arguments = @("sign", "--ks", $KeystorePath, "--ks-key-alias", $KeyAlias, "--v4-signing-enabled", "false")
    if ($useDefaultPassword) { $arguments += @("--ks-pass", "pass:android", "--key-pass", "pass:android") }
    & $signer @arguments --out $tempApk $unsignedApk
    if ($LASTEXITCODE -ne 0) { throw "Signing failed. For a custom password use -PromptPassword." }
    $certInfo = @(& $signer verify --verbose --print-certs $tempApk)
    if ($LASTEXITCODE -ne 0) { throw "Android signature verification failed." }
    $digests = @($certInfo | ForEach-Object { if ($_ -match '^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]+)\s*$') { $Matches[1] } })
    if ($digests.Count -ne 1 -or $digests[0] -ne $expectedCert) { throw "Certificate differs from Cove Recipe 1.4.0. Use the original Windows keystore." }
    [System.IO.File]::Move($tempApk, $finalApk)
    Write-Host "Verified the original 1.4.0 certificate. Signed APK: $finalApk"
    Write-Host ("APK SHA256: " + (Get-FileHash -Algorithm SHA256 -LiteralPath $finalApk).Hash)
} finally {
    if (Test-Path -LiteralPath $tempApk) { Remove-Item -LiteralPath $tempApk }
}
