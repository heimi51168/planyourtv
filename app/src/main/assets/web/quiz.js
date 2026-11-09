(() => {
  "use strict";

  const TEXTS = {
    zh: {
      eyebrow: "家长设置 · 局域网",
      title: "答题挑战",
      securityNote: "本页只配置答题挑战，不能解锁电视、关闭守护或修改系统设置。",
      status_loading: "正在连接电视…",
      status_none: "电视上还没有挑战",
      status_draft: "草稿已保存",
      status_ready: "挑战已就绪",
      status_running: "挑战进行中",
      status_settling: "正在结算",
      status_starting: "正在开始",
      status_start_failed: "启动失败",
      status_expired: "配置已过期",
      status_unreadable: "答题数据读取失败",
      status_blocked: "当前不可用",
      detail_none: "先在电视锁屏点击「开始挑战答题」，再扫描二维码。",
      detail_draft: "草稿只在网页和电视暂存，发布后锁屏才会出现入口。",
      detail_ready: "在电视锁屏点击「开始挑战答题」即可开始。",
      detail_running: "本轮在电视上进行，网页只读；结束后才能换题。",
      detail_expired: "二维码或挑战已过期，请在电视上重新打开设置页。",
      detail_unreadable: "请联系家长在电视上重置答题数据。",
      detail_blocked: "当前没有可用的挑战。",
      readyDetail: "{n} 道题，至少答对 {m} 道。",
      gate_scan_title: "请从电视扫码",
      gate_scan_text: "打开电视锁屏上的「开始挑战答题」，用手机扫描电视上的专用二维码。",
      gate_pair_title: "家长验证",
      gate_pair_text: "请输入电视上的 6 位家长密码。验证后当天内再次打开无需输入；次日 0 点后需要重新验证。网页不能解锁电视。",
      gate_pair_expired: "二维码已过期，请在电视上刷新后重新扫码。",
      gate_in_use_heading: "家长设置正在使用中",
      gate_in_use_lead: "同一时间只允许一台手机修改答题设置。",
      gate_in_use_title: "当前已有家长在使用",
      gate_in_use_text: "请稍后再试，或让已登录的手机退出「家长设置」；退出后这里会自动更新。",
      pinLabel: "请输入家长密码",
      pinButton: "进入设置",
      editor_locked: "本轮挑战进行中，网页只能查看摘要。",
      challengeTitle: "本次挑战",
      challengeHint: "题目只属于本次挑战；发布后电视锁屏才会显示入口。",
      addQuestion: "手动添加一道题",
      prompt: "题目",
      options: "选项（2–6 个，互不重复）",
      optionN: "选项 {n}",
      addOption: "增加选项",
      answer: "正确答案",
      answerPlaceholder: "请选择正确答案",
      explanation: "解题过程（可选；AI 出题会自动生成）",
      solution: "解题过程",
      points: "分数（1–5）",
      timeLimit: "本题时限（秒）",
      addToList: "加入本次挑战",
      emptyList: "还没有题目，先添加一道题。",
      remove: "移除",
      settingsTitle: "挑战规则",
      defaultTime: "默认时限（秒）",
      pullCount: "每轮题目数量",
      minutesPerQuestion: "每答对 1 题获得分钟",
      questionMinutes: "单题 {n} 分钟",
      minCorrect: "至少答对题数",
      maxEarned: "单次最多兑换分钟",
      answerDisplay: "答案显示时间（秒）",
      answerDisplayHint: "解析滚动到底部后才开始倒计时；倒计时结束会自动进入下一题。",
      shuffle: "打乱选项顺序",
      publish: "发布到电视",
      deleteSession: "删除挑战",
      disableQuiz: "关闭答题挑战",
      enableQuiz: "开启答题挑战",
      logout: "退出家长设置",
      disabled: "答题挑战已关闭。",
      enabled: "答题挑战已开启。",
      published: "已发布到电视。请在电视锁屏开始挑战。",
      deleted: "挑战已删除。",
      confirmDelete: "确定删除当前挑战？",
      confirmDisable: "确定关闭答题挑战？未开始的挑战会被删除。",
      qErrPrompt: "请填写题目。",
      qErrOptions: "请填写 2–6 个互不相同的选项。",
      qErrCount: "至少需要一道题。",
      qErrMinCorrect: "至少答对题数不能超过题目数量。",
      qErrNumber: "请填写有效的数字。",
      qErrAnswer: "请选择正确答案。",
      err_PIN_INVALID: "家长密码不正确。",
      err_PIN_LOCKED: "尝试次数过多，请稍后再试。",
      err_PIN_NOT_SET: "电视还没有设置家长密码。",
      err_PARENT_IN_USE: "当前已有家长在使用，请让已登录的手机退出「家长设置」后再试。",
      err_PAIR_EXPIRED: "二维码已过期，请在电视上刷新后重新扫码。",
      err_ADMIN_REQUIRED: "请先完成家长验证。",
      err_ADMIN_EXPIRED: "授权已过期，请重新输入家长密码。",
      err_ORIGIN: "请求来源不合法。",
      err_CONTENT_TYPE: "请求格式不正确。",
      err_INVALID_JSON: "请求内容无法读取。",
      err_INVALID_SESSION: "题目或规则不符合要求。",
      err_SESSION_RUNNING: "挑战进行中，暂时不能修改。",
      err_SESSION_EXISTS: "已有一份已就绪的挑战，请先删除或勾选替换。",
      err_SESSION_CONFLICT: "同一份挑战的内容不一致，请重新打开设置。",
      err_STORAGE_ERROR: "电视保存失败，请重试。",
      err_NOT_FOUND: "接口不存在或版本不匹配。",
      err_NETWORK: "无法连接电视，请确认手机和电视在同一局域网。",
      aiTitle: "AI 出题",
      aiIntro: "让模型生成选择题，再由你挑选加入本次挑战。也可以完全手动出题。",
      aiUnsupported: "此电视的 Android 版本不支持安全保存 API 密钥，因此只能手动出题。",
      aiUnsupportedBadge: "不可用",
      consentText: "我同意将出题要求（主题、年级、难度、数量等）发送给模型服务；不会发送家庭信息。API 密钥只保存在电视本地（系统支持时加密），不会回传给手机页面，也不会保存到本产品的云端；生成题目时只随请求发送给你配置的模型服务商。",
      consentHint: "不勾选时只能手动出题；撤回授权会删除电视上保存的密钥。",
      manualNote: "手动添加的题目只保存在电视上，不会上传。",
      baseUrl: "模型服务地址（HTTPS）",
      model: "模型名称",
      apiKey: "API 密钥",
      keyHintSaved: "已保存密钥，不会回显；留空表示沿用。",
      keyHintNew: "密钥只保存在电视本地（系统支持时加密）。",
      deepseekKeyHint: "默认服务是 DeepSeek。没有 Key？可在 DeepSeek 开放平台创建（可选，费用由你与该平台结算）。",
      saveProvider: "保存 AI 设置",
      testProvider: "测试连接",
      providerReady: "AI 出题已配置",
      providerNotReady: "未配置 AI 出题",
      providerSaved: "AI 出题设置已保存。",
      providerTestOk: "连接正常。",
      providerRevoked: "已撤回授权并删除密钥。",
      confirmRevoke: "撤回授权会删除电视上保存的 API 密钥，确定继续？",
      generateTitle: "AI 出题",
      generateHint: "选好年级和范围，提示词会自动组合；生成后勾选需要的题，直接发送到电视。",
      genGradeLabel: "年级",
      grade1: "小学一年级",
      grade2: "小学二年级",
      grade3: "小学三年级",
      grade4: "小学四年级",
      grade5: "小学五年级",
      grade6: "小学六年级",
      semesterFirst: "上册",
      semesterSecond: "下册",
      gradeWithSemester: "{grade}{semester}",
      genTextbookLabel: "教材",
      tbBubian: "统编版",
      tbRenjiao: "人教版",
      tbRenjiaoEj: "人教鄂教版",
      tbSujiao: "苏教版",
      tbBeishida: "北师大版",
      tbQingdao: "青岛版",
      tbPep: "人教版（PEP）",
      tbWaiyan: "外研社版",
      tbYilin: "译林版",
      tbKejiao: "教科版",
      tbGeneral: "综合",
      tbAll: "全部教材",
      genSubjectLabel: "学科",
      subjectChinese: "语文",
      subjectMath: "数学",
      subjectEnglish: "英语",
      subjectScience: "科学",
      subjectAll: "全科",
      subjectAllPrompt: "语文、数学、英语、科学",
      genScopeLabel: "想练什么",
      scopeUnit: "单元核心",
      scopeUnitSub: "本单元重点",
      scopeMidterm: "期中必考",
      scopeMidtermSub: "前半学期高频",
      scopeFinal: "期末必考",
      scopeFinalSub: "全学期综合",
      scopeReview: "总复习",
      scopeReviewSub: "查漏补缺",
      scopeMixed: "全科串烧",
      scopeMixedSub: "语数英科混合",
      genUnitLabel: "单元",
      genPromptLabel: "出题要求",
      genPromptBadgeAuto: "自动生成 · 可修改",
      genPromptBadgeEdited: "已手动修改",
      genPromptPlaceholder: "选好上面条件，提示词会自动写在这里。",
      genPromptTemplate: "请为{grade}出{subject}题，范围是{scope}。优先覆盖重点知识和易错题。",
      genSubjectWithBook: "{subject}（{book}）",
      promptScopeUnit: "第{unit}单元的核心知识点",
      promptScopeUnitTitle: "「{title}」的核心知识点",
      promptScopeUnitLessons: "「{title}」的核心知识点，重点覆盖：{lessons}",
      promptScopeUnitAll: "各科同步单元的核心知识点",
      promptScopeMidterm: "本学期期中考试范围",
      promptScopeFinal: "本学期期末考试范围",
      promptScopeReview: "本学期综合复习范围",
      promptScopeMixed: "全科混合复习",
      genAdvanced: "更多设置",
      genSummary: "{n} 题 · {o} 选项 · {d}",
      generateFootnote: "勾选后直接发送到电视，不需要保存草稿。",
      generateN: "生成 {n} 道题",
      genPromptRequired: "请填写出题要求。",
      genCount: "题目数量",
      genOptions: "每题选项数",
      genDifficulty: "难度",
      difficultyAny: "由模型决定",
      difficultyEasy: "简单",
      difficultyMedium: "中等",
      difficultyHard: "较难",
      genPoints: "每题分数",
      genTime: "每题时限（秒）",
      generateButton: "AI 出题",
      generating: "正在生成…（最多约 90 秒）",
      generatedN: "已生成 {n} 道题，请勾选后发送。",
      generationFailed: "生成没有完成。",
      candidateHint: "候选题只在电视内存中保留 5 分钟；勾选后可直接发送到电视。",
      sendSelectedToTv: "发送勾选的题到电视",
      candidatesAdded: "已发送 {n} 道题到电视。",
      candidatesSkipped: "跳过 {n} 道重复题。",
      noCandidatesSelected: "请至少勾选一道题。",
      err_CONSENT_REQUIRED: "请先同意隐私说明。",
      err_PROVIDER_KEY_REQUIRED: "请填写 API 密钥。",
      err_PROVIDER_KEY_INVALID: "API 密钥格式不正确。",
      err_PROVIDER_CONFIG: "模型服务地址或模型名称不正确。",
      err_AI_UNSUPPORTED: "此电视的 Android 版本不支持 AI 出题。",
      err_PROVIDER_TIMEOUT: "模型服务响应超时。",
      err_PROVIDER_NETWORK: "电视无法连接模型服务，请检查网络或代理。",
      err_PROVIDER_UNAUTHORIZED: "API 密钥无效或没有权限。",
      err_PROVIDER_BILLING: "模型服务账户余额不足。",
      err_PROVIDER_RATE_LIMIT: "请求太频繁，请稍后再试。",
      err_PROVIDER_UNAVAILABLE: "模型服务暂时不可用。",
      err_PROVIDER_HTTP: "模型服务返回错误。",
      err_PROVIDER_INVALID_RESPONSE: "模型返回的内容无法解析，请重试。",
      err_PROVIDER_NOT_CONFIGURED: "请先配置 AI 出题。",
      err_AI_EMPTY: "模型没有返回有效题目，请重试。",
      err_BUSY: "已有生成任务在进行。",
      err_JOB_EXPIRED: "生成任务已过期，请重新生成。",
      err_JOB_LOST: "生成任务不存在，请重新生成。",
      err_INVALID_JOB: "生成任务编号无效。",
      err_INVALID_REQUEST: "出题参数不正确。",
      warningsLabel: "提示",
      qErrTooMany: "一次挑战最多 10 道题。",
      settingsMenuTitle: "设置",
      languageLabel: "语言",
      close: "关闭",
      cancel: "取消",
      privacyInfo: "隐私说明",
      dangerZone: "危险操作",
      setupAiHint: "可现在配置，也可以稍后在齿轮中配置；手动出题不受影响。",
      rulesFirstHint: "只需设置一次，以后可在右上角齿轮中修改。",
      saveRules: "保存规则",
      saveAndPublish: "保存并发布",
      rulesSaved: "挑战规则已保存。",
      rulesNotSet: "未设置",
      rulesSummary: "至少答对 {m} 题",
      syncTitle: "答题配置同步",
      syncSynced: "已与家长端同步",
      syncPendingBinding: "正在上传本机答题配置",
      syncPendingEdit: "本机修改待上传",
      syncConflict: "家长端配置与本机修改冲突",
      syncCloudManaged: "由家长端管理",
      syncRetry: "重试同步",
      syncUseRemote: "使用家长端配置",
      syncLastNetwork: "网络不可用，会自动重试",
      syncLastServer: "服务器暂时不可用，会自动重试",
      syncLastConflict: "请选择重试或使用家长端配置",
      syncLastCloudManaged: "本机只读，请在家长端 App 中修改",
      syncLastStorage: "电视本地存储失败，请检查后重试",
      syncLastCloudUnsupported: "当前电视版本暂不支持云端题库，请升级电视端",
      rulesCloudHint: "答题配置已由家长端管理，本机规则只读。",
      err_CONFIG_CLOUD_MANAGED: "答题配置已由家长端管理，请在家长端 App 中修改。",
      aiConfigured: "已配置",
      aiNotConfigured: "未配置",
      aiUnavailableShort: "不可用",
      candidateTitle: "候选题",
      privacyTitle: "开始前，请了解数据如何保存",
      privacyIntro: "确认后才能进入；只需确认一次。",
      privacyLocalTitle: "保存在电视本地",
      privacyLocal: "题目、答案和进度只保存在电视上，挑战结束即删除。",
      privacyAiTitle: "AI 出题",
      privacyAi: "主题、年级、难度等出题要求会发送给你配置的模型服务商；不会发送家庭信息。",
      privacyKeyTitle: "API 密钥",
      privacyKey: "密钥只保存在电视本地（系统支持时加密），不会回显给手机页面，也不会保存到本产品的云端；生成题目时只随请求发送给你配置的模型服务商。",
      acceptAndContinue: "同意并继续",
      exit: "退出",
      cacheTitle: "题目缓存",
      cacheSummary: "可用 {n} 道",
      cacheIntro: "本地出题与云端题库共用这一份缓存；答题引擎只从缓存取题，不区分来源。",
      cacheAvailable: "可用",
      cacheReserved: "已预留",
      cacheConsumed: "已使用",
      cacheExpired: "已过期",
      cacheSourceValue: "当前来源：{s}",
      sourceLocal: "本地出题",
      sourceCloud: "云端题库",
      cacheRebuild: "按当前挑战重建缓存",
      cacheClear: "清空未使用题目",
      cacheRebuilt: "缓存已重建。",
      cacheCleared: "未使用题目已清空。",
      confirmClearCache: "确定清空尚未使用的题目？已发布但未开始的挑战也会受影响。",
      err_NO_SESSION: "当前没有可重建的挑战。",
      err_LEGACY_SESSION: "旧版挑战不能重建缓存，请重新发布。",
      err_CACHE_UNAVAILABLE: "题目缓存暂不可用，请稍后再试。",
      err_SESSION_RUNNING: "答题进行中，暂不能操作缓存。",
      err_NOT_READY: "当前挑战还没有发布，无法重建缓存。",
      err_INVALID_CONFIG: "挑战规则不符合要求。",
      footer: "答题挑战只在电视上开始和结算；手机不能远程解锁。",
    },
    en: {
      eyebrow: "Parent setup · Local network",
      title: "Quiz challenge",
      securityNote: "This page only configures the quiz. It cannot unlock the TV, disable the guard, or change system settings.",
      status_loading: "Connecting to the TV…",
      status_none: "No challenge on the TV",
      status_draft: "Draft saved",
      status_ready: "Challenge ready",
      status_running: "Challenge running",
      status_settling: "Settling",
      status_starting: "Starting",
      status_start_failed: "Start failed",
      status_expired: "Configuration expired",
      status_unreadable: "Quiz data could not be read",
      status_blocked: "Not available",
      detail_none: "Tap “Start quiz challenge” on the TV lock screen, then scan the QR code.",
      detail_draft: "Drafts stay on the phone and TV until you publish them.",
      detail_ready: "Tap “Start quiz challenge” on the TV lock screen to begin.",
      detail_running: "This round is running on the TV. The page is read-only until it ends.",
      detail_expired: "The QR code or challenge expired. Open the setup page on the TV again.",
      detail_unreadable: "Ask a parent to reset quiz data on the TV.",
      detail_blocked: "No challenge is available right now.",
      readyDetail: "{n} questions; at least {m} correct.",
      gate_scan_title: "Scan from the TV",
      gate_scan_text: "Open “Start quiz challenge” on the TV lock screen and scan its dedicated QR code.",
      gate_pair_title: "Parent verification",
      gate_pair_text: "Enter the 6-digit parent password shown on the TV. Reopening the page is password-free for the rest of the day; after local midnight it asks again. This page cannot unlock the TV.",
      gate_pair_expired: "The QR code expired. Refresh it on the TV and scan again.",
      gate_in_use_heading: "Parent settings are in use",
      gate_in_use_lead: "Only one phone can change quiz settings at a time.",
      gate_in_use_title: "A parent is already using the settings",
      gate_in_use_text: "Please try again shortly, or ask them to exit parent settings on their phone. This page updates automatically.",
      pinLabel: "Enter parent password",
      pinButton: "Open settings",
      editor_locked: "A challenge is running. This page can only show its summary.",
      challengeTitle: "This challenge",
      challengeHint: "Questions belong only to this challenge. The lock-screen entry appears after you publish.",
      addQuestion: "Add a question manually",
      prompt: "Question",
      options: "Options (2–6, all different)",
      optionN: "Option {n}",
      addOption: "Add option",
      answer: "Correct answer",
      answerPlaceholder: "Choose the correct answer",
      explanation: "Solution (optional; AI questions include one)",
      solution: "Solution",
      points: "Points (1–5)",
      timeLimit: "Time limit (seconds)",
      addToList: "Add to this challenge",
      emptyList: "No questions yet. Add one first.",
      remove: "Remove",
      settingsTitle: "Challenge rules",
      defaultTime: "Default time limit (seconds)",
      pullCount: "Questions per round",
      minutesPerQuestion: "Minutes per correct answer",
      questionMinutes: "{n} min per question",
      minCorrect: "Correct answers required",
      maxEarned: "Maximum earned minutes",
      answerDisplay: "Answer display time (seconds)",
      answerDisplayHint: "The countdown starts after the explanation is scrolled to the end; when it finishes, the next question opens automatically.",
      shuffle: "Shuffle options",
      publish: "Publish to TV",
      deleteSession: "Delete challenge",
      disableQuiz: "Disable quiz",
      enableQuiz: "Enable quiz",
      logout: "Exit parent settings",
      disabled: "Quiz challenge disabled.",
      enabled: "Quiz challenge enabled.",
      published: "Published to the TV. Start it from the lock screen.",
      deleted: "Challenge deleted.",
      confirmDelete: "Delete the current challenge?",
      confirmDisable: "Disable the quiz? An unstarted challenge will be deleted.",
      qErrPrompt: "Enter a question.",
      qErrOptions: "Enter 2–6 different options.",
      qErrCount: "Add at least one question.",
      qErrMinCorrect: "Required correct answers cannot exceed the question count.",
      qErrNumber: "Enter a valid number.",
      qErrAnswer: "Choose the correct answer.",
      err_PIN_INVALID: "The parent password is incorrect.",
      err_PIN_LOCKED: "Too many attempts. Try again later.",
      err_PIN_NOT_SET: "The TV has no parent password yet.",
      err_PARENT_IN_USE: "A parent is already using the settings. Ask them to exit parent settings on their phone, then try again.",
      err_PAIR_EXPIRED: "The QR code expired. Refresh it on the TV and scan again.",
      err_ADMIN_REQUIRED: "Verify the parent password first.",
      err_ADMIN_EXPIRED: "The setup session expired. Enter the parent password again.",
      err_ORIGIN: "The request origin is not allowed.",
      err_CONTENT_TYPE: "Unsupported request format.",
      err_INVALID_JSON: "The request could not be read.",
      err_INVALID_SESSION: "The questions or rules are not valid.",
      err_SESSION_RUNNING: "The challenge is running and cannot be changed now.",
      err_SESSION_EXISTS: "A ready challenge already exists. Delete it or replace it first.",
      err_SESSION_CONFLICT: "The same challenge changed elsewhere. Reopen settings.",
      err_STORAGE_ERROR: "The TV could not save. Try again.",
      err_NOT_FOUND: "The endpoint is missing or the version does not match.",
      err_NETWORK: "Cannot reach the TV. Check that the phone and TV are on the same LAN.",
      aiTitle: "AI questions",
      aiIntro: "Let a model draft multiple-choice questions, then choose which ones join this challenge. Manual questions work without AI.",
      aiUnsupported: "This TV's Android version cannot store an API key securely, so only manual questions are available.",
      aiUnsupportedBadge: "Unavailable",
      consentText: "I agree that question requirements (subject, grade, difficulty, count, and similar settings) may be sent to the model service. No family information is sent. The API key is stored only on the TV (encrypted when the system supports it), is never sent back to the phone page, and is never saved to our cloud; when generating questions it is sent only to the model provider you configure.",
      consentHint: "Without consent only manual questions are available. Revoking consent deletes the saved key from the TV.",
      manualNote: "Questions added manually are stored only on the TV and are not uploaded.",
      baseUrl: "Model service URL (HTTPS)",
      model: "Model name",
      apiKey: "API key",
      keyHintSaved: "A key is saved and never shown; leave this blank to keep it.",
      keyHintNew: "The key is stored only on the TV, encrypted when the system supports it.",
      deepseekKeyHint: "DeepSeek is the default. Need a key? Create one on the DeepSeek platform (optional; billed by DeepSeek).",
      saveProvider: "Save AI settings",
      testProvider: "Test connection",
      providerReady: "AI questions configured",
      providerNotReady: "AI questions not configured",
      providerSaved: "AI settings saved.",
      providerTestOk: "The connection works.",
      providerRevoked: "Consent revoked and the saved key deleted.",
      confirmRevoke: "Revoking consent deletes the saved API key from the TV. Continue?",
      generateTitle: "Generate with AI",
      generateHint: "Pick a grade and scope; the request is written for you. Tick the questions you want and send them to the TV.",
      genGradeLabel: "Grade",
      grade1: "Grade 1",
      grade2: "Grade 2",
      grade3: "Grade 3",
      grade4: "Grade 4",
      grade5: "Grade 5",
      grade6: "Grade 6",
      semesterFirst: "Semester 1",
      semesterSecond: "Semester 2",
      gradeWithSemester: "{grade} · {semester}",
      genTextbookLabel: "Textbook",
      tbBubian: "Unified edition",
      tbRenjiao: "Renjiao edition",
      tbRenjiaoEj: "Renjiao Hubei edition",
      tbSujiao: "Sujiao edition",
      tbBeishida: "BNU edition",
      tbQingdao: "Qingdao edition",
      tbPep: "PEP edition",
      tbWaiyan: "FLTRP edition",
      tbYilin: "Yilin edition",
      tbKejiao: "Science edition",
      tbGeneral: "General",
      tbAll: "All textbooks",
      genSubjectLabel: "Subject",
      subjectChinese: "Chinese",
      subjectMath: "Math",
      subjectEnglish: "English",
      subjectScience: "Science",
      subjectAll: "All subjects",
      subjectAllPrompt: "Chinese, Math, English, and Science",
      genScopeLabel: "What to practice",
      scopeUnit: "Unit core",
      scopeUnitSub: "Key unit concepts",
      scopeMidterm: "Midterm focus",
      scopeMidtermSub: "First-half high-frequency",
      scopeFinal: "Final focus",
      scopeFinalSub: "Whole-term review",
      scopeReview: "Overall review",
      scopeReviewSub: "Find and fill gaps",
      scopeMixed: "All-subject mix",
      scopeMixedSub: "Chinese, math, English, science",
      genUnitLabel: "Unit",
      genPromptLabel: "Question request",
      genPromptBadgeAuto: "Auto-generated · editable",
      genPromptBadgeEdited: "Edited",
      genPromptPlaceholder: "Choose the options above; the request appears here.",
      genPromptTemplate: "Create questions for {grade} {subject}, covering {scope}. Focus on key knowledge and common mistakes.",
      genSubjectWithBook: "{subject} ({book})",
      promptScopeUnit: "Unit {unit} core concepts",
      promptScopeUnitTitle: "core concepts of \"{title}\"",
      promptScopeUnitLessons: "core concepts of \"{title}\", focusing on: {lessons}",
      promptScopeUnitAll: "core concepts from the matching units across subjects",
      promptScopeMidterm: "this term's midterm exam scope",
      promptScopeFinal: "this term's final exam scope",
      promptScopeReview: "this term's overall review scope",
      promptScopeMixed: "a mixed all-subject review",
      genAdvanced: "More settings",
      genSummary: "{n} questions · {o} options · {d}",
      generateFootnote: "Tick the questions you want and send them straight to the TV. No draft step.",
      generateN: "Generate {n} questions",
      genPromptRequired: "Add a question request.",
      genCount: "Number of questions",
      genOptions: "Options per question",
      genDifficulty: "Difficulty",
      difficultyAny: "Model decides",
      difficultyEasy: "Easy",
      difficultyMedium: "Medium",
      difficultyHard: "Hard",
      genPoints: "Points per question",
      genTime: "Time limit per question (seconds)",
      generateButton: "Generate questions",
      generating: "Generating… (usually under 90 seconds)",
      generatedN: "Generated {n} questions. Tick the ones to send.",
      generationFailed: "Generation did not finish.",
      candidateHint: "Candidates stay in TV memory for 5 minutes; tick the ones to send straight to the TV.",
      sendSelectedToTv: "Send ticked questions to TV",
      candidatesAdded: "Sent {n} questions to the TV.",
      candidatesSkipped: "Skipped {n} duplicate questions.",
      noCandidatesSelected: "Tick at least one question.",
      err_CONSENT_REQUIRED: "Accept the privacy notice first.",
      err_PROVIDER_KEY_REQUIRED: "Enter the API key.",
      err_PROVIDER_KEY_INVALID: "The API key format is not valid.",
      err_PROVIDER_CONFIG: "The service URL or model name is not valid.",
      err_AI_UNSUPPORTED: "AI questions are not supported on this TV's Android version.",
      err_PROVIDER_TIMEOUT: "The model service timed out.",
      err_PROVIDER_NETWORK: "The TV cannot reach the model service. Check its network or proxy.",
      err_PROVIDER_UNAUTHORIZED: "The API key is invalid or has no permission.",
      err_PROVIDER_BILLING: "The model service account has insufficient balance.",
      err_PROVIDER_RATE_LIMIT: "Too many requests. Try again shortly.",
      err_PROVIDER_UNAVAILABLE: "The model service is temporarily unavailable.",
      err_PROVIDER_HTTP: "The model service returned an error.",
      err_PROVIDER_INVALID_RESPONSE: "The model response could not be parsed. Try again.",
      err_PROVIDER_NOT_CONFIGURED: "Configure AI questions first.",
      err_AI_EMPTY: "The model returned no valid questions. Try again.",
      err_BUSY: "Another generation is already running.",
      err_JOB_EXPIRED: "The generation job expired. Generate again.",
      err_JOB_LOST: "The generation job no longer exists. Generate again.",
      err_INVALID_JOB: "The generation job id is not valid.",
      err_INVALID_REQUEST: "The generation settings are not valid.",
      warningsLabel: "Notes",
      qErrTooMany: "A challenge can have at most 10 questions.",
      settingsMenuTitle: "Settings",
      languageLabel: "Language",
      close: "Close",
      cancel: "Cancel",
      privacyInfo: "Privacy notice",
      dangerZone: "Danger zone",
      setupAiHint: "Set this up now, or later from the gear. Manual questions work without AI.",
      rulesFirstHint: "Set these once. You can change them later from the gear.",
      saveRules: "Save rules",
      saveAndPublish: "Save and publish",
      rulesSaved: "Challenge rules saved.",
      rulesNotSet: "Not set",
      rulesSummary: "At least {m} correct",
      syncTitle: "Quiz config sync",
      syncSynced: "Synced with the parent app",
      syncPendingBinding: "Uploading this TV's quiz config",
      syncPendingEdit: "Local changes waiting to upload",
      syncConflict: "Parent config conflicts with this TV's changes",
      syncCloudManaged: "Managed by the parent app",
      syncRetry: "Retry sync",
      syncUseRemote: "Use parent config",
      syncLastNetwork: "Network unavailable; retrying automatically",
      syncLastServer: "Server unavailable; retrying automatically",
      syncLastConflict: "Retry, or use the parent configuration",
      syncLastCloudManaged: "Read-only here; edit in the Youxiaobei app",
      syncLastStorage: "TV storage failed; check and retry",
      syncLastCloudUnsupported: "This TV version does not support cloud questions yet; please update the TV app",
      rulesCloudHint: "Quiz settings are managed by the parent app and are read-only here.",
      err_CONFIG_CLOUD_MANAGED: "Quiz settings are managed by the parent app; edit them in the Youxiaobei app.",
      aiConfigured: "Configured",
      aiNotConfigured: "Not configured",
      aiUnavailableShort: "Unavailable",
      candidateTitle: "Generated questions",
      privacyTitle: "Before you start",
      privacyIntro: "Confirm to continue. This is asked once.",
      privacyLocalTitle: "Stored on the TV",
      privacyLocal: "Questions, answers, and progress stay on the TV and are deleted when the challenge ends.",
      privacyAiTitle: "AI questions",
      privacyAi: "The subject, grade, difficulty, and similar requirements are sent to the model provider you configure. No family information is sent.",
      privacyKeyTitle: "API key",
      privacyKey: "The key is stored only on the TV (encrypted when the system supports it) and is never shown again on this page. It is never saved to our cloud; when generating questions it is sent only to the model provider you configure.",
      acceptAndContinue: "Agree and continue",
      exit: "Exit",
      cacheTitle: "Question cache",
      cacheSummary: "{n} available",
      cacheIntro: "Local questions and the cloud bank share this cache. The quiz engine only reads the cache; it does not care where a question came from.",
      cacheAvailable: "Available",
      cacheReserved: "Reserved",
      cacheConsumed: "Used",
      cacheExpired: "Expired",
      cacheSourceValue: "Current source: {s}",
      sourceLocal: "Local generation",
      sourceCloud: "Cloud bank",
      cacheRebuild: "Rebuild from this challenge",
      cacheClear: "Clear unused questions",
      cacheRebuilt: "The cache was rebuilt.",
      cacheCleared: "Unused questions were cleared.",
      confirmClearCache: "Clear all unused questions? A published challenge that has not started is affected too.",
      err_NO_SESSION: "There is no challenge to rebuild.",
      err_LEGACY_SESSION: "An older challenge cannot be rebuilt; publish it again.",
      err_CACHE_UNAVAILABLE: "The question cache is unavailable. Try again later.",
      err_SESSION_RUNNING: "A quiz is in progress; the cache cannot be changed now.",
      err_NOT_READY: "The challenge is not published yet, so it cannot rebuild the cache.",
      err_INVALID_CONFIG: "The challenge rules are not valid.",
      footer: "Challenges start and settle on the TV only; the phone cannot unlock it remotely.",
    },
    ru: {
      eyebrow: "Настройка родителей · Локальная сеть",
      title: "Викторина",
      securityNote: "Страница настраивает только викторину. Она не разблокирует телевизор и не меняет защиту.",
      status_loading: "Подключение к телевизору…",
      status_none: "На телевизоре нет викторины",
      status_draft: "Черновик сохранён",
      status_ready: "Викторина готова",
      status_running: "Викторина идёт",
      status_settling: "Подсчёт",
      status_starting: "Запуск",
      status_start_failed: "Ошибка запуска",
      status_expired: "Настройка устарела",
      status_unreadable: "Не удалось прочитать данные",
      status_blocked: "Недоступно",
      detail_none: "Нажмите «Начать викторину» на экране блокировки и отсканируйте QR-код.",
      detail_draft: "Черновик хранится на телефоне и телевизоре до публикации.",
      detail_ready: "Нажмите «Начать викторину» на экране блокировки телевизора.",
      detail_running: "Раунд идёт на телевизоре. Страница только для чтения до конца.",
      detail_expired: "QR-код или викторина устарели. Откройте настройку на телевизоре снова.",
      detail_unreadable: "Попросите родителя сбросить данные викторины на телевизоре.",
      detail_blocked: "Сейчас нет доступной викторины.",
      readyDetail: "Вопросов: {n}; нужно верных: {m}.",
      gate_scan_title: "Сканируйте с телевизора",
      gate_scan_text: "Откройте «Начать викторину» на экране блокировки и отсканируйте специальный QR-код.",
      gate_pair_title: "Проверка родителя",
      gate_pair_text: "Введите 6-значный пароль родителя с телевизора. В течение дня страница открывается без пароля; после местной полуночи потребуется ввести его снова. Страница не разблокирует телевизор.",
      gate_pair_expired: "QR-код устарел. Обновите его на телевизоре и отсканируйте снова.",
      gate_in_use_heading: "Настройки родителя уже открыты",
      gate_in_use_lead: "Изменять настройки викторины можно только с одного телефона одновременно.",
      gate_in_use_title: "Настройки уже открыты у другого родителя",
      gate_in_use_text: "Попробуйте позже или попросите его выйти из настроек на своём телефоне. Страница обновится автоматически.",
      pinLabel: "Введите пароль родителя",
      pinButton: "Открыть настройки",
      editor_locked: "Викторина идёт. Страница показывает только сводку.",
      challengeTitle: "Эта викторина",
      challengeHint: "Вопросы принадлежат только этой викторине. Вход появится после публикации.",
      addQuestion: "Добавить вопрос вручную",
      prompt: "Вопрос",
      options: "Варианты (2–6, без повторов)",
      optionN: "Вариант {n}",
      addOption: "Добавить вариант",
      answer: "Правильный ответ",
      answerPlaceholder: "Выберите правильный ответ",
      explanation: "Решение (необязательно; ИИ добавляет его автоматически)",
      solution: "Решение",
      points: "Баллы (1–5)",
      timeLimit: "Лимит времени (секунды)",
      addToList: "Добавить в викторину",
      emptyList: "Вопросов пока нет. Добавьте первый.",
      remove: "Удалить",
      settingsTitle: "Правила",
      defaultTime: "Лимит по умолчанию (секунды)",
      pullCount: "Вопросов за раунд",
      minutesPerQuestion: "Минут за верный ответ",
      questionMinutes: "{n} мин за вопрос",
      minCorrect: "Нужно верных ответов",
      maxEarned: "Максимум минут",
      answerDisplay: "Время показа ответа (секунды)",
      answerDisplayHint: "Отсчёт начинается после прокрутки объяснения до конца; затем следующий вопрос откроется автоматически.",
      shuffle: "Перемешивать варианты",
      publish: "Опубликовать на ТВ",
      deleteSession: "Удалить викторину",
      disableQuiz: "Отключить викторину",
      enableQuiz: "Включить викторину",
      logout: "Выйти из настроек",
      disabled: "Викторина отключена.",
      enabled: "Викторина включена.",
      published: "Опубликовано на телевизоре. Начните с экрана блокировки.",
      deleted: "Викторина удалена.",
      confirmDelete: "Удалить текущую викторину?",
      confirmDisable: "Отключить викторину? Не начатая викторина будет удалена.",
      qErrPrompt: "Введите вопрос.",
      qErrOptions: "Введите 2–6 разных вариантов.",
      qErrCount: "Добавьте хотя бы один вопрос.",
      qErrMinCorrect: "Нужно верных больше, чем вопросов.",
      qErrNumber: "Введите корректное число.",
      qErrAnswer: "Выберите правильный ответ.",
      err_PIN_INVALID: "Неверный пароль родителя.",
      err_PIN_LOCKED: "Слишком много попыток. Повторите позже.",
      err_PIN_NOT_SET: "На телевизоре ещё нет пароля родителя.",
      err_PARENT_IN_USE: "Настройки уже открыты у другого родителя. Попросите его выйти из настроек на своём телефоне и повторите попытку.",
      err_PAIR_EXPIRED: "QR-код устарел. Обновите его на телевизоре.",
      err_ADMIN_REQUIRED: "Сначала подтвердите пароль родителя.",
      err_ADMIN_EXPIRED: "Сеанс истёк. Введите пароль родителя снова.",
      err_ORIGIN: "Источник запроса не разрешён.",
      err_CONTENT_TYPE: "Неподдерживаемый формат запроса.",
      err_INVALID_JSON: "Запрос не удалось прочитать.",
      err_INVALID_SESSION: "Вопросы или правила неверны.",
      err_SESSION_RUNNING: "Викторина идёт, изменения невозможны.",
      err_SESSION_EXISTS: "Готовая викторина уже есть. Удалите или замените её.",
      err_SESSION_CONFLICT: "Та же викторина изменена elsewhere. Откройте настройку снова.",
      err_STORAGE_ERROR: "Телевизор не сохранил данные. Повторите.",
      err_NOT_FOUND: "Интерфейс не найден или версия не совпадает.",
      err_NETWORK: "Нет связи с телевизором. Проверьте локальную сеть.",
      aiTitle: "ИИ-вопросы",
      aiIntro: "Модель создаёт вопросы с вариантами, а вы выбираете нужные для этой викторины. Можно добавлять вопросы вручную.",
      aiUnsupported: "Эта версия Android на телевизоре не позволяет безопасно хранить ключ API, поэтому доступны только вопросы, созданные вручную.",
      aiUnsupportedBadge: "Недоступно",
      consentText: "Я согласен отправлять параметры задания (тема, класс, сложность, количество) сервису модели; семейные данные не отправляются. Ключ API хранится только на телевизоре (шифруется, если система это поддерживает), не возвращается на страницу телефона и не сохраняется в облаке продукта; при генерации вопросов он отправляется только выбранному вами сервису модели.",
      consentHint: "Без согласия доступны только ручные вопросы. Отзыв согласия удалит ключ с телевизора.",
      manualNote: "Вопросы, добавленные вручную, хранятся только на телевизоре и не отправляются в интернет.",
      baseUrl: "Адрес сервиса модели (HTTPS)",
      model: "Название модели",
      apiKey: "Ключ API",
      keyHintSaved: "Ключ сохранён и не показывается; оставьте поле пустым, чтобы сохранить его.",
      keyHintNew: "Ключ хранится только на телевизоре; шифрование используется, если система его поддерживает.",
      deepseekKeyHint: "По умолчанию используется DeepSeek. Нет ключа? Создайте его на платформе DeepSeek (необязательно; оплачивается DeepSeek).",
      saveProvider: "Сохранить настройки ИИ",
      testProvider: "Проверить подключение",
      providerReady: "ИИ-вопросы настроены",
      providerNotReady: "ИИ-вопросы не настроены",
      providerSaved: "Настройки ИИ сохранены.",
      providerTestOk: "Подключение работает.",
      providerRevoked: "Согласие отозвано, ключ удалён.",
      confirmRevoke: "Отзыв согласия удалит ключ API с телевизора. Продолжить?",
      generateTitle: "Создать вопросы с ИИ",
      generateHint: "Выберите класс и диапазон — запрос составится автоматически. Отметьте нужные вопросы и отправьте их на телевизор.",
      genGradeLabel: "Класс",
      grade1: "1 класс",
      grade2: "2 класс",
      grade3: "3 класс",
      grade4: "4 класс",
      grade5: "5 класс",
      grade6: "6 класс",
      semesterFirst: "1-е полугодие",
      semesterSecond: "2-е полугодие",
      gradeWithSemester: "{grade} · {semester}",
      genTextbookLabel: "Учебник",
      tbBubian: "Единое издание",
      tbRenjiao: "Жэньцзяо",
      tbRenjiaoEj: "Жэньцзяо (Хубэй)",
      tbSujiao: "Субань",
      tbBeishida: "Бэйшида",
      tbQingdao: "Циндао",
      tbPep: "PEP",
      tbWaiyan: "Вайянь",
      tbYilin: "Илинь",
      tbKejiao: "Кэцзяо",
      tbGeneral: "Общий",
      tbAll: "Все учебники",
      genSubjectLabel: "Предмет",
      subjectChinese: "Китайский язык",
      subjectMath: "Математика",
      subjectEnglish: "Английский язык",
      subjectScience: "Естествознание",
      subjectAll: "Все предметы",
      subjectAllPrompt: "китайский язык, математика, английский язык и естествознание",
      genScopeLabel: "Что тренируем",
      scopeUnit: "Основное в блоке",
      scopeUnitSub: "Ключевые темы блока",
      scopeMidterm: "К полугодовой",
      scopeMidtermSub: "Частые темы первой половины",
      scopeFinal: "К итоговой",
      scopeFinalSub: "Весь семестр",
      scopeReview: "Общее повторение",
      scopeReviewSub: "Закрыть пробелы",
      scopeMixed: "Микс всех предметов",
      scopeMixedSub: "Китайский, математика, английский, естествознание",
      genUnitLabel: "Блок",
      genPromptLabel: "Запрос на вопросы",
      genPromptBadgeAuto: "Создано автоматически · можно изменить",
      genPromptBadgeEdited: "Изменено",
      genPromptPlaceholder: "Выберите условия выше — запрос появится здесь.",
      genPromptTemplate: "Составь вопросы для {grade} по предмету «{subject}», тема: {scope}. Основной упор — важные знания и типичные ошибки.",
      genSubjectWithBook: "{subject} ({book})",
      promptScopeUnit: "ключевые знания блока {unit}",
      promptScopeUnitTitle: "ключевые знания раздела «{title}»",
      promptScopeUnitLessons: "ключевые знания раздела «{title}», прежде всего: {lessons}",
      promptScopeUnitAll: "ключевые знания параллельных блоков по всем предметам",
      promptScopeMidterm: "программа полугодовой контрольной этого семестра",
      promptScopeFinal: "программа итоговой контрольной этого семестра",
      promptScopeReview: "общий обзор программы этого семестра",
      promptScopeMixed: "смешанное повторение по всем предметам",
      genAdvanced: "Дополнительно",
      genSummary: "{n} вопр. · {o} вар. · {d}",
      generateFootnote: "Отметьте вопросы и отправьте их сразу на телевизор — без черновика.",
      generateN: "Создать вопросы: {n}",
      genPromptRequired: "Введите запрос на вопросы.",
      genCount: "Количество вопросов",
      genOptions: "Вариантов на вопрос",
      genDifficulty: "Сложность",
      difficultyAny: "Выберет модель",
      difficultyEasy: "Легко",
      difficultyMedium: "Средне",
      difficultyHard: "Сложно",
      genPoints: "Баллы за вопрос",
      genTime: "Лимит времени (секунды)",
      generateButton: "Создать вопросы",
      generating: "Создание… (до ~90 секунд)",
      generatedN: "Создано вопросов: {n}. Отметьте нужные для отправки.",
      generationFailed: "Создание не завершено.",
      candidateHint: "Кандидаты хранятся в памяти телевизора 5 минут; отмеченные можно сразу отправить на ТВ.",
      sendSelectedToTv: "Отправить отмеченные вопросы на ТВ",
      candidatesAdded: "Отправлено вопросов на ТВ: {n}.",
      candidatesSkipped: "Пропущено повторяющихся: {n}.",
      noCandidatesSelected: "Отметьте хотя бы один вопрос.",
      err_CONSENT_REQUIRED: "Сначала примите уведомление о конфиденциальности.",
      err_PROVIDER_KEY_REQUIRED: "Введите ключ API.",
      err_PROVIDER_KEY_INVALID: "Неверный формат ключа API.",
      err_PROVIDER_CONFIG: "Неверный адрес сервиса или название модели.",
      err_AI_UNSUPPORTED: "ИИ-вопросы не поддерживаются этой версией Android на телевизоре.",
      err_PROVIDER_TIMEOUT: "Сервис модели не ответил вовремя.",
      err_PROVIDER_NETWORK: "Телевизор не может подключиться к сервису модели. Проверьте сеть или прокси.",
      err_PROVIDER_UNAUTHORIZED: "Ключ API недействителен или не имеет прав.",
      err_PROVIDER_BILLING: "На счёте сервиса модели недостаточно средств.",
      err_PROVIDER_RATE_LIMIT: "Слишком много запросов. Повторите позже.",
      err_PROVIDER_UNAVAILABLE: "Сервис модели временно недоступен.",
      err_PROVIDER_HTTP: "Сервис модели вернул ошибку.",
      err_PROVIDER_INVALID_RESPONSE: "Ответ модели не удалось разобрать. Повторите.",
      err_PROVIDER_NOT_CONFIGURED: "Сначала настройте ИИ-вопросы.",
      err_AI_EMPTY: "Модель не вернула корректных вопросов. Повторите.",
      err_BUSY: "Уже выполняется другая генерация.",
      err_JOB_EXPIRED: "Задание генерации устарело. Создайте заново.",
      err_JOB_LOST: "Задание генерации не найдено. Создайте заново.",
      err_INVALID_JOB: "Неверный номер задания генерации.",
      err_INVALID_REQUEST: "Неверные параметры генерации.",
      warningsLabel: "Примечания",
      qErrTooMany: "В одной викторине не больше 10 вопросов.",
      settingsMenuTitle: "Настройки",
      languageLabel: "Язык",
      close: "Закрыть",
      cancel: "Отмена",
      privacyInfo: "Конфиденциальность",
      dangerZone: "Опасные действия",
      setupAiHint: "Настройте сейчас или позже через шестерёнку. Ручные вопросы работают без ИИ.",
      rulesFirstHint: "Настраивается один раз. Позже можно изменить через шестерёнку.",
      saveRules: "Сохранить правила",
      saveAndPublish: "Сохранить и опубликовать",
      rulesSaved: "Правила викторины сохранены.",
      rulesNotSet: "Не задано",
      rulesSummary: "Верных: {m}",
      syncTitle: "Синхронизация настроек квиза",
      syncSynced: "Синхронизировано с приложением родителя",
      syncPendingBinding: "Загрузка настроек квиза с телевизора",
      syncPendingEdit: "Локальные изменения ждут отправки",
      syncConflict: "Настройки родителя конфликтуют с изменениями на телевизоре",
      syncCloudManaged: "Управляется приложением родителя",
      syncRetry: "Повторить синхронизацию",
      syncUseRemote: "Использовать настройки родителя",
      syncLastNetwork: "Сеть недоступна; повтор автоматически",
      syncLastServer: "Сервер недоступен; повтор автоматически",
      syncLastConflict: "Повторите или используйте настройки родителя",
      syncLastCloudManaged: "Только чтение; измените в приложении Youxiaobei",
      syncLastStorage: "Ошибка локального хранилища; проверьте и повторите",
      syncLastCloudUnsupported: "Эта версия телевизора пока не поддерживает облачные вопросы; обновите приложение",
      rulesCloudHint: "Настройки квиза управляются приложением родителя и доступны только для чтения.",
      err_CONFIG_CLOUD_MANAGED: "Настройки квиза управляются приложением родителя; измените их в приложении Youxiaobei.",
      aiConfigured: "Настроено",
      aiNotConfigured: "Не настроено",
      aiUnavailableShort: "Недоступно",
      candidateTitle: "Созданные вопросы",
      privacyTitle: "Перед началом",
      privacyIntro: "Подтвердите, чтобы продолжить. Это нужно сделать один раз.",
      privacyLocalTitle: "Хранится на телевизоре",
      privacyLocal: "Вопросы, ответы и прогресс остаются на телевизоре и удаляются после завершения викторины.",
      privacyAiTitle: "Вопросы ИИ",
      privacyAi: "Тема, класс, сложность и похожие параметры отправляются выбранному вами сервису модели. Семейные данные не отправляются.",
      privacyKeyTitle: "Ключ API",
      privacyKey: "Ключ хранится только на телевизоре (шифруется, если система это поддерживает) и больше не показывается на этой странице. Он не сохраняется в облаке продукта; при генерации вопросов он отправляется только выбранному вами сервису модели.",
      acceptAndContinue: "Согласиться и продолжить",
      exit: "Выйти",
      cacheTitle: "Кэш вопросов",
      cacheSummary: "Доступно: {n}",
      cacheIntro: "Локальные вопросы и облачный банк используют этот кэш. Движок викторины читает только кэш и не зависит от источника.",
      cacheAvailable: "Доступно",
      cacheReserved: "Зарезервировано",
      cacheConsumed: "Использовано",
      cacheExpired: "Истекло",
      cacheSourceValue: "Текущий источник: {s}",
      sourceLocal: "Локальная генерация",
      sourceCloud: "Облачный банк",
      cacheRebuild: "Пересобрать из этого задания",
      cacheClear: "Очистить неиспользованные",
      cacheRebuilt: "Кэш пересобран.",
      cacheCleared: "Неиспользованные вопросы очищены.",
      confirmClearCache: "Очистить все неиспользованные вопросы? Опубликованное, но не начатое задание тоже будет затронуто.",
      err_NO_SESSION: "Нет задания для пересборки.",
      err_LEGACY_SESSION: "Старое задание нельзя пересобрать; опубликуйте его заново.",
      err_CACHE_UNAVAILABLE: "Кэш вопросов недоступен. Попробуйте позже.",
      err_SESSION_RUNNING: "Викторина идёт; сейчас кэш менять нельзя.",
      err_NOT_READY: "Задание ещё не опубликовано, поэтому кэш нельзя пересобрать.",
      err_INVALID_CONFIG: "Правила викторины неверны.",
      footer: "Викторина начинается и завершается только на телевизоре.",
    },
    uk: {
      eyebrow: "Налаштування батьків · Локальна мережа",
      title: "Вікторина",
      securityNote: "Сторінка налаштовує лише вікторину. Вона не розблоковує телевізор і не змінює захист.",
      status_loading: "Підключення до телевізора…",
      status_none: "На телевізорі немає вікторини",
      status_draft: "Чернетку збережено",
      status_ready: "Вікторина готова",
      status_running: "Вікторина триває",
      status_settling: "Підрахунок",
      status_starting: "Запуск",
      status_start_failed: "Помилка запуску",
      status_expired: "Налаштування застаріло",
      status_unreadable: "Не вдалося прочитати дані",
      status_blocked: "Недоступно",
      detail_none: "Натисніть «Почати вікторину» на екрані блокування й відскануйте QR-код.",
      detail_draft: "Чернетка зберігається на телефоні й телевізорі до публікації.",
      detail_ready: "Натисніть «Почати вікторину» на екрані блокування телевізора.",
      detail_running: "Раунд іде на телевізорі. Сторінка лише для читання до кінця.",
      detail_expired: "QR-код або вікторина застаріли. Відкрийте налаштування на телевізорі знову.",
      detail_unreadable: "Попросіть батьків скинути дані вікторини на телевізорі.",
      detail_blocked: "Зараз немає доступної вікторини.",
      readyDetail: "Питань: {n}; потрібно правильних: {m}.",
      gate_scan_title: "Скануйте з телевізора",
      gate_scan_text: "Відкрийте «Почати вікторину» на екрані блокування й відскануйте спеціальний QR-код.",
      gate_pair_title: "Перевірка батьків",
      gate_pair_text: "Введіть 6-значний пароль батьків із телевізора. Протягом дня сторінка відкривається без пароля; після місцевої півночі його треба ввести знову. Сторінка не розблоковує телевізор.",
      gate_pair_expired: "QR-код застарів. Оновіть його на телевізорі й відскануйте знову.",
      gate_in_use_heading: "Налаштування батьків уже відкрито",
      gate_in_use_lead: "Змінювати налаштування вікторини можна лише з одного телефона одночасно.",
      gate_in_use_title: "Налаштування вже відкрито в іншого з батьків",
      gate_in_use_text: "Спробуйте пізніше або попросіть його вийти з налаштувань на своєму телефоні. Сторінка оновиться автоматично.",
      pinLabel: "Введіть пароль батьків",
      pinButton: "Відкрити налаштування",
      editor_locked: "Вікторина триває. Сторінка показує лише підсумок.",
      challengeTitle: "Ця вікторина",
      challengeHint: "Питання належать лише цій вікторині. Вхід з'явиться після публікації.",
      addQuestion: "Додати питання вручну",
      prompt: "Питання",
      options: "Варіанти (2–6, без повторів)",
      optionN: "Варіант {n}",
      addOption: "Додати варіант",
      answer: "Правильна відповідь",
      answerPlaceholder: "Виберіть правильну відповідь",
      explanation: "Рішення (необов'язково; ШІ додає його автоматично)",
      solution: "Рішення",
      points: "Бали (1–5)",
      timeLimit: "Ліміт часу (секунди)",
      addToList: "Додати до вікторини",
      emptyList: "Питань ще немає. Додайте перше.",
      remove: "Вилучити",
      settingsTitle: "Правила",
      defaultTime: "Ліміт за замовчуванням (секунди)",
      pullCount: "Питань за раунд",
      minutesPerQuestion: "Хвилин за правильну відповідь",
      questionMinutes: "{n} хв за питання",
      minCorrect: "Потрібно правильних",
      maxEarned: "Максимум хвилин",
      answerDisplay: "Час показу відповіді (секунди)",
      answerDisplayHint: "Відлік починається після прокручування пояснення до кінця; потім наступне питання відкриється автоматично.",
      shuffle: "Перемішувати варіанти",
      publish: "Опублікувати на ТБ",
      deleteSession: "Видалити вікторину",
      disableQuiz: "Вимкнути вікторину",
      enableQuiz: "Увімкнути вікторину",
      logout: "Вийти з налаштувань",
      disabled: "Вікторину вимкнено.",
      enabled: "Вікторину ввімкнено.",
      published: "Опубліковано на телевізорі. Почніть з екрана блокування.",
      deleted: "Вікторину видалено.",
      confirmDelete: "Видалити поточну вікторину?",
      confirmDisable: "Вимкнути вікторину? Непочату вікторину буде видалено.",
      qErrPrompt: "Введіть питання.",
      qErrOptions: "Введіть 2–6 різних варіантів.",
      qErrCount: "Додайте хоча б одне питання.",
      qErrMinCorrect: "Потрібно правильних більше, ніж питань.",
      qErrNumber: "Введіть коректне число.",
      qErrAnswer: "Виберіть правильну відповідь.",
      err_PIN_INVALID: "Неправильний пароль батьків.",
      err_PIN_LOCKED: "Забагато спроб. Спробуйте пізніше.",
      err_PIN_NOT_SET: "На телевізорі ще немає пароля батьків.",
      err_PARENT_IN_USE: "Налаштування вже відкрито в іншого з батьків. Попросіть його вийти з налаштувань на своєму телефоні й спробуйте ще раз.",
      err_PAIR_EXPIRED: "QR-код застарів. Оновіть його на телевізорі.",
      err_ADMIN_REQUIRED: "Спершу підтвердьте пароль батьків.",
      err_ADMIN_EXPIRED: "Сеанс завершився. Введіть пароль батьків ще раз.",
      err_ORIGIN: "Джерело запиту не дозволене.",
      err_CONTENT_TYPE: "Непідтримуваний формат запиту.",
      err_INVALID_JSON: "Запит не вдалося прочитати.",
      err_INVALID_SESSION: "Питання або правила некоректні.",
      err_SESSION_RUNNING: "Вікторина триває, зміни неможливі.",
      err_SESSION_EXISTS: "Готова вікторина вже є. Видаліть або замініть її.",
      err_SESSION_CONFLICT: "Ту саму вікторину змінено elsewhere. Відкрийте налаштування знову.",
      err_STORAGE_ERROR: "Телевізор не зберіг дані. Повторіть.",
      err_NOT_FOUND: "Інтерфейс відсутній або версія не збігається.",
      err_NETWORK: "Немає зв'язку з телевізором. Перевірте локальну мережу.",
      aiTitle: "ШІ-питання",
      aiIntro: "Модель створює питання з варіантами, а ви обираєте потрібні для цієї вікторини. Ручні питання працюють без ШІ.",
      aiUnsupported: "Ця версія Android на телевізорі не дозволяє безпечно зберігати ключ API, тому доступні лише питання, створені вручну.",
      aiUnsupportedBadge: "Недоступно",
      consentText: "Я погоджуюся надсилати параметри завдання (тема, клас, складність, кількість) сервісу моделі; родинні дані не надсилаються. Ключ API зберігається лише на телевізорі (шифрується, якщо система це підтримує), не повертається на сторінку телефона й не зберігається у хмарі продукту; під час генерації питань він надсилається лише обраному вами сервісу моделі.",
      consentHint: "Без згоди доступні лише ручні питання. Відкликання згоди видалить збережений ключ із телевізора.",
      manualNote: "Питання, додані вручну, зберігаються лише на телевізорі й не надсилаються в інтернет.",
      baseUrl: "Адреса сервісу моделі (HTTPS)",
      model: "Назва моделі",
      apiKey: "Ключ API",
      keyHintSaved: "Ключ збережено і не показується; залиште поле порожнім, щоб зберегти його.",
      keyHintNew: "Ключ зберігається лише на телевізорі; шифрування використовується, якщо система його підтримує.",
      deepseekKeyHint: "Типово використовується DeepSeek. Немає ключа? Створіть його на платформі DeepSeek (необов’язково; оплачується DeepSeek).",
      saveProvider: "Зберегти налаштування ШІ",
      testProvider: "Перевірити з'єднання",
      providerReady: "ШІ-питання налаштовано",
      providerNotReady: "ШІ-питання не налаштовано",
      providerSaved: "Налаштування ШІ збережено.",
      providerTestOk: "З'єднання працює.",
      providerRevoked: "Згоду відкликано, ключ видалено.",
      confirmRevoke: "Відкликання згоди видалить ключ API з телевізора. Продовжити?",
      generateTitle: "Створити питання за допомогою ШІ",
      generateHint: "Виберіть клас і діапазон — запит складеться автоматично. Позначте потрібні питання й надішліть їх на телевізор.",
      genGradeLabel: "Клас",
      grade1: "1 клас",
      grade2: "2 клас",
      grade3: "3 клас",
      grade4: "4 клас",
      grade5: "5 клас",
      grade6: "6 клас",
      semesterFirst: "1-й семестр",
      semesterSecond: "2-й семестр",
      gradeWithSemester: "{grade} · {semester}",
      genTextbookLabel: "Підручник",
      tbBubian: "Єдине видання",
      tbRenjiao: "Женьцзяо",
      tbRenjiaoEj: "Женьцзяо (Хубей)",
      tbSujiao: "Субань",
      tbBeishida: "Бейшіда",
      tbQingdao: "Циндао",
      tbPep: "PEP",
      tbWaiyan: "Вайянь",
      tbYilin: "Ілінь",
      tbKejiao: "Кецзяо",
      tbGeneral: "Загальний",
      tbAll: "Усі підручники",
      genSubjectLabel: "Предмет",
      subjectChinese: "Китайська мова",
      subjectMath: "Математика",
      subjectEnglish: "Англійська мова",
      subjectScience: "Природознавство",
      subjectAll: "Усі предмети",
      subjectAllPrompt: "китайська мова, математика, англійська мова й природознавство",
      genScopeLabel: "Що тренуємо",
      scopeUnit: "Основне в розділі",
      scopeUnitSub: "Ключові теми розділу",
      scopeMidterm: "До піврічної",
      scopeMidtermSub: "Часті теми першої половини",
      scopeFinal: "До підсумкової",
      scopeFinalSub: "Увесь семестр",
      scopeReview: "Загальне повторення",
      scopeReviewSub: "Закрити прогалини",
      scopeMixed: "Мікс усіх предметів",
      scopeMixedSub: "Китайська, математика, англійська, природознавство",
      genUnitLabel: "Розділ",
      genPromptLabel: "Запит на питання",
      genPromptBadgeAuto: "Створено автоматично · можна змінити",
      genPromptBadgeEdited: "Змінено",
      genPromptPlaceholder: "Виберіть умови вище — запит з'явиться тут.",
      genPromptTemplate: "Створи питання для {grade} з предмета «{subject}», тема: {scope}. Основна увага — ключові знання й типові помилки.",
      genSubjectWithBook: "{subject} ({book})",
      promptScopeUnit: "ключові знання розділу {unit}",
      promptScopeUnitTitle: "ключові знання розділу «{title}»",
      promptScopeUnitLessons: "ключові знання розділу «{title}», насамперед: {lessons}",
      promptScopeUnitAll: "ключові знання паралельних розділів з усіх предметів",
      promptScopeMidterm: "програма піврічної контрольної цього семестру",
      promptScopeFinal: "програма підсумкової контрольної цього семестру",
      promptScopeReview: "загальний огляд програми цього семестру",
      promptScopeMixed: "змішане повторення з усіх предметів",
      genAdvanced: "Додатково",
      genSummary: "{n} пит. · {o} вар. · {d}",
      generateFootnote: "Позначте питання й одразу надішліть їх на телевізор — без чернетки.",
      generateN: "Створити питань: {n}",
      genPromptRequired: "Введіть запит на питання.",
      genCount: "Кількість питань",
      genOptions: "Варіантів на питання",
      genDifficulty: "Складність",
      difficultyAny: "Обере модель",
      difficultyEasy: "Легко",
      difficultyMedium: "Середньо",
      difficultyHard: "Складно",
      genPoints: "Балів за питання",
      genTime: "Ліміт часу (секунди)",
      generateButton: "Створити питання",
      generating: "Створення… (до ~90 секунд)",
      generatedN: "Створено питань: {n}. Позначте потрібні для надсилання.",
      generationFailed: "Створення не завершено.",
      candidateHint: "Кандидати зберігаються в пам'яті телевізора 5 хвилин; позначені можна одразу надіслати на ТБ.",
      sendSelectedToTv: "Надіслати позначені питання на ТБ",
      candidatesAdded: "Надіслано питань на ТБ: {n}.",
      candidatesSkipped: "Пропущено повторів: {n}.",
      noCandidatesSelected: "Позначте хоча б одне питання.",
      err_CONSENT_REQUIRED: "Спершу прийміть повідомлення про конфіденційність.",
      err_PROVIDER_KEY_REQUIRED: "Введіть ключ API.",
      err_PROVIDER_KEY_INVALID: "Неправильний формат ключа API.",
      err_PROVIDER_CONFIG: "Неправильна адреса сервісу або назва моделі.",
      err_AI_UNSUPPORTED: "ШІ-питання не підтримуються цією версією Android на телевізорі.",
      err_PROVIDER_TIMEOUT: "Сервіс моделі не відповів вчасно.",
      err_PROVIDER_NETWORK: "Телевізор не може підключитися до сервісу моделі. Перевірте мережу або проксі.",
      err_PROVIDER_UNAUTHORIZED: "Ключ API недійсний або не має прав.",
      err_PROVIDER_BILLING: "На рахунку сервісу моделі недостатньо коштів.",
      err_PROVIDER_RATE_LIMIT: "Забагато запитів. Повторіть пізніше.",
      err_PROVIDER_UNAVAILABLE: "Сервіс моделі тимчасово недоступний.",
      err_PROVIDER_HTTP: "Сервіс моделі повернув помилку.",
      err_PROVIDER_INVALID_RESPONSE: "Відповідь моделі не вдалося розібрати. Повторіть.",
      err_PROVIDER_NOT_CONFIGURED: "Спершу налаштуйте ШІ-питання.",
      err_AI_EMPTY: "Модель не повернула коректних питань. Повторіть.",
      err_BUSY: "Уже виконується інше створення.",
      err_JOB_EXPIRED: "Завдання створення застаріло. Створіть знову.",
      err_JOB_LOST: "Завдання створення не знайдено. Створіть знову.",
      err_INVALID_JOB: "Неправильний номер завдання.",
      err_INVALID_REQUEST: "Неправильні параметри створення.",
      warningsLabel: "Примітки",
      qErrTooMany: "У вікторині не більше 10 питань.",
      settingsMenuTitle: "Налаштування",
      languageLabel: "Мова",
      close: "Закрити",
      cancel: "Скасувати",
      privacyInfo: "Конфіденційність",
      dangerZone: "Небезпечні дії",
      setupAiHint: "Налаштуйте зараз або пізніше через шестерню. Ручні питання працюють без ШІ.",
      rulesFirstHint: "Налаштовується один раз. Пізніше можна змінити через шестерню.",
      saveRules: "Зберегти правила",
      saveAndPublish: "Зберегти й опублікувати",
      rulesSaved: "Правила вікторини збережено.",
      rulesNotSet: "Не задано",
      rulesSummary: "Правильних: {m}",
      syncTitle: "Синхронізація налаштувань квізу",
      syncSynced: "Синхронізовано з додатком батьків",
      syncPendingBinding: "Завантаження налаштувань квізу з телевізора",
      syncPendingEdit: "Локальні зміни очікують надсилання",
      syncConflict: "Налаштування батьків конфліктують зі змінами на телевізорі",
      syncCloudManaged: "Керується додатком батьків",
      syncRetry: "Повторити синхронізацію",
      syncUseRemote: "Використати налаштування батьків",
      syncLastNetwork: "Мережа недоступна; повтор автоматично",
      syncLastServer: "Сервер недоступний; повтор автоматично",
      syncLastConflict: "Повторіть або використайте налаштування батьків",
      syncLastCloudManaged: "Лише читання; змінюйте в додатку Youxiaobei",
      syncLastStorage: "Помилка локального сховища; перевірте та повторіть",
      syncLastCloudUnsupported: "Ця версія телевізора ще не підтримує хмарні питання; оновіть додаток",
      rulesCloudHint: "Налаштування квізу керуються додатком батьків і доступні лише для читання.",
      err_CONFIG_CLOUD_MANAGED: "Налаштування квізу керуються додатком батьків; змініть їх у додатку Youxiaobei.",
      aiConfigured: "Налаштовано",
      aiNotConfigured: "Не налаштовано",
      aiUnavailableShort: "Недоступно",
      candidateTitle: "Створені питання",
      privacyTitle: "Перед початком",
      privacyIntro: "Підтвердьте, щоб продовжити. Це потрібно зробити лише раз.",
      privacyLocalTitle: "Зберігається на телевізорі",
      privacyLocal: "Питання, відповіді та поступ залишаються на телевізорі й видаляються після завершення вікторини.",
      privacyAiTitle: "Питання ШІ",
      privacyAi: "Тема, клас, складність та подібні параметри надсилаються обраному вами сервісу моделі. Родинні дані не надсилаються.",
      privacyKeyTitle: "Ключ API",
      privacyKey: "Ключ зберігається лише на телевізорі (шифрується, якщо система це підтримує) й більше не показується на цій сторінці. Він не зберігається у хмарі продукту; під час генерації питань він надсилається лише обраному вами сервісу моделі.",
      acceptAndContinue: "Погодитися й продовжити",
      exit: "Вийти",
      cacheTitle: "Кеш питань",
      cacheSummary: "Доступно: {n}",
      cacheIntro: "Локальні питання й хмарний банк використовують цей кеш. Рушій вікторини читає лише кеш і не залежить від джерела.",
      cacheAvailable: "Доступно",
      cacheReserved: "Зарезервовано",
      cacheConsumed: "Використано",
      cacheExpired: "Прострочено",
      cacheSourceValue: "Поточне джерело: {s}",
      sourceLocal: "Локальна генерація",
      sourceCloud: "Хмарний банк",
      cacheRebuild: "Перебудувати з цього завдання",
      cacheClear: "Очистити невикористані",
      cacheRebuilt: "Кеш перебудовано.",
      cacheCleared: "Невикористані питання очищено.",
      confirmClearCache: "Очистити всі невикористані питання? Опубліковане, але не розпочате завдання також постраждає.",
      err_NO_SESSION: "Немає завдання для перебудови.",
      err_LEGACY_SESSION: "Старе завдання не можна перебудувати; опублікуйте його знову.",
      err_CACHE_UNAVAILABLE: "Кеш питань недоступний. Спробуйте пізніше.",
      err_SESSION_RUNNING: "Вікторина триває; зараз кеш змінювати не можна.",
      err_NOT_READY: "Завдання ще не опубліковано, тому кеш не можна перебудувати.",
      err_INVALID_CONFIG: "Правила вікторини некоректні.",
      footer: "Вікторина починається й завершується лише на телевізорі.",
    },
    kk: {
      eyebrow: "Ата-ана баптауы · Жергілікті желі",
      title: "Викторина",
      securityNote: "Бұл бет тек викторинаны баптайды. Теледидарды ашпайды және қорғанысты өзгертпейді.",
      status_loading: "Теледидарға қосылу…",
      status_none: "Теледидарда викторина жоқ",
      status_draft: "Жоба сақталды",
      status_ready: "Викторина дайын",
      status_running: "Викторина жүріп жатыр",
      status_settling: "Есептелуде",
      status_starting: "Басталуда",
      status_start_failed: "Бастау қатесі",
      status_expired: "Баптау ескірді",
      status_unreadable: "Деректерді оқу мүмкін болмады",
      status_blocked: "Қолжетімсіз",
      detail_none: "Құлыптау экранындағы «Викторинаны бастау» түймесін басып, QR-кодты сканерлеңіз.",
      detail_draft: "Жоба жарияланғанға дейін телефон мен теледидарда сақталады.",
      detail_ready: "Теледидардың құлыптау экранында «Викторинаны бастау» түймесін басыңыз.",
      detail_running: "Раунд теледидарда жүріп жатыр. Бет аяқталғанша тек оқуға арналған.",
      detail_expired: "QR-код немесе викторина ескірді. Теледидарда баптауды қайта ашыңыз.",
      detail_unreadable: "Ата-анадан теледидардағы викторина деректерін тазалауды сұраңыз.",
      detail_blocked: "Қазір қолжетімді викторина жоқ.",
      readyDetail: "Сұрақтар: {n}; кемінде дұрыс: {m}.",
      gate_scan_title: "Теледидардан сканерлеңіз",
      gate_scan_text: "Құлыптау экранында «Викторинаны бастау» ашып, арнайы QR-кодты сканерлеңіз.",
      gate_pair_title: "Ата-ананы растау",
      gate_pair_text: "Теледидардағы 6 таңбалы ата-ана құпиясөзін енгізіңіз. Күн ішінде бет құпиясөзсіз ашылады; жергілікті түн ортасынан кейін оны қайта енгізу керек. Бет теледидарды ашпайды.",
      gate_pair_expired: "QR-код ескірді. Теледидарда жаңартып, қайта сканерлеңіз.",
      gate_in_use_heading: "Ата-ана баптауы ашық тұр",
      gate_in_use_lead: "Викторина баптауларын бір уақытта тек бір телефоннан өзгертуге болады.",
      gate_in_use_title: "Баптау қазір басқа ата-анада ашық",
      gate_in_use_text: "Сәл кейінірек қайталап көріңіз немесе одан өз телефонында баптаудан шығуды сұраңыз. Бет өзі жаңарады.",
      pinLabel: "Ата-ана құпиясөзін енгізіңіз",
      pinButton: "Баптауды ашу",
      editor_locked: "Викторина жүріп жатыр. Бет тек қорытындыны көрсетеді.",
      challengeTitle: "Осы викторина",
      challengeHint: "Сұрақтар тек осы викторинаға тиесілі. Кіру жарияланғаннан кейін пайда болады.",
      addQuestion: "Сұрақты қолмен қосу",
      prompt: "Сұрақ",
      options: "Нұсқалар (2–6, қайталанбайды)",
      optionN: "{n}-нұсқа",
      addOption: "Нұсқа қосу",
      answer: "Дұрыс жауап",
      answerPlaceholder: "Дұрыс жауапты таңдаңыз",
      explanation: "Шешу жолы (міндетті емес; AI оны өзі жасайды)",
      solution: "Шешу жолы",
      points: "Ұпай (1–5)",
      timeLimit: "Уақыт шегі (секунд)",
      addToList: "Викторинаға қосу",
      emptyList: "Әзірге сұрақ жоқ. Алдымен біреуін қосыңыз.",
      remove: "Жою",
      settingsTitle: "Ережелер",
      defaultTime: "Әдепкі уақыт шегі (секунд)",
      pullCount: "Раундтағы сұрақ саны",
      minutesPerQuestion: "Дұрыс жауапқа минут",
      questionMinutes: "Сұраққа {n} минут",
      minCorrect: "Қажетті дұрыс жауап",
      maxEarned: "Ең көп минут",
      answerDisplay: "Жауапты көрсету уақыты (секунд)",
      answerDisplayHint: "Түсіндірмені соңына дейін жылжытқаннан кейін кері санақ басталады; содан кейін келесі сұрақ автоматты түрде ашылады.",
      shuffle: "Нұсқаларды араластыру",
      publish: "ТВ-ға жариялау",
      deleteSession: "Викторинаны жою",
      disableQuiz: "Викторинаны өшіру",
      enableQuiz: "Викторинаны қосу",
      logout: "Ата-ана баптауынан шығу",
      disabled: "Викторина өшірілді.",
      enabled: "Викторина қосылды.",
      published: "Теледидарға жарияланды. Құлыптау экранынан бастаңыз.",
      deleted: "Викторина жойылды.",
      confirmDelete: "Ағымдағы викторинаны жою керек пе?",
      confirmDisable: "Викторинаны өшіру керек пе? Басталмаған викторина жойылады.",
      qErrPrompt: "Сұрақты енгізіңіз.",
      qErrOptions: "2–6 түрлі нұсқа енгізіңіз.",
      qErrCount: "Кемінде бір сұрақ қосыңыз.",
      qErrMinCorrect: "Қажетті дұрыс жауап сұрақ санынан көп болмайды.",
      qErrNumber: "Дұрыс сан енгізіңіз.",
      qErrAnswer: "Дұрыс жауапты таңдаңыз.",
      err_PIN_INVALID: "Ата-ана құпиясөзі дұрыс емес.",
      err_PIN_LOCKED: "Әрекет тым көп. Кейінірек қайталаңыз.",
      err_PIN_NOT_SET: "Теледидарда әзірге ата-ана құпиясөзі жоқ.",
      err_PARENT_IN_USE: "Баптау қазір басқа ата-анада ашық. Одан өз телефонында баптаудан шығуды сұрап, қайта көріңіз.",
      err_PAIR_EXPIRED: "QR-код ескірді. Теледидарда жаңартыңыз.",
      err_ADMIN_REQUIRED: "Алдымен ата-ана құпиясөзін растаңыз.",
      err_ADMIN_EXPIRED: "Сеанс аяқталды. Ата-ана құпиясөзін қайта енгізіңіз.",
      err_ORIGIN: "Сұрау көзі рұқсат етілмеген.",
      err_CONTENT_TYPE: "Сұрау пішімі қолдау көрсетілмейді.",
      err_INVALID_JSON: "Сұрауды оқу мүмкін болмады.",
      err_INVALID_SESSION: "Сұрақтар немесе ережелер дұрыс емес.",
      err_SESSION_RUNNING: "Викторина жүріп жатыр, өзгерту мүмкін емес.",
      err_SESSION_EXISTS: "Дайын викторина бар. Оны жойыңыз немесе ауыстырыңыз.",
      err_SESSION_CONFLICT: "Сол викторина elsewhere өзгерген. Баптауды қайта ашыңыз.",
      err_STORAGE_ERROR: "Теледидар сақтай алмады. Қайталаңыз.",
      err_NOT_FOUND: "Интерфейс жоқ немесе нұсқа сәйкес емес.",
      err_NETWORK: "Теледидармен байланыс жоқ. Жергілікті желіні тексеріңіз.",
      aiTitle: "AI сұрақтары",
      aiIntro: "Модель нұсқалары бар сұрақтар жасайды, ал сіз осы викторинаға қажеттерін таңдайсыз. Қолмен қосу AI-сыз да жұмыс істейді.",
      aiUnsupported: "Теледидардағы Android нұсқасы API кілтін қауіпсіз сақтауға мүмкіндік бермейді, сондықтан тек қолмен жасалған сұрақтар қолжетімді.",
      aiUnsupportedBadge: "Қолжетімсіз",
      consentText: "Сұрақ талаптарын (тақырып, сынып, қиындық, саны) модель қызметіне жіберуге келісемін; отбасылық ақпарат жіберілмейді. API кілті тек теледидарда сақталады (жүйе қолдаса, шифрланады), телефон бетіне қайтарылмайды және өнім бұлтында сақталмайды; сұрақ жасау кезінде ол тек сіз баптаған модель қызметіне жіберіледі.",
      consentHint: "Келісімсіз тек қолмен қосу қолжетімді. Келісімді қайтарып алсаңыз, теледидардағы кілт жойылады.",
      manualNote: "Қолмен қосылған сұрақтар тек теледидарда сақталады және интернетке жіберілмейді.",
      baseUrl: "Модель қызметінің мекенжайы (HTTPS)",
      model: "Модель атауы",
      apiKey: "API кілті",
      keyHintSaved: "Кілт сақталған және көрсетілмейді; сақтау үшін өрісті бос қалдырыңыз.",
      keyHintNew: "Кілт тек теледидарда сақталады; жүйе қолдаса, шифрланады.",
      deepseekKeyHint: "Әдепкіде DeepSeek қолданылады. Кілт жоқ па? Оны DeepSeek платформасында жасаңыз (міндетті емес; төлемді DeepSeek алады).",
      saveProvider: "AI баптауын сақтау",
      testProvider: "Қосылымды тексеру",
      providerReady: "AI сұрақтары бапталған",
      providerNotReady: "AI сұрақтары бапталмаған",
      providerSaved: "AI баптауы сақталды.",
      providerTestOk: "Қосылым жұмыс істейді.",
      providerRevoked: "Келісім қайтарылды, кілт жойылды.",
      confirmRevoke: "Келісімді қайтару теледидардағы API кілтін жояды. Жалғастыру керек пе?",
      generateTitle: "AI көмегімен сұрақ жасау",
      generateHint: "Сынып пен ауқымды таңдаңыз — талап автоматты түрде құралады. Қажет сұрақтарды белгілеп, теледидарға жіберіңіз.",
      genGradeLabel: "Сынып",
      grade1: "1-сынып",
      grade2: "2-сынып",
      grade3: "3-сынып",
      grade4: "4-сынып",
      grade5: "5-сынып",
      grade6: "6-сынып",
      semesterFirst: "1-жартыжылдық",
      semesterSecond: "2-жартыжылдық",
      gradeWithSemester: "{grade} · {semester}",
      genTextbookLabel: "Оқулық",
      tbBubian: "Бірыңғай басылым",
      tbRenjiao: "Жэньцзяо",
      tbRenjiaoEj: "Жэньцзяо (Хубэй)",
      tbSujiao: "Субань",
      tbBeishida: "Бэйшида",
      tbQingdao: "Циндао",
      tbPep: "PEP",
      tbWaiyan: "Вайянь",
      tbYilin: "Илинь",
      tbKejiao: "Кэцзяо",
      tbGeneral: "Жалпы",
      tbAll: "Барлық оқулықтар",
      genSubjectLabel: "Пән",
      subjectChinese: "Қытай тілі",
      subjectMath: "Математика",
      subjectEnglish: "Ағылшын тілі",
      subjectScience: "Жаратылыстану",
      subjectAll: "Барлық пәндер",
      subjectAllPrompt: "қытай тілі, математика, ағылшын тілі және жаратылыстану",
      genScopeLabel: "Нені жаттықтырамыз",
      scopeUnit: "Бөлім негізі",
      scopeUnitSub: "Бөлімнің маңызды тақырыптары",
      scopeMidterm: "Аралық бақылауға",
      scopeMidtermSub: "Бірінші жартыжылдықтың жиі тақырыптары",
      scopeFinal: "Қорытындыға",
      scopeFinalSub: "Бүкіл тоқсан",
      scopeReview: "Жалпы қайталау",
      scopeReviewSub: "Олқылықтарды толықтыру",
      scopeMixed: "Барлық пән аралас",
      scopeMixedSub: "Қытай тілі, математика, ағылшын, жаратылыстану",
      genUnitLabel: "Бөлім",
      genPromptLabel: "Сұрақ талабы",
      genPromptBadgeAuto: "Автоматты құрылды · өңдеуге болады",
      genPromptBadgeEdited: "Өңделді",
      genPromptPlaceholder: "Жоғарыдағы шарттарды таңдаңыз — талап осында пайда болады.",
      genPromptTemplate: "{grade} үшін «{subject}» пәнінен сұрақтар жаса, ауқымы: {scope}. Негізгі назар — маңызды білім мен жиі қателерге.",
      genSubjectWithBook: "{subject} ({book})",
      promptScopeUnit: "{unit}-бөлімнің негізгі білімдері",
      promptScopeUnitTitle: "«{title}» бөлімінің негізгі білімдері",
      promptScopeUnitLessons: "«{title}» бөлімінің негізгі білімдері, әсіресе: {lessons}",
      promptScopeUnitAll: "барлық пәндердің сәйкес бөлімдеріндегі негізгі білімдер",
      promptScopeMidterm: "осы тоқсанның аралық бақылау бағдарламасы",
      promptScopeFinal: "осы тоқсанның қорытынды бақылау бағдарламасы",
      promptScopeReview: "осы тоқсанның жалпы қайталау бағдарламасы",
      promptScopeMixed: "барлық пәндер бойынша аралас қайталау",
      genAdvanced: "Қосымша",
      genSummary: "{n} сұрақ · {o} нұсқа · {d}",
      generateFootnote: "Сұрақтарды белгілеп, жобасыз бірден теледидарға жіберіңіз.",
      generateN: "{n} сұрақ жасау",
      genPromptRequired: "Сұрақ талабын жазыңыз.",
      genCount: "Сұрақ саны",
      genOptions: "Әр сұрақтағы нұсқа саны",
      genDifficulty: "Қиындық",
      difficultyAny: "Модель таңдайды",
      difficultyEasy: "Оңай",
      difficultyMedium: "Орташа",
      difficultyHard: "Қиын",
      genPoints: "Әр сұраққа ұпай",
      genTime: "Уақыт шегі (секунд)",
      generateButton: "Сұрақ жасау",
      generating: "Жасалуда… (шамамен 90 секундқа дейін)",
      generatedN: "{n} сұрақ жасалды. Жіберу үшін қажеттерін белгілеңіз.",
      generationFailed: "Жасау аяқталмады.",
      candidateHint: "Кандидаттар теледидар жадында 5 минут қалады; белгіленгендерді бірден ТВ-ға жіберуге болады.",
      sendSelectedToTv: "Белгіленген сұрақтарды ТВ-ға жіберу",
      candidatesAdded: "ТВ-ға {n} сұрақ жіберілді.",
      candidatesSkipped: "{n} қайталанған сұрақ өткізілді.",
      noCandidatesSelected: "Кемінде бір сұрақты белгілеңіз.",
      err_CONSENT_REQUIRED: "Алдымен құпиялық туралы хабарламаны қабылдаңыз.",
      err_PROVIDER_KEY_REQUIRED: "API кілтін енгізіңіз.",
      err_PROVIDER_KEY_INVALID: "API кілтінің пішімі дұрыс емес.",
      err_PROVIDER_CONFIG: "Қызмет мекенжайы немесе модель атауы дұрыс емес.",
      err_AI_UNSUPPORTED: "Бұл теледидардың Android нұсқасы AI сұрақтарын қолдамайды.",
      err_PROVIDER_TIMEOUT: "Модель қызметі уақытында жауап бермеді.",
      err_PROVIDER_NETWORK: "Теледидар модель қызметіне қосыла алмайды. Желіні немесе проксиді тексеріңіз.",
      err_PROVIDER_UNAUTHORIZED: "API кілті жарамсыз немесе рұқсаты жоқ.",
      err_PROVIDER_BILLING: "Модель қызметінің шотында қаражат жеткіліксіз.",
      err_PROVIDER_RATE_LIMIT: "Сұраныс тым көп. Кейінірек қайталаңыз.",
      err_PROVIDER_UNAVAILABLE: "Модель қызметі уақытша қолжетімсіз.",
      err_PROVIDER_HTTP: "Модель қызметі қате қайтарды.",
      err_PROVIDER_INVALID_RESPONSE: "Модель жауабын талдау мүмкін болмады. Қайталаңыз.",
      err_PROVIDER_NOT_CONFIGURED: "Алдымен AI сұрақтарын баптаңыз.",
      err_AI_EMPTY: "Модель жарамды сұрақтар қайтармады. Қайталаңыз.",
      err_BUSY: "Басқа жасау жүріп жатыр.",
      err_JOB_EXPIRED: "Жасау тапсырмасы ескірді. Қайта жасаңыз.",
      err_JOB_LOST: "Жасау тапсырмасы табылмады. Қайта жасаңыз.",
      err_INVALID_JOB: "Жасау тапсырмасының нөмірі дұрыс емес.",
      err_INVALID_REQUEST: "Жасау параметрлері дұрыс емес.",
      warningsLabel: "Ескертпелер",
      qErrTooMany: "Бір викторинада ең көбі 10 сұрақ.",
      settingsMenuTitle: "Баптау",
      languageLabel: "Тіл",
      close: "Жабу",
      cancel: "Бас тарту",
      privacyInfo: "Құпиялық",
      dangerZone: "Қауіпті әрекеттер",
      setupAiHint: "Қазір немесе кейінірек шестерня арқылы баптаңыз. Қолмен қосылған сұрақтар AI-сыз жұмыс істейді.",
      rulesFirstHint: "Бір рет бапталады. Кейінірек шестерня арқылы өзгертуге болады.",
      saveRules: "Ережелерді сақтау",
      saveAndPublish: "Сақтап жариялау",
      rulesSaved: "Викторина ережелері сақталды.",
      rulesNotSet: "Орнатылмаған",
      rulesSummary: "Кемінде дұрыс: {m}",
      syncTitle: "Викторина баптауларын синхрондау",
      syncSynced: "Ата-ана қолданбасымен синхрондалды",
      syncPendingBinding: "Теледидардағы викторина баптаулары жүктелуде",
      syncPendingEdit: "Жергілікті өзгерістер жіберілуде күтіп тұр",
      syncConflict: "Ата-ана баптауы теледидардағы өзгерістермен қайшы",
      syncCloudManaged: "Ата-ана қолданбасымен басқарылады",
      syncRetry: "Синхрондауды қайталау",
      syncUseRemote: "Ата-ана баптауын қолдану",
      syncLastNetwork: "Желі қолжетімсіз; автоматты түрде қайталанады",
      syncLastServer: "Сервер қолжетімсіз; автоматты түрде қайталанады",
      syncLastConflict: "Қайталаңыз немесе ата-ана баптауын қолданыңыз",
      syncLastCloudManaged: "Тек оқу; Youxiaobei қолданбасында өзгертіңіз",
      syncLastStorage: "Теледидар жадында қате; тексеріп, қайталаңыз",
      syncLastCloudUnsupported: "Бұл теледидар нұсқасы бұлттық сұрақтарды әзірге қолдамайды; қолданбаны жаңартыңыз",
      rulesCloudHint: "Викторина баптаулары ата-ана қолданбасымен басқарылады және тек оқуға арналған.",
      err_CONFIG_CLOUD_MANAGED: "Викторина баптаулары ата-ана қолданбасымен басқарылады; оларды Youxiaobei қолданбасында өзгертіңіз.",
      aiConfigured: "Бапталған",
      aiNotConfigured: "Бапталмаған",
      aiUnavailableShort: "Қолжетімсіз",
      candidateTitle: "Жасалған сұрақтар",
      privacyTitle: "Бастамас бұрын",
      privacyIntro: "Жалғастыру үшін растаңыз. Бұл бір рет сұралады.",
      privacyLocalTitle: "Теледидарда сақталады",
      privacyLocal: "Сұрақтар, жауаптар және барыс тек теледидарда қалады және викторина аяқталғанда жойылады.",
      privacyAiTitle: "AI сұрақтары",
      privacyAi: "Тақырып, сынып, қиындық және сол сияқты талаптар сіз баптаған модель қызметіне жіберіледі. Отбасылық ақпарат жіберілмейді.",
      privacyKeyTitle: "API кілті",
      privacyKey: "Кілт тек теледидарда сақталады (жүйе қолдаса, шифрланады) және бұл бетте қайта көрсетілмейді. Ол өнім бұлтында сақталмайды; сұрақ жасау кезінде тек сіз баптаған модель қызметіне жіберіледі.",
      acceptAndContinue: "Келісіп жалғастыру",
      exit: "Шығу",
      cacheTitle: "Сұрақтар кэші",
      cacheSummary: "Қолжетімді: {n}",
      cacheIntro: "Жергілікті сұрақтар мен бұлттық банк осы кэшті пайдаланады. Викторина қозғалтқышы тек кэшті оқиды және көзге тәуелді емес.",
      cacheAvailable: "Қолжетімді",
      cacheReserved: "Резервтелген",
      cacheConsumed: "Пайдаланылған",
      cacheExpired: "Мерзімі өткен",
      cacheSourceValue: "Ағымдағы көз: {s}",
      sourceLocal: "Жергілікті генерация",
      sourceCloud: "Бұлттық банк",
      cacheRebuild: "Осы тапсырмадан қайта құру",
      cacheClear: "Пайдаланылмағандарды тазалау",
      cacheRebuilt: "Кэш қайта құрылды.",
      cacheCleared: "Пайдаланылмаған сұрақтар тазаланды.",
      confirmClearCache: "Пайдаланылмаған барлық сұрақты тазалау керек пе? Жарияланған, бірақ басталмаған тапсырма да әсер етеді.",
      err_NO_SESSION: "Қайта құруға тапсырма жоқ.",
      err_LEGACY_SESSION: "Ескі тапсырманы қайта құру мүмкін емес; оны қайта жариялаңыз.",
      err_CACHE_UNAVAILABLE: "Сұрақтар кэші қолжетімсіз. Кейінірек көріңіз.",
      err_SESSION_RUNNING: "Викторина жүріп жатыр; кэшті қазір өзгертуге болмайды.",
      err_NOT_READY: "Тапсырма әлі жарияланбаған, сондықтан кэшті қайта құруға болмайды.",
      err_INVALID_CONFIG: "Викторина ережелері дұрыс емес.",
      footer: "Викторина тек теледидарда басталады және есептеледі.",
    },
  };

  // V17 four-tab copy. Kept in one table so every language gains the same
  // keys; the older keys above remain for the modals carried over unchanged.
  const V17_TEXTS = {
    zh: {
      tabGenerate: "出题",
      tabBank: "题库",
      tabWrong: "错题",
      tabMe: "我的",
      navLabel: "主导航",
      sourceValueLocal: "本地题库",
      sourceValueCloud: "远程题库",
      generateLead: "选好范围，AI 会按目标数量分批把题目加入本地题库。",
      generateCountLabel: "本次生成",
      generateJoinBank: "AI 出题 · 加入本地题库",
      generateProgress: "正在生成 {done}/{total} 道…",
      generateCancel: "停止生成",
      generateStopped: "已停止；已加入 {n} 道",
      generateDoneLocal: "已加入本地题库 {n} 道；下一轮答题可用",
      generateDoneBound: "已加入本地题库 {n} 道；电视当前使用远程题库，解绑后这批题才会参与答题",
      generateAppended: "已加入 {n} 道",
      generateSkipped: "跳过重复 {n} 道",
      generateRejected: "忽略无效 {n} 道",
      localStockTitle: "本地题库",
      localStockValue: "共 {total} 道 · 未使用 {unused} 道",
      localStockCapacity: "容量 {capacity} 道 · 本轮中 {inRound} 道",
      inRoundNotice: "电视正在答题，新题会用于下一轮",
      insufficientNotice: "未使用题目不足，电视上会提示家长扫码出题",
      rangeTitle: "出题范围",
      rangeDone: "完成",
      editRange: "修改",
      refresh: "刷新",
      rangeSummaryLine: "{grade} · {subject} · {scope}",
      settingsRound: "每轮题数",
      settingsCountdown: "答题时限（秒）",
      settingsMinCorrect: "至少答对",
      settingsMinCorrectLocal: "仅本机生效，不同步到家长端。",
      settingsMore: "更多设置",
      challengeSettings: "答题配置",
      answerScopeTitle: "答题范围",
      answerFormTitle: "答题形式",
      scopeCustom: "自定义范围",
      scopeSubject: "学科",
      scopeGrade: "年级",
      scopeSemester: "册次",
      scopeTextbook: "教材",
      scopeTopic: "题目主题",
      scopeDifficulty: "难度",
      scopeDifficultyAny: "不限",
      minutesPerQuestion: "单题可获得分钟",
      maxEarned: "单次最多兑换分钟",
      answerDisplay: "答案展示时长（秒）",
      shuffle: "打乱选项顺序",
      difficultyHard: "困难",
      err_INVALID_SCOPE: "答题范围无效，请重新选择。",
      err_CLOUD_JUNIOR_UNSUPPORTED: "云端题库暂不支持初中答题范围。",
      cloudJuniorUnsupported: "云端题库暂不支持初中，请选择小学年级。",
      settingsSummary: "每轮 {pull} 题 · 每题 {minutes} 分钟",
      settingsActiveHint: "本轮答题进行中，修改将在下一轮生效。",
      challengeToggle: "答题挑战",
      settingsSaved: "已保存",
      meSource: "答题来源",
      meAdvanced: "高级",
      meMaintenance: "题库维护",
      meMaintenanceHint: "清理未使用、已回答或错题",
      clearUnused: "清空未使用",
      clearAnswered: "清空已回答",
      clearWrong: "清空错题",
      clearAll: "清空全部题库",
      confirmClearUnused: "清空所有未使用的题目？",
      confirmClearAnswered: "清空所有已回答的题目？",
      confirmClearWrong: "清空所有错题记录？",
      confirmClearAll: "清空整个本地题库？此操作不能撤销。",
      clearDone: "已清理。",
      bankFilterAll: "全部",
      bankFilterUnused: "未使用",
      bankFilterWrong: "回答错误",
      bankFilterAnswered: "已回答",
      bankCountsLine: "共 {total} 道 · 未使用 {unused} · 已回答 {answered}",
      bankEmptyAll: "本地题库还没有题目。到「出题」生成一批吧。",
      bankEmptyUnused: "没有未使用的题目。",
      bankEmptyWrong: "还没有回答错误的题目。",
      bankEmptyAnswered: "还没有已回答的题目。",
      wrongLead: "本地题库中答错的题目，最近答错的排在前面。",
      wrongEmpty: "还没有错题。",
      wrongCountLine: "共 {n} 道错题",
      bankStatusUnused: "未使用",
      bankStatusInRound: "本轮中",
      bankStatusCorrect: "答对",
      bankStatusWrong: "答错",
      bankStatusLegacy: "旧记录",
      bankStatusExpired: "已过期",
      yourAnswer: "你的答案：{a}",
      noAnswer: "未作答（超时）",
      wrongCountTimes: "答错 {n} 次",
      answeredOn: "作答于 {date}",
      secondsN: "{n} 秒",
      targetN: "{n} 道",
      loadMore: "加载更多",
      err_BANK_FULL: "本地题库容量不足，请先清理已答题目。",
    },
    en: {
      tabGenerate: "Create",
      tabBank: "Bank",
      tabWrong: "Mistakes",
      tabMe: "Mine",
      navLabel: "Main navigation",
      sourceValueLocal: "Local bank",
      sourceValueCloud: "Remote bank",
      generateLead: "Choose a range; AI adds questions to the local bank in batches.",
      generateCountLabel: "Generate",
      generateJoinBank: "AI questions · add to local bank",
      generateProgress: "Generating {done}/{total}…",
      generateCancel: "Stop generating",
      generateStopped: "Stopped; added {n}.",
      generateDoneLocal: "Added {n} to the local bank; available from the next round",
      generateDoneBound: "Added {n} to the local bank; the TV uses the remote bank, so these join after unbinding",
      generateAppended: "Added {n}.",
      generateSkipped: "Skipped {n} duplicates.",
      generateRejected: "Ignored {n} invalid.",
      localStockTitle: "Local bank",
      localStockValue: "{total} total · {unused} unused",
      localStockCapacity: "Capacity {capacity} · {inRound} in round",
      inRoundNotice: "A round is running on the TV; new questions go to the next round",
      insufficientNotice: "Not enough unused questions; the TV will ask a parent to create some",
      rangeTitle: "Question range",
      rangeDone: "Done",
      editRange: "Change",
      refresh: "Refresh",
      rangeSummaryLine: "{grade} · {subject} · {scope}",
      settingsRound: "Questions per round",
      settingsCountdown: "Answer time limit (seconds)",
      settingsMinCorrect: "Correct to pass",
      settingsMinCorrectLocal: "Applies to this TV only; not synced to the parent app.",
      settingsMore: "More settings",
      challengeSettings: "Answer configuration",
      answerScopeTitle: "Question range",
      answerFormTitle: "Answer format",
      scopeCustom: "Custom range",
      scopeSubject: "Subject",
      scopeGrade: "Grade",
      scopeSemester: "Semester",
      scopeTextbook: "Textbook",
      scopeTopic: "Topic",
      scopeDifficulty: "Difficulty",
      scopeDifficultyAny: "Any",
      minutesPerQuestion: "Minutes per question",
      maxEarned: "Maximum earned minutes",
      answerDisplay: "Answer display time (seconds)",
      shuffle: "Shuffle options",
      difficultyHard: "Hard",
      err_INVALID_SCOPE: "The question range is invalid. Choose it again.",
      err_CLOUD_JUNIOR_UNSUPPORTED: "The cloud question bank does not support junior-high ranges yet.",
      cloudJuniorUnsupported: "The cloud question bank does not support junior high yet. Choose a primary grade.",
      settingsSummary: "{pull} per round · {minutes} min per question",
      settingsActiveHint: "A round is running; changes apply from the next round.",
      challengeToggle: "Quiz challenge",
      settingsSaved: "Saved",
      meSource: "Quiz source",
      meAdvanced: "Advanced",
      meMaintenance: "Bank maintenance",
      meMaintenanceHint: "Clear unused, answered or wrong",
      clearUnused: "Clear unused",
      clearAnswered: "Clear answered",
      clearWrong: "Clear mistakes",
      clearAll: "Clear whole bank",
      confirmClearUnused: "Clear all unused questions?",
      confirmClearAnswered: "Clear all answered questions?",
      confirmClearWrong: "Clear all mistake records?",
      confirmClearAll: "Clear the entire local bank? This cannot be undone.",
      clearDone: "Cleared.",
      bankFilterAll: "All",
      bankFilterUnused: "Unused",
      bankFilterWrong: "Wrong",
      bankFilterAnswered: "Answered",
      bankCountsLine: "{total} total · {unused} unused · {answered} answered",
      bankEmptyAll: "The local bank is empty. Create a batch from Create.",
      bankEmptyUnused: "No unused questions.",
      bankEmptyWrong: "No wrong answers yet.",
      bankEmptyAnswered: "No answered questions yet.",
      wrongLead: "Wrong answers from the local bank; the latest are first.",
      wrongEmpty: "No mistakes yet.",
      wrongCountLine: "Mistakes: {n}",
      bankStatusUnused: "Unused",
      bankStatusInRound: "In round",
      bankStatusCorrect: "Correct",
      bankStatusWrong: "Wrong",
      bankStatusLegacy: "Old record",
      bankStatusExpired: "Expired",
      yourAnswer: "Your answer: {a}",
      noAnswer: "No answer (timed out)",
      wrongCountTimes: "Wrong {n} times",
      answeredOn: "Answered {date}",
      secondsN: "{n} s",
      targetN: "{n} q",
      loadMore: "Load more",
      err_BANK_FULL: "The local bank is full. Clear answered questions first.",
    },
    ru: {
      tabGenerate: "Создать",
      tabBank: "Банк",
      tabWrong: "Ошибки",
      tabMe: "Моё",
      navLabel: "Основная навигация",
      sourceValueLocal: "Локальный банк",
      sourceValueCloud: "Облачный банк",
      generateLead: "Выберите диапазон: ИИ добавит вопросы в локальный банк партиями.",
      generateCountLabel: "Создать сейчас",
      generateJoinBank: "ИИ: создать и добавить в банк",
      generateProgress: "Создание {done}/{total}…",
      generateCancel: "Остановить",
      generateStopped: "Остановлено; добавлено: {n}",
      generateDoneLocal: "Добавлено в локальный банк: {n}; доступно со следующего раунда",
      generateDoneBound: "Добавлено в локальный банк: {n}; ТВ использует облачный банк — вопросы включатся после отвязки",
      generateAppended: "Добавлено: {n}",
      generateSkipped: "Пропущено повторов: {n}",
      generateRejected: "Отклонено: {n}",
      localStockTitle: "Локальный банк",
      localStockValue: "Всего: {total} · не использовано: {unused}",
      localStockCapacity: "Ёмкость: {capacity} · в раунде: {inRound}",
      inRoundNotice: "На ТВ идёт раунд; новые вопросы пойдут в следующий",
      insufficientNotice: "Мало неиспользованных вопросов; ТВ попросит родителя создать их",
      rangeTitle: "Диапазон вопросов",
      rangeDone: "Готово",
      editRange: "Изменить",
      refresh: "Обновить",
      rangeSummaryLine: "{grade} · {subject} · {scope}",
      settingsRound: "Вопросов за раунд",
      settingsCountdown: "Лимит ответа (секунды)",
      settingsMinCorrect: "Минимум верных",
      settingsMinCorrectLocal: "Действует только на этом телевизоре и не синхронизируется с приложением.",
      settingsMore: "Ещё настройки",
      challengeSettings: "Настройки ответов",
      answerScopeTitle: "Диапазон вопросов",
      answerFormTitle: "Формат ответа",
      scopeCustom: "Свой диапазон",
      scopeSubject: "Предмет",
      scopeGrade: "Класс",
      scopeSemester: "Полугодие",
      scopeTextbook: "Учебник",
      scopeTopic: "Тема",
      scopeDifficulty: "Сложность",
      scopeDifficultyAny: "Любая",
      minutesPerQuestion: "Минут за вопрос",
      maxEarned: "Максимум минут",
      answerDisplay: "Время показа ответа (секунды)",
      shuffle: "Перемешивать варианты",
      difficultyHard: "Сложно",
      err_INVALID_SCOPE: "Диапазон вопросов недопустим. Выберите его заново.",
      err_CLOUD_JUNIOR_UNSUPPORTED: "Облачная база вопросов пока не поддерживает среднюю школу.",
      cloudJuniorUnsupported: "Облачная база вопросов пока не поддерживает среднюю школу. Выберите класс начальной школы.",
      settingsSummary: "{pull} за раунд · {minutes} мин на вопрос",
      settingsActiveHint: "Раунд идёт; изменения применятся со следующего раунда.",
      challengeToggle: "Викторина",
      settingsSaved: "Сохранено",
      meSource: "Источник викторины",
      meAdvanced: "Дополнительно",
      meMaintenance: "Обслуживание банка",
      meMaintenanceHint: "Очистка неиспользованных, отвеченных или неверных",
      clearUnused: "Очистить неиспользованные",
      clearAnswered: "Очистить отвеченные",
      clearWrong: "Очистить ошибки",
      clearAll: "Очистить весь банк",
      confirmClearUnused: "Очистить все неиспользованные вопросы?",
      confirmClearAnswered: "Очистить все отвеченные вопросы?",
      confirmClearWrong: "Очистить все записи об ошибках?",
      confirmClearAll: "Очистить весь локальный банк? Это нельзя отменить.",
      clearDone: "Очищено.",
      bankFilterAll: "Все",
      bankFilterUnused: "Не использованные",
      bankFilterWrong: "Неверные",
      bankFilterAnswered: "Отвеченные",
      bankCountsLine: "Всего: {total} · не использовано: {unused} · отвечено: {answered}",
      bankEmptyAll: "Локальный банк пуст. Создайте вопросы на вкладке «Создать».",
      bankEmptyUnused: "Нет неиспользованных вопросов.",
      bankEmptyWrong: "Неверных ответов пока нет.",
      bankEmptyAnswered: "Отвеченных вопросов пока нет.",
      wrongLead: "Неверные ответы из локального банка; последние — выше.",
      wrongEmpty: "Ошибок пока нет.",
      wrongCountLine: "Ошибок: {n}",
      bankStatusUnused: "Не использован",
      bankStatusInRound: "В раунде",
      bankStatusCorrect: "Верно",
      bankStatusWrong: "Неверно",
      bankStatusLegacy: "Старая запись",
      bankStatusExpired: "Истёк",
      yourAnswer: "Ваш ответ: {a}",
      noAnswer: "Без ответа (тайм-аут)",
      wrongCountTimes: "Неверно: {n}",
      answeredOn: "Ответ: {date}",
      secondsN: "{n} с",
      targetN: "{n} вопр.",
      loadMore: "Показать ещё",
      err_BANK_FULL: "В локальном банке не хватает места. Сначала очистите отвеченные вопросы.",
    },
    uk: {
      tabGenerate: "Створити",
      tabBank: "Банк",
      tabWrong: "Помилки",
      tabMe: "Моє",
      navLabel: "Основна навігація",
      sourceValueLocal: "Локальний банк",
      sourceValueCloud: "Хмарний банк",
      generateLead: "Виберіть діапазон: ШІ додасть питання до локального банку партіями.",
      generateCountLabel: "Створити зараз",
      generateJoinBank: "ШІ: створити й додати до банку",
      generateProgress: "Створення {done}/{total}…",
      generateCancel: "Зупинити",
      generateStopped: "Зупинено; додано: {n}",
      generateDoneLocal: "Додано до локального банку: {n}; доступно з наступного раунду",
      generateDoneBound: "Додано до локального банку: {n}; ТВ використовує хмарний банк — питання увімкнуться після відв'язки",
      generateAppended: "Додано: {n}",
      generateSkipped: "Пропущено повторів: {n}",
      generateRejected: "Відхилено: {n}",
      localStockTitle: "Локальний банк",
      localStockValue: "Усього: {total} · не використано: {unused}",
      localStockCapacity: "Місткість: {capacity} · у раунді: {inRound}",
      inRoundNotice: "На ТВ триває раунд; нові питання підуть у наступний",
      insufficientNotice: "Мало невикористаних питань; ТВ попросить батьків створити їх",
      rangeTitle: "Діапазон питань",
      rangeDone: "Готово",
      editRange: "Змінити",
      refresh: "Оновити",
      rangeSummaryLine: "{grade} · {subject} · {scope}",
      settingsRound: "Питань за раунд",
      settingsCountdown: "Ліміт відповіді (секунди)",
      settingsMinCorrect: "Мінімум правильних",
      settingsMinCorrectLocal: "Діє лише на цьому телевізорі й не синхронізується з батьківським застосунком.",
      settingsMore: "Більше налаштувань",
      challengeSettings: "Налаштування відповідей",
      answerScopeTitle: "Діапазон питань",
      answerFormTitle: "Формат відповіді",
      scopeCustom: "Власний діапазон",
      scopeSubject: "Предмет",
      scopeGrade: "Клас",
      scopeSemester: "Семестр",
      scopeTextbook: "Підручник",
      scopeTopic: "Тема",
      scopeDifficulty: "Складність",
      scopeDifficultyAny: "Будь-яка",
      minutesPerQuestion: "Хвилин за питання",
      maxEarned: "Максимум хвилин",
      answerDisplay: "Час показу відповіді (секунди)",
      shuffle: "Перемішувати варіанти",
      difficultyHard: "Складно",
      err_INVALID_SCOPE: "Діапазон питань недійсний. Виберіть його знову.",
      err_CLOUD_JUNIOR_UNSUPPORTED: "Хмарна база запитань поки не підтримує середню школу.",
      cloudJuniorUnsupported: "Хмарна база запитань поки не підтримує середню школу. Виберіть клас початкової школи.",
      settingsSummary: "{pull} за раунд · {minutes} хв на питання",
      settingsActiveHint: "Раунд триває; зміни застосуються з наступного раунду.",
      challengeToggle: "Вікторина",
      settingsSaved: "Збережено",
      meSource: "Джерело вікторини",
      meAdvanced: "Додатково",
      meMaintenance: "Обслуговування банку",
      meMaintenanceHint: "Очищення невикористаних, відповідей або помилок",
      clearUnused: "Очистити невикористані",
      clearAnswered: "Очистити відповіді",
      clearWrong: "Очистити помилки",
      clearAll: "Очистити весь банк",
      confirmClearUnused: "Очистити всі невикористані питання?",
      confirmClearAnswered: "Очистити всі відповіді?",
      confirmClearWrong: "Очистити всі записи про помилки?",
      confirmClearAll: "Очистити весь локальний банк? Це неможливо скасувати.",
      clearDone: "Очищено.",
      bankFilterAll: "Усі",
      bankFilterUnused: "Невикористані",
      bankFilterWrong: "Неправильні",
      bankFilterAnswered: "Відповіді",
      bankCountsLine: "Усього: {total} · не використано: {unused} · відповідей: {answered}",
      bankEmptyAll: "Локальний банк порожній. Створіть питання на вкладці «Створити».",
      bankEmptyUnused: "Немає невикористаних питань.",
      bankEmptyWrong: "Неправильних відповідей ще немає.",
      bankEmptyAnswered: "Відповідей ще немає.",
      wrongLead: "Неправильні відповіді з локального банку; останні — вище.",
      wrongEmpty: "Помилок ще немає.",
      wrongCountLine: "Помилок: {n}",
      bankStatusUnused: "Не використано",
      bankStatusInRound: "У раунді",
      bankStatusCorrect: "Правильно",
      bankStatusWrong: "Неправильно",
      bankStatusLegacy: "Старий запис",
      bankStatusExpired: "Прострочено",
      yourAnswer: "Ваша відповідь: {a}",
      noAnswer: "Без відповіді (тайм-аут)",
      wrongCountTimes: "Неправильно: {n}",
      answeredOn: "Відповідь: {date}",
      secondsN: "{n} с",
      targetN: "{n} пит.",
      loadMore: "Показати ще",
      err_BANK_FULL: "У локальному банку не вистачає місця. Спершу очистіть відповіді.",
    },
    kk: {
      tabGenerate: "Жасау",
      tabBank: "Банк",
      tabWrong: "Қателер",
      tabMe: "Менің",
      navLabel: "Негізгі навигация",
      sourceValueLocal: "Жергілікті банк",
      sourceValueCloud: "Бұлттық банк",
      generateLead: "Ауқымды таңдаңыз: AI сұрақтарды жергілікті банкке партиялап қосады.",
      generateCountLabel: "Қазір жасау",
      generateJoinBank: "AI: жасап, банкке қосу",
      generateProgress: "Жасалуда {done}/{total}…",
      generateCancel: "Тоқтату",
      generateStopped: "Тоқтатылды; қосылды: {n}",
      generateDoneLocal: "Жергілікті банкке {n} қосылды; келесі раундтан қолжетімді",
      generateDoneBound: "Жергілікті банкке {n} қосылды; ТВ бұлттық банкті пайдаланады, бұл сұрақтар ажыратқаннан кейін қосылады",
      generateAppended: "Қосылды: {n}",
      generateSkipped: "Қайталанғандар өткізілді: {n}",
      generateRejected: "Қабылданбады: {n}",
      localStockTitle: "Жергілікті банк",
      localStockValue: "Барлығы: {total} · пайдаланылмаған: {unused}",
      localStockCapacity: "Сыйымдылық: {capacity} · раундта: {inRound}",
      inRoundNotice: "ТВ-да раунд жүріп жатыр; жаңа сұрақтар келесіге барады",
      insufficientNotice: "Пайдаланылмаған сұрақтар жеткіліксіз; ТВ ата-анадан жасауды сұрайды",
      rangeTitle: "Сұрақ ауқымы",
      rangeDone: "Дайын",
      editRange: "Өзгерту",
      refresh: "Жаңарту",
      rangeSummaryLine: "{grade} · {subject} · {scope}",
      settingsRound: "Раундтағы сұрақ",
      settingsCountdown: "Жауап уақыты (секунд)",
      settingsMinCorrect: "Кемінде дұрыс",
      settingsMinCorrectLocal: "Тек осы теледидарда қолданылады, ата-ана қолданбасына синхрондалмайды.",
      settingsMore: "Қосымша баптау",
      challengeSettings: "Жауап баптауы",
      answerScopeTitle: "Сұрақ ауқымы",
      answerFormTitle: "Жауап форматы",
      scopeCustom: "Өз ауқымы",
      scopeSubject: "Пән",
      scopeGrade: "Сынып",
      scopeSemester: "Жартыжылдық",
      scopeTextbook: "Оқулық",
      scopeTopic: "Тақырып",
      scopeDifficulty: "Қиындық",
      scopeDifficultyAny: "Кез келген",
      minutesPerQuestion: "Бір сұраққа минут",
      maxEarned: "Ең көп минут",
      answerDisplay: "Жауапты көрсету уақыты (секунд)",
      shuffle: "Нұсқаларды араластыру",
      difficultyHard: "Қиын",
      err_INVALID_SCOPE: "Сұрақ ауқымы жарамсыз. Оны қайта таңдаңыз.",
      err_CLOUD_JUNIOR_UNSUPPORTED: "Бұлттық сұрақ базасы әзірге орта мектепті қолдамайды.",
      cloudJuniorUnsupported: "Бұлттық сұрақ базасы әзірге орта мектепті қолдамайды. Бастауыш сыныпты таңдаңыз.",
      settingsSummary: "Раундта {pull} сұрақ · сұраққа {minutes} мин",
      settingsActiveHint: "Раунд жүріп жатыр; өзгерістер келесі раундтан қолданылады.",
      challengeToggle: "Викторина",
      settingsSaved: "Сақталды",
      meSource: "Викторина көзі",
      meAdvanced: "Қосымша",
      meMaintenance: "Банкті күтіп-ұстау",
      meMaintenanceHint: "Пайдаланылмаған, жауапталған немесе қателерді тазалау",
      clearUnused: "Пайдаланылмағанды тазалау",
      clearAnswered: "Жауаптарды тазалау",
      clearWrong: "Қателерді тазалау",
      clearAll: "Бүкіл банкті тазалау",
      confirmClearUnused: "Барлық пайдаланылмаған сұрақты тазалау керек пе?",
      confirmClearAnswered: "Барлық жауапталған сұрақты тазалау керек пе?",
      confirmClearWrong: "Барлық қате жазбаларын тазалау керек пе?",
      confirmClearAll: "Бүкіл жергілікті банкті тазалау керек пе? Бұны қайтару мүмкін емес.",
      clearDone: "Тазаланды.",
      bankFilterAll: "Барлығы",
      bankFilterUnused: "Пайдаланылмаған",
      bankFilterWrong: "Қате жауаптар",
      bankFilterAnswered: "Жауапталған",
      bankCountsLine: "Барлығы: {total} · пайдаланылмаған: {unused} · жауапталған: {answered}",
      bankEmptyAll: "Жергілікті банк бос. «Жасау» бөлімінде сұрақ жасаңыз.",
      bankEmptyUnused: "Пайдаланылмаған сұрақ жоқ.",
      bankEmptyWrong: "Әзірге қате жауап жоқ.",
      bankEmptyAnswered: "Әзірге жауапталған сұрақ жоқ.",
      wrongLead: "Жергілікті банктегі қате жауаптар; соңғылары жоғарыда.",
      wrongEmpty: "Әзірге қате жоқ.",
      wrongCountLine: "Қателер: {n}",
      bankStatusUnused: "Пайдаланылмаған",
      bankStatusInRound: "Раундта",
      bankStatusCorrect: "Дұрыс",
      bankStatusWrong: "Қате",
      bankStatusLegacy: "Ескі жазба",
      bankStatusExpired: "Мерзімі өткен",
      yourAnswer: "Сіздің жауабыңыз: {a}",
      noAnswer: "Жауап жоқ (уақыт бітті)",
      wrongCountTimes: "Қате: {n}",
      answeredOn: "Жауап: {date}",
      secondsN: "{n} с",
      targetN: "{n} сұрақ",
      loadMore: "Көбірек көрсету",
      err_BANK_FULL: "Жергілікті банкте орын жеткіліксіз. Алдымен жауапталған сұрақтарды тазалаңыз.",
    },
  };

  // V19 editor: the home page merges the generation config and its action into
  // one card, with quiz setup as the second card. Kept separate so the older
  // four-tab table above stays untouched for cached markup and rollback.
  const V19_TEXTS = {
    zh: {
      genConfigTitle: "题目生成",
      answerConfigTitle: "答题配置",
      answerRulesTitle: "答题规则",
      generateCardTitle: "开始生成",
      scopeBound: "和题目生成一致",
      scopeCustomMode: "自定义配置",
      genRequirementLabel: "题目要求",
      genRequirementSamples: "常用要求",
      genRequirementPlaceholder: "如：第六单元易错题",
      genRequirementScope: "题库归类：{scope}",
      genRequirementScopeWithCount: "题库归类：{scope} · 共 {count} 个单元",
      genRequirementInvalid: "当前教材没有{unit}，共 {count} 个单元可选。",
      genRequirementMissing: "尚未填写题目要求",
      genRequirementNote: "出题要求：{text}",
      promptScopeManual: "「{topic}」",
      genAutoAdd: "生成后自动加入题库",
      genAutoAddHint: "开启：生成完成自动入库；关闭：在题目清单确认后加入。",
      genAutoAddShort: "自动入库",
      genManualAddShort: "手动确认入库",
      homeBankInsufficient: "电视可用 {available}/{pull} 题",
      flowAiToBank: "AI → 题库",
      flowBankToTv: "题库 → 电视",
      genScopeStatusInsufficient: "电视可用 {available}/{pull} 题；生成完成后可直接答题。",
      genScopeStatusMismatch: "当前生成范围与电视答题范围不一致；新生成的题不会进入电视答题范围。",
      genScopeStatusMismatchInsufficient: "电视可用 {available}/{pull} 题；当前生成范围不会进入电视答题范围。",
      alignToAnswerScope: "按答题范围生成",
      editGenerationScope: "调整生成范围",
      alignReplacesRequirement: "将使用电视答题范围，并替换当前题目要求。",
      scopeMismatchTitle: "生成范围与答题范围不一致",
      scopeMismatchIntro: "电视答题范围题目不足，当前生成范围又与它不一致。",
      scopeMismatchAnswerLine: "电视答题范围：{scope}",
      scopeMismatchGenerateLine: "本次生成范围：{scope}",
      scopeMismatchBody: "电视可用 {available}/{pull} 题。按当前范围生成后，新题不会进入电视答题范围。",
      scopeMismatchNote: "将替换当前生成范围和题目要求；电视答题配置不会改变。",
      keepCurrentScope: "仍然按当前范围生成",
      generateAdding: "正在加入题库…",
      saving: "保存中…",
      settingsLoading: "正在读取答题配置…",
      err_REQUIREMENT_REQUIRED: "请先填写题目要求。",
      err_AUTO_ADD_FAILED: "自动加入题库失败，请打开题目清单手动加入。",
      difficultyUnlimited: "不限",
      saveConfig: "保存",
      back: "返回",
      generateStart: "生成题目",
      generateStartCount: "生成 {n} 道题",
      generateDisclaimer: "题目由 AI 生成，可能无法完全匹配最新教材的单元顺序和内容，仅供练习参考；请以学校教材为准。",
      genTextbookHint: "教材版本按 2026 年六三学制主流版本整理；找不到请选「综合」。",
      generateStop: "停止",
      generateProviderRequired: "请先在「我的 · AI 出题」配置模型 API 密钥。",
      viewQuestionList: "查看题目清单",
      generateDoneShort: "已生成 {n} 道",
      bankMetaCount: "题库 {n} 题",
      homeAnswerMain: "{scope} · 每轮 {n} 题",
      homeAnswerSub: "每轮 {n} 题 · {seconds} 秒/题 · 每题 +{minutes} 分钟",
      roundN: "{n} 题",
      roundUnit: "题",
      settingsRoundHint: "上下滑动 · 5–50 题，每次 5 题",
      rewardMinutes: "单题奖励",
      rewardMinutesN: "+{n} 分钟",
      scopeAvailableCount: "题库中 {n} 题符合",
      scopeEmptyTitle: "题库里还没有符合的题",
      scopeEmptyText: "先按当前出题配置生成一些题目，或调整条件。",
      scopeLoading: "正在读取题库…",
      scopeIncompleteShort: "尚未选择完整",
      err_SCOPE_INCOMPLETE: "请先完成答题范围选择。",
      err_SCOPE_EMPTY: "当前范围没有题库题目，请调整条件或先生成题目。",
      goGenerate: "去生成题目",
      goGenerationConfig: "调整题目生成",
      questionListTitle: "题目清单",
      questionListMeta: "{total} 道 · 已选 {selected}",
      selectAll: "全选",
      unselectAll: "取消全选",
      inBank: "已在题库",
      addToBank: "加入题库",
      selectedCount: "已选 {n} 道",
      generateNoQuestions: "还没有生成题目。",
      generateAdded: "已加入题库 {n} 道。",
      generateNothingSelected: "至少选一道题。",
      questionAnswer: "答案",
      unitN: "第{n}单元",
      generateStoppedReview: "已停止；已生成的题目保留在清单里，确认后加入题库。",
      generateDoNotRefresh: "生成中，请不要刷新页面。",
    },
    en: {
      genConfigTitle: "Question generation",
      answerConfigTitle: "Quiz setup",
      answerRulesTitle: "Rules",
      generateCardTitle: "Start generating",
      scopeBound: "Same as question generation",
      scopeCustomMode: "Custom",
      genRequirementLabel: "Question topic",
      genRequirementSamples: "Common topics",
      genRequirementPlaceholder: "e.g. Unit 6 common mistakes",
      genRequirementScope: "Bank category: {scope}",
      genRequirementScopeWithCount: "Bank category: {scope} · {count} units",
      genRequirementInvalid: "This book has no {unit}; choose from its {count} units.",
      genRequirementMissing: "Topic not set",
      genRequirementNote: "Extra requirement: {text}",
      promptScopeManual: "“{topic}”",
      genAutoAdd: "Add to bank automatically",
      genAutoAddHint: "On: finished questions are added automatically. Off: review the list first.",
      genAutoAddShort: "Auto-add",
      genManualAddShort: "Review first",
      homeBankInsufficient: "{available}/{pull} questions ready on TV",
      flowAiToBank: "AI → Bank",
      flowBankToTv: "Bank → TV",
      genScopeStatusInsufficient: "TV has {available}/{pull} questions ready. Questions from this run can be used right away.",
      genScopeStatusMismatch: "The generation range differs from the TV quiz range. New questions will not appear in the TV quiz.",
      genScopeStatusMismatchInsufficient: "TV has {available}/{pull} questions ready. Questions from the current generation range will not appear in the TV quiz.",
      alignToAnswerScope: "Generate for TV range",
      editGenerationScope: "Edit generation range",
      alignReplacesRequirement: "Uses the TV quiz range and replaces the current question topic.",
      scopeMismatchTitle: "Generation range does not match the TV quiz range",
      scopeMismatchIntro: "The TV quiz range is short of questions, and the current generation range does not match it.",
      scopeMismatchAnswerLine: "TV quiz range: {scope}",
      scopeMismatchGenerateLine: "Current generation range: {scope}",
      scopeMismatchBody: "TV has {available}/{pull} questions ready. New questions from the current range will not appear in the TV quiz.",
      scopeMismatchNote: "This replaces the current generation range and question topic. The TV quiz setup will not change.",
      keepCurrentScope: "Generate with current range",
      generateAdding: "Adding to the bank…",
      saving: "Saving…",
      settingsLoading: "Loading quiz setup…",
      err_REQUIREMENT_REQUIRED: "Enter a question topic first.",
      err_AUTO_ADD_FAILED: "Could not add automatically. Open the question list and add them manually.",
      difficultyUnlimited: "Any",
      saveConfig: "Save",
      back: "Back",
      generateStart: "Generate questions",
      generateStartCount: "Generate {n} questions",
      generateDisclaimer: "Questions are generated by AI and may not fully match the unit order or content of the latest textbooks. For practice reference only; follow the school textbook.",
      genTextbookHint: "Textbook editions follow mainstream 2026 6–3 school-system editions. Choose “General” if yours is not listed.",
      generateStop: "Stop",
      generateProviderRequired: "Set the model API key in Me · AI questions first.",
      viewQuestionList: "Review questions",
      generateDoneShort: "{n} generated",
      bankMetaCount: "Bank: {n} questions",
      homeAnswerMain: "{scope} · {n} per round",
      homeAnswerSub: "{n} per round · {seconds}s each · +{minutes} min per question",
      roundN: "{n} questions",
      roundUnit: "questions",
      settingsRoundHint: "Swipe up or down · 5–50, step 5",
      rewardMinutes: "Reward per question",
      rewardMinutesN: "+{n} min",
      scopeAvailableCount: "{n} bank questions match",
      scopeEmptyTitle: "No matching questions yet",
      scopeEmptyText: "Generate questions with the current setup first, or adjust the filters.",
      scopeLoading: "Loading bank options…",
      scopeIncompleteShort: "Not fully selected",
      err_SCOPE_INCOMPLETE: "Finish selecting the question range first.",
      err_SCOPE_EMPTY: "No bank questions match this range. Adjust the filters or generate questions first.",
      goGenerate: "Generate questions",
      goGenerationConfig: "Edit question generation",
      questionListTitle: "Question list",
      questionListMeta: "{total} questions · {selected} selected",
      selectAll: "Select all",
      unselectAll: "Clear",
      inBank: "In bank",
      addToBank: "Add to bank",
      selectedCount: "{n} selected",
      generateNoQuestions: "No generated questions yet.",
      generateAdded: "Added {n} questions to the bank.",
      generateNothingSelected: "Select at least one question.",
      questionAnswer: "Answer",
      unitN: "Unit {n}",
      generateStoppedReview: "Stopped. Generated questions are kept for review; add them when ready.",
      generateDoNotRefresh: "Generating. Do not refresh this page.",
    },
    ru: {
      genConfigTitle: "Генерация вопросов",
      answerConfigTitle: "Настройка викторины",
      answerRulesTitle: "Правила",
      generateCardTitle: "Начать генерацию",
      scopeBound: "Совпадает с генерацией вопросов",
      scopeCustomMode: "Свои условия",
      genRequirementLabel: "Тема вопросов",
      genRequirementSamples: "Частые темы",
      genRequirementPlaceholder: "например, типичные ошибки блока 6",
      genRequirementScope: "Категория в банке: {scope}",
      genRequirementScopeWithCount: "Категория в банке: {scope} · разделов: {count}",
      genRequirementInvalid: "В этом учебнике нет «{unit}»; доступно разделов: {count}.",
      genRequirementMissing: "Тема не указана",
      genRequirementNote: "Доп. требование: {text}",
      promptScopeManual: "«{topic}»",
      genAutoAdd: "Добавлять в банк автоматически",
      genAutoAddHint: "Вкл.: готовые вопросы добавляются сразу. Выкл.: сначала проверьте список.",
      genAutoAddShort: "Автодобавление",
      genManualAddShort: "Сначала проверка",
      homeBankInsufficient: "На ТВ доступно {available} из {pull}",
      flowAiToBank: "ИИ → Банк",
      flowBankToTv: "Банк → ТВ",
      genScopeStatusInsufficient: "На ТВ доступно {available} из {pull}. Вопросы этой генерации сразу можно будет использовать.",
      genScopeStatusMismatch: "Диапазон генерации не совпадает с диапазоном викторины на ТВ. Новые вопросы не попадут в викторину.",
      genScopeStatusMismatchInsufficient: "На ТВ доступно {available} из {pull}. Вопросы текущего диапазона генерации не попадут в викторину.",
      alignToAnswerScope: "Создать для диапазона ТВ",
      editGenerationScope: "Изменить диапазон генерации",
      alignReplacesRequirement: "Будет использован диапазон викторины на ТВ; текущая тема вопросов заменится.",
      scopeMismatchTitle: "Диапазон генерации не совпадает с диапазоном викторины",
      scopeMismatchIntro: "В диапазоне викторины на ТВ не хватает вопросов, а текущий диапазон генерации с ним не совпадает.",
      scopeMismatchAnswerLine: "Диапазон викторины на ТВ: {scope}",
      scopeMismatchGenerateLine: "Текущий диапазон генерации: {scope}",
      scopeMismatchBody: "На ТВ доступно {available} из {pull}. Новые вопросы текущего диапазона не попадут в викторину.",
      scopeMismatchNote: "Текущий диапазон генерации и тема вопросов будут заменены. Настройка викторины на ТВ не изменится.",
      keepCurrentScope: "Создать в текущем диапазоне",
      generateAdding: "Добавляем в банк…",
      saving: "Сохранение…",
      settingsLoading: "Загружаем настройку викторины…",
      err_REQUIREMENT_REQUIRED: "Сначала укажите тему вопросов.",
      err_AUTO_ADD_FAILED: "Не удалось добавить автоматически. Откройте список и добавьте вручную.",
      difficultyUnlimited: "Любая",
      saveConfig: "Сохранить",
      back: "Назад",
      generateStart: "Сгенерировать вопросы",
      generateStartCount: "Создать {n} вопросов",
      generateDisclaimer: "Вопросы создаёт ИИ; они могут не полностью совпадать с порядком разделов и содержанием новейших учебников. Только для тренировки; ориентируйтесь на школьный учебник.",
      genTextbookHint: "Версии учебников соответствуют основным изданиям 2026 года для системы 6–3. Если вашей нет, выберите «Общий».",
      generateStop: "Стоп",
      generateProviderRequired: "Сначала укажите ключ API модели в разделе «Я · Вопросы ИИ».",
      viewQuestionList: "Проверить вопросы",
      generateDoneShort: "Сгенерировано: {n}",
      bankMetaCount: "Банк: {n} вопр.",
      homeAnswerMain: "{scope} · {n} за раунд",
      homeAnswerSub: "{n} за раунд · {seconds} с на вопрос · +{minutes} мин за вопрос",
      roundN: "{n} вопр.",
      roundUnit: "вопр.",
      settingsRoundHint: "Смахните · 5–50, шаг 5",
      rewardMinutes: "Награда за вопрос",
      rewardMinutesN: "+{n} мин",
      scopeAvailableCount: "В банке подходят {n} вопр.",
      scopeEmptyTitle: "Подходящих вопросов пока нет",
      scopeEmptyText: "Сначала сгенерируйте вопросы с текущими настройками или измените фильтры.",
      scopeLoading: "Загрузка вариантов из банка…",
      scopeIncompleteShort: "Выбрано не полностью",
      err_SCOPE_INCOMPLETE: "Сначала завершите выбор диапазона вопросов.",
      err_SCOPE_EMPTY: "В банке нет вопросов для этого диапазона. Измените фильтры или сначала сгенерируйте вопросы.",
      goGenerate: "Сгенерировать вопросы",
      goGenerationConfig: "Изменить генерацию вопросов",
      questionListTitle: "Список вопросов",
      questionListMeta: "Всего: {total} · выбрано: {selected}",
      selectAll: "Выбрать все",
      unselectAll: "Снять выбор",
      inBank: "Уже в банке",
      addToBank: "Добавить в банк",
      selectedCount: "Выбрано: {n}",
      generateNoQuestions: "Вопросы ещё не сгенерированы.",
      generateAdded: "В банк добавлено вопросов: {n}.",
      generateNothingSelected: "Выберите хотя бы один вопрос.",
      questionAnswer: "Ответ",
      unitN: "Раздел {n}",
      generateStoppedReview: "Остановлено. Сгенерированные вопросы сохранены для проверки.",
      generateDoNotRefresh: "Идёт генерация. Не обновляйте страницу.",
    },
    uk: {
      genConfigTitle: "Генерація питань",
      answerConfigTitle: "Налаштування вікторини",
      answerRulesTitle: "Правила",
      generateCardTitle: "Почати генерацію",
      scopeBound: "Збігається з генерацією питань",
      scopeCustomMode: "Власні умови",
      genRequirementLabel: "Тема питань",
      genRequirementSamples: "Часті теми",
      genRequirementPlaceholder: "наприклад, типові помилки розділу 6",
      genRequirementScope: "Категорія в банку: {scope}",
      genRequirementScopeWithCount: "Категорія в банку: {scope} · розділів: {count}",
      genRequirementInvalid: "У цьому підручнику немає «{unit}»; доступно розділів: {count}.",
      genRequirementMissing: "Тему не вказано",
      genRequirementNote: "Дод. вимога: {text}",
      promptScopeManual: "«{topic}»",
      genAutoAdd: "Додавати до банку автоматично",
      genAutoAddHint: "Увімк.: готові питання додаються одразу. Вимк.: спершу перегляньте список.",
      genAutoAddShort: "Автододавання",
      genManualAddShort: "Спершу перевірка",
      homeBankInsufficient: "На ТБ доступно {available} з {pull}",
      flowAiToBank: "ШІ → Банк",
      flowBankToTv: "Банк → ТБ",
      genScopeStatusInsufficient: "На ТБ доступно {available} з {pull}. Питання цієї генерації одразу можна буде використати.",
      genScopeStatusMismatch: "Діапазон генерації не збігається з діапазоном вікторини на ТБ. Нові питання не потраплять у вікторину.",
      genScopeStatusMismatchInsufficient: "На ТБ доступно {available} з {pull}. Питання поточного діапазону генерації не потраплять у вікторину.",
      alignToAnswerScope: "Згенерувати для діапазону ТБ",
      editGenerationScope: "Змінити діапазон генерації",
      alignReplacesRequirement: "Буде використано діапазон вікторини на ТБ; поточну тему питань буде замінено.",
      scopeMismatchTitle: "Діапазон генерації не збігається з діапазоном вікторини",
      scopeMismatchIntro: "У діапазоні вікторини на ТБ бракує питань, а поточний діапазон генерації з ним не збігається.",
      scopeMismatchAnswerLine: "Діапазон вікторини на ТБ: {scope}",
      scopeMismatchGenerateLine: "Поточний діапазон генерації: {scope}",
      scopeMismatchBody: "На ТБ доступно {available} з {pull}. Нові питання поточного діапазону не потраплять у вікторину.",
      scopeMismatchNote: "Поточний діапазон генерації й тему питань буде замінено. Налаштування вікторини на ТБ не зміниться.",
      keepCurrentScope: "Згенерувати в поточному діапазоні",
      generateAdding: "Додаємо до банку…",
      saving: "Збереження…",
      settingsLoading: "Завантажуємо налаштування вікторини…",
      err_REQUIREMENT_REQUIRED: "Спочатку вкажіть тему питань.",
      err_AUTO_ADD_FAILED: "Не вдалося додати автоматично. Відкрийте список і додайте вручну.",
      difficultyUnlimited: "Будь-яка",
      saveConfig: "Зберегти",
      back: "Назад",
      generateStart: "Згенерувати питання",
      generateStartCount: "Згенерувати {n} питань",
      generateDisclaimer: "Питання створює ШІ; вони можуть не повністю збігатися з порядком розділів і змістом найновіших підручників. Лише для тренування; орієнтуйтеся на шкільний підручник.",
      genTextbookHint: "Версії підручників відповідають основним виданням 2026 року для системи 6–3. Якщо вашої немає, виберіть «Загальний».",
      generateStop: "Стоп",
      generateProviderRequired: "Спочатку вкажіть ключ API моделі в розділі «Я · Питання ШІ».",
      viewQuestionList: "Переглянути питання",
      generateDoneShort: "Згенеровано: {n}",
      bankMetaCount: "Банк: {n} питань",
      homeAnswerMain: "{scope} · {n} за раунд",
      homeAnswerSub: "{n} за раунд · {seconds} с на питання · +{minutes} хв за питання",
      roundN: "{n} питань",
      roundUnit: "питань",
      settingsRoundHint: "Проведіть · 5–50, крок 5",
      rewardMinutes: "Винагорода за питання",
      rewardMinutesN: "+{n} хв",
      scopeAvailableCount: "У банку відповідають {n} питань",
      scopeEmptyTitle: "Відповідних питань поки немає",
      scopeEmptyText: "Спочатку згенеруйте питання з поточними налаштуваннями або змініть фільтри.",
      scopeLoading: "Завантаження варіантів із банку…",
      scopeIncompleteShort: "Вибрано не повністю",
      err_SCOPE_INCOMPLETE: "Спочатку завершіть вибір діапазону питань.",
      err_SCOPE_EMPTY: "У банку немає питань для цього діапазону. Змініть фільтри або спочатку згенеруйте питання.",
      goGenerate: "Згенерувати питання",
      goGenerationConfig: "Змінити генерацію питань",
      questionListTitle: "Список питань",
      questionListMeta: "Усього: {total} · вибрано: {selected}",
      selectAll: "Вибрати всі",
      unselectAll: "Зняти вибір",
      inBank: "Уже в банку",
      addToBank: "Додати до банку",
      selectedCount: "Вибрано: {n}",
      generateNoQuestions: "Питання ще не згенеровано.",
      generateAdded: "Додано до банку питань: {n}.",
      generateNothingSelected: "Виберіть хоча б одне питання.",
      questionAnswer: "Відповідь",
      unitN: "Розділ {n}",
      generateStoppedReview: "Зупинено. Згенеровані питання збережено для перегляду.",
      generateDoNotRefresh: "Триває генерація. Не оновлюйте сторінку.",
    },
    kk: {
      genConfigTitle: "Сұрақ жасау",
      answerConfigTitle: "Викторина баптауы",
      answerRulesTitle: "Ережелер",
      generateCardTitle: "Жасауды бастау",
      scopeBound: "Сұрақ жасаумен бірдей",
      scopeCustomMode: "Өз шарттары",
      genRequirementLabel: "Сұрақ тақырыбы",
      genRequirementSamples: "Жиі тақырыптар",
      genRequirementPlaceholder: "мысалы, 6-бөлімнің жиі қателері",
      genRequirementScope: "Банктегі санат: {scope}",
      genRequirementScopeWithCount: "Банктегі санат: {scope} · {count} бөлім",
      genRequirementInvalid: "Бұл оқулықта «{unit}» жоқ; {count} бөлім қолжетімді.",
      genRequirementMissing: "Тақырып көрсетілмеген",
      genRequirementNote: "Қосымша талап: {text}",
      promptScopeManual: "«{topic}»",
      genAutoAdd: "Банкке автоматты қосу",
      genAutoAddHint: "Қосулы: дайын сұрақтар бірден қосылады. Өшірулі: алдымен тізімді тексеріңіз.",
      genAutoAddShort: "Автоқосу",
      genManualAddShort: "Алдымен тексеру",
      homeBankInsufficient: "ТВ-де {available}/{pull} сұрақ қолжетімді",
      flowAiToBank: "AI → Банк",
      flowBankToTv: "Банк → ТВ",
      genScopeStatusInsufficient: "ТВ-де {available}/{pull} сұрақ бар. Осы генерацияның сұрақтарын бірден қолдануға болады.",
      genScopeStatusMismatch: "Жасау ауқымы ТВ викторина ауқымына сәйкес емес. Жаңа сұрақтар ТВ викторинасына қосылмайды.",
      genScopeStatusMismatchInsufficient: "ТВ-де {available}/{pull} сұрақ бар. Ағымдағы жасау ауқымының сұрақтары ТВ викторинасына қосылмайды.",
      alignToAnswerScope: "ТВ ауқымы бойынша жасау",
      editGenerationScope: "Жасау ауқымын өзгерту",
      alignReplacesRequirement: "ТВ викторина ауқымы қолданылып, ағымдағы сұрақ тақырыбы ауыстырылады.",
      scopeMismatchTitle: "Жасау ауқымы ТВ викторина ауқымына сәйкес емес",
      scopeMismatchIntro: "ТВ викторина ауқымында сұрақ жеткіліксіз, ал ағымдағы жасау ауқымы оған сәйкес емес.",
      scopeMismatchAnswerLine: "ТВ викторина ауқымы: {scope}",
      scopeMismatchGenerateLine: "Ағымдағы жасау ауқымы: {scope}",
      scopeMismatchBody: "ТВ-де {available}/{pull} сұрақ бар. Ағымдағы ауқым бойынша жасалған жаңа сұрақтар ТВ викторинасына қосылмайды.",
      scopeMismatchNote: "Ағымдағы жасау ауқымы мен сұрақ тақырыбы ауыстырылады. ТВ викторина баптауы өзгермейді.",
      keepCurrentScope: "Ағымдағы ауқыммен жасау",
      generateAdding: "Банкке қосылуда…",
      saving: "Сақталуда…",
      settingsLoading: "Викторина баптауы жүктелуде…",
      err_REQUIREMENT_REQUIRED: "Алдымен сұрақ тақырыбын енгізіңіз.",
      err_AUTO_ADD_FAILED: "Автоматты қосу сәтсіз. Тізімді ашып, қолмен қосыңыз.",
      difficultyUnlimited: "Кез келген",
      saveConfig: "Сақтау",
      back: "Артқа",
      generateStart: "Сұрақ жасау",
      generateStartCount: "{n} сұрақ жасау",
      generateDisclaimer: "Сұрақтарды AI жасайды; олар ең жаңа оқулықтардың бөлім ретімен және мазмұнымен толық сәйкес келмеуі мүмкін. Тек жаттығуға арналған; мектеп оқулығын басшылыққа алыңыз.",
      genTextbookHint: "Оқулық нұсқалары 2026 жылғы 6–3 жүйесінің негізгі басылымдарына сәйкес. Өзіңіздікі тізімде жоқ болса, «Жалпы» нұсқасын таңдаңыз.",
      generateStop: "Тоқтату",
      generateProviderRequired: "Алдымен «Мен · AI сұрақтары» бөлімінде модель API кілтін баптаңыз.",
      viewQuestionList: "Сұрақтарды қарау",
      generateDoneShort: "{n} сұрақ жасалды",
      bankMetaCount: "Банк: {n} сұрақ",
      homeAnswerMain: "{scope} · раундта {n} сұрақ",
      homeAnswerSub: "раундта {n} · сұраққа {seconds} с · сұраққа +{minutes} мин",
      roundN: "{n} сұрақ",
      roundUnit: "сұрақ",
      settingsRoundHint: "Сырғытыңыз · 5–50, қадам 5",
      rewardMinutes: "Сұрақ сыйақысы",
      rewardMinutesN: "+{n} мин",
      scopeAvailableCount: "Банкте {n} сұрақ сәйкес",
      scopeEmptyTitle: "Сәйкес сұрақтар әзірге жоқ",
      scopeEmptyText: "Алдымен ағымдағы баптаумен сұрақ жасаңыз немесе сүзгілерді өзгертіңіз.",
      scopeLoading: "Банк нұсқалары жүктелуде…",
      scopeIncompleteShort: "Толық таңдалмаған",
      err_SCOPE_INCOMPLETE: "Алдымен сұрақ ауқымын таңдауды аяқтаңыз.",
      err_SCOPE_EMPTY: "Бұл ауқымға сәйкес банкте сұрақ жоқ. Сүзгілерді өзгертіңіз немесе алдымен сұрақ жасаңыз.",
      goGenerate: "Сұрақ жасау",
      goGenerationConfig: "Сұрақ жасауды өзгерту",
      questionListTitle: "Сұрақтар тізімі",
      questionListMeta: "Барлығы: {total} · таңдалды: {selected}",
      selectAll: "Барлығын таңдау",
      unselectAll: "Таңдауды алу",
      inBank: "Банкте бар",
      addToBank: "Банкке қосу",
      selectedCount: "Таңдалды: {n}",
      generateNoQuestions: "Сұрақтар әзірге жасалмаған.",
      generateAdded: "Банкке {n} сұрақ қосылды.",
      generateNothingSelected: "Кемінде бір сұрақ таңдаңыз.",
      questionAnswer: "Жауап",
      unitN: "{n}-бөлім",
      generateStoppedReview: "Тоқтатылды. Жасалған сұрақтар тексеру үшін сақталды.",
      generateDoNotRefresh: "Генерация жүріп жатыр. Бетті жаңартпаңыз.",
    },
  };

  // V20: the answer range is an ordered list of parent-designed levels. The TV
  // shows that list as a wheel; these texts cover the list, the editor and the
  // home summary.
  const V20_TEXTS = {
    zh: {
      levelCountLabel: "{n} 个关卡",
      levelMore: "还有 {n} 关",
      levelNoneLabel: "未设置关卡",
      levelListTitle: "关卡",
      levelListLead: "电视上按这里的顺序显示关卡，孩子用遥控上下选择。",
      levelListCount: "{n}/{max}",
      levelEmptyTitle: "还没有设置关卡",
      levelEmptyText: "添加一个答题范围，孩子就能在电视上像选关一样答题。",
      levelAdd: "添加关卡",
      levelMaxHint: "最多设置 {n} 个关卡。",
      levelUndo: "撤销",
      levelDeleted: "已删除「{name}」",
      levelSaved: "关卡已保存",
      levelOrderSaved: "关卡顺序已保存",
      levelNameLabel: "关卡名称（可选）",
      levelNamePlaceholder: "例如：语文期中冲刺",
      levelNameHint: "最多 10 个字；不填时电视显示范围摘要。",
      levelNameTooLong: "关卡名称最多 10 个字。",
      levelAddTitle: "添加关卡",
      levelEditTitle: "编辑关卡",
      levelAvailable: "可用 {n} 题",
      levelShort: "每轮需要 {pull} 题，当前只有 {available} 题",
      levelDuplicate: "这个范围已经存在于其他关卡，请换一个范围。",
      levelSave: "保存关卡",
      levelFallbackName: "关卡 {n}",
      levelDeleteAria: "删除「{name}」",
      levelMoveUpAria: "上移「{name}」",
      levelMoveDownAria: "下移「{name}」",
      levelRulesApplyAll: "以下答题规则对所有关卡生效。",
      homeLevelsInsufficient: "有 {n} 个关卡题量不足（每轮 {pull} 题）",
      genScopeStatusLevelsInsufficient: "有 {n} 个关卡题量不足（每轮 {pull} 题）；生成完成后可直接答题。",
      genScopeStatusLevelMismatch: "关卡「{level}」题量不足，且当前生成范围与它不一致。",
    },
    en: {
      levelCountLabel: "{n} levels",
      levelMore: "and {n} more",
      levelNoneLabel: "No levels",
      levelListTitle: "Levels",
      levelListLead: "The TV lists the levels in this order so your child can pick one with the remote.",
      levelListCount: "{n}/{max}",
      levelEmptyTitle: "No levels yet",
      levelEmptyText: "Add an answer range so your child can pick it on the TV like a game level.",
      levelAdd: "Add level",
      levelMaxHint: "Up to {n} levels.",
      levelUndo: "Undo",
      levelDeleted: "Deleted “{name}”",
      levelSaved: "Level saved",
      levelOrderSaved: "Level order saved",
      levelNameLabel: "Level name (optional)",
      levelNamePlaceholder: "e.g. Chinese midterm sprint",
      levelNameHint: "Up to 10 characters. Leave empty to show the range summary on TV.",
      levelNameTooLong: "Level name must be 10 characters or fewer.",
      levelAddTitle: "Add level",
      levelEditTitle: "Edit level",
      levelAvailable: "{n} questions ready",
      levelShort: "Needs {pull} per round; only {available} ready",
      levelDuplicate: "This range already exists in another level. Choose a different range.",
      levelSave: "Save level",
      levelFallbackName: "Level {n}",
      levelDeleteAria: "Delete “{name}”",
      levelMoveUpAria: "Move “{name}” up",
      levelMoveDownAria: "Move “{name}” down",
      levelRulesApplyAll: "These rules apply to every level.",
      homeLevelsInsufficient: "{n} level(s) need more questions ({pull} per round)",
      genScopeStatusLevelsInsufficient: "{n} level(s) need more questions ({pull} per round). Generated questions can be used right away.",
      genScopeStatusLevelMismatch: "Level “{level}” needs more questions and does not match the current generation range.",
    },
    ru: {
      levelCountLabel: "Уровней: {n}",
      levelMore: "и ещё {n}",
      levelNoneLabel: "Уровни не заданы",
      levelListTitle: "Уровни",
      levelListLead: "На ТВ уровни показываются в этом порядке; ребёнок выбирает их пультом.",
      levelListCount: "{n}/{max}",
      levelEmptyTitle: "Уровней пока нет",
      levelEmptyText: "Добавьте диапазон вопросов, чтобы ребёнок мог выбрать его на ТВ как уровень игры.",
      levelAdd: "Добавить уровень",
      levelMaxHint: "Можно добавить не больше {n} уровней.",
      levelUndo: "Отменить",
      levelDeleted: "Удалён уровень «{name}»",
      levelSaved: "Уровень сохранён",
      levelOrderSaved: "Порядок уровней сохранён",
      levelNameLabel: "Название уровня (необязательно)",
      levelNamePlaceholder: "например: Подготовка к четверти",
      levelNameHint: "До 10 символов. Если пусто, на ТВ показывается описание диапазона.",
      levelNameTooLong: "Название уровня — не больше 10 символов.",
      levelAddTitle: "Добавить уровень",
      levelEditTitle: "Изменить уровень",
      levelAvailable: "Готово вопросов: {n}",
      levelShort: "Нужно {pull} за раунд, готово только {available}",
      levelDuplicate: "Такой диапазон уже есть в другом уровне. Выберите другой диапазон.",
      levelSave: "Сохранить уровень",
      levelFallbackName: "Уровень {n}",
      levelDeleteAria: "Удалить «{name}»",
      levelMoveUpAria: "Переместить «{name}» выше",
      levelMoveDownAria: "Переместить «{name}» ниже",
      levelRulesApplyAll: "Эти правила действуют для всех уровней.",
      homeLevelsInsufficient: "Уровней без нужного запаса вопросов: {n} (нужно {pull} за раунд)",
      genScopeStatusLevelsInsufficient: "Уровней без нужного запаса вопросов: {n} (нужно {pull} за раунд). Сгенерированные вопросы можно использовать сразу.",
      genScopeStatusLevelMismatch: "Для уровня «{level}» не хватает вопросов, и текущий диапазон генерации с ним не совпадает.",
    },
    uk: {
      levelCountLabel: "Рівнів: {n}",
      levelMore: "і ще {n}",
      levelNoneLabel: "Рівні не налаштовані",
      levelListTitle: "Рівні",
      levelListLead: "На ТБ рівні показуються в цьому порядку; дитина вибирає їх пультом.",
      levelListCount: "{n}/{max}",
      levelEmptyTitle: "Рівнів ще немає",
      levelEmptyText: "Додайте діапазон питань, щоб дитина могла вибрати його на ТБ як рівень гри.",
      levelAdd: "Додати рівень",
      levelMaxHint: "Можна додати щонайбільше {n} рівнів.",
      levelUndo: "Скасувати",
      levelDeleted: "Видалено рівень «{name}»",
      levelSaved: "Рівень збережено",
      levelOrderSaved: "Порядок рівнів збережено",
      levelNameLabel: "Назва рівня (необов’язково)",
      levelNamePlaceholder: "наприклад: Підготовка до семестру",
      levelNameHint: "До 10 символів. Якщо порожньо, на ТБ показується опис діапазону.",
      levelNameTooLong: "Назва рівня — не більше 10 символів.",
      levelAddTitle: "Додати рівень",
      levelEditTitle: "Змінити рівень",
      levelAvailable: "Готово питань: {n}",
      levelShort: "Потрібно {pull} за раунд, готово лише {available}",
      levelDuplicate: "Такий діапазон уже є в іншому рівні. Виберіть інший діапазон.",
      levelSave: "Зберегти рівень",
      levelFallbackName: "Рівень {n}",
      levelDeleteAria: "Видалити «{name}»",
      levelMoveUpAria: "Перемістити «{name}» вище",
      levelMoveDownAria: "Перемістити «{name}» нижче",
      levelRulesApplyAll: "Ці правила діють для всіх рівнів.",
      homeLevelsInsufficient: "Рівнів без потрібного запасу питань: {n} (потрібно {pull} за раунд)",
      genScopeStatusLevelsInsufficient: "Рівнів без потрібного запасу питань: {n} (потрібно {pull} за раунд). Згенеровані питання можна використати одразу.",
      genScopeStatusLevelMismatch: "Для рівня «{level}» бракує питань, і поточний діапазон генерації з ним не збігається.",
    },
    kk: {
      levelCountLabel: "Деңгейлер: {n}",
      levelMore: "тағы {n} деңгей",
      levelNoneLabel: "Деңгейлер орнатылмаған",
      levelListTitle: "Деңгейлер",
      levelListLead: "ТВ-де деңгейлер осы ретпен көрсетіледі; бала оны қашықтан басқару пультімен таңдайды.",
      levelListCount: "{n}/{max}",
      levelEmptyTitle: "Деңгейлер әзірге жоқ",
      levelEmptyText: "Бала ТВ-де ойын деңгейі ретінде таңдай алатындай сұрақ ауқымын қосыңыз.",
      levelAdd: "Деңгей қосу",
      levelMaxHint: "Ең көбі {n} деңгей қосуға болады.",
      levelUndo: "Қайтару",
      levelDeleted: "«{name}» деңгейі жойылды",
      levelSaved: "Деңгей сақталды",
      levelOrderSaved: "Деңгей реті сақталды",
      levelNameLabel: "Деңгей атауы (міндетті емес)",
      levelNamePlaceholder: "мысалы: Тоқсанға дайындық",
      levelNameHint: "10 таңбаға дейін. Бос болса, ТВ-де ауқым сипаттамасы көрсетіледі.",
      levelNameTooLong: "Деңгей атауы 10 таңбадан аспауы керек.",
      levelAddTitle: "Деңгей қосу",
      levelEditTitle: "Деңгейді өзгерту",
      levelAvailable: "Дайын сұрақтар: {n}",
      levelShort: "Раундқа {pull} керек, дайын тек {available}",
      levelDuplicate: "Бұл ауқым басқа деңгейде бар. Басқа ауқым таңдаңыз.",
      levelSave: "Деңгейді сақтау",
      levelFallbackName: "{n}-деңгей",
      levelDeleteAria: "«{name}» деңгейін жою",
      levelMoveUpAria: "«{name}» деңгейін жоғары жылжыту",
      levelMoveDownAria: "«{name}» деңгейін төмен жылжыту",
      levelRulesApplyAll: "Бұл ережелер барлық деңгейге қолданылады.",
      homeLevelsInsufficient: "Сұрағы жеткіліксіз деңгейлер: {n} (раундқа {pull} керек)",
      genScopeStatusLevelsInsufficient: "Сұрағы жеткіліксіз деңгейлер: {n} (раундқа {pull} керек). Жасалған сұрақтарды бірден қолдануға болады.",
      genScopeStatusLevelMismatch: "«{level}» деңгейіне сұрақ жеткіліксіз және ағымдағы жасау ауқымы оған сәйкес емес.",
    },
  };

  // V21: scoring is per difficulty (reward and deduction), and the unlock
  // threshold is a net-minute line rather than a minimum correct count.
  const V21_TEXTS = {
    zh: {
      scoringTitle: "难度计分",
      scoringLead: "答对按题目难度加时间；答错或超时按同一难度扣时间。",
      scoringCorrect: "答对（分钟）",
      scoringWrong: "答错/超时（分钟）",
      scoringRangeHint: "每题 0–30 分钟；填 0 表示答错不扣时间。",
      scoringReset: "恢复默认计分",
      scoringExample: "示例：答对简单 3 道、中等 2 道、困难 1 道，答错中等 1 道 → 加分 {gross}，扣分 {penalty}，净得 {net} 分钟",
      scoringExampleNoPenalty: "示例：答对简单 3 道、中等 2 道、困难 1 道 → 净得 {net} 分钟",
      scoringExampleCapped: "（单轮上限 {cap} 分钟）",
      scoringWarnNoReward: "三档奖励都是 0 分钟，孩子答完也拿不到时间。",
      scoringWarnThreshold: "最低解锁时长 {threshold} 分钟高于单轮上限 {cap} 分钟，可能永远无法解锁。",
      scoringWarnPenalty: "有难度的扣分不低于答对奖励，答错一题可能抵消多道答对。",
      unlockTitle: "最低解锁时长（分钟）",
      unlockHint: "净得分钟达到门槛才能解锁；0 表示不设门槛。门槛只做判断，不会从奖励里扣除。",
      unlockNone: "不设门槛",
      minuteN: "{n} 分钟",
      homeAnswerSub: "每轮 {n} 题 · 简单 +{easy} / 中等 +{medium} / 困难 +{hard} 分钟 · {unlock}",
      homeUnlock: "门槛 {n} 分钟",
      homeUnlockNone: "无门槛",
    },
    en: {
      scoringTitle: "Difficulty scoring",
      scoringLead: "Correct answers add time by difficulty; wrong answers and timeouts subtract time at the same difficulty.",
      scoringCorrect: "Correct (min)",
      scoringWrong: "Wrong/timeout (min)",
      scoringRangeHint: "0–30 minutes per question; 0 means no deduction.",
      scoringReset: "Reset scoring",
      scoringExample: "Example: 3 easy, 2 medium and 1 hard correct, 1 medium wrong → +{gross}, −{penalty}, net {net} min",
      scoringExampleNoPenalty: "Example: 3 easy, 2 medium and 1 hard correct → net {net} min",
      scoringExampleCapped: "(round cap {cap} min)",
      scoringWarnNoReward: "All three rewards are 0, so a finished round earns no time.",
      scoringWarnThreshold: "The {threshold} min unlock threshold is above the {cap} min round cap, so it may never unlock.",
      scoringWarnPenalty: "A deduction is at least its reward, so one wrong answer can cancel several correct ones.",
      unlockTitle: "Minimum unlock time (minutes)",
      unlockHint: "The round unlocks when net minutes reach this line; 0 means no threshold. The threshold only checks the result and is never deducted.",
      unlockNone: "No threshold",
      minuteN: "{n} min",
      homeAnswerSub: "{n} per round · easy +{easy} / medium +{medium} / hard +{hard} min · {unlock}",
      homeUnlock: "threshold {n} min",
      homeUnlockNone: "no threshold",
    },
    ru: {
      scoringTitle: "Очки по сложности",
      scoringLead: "Верный ответ добавляет время по сложности; ошибка или тайм-аут вычитает время той же сложности.",
      scoringCorrect: "Верно (мин)",
      scoringWrong: "Ошибка/тайм-аут (мин)",
      scoringRangeHint: "0–30 минут за вопрос; 0 — без вычета.",
      scoringReset: "Сбросить очки",
      scoringExample: "Пример: верно 3 простых, 2 средних и 1 сложный, 1 средняя ошибка → +{gross}, −{penalty}, итог {net} мин",
      scoringExampleNoPenalty: "Пример: верно 3 простых, 2 средних и 1 сложный → итог {net} мин",
      scoringExampleCapped: "(лимит раунда {cap} мин)",
      scoringWarnNoReward: "Все три награды равны 0 — за раунд ребёнок не получит время.",
      scoringWarnThreshold: "Порог {threshold} мин выше лимита раунда {cap} мин — разблокировка может не сработать.",
      scoringWarnPenalty: "Штраф не меньше награды: одна ошибка может перекрыть несколько верных ответов.",
      unlockTitle: "Минимум для разблокировки (минуты)",
      unlockHint: "Раунд разблокируется, когда чистые минуты достигают порога; 0 — без порога. Порог только проверяет результат и не вычитается.",
      unlockNone: "Без порога",
      minuteN: "{n} мин",
      homeAnswerSub: "{n} за раунд · лёгкий +{easy} / средний +{medium} / сложный +{hard} мин · {unlock}",
      homeUnlock: "порог {n} мин",
      homeUnlockNone: "без порога",
    },
    uk: {
      scoringTitle: "Очки за складністю",
      scoringLead: "Правильна відповідь додає час за складністю; помилка або тайм-аут віднімає час тієї ж складності.",
      scoringCorrect: "Правильно (хв)",
      scoringWrong: "Помилка/тайм-аут (хв)",
      scoringRangeHint: "0–30 хвилин за питання; 0 — без відрахування.",
      scoringReset: "Скинути очки",
      scoringExample: "Приклад: правильно 3 простих, 2 середніх і 1 складний, 1 середня помилка → +{gross}, −{penalty}, разом {net} хв",
      scoringExampleNoPenalty: "Приклад: правильно 3 простих, 2 середніх і 1 складний → разом {net} хв",
      scoringExampleCapped: "(ліміт раунду {cap} хв)",
      scoringWarnNoReward: "Усі три нагороди дорівнюють 0 — за раунд дитина не отримає часу.",
      scoringWarnThreshold: "Поріг {threshold} хв вищий за ліміт раунду {cap} хв — розблокування може не спрацювати.",
      scoringWarnPenalty: "Штраф не менший за нагороду: одна помилка може перекрити кілька правильних відповідей.",
      unlockTitle: "Мінімум для розблокування (хвилини)",
      unlockHint: "Раунд розблоковується, коли чисті хвилини сягають порогу; 0 — без порогу. Поріг лише перевіряє результат і не віднімається.",
      unlockNone: "Без порогу",
      minuteN: "{n} хв",
      homeAnswerSub: "{n} за раунд · легкий +{easy} / середній +{medium} / складний +{hard} хв · {unlock}",
      homeUnlock: "поріг {n} хв",
      homeUnlockNone: "без порогу",
    },
    kk: {
      scoringTitle: "Қиындық бойынша ұпай",
      scoringLead: "Дұрыс жауап қиындыққа қарай уақыт қосады; қате немесе уақыт біту сол қиындықтан уақыт шегереді.",
      scoringCorrect: "Дұрыс (мин)",
      scoringWrong: "Қате/уақыт біту (мин)",
      scoringRangeHint: "Әр сұраққа 0–30 минут; 0 — шегерусіз.",
      scoringReset: "Ұпайды әдепкіге қайтару",
      scoringExample: "Мысал: 3 жеңіл, 2 орташа және 1 қиын дұрыс, 1 орташа қате → +{gross}, −{penalty}, қорытынды {net} мин",
      scoringExampleNoPenalty: "Мысал: 3 жеңіл, 2 орташа және 1 қиын дұрыс → қорытынды {net} мин",
      scoringExampleCapped: "(раунд шегі {cap} мин)",
      scoringWarnNoReward: "Үш сыйақы да 0 — раунд біткенде бала уақыт алмайды.",
      scoringWarnThreshold: "Ең төмен ашу шегі {threshold} мин раунд шегінен {cap} мин жоғары — ашу мүмкін болмауы мүмкін.",
      scoringWarnPenalty: "Шегерім сыйақыдан кем емес: бір қате бірнеше дұрыс жауапты жаба алады.",
      unlockTitle: "Ашуға қажет ең аз уақыт (минут)",
      unlockHint: "Раунд таза минуттар шекке жеткенде ашылады; 0 — шек жоқ. Шек тек нәтижені тексереді, сыйақыдан шегерілмейді.",
      unlockNone: "Шек жоқ",
      minuteN: "{n} мин",
      homeAnswerSub: "Раундта {n} сұрақ · жеңіл +{easy} / орташа +{medium} / қиын +{hard} мин · {unlock}",
      homeUnlock: "шек {n} мин",
      homeUnlockNone: "шек жоқ",
    },
  };

  const LANGS = ["zh", "en", "ru", "uk", "kk"];
  for (const code of LANGS) {
    Object.assign(TEXTS[code], V17_TEXTS[code], V19_TEXTS[code], V20_TEXTS[code], V21_TEXTS[code]);
  }

  // Local days are normally 24h; allow the 25h DST fall-back day.
  const AUTH_MAX_TTL_MS = 25 * 60 * 60 * 1000;
  const AUTH_CLOCK_SKEW_MS = 30 * 60 * 1000;
  const DEFAULT_CONFIG = {
    defaultTimeLimitSec: 60,
    minutesPerPoint: 5,
    minCorrectCount: 1,
    maxEarnedMinutes: 60,
    answerDisplaySec: 30,
    shuffleOptions: true,
    pullCount: 5,
    minutesPerQuestion: 5,
    pointsPerQuestion: 1,
    easyRewardMinutes: 1,
    mediumRewardMinutes: 2,
    hardRewardMinutes: 5,
    easyPenaltyMinutes: 0,
    mediumPenaltyMinutes: 0,
    hardPenaltyMinutes: 0,
    minUnlockMinutes: 0,
  };
  const SCORING_DEFAULTS = {
    easyRewardMinutes: 1,
    mediumRewardMinutes: 2,
    hardRewardMinutes: 5,
    easyPenaltyMinutes: 0,
    mediumPenaltyMinutes: 0,
    hardPenaltyMinutes: 0,
    minUnlockMinutes: 0,
  };
  const UNLOCK_VALUES = [0, 5, 10, 20, 30];
  const SCOPE_VALUES = ["unit", "midterm", "final", "review"];
  const REQUIREMENT_MAX_CHARS = 30;
  const GEN_OPTIONS_VALUES = [4, 6];
  const TARGET_COUNTS = [20, 30, 50];
  const ROUND_MIN = 5;
  const ROUND_MAX = 50;
  const ROUND_STEP = 5;
  const ROUND_ITEM_HEIGHT = 44;
  const ROUND_VALUES = Array.from(
    { length: (ROUND_MAX - ROUND_MIN) / ROUND_STEP + 1 },
    (_, index) => ROUND_MIN + index * ROUND_STEP,
  );
  const GEN_DIFFICULTY_VALUES = ["", "easy", "medium", "hard"];
  const GEN_PAGE_IDS = {
    home: "generateHome",
    gen: "genConfigPage",
    answer: "answerConfigPage",
    level: "levelEditorPage",
    questions: "questionsPage",
  };
  const MAX_LEVELS = 6;
  const LEVEL_NAME_MAX = 10;
  const BANK_PAGE_SIZE = 40;
  const SUBJECT_LABEL_KEYS = {
    chinese: "subjectChinese",
    math: "subjectMath",
    english: "subjectEnglish",
    science: "subjectScience",
    all: "subjectAll",
  };
  const TEXTBOOK_LABEL_KEYS = {
    bubian: "tbBubian",
    renjiao: "tbRenjiao",
    renjiao_ej: "tbRenjiaoEj",
    sujiao: "tbSujiao",
    beishida: "tbBeishida",
    qingdao: "tbQingdao",
    pep: "tbPep",
    waiyan: "tbWaiyan",
    yilin: "tbYilin",
    kejiao: "tbKejiao",
    general: "tbGeneral",
    all: "tbAll",
  };
  let TEXTBOOKS = {
    chinese: ["bubian"],
    math: ["renjiao", "beishida", "sujiao", "qingdao"],
    english: ["pep", "waiyan", "yilin"],
    science: ["kejiao", "sujiao", "renjiao_ej"],
    all: ["general", "all"],
  };
  let DEFAULT_TEXTBOOK = {
    chinese: "bubian",
    math: "renjiao",
    english: "pep",
    science: "kejiao",
    all: "general",
  };
  const SCOPE_PROMPT_KEYS = {
    unit: "promptScopeUnit",
    midterm: "promptScopeMidterm",
    final: "promptScopeFinal",
    review: "promptScopeReview",
  };
  const SCOPE_LABEL_KEYS = {
    unit: "scopeUnit",
    midterm: "scopeMidterm",
    final: "scopeFinal",
    review: "scopeReview",
  };
  const DIFFICULTY_LABEL_KEYS = {
    "": "difficultyAny",
    easy: "difficultyEasy",
    medium: "difficultyMedium",
    hard: "difficultyHard",
  };
  // 答题范围是答题配置的一部分（与 PTV 同一套规范字段）。难度属于范围；
  // 范围为空 = 绑定出题范围（由出题配置实时推导），此时没有难度过滤。
  let SCOPE_SUBJECTS = ["chinese", "math", "english", "science", "all"];
  const SUBJECT_ALL = "all";
  const TEXTBOOK_ALL = "all";
  const SCOPE_TOPIC_VALUES = ["unit", "midterm", "final", "all"];
  const SCOPE_TOPIC_TO_CANONICAL = {
    unit: "UNIT",
    midterm: "MIDTERM",
    final: "FINAL",
    all: "REVIEW",
  };
  const SCOPE_TOPIC_FROM_CANONICAL = {
    UNIT: "unit",
    MIDTERM: "midterm",
    FINAL: "final",
    REVIEW: "all",
  };
  const SCOPE_DIFFICULTY_VALUES = ["", "EASY", "MEDIUM", "HARD"];
  // Custom answer scope is a stepwise filter: each step shows only values that
  // still have bank questions under the upstream selections. Units are listed
  // individually in the topic step instead of being summarized into one chip.
  const SCOPE_STEPS = ["grade", "semester", "subject", "textbook", "topic", "difficulty"];
  const SCOPE_STEP_IDS = {
    grade: "scopeStepGrade",
    semester: "scopeStepSemester",
    subject: "scopeStepSubject",
    textbook: "scopeStepTextbook",
    topic: "scopeStepTopic",
    difficulty: "scopeStepDifficulty",
  };
  const SCOPE_STEP_CHIP_IDS = {
    grade: "scopeGradeChips",
    semester: "scopeSemesterChips",
    subject: "scopeSubjectChips",
    textbook: "scopeTextbookChips",
    topic: "scopeTopicChips",
    difficulty: "scopeDifficultyChips",
  };
  const STATUS_LABEL_KEYS = {
    unused: "bankStatusUnused",
    in_round: "bankStatusInRound",
    correct: "bankStatusCorrect",
    wrong: "bankStatusWrong",
    legacy: "bankStatusLegacy",
    expired: "bankStatusExpired",
  };
  const GEN_PREFS_KEY = "quiz.gen.prefs";
  const TAB_VIEWS = {
    generate: "view-generate",
    bank: "view-bank",
    me: "view-me",
  };
  const $ = (id) => document.getElementById(id);
  let lang = detectLang();
  let adminToken = null;
  let adminExpiresAt = 0;
  let gateShown = false;
  let gateOccupied = false;
  let publicState = null;
  let adminSession = null;
  let challengeConfig = null;
  let syncState = null;
  let cloudManaged = false;
  let bankCapacity = 6000;
  let bankCounts = null;
  let providerState = { aiSupported: true, consentAccepted: false, configured: false, baseUrl: "", model: "" };
  let providerMsg = null;
  let pendingGenerate = false;
  // The privacy notice is a gate, not a dismissible hint: the editor stays
  // hidden until a parent confirms it on this TV.
  let pendingEnter = false;
  let activeTab = "generate";
  let bankFilter = "all";
  let bankItems = [];
  let bankOffset = 0;
  let bankHasMore = false;
  let bankLoading = false;
  let bankRequest = 0;
  let targetCount = 20;
  let genState = idleGeneration();
  let genTimer = null;
  let generationToken = 0;
  let settingsSaving = false;
  let rangeSaving = false;
  let activeModal = null;
  let lastFocused = null;
  let genGradeValue = 4;
  let genSemesterValue = 1;
  let genUnitValue = 1;
  let genSubjectValue = "math";
  // 题目要求为空时使用“总复习”作为兜底归类；文字可自动覆盖。
  let genScopeValue = "review";
  let genTextbookId = "renjiao";
  let genDifficultyValue = "";
  // 完全自由的题目要求；样例只是快捷填充，不再代表下拉选中项。
  let genRequirementValue = "";
  let genOptionsValue = 4;
  let genAutoAddValue = true;
  let genPromptDirty = false;
  let genPrefsSaveTimer = null;
  let genPage = "home";
  let answerTab = "scope";
  // The ordered parent-designed levels. Each entry keeps the canonical API
  // shape plus a normalised local scope for rendering and duplicate checks.
  let answerLevels = [];
  let levelEditingId = null;
  let levelNameDraft = "";
  let levelSaving = false;
  let levelUndo = null;
  let levelUndoTimer = 0;
  let levelMessageTimer = 0;
  // Legacy single-scope view, derived from the first level for the old
  // generation helpers and cached surfaces.
  let answerScope = null;
  let answerScopeMode = "bound";
  let answerScopeAvailable = 0;
  let scopeLoaded = false;
  let answerDirty = false;
  // Custom scope draft may be partial while the parent picks step by step.
  let customScope = null;
  // Per-step facet cache: { counts, unrestricted, matched }. Values are keyed
  // by the upstream selections only, so changing one step never rewrites
  // earlier choices.
  let scopeFacets = {};
  let scopeOptionsLoading = {};
  let scopeRequestToken = 0;
  let scopeMatched = null;
  let scopeMismatchAckKey = "";
  let selectedBatch = new Set();
  let roundWheelValue = DEFAULT_CONFIG.pullCount;
  let roundWheelItems = [];
  let roundWheelScrollTimer = 0;
  // The page lives under the TV's random token path. A leading "/" would make
  // fetch resolve the API from the host root, which PhoneServer answers with 404.
  const API_ROOT = location.pathname.replace(/\/quiz\/?$/, "/");
  const AUTH_KEY = "quiz.auth:" + location.host + API_ROOT;

  function idleGeneration() {
    return {
      phase: "idle",
      jobId: null,
      batchId: null,
      target: targetCount,
      generated: 0,
      unique: 0,
      duplicates: 0,
      rejected: 0,
      batch: [],
      batchLoaded: false,
      customTopic: "",
      autoAdd: true,
      stopped: false,
      stopping: false,
      warnings: [],
      errorCode: null,
      noticeKey: null,
      noticeCount: 0,
    };
  }

  function loadAuth() {
    try {
      const raw = localStorage.getItem(AUTH_KEY);
      if (!raw) return;
      const data = JSON.parse(raw);
      const remaining = Number(data && data.expiresAt) - Date.now();
      // Tokens from the old one-year scheme are rejected here so the editor
      // does not flash before the TV rejects them.
      if (data && /^[0-9a-f]{32}$/.test(data.token || "") &&
          remaining > 0 && remaining <= AUTH_MAX_TTL_MS + AUTH_CLOCK_SKEW_MS) {
        adminToken = data.token;
        adminExpiresAt = Number(data.expiresAt);
      } else {
        localStorage.removeItem(AUTH_KEY);
      }
    } catch (_) { /* private mode */ }
  }

  function saveAuth() {
    try {
      localStorage.setItem(AUTH_KEY, JSON.stringify({ token: adminToken, expiresAt: adminExpiresAt }));
    } catch (_) { /* private mode */ }
  }

  function clearAuth() {
    adminToken = null;
    adminExpiresAt = 0;
    try { localStorage.removeItem(AUTH_KEY); } catch (_) { /* ignore */ }
  }

  function detectLang() {
    try {
      const saved = localStorage.getItem("quiz.lang");
      if (saved && TEXTS[saved]) return saved;
    } catch (_) { /* private mode */ }
    const browser = (navigator.language || "en").slice(0, 2).toLowerCase();
    return TEXTS[browser] ? browser : "en";
  }

  function t(key, vars) {
    let value = (TEXTS[lang] && TEXTS[lang][key]) || TEXTS.en[key] || key;
    if (vars) {
      for (const name of Object.keys(vars)) {
        value = value.replaceAll("{" + name + "}", String(vars[name]));
      }
    }
    return value;
  }

  // ---- 课程表 ----
  // 新电视在 /admin/session 里下发课程表，年级/科目/教材/单元上限与五语标签
  // 全部以数据为准；旧电视没有这个字段，继续用内置 1-6 兜底。
  let curriculumModel = null;
  let unitCatalogModel = null;
  let cloudJuniorReady = true;

  function normalizeCurriculum(raw) {
    if (!raw || typeof raw !== "object") return null;
    const grades = (Array.isArray(raw.grades) ? raw.grades : [])
      .map((grade) => ({
        no: Number(grade && grade.no),
        stage: String(grade && grade.stage || ""),
        labels: (grade && grade.labels) || {},
      }))
      .filter((grade) => Number.isInteger(grade.no) && grade.no >= 1)
      .sort((a, b) => a.no - b.no);
    if (grades.length === 0) return null;
    const stages = (Array.isArray(raw.stages) ? raw.stages : [])
      .map((stage) => {
        const range = Array.isArray(stage && stage.grades) ? stage.grades : [];
        return {
          id: String(stage && stage.id || ""),
          minGrade: Number(range[0]),
          maxGrade: Number(range[1]),
          unitMax: Number(stage && stage.unitMax),
          labels: (stage && stage.labels) || {},
        };
      })
      .filter((stage) => Number.isInteger(stage.minGrade) &&
        Number.isInteger(stage.maxGrade) &&
        Number.isInteger(stage.unitMax) && stage.unitMax > 0);
    const subjects = (Array.isArray(raw.subjects) ? raw.subjects : [])
      .map((subject) => ({
        id: String(subject && subject.id || "").toLowerCase(),
        labels: (subject && subject.labels) || {},
        aliases: Array.isArray(subject && subject.aliases)
          ? subject.aliases.map((alias) => String(alias))
          : [],
        options: (Array.isArray(subject && subject.options) ? subject.options : [])
          .map((option) => {
            const range = Array.isArray(option && option.grades) ? option.grades : [];
            const textbooks = Array.isArray(option && option.textbooks)
              ? option.textbooks.map((id) => String(id).toLowerCase())
              : [];
            return {
              minGrade: Number(range[0]),
              maxGrade: Number(range[1]),
              defaultTextbook: String(option && option.defaultTextbook || "").toLowerCase(),
              textbooks,
            };
          })
          .filter((option) => Number.isInteger(option.minGrade) &&
            Number.isInteger(option.maxGrade) && option.textbooks.length > 0),
      }))
      .filter((subject) => subject.id && subject.options.length > 0);
    const textbooks = (Array.isArray(raw.textbooks) ? raw.textbooks : [])
      .map((textbook) => ({
        id: String(textbook && textbook.id || "").toLowerCase(),
        labels: (textbook && textbook.labels) || {},
      }))
      .filter((textbook) => textbook.id);
    if (subjects.length === 0 || textbooks.length === 0) return null;
    return {
      stages,
      grades,
      subjects,
      textbooks,
      unitAliases: Array.isArray(raw.unitAliases)
        ? raw.unitAliases.map((alias) => String(alias)).filter(Boolean)
        : [],
    };
  }

  function fallbackCurriculum() {
    const numbers = [1, 2, 3, 4, 5, 6];
    const subjects = SCOPE_SUBJECTS.map((id) => ({
      id,
      labels: {},
      aliases: [],
      options: [{
        minGrade: 1,
        maxGrade: 6,
        defaultTextbook: DEFAULT_TEXTBOOK[id] || "general",
        textbooks: (TEXTBOOKS[id] || ["general"]).slice(),
      }],
    }));
    return {
      stages: [{ id: "primary", minGrade: 1, maxGrade: 6, unitMax: 8, labels: {} }],
      grades: numbers.map((no) => ({ no, stage: "primary", labels: {} })),
      subjects,
      textbooks: Object.keys(TEXTBOOK_LABEL_KEYS).map((id) => ({ id, labels: {} })),
      unitAliases: ["单元", "章", "Unit", "Chapter"],
    };
  }

  function curriculum() {
    return curriculumModel || fallbackCurriculum();
  }

  function gradeNos() {
    return curriculum().grades.map((grade) => grade.no);
  }

  function gradeCodes() {
    return gradeNos().flatMap((no) => [String(no) + "a", String(no) + "b"]);
  }

  function gradeDef(no) {
    const number = Number(no);
    return curriculum().grades.find((grade) => grade.no === number) || null;
  }

  function gradeLabel(no) {
    const def = gradeDef(no);
    return (def && def.labels && def.labels[lang]) || t("grade" + Number(no));
  }

  function subjectDef(id) {
    const key = String(id || "").toLowerCase();
    return curriculum().subjects.find((subject) => subject.id === key) || null;
  }

  function subjectLabel(id) {
    const def = subjectDef(id);
    return (def && def.labels && def.labels[lang]) ||
      t(SUBJECT_LABEL_KEYS[id] || "subjectMath");
  }

  function textbookDef(id) {
    const key = String(id || "").toLowerCase();
    return curriculum().textbooks.find((textbook) => textbook.id === key) || null;
  }

  function textbookLabel(id) {
    const def = textbookDef(id);
    return (def && def.labels && def.labels[lang]) ||
      t(TEXTBOOK_LABEL_KEYS[id] || "tbGeneral");
  }

  function stageForGrade(no) {
    const grade = Number(no);
    return curriculum().stages.find((stage) =>
      grade >= stage.minGrade && grade <= stage.maxGrade) || null;
  }

  function unitMaxForGrade(no) {
    const stage = stageForGrade(no);
    return stage ? stage.unitMax : 8;
  }

  function maxUnit() {
    return curriculum().stages.reduce(
      (max, stage) => Math.max(max, stage.unitMax), 0) || 8;
  }

  // ---- 教材单元目录（仅展示与提示词；unitNo 仍是唯一键）----
  // 电视通过 /api/quiz/admin/units 下发国家中小学智慧教育平台的目录。
  // 目录缺失、旧电视或未覆盖的版本继续使用“第N单元”和学段上限。
  function normalizeUnitCatalog(raw) {
    if (!raw || typeof raw !== "object") return null;
    const books = (Array.isArray(raw.books) ? raw.books : [])
      .map((book) => {
        const grade = Number(book && book.grade);
        const semesters = (Array.isArray(book && book.semesters) ? book.semesters : [])
          .map(Number)
          .filter((semester) => semester === 1 || semester === 2);
        const units = (Array.isArray(book && book.units) ? book.units : [])
          .map((unit) => {
            const no = Number(unit && unit.no);
            const title = String(unit && unit.title || "").trim();
            if (!Number.isInteger(no) || no < 1 || no > 99 || !title) return null;
            return {
              no,
              title,
              kind: String(unit && unit.kind || "UNIT").trim().toUpperCase() || "UNIT",
              lessons: (Array.isArray(unit && unit.lessons) ? unit.lessons : [])
                .map((lesson) => String(lesson || "").trim())
                .filter(Boolean),
            };
          })
          .filter(Boolean)
          .sort((a, b) => a.no - b.no);
        if (!Number.isInteger(grade) || grade < 1 || grade > 9 ||
            semesters.length === 0 || units.length === 0) {
          return null;
        }
        const computedMax = units
          .filter((unit) => unit.kind === "UNIT")
          .reduce((max, unit) => Math.max(max, unit.no), 0);
        const maxUnitNo = Math.max(computedMax, Number(book && book.maxUnitNo) || 0);
        if (maxUnitNo < 1) return null;
        return {
          grade,
          semesters: Array.from(new Set(semesters)),
          subject: String(book && book.subject || "").toLowerCase(),
          textbook: String(book && book.textbook || "").toLowerCase(),
          volume: String(book && book.volume || ""),
          maxUnitNo,
          units,
        };
      })
      .filter((book) => book && book.subject && book.textbook);
    if (!books.length) return null;
    return {
      catalogVersion: String(raw.catalogVersion || ""),
      fetchedAt: String(raw.fetchedAt || ""),
      books,
    };
  }

  function unitBook(grade, semester, subject, textbook) {
    if (!unitCatalogModel) return null;
    const number = Number(grade);
    const term = Number(semester);
    const subjectId = String(subject || "").toLowerCase();
    const textbookId = String(textbook || "").toLowerCase();
    if (!Number.isInteger(number) || (term !== 1 && term !== 2) ||
        !subjectId || subjectId === SUBJECT_ALL ||
        !textbookId || textbookId === TEXTBOOK_ALL) {
      return null;
    }
    const candidates = unitCatalogModel.books.filter((book) =>
      book.grade === number &&
      book.semesters.includes(term) &&
      book.subject === subjectId &&
      book.textbook === textbookId);
    // 全一册和单册可能同时覆盖同一学期；优先单册，和电视端保持一致。
    const preferredVolume = term === 1 ? "上册" : "下册";
    return candidates.find((book) => book.volume === preferredVolume) ||
      candidates[0] || null;
  }

  function unitMaxForContext(grade, semester, subject, textbook) {
    if (unitCatalogModel) {
      const exact = unitBook(grade, semester, subject, textbook);
      if (exact) return exact.maxUnitNo;
      const subjectId = String(subject || "").toLowerCase();
      const textbookId = String(textbook || "").toLowerCase();
      const candidates = unitCatalogModel.books.filter((book) =>
        book.grade === Number(grade) &&
        book.semesters.includes(Number(semester)) &&
        (subjectId === SUBJECT_ALL || book.subject === subjectId) &&
        (textbookId === TEXTBOOK_ALL || book.textbook === textbookId));
      if (candidates.length) {
        return candidates.reduce((max, book) => Math.max(max, book.maxUnitNo), 0);
      }
    }
    return unitMaxForGrade(grade);
  }

  function unitDisplayTitle(grade, semester, subject, textbook, unitNo) {
    const book = unitBook(grade, semester, subject, textbook);
    if (!book) return "";
    return (book.units.find((unit) => unit.no === Number(unitNo)) || {}).title || "";
  }

  function unitDisplayLabel(grade, semester, subject, textbook, unitNo) {
    // Chip labels stay compact: some English unit titles are full sentences.
    return truncateText(
      unitDisplayTitle(grade, semester, subject, textbook, unitNo) ||
        t("unitN", { n: unitNo }),
      24);
  }

  function unitLessons(grade, semester, subject, textbook, unitNo) {
    const book = unitBook(grade, semester, subject, textbook);
    if (!book) return [];
    const unit = book.units.find((item) => item.no === Number(unitNo));
    return unit ? unit.lessons.slice() : [];
  }

  function generationUnitBook() {
    return unitBook(genGradeValue, genSemesterValue, genSubjectValue, genTextbookId);
  }

  function generationUnitMax() {
    return unitMaxForContext(
      genGradeValue, genSemesterValue, genSubjectValue, genTextbookId);
  }

  function generationUnitTitle(unitNo) {
    return unitDisplayTitle(
      genGradeValue, genSemesterValue, genSubjectValue, genTextbookId, unitNo);
  }

  function unitMatchKey(value) {
    return String(value || "")
      .toLowerCase()
      .replace(/[\s　·•.。,，:：;；、'"“”‘’()（）\[\]【】!?！？\-—_\/\\]+/g, "");
  }

  function unitNumberToken(value) {
    return /^\d+$/.test(value) ? Number(value) : chineseNumber(value);
  }

  // The platform titles usually carry the textbook's own number, which is not
  // always the raw tree ordinal: geography starts at 第七章, chemistry's second
  // term at 第八单元, and project/activity nodes can sit between units.
  function catalogUnitLabel(unit) {
    const title = String(unit && unit.title || "");
    let match = /第\s*([一二三四五六七八九十百零〇两\d]+)\s*(?:单元|章|课)/.exec(title);
    if (match) return unitNumberToken(match[1]);
    match = /\bunit\s*(\d+)\b/i.exec(title);
    if (match) return Number(match[1]);
    match = /^\s*([一二三四五六七八九十百]+)\s*[、.．:：\s]/.exec(title);
    if (match) return unitNumberToken(match[1]);
    match = /^\s*(\d+)\s*[、.．:：\s]/.exec(title);
    if (match) return Number(match[1]);
    return null;
  }

  function explicitUnitNumber(value) {
    const text = String(value || "");
    let match = /第\s*([一二三四五六七八九十百零〇两\d]+)\s*(?:单元|章|课)/.exec(text);
    if (match) return unitNumberToken(match[1]);
    match = /\bunit\s*(\d+)\b/i.exec(text);
    return match ? Number(match[1]) : null;
  }

  // Match a parent's free text against the current book's unit titles and
  // lesson titles. The longest match wins, so "第一单元 观潮" beats "第一单元".
  function catalogUnitFromText(value, grade, semester, subject, textbook) {
    const book = unitBook(grade, semester, subject, textbook);
    if (!book) return null;
    const text = unitMatchKey(value);
    if (!text) return null;
    let best = null;
    let bestLength = 0;
    book.units.forEach((unit) => {
      if (unit.kind !== "UNIT") return;
      [unit.title].concat(unit.lessons).forEach((alias) => {
        const key = unitMatchKey(alias);
        if (key.length < 2) return;
        if (text.includes(key) && key.length > bestLength) {
          best = unit;
          bestLength = key.length;
        }
      });
    });
    if (best) return best;
    // "第七章" / "Unit 10" alone has no full-title match. Resolve it against
    // the number printed in each unit title instead of the raw ordinal.
    const label = explicitUnitNumber(value);
    if (label === null) return null;
    return book.units.find((unit) =>
      unit.kind === "UNIT" && catalogUnitLabel(unit) === label) || null;
  }

  function truncateText(value, maxChars) {
    const text = String(value || "");
    return text.length <= maxChars ? text : text.slice(0, maxChars - 1) + "…";
  }

  function unitLessonSummary(unitNo) {
    const lessons = unitLessons(
      genGradeValue, genSemesterValue, genSubjectValue, genTextbookId, unitNo);
    if (!lessons.length) return "";
    return truncateText(lessons.join(lang === "zh" ? "、" : ", "), 80);
  }

  function generationUnitPromptScope() {
    const title = generationUnitTitle(genUnitValue);
    if (!title) return "";
    const lessons = unitLessonSummary(genUnitValue);
    return lessons
      ? t("promptScopeUnitLessons", { title, lessons })
      : t("promptScopeUnitTitle", { title });
  }

  function subjectOptionForGrade(id, no) {
    const def = subjectDef(id);
    if (!def) return null;
    const grade = Number(no);
    return def.options.find((option) =>
      grade >= option.minGrade && grade <= option.maxGrade) || null;
  }

  function subjectIdsForGrade(no) {
    const grade = Number(no);
    if (!Number.isInteger(grade)) return allSubjectIds();
    return curriculum().subjects
      .filter((subject) => Boolean(subjectOptionForGrade(subject.id, grade)))
      .map((subject) => subject.id);
  }

  function textbooksFor(no, subject) {
    const option = subjectOptionForGrade(subject, no);
    return option ? option.textbooks.slice() : [];
  }

  function defaultTextbookFor(no, subject) {
    const option = subjectOptionForGrade(subject, no);
    if (option && option.defaultTextbook) return option.defaultTextbook;
    return (option && option.textbooks[0]) || "general";
  }

  function allSubjectIds() {
    return curriculum().subjects.map((subject) => subject.id);
  }

  function allTextbookIds() {
    return curriculum().textbooks.map((textbook) => textbook.id);
  }

  function isKnownSubject(id) {
    return Boolean(subjectDef(id));
  }

  function isKnownTextbook(id) {
    return Boolean(textbookDef(id));
  }

  function cloudJuniorBlocked(grade) {
    const stage = stageForGrade(grade);
    return !cloudJuniorReady && cloudManaged && Boolean(stage) && stage.id === "junior";
  }

  function applyCurriculum(raw, juniorReady) {
    curriculumModel = normalizeCurriculum(raw);
    // 旧电视不带 cloudJuniorReady；它们本来就只支持 1-6，不会走到初中分支。
    cloudJuniorReady = juniorReady !== false;
    SCOPE_SUBJECTS = allSubjectIds();
  }

  function codeText(code) {
    const known = t("err_" + code);
    return known === "err_" + code ? code : known;
  }

  function errorText(error) {
    if (!error) return "";
    return codeText(error.code || error.message || "NETWORK");
  }

  function normalizeConfig(config) {
    return Object.assign({}, DEFAULT_CONFIG, config || {});
  }

  function currentConfig() {
    return challengeConfig || normalizeConfig(DEFAULT_CONFIG);
  }

  function currentCounts() {
    if (bankCounts) return bankCounts;
    if (publicState && publicState.bank) return publicState.bank;
    return { total: 0, unused: 0, inRound: 0, answered: 0, correct: 0, wrong: 0, legacy: 0, expired: 0 };
  }

  function applyBankCounts(counts) {
    if (!counts) return;
    bankCounts = {
      total: Number(counts.total || 0),
      unused: Number(counts.unused || 0),
      inRound: Number(counts.inRound || 0),
      answered: Number(counts.answered || 0),
      correct: Number(counts.correct || 0),
      wrong: Number(counts.wrong || 0),
      legacy: Number(counts.legacy || 0),
      expired: Number(counts.expired || 0),
    };
  }

  function formatDate(ms) {
    if (!ms) return "";
    try {
      const locale = lang === "zh" ? "zh-CN" : lang;
      return new Intl.DateTimeFormat(locale, {
        month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
      }).format(new Date(ms));
    } catch (_) {
      return new Date(ms).toLocaleString();
    }
  }

  function applyLanguage(value) {
    lang = LANGS.includes(value) ? value : "en";
    try { localStorage.setItem("quiz.lang", lang); } catch (_) { /* ignore */ }
    applyI18n();
  }

  function applyI18n() {
    document.documentElement.lang = lang;
    document.title = t("title");
    document.querySelectorAll("[data-i18n]").forEach((el) => {
      el.textContent = t(el.dataset.i18n);
    });
    document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
      el.placeholder = t(el.dataset.i18nPlaceholder);
    });
    document.querySelectorAll("[data-i18n-aria]").forEach((el) => {
      el.setAttribute("aria-label", t(el.dataset.i18nAria));
    });
    $("langSelect").value = lang;
    $("langSelectMe").value = lang;
    if (!$("gateCard").classList.contains("hidden")) {
      if (gateShown) {
        renderGateOccupation();
      } else {
        // The gate is visible before the first status call finishes; show its
        // normal copy instead of the placeholder dash.
        $("gateTitle").textContent = t("gate_pair_title");
        $("gateText").textContent = t("gate_pair_text");
      }
    }
    syncGenerateComposer(true);
    renderStock();
    renderHome();
    renderGenerate();
    renderProvider();
    renderProviderMessages();
    renderSync();
    renderSettings();
    renderMaintenance();
    renderBankCounts();
    renderBank();
    if (genPage === "answer") renderAnswerPage();
    if (genPage === "level") renderLevelEditor();
    if (genPage === "questions") renderQuestions();
    updateToggleState();
  }

  function renderStock() {
    const counts = currentCounts();
    if ($("bankMeta")) $("bankMeta").textContent = t("bankMetaCount", { n: counts.total });
    let notice = "";
    if (counts.inRound > 0) {
      notice = t("inRoundNotice");
    } else if (publicState && publicState.enabled !== false && publicState.source !== "CLOUD" &&
               (publicState.bankState === "empty" || publicState.bankState === "insufficient")) {
      notice = t("insufficientNotice");
    }
    if ($("stockNotice")) {
      $("stockNotice").textContent = notice;
      $("stockNotice").classList.toggle("hidden", !notice);
    }
  }

  function gradeText(grade, semester) {
    return t("gradeWithSemester", {
      grade: gradeLabel(grade),
      semester: t(Number(semester) === 2 ? "semesterSecond" : "semesterFirst"),
    });
  }

  function generationTopicText() {
    if (genScopeValue === "unit") {
      return generationUnitTitle(genUnitValue) || t("unitN", { n: genUnitValue });
    }
    return t(SCOPE_LABEL_KEYS[genScopeValue] || "scopeUnit");
  }

  function generationDifficultyText() {
    const key = genDifficultyValue
      ? (DIFFICULTY_LABEL_KEYS[genDifficultyValue] || "difficultyMedium")
      : "difficultyUnlimited";
    return t(key);
  }

  // The home card shows at most three level rows. One level keeps its full
  // name, two or three levels get a numbered row each, and longer lists keep
  // the first and last row with an ellipsis count between them. The hidden
  // text leaves the collapsed middle levels available to screen readers.
  function renderAnswerSummary(levels) {
    const host = $("homeAnswerMain");
    if (!host) return;
    const hidden = $("homeAnswerA11y");
    host.textContent = "";
    host.classList.remove("single", "empty");
    if (hidden) hidden.textContent = "";

    const entries = levels.map((level, index) => ({
      ordinal: index + 1,
      name: String(level.name || "").trim() || scopeSummaryText(level.scope),
    }));
    if (!entries.length) {
      host.classList.add("empty");
      host.textContent = t("levelEmptyTitle");
      return;
    }

    const rows = [];
    if (entries.length <= 3) {
      entries.forEach((entry) => rows.push({ ordinal: entry.ordinal, name: entry.name }));
    } else {
      rows.push({ ordinal: entries[0].ordinal, name: entries[0].name });
      rows.push({ more: entries.length - 2 });
      rows.push({
        ordinal: entries[entries.length - 1].ordinal,
        name: entries[entries.length - 1].name,
      });
      if (hidden) {
        hidden.textContent = entries.slice(1, -1)
          .map((entry) => `${entry.ordinal}. ${entry.name}`)
          .join(" · ");
      }
    }

    const single = entries.length === 1;
    host.classList.toggle("single", single);
    rows.forEach((row) => {
      const line = document.createElement("span");
      line.className = "level-summary-line";
      if (row.more) {
        line.classList.add("level-summary-more");
        const mark = document.createElement("span");
        mark.className = "level-summary-mark";
        mark.textContent = "⋯";
        mark.setAttribute("aria-hidden", "true");
        const text = document.createElement("span");
        text.className = "level-summary-name";
        text.textContent = t("levelMore", { n: row.more });
        line.append(mark, text);
      } else {
        if (!single) {
          const badge = document.createElement("span");
          badge.className = "level-summary-badge";
          badge.textContent = String(row.ordinal);
          badge.setAttribute("aria-hidden", "true");
          line.append(badge);
        }
        const text = document.createElement("span");
        text.className = "level-summary-name";
        text.textContent = row.name;
        line.append(text);
      }
      host.append(line);
    });
  }

  function renderHome() {
    if (!$("homeGenMain")) return;
    const subject = subjectLabel(genSubjectValue);
    const book = genSubjectValue === "all" ? "" : textbookLabel(genTextbookId);
    const requirement = normalizeRequirement(genRequirementValue);
    $("homeGenMain").textContent = [
      gradeText(genGradeValue, genSemesterValue),
      subject,
      book,
      requirement || t("genRequirementMissing"),
    ].filter(Boolean).join(" · ");
    $("homeGenSub").textContent = [
      generationDifficultyText(),
      t("targetN", { n: targetCount }),
      genAutoAddValue ? t("genAutoAddShort") : t("genManualAddShort"),
    ].join(" · ");

    const config = currentConfig();
    const pull = Number(config.pullCount || 5);
    const scopeModeLabel = $("homeAnswerScopeMode");
    if (scopeModeLabel) {
      scopeModeLabel.textContent = answerLevels.length
        ? t("levelCountLabel", { n: answerLevels.length })
        : t("levelNoneLabel");
    }
    renderAnswerSummary(answerLevels);
    const scoring = scoringValues(config);
    $("homeAnswerSub").textContent = t("homeAnswerSub", {
      n: pull,
      easy: scoring.easyRewardMinutes,
      medium: scoring.mediumRewardMinutes,
      hard: scoring.hardRewardMinutes,
      unlock: scoring.minUnlockMinutes > 0
        ? t("homeUnlock", { n: scoring.minUnlockMinutes })
        : t("homeUnlockNone"),
    });
    const warning = $("homeAnswerWarning");
    if (warning) {
      const short = Boolean(publicState) &&
        publicState.enabled !== false &&
        publicState.source !== "CLOUD" &&
        scopeLoaded
        ? shortLevels(pull)
        : [];
      warning.textContent = short.length
        ? t("homeLevelsInsufficient", { n: short.length, pull })
        : "";
      warning.classList.toggle("hidden", !short.length);
    }
    updateGenerationScopeStatus();
    renderStock();
  }

  function updateTargetChips() {
    document.querySelectorAll("#targetCounts [data-target]").forEach((chip) => {
      const value = Number(chip.dataset.target);
      chip.textContent = t("targetN", { n: value });
      chip.setAttribute("aria-pressed", String(value === targetCount));
    });
    if ($("generate")) $("generate").textContent = t("generateStartCount", { n: targetCount });
  }

  function renderGenerate() {
    if (!$("generate")) return;
    const adding = genState.phase === "adding";
    const running = genState.phase === "running" || adding;
    const hasBatch = genState.batch.length > 0;
    const done = hasBatch && !running;
    updateTargetChips();
    $("generateIdle").classList.toggle("hidden", running || done);
    $("generateRunning").classList.toggle("hidden", !running);
    $("generateDone").classList.toggle("hidden", !done);
    $("generate").disabled = running;
    const providerBlocked = providerState.aiSupported === false ||
      !providerState.consentAccepted || !providerState.configured;
    if ($("generateSetupHint")) {
      const showHint = providerBlocked && !running && !done;
      $("generateSetupHint").classList.toggle("hidden", !showHint);
      $("generateSetupHint").textContent = !showHint ? "" : providerState.aiSupported === false
        ? t("aiUnsupported")
        : !providerState.consentAccepted
          ? t("err_CONSENT_REQUIRED")
          : t("generateProviderRequired");
    }
    $("cancelGenerate").classList.toggle("hidden", adding);
    $("cancelGenerate").disabled = genState.stopping;
    $("openQuestions").disabled = !hasBatch;
    $("generateProgress").textContent = adding
      ? t("generateAdding")
      : running
        ? t("generateProgress", { done: genState.unique, total: genState.target })
        : "";
    const percent = adding
      ? 100
      : genState.target > 0
        ? Math.min(100, Math.round((genState.unique / genState.target) * 100))
        : 0;
    $("progressFill").style.width = percent + "%";
    if ($("generatedCount")) {
      $("generatedCount").textContent = t("generateDoneShort", { n: genState.batch.length });
    }
    if ($("listCount")) $("listCount").textContent = String(genState.batch.length);
    $("generateError").textContent = genState.errorCode === "REQUIREMENT_INVALID_UNIT"
      ? requirementInvalidUnitText()
      : genState.errorCode ? codeText(genState.errorCode) : "";
    $("generateOk").textContent = genState.noticeKey
      ? t(genState.noticeKey, { n: genState.noticeCount })
      : "";
    updateGenerationScopeStatus();
    renderStock();
  }

  function renderQuestions() {
    if (!$("questionList")) return;
    const items = genState.batch;
    const selectable = items.filter((item) => !item.duplicate).map((item) => item.index);
    for (const index of Array.from(selectedBatch)) {
      if (!items[index] || items[index].duplicate) selectedBatch.delete(index);
    }
    const allSelected = selectable.length > 0 && selectable.every((index) => selectedBatch.has(index));
    $("questionMeta").textContent = t("questionListMeta", {
      total: items.length,
      selected: selectedBatch.size,
    }) + (genState.customTopic ? " · " + genState.customTopic : "");
    $("selectAll").textContent = t(allSelected ? "unselectAll" : "selectAll");
    $("selectAll").disabled = selectable.length === 0;
    $("stickySelected").textContent = t("selectedCount", { n: selectedBatch.size });
    $("addSelected").disabled = selectedBatch.size === 0;
    $("questionEmpty").classList.toggle("hidden", items.length > 0);
    $("questionList").replaceChildren();
    items.forEach((item) => {
      const selected = selectedBatch.has(item.index);
      const card = document.createElement("button");
      card.type = "button";
      card.className = "q-card" + (selected ? " selected" : "") + (item.duplicate ? " disabled" : "");
      card.disabled = Boolean(item.duplicate);
      card.setAttribute("aria-pressed", String(selected));

      const check = document.createElement("span");
      check.className = "q-check";
      check.setAttribute("aria-hidden", "true");
      check.textContent = selected ? "✓" : "";

      const body = document.createElement("span");
      body.className = "q-body";
      const prompt = document.createElement("span");
      prompt.className = "q-prompt";
      prompt.textContent = String(item.prompt || "");
      const options = document.createElement("span");
      options.className = "q-options";
      options.textContent = (Array.isArray(item.options) ? item.options : []).join(" · ");
      const meta = document.createElement("span");
      meta.className = "q-meta";
      const answer = document.createElement("span");
      answer.className = "answer";
      answer.textContent = t("questionAnswer") + ": " + String(item.answerText || "");
      meta.appendChild(answer);
      const difficulty = String(item.difficulty || "").toLowerCase();
      if (difficulty && DIFFICULTY_LABEL_KEYS[difficulty]) {
        const dot = document.createElement("span");
        dot.className = "dot";
        meta.appendChild(dot);
        const label = document.createElement("span");
        label.textContent = t(DIFFICULTY_LABEL_KEYS[difficulty]);
        meta.appendChild(label);
      }
      if (item.duplicate) {
        const badge = document.createElement("span");
        badge.className = "badge";
        badge.textContent = t("inBank");
        meta.appendChild(badge);
      }
      body.append(prompt, options, meta);
      card.append(check, body);
      if (!item.duplicate) {
        card.addEventListener("click", () => {
          if (selectedBatch.has(item.index)) selectedBatch.delete(item.index);
          else selectedBatch.add(item.index);
          renderQuestions();
        });
      }
      $("questionList").appendChild(card);
    });
  }

  function showGenPage(page) {
    if (!GEN_PAGE_IDS[page]) page = "home";
    genPage = page;
    for (const [name, id] of Object.entries(GEN_PAGE_IDS)) {
      const el = $(id);
      if (el) el.classList.toggle("hidden", name !== page);
    }
    document.body.classList.toggle("subpage-open", page !== "home");
    if (page === "questions") renderQuestions();
    if (page === "answer") renderAnswerPage();
    if (page === "level") renderLevelEditor();
    window.scrollTo({ top: 0 });
  }

  function backToGenerateHome() {
    $("questionError").textContent = "";
    showGenPage("home");
    renderHome();
    renderGenerate();
  }

  // Levels save as soon as they are added, edited, reordered or deleted, so
  // leaving the page only has to drop an unfinished editor draft. Rule changes
  // still wait for the Save button.
  function backFromAnswerPage() {
    $("settingsError").textContent = "";
    levelEditingId = null;
    customScope = null;
    resetScopeOptions();
    if (answerDirty) {
      answerDirty = false;
      loadAdminSession().catch(() => {});
    }
    backToGenerateHome();
  }

  function backFromLevelEditor() {
    levelEditingId = null;
    levelNameDraft = "";
    customScope = null;
    answerScopeMode = answerLevels.length ? "custom" : "bound";
    resetScopeOptions();
    showGenPage("answer");
  }

  function openGenConfigPage() {
    $("rangeError").textContent = "";
    if (levelEditorOpen()) {
      levelEditingId = null;
      levelNameDraft = "";
      customScope = null;
      resetScopeOptions();
    }
    syncGenerateComposer(true);
    showGenPage("gen");
  }

  function openAnswerConfigPage() {
    answerTab = "scope";
    levelEditingId = null;
    customScope = null;
    resetScopeOptions();
    $("levelError").textContent = "";
    showGenPage("answer");
  }

  function openQuestionsPage() {
    $("questionError").textContent = "";
    renderQuestions();
    showGenPage("questions");
  }

  async function addSelectedQuestions() {
    if (!genState.jobId || selectedBatch.size === 0) {
      $("questionError").textContent = t("generateNothingSelected");
      return;
    }
    const indexes = Array.from(selectedBatch)
      .filter((index) => genState.batch[index] && !genState.batch[index].duplicate)
      .sort((a, b) => a - b);
    if (!indexes.length) {
      $("questionError").textContent = t("generateNothingSelected");
      return;
    }
    $("questionError").textContent = "";
    $("addSelected").disabled = true;
    try {
      const data = await api("/api/quiz/admin/generate/add", {
        method: "POST",
        admin: true,
        body: { jobId: genState.jobId, indexes },
      });
      applyBankCounts(data.counts);
      selectedBatch.clear();
      genState = idleGeneration();
      genState.noticeKey = "generateAdded";
      genState.noticeCount = Number(data.appended || 0);
      backToGenerateHome();
      refreshStatus();
      loadAdminSession().catch(() => {});
    } catch (error) {
      $("questionError").textContent = errorText(error);
      renderQuestions();
    }
  }

  function renderChipRow(container, values, selected, attr, labelFn, disabledFn) {
    if (!container) return;
    container.replaceChildren();
    values.forEach((value) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "choice-chip";
      button.setAttribute(attr, String(value));
      button.setAttribute("aria-pressed", String(value === selected));
      const disabled = Boolean(disabledFn && disabledFn(value));
      button.disabled = disabled;
      if (disabled) button.setAttribute("aria-disabled", "true");
      button.textContent = labelFn(value);
      container.appendChild(button);
    });
  }

  function configSeconds(config) {
    return Number(config.defaultTimeLimitSec || 60);
  }

  // A saved config may predate the 5-step wheel (the old chips offered 8).
  // Keep that one value selectable until the parent picks a stepped value.
  function roundPickerValues(selected) {
    const value = Number(selected);
    const values = ROUND_VALUES.slice();
    if (Number.isInteger(value) && value >= ROUND_MIN && value <= ROUND_MAX &&
        !values.includes(value)) {
      values.push(value);
      values.sort((a, b) => a - b);
    }
    return values;
  }

  function paintRoundWheel(index) {
    const wheel = $("roundWheel");
    if (!wheel || index < 0 || index >= roundWheelItems.length) return;
    const value = Number(roundWheelItems[index].textContent);
    roundWheelValue = value;
    roundWheelItems.forEach((item, itemIndex) => {
      item.setAttribute("aria-selected", String(itemIndex === index));
    });
    wheel.dataset.index = String(index);
    wheel.setAttribute("aria-valuenow", String(value));
    wheel.setAttribute("aria-valuetext", t("roundN", { n: value }));
    if ($("roundWheelValue")) $("roundWheelValue").textContent = String(value);
  }

  function renderRoundWheel(selected) {
    const wheel = $("roundWheel");
    const track = $("roundWheelTrack");
    if (!wheel || !track) return;
    const value = Number(selected || DEFAULT_CONFIG.pullCount);
    const values = roundPickerValues(value);
    const signature = values.join(",");
    if (track.dataset.values !== signature) {
      track.dataset.values = signature;
      const fragment = document.createDocumentFragment();
      values.forEach((option, index) => {
        const item = document.createElement("div");
        item.className = "round-wheel-item";
        item.setAttribute("role", "option");
        item.dataset.index = String(index);
        item.textContent = String(option);
        fragment.appendChild(item);
      });
      track.replaceChildren(fragment);
    }
    roundWheelItems = Array.from(track.children);
    const index = Math.max(0, values.indexOf(value));
    paintRoundWheel(index);
    const top = index * ROUND_ITEM_HEIGHT;
    if (Math.abs(wheel.scrollTop - top) > 1) {
      wheel.scrollTo({ top, behavior: "auto" });
    }
  }

  function commitRoundWheel(index) {
    const wheel = $("roundWheel");
    if (!wheel || !roundWheelItems.length) return;
    const clamped = Math.min(roundWheelItems.length - 1, Math.max(0, index));
    paintRoundWheel(clamped);
    wheel.scrollTo({ top: clamped * ROUND_ITEM_HEIGHT, behavior: "smooth" });
    const config = currentConfig();
    const pull = Number(config.pullCount || DEFAULT_CONFIG.pullCount);
    if (pull === roundWheelValue) return;
    updateRuleConfig({ pullCount: roundWheelValue });
  }

  function settleRoundWheel() {
    const wheel = $("roundWheel");
    if (!wheel || !roundWheelItems.length) return;
    commitRoundWheel(Math.round(wheel.scrollTop / ROUND_ITEM_HEIGHT));
  }

  // Difficulty scoring values are normalized on every render so an old saved
  // config (or a blank input) can never put the TV outside the policy ranges.
  function scoringNumber(value, fallback, max) {
    const number = Number(value);
    if (!Number.isInteger(number)) return fallback;
    return Math.min(max, Math.max(0, number));
  }

  function scoringValues(config) {
    const source = config || {};
    return {
      easyRewardMinutes: scoringNumber(
        source.easyRewardMinutes, SCORING_DEFAULTS.easyRewardMinutes, 30),
      mediumRewardMinutes: scoringNumber(
        source.mediumRewardMinutes, SCORING_DEFAULTS.mediumRewardMinutes, 30),
      hardRewardMinutes: scoringNumber(
        source.hardRewardMinutes, SCORING_DEFAULTS.hardRewardMinutes, 30),
      easyPenaltyMinutes: scoringNumber(source.easyPenaltyMinutes, 0, 30),
      mediumPenaltyMinutes: scoringNumber(source.mediumPenaltyMinutes, 0, 30),
      hardPenaltyMinutes: scoringNumber(source.hardPenaltyMinutes, 0, 30),
      minUnlockMinutes: scoringNumber(source.minUnlockMinutes, 0, 180),
      maxEarnedMinutes: Math.min(
        180, Math.max(1, Number(source.maxEarnedMinutes) || DEFAULT_CONFIG.maxEarnedMinutes)),
    };
  }

  function readScoringPatch() {
    return {
      easyRewardMinutes: numberField(
        "easyReward", 0, 30, SCORING_DEFAULTS.easyRewardMinutes),
      mediumRewardMinutes: numberField(
        "mediumReward", 0, 30, SCORING_DEFAULTS.mediumRewardMinutes),
      hardRewardMinutes: numberField(
        "hardReward", 0, 30, SCORING_DEFAULTS.hardRewardMinutes),
      easyPenaltyMinutes: numberField("easyPenalty", 0, 30, 0),
      mediumPenaltyMinutes: numberField("mediumPenalty", 0, 30, 0),
      hardPenaltyMinutes: numberField("hardPenalty", 0, 30, 0),
      minUnlockMinutes: numberField("minUnlock", 0, 180, 0),
    };
  }

  function renderScoringInputs(config) {
    if (!$("easyReward")) return;
    const scoring = scoringValues(config);
    $("easyReward").value = String(scoring.easyRewardMinutes);
    $("mediumReward").value = String(scoring.mediumRewardMinutes);
    $("hardReward").value = String(scoring.hardRewardMinutes);
    $("easyPenalty").value = String(scoring.easyPenaltyMinutes);
    $("mediumPenalty").value = String(scoring.mediumPenaltyMinutes);
    $("hardPenalty").value = String(scoring.hardPenaltyMinutes);
    $("minUnlock").value = String(scoring.minUnlockMinutes);
  }

  function renderScoringSummary() {
    if (!$("scoringExample")) return;
    const scoring = scoringValues(currentConfig());
    const gross = scoring.easyRewardMinutes * 3 +
      scoring.mediumRewardMinutes * 2 + scoring.hardRewardMinutes;
    const penalty = scoring.mediumPenaltyMinutes;
    const net = Math.max(0, gross - penalty);
    const capped = Math.min(net, scoring.maxEarnedMinutes);
    let example = penalty > 0
      ? t("scoringExample", { gross, penalty, net: capped })
      : t("scoringExampleNoPenalty", { net: capped });
    if (net > capped) example += " " + t("scoringExampleCapped", { cap: capped });
    $("scoringExample").textContent = example;

    const warning = $("scoringWarning");
    if (!warning) return;
    let text = "";
    if (scoring.easyRewardMinutes + scoring.mediumRewardMinutes +
        scoring.hardRewardMinutes === 0) {
      text = t("scoringWarnNoReward");
    } else if (scoring.minUnlockMinutes > 0 &&
        scoring.minUnlockMinutes > scoring.maxEarnedMinutes) {
      text = t("scoringWarnThreshold", {
        threshold: scoring.minUnlockMinutes,
        cap: scoring.maxEarnedMinutes,
      });
    } else if (
      (scoring.easyRewardMinutes > 0 &&
        scoring.easyPenaltyMinutes >= scoring.easyRewardMinutes) ||
      (scoring.mediumRewardMinutes > 0 &&
        scoring.mediumPenaltyMinutes >= scoring.mediumRewardMinutes) ||
      (scoring.hardRewardMinutes > 0 &&
        scoring.hardPenaltyMinutes >= scoring.hardRewardMinutes)
    ) {
      text = t("scoringWarnPenalty");
    }
    warning.textContent = text;
    warning.classList.toggle("hidden", !text);
  }

  function renderUnlockChips() {
    const scoring = scoringValues(currentConfig());
    const selected = scoring.minUnlockMinutes;
    const values = Array.from(new Set(UNLOCK_VALUES.concat([selected])))
      .sort((a, b) => a - b);
    renderChipRow($("unlockCounts"), values, selected, "data-unlock",
      (value) => value === 0 ? t("unlockNone") : t("minuteN", { n: value }));
  }

  function resetScoringDefaults() {
    $("easyReward").value = String(SCORING_DEFAULTS.easyRewardMinutes);
    $("mediumReward").value = String(SCORING_DEFAULTS.mediumRewardMinutes);
    $("hardReward").value = String(SCORING_DEFAULTS.hardRewardMinutes);
    $("easyPenalty").value = String(SCORING_DEFAULTS.easyPenaltyMinutes);
    $("mediumPenalty").value = String(SCORING_DEFAULTS.mediumPenaltyMinutes);
    $("hardPenalty").value = String(SCORING_DEFAULTS.hardPenaltyMinutes);
    $("minUnlock").value = String(SCORING_DEFAULTS.minUnlockMinutes);
    updateRuleConfig(Object.assign({}, SCORING_DEFAULTS));
  }

  function renderSettingsChrome() {
    if (!$("roundWheel")) return;
    const config = currentConfig();
    const pull = Number(config.pullCount || DEFAULT_CONFIG.pullCount);
    const time = configSeconds(config);
    const timeOptions = Array.from(new Set([30, 45, 60, 90, time].filter((value) => value > 0)))
      .sort((a, b) => a - b);
    renderRoundWheel(pull);
    renderChipRow($("timeCounts"), timeOptions, time, "data-seconds", (value) => t("secondsN", { n: value }));
    renderUnlockChips();
    renderScoringSummary();
    if ($("ruleScope")) {
      $("ruleScope").textContent = answerLevels.length
        ? t("levelCountLabel", { n: answerLevels.length })
        : t("levelNoneLabel");
    }
  }

  function renderSettings() {
    if (!$("maxEarned")) return;
    const config = currentConfig();
    renderScoringInputs(config);
    $("maxEarned").value = config.maxEarnedMinutes || 60;
    $("shuffle").checked = config.shuffleOptions !== false;
    renderSettingsChrome();
    renderSettingsAttention();
  }

  // A round freezes its config when it starts, so a change made while it is on
  // screen only reaches the next round. Say that instead of silently ignoring it.
  function renderSettingsAttention() {
    if (!$("settingsActiveHint")) return;
    const active = publicState && (
      publicState.sessionState === "running" ||
      publicState.sessionState === "settling" ||
      publicState.sessionState === "starting"
    );
    $("settingsActiveHint").classList.toggle("hidden", !active);
  }

  function applyConfig(config) {
    challengeConfig = normalizeConfig(config);
    renderSettings();
    renderHome();
  }

  function renderProvider() {
    if (!$("providerBadge")) return;
    const supported = providerState.aiSupported !== false;
    for (const id of ["baseUrl", "model", "apiKey", "saveProvider", "testProvider"]) {
      const el = $(id);
      if (el) el.disabled = !supported;
    }
    if (!supported) {
      $("providerBadge").textContent = t("aiUnavailableShort");
      $("providerBadge").className = "setting-value";
      $("keyHint").textContent = "";
      return;
    }
    $("providerBadge").textContent = t(providerState.configured ? "aiConfigured" : "aiNotConfigured");
    $("providerBadge").className = "setting-value" + (providerState.configured ? " good" : "");
    $("keyHint").textContent = t(providerState.configured ? "keyHintSaved" : "keyHintNew");
    // Keep the generate card honest when the model key is cleared elsewhere.
    renderGenerate();
  }

  function renderProviderMessages() {
    if (!$("providerError")) return;
    const msg = providerMsg;
    $("providerError").textContent =
      msg && msg.kind === "error" ? (msg.key ? t(msg.key) : codeText(msg.code)) : "";
    $("providerOk").textContent =
      msg && msg.kind === "ok" ? (msg.key ? t(msg.key) : codeText(msg.code)) : "";
  }

  function setProviderError(error) {
    providerMsg = { kind: "error", code: (error && (error.code || error.message)) || "NETWORK" };
    renderProviderMessages();
  }

  function syncErrorText(code) {
    if (!code) return "";
    const key = "syncLast" + code.split("_")
      .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
      .join("");
    const text = t(key);
    return text === key ? "" : text;
  }

  function renderSync() {
    if (!$("syncSummary")) return;
    const pending = Boolean(syncState && (syncState.pendingLocal || syncState.lastError));
    let text = t("syncSynced");
    if (cloudManaged) {
      text = t("syncCloudManaged");
    } else if (syncState && syncState.conflict) {
      text = t("syncConflict");
    } else if (syncState && syncState.pendingLocal) {
      text = syncState.pendingKind === "BINDING"
        ? t("syncPendingBinding")
        : t("syncPendingEdit");
    }
    const detail = syncErrorText(syncState && syncState.lastError);
    if (detail) text += " · " + detail;
    $("syncSummary").textContent = text;
    $("syncRetry").classList.toggle("hidden", !pending);
    $("syncUseRemote").classList.toggle("hidden", !(syncState && syncState.conflict));
  }

  async function refreshSync() {
    try {
      const data = await api("/api/quiz/admin/sync", { admin: true });
      syncState = data.syncState || null;
      cloudManaged = Boolean(data.cloudManaged);
      renderSync();
    } catch (_) { /* keep the last known state */ }
  }

  async function retrySync() {
    try {
      const data = await api("/api/quiz/admin/sync/retry", {
        method: "POST",
        admin: true,
        body: {},
      });
      syncState = data.syncState || syncState;
      renderSync();
      setTimeout(refreshSync, 1500);
    } catch (error) {
      $("settingsError").textContent = errorText(error);
    }
  }

  async function useRemoteConfig() {
    try {
      const data = await api("/api/quiz/admin/sync/use-remote", {
        method: "POST",
        admin: true,
        body: {},
      });
      syncState = data.syncState || syncState;
      renderSync();
      await loadAdminSession();
      setTimeout(refreshSync, 1500);
    } catch (error) {
      $("settingsError").textContent = errorText(error);
    }
  }

  function renderBankCounts() {
    const counts = currentCounts();
    if ($("bankCounts")) {
      $("bankCounts").textContent = t("bankCountsLine", {
        total: counts.total,
        unused: counts.unused,
        answered: counts.answered,
      });
    }
    if ($("maintenanceCounts")) {
      $("maintenanceCounts").textContent = t("bankCountsLine", {
        total: counts.total,
        unused: counts.unused,
        answered: counts.answered,
      });
    }
    if ($("bankInRoundNotice")) {
      $("bankInRoundNotice").classList.toggle("hidden", counts.inRound <= 0);
    }
  }

  function statusChip(item) {
    const chip = document.createElement("span");
    const status = item.status || "unused";
    chip.className = "status-chip " + status;
    chip.textContent = t(STATUS_LABEL_KEYS[status] || "bankStatusUnused");
    return chip;
  }

  function appendSolution(card, item) {
    const text = item && item.explanation ? String(item.explanation).trim() : "";
    if (!text) return;
    const details = document.createElement("details");
    details.className = "solution";
    const summary = document.createElement("summary");
    summary.textContent = t("solution");
    const body = document.createElement("p");
    body.textContent = text;
    details.append(summary, body);
    card.appendChild(details);
  }

  function bankCard(item) {
    const card = document.createElement("article");
    card.className = "bank-card";

    const head = document.createElement("div");
    head.className = "bank-head";
    const prompt = document.createElement("p");
    prompt.className = "prompt";
    prompt.textContent = item.prompt;
    head.append(prompt, statusChip(item));
    card.appendChild(head);

    const options = document.createElement("ol");
    options.className = "bank-options";
    (item.options || []).forEach((option, index) => {
      const li = document.createElement("li");
      li.textContent = option;
      if (index === item.answerIndex) li.className = "correct";
      if (item.selectedIndex !== null && item.selectedIndex !== undefined &&
          index === item.selectedIndex && index !== item.answerIndex) {
        li.classList.add("picked");
      }
      options.appendChild(li);
    });
    card.appendChild(options);
    appendSolution(card, item);

    const meta = document.createElement("div");
    meta.className = "meta";
    if (item.status === "correct" || item.status === "wrong" || item.status === "legacy") {
      let answered = "";
      if (item.selectedIndex !== null && item.selectedIndex !== undefined && item.selectedText) {
        answered = item.selectedText;
      } else if (item.timedOut) {
        answered = t("noAnswer");
      }
      if (answered) {
        const span = document.createElement("span");
        span.textContent = t("yourAnswer", { a: answered });
        meta.appendChild(span);
      }
      if (item.status === "wrong" && Number(item.wrongCount || 0) > 0) {
        const span = document.createElement("span");
        span.textContent = t("wrongCountTimes", { n: Number(item.wrongCount) });
        meta.appendChild(span);
      }
      if (item.answeredAt) {
        const span = document.createElement("span");
        span.textContent = t("answeredOn", { date: formatDate(item.answeredAt) });
        meta.appendChild(span);
      }
    } else if (item.status === "unused" || item.status === "in_round") {
      const span = document.createElement("span");
      span.textContent = t("timeLimit") + ": " + item.timeLimitSec;
      meta.appendChild(span);
    }
    if (meta.childElementCount) card.appendChild(meta);
    return card;
  }

  function renderBankList(list, items, emptyKey, hasMore, loading) {
    if (!list) return;
    list.replaceChildren();
    if (!items.length) {
      const empty = document.createElement("p");
      empty.className = "empty";
      empty.textContent = loading ? "…" : t(emptyKey);
      list.appendChild(empty);
      return;
    }
    items.forEach((item) => list.appendChild(bankCard(item)));
  }

  function renderBank() {
    if (!$("bankList")) return;
    const emptyKey = bankFilter === "unused" ? "bankEmptyUnused"
      : bankFilter === "wrong" ? "bankEmptyWrong" : "bankEmptyAll";
    renderBankList($("bankList"), bankItems, emptyKey, bankHasMore, bankLoading);
    $("bankMore").classList.toggle("hidden", !bankHasMore);
    $("bankMore").disabled = bankLoading;
    document.querySelectorAll("#bankFilters [data-filter]").forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.filter === bankFilter));
    });
  }

  function renderMaintenance() {
    if (!$("maintenanceCounts")) return;
    $("maintenanceCounts").textContent = t("meMaintenanceHint");
  }

  function updateToggleState() {
    if (!$("toggleEnabled")) return;
    const enabled = !publicState || publicState.enabled !== false;
    $("toggleEnabled").checked = enabled;
  }

  async function api(path, options = {}) {
    const headers = {};
    const method = options.method || "GET";
    if (options.body !== undefined || method !== "GET") {
      headers["Content-Type"] = "application/json";
    }
    if (options.admin && adminToken) {
      headers["X-Quiz-Admin"] = "1";
      headers["X-Quiz-Admin-Token"] = adminToken;
    }
    let response;
    try {
      const requestPath = path.startsWith("/api/") ? API_ROOT + path.slice(1) : path;
      response = await fetch(requestPath, {
        method,
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        cache: "no-store",
        credentials: "omit",
        keepalive: Boolean(options.keepalive),
      });
    } catch (_) {
      throw new Error("NETWORK");
    }
    let data = null;
    try { data = await response.json(); } catch (_) { data = null; }
    if (!response.ok) {
      const error = new Error((data && data.error) || ("HTTP_" + response.status));
      error.code = data && data.error ? data.error : "HTTP_" + response.status;
      error.data = data;
      if (error.code === "ADMIN_EXPIRED" || error.code === "ADMIN_REQUIRED") {
        clearAuth();
        showPinGate();
      }
      throw error;
    }
    return data;
  }

  function modalFocusable(dialog) {
    return Array.from(dialog.querySelectorAll(
      'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex]:not([tabindex="-1"])'
    )).filter((el) => el.offsetParent !== null);
  }

  function openModal(id) {
    const dialog = $(id);
    if (!dialog) return;
    if (!activeModal) lastFocused = document.activeElement;
    document.querySelectorAll("#modalRoot .modal").forEach((el) => {
      el.classList.toggle("hidden", el !== dialog);
    });
    $("modalRoot").classList.remove("hidden");
    document.body.classList.add("modal-open");
    activeModal = id;
    const target = modalFocusable(dialog)[0];
    if (target) {
      setTimeout(() => {
        if (activeModal === id) target.focus();
      }, 30);
    }
  }

  function closeModal() {
    if (!activeModal) return;
    document.querySelectorAll("#modalRoot .modal").forEach((el) => el.classList.add("hidden"));
    $("modalRoot").classList.add("hidden");
    document.body.classList.remove("modal-open");
    activeModal = null;
    const target = lastFocused;
    lastFocused = null;
    if (target && document.body.contains(target) && target.offsetParent !== null) {
      target.focus();
    } else if (!$("bottomNav").classList.contains("hidden")) {
      const nav = document.querySelector('.nav-item[data-tab="' + activeTab + '"]');
      if (nav) nav.focus();
    }
  }

  function modalKeydown(event) {
    if (!activeModal) return;
    const dialog = $(activeModal);
    if (event.key === "Escape" && dialog.dataset.staticModal !== "true") {
      event.preventDefault();
      closeModal();
      return;
    }
    if (event.key !== "Tab") return;
    const items = modalFocusable(dialog);
    if (!items.length) {
      event.preventDefault();
      return;
    }
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && (document.activeElement === first || !dialog.contains(document.activeElement))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (document.activeElement === last || !dialog.contains(document.activeElement))) {
      event.preventDefault();
      first.focus();
    }
  }

  function setGate(titleKey, textKey, showPin) {
    $("gateCard").classList.remove("hidden");
    $("gateTitle").textContent = t(titleKey);
    $("gateText").textContent = t(textKey);
    $("pinForm").classList.toggle("hidden", !showPin);
    if (showPin) setTimeout(() => $("pin").focus(), 50);
  }

  function resetSessionState() {
    generationToken++;
    clearGenTimer();
    adminSession = null;
    curriculumModel = null;
    unitCatalogModel = null;
    challengeConfig = null;
    syncState = null;
    cloudManaged = false;
    bankCounts = null;
    bankCapacity = 6000;
    bankItems = [];
    bankFilter = "all";
    bankOffset = 0;
    bankHasMore = false;
    bankLoading = false;
    bankRequest++;
    providerState = { aiSupported: true, consentAccepted: false, configured: false, baseUrl: "", model: "" };
    providerMsg = null;
    pendingGenerate = false;
    pendingEnter = false;
    genState = idleGeneration();
    targetCount = 20;
    answerLevels = [];
    levelEditingId = null;
    levelNameDraft = "";
    levelSaving = false;
    levelUndo = null;
    if (levelUndoTimer) {
      clearTimeout(levelUndoTimer);
      levelUndoTimer = 0;
    }
    if (levelMessageTimer) {
      clearTimeout(levelMessageTimer);
      levelMessageTimer = 0;
    }
    answerScope = null;
    answerScopeMode = "bound";
    answerScopeAvailable = 0;
    scopeLoaded = false;
    answerDirty = false;
    customScope = null;
    resetScopeOptions();
    selectedBatch = new Set();
    answerTab = "scope";
    roundWheelValue = DEFAULT_CONFIG.pullCount;
    if (roundWheelScrollTimer) {
      clearTimeout(roundWheelScrollTimer);
      roundWheelScrollTimer = 0;
    }
    genRequirementValue = "";
    genOptionsValue = 4;
    genAutoAddValue = true;
    genScopeValue = "review";
    genUnitValue = 1;
    if (genPrefsSaveTimer) {
      clearTimeout(genPrefsSaveTimer);
      genPrefsSaveTimer = null;
    }
    $("apiKey").value = "";
    $("consentError").textContent = "";
    $("rangeError").textContent = "";
    $("settingsError").textContent = "";
    $("settingsState").textContent = "";
    $("maintenanceError").textContent = "";
    $("maintenanceState").textContent = "";
    $("bankError").textContent = "";
    $("generateError").textContent = "";
    $("generateOk").textContent = "";
    $("generateProgress").textContent = "";
    $("questionError").textContent = "";
    showGenPage("home");
    renderSettings();
    renderGenerate();
    renderHome();
  }

  function showPinGate() {
    if (activeModal) closeModal();
    resetSessionState();
    document.body.classList.remove("editor-open");
    $("editor").classList.add("hidden");
    $("bottomNav").classList.add("hidden");
    gateShown = true;
    setGate("gate_pair_title", "gate_pair_text", true);
    renderGateOccupation();
  }

  function showEditor() {
    gateShown = false;
    gateOccupied = false;
    document.body.classList.add("editor-open");
    $("gateCard").classList.add("hidden");
    $("editor").classList.remove("hidden");
    $("bottomNav").classList.remove("hidden");
    setTab(activeTab, true);
  }

  // A second phone sees the same PIN gate. When the first phone still owns
  // the session, replace the form with a calm notice; the 5-second status
  // poll reveals the form again as soon as that phone logs out.
  function renderGateOccupation() {
    if (!gateShown) return;
    const inUse = !adminToken && Boolean(publicState && publicState.parentInUse);
    $("gateTitle").textContent = t(inUse ? "gate_in_use_heading" : "gate_pair_title");
    $("gateText").textContent = t(inUse ? "gate_in_use_lead" : "gate_pair_text");
    if (inUse) {
      $("gateNoticeTitle").textContent = t("gate_in_use_title");
      $("gateNoticeText").textContent = t("gate_in_use_text");
      $("gateError").textContent = "";
      $("pin").value = "";
    } else {
      $("gateNoticeTitle").textContent = "";
      $("gateNoticeText").textContent = "";
    }
    $("gateNotice").classList.toggle("hidden", !inUse);
    $("pinForm").classList.toggle("hidden", inUse);
    const released = gateOccupied && !inUse;
    gateOccupied = inUse;
    if (released) {
      setTimeout(() => {
        const active = document.activeElement;
        const busy = active && ["INPUT", "SELECT", "TEXTAREA", "BUTTON"].includes(active.tagName);
        if (!busy) $("pin").focus();
      }, 50);
    }
  }

  function setTab(tab, force) {
    if (!TAB_VIEWS[tab]) tab = "generate";
    if (!force && activeTab === tab) return;
    if (!force) showGenPage("home");
    activeTab = tab;
    for (const name of Object.keys(TAB_VIEWS)) {
      $(TAB_VIEWS[name]).classList.toggle("hidden", name !== tab);
    }
    document.querySelectorAll(".nav-item[data-tab]").forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.tab === tab));
    });
    if (tab === "bank") {
      loadBank(true);
    } else if (tab === "me") {
      refreshSync();
    }
    window.scrollTo({ top: 0 });
  }

  async function loadUnitCatalog() {
    try {
      const data = await api("/api/quiz/admin/units", { admin: true });
      unitCatalogModel = normalizeUnitCatalog(data && data.catalog);
    } catch (_) {
      // Older TV builds do not expose the endpoint; numeric labels still work.
      unitCatalogModel = null;
    }
  }

  async function loadAdminSession() {
    const [data] = await Promise.all([
      api("/api/quiz/admin/session", { admin: true }),
      loadUnitCatalog(),
    ]);
    adminSession = data.session || null;
    applyCurriculum(data.curriculum, data.cloudJuniorReady);
    // Re-resolve a saved requirement now that unit titles are available; a
    // title without "第N单元" could not map before this point.
    applyRequirementScope(genRequirementValue, false);
    applyConfig(data.challengeConfig || DEFAULT_CONFIG);
    const scopeBusy = settingsSaving || answerDirty || levelSaving ||
      Boolean(levelEditingId);
    if (!Array.isArray(data.answerScopes) &&
        !Object.prototype.hasOwnProperty.call(data, "answerScope")) {
      // 契约破损时保留本地范围并显式报错，不把「缺字段」当成「没有关卡」。
      console.error("[quiz] admin session is missing answerScopes/answerScope");
      $("settingsError").textContent = t("err_INVALID_SCOPE");
    } else if (!scopeLoaded || !scopeBusy) {
      if (Array.isArray(data.answerScopes)) {
        applyAnswerLevels(data.answerScopes);
      } else {
        answerScopeAvailable = Number(data.answerScopeAvailable || 0);
        applyAnswerScope(data.answerScope, data.answerScopeMode);
      }
    }
    syncState = data.syncState || null;
    cloudManaged = Boolean(data.cloudManaged);
    bankCapacity = Number(data.bankCapacity || 6000);
    applyBankCounts(data.bank);
    renderSync();
    renderStock();
    renderBankCounts();
    renderMaintenance();
    renderGenConfig(false);
    renderGenerate();
  }

  async function loadProvider() {
    const data = await api("/api/quiz/admin/provider", { admin: true });
    providerState = {
      aiSupported: data.aiSupported !== false,
      consentAccepted: Boolean(data.consentAccepted),
      configured: Boolean(data.configured),
      baseUrl: data.baseUrl || "",
      model: data.model || "",
    };
    $("baseUrl").value = providerState.baseUrl;
    $("model").value = providerState.model;
    $("apiKey").value = "";
    providerMsg = null;
    renderProvider();
    renderProviderMessages();
  }

  function providerPayload(includeKey) {
    const payload = {
      baseUrl: $("baseUrl").value.trim(),
      model: $("model").value.trim(),
    };
    const key = $("apiKey").value.trim();
    if (includeKey && key) payload.apiKey = key;
    return payload;
  }

  function openProviderDialog(pending) {
    pendingGenerate = Boolean(pending);
    providerMsg = null;
    $("apiKey").value = "";
    renderProviderMessages();
    renderProvider();
    openModal("providerDialog");
  }

  async function saveProvider() {
    providerMsg = null;
    renderProviderMessages();
    if (providerState.aiSupported === false) {
      providerMsg = { kind: "error", key: "err_AI_UNSUPPORTED" };
      renderProviderMessages();
      return;
    }
    if (!providerState.consentAccepted) {
      providerMsg = { kind: "error", key: "err_CONSENT_REQUIRED" };
      renderProviderMessages();
      return;
    }
    if (!$("apiKey").value.trim() && !providerState.configured) {
      providerMsg = { kind: "error", key: "err_PROVIDER_KEY_REQUIRED" };
      renderProviderMessages();
      return;
    }
    $("saveProvider").disabled = true;
    try {
      const data = await api("/api/quiz/admin/provider", {
        method: "POST",
        admin: true,
        body: providerPayload(true),
      });
      providerState = {
        aiSupported: data.aiSupported !== false,
        consentAccepted: Boolean(data.consentAccepted),
        configured: Boolean(data.configured),
        baseUrl: data.baseUrl || "",
        model: data.model || "",
      };
      $("apiKey").value = "";
      providerMsg = { kind: "ok", key: "providerSaved" };
      renderProvider();
      renderProviderMessages();
      const shouldGenerate = pendingGenerate;
      pendingGenerate = false;
      closeModal();
      if (shouldGenerate) startGeneration();
    } catch (error) {
      setProviderError(error);
    } finally {
      $("saveProvider").disabled = providerState.aiSupported === false;
    }
  }

  async function testProviderConnection() {
    providerMsg = null;
    renderProviderMessages();
    if (providerState.aiSupported === false) {
      providerMsg = { kind: "error", key: "err_AI_UNSUPPORTED" };
      renderProviderMessages();
      return;
    }
    if (!providerState.consentAccepted) {
      providerMsg = { kind: "error", key: "err_CONSENT_REQUIRED" };
      renderProviderMessages();
      return;
    }
    if (!$("apiKey").value.trim() && !providerState.configured) {
      providerMsg = { kind: "error", key: "err_PROVIDER_KEY_REQUIRED" };
      renderProviderMessages();
      return;
    }
    $("testProvider").disabled = true;
    try {
      await api("/api/quiz/admin/provider/test", {
        method: "POST",
        admin: true,
        body: providerPayload(true),
      });
      providerMsg = { kind: "ok", key: "providerTestOk" };
      renderProviderMessages();
    } catch (error) {
      setProviderError(error);
    } finally {
      $("testProvider").disabled = providerState.aiSupported === false;
    }
  }

  async function acceptConsent() {
    $("consentError").textContent = "";
    $("acceptConsent").disabled = true;
    try {
      await api("/api/quiz/admin/consent", {
        method: "POST",
        admin: true,
        body: { accepted: true },
      });
      providerState.consentAccepted = true;
      closeModal();
      renderProvider();
      if (pendingEnter) {
        pendingEnter = false;
        showEditor();
        setTab("generate", true);
        return;
      }
      // Accepting privacy does not force AI setup. Only a pending "generate"
      // action continues into the provider dialog or the run itself.
      if (pendingGenerate) {
        if (providerState.aiSupported !== false && !providerState.configured) {
          openProviderDialog(true);
        } else {
          pendingGenerate = false;
          startGeneration();
        }
      }
    } catch (error) {
      $("consentError").textContent = errorText(error);
    } finally {
      $("acceptConsent").disabled = false;
    }
  }

  function openConsent() {
    $("consentError").textContent = "";
    $("acceptConsent").disabled = false;
    $("declineConsent").disabled = false;
    openModal("consentDialog");
  }

  function clearGenTimer() {
    if (genTimer) clearTimeout(genTimer);
    genTimer = null;
  }

  // ---- 出题配置（年级 + 上下册是同一组，教材随科目变化）----

  function generationGradeCode() {
    return String(genGradeValue) + (genSemesterValue === 2 ? "b" : "a");
  }

  function applyGenerationGrade(code) {
    const match = /^([1-9])([ab])?$/.exec(String(code || ""));
    if (!match) return false;
    const grade = Number(match[1]);
    if (!gradeNos().includes(grade)) return false;
    genGradeValue = grade;
    genSemesterValue = match[2] === "b" ? 2 : 1;
    return true;
  }

  function escapeRegExp(value) {
    return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }

  function chineseNumber(value) {
    const digits = {
      "一": 1, "二": 2, "三": 3, "四": 4, "五": 5,
      "六": 6, "七": 7, "八": 8, "九": 9,
    };
    if (value === "十") return 10;
    if (value.startsWith("十")) return 10 + (digits[value.slice(1)] || 0);
    if (value.includes("十")) {
      const parts = value.split("十");
      return (digits[parts[0]] || 1) * 10 + (digits[parts[1]] || 0);
    }
    return digits[value] ?? Number(value);
  }

  // 自定义主题只影响出题提示词与清单展示；答题范围仍按规范六字段归类。
  function normalizeCustomTopic(value) {
    return String(value || "")
      .replace(/[\u0000-\u001f\u007f]/g, " ")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 30);
  }

  function normalizeRequirement(value) {
    return normalizeCustomTopic(value);
  }

  // Every unit in the current book is listed so the parent can see the real
  // range instead of guessing a number the textbook does not have.
  function requirementSamples() {
    const units = [];
    const book = generationUnitBook();
    if (book) {
      book.units
        .filter((unit) => unit.kind === "UNIT")
        .forEach((unit) => {
          units.push({
            label: unit.title.length <= REQUIREMENT_MAX_CHARS
              ? unit.title
              : t("unitN", { n: unit.no }),
            title: unit.title,
            scope: "unit",
            unit: unit.no,
          });
        });
    }
    if (!units.length) {
      const max = generationUnitMax();
      for (let unit = 1; unit <= max; unit += 1) {
        units.push({ label: t("unitN", { n: unit }), scope: "unit", unit });
      }
    }
    return units.concat([
      { label: t(SCOPE_LABEL_KEYS.midterm), scope: "midterm" },
      { label: t(SCOPE_LABEL_KEYS.final), scope: "final" },
      { label: t(SCOPE_LABEL_KEYS.review), scope: "review" },
    ]);
  }

  function generationUnitNumbers() {
    const book = generationUnitBook();
    if (book) {
      return book.units
        .filter((unit) => unit.kind === "UNIT")
        .map((unit) => unit.no);
    }
    return Array.from({ length: generationUnitMax() }, (_, index) => index + 1);
  }

  function generationUnitCount() {
    return generationUnitNumbers().length;
  }

  // A manual "第十单元" must not silently keep the previous bank category.
  // Ordinals can be sparse (chemistry keeps the raw tree position), so check
  // the actual UNIT list rather than comparing against its length.
  function outOfRangeRequirementUnit(value) {
    const text = normalizeRequirement(value);
    if (!text) return null;
    const unit = explicitUnitNumber(text);
    if (unit === null) return null;
    if (canonicalFromRequirement(text)) return null;
    return generationUnitNumbers().includes(unit) ? null : unit;
  }

  function requirementInvalidUnitText() {
    const unit = outOfRangeRequirementUnit(genRequirementValue);
    if (unit === null) return "";
    return t("genRequirementInvalid", {
      unit: t("unitN", { n: unit }),
      count: generationUnitCount(),
    });
  }

  function unitAliasPattern() {
    const aliases = curriculum().unitAliases;
    const values = (aliases.length ? aliases : ["单元", "章", "Unit", "Chapter"])
      .map(escapeRegExp);
    return "(?:" + values.join("|") + ")";
  }

  function canonicalUnit(value) {
    const text = String(value || "");
    // With a known book, an explicit "第N单元/章" that the catalogue could not
    // resolve must not fall back to the raw tree ordinal: in geography and
    // chemistry those two numbers differ.
    if (generationUnitBook() && explicitUnitNumber(text) !== null) return null;
    const alias = unitAliasPattern();
    const before = text.match(
      new RegExp("第?\\s*([一二三四五六七八九十]+|\\d+)\\s*" + alias, "i"));
    const after = text.match(
      new RegExp("(?:^|\\b)" + alias + "\\s*([0-9]+)\\b", "i"));
    const match = before || after;
    if (!match) return null;
    const unit = unitNumberToken(match[1]);
    return Number.isInteger(unit) && unit >= 1 && unit <= generationUnitMax() ? unit : null;
  }

  // True when the free text only names the resolved catalogue unit ("第七章",
  // "Unit 7", a full title or a lesson), without extra instructions. Such text
  // should keep the lesson coverage in the prompt instead of falling back to
  // the manual-text branch just because it is shorter than the full title.
  function requirementOnlyNamesUnit(value, unit) {
    if (!unit) return false;
    const text = normalizeRequirement(value);
    if (!text) return false;
    const key = unitMatchKey(text);
    if (!key) return false;
    if (key === unitMatchKey(unit.title)) return true;
    if (unit.lessons.some((lesson) => unitMatchKey(lesson) === key)) return true;
    if (explicitUnitNumber(text) === null) return false;
    const stripped = text
      .replace(/第\s*[一二三四五六七八九十百零〇两\d]+\s*(?:单元|章|课)/g, "")
      .replace(/(?:^|\b)unit\s*\d+\b/gi, "");
    return unitMatchKey(stripped) === "";
  }

  // 只识别明确的规范标记；识别不到时保留当前归类，避免把“易错题”误判。
  function canonicalFromRequirement(value) {
    const text = normalizeRequirement(value);
    if (!text) return null;
    const sample = requirementSamples().find((item) => item.label === text);
    if (sample) {
      return sample.scope === "unit"
        ? { scope: "unit", unit: sample.unit }
        : { scope: sample.scope };
    }
    const catalogUnit = catalogUnitFromText(
      text, genGradeValue, genSemesterValue, genSubjectValue, genTextbookId);
    if (catalogUnit) return { scope: "unit", unit: catalogUnit.no };
    const unit = canonicalUnit(text);
    if (unit) return { scope: "unit", unit };
    if (/期中|midterm|mid-term/i.test(text)) return { scope: "midterm" };
    if (/期末|final/i.test(text)) return { scope: "final" };
    if (/总复习|全册|全学期|复习|review|revision/i.test(text)) return { scope: "review" };
    return null;
  }

  function setGenerationScope(parsed) {
    if (!parsed || !SCOPE_VALUES.includes(parsed.scope)) return;
    genScopeValue = parsed.scope;
    if (parsed.scope === "unit") {
      const unit = Number(parsed.unit);
      if (Number.isInteger(unit) && unit >= 1 && unit <= generationUnitMax()) {
        genUnitValue = unit;
      }
    }
  }

  function applyRequirementScope(value, resetWhenEmpty) {
    const text = normalizeRequirement(value);
    const parsed = canonicalFromRequirement(text);
    if (parsed) {
      setGenerationScope(parsed);
    } else if (resetWhenEmpty && !text) {
      genScopeValue = "review";
      genUnitValue = 1;
    }
    return parsed;
  }

  function legacyRequirementLabel(scope, unit) {
    if (scope === "unit") {
      return generationUnitTitle(unit) || t("unitN", { n: unit });
    }
    return t(SCOPE_LABEL_KEYS[scope] || "scopeReview");
  }

  function applyGenerationPrefs(prefs) {
    if (!prefs || typeof prefs !== "object") return;
    const version = Number(prefs.presetVersion) || 0;
    const customTopic = normalizeRequirement(prefs.customTopic);
    // 版本 3 起不再有默认值。旧版默认“第三单元”（无版本号）和
    // v2 默认“期中必考”且未手动改过文字的偏好，迁移为未填写。
    const legacyDefaultUnit = version < 2 && !customTopic &&
      prefs.scope === "unit" && Number(prefs.unit) === 3;
    const legacyDefaultMidterm = version === 2 && !customTopic && prefs.scope === "midterm";
    const legacyDefault = legacyDefaultUnit || legacyDefaultMidterm;
    applyGenerationGrade(prefs.grade);
    const subjects = subjectIdsForGrade(genGradeValue);
    if (subjects.includes(prefs.subject)) {
      genSubjectValue = prefs.subject;
    } else if (!subjects.includes(genSubjectValue)) {
      genSubjectValue = subjects.includes("math") ? "math" : (subjects[0] || "math");
    }
    if (legacyDefault) {
      genScopeValue = "review";
      genUnitValue = 1;
    } else if (SCOPE_VALUES.includes(prefs.scope)) {
      genScopeValue = prefs.scope;
    }
    if (isKnownTextbook(prefs.textbook)) {
      genTextbookId = String(prefs.textbook);
    }
    if (genSubjectValue === "all") genTextbookId = "general";
    keepGenerationTextbook();
    const unit = Number(prefs.unit);
    if (!legacyDefault && Number.isInteger(unit) && unit >= 1 &&
        (genScopeValue !== "unit" || unit <= generationUnitMax())) {
      genUnitValue = unit;
    }
    const target = Number(prefs.targetCount);
    if (TARGET_COUNTS.includes(target)) targetCount = target;
    const options = Number(prefs.options);
    if (GEN_OPTIONS_VALUES.includes(options)) genOptionsValue = options;
    if (GEN_DIFFICULTY_VALUES.includes(prefs.difficulty)) {
      genDifficultyValue = prefs.difficulty;
    }
    if (version >= 3 || customTopic) {
      genRequirementValue = customTopic;
    } else if (legacyDefault) {
      genRequirementValue = "";
    } else {
      genRequirementValue = legacyRequirementLabel(genScopeValue, genUnitValue);
    }
    applyRequirementScope(genRequirementValue, true);
  }

  function loadGeneratePrefs() {
    let prefs = null;
    try {
      prefs = JSON.parse(localStorage.getItem(GEN_PREFS_KEY) || "null");
    } catch (_) { /* private mode */ }
    applyGenerationPrefs(prefs);
  }

  function readGenerationPrefs() {
    const textbook = genSubjectValue === "all" ? "general" : genTextbookId;
    const prefs = {
      grade: generationGradeCode(),
      subject: genSubjectValue,
      textbook,
      scope: genScopeValue,
      unit: genUnitValue,
      targetCount,
      options: genOptionsValue,
      difficulty: genDifficultyValue,
      // 版本 3 起这个字段就是完整的题目要求文本，空字符串表示未填写。
      customTopic: normalizeRequirement(genRequirementValue),
      autoAdd: genAutoAddValue,
      presetVersion: 3,
    };
    const subjects = subjectIdsForGrade(genGradeValue);
    const books = textbooksFor(genGradeValue, genSubjectValue);
    const valid = gradeCodes().includes(prefs.grade) &&
      subjects.includes(prefs.subject) &&
      books.includes(prefs.textbook) &&
      SCOPE_VALUES.includes(prefs.scope) &&
      Number.isInteger(prefs.unit) && prefs.unit >= 1 &&
      (prefs.scope !== "unit" || prefs.unit <= generationUnitMax()) &&
      TARGET_COUNTS.includes(prefs.targetCount) &&
      GEN_OPTIONS_VALUES.includes(prefs.options) &&
      GEN_DIFFICULTY_VALUES.includes(prefs.difficulty) &&
      typeof prefs.customTopic === "string" &&
      prefs.customTopic.length <= REQUIREMENT_MAX_CHARS &&
      outOfRangeRequirementUnit(prefs.customTopic) === null &&
      typeof prefs.autoAdd === "boolean" &&
      prefs.presetVersion === 3 &&
      !cloudJuniorBlocked(genGradeValue);
    return valid ? prefs : null;
  }

  async function postGenerationPrefs(prefs) {
    if (!adminToken) return null;
    const data = await api("/api/quiz/admin/generate/prefs", {
      method: "POST",
      admin: true,
      body: prefs,
    });
    if (data && data.syncState) {
      syncState = data.syncState;
      renderSync();
    }
    return data;
  }

  function saveGeneratePrefs() {
    const prefs = readGenerationPrefs();
    if (!prefs) return null;
    try {
      localStorage.setItem(GEN_PREFS_KEY, JSON.stringify(prefs));
    } catch (_) { /* private mode */ }
    if (genPrefsSaveTimer) clearTimeout(genPrefsSaveTimer);
    genPrefsSaveTimer = setTimeout(() => {
      genPrefsSaveTimer = null;
      postGenerationPrefs(prefs).catch(() => {
        // localStorage still remembers the choice for this phone.
      });
    }, 800);
    return prefs;
  }

  async function saveGeneratePrefsNow() {
    const prefs = readGenerationPrefs();
    if (!prefs) return null;
    try {
      localStorage.setItem(GEN_PREFS_KEY, JSON.stringify(prefs));
    } catch (_) { /* private mode */ }
    if (genPrefsSaveTimer) {
      clearTimeout(genPrefsSaveTimer);
      genPrefsSaveTimer = null;
    }
    return postGenerationPrefs(prefs);
  }

  async function loadGenerationPrefsFromTv() {
    try {
      const data = await api("/api/quiz/admin/generate/prefs", { admin: true });
      if (!data || !data.prefs || typeof data.prefs !== "object") return;
      applyGenerationPrefs(data.prefs);
      genPromptDirty = false;
      syncGenerateComposer(true);
      const normalized = readGenerationPrefs();
      if (normalized) {
        try {
          localStorage.setItem(GEN_PREFS_KEY, JSON.stringify(normalized));
        } catch (_) { /* private mode */ }
        // 旧偏好迁移到版本 3：旧默认“期中必考”会被清空，其他选择保留。
        if (data.prefs.scope && Number(data.prefs.presetVersion) !== 3) {
          postGenerationPrefs(normalized).catch(() => {});
        }
      }
    } catch (_) {
      // Older TV builds have no prefs endpoint; local memory still applies.
    }
  }

  function generationTextbookBooks() {
    const books = textbooksFor(genGradeValue, genSubjectValue);
    return books.length ? books : ["general"];
  }

  function keepGenerationSubject() {
    const subjects = subjectIdsForGrade(genGradeValue);
    if (!subjects.includes(genSubjectValue)) {
      genSubjectValue = subjects.includes("math") ? "math" : (subjects[0] || "math");
    }
  }

  function keepGenerationTextbook() {
    const books = generationTextbookBooks();
    if (!books.includes(genTextbookId)) {
      genTextbookId = defaultTextbookFor(genGradeValue, genSubjectValue) || books[0] || "general";
    }
  }

  function renderGenerationSubjects() {
    const container = $("genSubjects");
    if (!container) return;
    container.replaceChildren();
    subjectIdsForGrade(genGradeValue).forEach((id) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "choice-chip";
      button.dataset.subject = id;
      button.setAttribute("aria-pressed", String(id === genSubjectValue));
      button.textContent = subjectLabel(id);
      container.appendChild(button);
    });
  }

  function renderRequirementSamples() {
    const container = $("genRequirementSamples");
    if (!container) return;
    const current = normalizeRequirement(genRequirementValue);
    container.replaceChildren();
    requirementSamples().forEach((sample) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "choice-chip";
      button.dataset.requirement = sample.label;
      button.setAttribute("aria-pressed", String(sample.label === current));
      if (sample.title) button.title = sample.title;
      button.textContent = sample.label;
      container.appendChild(button);
    });
  }

  function renderRequirementState() {
    const input = $("genRequirement");
    if (input && document.activeElement !== input) input.value = genRequirementValue;
    renderRequirementSamples();
    const hint = $("genRequirementScope");
    if (hint) {
      const text = normalizeRequirement(genRequirementValue);
      const invalidUnit = outOfRangeRequirementUnit(text);
      hint.classList.toggle("error", invalidUnit !== null);
      if (invalidUnit !== null) {
        hint.textContent = requirementInvalidUnitText();
      } else {
        hint.textContent = text
          ? t("genRequirementScopeWithCount", {
            scope: generationTopicText(),
            count: generationUnitCount(),
          })
          : "";
      }
    }
  }

  function setRequirement(value, options = {}) {
    genRequirementValue = normalizeRequirement(value);
    applyRequirementScope(genRequirementValue, true);
    // 样例点击可能发生在输入框仍聚焦时，必须强制回写；逐字输入用 keepInput。
    const input = $("genRequirement");
    if (input && options.keepInput !== true) input.value = genRequirementValue;
    renderRequirementState();
    if (options.recompose !== false && !genPromptDirty) {
      $("genPrompt").value = composeGenerationPrompt();
    }
    updatePromptBadge();
    renderHome();
    if (options.persist !== false) saveGeneratePrefs();
  }

  function renderGenConfig(recompose) {
    if (!$("genGradeChips")) return;
    // A stale junior preference on a cloud-managed TV cannot be used; fall back
    // to the first selectable grade instead of leaving an unsavable state.
    if (cloudJuniorBlocked(genGradeValue)) {
      const allowed = gradeNos().filter((no) => !cloudJuniorBlocked(no));
      genGradeValue = allowed.includes(4) ? 4 : (allowed[0] || genGradeValue);
      genSemesterValue = 1;
      genUnitValue = 1;
    }
    keepGenerationSubject();
    keepGenerationTextbook();

    renderChipRow($("genGradeChips"), gradeNos(), genGradeValue, "data-grade",
      (value) => gradeLabel(value),
      (value) => cloudJuniorBlocked(value));
    const gradeHint = $("genGradeHint");
    if (gradeHint) {
      const blocked = cloudJuniorBlocked(genGradeValue);
      gradeHint.textContent = blocked ? t("cloudJuniorUnsupported") : "";
      gradeHint.classList.toggle("hidden", !blocked);
    }
    renderChipRow($("genSemester"), [1, 2], genSemesterValue, "data-semester",
      (value) => t(Number(value) === 2 ? "semesterSecond" : "semesterFirst"));

    renderGenerationSubjects();
    // All-subject generation forces textbook=ALL on the wire, so a textbook
    // picker would be misleading; the field (and its curation hint) is hidden.
    const textbookField = $("genTextbookField");
    if (textbookField) {
      textbookField.classList.toggle("hidden", genSubjectValue === SUBJECT_ALL);
    }
    const books = generationTextbookBooks();
    renderChipRow($("genTextbook"), books, genTextbookId, "data-textbook",
      (id) => textbookLabel(id));
    if (genUnitValue > generationUnitMax()) {
      genUnitValue = generationUnitMax();
    }
    if (genUnitValue < 1) genUnitValue = 1;

    renderRequirementState();
    if ($("genAutoAdd")) $("genAutoAdd").checked = genAutoAddValue;

    renderChipRow($("genDifficultyChips"), GEN_DIFFICULTY_VALUES, genDifficultyValue,
      "data-difficulty", (value) => t(value
        ? (DIFFICULTY_LABEL_KEYS[value] || "difficultyMedium")
        : "difficultyUnlimited"));

    updateTargetChips();
    renderChipRow($("genOptionsChips"), GEN_OPTIONS_VALUES, genOptionsValue, "data-options",
      (value) => String(value));
    if (recompose && !genPromptDirty) {
      $("genPrompt").value = composeGenerationPrompt();
    }
    updatePromptBadge();
    $("genAdvancedSummary").textContent = t("genSummary", {
      n: targetCount,
      o: genOptionsValue,
      d: generationDifficultyText(),
    });
    renderHome();
  }

  // 旧名字保留：缓存页面和登录流程都按这个名字调用编排刷新。
  function syncGenerateComposer(recompose) {
    renderGenConfig(recompose);
  }

  // ---- 答题范围（六字段；难度属于范围）----

  function normalizeAnswerScope(raw) {
    if (!raw || typeof raw !== "object") return null;
    const grade = Number(raw.grade);
    const semester = Number(raw.semester);
    const subject = String(raw.subject || "").toLowerCase();
    // 全科没有单一教材：教材维度跟随通配，避免显示成某一科的教材。
    const textbook = subject === SUBJECT_ALL
      ? TEXTBOOK_ALL
      : String(raw.textbook || "").toLowerCase();
    const topic = raw.topic
      ? String(raw.topic).toLowerCase()
      : SCOPE_TOPIC_FROM_CANONICAL[String(raw.scopeType || "").toUpperCase()];
    const difficulty = String(raw.difficulty || "").toUpperCase();
    const unitNo = raw.unitNo === null || raw.unitNo === undefined ? null : Number(raw.unitNo);
    if (!Number.isInteger(grade) || !gradeNos().includes(grade)) return null;
    if (!Number.isInteger(semester) || semester < 1 || semester > 2) return null;
    if (!subjectIdsForGrade(grade).includes(subject)) return null;
    if (!isKnownTextbook(textbook)) return null;
    if (!SCOPE_TOPIC_VALUES.includes(topic)) return null;
    if (!SCOPE_DIFFICULTY_VALUES.includes(difficulty)) return null;
    if (topic === "unit") {
      if (!Number.isInteger(unitNo) || unitNo < 1 ||
          unitNo > unitMaxForContext(grade, semester, subject, textbook)) {
        return null;
      }
    } else if (unitNo !== null) {
      return null;
    }
    return { grade, semester, subject, textbook, topic, unitNo, difficulty };
  }

  function scopeToCanonical(scope) {
    return {
      grade: scope.grade,
      semester: scope.semester,
      subject: scope.subject.toUpperCase(),
      textbook: scope.textbook.toUpperCase(),
      scopeType: SCOPE_TOPIC_TO_CANONICAL[scope.topic],
      unitNo: scope.topic === "unit" ? scope.unitNo : null,
      difficulty: scope.difficulty || null,
    };
  }

  // 保留上一份已知配置并显式报错，不静默换成默认范围。
  // Only used when the TV omits answerScopes (cached page or contract break).
  // One legacy scope becomes level 1 so the rest of the page keeps working.
  function applyAnswerScope(raw, mode) {
    const custom = mode ? mode === "custom" : raw !== null && raw !== undefined;
    if (!custom) {
      applyAnswerLevels([]);
      return true;
    }
    const scope = normalizeAnswerScope(raw);
    if (!scope) {
      console.error("[quiz] invalid answer scope from TV", raw);
      $("settingsError").textContent = t("err_INVALID_SCOPE");
      return false;
    }
    applyAnswerLevels([{
      id: "legacy-1",
      name: "",
      scope,
      available: answerScopeAvailable,
    }]);
    return true;
  }

  function scopeTopicLabel(topic) {
    return t(SCOPE_LABEL_KEYS[topic === "all" ? "review" : topic] || "scopeMidterm");
  }

  function scopeSummaryText(scope) {
    if (!scope) return "—";
    return [
      gradeText(scope.grade, scope.semester),
      subjectLabel(scope.subject),
      textbookLabel(scope.textbook),
      scope.topic === "unit"
        ? unitDisplayLabel(
          scope.grade, scope.semester, scope.subject, scope.textbook, scope.unitNo)
        : scopeTopicLabel(scope.topic),
      scope.difficulty ? t(DIFFICULTY_LABEL_KEYS[scope.difficulty.toLowerCase()] ||
        "difficultyMedium") : t("difficultyUnlimited"),
    ].join(" · ");
  }

  // Mirror QuizQuestionCache.matchesAnswerScope: a generated item is usable only
  // when its scope is inside one level's answer scope. Answer "all" values are
  // wildcards; an unrestricted answer difficulty accepts any generated level.
  function generationFitsAnswerScope(scope) {
    const ans = normalizeAnswerScope(scope);
    if (!ans) return true;
    if (genGradeValue !== ans.grade || genSemesterValue !== ans.semester) return false;
    const genTopic = genScopeValue === "review" ? "all" : genScopeValue;
    if (genTopic !== ans.topic) return false;
    if (ans.topic === "unit" && genUnitValue !== ans.unitNo) return false;
    if (ans.difficulty && genDifficultyValue.toUpperCase() !== ans.difficulty) return false;
    if (ans.subject !== SUBJECT_ALL) {
      if (genSubjectValue !== ans.subject) return false;
      if (ans.textbook !== TEXTBOOK_ALL && genTextbookId !== ans.textbook) return false;
    }
    return true;
  }

  function generationScopeMismatch() {
    const target = generationTargetLevel();
    return Boolean(target) && !generationFitsAnswerScope(target.scope);
  }

  function answerScopeNeedsQuestions() {
    return Boolean(publicState) &&
      publicState.enabled !== false &&
      publicState.source !== "CLOUD" &&
      scopeLoaded &&
      shortLevels(Number(currentConfig().pullCount || 5)).length > 0;
  }

  function canAlignGenerationToAnswerScope(scope) {
    const ans = normalizeAnswerScope(scope);
    if (!ans || cloudJuniorBlocked(ans.grade)) return false;
    if (ans.subject === SUBJECT_ALL) return true;
    if (!subjectIdsForGrade(ans.grade).includes(ans.subject)) return false;
    return ans.textbook === TEXTBOOK_ALL ||
      textbooksFor(ans.grade, ans.subject).includes(ans.textbook);
  }

  function generationScopeSignature() {
    const target = generationTargetLevel();
    const ans = target ? target.scope : null;
    const answerPart = ans ? levelScopeSignature(ans) : "";
    const genPart = [
      genGradeValue, genSemesterValue, genSubjectValue, genTextbookId,
      genScopeValue, genUnitValue, genDifficultyValue,
    ].join("/");
    return (target ? target.id : "none") + "|" + answerPart + "|" + genPart;
  }

  function updateGenerationScopeStatus() {
    const box = $("genScopeStatus");
    if (!box) return;
    const pull = Number(currentConfig().pullCount || 5);
    const active = Boolean(publicState) &&
      publicState.enabled !== false &&
      publicState.source !== "CLOUD" &&
      scopeLoaded;
    const busy = genState.phase === "running" || genState.phase === "adding";
    if (!active || busy) {
      box.classList.add("hidden");
      return;
    }
    const short = shortLevels(pull);
    // 只在电视当前关卡确实缺题时占用生成卡的空间；题量足够时不打扰。
    if (!short.length) {
      box.classList.add("hidden");
      return;
    }
    const target = generationTargetLevel();
    const mismatch = Boolean(target) && !generationFitsAnswerScope(target.scope);
    box.classList.remove("hidden");
    // 不足和范围不一致都属于需要处理的异常，统一用警示色；
    // 蓝色只留给两张卡的数据流标签。
    box.classList.add("warn");
    if (mismatch && target) {
      const index = answerLevels.indexOf(target);
      $("genScopeStatusText").textContent = t("genScopeStatusLevelMismatch", {
        level: levelLabel(target, index),
      });
    } else {
      $("genScopeStatusText").textContent =
        t("genScopeStatusLevelsInsufficient", { n: short.length, pull });
    }
    const canAlign = mismatch && Boolean(target) &&
      canAlignGenerationToAnswerScope(target.scope);
    $("genScopeStatusActions").classList.toggle("hidden", !mismatch);
    $("alignGenToAnswer").classList.toggle("hidden", !canAlign);
    $("genScopeStatusNote").classList.toggle("hidden", !canAlign);
  }

  function alignGenerationToAnswerScope() {
    const target = generationTargetLevel();
    const ans = target ? target.scope : null;
    if (!ans || !canAlignGenerationToAnswerScope(ans)) return false;
    genGradeValue = ans.grade;
    genSemesterValue = ans.semester;
    genSubjectValue = ans.subject;
    if (ans.subject === SUBJECT_ALL) {
      genTextbookId = "general";
    } else if (ans.textbook === TEXTBOOK_ALL) {
      genTextbookId = defaultTextbookFor(ans.grade, ans.subject);
    } else {
      genTextbookId = ans.textbook;
    }
    keepGenerationSubject();
    keepGenerationTextbook();
    genScopeValue = ans.topic === "all" ? "review" : ans.topic;
    genUnitValue = ans.topic === "unit" ? ans.unitNo : 1;
    genDifficultyValue = ans.difficulty ? ans.difficulty.toLowerCase() : "";
    setRequirement(legacyRequirementLabel(genScopeValue, genUnitValue), { persist: false });
    renderGenConfig(true);
    saveGeneratePrefs();
    scopeMismatchAckKey = "";
    renderHome();
    renderGenerate();
    return true;
  }

  function openScopeMismatchDialog() {
    const target = generationTargetLevel();
    const ans = target ? target.scope : null;
    if (!ans) return false;
    const pull = Number(currentConfig().pullCount || 5);
    const index = answerLevels.indexOf(target);
    $("scopeMismatchAnswer").textContent = t("scopeMismatchAnswerLine", {
      scope: levelLabel(target, index) + " · " + scopeSummaryText(ans),
    });
    $("scopeMismatchGenerate").textContent =
      t("scopeMismatchGenerateLine", { scope: generationScopeSummary() });
    $("scopeMismatchBody").textContent = t("scopeMismatchBody", {
      available: target.available,
      pull,
    });
    $("scopeMismatchAlign").classList.toggle(
      "hidden",
      !canAlignGenerationToAnswerScope(ans),
    );
    openModal("scopeMismatchDialog");
    return true;
  }

  function requirementDisplayTopic() {
    const text = normalizeRequirement(genRequirementValue);
    return text && canonicalFromRequirement(text) ? text : generationTopicText();
  }

  function requirementNote() {
    const text = normalizeRequirement(genRequirementValue);
    if (!text) return t("genRequirementMissing");
    return canonicalFromRequirement(text) ? "" : t("genRequirementNote", { text });
  }

  function generationScopeParts() {
    const subject = subjectLabel(genSubjectValue);
    const book = genSubjectValue === "all" ? "" : textbookLabel(genTextbookId);
    return {
      main: [
        gradeText(genGradeValue, genSemesterValue),
        subject,
        book,
        requirementDisplayTopic(),
      ].filter(Boolean).join(" · "),
      detail: [generationDifficultyText(), requirementNote()].filter(Boolean).join(" · "),
    };
  }

  function generationScopeSummary() {
    const parts = generationScopeParts();
    return parts.main + " · " + parts.detail;
  }

  function resetScopeOptions() {
    scopeRequestToken++;
    scopeFacets = {};
    scopeOptionsLoading = {};
    scopeMatched = null;
  }

  // Keep the changed step's own options; only downstream steps depend on it.
  function invalidateScopeAfter(step) {
    const index = SCOPE_STEPS.indexOf(step);
    if (index < 0) return;
    scopeRequestToken++;
    scopeOptionsLoading = {};
    SCOPE_STEPS.forEach((name, position) => {
      if (position > index) delete scopeFacets[name];
    });
    if (step === "difficulty") delete scopeFacets.difficulty;
    scopeMatched = null;
  }

  function scopeDraftValue(step, draft) {
    if (!draft) return null;
    if (step === "grade") return draft.grade ? String(draft.grade) : null;
    if (step === "semester") return draft.semester ? String(draft.semester) : null;
    if (step === "subject") return draft.subject || null;
    if (step === "textbook") return draft.textbook || null;
    if (step === "topic") {
      if (draft.topic === "unit" && draft.unitNo) return "unit:" + draft.unitNo;
      return draft.topic || null;
    }
    if (step === "difficulty") return String(draft.difficulty || "").toUpperCase();
    return null;
  }

  function scopeRequiredComplete(draft) {
    if (!draft) return false;
    const grade = Number(draft.grade);
    const semester = Number(draft.semester);
    const unitNo = Number(draft.unitNo);
    if (!Number.isInteger(grade) || !gradeNos().includes(grade)) return false;
    if (semester !== 1 && semester !== 2) return false;
    if (!subjectIdsForGrade(grade).includes(draft.subject)) return false;
    if (draft.subject === SUBJECT_ALL && draft.textbook !== TEXTBOOK_ALL) return false;
    if (!isKnownTextbook(draft.textbook)) return false;
    if (!SCOPE_TOPIC_VALUES.includes(draft.topic)) return false;
    if (draft.topic === "unit" &&
        (!Number.isInteger(unitNo) || unitNo < 1 ||
         unitNo > unitMaxForContext(grade, semester, draft.subject, draft.textbook))) {
      return false;
    }
    return SCOPE_DIFFICULTY_VALUES.includes(String(draft.difficulty || "").toUpperCase());
  }

  function scopeVisibleSteps(draft) {
    const visible = ["grade"];
    if (!draft || !draft.grade) return visible;
    visible.push("semester");
    if (!draft.semester) return visible;
    visible.push("subject");
    if (!draft.subject) return visible;
    // 全科的教材固定为通配，不再让家长在无意义的教材维度上做选择。
    if (draft.subject !== SUBJECT_ALL) {
      visible.push("textbook");
      if (!draft.textbook) return visible;
    }
    visible.push("topic");
    if (!draft.topic) return visible;
    visible.push("difficulty");
    return visible;
  }

  function activeScopeStep(draft) {
    const visible = scopeVisibleSteps(draft);
    return visible[visible.length - 1];
  }

  function scopeValueLabel(step, value, draft) {
    if (step === "grade") return gradeLabel(Number(value));
    if (step === "semester") return t(Number(value) === 2 ? "semesterSecond" : "semesterFirst");
    if (step === "subject") return subjectLabel(value);
    if (step === "textbook") return textbookLabel(value);
    if (step === "topic") {
      const unit = /^unit:([0-9]+)$/.exec(String(value));
      if (unit) {
        const context = draft || {};
        return unitDisplayLabel(
          context.grade, context.semester, context.subject, context.textbook,
          Number(unit[1]));
      }
      return scopeTopicLabel(value);
    }
    if (step === "difficulty") {
      return t(value
        ? (DIFFICULTY_LABEL_KEYS[String(value).toLowerCase()] || "difficultyMedium")
        : "difficultyUnlimited");
    }
    return String(value);
  }

  function scopeCandidateValues(step, draft, counts) {
    if (step === "grade") {
      return gradeNos().filter((no) => !cloudJuniorBlocked(no)).map(String);
    }
    if (step === "semester") return ["1", "2"];
    if (step === "subject") {
      return subjectIdsForGrade(draft && draft.grade);
    }
    if (step === "textbook") {
      if (draft && draft.subject === SUBJECT_ALL) return [TEXTBOOK_ALL];
      const known = textbooksFor(draft && draft.grade, draft && draft.subject);
      const extra = Object.keys(counts || {})
        .map((key) => String(key).toLowerCase())
        .filter((key) => isKnownTextbook(key));
      // 具体学科也可以跨教材出题；“全部教材”放在第一位。
      return Array.from(new Set([TEXTBOOK_ALL].concat(known, extra)));
    }
    if (step === "topic") {
      // List the bank's real topic values: every unit separately, then the
      // canonical midterm/final/review scopes that actually have questions.
      const unitMax = draft
        ? unitMaxForContext(draft.grade, draft.semester, draft.subject, draft.textbook)
        : unitMaxForGrade(0);
      const units = Object.keys(counts || {})
        .map((key) => /^UNIT:([0-9]+)$/.exec(key))
        .filter((match) => match && Number(match[1]) >= 1 && Number(match[1]) <= unitMax)
        .sort((a, b) => Number(a[1]) - Number(b[1]))
        .map((match) => "unit:" + match[1]);
      const topics = ["MIDTERM", "FINAL", "REVIEW"]
        .filter((key) => Number(counts[key] || 0) > 0)
        .map((key) => SCOPE_TOPIC_FROM_CANONICAL[key]);
      return units.concat(topics);
    }
    if (step === "difficulty") return SCOPE_DIFFICULTY_VALUES.slice();
    return [];
  }

  function scopeValueCount(step, value, data) {
    if (!data || !data.counts) return null;
    const counts = data.counts;
    if (step === "topic") {
      const unit = /^unit:([0-9]+)$/.exec(String(value));
      if (unit) return Number(counts["UNIT:" + unit[1]] || 0);
      return Number(counts[SCOPE_TOPIC_TO_CANONICAL[value]] || 0);
    }
    if (step === "difficulty") {
      return value === "" ? Number(data.unrestricted || 0) : Number(counts[value] || 0);
    }
    if (step === "subject") {
      if (String(value) === SUBJECT_ALL) {
        // 题库按单题学科计数；全科范围是这些计数的总和（含识别不出学科的 ALL）。
        return allSubjectIds()
          .map((id) => id.toUpperCase())
          .reduce((sum, key) => sum + Number(counts[key] || 0), 0);
      }
      return Number(counts[String(value).toUpperCase()] || 0);
    }
    if (step === "textbook") {
      if (String(value) === TEXTBOOK_ALL) {
        return Object.keys(counts || {})
          .filter((key) => isKnownTextbook(String(key).toLowerCase()))
          .reduce((sum, key) => sum + Number(counts[key] || 0), 0);
      }
      return Number(counts[String(value).toUpperCase()] || 0);
    }
    return Number(counts[String(value)] || 0);
  }

  function scopeStepEntries(step, draft) {
    const data = scopeFacets[step];
    const current = scopeDraftValue(step, draft);
    if (!data || data.failed) {
      return current === null
        ? []
        : [{ value: current, label: scopeValueLabel(step, current, draft), count: null }];
    }
    const counts = data.counts || {};
    const entries = scopeCandidateValues(step, draft, counts).map((value) => ({
      value,
      label: scopeValueLabel(step, value, draft),
      count: scopeValueCount(step, value, data),
    }));
    if (current !== null && !entries.some((entry) => String(entry.value) === String(current))) {
      entries.push({
        value: current,
        label: scopeValueLabel(step, current, draft),
        count: 0,
      });
    }
    // Hide values with no bank questions. Keep the current selection visible
    // even at 0, and always keep "any difficulty" so it can be cleared.
    return entries.filter((entry) =>
      entry.count === null ||
      entry.count > 0 ||
      String(entry.value) === String(current) ||
      (step === "difficulty" && entry.value === ""));
  }

  function renderScopeStepChips(step, draft) {
    const container = $(SCOPE_STEP_CHIP_IDS[step]);
    if (!container) return;
    const current = scopeDraftValue(step, draft);
    container.replaceChildren();
    scopeStepEntries(step, draft).forEach((entry) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "choice-chip";
      button.dataset.scopeStep = step;
      button.dataset.scopeValue = String(entry.value);
      button.setAttribute("aria-pressed", String(String(entry.value) === String(current)));
      button.appendChild(document.createTextNode(entry.label));
      if (entry.count !== null) {
        const count = document.createElement("span");
        count.className = "chip-count";
        count.textContent = String(entry.count);
        button.appendChild(count);
      }
      container.appendChild(button);
    });
  }

  function renderCustomScopeControls() {
    if (!customScope || typeof customScope !== "object") customScope = {};
    const visible = new Set(scopeVisibleSteps(customScope));
    SCOPE_STEPS.forEach((step) => {
      const container = $(SCOPE_STEP_IDS[step]);
      if (!container) return;
      const shown = visible.has(step);
      container.classList.toggle("hidden", !shown);
      if (shown) {
        renderScopeStepChips(step, customScope);
      } else {
        const chips = $(SCOPE_STEP_CHIP_IDS[step]);
        if (chips) chips.replaceChildren();
      }
    });
  }

  // Each step sends only its upstream selections. The facets endpoint skips the
  // dimension being counted, so the options stay complete for that step.
  function scopeQueryForStep(step, draft) {
    const params = new URLSearchParams();
    const index = SCOPE_STEPS.indexOf(step);
    const add = (name, value) => {
      if (value === null || value === undefined || value === "") return;
      params.set(name, String(value));
    };
    if (index > 0) add("grade", draft.grade);
    if (index > 1) add("semester", draft.semester);
    if (index > 2) add("subject", draft.subject ? draft.subject.toUpperCase() : "");
    if (index > 3) add("textbook", draft.textbook ? draft.textbook.toUpperCase() : "");
    if (step === "difficulty") {
      if (draft.topic === "unit" && draft.unitNo) add("topic", "UNIT:" + draft.unitNo);
      else if (draft.topic) add("topic", SCOPE_TOPIC_TO_CANONICAL[draft.topic]);
      add("difficulty", draft.difficulty);
    }
    return params.toString();
  }

  function levelEditorOpen() {
    const page = $("levelEditorPage");
    return Boolean(page) && !page.classList.contains("hidden");
  }

  function ensureScopeOptions() {
    if (!levelEditorOpen()) return;
    const draft = customScope || {};
    scopeVisibleSteps(draft).forEach((step) => {
      if (!Object.prototype.hasOwnProperty.call(scopeFacets, step) && !scopeOptionsLoading[step]) {
        loadScopeStep(step);
      }
    });
  }

  async function loadScopeStep(step) {
    if (!levelEditorOpen() || scopeOptionsLoading[step]) return;
    const token = scopeRequestToken;
    const draft = Object.assign({}, customScope || {});
    scopeOptionsLoading[step] = true;
    const query = scopeQueryForStep(step, draft);
    try {
      const data = await api(
        "/api/quiz/admin/bank/facets" + (query ? "?" + query : ""),
        { admin: true },
      );
      if (token !== scopeRequestToken) return;
      const raw = data && data.facets && data.facets[step];
      scopeFacets[step] = {
        counts: raw && typeof raw === "object" ? raw : {},
        unrestricted: Number(data && data.unrestricted || 0),
        matched: Number(data && data.matched || 0),
      };
      if (step === "difficulty") scopeMatched = Number(data && data.matched || 0);
      if (levelEditorOpen()) $("levelEditorError").textContent = "";
    } catch (error) {
      if (token !== scopeRequestToken) return;
      scopeFacets[step] = { counts: {}, unrestricted: 0, matched: null, failed: true };
      if (step === "difficulty") scopeMatched = null;
      if (error.code !== "NOT_FOUND" && levelEditorOpen()) {
        $("levelEditorError").textContent = errorText(error);
      }
    } finally {
      if (token === scopeRequestToken) {
        scopeOptionsLoading[step] = false;
        if (levelEditorOpen()) renderLevelEditor();
      }
    }
  }

  function scopeIsEmpty(draft) {
    if (!draft) return false;
    const step = activeScopeStep(draft);
    if (scopeOptionsLoading[step]) return false;
    const data = scopeFacets[step];
    if (!data || data.failed) return false;
    if (step === "difficulty") return scopeMatched !== null && Number(scopeMatched) === 0;
    return scopeStepEntries(step, draft).length === 0;
  }

  function scopeAvailableText(draft) {
    if (!scopeRequiredComplete(draft)) return "";
    if (scopeOptionsLoading.difficulty) return t("scopeLoading");
    if (scopeMatched === null) return "";
    return t("scopeAvailableCount", { n: Number(scopeMatched) });
  }

  function answerScopeErrorMessage() {
    if (answerScopeMode !== "custom") return "";
    if (customScope && cloudJuniorBlocked(customScope.grade)) {
      return t("err_CLOUD_JUNIOR_UNSUPPORTED");
    }
    if (!scopeRequiredComplete(customScope)) return t("err_SCOPE_INCOMPLETE");
    if (scopeMatched !== null && Number(scopeMatched) === 0) return t("err_SCOPE_EMPTY");
    return "";
  }

  function focusActiveScopeStep() {
    const container = $(SCOPE_STEP_IDS[activeScopeStep(customScope || {})]);
    if (container) container.scrollIntoView({ block: "center" });
  }

  function selectScopeValue(step, rawValue) {
    if (answerScopeMode !== "custom" || !SCOPE_STEPS.includes(step)) return;
    const draft = Object.assign({}, customScope || {});
    let value = String(rawValue === undefined || rawValue === null ? "" : rawValue);
    if (step === "grade" || step === "semester") {
      const number = Number(value);
      if (step === "grade") {
        if (!gradeNos().includes(number) || cloudJuniorBlocked(number)) return;
      } else if (number !== 1 && number !== 2) {
        return;
      }
      value = number;
    }
    if (step === "subject" && !subjectIdsForGrade(draft.grade).includes(value)) return;
    if (step === "textbook" && !isKnownTextbook(value)) return;
    let topicUnit = null;
    if (step === "topic") {
      const unit = /^unit:([0-9]+)$/.exec(value);
      if (unit) {
        topicUnit = Number(unit[1]);
        if (topicUnit < 1 ||
            topicUnit > unitMaxForContext(
              draft.grade, draft.semester, draft.subject, draft.textbook)) {
          return;
        }
      } else if (!SCOPE_TOPIC_VALUES.includes(value)) {
        return;
      }
    }
    if (step === "difficulty") {
      value = value.toUpperCase();
      if (!SCOPE_DIFFICULTY_VALUES.includes(value)) return;
    }
    if (String(value) === String(scopeDraftValue(step, draft))) return;

    if (step === "grade") {
      draft.grade = value;
      delete draft.semester;
      delete draft.subject;
      delete draft.textbook;
      delete draft.topic;
      delete draft.unitNo;
      delete draft.difficulty;
    } else if (step === "semester") {
      draft.semester = value;
      delete draft.subject;
      delete draft.textbook;
      delete draft.topic;
      delete draft.unitNo;
      delete draft.difficulty;
    } else if (step === "subject") {
      draft.subject = value;
      // 全科直接落到“全部教材”；具体学科重新选择教材。
      if (value === SUBJECT_ALL) draft.textbook = TEXTBOOK_ALL;
      else delete draft.textbook;
      delete draft.topic;
      delete draft.unitNo;
      delete draft.difficulty;
    } else if (step === "textbook") {
      draft.textbook = value;
      delete draft.topic;
      delete draft.unitNo;
      delete draft.difficulty;
    } else if (step === "topic") {
      if (topicUnit !== null) {
        draft.topic = "unit";
        draft.unitNo = topicUnit;
      } else {
        draft.topic = value;
        delete draft.unitNo;
      }
      delete draft.difficulty;
    } else if (step === "difficulty") {
      draft.difficulty = value;
    }

    customScope = draft;
    $("levelEditorError").textContent = "";
    invalidateScopeAfter(step);
    renderLevelEditor();
  }

  function levelLabel(level, index) {
    const name = String(level && level.name || "").trim();
    return name || t("levelFallbackName", { n: Number(index) + 1 });
  }

  function levelScopeSignature(scope) {
    const normalized = normalizeAnswerScope(scope);
    if (!normalized) return "";
    return [
      normalized.grade,
      normalized.semester,
      normalized.subject,
      normalized.textbook,
      normalized.topic,
      normalized.unitNo === null ? "" : normalized.unitNo,
      normalized.difficulty,
    ].join("/");
  }

  function shortLevels(pull) {
    return answerLevels.filter((level) => level.available < pull);
  }

  // Levels drive the generation hint: prefer the first short level whose scope
  // the current generation settings do not fit, so the offered action is useful.
  function generationTargetLevel() {
    const pull = Number(currentConfig().pullCount || 5);
    const short = shortLevels(pull);
    if (!short.length) return null;
    return short.find((level) => !generationFitsAnswerScope(level.scope)) || short[0];
  }

  function renderLevelUndo() {
    const bar = $("levelUndo");
    if (!bar) return;
    if (!levelUndo) {
      bar.classList.add("hidden");
      return;
    }
    $("levelUndoText").textContent = t("levelDeleted", { name: levelUndo.name });
    bar.classList.remove("hidden");
  }

  function scheduleLevelMessage(text) {
    if ($("levelState")) $("levelState").textContent = text;
    if (levelMessageTimer) clearTimeout(levelMessageTimer);
    levelMessageTimer = setTimeout(() => {
      levelMessageTimer = 0;
      if ($("levelState")) $("levelState").textContent = "";
    }, 2600);
  }

  function renderAnswerLevels() {
    const list = $("levelList");
    if (!list) return;
    list.replaceChildren();
    answerLevels.forEach((level, index) => {
      const row = document.createElement("div");
      row.className = "level-row";
      row.dataset.levelId = level.id;
      if (level.available < Number(currentConfig().pullCount || 5)) {
        row.classList.add("short");
      }

      const main = document.createElement("button");
      main.type = "button";
      main.className = "level-main";
      main.dataset.levelEdit = level.id;

      const badge = document.createElement("span");
      badge.className = "level-index";
      badge.textContent = String(index + 1);
      main.appendChild(badge);

      const copy = document.createElement("span");
      copy.className = "level-copy";
      const title = document.createElement("span");
      title.className = "level-title";
      const summary = document.createElement("span");
      summary.className = "level-meta";
      if (String(level.name || "").trim()) {
        title.textContent = level.name.trim();
        summary.textContent = scopeSummaryText(level.scope);
      } else {
        title.textContent = scopeSummaryText(level.scope);
      }
      const available = document.createElement("span");
      available.className = "level-available";
      const pull = Number(currentConfig().pullCount || 5);
      available.textContent = level.available < pull
        ? t("levelShort", { available: level.available, pull })
        : t("levelAvailable", { n: level.available });
      if (level.available < pull) available.classList.add("warn");
      copy.appendChild(title);
      if (summary.textContent) copy.appendChild(summary);
      copy.appendChild(available);
      main.appendChild(copy);
      row.appendChild(main);

      const actions = document.createElement("div");
      actions.className = "level-actions";
      const label = levelLabel(level, index);
      const addAction = (attr, text, aria) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "level-action";
        button.setAttribute(attr, level.id);
        button.setAttribute("aria-label", t(aria, { name: label }));
        button.textContent = text;
        button.disabled = levelSaving;
        actions.appendChild(button);
        return button;
      };
      const up = addAction("data-level-up", "↑", "levelMoveUpAria");
      up.disabled = levelSaving || index === 0;
      const down = addAction("data-level-down", "↓", "levelMoveDownAria");
      down.disabled = levelSaving || index === answerLevels.length - 1;
      addAction("data-level-delete", "×", "levelDeleteAria");
      row.appendChild(actions);
      list.appendChild(row);
    });

    $("levelEmpty").classList.toggle("hidden", answerLevels.length > 0);
    $("levelListCount").textContent = t("levelListCount", {
      n: answerLevels.length,
      max: MAX_LEVELS,
    });
    $("addLevel").classList.toggle("hidden", answerLevels.length >= MAX_LEVELS);
    $("addLevel").disabled = levelSaving;
    $("levelMaxHint").classList.toggle("hidden", answerLevels.length < MAX_LEVELS);
    $("levelMaxHint").textContent = t("levelMaxHint", { n: MAX_LEVELS });
    renderLevelUndo();
  }

  function renderAnswerPage() {
    if (!$("answerTabs")) return;
    document.querySelectorAll("#answerTabs [data-answer-tab]").forEach((button) => {
      button.setAttribute("aria-selected", String(button.dataset.answerTab === answerTab));
    });
    $("answerPaneScope").classList.toggle("hidden", answerTab !== "scope");
    $("answerPaneRules").classList.toggle("hidden", answerTab !== "rules");
    $("answerSaveBar").classList.toggle("hidden", answerTab !== "rules");
    if (answerTab === "scope") renderAnswerLevels();
    renderSettingsChrome();
  }

  function applyAnswerLevels(rawLevels) {
    answerLevels = (Array.isArray(rawLevels) ? rawLevels : [])
      .map((item) => {
        const scope = normalizeAnswerScope(item && item.scope);
        if (!scope) return null;
        return {
          id: String(item.id || ""),
          name: String(item.name || "").trim(),
          scope,
          available: Math.max(0, Number(item.available || 0)),
        };
      })
      .filter(Boolean)
      .slice(0, MAX_LEVELS);
    answerScope = answerLevels.length ? Object.assign({}, answerLevels[0].scope) : null;
    answerScopeMode = answerLevels.length ? "custom" : "bound";
    answerScopeAvailable = answerLevels.reduce(
      (max, level) => Math.max(max, level.available),
      0,
    );
    scopeLoaded = true;
    if ($("answerTabs")) renderAnswerPage();
    renderHome();
  }

  function renderLevelEditor() {
    if (!$("levelEditorPage")) return;
    if (!customScope || typeof customScope !== "object") customScope = {};
    $("levelEditorTitle").textContent = t(
      levelEditingId ? "levelEditTitle" : "levelAddTitle",
    );
    if (document.activeElement !== $("levelName")) {
      $("levelName").value = levelNameDraft;
    }
    renderCustomScopeControls();
    ensureScopeOptions();
    $("levelEditorAvailable").textContent = scopeAvailableText(customScope);
    $("levelEditorEmpty").classList.toggle("hidden", !scopeIsEmpty(customScope));
    $("saveLevel").disabled = levelSaving;
  }

  function openLevelEditor(id) {
    const level = id ? answerLevels.find((item) => item.id === id) : null;
    if (id && !level) return false;
    levelEditingId = level ? level.id : null;
    levelNameDraft = level ? level.name : "";
    customScope = level ? Object.assign({}, level.scope) : {};
    answerScopeMode = "custom";
    $("levelName").value = levelNameDraft;
    $("levelEditorError").textContent = "";
    resetScopeOptions();
    showGenPage("level");
    ensureScopeOptions();
    renderLevelEditor();
    return true;
  }

  async function postLevels(levels, successKey) {
    if (levelSaving) return false;
    if (!challengeConfig) {
      $("levelError").textContent = t("settingsLoading");
      try {
        await loadAdminSession();
      } catch (error) {
        $("levelError").textContent = errorText(error);
        return false;
      }
      if (!challengeConfig) return false;
    }
    levelSaving = true;
    // Any new list operation supersedes a pending undo snapshot; restoring it
    // later would also undo the newer change.
    if (levelUndo) {
      levelUndo = null;
      if (levelUndoTimer) {
        clearTimeout(levelUndoTimer);
        levelUndoTimer = 0;
      }
      renderLevelUndo();
    }
    $("levelError").textContent = "";
    if ($("saveLevel")) $("saveLevel").disabled = true;
    renderAnswerLevels();
    const config = readSettingsConfig();
    try {
      const data = await api("/api/quiz/admin/challenge-config", {
        method: "POST",
        admin: true,
        body: {
          config,
          answerScopes: levels.map((level) => {
            const payload = {
              name: String(level.name || ""),
              scope: scopeToCanonical(level.scope),
            };
            if (level.id) payload.id = level.id;
            return payload;
          }),
        },
      });
      challengeConfig = normalizeConfig(data.config || config);
      applyAnswerLevels(data.answerScopes || []);
      syncState = data.syncState || syncState;
      answerDirty = false;
      renderSync();
      renderSettings();
      renderHome();
      renderAnswerPage();
      if (successKey) scheduleLevelMessage(t(successKey));
      return true;
    } catch (error) {
      $("levelError").textContent = errorText(error);
      return false;
    } finally {
      levelSaving = false;
      if ($("saveLevel")) $("saveLevel").disabled = false;
      // The success path re-renders while levelSaving is still true, so render
      // once more to re-enable every row action.
      renderAnswerLevels();
    }
  }

  async function saveLevelDraft() {
    if (levelSaving) return;
    const name = String($("levelName").value || "").trim();
    if (Array.from(name).length > LEVEL_NAME_MAX) {
      $("levelEditorError").textContent = t("levelNameTooLong");
      return;
    }
    const scopeError = answerScopeErrorMessage();
    if (scopeError) {
      $("levelEditorError").textContent = scopeError;
      focusActiveScopeStep();
      return;
    }
    const scope = normalizeAnswerScope(customScope);
    if (!scope) {
      $("levelEditorError").textContent = t("err_SCOPE_INCOMPLETE");
      return;
    }
    const signature = levelScopeSignature(scope);
    const duplicate = answerLevels.some((level) =>
      level.id !== levelEditingId && levelScopeSignature(level.scope) === signature);
    if (duplicate) {
      $("levelEditorError").textContent = t("levelDuplicate");
      return;
    }
    $("levelEditorError").textContent = "";
    let next;
    if (levelEditingId) {
      next = answerLevels.map((level) => level.id === levelEditingId
        ? Object.assign({}, level, { name, scope })
        : level);
    } else {
      next = answerLevels.concat([{ id: "", name, scope, available: 0 }]);
    }
    const saved = await postLevels(next, "levelSaved");
    if (!saved) {
      $("levelEditorError").textContent = $("levelError").textContent;
      $("levelError").textContent = "";
      return;
    }
    levelEditingId = null;
    levelNameDraft = "";
    customScope = null;
    resetScopeOptions();
    showGenPage("answer");
  }

  async function deleteLevel(id) {
    const index = answerLevels.findIndex((level) => level.id === id);
    if (index < 0) return;
    const previous = answerLevels.slice();
    const name = levelLabel(answerLevels[index], index);
    const next = answerLevels.filter((level) => level.id !== id);
    const saved = await postLevels(next, "");
    if (!saved) return;
    levelUndo = { levels: previous, name };
    if (levelUndoTimer) clearTimeout(levelUndoTimer);
    levelUndoTimer = setTimeout(() => {
      levelUndoTimer = 0;
      levelUndo = null;
      renderLevelUndo();
    }, 8000);
    renderLevelUndo();
  }

  async function undoDeleteLevel() {
    if (!levelUndo || levelSaving) return;
    const restore = levelUndo.levels;
    levelUndo = null;
    if (levelUndoTimer) {
      clearTimeout(levelUndoTimer);
      levelUndoTimer = 0;
    }
    renderLevelUndo();
    await postLevels(restore, "levelSaved");
  }

  async function moveLevel(id, delta) {
    const index = answerLevels.findIndex((level) => level.id === id);
    const target = index + delta;
    if (index < 0 || target < 0 || target >= answerLevels.length) return;
    const next = answerLevels.slice();
    const moved = next.splice(index, 1)[0];
    next.splice(target, 0, moved);
    await postLevels(next, "levelOrderSaved");
  }

  function setAnswerTab(tab) {
    answerTab = tab === "rules" ? "rules" : "scope";
    $("levelError").textContent = "";
    $("levelState").textContent = "";
    renderAnswerPage();
  }

  // ---- AI 提示词（自动生成；家长编辑后由徽标提示）----

  function joinSubjectLabels(names) {
    if (!names.length) return "";
    if (names.length === 1) return names[0];
    if (lang === "zh") return names.join("、");
    const conjunction = {
      en: " and ",
      ru: " и ",
      uk: " й ",
      kk: " және ",
    }[lang] || " and ";
    if (names.length === 2) return names[0] + conjunction + names[1];
    return names.slice(0, -1).join(", ") +
      (lang === "en" ? ", and " : " " + conjunction.trim() + " ") +
      names[names.length - 1];
  }

  function generationSubjectText() {
    if (genSubjectValue === "all") {
      return joinSubjectLabels(
        subjectIdsForGrade(genGradeValue)
          .filter((id) => id !== SUBJECT_ALL)
          .map((id) => subjectLabel(id)),
      );
    }
    return t("genSubjectWithBook", {
      subject: subjectLabel(genSubjectValue),
      book: textbookLabel(genTextbookId),
    });
  }

  function composeGenerationPrompt() {
    const grade = gradeText(genGradeValue, genSemesterValue);
    const requirement = normalizeRequirement(genRequirementValue);
    const canonicalLabel = generationTopicText();
    const resolved = requirement ? canonicalFromRequirement(requirement) : null;
    const resolvedBook = resolved && resolved.scope === "unit"
      ? unitBook(genGradeValue, genSemesterValue, genSubjectValue, genTextbookId)
      : null;
    const resolvedUnit = resolvedBook && resolved.scope === "unit"
      ? resolvedBook.units.find((unit) => unit.no === resolved.unit)
      : null;
    const namesUnitOnly = resolvedUnit &&
      genScopeValue === "unit" &&
      genUnitValue === resolved.unit &&
      requirementOnlyNamesUnit(requirement, resolvedUnit);
    const invalidUnit = outOfRangeRequirementUnit(requirement);
    let scope;
    // A unit the book does not have must not leak into the prompt as if it
    // were the active scope; the requirement hint explains the real range.
    if (invalidUnit === null && requirement && requirement !== canonicalLabel && !namesUnitOnly) {
      // 自由要求写进提示词；规范归类由文字里的明确标记决定。
      scope = t("promptScopeManual", { topic: requirement });
    } else if (genScopeValue === "unit") {
      scope = genSubjectValue === "all"
        ? t("promptScopeUnitAll")
        : (generationUnitPromptScope() ||
          t("promptScopeUnit", { unit: genUnitValue }));
    } else if (genSubjectValue === "all" && genScopeValue === "review") {
      scope = t("promptScopeMixed");
    } else {
      scope = t(SCOPE_PROMPT_KEYS[genScopeValue]);
    }
    return t("genPromptTemplate", {
      grade,
      subject: generationSubjectText(),
      scope,
    });
  }

  function updatePromptBadge() {
    if (!$("genPromptBadge")) return;
    const edited = genPromptDirty && $("genPrompt").value.trim() !== composeGenerationPrompt();
    $("genPromptBadge").textContent = t(edited ? "genPromptBadgeEdited" : "genPromptBadgeAuto");
  }

  function setButtonSaving(button, saving) {
    if (!button) return;
    if (saving) {
      button.dataset.idleLabel = button.textContent;
      button.textContent = t("saving");
      button.disabled = true;
      button.setAttribute("aria-busy", "true");
    } else {
      if (button.dataset.idleLabel) button.textContent = button.dataset.idleLabel;
      delete button.dataset.idleLabel;
      button.disabled = false;
      button.removeAttribute("aria-busy");
    }
  }

  // 移动端第一次点触有时只用于收起键盘；pointerup 可靠触发，click 兜底。
  function bindReliableTap(element, handler) {
    if (!element) return;
    let start = null;
    let lastPointerAt = 0;
    element.addEventListener("pointerdown", (event) => {
      if (event.pointerType === "mouse" && event.button !== 0) return;
      if (element.disabled) return;
      start = { id: event.pointerId, x: event.clientX, y: event.clientY };
      // 键盘收起会让底部按钮位移；捕获指针保证 pointerup 仍落在按钮上。
      try { element.setPointerCapture(event.pointerId); } catch (_) { /* ignore */ }
    });
    element.addEventListener("pointerup", (event) => {
      if (!start || start.id !== event.pointerId) return;
      const moved = Math.hypot(event.clientX - start.x, event.clientY - start.y);
      start = null;
      if (moved > 12 || element.disabled) return;
      lastPointerAt = Date.now();
      handler(event);
    });
    element.addEventListener("pointercancel", () => { start = null; });
    element.addEventListener("click", (event) => {
      if (Date.now() - lastPointerAt < 700 || element.disabled) return;
      handler(event);
    });
  }

  function saveRange() {
    if (rangeSaving) return;
    $("rangeError").textContent = "";
    genRequirementValue = normalizeRequirement(
      $("genRequirement") ? $("genRequirement").value : genRequirementValue,
    );
    applyRequirementScope(genRequirementValue, true);
    if (!genRequirementValue) {
      $("rangeError").textContent = t("err_REQUIREMENT_REQUIRED");
      if ($("genRequirement")) $("genRequirement").focus();
      return;
    }
    if (outOfRangeRequirementUnit(genRequirementValue) !== null) {
      $("rangeError").textContent = requirementInvalidUnitText();
      if ($("genRequirement")) $("genRequirement").focus();
      return;
    }
    if (!readGenerationPrefs()) {
      $("rangeError").textContent = t("err_INVALID_CONFIG");
      return;
    }
    if (!$("genPrompt").value.trim()) {
      $("rangeError").textContent = t("genPromptRequired");
      $("genPrompt").focus();
      return;
    }
    const button = $("saveRange");
    rangeSaving = true;
    setButtonSaving(button, true);
    saveGeneratePrefsNow()
      .then(() => {
        if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
        showGenPage("home");
        renderHome();
        renderGenerate();
        // 首页先返回；题库/范围刷新放后台，避免网络慢时看起来“没保存”。
        if (answerScopeMode === "bound") {
          loadAdminSession().then(() => renderHome()).catch(() => {});
        }
      })
      .catch((error) => {
        $("rangeError").textContent = errorText(error);
      })
      .finally(() => {
        rangeSaving = false;
        setButtonSaving(button, false);
      });
  }

  function validateGenerationPayload(payload) {
    return TARGET_COUNTS.includes(payload.targetCount) &&
      [4, 6].includes(payload.options) &&
      LANGS.includes(payload.language) &&
      payload.prompt.length > 0 && payload.prompt.length <= 400 &&
      ["", "easy", "medium", "hard"].includes(payload.difficulty);
  }

  async function startGeneration(options) {
    const skipScopePrompt = Boolean(options && options.skipScopePrompt);
    if (genState.phase === "running" || genState.phase === "adding") return;
    genRequirementValue = normalizeRequirement(
      $("genRequirement") ? $("genRequirement").value : genRequirementValue,
    );
    applyRequirementScope(genRequirementValue, true);
    if (!genRequirementValue) {
      genState = idleGeneration();
      genState.phase = "failed";
      genState.errorCode = "REQUIREMENT_REQUIRED";
      renderGenerate();
      return;
    }
    // Leaving the config page without saving must not skip the unit check.
    if (outOfRangeRequirementUnit(genRequirementValue) !== null) {
      genState = idleGeneration();
      genState.phase = "failed";
      genState.errorCode = "REQUIREMENT_INVALID_UNIT";
      renderGenerate();
      return;
    }
    if (providerState.aiSupported === false) {
      genState = idleGeneration();
      genState.phase = "failed";
      genState.errorCode = "AI_UNSUPPORTED";
      renderGenerate();
      return;
    }
    if (!providerState.consentAccepted) {
      pendingGenerate = true;
      openConsent();
      return;
    }
    if (!providerState.configured) {
      pendingGenerate = true;
      openProviderDialog(true);
      providerMsg = { kind: "error", key: "err_PROVIDER_NOT_CONFIGURED" };
      renderProviderMessages();
      return;
    }
    // The status action and this fallback share the same alignment helper.
    // Only prompt when the TV scope is short and the two scopes really differ;
    // once the parent aligns or accepts the current range, never ask again.
    if (!skipScopePrompt &&
        answerScopeNeedsQuestions() &&
        generationScopeMismatch() &&
        canAlignGenerationToAnswerScope() &&
        scopeMismatchAckKey !== generationScopeSignature()) {
      if (openScopeMismatchDialog()) return;
    }
    const prompt = $("genPrompt").value.trim() || composeGenerationPrompt();
    const payload = {
      targetCount,
      options: genOptionsValue,
      language: lang,
      subject: subjectLabel(genSubjectValue),
      grade: gradeText(genGradeValue, genSemesterValue),
      prompt,
      difficulty: genDifficultyValue,
    };
    // 六字段范围始终写进请求：全科用 subject=ALL / textbook=ALL 通配，
    // 具体题目由电视按模型返回的学科补全，答题范围才能按题库数据做 facets。
    payload.subjectId = genSubjectValue;
    payload.gradeNo = genGradeValue;
    payload.semester = genSemesterValue;
    payload.textbook = genSubjectValue === SUBJECT_ALL ? TEXTBOOK_ALL : genTextbookId;
    payload.scopeType = genScopeValue;
    if (genScopeValue === "unit") payload.unitNo = genUnitValue;
    if (!validateGenerationPayload(payload)) {
      genState = idleGeneration();
      genState.phase = "failed";
      genState.errorCode = "INVALID_REQUEST";
      renderGenerate();
      return;
    }
    saveGeneratePrefs();
    const token = ++generationToken;
    clearGenTimer();
    selectedBatch.clear();
    genState = idleGeneration();
    genState.phase = "running";
    genState.target = targetCount;
    genState.customTopic = genRequirementValue !== generationTopicText()
      ? genRequirementValue
      : "";
    genState.autoAdd = genAutoAddValue;
    genState.stopped = false;
    renderGenerate();
    try {
      const data = await api("/api/quiz/admin/generate", {
        method: "POST",
        admin: true,
        body: payload,
      });
      if (token !== generationToken) {
        api("/api/quiz/admin/generate/cancel", {
          method: "POST",
          admin: true,
          body: { jobId: data.jobId },
        }).catch(() => {});
        return;
      }
      genState.jobId = data.jobId;
      genState.batchId = data.batchId || data.jobId;
      genState.target = Number(data.target || targetCount);
      genState.expiresAt = Number(data.expiresAt || 0);
      renderGenerate();
      genTimer = setTimeout(pollGeneration, 1200);
    } catch (error) {
      if (token !== generationToken) return;
      genState = idleGeneration();
      genState.phase = "failed";
      genState.errorCode = error.code || "PROVIDER_NETWORK";
      renderGenerate();
    }
  }

  function applyGenerationProgress(data) {
    genState.target = Number(data.target || genState.target);
    genState.jobId = data.jobId || genState.jobId;
    genState.batchId = data.batchId || genState.jobId || genState.batchId;
    genState.generated = Number(data.generated || 0);
    genState.unique = Number(data.unique || 0);
    genState.duplicates = Number(data.duplicates || 0);
    genState.rejected = Number(data.rejected || 0);
    genState.remaining = Number(data.remaining || 0);
    genState.warnings = Array.isArray(data.warnings) ? data.warnings : [];
    genState.expiresAt = Number(data.expiresAt || 0);
    if (Array.isArray(data.batch)) {
      genState.batch = data.batch;
      genState.batchLoaded = true;
      // 重复题不可选，其余默认全选；家长取消勾选后点“加入题库”才真正写库。
      selectedBatch = new Set(
        data.batch
          .filter((item) => !item.duplicate)
          .map((item) => Number(item.index)),
      );
    }
    if (data.counts) {
      applyBankCounts(data.counts);
      renderBankCounts();
      renderMaintenance();
    }
    renderStock();
    renderGenerate();
    renderHome();
  }

  async function autoAddGeneratedBatch(token) {
    const indexes = genState.batch
      .filter((item) => !item.duplicate)
      .map((item) => Number(item.index))
      .filter((index) => Number.isInteger(index) && index >= 0);
    if (!genState.jobId || indexes.length === 0) {
      renderGenerate();
      refreshStatus();
      return;
    }
    const jobId = genState.jobId;
    genState.phase = "adding";
    genState.stopping = false;
    renderGenerate();
    try {
      const data = await api("/api/quiz/admin/generate/add", {
        method: "POST",
        admin: true,
        body: { jobId, indexes },
      });
      if (token !== generationToken) return;
      applyBankCounts(data.counts);
      selectedBatch.clear();
      genState = idleGeneration();
      genState.noticeKey = "generateAdded";
      genState.noticeCount = Number(data.appended || 0);
      backToGenerateHome();
      refreshStatus();
      loadAdminSession().catch(() => {});
      if (activeTab === "bank") loadBank(true);
    } catch (error) {
      if (token !== generationToken) return;
      console.error("[quiz] auto add failed", error);
      genState.phase = "ready";
      genState.errorCode = "AUTO_ADD_FAILED";
      genState.noticeKey = null;
      selectedBatch = new Set(indexes);
      renderGenerate();
    }
  }

  async function pollGeneration() {
    if (genState.phase !== "running" || !genState.jobId) return;
    const token = generationToken;
    try {
      const data = await api(
        "/api/quiz/admin/generate/status?jobId=" + encodeURIComponent(genState.jobId),
        { admin: true },
      );
      if (token !== generationToken) return;
      genState.pollFailures = 0;
      applyGenerationProgress(data);
      if (data.state === "running") {
        genTimer = setTimeout(pollGeneration, 1500);
        return;
      }
      let reviewOnReady = false;
      if (data.state === "ready") {
        genState.phase = "ready";
        genState.errorCode = null;
        genState.noticeKey = null;
        const canAutoAdd = genState.autoAdd &&
          !genState.stopped &&
          genState.batch.some((item) => !item.duplicate);
        if (canAutoAdd) {
          await autoAddGeneratedBatch(token);
          return;
        }
        // 关闭自动入库时直接展示题目清单，家长确认后再手动加入。
        reviewOnReady = !genState.autoAdd && !genState.stopped &&
          genState.batch.length > 0;
      } else {
        genState.phase = "failed";
        genState.errorCode = data.error || "PROVIDER_HTTP";
        genState.noticeKey = null;
        if (!genState.batch.length) genState.jobId = null;
      }
      renderGenerate();
      refreshStatus();
      if (activeTab === "bank") loadBank(true);
      if (reviewOnReady) openQuestionsPage();
    } catch (error) {
      if (token !== generationToken) return;
      if (["JOB_EXPIRED", "JOB_LOST", "INVALID_JOB"].includes(error.code) ||
          error.code === "ADMIN_EXPIRED" || error.code === "ADMIN_REQUIRED") {
        genState.phase = "failed";
        genState.errorCode = error.code;
        genState.noticeKey = null;
        if (!genState.batch.length) genState.jobId = null;
        renderGenerate();
        return;
      }
      genState.pollFailures = (genState.pollFailures || 0) + 1;
      if (genState.pollFailures <= 5) {
        genTimer = setTimeout(pollGeneration, 3000);
      } else {
        genState.phase = "failed";
        genState.errorCode = error.code || "NETWORK";
        genState.noticeKey = null;
        if (!genState.batch.length) genState.jobId = null;
        renderGenerate();
      }
    }
  }

  // 停止后服务端把任务留在 READY，已生成的题仍可勾选入库。
  async function cancelGeneration() {
    if (genState.phase !== "running" || !genState.jobId || genState.stopping) return;
    const jobId = genState.jobId;
    const token = ++generationToken;
    clearGenTimer();
    genState.stopped = true;
    genState.stopping = true;
    // 先把界面停掉；服务端取消和状态读取在后台收尾，结果不再进入轮询。
    const hasBatch = genState.batch.length > 0;
    genState.phase = hasBatch ? "ready" : "idle";
    genState.noticeKey = hasBatch ? "generateStoppedReview" : null;
    if (!hasBatch) genState.jobId = null;
    renderGenerate();
    try {
      await api("/api/quiz/admin/generate/cancel", {
        method: "POST",
        admin: true,
        body: { jobId },
      });
    } catch (_) {
      // The job also expires on its own; the status read below recovers what it can.
    }
    try {
      const data = await api(
        "/api/quiz/admin/generate/status?jobId=" + encodeURIComponent(jobId),
        { admin: true },
      );
      if (token !== generationToken) return;
      applyGenerationProgress(data);
      genState.stopping = false;
      if (data.state === "running") {
        // 已经点了停止就不再恢复轮询；服务端稍后完成的结果直接丢弃。
        const hasBatch = genState.batch.length > 0;
        genState.phase = hasBatch ? "ready" : "idle";
        genState.noticeKey = hasBatch ? "generateStoppedReview" : null;
        if (!hasBatch) genState.jobId = null;
        renderGenerate();
        refreshStatus();
        return;
      }
      const hasBatch = genState.batch.length > 0;
      genState.phase = data.state === "failed" ? "failed" : (hasBatch ? "ready" : "idle");
      genState.errorCode = data.state === "failed" ? (data.error || "PROVIDER_HTTP") : null;
      genState.noticeKey = hasBatch ? "generateStoppedReview" : null;
      if (!hasBatch) genState.jobId = null;
      renderGenerate();
      refreshStatus();
    } catch (error) {
      if (token !== generationToken) return;
      genState.stopping = false;
      if (["JOB_LOST", "JOB_EXPIRED", "INVALID_JOB"].includes(error.code)) {
        const hasBatch = genState.batch.length > 0;
        genState.phase = hasBatch ? "ready" : "idle";
        genState.noticeKey = hasBatch ? "generateStoppedReview" : null;
        if (!hasBatch) genState.jobId = null;
      } else {
        genState.phase = "failed";
        genState.errorCode = error.code || "NETWORK";
        if (!genState.batch.length) genState.jobId = null;
      }
      renderGenerate();
    }
  }

  async function refreshStatus() {
    try {
      const previous = publicState;
      publicState = await api("/api/quiz/status");
      applyBankCounts(publicState.bank);
      renderStock();
      renderGenerate();
      renderBankCounts();
      renderMaintenance();
      renderSettingsAttention();
      updateToggleState();
      renderGateOccupation();
      if (adminToken && previous && previous.sessionState !== publicState.sessionState) {
        loadAdminSession().catch((error) => {
          if (error.code !== "ADMIN_EXPIRED" && error.code !== "ADMIN_REQUIRED") {
            $("settingsError").textContent = errorText(error);
          }
        });
      }
    } catch (_) {
      updateToggleState();
    }
  }

  async function submitPin(event) {
    event.preventDefault();
    $("gateError").textContent = "";
    $("pinBtn").disabled = true;
    try {
      const data = await api("/api/quiz/auth/pin", {
        method: "POST",
        body: { pin: $("pin").value.trim() },
      });
      adminToken = data.adminToken;
      adminExpiresAt = data.expiresAt;
      saveAuth();
      $("pin").value = "";
      await enterEditor();
    } catch (error) {
      if (error.code === "PARENT_IN_USE") {
        // The first phone logged in between the last status poll and this
        // submit; show the same notice instead of a misleading PIN error.
        if (publicState) publicState.parentInUse = true;
        renderGateOccupation();
      } else {
        $("gateError").textContent = errorText(error);
      }
    } finally {
      $("pinBtn").disabled = false;
    }
  }

  async function enterEditor() {
    $("settingsError").textContent = "";
    $("gateError").textContent = "";
    try {
      await refreshStatus();
      await loadAdminSession();
      await loadProvider();
      await loadGenerationPrefsFromTv();
    } catch (error) {
      if (error.code !== "ADMIN_EXPIRED" && error.code !== "ADMIN_REQUIRED") {
        // The editor is still hidden behind the PIN gate at this point, so an
        // error inside it would be invisible.
        $("gateError").textContent = errorText(error);
      }
      return;
    }
    if (!providerState.consentAccepted) {
      pendingEnter = true;
      openConsent();
      return;
    }
    pendingEnter = false;
    showEditor();
    setTab("generate", true);
  }

  function logout() {
    // Drop local auth immediately; revoking the token on the TV is best-effort
    // so declining the privacy gate never waits on the network.
    if (adminToken) {
      api("/api/quiz/auth/logout", { method: "POST", admin: true, body: {} })
        .catch(() => { /* the token may already be invalid */ });
    }
    clearAuth();
    // This phone released the slot; do not show the "another parent" notice
    // while the next status poll catches up with the revoked token.
    if (publicState) publicState.parentInUse = false;
    showPinGate();
  }

  async function loadBank(reset) {
    const request = ++bankRequest;
    if (reset) {
      bankItems = [];
      bankOffset = 0;
      bankHasMore = false;
    }
    bankLoading = true;
    $("bankError").textContent = "";
    renderBank();
    try {
      const data = await api(
        "/api/quiz/admin/bank?filter=" + encodeURIComponent(bankFilter) +
        "&offset=" + bankOffset + "&limit=" + BANK_PAGE_SIZE,
        { admin: true },
      );
      if (request !== bankRequest) return;
      const items = Array.isArray(data.items) ? data.items : [];
      bankItems = reset ? items : bankItems.concat(items);
      bankOffset = bankItems.length;
      bankHasMore = items.length >= Number(data.limit || BANK_PAGE_SIZE);
      applyBankCounts(data.counts);
      renderBankCounts();
      renderMaintenance();
      renderStock();
    } catch (error) {
      if (request !== bankRequest) return;
      if (error.code !== "ADMIN_EXPIRED" && error.code !== "ADMIN_REQUIRED") {
        $("bankError").textContent = errorText(error);
      }
    } finally {
      if (request === bankRequest) {
        bankLoading = false;
        renderBank();
      }
    }
  }

  function numberField(id, min, max, fallback) {
    const value = Number($(id).value);
    if (!Number.isInteger(value)) return fallback;
    return Math.min(max, Math.max(min, value));
  }

  function currentRoundWheelValue() {
    const fallback = Number(currentConfig().pullCount || DEFAULT_CONFIG.pullCount);
    if (!$("roundWheel") || !roundWheelItems.length) return fallback;
    const index = Math.round($("roundWheel").scrollTop / ROUND_ITEM_HEIGHT);
    const item = roundWheelItems[Math.min(roundWheelItems.length - 1, Math.max(0, index))];
    return item ? Number(item.textContent) : fallback;
  }

  function readSettingsConfig() {
    const base = currentConfig();
    const pull = currentRoundWheelValue();
    const scoring = readScoringPatch();
    return Object.assign({}, base, {
      defaultTimeLimitSec: configSeconds(base),
      pullCount: pull,
      // Legacy single-rate fields stay for cloud compatibility; local v5
      // rounds settle from the difficulty table below.
      minutesPerPoint: Number(base.minutesPerPoint || DEFAULT_CONFIG.minutesPerPoint),
      minutesPerQuestion: Number(base.minutesPerQuestion || DEFAULT_CONFIG.minutesPerQuestion),
      maxEarnedMinutes: numberField("maxEarned", 1, 180, Number(base.maxEarnedMinutes || 60)),
      // No longer parent-editable; keep the stored value for protocol compatibility.
      answerDisplaySec: Number(base.answerDisplaySec || DEFAULT_CONFIG.answerDisplaySec),
      shuffleOptions: $("shuffle").checked,
      pointsPerQuestion: 1,
    }, scoring);
  }

  // Levels save themselves immediately; the Save button only persists the
  // rules that apply to every level.
  async function saveSettings() {
    if (settingsSaving) return;
    if (!challengeConfig) {
      // 首次点保存时配置可能还在加载；等加载完自动继续，避免“要点两次”。
      $("settingsError").textContent = t("settingsLoading");
      loadAdminSession()
        .then(() => { if (challengeConfig) saveSettings(); })
        .catch((error) => { $("settingsError").textContent = errorText(error); });
      return;
    }
    const config = readSettingsConfig();
    settingsSaving = true;
    setButtonSaving($("saveAnswer"), true);
    $("settingsError").textContent = "";
    try {
      const data = await api("/api/quiz/admin/challenge-config", {
        method: "POST",
        admin: true,
        body: { config },
      });
      challengeConfig = normalizeConfig(data.config || config);
      if (Array.isArray(data.answerScopes)) applyAnswerLevels(data.answerScopes);
      answerDirty = false;
      syncState = data.syncState || syncState;
      renderSync();
      renderSettings();
      renderAnswerPage();
      $("settingsState").textContent = "";
      backToGenerateHome();
    } catch (error) {
      $("settingsError").textContent = errorText(error);
    } finally {
      settingsSaving = false;
      setButtonSaving($("saveAnswer"), false);
    }
  }

  async function clearBank(mode, confirmKey) {
    $("maintenanceError").textContent = "";
    $("maintenanceState").textContent = "";
    if (!confirm(t(confirmKey))) return;
    try {
      const data = await api("/api/quiz/admin/bank/clear", {
        method: "POST",
        admin: true,
        body: { mode },
      });
      applyBankCounts(data.counts);
      if (publicState && data.bankState) publicState.bankState = data.bankState;
      $("maintenanceState").textContent = t("clearDone");
      renderStock();
      renderBankCounts();
      renderMaintenance();
      await refreshStatus();
      if (activeTab === "bank") loadBank(true);
    } catch (error) {
      $("maintenanceError").textContent = errorText(error);
    }
  }

  async function toggleEnabled() {
    $("maintenanceError").textContent = "";
    $("maintenanceState").textContent = "";
    const enabled = Boolean($("toggleEnabled").checked);
    if (!enabled && !confirm(t("confirmDisable"))) {
      $("toggleEnabled").checked = true;
      return;
    }
    try {
      await api("/api/quiz/admin/" + (enabled ? "enable" : "disable"), {
        method: "POST",
        admin: true,
        body: {},
      });
      $("maintenanceState").textContent = t(enabled ? "enabled" : "disabled");
      await refreshStatus();
    } catch (error) {
      $("maintenanceError").textContent = errorText(error);
      updateToggleState();
    }
  }

  function updateRuleConfig(patch) {
    answerDirty = true;
    challengeConfig = Object.assign(currentConfig(), patch);
    renderSettingsChrome();
    renderHome();
  }

  function bind() {
    $("langSelect").addEventListener("change", () => applyLanguage($("langSelect").value));
    $("langSelectMe").addEventListener("change", () => applyLanguage($("langSelectMe").value));
    $("pinForm").addEventListener("submit", submitPin);
    $("pin").addEventListener("input", () => {
      $("pin").value = $("pin").value.replace(/\D/g, "").slice(0, 6);
    });
    $("syncRetry").addEventListener("click", retrySync);
    $("syncUseRemote").addEventListener("click", useRemoteConfig);
    $("meAi").addEventListener("click", () => openProviderDialog(false));
    $("mePrivacy").addEventListener("click", () => openModal("privacyInfoDialog"));
    $("logout").addEventListener("click", logout);
    $("acceptConsent").addEventListener("click", acceptConsent);
    $("declineConsent").addEventListener("click", logout);
    $("saveProvider").addEventListener("click", saveProvider);
    $("testProvider").addEventListener("click", testProviderConnection);
    bindReliableTap($("saveRange"), saveRange);
    $("openGenConfig").addEventListener("click", openGenConfigPage);
    $("openAnswerConfig").addEventListener("click", openAnswerConfigPage);
    $("backFromGen").addEventListener("click", backToGenerateHome);
    $("backFromAnswer").addEventListener("click", backFromAnswerPage);
    $("backFromQuestions").addEventListener("click", backToGenerateHome);
    $("openQuestions").addEventListener("click", openQuestionsPage);
    $("addSelected").addEventListener("click", addSelectedQuestions);
    bindReliableTap($("saveAnswer"), saveSettings);
    $("generate").addEventListener("click", () => startGeneration());
    $("cancelGenerate").addEventListener("click", cancelGeneration);
    $("alignGenToAnswer").addEventListener("click", () => {
      if (!alignGenerationToAnswerScope()) return;
      startGeneration({ skipScopePrompt: true });
    });
    $("editGenScope").addEventListener("click", openGenConfigPage);
    $("scopeMismatchAlign").addEventListener("click", () => {
      closeModal();
      if (!alignGenerationToAnswerScope()) return;
      startGeneration({ skipScopePrompt: true });
    });
    $("scopeMismatchKeep").addEventListener("click", () => {
      scopeMismatchAckKey = generationScopeSignature();
      closeModal();
      startGeneration({ skipScopePrompt: true });
    });

    $("selectAll").addEventListener("click", () => {
      const selectable = genState.batch
        .filter((item) => !item.duplicate)
        .map((item) => Number(item.index));
      const allSelected = selectable.length > 0 &&
        selectable.every((index) => selectedBatch.has(index));
      selectedBatch = allSelected ? new Set() : new Set(selectable);
      renderQuestions();
    });

    $("targetCounts").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-target]");
      if (!chip) return;
      targetCount = Number(chip.dataset.target);
      renderGenConfig(false);
      saveGeneratePrefs();
    });
    $("genGradeChips").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-grade]");
      if (!chip || chip.disabled) return;
      const grade = Number(chip.dataset.grade);
      if (!gradeNos().includes(grade) || cloudJuniorBlocked(grade)) return;
      genGradeValue = grade;
      genPromptDirty = false;
      renderGenConfig(true);
      saveGeneratePrefs();
    });
    $("genSemester").addEventListener("click", (event) => {
      const button = event.target.closest("[data-semester]");
      if (!button) return;
      genSemesterValue = Number(button.dataset.semester) === 2 ? 2 : 1;
      genPromptDirty = false;
      renderGenConfig(true);
      saveGeneratePrefs();
    });
    $("genTextbook").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-textbook]");
      if (!chip) return;
      genTextbookId = chip.dataset.textbook;
      genPromptDirty = false;
      renderGenConfig(true);
      saveGeneratePrefs();
    });
    $("genSubjects").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-subject]");
      if (!chip) return;
      if (!subjectIdsForGrade(genGradeValue).includes(chip.dataset.subject)) return;
      genSubjectValue = chip.dataset.subject;
      keepGenerationTextbook();
      genPromptDirty = false;
      renderGenConfig(true);
      saveGeneratePrefs();
    });
    // 题目要求是纯自由输入；上方样例只是快捷填充，不是下拉选项。
    $("genRequirementSamples").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-requirement]");
      if (!chip) return;
      genPromptDirty = false;
      setRequirement(chip.dataset.requirement);
    });
    $("genRequirement").addEventListener("input", () => {
      setRequirement($("genRequirement").value, { keepInput: true });
    });
    $("genAutoAdd").addEventListener("change", () => {
      genAutoAddValue = Boolean($("genAutoAdd").checked);
      renderHome();
      saveGeneratePrefs();
    });
    $("genDifficultyChips").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-difficulty]");
      if (!chip) return;
      genDifficultyValue = chip.dataset.difficulty;
      genPromptDirty = false;
      renderGenConfig(true);
      saveGeneratePrefs();
    });
    $("genPrompt").addEventListener("input", () => {
      genPromptDirty = $("genPrompt").value.trim() !== composeGenerationPrompt();
      updatePromptBadge();
    });
    $("genOptionsChips").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-options]");
      if (!chip) return;
      genOptionsValue = Number(chip.dataset.options);
      renderGenConfig(false);
      saveGeneratePrefs();
    });

    $("answerTabs").addEventListener("click", (event) => {
      const button = event.target.closest("[data-answer-tab]");
      if (!button) return;
      setAnswerTab(button.dataset.answerTab);
    });
    $("addLevel").addEventListener("click", () => openLevelEditor(null));
    $("backFromLevel").addEventListener("click", backFromLevelEditor);
    $("levelList").addEventListener("click", (event) => {
      const edit = event.target.closest("[data-level-edit]");
      if (edit) {
        openLevelEditor(edit.dataset.levelEdit);
        return;
      }
      const up = event.target.closest("[data-level-up]");
      if (up && !up.disabled) {
        moveLevel(up.dataset.levelUp, -1);
        return;
      }
      const down = event.target.closest("[data-level-down]");
      if (down && !down.disabled) {
        moveLevel(down.dataset.levelDown, 1);
        return;
      }
      const remove = event.target.closest("[data-level-delete]");
      if (remove && !remove.disabled) deleteLevel(remove.dataset.levelDelete);
    });
    $("levelUndoButton").addEventListener("click", undoDeleteLevel);
    $("levelName").addEventListener("input", () => {
      levelNameDraft = $("levelName").value;
    });
    bindReliableTap($("saveLevel"), saveLevelDraft);
    $("levelEditorPage").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-scope-step]");
      if (!chip) return;
      selectScopeValue(chip.dataset.scopeStep, chip.dataset.scopeValue);
    });
    $("levelEmptyGen").addEventListener("click", () => {
      levelEditingId = null;
      customScope = null;
      resetScopeOptions();
      backToGenerateHome();
    });
    $("levelEmptyConfig").addEventListener("click", openGenConfigPage);

    $("roundWheel").addEventListener("scroll", () => {
      const wheel = $("roundWheel");
      const index = Math.round(wheel.scrollTop / ROUND_ITEM_HEIGHT);
      if (Number(wheel.dataset.index) !== index) paintRoundWheel(index);
      if (roundWheelScrollTimer) clearTimeout(roundWheelScrollTimer);
      roundWheelScrollTimer = setTimeout(settleRoundWheel, 120);
    }, { passive: true });
    $("roundWheel").addEventListener("keydown", (event) => {
      const current = Number($("roundWheel").dataset.index || 0);
      const last = Math.max(0, roundWheelItems.length - 1);
      let next = null;
      if (event.key === "ArrowDown") next = Math.min(last, current + 1);
      else if (event.key === "ArrowUp") next = Math.max(0, current - 1);
      else if (event.key === "Home") next = 0;
      else if (event.key === "End") next = last;
      if (next === null) return;
      event.preventDefault();
      commitRoundWheel(next);
    });
    $("roundWheelTrack").addEventListener("click", (event) => {
      const item = event.target.closest(".round-wheel-item");
      if (!item) return;
      const index = Number(item.dataset.index);
      if (!Number.isInteger(index)) return;
      $("roundWheel").focus({ preventScroll: true });
      commitRoundWheel(index);
    });
    $("timeCounts").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-seconds]");
      if (!chip) return;
      updateRuleConfig({ defaultTimeLimitSec: Number(chip.dataset.seconds) });
    });
    for (const id of [
      "easyReward", "mediumReward", "hardReward",
      "easyPenalty", "mediumPenalty", "hardPenalty",
    ]) {
      $(id).addEventListener("input", () => updateRuleConfig(readScoringPatch()));
    }
    $("minUnlock").addEventListener("input", () => {
      updateRuleConfig({ minUnlockMinutes: numberField("minUnlock", 0, 180, 0) });
    });
    $("unlockCounts").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-unlock]");
      if (!chip) return;
      $("minUnlock").value = chip.dataset.unlock;
      updateRuleConfig({ minUnlockMinutes: Number(chip.dataset.unlock) });
    });
    $("resetScoring").addEventListener("click", resetScoringDefaults);
    $("maxEarned").addEventListener("change", () => {
      updateRuleConfig({ maxEarnedMinutes: numberField("maxEarned", 1, 180, 60) });
    });
    $("shuffle").addEventListener("change", () => {
      updateRuleConfig({ shuffleOptions: $("shuffle").checked });
    });

    $("bankFilters").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-filter]");
      if (!chip) return;
      bankFilter = chip.dataset.filter;
      renderBank();
      loadBank(true);
    });
    $("refreshBank").addEventListener("click", () => loadBank(true));
    $("bankMore").addEventListener("click", () => loadBank(false));

    $("clearUnused").addEventListener("click", () => clearBank("unused", "confirmClearUnused"));
    $("clearAnswered").addEventListener("click", () => clearBank("answered", "confirmClearAnswered"));
    $("clearWrong").addEventListener("click", () => clearBank("wrong", "confirmClearWrong"));
    $("clearAll").addEventListener("click", () => clearBank("all", "confirmClearAll"));
    $("toggleEnabled").addEventListener("change", toggleEnabled);

    document.querySelectorAll(".nav-item[data-tab]").forEach((button) => {
      button.addEventListener("click", () => setTab(button.dataset.tab, false));
    });

    $("modalRoot").addEventListener("click", (event) => {
      if (activeModal && $(activeModal).dataset.staticModal === "true") return;
      if (event.target.closest("[data-close-modal]")) closeModal();
    });
    document.addEventListener("keydown", modalKeydown);
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden) refreshStatus();
    });
    setInterval(() => {
      if (!document.hidden) refreshStatus();
    }, 5000);
  }

  async function init() {
    loadAuth();
    applyI18n();
    bind();
    loadGeneratePrefs();
    syncGenerateComposer(true);
    await refreshStatus();
    if (adminToken && Date.now() < adminExpiresAt) {
      await enterEditor();
    } else {
      clearAuth();
      showPinGate();
    }
  }

  init();
})();
