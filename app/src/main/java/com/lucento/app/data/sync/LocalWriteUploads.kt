package com.lucento.app.data.sync

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

/**
 * Emits once a burst of local writes settles whenever the outbox grew — a new row, an edit of a synced row, or a
 * delete. Nothing used to schedule an upload for a plain insert, so new entries waited for "Sync now" (QA R6-03).
 * The first value counts as growth when it is above zero, so changes left from a previous run go out too.
 */
@OptIn(FlowPreview::class)
internal fun uploadRequests(pendingChanges: Flow<Int>, settleMillis: Long = 1_500): Flow<Unit> =
    pendingChanges
        .distinctUntilChanged()
        .runningFold(Pair(0, 0)) { (_, previous), current -> previous to current }
        .filter { (previous, current) -> current > previous }
        .debounce(settleMillis)
        .map { }
