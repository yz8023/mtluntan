package io.mtluntan.app.data.network

import java.util.regex.Pattern

/**
 * Solves the anti-bot JavaScript challenge ("acw_sc__v2") that the site's CDN
 * (Alibaba ESA) serves in front of every dynamic page.
 *
 * The challenge is a fixed deterministic algorithm parameterized by an `arg1`
 * hex seed embedded in the served script:
 *
 *   1. read `arg1` (40 hex chars)
 *   2. reorder its 40 characters by the permutation table `m` (from the script)
 *   3. XOR each byte (2 hex chars) with the constant `p`
 *
 * The result is the requested `acw_sc__v2=<value>` cookie. Earlier versions ran
 * the obfuscated script in an embedded JS engine; that proved unreliable (Rhino
 * OOMs on the big-loop anti-debug block), so we reproduce the algorithm itself.
 * Both `arg1` and the `m` table are parsed from the live script, so the solver
 * stays correct if `m` / the script shape changes.
 */
object WafChallengeSolver {

    private const val TAG = "WafSolver"
    private const val P = "3000176000856006061501533003690027800375"

    private val ARG1_RE = Pattern.compile("var\\s+arg1\\s*=\\s*['\"]([0-9A-Fa-f]{40})['\"]")
    private val M_RE = Pattern.compile("var\\s+m\\s*=\\s*\\[([^\\]]*)\\]")

    // Default permutation table (as served by the site). Replaced when the
    // live script contains a real table.
    private val DEFAULT_M = intArrayOf(
        0x0f, 0x23, 0x1d, 0x18, 0x21, 0x10, 0x01, 0x26, 0x0a, 0x09,
        0x13, 0x1f, 0x28, 0x1b, 0x16, 0x17, 0x19, 0x0d, 0x06, 0x0b,
        0x27, 0x12, 0x14, 0x08, 0x0e, 0x15, 0x20, 0x1a, 0x02, 0x1e,
        0x07, 0x04, 0x11, 0x05, 0x03, 0x1c, 0x22, 0x25, 0x0c, 0x24,
    )

    /** Returns the `acw_sc__v2=<value>` cookie or null if the page is not a challenge. */
    fun solve(html: String): String? {
        if (!html.contains("var arg1=")) return null
        val arg1Match = ARG1_RE.matcher(html)
        if (!arg1Match.find()) return null
        val arg1 = arg1Match.group(1) ?: return null
        if (arg1.length != 40) return null

        val m = extractM(html)
        if (m.size != 40) return null

        val value = compute(arg1, m)
        if (value.length != 40) return null
        return "acw_sc__v2=$value"
    }

    private fun extractM(html: String): IntArray {
        val mm = M_RE.matcher(html)
        if (!mm.find()) return DEFAULT_M
        val tokens = mm.group(1)?.split(',') ?: return DEFAULT_M
        if (tokens.size != 40) return DEFAULT_M
        val out = IntArray(40)
        for (i in 0 until 40) {
            val t = tokens[i].trim()
            if (!t.startsWith("0x") && !t.startsWith("0X")) return DEFAULT_M
            val v = t.substring(2).toIntOrNull(16) ?: return DEFAULT_M
            out[i] = v
        }
        return out
    }

    private fun compute(arg1: String, m: IntArray): String {
        // q[z] = arg1[x] where m[z] == x+1
        val q = CharArray(40)
        for (x in 0 until 40) {
            for (z in 0 until 40) {
                if (m[z] == x + 1) q[z] = arg1[x]
            }
        }
        val u = String(q)
        val sb = StringBuilder()
        for (x in 0 until 40 step 2) {
            val a = u.substring(x, x + 2).toInt(16)
            val b = P.substring(x, x + 2).toInt(16)
            val xor = (a xor b).toString(16)
            if (xor.length == 1) sb.append('0')
            sb.append(xor)
        }
        return sb.toString()
    }
}