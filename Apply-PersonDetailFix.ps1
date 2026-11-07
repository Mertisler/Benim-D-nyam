# Run from the Android project root. Only edits PersonDetailViewModel.kt.
# A timestamped backup is created before any change.
$ErrorActionPreference = "Stop"

$target = Join-Path (Get-Location) 'app/src/main/java/com/loc/benimdunyam/feature/people/detail/PersonDetailViewModel.kt'
if (-not (Test-Path -LiteralPath $target)) {
    throw "PersonDetailViewModel.kt bulunamadi. Bu betigi proje ana klasorunde calistirin."
}
$source = [System.IO.File]::ReadAllText($target)

if ($source -notmatch 'class\s+PersonDetailViewModel\s+@Inject\s+constructor\s*\(') {
    throw 'Beklenen @Inject constructor bulunamadi. Dosya degistirilmedi.'
}
$names = @('saveSpecialMemory', 'removeSpecialMemory', 'clearSpecialMemoryError')
$found = @($names | Where-Object { $source -match ("fun\s+" + $_ + "\s*\(") })
if ($found.Count -gt 0) {
    throw "Bazi metotlar zaten tanimli: $($found -join ', '). Dosya degistirilmedi."
}
if ($source -notmatch '(?m)\b(?:val|var)\s+errorMessage\s*=') {
    throw 'errorMessage MutableStateFlow alani bulunamadi. Yanlis ViewModel surumune yazmamak icin iptal edildi.'
}
if ($source -notmatch 'private\s+val\s+personId\s*:') {
    throw 'personId alani bulunamadi. Dosya degistirilmedi.'
}

# Reuse existing constructor-injected dependencies when available.
$repoMatch = [regex]::Match($source, '(?<name>[A-Za-z_][A-Za-z0-9_]*)\s*:\s*PersonSpecialMemoryRepositoryImpl\b')
$repoVar = if ($repoMatch.Success) { $repoMatch.Groups['name'].Value } else { 'specialMemoryMutationRepository' }
$coordMatch = [regex]::Match($source, '(?<name>[A-Za-z_][A-Za-z0-9_]*)\s*:\s*BackupWriteCoordinator\b')
$coordVar = if ($coordMatch.Success) { $coordMatch.Groups['name'].Value } else { 'specialMemoryWriteCoordinator' }

$addParams = @()
if (-not $repoMatch.Success) { $addParams += "private val $($repoVar): PersonSpecialMemoryRepositoryImpl" }
if (-not $coordMatch.Success) { $addParams += "private val $($coordVar): BackupWriteCoordinator" }

# Find constructor's balancing closing parenthesis.
$m = [regex]::Match($source, 'class\s+PersonDetailViewModel\s+@Inject\s+constructor\s*\(')
$p = $m.Index + $m.Length
$depth = 1
$close = -1
for ($i = $p; $i -lt $source.Length; $i++) {
    $ch = $source[$i]
    if ($ch -eq '(') { $depth++ }
    elseif ($ch -eq ')') {
        $depth--
        if ($depth -eq 0) { $close = $i; break }
    }
}
if ($close -lt 0) { throw 'Constructor kapanisi bulunamadi; degisiklik yapilmadi.' }

$updated = $source
if ($addParams.Count -gt 0) {
    $before = $source.Substring(0, $close)
    $after = $source.Substring($close)
    $trimmedBefore = $before.TrimEnd()
    $separator = if ($trimmedBefore.EndsWith(',')) { '' } else { ',' }
    $updated = $trimmedBefore + $separator + "`r`n    " + ($addParams -join ",`r`n    ") + "`r`n" + $after
}

$requiredImports = @(
    'import com.loc.benimdunyam.data.backup.BackupWriteCoordinator',
    'import com.loc.benimdunyam.data.repository.PersonSpecialMemoryRepositoryImpl',
    'import com.loc.benimdunyam.domain.model.PersonSpecialMemoryCategory',
    'import java.util.concurrent.atomic.AtomicBoolean'
)
foreach ($entry in $requiredImports) {
    if (-not $updated.Contains($entry)) {
        $packageLine = [regex]::Match($updated, '(?m)^package\s+com\.loc\.benimdunyam\.feature\.people\.detail\s*$')
        if (-not $packageLine.Success) { throw 'Beklenen paket tanimi bulunamadi.' }
        $idx = $packageLine.Index + $packageLine.Length
        $updated = $updated.Insert($idx, "`r`n$entry")
    }
}

