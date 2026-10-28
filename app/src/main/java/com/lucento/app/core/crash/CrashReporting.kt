package com.lucento.app.core.crash

/**
 * Crash and ANR reports, when a Firebase project is wired in (see docs/QUALITY.md). With no
 * `google-services.json` the SDK is not on the classpath and [install] does nothing.
 *
 * Reports keep the stack and the exception type. The message, which can contain an amount or something the user
 * typed, is never sent. No user id, email or custom key is set.
 */
object CrashReporting {
    fun install() {
        val crashlytics = runCatching {
            Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
        }.getOrNull() ?: return
        val instance = runCatching { crashlytics.getMethod("getInstance").invoke(null) }.getOrNull() ?: return
        runCatching {
            crashlytics.getMethod("setCrashlyticsCollectionEnabled", Boolean::class.javaPrimitiveType)
                .invoke(instance, true)
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val safe = sanitize(throwable)
            if (previous != null) previous.uncaughtException(thread, safe) else throw safe
        }
    }

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
