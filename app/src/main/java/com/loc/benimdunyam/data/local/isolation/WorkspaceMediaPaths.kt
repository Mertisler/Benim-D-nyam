package com.loc.benimdunyam.data.local.isolation

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File

/**
 * Context alan yardımcı kodların da Hilt'in başlangıçta seçtiği AYNI medya
 * dizinini kullanabilmesi için tek erişim noktası.
 * Her çağrıda Firebase hesabına göre yeniden dizin hesaplamak veri karıştırabilir.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkspaceMediaEntryPoint {
    fun activeWorkspaceSession(): ActiveWorkspaceSession
}

object WorkspaceMediaPaths {
    fun root(context: Context): File = EntryPointAccessors
        .fromApplication(context.applicationContext, WorkspaceMediaEntryPoint::class.java)
        .activeWorkspaceSession().mediaRoot()
}
