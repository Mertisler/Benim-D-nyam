package com.loc.benimdunyam.feature.settings.cloudaccount

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.domain.model.CloudAuthError
import com.loc.benimdunyam.domain.model.CloudAuthResult
import com.loc.benimdunyam.domain.model.CloudConnectionStatus
import com.loc.benimdunyam.domain.repository.CloudAuthRepository
import com.loc.benimdunyam.domain.repository.CloudConnectionRepository
import com.loc.benimdunyam.domain.repository.CloudArchiveRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import javax.crypto.AEADBadTagException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CloudAccountViewModel @Inject constructor(
    private val cloudAuthRepository: CloudAuthRepository,
    private val cloudConnectionRepository: CloudConnectionRepository,
    private val archiveRepository: CloudArchiveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudAccountUiState())
    val uiState: StateFlow<CloudAccountUiState> = _uiState.asStateFlow()

    private var connectionJob: Job? = null
    private var archiveJob: Job? = null

    init {
        viewModelScope.launch {
            cloudAuthRepository.account.collect { account ->
                val previousUid = _uiState.value.account?.uid
                if (previousUid != account?.uid) {
                    connectionJob?.cancel()
                    archiveJob?.cancel()
                    _uiState.update {
                        it.copy(
                            isArchiveBusy = false,
                            archiveAction = null,
                            latestArchive = null,
                            restorePreview = null,
                            localCoverage = null,
                            syncAudit = null,
                            archiveMessage = null,
                            archiveError = null,
                            connectionStatus = null,
                            isCheckingConnection = false
                        )
                    }
                }
                _uiState.update {
                    it.copy(isCheckingSession = false, account = account)
                }
            }
        }
    }

    fun signIn(email: String, password: String) = submitAuth(email, password, isRegistration = false)

    fun register(email: String, password: String, passwordConfirmation: String) {
        if (password != passwordConfirmation) {
            _uiState.update { it.copy(errorMessage = "Şifreler eşleşmiyor.", message = null) }
            return
        }
        submitAuth(email, password, isRegistration = true)
    }

    private fun submitAuth(email: String, password: String, isRegistration: Boolean) {
        val currentState = _uiState.value
        if (currentState.isCheckingSession || currentState.isSubmitting || currentState.account != null) return

        val normalizedEmail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            _uiState.update { it.copy(errorMessage = "Geçerli bir e-posta adresi gir.", message = null) }
            return
        }
        if (password.isBlank() || (isRegistration && password.length < 6)) {
            _uiState.update {
                it.copy(
                    errorMessage = if (isRegistration) "Şifre en az 6 karakter olmalı." else "Şifreni gir.",
                    message = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null, message = null) }
            try {
                val result = if (isRegistration) {
                    cloudAuthRepository.registerWithEmail(normalizedEmail, password)
                } else {
                    cloudAuthRepository.signInWithEmail(normalizedEmail, password)
                }
                when (result) {
                    is CloudAuthResult.Success -> _uiState.update {
                        it.copy(message = if (isRegistration) "Hesabın oluşturuldu. Bulut eşitlemesi henüz kapalı." else "Hesabına giriş yapıldı.")
                    }
                    is CloudAuthResult.Failure -> _uiState.update {
                        it.copy(errorMessage = result.reason.toUserMessage())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "İşlem tamamlanamadı. Tekrar dene.") }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun checkConnection() {
        val uid = _uiState.value.account?.uid ?: run {
            _uiState.update { it.copy(errorMessage = "Önce bulut hesabına giriş yap.") }
            return
        }
        if (_uiState.value.isCheckingConnection || _uiState.value.isSubmitting) return
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isCheckingConnection = true, connectionStatus = null, errorMessage = null, message = null)
            }
            try {
                val status = cloudConnectionRepository.checkReadAccess()
                // Oturum test sürerken değişmişse eski hesaba ait sonucu göstermeyelim.
                if (_uiState.value.account?.uid == uid) {
                    _uiState.update { it.copy(connectionStatus = status) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (_uiState.value.account?.uid == uid) {
                    _uiState.update { it.copy(connectionStatus = CloudConnectionStatus.UnknownError) }
                }
            } finally {
                _uiState.update { it.copy(isCheckingConnection = false) }
            }
        }
    }

    fun signOut() {
        if (_uiState.value.isSubmitting || _uiState.value.isArchiveBusy) {
            _uiState.update { it.copy(errorMessage = "Bulut arşivi işlemi devam ederken çıkış yapılamaz.") }
            return
        }
        connectionJob?.cancel()
        try {
            cloudAuthRepository.signOut()
            _uiState.update {
                it.copy(
                    account = null,
                    connectionStatus = null,
                    isCheckingConnection = false,
                    message = "Bulut hesabından çıkış yapıldı. Cihazındaki anıların korunuyor.",
                    errorMessage = null
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Çıkış yapılamadı. Tekrar dene.", message = null) }
        }
    }

    /**
     * Kullanıcı isterse cihazdaki kayıtları sayar ve son bulut kopyasını listeler.
     * Yükleme, silme veya geri yükleme işlemi yapılmaz.
     */
    fun inspectCloudReadiness() = launchArchiveAction("Yerel ve bulut kayıtları inceleniyor") { uid ->
        val local = archiveRepository.inspectLocalCoverage()
        if (_uiState.value.account?.uid == uid) {
            _uiState.update { it.copy(localCoverage = local, syncAudit = null) }
        }
        val latest = archiveRepository.latestArchive()
        if (_uiState.value.account?.uid == uid) {
            _uiState.update {
                it.copy(
                    latestArchive = latest,
                    archiveMessage = if (latest == null)
                        "Cihaz tarandı. Bu hesapta tamamlanmış bulut arşivi yok."
                    else "Kapsam incelendi. Kesin fark analizi için arşiv şifrenle karşılaştırma yap."
                )
            }
        }
    }

    /**
     * Son şifreli bulut arşivini açarak kayıtlardaki gerçek farkları bulur.
     * Şifre UI durumuna yazılmaz ve işlemden sonra CharArray temizlenir.
     */
    fun compareCloudArchive(passphrase: String) {
        val secret = passphrase.toCharArray()
        val job = launchArchiveAction("Şifreli bulut arşivi karşılaştırılıyor") { uid ->
            try {
                val report = archiveRepository.compareLatestWithLocal(secret)
                if (_uiState.value.account?.uid == uid) {
                    _uiState.update {
                        it.copy(
                            localCoverage = report.local,
                            latestArchive = report.archive,
                            syncAudit = report,
                            archiveMessage = if (report.textRecordsMatch)
                                "Metin kayıtları eşleşiyor. Fotoğraflar henüz bulut arşivinde yok."
                            else "Kayıt farkları bulundu. Bu işlem hiçbir veriyi değiştirmedi."
                        )
                    }
                }
            } finally {
                secret.fill('\u0000')
            }
        }
        if (job == null) secret.fill('\u0000')
    }

    /** Yalnızca sunucudan okuma; Room verisini değiştirmez. */
    fun loadLatestArchive() = launchArchiveAction("Son kopya aranıyor") { uid ->
        val latest = archiveRepository.latestArchive()
        if (_uiState.value.account?.uid == uid) {
            _uiState.update {
                it.copy(
                    latestArchive = latest,
                    restorePreview = null,
                    archiveMessage = if (latest == null) "Bu hesapta henüz tamamlanmış bir bulut kopyası yok." else "Bulut arşivi bulundu.",
                    archiveError = null
                )
            }
        }
    }

    /** Kullanıcının onayıyla ayrı bir şifreli kopya üretir. Otomatik çalışmaz. */
    fun uploadEncryptedArchive(passphrase: String) {
        if (passphrase.length !in 12..128) {
            _uiState.update { it.copy(archiveError = "Arşiv şifresi 12–128 karakter olmalı.") }
            return
        }
        val secret = passphrase.toCharArray()
        val job = launchArchiveAction("Şifreli arşiv gönderiliyor") { uid ->
            try {
                val result = archiveRepository.uploadEncrypted(secret)
                if (_uiState.value.account?.uid == uid) {
                    _uiState.update {
                        it.copy(
                            latestArchive = result,
                            restorePreview = null,
                            syncAudit = null,
                            archiveError = null,
                            archiveMessage = "Şifreli bulut kopyası tamamlandı. Yerel veriler değiştirilmedi."
                        )
                    }
                }
            } finally { secret.fill('\u0000') }
        }
        if (job == null) secret.fill('\u0000')
    }

    /** Sunucudan gerçek parçaları indirip GCM doğrulaması yapar; Room'a YAZMAZ. */
    fun verifyEncryptedArchive(passphrase: String) {
        val secret = passphrase.toCharArray()
        val job = launchArchiveAction("Bulut kopyası doğrulanıyor") { uid ->
            try {
                val result = archiveRepository.verifyLatestArchive(secret)
                if (_uiState.value.account?.uid == uid) {
                    _uiState.update {
                        it.copy(
                            latestArchive = result,
                            restorePreview = null,
                            archiveError = null,
                            archiveMessage = "Şifre ve bütünlük kontrolü başarılı. Bulut kopyası okunabiliyor."
                        )
                    }
                }
            } finally { secret.fill('\u0000') }
        }
        if (job == null) secret.fill('\u0000')
    }

    /** First user consent: download, decrypt and preview, without changing any local row. */
    fun previewCloudRestore(passphrase: String) {
        val secret = passphrase.toCharArray()
        val job = launchArchiveAction("Geri yükleme önizlemesi hazırlanıyor") { uid ->
            try {
                val preview = archiveRepository.previewLatestRestore(secret)
                if (_uiState.value.account?.uid == uid) {
                    _uiState.update {
                        it.copy(
                            restorePreview = preview,
                            latestArchive = preview.archive,
                            archiveError = null,
                            archiveMessage = if (preview.canImport)
                                "Arşiv doğrulandı. Kayıtlar henüz cihazına aktarılmadı."
                            else "Arşiv doğrulandı ancak mevcut cihaz verilerini korumak için içe aktarma engellendi."
                        )
                    }
                }
            } finally {
                secret.fill('\u0000')
            }
        }
        if (job == null) secret.fill('\u0000')
    }

    /** Second explicit consent, with a fresh password; never overwrites existing Room rows. */
    fun importPreviewedCloudRestore(passphrase: String) {
        val preview = _uiState.value.restorePreview ?: run {
            _uiState.update { it.copy(archiveError = "Önce arşivi önizlemelisin.") }
            return
        }
        if (!preview.canImport) {
            _uiState.update { it.copy(archiveError = preview.blockedReason ?: "İçe aktarma güvenli değil.") }
            return
        }
        val secret = passphrase.toCharArray()
        val job = launchArchiveAction("Şifreli arşiv cihaza aktarılıyor") { uid ->
            try {
                val result = archiveRepository.importPreviewedArchive(preview, secret)
                if (_uiState.value.account?.uid == uid) {
                    _uiState.update {
                        it.copy(
                            restorePreview = null,
                            localCoverage = null,
                            syncAudit = null,
                            archiveError = null,
                            archiveMessage = "Geri yükleme tamamlandı: ${result.journalCount} anı, " +
                                    "${result.personCount} kişi, ${result.seasonCount} dönem, " +
                                    "${result.episodeCount} bölüm ve ${result.moodCount} ruh hali eklendi. " +
                                    "Fotoğraflar bu kopyaya dahil değil."
                        )
                    }
                }
            } finally {
                secret.fill('\u0000')
            }
        }
        if (job == null) secret.fill('\u0000')
    }

    fun clearRestorePreview() {
        if (!_uiState.value.isArchiveBusy) {
            _uiState.update { it.copy(restorePreview = null, archiveError = null) }
        }
    }

    private fun launchArchiveAction(label: String, action: suspend (String) -> Unit): Job? {
        val uid = _uiState.value.account?.uid ?: run {
            _uiState.update { it.copy(archiveError = "Önce bulut hesabına giriş yap.") }
            return null
        }
        if (_uiState.value.isArchiveBusy || _uiState.value.isSubmitting) return null
        // Aynı ekranın butonundan peş peşe iki işlem başlatılmasını engelle.
        _uiState.update { it.copy(isArchiveBusy = true, archiveAction = label, archiveError = null, archiveMessage = null) }
        return viewModelScope.launch {
            try {
                action(uid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: AEADBadTagException) {
                if (_uiState.value.account?.uid == uid) {
                    _uiState.update { it.copy(archiveError = "Şifre yanlış veya bulut arşivi bozulmuş.") }
                }
            } catch (error: FirebaseFirestoreException) {
                if (_uiState.value.account?.uid == uid) {
                    val message = when (error.code) {
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firestore kuralları erişimi engelledi. Faz 2A Rules dosyasını yayımla."
                        FirebaseFirestoreException.Code.UNAVAILABLE,
                        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Sunucuya ulaşılamıyor. İnternet bağlantını kontrol et."
                        else -> "Firestore işlemi tamamlanamadı: ${error.code}"
                    }
                    _uiState.update { it.copy(archiveError = message) }
                }
            } catch (error: Exception) {
                if (_uiState.value.account?.uid == uid) {
                    _uiState.update { it.copy(archiveError = error.message ?: "Bulut arşivi işlemi tamamlanamadı.") }
                }
            } finally {
                if (_uiState.value.account?.uid == uid) {
                    _uiState.update { it.copy(isArchiveBusy = false, archiveAction = null) }
                }
            }
        }.also { archiveJob = it }
    }

    fun dismissArchiveMessage() {
        _uiState.update { it.copy(archiveMessage = null, archiveError = null) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null, errorMessage = null) }
    }

    private fun CloudAuthError.toUserMessage(): String = when (this) {
        CloudAuthError.INVALID_EMAIL_OR_PASSWORD -> "E-posta veya şifre hatalı. Bilgilerini kontrol et."
        CloudAuthError.EMAIL_ALREADY_IN_USE -> "Bu e-posta adresiyle zaten bir hesap var. Giriş yapmayı dene."
        CloudAuthError.WEAK_PASSWORD -> "Şifren zayıf. Daha güçlü bir şifre oluştur."
        CloudAuthError.NETWORK -> "İnternet bağlantısı kurulamıyor. Bağlantını kontrol et."
        CloudAuthError.RATE_LIMITED -> "Çok sayıda deneme yapıldı. Daha sonra tekrar dene."
        CloudAuthError.UNKNOWN -> "İşlem tamamlanamadı. Firebase ayarlarını kontrol edip tekrar dene."
    }
}
