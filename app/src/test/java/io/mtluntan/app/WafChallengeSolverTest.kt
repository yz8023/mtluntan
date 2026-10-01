package io.mtluntan.app

import io.mtluntan.app.data.network.WafChallengeSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the acw_sc__v2 solver against real challenge pages served by the CDN. */
class WafChallengeSolverTest {

    @Test
    fun solvesRealChallengeA() {
        val html = load("challenge_a.html")
        val cookie = WafChallengeSolver.solve(html)
        assertTrue("challenge A must be solvable", cookie != null && cookie.startsWith("acw_sc__v2="))
        assertEquals(
            "acw_sc__v2=6abeb44dc461b82bd57e9f6c707b8a8a3c83e574",
            cookie,
        )
    }

    @Test
    fun solvesRealChallengeB() {
        val html = load("challenge_b.html")
        val cookie = WafChallengeSolver.solve(html)
        assertTrue("challenge B must be solvable", cookie != null && cookie.startsWith("acw_sc__v2="))
        assertEquals(
            "acw_sc__v2=6abe9c24adcaad72dfea1c13c302770537563196",
            cookie,
        )
    }

    @Test
    fun nonChallengeReturnsNull() {
        val normal = "<html><body><h1>hello</h1></body></html>"
        assertNull(WafChallengeSolver.solve(normal))
    }

    @Test
    fun missingArg1ReturnsNull() {
        val broken = "<html><script>var x=1;</script></html>"
        assertNull(WafChallengeSolver.solve(broken))
    }

    @Test
    fun shortArg1ReturnsNull() {
        val broken = """<html><script>var arg1='1234';</script></html>"""
        assertNull(WafChallengeSolver.solve(broken))
    }

    private fun load(name: String): String {
        return javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}