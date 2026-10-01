package io.mtluntan.app

import io.mtluntan.app.data.parser.ThreadListParser
import io.mtluntan.app.data.parser.ForumIndexParser
import io.mtluntan.app.data.parser.ThreadDetailParser
import io.mtluntan.app.data.parser.BbcToHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.Jsoup

class ParserTest {

    @Test
    fun comiisCardList() {
        val html = """
            <html><body><ul class="comiis_znalist">
            <li class="forumlist_li comiis_znalist">
              <div class="mmlist_li_box"><h2><a href="forum.php?mod=viewthread&amp;tid=12345&amp;extra=page%3D1">给我一个下1</a></h2></div>
              <div class="comiis_xznalist_bk"><a href="forum.php?mod=forumdisplay&amp;fid=2">软件分享</a></div>
              <div class="top_user"><a href="home.php?mod=space&amp;uid=1001">路人甲</a></div>
              <div class="comiis_xznalist_bottom"><ul><li><span class="comiis_tm">3</span></li><li><span class="comiis_tm">2</span></li><li><span class="comiis_tm">999</span></li></ul></div>
              <div class="forumlist_li_time">2026-09-30</div>
            </li>
            </ul></body></html>
        """.trimIndent()
        val result = ThreadListParser.parse(html)
        assertTrue(result.matched)
        val item = result.items.first()
        assertEquals(12345L, item.threadId)
        assertEquals("软件分享", item.boardName)
        assertEquals("路人甲", item.authorName)
        assertEquals(3, item.likes)
        assertEquals(2, item.replies)
        assertEquals(999, item.views)
    }

    @Test
    fun forumIndexPrimary() {
        val html = """
            <html><body>
            <div class="comiis_forumlist">
              <div class="comiis_bbs_show"><h2><a href="forum.php?mod=forumdisplay&amp;fid=37">移动专区</a></h2></div>
              <div class="comiis_forum_nbox"><ul>
                <li><a href="forum.php?mod=forumdisplay&amp;fid=2"><em><img src="/static/icon.png"/></em><p>软件分享</p></a></li>
                <li><a href="forum.php?mod=forumdisplay&amp;fid=39"><p>游戏天地</p></a></li>
              </ul></div>
            </div>
            </body></html>
        """.trimIndent()
        val cats = ForumIndexParser.parse(html)
        assertEquals(1, cats.size)
        assertEquals("移动专区", cats[0].name)
        assertEquals(2, cats[0].forums.size)
        assertEquals("软件分享", cats[0].forums[0].name)
        assertEquals(2L, cats[0].forums[0].id)
    }

    @Test
    fun threadDetailPc() {
        val html = """
            <html><body>
            <form id="postform"><input type="hidden" name="formhash" value="abc123def"/></form>
            <div id="postlist">
            <table id="pid111" class="plhin"><tr>
              <td class="pls"><img src="avatar.php?uid=42&size=middle"/><a href="space&uid=42">楼主</a></td>
              <td class="plc">
                <div class="authi"><em id="authorposton111">2026-9-1</em></div>
                <div class="pct"><div class="pcb"><div class="t_f" id="postmessage_111">
                  <img src="/data/attachment/forum/1.jpg"/><b>加粗</b><br/>正文内容
                </div></div></div>
              </td>
            </tr></table>
            <table id="pid222" class="plhin"><tr>
              <td class="pls"><a href="space&uid=99">二楼</a></td>
              <td class="plc"><em id="authorposton222">2026-9-2</em><div class="t_f" id="postmessage_222">回复内容</div></td>
            </tr></table>
            </div>
            <div class="pg"><strong>1</strong><span title="共 5 页">1 / 5</span></div>
            </body></html>
        """.trimIndent()
        val detail = ThreadDetailParser.parse(html, page = 1)
        assertEquals("abc123def", detail.formhash)
        assertEquals(1, detail.currentPage)
        assertEquals(5, detail.totalPages)
        assertEquals(111L, detail.mainPost?.pid)
        assertTrue(detail.mainPost?.contentBbc?.contains("正文内容") == true)
        assertEquals(1, detail.posts.size)
        assertEquals(222L, detail.posts[0].pid)
        assertTrue(detail.mainPost!!.contentBbc.contains("[img]"))
        assertTrue(detail.mainPost!!.contentBbc.contains("[b]加粗[/b]"))
    }

    @Test
    fun bbcConversion() {
        val el = Jsoup.parseBodyFragment(
            "hello <b>bold</b><br/><img src=\"/data/a.png\"/><a href=\"https://x.y/z\">link</a>"
        ).body()
        val bbc = BbcToHtml.htmlToBbc(el)
        assertTrue(bbc.contains("hello"))
        assertTrue(bbc.contains("[b]bold[/b]"))
        assertTrue(bbc.contains("[img]https://bbs.binmt.cc/data/a.png[/img]"))
        assertTrue(bbc.contains("[url=https://x.y/z]link[/url]"))
    }

    @Test
    fun loginPageDetected() {
        val html = """<html><body><form><input type="text" name="username"/><input type="submit" name="loginsubmit" value="登录"/></form></body></html>"""
        val check = io.mtluntan.app.data.parser.Parsing.checkPageError(Jsoup.parse(html), html)
        assertTrue(check.loginRequired)
    }
}