// Minimal stand-in for kotlinx-coroutines-test's runTest (the harness only has coroutines-core).
package kotlinx.coroutines.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

fun runTest(block: suspend CoroutineScope.() -> Unit) {
    runBlocking { block() }
}
