package io.mtluntan.app

import io.mtluntan.app.data.parser.ThreadListParser
import io.mtluntan.app.data.parser.ForumIndexParser
import io.mtluntan.app.data.parser.ThreadDetailParser
import io.mtluntan.app.data.parser.UserPagesParser
import io.mtluntan.app.data.parser.BbcBlocks
import io.mtluntan.app.data.parser.BbcBlock
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

    // ---------------- 账号用户名识别（用户反馈「名字识别错」） ----------------

    @Test
    fun profileUsernameFromComiisHeader() {
        val html = """
            <html><body>
            <div class="comiis_space_info">
              <div class="comiis_space_tx">
                <div class="user_img"><img src="uc_server/avatar.php?uid=42&size=middle"></div>
                <h2 class="fyy">张三</h2>
                <span class="kmlevs kmlv">Lv.7</span>
                <span class="kmlev">硕士生</span>
              </div>
            </div>
            <li>用户ID <span class="profile_rs">42</span></li>
            </body></html>
        """.trimIndent()
        val profile = UserPagesParser.parseProfile(html)
        assertEquals("张三", profile.username)
        assertEquals(42L, profile.uid)
    }

    @Test
    fun profileUsernameNotConfusedByGroupOrStats() {
        // h2 里是用户组、旁边全是统计数字时，不能把「硕士生 / Lv.7 / 帖子」当名字
        val html = """
            <html><body>
            <div class="comiis_space_tx">
              <img src="uc_server/avatar.php?uid=7">
              <h2 class="fyy">李四</h2>
              <span class="kmlev">硕士生</span>
              <span class="xg1">最后访问 2026-10-01</span>
            </div>
            <ul class="comiis_space_profileico">
              <li><em>帖子</em><span>128</span></li>
              <li><em>积分</em><span>999</span></li>
            </ul>
            </body></html>
        """.trimIndent()
        val profile = UserPagesParser.parseProfile(html)
        assertEquals("李四", profile.username)
    }

    @Test
    fun profileUsernameFromWelcomeTextWhenHeaderMissing() {
        // 登录校验页没有个人页头部，只有「欢迎您回来」
        val html = """
            <html><body>
            <div class="tip">欢迎您回来，王五，现在将转入登录前页面</div>
            </body></html>
        """.trimIndent()
        val profile = UserPagesParser.parseProfile(html)
        assertEquals("王五", profile.username)
    }

    @Test
    fun sanitizeUsernameStripsSuffixes() {
        assertEquals("赵六", UserPagesParser.sanitizeUsername("赵六 的个人资料"))
        assertEquals("赵六", UserPagesParser.sanitizeUsername("赵六的空间"))
        assertEquals("赵六", UserPagesParser.sanitizeUsername("赵六 - MT论坛"))
        assertEquals("", UserPagesParser.sanitizeUsername("登录"))
        assertEquals("", UserPagesParser.sanitizeUsername("12345"))
    }

    @Test
    fun looksLikeUsernameRejectsJunk() {
        assertTrue(UserPagesParser.looksLikeUsername("abc123"))
        assertTrue(UserPagesParser.looksLikeUsername("老王"))
        assertTrue(!UserPagesParser.looksLikeUsername("Lv.7"))
        assertTrue(!UserPagesParser.looksLikeUsername("帖子 128"))
        assertTrue(!UserPagesParser.looksLikeUsername("手机版"))
    }

    @Test
    fun mobileThreadMarksMainPostAndReplies() {
        // 移动模板：div.comiis_postli，第一条是楼主（正文），其余是评论
        val html = """
            <html><body>
            <div class="comiis_viewtit"><h2><div class="km_tits">测试主题</div></h2></div>
            <div class="comiis_postli" id="pid100">
              <div class="comiis_postli_top">
                <a class="top_user f_b" href="home.php?mod=space&amp;uid=1">楼主甲</a>
                <a class="postli_top_tximg"><img class="top_tximg" src="uc_server/avatar.php?uid=1"></a>
                <div class="comiis_postli_time"><span class="kmtime">2026-10-01</span></div>
              </div>
              <div class="comiis_message">这是正文内容</div>
            </div>
            <div class="comiis_postli" id="pid101">
              <div class="comiis_postli_top">
                <a class="top_user f_b" href="home.php?mod=space&amp;uid=2">楼层乙</a>
                <div class="comiis_postli_time"><span class="kmtime">2026-10-02</span></div>
              </div>
              <div class="comiis_message">这是评论内容</div>
            </div>
            <div class="comiis_pager"><span class="prev">1</span><span class="next">3</span></div>
            </body></html>
        """.trimIndent()
        val detail = ThreadDetailParser.parse(html, 1)
        assertTrue(detail.mainPost?.isMainPost == true)
        assertEquals("楼主甲", detail.mainPost?.authorName)
        assertEquals(1, detail.posts.size)
        assertEquals("楼层乙", detail.posts.first().authorName)
    }

    @Test
    fun bbcProtocolRelativeImagesAreKept() {
        // 站点常给 //bbs.binmt.cc/... 这种协议相对地址：1.1.5 之前会被漏成一段文字
        val blocks = BbcBlocks.parse("[img]//bbs.binmt.cc/data/attachment/forum/1.png[/img]")
        val img = blocks.filterIsInstance<BbcBlock.Image>().firstOrNull()
        assertTrue(img != null)
        assertEquals("https://bbs.binmt.cc/data/attachment/forum/1.png", img!!.url)
    }

    @Test
    fun bbcRelativeImagesAreKept() {
        val blocks = BbcBlocks.parse("[img]data/attachment/forum/2.jpg[/img]")
        val img = blocks.filterIsInstance<BbcBlock.Image>().firstOrNull()
        assertTrue(img != null)
        assertEquals("https://bbs.binmt.cc/data/attachment/forum/2.jpg", img!!.url)
    }

    @Test
    fun bbcAbsoluteImagesKeepHttps() {
        val blocks = BbcBlocks.parse("[img]http://example.com/a.png[/img]")
        assertEquals("http://example.com/a.png", blocks.filterIsInstance<BbcBlock.Image>().first().url)
    }
}