$methods = @'

    // Unutulmaz Anilar: islemler geri yukleme kilidi altinda gerceklestirilir.
    private val specialMemoryMutationBusy = AtomicBoolean(false)

    fun saveSpecialMemory(
        journalId: String,
        category: PersonSpecialMemoryCategory,
        note: String?,
        onSaved: () -> Unit
    ) {
        if (!specialMemoryMutationBusy.compareAndSet(false, true)) return

        viewModelScope.launch {
            errorMessage.value = null
            try {
                specialMemoryWriteCoordinator.withRegularWrite {
                    specialMemoryMutationRepository.save(
                        personId = personId,
                        journalId = journalId,
                        category = category,
                        note = note?.trim()?.takeIf { it.isNotEmpty() }
                    )
                }
                onSaved()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                errorMessage.value = exception.localizedMessage
                    ?: "Unutulmaz ani kaydedilemedi."
            } finally {
                specialMemoryMutationBusy.set(false)
            }
        }
    }

    fun removeSpecialMemory(
        journalId: String,
        onRemoved: () -> Unit
    ) {
        if (!specialMemoryMutationBusy.compareAndSet(false, true)) return

        viewModelScope.launch {
            errorMessage.value = null
            try {
                specialMemoryWriteCoordinator.withRegularWrite {
                    specialMemoryMutationRepository.remove(
                        personId = personId,
                        journalId = journalId
                    )
                }
                onRemoved()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                errorMessage.value = exception.localizedMessage
                    ?: "Unutulmaz ani kaldirilamadi."
            } finally {
                specialMemoryMutationBusy.set(false)
            }
        }
    }

    fun clearSpecialMemoryError() {
        errorMessage.value = null
    }
'@

# Connect the existing special-memory UI state flows when their names are present.
$savingFlow = [regex]::Match($source, '(?m)^\s*private\s+val\s+(?<name>_?isSavingSpecialMemory)\s*=\s*MutableStateFlow')
if ($savingFlow.Success) {
    $savingName = $savingFlow.Groups['name'].Value
    $methods = $methods.Replace('viewModelScope.launch {', "viewModelScope.launch {`r`n            $savingName.value = true")
    $methods = $methods.Replace('specialMemoryMutationBusy.set(false)', "$savingName.value = false`r`n                specialMemoryMutationBusy.set(false)")
}
$errorFlow = [regex]::Match($source, '(?m)^\s*private\s+val\s+(?<name>_?specialMemoryErrorMessage)\s*=\s*MutableStateFlow')
if ($errorFlow.Success) {
    $errorName = $errorFlow.Groups['name'].Value
    $methods = $methods.Replace('errorMessage.value = null', "errorMessage.value = null`r`n            $errorName.value = null")
    $methods = $methods.Replace('errorMessage.value = exception.localizedMessage', "$errorName.value = exception.localizedMessage`r`n                errorMessage.value = exception.localizedMessage")
}

# Insert before class closing brace. The ViewModel is expected to be the last top-level declaration.
$endBrace = $updated.LastIndexOf('}')
if ($endBrace -lt $m.Index) { throw 'ViewModel govde kapanisi bulunamadi; degisiklik yapilmadi.' }
if ($updated.Substring($endBrace + 1).Trim().Length -gt 0) { throw 'Dosyada beklenmeyen kod var; degisiklik yapilmadi.' }
$updated = $updated.Insert($endBrace, $methods + "`r`n")

# Adapt repository and coordinator variable names when already injected.
if ($repoVar -ne 'specialMemoryMutationRepository') {
    $updated = $updated.Replace('specialMemoryMutationRepository.save(', "$repoVar.save(")
    $updated = $updated.Replace('specialMemoryMutationRepository.remove(', "$repoVar.remove(")
}
if ($coordVar -ne 'specialMemoryWriteCoordinator') {
    $updated = $updated.Replace('specialMemoryWriteCoordinator.withRegularWrite', "$coordVar.withRegularWrite")
}

$backup = "$target.before-special-memory-fix.$((Get-Date).ToString('yyyyMMdd-HHmmss')).bak"
[System.IO.File]::Copy($target, $backup)
[System.IO.File]::WriteAllText($target, $updated, [System.Text.UTF8Encoding]::new($false))
Write-Host "Tamam: $target" -ForegroundColor Green
Write-Host "Orijinal kopya: $backup"
Write-Host 'Android Studio > Build > Rebuild Project ile kontrol edin.'
