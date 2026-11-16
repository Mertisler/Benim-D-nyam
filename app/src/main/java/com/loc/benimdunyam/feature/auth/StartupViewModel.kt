package com.loc.benimdunyam.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.core.auth.IntroPreferenceStore
import com.loc.benimdunyam.domain.repository.CloudAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Uygulamanın başlangıç yönlendirmesi.
 *
 * Açılmış Firebase oturumu veya daha önce seçilmiş hesapsız kullanım varsa
 * doğrudan ana sayfaya geçer; aksi halde kullanıcıya seçeneklerini sunar.
 * İlk açılışta kesinlikle veri silme, yedek indirme veya eşitleme yapılmaz.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val cloudAuthRepository: CloudAuthRepository,
    private val introPreferenceStore: IntroPreferenceStore
) : ViewModel() {

    private val _nextDestination = MutableStateFlow<StartupDestination?>(null)
    val nextDestination: StateFlow<StartupDestination?> = _nextDestination.asStateFlow()

    init {
        viewModelScope.launch {
            // Mevcut kullanıcıya ekstra karşılama zorunluluğu getirmiyoruz.
            val introCompleted = introPreferenceStore.isCompleted()
            val signedIn = try {
                cloudAuthRepository.account.first() != null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            _nextDestination.value = determineStartupDestination(signedIn, introCompleted)
        }
    }
}

