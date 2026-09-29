[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^(docker\.io/)?gedmarc/entityassist-website(:[A-Za-z0-9_][A-Za-z0-9_.-]*|@sha256:[a-f0-9]{64})$')]
    [string] $Image,
    [string] $Subscription = 'aee3c089-49a8-472c-8d40-5bf568a1ff55',
    [switch] $Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$group = 'DevSites'
$name = 'entityassist-website'

function Invoke-AzureJson {
    param([string[]] $Arguments)
    $output = & az @Arguments --subscription $Subscription --only-show-errors --output json
    if ($LASTEXITCODE -ne 0) { throw "Azure command failed: az $($Arguments -join ' ')" }
    return ($output | ConvertFrom-Json)
}

$account = Invoke-AzureJson @('account', 'show')
if ($account.id -ne $Subscription) { throw 'Azure returned a different subscription.' }
$reference = Invoke-AzureJson @('containerapp', 'show', '--name', 'guicedee-website', '--resource-group', $group)
$environment = $reference.properties.managedEnvironmentId
if (-not $environment.StartsWith("/subscriptions/$Subscription/resourceGroups/$group/", [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The reference website is outside the requested subscription/resource group.'
}
$apps = @(Invoke-AzureJson @('containerapp', 'list', '--resource-group', $group))
$existing = @($apps | Where-Object name -EQ $name)
if ($existing.Count -gt 0 -and $existing[0].properties.managedEnvironmentId -ne $environment) {
    throw 'The existing Entity Assist site is in a different environment.'
}
$operation = if ($existing.Count -gt 0) { 'update' } else { 'create' }
[ordered]@{
    operation = $operation; subscription = $account.name; subscriptionId = $Subscription
    resourceGroup = $group; environment = $environment; app = $name; image = $Image
    ingress = 'external, port 80, HTTPS enforced'; cpu = 0.25; memory = '0.5Gi'
    minReplicas = 0; maxReplicas = 1; revisionMode = 'Single'
} | ConvertTo-Json

if (-not $Apply) {
    Write-Host 'Deployment plan only. Pass -Apply to deploy the published image.'
    return
}

if ($operation -eq 'create') {
    $null = Invoke-AzureJson @('containerapp', 'create', '--name', $name, '--resource-group', $group,
        '--environment', $environment, '--image', $Image, '--ingress', 'external', '--target-port', '80',
        '--transport', 'auto', '--revisions-mode', 'single', '--cpu', '0.25', '--memory', '0.5Gi',
        '--min-replicas', '0', '--max-replicas', '1', '--scale-rule-name', 'http-scaler',
        '--scale-rule-type', 'http', '--scale-rule-http-concurrency', '10')
} else {
    $null = Invoke-AzureJson @('containerapp', 'update', '--name', $name, '--resource-group', $group, '--image', $Image)
}

$site = Invoke-AzureJson @('containerapp', 'show', '--name', $name, '--resource-group', $group)
Write-Host "Azure state: $($site.properties.provisioningState)"
Write-Host "Website: https://$($site.properties.configuration.ingress.fqdn)"
Write-Host 'Verify revision health and route rendering before configuring entityassist.com DNS and its managed certificate.'
