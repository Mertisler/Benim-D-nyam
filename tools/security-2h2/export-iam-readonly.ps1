<#
 Faz 2H.2 — Firebase Cloud Functions IAM salt-okunur envanter çıkarma aracı.
 Her gcloud komutu DESCRIBE / GET-IAM-POLICY: değişiklik, deploy veya silme yoktur.
 Gerçek projenizin adını yalnızca komut argümanıyla verin.
 Kişisel hesap veya servis hesabı anahtarı JSON dosyası istemez.
#>
param(
    [Parameter(Mandatory=$true)][string]$ProjectId,
    [Parameter(Mandatory=$true)][string]$BucketName,
    [string]$Region = 'europe-west1',
    [string]$OutputDir = './iam-evidence-2h2'
)
$ErrorActionPreference = 'Stop'
if ($ProjectId -notmatch '^[a-z][a-z0-9-]{4,35}$') { throw 'Geçersiz Firebase/GCP proje kimliği.' }
if ($BucketName -notmatch '^[a-z0-9][a-z0-9._-]{2,220}$') { throw 'Geçersiz Storage bucket adı.' }
if ($Region -notmatch '^[a-z0-9-]+$') { throw 'Geçersiz Cloud Functions bölgesi.' }
if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) { throw 'Önce gcloud CLI kurulumunu tamamlayın.' }
New-Item -Type Directory -Path $OutputDir -Force | Out-Null
$functions = @('bdMediaCleanupStatusV2', 'bdMediaCleanupPrepareV2', 'bdMediaCleanupExecuteV2')
foreach ($fn in $functions) {
    Write-Host "Okunuyor: $fn"
    $detail = gcloud functions describe $fn --gen2 --project=$ProjectId --region=$Region --format=json
    if ($LASTEXITCODE -ne 0) { throw "Function bulunamadı veya yetki yok: $fn" }
    $detail | Out-File -LiteralPath (Join-Path $OutputDir "function-$fn.json") -Encoding utf8
    $object = ($detail -join "`n") | ConvertFrom-Json
    if (-not $object.serviceConfig.service) { throw "$fn için Cloud Run service yolu bulunamadı." }
    $service = ($object.serviceConfig.service -split '/')[-1]
    $policy = gcloud run services get-iam-policy $service --project=$ProjectId --region=$Region --format=json
    if ($LASTEXITCODE -ne 0) { throw "Cloud Run IAM okunamadı: $service" }
    $policy | Out-File -LiteralPath (Join-Path $OutputDir "run-$fn.json") -Encoding utf8
}
$projectPolicy = gcloud projects get-iam-policy $ProjectId --format=json
if ($LASTEXITCODE -ne 0) { throw 'Proje IAM okunamadı.' }
$projectPolicy | Out-File -LiteralPath (Join-Path $OutputDir 'project-policy.json') -Encoding utf8
$bucketPolicy = gcloud storage buckets get-iam-policy "gs://$BucketName" --format=json
if ($LASTEXITCODE -ne 0) { throw 'Storage bucket IAM okunamadı.' }
$bucketPolicy | Out-File -LiteralPath (Join-Path $OutputDir 'bucket-policy.json') -Encoding utf8
Write-Host "Salt-okunur raporlar oluşturuldu: $OutputDir"
Write-Host 'Dikkat: Çıktılarda servis hesabı ve yetkili kimliği bulunabilir. Herkese açık paylaşmayın.'
Write-Host 'Yerel inceleme: node audit-iam.mjs ./iam-evidence-2h2'
