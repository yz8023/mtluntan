# Feature Port Checklist — from MTForum (reference) to MT Luntan (Compose)

Reference: https://github.com/yz8023/mt-lun-tan (v3.8, Java/Views)
Target: /workspace/mtLuntan (Kotlin + Compose)
Site: https://bbs.binmt.cc (Discuz + Comiis mobile template, Alibaba ESA WAF)

Status legend: [ ] not started | [~] in progress | [x] done | [-] intentionally deferred

## Round 0 — Content-not-viewable & network alignment (root cause)

- [x] Pure-Kotlin WAF `acw_sc__v2` solver (no Rhino), verified vs real challenges
- [x] WafInterceptor cookie merge on retry (preserves session cookies)
- [x] Thread list parser aligned to reference `li.forumlist_li` selectors + lazy-image attrs + hidden-post detection
- [x] Thread detail parser aligned to reference mobile Comiis template (`div.comiis_viewtit`, `div.comiis_postli`, `div.comiis_message`) with PC-table fallback
- [x] ThreadScreen fetches mobile `&mobile=2` viewthread (was desktop PC page)
- [x] Mobile UA aligned to reference (`SM-S918B / Chrome/120`)
- [x] RequestThrottle: foreground lane (no wait) + background token bucket (foreground param in Net)
- [x] WebView acw-cookie sync in the login flow (WebView self-solves ESA via JS; full session incl. acw_sc__v2 imported via importSession)

## Round 1 — Session & account

- [ ] Multi-account login + switch (UserSessionManager / AccountManager / LoginBottomSheet pattern)
- [ ] Cookie snapshot whole-string save/restore (auth + saltkey pairing)
- [ ] Password custody via Android KeyStore AES-GCM (write-only, masked display)
- [ ] SessionGuard: silent auto re-login on 403 / session drop
- [ ] Account list in Mine screen with quick switch (guest → login)

## Round 2 — Content & interaction enhancements

- [ ] Browse history (HistoryStore) persisted locally
- [ ] Favorites list + favorite state (FavoritesCache / FavoritePrefetcher)
- [ ] Code block fold + copy (CodeBlockView pattern, comiis_blockcode)
- [ ] Quote block fold + copy
- [ ] Post/reply copy: main-post copy button, long-press reply copy menu (all / code only / with floor)
- [ ] Like users list (LikeUserFetcher)
- [ ] Hidden content in-place expand; hidden-post list marking
- [ ] Relative image URL fix + placeholder-gif skip (done for lists; check detail)
- [ ] Full-width in-place image + tap-to-preview (ZoomableImageView)

## Round 3 — Post / reply editor

- [ ] BBCode editor with 21 preset tags + RGB picker + rainbow text + live preview
- [ ] Quick-reply phrase strip (QuickReplyManager, one line per phrase)
- [ ] Reply image upload; edit own posts; report; delete own reply (whole form re-submit)
- [ ] Attachment parse + download with coin-spend confirm
- [ ] Draft persistence (DraftManager)

## Round 4 — Multi-account auto sign-in + records

- [ ] Daily scheduled sign-in (WorkManager, default 08:30) for all enabled accounts
- [ ] One-click sign-in for all accounts (isolated session, does not disturb foreground account)
- [ ] Per-account sign-in records: status / rank / reward / date
- [ ] Sign-in result notifications (expandable summary)
- [ ] Account spacing / ESA throttle between accounts (default 5s)
- [ ] Record center (LogCenterActivity): history / unlock logs / load timing / run logs, card-style
- [ ] PerfLog load-timing instrumentation

## Round 5 — Notices / PM / profile

- [ ] Notice list + detail (NoticeFragment / NoticeActivity)
- [ ] PM list + chat bubbles (ChatActivity, parseChatMessages)
- [ ] User profile page (UserProfileActivity: credits, friends, follow state)
- [ ] Friend list + follow/unfollow (extractFollowingUids)
- [ ] Blacklist (BlacklistManager / BlacklistSyncer)
- [ ] Credit detail (CreditDetailActivity)

## Round 6 — AI features

- [ ] AI chat session list + store (AiSessionStore)
- [ ] AI client + config (AiClient / AiConfigManager, user-provided key)
- [ ] AI summarize thread (AiSummarizeActivity)
- [ ] Auto-reply engine + scheduler (AutoReplyEngine / AutoReplyScheduler)

## Round 7 — Theme & polish

- [ ] Dark mode + theme colors (ThemeManager; Compose Material3 color scheme)
- [ ] Theme opacity presets
- [ ] Gradient text (8 presets)
- [ ] Badge baseline per-account (NoticeBadgeManager)
- [ ] Bottom bar tap-to-refresh; auto-hide on scroll

## Build & release

- [ ] Rebuild release APK after each round, publish to GitHub release v1.0.0 asset
- [ ] Keep WafChallengeSolver tests + ParserTest green
