package com.lucento.app.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lucento's own listening session: how long it waits, when it listens again, and what it sends. */
class VoiceSessionTest {

    private val t = VoiceTiming()

    /** Feeds events at the given times (ms after the start) and returns the state and every command. */
    private fun run(vararg steps: Pair<Long, VoiceEvent>, offline: Boolean = false): Pair<VoiceState, List<VoiceCommand>> {
        var state = VoiceSession.start(0, offline)
        val commands = mutableListOf<VoiceCommand>()
        for ((at, event) in steps) {
            val (next, command) = VoiceSession.reduce(state, event, at, t)
            state = next
            commands += command
        }
        return state to commands
    }

    @Test
    fun `a speech service that gives up at once is asked again while the person is still thinking`() {
        // The owner's phone: the service ended within a fraction of a second, before anyone spoke.
        val (state, commands) = run(
            300L to VoiceEvent.Error(VoiceError.NO_SPEECH),
            900L to VoiceEvent.Error(VoiceError.NO_SPEECH),
            5_000L to VoiceEvent.Tick,
        )
        assertEquals(listOf(VoiceCommand.ListenAgain, VoiceCommand.ListenAgain, VoiceCommand.None), commands)
        assertEquals(VoicePhase.LISTENING, state.phase)
    }

    @Test
    fun `words after eight seconds of thinking are still heard`() {
        val (_, commands) = run(
            4_000L to VoiceEvent.Error(VoiceError.NO_SPEECH),
            8_000L to VoiceEvent.SpeechStarted,
            9_000L to VoiceEvent.Partial("lunch 250"),
            9_800L to VoiceEvent.Final("lunch 250"),
            13_400L to VoiceEvent.Tick,
        )
        assertEquals(VoiceCommand.Finish("lunch 250"), commands.last())
    }

    @Test
    fun `with no words at all it stops after the first-words wait`() {
        val (_, early) = run(9_900L to VoiceEvent.Tick)
        assertEquals(VoiceCommand.None, early.last())
        val (state, commands) = run(10_000L to VoiceEvent.Tick)
        assertEquals(VoiceCommand.Finish(null), commands.last())
        assertEquals(VoicePhase.FINISHED, state.phase)
        // A "no speech" from the service after the wait ends it too, instead of listening again.
        val (_, late) = run(10_200L to VoiceEvent.Error(VoiceError.NO_SPEECH))
        assertEquals(VoiceCommand.Finish(null), late.last())
    }

    @Test
    fun `a pause between items keeps the session open and the pieces are sent together`() {
        val (state, commands) = run(
            1_000L to VoiceEvent.SpeechStarted,
            2_000L to VoiceEvent.Final("lunch 250"),
            // Thinking about the next one: the service ends its stretch, Lucento listens again.
            4_000L to VoiceEvent.Error(VoiceError.NO_SPEECH),
            5_000L to VoiceEvent.SpeechStarted,
            6_000L to VoiceEvent.Final("groceries 800"),
            9_499L to VoiceEvent.Tick,
            9_500L to VoiceEvent.Tick,
        )
        assertEquals(VoiceCommand.ListenAgain, commands[1])
        assertEquals(VoiceCommand.ListenAgain, commands[2])
        assertEquals(VoiceCommand.None, commands[5])
        assertEquals(VoiceCommand.Finish("lunch 250, groceries 800"), commands.last())
        assertEquals(listOf("lunch 250", "groceries 800"), state.heard)
    }

    @Test
    fun `a long quiet after words ends it even when the service keeps reporting no speech`() {
        val (_, commands) = run(
            1_000L to VoiceEvent.Final("parking 90"),
            3_000L to VoiceEvent.Error(VoiceError.NO_SPEECH),
            4_600L to VoiceEvent.Error(VoiceError.NO_SPEECH),
        )
        assertEquals(VoiceCommand.ListenAgain, commands[1])
        assertEquals(VoiceCommand.Finish("parking 90"), commands.last())
    }

    @Test
    fun `done mid-sentence asks the service for its last words and sends them`() {
        val (state, commands) = run(
            1_000L to VoiceEvent.SpeechStarted,
            1_500L to VoiceEvent.Partial("dinner 4"),
            1_600L to VoiceEvent.DoneTapped,
            2_000L to VoiceEvent.Final("dinner 450"),
        )
        assertEquals(VoiceCommand.Stop, commands[2])
        assertEquals(VoicePhase.FINISHED, state.phase)
        assertEquals(VoiceCommand.Finish("dinner 450"), commands.last())
    }

    @Test
    fun `done keeps what was on screen when the service never answers or finds no match`() {
        val (_, timedOut) = run(
            1_500L to VoiceEvent.Partial("taxi 120"),
            1_600L to VoiceEvent.DoneTapped,
            4_099L to VoiceEvent.Tick,
            4_100L to VoiceEvent.Tick,
        )
        assertEquals(VoiceCommand.None, timedOut[2])
        assertEquals(VoiceCommand.Finish("taxi 120"), timedOut.last())

        val (_, noMatch) = run(
            1_000L to VoiceEvent.Final("lunch 250"),
            2_000L to VoiceEvent.Partial("coffee"),
            2_100L to VoiceEvent.DoneTapped,
            2_300L to VoiceEvent.Error(VoiceError.NO_SPEECH),
        )
        assertEquals(VoiceCommand.Finish("lunch 250, coffee"), noMatch.last())
    }

