package com.lucento.app.core.crash

/**
 * Crash and ANR reports, when a Firebase project is wired in (see docs/QUALITY.md). With no
 * `google-services.json` the SDK is not on the classpath and [install] does nothing.
 *
 * Reports keep the stack and the exception type. The message, which can contain an amount or something the user
 * typed, is never sent. No user id, email or custom key is set.
 */
object CrashReporting {
    /** The last value asked of the SDK. Null until [install] or [setCollectionEnabled]. Visible for tests. */
    internal var collectionEnabled: Boolean? = null
        private set

    /** How many times opt-out asked the SDK to drop reports still waiting to send. Visible for tests. */
    internal var unsentReportsDeleted: Int = 0
        private set

    /**
     * Installs the sanitising handler and sets collection to [enabled]. Pass the synchronous opt-in from
     * [CrashReportsFlag] so a crash in the first moments is reported for someone who already turned reports on.
     * The default is off. With no Crashlytics class this does not touch Firebase. No user id, custom key, or log
     * is ever set.
     */
    fun install(enabled: Boolean = false) {
        setCollectionEnabled(enabled)
        if (!enabled) deleteUnsentReports()
        val crashlytics = crashlyticsClass() ?: return
        val instance = instance(crashlytics) ?: return
        if (handlerInstalled) return
        handlerInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val safe = sanitize(throwable)
            if (previous != null) previous.uncaughtException(thread, safe) else throw safe
        }
    }

    /** Turns collection on or off. Dropping unsent reports is [deleteUnsentReports], after the choice is saved. */
    fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
        val crashlytics = crashlyticsClass()
        val instance = crashlytics?.let { instance(it) }
        if (crashlytics != null && instance != null) {
            runCatching {
                crashlytics.getMethod("setCrashlyticsCollectionEnabled", Boolean::class.javaPrimitiveType)
                    .invoke(instance, enabled)
            }
        }
    }

    /** Drops reports still waiting on the device. Safe to call more than once. No-op without the SDK. */
    fun deleteUnsentReports() {
        unsentReportsDeleted++
        val crashlytics = crashlyticsClass() ?: return
        val instance = instance(crashlytics) ?: return
        runCatching { crashlytics.getMethod("deleteUnsentReports").invoke(instance) }
    }

    private var handlerInstalled = false

    private fun crashlyticsClass(): Class<*>? =
        runCatching { Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics") }.getOrNull()

    private fun instance(crashlytics: Class<*>): Any? =
        runCatching { crashlytics.getMethod("getInstance").invoke(null) }.getOrNull()

    /** A throwable whose message is only the original type name, with the same stack. */
    fun sanitize(throwable: Throwable): Throwable {
        val safe = SanitizedCrash(throwable.javaClass.name)
        safe.stackTrace = throwable.stackTrace
        val cause = throwable.cause
        if (cause != null && cause !== throwable) safe.initCause(sanitize(cause))
        return safe
    }
}

private class SanitizedCrash(typeName: String) : RuntimeException(typeName)
