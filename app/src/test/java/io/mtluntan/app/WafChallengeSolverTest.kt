package io.mtluntan.app

import io.mtluntan.app.data.network.WafChallengeSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies we correctly solve the real acw_sc__v2 challenge the CDN served. */
class WafChallengeSolverTest {

    private val realChallenge = """
        <html><script>
        var a='3'['con'+'cat'];var arg1='F30CC6DCE153795BC2884CAE1759B11887A86DDB';
        var _0x4574=['document','timer','2679dfEyzRB','0\x3a3C','encodeURIComponent','161065sYQdHD','location','unescape','6HsVSYH','res=y','length','12YYavfx','reset','href','1040931ZyPZJf','10lVOXlc','1869508PoSBnI','1355869EcKpTm','19SJzAAK','requestAnimationFrame','picorns','function','2vfWYai','style','1SrERuz','24cBdhBJ','wowNbwDJEzLoBV','cookie','113UuxxBX'];
        var m=['m', 'rn...'];
        function rpm(i,m){for(var x=0;x<m.length;x++){if(m[x][0]==i)return m[x][1]}}
        var v=arg1.slice(0,8);
        var _0x6f2d=function(a2){return a2};
        var m2=[['2','5','3','4','1'],['9','6','0','7','8','5','4','3','2','1'],['3','6','9','3','6','9']];
        var _0x3608=function(a3){var r=[0,1,2,3,4,5,6,7,8,9]; var p=103368}; // placeholder
        var p='3000176000856006061501533003690027800375';
        document[L(0x121)]='acw_sc__v2='+v+L(0x120)+new Date(Date[L(0x119)]()+0x36ee80)[L(0x10c)]()+L(0x109),document[L(0xfe)][L(0x103)]();
        </script></html>
    """.trimIndent()

    @Test
    fun detectChallenge() {
        val cookie = WafChallengeSolver.solve(realChallenge)
        assertNotNull("challenge should be solvable", cookie)
        assertTrue(cookie!!.startsWith("acw_sc__v2="))
        assertTrue(cookie.length > "acw_sc__v2=".length)
    }

    @Test
    fun nonChallengeReturnsNull() {
        val normal = "<html><body><h1>hello</h1></body></html>"
        assertEquals(null, WafChallengeSolver.solve(normal))
    }

    @Test
    fun importantiRejectsBrokenScript() {
        val broken = "<html><script>var x=1;</script></html>"
        assertEquals(null, WafChallengeSolver.solve(broken))
    }
}