    @Test
    fun `done in a quiet moment finishes straight away`() {
        val (_, commands) = run(1_000L to VoiceEvent.Final("salary 35000"), 1_500L to VoiceEvent.DoneTapped)
        assertEquals(VoiceCommand.Finish("salary 35000"), commands.last())
        val (_, nothing) = run(500L to VoiceEvent.DoneTapped)
        assertEquals(VoiceCommand.Finish(null), nothing.last())
    }

    @Test
    fun `a second tap on done doesn't wait for the service`() {
        val (_, commands) = run(
            1_000L to VoiceEvent.Partial("rent 9000"),
            1_100L to VoiceEvent.DoneTapped,
            1_300L to VoiceEvent.DoneTapped,
        )
        assertEquals(VoiceCommand.Finish("rent 9000"), commands.last())
    }

    @Test
    fun `offline without a language pack ends with the online voice offer`() {
        listOf(VoiceError.LANGUAGE, VoiceError.NETWORK, VoiceError.OTHER).forEach { error ->
            val (_, commands) = run(200L to VoiceEvent.Error(error), offline = true)
            assertEquals(error.name, VoiceCommand.Fail(VoiceFailure.OFFLINE_UNAVAILABLE), commands.last())
        }
    }

    @Test
    fun `online failures say what went wrong`() {
        assertEquals(VoiceFailure.NETWORK, VoiceSession.failureFor(VoiceError.NETWORK, offline = false))
        assertEquals(VoiceFailure.LANGUAGE, VoiceSession.failureFor(VoiceError.LANGUAGE, offline = false))
        assertEquals(VoiceFailure.OTHER, VoiceSession.failureFor(VoiceError.OTHER, offline = false))
        assertEquals(VoiceFailure.MICROPHONE, VoiceSession.failureFor(VoiceError.AUDIO, offline = true))
        assertEquals(VoiceFailure.PERMISSION, VoiceSession.failureFor(VoiceError.PERMISSION, offline = false))
    }

    @Test
    fun `an error after words never loses them`() {
        val (_, commands) = run(
            1_000L to VoiceEvent.Final("groceries 800"),
            2_000L to VoiceEvent.SpeechStarted,
            2_500L to VoiceEvent.Error(VoiceError.NETWORK),
        )
        assertEquals(VoiceCommand.Finish("groceries 800"), commands.last())
    }

    @Test
    fun `a busy service is tried again`() {
        val (_, commands) = run(100L to VoiceEvent.Error(VoiceError.RETRY))
        assertEquals(VoiceCommand.ListenAgain, commands.last())
    }

    @Test
    fun `someone still speaking is never cut off by the quiet timer`() {
        val (state, commands) = run(
            1_000L to VoiceEvent.Final("lunch 250"),
            2_000L to VoiceEvent.SpeechStarted,
            3_000L to VoiceEvent.Partial("groceries"),
            6_000L to VoiceEvent.Partial("groceries eight hundred"),
            8_000L to VoiceEvent.Tick,
        )
        assertEquals(VoiceCommand.None, commands.last())
        assertEquals(VoicePhase.LISTENING, state.phase)
        assertEquals("lunch 250, groceries eight hundred", state.transcript)
    }

    @Test
    fun `speech that starts but never turns into words is wrapped up`() {
        // Background noise can look like speech to the service; it must not hold the session open for a minute.
        val (state, commands) = run(1_000L to VoiceEvent.SpeechStarted, 11_000L to VoiceEvent.Tick)
        assertEquals(VoiceCommand.Stop, commands.last())
        assertEquals(VoicePhase.STOPPING, state.phase)
    }

    @Test
    fun `one session lasts a minute at most`() {
        // Mid-sentence at the minute: the service is asked for its last words.
        val (_, speaking) = run(1_000L to VoiceEvent.Final("bus 15"), 2_000L to VoiceEvent.SpeechStarted, 60_000L to VoiceEvent.Tick)
        assertEquals(VoiceCommand.Stop, speaking.last())
        // An item every three seconds keeps it open, until the minute is up.
        val items: List<Pair<Long, VoiceEvent>> = (1..20).map { i -> (i * 3_000L - 2_000L) to VoiceEvent.Final("item $i") }
        val (state, commands) = run(*items.toTypedArray(), 60_000L to VoiceEvent.Tick)
        assertTrue(commands.dropLast(1).all { it == VoiceCommand.ListenAgain })
        assertEquals(VoiceCommand.Finish(state.heard.joinToString(", ")), commands.last())
        assertEquals(20, state.heard.size)
    }

    @Test
    fun `a service that fails instantly forever is not restarted forever`() {
        var state = VoiceSession.start(0, offline = false)
        var restarts = 0
        var last: VoiceCommand = VoiceCommand.None
        for (i in 1..100) {
            val (next, command) = VoiceSession.reduce(state, VoiceEvent.Error(VoiceError.NO_SPEECH), i * 50L, t)
            state = next
            last = command
            if (command == VoiceCommand.ListenAgain) restarts++ else break
        }
        assertEquals(t.maxRestarts, restarts)
        assertEquals(VoiceCommand.Finish(null), last)
    }

    @Test
    fun `nothing happens after the session ended`() {
        val (state, _) = run(500L to VoiceEvent.DoneTapped)
        val (after, command) = VoiceSession.reduce(state, VoiceEvent.Final("late words"), 600L, t)
        assertEquals(VoiceCommand.None, command)
        assertTrue(after.heard.isEmpty())
    }

    @Test
    fun `blank results add nothing`() {
        val (state, commands) = run(1_000L to VoiceEvent.Final("   "), 1_200L to VoiceEvent.DoneTapped)
        assertTrue(state.heard.isEmpty())
        assertEquals(VoiceCommand.Finish(null), commands.last())
    }
}
