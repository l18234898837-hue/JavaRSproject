[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$SourceRoot,

    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$RunDirectory,

    [string]$PackageId,

    [ValidateSet("EPSG:4326", "EPSG:4490")]
    [string]$TargetCrs = "EPSG:4326",

    [switch]$EnableOptionalAlgorithms,

    [Parameter(Mandatory)]
    [switch]$ConfirmFullDisk
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Write-JsonFile {
    param([Parameter(Mandatory)]$Value, [Parameter(Mandatory)][string]$Path)
    $Value | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $Path -Encoding utf8
}

function Read-JsonFile {
    param([Parameter(Mandatory)][string]$Path)
    return Get-Content -LiteralPath $Path -Raw -Encoding utf8 | ConvertFrom-Json -AsHashtable
}

function Get-PackageFiles {
    param([Parameter(Mandatory)][string]$Root, [string]$SelectedPackageId)
    $pattern = '^FY4B-_AGRI--_N_DISK_(?<subsat>[^_]+)_L1-_(?<kind>FDI|GEO)-_MULT_NOM_(?<start>\d{14})_(?<end>\d{14})_(?<resolution>0500M|500M|1000M|2000M|4000M)_(?<version>V[^.]+)\.HDF$'
    $roleMap = @{
        'FDI:0500M' = 'L1_0500M'; 'FDI:500M' = 'L1_0500M'; 'FDI:1000M' = 'L1_1000M';
        'FDI:2000M' = 'L1_2000M'; 'FDI:4000M' = 'L1_4000M'; 'GEO:4000M' = 'GEO_4000M'
    }
    $groups = @{}
    Get-ChildItem -LiteralPath $Root -Recurse -File -Filter '*.HDF' | ForEach-Object {
        $match = [regex]::Match($_.Name, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if (-not $match.Success) { return }
        $package = @($match.Groups['subsat'].Value, $match.Groups['start'].Value, $match.Groups['end'].Value, $match.Groups['version'].Value) -join '_'
        $role = $roleMap["$($match.Groups['kind'].Value.ToUpperInvariant()):$($match.Groups['resolution'].Value.ToUpperInvariant())"]
        if ($null -eq $role) { return }
        if (-not $groups.ContainsKey($package)) { $groups[$package] = @{} }
        if ($groups[$package].ContainsKey($role)) { throw "FY-4B package '$package' has duplicate role '$role'." }
        $groups[$package][$role] = $_.FullName
    }
    $required = @('L1_0500M', 'L1_1000M', 'L1_2000M', 'L1_4000M', 'GEO_4000M')
    $complete = @($groups.Keys | Where-Object { @($groups[$_].Keys | Sort-Object) -join ',' -eq @($required | Sort-Object) -join ',' })
    if ($SelectedPackageId) { $complete = @($complete | Where-Object { $_ -eq $SelectedPackageId }) }
    if ($complete.Count -ne 1) { throw "Expected exactly one complete FY-4B package; found $($complete.Count). Supply -PackageId when the source contains more than one package." }
    $selected = $groups[$complete[0]]
    return @{ PackageId = $complete[0]; Files = @($required | ForEach-Object { @{ Role = $_; Path = $selected[$_] } }) }
}

function Invoke-Worker {
    param([Parameter(Mandatory)][hashtable]$Request, [Parameter(Mandatory)][hashtable]$Manifest, [Parameter(Mandatory)][string]$StageDirectory)
    $requestPath = Join-Path $StageDirectory 'request.json'
    $manifestPath = Join-Path $StageDirectory 'asset-manifest.json'
    $responsePath = Join-Path $StageDirectory 'response.json'
    Write-JsonFile $Request $requestPath
    Write-JsonFile $Manifest $manifestPath
    & $script:Python -m rs_algorithm_worker.cli --request $requestPath --asset-manifest $manifestPath --response $responsePath
    $label = $Request.operation
    if ($Request.operation -eq 'execute-stage' -and $Request.payload.ContainsKey('stageKey')) { $label = $Request.payload.stageKey }
    if ($LASTEXITCODE -ne 0) { throw "Worker CLI failed for '$label'. See '$responsePath'." }
    $response = Read-JsonFile $responsePath
    if ($response.ContainsKey('error') -or $response.payload.status -ne 'SUCCEEDED') { throw "Worker rejected '$label'. See '$responsePath'." }
    return $response.payload
}

function New-Manifest {
    param([Parameter(Mandatory)][array]$Inputs, [Parameter(Mandatory)][string]$StageDirectory, [Parameter(Mandatory)][string]$RequestId)
    return @{
        manifestVersion = '1.0'; requestId = $RequestId; grantReference = "manual-fy4b-$RequestId"
        expiresAt = [DateTime]::UtcNow.AddHours(48).ToString('o')
        inputs = $Inputs; output = @{ path = (Join-Path $StageDirectory 'output') }
    }
}

function New-RequestInputs {
    param([Parameter(Mandatory)][array]$ManifestInputs)
    return @($ManifestInputs | ForEach-Object { @{ storageUriSnapshot = @{ value = $_.storageUri } } })
}

function Assert-Artifact {
    param([Parameter(Mandatory)][hashtable]$Artifact, [Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Worker reported '$($Artifact.type)' but did not create '$Path'." }
    $checksum = 'sha256:' + (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($Artifact.checksum -ne $checksum) { throw "Checksum mismatch for '$($Artifact.type)'." }
}

$source = (Resolve-Path -LiteralPath $SourceRoot -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $source -PathType Container)) { throw 'SourceRoot must be a directory.' }
$run = [IO.Path]::GetFullPath($RunDirectory)
if ((Split-Path -Qualifier $run).TrimEnd(':') -ne 'D') { throw 'RunDirectory must be located on D:.' }
if (Test-Path -LiteralPath $run) { throw "RunDirectory already exists: '$run'. Choose a new empty run directory." }

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$workerRoot = Join-Path $repositoryRoot 'rs-algorithm-worker'
$script:Python = Join-Path $workerRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $script:Python -PathType Leaf)) { throw "Worker virtual environment is missing: '$script:Python'." }

$package = Get-PackageFiles -Root $source -SelectedPackageId $PackageId
$parameters = @{
    schemaVersion = '1'; targetCrs = $TargetCrs
    features = @{ enabled = $EnableOptionalAlgorithms.IsPresent; metrics = @('statistics', 'gradient', 'local_variance', 'toa_nir_red_nd', 'btd_c13_c14'); windowSize = 5 }
    fusion = @{ enabled = $EnableOptionalAlgorithms.IsPresent; mode = 'NIR_RED_BLUE'; percentiles = @(2, 98) }
    saliency = @{ enabled = $EnableOptionalAlgorithms.IsPresent; source = 'btd_c13_c14'; threshold = 0.55; windowSize = 5 }
}

New-Item -ItemType Directory -Path $run | Out-Null
$rawInputs = @()
for ($index = 0; $index -lt $package.Files.Count; $index++) {
    $file = $package.Files[$index]
    $rawInputs += @{ storageUri = "rs://input/manual-fy4b/$($file.Role)"; role = $file.Role; ordinal = $index; path = [IO.Path]::GetFullPath($file.Path); checksum = 'sha256:' + (Get-FileHash -LiteralPath $file.Path -Algorithm SHA256).Hash.ToLowerInvariant() }
}

try {
    $bootstrap = Join-Path $run '00-plan'
    New-Item -ItemType Directory -Path $bootstrap | Out-Null
    $inspectRequest = @{ schemaVersion = '1.0'; operation = 'inspect'; requestId = 'fy4b-inspect'; payload = @{ inputs = (New-RequestInputs $rawInputs) } }
    $inspect = Invoke-Worker -Request $inspectRequest -Manifest (New-Manifest $rawInputs $bootstrap 'fy4b-inspect') -StageDirectory $bootstrap
    $candidate = @($inspect.candidates | Where-Object { $_.packageId -eq $package.PackageId })
    if ($candidate.Count -ne 1) { throw "Inspect did not recognize expected package '$($package.PackageId)'." }
    $planRequest = @{ schemaVersion = '1.0'; operation = 'build-plan'; requestId = 'fy4b-plan'; payload = @{ candidate = $candidate[0]; parameterSnapshot = $parameters } }
    $plan = Invoke-Worker -Request $planRequest -Manifest (New-Manifest $rawInputs $bootstrap 'fy4b-plan') -StageDirectory $bootstrap
    if ($plan.version -ne 'plan-v2') { throw 'Worker did not return FY-4B plan-v2.' }
    Write-JsonFile $plan (Join-Path $run 'processing-plan.json')

    $available = @{}
    foreach ($input in $rawInputs) { $available[$input.role] = @{ path = $input.path; storageUri = $input.storageUri; checksum = $input.checksum } }
    for ($sequence = 0; $sequence -lt $plan.stages.Count; $sequence++) {
        $stage = $plan.stages[$sequence]
        $stageDirectory = Join-Path $run ("{0:D2}-{1}" -f ($sequence + 1), $stage.stageKey)
        New-Item -ItemType Directory -Path $stageDirectory | Out-Null
        $stageInputs = @()
        for ($ordinal = 0; $ordinal -lt $stage.inputs.Count; $ordinal++) {
            $declared = $stage.inputs[$ordinal]
            $artifact = $available[$declared.artifactType]
            if ($null -eq $artifact) { throw "Stage '$($stage.stageKey)' requires unavailable '$($declared.artifactType)'." }
            $stageInputs += @{ storageUri = $artifact.storageUri; role = $declared.key; ordinal = $ordinal; path = $artifact.path; checksum = $artifact.checksum; artifactType = $declared.artifactType }
        }
        $requestId = "fy4b-stage-$sequence"
        $request = @{ schemaVersion = '1.0'; operation = 'execute-stage'; requestId = $requestId; payload = @{ profileCode = 'FY-4B-AGRI'; stageKey = $stage.stageKey; outputUri = "rs://output/manual-fy4b/$sequence"; parameterSnapshot = $parameters; inputs = (New-RequestInputs $stageInputs) } }
        $result = Invoke-Worker -Request $request -Manifest (New-Manifest $stageInputs $stageDirectory $requestId) -StageDirectory $stageDirectory
        foreach ($artifact in $result.artifacts) {
            $output = @($stage.outputs | Where-Object { $_.artifactType -eq $artifact.type })
            if ($output.Count -ne 1) { throw "Stage '$($stage.stageKey)' returned undeclared artifact '$($artifact.type)'." }
            $artifactPath = Join-Path (Join-Path $stageDirectory 'output') $output[0].relativePath
            Assert-Artifact -Artifact $artifact -Path $artifactPath
            $available[$artifact.type] = @{ path = $artifactPath; storageUri = $artifact.uri; checksum = $artifact.checksum }
        }
        $missing = @($stage.outputs | Where-Object { -not $available.ContainsKey($_.artifactType) })
        if ($missing.Count -gt 0) { throw "Stage '$($stage.stageKey)' did not return all declared artifacts." }
        Write-Host "Completed $($stage.stageKey)" -ForegroundColor Green
    }
    Write-JsonFile $available (Join-Path $run 'artifact-index.json')
    Write-Host "FY-4B full-disk execution succeeded. Outputs and logs are under '$run'." -ForegroundColor Green
}
catch {
    Write-Error "FY-4B execution failed. Preserve '$run' for diagnosis. $($_.Exception.Message)"
    exit 1
}
