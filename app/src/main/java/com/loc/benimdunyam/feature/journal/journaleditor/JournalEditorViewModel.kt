package com.loc.benimdunyam.feature.journal.journaleditor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.data.local.draft.JournalDraftEntity
import com.loc.benimdunyam.data.local.draft.JournalDraftStore
import com.loc.benimdunyam.domain.model.JournalEntry
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.domain.model.LifeEpisode
import com.loc.benimdunyam.domain.model.Person
import com.loc.benimdunyam.domain.usecase.GetJournalByIdUseCase
import com.loc.benimdunyam.domain.usecase.ObserveEpisodesBySeasonUseCase
import com.loc.benimdunyam.domain.usecase.ObserveLifeSeasonsUseCase
import com.loc.benimdunyam.domain.usecase.ObservePeopleUseCase
import com.loc.benimdunyam.domain.usecase.SaveJournalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray

@HiltViewModel
class JournalEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getJournalByIdUseCase: GetJournalByIdUseCase,
    private val saveJournalUseCase: SaveJournalUseCase,
    private val observePeopleUseCase: ObservePeopleUseCase,
    private val observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase,
    private val observeEpisodesBySeasonUseCase: ObserveEpisodesBySeasonUseCase,
    draftStore: JournalDraftStore,
    private val photoStore: JournalPhotoStore
) : ViewModel() {

    private val journalId: String? =
        savedStateHandle.get<String>("journalId")?.takeIf { it.isNotBlank() }
    private val initialSeasonId: String? =
        savedStateHandle.get<String>("seasonId")?.takeIf { it.isNotBlank() }
    private val initialEpisodeId: String? =
        savedStateHandle.get<String>("episodeId")?.takeIf { it.isNotBlank() }

    private val draftKey = JournalDraftStore.keyFor(journalId)
    private val draftAutosave = JournalDraftAutosave(
        store = draftStore,
        scope = viewModelScope,
        draftKey = draftKey
    )

    private val _uiState = MutableStateFlow(JournalEditorUiState(isLoading = true))
    val uiState: StateFlow<JournalEditorUiState> = _uiState

    private var originalCreatedAt: Long? = null
    private var publishedJournalId: String? = null
    private var pendingRestore: JournalDraftEntity? = null

    // Veriler asenkron yuklendiginde geri yuklenen @kisi etiketlerini korur.
    private var restoredMentionIds: Set<String>? = null

    init {
        observePeople()
        observeSeasons()
        observeEpisodes()
        observeDraftStatus()

        if (journalId != null) {
            loadJournal(journalId)
        } else {
            _uiState.update {
                it.copy(
                    selectedSeasonId = initialSeasonId,
                    selectedEpisodeId = initialEpisodeId
                )
            }
            prepareNewJournal()
        }
    }

    private fun observeDraftStatus() {
        viewModelScope.launch {
            draftAutosave.state.collectLatest { status ->
                _uiState.update { it.copy(draftSaveState = status) }
            }
        }
    }

    private fun observePeople() {
        viewModelScope.launch {
            observePeopleUseCase()
                .catch {
                    _uiState.update { state ->
                        state.copy(errorMessage = "Kişiler yüklenemedi.")
                    }
                    emit(emptyList())
                }
                .collectLatest { people ->
                    _uiState.update { state ->
                        val pendingIds = restoredMentionIds
                        state.copy(
                            allPeople = people,
                            mentionedPeople = if (pendingIds != null) {
                                people.filter { person -> person.id in pendingIds }
                            } else {
                                state.mentionedPeople
                            }
                        )
                    }
                }
        }
    }

    private fun observeSeasons() {
        viewModelScope.launch {
            observeLifeSeasonsUseCase()
                .catch {
                    _uiState.update { state ->
                        state.copy(errorMessage = "Dönemler yüklenemedi.")
                    }
                    emit(emptyList())
                }
                .collectLatest { seasons ->
                    _uiState.update { state -> state.copy(allSeasons = seasons) }
                }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeEpisodes() {
        viewModelScope.launch {
            _uiState
                .map { it.selectedSeasonId }
                .distinctUntilChanged()
                .flatMapLatest { seasonId ->
                    if (seasonId.isNullOrBlank()) {
                        flowOf(emptyList<LifeEpisode>())
                    } else {
                        observeEpisodesBySeasonUseCase(seasonId)
                    }
                }
                .catch {
                    _uiState.update { state ->
                        state.copy(
                            currentSeasonEpisodes = emptyList(),
                            errorMessage = "Bölümler yüklenemedi."
                        )
                    }
                    emit(emptyList())
                }
                .collectLatest { episodes ->
                    _uiState.update { state ->
                        val stillExists = state.selectedEpisodeId?.let { id ->
                            episodes.any { it.id == id }
                        } ?: true
                        state.copy(
                            currentSeasonEpisodes = episodes,
                            selectedEpisodeId = if (episodes.isNotEmpty() && !stillExists) {
                                null
                            } else {
                                state.selectedEpisodeId
                            }
                        )
                    }
                }
        }
    }

    private fun prepareNewJournal() {
        viewModelScope.launch {
            try {
                offerExistingDraftIfNeeded(journalUpdatedAt = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Taslak yüklenemedi: ${error.localizedMessage}")
                }
            }
        }
    }

    private fun loadJournal(id: String) {
        viewModelScope.launch {
            try {
                val journal = getJournalByIdUseCase(id)
                if (journal == null) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Günlük bulunamadı.")
                    }
                    return@launch
                }

                originalCreatedAt = journal.createdAt
                _uiState.update { state ->
                    state.copy(
                        title = journal.title,
                        content = journal.content,
                        entryDate = journal.entryDate,
                        mentionedPeople = journal.mentionedPeople,
                        selectedSeasonId = journal.seasonId,
                        selectedEpisodeId = journal.episodeId,
                        // Mevcut JournalEntry yazma/yukleme akisina burada dokunmuyoruz.
                        isLoading = true,
                        errorMessage = null
                    )
                }
                offerExistingDraftIfNeeded(journalUpdatedAt = journal.updatedAt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Günlük veya taslak yüklenemedi: ${error.localizedMessage}")
                }
            }
        }
    }

    private suspend fun offerExistingDraftIfNeeded(journalUpdatedAt: Long?) {
        // Fotoğrafları ekranda her zaman güncel günlük kimliğinden okuyalım.
        val saved = journalId?.let { photoStore.list(it) }.orEmpty()
        val staged = photoStore.listDraft(draftKey)
        _uiState.update { it.copy(savedPhotos = saved, draftPhotos = staged) }

        val draft = draftAutosave.load()
        val current = _uiState.value
        if (draft != null) {
            val stale = journalUpdatedAt != null && draft.updatedAt <= journalUpdatedAt
            if (staged.isEmpty() && (stale || draftMatchesState(draft, current))) {
                draftAutosave.discard()
                // Eski yayımlanma kimliği dosyası da varsa temizle.
                photoStore.discardDraftPhotos(draftKey)
            } else {
                pendingRestore = draft
            }
        } else if (staged.isNotEmpty()) {
            // Uygulama, fotoğraf diske yazıldıktan hemen sonra kapanmış olabilir.
            // Fotoğraf içerdiği için boş metinli taslağı da kurtarmayı teklif et.
            pendingRestore = createDraft(current)
        } else {
            // Taslak kaydı yoksa olası eski yayın kimliği dosyasını temizle.
            photoStore.discardDraftPhotos(draftKey)
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                showRestoreDraftDialog = pendingRestore != null
            )
        }
    }

    /** Kullanici kaldigi yerden devam etmeyi sectiginde. */
    fun restoreDraft() {
        val draft = pendingRestore ?: return
        restoredMentionIds = readPersonIds(draft.mentionedPeopleJson)
        pendingRestore = null

        _uiState.update { state ->
            state.copy(
                title = draft.title,
                content = draft.content,
                entryDate = parseDate(draft.entryDateIso) ?: state.entryDate,
                selectedSeasonId = draft.seasonId,
                selectedEpisodeId = draft.episodeId,
                selectedMood = parseMood(draft.moodName),
                mentionedPeople = state.allPeople.filter { it.id in (restoredMentionIds ?: emptySet()) },
                isMentionPopupVisible = false,
                mentionQuery = "",
                showRestoreDraftDialog = false,
                errorMessage = null
            )
        }
        draftAutosave.markRestored(draft)
    }

    /** Kullanici onceden kaydedilmis taslagi reddettiginde. */
    fun discardDraft() {
        if (pendingRestore == null || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Önce geçici görselleri temizle, ardından Room taslağını sil.
                photoStore.discardDraftPhotos(draftKey)
                draftAutosave.discard()
                pendingRestore = null
                restoredMentionIds = null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showRestoreDraftDialog = false,
                        draftPhotos = emptyList(),
                        errorMessage = null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Taslak silinemedi: ${error.localizedMessage}")
                }
            }
        }
    }

    // Kullanici etkilesimleri: yalnizca gercek form degisikligi taslagi yeniler.
    private fun applyUserEdit(change: (JournalEditorUiState) -> JournalEditorUiState) {
        var snapshot: JournalEditorUiState? = null
        _uiState.update { state ->
            if (state.isLoading || state.isSaving || state.isSaved || state.isLeaving || state.showRestoreDraftDialog) {
                state
            } else {
                change(state).also { updated ->
                    if (updated != state) snapshot = updated
                }
            }
        }
        snapshot?.let { draftAutosave.schedule(createDraft(it)) }
    }

    fun onTitleChanged(newTitle: String) = applyUserEdit {
        it.copy(title = newTitle, errorMessage = null)
    }

    fun onContentChanged(newContent: String) {
        val mention = JournalMentionHandler.detect(newContent)
        applyUserEdit {
            it.copy(
                content = newContent,
                isMentionPopupVisible = mention.isVisible,
                mentionQuery = if (mention.isVisible) mention.query else "",
                errorMessage = null
            )
        }
    }

    fun onDateChanged(newDate: Long) = applyUserEdit {
        it.copy(entryDate = newDate, errorMessage = null)
    }

    fun onMoodSelected(mood: JournalMood?) = applyUserEdit {
        it.copy(selectedMood = mood, errorMessage = null)
    }

    fun onSeasonSelected(seasonId: String?) {
        val normalizedId = seasonId?.takeIf { it.isNotBlank() }
        applyUserEdit { state ->
            if (state.selectedSeasonId == normalizedId) state else state.copy(
                selectedSeasonId = normalizedId,
                selectedEpisodeId = null,
                currentSeasonEpisodes = emptyList(),
                errorMessage = null
            )
        }
    }

    fun onEpisodeSelected(episodeId: String?) = applyUserEdit {
        it.copy(selectedEpisodeId = episodeId, errorMessage = null)
    }

    fun selectPersonForMention(person: Person) {
        restoredMentionIds = null
        applyUserEdit { state ->
            val content = JournalMentionHandler.replaceMention(state.content, person.name)
            val mentions = if (state.mentionedPeople.any { it.id == person.id }) {
                state.mentionedPeople
            } else {
                state.mentionedPeople + person
            }
            state.copy(
                content = content,
                mentionedPeople = mentions,
                isMentionPopupVisible = false,
                mentionQuery = "",
                errorMessage = null
            )
        }
    }

    fun removeMentionedPerson(person: Person) {
        restoredMentionIds = null
        applyUserEdit { state ->
            state.copy(
                mentionedPeople = state.mentionedPeople.filterNot { it.id == person.id },
                errorMessage = null
            )
        }
    }

    /** Fotoğraflar, "Anıyı Kaydet" basılmadan önce taslak klasörüne kopyalanır. */
    fun addPhotos(uris: List<Uri>) {
        val state = _uiState.value
        if (uris.isEmpty() || state.isLoading || state.isSaving || state.isLeaving ||
            state.isPhotoWorking || state.showRestoreDraftDialog || state.isSaved
        ) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPhotoWorking = true, photoErrorMessage = null) }
            var failed = 0
            var firstError: String? = null
            try {
                for (uri in uris.distinct()) {
                    try {
                        photoStore.importDraftPhoto(draftKey, uri, journalId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failed++
                        if (firstError == null) firstError = error.localizedMessage
                    }
                }
                val photos = photoStore.listDraft(draftKey)
                _uiState.update { stateNow ->
                    stateNow.copy(
                        draftPhotos = photos,
                        isPhotoWorking = false,
                        photoErrorMessage = if (failed > 0) {
                            "$failed fotoğraf eklenemedi: ${firstError ?: "Bilinmeyen hata"}"
                        } else null
                    )
                }
                if (photos.isNotEmpty()) {
                    // Sadece fotoğraf seçilmiş, başlık henüz yazılmamış olsa bile
                    // kurtarma penceresinin yeniden açılabilmesi için Room'a kaydet.
                    draftAutosave.schedule(createDraft(_uiState.value))
                    if (!draftAutosave.flush()) {
                        _uiState.update {
                            it.copy(photoErrorMessage = "Fotoğraflar saklandı ama taslak bilgileri yazılamadı.")
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isPhotoWorking = false, photoErrorMessage = "Fotoğraf eklenemedi: ${error.localizedMessage}")
                }
            }
        }
    }

    fun removeDraftPhoto(photoId: String) {
        val state = _uiState.value
        if (state.isPhotoWorking || state.isLoading || state.isSaving ||
            state.isLeaving || state.showRestoreDraftDialog || state.isSaved
        ) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPhotoWorking = true, photoErrorMessage = null) }
            try {
                photoStore.deleteDraftPhoto(draftKey, photoId)
                val remaining = photoStore.listDraft(draftKey)
                _uiState.update {
                    it.copy(
                        draftPhotos = remaining,
                        isPhotoWorking = false
                    )
                }
                draftAutosave.schedule(createDraft(_uiState.value))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isPhotoWorking = false, photoErrorMessage = "Fotoğraf silinemedi: ${error.localizedMessage}")
                }
            }
        }
    }

    fun clearPhotoError() {
        _uiState.update { it.copy(photoErrorMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Sistem geri tusu ve editor basligindaki geri dugmesi icin. */
    fun requestExit(onNavigateBack: () -> Unit) {
        val state = _uiState.value
        if (state.isSaving || state.isLeaving) return
        if (state.isPhotoWorking) {
            _uiState.update { it.copy(photoErrorMessage = "Fotoğraflar işlenirken çıkış yapılamaz.") }
            return
        }
        if (state.showRestoreDraftDialog) {
            // Onceki taslaga dokunmadan ekrandan cik.
            onNavigateBack()
            return
        }
        _uiState.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            try {
                if (draftAutosave.flush()) {
                    onNavigateBack()
                } else {
                    _uiState.update {
                        it.copy(isLeaving = false, errorMessage = "Taslak kaydedilemedi. Ekrandan cikilmadi.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isLeaving = false, errorMessage = "Taslak kaydedilemedi: ${error.localizedMessage}")
                }
            }
        }
    }

    /** Arka plana gidildiginde bekleyen degisikligi kalici hale getirmeyi dener. */
    fun flushOnBackground() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            try {
                val success = draftAutosave.flush()
                if (!success) {
                    _uiState.update {
                        it.copy(errorMessage = "Taslak diske kaydedilemedi.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Taslak diske kaydedilemedi: ${error.localizedMessage}")
                }
            }
        }
    }

    fun saveJournal(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isLeaving || state.showRestoreDraftDialog || state.isPhotoWorking) return
        val title = state.title.trim()
        val content = state.content.trim()
        if (title.isEmpty() || content.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Başlık ve içerik boş bırakılamaz.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, isSaved = false, errorMessage = null) }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                // Yeni anı için kalıcı hedef kimliği önceden diske yazılır.
                // Fotoğraf aktarımı yarıda kalırsa sonraki deneme aynı anıyı günceller.
                val currentId = journalId ?: publishedJournalId
                ?: photoStore.getOrCreatePublicationId(draftKey)
                // Kısmi fotoğraf aktarımından sonra aynı kayıt tekrar denenirse
                // ilk oluşturulma tarihini değiştirme.
                val createdAt = originalCreatedAt
                    ?: getJournalByIdUseCase(currentId)?.createdAt
                    ?: now

                val journal = JournalEntry(
                    id = currentId,
                    title = title,
                    content = content,
                    entryDate = state.entryDate,
                    seasonId = state.selectedSeasonId,
                    episodeId = state.selectedEpisodeId,
                    createdAt = createdAt,
                    updatedAt = now,
                    mentionedPeople = state.mentionedPeople
                )

                val personIds = restoredMentionIds?.toList()
                    ?: state.mentionedPeople.map { it.id }

                saveJournalUseCase(journal, personIds)

                // Asil Room islemi BASARILI. Olası taslak temizleme hatasi
                // normal kaydin basarisiz oldugu anlamina gelmez.
                publishedJournalId = currentId
                originalCreatedAt = createdAt

                // Önce fotoğrafları taslaktan gerçek anıya taşı.
                // Bu adım başarısızsa kullanıcı aynı kimlikle tekrar deneyebilir.
                try {
                    photoStore.publishDraftPhotos(draftKey, currentId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (photoError: Exception) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSaved = false,
                            photoErrorMessage = "Anı kaydedildi ancak fotoğraflar taşınamadı. " +
                                    "Tekrar Kaydet'e bas: ${photoError.localizedMessage}"
                        )
                    }
                    return@launch
                }

                try {
                    draftAutosave.clearAfterPublish()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (cleanupError: Exception) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSaved = true,
                            errorMessage = "Anı kaydedildi fakat taslak temizlenemedi. Yeniden Kaydet ile tekrar deneyebilirsin."
                        )
                    }
                    return@launch
                }

                // Artık dosyaları anıya aktarıldı; boş geçici klasörün ve
                // kayıt kimliği dosyasının da silinmesini dene.
                try {
                    photoStore.discardDraftPhotos(draftKey)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Anı ve fotoğraflar kaydedildi; gereksiz geçici klasör
                    // sonraki editör açılışında otomatik temizlenecek.
                }

                _uiState.update {
                    it.copy(isSaving = false, isSaved = true, errorMessage = null)
                }
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSaved = false,
                        errorMessage = error.localizedMessage ?: "Günlük kaydedilirken hata oluştu."
                    )
                }
            }
        }
    }

    private fun createDraft(state: JournalEditorUiState): JournalDraftEntity {
        val peopleJson = JSONArray().apply {
            state.mentionedPeople.map { it.id }.distinct().forEach { put(it) }
        }.toString()

        return JournalDraftEntity(
            draftKey = draftKey,
            title = state.title,
            content = state.content,
            entryDateIso = formatDate(state.entryDate),
            seasonId = state.selectedSeasonId,
            episodeId = state.selectedEpisodeId,
            moodName = moodName(state.selectedMood),
            mentionedPeopleJson = peopleJson
        )
    }

    private fun draftMatchesState(draft: JournalDraftEntity, state: JournalEditorUiState): Boolean =
        draft.title == state.title &&
                draft.content == state.content &&
                draft.entryDateIso == formatDate(state.entryDate) &&
                draft.seasonId == state.selectedSeasonId &&
                draft.episodeId == state.selectedEpisodeId &&
                draft.moodName == moodName(state.selectedMood) &&
                readPersonIds(draft.mentionedPeopleJson) == state.mentionedPeople.map { it.id }.toSet()

    private fun readPersonIds(raw: String): Set<String> {
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length())
            .mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            .toSet()
    }

    private fun moodName(mood: JournalMood?): String? =
        (mood as? Enum<*>)?.name ?: mood?.toString()

    private fun parseMood(raw: String?): JournalMood? =
        JournalMood::class.java.enumConstants
            ?.firstOrNull { (it as? Enum<*>)?.name == raw }

    /** Bu alanin tipi Room tablosunu degistirmeden ISO metni tutar. */
    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(epochMillis))

    private fun parseDate(value: String?): Long? {
        if (value == null) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
                isLenient = false
            }.parse(value)?.time
        }.getOrNull() ?: runCatching {
            // Ilk altyapi paketinin tarih-only kayitlari icin geriye uyumluluk.
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }.parse(value)?.time
        }.getOrNull()
    }
}
