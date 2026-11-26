# =============================================================================
# Benim Dünyam — R8 kuralları
#
# Temel ilke: JSON'a yazılan veya ADIYLA saklanan her şeyin adı korunur.
# Aksi halde R8 alanları "a", "b" diye yeniden adlandırır ve
#   - eski .bdbackup / bulut arşivi / sync kataloğu dosyaları AÇILAMAZ,
#   - kayıtlı ruh halleri ve tema seçimi OKUNAMAZ,
#   - önceden planlanmış WorkManager işleri ÇALIŞMAZ.
# Yeni bir Gson modeli eklerken bu dosyayı güncelle.
# =============================================================================

# Çökme raporlarında satır numarası; dosya adı gizlenir.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# Gson (2.10.x kendi R8 kurallarını içermez)
# -----------------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod,InnerClasses
-dontwarn sun.misc.**
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# -----------------------------------------------------------------------------
# Gson ile serileştirilen uygulama modelleri: sınıf, alan adları ve kurucular
# -----------------------------------------------------------------------------

# .bdbackup (data/backup.json), hesap taşıma (room/main.json), bulut arşivi,
# sync ilişki kataloğu: BackupData ve altı Room varlığı.
-keep class com.loc.benimdunyam.data.backup.BackupData { <fields>; <init>(...); }
-keep class com.loc.benimdunyam.data.local.entity.** { <fields>; <init>(...); }

# .bdbackup manifest.json ve data/moods.json
-keep class com.loc.benimdunyam.data.backup.BackupArchiveManifest { <fields>; <init>(...); }
-keep class com.loc.benimdunyam.data.backup.BackupMediaFile { <fields>; <init>(...); }
-keep class com.loc.benimdunyam.data.backup.BackupMoodCodec$Payload { <fields>; <init>(...); }

# Ayrı Room'lardaki kayıtlar (moods/drafts/featured .json bölümleri)
-keep class com.loc.benimdunyam.data.local.mood.JournalMoodRecord { <fields>; <init>(...); }
-keep class com.loc.benimdunyam.data.local.draft.JournalDraftEntity { <fields>; <init>(...); }
-keep class com.loc.benimdunyam.feature.yearreview.featured.YearFeaturedMemoryEntity { <fields>; <init>(...); }

# Bulut arşivi ve sync ilişki kataloğu
-keep class com.loc.benimdunyam.data.remote.firebase.archive.CloudArchivePayload { <fields>; <init>(...); }
-keep class com.loc.benimdunyam.data.sync.remote.SyncDependencyCatalog { <fields>; <init>(...); }

# "Son silinenler" anlık görüntüsü (trashed_journals.snapshotJson)
-keep class com.loc.benimdunyam.data.trash.TrashedJournalSnapshot { <fields>; <init>(...); }

# -----------------------------------------------------------------------------
# Adıyla saklanan / okunan enum'lar (JournalMood.name, AppThemeCategory.valueOf...)
# -----------------------------------------------------------------------------
-keep enum com.loc.benimdunyam.** { *; }

# -----------------------------------------------------------------------------
# WorkManager: sınıf adı WorkManager veritabanında saklanır. Sürüm güncellemesinde
# daha önce planlanmış işlerin bulunabilmesi için adlar sabit kalmalı.
# -----------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Yapay zekâ önbelleği (Gson ile JSON olarak saklanır).
-keep class com.loc.benimdunyam.data.ai.StoredAiPayload { <fields>; <init>(...); }
