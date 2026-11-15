# WeChat ClawBot integration — design notes

Status: implemented (v1). This document records the as-built design and the reasoning, so future
changes keep the same invariants. Feature summary: the parent binds their WeChat by scanning a QR
on the TV; after that they can query and control the TV from a WeChat chat, and receive pushes
about sessions and quiz rounds.

## Protocol

The TV speaks the open WeChat ClawBot backend protocol directly
([Tencent/openclaw-weixin docs/protocol.md](https://github.com/Tencent/openclaw-weixin/blob/main/docs/protocol.md)):
plain HTTPS + JSON, `getupdates` long polling (~35 s server hang), `sendmessage` with a
`context_token` that arrives with inbound messages. No WebSocket, no OpenClaw gateway, no extra
dependency — the client is `HttpURLConnection` like the rest of the project.

The `channel_version` header uses the doc's third-party suffix convention:
`3.0.1/planyourtv-<versionName>`.

Consequences of the protocol that shape the design:

- delivery is at-least-once → dedup by `message_id` in `BotCommandGate`;
- `errcode -14` requires a one-hour polling pause → per-binding pause, isolated;
- a QR may be refreshed at most three times → `BotBindingFlow` budget;
- `context_token` exists only after the parent's first message → pushes before first contact are
  queued (max 10) and flushed on it; the welcome message is a reply to that first message.

## Layout

All code lives in `app/src/main/java/app/orchard/tv/bot/` (new package, no Aerial file edits), the
parent-center rail row and pane in `ui/policy`, and exactly one hook in `PolicyProduct.install()`:

```
WeixinClawApi      protocol endpoints (QR / poll / send), pure codec split out as WeixinClawCodec
BotBindingFlow     one QR session state machine (Showing/Scanned/NeedCode/Bound/Stopped)
BotBindingStore    ≤4 bindings in TvStore (MMKV), durable write, replace-by-userId on re-bind
BotChannel         one poll coroutine per binding: cursor, backoff, pause, whitelist, dispatch
BotCommandParser   deterministic layers only: whitelist, synonyms, full/half width, Chinese
                   numerals, units; fuzzy suggestions never execute
BotCommandGate     dedup LRU(512), 6 commands/min/binding, one global execution lock
BotAuditLog        durable ring (200) of every control attempt: actor, command, accepted,
                   outcome; queried by the 审计 command
BotActions         thin mapping onto the coordinator's existing admission paths
                   (startLocalManualSession, tryBeginParentOverrideFromLockScreen,
                   endSessionByParent, enter/exitStudyMode, resumeChildGuard, QuizSettings)
BotAiParser        optional NL parsing via the parent's own OpenAI-compatible provider; fixed
                   whitelist output, 5–120 enforced here, 30 calls/day cap, Verify/Unbind unreachable
BotEventSource     enum-transition collectors over PolicyModeCoordinator/QuizRuntime state
BotRuntime         lifecycle, fan-out pushes (audit always on; timer/quiz per-binding toggles)
```

## Security invariants

Authentication is the binding, full stop (changed 2026-11-15 at the owner's request): chat carries
no PIN anywhere — not per command, not once, and not even the default-PIN bar. To bind, a parent
already passed the TV parent center's PIN gate, and a bound WeChat account is the only identity
whose messages are ever parsed. Accountability replaces per-command auth:

1. Only messages from the bound `ilink_user_id` are parsed; everything else is dropped silently.
2. Controls still run the full policy admission the lock screen runs (window/rest/study/activation/
   screen checks) — product rules, not authentication. The unlock path reuses the web eligibility
   set but passes the web layer's own default-PIN refusal through, since that is authentication,
   not state.
3. Every control attempt is recorded in `BotAuditLog` (durable ring of 200: actor, command,
   accepted or refused, one-line outcome) and queryable with `审计`; bind/unbind are logged too.
4. Every executed control command is instantly broadcast to all bound parents — deterrence and
   detection for the "child grabs a parent's phone" case.
5. Only deterministic parser layers can execute. Fuzzy matches reply with a suggestion; AI output
   must reduce to a whitelisted command, and AI control commands additionally need a Y confirm
   (misparse protection, not authentication).
6. The feature is off by default and opens no connection until enabled with at least one binding.

## Commands

Queries: `状态`/`今日`/`日报`/`规则`/`应用`/`题库`/`错题`/`关卡`/`诊断`/`审计`/`帮助`.
Controls (no PIN, audited + broadcast): `加时N` (5–120, accepts `加时二十`/`加时半小时`), `停止`,
`解锁`, `锁定`, `学习进/出`, `答题开/关`, `设置答题上限N` (0–240, 0=unlimited), `设置每轮题数N`
/`设置每题时限N`/`设置门槛N`/`设置单轮上限N` (challenge-config single fields, codec-validated),
`模式自动`/`模式手动` (needs one Y, like the TV's confirm dialog), `提醒开/关`
(5-minutes-left push), `撤销` (reverts the last undoable control within 60 s; Stop/Undo/Unbind are
not undoable).
Self-service: `解绑` removes only the sender's binding and leaves an audit entry.

`日报` shows today-so-far plus yesterday from `BotDailyReport` — a one-day rollup the event
collector feeds (the engine itself keeps no history): the day's absolute watched total, quiz round
count and earned minutes, archived into a single yesterday slot at local midnight. The same text
is pushed automatically once per local day after 21:00 (`BotDailyReport.AUTO_PUSH_HOUR`).

The quiz-earned daily cap is enforced in the quiz path itself, not only in chat:
`QuizEarnedStore` keeps one counter per local day, `QuizChallenge.closeFeedback` clamps a local
round's earned minutes to the remaining allowance (so the child's report matches the grant), and
`completeReview` records only after the reward session actually started. `今日`, `规则` and the
settled-round push all surface the running total.

## Open items / follow-ups

- Field names of the QR-status response are lenient (`status`/`qrcode_status`/`state`); tighten
  once verified against the live backend.
- The QR payload rendered on TV is the `qrcode` value (the poll key); if the live backend expects
  `qrcode_img_content` instead, switch `PolicyWeChatPane` to that string.
- Nickname at bind time: the protocol response has no display name, so bindings default to
  家长N; renaming is not in the UI yet (`BotBindingStore.rename` exists).
- 今日答题累计 counter (for the push text and a possible daily quiz-earnings cap) is not wired.
- Quiz config single-field tweaks (「设置 每轮题数10」) and rule edits stay web/TV-only in v1 by
  the agreed scope; add a chat surface later if parents ask.
- `BotRuntime.shutdown()` cancels its scope for good — tests only; production never restarts
  the process in place.
- No voice input (v1 is text-only; the AI provider has no ASR).
