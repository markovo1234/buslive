package com.buslive.app.service

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncState { IDLE, RUNNING, FAILED }

/** Observes the one-time static data download so the UI can show "Downloading stops…". */
@Singleton
class SyncMonitor @Inject constructor(@ApplicationContext private val context: Context) {
    val state: Flow<SyncState> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(StaticSyncWorker.ONE_TIME)
        .map { infos ->
            val info = infos.lastOrNull() ?: return@map SyncState.IDLE
            when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> SyncState.RUNNING
                WorkInfo.State.FAILED -> SyncState.FAILED
                else -> SyncState.IDLE
            }
        }
        .distinctUntilChanged()

    fun syncNow(force: Boolean = false) = StaticSyncWorker.runNow(context, force)
}
