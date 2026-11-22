(() => {
  "use strict";

  const TEXTS = {
    zh: {
      eyebrow: "家长设置 · 局域网",
      title: "家长网页",
      status_loading: "正在连接电视…",
      status_none: "电视上还没有闯关",
      status_draft: "草稿已保存",
      status_ready: "闯关已就绪",
      status_running: "闯关进行中",
      status_settling: "正在结算",
      status_starting: "正在开始",
      status_start_failed: "启动失败",
      status_expired: "配置已过期",
      status_unreadable: "答题数据读取失败",
      status_blocked: "当前不可用",
      detail_none: "先在电视锁屏点击「开始答题闯关」，再扫描二维码。",
      detail_draft: "草稿只在网页和电视暂存，发布后锁屏才会出现入口。",
      detail_ready: "在电视锁屏点击「开始答题闯关」即可开始。",
      detail_running: "本轮在电视上进行，网页只读；结束后才能换题。",
      detail_expired: "二维码或闯关已过期，请在电视上重新打开设置页。",
      detail_unreadable: "请联系家长在电视上重置答题数据。",
      detail_blocked: "当前没有可开始的闯关。",
      readyDetail: "{n} 道题，至少答对 {m} 道。",
      gate_scan_title: "请从电视扫码",
      gate_scan_text: "打开电视锁屏上的「开始答题闯关」，用手机扫描电视上的专用二维码。",
      gate_pair_title: "家长验证",
      gate_pair_text: "请输入电视上的 6 位家长密码。验证后当天内再次打开无需输入；次日 0 点后需要重新验证。临时解锁只作用于电视锁屏。",
      gate_pair_expired: "二维码已过期，请在电视上刷新后重新扫码。",
      gate_in_use_badge: "使用中",
      gate_in_use_heading: "另一台手机正在使用",
      gate_in_use_lead: "家长设置同一时间只能由一台手机修改。",
      gate_in_use_title: "约 10 秒后自动恢复",
      gate_in_use_text: "若对方已离开，本页会自动更新；\n对方点「退出家长设置」可立即释放。",
      gate_takeover_button: "仍要接管",
      gate_takeover_note: "需要家长密码；对方手机会立即退出。",
      webDisabledTitleBound: "网页已停用",
      webDisabledTitleBinding: "正在绑定远程",
      webDisabledTitleNoChild: "网页家长中心不可用",
      webDisabledTitleNotReady: "电视正在启动",
      webDisabledBoundText: "这台电视已绑定远程。远程完全接管本地策略，网页只在本地模式且未绑定时可用。请在「家长端 App」→ 设备守护 → 电视设备 中修改。",
      webDisabledBindingText: "电视正在绑定远程。绑定完成后网页会自动停用；请在家长端 App 完成绑定和后续设置。",
      webDisabledNoChildText: "网页只在本地儿童模式下可用。请先在电视「家长中心」开启儿童模式；开启后电视右侧会出现手机配置二维码。",
      webDisabledNotReadyText: "电视正在启动，请稍后刷新。",
      webDisabledStateBound: "当前：远程模式 / 已绑定",
      webDisabledStateBinding: "当前：远程模式 / 绑定中",
      webDisabledStateNoChild: "当前：本地模式 / 儿童模式关闭",
      webDisabledStateNotReady: "当前：电视未就绪",
      webDisabledRefresh: "刷新状态",
      webDisabledRefreshFailed: "连接电视失败，请确认手机和电视在同一 Wi-Fi 后重试。",
      webDisabledHint: "网页只在电视开启本地儿童模式且未绑定远程时可用。",
      pinLabel: "请输入家长密码",
      pinButton: "进入设置",
      forgotPin: "忘记密码？",
      recoveryTitle: "用恢复码重置密码",
      recoveryLead: "输入首次开启儿童模式时保存的 12 位恢复码，再设置新的 6 位家长密码。恢复码只显示过一次，网页和电视都无法找回。",
      recoveryCodeLabel: "恢复码",
      recoveryNewPinLabel: "新的家长密码（6 位数字）",
      recoveryConfirmLabel: "再次输入新密码",
      recoverySubmit: "重置密码",
      recoveryCancel: "返回登录",
      recoveryDone: "家长密码已重置，请用新密码登录。",
      recoveryMismatch: "两次输入的新密码不一致。",
      recoveryFormat: "请输入 12 位恢复码。",
      err_RECOVERY_INVALID: "恢复码不正确。",
      err_RECOVERY_LOCKED: "恢复码错误次数过多，请稍后再试。",
      err_RECOVERY_LOCKED_RETRY: "恢复码错误次数过多，请在 {n} 秒后重试。",
      err_RECOVERY_NOT_SET: "这台电视没有保存恢复码，无法在网页重置。恢复码只在首次开启儿童模式时显示一次。",
      err_RECOVERY_FAILED: "重置失败，请重试。",
      err_NEW_PIN_INVALID: "新密码必须是 6 位数字。",
      err_PIN_TOO_SIMPLE: "请不要使用 123456 或 666666 这类简单密码。",
      editor_locked: "本轮闯关进行中，网页只能查看摘要。",
      challengeTitle: "本次闯关",
      challengeHint: "题目只属于本次闯关；发布后电视锁屏才会显示入口。",
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
      addToList: "加入本次闯关",
      emptyList: "还没有题目，先添加一道题。",
      remove: "移除",
      settingsTitle: "闯关规则",
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
      deleteSession: "删除闯关",
      logout: "退出家长设置",
      published: "已发布到电视。请在电视锁屏开始闯关。",
      deleted: "闯关已删除。",
      confirmDelete: "确定删除当前闯关？",
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
      err_SESSION_RUNNING: "闯关进行中，暂时不能修改。",
      err_SESSION_EXISTS: "已有一套就绪的闯关，请先删除或勾选替换。",
      err_SESSION_CONFLICT: "电视上的闯关内容与本次不一致，请重新打开设置。",
      err_STORAGE_ERROR: "电视保存失败，请重试。",
      err_DOCUMENT_UNREADABLE: "电视上的答题配置已损坏，请重新保存一次。",
      err_INVALID_DOCUMENT: "答题规则不符合要求，请检查后重试。",
      err_REVISION_CONFLICT: "配置已被其他页面修改，请重新打开设置。",
      err_UNSUPPORTED: "当前电视不支持这些答题设置。",
      err_DOCUMENT_PROJECTION_FAILED: "保存后的配置无法生效，请重试。",
      err_NOT_FOUND: "接口不存在或版本不匹配。",
      err_NETWORK: "无法连接电视，请确认手机和电视在同一局域网。",
      aiTitle: "AI 出题",
      aiIntro: "让模型生成选择题，再由你挑选加入本次闯关。也可以完全手动出题。",
      aiUnsupported: "此电视的 Android 版本不支持安全保存 API 密钥，因此只能手动出题。",
      aiUnsupportedBadge: "不可用",
      consentText: "我同意将出题要求（主题、年级、难度、数量等）发送给模型服务；不会发送家庭信息。API 密钥只保存在电视本地（系统支持时加密），不会回传给手机页面，也不会保存到本产品的云端；生成题目时只随请求发送给你配置的模型服务商。若开启 DeepSeek 语音播报，题目、选项、答案和解题过程文本还会发送给 DeepSeek 语音服务。",
      consentHint: "不勾选时只能手动出题；撤回授权会删除电视上保存的密钥。",
      manualNote: "手动添加的题目只保存在电视上，不会上传。",
      baseUrl: "模型服务地址（HTTPS）",
      model: "模型名称",
      apiKey: "API 密钥",
      keyHintSaved: "已保存密钥，不会回显；留空表示沿用。",
      keyHintNew: "密钥只保存在电视本地（系统支持时加密）。",
      deepseekKeyHint: "默认服务是 DeepSeek。没有 Key？可在 DeepSeek 开放平台创建（可选，费用由你与该平台结算）。",
      providerPresetLabel: "快捷填入服务地址",
      providerPresetNote: "仅为示例，非官方合作；费用和数据由所选服务商处理。",
      providerPresetHintDeepSeek: "默认模型 deepseek-flash；当前配置已测试正常。",
      providerPresetHintKimi: "例：kimi-k3.1-pro / kimi-k3。K3 系列为思考模型，题量多时可能被截断。",
      providerPresetHintGlm: "免费模型 glm-4.7-flash 已关闭思考，约 2 分钟/5 题；测试连接只检查 /models，不代表能出题。",
      providerPresetHintQwen: "例：qwen-plus / qwen3-max。新账号可能使用专属业务空间地址；模型以控制台为准。",
      providerPresetHintDoubao: "例：doubao-seed-2-1-pro-260915，或控制台的接入点 ID（ep-...）。模型带日期，迭代较快。",
      providerPresetHintHunyuan: "例：hunyuan-turbos-latest / hunyuan-t1-latest。旧接口仍可用；新账号可改用 TokenHub 地址。",
      providerPresetHintErnie: "例：ernie-5.1 / ernie-4.5-turbo-128k。旧模型可能下线，以控制台模型列表为准。",
      providerPresetHintSpark: "例：generalv3.5 / 4.0Ultra。密钥填控制台的 APIPassword（APIKey:APISecret）。",
      providerPresetHintMiniMax: "例：MiniMax-M2 / MiniMax-M3。M2.x 始终思考；结构化输出可能不稳定。",
      providerPresetHintLongCat: "例：LongCat-Flash-Chat / LongCat-2.5。模型更新较快，以 /models 返回为准。",
      providerPresetHintStepFun: "例：step-3.5-flash / step-5-preview。Step Plan 订阅需使用不同的服务地址。",
      providerPresetHintCustom: "填写服务商提供的模型 ID，以服务商控制台或 /models 为准。",
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
      tbXinanda: "西南大学版",
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
      genCopyPrompt: "复制提示",
      genCopyPromptDone: "已复制",
      genCopyPromptFailed: "复制失败",
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
      genQuestionType: "题型",
      genTypeSingle: "单选",
      genTypeMultiple: "多选",
      genTypeMixed: "混合",
      genTypeHint: "多选和混合题在电视上需要按“确认答案”提交；生成后请核对正确答案。",
      genMultiReviewWarning: "本次生成包含多选题。AI 可能漏选正确项，请逐题核对后再入库。",
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
      err_PROVIDER_KEY_INVALID: "API 密钥无效，请检查是否粘贴完整后重试。",
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
      err_PROVIDER_TEST_UNSUPPORTED: "该服务未提供自动检测接口（地址也可能有误）；可保存后直接出题验证。",
      err_PROVIDER_NOT_CONFIGURED: "请先配置 AI 出题。",
      err_AI_EMPTY: "模型没有返回有效题目，请重试。",
      err_BUSY: "已有生成任务在进行。",
      err_JOB_EXPIRED: "生成任务已过期，请重新生成。",
      err_JOB_LOST: "生成任务不存在，请重新生成。",
      err_INVALID_JOB: "生成任务编号无效。",
      err_INVALID_REQUEST: "出题参数不正确。",
      warningsLabel: "提示",
      qErrTooMany: "一次闯关最多 10 道题。",
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
      rulesSaved: "闯关规则已保存。",
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
      privacyTitle: "使用 AI 出题前，请了解数据如何保存",
      privacyIntro: "只有使用 AI 出题或配置 AI 服务时才需要确认；手动出题和预置题库不需要 API 密钥。",
      privacyLocalTitle: "保存在电视本地",
      privacyLocal: "题目、答案和进度只保存在电视上，闯关结束即删除。",
      privacyAiTitle: "AI 出题",
      privacyAi: "主题、年级、难度等出题要求会发送给你配置的模型服务商；不会发送家庭信息。开启 DeepSeek 语音播报时，题目、选项、答案和解题过程文本也会发送给 DeepSeek 语音服务。",
      privacyKeyTitle: "API 密钥",
      privacyKey: "密钥只保存在电视本地（系统支持时加密），不会回显给手机页面，也不会保存到本产品的云端；生成题目时只随请求发送给你配置的模型服务商。语音播报仅使用 DeepSeek 官方服务地址。",
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
      cacheRebuild: "按当前闯关重建缓存",
      cacheClear: "清空未使用题目",
      cacheRebuilt: "缓存已重建。",
      cacheCleared: "未使用题目已清空。",
      confirmClearCache: "确定清空尚未使用的题目？已发布但未开始的闯关也会受影响。",
      err_NO_SESSION: "当前没有可重建的闯关。",
      err_LEGACY_SESSION: "旧版闯关不能重建缓存，请重新发布。",
      err_CACHE_UNAVAILABLE: "题目缓存暂不可用，请稍后再试。",
      err_SESSION_RUNNING: "答题进行中，暂不能操作缓存。",
      err_NOT_READY: "当前闯关还没有发布，无法重建缓存。",
      err_INVALID_CONFIG: "闯关规则不符合要求。",
      footer: "答题闯关只在电视上开始和结算；临时解锁仅在电视锁屏时可用，关屏或重启后恢复守护。",
    },
    en: {
      eyebrow: "Parent setup · Local network",
      title: "Parent web page",
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
      gate_pair_text: "Enter the 6-digit parent password shown on the TV. After verification, this phone can reopen the page without a PIN until local midnight. The temporary unlock only applies on the TV lock screen.",
      gate_pair_expired: "The QR code expired. Refresh it on the TV and scan again.",
      gate_in_use_badge: "In use",
      gate_in_use_heading: "Another phone is in use",
      gate_in_use_lead: "Only one phone can change parent settings at a time.",
      gate_in_use_title: "Auto-resumes in about 10 seconds",
      gate_in_use_text: "If the other phone has left, this page refreshes automatically.\nExit parent settings there to release it now.",
      gate_takeover_button: "Take over",
      gate_takeover_note: "Parent password required; the other phone is signed out immediately.",
      webDisabledTitleBound: "Web page disabled",
      webDisabledTitleBinding: "Remote binding in progress",
      webDisabledTitleNoChild: "Web parent center unavailable",
      webDisabledTitleNotReady: "The TV is starting",
      webDisabledBoundText: "This TV is bound to remote management. The phone app now owns the local policy; this page works only in local mode and while unbound. Change settings in the parent app under Device guard → TV devices.",
      webDisabledBindingText: "The TV is binding to remote management. This page turns off after binding completes; finish binding and later settings in the parent app.",
      webDisabledNoChildText: "This page works only with local child mode. Turn on child mode in the TV's Parent Center first; the phone setup QR appears on the TV once it is on.",
      webDisabledNotReadyText: "The TV is still starting. Refresh in a moment.",
      webDisabledStateBound: "Now: remote mode / bound",
      webDisabledStateBinding: "Now: remote mode / binding",
      webDisabledStateNoChild: "Now: local mode / child mode off",
      webDisabledStateNotReady: "Now: TV not ready",
      webDisabledRefresh: "Refresh status",
      webDisabledRefreshFailed: "Could not reach the TV. Check that the phone and TV share the same Wi-Fi and try again.",
      webDisabledHint: "This page is available only while local child mode is on and the TV is not bound to remote management.",
      pinLabel: "Enter parent password",
      pinButton: "Open settings",
      forgotPin: "Forgot password?",
      recoveryTitle: "Reset with a recovery code",
      recoveryLead: "Enter the 12-digit recovery code saved when child mode was first enabled, then choose a new 6-digit parent password. The code was shown only once; neither this page nor the TV can retrieve it.",
      recoveryCodeLabel: "Recovery code",
      recoveryNewPinLabel: "New parent password (6 digits)",
      recoveryConfirmLabel: "Enter the new password again",
      recoverySubmit: "Reset password",
      recoveryCancel: "Back to sign-in",
      recoveryDone: "The parent password was reset. Sign in with the new password.",
      recoveryMismatch: "The new passwords do not match.",
      recoveryFormat: "Enter the 12-digit recovery code.",
      err_RECOVERY_INVALID: "The recovery code is incorrect.",
      err_RECOVERY_LOCKED: "Too many wrong recovery codes. Try again later.",
      err_RECOVERY_LOCKED_RETRY: "Too many wrong recovery codes. Try again in {n}s.",
      err_RECOVERY_NOT_SET: "This TV has no saved recovery code, so it cannot be reset from this page. The code is shown only once, when child mode is first enabled.",
      err_RECOVERY_FAILED: "The reset failed. Try again.",
      err_NEW_PIN_INVALID: "The new password must be 6 digits.",
      err_PIN_TOO_SIMPLE: "Do not use a simple password such as 123456 or 666666.",
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
      logout: "Exit parent settings",
      published: "Published to the TV. Start it from the lock screen.",
      deleted: "Challenge deleted.",
      confirmDelete: "Delete the current challenge?",
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
      err_DOCUMENT_UNREADABLE: "The quiz configuration on this TV is damaged. Save it again from this page.",
      err_INVALID_DOCUMENT: "The quiz rules are not valid. Check them and try again.",
      err_REVISION_CONFLICT: "The configuration changed in another page. Reopen settings.",
      err_UNSUPPORTED: "This TV does not support these quiz settings.",
      err_DOCUMENT_PROJECTION_FAILED: "The saved configuration cannot take effect. Try again.",
      err_NOT_FOUND: "The endpoint is missing or the version does not match.",
      err_NETWORK: "Cannot reach the TV. Check that the phone and TV are on the same LAN.",
      aiTitle: "AI questions",
      aiIntro: "Let a model draft multiple-choice questions, then choose which ones join this challenge. Manual questions work without AI.",
      aiUnsupported: "This TV's Android version cannot store an API key securely, so only manual questions are available.",
      aiUnsupportedBadge: "Unavailable",
      consentText: "I agree that question requirements (subject, grade, difficulty, count, and similar settings) may be sent to the model service. No family information is sent. The API key is stored only on the TV (encrypted when the system supports it), is never sent back to the phone page, and is never saved to our cloud; when generating questions it is sent only to the model provider you configure. When DeepSeek voice reading is enabled, question, option, answer, and explanation text is also sent to DeepSeek speech.",
      consentHint: "Without consent only manual questions are available. Revoking consent deletes the saved key from the TV.",
      manualNote: "Questions added manually are stored only on the TV and are not uploaded.",
      baseUrl: "Model service URL (HTTPS)",
      model: "Model name",
      apiKey: "API key",
      keyHintSaved: "A key is saved and never shown; leave this blank to keep it.",
      keyHintNew: "The key is stored only on the TV, encrypted when the system supports it.",
      deepseekKeyHint: "DeepSeek is the default. Need a key? Create one on the DeepSeek platform (optional; billed by DeepSeek).",
      providerPresetLabel: "Quick fill service URL",
      providerPresetNote: "Examples only, not an endorsement; fees and data are handled by the selected provider.",
      providerPresetHintDeepSeek: "Default model is deepseek-flash; this setup is tested.",
      providerPresetHintKimi: "e.g. kimi-k3.1-pro / kimi-k3. The K3 series always thinks; large batches may be truncated.",
      providerPresetHintGlm: "Free glm-4.7-flash with thinking disabled takes about 2 minutes per 5 questions; the connection test only checks /models, not generation.",
      providerPresetHintQwen: "e.g. qwen-plus / qwen3-max. New accounts may need a workspace-specific host; check the console.",
      providerPresetHintDoubao: "e.g. doubao-seed-2-1-pro-260915, or an Ark endpoint ID (ep-...). Dated model IDs change often.",
      providerPresetHintHunyuan: "e.g. hunyuan-turbos-latest / hunyuan-t1-latest. The legacy endpoint still works; new accounts may use TokenHub.",
      providerPresetHintErnie: "e.g. ernie-5.1 / ernie-4.5-turbo-128k. Older models may retire; check the console list.",
      providerPresetHintSpark: "e.g. generalv3.5 / 4.0Ultra. Use the console APIPassword (APIKey:APISecret) as the key.",
      providerPresetHintMiniMax: "e.g. MiniMax-M2 / MiniMax-M3. M2.x always thinks; structured output may be unreliable.",
      providerPresetHintLongCat: "e.g. LongCat-Flash-Chat / LongCat-2.5. Model IDs change quickly; check /models.",
      providerPresetHintStepFun: "e.g. step-3.5-flash / step-5-preview. Step Plan subscriptions use a different host.",
      providerPresetHintCustom: "Enter the model ID from the provider console or /models.",
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
      tbXinanda: "SWU edition",
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
      genCopyPrompt: "Copy prompt",
      genCopyPromptDone: "Copied",
      genCopyPromptFailed: "Copy failed",
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
      genQuestionType: "Question type",
      genTypeSingle: "Single answer",
      genTypeMultiple: "Multiple answers",
      genTypeMixed: "Mixed",
      genTypeHint: "Multiple-answer questions need a confirm press on the TV. Review the answer key after generating.",
      genMultiReviewWarning: "This batch has multiple-answer questions. AI may miss a correct option; review each answer key before adding them.",
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
      err_PROVIDER_KEY_INVALID: "The API key is not valid. Check that it was pasted in full and try again.",
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
      err_PROVIDER_TEST_UNSUPPORTED: "This service has no automatic test endpoint (the URL may also be wrong). Save and try generating to verify.",
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
      privacyTitle: "Before using AI questions",
      privacyIntro: "Confirm only when you generate with AI or configure an AI service. Manual questions and preset banks need no API key.",
      privacyLocalTitle: "Stored on the TV",
      privacyLocal: "Questions, answers, and progress stay on the TV and are deleted when the challenge ends.",
      privacyAiTitle: "AI questions",
      privacyAi: "The subject, grade, difficulty, and similar requirements are sent to the model provider you configure. No family information is sent. If DeepSeek voice reading is enabled, question, option, answer, and explanation text is also sent to DeepSeek speech.",
      privacyKeyTitle: "API key",
      privacyKey: "The key is stored only on the TV (encrypted when the system supports it) and is never shown again on this page. It is never saved to our cloud; when generating questions it is sent only to the model provider you configure. Voice reading uses only the official DeepSeek endpoint.",
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
      footer: "Quizzes start and settle on the TV only; temporary unlock is available only on the TV lock screen and ends when the screen turns off or the TV restarts.",
    },
    ru: {
      eyebrow: "Настройка родителей · Локальная сеть",
      title: "Веб-страница для родителей",
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
      gate_pair_text: "Введите 6-значный пароль родителя с телевизора. После проверки страница открывается без пароля до местной полуночи. Временная разблокировка действует только на экране блокировки телевизора.",
      gate_pair_expired: "QR-код устарел. Обновите его на телевизоре и отсканируйте снова.",
      gate_in_use_badge: "Используется",
      gate_in_use_heading: "Другой телефон сейчас использует настройки",
      gate_in_use_lead: "Настройки родителя можно менять только с одного телефона одновременно.",
      gate_in_use_title: "Восстановится примерно через 10 секунд",
      gate_in_use_text: "Если другой телефон уже вышел, страница обновится сама.\nВыход из настроек освобождает доступ сразу.",
      gate_takeover_button: "Перехватить управление",
      gate_takeover_note: "Нужен пароль родителя; другой телефон сразу выйдет из настроек.",
      webDisabledTitleBound: "Веб-страница отключена",
      webDisabledTitleBinding: "Идёт привязка к удалённому управлению",
      webDisabledTitleNoChild: "Веб-центр родителя недоступен",
      webDisabledTitleNotReady: "Телевизор запускается",
      webDisabledBoundText: "Этот телевизор привязан к удалённому управлению. Удалённое управление полностью определяет локальную политику; страница доступна только в локальном режиме и без привязки. Изменяйте настройки в приложении родителя: Защита устройств → Телевизоры.",
      webDisabledBindingText: "Телевизор привязывается к удалённому управлению. После завершения привязки страница отключится; завершите привязку и дальнейшие настройки в приложении родителя.",
      webDisabledNoChildText: "Страница работает только в локальном детском режиме. Сначала включите детский режим в «Центре родителя» на телевизоре; после этого справа появится QR-код для настройки с телефона.",
      webDisabledNotReadyText: "Телевизор ещё запускается. Обновите страницу чуть позже.",
      webDisabledStateBound: "Сейчас: удалённый режим / привязан",
      webDisabledStateBinding: "Сейчас: удалённый режим / привязка",
      webDisabledStateNoChild: "Сейчас: локальный режим / детский режим выключен",
      webDisabledStateNotReady: "Сейчас: телевизор не готов",
      webDisabledRefresh: "Обновить состояние",
      webDisabledRefreshFailed: "Не удалось связаться с телевизором. Убедитесь, что телефон и телевизор в одной сети Wi-Fi, и повторите.",
      webDisabledHint: "Страница доступна, только когда на телевизоре включён локальный детский режим и нет привязки к удалённому управлению.",
      pinLabel: "Введите пароль родителя",
      pinButton: "Открыть настройки",
      forgotPin: "Забыли пароль?",
      recoveryTitle: "Сброс по коду восстановления",
      recoveryLead: "Введите 12-значный код восстановления, сохранённый при первом включении детского режима, и задайте новый 6-значный пароль родителя. Код показывался только один раз; ни страница, ни телевизор не могут его восстановить.",
      recoveryCodeLabel: "Код восстановления",
      recoveryNewPinLabel: "Новый пароль родителя (6 цифр)",
      recoveryConfirmLabel: "Повторите новый пароль",
      recoverySubmit: "Сбросить пароль",
      recoveryCancel: "Назад ко входу",
      recoveryDone: "Пароль родителя сброшен. Войдите с новым паролем.",
      recoveryMismatch: "Пароли не совпадают.",
      recoveryFormat: "Введите 12-значный код восстановления.",
      err_RECOVERY_INVALID: "Неверный код восстановления.",
      err_RECOVERY_LOCKED: "Слишком много неверных кодов восстановления. Попробуйте позже.",
      err_RECOVERY_LOCKED_RETRY: "Слишком много неверных кодов восстановления. Повторите через {n} с.",
      err_RECOVERY_NOT_SET: "На этом телевизоре нет кода восстановления, поэтому сброс со страницы невозможен. Код показывается только один раз при первом включении детского режима.",
      err_RECOVERY_FAILED: "Не удалось сбросить пароль. Повторите попытку.",
      err_NEW_PIN_INVALID: "Новый пароль должен состоять из 6 цифр.",
      err_PIN_TOO_SIMPLE: "Не используйте простой пароль вроде 123456 или 666666.",
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
      logout: "Выйти из настроек",
      published: "Опубликовано на телевизоре. Начните с экрана блокировки.",
      deleted: "Викторина удалена.",
      confirmDelete: "Удалить текущую викторину?",
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
      err_DOCUMENT_UNREADABLE: "Конфигурация викторины на телевизоре повреждена. Сохраните её заново на этой странице.",
      err_INVALID_DOCUMENT: "Правила викторины неверны. Проверьте их и повторите.",
      err_REVISION_CONFLICT: "Конфигурация изменена на другой странице. Откройте настройку снова.",
      err_UNSUPPORTED: "Этот телевизор не поддерживает такие настройки викторины.",
      err_DOCUMENT_PROJECTION_FAILED: "Сохранённая конфигурация не может вступить в силу. Повторите.",
      err_NOT_FOUND: "Интерфейс не найден или версия не совпадает.",
      err_NETWORK: "Нет связи с телевизором. Проверьте локальную сеть.",
      aiTitle: "ИИ-вопросы",
      aiIntro: "Модель создаёт вопросы с вариантами, а вы выбираете нужные для этой викторины. Можно добавлять вопросы вручную.",
      aiUnsupported: "Эта версия Android на телевизоре не позволяет безопасно хранить ключ API, поэтому доступны только вопросы, созданные вручную.",
      aiUnsupportedBadge: "Недоступно",
      consentText: "Я согласен отправлять параметры задания (тема, класс, сложность, количество) сервису модели; семейные данные не отправляются. Ключ API хранится только на телевизоре (шифруется, если система это поддерживает), не возвращается на страницу телефона и не сохраняется в облаке продукта; при генерации вопросов он отправляется только выбранному вами сервису модели. Если включено озвучивание DeepSeek, текст вопроса, вариантов, ответа и решения также отправляется в службу синтеза речи DeepSeek.",
      consentHint: "Без согласия доступны только ручные вопросы. Отзыв согласия удалит ключ с телевизора.",
      manualNote: "Вопросы, добавленные вручную, хранятся только на телевизоре и не отправляются в интернет.",
      baseUrl: "Адрес сервиса модели (HTTPS)",
      model: "Название модели",
      apiKey: "Ключ API",
      keyHintSaved: "Ключ сохранён и не показывается; оставьте поле пустым, чтобы сохранить его.",
      keyHintNew: "Ключ хранится только на телевизоре; шифрование используется, если система его поддерживает.",
      deepseekKeyHint: "По умолчанию используется DeepSeek. Нет ключа? Создайте его на платформе DeepSeek (необязательно; оплачивается DeepSeek).",
      providerPresetLabel: "Быстрое заполнение адреса",
      providerPresetNote: "Только примеры, не официальное партнёрство; оплату и данные обрабатывает выбранный сервис.",
      providerPresetHintDeepSeek: "Модель по умолчанию — deepseek-flash; эта конфигурация проверена.",
      providerPresetHintKimi: "Например, kimi-k3.1-pro / kimi-k3. Серия K3 всегда размышляет; большие наборы могут обрезаться.",
      providerPresetHintGlm: "Бесплатная glm-4.7-flash с отключённым размышлением обрабатывает 5 вопросов примерно за 2 минуты; проверка подключения проверяет только /models, а не генерацию.",
      providerPresetHintQwen: "Например, qwen-plus / qwen3-max. Новым аккаунтам может понадобиться отдельный адрес рабочего пространства; проверьте консоль.",
      providerPresetHintDoubao: "Например, doubao-seed-2-1-pro-260915 или endpoint ID из консоли (ep-...). ID моделей с датой быстро меняются.",
      providerPresetHintHunyuan: "Например, hunyuan-turbos-latest / hunyuan-t1-latest. Старый адрес ещё работает; новые аккаунты могут использовать TokenHub.",
      providerPresetHintErnie: "Например, ernie-5.1 / ernie-4.5-turbo-128k. Старые модели могут отключать; смотрите список в консоли.",
      providerPresetHintSpark: "Например, generalv3.5 / 4.0Ultra. В поле ключа укажите APIPassword из консоли (APIKey:APISecret).",
      providerPresetHintMiniMax: "Например, MiniMax-M2 / MiniMax-M3. M2.x всегда размышляет; структурированный вывод может быть нестабильным.",
      providerPresetHintLongCat: "Например, LongCat-Flash-Chat / LongCat-2.5. ID моделей часто меняются; смотрите /models.",
      providerPresetHintStepFun: "Например, step-3.5-flash / step-5-preview. Для подписки Step Plan используется другой адрес.",
      providerPresetHintCustom: "Укажите ID модели из консоли сервиса или /models.",
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
      tbXinanda: "Синанда",
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
      genCopyPrompt: "Копировать промпт",
      genCopyPromptDone: "Скопировано",
      genCopyPromptFailed: "Не удалось скопировать",
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
      genQuestionType: "Тип вопросов",
      genTypeSingle: "Один ответ",
      genTypeMultiple: "Несколько ответов",
      genTypeMixed: "Смешанные",
      genTypeHint: "В вопросах с несколькими ответами на телевизоре нужно нажать «Подтвердить». После создания проверьте ключи ответов.",
      genMultiReviewWarning: "В этой партии есть вопросы с несколькими ответами. ИИ может пропустить правильный вариант; проверьте каждый ключ перед добавлением.",
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
      err_PROVIDER_KEY_INVALID: "Ключ API недействителен. Проверьте, что он вставлен полностью, и повторите попытку.",
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
      err_PROVIDER_TEST_UNSUPPORTED: "Сервис не предоставляет автоматическую проверку (адрес также может быть неверным). Сохраните и попробуйте сгенерировать вопросы.",
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
      privacyTitle: "Перед использованием ИИ-вопросов",
      privacyIntro: "Подтверждение нужно только для генерации ИИ или настройки ИИ-сервиса. Ручные вопросы и готовые наборы не требуют ключа API.",
      privacyLocalTitle: "Хранится на телевизоре",
      privacyLocal: "Вопросы, ответы и прогресс остаются на телевизоре и удаляются после завершения викторины.",
      privacyAiTitle: "Вопросы ИИ",
      privacyAi: "Тема, класс, сложность и похожие параметры отправляются выбранному вами сервису модели. Семейные данные не отправляются. Если включено озвучивание DeepSeek, текст вопроса, вариантов, ответа и решения также отправляется в службу синтеза речи DeepSeek.",
      privacyKeyTitle: "Ключ API",
      privacyKey: "Ключ хранится только на телевизоре (шифруется, если система это поддерживает) и больше не показывается на этой странице. Он не сохраняется в облаке продукта; при генерации вопросов он отправляется только выбранному вами сервису модели. Для озвучивания используется только официальный адрес DeepSeek.",
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
      footer: "Викторина начинается и завершается только на телевизоре; временная разблокировка доступна только на экране блокировки и снимается при выключении экрана или перезапуске.",
    },
    uk: {
      eyebrow: "Налаштування батьків · Локальна мережа",
      title: "Вебсторінка для батьків",
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
      gate_pair_text: "Введіть 6-значний пароль батьків із телевізора. Після перевірки сторінка відкривається без пароля до місцевої півночі. Тимчасове розблокування діє лише на екрані блокування телевізора.",
      gate_pair_expired: "QR-код застарів. Оновіть його на телевізорі й відскануйте знову.",
      gate_in_use_badge: "Використовується",
      gate_in_use_heading: "Інший телефон зараз використовує налаштування",
      gate_in_use_lead: "Налаштування батьків можна змінювати лише з одного телефона одночасно.",
      gate_in_use_title: "Відновиться приблизно через 10 секунд",
      gate_in_use_text: "Якщо інший телефон уже вийшов, сторінка оновиться сама.\nВихід із налаштувань звільняє доступ одразу.",
      gate_takeover_button: "Перехопити керування",
      gate_takeover_note: "Потрібен пароль батьків; інший телефон одразу вийде з налаштувань.",
      webDisabledTitleBound: "Вебсторінку вимкнено",
      webDisabledTitleBinding: "Триває прив'язка до віддаленого керування",
      webDisabledTitleNoChild: "Веб-центр батьків недоступний",
      webDisabledTitleNotReady: "Телевізор запускається",
      webDisabledBoundText: "Цей телевізор прив'язано до віддаленого керування. Віддалене керування повністю визначає локальну політику; сторінка доступна лише в локальному режимі та без прив'язки. Змінюйте налаштування в застосунку батьків: Захист пристроїв → Телевізори.",
      webDisabledBindingText: "Телевізор прив'язується до віддаленого керування. Після завершення прив'язки сторінка вимкнеться; завершіть прив'язку та подальші налаштування в застосунку батьків.",
      webDisabledNoChildText: "Сторінка працює лише в локальному дитячому режимі. Спершу ввімкніть дитячий режим у «Центрі батьків» на телевізорі; після цього праворуч з'явиться QR-код для налаштування з телефона.",
      webDisabledNotReadyText: "Телевізор ще запускається. Оновіть сторінку трохи пізніше.",
      webDisabledStateBound: "Зараз: віддалений режим / прив'язано",
      webDisabledStateBinding: "Зараз: віддалений режим / прив'язка",
      webDisabledStateNoChild: "Зараз: локальний режим / дитячий режим вимкнено",
      webDisabledStateNotReady: "Зараз: телевізор не готовий",
      webDisabledRefresh: "Оновити стан",
      webDisabledRefreshFailed: "Не вдалося зв'язатися з телевізором. Переконайтеся, що телефон і телевізор в одній мережі Wi-Fi, і повторіть.",
      webDisabledHint: "Сторінка доступна, лише коли на телевізорі ввімкнено локальний дитячий режим і немає прив'язки до віддаленого керування.",
      pinLabel: "Введіть пароль батьків",
      pinButton: "Відкрити налаштування",
      forgotPin: "Забули пароль?",
      recoveryTitle: "Скидання за кодом відновлення",
      recoveryLead: "Введіть 12-значний код відновлення, збережений під час першого ввімкнення дитячого режиму, і задайте новий 6-значний пароль батьків. Код показувався лише один раз; ні ця сторінка, ні телевізор не можуть його відновити.",
      recoveryCodeLabel: "Код відновлення",
      recoveryNewPinLabel: "Новий пароль батьків (6 цифр)",
      recoveryConfirmLabel: "Повторіть новий пароль",
      recoverySubmit: "Скинути пароль",
      recoveryCancel: "Назад до входу",
      recoveryDone: "Пароль батьків скинуто. Увійдіть із новим паролем.",
      recoveryMismatch: "Паролі не збігаються.",
      recoveryFormat: "Введіть 12-значний код відновлення.",
      err_RECOVERY_INVALID: "Неправильний код відновлення.",
      err_RECOVERY_LOCKED: "Забагато неправильних кодів відновлення. Спробуйте пізніше.",
      err_RECOVERY_LOCKED_RETRY: "Забагато неправильних кодів відновлення. Повторіть через {n} с.",
      err_RECOVERY_NOT_SET: "На цьому телевізорі немає коду відновлення, тому скидання зі сторінки неможливе. Код показується лише один раз під час першого ввімкнення дитячого режиму.",
      err_RECOVERY_FAILED: "Не вдалося скинути пароль. Спробуйте ще раз.",
      err_NEW_PIN_INVALID: "Новий пароль має складатися з 6 цифр.",
      err_PIN_TOO_SIMPLE: "Не використовуйте простий пароль, як-от 123456 або 666666.",
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
      logout: "Вийти з налаштувань",
      published: "Опубліковано на телевізорі. Почніть з екрана блокування.",
      deleted: "Вікторину видалено.",
      confirmDelete: "Видалити поточну вікторину?",
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
      err_DOCUMENT_UNREADABLE: "Конфігурація вікторини на телевізорі пошкоджена. Збережіть її знову на цій сторінці.",
      err_INVALID_DOCUMENT: "Правила вікторини некоректні. Перевірте їх і повторіть.",
      err_REVISION_CONFLICT: "Конфігурацію змінено на іншій сторінці. Відкрийте налаштування знову.",
      err_UNSUPPORTED: "Цей телевізор не підтримує такі налаштування вікторини.",
      err_DOCUMENT_PROJECTION_FAILED: "Збережена конфігурація не може набути чинності. Повторіть.",
      err_NOT_FOUND: "Інтерфейс відсутній або версія не збігається.",
      err_NETWORK: "Немає зв'язку з телевізором. Перевірте локальну мережу.",
      aiTitle: "ШІ-питання",
      aiIntro: "Модель створює питання з варіантами, а ви обираєте потрібні для цієї вікторини. Ручні питання працюють без ШІ.",
      aiUnsupported: "Ця версія Android на телевізорі не дозволяє безпечно зберігати ключ API, тому доступні лише питання, створені вручну.",
      aiUnsupportedBadge: "Недоступно",
      consentText: "Я погоджуюся надсилати параметри завдання (тема, клас, складність, кількість) сервісу моделі; родинні дані не надсилаються. Ключ API зберігається лише на телевізорі (шифрується, якщо система це підтримує), не повертається на сторінку телефона й не зберігається у хмарі продукту; під час генерації питань він надсилається лише обраному вами сервісу моделі. Якщо ввімкнено озвучення DeepSeek, текст запитання, варіантів, відповіді та рішення також надсилається до служби синтезу мовлення DeepSeek.",
      consentHint: "Без згоди доступні лише ручні питання. Відкликання згоди видалить збережений ключ із телевізора.",
      manualNote: "Питання, додані вручну, зберігаються лише на телевізорі й не надсилаються в інтернет.",
      baseUrl: "Адреса сервісу моделі (HTTPS)",
      model: "Назва моделі",
      apiKey: "Ключ API",
      keyHintSaved: "Ключ збережено і не показується; залиште поле порожнім, щоб зберегти його.",
      keyHintNew: "Ключ зберігається лише на телевізорі; шифрування використовується, якщо система його підтримує.",
      deepseekKeyHint: "Типово використовується DeepSeek. Немає ключа? Створіть його на платформі DeepSeek (необов’язково; оплачується DeepSeek).",
      providerPresetLabel: "Швидке заповнення адреси",
      providerPresetNote: "Лише приклади, не офіційне партнерство; оплату й дані обробляє обраний сервіс.",
      providerPresetHintDeepSeek: "Модель за замовчуванням — deepseek-flash; цю конфігурацію перевірено.",
      providerPresetHintKimi: "Наприклад, kimi-k3.1-pro / kimi-k3. Серія K3 завжди міркує; великі набори можуть обрізатися.",
      providerPresetHintGlm: "Безкоштовна glm-4.7-flash із вимкненим міркуванням обробляє 5 питань приблизно за 2 хвилини; перевірка підключення перевіряє лише /models, а не генерацію.",
      providerPresetHintQwen: "Наприклад, qwen-plus / qwen3-max. Новим акаунтам може знадобитися окрема адреса робочого простору; перевірте консоль.",
      providerPresetHintDoubao: "Наприклад, doubao-seed-2-1-pro-260915 або endpoint ID з консолі (ep-...). ID моделей із датою швидко змінюються.",
      providerPresetHintHunyuan: "Наприклад, hunyuan-turbos-latest / hunyuan-t1-latest. Стара адреса ще працює; нові акаунти можуть використовувати TokenHub.",
      providerPresetHintErnie: "Наприклад, ernie-5.1 / ernie-4.5-turbo-128k. Старі моделі можуть вимикати; дивіться список у консолі.",
      providerPresetHintSpark: "Наприклад, generalv3.5 / 4.0Ultra. У полі ключа вкажіть APIPassword з консолі (APIKey:APISecret).",
      providerPresetHintMiniMax: "Наприклад, MiniMax-M2 / MiniMax-M3. M2.x завжди міркує; структурований вивід може бути нестабільним.",
      providerPresetHintLongCat: "Наприклад, LongCat-Flash-Chat / LongCat-2.5. ID моделей часто змінюються; дивіться /models.",
      providerPresetHintStepFun: "Наприклад, step-3.5-flash / step-5-preview. Для підписки Step Plan використовується інша адреса.",
      providerPresetHintCustom: "Укажіть ID моделі з консолі сервісу або /models.",
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
      tbXinanda: "Сінанда",
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
      genCopyPrompt: "Копіювати промпт",
      genCopyPromptDone: "Скопійовано",
      genCopyPromptFailed: "Не вдалося скопіювати",
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
      genQuestionType: "Тип питань",
      genTypeSingle: "Одна відповідь",
      genTypeMultiple: "Кілька відповідей",
      genTypeMixed: "Змішані",
      genTypeHint: "У питаннях із кількома відповідями на телевізорі треба натиснути «Підтвердити». Після створення перевірте ключі відповідей.",
      genMultiReviewWarning: "У цій партії є питання з кількома відповідями. ШІ може пропустити правильний варіант; перевірте кожен ключ перед додаванням.",
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
      err_PROVIDER_KEY_INVALID: "Ключ API недійсний. Перевірте, чи його вставлено повністю, і повторіть спробу.",
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
      err_PROVIDER_TEST_UNSUPPORTED: "Сервіс не надає автоматичної перевірки (адреса також може бути неправильною). Збережіть і спробуйте згенерувати питання.",
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
      privacyTitle: "Перед використанням ШІ-питань",
      privacyIntro: "Підтвердження потрібне лише для генерації ШІ або налаштування ШІ-сервісу. Ручні питання й готові набори не потребують ключа API.",
      privacyLocalTitle: "Зберігається на телевізорі",
      privacyLocal: "Питання, відповіді та поступ залишаються на телевізорі й видаляються після завершення вікторини.",
      privacyAiTitle: "Питання ШІ",
      privacyAi: "Тема, клас, складність та подібні параметри надсилаються обраному вами сервісу моделі. Родинні дані не надсилаються. Якщо ввімкнено озвучення DeepSeek, текст запитання, варіантів, відповіді та рішення також надсилається до служби синтезу мовлення DeepSeek.",
      privacyKeyTitle: "Ключ API",
      privacyKey: "Ключ зберігається лише на телевізорі (шифрується, якщо система це підтримує) й більше не показується на цій сторінці. Він не зберігається у хмарі продукту; під час генерації питань він надсилається лише обраному вами сервісу моделі. Для озвучення використовується лише офіційна адреса DeepSeek.",
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
      footer: "Вікторина починається й завершується лише на телевізорі; тимчасове розблокування доступне лише на екрані блокування й знімається після вимкнення екрана або перезапуску.",
    },
    kk: {
      eyebrow: "Ата-ана баптауы · Жергілікті желі",
      title: "Ата-анаға арналған веб-бет",
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
      gate_pair_text: "Теледидардағы 6 таңбалы ата-ана құпиясөзін енгізіңіз. Тексеруден кейін бет жергілікті түн ортасына дейін құпиясөзсіз ашылады. Уақытша ашу тек теледидар құлыптау экранында әрекет етеді.",
      gate_pair_expired: "QR-код ескірді. Теледидарда жаңартып, қайта сканерлеңіз.",
      gate_in_use_badge: "Қолданылуда",
      gate_in_use_heading: "Басқа телефон қазір баптауды қолданып тұр",
      gate_in_use_lead: "Ата-ана баптауларын бір уақытта тек бір телефоннан өзгертуге болады.",
      gate_in_use_title: "Шамамен 10 секундтан кейін қалпына келеді",
      gate_in_use_text: "Басқа телефон шыққан болса, бет өзі жаңарады.\nБаптаудан шығу қолжетімділікті бірден босатады.",
      gate_takeover_button: "Басқарып алу",
      gate_takeover_note: "Ата-ана құпиясөзі қажет; басқа телефон бірден шығады.",
      webDisabledTitleBound: "Веб-бет өшірілді",
      webDisabledTitleBinding: "Қашықтан басқаруға байлау жүруде",
      webDisabledTitleNoChild: "Веб ата-ана орталығы қолжетімсіз",
      webDisabledTitleNotReady: "Теледидар іске қосылуда",
      webDisabledBoundText: "Бұл теледидар қашықтан басқаруға байланған. Қашықтан басқару жергілікті саясатты толық анықтайды; бет тек жергілікті режимде және байланыс жоқ кезде қолжетімді. Параметрлерді ата-ана қолданбасындағы Құрылғы қорғанысы → Теледидарлар бөлімінде өзгертіңіз.",
      webDisabledBindingText: "Теледидар қашықтан басқаруға байланып жатыр. Байлау аяқталған соң бет өшіріледі; байлауды және кейінгі параметрлерді ата-ана қолданбасында аяқтаңыз.",
      webDisabledNoChildText: "Бет тек жергілікті бала режимінде жұмыс істейді. Алдымен теледидардағы «Ата-ана орталығында» бала режимін қосыңыз; қосылған соң оң жақта телефонмен баптауға арналған QR-код пайда болады.",
      webDisabledNotReadyText: "Теледидар әлі іске қосылуда. Сәл кейінірек бетті жаңартыңыз.",
      webDisabledStateBound: "Қазір: қашықтан режим / байланған",
      webDisabledStateBinding: "Қазір: қашықтан режим / байлау",
      webDisabledStateNoChild: "Қазір: жергілікті режим / бала режимі өшірулі",
      webDisabledStateNotReady: "Қазір: теледидар дайын емес",
      webDisabledRefresh: "Күйді жаңарту",
      webDisabledRefreshFailed: "Теледидармен байланысу мүмкін болмады. Телефон мен теледидар бір Wi-Fi желісінде екенін тексеріп, қайталаңыз.",
      webDisabledHint: "Бет тек теледидарда жергілікті бала режимі қосулы және қашықтан басқаруға байланыс жоқ кезде қолжетімді.",
      pinLabel: "Ата-ана құпиясөзін енгізіңіз",
      pinButton: "Баптауды ашу",
      forgotPin: "Құпиясөзді ұмыттыңыз ба?",
      recoveryTitle: "Қалпына келтіру кодымен қалпына келтіру",
      recoveryLead: "Балалар режимін алғаш қосқанда сақталған 12 таңбалы қалпына келтіру кодын енгізіп, жаңа 6 таңбалы ата-ана құпиясөзін орнатыңыз. Код бір рет қана көрсетілген; оны бұл бет те, теледидар да қайта көрсете алмайды.",
      recoveryCodeLabel: "Қалпына келтіру коды",
      recoveryNewPinLabel: "Жаңа ата-ана құпиясөзі (6 сан)",
      recoveryConfirmLabel: "Жаңа құпиясөзді қайталаңыз",
      recoverySubmit: "Құпиясөзді қалпына келтіру",
      recoveryCancel: "Кіруге оралу",
      recoveryDone: "Ата-ана құпиясөзі қалпына келтірілді. Жаңа құпиясөзбен кіріңіз.",
      recoveryMismatch: "Құпиясөздер сәйкес емес.",
      recoveryFormat: "12 таңбалы қалпына келтіру кодын енгізіңіз.",
      err_RECOVERY_INVALID: "Қалпына келтіру коды дұрыс емес.",
      err_RECOVERY_LOCKED: "Қалпына келтіру коды тым көп қате енгізілді. Кейінірек қайталаңыз.",
      err_RECOVERY_LOCKED_RETRY: "Қалпына келтіру коды тым көп қате енгізілді. {n} секундтан кейін қайталаңыз.",
      err_RECOVERY_NOT_SET: "Бұл теледидарда қалпына келтіру коды сақталмаған, сондықтан оны беттен қалпына келтіру мүмкін емес. Код балалар режимін алғаш қосқанда бір рет қана көрсетіледі.",
      err_RECOVERY_FAILED: "Қалпына келтіру сәтсіз аяқталды. Қайталап көріңіз.",
      err_NEW_PIN_INVALID: "Жаңа құпиясөз 6 саннан тұруы керек.",
      err_PIN_TOO_SIMPLE: "123456 немесе 666666 сияқты қарапайым құпиясөзді қолданбаңыз.",
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
      logout: "Ата-ана баптауынан шығу",
      published: "Теледидарға жарияланды. Құлыптау экранынан бастаңыз.",
      deleted: "Викторина жойылды.",
      confirmDelete: "Ағымдағы викторинаны жою керек пе?",
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
      err_DOCUMENT_UNREADABLE: "Теледидардағы викторина конфигурациясы бүлінген. Осы бетте қайта сақтаңыз.",
      err_INVALID_DOCUMENT: "Викторина ережелері дұрыс емес. Тексеріп, қайталаңыз.",
      err_REVISION_CONFLICT: "Конфигурация басқа бетте өзгертілген. Баптауды қайта ашыңыз.",
      err_UNSUPPORTED: "Бұл теледидар мұндай викторина баптауларын қолдамайды.",
      err_DOCUMENT_PROJECTION_FAILED: "Сақталған конфигурация күшіне ене алмады. Қайталаңыз.",
      err_NOT_FOUND: "Интерфейс жоқ немесе нұсқа сәйкес емес.",
      err_NETWORK: "Теледидармен байланыс жоқ. Жергілікті желіні тексеріңіз.",
      aiTitle: "AI сұрақтары",
      aiIntro: "Модель нұсқалары бар сұрақтар жасайды, ал сіз осы викторинаға қажеттерін таңдайсыз. Қолмен қосу AI-сыз да жұмыс істейді.",
      aiUnsupported: "Теледидардағы Android нұсқасы API кілтін қауіпсіз сақтауға мүмкіндік бермейді, сондықтан тек қолмен жасалған сұрақтар қолжетімді.",
      aiUnsupportedBadge: "Қолжетімсіз",
      consentText: "Сұрақ талаптарын (тақырып, сынып, қиындық, саны) модель қызметіне жіберуге келісемін; отбасылық ақпарат жіберілмейді. API кілті тек теледидарда сақталады (жүйе қолдаса, шифрланады), телефон бетіне қайтарылмайды және өнім бұлтында сақталмайды; сұрақ жасау кезінде ол тек сіз баптаған модель қызметіне жіберіледі. DeepSeek дауыстап оқу қосулы болса, сұрақ, нұсқалар, жауап және шешу жолы мәтіні де DeepSeek дауыс қызметіне жіберіледі.",
      consentHint: "Келісімсіз тек қолмен қосу қолжетімді. Келісімді қайтарып алсаңыз, теледидардағы кілт жойылады.",
      manualNote: "Қолмен қосылған сұрақтар тек теледидарда сақталады және интернетке жіберілмейді.",
      baseUrl: "Модель қызметінің мекенжайы (HTTPS)",
      model: "Модель атауы",
      apiKey: "API кілті",
      keyHintSaved: "Кілт сақталған және көрсетілмейді; сақтау үшін өрісті бос қалдырыңыз.",
      keyHintNew: "Кілт тек теледидарда сақталады; жүйе қолдаса, шифрланады.",
      deepseekKeyHint: "Әдепкіде DeepSeek қолданылады. Кілт жоқ па? Оны DeepSeek платформасында жасаңыз (міндетті емес; төлемді DeepSeek алады).",
      providerPresetLabel: "Мекенжайды жылдам толтыру",
      providerPresetNote: "Тек мысалдар, ресми серіктестік емес; төлем мен деректерді таңдалған қызмет өңдейді.",
      providerPresetHintDeepSeek: "Әдепкі модель — deepseek-flash; бұл конфигурация тексерілген.",
      providerPresetHintKimi: "Мысалы, kimi-k3.1-pro / kimi-k3. K3 сериясы әрдайым ойланады; үлкен топтамалар қысқарып қалуы мүмкін.",
      providerPresetHintGlm: "Ойлау өшірілген тегін glm-4.7-flash шамамен 5 сұрақты 2 минутта өңдейді; қосылымды тексеру тек /models тексереді, генерацияны емес.",
      providerPresetHintQwen: "Мысалы, qwen-plus / qwen3-max. Жаңа аккаунттарға жеке жұмыс кеңістігі мекенжайы қажет болуы мүмкін; консольді тексеріңіз.",
      providerPresetHintDoubao: "Мысалы, doubao-seed-2-1-pro-260915 немесе консольдегі endpoint ID (ep-...). Күні бар модель ID-лері жиі өзгереді.",
      providerPresetHintHunyuan: "Мысалы, hunyuan-turbos-latest / hunyuan-t1-latest. Ескі мекенжай әлі жұмыс істейді; жаңа аккаунттар TokenHub қолдануы мүмкін.",
      providerPresetHintErnie: "Мысалы, ernie-5.1 / ernie-4.5-turbo-128k. Ескі модельдер өшірілуі мүмкін; консольдегі тізімді қараңыз.",
      providerPresetHintSpark: "Мысалы, generalv3.5 / 4.0Ultra. Кілт өрісіне консольдегі APIPassword (APIKey:APISecret) енгізіңіз.",
      providerPresetHintMiniMax: "Мысалы, MiniMax-M2 / MiniMax-M3. M2.x әрдайым ойланады; құрылымдалған шығыс тұрақсыз болуы мүмкін.",
      providerPresetHintLongCat: "Мысалы, LongCat-Flash-Chat / LongCat-2.5. Модель ID-лері жиі өзгереді; /models қараңыз.",
      providerPresetHintStepFun: "Мысалы, step-3.5-flash / step-5-preview. Step Plan жазылымы басқа мекенжайды қолданады.",
      providerPresetHintCustom: "Модель ID-ін қызмет консолінен немесе /models арқылы енгізіңіз.",
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
      tbXinanda: "Синанда",
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
      genCopyPrompt: "Промптты көшіру",
      genCopyPromptDone: "Көшірілді",
      genCopyPromptFailed: "Көшіру сәтсіз",
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
      genQuestionType: "Сұрақ түрі",
      genTypeSingle: "Бір жауап",
      genTypeMultiple: "Бірнеше жауап",
      genTypeMixed: "Аралас",
      genTypeHint: "Бірнеше жауапты сұрақтар теледидарда «Растау» түймесімен жіберіледі. Жасалғаннан кейін жауап кілттерін тексеріңіз.",
      genMultiReviewWarning: "Бұл топта бірнеше жауапты сұрақтар бар. ЖИ дұрыс нұсқаны өткізіп жіберуі мүмкін; қосу алдында әр кілтті тексеріңіз.",
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
      err_PROVIDER_KEY_INVALID: "API кілті жарамсыз. Оның толық қойылғанын тексеріп, қайталап көріңіз.",
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
      err_PROVIDER_TEST_UNSUPPORTED: "Бұл қызметте автоматты тексеру интерфейсі жоқ (мекенжай да қате болуы мүмкін). Сақтап, сұрақ жасауды көріңіз.",
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
      privacyTitle: "AI сұрақтарын пайдалану алдында",
      privacyIntro: "Растау тек AI арқылы сұрақ жасау немесе AI қызметін баптау үшін қажет. Қолмен қосылған сұрақтар мен дайын жинақтарға API кілті қажет емес.",
      privacyLocalTitle: "Теледидарда сақталады",
      privacyLocal: "Сұрақтар, жауаптар және барыс тек теледидарда қалады және викторина аяқталғанда жойылады.",
      privacyAiTitle: "AI сұрақтары",
      privacyAi: "Тақырып, сынып, қиындық және сол сияқты талаптар сіз баптаған модель қызметіне жіберіледі. Отбасылық ақпарат жіберілмейді. DeepSeek дауыстап оқу қосулы болса, сұрақ, нұсқалар, жауап және шешу жолы мәтіні де DeepSeek дауыс қызметіне жіберіледі.",
      privacyKeyTitle: "API кілті",
      privacyKey: "Кілт тек теледидарда сақталады (жүйе қолдаса, шифрланады) және бұл бетте қайта көрсетілмейді. Ол өнім бұлтында сақталмайды; сұрақ жасау кезінде тек сіз баптаған модель қызметіне жіберіледі. Дауыстап оқу тек DeepSeek ресми мекенжайын пайдаланады.",
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
      footer: "Викторина тек теледидарда басталады және есептеледі; уақытша ашу тек құлыптау экранында қолжетімді және экран өшкенде немесе теледидар қайта іске қосылғанда жойылады.",
    },
  };

  // V17 four-tab copy. Kept in one table so every language gains the same
  // keys; the older keys above remain for the modals carried over unchanged.
  const V17_TEXTS = {
    zh: {
      tabGenerate: "首页",
      tabBank: "题库",
      tabWrong: "错题",
      tabControl: "控制",
      tabMe: "更多",
      controlPanelTitle: "控制面板",
      controlOpenHint: "在本机局域网内远程查看电视状态并执行家长操作。",
      controlOpenAction: "打开控制面板",
      controlUnavailable: "当前电视不支持远程控制。请确认电视处于本地儿童模式。",
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
      settingsSaved: "已保存",
      meSource: "答题来源",
      meAdvanced: "高级",
      meMaintenance: "题库维护",
      meMaintenanceHint: "删除题目或作答历史；「未使用」不是历史记录",
      clearUnused: "清空未使用",
      clearAnswered: "清空已回答",
      clearWrong: "清空错题",
      clearAll: "清空全部题库",
      confirmClearUnused: "清空所有未使用的题目？未开始的闯关会受影响，且不能撤销。",
      confirmClearAnswered: "删除所有已回答的记录？此操作不能撤销。",
      confirmClearWrong: "删除所有错题记录？此操作不能撤销。",
      confirmClearAll: "清空整个本地题库？此操作不能撤销。",
      clearDone: "已清理。",
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
      tabGenerate: "Home",
      tabBank: "Bank",
      tabWrong: "Mistakes",
      tabControl: "Control",
      tabMe: "More",
      controlPanelTitle: "Control panel",
      controlOpenHint: "View the TV state and run parent actions on this local network.",
      controlOpenAction: "Open control panel",
      controlUnavailable: "Remote control is unavailable. Make sure the TV is in local child mode.",
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
      settingsSaved: "Saved",
      meSource: "Quiz source",
      meAdvanced: "Advanced",
      meMaintenance: "Bank maintenance",
      meMaintenanceHint: "Delete questions or answer history; “unused” is not history",
      clearUnused: "Clear unused",
      clearAnswered: "Clear answered",
      clearWrong: "Clear mistakes",
      clearAll: "Clear whole bank",
      confirmClearUnused: "Clear all unused questions? An unstarted challenge is affected, and this cannot be undone.",
      confirmClearAnswered: "Delete all answered records? This cannot be undone.",
      confirmClearWrong: "Delete all mistake records? This cannot be undone.",
      confirmClearAll: "Clear the entire local bank? This cannot be undone.",
      clearDone: "Cleared.",
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
      tabGenerate: "Главная",
      tabBank: "Банк",
      tabWrong: "Ошибки",
      tabControl: "Пульт",
      tabMe: "Ещё",
      controlPanelTitle: "Панель управления",
      controlOpenHint: "Смотрите состояние телевизора и выполняйте действия родителя в локальной сети.",
      controlOpenAction: "Открыть панель",
      controlUnavailable: "Удалённое управление недоступно. Убедитесь, что телевизор в локальном детском режиме.",
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
      settingsSaved: "Сохранено",
      meSource: "Источник викторины",
      meAdvanced: "Дополнительно",
      meMaintenance: "Обслуживание банка",
      meMaintenanceHint: "Удаление вопросов или истории ответов; «неиспользованные» — не история",
      clearUnused: "Очистить неиспользованные",
      clearAnswered: "Очистить отвеченные",
      clearWrong: "Очистить ошибки",
      clearAll: "Очистить весь банк",
      confirmClearUnused: "Очистить все неиспользованные вопросы? Это затронет не начатую викторину и не отменяется.",
      confirmClearAnswered: "Удалить все записи об отвеченных вопросах? Это нельзя отменить.",
      confirmClearWrong: "Удалить все записи об ошибках? Это нельзя отменить.",
      confirmClearAll: "Очистить весь локальный банк? Это нельзя отменить.",
      clearDone: "Очищено.",
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
      tabGenerate: "Головна",
      tabBank: "Банк",
      tabWrong: "Помилки",
      tabControl: "Пульт",
      tabMe: "Ще",
      controlPanelTitle: "Панель керування",
      controlOpenHint: "Переглядайте стан телевізора й виконуйте дії батьків у локальній мережі.",
      controlOpenAction: "Відкрити панель",
      controlUnavailable: "Віддалене керування недоступне. Переконайтеся, що телевізор у локальному дитячому режимі.",
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
      settingsSaved: "Збережено",
      meSource: "Джерело вікторини",
      meAdvanced: "Додатково",
      meMaintenance: "Обслуговування банку",
      meMaintenanceHint: "Видалення питань або історії відповідей; «невикористані» — не історія",
      clearUnused: "Очистити невикористані",
      clearAnswered: "Очистити відповіді",
      clearWrong: "Очистити помилки",
      clearAll: "Очистити весь банк",
      confirmClearUnused: "Очистити всі невикористані питання? Це вплине на непочату вікторину, і скасувати не можна.",
      confirmClearAnswered: "Видалити всі записи про відповіді? Це неможливо скасувати.",
      confirmClearWrong: "Видалити всі записи про помилки? Це неможливо скасувати.",
      confirmClearAll: "Очистити весь локальний банк? Це неможливо скасувати.",
      clearDone: "Очищено.",
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
      tabGenerate: "Басты бет",
      tabBank: "Банк",
      tabWrong: "Қателер",
      tabControl: "Қашықтан",
      tabMe: "Қосымша",
      controlPanelTitle: "Басқару панелі",
      controlOpenHint: "Жергілікті желіде теледидар күйін қарап, ата-ана әрекеттерін орындаңыз.",
      controlOpenAction: "Панельді ашу",
      controlUnavailable: "Қашықтан басқару қолжетімсіз. Теледидардың жергілікті бала режимінде екенін тексеріңіз.",
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
      settingsSaved: "Сақталды",
      meSource: "Викторина көзі",
      meAdvanced: "Қосымша",
      meMaintenance: "Банкті күтіп-ұстау",
      meMaintenanceHint: "Сұрақтарды немесе жауап тарихын жою; «пайдаланылмаған» — тарих емес",
      clearUnused: "Пайдаланылмағанды тазалау",
      clearAnswered: "Жауаптарды тазалау",
      clearWrong: "Қателерді тазалау",
      clearAll: "Бүкіл банкті тазалау",
      confirmClearUnused: "Барлық пайдаланылмаған сұрақты тазалау керек пе? Басталмаған викторинаға әсер етеді, қайтару мүмкін емес.",
      confirmClearAnswered: "Барлық жауапталған жазбаны жою керек пе? Бұны қайтару мүмкін емес.",
      confirmClearWrong: "Барлық қате жазбаларын жою керек пе? Бұны қайтару мүмкін емес.",
      confirmClearAll: "Бүкіл жергілікті банкті тазалау керек пе? Бұны қайтару мүмкін емес.",
      clearDone: "Тазаланды.",
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
      genConfigTitle: "AI课时同步出题",
      genScopeUnsetTitle: "尚未设置本成员的出题范围",
      genScopeUnsetHint: "点击设置年级、学期、学科和教材",
      genScopeSetupHint: "请选择年级、学期、学科和教材；不会套用默认参数。",
      err_GEN_SCOPE_REQUIRED: "请先选择年级、学期、学科和教材。",
      answerConfigTitle: "答题闯关配置",
      answerRulesTitle: "答题规则",
      generateCardTitle: "开始生成",
      scopeBound: "和 AI 课时同步出题一致",
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
      goGenerationConfig: "调整 AI 课时同步出题",
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
      genConfigTitle: "AI lesson-synced questions",
      genScopeUnsetTitle: "This member's question range is not set",
      genScopeUnsetHint: "Tap to choose grade, semester, subject and textbook",
      genScopeSetupHint: "Choose grade, semester, subject and textbook. Defaults are not applied.",
      err_GEN_SCOPE_REQUIRED: "Choose grade, semester, subject and textbook first.",
      answerConfigTitle: "Quiz challenge settings",
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
      genConfigTitle: "Вопросы ИИ по уроку",
      genScopeUnsetTitle: "Диапазон вопросов для этого участника не задан",
      genScopeUnsetHint: "Нажмите, чтобы выбрать класс, полугодие, предмет и учебник",
      genScopeSetupHint: "Выберите класс, полугодие, предмет и учебник. Значения по умолчанию не подставляются.",
      err_GEN_SCOPE_REQUIRED: "Сначала выберите класс, полугодие, предмет и учебник.",
      answerConfigTitle: "Настройки квиз-испытания",
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
      genConfigTitle: "Питання ШІ за уроком",
      genScopeUnsetTitle: "Діапазон питань для цього учасника не задано",
      genScopeUnsetHint: "Натисніть, щоб вибрати клас, семестр, предмет і підручник",
      genScopeSetupHint: "Виберіть клас, семестр, предмет і підручник. Типові значення не застосовуються.",
      err_GEN_SCOPE_REQUIRED: "Спочатку виберіть клас, семестр, предмет і підручник.",
      answerConfigTitle: "Налаштування вікторини-випробування",
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
      genConfigTitle: "AI сабаққа сай сұрақтар",
      genScopeUnsetTitle: "Бұл қатысушының сұрақ ауқымы орнатылмаған",
      genScopeUnsetHint: "Сынып, семестр, пән және оқулықты таңдау үшін түртіңіз",
      genScopeSetupHint: "Сынып, семестр, пән және оқулықты таңдаңыз. Әдепкі мәндер қолданылмайды.",
      err_GEN_SCOPE_REQUIRED: "Алдымен сынып, семестр, пән және оқулықты таңдаңыз.",
      answerConfigTitle: "Викторина-сынақ баптаулары",
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
      levelEditorLead: "默认已填好「小学四年级 · 上册 · 全科 · 期中必考 · 不限难度」。保存后电视会多一个关卡，孩子可用遥控上下键选择。",
      levelListCount: "{n}/{max}",
      levelEmptyTitle: "还没有设置关卡",
      levelEmptyText: "添加一个答题范围，孩子就能在电视上像选关一样答题。",
      levelMaxHint: "最多设置 {n} 个关卡。",
      levelDeleteConfirmTitle: "删除关卡？",
      levelDeleteConfirmText: "确定删除「{name}」？删除后不能恢复，题库中的题目不受影响。",
      levelDeleteConfirmAction: "删除",
      levelDeleted: "已删除「{name}」",
      levelSaved: "关卡已保存",
      levelOrderSaved: "关卡顺序已保存",
      levelNameLabel: "关卡名称（可选）",
      levelNamePlaceholder: "例如：语文期中冲刺",
      levelNameHint: "最多 10 个字；不填时电视显示范围摘要。",
      levelNameTooLong: "关卡名称最多 10 个字。",
      levelAddTitle: "新建关卡",
      levelEditTitle: "编辑关卡",
      levelAvailable: "总题量 {total} 道 · 可用 {available} 题",
      levelShort: "总题量 {total} 道 · 每轮需要 {pull} 题，当前可用 {available} 题",
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
      levelEditorLead: "The default range is Grade 4, first semester, all subjects, midterm, any difficulty. After saving, the TV gains one more level, and your child can switch levels with the remote.",
      levelListCount: "{n}/{max}",
      levelEmptyTitle: "No levels yet",
      levelEmptyText: "Add an answer range so your child can pick it on the TV like a game level.",
      levelMaxHint: "Up to {n} levels.",
      levelDeleteConfirmTitle: "Delete level?",
      levelDeleteConfirmText: "Delete “{name}”? This cannot be undone. Questions in the bank are not affected.",
      levelDeleteConfirmAction: "Delete",
      levelDeleted: "Deleted “{name}”",
      levelSaved: "Level saved",
      levelOrderSaved: "Level order saved",
      levelNameLabel: "Level name (optional)",
      levelNamePlaceholder: "e.g. Chinese midterm sprint",
      levelNameHint: "Up to 10 characters. Leave empty to show the range summary on TV.",
      levelNameTooLong: "Level name must be 10 characters or fewer.",
      levelAddTitle: "New level",
      levelEditTitle: "Edit level",
      levelAvailable: "{total} total · {available} usable",
      levelShort: "{total} total · needs {pull} per round; only {available} usable",
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
      levelEditorLead: "По умолчанию: 4 класс, первое полугодие, все предметы, к полугодовой, любая сложность. После сохранения на ТВ появится ещё один уровень; ребёнок сможет переключать уровни пультом.",
      levelListCount: "{n}/{max}",
      levelEmptyTitle: "Уровней пока нет",
      levelEmptyText: "Добавьте диапазон вопросов, чтобы ребёнок мог выбрать его на ТВ как уровень игры.",
      levelMaxHint: "Можно добавить не больше {n} уровней.",
      levelDeleteConfirmTitle: "Удалить уровень?",
      levelDeleteConfirmText: "Удалить «{name}»? Это нельзя отменить. Вопросы в банке не пострадают.",
      levelDeleteConfirmAction: "Удалить",
      levelDeleted: "Удалён уровень «{name}»",
      levelSaved: "Уровень сохранён",
      levelOrderSaved: "Порядок уровней сохранён",
      levelNameLabel: "Название уровня (необязательно)",
      levelNamePlaceholder: "например: Подготовка к четверти",
      levelNameHint: "До 10 символов. Если пусто, на ТВ показывается описание диапазона.",
      levelNameTooLong: "Название уровня — не больше 10 символов.",
      levelAddTitle: "Новый уровень",
      levelEditTitle: "Изменить уровень",
      levelAvailable: "Всего {total} · доступно {available}",
      levelShort: "Всего {total} · нужно {pull} за раунд, доступно {available}",
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
      levelEditorLead: "Типово: 4 клас, перший семестр, усі предмети, до піврічної, будь-яка складність. Після збереження на ТБ з’явиться ще один рівень; дитина зможе перемикати рівні пультом.",
      levelListCount: "{n}/{max}",
      levelEmptyTitle: "Рівнів ще немає",
      levelEmptyText: "Додайте діапазон питань, щоб дитина могла вибрати його на ТБ як рівень гри.",
      levelMaxHint: "Можна додати щонайбільше {n} рівнів.",
      levelDeleteConfirmTitle: "Видалити рівень?",
      levelDeleteConfirmText: "Видалити «{name}»? Це неможливо скасувати. Питання в банку не постраждають.",
      levelDeleteConfirmAction: "Видалити",
      levelDeleted: "Видалено рівень «{name}»",
      levelSaved: "Рівень збережено",
      levelOrderSaved: "Порядок рівнів збережено",
      levelNameLabel: "Назва рівня (необов’язково)",
      levelNamePlaceholder: "наприклад: Підготовка до семестру",
      levelNameHint: "До 10 символів. Якщо порожньо, на ТБ показується опис діапазону.",
      levelNameTooLong: "Назва рівня — не більше 10 символів.",
      levelAddTitle: "Новий рівень",
      levelEditTitle: "Змінити рівень",
      levelAvailable: "Усього {total} · доступно {available}",
      levelShort: "Усього {total} · потрібно {pull} за раунд, доступно {available}",
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
      levelEditorLead: "Әдепкі: 4-сынып, бірінші тоқсан, барлық пәндер, аралық бақылауға, кез келген қиындық. Сақтағаннан кейін ТВ-де тағы бір деңгей пайда болады; бала деңгейлерді пультпен ауыстыра алады.",
      levelListCount: "{n}/{max}",
      levelEmptyTitle: "Деңгейлер әзірге жоқ",
      levelEmptyText: "Бала ТВ-де ойын деңгейі ретінде таңдай алатындай сұрақ ауқымын қосыңыз.",
      levelMaxHint: "Ең көбі {n} деңгей қосуға болады.",
      levelDeleteConfirmTitle: "Деңгейді жою керек пе?",
      levelDeleteConfirmText: "«{name}» деңгейін жою керек пе? Бұны қайтару мүмкін емес. Банктегі сұрақтарға әсер етпейді.",
      levelDeleteConfirmAction: "Жою",
      levelDeleted: "«{name}» деңгейі жойылды",
      levelSaved: "Деңгей сақталды",
      levelOrderSaved: "Деңгей реті сақталды",
      levelNameLabel: "Деңгей атауы (міндетті емес)",
      levelNamePlaceholder: "мысалы: Тоқсанға дайындық",
      levelNameHint: "10 таңбаға дейін. Бос болса, ТВ-де ауқым сипаттамасы көрсетіледі.",
      levelNameTooLong: "Деңгей атауы 10 таңбадан аспауы керек.",
      levelAddTitle: "Жаңа деңгей",
      levelEditTitle: "Деңгейді өзгерту",
      levelAvailable: "Барлығы {total} · қолжетімді {available}",
      levelShort: "Барлығы {total} · раундқа {pull} керек, қолжетімді {available}",
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

  const V22_TEXTS = {
    zh: {
      bankImport: "导入题库",
      bankImportAction: "手动导入",
      bankSourceLine: "预置 + AI {course} 道 · 手动导入 {custom} 道 · 错题 {wrong} 道 · 总 {total} / {capacity}",
      bankSourceCustomTag: "标签：{name}",
      goImportBank: "导入题库",
      importTitle: "导入题库",
      importSingleOnlyBadge: "支持单选和多选",
      importSingleOnlyHint: "支持单选题和多选题；填空、听力、图片题和无答案题不会入库。",
      importCloudNotice: "绑定云端题库时不能导入本地题目。请先解绑云端。",
      importTargetLabel: "导入到标签",
      importTagPlaceholder: "输入或选择标签",
      importTagCount: "共 {n} 道",
      importTagSuggestEmpty: "输入新名称可直接新建标签",
      importTagSuggestNew: "＋ 新建标签「{name}」",
      importTagSuggestAria: "已有标签建议",
      importTagClear: "清空标签名称",
      importNoTagsHint: "还没有标签，正式导入成功后会创建。",
      importTargetHint: "将导入到已有标签「{name}」",
      importTargetNewHint: "将新建标签「{name}」，正式导入成功后保存",
      importTargetNone: "请选择已有标签，或输入新名称。",
      importTargetBadgeNew: "新建",
      importTargetBadgeExisting: "已有",
      importInputLabel: "题目内容",
      importChooseFile: "选择 .json 或 .txt 文件",
      importPasteLabel: "也可以直接粘贴题目",
      importPasteClear: "清空",
      importTextPlaceholder: "粘贴规范 JSON，或编号文本（1. 题干 / A. 选项 / 答案：B 或 A、C）",
      importCopyConvertPrompt: "复制格式转换提示词",
      importCopyGeneratePrompt: "复制出题提示词",
      importPromptConvertTitle: "格式转换提示词",
      importPromptGenerateTitle: "出题提示词",
      importPromptHint: "已有题目用「格式转换」，从零出题用「出题」；复制后粘贴给任意 AI 助手，再把结果保存为 JSON 或直接粘贴到这里。",
      importAiHint: "可先把照片、PDF 或杂乱的文字交给常用的 AI 网页助手整理；导入前请删除姓名、学校、学号等隐私。导入器只校验文本，不会上传内容。",
      importDefaultDifficulty: "题目没写难度时使用",
      importParse: "解析并预览",
      importParsing: "正在解析…",
      importEmptyInput: "请先选择文件或粘贴题目。",
      importTooLarge: "文件超过 16 MB，请拆成更小的文件。",
      importTooMany: "一次最多导入 {max} 道题，请拆成多个文件。",
      importLargeRisk: "大文件导入可能需要数分钟，请保持页面打开。题目只保存在电视本机，不参与备份；清除应用数据会丢失。",
      importJsonInvalid: "JSON 格式不正确，请检查引号、逗号和括号。",
      importNoQuestions: "没有找到题目。",
      importFileReadFailed: "文件读取失败，请换一个文件或直接粘贴。",
      importSelectTagFirst: "请先选择已有标签，或输入新名称。",
      importNewTagInvalid: "标签名称需要 1–20 个字。",
      importNewTagExists: "已经有同名标签，请换一个名称。",
      importNewTagLimit: "最多只能有 20 个标签。",
      importErrorAt: "第 {n} 题",
      importPreviewTitle: "导入预览",
      importPreviewTarget: "目标标签",
      importPreviewTotal: "文件总题数",
      importPreviewFileDup: "文件内重复",
      importPreviewCollectionDup: "本标签已有",
      importPreviewCrossDup: "其他来源重复（仍会保留）",
      importPreviewAppend: "将新增",
      importPreviewRemaining: "题库剩余",
      importPreviewRemainingValue: "{remaining} / {capacity} 道",
      importPreviewNoNew: "没有新题；文件内和本标签的题都已存在。",
      importPreviewRejected: "有 {n} 道题未通过校验，请检查答案和选项。",
      importPreviewNotEnough: "题库容量不足，还差 {n} 道。请先到「我的 → 题库维护」清理。",
      importPreviewCrossNote: "其他来源已有 {n} 道同内容题；它们仍会作为本标签的独立副本导入。",
      importConfirm: "导入 {n} 道题",
      importImporting: "正在导入…",
      importResultTitle: "已导入 {n} 道到「{name}」",
      importResultDetail: "批内重复 {file} 道 · 本标签已有 {collection} 道",
      importResultCross: "其他来源重复 {n} 道（已保留）",
      importResultRemaining: "题库剩余 {remaining} / {capacity} 道",
      importPreviewFormat: "文件格式",
      importPreviewFileSize: "文件大小",
      importPreviewBatches: "导入批次",
      importFormatJson: "JSON",
      importFormatText: "编号文本",
      importPreviewLocalNote: "本预览只在本机解析文件用于确认格式，固定每页 10 条；去重和校验以实际导入结果为准。单次最多 {max} 道、{size}。",
      importPagePrev: "上一页",
      importPageNext: "下一页",
      importPageGo: "跳转",
      importPageJump: "跳转到页码",
      importPreviewPage: "第 {page} / {pages} 页",
      importProgressCount: "已处理 {processed} / {total} 题",
      importProgressBatch: "第 {batch} / {batches} 批",
      importProgressStats: "已新增 {appended} · 批内重复 {file} · 本标签已有 {collection} · 其他来源重复 {cross} · 校验失败 {rejected}",
      importProgressHint: "导入期间请不要关闭页面。已完成的批次会保留；取消只停止后续批次。",
      importCancel: "取消导入",
      importCancelRequested: "正在停止…",
      importResultPartialTitle: "部分导入：已写入 {n} 道到「{name}」",
      importResultProcessed: "已处理 {processed} / {total} 题",
      importResultCancelled: "已取消；已写入的批次会保留。修复文件后可以重试，重复题不会再次写入。",
      importResultFailed: "第 {batch} 批失败，后续批次未导入。已写入的批次保留；修复后重试不会重复写入。",
      importPromptCopied: "提示词已复制。",
      importCopyFailed: "复制失败，请展开提示词后手动复制。",
      importErrorCopy: "复制错误给 AI 修复",
      importErrorCopied: "已复制，可直接粘贴给 AI。",
      importErrorCopyFailed: "复制失败，请手动复制错误信息。",
      importErrorCopyIntro: "我在导入学习机题库时遇到以下错误：",
      importErrorCopyAsk: "请按上面的题号修复，并给出修复后的完整结果。要求：\n1. 只输出完整的 JSON（version 和 items），不要解释，不要 Markdown 代码块，不要前后缀。\n2. 除列出的错误题目外，不要改动其他题目；不要删题、合并题目。\n3. 使用英文双引号，没有尾逗号，答案下标都在 options 范围内。\n4. 多选题至少 3 个选项、至少 2 个正确答案，且不能全选（至少保留 1 个错误选项）。如果原题所有选项都正确，请把其中一个选项改成明确错误的说法，不要改成单选，也不要删题。",
      importErrorCopySource: "原始数据：",
      importErrorCopySourceOmitted: "（原始数据过长，未自动带上；请把完整原始数据一并粘贴给 AI。）",
      importField_item: "题目",
      importField_items: "题目列表",
      importField_text: "文本",
      importField_prompt: "题干",
      importField_options: "选项",
      importField_answer: "答案",
      importField_explanation: "解析",
      importField_difficulty: "难度",
      importCode_TYPE: "字段类型不对",
      importCode_UNKNOWN_FIELD: "不支持的字段",
      importCode_DUPLICATE: "内容重复",
      importCode_REQUIRED: "缺少必填内容",
      importCode_ORDER: "内容顺序不正确",
      importCode_FORMAT: "格式不正确",
      importCode_COUNT: "选项数量必须是 2–6 个",
      importCode_INVALID_TEXT: "内容为空或超出长度",
      importCode_TOO_LONG: "内容太长",
      importCode_ANSWER_FORMAT: "答案格式不正确：单选写一个字母（A–F）或一个选项原文；多选写多个字母或 answerIndices 数组",
      importCode_OPTIONS_TOO_FEW: "多选题至少要有 3 个选项",
      importCode_ANSWER_TOO_FEW: "多选题至少要有 2 个正确答案",
      importCode_ANSWER_ALL: "多选题不能全选，至少要保留 1 个错误选项",
      importCode_ANSWER_RANGE: "答案不在选项范围内",
      importCode_ANSWER_CONFLICT: "answer、answerIndex、answerIndices 只能填写一个",
      importCode_INVALID_DIFFICULTY: "难度只能是 easy、medium 或 hard",
      importCode_NO_ITEMS: "没有题目",
      importCode_TOO_MANY_ITEMS: "一次最多 200 道题",
      levelAddCourseTitle: "教材关卡",
      levelAddCourseMeta: "年级 · 学科 · 单元",
      levelAddCustomTitle: "自定义关卡",
      levelAddCustomMeta: "从导入标签取题",
      levelAddCustomMetaNoTags: "先导入题目",
      levelAddCustomMetaCloud: "云端模式下不可用",
      levelAddWrongTitle: "错题复习",
      levelAddWrongMeta: "全学科 · 答对后消除错题",
      levelAddWrongMetaEmpty: "暂无错题，答错后自动加入",
      levelAddWrongMetaCloud: "云端模式下不可用",
      wrongLevelListTitle: "错题复习",
      wrongLevelListLead: "只有一个错题复习关卡，自动包含所有学科的错题。",
      wrongLevelListCount: "{n}/1",
      wrongLevelBadge: "错题",
      wrongLevelMeta: "全学科 · 答对后从错题本移除",
      wrongLevelFallbackName: "错题复习",
      wrongLevelAvailable: "错题 {n} 道 · 每轮最多 {pull} 道",
      wrongLevelEmptyPool: "当前没有错题；答错后自动加入",
      wrongLevelAddTitle: "添加错题复习",
      wrongLevelEditTitle: "编辑错题复习",
      wrongLevelEditorLead: "错题复习不分学科，自动收录本机所有答错的题；答对后该错题会从错题本移除。",
      wrongLevelNameHint: "留空时电视显示「错题复习」；最多 10 个字。",
      wrongLevelEditorAvailable: "当前错题 {n} 道 · 每轮最多 {pull} 道。",
      wrongLevelEditorEmpty: "当前没有错题。孩子答错后会加入错题本，之后就可以复习。",
      wrongLevelDeleteConfirm: "确定删除错题复习关卡「{name}」？错题本身不会删除。",
      wrongLevelDeleteAria: "删除错题复习「{name}」",
      wrongLevelSaved: "错题复习关卡已保存。",
      wrongLevelDeleted: "错题复习关卡已删除。",
      err_INVALID_WRONG_LEVEL: "错题复习关卡不符合要求，请刷新页面后重试。",
      customLevelListTitle: "自定义关卡",
      customLevelNameHint: "留空时显示标签名称；最多 10 个字。",
      customLevelTagLabel: "题目来源标签",
      customLevelTagHint: "每个标签只能建一个关卡。",
      customLevelEmptyTitle: "还没有可用的标签",
      customLevelEmptyText: "先导入题目并创建标签，才能建立自定义关卡。",
      customLevelCloudHint: "绑定云端题库时不能修改本地自定义关卡。",
      customLevelBadge: "自定义",
      customLevelTagLine: "来源：{name}",
      customLevelFallbackName: "自定义关卡",
      customLevelNameFallback: "自定义 · {name}",
      customLevelTagCounts: "{total} 道 · 可用 {unused}",
      customLevelTagCountsEmpty: "{total} 道",
      customLevelEditorAvailable: "「{name}」可用 {n} 道题",
      customLevelEditorNoAvailable: "「{name}」暂时没有未使用的题。",
      customLevelAddTitle: "新建自定义关卡",
      customLevelDeleteConfirm: "确定删除自定义关卡「{name}」？题目和标签不会删除。",
      customLevelTagDuplicate: "这个标签已经建过关卡。",
      customLevelTagRequired: "请选择标签。",
      customLevelSaved: "自定义关卡已保存。",
      customLevelDeleted: "自定义关卡已删除。",
      levelsTotalHint: "关卡 {used} / {max}",
      customLevelCombined: "课程、自定义与错题复习关卡合计 {used} / {max}。",
      maintenanceSourceLabel: "清理范围",
      maintenanceSourceCourse: "课程/AI",
      maintenanceSourceCustom: "自定义",
      maintenanceSourceAll: "全部",
      maintenanceSourceCourseHint: "只清理课程/AI 题库，不会动自定义标签。",
      maintenanceSourceCustomHint: "只清理选中的标签，不影响其他标签和课程/AI 题库。",
      maintenanceSourceAllHint: "会同时清理课程/AI 和自定义题库（自定义 {custom} 道），请谨慎操作。",
      maintenanceSourceNoTags: "还没有自定义标签。",
      maintenanceTagOption: "{name}（{total} 道，可用 {unused}）",
      confirmClearAllMixed: "注意：本次会同时清理课程/AI 和自定义题（自定义 {n} 道）。",
      confirmClearCustom: "本次只清理标签「{name}」。",
      err_COLLECTION_CONFLICT: "标签已存在或正在使用，请换个名称。",
      err_COLLECTION_EXISTS: "已有同名标签。",
      err_COLLECTION_LIMIT: "最多只能有 20 个标签。",
      err_COLLECTION_NOT_FOUND: "标签不存在，请刷新后重试。",
      err_COLLECTION_REQUIRED: "请先选择或新建标签。",
      err_INVALID_COLLECTION_NAME: "标签名称需要 1–20 个字。",
      err_INVALID_CUSTOM_LEVEL: "自定义关卡配置不正确。",
      err_INVALID_IMPORT: "导入内容有错误，请按提示修改。",
      err_TOO_MANY_ITEMS: "一次最多导入 200 道题。",
      err_TOO_MANY_LEVELS: "课程、自定义与错题复习关卡合计最多 20 个。",
      err_INVALID_INDEX: "题目位置不正确，请重新解析。",
      importConvertPrompt: `你是题库格式转换助手。我会给你一批已有题目，请原样转换成学习机能导入的 JSON，只输出 JSON。不要新增、改写、合并题目。

输出字段：
version 固定为 1。
items 是题目数组。
prompt 是题干。
options 是选项数组。
type 只能是 single 或 multiple。
单选使用 answerIndex，多选使用 answerIndices，下标都从 0 开始。
硬性格式（先检查再输出）：
- 单选：type 为 "single"，只写一个 answerIndex（从 0 开始），只能有 1 个正确项。
- 多选：type 为 "multiple"，只写 answerIndices（升序、不重复）；至少 2 项，且必须少于 options 数量；不能全选，至少留 1 个错误项。4 个选项时 [0,1,2,3] 会被拒绝。
- 不要同时写 answerIndex 和 answerIndices；不要输出上面字段以外的字段。
explanation 是解析，可选。
difficulty 只能是 easy、medium、hard，可选。

参考样例：
{
  "version": 1,
  "items": [
    {
      "prompt": "12 + 8 等于多少？",
      "options": ["18", "20", "22", "24"],
      "type": "single",
      "answerIndex": 1,
      "explanation": "12 + 8 = 20，所以选 20。",
      "difficulty": "easy"
    },
    {
      "prompt": "下列哪些是哺乳动物？",
      "options": ["鲸", "企鹅", "蝙蝠", "海豚"],
      "type": "multiple",
      "answerIndices": [0, 2, 3],
      "explanation": "鲸、蝙蝠和海豚都是哺乳动物，企鹅是鸟类。",
      "difficulty": "medium"
    }
  ]
}

转换规则：
1. 只输出 JSON，不要解释，不要 Markdown 代码块，不要任何前后缀。
2. prompt 保留原题题意，去掉题号和选项，不改写、不补全、不合并，不超过 200 字。
3. options 保留原题选项和顺序，至少 2 个、至多 6 个；去掉选项前面的字母编号；每项不超过 60 字；内容不能重复。
4. 单选只写一个 answerIndex；多选写 answerIndices，按从小到大排列、去掉重复、至少两项。不要同时写两个答案字段，也不要把多选题压成一个答案。
5. explanation 只在原题有解析时保留，最多 600 字；原题没有解析就省略，不要自己编写。
6. difficulty 只在原题标注难度时填写，只能写 easy、medium、hard。
7. 判断题写成两个选项 ["正确", "错误"]，用 answerIndex 表示答案；组合选项按单选处理并原样保留。
8. 多选题至少 3 个选项、至少 2 个正确项，并至少保留 1 个错误项；不满足就整题跳过，不要猜答案，不要只挑一个正确项，也不要改成单选。
9. 填空题、听力题、看图题、缺少答案或答案不确定的题，整题跳过。
10. 不要生成资料里没有的题目，不要修改题干里的数字、单位和选项。
11. 题目数量不设上限，资料里的题全部转换，不要为了数量删减。
12. 输出前自检：JSON 能直接解析；所有字符串都用英文双引号；没有多余的逗号；每个答案下标都在 options 范围内；type 和答案字段匹配；items 数量与转换出的题数一致。
13. 资料是图片或 PDF 时，先完整识别文字再转换；看不清的字用“□”代替，不要猜测。
14. 如果一次回复放不下全部内容，先输出完整可解析的 JSON，我回复“继续”后再输出剩下的题，不要重复已经输出过的题。

下面是我要转换的题目资料：`,
      importGeneratePrompt: `你是出题助手。请按我给出的出题要求原创单选题和多选题，并整理成学习机能导入的 JSON，只输出 JSON。

输出字段：
version 固定为 1。
items 是题目数组。
prompt 是题干。
options 是选项数组。
type 只能是 single 或 multiple。
单选使用 answerIndex，多选使用 answerIndices，下标都从 0 开始。
硬性格式（先检查再输出）：
- 单选：type 为 "single"，只写一个 answerIndex（从 0 开始），只能有 1 个正确项。
- 多选：type 为 "multiple"，只写 answerIndices（升序、不重复）；至少 2 项，且必须少于 options 数量；不能全选，至少留 1 个错误项。4 个选项时 [0,1,2,3] 会被拒绝。
- 不要同时写 answerIndex 和 answerIndices；不要输出上面字段以外的字段。
explanation 是解析。
difficulty 只能是 easy、medium、hard。

参考样例：
{
  "version": 1,
  "items": [
    {
      "prompt": "12 + 8 等于多少？",
      "options": ["18", "20", "22", "24"],
      "type": "single",
      "answerIndex": 1,
      "explanation": "12 + 8 = 20。",
      "difficulty": "easy"
    },
    {
      "prompt": "下列哪些是哺乳动物？",
      "options": ["鲸", "企鹅", "蝙蝠", "海豚"],
      "type": "multiple",
      "answerIndices": [0, 2, 3],
      "explanation": "鲸、蝙蝠和海豚都是哺乳动物，企鹅是鸟类。",
      "difficulty": "medium"
    }
  ]
}

出题规则：
1. 只输出 JSON，不要解释，不要 Markdown 代码块，不要任何前后缀。
2. 按题型要求出题：单选写 "type":"single" 和 "answerIndex"；多选写 "type":"multiple" 和 "answerIndices"；混合题型两种都要有；没有写题型就默认单选。
3. prompt 完整、明确、无歧义，不带题号，不超过 200 字。
4. options 至少 2 个、至多 6 个，互不重复；多选至少 3 个选项；去掉选项前面的字母编号；每项不超过 60 字；干扰项要合理但明确错误；不要使用“以上都对”“以上都不对”这类依赖其他选项的答案。
5. 单选只能有一个正确项；多选至少有 2 个正确项，并至少保留 1 个错误项，不能把所有选项都标成正确。不要出填空题、听力题、看图题和需要图片才能作答的题。
6. explanation 用 1 到 3 句话说明为什么选这些答案，不超过 600 字；不确定就省略，不要编造。
7. difficulty 按题目实际难度填写，只能写 easy、medium、hard。
8. 题目必须符合指定年级和学科水平，不超纲；数字、单位、事实和答案都必须正确。
9. 同一批题目不要重复，也不要用只换数字或近义词的题凑数。
10. 题目数量不设上限，按出题要求里的题量输出，不要因为数量多就删减、跳过或重复。
11. 输出前自检：JSON 能直接解析；所有字符串都用英文双引号；没有多余的逗号；type 和答案字段匹配；单选只有一个答案；多选答案不少于 2 项且没有占满全部选项；每个答案下标都在 options 范围内；items 数量与题目数量一致。
12. 如果一次回复放不下全部内容，先输出完整可解析的 JSON，我回复“继续”后再输出剩下的题，不要重复已经输出过的题。

出题要求：
学段/年级：小学三年级
科目：数学
教材/知识点：人教版三年级上册 第一单元
题型：混合
题量：20 道
难度分布：easy 40%、medium 40%、hard 20%
输出语言：中文
额外要求：无`,
      // Keep in sync with the final heading of importGeneratePrompt.
      importGeneratePromptHeading: "出题要求：",
    },
    en: {
      bankImport: "Import bank",
      bankImportAction: "Manual import",
      bankSourceLine: "Preset + AI {course} · Manual {custom} · Wrong {wrong} · Total {total} / {capacity}",
      bankSourceCustomTag: "Tag: {name}",
      goImportBank: "Import bank",
      importTitle: "Import question bank",
      importSingleOnlyBadge: "Single and multiple choice",
      importSingleOnlyHint: "Single- and multiple-answer questions are imported; fill-in, listening, image-based and answerless questions are skipped.",
      importCloudNotice: "Local imports are unavailable while a cloud bank is linked. Unlink it first.",
      importTargetLabel: "Import into tag",
      importTagPlaceholder: "Type or choose a tag",
      importTagCount: "{n} questions",
      importTagSuggestEmpty: "Type a new name to create a tag",
      importTagSuggestNew: "+ New tag “{name}”",
      importTagSuggestAria: "Existing tag suggestions",
      importTagClear: "Clear tag name",
      importNoTagsHint: "There are no tags yet; importing will create one.",
      importTargetHint: "Importing into existing tag “{name}”",
      importTargetNewHint: "A tag “{name}” will be created and saved after the import succeeds",
      importTargetNone: "Choose an existing tag or type a new name.",
      importTargetBadgeNew: "New",
      importTargetBadgeExisting: "Existing",
      importInputLabel: "Question content",
      importChooseFile: "Choose a .json or .txt file",
      importPasteLabel: "Or paste questions directly",
      importPasteClear: "Clear",
      importTextPlaceholder: "Paste canonical JSON or numbered text (1. question / A. option / Answer: B or A, C)",
      importCopyConvertPrompt: "Copy format-conversion prompt",
      importCopyGeneratePrompt: "Copy question-generation prompt",
      importPromptConvertTitle: "Format-conversion prompt",
      importPromptGenerateTitle: "Question-generation prompt",
      importPromptHint: "Have existing questions? Use format conversion. Starting from scratch? Use question generation. Copy one into any AI assistant, then save its JSON or paste it here.",
      importAiHint: "You can first have a familiar AI web assistant organize photos, PDFs, or messy text; remove names, school, student numbers, and other private data before importing. The importer only validates text and uploads nothing.",
      importDefaultDifficulty: "Used when a question has no difficulty",
      importParse: "Parse and preview",
      importParsing: "Parsing…",
      importEmptyInput: "Choose a file or paste questions first.",
      importTooLarge: "The file is larger than 16 MB; split it into smaller files.",
      importTooMany: "At most {max} questions can be imported at once; split the file.",
      importLargeRisk: "A large import can take several minutes; keep this page open. Questions are stored only on this TV and are not backed up; clearing app data deletes them.",
      importJsonInvalid: "Invalid JSON; check quotes, commas, and brackets.",
      importNoQuestions: "No questions were found.",
      importFileReadFailed: "Could not read the file. Choose another file or paste the text.",
      importSelectTagFirst: "Choose an existing tag or type a new name first.",
      importNewTagInvalid: "A tag name must be 1–20 characters.",
      importNewTagExists: "A tag with that name already exists.",
      importNewTagLimit: "You can have at most 20 tags.",
      importErrorAt: "Question {n}",
      importPreviewTitle: "Import preview",
      importPreviewTarget: "Target tag",
      importPreviewTotal: "Questions in file",
      importPreviewFileDup: "Duplicates in file",
      importPreviewCollectionDup: "Already in this tag",
      importPreviewCrossDup: "Duplicates in other sources (kept)",
      importPreviewAppend: "Will add",
      importPreviewRemaining: "Bank space left",
      importPreviewRemainingValue: "{remaining} / {capacity} questions",
      importPreviewNoNew: "No new questions; every item already exists in the file or this tag.",
      importPreviewRejected: "{n} questions failed validation; check answers and options.",
      importPreviewNotEnough: "Not enough bank space; {n} more rows are needed. Clear some in Me → Bank maintenance.",
      importPreviewCrossNote: "{n} items already exist in other sources; they will still be added as independent copies under this tag.",
      importConfirm: "Import {n} questions",
      importImporting: "Importing…",
      importResultTitle: "Imported {n} questions into “{name}”",
      importResultDetail: "Duplicates within each batch: {file} · already in this tag: {collection}",
      importResultCross: "Duplicates in other sources: {n} (kept)",
      importResultRemaining: "Bank space left: {remaining} / {capacity}",
      importPreviewFormat: "File format",
      importPreviewFileSize: "File size",
      importPreviewBatches: "Import batches",
      importFormatJson: "JSON",
      importFormatText: "Numbered text",
      importPreviewLocalNote: "This preview parses the file locally only to confirm its format and shows 10 items per page. Deduplication and validation are final only in the import result. Up to {max} questions and {size} per import.",
      importPagePrev: "Previous",
      importPageNext: "Next",
      importPageGo: "Go",
      importPageJump: "Jump to page",
      importPreviewPage: "Page {page} / {pages}",
      importProgressCount: "Processed {processed} / {total} questions",
      importProgressBatch: "Batch {batch} / {batches}",
      importProgressStats: "Added {appended} · duplicates within each batch {file} · already in tag {collection} · other sources {cross} · rejected {rejected}",
      importProgressHint: "Keep this page open while importing. Finished batches are kept; cancel stops only the remaining batches.",
      importCancel: "Cancel import",
      importCancelRequested: "Stopping…",
      importResultPartialTitle: "Partial import: wrote {n} questions into “{name}”",
      importResultProcessed: "Processed {processed} / {total} questions",
      importResultCancelled: "Cancelled; batches already written are kept. Fix the file and retry; duplicates will not be written twice.",
      importResultFailed: "Batch {batch} failed and later batches were not imported. Batches already written are kept; retrying after a fix will not duplicate them.",
      importPromptCopied: "Prompt copied.",
      importCopyFailed: "Copy failed. Open the prompt and copy it manually.",
      importErrorCopy: "Copy errors for AI repair",
      importErrorCopied: "Copied. Paste it into your AI assistant.",
      importErrorCopyFailed: "Copy failed. Select the error messages and copy them manually.",
      importErrorCopyIntro: "I got these errors while importing questions into a study device:",
      importErrorCopyAsk: "Fix the questions by number above and return the complete repaired result. Requirements:\n1. Output only the complete JSON (version and items): no explanation, no Markdown code fence, no prefix or suffix.\n2. Do not change any question except the ones listed above; do not delete or merge questions.\n3. Use ASCII double quotes, no trailing commas, and keep every answer index within options.\n4. Multiple-choice questions need at least 3 options and at least 2 correct answers, and must not mark every option correct: leave at least 1 incorrect option. If every option in the original question is correct, change one option to a clearly incorrect statement; do not convert it to single choice or delete it.",
      importErrorCopySource: "Original data:",
      importErrorCopySourceOmitted: "(The original data is too large to include automatically; paste the complete original data for the AI.)",
      importField_item: "Question",
      importField_items: "Question list",
      importField_text: "Text",
      importField_prompt: "Prompt",
      importField_options: "Options",
      importField_answer: "Answer",
      importField_explanation: "Solution",
      importField_difficulty: "Difficulty",
      importCode_TYPE: "Wrong field type",
      importCode_UNKNOWN_FIELD: "Unsupported field",
      importCode_DUPLICATE: "Duplicate content",
      importCode_REQUIRED: "Required content is missing",
      importCode_ORDER: "Content is in the wrong order",
      importCode_FORMAT: "Wrong format",
      importCode_COUNT: "There must be 2–6 options",
      importCode_INVALID_TEXT: "Text is empty or too long",
      importCode_TOO_LONG: "Text is too long",
      importCode_ANSWER_FORMAT: "Invalid answer format: use one letter (A–F) or one exact option text for single choice, or several letters or an answerIndices array for multiple choice",
      importCode_OPTIONS_TOO_FEW: "Multiple-choice questions need at least 3 options",
      importCode_ANSWER_TOO_FEW: "Multiple-choice questions need at least 2 correct answers",
      importCode_ANSWER_ALL: "Multiple-choice answers cannot include every option; leave at least 1 incorrect option",
      importCode_ANSWER_RANGE: "The answer is outside the options",
      importCode_ANSWER_CONFLICT: "Use only one of answer, answerIndex, or answerIndices",
      importCode_INVALID_DIFFICULTY: "Difficulty must be easy, medium, or hard",
      importCode_NO_ITEMS: "No questions",
      importCode_TOO_MANY_ITEMS: "At most 200 questions at a time",
      levelAddCourseTitle: "Textbook levels",
      levelAddCourseMeta: "Grade · subject · unit",
      levelAddCustomTitle: "Custom levels",
      levelAddCustomMeta: "From imported tags",
      levelAddCustomMetaNoTags: "Import questions first",
      levelAddCustomMetaCloud: "Unavailable in cloud mode",
      levelAddWrongTitle: "Wrong review",
      levelAddWrongMeta: "All subjects · clears mistakes",
      levelAddWrongMetaEmpty: "No mistakes yet; wrong answers join automatically",
      levelAddWrongMetaCloud: "Unavailable in cloud mode",
      wrongLevelListTitle: "Wrong review",
      wrongLevelListLead: "Only one wrong-review level; it automatically covers mistakes from every subject.",
      wrongLevelListCount: "{n}/1",
      wrongLevelBadge: "Review",
      wrongLevelMeta: "All subjects · a correct answer clears the mistake",
      wrongLevelFallbackName: "Wrong review",
      wrongLevelAvailable: "{n} mistakes · up to {pull} per round",
      wrongLevelEmptyPool: "No mistakes right now; wrong answers join automatically",
      wrongLevelAddTitle: "New wrong-review level",
      wrongLevelEditTitle: "Edit wrong review",
      wrongLevelEditorLead: "Wrong review ignores subjects and draws from every local wrong answer. A correct answer removes that mistake.",
      wrongLevelNameHint: "Leave blank to show “Wrong review”; at most 10 characters.",
      wrongLevelEditorAvailable: "{n} mistakes · up to {pull} per round.",
      wrongLevelEditorEmpty: "No mistakes right now. Wrong answers join automatically and can be reviewed later.",
      wrongLevelDeleteConfirm: "Delete the wrong-review level “{name}”? The mistakes themselves are kept.",
      wrongLevelDeleteAria: "Delete wrong review “{name}”",
      wrongLevelSaved: "Wrong-review level saved.",
      wrongLevelDeleted: "Wrong-review level deleted.",
      err_INVALID_WRONG_LEVEL: "The wrong-review level is invalid. Refresh the page and try again.",
      customLevelListTitle: "Custom levels",
      customLevelNameHint: "Leave blank to show the tag name; at most 10 characters.",
      customLevelTagLabel: "Question source tag",
      customLevelTagHint: "Each tag can back only one level.",
      customLevelEmptyTitle: "No usable tags yet",
      customLevelEmptyText: "Import questions and create a tag first, then you can add a custom level.",
      customLevelCloudHint: "Local custom levels cannot be changed while a cloud bank is linked.",
      customLevelBadge: "Custom",
      customLevelTagLine: "Source: {name}",
      customLevelFallbackName: "Custom level",
      customLevelNameFallback: "Custom · {name}",
      customLevelTagCounts: "{total} questions · {unused} available",
      customLevelTagCountsEmpty: "{total} questions",
      customLevelEditorAvailable: "“{name}” has {n} available questions",
      customLevelEditorNoAvailable: "“{name}” has no unused questions right now.",
      customLevelAddTitle: "New custom level",
      customLevelDeleteConfirm: "Delete the custom level “{name}”? Questions and the tag are kept.",
      customLevelTagDuplicate: "This tag already backs a level.",
      customLevelTagRequired: "Choose a tag.",
      customLevelSaved: "Custom level saved.",
      customLevelDeleted: "Custom level deleted.",
      levelsTotalHint: "Levels {used} / {max}",
      customLevelCombined: "Course, custom, and wrong-review levels: {used} / {max}.",
      maintenanceSourceLabel: "Clear scope",
      maintenanceSourceCourse: "Course/AI",
      maintenanceSourceCustom: "Custom",
      maintenanceSourceAll: "All",
      maintenanceSourceCourseHint: "Clears only the course/AI bank; custom tags are untouched.",
      maintenanceSourceCustomHint: "Clears only the selected tag; other tags and the course/AI bank are untouched.",
      maintenanceSourceAllHint: "Clears both the course/AI and custom banks ({custom} custom questions). Use with care.",
      maintenanceSourceNoTags: "There are no custom tags yet.",
      maintenanceTagOption: "{name} ({total} questions, {unused} available)",
      confirmClearAllMixed: "Warning: this also clears custom questions ({n} custom rows).",
      confirmClearCustom: "This clears only the tag “{name}”.",
      err_COLLECTION_CONFLICT: "The tag already exists or is in use; choose another name.",
      err_COLLECTION_EXISTS: "A tag with that name already exists.",
      err_COLLECTION_LIMIT: "You can have at most 20 tags.",
      err_COLLECTION_NOT_FOUND: "The tag no longer exists; refresh and try again.",
      err_COLLECTION_REQUIRED: "Choose or create a tag first.",
      err_INVALID_COLLECTION_NAME: "A tag name must be 1–20 characters.",
      err_INVALID_CUSTOM_LEVEL: "The custom level settings are invalid.",
      err_INVALID_IMPORT: "The import contains errors; fix them using the messages.",
      err_TOO_MANY_ITEMS: "At most 200 questions can be imported at once.",
      err_TOO_MANY_LEVELS: "Course, custom, and wrong-review levels together cannot exceed 20.",
      err_INVALID_INDEX: "The question position is invalid; parse the import again.",
      importConvertPrompt: `You convert question banks into the import format. I will give you a set of existing questions; convert them as-is into JSON that a study device can import. Output only JSON. Do not add, rewrite, or merge questions.

Output fields:
version is always 1.
items is the array of questions.
prompt is the question stem.
options is the array of options.
type is either single or multiple.
Use answerIndex for single-answer questions and answerIndices for multiple-answer questions; indices start at 0.
Hard format rules (check before output):
- Single choice: type is "single", use exactly one answerIndex (0-based); exactly one correct option.
- Multiple choice: type is "multiple", use only answerIndices (ascending, no duplicates); at least 2 entries and fewer than the number of options; never mark every option correct (leave at least 1 wrong option). With 4 options, [0,1,2,3] is rejected.
- Never write both answerIndex and answerIndices; do not output fields other than those listed above.
explanation is the solution and is optional.
difficulty is only easy, medium, or hard and is optional.

Reference sample:
{
  "version": 1,
  "items": [
    {
      "prompt": "What is 12 + 8?",
      "options": ["18", "20", "22", "24"],
      "type": "single",
      "answerIndex": 1,
      "explanation": "12 + 8 = 20, so the answer is 20.",
      "difficulty": "easy"
    },
    {
      "prompt": "Which of these are mammals?",
      "options": ["whale", "penguin", "bat", "dolphin"],
      "type": "multiple",
      "answerIndices": [0, 2, 3],
      "explanation": "Whales, bats, and dolphins are mammals; a penguin is a bird.",
      "difficulty": "medium"
    }
  ]
}

Conversion rules:
1. Output only the JSON: no explanation, no Markdown code fence, no prefix or suffix.
2. prompt keeps the original meaning and removes the question number and options; do not rewrite, complete, or merge it; at most 200 characters.
3. options keeps the original options and order, at least 2 and at most 6; remove letter prefixes; each at most 60 characters; no duplicate content.
4. Single-answer questions use exactly one answerIndex. Multiple-answer questions use answerIndices, sorted ascending, deduplicated, with at least two entries. Never write both answer fields, and never collapse a multiple-answer question into one answer.
5. explanation is kept only when the source has a solution, at most 600 characters; omit it otherwise and never invent one.
6. difficulty is filled only when the source marks it, and only easy, medium, or hard.
7. True/false questions become two options ["True", "False"] with an answerIndex. Combination options are treated as single-answer and kept as-is.
8. A multiple-answer question needs at least 3 options, at least 2 correct options, and at least 1 wrong option. If it does not, skip the whole question; do not guess, do not pick one correct option, and do not turn it into a single-answer question.
9. Skip fill-in-the-blank, listening, image-based, answerless, or uncertain-answer questions entirely.
10. Do not create questions that are not in the material, and do not change numbers, units, or options in the stem.
11. There is no limit on the number of questions; convert every question in the material and do not drop any for length.
12. Self-check before output: the JSON parses; all strings use ASCII double quotes; no trailing commas; every answer index is within options; type matches the answer fields; the number of items matches the number of converted questions.
13. If the material is an image or PDF, recognize all text first; replace unclear characters with "□" and do not guess.
14. If one reply cannot hold everything, output a complete parseable JSON first; after I reply "continue", output the remaining questions and do not repeat any.

Here is the question material I want to convert:`,
      importGeneratePrompt: `You create quiz questions. Create original single- and multiple-answer questions according to the requirements I give you, and format them as JSON that a study device can import. Output only JSON.

Output fields:
version is always 1.
items is the array of questions.
prompt is the question stem.
options is the array of options.
type is either single or multiple.
Use answerIndex for single-answer questions and answerIndices for multiple-answer questions; indices start at 0.
Hard format rules (check before output):
- Single choice: type is "single", use exactly one answerIndex (0-based); exactly one correct option.
- Multiple choice: type is "multiple", use only answerIndices (ascending, no duplicates); at least 2 entries and fewer than the number of options; never mark every option correct (leave at least 1 wrong option). With 4 options, [0,1,2,3] is rejected.
- Never write both answerIndex and answerIndices; do not output fields other than those listed above.
explanation is the solution.
difficulty is only easy, medium, or hard.

Reference sample:
{
  "version": 1,
  "items": [
    {
      "prompt": "What is 12 + 8?",
      "options": ["18", "20", "22", "24"],
      "type": "single",
      "answerIndex": 1,
      "explanation": "12 + 8 = 20.",
      "difficulty": "easy"
    },
    {
      "prompt": "Which of these are mammals?",
      "options": ["whale", "penguin", "bat", "dolphin"],
      "type": "multiple",
      "answerIndices": [0, 2, 3],
      "explanation": "Whales, bats, and dolphins are mammals; a penguin is a bird.",
      "difficulty": "medium"
    }
  ]
}

Generation rules:
1. Output only the JSON: no explanation, no Markdown code fence, no prefix or suffix.
2. Follow the requested question type: single-answer questions use "type":"single" and "answerIndex"; multiple-answer questions use "type":"multiple" and "answerIndices"; mixed means both types; if no type is given, default to single-answer.
3. prompt is complete, clear, and unambiguous, without a question number; at most 200 characters.
4. options has at least 2 and at most 6 entries with no duplicates; multiple-answer questions need at least 3; remove letter prefixes; each at most 60 characters; distractors must look plausible but be clearly wrong; do not use options such as "all of the above" or "none of the above".
5. Single-answer questions have exactly one correct option. Multiple-answer questions have at least 2 correct options and at least 1 wrong option; never mark every option correct. Do not create fill-in-the-blank, listening, image-based, or picture-dependent questions.
6. explanation uses 1 to 3 sentences to explain why these answers are correct, at most 600 characters; omit it if unsure and never invent facts.
7. difficulty reflects the actual difficulty and is only easy, medium, or hard.
8. Questions must fit the requested grade and subject and stay within the curriculum; numbers, units, facts, and answers must be correct.
9. Do not repeat questions in one batch, and do not pad the count with variants that only change numbers or synonyms.
10. There is no limit on the number of questions; follow the number in the requirements; do not drop, skip, or repeat questions because there are many.
11. Self-check before output: the JSON parses; all strings use ASCII double quotes; no trailing commas; type matches the answer fields; single-answer questions have exactly one answer; multiple-answer questions have at least two answers and do not mark every option correct; every answer index is within options; the number of items matches the number of questions.
12. If one reply cannot hold everything, output a complete parseable JSON first; after I reply "continue", output the remaining questions and do not repeat any.

Question requirements:
Grade/level: Grade 3, primary school
Subject: Mathematics
Textbook/topic: Unit 1, first semester
Question type: mixed
Number of questions: 20
Difficulty mix: easy 40%, medium 40%, hard 20%
Output language: English
Extra requirements: none`,
      // Keep in sync with the final heading of importGeneratePrompt.
      importGeneratePromptHeading: "Question requirements:",
    },
    ru: {
      bankImport: "Импорт банка",
      bankImportAction: "Импорт вручную",
      bankSourceLine: "Готовые + ИИ {course} · Ручной импорт {custom} · Ошибки {wrong} · Всего {total} / {capacity}",
      bankSourceCustomTag: "Тег: {name}",
      goImportBank: "Импортировать банк",
      importTitle: "Импорт банка вопросов",
      importSingleOnlyBadge: "Один или несколько ответов",
      importSingleOnlyHint: "Импортируются вопросы с одним и несколькими ответами; вопросы с пропусками, аудированием, изображениями и без ответа пропускаются.",
      importCloudNotice: "При привязке к облачному банку импорт локальных вопросов недоступен. Сначала отвяжите облако.",
      importTargetLabel: "Импортировать в тег",
      importTagPlaceholder: "Введите или выберите тег",
      importTagCount: "{n} вопр.",
      importTagSuggestEmpty: "Введите новое название, чтобы создать тег",
      importTagSuggestNew: "+ Новый тег «{name}»",
      importTagSuggestAria: "Подсказки существующих тегов",
      importTagClear: "Очистить название тега",
      importNoTagsHint: "Тегов пока нет; при импорте он будет создан.",
      importTargetHint: "Импорт в существующий тег «{name}»",
      importTargetNewHint: "Тег «{name}» будет создан и сохранён после успешного импорта",
      importTargetNone: "Выберите существующий тег или введите новое название.",
      importTargetBadgeNew: "Новый",
      importTargetBadgeExisting: "Существующий",
      importInputLabel: "Содержимое вопросов",
      importChooseFile: "Выбрать файл .json или .txt",
      importPasteLabel: "Или вставьте вопросы",
      importPasteClear: "Очистить",
      importTextPlaceholder: "Вставьте канонический JSON или нумерованный текст (1. вопрос / A. вариант / Ответ: B или A, C)",
      importCopyConvertPrompt: "Копировать промпт для преобразования формата",
      importCopyGeneratePrompt: "Копировать промпт для создания вопросов",
      importPromptConvertTitle: "Промпт для преобразования формата",
      importPromptGenerateTitle: "Промпт для создания вопросов",
      importPromptHint: "Есть готовые вопросы? Используйте преобразование формата. Нужно создать с нуля? Используйте создание вопросов. Скопируйте промпт в любой ИИ-помощник, затем сохраните полученный JSON или вставьте его сюда.",
      importAiHint: "Фото, PDF или неаккуратный текст можно сначала упорядочить в обычном веб-помощнике ИИ; перед импортом удалите имена, школу, номер ученика и другие личные данные. Импортёр только проверяет текст и ничего не загружает.",
      importDefaultDifficulty: "Использовать, если сложность не указана",
      importParse: "Проверить и показать",
      importParsing: "Проверка…",
      importEmptyInput: "Сначала выберите файл или вставьте вопросы.",
      importTooLarge: "Файл больше 16 МБ; разделите его на части.",
      importTooMany: "За один раз можно импортировать не более {max} вопросов; разделите файл.",
      importLargeRisk: "Большой импорт может занять несколько минут; не закрывайте страницу. Вопросы хранятся только на этом телевизоре и не резервируются; очистка данных приложения их удалит.",
      importJsonInvalid: "Неверный JSON: проверьте кавычки, запятые и скобки.",
      importNoQuestions: "Вопросы не найдены.",
      importFileReadFailed: "Не удалось прочитать файл. Выберите другой или вставьте текст.",
      importSelectTagFirst: "Сначала выберите существующий тег или введите новое название.",
      importNewTagInvalid: "Название тега должно содержать 1–20 символов.",
      importNewTagExists: "Тег с таким названием уже есть.",
      importNewTagLimit: "Можно создать не более 20 тегов.",
      importErrorAt: "Вопрос {n}",
      importPreviewTitle: "Предпросмотр импорта",
      importPreviewTarget: "Целевой тег",
      importPreviewTotal: "Вопросов в файле",
      importPreviewFileDup: "Повторы в файле",
      importPreviewCollectionDup: "Уже есть в теге",
      importPreviewCrossDup: "Повторы из других источников (сохраняются)",
      importPreviewAppend: "Будет добавлено",
      importPreviewRemaining: "Остаток банка",
      importPreviewRemainingValue: "{remaining} / {capacity} вопр.",
      importPreviewNoNew: "Новых вопросов нет: все уже есть в файле или теге.",
      importPreviewRejected: "{n} вопросов не прошли проверку; проверьте ответы и варианты.",
      importPreviewNotEnough: "Не хватает места: нужно ещё {n}. Освободите место в «Я → Обслуживание банка».",
      importPreviewCrossNote: "В других источниках уже есть {n} таких вопросов; они будут добавлены как отдельные копии этого тега.",
      importConfirm: "Импортировать {n} вопр.",
      importImporting: "Импорт…",
      importResultTitle: "Импортировано {n} в «{name}»",
      importResultDetail: "Повторов внутри пакетов: {file} · уже в теге: {collection}",
      importResultCross: "Повторов из других источников: {n} (сохранены)",
      importResultRemaining: "Осталось {remaining} / {capacity}",
      importPreviewFormat: "Формат файла",
      importPreviewFileSize: "Размер файла",
      importPreviewBatches: "Пакеты импорта",
      importFormatJson: "JSON",
      importFormatText: "Нумерованный текст",
      importPreviewLocalNote: "Предпросмотр разбирает файл локально только для проверки формата и показывает 10 вопросов на страницу. Дедупликация и проверка окончательны только в результате импорта. За раз не более {max} вопросов и {size}.",
      importPagePrev: "Назад",
      importPageNext: "Далее",
      importPageGo: "Перейти",
      importPageJump: "Перейти к странице",
      importPreviewPage: "Страница {page} / {pages}",
      importProgressCount: "Обработано {processed} / {total} вопр.",
      importProgressBatch: "Пакет {batch} / {batches}",
      importProgressStats: "Добавлено {appended} · повторов внутри пакетов {file} · уже в теге {collection} · из других источников {cross} · не прошли {rejected}",
      importProgressHint: "Не закрывайте страницу во время импорта. Завершённые пакеты сохраняются; отмена останавливает только следующие.",
      importCancel: "Отменить импорт",
      importCancelRequested: "Остановка…",
      importResultPartialTitle: "Частичный импорт: записано {n} в «{name}»",
      importResultProcessed: "Обработано {processed} / {total} вопр.",
      importResultCancelled: "Отменено; уже записанные пакеты сохранены. Исправьте файл и повторите — дубли не запишутся повторно.",
      importResultFailed: "Пакет {batch} завершился ошибкой, следующие пакеты не импортированы. Записанные пакеты сохранены; повтор после исправления не создаст дубли.",
      importPromptCopied: "Промпт скопирован.",
      importCopyFailed: "Не удалось скопировать. Откройте промпт и скопируйте вручную.",
      importErrorCopy: "Скопировать ошибки для ИИ",
      importErrorCopied: "Скопировано. Вставьте в ИИ-помощник.",
      importErrorCopyFailed: "Не удалось скопировать. Выделите сообщения об ошибках и скопируйте вручную.",
      importErrorCopyIntro: "При импорте вопросов в учебное устройство возникли ошибки:",
      importErrorCopyAsk: "Исправьте вопросы по указанным выше номерам и верните полный исправленный результат. Требования:\n1. Выводите только полный JSON (version и items): без пояснений, без блока Markdown, без префиксов и суффиксов.\n2. Не изменяйте вопросы, кроме перечисленных выше; не удаляйте и не объединяйте вопросы.\n3. Используйте прямые двойные кавычки, без висячих запятых; все индексы ответов должны быть в пределах options.\n4. В вопросе с несколькими ответами должно быть не менее 3 вариантов и не менее 2 правильных ответов; нельзя отмечать все варианты как правильные — оставьте хотя бы 1 неверный вариант. Если в исходном вопросе все варианты правильные, измените один вариант на явно неверное утверждение; не превращайте вопрос в вопрос с одним ответом и не удаляйте его.",
      importErrorCopySource: "Исходные данные:",
      importErrorCopySourceOmitted: "(Исходные данные слишком велики для автоматического добавления; вставьте полный текст для ИИ.)",
      importField_item: "Вопрос",
      importField_items: "Список вопросов",
      importField_text: "Текст",
      importField_prompt: "Условие",
      importField_options: "Варианты",
      importField_answer: "Ответ",
      importField_explanation: "Решение",
      importField_difficulty: "Сложность",
      importCode_TYPE: "Неверный тип поля",
      importCode_UNKNOWN_FIELD: "Неподдерживаемое поле",
      importCode_DUPLICATE: "Повторяющееся содержимое",
      importCode_REQUIRED: "Не хватает обязательных данных",
      importCode_ORDER: "Неверный порядок данных",
      importCode_FORMAT: "Неверный формат",
      importCode_COUNT: "Должно быть 2–6 вариантов",
      importCode_INVALID_TEXT: "Пустой текст или превышена длина",
      importCode_TOO_LONG: "Слишком длинный текст",
      importCode_ANSWER_FORMAT: "Неверный формат ответа: для одного ответа — одна буква A–F или точный текст варианта; для нескольких — несколько букв или массив answerIndices",
      importCode_OPTIONS_TOO_FEW: "В вопросе с несколькими ответами должно быть не менее 3 вариантов",
      importCode_ANSWER_TOO_FEW: "В вопросе с несколькими ответами должно быть не менее 2 правильных вариантов",
      importCode_ANSWER_ALL: "Нельзя отмечать все варианты как правильные: оставьте хотя бы 1 неверный вариант",
      importCode_ANSWER_RANGE: "Ответ вне диапазона вариантов",
      importCode_ANSWER_CONFLICT: "Укажите только одно из полей answer, answerIndex или answerIndices",
      importCode_INVALID_DIFFICULTY: "Сложность: только easy, medium или hard",
      importCode_NO_ITEMS: "Нет вопросов",
      importCode_TOO_MANY_ITEMS: "Не более 200 вопросов за раз",
      levelAddCourseTitle: "Учебные уровни",
      levelAddCourseMeta: "Класс · предмет · раздел",
      levelAddCustomTitle: "Свои уровни",
      levelAddCustomMeta: "Из импортированных тегов",
      levelAddCustomMetaNoTags: "Сначала импортируйте вопросы",
      levelAddCustomMetaCloud: "Недоступно в облачном режиме",
      levelAddWrongTitle: "Повторение ошибок",
      levelAddWrongMeta: "Все предметы · ошибки исчезают",
      levelAddWrongMetaEmpty: "Ошибок пока нет; неверные ответы добавятся сами",
      levelAddWrongMetaCloud: "Недоступно в облачном режиме",
      wrongLevelListTitle: "Повторение ошибок",
      wrongLevelListLead: "Уровень повторения один и автоматически включает ошибки по всем предметам.",
      wrongLevelListCount: "{n}/1",
      wrongLevelBadge: "Ошибки",
      wrongLevelMeta: "Все предметы · верный ответ убирает ошибку",
      wrongLevelFallbackName: "Повторение ошибок",
      wrongLevelAvailable: "Ошибок: {n} · до {pull} за раунд",
      wrongLevelEmptyPool: "Ошибок сейчас нет; неверные ответы добавятся сами",
      wrongLevelAddTitle: "Новый уровень повторения",
      wrongLevelEditTitle: "Изменить повторение ошибок",
      wrongLevelEditorLead: "Повторение не делится по предметам и берёт все локальные ошибки. Верный ответ убирает ошибку.",
      wrongLevelNameHint: "Если пусто, показывается «Повторение ошибок»; до 10 символов.",
      wrongLevelEditorAvailable: "Ошибок: {n} · до {pull} за раунд.",
      wrongLevelEditorEmpty: "Ошибок сейчас нет. Неверные ответы добавятся сами, и их можно будет повторить.",
      wrongLevelDeleteConfirm: "Удалить уровень повторения «{name}»? Сами ошибки сохранятся.",
      wrongLevelDeleteAria: "Удалить повторение ошибок «{name}»",
      wrongLevelSaved: "Уровень повторения сохранён.",
      wrongLevelDeleted: "Уровень повторения удалён.",
      err_INVALID_WRONG_LEVEL: "Некорректный уровень повторения. Обновите страницу и повторите.",
      customLevelListTitle: "Свои уровни",
      customLevelNameHint: "Если пусто, показывается название тега; до 10 символов.",
      customLevelTagLabel: "Тег источника вопросов",
      customLevelTagHint: "Для каждого тега можно создать один уровень.",
      customLevelEmptyTitle: "Пока нет доступных тегов",
      customLevelEmptyText: "Сначала импортируйте вопросы и создайте тег, затем можно создать свой уровень.",
      customLevelCloudHint: "При облачном банке локальные свои уровни изменять нельзя.",
      customLevelBadge: "Свой",
      customLevelTagLine: "Источник: {name}",
      customLevelFallbackName: "Свой уровень",
      customLevelNameFallback: "Свой · {name}",
      customLevelTagCounts: "{total} вопр. · свободно {unused}",
      customLevelTagCountsEmpty: "{total} вопр.",
      customLevelEditorAvailable: "«{name}»: доступно {n} вопросов",
      customLevelEditorNoAvailable: "В «{name}» сейчас нет неиспользованных вопросов.",
      customLevelAddTitle: "Новый свой уровень",
      customLevelDeleteConfirm: "Удалить свой уровень «{name}»? Вопросы и тег останутся.",
      customLevelTagDuplicate: "Для этого тега уже есть уровень.",
      customLevelTagRequired: "Выберите тег.",
      customLevelSaved: "Свой уровень сохранён.",
      customLevelDeleted: "Свой уровень удалён.",
      levelsTotalHint: "Уровни {used} / {max}",
      customLevelCombined: "Курсовые, свои и уровни повторения ошибок: {used} / {max}.",
      maintenanceSourceLabel: "Область очистки",
      maintenanceSourceCourse: "Курс/ИИ",
      maintenanceSourceCustom: "Свои",
      maintenanceSourceAll: "Все",
      maintenanceSourceCourseHint: "Очищается только банк курса/ИИ; свои теги не затрагиваются.",
      maintenanceSourceCustomHint: "Очищается только выбранный тег; другие теги и курс/ИИ не затрагиваются.",
      maintenanceSourceAllHint: "Будут очищены и курс/ИИ, и свои вопросы (своих: {custom}). Действуйте осторожно.",
      maintenanceSourceNoTags: "Своих тегов пока нет.",
      maintenanceTagOption: "{name} ({total} вопр., свободно {unused})",
      confirmClearAllMixed: "Внимание: будут очищены и курс/ИИ, и свои вопросы (своих: {n}).",
      confirmClearCustom: "Будет очищен только тег «{name}».",
      err_COLLECTION_CONFLICT: "Тег уже существует или используется. Выберите другое название.",
      err_COLLECTION_EXISTS: "Тег с таким названием уже есть.",
      err_COLLECTION_LIMIT: "Можно создать не более 20 тегов.",
      err_COLLECTION_NOT_FOUND: "Тег не найден. Обновите страницу.",
      err_COLLECTION_REQUIRED: "Сначала выберите или создайте тег.",
      err_INVALID_COLLECTION_NAME: "Название тега должно содержать 1–20 символов.",
      err_INVALID_CUSTOM_LEVEL: "Неверная настройка своего уровня.",
      err_INVALID_IMPORT: "В импортируемых данных есть ошибки. Исправьте их по подсказкам.",
      err_TOO_MANY_ITEMS: "Не более 200 вопросов за один раз.",
      err_TOO_MANY_LEVELS: "Всего можно создать не более 20 курсовых, своих уровней и уровней повторения ошибок.",
      err_INVALID_INDEX: "Неверная позиция вопроса. Проверьте импорт заново.",
      importConvertPrompt: `Ты помощник по преобразованию банка вопросов. Я дам тебе готовые вопросы; преобразуй их как есть в JSON, который можно импортировать на учебное устройство. Выводи только JSON. Не добавляй, не переписывай и не объединяй вопросы.

Поля вывода:
version всегда 1.
items — массив вопросов.
prompt — условие вопроса.
options — массив вариантов.
type — только single или multiple.
Для одного ответа используй answerIndex, для нескольких — answerIndices; индексы начинаются с 0.
Жёсткие правила формата (проверь перед выводом):
- Один ответ: type "single", ровно один answerIndex (с 0); только один правильный вариант.
- Несколько ответов: type "multiple", только answerIndices (по возрастанию, без повторов); не менее 2 элементов и меньше числа вариантов; нельзя отмечать все варианты правильными (оставь хотя бы 1 неверный). При 4 вариантах [0,1,2,3] будет отклонён.
- Не пиши одновременно answerIndex и answerIndices; не выводи поля, которых нет в списке выше.
explanation — решение, необязательно.
difficulty — только easy, medium или hard, необязательно.

Образец:
{
  "version": 1,
  "items": [
    {
      "prompt": "Сколько будет 12 + 8?",
      "options": ["18", "20", "22", "24"],
      "type": "single",
      "answerIndex": 1,
      "explanation": "12 + 8 = 20, поэтому правильный ответ 20.",
      "difficulty": "easy"
    },
    {
      "prompt": "Какие из этих животных — млекопитающие?",
      "options": ["кит", "пингвин", "летучая мышь", "дельфин"],
      "type": "multiple",
      "answerIndices": [0, 2, 3],
      "explanation": "Кит, летучая мышь и дельфин — млекопитающие, пингвин — птица.",
      "difficulty": "medium"
    }
  ]
}

Правила преобразования:
1. Выводи только JSON: без объяснений, без блока Markdown, без префиксов и суффиксов.
2. prompt сохраняет смысл исходного вопроса, без номера и вариантов; не переписывай, не дополняй и не объединяй вопросы; не более 200 символов.
3. options сохраняет исходные варианты и порядок, от 2 до 6; удали буквенные префиксы; каждый не длиннее 60 символов; содержимое не должно повторяться.
4. Вопрос с одним ответом использует ровно один answerIndex. Вопрос с несколькими ответами использует answerIndices по возрастанию, без повторов, не менее двух. Не пиши одновременно два поля ответа и не своди вопрос с несколькими ответами к одному.
5. explanation сохраняй только если в исходнике есть решение, не более 600 символов; иначе опусти и не придумывай.
6. difficulty заполняй только если в исходнике указана сложность; допустимо только easy, medium, hard.
7. Вопросы «верно или неверно» оформляй как два варианта ["Верно", "Неверно"] и указывай answerIndex. Составные варианты считаются одним ответом и сохраняются как есть.
8. Вопрос с несколькими ответами требует не менее 3 вариантов, не менее 2 верных и хотя бы 1 неверного. Иначе пропусти вопрос целиком; не угадывай, не выбирай один верный вариант и не превращай вопрос в вопрос с одним ответом.
9. Вопросы с пропусками, аудированием, картинками, без ответа или с неопределённым ответом пропускай целиком.
10. Не создавай вопросов, которых нет в материале, и не меняй числа, единицы и варианты в условии.
11. Количество вопросов не ограничено; преобразуй все вопросы из материала и не сокращай их.
12. Перед выводом проверь: JSON разбирается; все строки в ASCII-двойных кавычках; нет висячих запятых; каждый индекс входит в options; type соответствует полям ответа; число items совпадает с числом преобразованных вопросов.
13. Если материал — фото или PDF, сначала полностью распознай текст; неразборчивые символы замени на «□» и не угадывай.
14. Если всё не помещается в один ответ, сначала выведи полный JSON, который можно разобрать; после моего ответа «продолжай» выведи оставшиеся вопросы и не повторяй уже выведенные.

Материал вопросов для преобразования:`,
      importGeneratePrompt: `Ты помощник по созданию вопросов. Составь оригинальные вопросы с одним и несколькими ответами по моим требованиям и оформи их в JSON, который можно импортировать на учебное устройство. Выводи только JSON.

Поля вывода:
version всегда 1.
items — массив вопросов.
prompt — условие вопроса.
options — массив вариантов.
type — только single или multiple.
Для одного ответа используй answerIndex, для нескольких — answerIndices; индексы начинаются с 0.
Жёсткие правила формата (проверь перед выводом):
- Один ответ: type "single", ровно один answerIndex (с 0); только один правильный вариант.
- Несколько ответов: type "multiple", только answerIndices (по возрастанию, без повторов); не менее 2 элементов и меньше числа вариантов; нельзя отмечать все варианты правильными (оставь хотя бы 1 неверный). При 4 вариантах [0,1,2,3] будет отклонён.
- Не пиши одновременно answerIndex и answerIndices; не выводи поля, которых нет в списке выше.
explanation — решение.
difficulty — только easy, medium или hard.

Образец:
{
  "version": 1,
  "items": [
    {
      "prompt": "Сколько будет 12 + 8?",
      "options": ["18", "20", "22", "24"],
      "type": "single",
      "answerIndex": 1,
      "explanation": "12 + 8 = 20.",
      "difficulty": "easy"
    },
    {
      "prompt": "Какие из этих животных — млекопитающие?",
      "options": ["кит", "пингвин", "летучая мышь", "дельфин"],
      "type": "multiple",
      "answerIndices": [0, 2, 3],
      "explanation": "Кит, летучая мышь и дельфин — млекопитающие, пингвин — птица.",
      "difficulty": "medium"
    }
  ]
}

Правила создания:
1. Выводи только JSON: без объяснений, без блока Markdown, без префиксов и суффиксов.
2. Соблюдай указанный тип вопросов: для одного ответа пиши "type":"single" и "answerIndex"; для нескольких — "type":"multiple" и "answerIndices"; для смешанных нужны оба типа; если тип не указан, используй вопросы с одним ответом.
3. prompt полный, ясный и однозначный, без номера вопроса; не более 200 символов.
4. options от 2 до 6, без повторов; для вопросов с несколькими ответами не менее 3; удали буквенные префиксы; каждый не длиннее 60 символов; неверные варианты должны выглядеть правдоподобно, но быть однозначно ошибочными; не используй варианты «всё вышеперечисленное» и «ничего из перечисленного».
5. Вопрос с одним ответом имеет ровно один верный вариант. Вопрос с несколькими ответами имеет не менее 2 верных и хотя бы 1 неверный; никогда не помечай все варианты верными. Не создавай вопросы с пропусками, аудированием, картинками или зависимостью от изображения.
6. explanation объясняет ответ в 1–3 предложениях, не более 600 символов; если не уверен, опусти и не выдумывай факты.
7. difficulty соответствует реальной сложности; только easy, medium, hard.
8. Вопросы должны соответствовать указанному классу и предмету и не выходить за программу; числа, единицы, факты и ответы должны быть верными.
9. Не повторяй вопросы в одном пакете и не добивай количество вариантами, где меняются только числа или синонимы.
10. Количество вопросов не ограничено; выведи столько, сколько указано в требованиях; не сокращай, не пропускай и не повторяй вопросы из-за количества.
11. Перед выводом проверь: JSON разбирается; все строки в ASCII-двойных кавычках; нет висячих запятых; type соответствует полям ответа; у вопроса с одним ответом ровно один ответ; у вопроса с несколькими ответами не менее двух ответов и не все варианты верные; каждый индекс входит в options; число items совпадает с числом вопросов.
12. Если всё не помещается в один ответ, сначала выведи полный JSON, который можно разобрать; после моего ответа «продолжай» выведи оставшиеся вопросы и не повторяй уже выведенные.

Требования к вопросам:
Класс/уровень: 3 класс
Предмет: математика
Учебник/тема: 1-й семестр, тема 1
Тип вопросов: смешанные
Количество вопросов: 20
Распределение сложности: easy 40%, medium 40%, hard 20%
Язык вывода: русский
Дополнительные требования: нет`,
      // Keep in sync with the final heading of importGeneratePrompt.
      importGeneratePromptHeading: "Требования к вопросам:",
    },
    uk: {
      bankImport: "Імпорт банку",
      bankImportAction: "Імпорт вручну",
      bankSourceLine: "Готові + ШІ {course} · Ручне імпортування {custom} · Помилки {wrong} · Усього {total} / {capacity}",
      bankSourceCustomTag: "Тег: {name}",
      goImportBank: "Імпортувати банк",
      importTitle: "Імпорт банку питань",
      importSingleOnlyBadge: "Один або кілька відповідей",
      importSingleOnlyHint: "Імпортуються питання з однією та кількома відповідями; питання з пропусками, аудіюванням, зображеннями та без відповіді пропускаються.",
      importCloudNotice: "Під час прив’язки до хмарного банку імпорт локальних питань недоступний. Спершу відв’яжіть хмару.",
      importTargetLabel: "Імпортувати в тег",
      importTagPlaceholder: "Введіть або виберіть тег",
      importTagCount: "{n} пит.",
      importTagSuggestEmpty: "Введіть нову назву, щоб створити тег",
      importTagSuggestNew: "+ Новий тег «{name}»",
      importTagSuggestAria: "Підказки наявних тегів",
      importTagClear: "Очистити назву тегу",
      importNoTagsHint: "Тегів ще немає; під час імпорту його буде створено.",
      importTargetHint: "Імпорт у наявний тег «{name}»",
      importTargetNewHint: "Тег «{name}» буде створено й збережено після успішного імпорту",
      importTargetNone: "Виберіть наявний тег або введіть нову назву.",
      importTargetBadgeNew: "Новий",
      importTargetBadgeExisting: "Наявний",
      importInputLabel: "Вміст питань",
      importChooseFile: "Вибрати файл .json або .txt",
      importPasteLabel: "Або вставте питання",
      importPasteClear: "Очистити",
      importTextPlaceholder: "Вставте канонічний JSON або нумерований текст (1. питання / A. варіант / Відповідь: B або A, C)",
      importCopyConvertPrompt: "Копіювати промпт для перетворення формату",
      importCopyGeneratePrompt: "Копіювати промпт для створення питань",
      importPromptConvertTitle: "Промпт для перетворення формату",
      importPromptGenerateTitle: "Промпт для створення питань",
      importPromptHint: "Є готові питання? Використовуйте перетворення формату. Потрібно створити з нуля? Використовуйте створення питань. Скопіюйте промпт у будь-який ШІ-помічник, потім збережіть отриманий JSON або вставте його сюди.",
      importAiHint: "Фото, PDF або неохайний текст можна спершу впорядкувати у звичайному веб-помічнику ШІ; перед імпортом видаліть імена, школу, номер учня та інші особисті дані. Імпортер лише перевіряє текст і нічого не завантажує.",
      importDefaultDifficulty: "Використовувати, якщо складність не вказана",
      importParse: "Перевірити й показати",
      importParsing: "Перевірка…",
      importEmptyInput: "Спершу виберіть файл або вставте питання.",
      importTooLarge: "Файл більший за 16 МБ; розділіть його на частини.",
      importTooMany: "За один раз можна імпортувати не більше {max} питань; розділіть файл.",
      importLargeRisk: "Великий імпорт може тривати кілька хвилин; не закривайте сторінку. Питання зберігаються лише на цьому телевізорі й не резервуються; очищення даних застосунку їх видалить.",
      importJsonInvalid: "Неправильний JSON: перевірте лапки, коми та дужки.",
      importNoQuestions: "Питань не знайдено.",
      importFileReadFailed: "Не вдалося прочитати файл. Виберіть інший або вставте текст.",
      importSelectTagFirst: "Спершу виберіть наявний тег або введіть нову назву.",
      importNewTagInvalid: "Назва тегу має містити 1–20 символів.",
      importNewTagExists: "Тег із такою назвою вже є.",
      importNewTagLimit: "Можна створити не більше 20 тегів.",
      importErrorAt: "Питання {n}",
      importPreviewTitle: "Попередній перегляд імпорту",
      importPreviewTarget: "Цільовий тег",
      importPreviewTotal: "Питань у файлі",
      importPreviewFileDup: "Повтори у файлі",
      importPreviewCollectionDup: "Уже є в тезі",
      importPreviewCrossDup: "Повтори з інших джерел (зберігаються)",
      importPreviewAppend: "Буде додано",
      importPreviewRemaining: "Залишок банку",
      importPreviewRemainingValue: "{remaining} / {capacity} пит.",
      importPreviewNoNew: "Нових питань немає: усі вже є у файлі або тезі.",
      importPreviewRejected: "{n} питань не пройшли перевірку; перевірте відповіді та варіанти.",
      importPreviewNotEnough: "Не вистачає місця: потрібно ще {n}. Звільніть місце в «Я → Обслуговування банку».",
      importPreviewCrossNote: "В інших джерелах уже є {n} таких питань; вони будуть додані як окремі копії цього тегу.",
      importConfirm: "Імпортувати {n} пит.",
      importImporting: "Імпорт…",
      importResultTitle: "Імпортовано {n} у «{name}»",
      importResultDetail: "Повторів усередині пакетів: {file} · уже в тезі: {collection}",
      importResultCross: "Повторів з інших джерел: {n} (збережено)",
      importResultRemaining: "Залишилось {remaining} / {capacity}",
      importPreviewFormat: "Формат файлу",
      importPreviewFileSize: "Розмір файлу",
      importPreviewBatches: "Пакети імпорту",
      importFormatJson: "JSON",
      importFormatText: "Нумерований текст",
      importPreviewLocalNote: "Попередній перегляд розбирає файл локально лише для перевірки формату й показує 10 питань на сторінку. Дедуплікація та перевірка остаточні лише в результаті імпорту. За раз не більше {max} питань і {size}.",
      importPagePrev: "Назад",
      importPageNext: "Далі",
      importPageGo: "Перейти",
      importPageJump: "Перейти до сторінки",
      importPreviewPage: "Сторінка {page} / {pages}",
      importProgressCount: "Оброблено {processed} / {total} пит.",
      importProgressBatch: "Пакет {batch} / {batches}",
      importProgressStats: "Додано {appended} · повторів усередині пакетів {file} · уже в тезі {collection} · з інших джерел {cross} · не пройшли {rejected}",
      importProgressHint: "Не закривайте сторінку під час імпорту. Завершені пакети зберігаються; скасування зупиняє лише наступні.",
      importCancel: "Скасувати імпорт",
      importCancelRequested: "Зупинка…",
      importResultPartialTitle: "Частковий імпорт: записано {n} у «{name}»",
      importResultProcessed: "Оброблено {processed} / {total} пит.",
      importResultCancelled: "Скасовано; уже записані пакети збережено. Виправте файл і повторіть — повтори не запишуться вдруге.",
      importResultFailed: "Пакет {batch} завершився помилкою, наступні пакети не імпортовано. Записані пакети збережено; повтор після виправлення не створить повторів.",
      importPromptCopied: "Промпт скопійовано.",
      importCopyFailed: "Не вдалося скопіювати. Розгорніть промпт і скопіюйте вручну.",
      importErrorCopy: "Скопіювати помилки для ШІ",
      importErrorCopied: "Скопійовано. Вставте в ШІ-помічник.",
      importErrorCopyFailed: "Не вдалося скопіювати. Виділіть повідомлення про помилки та скопіюйте вручну.",
      importErrorCopyIntro: "Під час імпорту питань у навчальний пристрій виникли помилки:",
      importErrorCopyAsk: "Виправте питання за вказаними вище номерами та поверніть повний виправлений результат. Вимоги:\n1. Виводьте лише повний JSON (version і items): без пояснень, без блоку Markdown, без префіксів і суфіксів.\n2. Не змінюйте питання, крім перелічених вище; не видаляйте й не об’єднуйте питання.\n3. Використовуйте прямі подвійні лапки, без висячих ком, усі індекси відповідей мають бути в межах options.\n4. У питанні з кількома відповідями має бути щонайменше 3 варіанти й щонайменше 2 правильні відповіді; не можна позначати всі варіанти як правильні — залиште щонайменше 1 неправильний варіант. Якщо в початковому питанні всі варіанти правильні, змініть один варіант на явно неправильне твердження; не перетворюйте питання на питання з однією відповіддю й не видаляйте його.",
      importErrorCopySource: "Початкові дані:",
      importErrorCopySourceOmitted: "(Початкові дані завеликі для автоматичного додавання; вставте повний текст для ШІ.)",
      importField_item: "Питання",
      importField_items: "Список питань",
      importField_text: "Текст",
      importField_prompt: "Умова",
      importField_options: "Варіанти",
      importField_answer: "Відповідь",
      importField_explanation: "Розв’язання",
      importField_difficulty: "Складність",
      importCode_TYPE: "Неправильний тип поля",
      importCode_UNKNOWN_FIELD: "Непідтримуване поле",
      importCode_DUPLICATE: "Повторюваний вміст",
      importCode_REQUIRED: "Бракує обов’язкових даних",
      importCode_ORDER: "Неправильний порядок даних",
      importCode_FORMAT: "Неправильний формат",
      importCode_COUNT: "Повинно бути 2–6 варіантів",
      importCode_INVALID_TEXT: "Порожній текст або перевищена довжина",
      importCode_TOO_LONG: "Текст задовгий",
      importCode_ANSWER_FORMAT: "Неверний формат відповіді: для однієї відповіді — одна буква A–F або точний текст варіанта; для кількох — кілька букв або масив answerIndices",
      importCode_OPTIONS_TOO_FEW: "У питанні з кількома відповідями має бути щонайменше 3 варіанти",
      importCode_ANSWER_TOO_FEW: "У питанні з кількома відповідями має бути щонайменше 2 правильні відповіді",
      importCode_ANSWER_ALL: "Не можна позначати всі варіанти як правильні: залиште щонайменше 1 неправильний варіант",
      importCode_ANSWER_RANGE: "Відповідь поза діапазоном варіантів",
      importCode_ANSWER_CONFLICT: "Укажіть лише одне з полів answer, answerIndex або answerIndices",
      importCode_INVALID_DIFFICULTY: "Складність: лише easy, medium або hard",
      importCode_NO_ITEMS: "Немає питань",
      importCode_TOO_MANY_ITEMS: "Не більше 200 питань за раз",
      levelAddCourseTitle: "Навчальні рівні",
      levelAddCourseMeta: "Клас · предмет · розділ",
      levelAddCustomTitle: "Власні рівні",
      levelAddCustomMeta: "З імпортованих тегів",
      levelAddCustomMetaNoTags: "Спершу імпортуйте питання",
      levelAddCustomMetaCloud: "Недоступно в хмарному режимі",
      levelAddWrongTitle: "Повторення помилок",
      levelAddWrongMeta: "Усі предмети · помилки зникають",
      levelAddWrongMetaEmpty: "Помилок ще немає; неправильні відповіді додадуться самі",
      levelAddWrongMetaCloud: "Недоступно в хмарному режимі",
      wrongLevelListTitle: "Повторення помилок",
      wrongLevelListLead: "Рівень повторення один і автоматично охоплює помилки з усіх предметів.",
      wrongLevelListCount: "{n}/1",
      wrongLevelBadge: "Помилки",
      wrongLevelMeta: "Усі предмети · правильна відповідь прибирає помилку",
      wrongLevelFallbackName: "Повторення помилок",
      wrongLevelAvailable: "Помилок: {n} · до {pull} за раунд",
      wrongLevelEmptyPool: "Помилок зараз немає; неправильні відповіді додадуться самі",
      wrongLevelAddTitle: "Новий рівень повторення",
      wrongLevelEditTitle: "Змінити повторення помилок",
      wrongLevelEditorLead: "Повторення не поділяється за предметами й бере всі локальні помилки. Правильна відповідь прибирає помилку.",
      wrongLevelNameHint: "Якщо порожньо, показується «Повторення помилок»; до 10 символів.",
      wrongLevelEditorAvailable: "Помилок: {n} · до {pull} за раунд.",
      wrongLevelEditorEmpty: "Помилок зараз немає. Неправильні відповіді додадуться самі, і їх можна буде повторити.",
      wrongLevelDeleteConfirm: "Видалити рівень повторення «{name}»? Самі помилки збережуться.",
      wrongLevelDeleteAria: "Видалити повторення помилок «{name}»",
      wrongLevelSaved: "Рівень повторення збережено.",
      wrongLevelDeleted: "Рівень повторення видалено.",
      err_INVALID_WRONG_LEVEL: "Некоректний рівень повторення. Оновіть сторінку та повторіть.",
      customLevelListTitle: "Власні рівні",
      customLevelNameHint: "Якщо порожньо, показується назва тегу; до 10 символів.",
      customLevelTagLabel: "Тег джерела питань",
      customLevelTagHint: "Для кожного тегу можна створити один рівень.",
      customLevelEmptyTitle: "Ще немає доступних тегів",
      customLevelEmptyText: "Спершу імпортуйте питання й створіть тег, потім можна створити власний рівень.",
      customLevelCloudHint: "Під час хмарного банку локальні власні рівні змінювати не можна.",
      customLevelBadge: "Власний",
      customLevelTagLine: "Джерело: {name}",
      customLevelFallbackName: "Власний рівень",
      customLevelNameFallback: "Власний · {name}",
      customLevelTagCounts: "{total} пит. · доступно {unused}",
      customLevelTagCountsEmpty: "{total} пит.",
      customLevelEditorAvailable: "«{name}»: доступно {n} питань",
      customLevelEditorNoAvailable: "У «{name}» зараз немає невикористаних питань.",
      customLevelAddTitle: "Новий власний рівень",
      customLevelDeleteConfirm: "Видалити власний рівень «{name}»? Питання й тег залишаться.",
      customLevelTagDuplicate: "Для цього тегу вже є рівень.",
      customLevelTagRequired: "Виберіть тег.",
      customLevelSaved: "Власний рівень збережено.",
      customLevelDeleted: "Власний рівень видалено.",
      levelsTotalHint: "Рівні {used} / {max}",
      customLevelCombined: "Курсові, власні та рівні повторення помилок: {used} / {max}.",
      maintenanceSourceLabel: "Область очищення",
      maintenanceSourceCourse: "Курс/ШІ",
      maintenanceSourceCustom: "Власні",
      maintenanceSourceAll: "Усі",
      maintenanceSourceCourseHint: "Очищається лише банк курсу/ШІ; власні теги не зачіпаються.",
      maintenanceSourceCustomHint: "Очищається лише вибраний тег; інші теги й курс/ШІ не зачіпаються.",
      maintenanceSourceAllHint: "Буде очищено і курс/ШІ, і власні питання (власних: {custom}). Дійте обережно.",
      maintenanceSourceNoTags: "Власних тегів ще немає.",
      maintenanceTagOption: "{name} ({total} пит., доступно {unused})",
      confirmClearAllMixed: "Увага: буде очищено і курс/ШІ, і власні питання (власних: {n}).",
      confirmClearCustom: "Буде очищено лише тег «{name}».",
      err_COLLECTION_CONFLICT: "Тег уже існує або використовується. Виберіть іншу назву.",
      err_COLLECTION_EXISTS: "Тег із такою назвою вже є.",
      err_COLLECTION_LIMIT: "Можна створити не більше 20 тегів.",
      err_COLLECTION_NOT_FOUND: "Тег не знайдено. Оновіть сторінку.",
      err_COLLECTION_REQUIRED: "Спершу виберіть або створіть тег.",
      err_INVALID_COLLECTION_NAME: "Назва тегу має містити 1–20 символів.",
      err_INVALID_CUSTOM_LEVEL: "Неправильні налаштування власного рівня.",
      err_INVALID_IMPORT: "В імпортованих даних є помилки. Виправте їх за підказками.",
      err_TOO_MANY_ITEMS: "Не більше 200 питань за один раз.",
      err_TOO_MANY_LEVELS: "Усього можна створити не більше 20 курсових, власних рівнів і рівнів повторення помилок.",
      err_INVALID_INDEX: "Неправильна позиція питання. Перевірте імпорт ще раз.",
      importConvertPrompt: `Ти помічник з перетворення банку питань. Я дам тобі готові питання; перетвори їх як є на JSON, який можна імпортувати на навчальний пристрій. Виводь лише JSON. Не додавай, не переписуй і не об’єднуй питання.

Поля виводу:
version завжди 1.
items — масив питань.
prompt — умова питання.
options — масив варіантів.
type — лише single або multiple.
Для однієї відповіді використовуй answerIndex, для кількох — answerIndices; індекси починаються з 0.
Жорсткі правила формату (перевір перед виводом):
- Одна відповідь: type "single", рівно один answerIndex (з 0); лише один правильний варіант.
- Кілька відповідей: type "multiple", лише answerIndices (за зростанням, без повторів); щонайменше 2 елементи й менше, ніж варіантів; не можна позначати всі варіанти правильними (залиш хоча б 1 неправильний). За 4 варіантів [0,1,2,3] буде відхилено.
- Не пиши одночасно answerIndex і answerIndices; не виводь поля, яких немає у списку вище.
explanation — розв’язання, необов’язково.
difficulty — лише easy, medium або hard, необов’язково.

Зразок:
{
  "version": 1,
  "items": [
    {
      "prompt": "Скільки буде 12 + 8?",
      "options": ["18", "20", "22", "24"],
      "type": "single",
      "answerIndex": 1,
      "explanation": "12 + 8 = 20, тому правильна відповідь 20.",
      "difficulty": "easy"
    },
    {
      "prompt": "Які з цих тварин — ссавці?",
      "options": ["кит", "пінгвін", "кажан", "дельфін"],
      "type": "multiple",
      "answerIndices": [0, 2, 3],
      "explanation": "Кит, кажан і дельфін — ссавці, пінгвін — птах.",
      "difficulty": "medium"
    }
  ]
}

Правила перетворення:
1. Виводь лише JSON: без пояснень, без блоку Markdown, без префіксів і суфіксів.
2. prompt зберігає зміст початкового питання, без номера й варіантів; не переписуй, не доповнюй і не об’єднуй питання; не більше 200 символів.
3. options зберігає початкові варіанти й порядок, від 2 до 6; видали літерні префікси; кожен не довший за 60 символів; вміст не має повторюватися.
4. Питання з однією відповіддю використовує рівно один answerIndex. Питання з кількома відповідями використовує answerIndices за зростанням, без повторів, щонайменше два. Не пиши одночасно два поля відповіді й не зводь питання з кількома відповідями до однієї.
5. explanation зберігай лише якщо в джерелі є розв’язання, не більше 600 символів; інакше опусти й не вигадуй.
6. difficulty заповнюй лише якщо в джерелі позначено складність; допустимо лише easy, medium, hard.
7. Питання «правильно чи неправильно» оформлюй як два варіанти ["Правильно", "Неправильно"] і вказуй answerIndex. Складені варіанти вважаються однією відповіддю і зберігаються як є.
8. Питання з кількома відповідями потребує щонайменше 3 варіантів, щонайменше 2 правильних і хоча б 1 неправильного. Інакше пропусти питання повністю; не вгадуй, не вибирай один правильний варіант і не переробляй його на питання з однією відповіддю.
9. Питання з пропусками, аудіюванням, зображеннями, без відповіді або з невизначеною відповіддю пропускай повністю.
10. Не створюй питань, яких немає в матеріалі, і не змінюй числа, одиниці та варіанти в умові.
11. Кількість питань не обмежена; перетвори всі питання з матеріалу й не скорочуй їх.
12. Перед виводом перевір: JSON розбирається; усі рядки в ASCII-подвійних лапках; немає висячих ком; кожен індекс входить до options; type відповідає полям відповіді; кількість items збігається з кількістю перетворених питань.
13. Якщо матеріал — фото або PDF, спершу повністю розпізнай текст; нерозбірливі символи заміни на «□» і не вгадуй.
14. Якщо все не вміщується в одну відповідь, спершу виведи повний JSON, який можна розібрати; після моєї відповіді «продовжуй» виведи решту питань і не повторюй уже виведені.

Матеріал питань для перетворення:`,
      importGeneratePrompt: `Ти помічник зі створення питань. Створи оригінальні питання з однією та кількома відповідями за моїми вимогами й оформи їх у JSON, який можна імпортувати на навчальний пристрій. Виводь лише JSON.

Поля виводу:
version завжди 1.
items — масив питань.
prompt — умова питання.
options — масив варіантів.
type — лише single або multiple.
Для однієї відповіді використовуй answerIndex, для кількох — answerIndices; індекси починаються з 0.
Жорсткі правила формату (перевір перед виводом):
- Одна відповідь: type "single", рівно один answerIndex (з 0); лише один правильний варіант.
- Кілька відповідей: type "multiple", лише answerIndices (за зростанням, без повторів); щонайменше 2 елементи й менше, ніж варіантів; не можна позначати всі варіанти правильними (залиш хоча б 1 неправильний). За 4 варіантів [0,1,2,3] буде відхилено.
- Не пиши одночасно answerIndex і answerIndices; не виводь поля, яких немає у списку вище.
explanation — розв’язання.
difficulty — лише easy, medium або hard.

Зразок:
{
  "version": 1,
  "items": [
    {
      "prompt": "Скільки буде 12 + 8?",
      "options": ["18", "20", "22", "24"],
      "type": "single",
      "answerIndex": 1,
      "explanation": "12 + 8 = 20.",
      "difficulty": "easy"
    },
    {
      "prompt": "Які з цих тварин — ссавці?",
      "options": ["кит", "пінгвін", "кажан", "дельфін"],
      "type": "multiple",
      "answerIndices": [0, 2, 3],
      "explanation": "Кит, кажан і дельфін — ссавці, пінгвін — птах.",
      "difficulty": "medium"
    }
  ]
}

Правила створення:
1. Виводь лише JSON: без пояснень, без блоку Markdown, без префіксів і суфіксів.
2. Дотримуйся вказаного типу питань: для однієї відповіді пиши "type":"single" і "answerIndex"; для кількох — "type":"multiple" і "answerIndices"; для змішаних потрібні обидва типи; якщо тип не вказано, використовуй питання з однією відповіддю.
3. prompt повна однозначна умова без номера питання; не більше 200 символів.
4. options від 2 до 6, без повторів; для питань із кількома відповідями щонайменше 3; видали літерні префікси; кожен не довший за 60 символів; неправильні варіанти мають виглядати правдоподібно, але бути однозначно хибними; не використовуй варіанти «усе перелічене вище» і «нічого з переліченого».
5. Питання з однією відповіддю має рівно один правильний варіант. Питання з кількома відповідями має щонайменше 2 правильних і хоча б 1 неправильний; ніколи не позначай усі варіанти правильними. Не створюй питань із пропусками, аудіюванням, зображеннями або залежністю від малюнка.
6. explanation пояснює відповідь у 1–3 реченнях, не більше 600 символів; якщо не впевнений, опусти й не вигадуй факти.
7. difficulty відповідає реальній складності; лише easy, medium, hard.
8. Питання мають відповідати вказаному класу й предмету та не виходити за програму; числа, одиниці, факти й відповіді мають бути правильними.
9. Не повторюй питання в одному пакеті й не добирай кількість варіантами, де змінюються лише числа чи синоніми.
10. Кількість питань не обмежена; виведи стільки, скільки вказано у вимогах; не скорочуй, не пропускай і не повторюй питання через кількість.
11. Перед виводом перевір: JSON розбирається; усі рядки в ASCII-подвійних лапках; немає висячих ком; type відповідає полям відповіді; у питання з однією відповіддю рівно одна відповідь; у питання з кількома відповідями щонайменше дві відповіді й не всі варіанти правильні; кожен індекс входить до options; кількість items збігається з кількістю питань.
12. Якщо все не вміщується в одну відповідь, спершу виведи повний JSON, який можна розібрати; після моєї відповіді «продовжуй» виведи решту питань і не повторюй уже виведені.

Вимоги до питань:
Клас/рівень: 3 клас
Предмет: математика
Підручник/тема: 1-й семестр, тема 1
Тип питань: змішані
Кількість питань: 20
Розподіл складності: easy 40%, medium 40%, hard 20%
Мова виводу: українська
Додаткові вимоги: немає`,
      // Keep in sync with the final heading of importGeneratePrompt.
      importGeneratePromptHeading: "Вимоги до питань:",
    },
    kk: {
      bankImport: "Банкіні импорттау",
      bankImportAction: "Қолмен импорттау",
      bankSourceLine: "Дайын + ЖИ {course} · Қолмен импорт {custom} · Қателер {wrong} · Барлығы {total} / {capacity}",
      bankSourceCustomTag: "Тег: {name}",
      goImportBank: "Банкіні импорттау",
      importTitle: "Сұрақ банкін импорттау",
      importSingleOnlyBadge: "Бір немесе бірнеше жауап",
      importSingleOnlyHint: "Бір жауапты және бірнеше жауапты сұрақтар импортталады; бос орынды, тыңдауға арналған, суретке тәуелді және жауабы жоқ сұрақтар өткізіп жіберіледі.",
      importCloudNotice: "Бұлтты банкке байланғанда жергілікті сұрақтарды импорттауға болмайды. Алдымен бұлтты ажыратыңыз.",
      importTargetLabel: "Тегке импорттау",
      importTagPlaceholder: "Тегті енгізіңіз немесе таңдаңыз",
      importTagCount: "{n} сұрақ",
      importTagSuggestEmpty: "Жаңа атауды енгізіп, тег жасауға болады",
      importTagSuggestNew: "+ Жаңа тег «{name}»",
      importTagSuggestAria: "Бар тегтер бойынша ұсыныстар",
      importTagClear: "Тег атауын тазалау",
      importNoTagsHint: "Әзірге тег жоқ; импорт кезінде ол жасалады.",
      importTargetHint: "«{name}» бар тегіне импортталады",
      importTargetNewHint: "«{name}» тегі импорт сәтті аяқталған соң жасалып, сақталады",
      importTargetNone: "Бар тегті таңдаңыз немесе жаңа атау енгізіңіз.",
      importTargetBadgeNew: "Жаңа",
      importTargetBadgeExisting: "Бар",
      importInputLabel: "Сұрақ мазмұны",
      importChooseFile: ".json немесе .txt файлын таңдау",
      importPasteLabel: "Немесе сұрақтарды тікелей қойыңыз",
      importPasteClear: "Тазалау",
      importTextPlaceholder: "Канондық JSON немесе нөмірленген мәтін қойыңыз (1. сұрақ / A. нұсқа / Жауап: B немесе A, C)",
      importCopyConvertPrompt: "Форматты түрлендіру промптын көшіру",
      importCopyGeneratePrompt: "Сұрақ құрастыру промптын көшіру",
      importPromptConvertTitle: "Форматты түрлендіру промпты",
      importPromptGenerateTitle: "Сұрақ құрастыру промпты",
      importPromptHint: "Дайын сұрақтарыңыз бар ма? Форматты түрлендіруді қолданыңыз. Нөлден құрастыру керек пе? Сұрақ құрастыруды қолданыңыз. Промптты кез келген AI көмекшісіне көшіріп, алынған JSON-ды сақтаңыз немесе осында қойыңыз.",
      importAiHint: "Фото, PDF немесе ретсіз мәтінді алдымен әдеттегі AI веб-көмекшісіне реттетуге болады; импорттау алдында аты-жөні, мектеп, оқушы нөмірі сияқты жеке деректерді жойыңыз. Импорттаушы тек мәтінді тексереді, ештеңе жүктемейді.",
      importDefaultDifficulty: "Сұрақта қиындық көрсетілмесе қолданылады",
      importParse: "Тексеріп, алдын ала көру",
      importParsing: "Тексерілуде…",
      importEmptyInput: "Алдымен файл таңдаңыз немесе сұрақтарды қойыңыз.",
      importTooLarge: "Файл 16 МБ-тан үлкен; оны бөліктерге бөліңіз.",
      importTooMany: "Бір ретте ең көбі {max} сұрақ импорттауға болады; файлды бөліңіз.",
      importLargeRisk: "Үлкен импорт бірнеше минутқа созылуы мүмкін; бетті жаппаңыз. Сұрақтар тек осы теледидарда сақталады және резервтік көшірмеге кірмейді; қолданба деректерін тазалау оларды жояды.",
      importJsonInvalid: "JSON дұрыс емес: тырнақшаларды, үтірлерді және жақшаларды тексеріңіз.",
      importNoQuestions: "Сұрақтар табылмады.",
      importFileReadFailed: "Файлды оқу мүмкін болмады. Басқа файл таңдаңыз немесе мәтінді қойыңыз.",
      importSelectTagFirst: "Алдымен бар тегті таңдаңыз немесе жаңа атау енгізіңіз.",
      importNewTagInvalid: "Тег атауы 1–20 таңбадан тұруы керек.",
      importNewTagExists: "Осындай атаулы тег бар.",
      importNewTagLimit: "Ең көбі 20 тег жасауға болады.",
      importErrorAt: "{n}-сұрақ",
      importPreviewTitle: "Импортты алдын ала көру",
      importPreviewTarget: "Мақсатты тег",
      importPreviewTotal: "Файлдағы сұрақ саны",
      importPreviewFileDup: "Файл ішіндегі қайталаулар",
      importPreviewCollectionDup: "Тегте бұрыннан бар",
      importPreviewCrossDup: "Басқа көздердегі қайталаулар (сақталады)",
      importPreviewAppend: "Қосылады",
      importPreviewRemaining: "Банк қалдығы",
      importPreviewRemainingValue: "{remaining} / {capacity} сұрақ",
      importPreviewNoNew: "Жаңа сұрақ жоқ: барлығы файлда немесе тегте бар.",
      importPreviewRejected: "{n} сұрақ тексеруден өтпеді; жауаптар мен нұсқаларды тексеріңіз.",
      importPreviewNotEnough: "Орын жетпейді: тағы {n} керек. «Мен → Банк күтімі» бөлімінде орын босатыңыз.",
      importPreviewCrossNote: "Басқа көздерде {n} бірдей сұрақ бар; олар осы тегтің жеке көшірмелері ретінде қосылады.",
      importConfirm: "{n} сұрақты импорттау",
      importImporting: "Импортталуда…",
      importResultTitle: "«{name}» тегіне {n} сұрақ импортталды",
      importResultDetail: "Топтама ішіндегі қайталау: {file} · тегте бұрыннан бар: {collection}",
      importResultCross: "Басқа көздердегі қайталау: {n} (сақталды)",
      importResultRemaining: "Банкте қалды: {remaining} / {capacity}",
      importPreviewFormat: "Файл пішімі",
      importPreviewFileSize: "Файл өлшемі",
      importPreviewBatches: "Импорт топтамалары",
      importFormatJson: "JSON",
      importFormatText: "Нөмірленген мәтін",
      importPreviewLocalNote: "Бұл алдын ала көру файлды тек пішімін тексеру үшін жергілікті талдайды және әр бетте 10 сұрақ көрсетеді. Қайталауды жою мен тексеру тек импорт нәтижесінде нақты болады. Бір ретте ең көбі {max} сұрақ және {size}.",
      importPagePrev: "Артқа",
      importPageNext: "Алға",
      importPageGo: "Өту",
      importPageJump: "Бетке өту",
      importPreviewPage: "{page} / {pages} бет",
      importProgressCount: "Өңделді {processed} / {total} сұрақ",
      importProgressBatch: "Топтама {batch} / {batches}",
      importProgressStats: "Қосылды {appended} · топтама ішіндегі қайталау {file} · тегте бар {collection} · басқа көздерден {cross} · тексеруден өтпеді {rejected}",
      importProgressHint: "Импорт кезінде бетті жаппаңыз. Аяқталған топтамалар сақталады; тоқтату тек келесі топтамаларды тоқтатады.",
      importCancel: "Импортты тоқтату",
      importCancelRequested: "Тоқтатылуда…",
      importResultPartialTitle: "Ішінара импорт: «{name}» тегіне {n} сұрақ жазылды",
      importResultProcessed: "Өңделді {processed} / {total} сұрақ",
      importResultCancelled: "Тоқтатылды; жазылған топтамалар сақталады. Файлды түзетіп қайталаңыз — қайталаулар қайта жазылмайды.",
      importResultFailed: "{batch}-топтама сәтсіз аяқталды, келесі топтамалар импортталмады. Жазылған топтамалар сақталады; түзеткеннен кейін қайталау қайталау жасамайды.",
      importPromptCopied: "Промпт көшірілді.",
      importCopyFailed: "Көшіру сәтсіз. Промптты ашып, қолмен көшіріңіз.",
      importErrorCopy: "Қателерді AI-ға жөндеуге көшіру",
      importErrorCopied: "Көшірілді. AI көмекшісіне қойыңыз.",
      importErrorCopyFailed: "Көшіру сәтсіз. Қате хабарламаларын таңдап, қолмен көшіріңіз.",
      importErrorCopyIntro: "Оқу құрылғысына сұрақтарды импорттау кезінде мына қателер шықты:",
      importErrorCopyAsk: "Жоғарыдағы нөмірлер бойынша сұрақтарды жөндеп, толық жөнделген нәтижені қайтарыңыз. Талаптар:\n1. Тек толық JSON (version және items) шығарыңыз: түсіндірме жоқ, Markdown блогы жоқ, префикс пен суффикс жоқ.\n2. Жоғарыда көрсетілген қате сұрақтардан басқа сұрақтарды өзгертпеңіз; сұрақтарды жоймаңыз немесе біріктірмеңіз.\n3. Тік қос тырнақшаларды қолданыңыз, артық үтір қалдырмаңыз, барлық жауап индекстері options шегінде болуы керек.\n4. Бірнеше жауапты сұрақта кемінде 3 нұсқа және кемінде 2 дұрыс жауап болуы керек; барлық нұсқаны дұрыс деп белгілеуге болмайды — кемінде 1 қате нұсқа қалдырыңыз. Егер бастапқы сұрақта барлық нұсқа дұрыс болса, бір нұсқаны анық қате тұжырымға өзгертіңіз; сұрақты бір жауапты сұраққа айналдырмаңыз және жоймаңыз.",
      importErrorCopySource: "Бастапқы деректер:",
      importErrorCopySourceOmitted: "(Бастапқы деректер автоматты қосу үшін тым үлкен; AI үшін толық мәтінді қойыңыз.)",
      importField_item: "Сұрақ",
      importField_items: "Сұрақтар тізімі",
      importField_text: "Мәтін",
      importField_prompt: "Шарт",
      importField_options: "Нұсқалар",
      importField_answer: "Жауап",
      importField_explanation: "Шешім",
      importField_difficulty: "Қиындық",
      importCode_TYPE: "Өрістің түрі дұрыс емес",
      importCode_UNKNOWN_FIELD: "Қолдау көрсетілмейтін өріс",
      importCode_DUPLICATE: "Мазмұн қайталанады",
      importCode_REQUIRED: "Міндетті мазмұн жоқ",
      importCode_ORDER: "Мазмұн реті дұрыс емес",
      importCode_FORMAT: "Формат дұрыс емес",
      importCode_COUNT: "2–6 нұсқа болуы керек",
      importCode_INVALID_TEXT: "Мәтін бос немесе ұзындығы асып кеткен",
      importCode_TOO_LONG: "Мәтін тым ұзын",
      importCode_ANSWER_FORMAT: "Жауап форматы дұрыс емес: бір жауап үшін — A–F аралығындағы бір әріп немесе нұсқаның нақты мәтіні; бірнеше жауап үшін — бірнеше әріп немесе answerIndices массиві",
      importCode_OPTIONS_TOO_FEW: "Бірнеше жауапты сұрақта кемінде 3 нұсқа болуы керек",
      importCode_ANSWER_TOO_FEW: "Бірнеше жауапты сұрақта кемінде 2 дұрыс жауап болуы керек",
      importCode_ANSWER_ALL: "Барлық нұсқаны дұрыс деп белгілеуге болмайды: кемінде 1 қате нұсқа қалдырыңыз",
      importCode_ANSWER_RANGE: "Жауап нұсқалар ауқымынан тыс",
      importCode_ANSWER_CONFLICT: "answer, answerIndex және answerIndices өрістерінің тек біреуін ғана толтырыңыз",
      importCode_INVALID_DIFFICULTY: "Қиындық тек easy, medium немесе hard болуы керек",
      importCode_NO_ITEMS: "Сұрақ жоқ",
      importCode_TOO_MANY_ITEMS: "Бір ретте ең көбі 200 сұрақ",
      levelAddCourseTitle: "Оқу деңгейлері",
      levelAddCourseMeta: "Сынып · пән · бөлім",
      levelAddCustomTitle: "Жеке деңгейлер",
      levelAddCustomMeta: "Импортталған тегтерден",
      levelAddCustomMetaNoTags: "Алдымен сұрақтарды импорттаңыз",
      levelAddCustomMetaCloud: "Бұлт режимінде қолжетімсіз",
      levelAddWrongTitle: "Қателерді қайталау",
      levelAddWrongMeta: "Барлық пән · қате жойылады",
      levelAddWrongMetaEmpty: "Әзірге қате жоқ; қате жауаптар өзі қосылады",
      levelAddWrongMetaCloud: "Бұлт режимінде қолжетімсіз",
      wrongLevelListTitle: "Қателерді қайталау",
      wrongLevelListLead: "Қайталау деңгейі біреу; ол барлық пәндегі қателерді автоматты қамтиды.",
      wrongLevelListCount: "{n}/1",
      wrongLevelBadge: "Қателер",
      wrongLevelMeta: "Барлық пән · дұрыс жауап қатені жояды",
      wrongLevelFallbackName: "Қателерді қайталау",
      wrongLevelAvailable: "Қате: {n} · раундқа {pull}-ге дейін",
      wrongLevelEmptyPool: "Қазір қате жоқ; қате жауаптар өзі қосылады",
      wrongLevelAddTitle: "Жаңа қайталау деңгейі",
      wrongLevelEditTitle: "Қателерді қайталауды өзгерту",
      wrongLevelEditorLead: "Қайталау пәнге бөлінбейді және барлық жергілікті қателерді алады. Дұрыс жауап қатені жояды.",
      wrongLevelNameHint: "Бос болса «Қателерді қайталау» көрсетіледі; ең көбі 10 таңба.",
      wrongLevelEditorAvailable: "Қате: {n} · раундқа {pull}-ге дейін.",
      wrongLevelEditorEmpty: "Қазір қате жоқ. Қате жауаптар өзі қосылып, кейін қайталауға болады.",
      wrongLevelDeleteConfirm: "«{name}» қайталау деңгейін жою керек пе? Қателердің өзі сақталады.",
      wrongLevelDeleteAria: "«{name}» қателерді қайталауды жою",
      wrongLevelSaved: "Қайталау деңгейі сақталды.",
      wrongLevelDeleted: "Қайталау деңгейі жойылды.",
      err_INVALID_WRONG_LEVEL: "Қайталау деңгейі жарамсыз. Бетті жаңартып, қайталаңыз.",
      customLevelListTitle: "Жеке деңгейлер",
      customLevelNameHint: "Бос болса тег атауы көрсетіледі; ең көбі 10 таңба.",
      customLevelTagLabel: "Сұрақ көзінің тегі",
      customLevelTagHint: "Әр тегке тек бір деңгей жасауға болады.",
      customLevelEmptyTitle: "Қолжетімді тег жоқ",
      customLevelEmptyText: "Алдымен сұрақтарды импорттап, тег жасаңыз, содан кейін жеке деңгей жасай аласыз.",
      customLevelCloudHint: "Бұлтты банк кезінде жергілікті жеке деңгейлерді өзгертуге болмайды.",
      customLevelBadge: "Жеке",
      customLevelTagLine: "Көзі: {name}",
      customLevelFallbackName: "Жеке деңгей",
      customLevelNameFallback: "Жеке · {name}",
      customLevelTagCounts: "{total} сұрақ · қолжетімді {unused}",
      customLevelTagCountsEmpty: "{total} сұрақ",
      customLevelEditorAvailable: "«{name}»: {n} сұрақ қолжетімді",
      customLevelEditorNoAvailable: "«{name}» тегінде әзірге пайдаланылмаған сұрақ жоқ.",
      customLevelAddTitle: "Жаңа жеке деңгей",
      customLevelDeleteConfirm: "«{name}» жеке деңгейін жою керек пе? Сұрақтар мен тег сақталады.",
      customLevelTagDuplicate: "Бұл тегке деңгей бұрыннан бар.",
      customLevelTagRequired: "Тег таңдаңыз.",
      customLevelSaved: "Жеке деңгей сақталды.",
      customLevelDeleted: "Жеке деңгей жойылды.",
      levelsTotalHint: "Деңгейлер {used} / {max}",
      customLevelCombined: "Курстық, жеке және қателерді қайталау деңгейлері: {used} / {max}.",
      maintenanceSourceLabel: "Тазалау аясы",
      maintenanceSourceCourse: "Курс/AI",
      maintenanceSourceCustom: "Жеке",
      maintenanceSourceAll: "Барлығы",
      maintenanceSourceCourseHint: "Тек курс/AI банкі тазаланады; жеке тегтерге тимейді.",
      maintenanceSourceCustomHint: "Тек таңдалған тег тазаланады; басқа тегтер мен курс/AI банкіне тимейді.",
      maintenanceSourceAllHint: "Курс/AI мен жеке сұрақтар бірге тазаланады (жеке: {custom}). Абайлап орындаңыз.",
      maintenanceSourceNoTags: "Жеке тегтер әзірге жоқ.",
      maintenanceTagOption: "{name} ({total} сұрақ, қолжетімді {unused})",
      confirmClearAllMixed: "Назар аударыңыз: курс/AI мен жеке сұрақтар бірге тазаланады (жеке: {n}).",
      confirmClearCustom: "Тек «{name}» тегі тазаланады.",
      err_COLLECTION_CONFLICT: "Тег бұрыннан бар немесе қолданылуда. Басқа атау таңдаңыз.",
      err_COLLECTION_EXISTS: "Осындай атаулы тег бар.",
      err_COLLECTION_LIMIT: "Ең көбі 20 тег жасауға болады.",
      err_COLLECTION_NOT_FOUND: "Тег табылмады. Бетті жаңартып, қайталаңыз.",
      err_COLLECTION_REQUIRED: "Алдымен тег таңдаңыз немесе жасаңыз.",
      err_INVALID_COLLECTION_NAME: "Тег атауы 1–20 таңбадан тұруы керек.",
      err_INVALID_CUSTOM_LEVEL: "Жеке деңгей баптауы дұрыс емес.",
      err_INVALID_IMPORT: "Импортталған деректерде қателер бар. Нұсқаулар бойынша түзетіңіз.",
      err_TOO_MANY_ITEMS: "Бір ретте ең көбі 200 сұрақ импорттауға болады.",
      err_TOO_MANY_LEVELS: "Курстық, жеке және қателерді қайталау деңгейлерінің барлығы 20-дан аспауы керек.",
      err_INVALID_INDEX: "Сұрақ орны дұрыс емес. Импортты қайта тексеріңіз.",
      importConvertPrompt: `Сен сұрақ банкін түрлендіруші көмекшісің. Мен саған дайын сұрақтар беремін; оларды сол күйінде оқу құрылғысына импорттауға болатын JSON-ға түрлендір. Тек JSON шығар. Сұрақтарды қоспа, қайта жазба және біріктірме.

Шығыс өрістері:
version әрқашан 1.
items — сұрақтар массиві.
prompt — сұрақ шарты.
options — нұсқалар массиві.
type — тек single немесе multiple.
Бір жауап үшін answerIndex, бірнеше жауап үшін answerIndices қолдан; индекстер 0-ден басталады.
Қатаң формат ережелері (шығарар алдында тексер):
- Бір жауап: type "single", дәл бір answerIndex (0-ден); тек бір дұрыс нұсқа.
- Бірнеше жауап: type "multiple", тек answerIndices (өсу ретімен, қайталанбай); кемінде 2 элемент және нұсқалар санынан аз болуы керек; барлық нұсқаны дұрыс деп белгілеуге болмайды (кемінде 1 қате нұсқа қалдыр). 4 нұсқа болса, [0,1,2,3] қабылданбайды.
- answerIndex пен answerIndices-ті бір мезгілде жазба; жоғарыдағы өрістерден басқа өрістерді шығарма.
explanation — шешім, міндетті емес.
difficulty — тек easy, medium немесе hard, міндетті емес.

Үлгі:
{
  "version": 1,
  "items": [
    {
      "prompt": "12 + 8 нешеге тең?",
      "options": ["18", "20", "22", "24"],
      "type": "single",
      "answerIndex": 1,
      "explanation": "12 + 8 = 20, сондықтан дұрыс жауап 20.",
      "difficulty": "easy"
    },
    {
      "prompt": "Мына жануарлардың қайсысы сүтқоректілер?",
      "options": ["кит", "пингвин", "жарғанат", "дельфин"],
      "type": "multiple",
      "answerIndices": [0, 2, 3],
      "explanation": "Кит, жарғанат және дельфин — сүтқоректілер, пингвин — құс.",
      "difficulty": "medium"
    }
  ]
}

Түрлендіру ережелері:
1. Тек JSON шығар: түсіндірме жоқ, Markdown блогы жоқ, алдыңғы және соңғы қосымша мәтін жоқ.
2. prompt бастапқы сұрақтың мағынасын сақтайды, нөмірі мен нұсқаларын алып тастайды; сұрақты қайта жазба, толықтырма және біріктірме; 200 таңбадан аспасын.
3. options бастапқы нұсқалар мен ретті сақтайды, кемінде 2, ең көбі 6; әріптік префикстерді жой; әрқайсысы 60 таңбадан аспасын; мазмұны қайталанбасын.
4. Бір жауапты сұрақта дәл бір answerIndex. Бірнеше жауапты сұрақта answerIndices өсу ретімен, қайталанбай, кемінде екі. Бір мезгілде екі жауап өрісін жазба және бірнеше жауапты сұрақты бір жауапқа қысқартпа.
5. explanation бастапқы материалда шешім болса ғана сақталады, 600 таңбадан аспасын; әйтпесе өткізіп жібер және өзің ойлап таппа.
6. difficulty бастапқы материалда қиындық көрсетілсе ғана толтырылады; тек easy, medium, hard.
7. «Дұрыс/бұрыс» сұрақтарын екі нұсқамен ["Дұрыс", "Бұрыс"] жаз және answerIndex көрсет. Құрама нұсқалар бір жауап болып саналады және сол күйінде қалады.
8. Бірнеше жауабы бар сұрақта кемінде 3 нұсқа, кемінде 2 дұрыс және кемінде 1 қате нұсқа болсын. Бұл орындалмаса, сұрақты толығымен өткізіп жібер; болжама, бір дұрыс нұсқаны таңдама және оны бір жауапты сұраққа айналдырма.
9. Бос орынды, тыңдауға арналған, суретке тәуелді, жауабы жоқ немесе жауабы анық емес сұрақтарды толығымен өткізіп жібер.
10. Материалда жоқ сұрақтарды жасама және шарттағы сандарды, өлшем бірліктерін, нұсқаларды өзгертпе.
11. Сұрақ саны шектелмейді; материалдағы барлық сұрақты түрлендір және санын азайтпа.
12. Шығарар алдында тексер: JSON талданады; барлық жолдар ASCII қос тырнақшада; соңғы үтір жоқ; әр индекс options ауқымында; type жауап өрістеріне сәйкес; items саны түрлендірілген сұрақтар санына тең.
13. Материал фото немесе PDF болса, алдымен мәтінді толық таны; анық емес таңбаларды «□»-пен алмастыр және болжама.
14. Барлығы бір жауапқа сыймаса, алдымен толық талданатын JSON шығар; мен «жалғастыр» деп жазған соң қалған сұрақтарды шығар және бұрын шығарылғандарды қайталама.

Түрлендіретін сұрақ материалы:`,
      importGeneratePrompt: `Сен сұрақ құрастырушы көмекшісің. Менің талаптарым бойынша бір дұрыс жауабы бар және бірнеше дұрыс жауабы бар түпнұсқа сұрақтар құрастырып, оларды оқу құрылғысына импорттауға болатын JSON етіп рәсімде. Тек JSON шығар.

Шығыс өрістері:
version әрқашан 1.
items — сұрақтар массиві.
prompt — сұрақ шарты.
options — нұсқалар массиві.
type — тек single немесе multiple.
Бір жауап үшін answerIndex, бірнеше жауап үшін answerIndices қолдан; индекстер 0-ден басталады.
Қатаң формат ережелері (шығарар алдында тексер):
- Бір жауап: type "single", дәл бір answerIndex (0-ден); тек бір дұрыс нұсқа.
- Бірнеше жауап: type "multiple", тек answerIndices (өсу ретімен, қайталанбай); кемінде 2 элемент және нұсқалар санынан аз болуы керек; барлық нұсқаны дұрыс деп белгілеуге болмайды (кемінде 1 қате нұсқа қалдыр). 4 нұсқа болса, [0,1,2,3] қабылданбайды.
- answerIndex пен answerIndices-ті бір мезгілде жазба; жоғарыдағы өрістерден басқа өрістерді шығарма.
explanation — шешім.
difficulty — тек easy, medium немесе hard.

Үлгі:
{
  "version": 1,
  "items": [
    {
      "prompt": "12 + 8 нешеге тең?",
      "options": ["18", "20", "22", "24"],
      "type": "single",
      "answerIndex": 1,
      "explanation": "12 + 8 = 20.",
      "difficulty": "easy"
    },
    {
      "prompt": "Мына жануарлардың қайсысы сүтқоректілер?",
      "options": ["кит", "пингвин", "жарғанат", "дельфин"],
      "type": "multiple",
      "answerIndices": [0, 2, 3],
      "explanation": "Кит, жарғанат және дельфин — сүтқоректілер, пингвин — құс.",
      "difficulty": "medium"
    }
  ]
}

Құрастыру ережелері:
1. Тек JSON шығар: түсіндірме жоқ, Markdown блогы жоқ, алдыңғы және соңғы қосымша мәтін жоқ.
2. Көрсетілген сұрақ түрін сақта: бір жауап үшін "type":"single" және "answerIndex"; бірнеше жауап үшін "type":"multiple" және "answerIndices"; аралас болса, екі түр де болсын; сұрақ түрі көрсетілмесе, бір жауапты сұрақ қолдан.
3. prompt толық, анық, бір мағыналы, нөмірсіз; 200 таңбадан аспасын.
4. options кемінде 2, ең көбі 6, қайталанбасын; бірнеше жауапты сұрақтарда кемінде 3; әріптік префикстерді жой; әрқайсысы 60 таңбадан аспасын; қате нұсқалар шынайы көрініп, бірақ анық қате болсын; «жоғарыдағылардың барлығы» және «жоғарыдағылардың ешқайсысы» сияқты нұсқаларды қолданба.
5. Бір жауапты сұрақта дәл бір дұрыс нұсқа; бірнеше жауапты сұрақта кемінде 2 дұрыс және кемінде 1 қате нұсқа; барлық нұсқаны дұрыс деп белгілеме. Бос орынды, тыңдауға арналған, суретке тәуелді немесе суретсіз орындалмайтын сұрақтарды құрастырма.
6. explanation жауапты түсіндіретін 1-3 сөйлем, 600 таңбадан аспасын; сенімді болмасаң өткізіп жібер, фактілерді ойлап таппа.
7. difficulty нақты қиындыққа сай, тек easy, medium, hard.
8. Сұрақтар көрсетілген сынып пен пәнге сай, оқу бағдарламасынан аспауы керек; сандар, өлшем бірліктері, фактілер және жауаптар дұрыс болсын.
9. Бір пакетте сұрақтарды қайталама және тек сандар немесе синонимдер ауысатын нұсқалармен санын толтырма.
10. Сұрақ саны шектелмейді; талаптағы санды шығар; сан көп болғандықтан қысқартпа, өткізіп жіберме және қайталама.
11. Шығарар алдында тексер: JSON талданады; барлық жолдар ASCII қос тырнақшада; соңғы үтір жоқ; type жауап өрістеріне сәйкес; бір жауапты сұрақта бір жауап; бірнеше жауапты сұрақта кемінде екі жауап және барлық нұсқа дұрыс болмайды; әр индекс options ауқымында; items саны сұрақтар санына сәйкес.
12. Барлығы бір жауапқа сыймаса, алдымен толық талданатын JSON шығар; мен «жалғастыр» деп жазған соң қалған сұрақтарды шығар және бұрын шығарылғандарды қайталама.

Сұрақ құрастыру талаптары:
Сынып/деңгей: 3-сынып
Пән: математика
Оқулық/тақырып: 1-тоқсан, 1-тақырып
Сұрақ түрі: аралас
Сұрақ саны: 20
Қиындық үлесі: easy 40%, medium 40%, hard 20%
Шығару тілі: қазақша
Қосымша талаптар: жоқ`,
      // Keep in sync with the final heading of importGeneratePrompt.
      importGeneratePromptHeading: "Сұрақ құрастыру талаптары:",
    },
  };

  const V23_TEXTS = {
    zh: {
      title: "家长网页",
      gate_pair_text: "请输入电视上的 6 位家长密码。验证后当天内再次打开无需输入；次日 0 点后需要重新验证。临时解锁只作用于电视锁屏。",
      footer: "答题闯关只在电视上开始和结算；临时解锁仅在电视锁屏时可用，关屏或重启后恢复守护。",
      unlockTitle: "临时解锁",
      unlockLead: "仅在电视锁屏时可用。输入家长密码后，电视临时解锁；关屏或重启电视即恢复守护。",
      unlockAction: "临时解锁电视",
      lockActionsLabel: "锁屏操作",
      manualTitle: "开启单次使用",
      manualLead: "仅在手动模式和电视锁屏时可用。时长和密码在这里填写，计时由电视开始和执行。",
      manualDurationLabel: "本次时长",
      manualDurationUnit: "分钟",
      manualDecrease: "减少 5 分钟",
      manualIncrease: "增加 5 分钟",
      manualAction: "输入密码并开始",
      manualDialogTitle: "开启单次使用",
      manualDialogLead: "电视将立即开始 {minutes} 分钟计时。请输入家长密码；密码不会保存在手机上。",
      manualChecking: "正在读取电视状态…",
      manualSubmit: "确认开始",
      manualReason_OK: "电视已锁屏，可以开启一次手动计时。",
      manualReason_ALREADY_UNLOCKED: "电视已临时解锁，不能开始计时。",
      manualReason_SESSION_ACTIVE: "电视正在计时，不能重复开始。",
      manualReason_SESSION_PENDING: "电视正在等待确认一次计时，不能重复开始。",
      manualReason_RESTING: "电视正在休息，稍后再试。",
      manualReason_OUTSIDE_WINDOW: "当前不在允许使用的时段。",
      manualReason_DAILY_LIMIT: "今天的可用时长已经用完。",
      manualReason_NO_TIMED_APPS: "电视还没有设置可计时的应用。",
      manualReason_ONLY_UNLIMITED: "当前只有不限时应用，无需开始计时。",
      manualRestWait: "电视正在休息，约 {minutes} 分钟后可以开始。",
      manualSessionActive: "电视正在计时，剩余约 {minutes} 分钟。",
      manualError_INVALID_DURATION: "时长不正确，请重新选择。",
      unlockDialogTitle: "输入家长密码",
      unlockDialogLead: "密码只用于本次解锁，不会保存在手机上。",
      unlockPinLabel: "家长密码",
      unlockSubmit: "确认解锁",
      unlockChecking: "正在读取电视状态…",
      unlockReason_OK: "电视已锁屏，可以临时解锁。",
      unlockReason_ALREADY_UNLOCKED: "电视已临时解锁。关屏或重启电视后恢复守护。",
      unlockReason_SESSION_ACTIVE: "电视正在计时，不能临时解锁。",
      unlockReason_STUDY_MODE: "电视在学习模式，不能临时解锁。",
      unlockReason_QUIZ_ACTIVE: "答题正在进行，不能临时解锁。",
      unlockReason_SCREEN_OFF: "电视屏幕已关闭，请先打开电视。",
      unlockReason_NOT_ON_LOCK_SCREEN: "电视当前不在锁屏界面，请在电视上返回锁屏后再试。",
      unlockReason_SETUP_REQUIRED: "电视还没完成儿童模式设置。",
      unlockReason_PIN_NOT_SET: "电视还没有设置家长密码。",
      unlockReason_DEFAULT_PIN: "请先在电视上修改初始密码。",
      unlockReason_NOT_LOCAL: "只有本地模式的电视支持家长网页。",
      unlockReason_NOT_READY: "电视状态同步中，请稍后再试。",
      unlockReason_STATE_CHANGED: "电视状态已变化，请稍后再试。",
      unlockReason_OFFLINE: "无法连接电视，请确认手机和电视在同一局域网。",
      unlockPinLength: "请输入 6 位家长密码。",
    },
    en: {
      title: "Parent web page",
      gate_pair_text: "Enter the 6-digit parent password shown on the TV. After verification, this phone can reopen the page without a PIN until local midnight. The temporary unlock only applies on the TV lock screen.",
      footer: "Quizzes start and settle on the TV only; temporary unlock is available only on the TV lock screen and ends when the screen turns off or the TV restarts.",
      unlockTitle: "Temporary unlock",
      unlockLead: "Available only while the TV is on its lock screen. Enter the parent password to unlock the TV temporarily; turning the TV screen off or restarting it restores the guard.",
      unlockAction: "Unlock TV temporarily",
      lockActionsLabel: "Lock-screen actions",
      manualTitle: "Start one session",
      manualLead: "Available only in manual mode while the TV is on its lock screen. Choose the duration and enter the password here; the TV starts and runs the timer.",
      manualDurationLabel: "Session length",
      manualDurationUnit: "minutes",
      manualDecrease: "Decrease by 5 minutes",
      manualIncrease: "Increase by 5 minutes",
      manualAction: "Enter password and start",
      manualDialogTitle: "Start one session",
      manualDialogLead: "The TV will start a {minutes}-minute timer immediately. Enter the parent password; it is not saved on the phone.",
      manualChecking: "Checking the TV state…",
      manualSubmit: "Start timer",
      manualReason_OK: "The TV is locked and ready for one manual session.",
      manualReason_ALREADY_UNLOCKED: "The TV is temporarily unlocked. A timed session cannot start.",
      manualReason_SESSION_ACTIVE: "The TV timer is already running.",
      manualReason_SESSION_PENDING: "The TV is waiting to confirm a session. Try again shortly.",
      manualReason_RESTING: "The TV is resting. Try again later.",
      manualReason_OUTSIDE_WINDOW: "The current time is outside the allowed window.",
      manualReason_DAILY_LIMIT: "Today's time allowance is used up.",
      manualReason_NO_TIMED_APPS: "No timed apps are configured on the TV.",
      manualReason_ONLY_UNLIMITED: "Only unlimited apps are in scope; no timer is needed.",
      manualRestWait: "The TV is resting; a session can start in about {minutes} minutes.",
      manualSessionActive: "The TV timer is running with about {minutes} minutes left.",
      manualError_INVALID_DURATION: "That duration is not valid. Choose another.",
      unlockDialogTitle: "Enter the parent password",
      unlockDialogLead: "The password is used only for this unlock and is not saved on the phone.",
      unlockPinLabel: "Parent password",
      unlockSubmit: "Unlock",
      unlockChecking: "Reading TV status…",
      unlockReason_OK: "The TV is locked and can be unlocked temporarily.",
      unlockReason_ALREADY_UNLOCKED: "The TV is temporarily unlocked. Turn the screen off or restart the TV to restore the guard.",
      unlockReason_SESSION_ACTIVE: "The TV timer is running. Temporary unlock is unavailable.",
      unlockReason_STUDY_MODE: "The TV is in study mode. Temporary unlock is unavailable.",
      unlockReason_QUIZ_ACTIVE: "A quiz is in progress. Temporary unlock is unavailable.",
      unlockReason_SCREEN_OFF: "The TV screen is off. Turn the TV on first.",
      unlockReason_NOT_ON_LOCK_SCREEN: "The TV is not on the lock screen right now. Return to the lock screen on the TV and try again.",
      unlockReason_SETUP_REQUIRED: "Kids mode setup is not complete on the TV.",
      unlockReason_PIN_NOT_SET: "No parent password is set on the TV.",
      unlockReason_DEFAULT_PIN: "Change the default parent password on the TV first.",
      unlockReason_NOT_LOCAL: "The parent web page is only available on a TV in local mode.",
      unlockReason_NOT_READY: "TV status is still syncing. Try again shortly.",
      unlockReason_STATE_CHANGED: "The TV state changed. Try again.",
      unlockReason_OFFLINE: "Cannot reach the TV. Check that the phone and TV are on the same LAN.",
      unlockPinLength: "Enter the 6-digit parent password.",
    },
    ru: {
      title: "Веб-страница для родителей",
      gate_pair_text: "Введите 6-значный пароль родителя с телевизора. После проверки страница открывается без пароля до местной полуночи. Временная разблокировка действует только на экране блокировки телевизора.",
      footer: "Викторина начинается и завершается только на телевизоре; временная разблокировка доступна только на экране блокировки и снимается при выключении экрана или перезапуске.",
      unlockTitle: "Временная разблокировка",
      unlockLead: "Доступно только когда телевизор на экране блокировки. Введите пароль родителя, чтобы временно разблокировать телевизор; выключение экрана или перезапуск вернут защиту.",
      unlockAction: "Временно разблокировать телевизор",
      lockActionsLabel: "Действия на экране блокировки",
      manualTitle: "Запустить один сеанс",
      manualLead: "Доступно только в ручном режиме и когда телевизор на экране блокировки. Выберите длительность и введите пароль здесь; таймер запускает и ведёт телевизор.",
      manualDurationLabel: "Длительность",
      manualDurationUnit: "минут",
      manualDecrease: "Уменьшить на 5 минут",
      manualIncrease: "Увеличить на 5 минут",
      manualAction: "Ввести пароль и начать",
      manualDialogTitle: "Запуск одного сеанса",
      manualDialogLead: "Телевизор сразу запустит таймер на {minutes} мин. Введите пароль родителя; на телефоне он не сохраняется.",
      manualChecking: "Проверяем состояние телевизора…",
      manualSubmit: "Запустить таймер",
      manualReason_OK: "Телевизор заблокирован; можно запустить один ручной сеанс.",
      manualReason_ALREADY_UNLOCKED: "Телевизор временно разблокирован. Запустить таймер нельзя.",
      manualReason_SESSION_ACTIVE: "Таймер на телевизоре уже идёт.",
      manualReason_SESSION_PENDING: "Телевизор ждёт подтверждения сеанса. Повторите позже.",
      manualReason_RESTING: "Телевизор отдыхает. Повторите позже.",
      manualReason_OUTSIDE_WINDOW: "Сейчас время вне разрешённого интервала.",
      manualReason_DAILY_LIMIT: "Дневной лимит времени уже израсходован.",
      manualReason_NO_TIMED_APPS: "На телевизоре не выбраны приложения для таймера.",
      manualReason_ONLY_UNLIMITED: "В области только приложения без ограничений; таймер не нужен.",
      manualRestWait: "Телевизор отдыхает; сеанс можно начать примерно через {minutes} мин.",
      manualSessionActive: "Таймер идёт, осталось около {minutes} мин.",
      manualError_INVALID_DURATION: "Недопустимая длительность. Выберите другую.",
      unlockDialogTitle: "Введите пароль родителя",
      unlockDialogLead: "Пароль используется только для этой разблокировки и не сохраняется на телефоне.",
      unlockPinLabel: "Пароль родителя",
      unlockSubmit: "Разблокировать",
      unlockChecking: "Читаем состояние телевизора…",
      unlockReason_OK: "Телевизор заблокирован; можно временно разблокировать.",
      unlockReason_ALREADY_UNLOCKED: "Телевизор временно разблокирован. Выключите экран или перезапустите телевизор, чтобы вернуть защиту.",
      unlockReason_SESSION_ACTIVE: "На телевизоре идёт таймер. Временная разблокировка недоступна.",
      unlockReason_STUDY_MODE: "Телевизор в режиме учёбы. Временная разблокировка недоступна.",
      unlockReason_QUIZ_ACTIVE: "Идёт викторина. Временная разблокировка недоступна.",
      unlockReason_SCREEN_OFF: "Экран телевизора выключен. Сначала включите телевизор.",
      unlockReason_NOT_ON_LOCK_SCREEN: "Сейчас на телевизоре не показан экран блокировки. Вернитесь к экрану блокировки на телевизоре и повторите.",
      unlockReason_SETUP_REQUIRED: "Настройка детского режима на телевизоре не завершена.",
      unlockReason_PIN_NOT_SET: "На телевизоре не задан пароль родителя.",
      unlockReason_DEFAULT_PIN: "Сначала смените начальный пароль на телевизоре.",
      unlockReason_NOT_LOCAL: "Веб-страница родителей доступна только в локальном режиме.",
      unlockReason_NOT_READY: "Состояние телевизора ещё синхронизируется. Повторите позже.",
      unlockReason_STATE_CHANGED: "Состояние телевизора изменилось. Повторите.",
      unlockReason_OFFLINE: "Нет связи с телевизором. Убедитесь, что телефон и телевизор в одной локальной сети.",
      unlockPinLength: "Введите 6-значный пароль родителя.",
    },
    uk: {
      title: "Веб-сторінка для батьків",
      gate_pair_text: "Введіть 6-значний пароль батьків із телевізора. Після перевірки сторінка відкривається без пароля до місцевої півночі. Тимчасове розблокування діє лише на екрані блокування телевізора.",
      footer: "Вікторина починається й завершується лише на телевізорі; тимчасове розблокування доступне лише на екрані блокування і знімається після вимкнення екрана або перезапуску.",
      unlockTitle: "Тимчасове розблокування",
      unlockLead: "Доступне лише коли телевізор на екрані блокування. Введіть пароль батьків, щоб тимчасово розблокувати телевізор; вимкнення екрана або перезапуск повернуть захист.",
      unlockAction: "Тимчасово розблокувати телевізор",
      lockActionsLabel: "Дії на екрані блокування",
      manualTitle: "Запустити один сеанс",
      manualLead: "Доступно лише в ручному режимі й коли телевізор на екрані блокування. Виберіть тривалість і введіть пароль тут; таймер запускає й веде телевізор.",
      manualDurationLabel: "Тривалість",
      manualDurationUnit: "хвилин",
      manualDecrease: "Зменшити на 5 хвилин",
      manualIncrease: "Збільшити на 5 хвилин",
      manualAction: "Ввести пароль і почати",
      manualDialogTitle: "Запуск одного сеансу",
      manualDialogLead: "Телевізор одразу запустить таймер на {minutes} хв. Введіть пароль батьків; на телефоні він не зберігається.",
      manualChecking: "Перевіряємо стан телевізора…",
      manualSubmit: "Запустити таймер",
      manualReason_OK: "Телевізор заблокований; можна запустити один ручний сеанс.",
      manualReason_ALREADY_UNLOCKED: "Телевізор тимчасово розблокований. Запустити таймер не можна.",
      manualReason_SESSION_ACTIVE: "Таймер на телевізорі вже працює.",
      manualReason_SESSION_PENDING: "Телевізор чекає підтвердження сеансу. Спробуйте пізніше.",
      manualReason_RESTING: "Телевізор відпочиває. Спробуйте пізніше.",
      manualReason_OUTSIDE_WINDOW: "Зараз час поза дозволеним інтервалом.",
      manualReason_DAILY_LIMIT: "Денний ліміт часу вже використано.",
      manualReason_NO_TIMED_APPS: "На телевізорі не вибрано застосунки для таймера.",
      manualReason_ONLY_UNLIMITED: "У зоні лише застосунки без обмежень; таймер не потрібен.",
      manualRestWait: "Телевізор відпочиває; сеанс можна почати приблизно через {minutes} хв.",
      manualSessionActive: "Таймер працює, лишилося близько {minutes} хв.",
      manualError_INVALID_DURATION: "Неприпустима тривалість. Виберіть іншу.",
      unlockDialogTitle: "Введіть пароль батьків",
      unlockDialogLead: "Пароль використовується лише для цього розблокування і не зберігається на телефоні.",
      unlockPinLabel: "Пароль батьків",
      unlockSubmit: "Розблокувати",
      unlockChecking: "Читаємо стан телевізора…",
      unlockReason_OK: "Телевізор заблокований; можна тимчасово розблокувати.",
      unlockReason_ALREADY_UNLOCKED: "Телевізор тимчасово розблокований. Вимкніть екран або перезапустіть телевізор, щоб повернути захист.",
      unlockReason_SESSION_ACTIVE: "На телевізорі йде таймер. Тимчасове розблокування недоступне.",
      unlockReason_STUDY_MODE: "Телевізор у режимі навчання. Тимчасове розблокування недоступне.",
      unlockReason_QUIZ_ACTIVE: "Триває вікторина. Тимчасове розблокування недоступне.",
      unlockReason_SCREEN_OFF: "Екран телевізора вимкнено. Спершу увімкніть телевізор.",
      unlockReason_NOT_ON_LOCK_SCREEN: "Зараз на телевізорі не показано екран блокування. Поверніться до екрана блокування на телевізорі та повторіть спробу.",
      unlockReason_SETUP_REQUIRED: "Налаштування дитячого режиму на телевізорі не завершено.",
      unlockReason_PIN_NOT_SET: "На телевізорі не задано пароль батьків.",
      unlockReason_DEFAULT_PIN: "Спершу змініть початковий пароль на телевізорі.",
      unlockReason_NOT_LOCAL: "Веб-сторінка батьків доступна лише в локальному режимі.",
      unlockReason_NOT_READY: "Стан телевізора ще синхронізується. Спробуйте пізніше.",
      unlockReason_STATE_CHANGED: "Стан телевізора змінився. Спробуйте ще раз.",
      unlockReason_OFFLINE: "Немає зв'язку з телевізором. Перевірте, що телефон і телевізор в одній локальній мережі.",
      unlockPinLength: "Введіть 6-значний пароль батьків.",
    },
    kk: {
      title: "Ата-аналарға арналған веб-бет",
      gate_pair_text: "Теледидардағы 6 таңбалы ата-ана құпиясөзін енгізіңіз. Тексеруден кейін бет жергілікті түн ортасына дейін құпиясөзсіз ашылады. Уақытша ашу тек теледидар құлыптау экранында әрекет етеді.",
      footer: "Викторина тек теледидарда басталады және есептеледі; уақытша ашу тек құлыптау экранында қолжетімді және экран өшкенде немесе теледидар қайта іске қосылғанда жойылады.",
      unlockTitle: "Уақытша ашу",
      unlockLead: "Тек теледидар құлыптау экранында қолжетімді. Ата-ана құпиясөзін енгізіп, теледидарды уақытша ашыңыз; экранды өшірсеңіз немесе теледидарды қайта іске қоссаңыз, қорғаныс қайтады.",
      unlockAction: "Теледидарды уақытша ашу",
      lockActionsLabel: "Құлыптау экранындағы әрекеттер",
      manualTitle: "Бір сеансты бастау",
      manualLead: "Тек қолмен режимінде және теледидар құлыптау экранында болғанда қолжетімді. Ұзақтықты таңдап, құпиясөзді осында енгізіңіз; таймерді теледидар бастайды және жүргізеді.",
      manualDurationLabel: "Ұзақтығы",
      manualDurationUnit: "минут",
      manualDecrease: "5 минутқа азайту",
      manualIncrease: "5 минутқа қосу",
      manualAction: "Құпиясөзді енгізіп бастау",
      manualDialogTitle: "Бір сеансты бастау",
      manualDialogLead: "Теледидар бірден {minutes} минуттық таймерді бастайды. Ата-ана құпиясөзін енгізіңіз; ол телефонда сақталмайды.",
      manualChecking: "Теледидар күйі тексерілуде…",
      manualSubmit: "Таймерді бастау",
      manualReason_OK: "Теледидар құлыптаулы; бір қолмен сеансын бастауға болады.",
      manualReason_ALREADY_UNLOCKED: "Теледидар уақытша ашық. Таймерді бастауға болмайды.",
      manualReason_SESSION_ACTIVE: "Теледидарда таймер әлдеқашан жүріп жатыр.",
      manualReason_SESSION_PENDING: "Теледидар сеансты растауды күтіп тұр. Сәл кейінірек қайталаңыз.",
      manualReason_RESTING: "Теледидар демалып тұр. Кейінірек қайталаңыз.",
      manualReason_OUTSIDE_WINDOW: "Қазір рұқсат етілген уақыт аралығынан тыс.",
      manualReason_DAILY_LIMIT: "Күнделікті уақыт шегі таусылды.",
      manualReason_NO_TIMED_APPS: "Теледидарда таймерге арналған қолданбалар таңдалмаған.",
      manualReason_ONLY_UNLIMITED: "Аумақта тек шектеусіз қолданбалар бар; таймер қажет емес.",
      manualRestWait: "Теледидар демалып тұр; сеансты шамамен {minutes} минуттан кейін бастауға болады.",
      manualSessionActive: "Таймер жүріп жатыр, шамамен {minutes} минут қалды.",
      manualError_INVALID_DURATION: "Ұзақтық жарамсыз. Басқа мәнді таңдаңыз.",
      unlockDialogTitle: "Ата-ана құпиясөзін енгізіңіз",
      unlockDialogLead: "Құпиясөз тек осы ашу үшін пайдаланылады және телефонда сақталмайды.",
      unlockPinLabel: "Ата-ана құпиясөзі",
      unlockSubmit: "Ашу",
      unlockChecking: "Теледидар күйі оқылып жатыр…",
      unlockReason_OK: "Теледидар құлыптаулы; уақытша ашуға болады.",
      unlockReason_ALREADY_UNLOCKED: "Теледидар уақытша ашылды. Қорғанысты қайтару үшін экранды өшіріңіз немесе теледидарды қайта іске қосыңыз.",
      unlockReason_SESSION_ACTIVE: "Теледидарда таймер жүріп жатыр. Уақытша ашу қолжетімсіз.",
      unlockReason_STUDY_MODE: "Теледидар оқу режимінде. Уақытша ашу қолжетімсіз.",
      unlockReason_QUIZ_ACTIVE: "Викторина жүріп жатыр. Уақытша ашу қолжетімсіз.",
      unlockReason_SCREEN_OFF: "Теледидар экраны өшіп тұр. Алдымен теледидарды қосыңыз.",
      unlockReason_NOT_ON_LOCK_SCREEN: "Қазір теледидарда құлыптау экраны көрсетілмей тұр. Теледидарда құлыптау экранына оралып, қайталап көріңіз.",
      unlockReason_SETUP_REQUIRED: "Теледидарда балалар режимінің баптауы аяқталмаған.",
      unlockReason_PIN_NOT_SET: "Теледидарда ата-ана құпиясөзі орнатылмаған.",
      unlockReason_DEFAULT_PIN: "Алдымен теледидарда бастапқы құпиясөзді өзгертіңіз.",
      unlockReason_NOT_LOCAL: "Ата-аналарға арналған веб-бет тек жергілікті режимде қолжетімді.",
      unlockReason_NOT_READY: "Теледидар күйі әлі синхрондалып жатыр. Сәл кейінірек қайталаңыз.",
      unlockReason_STATE_CHANGED: "Теледидар күйі өзгерді. Қайталап көріңіз.",
      unlockReason_OFFLINE: "Теледидармен байланыс жоқ. Телефон мен теледидардың бір жергілікті желіде екенін тексеріңіз.",
      unlockPinLength: "6 таңбалы ата-ана құпиясөзін енгізіңіз.",
    },
  };

  const V25_TEXTS = {
    zh: {
      settingsCountdown: "答题时间",
      timeDecrease: "减少 15 秒",
      timeIncrease: "增加 15 秒",
      secondsUnit: "秒",
    },
    en: {
      settingsCountdown: "Answer time",
      timeDecrease: "Decrease by 15 seconds",
      timeIncrease: "Increase by 15 seconds",
      secondsUnit: "s",
    },
    ru: {
      settingsCountdown: "Время ответа",
      timeDecrease: "Уменьшить на 15 секунд",
      timeIncrease: "Увеличить на 15 секунд",
      secondsUnit: "с",
    },
    uk: {
      settingsCountdown: "Час відповіді",
      timeDecrease: "Зменшити на 15 секунд",
      timeIncrease: "Збільшити на 15 секунд",
      secondsUnit: "с",
    },
    kk: {
      settingsCountdown: "Жауап уақыты",
      timeDecrease: "15 секундқа азайту",
      timeIncrease: "15 секундқа қосу",
      secondsUnit: "с",
    },
  };

  const V26_TEXTS = {
    zh: {
      unlockThresholdTitle: "答题看电视的时间门槛（分钟）",
      settingsFeedbackHold: "答错查看解答最少停留时间",
      settingsFeedbackHoldHint: "答错或超时时，解答至少停留这么久（语音更长时以语音结束为准）。答对不停留。",
      roundDecrease: "减少 5 题",
      roundIncrease: "增加 5 题",
      timeDecrease: "减少 5 秒",
      timeIncrease: "增加 5 秒",
      feedbackHoldDecrease: "减少 1 秒",
      feedbackHoldIncrease: "增加 1 秒",
      minutesUnit: "分钟",
      unlockDecrease: "减少 5 分钟",
      unlockIncrease: "增加 5 分钟",
    },
    en: {
      unlockThresholdTitle: "TV time threshold (minutes)",
      settingsFeedbackHold: "Minimum explanation stay after a wrong answer",
      settingsFeedbackHoldHint: "After a wrong answer or timeout, the explanation stays at least this long; a longer voice clip still wins. Correct answers have no hold.",
      roundDecrease: "Decrease by 5 questions",
      roundIncrease: "Increase by 5 questions",
      timeDecrease: "Decrease by 5 seconds",
      timeIncrease: "Increase by 5 seconds",
      feedbackHoldDecrease: "Decrease by 1 second",
      feedbackHoldIncrease: "Increase by 1 second",
      minutesUnit: "min",
      unlockDecrease: "Decrease by 5 minutes",
      unlockIncrease: "Increase by 5 minutes",
    },
    ru: {
      unlockThresholdTitle: "Порог времени для ТВ (минуты)",
      settingsFeedbackHold: "Минимальное время просмотра объяснения после ошибки",
      settingsFeedbackHoldHint: "После неверного ответа или тайм-аута объяснение остаётся на экране не меньше этого времени; более длинная озвучка имеет приоритет. На верные ответы задержка не действует.",
      roundDecrease: "Уменьшить на 5 вопросов",
      roundIncrease: "Увеличить на 5 вопросов",
      timeDecrease: "Уменьшить на 5 секунд",
      timeIncrease: "Увеличить на 5 секунд",
      feedbackHoldDecrease: "Уменьшить на 1 секунду",
      feedbackHoldIncrease: "Увеличить на 1 секунду",
      minutesUnit: "мин",
      unlockDecrease: "Уменьшить на 5 минут",
      unlockIncrease: "Увеличить на 5 минут",
    },
    uk: {
      unlockThresholdTitle: "Поріг часу для ТБ (хвилини)",
      settingsFeedbackHold: "Мінімальний час перегляду пояснення після помилки",
      settingsFeedbackHoldHint: "Після неправильної відповіді або тайм-ауту пояснення залишається на екрані щонайменше стільки часу; довше озвучення має пріоритет. На правильні відповіді затримка не діє.",
      roundDecrease: "Зменшити на 5 питань",
      roundIncrease: "Збільшити на 5 питань",
      timeDecrease: "Зменшити на 5 секунд",
      timeIncrease: "Збільшити на 5 секунд",
      feedbackHoldDecrease: "Зменшити на 1 секунду",
      feedbackHoldIncrease: "Збільшити на 1 секунду",
      minutesUnit: "хв",
      unlockDecrease: "Зменшити на 5 хвилин",
      unlockIncrease: "Збільшити на 5 хвилин",
    },
    kk: {
      unlockThresholdTitle: "Теледидар уақытының шегі (минут)",
      settingsFeedbackHold: "Қате жауаптан кейін түсіндірмені көруге қажет ең аз уақыт",
      settingsFeedbackHoldHint: "Қате жауап немесе уақыт біткен кезде түсіндірме кемінде осы уақыт бойы тұрады; ұзағырақ дауыс басым болады. Дұрыс жауаптарға кідіріс жоқ.",
      roundDecrease: "5 сұраққа азайту",
      roundIncrease: "5 сұраққа қосу",
      timeDecrease: "5 секундқа азайту",
      timeIncrease: "5 секундқа қосу",
      feedbackHoldDecrease: "1 секундқа азайту",
      feedbackHoldIncrease: "1 секундқа қосу",
      minutesUnit: "мин",
      unlockDecrease: "5 минутқа азайту",
      unlockIncrease: "5 минутқа қосу",
    },
  };

  // V28: the parent app download lives in the Me tab. It is hidden inside the app shell,
  // where the app is already installed, and shares one daily click budget with the
  // parent center's first-entry prompt.
  const V28_TEXTS = {
    zh: {
      parentAppDownload: "下载家长端 App",
      parentAppDownloadRemaining: "今天还可下载 {n} 次",
      parentAppDownloadLimit: "今天已达上限，明天再试",
    },
    en: {
      parentAppDownload: "Download the parent app",
      parentAppDownloadRemaining: "Downloads left today: {n}",
      parentAppDownloadLimit: "Daily limit reached; try again tomorrow",
    },
    ru: {
      parentAppDownload: "Скачать приложение родителя",
      parentAppDownloadRemaining: "Осталось загрузок сегодня: {n}",
      parentAppDownloadLimit: "Дневной лимит; повторите завтра",
    },
    uk: {
      parentAppDownload: "Завантажити застосунок батьків",
      parentAppDownloadRemaining: "Залишилося завантажень сьогодні: {n}",
      parentAppDownloadLimit: "Денний ліміт; повторіть завтра",
    },
    kk: {
      parentAppDownload: "Ата-ана қолданбасын жүктеу",
      parentAppDownloadRemaining: "Бүгін қалған жүктеу саны: {n}",
      parentAppDownloadLimit: "Күнделікті шек; ертең қайталаңыз",
    },
  };

  // V34: bank management lives on its own subpage with an explicit per-bank
  // clear action. Every clear goes through two confirmations.
  const V34_TEXTS = {
    zh: {
      bankManageEntry: "题库管理",
      bankManageEntryState: "预置 + AI {course} · 手动导入 {custom} · 错题 {wrong}",
      bankManageTitle: "题库管理",
      bankManageLead: "预置题库、AI 题、每个手动导入标签和错题可分别清空；清空会永久删除题目和作答记录，每次需要两步确认。",
      bankManageCourseTitle: "教材 / AI 题库",
      bankManageCourseMeta: "共 {n} 道",
      bankManageCourseStateLead: "按状态单独清除（同样会永久删除）：",
      bankManageCustomTitle: "自定义题库",
      bankManageCustomLead: "每个标签单独一行，可只看数量或单独清空。",
      bankManageCustomMeta: "共 {n} 道 · {tags} 个标签",
      bankManageTagMeta: "共 {n} 道 · 未使用 {unused}",
      bankManageClear: "清空",
      bankManageClearCourse: "清空教材 / AI 题库",
      bankManageClearCustom: "清空全部自定义题库",
      bankManageClearTag: "清空「{name}」",
      bankManageWrongTitle: "错题题库",
      bankManageWrongMeta: "共 {n} 道 · 来自课程/AI 和自定义题库",
      bankManageClearWrong: "清空错题题库",
      bankManageAllTitle: "全部题库",
      bankManageAllMeta: "共 {n} 道 · 含错题 {wrong} 道",
      bankManageClearAll: "清空全部题库",
      bankClearScopeCourse: "教材 / AI 题库",
      bankClearScopeAllWrong: "全部错题",
      bankClearScopeCustomTag: "自定义题库「{name}」",
      bankClearScopeCustomAll: "全部自定义题库",
      bankClearScopeAll: "全部题库",
      bankClearTitleFirst: "清空{scope}？",
      bankClearTitleFinal: "再次确认：清空{scope}",
      bankClearAllDetail: "{scope}共 {n} 道题和作答记录将被永久删除。",
      bankClearUnusedDetail: "将删除{scope}中未使用（含已过期）的题目。",
      bankClearAnsweredDetail: "将删除{scope}中已回答的题目。",
      bankClearWrongDetail: "将删除{scope}中答错的题目。",
      bankClearWrongAllDetail: "所有答错的题目和作答记录将被永久删除。",
      bankClearFirstWarning: "此操作不能撤销，还需要再确认一次。",
      bankClearFinalWarning: "这是最后一次确认，清空后无法恢复。",
      bankClearContinue: "继续",
      bankClearConfirm: "确认清空",
      maxEarned: "单轮闯关奖励电视的时间上限",
      maxEarnedHint: "每轮最多奖励这么多分钟；达到上限后本轮不再增加。",
      maxEarnedDecrease: "减少 5 分钟",
      maxEarnedIncrease: "增加 5 分钟",
    },
    en: {
      bankManageEntry: "Manage banks",
      bankManageEntryState: "Preset + AI {course} · Manual {custom} · Wrong {wrong}",
      bankManageTitle: "Manage banks",
      bankManageLead: "The preset bank, AI questions, each manual-import tag, and wrong questions can be cleared separately. Clearing permanently deletes questions and answer history; every clear needs two confirmations.",
      bankManageCourseTitle: "Textbook / AI bank",
      bankManageCourseMeta: "{n} questions",
      bankManageCourseStateLead: "Clear by state instead (also permanent):",
      bankManageCustomTitle: "Custom banks",
      bankManageCustomLead: "Each tag has its own row, so you can check counts or clear it alone.",
      bankManageCustomMeta: "{n} questions · {tags} tags",
      bankManageTagMeta: "{n} questions · {unused} unused",
      bankManageClear: "Clear",
      bankManageClearCourse: "Clear textbook / AI bank",
      bankManageClearCustom: "Clear all custom banks",
      bankManageClearTag: "Clear “{name}”",
      bankManageWrongTitle: "Wrong-answer bank",
      bankManageWrongMeta: "{n} questions · from course/AI and custom banks",
      bankManageClearWrong: "Clear wrong-answer bank",
      bankManageAllTitle: "All banks",
      bankManageAllMeta: "{n} questions · includes {wrong} wrong",
      bankManageClearAll: "Clear all banks",
      bankClearScopeCourse: "the textbook / AI bank",
      bankClearScopeAllWrong: "all wrong questions",
      bankClearScopeCustomTag: "the custom bank “{name}”",
      bankClearScopeCustomAll: "all custom banks",
      bankClearScopeAll: "all banks",
      bankClearTitleFirst: "Clear {scope}?",
      bankClearTitleFinal: "Confirm again: clear {scope}",
      bankClearAllDetail: "All {n} questions and their answer history in {scope} will be permanently deleted.",
      bankClearUnusedDetail: "Unused (and expired) questions in {scope} will be deleted.",
      bankClearAnsweredDetail: "Answered questions in {scope} will be deleted.",
      bankClearWrongDetail: "Wrongly answered questions in {scope} will be deleted.",
      bankClearWrongAllDetail: "All wrongly answered questions and their answer history will be permanently deleted.",
      bankClearFirstWarning: "This cannot be undone; one more confirmation is required.",
      bankClearFinalWarning: "Final confirmation. This cannot be undone.",
      bankClearContinue: "Continue",
      bankClearConfirm: "Clear now",
      maxEarned: "Max TV time rewarded per round",
      maxEarnedHint: "The most TV time one round can earn; the round stops adding time at this cap.",
      maxEarnedDecrease: "Decrease by 5 minutes",
      maxEarnedIncrease: "Increase by 5 minutes",
    },
    ru: {
      bankManageEntry: "Управление банками",
      bankManageEntryState: "Готовые + ИИ {course} · Ручной импорт {custom} · Ошибки {wrong}",
      bankManageTitle: "Управление банками",
      bankManageLead: "Готовый банк, вопросы ИИ, каждый тег ручного импорта и ошибки можно очищать отдельно. Очистка навсегда удаляет вопросы и историю ответов; каждая требует двух подтверждений.",
      bankManageCourseTitle: "Банк учебника / ИИ",
      bankManageCourseMeta: "Всего: {n}",
      bankManageCourseStateLead: "Можно очистить по состоянию (тоже необратимо):",
      bankManageCustomTitle: "Свои банки",
      bankManageCustomLead: "Каждый тег в своей строке: можно проверить количество или очистить только его.",
      bankManageCustomMeta: "Всего: {n} · тегов: {tags}",
      bankManageTagMeta: "Всего: {n} · не использовано: {unused}",
      bankManageClear: "Очистить",
      bankManageClearCourse: "Очистить банк учебника / ИИ",
      bankManageClearCustom: "Очистить все свои банки",
      bankManageClearTag: "Очистить «{name}»",
      bankManageWrongTitle: "Банк ошибок",
      bankManageWrongMeta: "Всего: {n} · из курса/ИИ и своих банков",
      bankManageClearWrong: "Очистить банк ошибок",
      bankManageAllTitle: "Все банки",
      bankManageAllMeta: "Всего: {n} · включая ошибок: {wrong}",
      bankManageClearAll: "Очистить все банки",
      bankClearScopeCourse: "банк учебника / ИИ",
      bankClearScopeAllWrong: "все ошибки",
      bankClearScopeCustomTag: "свой банк «{name}»",
      bankClearScopeCustomAll: "все свои банки",
      bankClearScopeAll: "все банки",
      bankClearTitleFirst: "Очистить {scope}?",
      bankClearTitleFinal: "Подтвердите: очистить {scope}",
      bankClearAllDetail: "{scope}: все вопросы ({n}) и история ответов будут удалены навсегда.",
      bankClearUnusedDetail: "{scope}: будут удалены неиспользованные (и просроченные) вопросы.",
      bankClearAnsweredDetail: "{scope}: будут удалены отвеченные вопросы.",
      bankClearWrongDetail: "{scope}: будут удалены вопросы с неверными ответами.",
      bankClearWrongAllDetail: "Все вопросы с неверными ответами и история ответов будут удалены навсегда.",
      bankClearFirstWarning: "Это нельзя отменить; потребуется ещё одно подтверждение.",
      bankClearFinalWarning: "Последнее подтверждение. Отменить будет нельзя.",
      bankClearContinue: "Продолжить",
      bankClearConfirm: "Очистить",
      maxEarned: "Максимум времени ТВ за раунд",
      maxEarnedHint: "Сколько минут ТВ может принести один раунд; после этого время больше не начисляется.",
      maxEarnedDecrease: "Уменьшить на 5 минут",
      maxEarnedIncrease: "Увеличить на 5 минут",
    },
    uk: {
      bankManageEntry: "Керування банками",
      bankManageEntryState: "Готові + ШІ {course} · Ручне імпортування {custom} · Помилки {wrong}",
      bankManageTitle: "Керування банками",
      bankManageLead: "Готовий банк, питання ШІ, кожен тег ручного імпорту й помилки можна очищати окремо. Очищення назавжди видаляє питання й історію відповідей; кожне потребує двох підтверджень.",
      bankManageCourseTitle: "Банк підручника / ШІ",
      bankManageCourseMeta: "Усього: {n}",
      bankManageCourseStateLead: "Можна очистити за станом (також незворотно):",
      bankManageCustomTitle: "Власні банки",
      bankManageCustomLead: "Кожен тег має окремий рядок: можна перевірити кількість або очистити лише його.",
      bankManageCustomMeta: "Усього: {n} · тегів: {tags}",
      bankManageTagMeta: "Усього: {n} · не використано: {unused}",
      bankManageClear: "Очистити",
      bankManageClearCourse: "Очистити банк підручника / ШІ",
      bankManageClearCustom: "Очистити всі власні банки",
      bankManageClearTag: "Очистити «{name}»",
      bankManageWrongTitle: "Банк помилок",
      bankManageWrongMeta: "Усього: {n} · з курсу/ШІ та власних банків",
      bankManageClearWrong: "Очистити банк помилок",
      bankManageAllTitle: "Усі банки",
      bankManageAllMeta: "Усього: {n} · включно з помилками: {wrong}",
      bankManageClearAll: "Очистити всі банки",
      bankClearScopeCourse: "банк підручника / ШІ",
      bankClearScopeAllWrong: "усі помилки",
      bankClearScopeCustomTag: "власний банк «{name}»",
      bankClearScopeCustomAll: "усі власні банки",
      bankClearScopeAll: "усі банки",
      bankClearTitleFirst: "Очистити {scope}?",
      bankClearTitleFinal: "Підтвердьте ще раз: очистити {scope}",
      bankClearAllDetail: "{scope}: усі питання ({n}) та історію відповідей буде остаточно видалено.",
      bankClearUnusedDetail: "{scope}: буде видалено невикористані (і прострочені) питання.",
      bankClearAnsweredDetail: "{scope}: буде видалено питання з відповідями.",
      bankClearWrongDetail: "{scope}: буде видалено питання з неправильними відповідями.",
      bankClearWrongAllDetail: "Усі питання з неправильними відповідями та історію відповідей буде остаточно видалено.",
      bankClearFirstWarning: "Це не можна скасувати; потрібне ще одне підтвердження.",
      bankClearFinalWarning: "Останнє підтвердження. Скасувати буде неможливо.",
      bankClearContinue: "Продовжити",
      bankClearConfirm: "Очистити",
      maxEarned: "Максимум часу ТБ за раунд",
      maxEarnedHint: "Скільки хвилин ТБ може принести один раунд; після цього час більше не нараховується.",
      maxEarnedDecrease: "Зменшити на 5 хвилин",
      maxEarnedIncrease: "Збільшити на 5 хвилин",
    },
    kk: {
      bankManageEntry: "Банктерді басқару",
      bankManageEntryState: "Дайын + ЖИ {course} · Қолмен импорт {custom} · Қателер {wrong}",
      bankManageTitle: "Банктерді басқару",
      bankManageLead: "Дайын банкіні, ЖИ сұрақтарын, әр қолмен импорттау тегін және қателерді бөлек тазалауға болады. Тазалау сұрақтар мен жауап тарихын біржола жояды; әрқайсысы екі рет растауды қажет етеді.",
      bankManageCourseTitle: "Оқулық / AI банкі",
      bankManageCourseMeta: "Барлығы: {n}",
      bankManageCourseStateLead: "Күйі бойынша да тазалауға болады (қайтарылмайды):",
      bankManageCustomTitle: "Жеке банктер",
      bankManageCustomLead: "Әр тег жеке жолда: санын тексеруге немесе тек соны тазалауға болады.",
      bankManageCustomMeta: "Барлығы: {n} · тег: {tags}",
      bankManageTagMeta: "Барлығы: {n} · пайдаланылмаған: {unused}",
      bankManageClear: "Тазалау",
      bankManageClearCourse: "Оқулық / AI банкін тазалау",
      bankManageClearCustom: "Барлық жеке банкті тазалау",
      bankManageClearTag: "«{name}» тазалау",
      bankManageWrongTitle: "Қателер банкі",
      bankManageWrongMeta: "Барлығы: {n} · курс/AI және жеке банктерден",
      bankManageClearWrong: "Қателер банкін тазалау",
      bankManageAllTitle: "Барлық банктер",
      bankManageAllMeta: "Барлығы: {n} · қателермен қоса: {wrong}",
      bankManageClearAll: "Барлық банкті тазалау",
      bankClearScopeCourse: "оқулық / AI банкі",
      bankClearScopeAllWrong: "барлық қателер",
      bankClearScopeCustomTag: "«{name}» жеке банкі",
      bankClearScopeCustomAll: "барлық жеке банктер",
      bankClearScopeAll: "барлық банктер",
      bankClearTitleFirst: "{scope} тазалау керек пе?",
      bankClearTitleFinal: "Қайта растаңыз: {scope} тазалау",
      bankClearAllDetail: "{scope}: барлық {n} сұрақ пен жауап тарихы біржола жойылады.",
      bankClearUnusedDetail: "{scope}: пайдаланылмаған (және мерзімі өткен) сұрақтар жойылады.",
      bankClearAnsweredDetail: "{scope}: жауапталған сұрақтар жойылады.",
      bankClearWrongDetail: "{scope}: қате жауапталған сұрақтар жойылады.",
      bankClearWrongAllDetail: "Барлық қате жауапталған сұрақтар мен жауап тарихы біржола жойылады.",
      bankClearFirstWarning: "Бұны қайтару мүмкін емес; тағы бір растау қажет.",
      bankClearFinalWarning: "Соңғы растау. Тазалағаннан кейін қайтару мүмкін емес.",
      bankClearContinue: "Жалғастыру",
      bankClearConfirm: "Тазалау",
      maxEarned: "Бір раундтағы теледидар уақытының жоғарғы шегі",
      maxEarnedHint: "Бір раунд ең көбі осы минутты әкеле алады; шекке жеткенде уақыт қосылмайды.",
      maxEarnedDecrease: "5 минутқа азайту",
      maxEarnedIncrease: "5 минутқа қосу",
    },
  };

  // V35: DeepSeek voice reading. The module only appears for the official
  // DeepSeek host because speech uses a fixed DeepSeek endpoint and the stored
  // key must never be forwarded to an unrelated compatible gateway.
  const V35_TEXTS = {
    zh: {
      speechTitle: "开启语音播报",
      speechHint: "答题时自动朗读；答错或超时后再朗读答案和解题过程。",
      speechContent: "朗读内容",
      speechQuestion: "题目",
      speechOptions: "选项",
      speechAnswer: "答案和解题过程（答错或超时后）",
      speechKeepHint: "关闭开关不会清空勾选，下次开启时沿用。",
      speechModelHint: "语音播报使用 DeepSeek 语音接口和音色 stella，不使用上方的对话模型，只复用 Key。",
      speechSaveToApply: "保存 AI 设置后生效。",
      speechBillingHint: "使用上方 DeepSeek Key 和 DeepSeek 语音服务；题目、选项、答案和解题过程会发送给 DeepSeek，并按字符计费。朗读失败不影响答题。",
      speechSelectAtLeastOne: "开启语音播报时，请至少勾选一项朗读内容。",
      err_INVALID_SPEECH_SETTINGS: "语音播报设置无法读取，请重新打开设置。",
      err_SPEECH_CONTENT_REQUIRED: "请至少勾选一项朗读内容。",
    },
    en: {
      speechTitle: "Read questions aloud",
      speechHint: "Reads each question automatically; the answer and explanation are read after a wrong or timed-out reply.",
      speechContent: "Read aloud",
      speechQuestion: "Question",
      speechOptions: "Options",
      speechAnswer: "Answer and explanation (after a wrong or timed-out reply)",
      speechKeepHint: "Turning this off keeps your selections for next time.",
      speechModelHint: "Voice uses the DeepSeek speech endpoint and the stella voice, not the chat model above; only the key is shared.",
      speechSaveToApply: "Takes effect after you save the AI settings.",
      speechBillingHint: "Uses the DeepSeek key above and DeepSeek speech; question, options, answer, and explanation text are sent to DeepSeek and billed by character. A speech failure never blocks the quiz.",
      speechSelectAtLeastOne: "Select at least one item to read when voice is on.",
      err_INVALID_SPEECH_SETTINGS: "The voice settings could not be read. Reopen the settings.",
      err_SPEECH_CONTENT_REQUIRED: "Select at least one item to read.",
    },
    ru: {
      speechTitle: "Озвучивать вопросы",
      speechHint: "Вопрос читается автоматически; ответ и решение — только после неверного ответа или тайм-аута.",
      speechContent: "Что читать",
      speechQuestion: "Вопрос",
      speechOptions: "Варианты",
      speechAnswer: "Ответ и решение (после ошибки или тайм-аута)",
      speechKeepHint: "При выключении выбранные пункты сохраняются.",
      speechModelHint: "Озвучивание использует голосовой интерфейс DeepSeek и голос stella, а не модель чата выше; общий только ключ.",
      speechSaveToApply: "Вступит в силу после сохранения настроек ИИ.",
      speechBillingHint: "Использует ключ DeepSeek выше и синтез речи DeepSeek; текст вопроса, вариантов, ответа и решения отправляется в DeepSeek и оплачивается по символам. Сбой озвучивания не мешает ответу.",
      speechSelectAtLeastOne: "Выберите хотя бы один пункт для озвучивания.",
      err_INVALID_SPEECH_SETTINGS: "Не удалось прочитать настройки озвучивания. Откройте настройки снова.",
      err_SPEECH_CONTENT_REQUIRED: "Выберите хотя бы один пункт для озвучивания.",
    },
    uk: {
      speechTitle: "Озвучувати запитання",
      speechHint: "Запитання читається автоматично; відповідь і рішення — лише після помилки або тайм-ауту.",
      speechContent: "Що озвучувати",
      speechQuestion: "Запитання",
      speechOptions: "Варіанти",
      speechAnswer: "Відповідь і рішення (після помилки або тайм-ауту)",
      speechKeepHint: "Вимкнення зберігає вибрані пункти для наступного разу.",
      speechModelHint: "Озвучення використовує мовний інтерфейс DeepSeek і голос stella, а не модель чату вище; спільним є лише ключ.",
      speechSaveToApply: "Набуде чинності після збереження налаштувань ШІ.",
      speechBillingHint: "Використовує ключ DeepSeek вище та синтез мовлення DeepSeek; текст запитання, варіантів, відповіді та рішення надсилається до DeepSeek і оплачується за символами. Збій озвучення не заважає відповідати.",
      speechSelectAtLeastOne: "Виберіть принаймні один пункт для озвучення.",
      err_INVALID_SPEECH_SETTINGS: "Не вдалося прочитати налаштування озвучення. Відкрийте налаштування знову.",
      err_SPEECH_CONTENT_REQUIRED: "Виберіть принаймні один пункт для озвучення.",
    },
    kk: {
      speechTitle: "Сұрақтарды дауыстап оқу",
      speechHint: "Сұрақ автоматты түрде оқылады; жауап пен шешу жолы қате немесе уақыт біткеннен кейін ғана оқылады.",
      speechContent: "Дауыстап оқылатындар",
      speechQuestion: "Сұрақ",
      speechOptions: "Нұсқалар",
      speechAnswer: "Жауап және шешу жолы (қате немесе уақыт біткеннен кейін)",
      speechKeepHint: "Өшіргенде таңдаулар сақталады.",
      speechModelHint: "Дауыстап оқу DeepSeek дауыс интерфейсін және stella дауысын пайдаланады, жоғарыдағы чат моделін емес; ортақ тек кілт.",
      speechSaveToApply: "AI параметрлерін сақтағаннан кейін күшіне енеді.",
      speechBillingHint: "Жоғарыдағы DeepSeek кілтін және DeepSeek дауыс қызметін пайдаланады; сұрақ, нұсқалар, жауап және шешу жолы мәтіні DeepSeek-ке жіберіліп, таңба бойынша төленеді. Дауыс қатесі жауап беруге кедергі келмейді.",
      speechSelectAtLeastOne: "Дауыс қосулы кезде кемінде бір элементті таңдаңыз.",
      err_INVALID_SPEECH_SETTINGS: "Дауыс параметрлерін оқу мүмкін болмады. Параметрлерді қайта ашыңыз.",
      err_SPEECH_CONTENT_REQUIRED: "Кемінде бір дауыстап оқылатын элементті таңдаңыз.",
    },
  };

  // V37: one level is a package of ordered question sources.
  const V36_TEXTS = {
    zh: {
      levelAddPackageTitle: "添加关卡",
      levelAddPackageMeta: "一个关卡可组合预置孩子题库、预置家长题库、AI 题、手动导入和错题",
      levelSourcesTitle: "题目来源",
      levelSourcesLead: "答题时从每个来源轮流抽题；某个来源用完就跳过，直到抽满一轮。可继续添加来源，点底部「保存关卡」后才会生成关卡。",
      levelDraftBadge: "待保存",
      levelSourceIndex: "来源 {n}",
      levelSourceCourse: "教材题目",
      levelSourceCourseMeta: "按年级、学期、学科和单元选择",
      levelSourcePreset: "预置孩子题库",
      levelSourcePresetMeta: "电视自带 · 全员共享",
      levelSourceAi: "AI生成",
      levelSourceAiMeta: "当前成员生成",
      levelSourceLegacy: "教材题目（预置 + AI）",
      levelSourcePresetTag: "预置",
      levelSourceAiTag: "AI",
      levelSourceLegacyTag: "预置 + AI",
      levelSourceCustom: "手动导入",
      levelSourceCustomMeta: "导入时创建的标签",
      levelSourceWrong: "错题复习",
      levelSourceWrongMeta: "自动收录本机错题",
      levelSourceLife: "预置家长题库",
      levelSourceLifeMeta: "生活常识 · 单选与多选",
      levelSourceLifeCount: "趣味题 {n} 道",
      levelSourceDuplicatePreset: "预置家长题库已在关卡中。",
      levelTypeFilterTitle: "题目类型",
      levelTypeAny: "混合",
      levelTypeSingle: "单选",
      levelTypeMultiple: "多选",
      levelTypeCountsAll: "共 {total} 道 · 单选 {single} · 多选 {multiple}",
      levelTypeCountsWith: "{kind} {n} 道 · 共 {total} 道",
      levelTypeEmpty: "当前范围没有{kind}题，请换一个范围或类型。",
      levelSourceSave: "保存来源",
      levelSourceLimit: "每个关卡最多 {n} 个来源。",
      levelSourceDuplicateCourse: "这个范围、类型和来源已在关卡中。",
      levelSourceDuplicateWrong: "错题复习已在关卡中。",
      levelSourceMissingTag: "请选择一个标签。",
      levelSourcesCount: "{n}/{max} 个来源",
      levelSourcesTotal: "总题量 {n} 道",
      levelSourceAvailable: "可用 {n} 道",
      levelSourceBankCount: "题库 {n} 道",
      levelSourceWrongCount: "错题 {n} 道",
      levelSourceTotalAvailable: "共 {total} 道 · 可用 {available} 道",
      levelWrongIncluded: "含错题 {n} 道",
      levelAtLeastOneSource: "至少添加一个来源才能保存关卡。",
      levelPackageHint: "每个来源每轮抽一道题，来源之间轮流出现。",
      levelSourceAdded: "来源已加入关卡，可继续添加或保存关卡。",
      levelSourcesAdded: "已加入 {n} 个来源，可继续添加或保存关卡。",
      levelTagSourceTitle: "手动导入的标签",
      levelTagSourceLead: "选择一个或多个标签加入关卡，然后用底部按钮返回关卡页；只有点「保存关卡」才会真正保存。",
      levelTagSourceSelected: "已选 {n} 个",
      levelTagSourceAdd: "加入关卡（{n}）",
      levelTagSourceAlreadyAdded: "已在关卡中",
      levelTagSourceRemaining: "还可加入 {n} 个来源",
      levelSourceUpdated: "来源已更新。",
      levelPackageFallbackName: "关卡 {n}",
      levelPackagesUnreadable: "电视上的关卡数据无法读取，请重新打开家长设置。",
      levelModelUnsupported: "电视版本不支持新的关卡结构，请刷新页面。",
      err_LEVEL_MODEL_UPGRADED: "电视已升级关卡结构，请刷新页面后重试。",
      err_LEVEL_MODEL_UNREADABLE: "关卡数据无法读取，请在电视上重新保存。",
      err_INVALID_LEVEL_PACKAGES: "关卡内容不符合要求，请检查后重试。",
      err_TOO_MANY_LEVELS: "关卡数量已达上限。",
      err_LEVEL_SOURCE_LIMIT: "单个关卡的题目来源太多。",
      err_INVALID_LEVEL_SOURCE: "题目来源无效，请重新选择。",
    },
    en: {
      levelAddPackageTitle: "Add level",
      levelAddPackageMeta: "Combine the preset bank for kids, the preset bank for parents, AI questions, imports, and mistakes in one level",
      levelSourcesTitle: "Question sources",
      levelSourcesLead: "Questions rotate between sources. A source that runs out is skipped until the round is full. Add as many sources as you need; the level is created only after you tap Save level.",
      levelDraftBadge: "Unsaved",
      levelSourceIndex: "Source {n}",
      levelSourceCourse: "Textbook questions",
      levelSourceCourseMeta: "Pick grade, semester, subject, and unit",
      levelSourcePreset: "Preset bank for kids",
      levelSourcePresetMeta: "Bundled · shared by all",
      levelSourceAi: "AI questions",
      levelSourceAiMeta: "Made by this member",
      levelSourceLegacy: "Textbook (preset + AI)",
      levelSourcePresetTag: "Preset",
      levelSourceAiTag: "AI",
      levelSourceLegacyTag: "Preset + AI",
      levelSourceCustom: "Manual import",
      levelSourceCustomMeta: "Tags from imports",
      levelSourceWrong: "Wrong review",
      levelSourceWrongMeta: "All mistakes here",
      levelSourceLife: "Preset bank for parents",
      levelSourceLifeMeta: "Everyday life · single & multiple",
      levelSourceLifeCount: "{n} life questions",
      levelSourceDuplicatePreset: "The preset bank for parents is already in the level.",
      levelTypeFilterTitle: "Question type",
      levelTypeAny: "Mixed",
      levelTypeSingle: "Single choice",
      levelTypeMultiple: "Multiple choice",
      levelTypeCountsAll: "{total} total · {single} single · {multiple} multiple",
      levelTypeCountsWith: "{kind}: {n} · {total} total",
      levelTypeEmpty: "No {kind} questions in this range. Choose another range or type.",
      levelSourceSave: "Save source",
      levelSourceLimit: "A level can hold at most {n} sources.",
      levelSourceDuplicateCourse: "This range, type, and source are already in the level.",
      levelSourceDuplicateWrong: "Wrong review is already in the level.",
      levelSourceMissingTag: "Choose a tag.",
      levelSourcesCount: "{n}/{max} sources",
      levelSourcesTotal: "{n} questions total",
      levelSourceAvailable: "{n} available",
      levelSourceBankCount: "{n} in bank",
      levelSourceWrongCount: "{n} wrong",
      levelSourceTotalAvailable: "{total} total · {available} available",
      levelWrongIncluded: "includes {n} wrong",
      levelAtLeastOneSource: "Add at least one source before saving the level.",
      levelPackageHint: "Each source contributes one question per cycle, rotating between sources.",
      levelSourceAdded: "Source added. Add more sources or save the level.",
      levelSourcesAdded: "{n} sources added. Add more or save the level.",
      levelTagSourceTitle: "Imported tags",
      levelTagSourceLead: "Choose one or more tags for this level, then return with the button below. The level is written only when you tap Save level.",
      levelTagSourceSelected: "{n} selected",
      levelTagSourceAdd: "Add to level ({n})",
      levelTagSourceAlreadyAdded: "Already in level",
      levelTagSourceRemaining: "{n} more sources can be added",
      levelSourceUpdated: "Source updated.",
      levelPackageFallbackName: "Level {n}",
      levelPackagesUnreadable: "The level data on this TV could not be read. Reopen parent settings.",
      levelModelUnsupported: "This TV does not support the new level structure. Refresh the page.",
      err_LEVEL_MODEL_UPGRADED: "The TV upgraded its level structure. Refresh the page and try again.",
      err_LEVEL_MODEL_UNREADABLE: "The level data could not be read. Save it again on the TV.",
      err_INVALID_LEVEL_PACKAGES: "The level contents are not valid. Check them and try again.",
      err_TOO_MANY_LEVELS: "The level limit has been reached.",
      err_LEVEL_SOURCE_LIMIT: "This level has too many question sources.",
      err_INVALID_LEVEL_SOURCE: "A question source is invalid. Choose it again.",
    },
    ru: {
      levelAddPackageTitle: "Добавить уровень",
      levelAddPackageMeta: "Объедините готовый банк для детей, готовый банк для родителей, вопросы ИИ, ручной импорт и ошибки в одном уровне",
      levelSourcesTitle: "Источники вопросов",
      levelSourcesLead: "Вопросы чередуются между источниками. Исчерпанный источник пропускается до конца раунда. Добавляйте источники по одному: уровень создаётся только после нажатия «Сохранить уровень».",
      levelDraftBadge: "Не сохранено",
      levelSourceIndex: "Источник {n}",
      levelSourceCourse: "Вопросы учебника",
      levelSourceCourseMeta: "Выберите класс, полугодие, предмет и раздел",
      levelSourcePreset: "Готовый банк для детей",
      levelSourcePresetMeta: "Встроены · общие для всех",
      levelSourceAi: "Вопросы ИИ",
      levelSourceAiMeta: "Создано этим участником",
      levelSourceLegacy: "Учебник (готовые + ИИ)",
      levelSourcePresetTag: "Готовые",
      levelSourceAiTag: "ИИ",
      levelSourceLegacyTag: "Готовые + ИИ",
      levelSourceCustom: "Ручной импорт",
      levelSourceCustomMeta: "Теги из импорта",
      levelSourceWrong: "Повторение ошибок",
      levelSourceWrongMeta: "Все ошибки этого ТВ",
      levelSourceLife: "Готовый банк для родителей",
      levelSourceLifeMeta: "Быт и здоровье · один или несколько ответов",
      levelSourceLifeCount: "Бытовых вопросов: {n}",
      levelSourceDuplicatePreset: "Готовый банк для родителей уже есть в уровне.",
      levelTypeFilterTitle: "Тип вопроса",
      levelTypeAny: "Смешанные",
      levelTypeSingle: "Один ответ",
      levelTypeMultiple: "Несколько ответов",
      levelTypeCountsAll: "Всего {total} · один ответ {single} · несколько {multiple}",
      levelTypeCountsWith: "{kind}: {n} · всего {total}",
      levelTypeEmpty: "В этом диапазоне нет вопросов типа «{kind}». Выберите другой диапазон или тип.",
      levelSourceSave: "Сохранить источник",
      levelSourceLimit: "В уровне может быть не более {n} источников.",
      levelSourceDuplicateCourse: "Этот диапазон, тип и источник уже есть в уровне.",
      levelSourceDuplicateWrong: "Повторение ошибок уже есть в уровне.",
      levelSourceMissingTag: "Выберите тег.",
      levelSourcesCount: "Источников: {n}/{max}",
      levelSourcesTotal: "Всего вопросов: {n}",
      levelSourceAvailable: "Доступно: {n}",
      levelSourceBankCount: "В банке: {n}",
      levelSourceWrongCount: "Ошибок: {n}",
      levelSourceTotalAvailable: "Всего {total} · доступно {available}",
      levelWrongIncluded: "включая ошибок: {n}",
      levelAtLeastOneSource: "Добавьте хотя бы один источник, чтобы сохранить уровень.",
      levelPackageHint: "Каждый источник даёт один вопрос за цикл, источники чередуются.",
      levelSourceAdded: "Источник добавлен. Добавьте ещё или сохраните уровень.",
      levelSourcesAdded: "Добавлено источников: {n}. Добавьте ещё или сохраните уровень.",
      levelTagSourceTitle: "Теги ручного импорта",
      levelTagSourceLead: "Выберите один или несколько тегов для уровня и вернитесь кнопкой ниже. Уровень сохраняется только по кнопке «Сохранить уровень».",
      levelTagSourceSelected: "Выбрано: {n}",
      levelTagSourceAdd: "Добавить в уровень ({n})",
      levelTagSourceAlreadyAdded: "Уже в уровне",
      levelTagSourceRemaining: "Можно добавить ещё источников: {n}",
      levelSourceUpdated: "Источник обновлён.",
      levelPackageFallbackName: "Уровень {n}",
      levelPackagesUnreadable: "Не удалось прочитать данные уровней на ТВ. Откройте родительские настройки снова.",
      levelModelUnsupported: "Этот ТВ не поддерживает новую структуру уровней. Обновите страницу.",
      err_LEVEL_MODEL_UPGRADED: "ТВ обновил структуру уровней. Обновите страницу и повторите.",
      err_LEVEL_MODEL_UNREADABLE: "Не удалось прочитать данные уровней. Сохраните их снова на ТВ.",
      err_INVALID_LEVEL_PACKAGES: "Содержимое уровней недопустимо. Проверьте его и повторите.",
      err_TOO_MANY_LEVELS: "Достигнут предел числа уровней.",
      err_LEVEL_SOURCE_LIMIT: "В уровне слишком много источников вопросов.",
      err_INVALID_LEVEL_SOURCE: "Источник вопросов недопустим. Выберите его снова.",
    },
    uk: {
      levelAddPackageTitle: "Додати рівень",
      levelAddPackageMeta: "Поєднайте готовий банк для дітей, готовий банк для батьків, питання ШІ, ручний імпорт і помилки в одному рівні",
      levelSourcesTitle: "Джерела питань",
      levelSourcesLead: "Питання чергуються між джерелами. Вичерпане джерело пропускається до кінця раунду. Додавайте джерела по одному: рівень створюється лише після натискання «Зберегти рівень».",
      levelDraftBadge: "Не збережено",
      levelSourceIndex: "Джерело {n}",
      levelSourceCourse: "Питання підручника",
      levelSourceCourseMeta: "Виберіть клас, семестр, предмет і розділ",
      levelSourcePreset: "Готовий банк для дітей",
      levelSourcePresetMeta: "Вбудовані · спільні для всіх",
      levelSourceAi: "Питання ШІ",
      levelSourceAiMeta: "Створено цим учасником",
      levelSourceLegacy: "Підручник (готові + ШІ)",
      levelSourcePresetTag: "Готові",
      levelSourceAiTag: "ШІ",
      levelSourceLegacyTag: "Готові + ШІ",
      levelSourceCustom: "Ручне імпортування",
      levelSourceCustomMeta: "Теги з імпорту",
      levelSourceWrong: "Повторення помилок",
      levelSourceWrongMeta: "Усі помилки цього ТБ",
      levelSourceLife: "Готовий банк для батьків",
      levelSourceLifeMeta: "Побут і здоров'я · одна або кілька відповідей",
      levelSourceLifeCount: "Побутових питань: {n}",
      levelSourceDuplicatePreset: "Готовий банк для батьків уже є в рівні.",
      levelTypeFilterTitle: "Тип питання",
      levelTypeAny: "Змішані",
      levelTypeSingle: "Одна відповідь",
      levelTypeMultiple: "Кілька відповідей",
      levelTypeCountsAll: "Усього {total} · одна відповідь {single} · кілька {multiple}",
      levelTypeCountsWith: "{kind}: {n} · усього {total}",
      levelTypeEmpty: "У цьому діапазоні немає питань типу «{kind}». Виберіть інший діапазон або тип.",
      levelSourceSave: "Зберегти джерело",
      levelSourceLimit: "У рівні може бути не більше {n} джерел.",
      levelSourceDuplicateCourse: "Цей діапазон, тип і джерело вже є в рівні.",
      levelSourceDuplicateWrong: "Повторення помилок уже є в рівні.",
      levelSourceMissingTag: "Виберіть тег.",
      levelSourcesCount: "Джерел: {n}/{max}",
      levelSourcesTotal: "Усього питань: {n}",
      levelSourceAvailable: "Доступно: {n}",
      levelSourceBankCount: "У банку: {n}",
      levelSourceWrongCount: "Помилок: {n}",
      levelSourceTotalAvailable: "Усього {total} · доступно {available}",
      levelWrongIncluded: "включно з помилками: {n}",
      levelAtLeastOneSource: "Додайте принаймні одне джерело, щоб зберегти рівень.",
      levelPackageHint: "Кожне джерело дає одне питання за цикл, джерела чергуються.",
      levelSourceAdded: "Джерело додано. Додайте ще або збережіть рівень.",
      levelSourcesAdded: "Додано джерел: {n}. Додайте ще або збережіть рівень.",
      levelTagSourceTitle: "Теги ручного імпорту",
      levelTagSourceLead: "Виберіть один або кілька тегів для рівня та поверніться кнопкою нижче. Рівень зберігається лише за кнопкою «Зберегти рівень».",
      levelTagSourceSelected: "Вибрано: {n}",
      levelTagSourceAdd: "Додати до рівня ({n})",
      levelTagSourceAlreadyAdded: "Уже в рівні",
      levelTagSourceRemaining: "Ще можна додати джерел: {n}",
      levelSourceUpdated: "Джерело оновлено.",
      levelPackageFallbackName: "Рівень {n}",
      levelPackagesUnreadable: "Не вдалося прочитати дані рівнів на ТБ. Відкрийте батьківські налаштування знову.",
      levelModelUnsupported: "Цей ТБ не підтримує нову структуру рівнів. Оновіть сторінку.",
      err_LEVEL_MODEL_UPGRADED: "ТБ оновив структуру рівнів. Оновіть сторінку та повторіть.",
      err_LEVEL_MODEL_UNREADABLE: "Не вдалося прочитати дані рівнів. Збережіть їх знову на ТБ.",
      err_INVALID_LEVEL_PACKAGES: "Вміст рівнів недійсний. Перевірте його та повторіть.",
      err_TOO_MANY_LEVELS: "Досягнуто межі кількості рівнів.",
      err_LEVEL_SOURCE_LIMIT: "У рівні забагато джерел питань.",
      err_INVALID_LEVEL_SOURCE: "Джерело питань недійсне. Виберіть його знову.",
    },
    kk: {
      levelAddPackageTitle: "Деңгей қосу",
      levelAddPackageMeta: "Балаларға арналған дайын банкіні, ата-аналарға арналған дайын банкіні, ЖИ сұрақтарын, қолмен импортты және қателерді бір деңгейге біріктіріңіз",
      levelSourcesTitle: "Сұрақ көздері",
      levelSourcesLead: "Сұрақтар көздер арасында кезектеседі. Таусылған көз айналым толғанша өткізіледі. Көздерді біртіндеп қосыңыз: деңгей «Деңгейді сақтау» түймесін басқаннан кейін ғана жасалады.",
      levelDraftBadge: "Сақталмаған",
      levelSourceIndex: "Көз {n}",
      levelSourceCourse: "Оқулық сұрақтары",
      levelSourceCourseMeta: "Сынып, семестр, пән және тарауды таңдаңыз",
      levelSourcePreset: "Балаларға арналған дайын банк",
      levelSourcePresetMeta: "Кіріктірілген · ортақ",
      levelSourceAi: "ЖИ сұрақтары",
      levelSourceAiMeta: "Осы қатысушы жасаған",
      levelSourceLegacy: "Оқулық (дайын + ЖИ)",
      levelSourcePresetTag: "Дайын",
      levelSourceAiTag: "ЖИ",
      levelSourceLegacyTag: "Дайын + ЖИ",
      levelSourceCustom: "Қолмен импорттау",
      levelSourceCustomMeta: "Импорттағы тегтер",
      levelSourceWrong: "Қателерді қайталау",
      levelSourceWrongMeta: "Осы ТВ қателері",
      levelSourceLife: "Ата-аналарға арналған дайын банк",
      levelSourceLifeMeta: "Тұрмыс және денсаулық · бір немесе бірнеше жауап",
      levelSourceLifeCount: "Тұрмыстық сұрақ: {n}",
      levelSourceDuplicatePreset: "Ата-аналарға арналған дайын банк деңгейде бар.",
      levelTypeFilterTitle: "Сұрақ түрі",
      levelTypeAny: "Аралас",
      levelTypeSingle: "Бір жауап",
      levelTypeMultiple: "Бірнеше жауап",
      levelTypeCountsAll: "Барлығы {total} · бір жауап {single} · бірнеше {multiple}",
      levelTypeCountsWith: "{kind}: {n} · барлығы {total}",
      levelTypeEmpty: "Бұл ауқымда «{kind}» сұрақтары жоқ. Басқа ауқым немесе түр таңдаңыз.",
      levelSourceSave: "Көзді сақтау",
      levelSourceLimit: "Деңгейде ең көбі {n} көз болуы мүмкін.",
      levelSourceDuplicateCourse: "Бұл ауқым, түр және дереккөз деңгейде бар.",
      levelSourceDuplicateWrong: "Қателерді қайталау деңгейде бар.",
      levelSourceMissingTag: "Тег таңдаңыз.",
      levelSourcesCount: "Көздер: {n}/{max}",
      levelSourcesTotal: "Барлығы сұрақ: {n}",
      levelSourceAvailable: "Қолжетімді: {n}",
      levelSourceBankCount: "Банкте: {n}",
      levelSourceWrongCount: "Қателер: {n}",
      levelSourceTotalAvailable: "Барлығы {total} · қолжетімді {available}",
      levelWrongIncluded: "қателермен қоса: {n}",
      levelAtLeastOneSource: "Деңгейді сақтау үшін кемінде бір көз қосыңыз.",
      levelPackageHint: "Әр көз цикл сайын бір сұрақ береді, көздер кезектеседі.",
      levelSourceAdded: "Көз қосылды. Тағы қосыңыз немесе деңгейді сақтаңыз.",
      levelSourcesAdded: "{n} көз қосылды. Тағы қосыңыз немесе деңгейді сақтаңыз.",
      levelTagSourceTitle: "Қолмен импортталған тегтер",
      levelTagSourceLead: "Деңгейге бір немесе бірнеше тег таңдап, төмендегі түймемен оралыңыз. Деңгей тек «Деңгейді сақтау» түймесін басқанда жазылады.",
      levelTagSourceSelected: "Таңдалды: {n}",
      levelTagSourceAdd: "Деңгейге қосу ({n})",
      levelTagSourceAlreadyAdded: "Деңгейде бар",
      levelTagSourceRemaining: "Тағы {n} көз қосуға болады",
      levelSourceUpdated: "Көз жаңартылды.",
      levelPackageFallbackName: "Деңгей {n}",
      levelPackagesUnreadable: "ТВ-дегі деңгей деректерін оқу мүмкін болмады. Ата-ана параметрлерін қайта ашыңыз.",
      levelModelUnsupported: "Бұл ТВ жаңа деңгей құрылымын қолдамайды. Бетті жаңартыңыз.",
      err_LEVEL_MODEL_UPGRADED: "ТВ деңгей құрылымын жаңартты. Бетті жаңартып, қайталаңыз.",
      err_LEVEL_MODEL_UNREADABLE: "Деңгей деректерін оқу мүмкін болмады. Оларды ТВ-де қайта сақтаңыз.",
      err_INVALID_LEVEL_PACKAGES: "Деңгей мазмұны жарамсыз. Тексеріп, қайталаңыз.",
      err_TOO_MANY_LEVELS: "Деңгей санының шегіне жеттіңіз.",
      err_LEVEL_SOURCE_LIMIT: "Деңгейде тым көп сұрақ көзі бар.",
      err_INVALID_LEVEL_SOURCE: "Сұрақ көзі жарамсыз. Оны қайта таңдаңыз.",
    },
  };

  // V38: each saved level can be paused without deleting its sources.
  const V37_TEXTS = {
    zh: {
      levelEnabledLabel: "启用",
      levelPausedLabel: "已暂停",
      levelToggleOnAria: "暂停关卡：{name}",
      levelToggleOffAria: "启用关卡：{name}",
      levelPausedHint: "已暂停，答题闯关中不显示",
      levelPausedToast: "已暂停关卡「{name}」",
      levelEnabledToast: "已启用关卡「{name}」",
    },
    en: {
      levelEnabledLabel: "Enabled",
      levelPausedLabel: "Paused",
      levelToggleOnAria: "Pause level: {name}",
      levelToggleOffAria: "Enable level: {name}",
      levelPausedHint: "Paused — hidden from the answer challenge",
      levelPausedToast: "Paused level “{name}”",
      levelEnabledToast: "Enabled level “{name}”",
    },
    ru: {
      levelEnabledLabel: "Включён",
      levelPausedLabel: "Приостановлен",
      levelToggleOnAria: "Приостановить уровень: {name}",
      levelToggleOffAria: "Включить уровень: {name}",
      levelPausedHint: "Приостановлен — скрыт в задании",
      levelPausedToast: "Уровень «{name}» приостановлен",
      levelEnabledToast: "Уровень «{name}» включён",
    },
    uk: {
      levelEnabledLabel: "Увімкнено",
      levelPausedLabel: "Призупинено",
      levelToggleOnAria: "Призупинити рівень: {name}",
      levelToggleOffAria: "Увімкнути рівень: {name}",
      levelPausedHint: "Призупинено — приховано в завданні",
      levelPausedToast: "Рівень «{name}» призупинено",
      levelEnabledToast: "Рівень «{name}» увімкнено",
    },
    kk: {
      levelEnabledLabel: "Қосулы",
      levelPausedLabel: "Кідіртілді",
      levelToggleOnAria: "Деңгейді кідірту: {name}",
      levelToggleOffAria: "Деңгейді қосу: {name}",
      levelPausedHint: "Кідіртілді — жауап тапсырмасында көрсетілмейді",
      levelPausedToast: "«{name}» деңгейі кідіртілді",
      levelEnabledToast: "«{name}» деңгейі қосылды",
    },
  };

  // V41: four-nav labels, the editing-member picker, and the four bank
  // subpages. Built-in content is public; AI, imports and mistakes belong to
  // one member and must say whose they are.
  const V41_TEXTS = {
    zh: {
      tabUser: "用户配置",
      tabSystem: "系统配置",
      editingSubjectLabel: "编辑对象",
      subjectDialogTitle: "选择编辑对象",
      subjectDialogLead: "只切换网页正在编辑的成员；电视仍由当前成员使用。",
      subjectDialogHint: "要让电视也切换，请到「用户配置 → 切换到当前用户」。",
      subjectCurrentTag: "电视当前",
      subjectDefaultTag: "默认成员",
      subjectSwitchBlocked: "正在生成或导入时不能切换成员，请先完成或停止。",
      subjectNoMembers: "暂无家庭成员。",
      memberHeaderSubtitle: "{member} · 电视当前：{name}",
      bankScopeBuiltin: "预置题库",
      bankScopeAi: "AI生成",
      bankScopeManual: "手动导入",
      bankScopeWrong: "错题",
      bankScopeSummaryBuiltin: "公共内容 · 共 {total} 道",
      bankScopeSummaryAi: "{name} 的 AI 题 · 共 {total} 道",
      bankScopeSummaryManual: "{name} 的手动导入 · 共 {total} 道 · {tags} 个标签",
      bankScopeSummaryWrong: "{name} 的错题本 · {n} 道待复习",
      bankScopeClear: "清空",
      bankScopeTagAll: "全部标签",
      bankPrivateCapacity: "私有题 {used}/{capacity} · 剩余 {remaining}",
      bankRuleBuiltin: "预置题库是公共内容，只能整体清空。",
      bankManageTitleBuiltin: "预置题库",
      bankManageTitleManual: "手动导入",
      bankManageLeadBuiltin: "删除所有预置题；不影响 AI 生成和手动导入。",
      bankManageLeadManual: "删除 {name} 的手动导入；可按标签清理。",
      bankManageBuiltinMeta: "共 {n} 道预置题",
      bankManageManualMeta: "共 {n} 道 · {tags} 个标签",
      bankManageBuiltinTitle: "清空预置题库",
      bankManageBuiltinLead: "清除所有成员共享的预置题。此操作不能撤销。",
      bankManageBuiltinClear: "清空预置题库",
      bankManageManualTitle: "手动导入标签",
      bankManageManualLead: "这些标签只属于 {name}，其他成员看不到。",
      bankManageManualClear: "清空全部手动导入",
      bankManageTagEmpty: "还没有手动导入的标签。",
      bankManageTagMeta: "{n} 道 · 未使用 {unused} 道",
      bankManageTagClear: "清空",
      bankEmptyBuiltin: "预置题库还没有题目。",
      bankWrongRemove: "移出错题本",
      bankWrongRemoveTitle: "移出错题本",
      bankWrongRemoveLead: "只清除这道题的错题状态，不删除题目。",
      clearWrongOneDone: "已移出错题本。",
      bankClearScopeBuiltin: "预置题库",
      bankClearScopeAi: "AI 题库",
      bankClearScopeManual: "手动导入",
      bankClearScopeManualTag: "手动导入「{name}」",
      bankClearScopeWrong: "错题本",
      bankClearBuiltinDetail: "将清空预置题库中的 {n} 道公共题。",
      bankClearAiDetail: "将清空 {name} 的 {n} 道 AI 题。",
      bankClearManualDetail: "将清空 {name} 的 {n} 道手动导入。",
      bankClearWrongDetail: "将清除 {n} 道题的错题状态，题目保留。",
      bankClearWrongOneDetail: "只清除这道题的错题状态，题目保留。",
      err_INVALID_SUBJECT: "成员不存在或已失效，请重新选择。",
      err_SCOPE_REQUIRED: "请指定要清理的题库分类。",
    },
    en: {
      tabUser: "Users",
      tabSystem: "System",
      editingSubjectLabel: "Editing",
      subjectDialogTitle: "Choose who to edit",
      subjectDialogLead: "Only changes which member this page edits. The TV stays with its current member.",
      subjectDialogHint: "To switch the TV too, use Users → Switch to this user.",
      subjectCurrentTag: "On TV",
      subjectDefaultTag: "Default member",
      subjectSwitchBlocked: "Finish or stop the current generation/import before switching members.",
      subjectNoMembers: "No family members yet.",
      memberHeaderSubtitle: "{member} · On TV: {name}",
      bankScopeBuiltin: "Preset",
      bankScopeAi: "AI",
      bankScopeManual: "Imported",
      bankScopeWrong: "Mistakes",
      bankScopeSummaryBuiltin: "Public content · {total} questions",
      bankScopeSummaryAi: "{name}'s AI questions · {total}",
      bankScopeSummaryManual: "{name}'s imports · {total} · {tags} tags",
      bankScopeSummaryWrong: "{name}'s mistakes · {n} to review",
      bankScopeClear: "Clear",
      bankScopeTagAll: "All tags",
      bankPrivateCapacity: "Private {used}/{capacity} · {remaining} left",
      bankRuleBuiltin: "Preset questions are shared; only a whole clear is allowed.",
      bankManageTitleBuiltin: "Preset bank",
      bankManageTitleManual: "Manual imports",
      bankManageLeadBuiltin: "Delete every preset question. AI and imported questions are not affected.",
      bankManageLeadManual: "Delete {name}'s imports; you can clear one tag at a time.",
      bankManageBuiltinMeta: "{n} preset questions",
      bankManageManualMeta: "{n} questions · {tags} tags",
      bankManageBuiltinTitle: "Clear preset bank",
      bankManageBuiltinLead: "Removes the preset questions shared by every member. This cannot be undone.",
      bankManageBuiltinClear: "Clear preset bank",
      bankManageManualTitle: "Import tags",
      bankManageManualLead: "These tags belong only to {name}; other members cannot see them.",
      bankManageManualClear: "Clear all imports",
      bankManageTagEmpty: "No import tags yet.",
      bankManageTagMeta: "{n} questions · {unused} unused",
      bankManageTagClear: "Clear",
      bankEmptyBuiltin: "The preset bank has no questions.",
      bankWrongRemove: "Remove from mistakes",
      bankWrongRemoveTitle: "Remove from mistakes",
      bankWrongRemoveLead: "Only this question's mistake state is cleared; the question stays.",
      clearWrongOneDone: "Removed from mistakes.",
      bankClearScopeBuiltin: "Preset bank",
      bankClearScopeAi: "AI bank",
      bankClearScopeManual: "Manual imports",
      bankClearScopeManualTag: "Import tag \"{name}\"",
      bankClearScopeWrong: "Mistakes",
      bankClearBuiltinDetail: "This clears {n} public preset questions.",
      bankClearAiDetail: "This clears {n} AI questions for {name}.",
      bankClearManualDetail: "This clears {n} manual imports for {name}.",
      bankClearWrongDetail: "This clears the mistake state of {n} questions; the questions stay.",
      bankClearWrongOneDetail: "Only this question's mistake state is cleared; the question stays.",
      err_INVALID_SUBJECT: "That member no longer exists. Choose another.",
      err_SCOPE_REQUIRED: "Choose which bank to clear.",
    },
    ru: {
      tabUser: "Пользователь",
      tabSystem: "Система",
      editingSubjectLabel: "Редактируется",
      subjectDialogTitle: "Кого редактировать",
      subjectDialogLead: "Меняется только объект редактирования страницы. Телевизор остаётся у текущего участника.",
      subjectDialogHint: "Чтобы переключить и телевизор, откройте «Пользователь → Переключить на этого пользователя».",
      subjectCurrentTag: "На ТВ",
      subjectDefaultTag: "По умолчанию",
      subjectSwitchBlocked: "Завершите или остановите генерацию/импорт, прежде чем менять участника.",
      subjectNoMembers: "В семье пока нет участников.",
      memberHeaderSubtitle: "{member} · Сейчас на ТВ: {name}",
      bankScopeBuiltin: "Готовые",
      bankScopeAi: "ИИ",
      bankScopeManual: "Импорт",
      bankScopeWrong: "Ошибки",
      bankScopeSummaryBuiltin: "Общие вопросы · {total}",
      bankScopeSummaryAi: "ИИ-вопросы: {name} · {total}",
      bankScopeSummaryManual: "Импорт: {name} · {total} · тегов {tags}",
      bankScopeSummaryWrong: "Ошибки: {name} · к повторению {n}",
      bankScopeClear: "Очистить",
      bankScopeTagAll: "Все теги",
      bankPrivateCapacity: "Личные {used}/{capacity} · осталось {remaining}",
      bankRuleBuiltin: "Готовые вопросы общие; можно только очистить весь банк.",
      bankManageTitleBuiltin: "Готовый банк",
      bankManageTitleManual: "Ручной импорт",
      bankManageLeadBuiltin: "Удалить все готовые вопросы. ИИ и импорт не затрагиваются.",
      bankManageLeadManual: "Удалить импорт участника {name}; можно очистить один тег.",
      bankManageBuiltinMeta: "Готовых вопросов: {n}",
      bankManageManualMeta: "Вопросов: {n} · тегов: {tags}",
      bankManageBuiltinTitle: "Очистить готовый банк",
      bankManageBuiltinLead: "Удаляет готовые вопросы, общие для всех. Отменить нельзя.",
      bankManageBuiltinClear: "Очистить готовый банк",
      bankManageManualTitle: "Теги импорта",
      bankManageManualLead: "Эти теги принадлежат только {name}; другие их не видят.",
      bankManageManualClear: "Очистить весь импорт",
      bankManageTagEmpty: "Тегов импорта пока нет.",
      bankManageTagMeta: "{n} вопросов · не использовано {unused}",
      bankManageTagClear: "Очистить",
      bankEmptyBuiltin: "В готовом банке нет вопросов.",
      bankWrongRemove: "Убрать из ошибок",
      bankWrongRemoveTitle: "Убрать из ошибок",
      bankWrongRemoveLead: "Сбрасывается только статус ошибки; вопрос остаётся.",
      clearWrongOneDone: "Убрано из ошибок.",
      bankClearScopeBuiltin: "Готовый банк",
      bankClearScopeAi: "Банк ИИ",
      bankClearScopeManual: "Ручной импорт",
      bankClearScopeManualTag: "Тег импорта «{name}»",
      bankClearScopeWrong: "Ошибки",
      bankClearBuiltinDetail: "Будет очищено {n} общих готовых вопросов.",
      bankClearAiDetail: "Будет очищено {n} ИИ-вопросов участника {name}.",
      bankClearManualDetail: "Будет очищено {n} импортированных вопросов участника {name}.",
      bankClearWrongDetail: "Будет сброшен статус ошибки у {n} вопросов; вопросы останутся.",
      bankClearWrongOneDetail: "Сбрасывается только статус ошибки; вопрос остаётся.",
      err_INVALID_SUBJECT: "Участник не найден. Выберите другого.",
      err_SCOPE_REQUIRED: "Укажите, какой банк очистить.",
    },
    uk: {
      tabUser: "Користувач",
      tabSystem: "Система",
      editingSubjectLabel: "Редагується",
      subjectDialogTitle: "Кого редагувати",
      subjectDialogLead: "Змінюється лише об'єкт редагування сторінки. Телевізор залишається в поточного учасника.",
      subjectDialogHint: "Щоб перемкнути й телевізор, відкрийте «Користувач → Перемкнути на цього користувача».",
      subjectCurrentTag: "На ТБ",
      subjectDefaultTag: "За замовчуванням",
      subjectSwitchBlocked: "Завершіть або зупиніть генерацію/імпорт, перш ніж змінювати учасника.",
      subjectNoMembers: "У сім'ї ще немає учасників.",
      memberHeaderSubtitle: "{member} · Зараз на ТБ: {name}",
      bankScopeBuiltin: "Готові",
      bankScopeAi: "ШІ",
      bankScopeManual: "Імпорт",
      bankScopeWrong: "Помилки",
      bankScopeSummaryBuiltin: "Спільні питання · {total}",
      bankScopeSummaryAi: "ШІ-питання: {name} · {total}",
      bankScopeSummaryManual: "Імпорт: {name} · {total} · тегів {tags}",
      bankScopeSummaryWrong: "Помилки: {name} · до повторення {n}",
      bankScopeClear: "Очистити",
      bankScopeTagAll: "Усі теги",
      bankPrivateCapacity: "Особисті {used}/{capacity} · залишилось {remaining}",
      bankRuleBuiltin: "Готові питання спільні; можна лише очистити весь банк.",
      bankManageTitleBuiltin: "Готовий банк",
      bankManageTitleManual: "Ручний імпорт",
      bankManageLeadBuiltin: "Видалити всі готові питання. ШІ та імпорт не змінюються.",
      bankManageLeadManual: "Видалити імпорт учасника {name}; можна очистити один тег.",
      bankManageBuiltinMeta: "Готових питань: {n}",
      bankManageManualMeta: "Питань: {n} · тегів: {tags}",
      bankManageBuiltinTitle: "Очистити готовий банк",
      bankManageBuiltinLead: "Видаляє готові питання, спільні для всіх. Скасувати не можна.",
      bankManageBuiltinClear: "Очистити готовий банк",
      bankManageManualTitle: "Теги імпорту",
      bankManageManualLead: "Ці теги належать лише {name}; інші їх не бачать.",
      bankManageManualClear: "Очистити весь імпорт",
      bankManageTagEmpty: "Тегів імпорту ще немає.",
      bankManageTagMeta: "{n} питань · не використано {unused}",
      bankManageTagClear: "Очистити",
      bankEmptyBuiltin: "У готовому банку немає питань.",
      bankWrongRemove: "Прибрати з помилок",
      bankWrongRemoveTitle: "Прибрати з помилок",
      bankWrongRemoveLead: "Скидається лише статус помилки; питання залишається.",
      clearWrongOneDone: "Прибрано з помилок.",
      bankClearScopeBuiltin: "Готовий банк",
      bankClearScopeAi: "Банк ШІ",
      bankClearScopeManual: "Ручний імпорт",
      bankClearScopeManualTag: "Тег імпорту «{name}»",
      bankClearScopeWrong: "Помилки",
      bankClearBuiltinDetail: "Буде очищено {n} спільних готових питань.",
      bankClearAiDetail: "Буде очищено {n} ШІ-питань учасника {name}.",
      bankClearManualDetail: "Буде очищено {n} імпортованих питань учасника {name}.",
      bankClearWrongDetail: "Буде скинуто статус помилки в {n} питань; питання залишаться.",
      bankClearWrongOneDetail: "Скидається лише статус помилки; питання залишається.",
      err_INVALID_SUBJECT: "Учасника не знайдено. Виберіть іншого.",
      err_SCOPE_REQUIRED: "Укажіть, який банк очистити.",
    },
    kk: {
      tabUser: "Пайдаланушы",
      tabSystem: "Жүйе",
      editingSubjectLabel: "Өңделуде",
      subjectDialogTitle: "Кімді өңдеу керек",
      subjectDialogLead: "Тек осы беттің өңдеу нысаны ауысады. Теледидар ағымдағы қатысушыда қалады.",
      subjectDialogHint: "Теледидарды да ауыстыру үшін «Пайдаланушы → Осы пайдаланушыға ауысу» бөлімін ашыңыз.",
      subjectCurrentTag: "ТД-де",
      subjectDefaultTag: "Әдепкі қатысушы",
      subjectSwitchBlocked: "Қатысушыны ауыстыру алдында генерацияны/импортты аяқтаңыз немесе тоқтатыңыз.",
      subjectNoMembers: "Отбасында әзірге қатысушы жоқ.",
      memberHeaderSubtitle: "{member} · Қазір ТВ-де: {name}",
      bankScopeBuiltin: "Дайын",
      bankScopeAi: "ИИ",
      bankScopeManual: "Импорт",
      bankScopeWrong: "Қателер",
      bankScopeSummaryBuiltin: "Ортақ сұрақтар · {total}",
      bankScopeSummaryAi: "ИИ сұрақтары: {name} · {total}",
      bankScopeSummaryManual: "Импорт: {name} · {total} · тег {tags}",
      bankScopeSummaryWrong: "Қателер: {name} · қайталауға {n}",
      bankScopeClear: "Тазалау",
      bankScopeTagAll: "Барлық тегтер",
      bankPrivateCapacity: "Жеке {used}/{capacity} · қалғаны {remaining}",
      bankRuleBuiltin: "Дайын сұрақтар ортақ; тек бүкіл банкті тазалауға болады.",
      bankManageTitleBuiltin: "Дайын банк",
      bankManageTitleManual: "Қолмен импорт",
      bankManageLeadBuiltin: "Барлық дайын сұрақтарды жою. ЖИ мен импортқа әсер етпейді.",
      bankManageLeadManual: "{name} импортын жою; бір тегті бөлек тазалауға болады.",
      bankManageBuiltinMeta: "Дайын сұрақ: {n}",
      bankManageManualMeta: "Сұрақ: {n} · тег: {tags}",
      bankManageBuiltinTitle: "Дайын банкті тазалау",
      bankManageBuiltinLead: "Барлық қатысушыға ортақ дайын сұрақтарды жояды. Қайтару мүмкін емес.",
      bankManageBuiltinClear: "Дайын банкті тазалау",
      bankManageManualTitle: "Импорт тегтері",
      bankManageManualLead: "Бұл тегтер тек {name} үшін; басқалар көрмейді.",
      bankManageManualClear: "Барлық импортты тазалау",
      bankManageTagEmpty: "Импорт тегтері әзірге жоқ.",
      bankManageTagMeta: "{n} сұрақ · пайдаланылмаған {unused}",
      bankManageTagClear: "Тазалау",
      bankEmptyBuiltin: "Дайын банкте сұрақ жоқ.",
      bankWrongRemove: "Қателерден алу",
      bankWrongRemoveTitle: "Қателерден алу",
      bankWrongRemoveLead: "Тек осы сұрақтың қате мәртебесі тазаланады; сұрақ қалады.",
      clearWrongOneDone: "Қателерден алынды.",
      bankClearScopeBuiltin: "Дайын банк",
      bankClearScopeAi: "ИИ банкі",
      bankClearScopeManual: "Қолмен импорт",
      bankClearScopeManualTag: "Импорт тегі «{name}»",
      bankClearScopeWrong: "Қателер",
      bankClearBuiltinDetail: "{n} ортақ дайын сұрақ тазаланады.",
      bankClearAiDetail: "{name} үшін {n} ИИ сұрағы тазаланады.",
      bankClearManualDetail: "{name} үшін {n} импортталған сұрақ тазаланады.",
      bankClearWrongDetail: "{n} сұрақтың қате мәртебесі тазаланады; сұрақтар қалады.",
      bankClearWrongOneDetail: "Тек осы сұрақтың қате мәртебесі тазаланады; сұрақ қалады.",
      err_INVALID_SUBJECT: "Қатысушы табылмады. Басқасын таңдаңыз.",
      err_SCOPE_REQUIRED: "Қай банкті тазалау керегін көрсетіңіз.",
    },
  };

  const LANGS = ["zh", "en", "ru", "uk", "kk"];
  const LANGUAGE_LABELS = {
    zh: "中文",
    en: "English",
    ru: "Русский",
    uk: "Українська",
    kk: "Қазақша",
  };
  for (const code of LANGS) {
    Object.assign(
      TEXTS[code],
      V17_TEXTS[code],
      V19_TEXTS[code],
      V20_TEXTS[code],
      V21_TEXTS[code],
      V22_TEXTS[code],
      V23_TEXTS[code],
      V25_TEXTS[code],
      V26_TEXTS[code],
      V28_TEXTS[code],
      V34_TEXTS[code],
      V35_TEXTS[code],
      V36_TEXTS[code],
      V37_TEXTS[code],
      V41_TEXTS[code],
    );
  }

  // Local days are normally 24h; allow the 25h DST fall-back day.
  const AUTH_MAX_TTL_MS = 25 * 60 * 60 * 1000;
  const AUTH_CLOCK_SKEW_MS = 30 * 60 * 1000;
  // Sent only while the page is visible. The TV releases the editing slot
  // after 10 seconds without one, so closing WeChat still frees it even when
  // the unload request cannot be delivered. Three missed beats release the
  // slot, so a single dropped request does not cut off an active page.
  const HEARTBEAT_MS = 3 * 1000;
  // Product default: a wrong answer's explanation stays at least 10 seconds.
  const FEEDBACK_HOLD_DEFAULT_SEC = 10;
  const DEFAULT_CONFIG = {
    defaultTimeLimitSec: 60,
    minutesPerPoint: 5,
    minCorrectCount: 1,
    maxEarnedMinutes: 60,
    answerDisplaySec: 3,
    feedbackExtraHoldSec: FEEDBACK_HOLD_DEFAULT_SEC,
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
  // Technical ceilings only. The UI does not present them as product limits:
  // they keep the session record and the seconds-based wire fields in range.
  const ROUND_MIN = 1;
  const ROUND_MAX = 1000;
  const ROUND_STEP = 5;
  const ANSWER_TIME_MIN_SEC = 5;
  const ANSWER_TIME_MAX_SEC = 30 * 60;
  const ANSWER_TIME_STEP_SEC = 5;
  // Minimum stay on wrong/timeout feedback. Correct answers never wait.
  const FEEDBACK_HOLD_MIN_SEC = 0;
  const FEEDBACK_HOLD_MAX_SEC = Math.floor(2147483647 / 1000);
  const FEEDBACK_HOLD_STEP_SEC = 1;
  // The TV stores the threshold in seconds; this keeps minutes * 60 in range.
  const UNLOCK_MAX_MINUTES = Math.floor(2147483647 / 60);
  const UNLOCK_STEP_MINUTES = 5;
  // Per-round reward cap. The stored config still uses 1..180 minutes.
  const MAX_EARNED_MIN_MINUTES = 1;
  const MAX_EARNED_MAX_MINUTES = 180;
  const MAX_EARNED_STEP_MINUTES = 5;
  const SCOPE_VALUES = ["unit", "midterm", "final", "review"];
  const REQUIREMENT_MAX_CHARS = 30;
  const GEN_OPTIONS_VALUES = [4, 6];
  const TARGET_COUNTS = [20, 30, 50];
  // A fresh TV opens the generation page on a ready-to-run demo scope instead of
  // an empty requirement, which would block generation until a parent types one.
  // The title matches unit 1 of grade 4 / semester 1 / math / 人教版.
  const DEFAULT_GEN_REQUIREMENT = "一 万以上数的认识";
  const GEN_DIFFICULTY_VALUES = ["", "easy", "medium", "hard"];
  const GEN_QUESTION_TYPE_VALUES = ["single", "multiple", "mixed"];
  const GEN_QUESTION_TYPE_LABEL_KEYS = {
    single: "genTypeSingle",
    multiple: "genTypeMultiple",
    mixed: "genTypeMixed",
  };
  const GEN_PAGE_IDS = {
    home: "generateHome",
    gen: "genConfigPage",
    answer: "answerConfigPage",
    package: "packageEditorPage",
    tagSource: "tagSourcePage",
    level: "levelEditorPage",
    questions: "questionsPage",
  };
  const MAX_LEVELS = 20;
  const MAX_LEVEL_SOURCES = 8;
  const LEVEL_NAME_MAX = 10;
  // The package model is version 2. A session without it is served by a page
  // cached from an older APK; the new page reports that instead of writing
  // legacy fields the TV would reject.
  const LEVEL_MODEL = 2;
  const ANSWER_KIND_VALUES = ["any", "single", "multiple"];
  const ANSWER_KIND_FILTERS = {
    any: null,
    single: "single",
    multiple: "multiple",
  };
  const TAG_NAME_MAX = 20;
  const IMPORT_MAX_BYTES = 16 * 1024 * 1024;
  const IMPORT_MAX_ITEMS = 10000;
  const IMPORT_MAX_BATCH_ITEMS = 50;
  const IMPORT_MAX_BATCH_BYTES = 400 * 1024;
  const IMPORT_PREVIEW_LIMIT = 10;
  const MAX_TAGS = 20;
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
    xinanda: "tbXinanda",
    pep: "tbPep",
    waiyan: "tbWaiyan",
    yilin: "tbYilin",
    kejiao: "tbKejiao",
    general: "tbGeneral",
    all: "tbAll",
  };
  let TEXTBOOKS = {
    chinese: ["bubian"],
    math: ["renjiao", "beishida", "sujiao", "qingdao", "xinanda"],
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
  // The app download is a browser-only convenience. In the parent app shell it is
  // redundant, and the daily cap is a courtesy limit rather than a security control.
  const PARENT_APP_DOWNLOAD_URL = "https://planyourtv.me/download/parent";
  const PARENT_APP_DOWNLOAD_KEY = "ptv.parentAppDownload";
  const PARENT_APP_DOWNLOAD_LIMIT = 5;
  const TAB_VIEWS = {
    generate: "view-generate",
    bank: "view-bank",
    user: "view-user",
    system: "view-system",
  };
  // The member header names the page it belongs to, the way DingTalk puts the
  // page title next to the profile avatar. System keeps its own device header.
  const TAB_TITLE_KEYS = {
    generate: "tabGenerate",
    bank: "tabBank",
    user: "tabUser",
  };
  // V41: the member this page edits is independent of the member the TV runs.
  // The key is deliberately not tied to the TV's random path token so the
  // choice survives a TV restart.
  const EDITING_SUBJECT_KEY = "quiz.editingSubject:" + location.host;
  const $ = (id) => document.getElementById(id);
  let lang = detectLang();
  let adminToken = null;
  let adminExpiresAt = 0;
  let heartbeatTimer = 0;
  let statusTimer = 0;
  // V29: the web parent center is writable only in LOCAL mode and while unbound. Any other
  // state turns the whole page into the disabled card: no PIN, no parent UI, no quiz editor.
  let webDisabled = false;
  let webDisabledBootstrap = null;
  let webDisabledTimer = 0;
  let webDisabledBusy = false;
  let webDisabledVisibilityBound = false;
  let parentBootstrap = null;
  // The TV's parent-center QR opens /quiz#parent; after the normal PIN login the page should
  // land on More instead of the generation tab. The fragment is consumed at startup so a
  // refresh inside the editor does not jump back to More.
  let openParentAfterLogin = false;
  // Re-entering from the Control tab's login gate returns to Control, not to the
  // generation tab. The parent center is served by the TV, so both flags stay local.
  // The overlay tab the PIN gate must return to after a quiz login.
  let openOverlayAfterLogin = null;
  let forceTakeover = false;
  let gateShown = false;
  let gateOccupied = false;
  // Forgot-password form replaces the PIN form on the same gate card. It talks only to
  // /api/parent/recover, so it works while the PIN is forgotten or its login is locked out.
  let recoveryOpen = false;
  // The TV's lock-screen eligibility, refreshed by the 3s heartbeat. null means
  // "not read yet"; the action stays disabled until the TV answers.
  let unlockState = null;
  let unlockBusy = false;
  // V27: parent.js borrows this dialog so the web parent center has one unlock UI.
  let parentUnlockWaiter = null;
  // A one-shot manual session started from this page. The TV owns the timer;
  // this state only mirrors the TV's lock-screen eligibility and limits.
  let manualState = null;
  let manualBusy = false;
  let manualMinutes = 20;
  let manualMinutesTouched = false;
  let publicState = null;
  let adminSession = null;
  let challengeConfig = null;
  let syncState = null;
  let cloudManaged = false;
  let bankCapacity = 100000;
  let bankCounts = null;
  // V41 editing subject. `editingSubjectUserId` is the member the phone edits;
  // `activeUserId` is the member the TV currently runs. They are allowed to
  // differ, and only the explicit TV switch changes the latter.
  let editingSubjectUserId = readEditingSubject();
  let familyMembers = [];
  let activeUserId = "";
  let privateCapacity = 20000;
  let privateRemaining = 20000;
  let bankScope = "builtin";
  let bankScopeCounts = { builtin: 0, ai: 0, manual: 0, wrong: 0 };
  let bankScopeStats = null;
  let bankManualCollectionId = "";
  let bankManageScope = "builtin";
  let bankManageReturnOverlay = false;
  let subjectSwitching = false;
  let providerState = { aiSupported: true, consentAccepted: false, configured: false, baseUrl: "", model: "", speech: null };
  const DEFAULT_SPEECH = {
    initialized: false,
    enabled: true,
    readPrompt: true,
    readOptions: true,
    readAnswer: true,
  };
  let speechState = Object.assign({}, DEFAULT_SPEECH);
  let speechDirty = false;
  let providerMsg = null;
  let pendingGenerate = false;
  // The privacy notice is only raised by an AI action; the read-only copy stays
  // under settings. First entry must not look like AI setup is required.
  let pendingProviderAction = "";
  let activeTab = "generate";
  let bankPage = "home";
  // Manage-banks is reachable from More as well as from the Bank tab. Remember
  // the page that opened it so its back button does not strand the user on Bank.
  let bankManageReturnTab = null;
  let bankItems = [];
  let bankOffset = 0;
  let bankHasMore = false;
  let bankLoading = false;
  let bankRequest = 0;
  // V22 local tags. A level package may draw from any number of tags.
  let importCollections = [];
  let importTagSuggestItems = [];
  let importTagActiveIndex = -1;
  let importTagComposing = false;
  let importDefaultDifficulty = "medium";
  let importPreview = null;
  let importResult = null;
  let importBusy = false;
  let importRunning = false;
  let importCancelRequested = false;
  let importProgress = null;
  let importPreviewPage = 0;
  // The last import failure, kept so the user can copy the same error lines they
  // see into an AI chat. Only data errors are copyable; target and file errors
  // are not something a model can repair.
  let importErrorInfo = { message: "", errors: [], copyable: false };
  let importFileName = "";
  // A large file is kept here instead of in the textarea. Rendering a 16 MB
  // string in an editable field is what freezes older phone browsers.
  let importFileText = null;
  // Which prompt (convert or generate) the copy buttons currently show below.
  let importPromptKind = "";
  let bankSourceCounts = { course: 0, custom: 0 };
// The bundled parent life bank is outside the curriculum scopes; its count
// drives the extra source card and is independent of the builtin tab total.
let presetBank = { bankId: "", total: 0, available: 0 };
  let localBankActive = true;
  let bankClearPending = null;
  let bankClearBusy = false;
  let targetCount = 20;
  let genState = idleGeneration();
  let genTimer = null;
  let generationToken = 0;
  let settingsSaving = false;
  let rangeSaving = false;
  let activeModal = null;
  let lastFocused = null;
  // AI configuration is a full subpage rather than a modal. It can be opened
  // from the generate flow, the Me tab, or the parent center's More page.
  let aiPageOpen = false;
  let aiReturnTab = "generate";
  let aiReturnParentCenter = false;
  let genGradeValue = 4;
  let genSemesterValue = 1;
  let genUnitValue = 1;
  let genSubjectValue = "math";
  // Keep the default demo on the first unit so a new TV can generate without
  // any configuration. A saved preference replaces both values.
  let genScopeValue = "unit";
  let genTextbookId = "renjiao";
  let genDifficultyValue = "";
  // 完全自由的题目要求；样例只是快捷填充，不再代表下拉选中项。
  let genRequirementValue = DEFAULT_GEN_REQUIREMENT;
  let genQuestionTypeValue = "single";
  let genOptionsValue = 4;
  let genAutoAddValue = true;
  let genPromptDirty = false;
  // A second or later member starts with an empty generation scope: the parent
  // picks grade/semester/subject/textbook for that child. The default member
  // keeps the install defaults so a fresh TV can generate immediately.
  let genScopeSetup = false;
  let genPrefsSaveTimer = null;
  let genPage = "home";
  let answerTab = "scope";
  // Android WebView can deliver a late compatibility click at the same screen
  // point after a handler has already changed the page. Debounce every click
  // briefly so one tap can never trigger an action on the page that replaced
  // it (for example, adding a level and then reopening the source editor).
  let clickSuppressUntil = 0;
  document.addEventListener("click", (event) => {
    // Only a trusted click can be the late compatibility click this guards
    // against. Programmatic clicks are deliberate page actions -- cancelling
    // their default action would silently block the hidden wallpaper upload
    // input, whose click() must reach the WebView's file chooser.
    if (!event.isTrusted) return;
    // A label forwards its activation to the labeled control as a second
    // trusted click right after the label's own click. That forwarded click is
    // the real action: the file chooser opens, a checkbox or radio toggles, a
    // field focuses. Form controls must never be treated as a late duplicate,
    // or the parent center's app picker (label + hidden checkbox) and the
    // import file input both stop responding.
    const target = event.target;
    if (target instanceof HTMLInputElement ||
        target instanceof HTMLSelectElement ||
        target instanceof HTMLTextAreaElement) return;
    const now = Date.now();
    if (now < clickSuppressUntil) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    clickSuppressUntil = now + 300;
  }, true);
  // The ordered parent-designed packages. A package holds one or more sources;
  // the draft list is edited locally and saved as one `levelPackages` array.
  let levelPackages = [];
  let levelLimits = { maxLevels: MAX_LEVELS, maxSources: MAX_LEVEL_SOURCES, maxNameCodePoints: LEVEL_NAME_MAX };
  let levelPackagesUnreadable = false;
  // A package id while editing an existing package, null while adding one.
  let packageEditingId = null;
  let packageDraftSources = [];
  let packageNameDraft = "";
  let packageSaving = false;
  // True while the package editor or one of its child pages is open, so the
  // course-source and tag picker back buttons return to the draft.
  let packageEditorActive = false;
  // Manual import opens its own page. The selected tag ids are draft-only; the
  // level is still written only by savePackageDraft on the package page.
  let tagSourceSelected = new Set();
  let tagSourceReturnAfterImport = false;
  let levelSaving = false;
  let pendingLevelDeleteId = null;
  let levelMessageTimer = 0;
  // The course-source editor edits one source of packageDraftSources. A null
  // index means the source will be appended; -1 is reserved for "not editing".
  let courseSourceEditingIndex = null;
  // Which bank a course source draws from: "preset", "ai", or "all" for a
  // level saved before the split. New sources can never choose "all".
  let courseSourceOrigin = "all";
  let courseSourceAnswerKind = "any";
  // A new source reveals the type filter only after the parent has answered
  // the difficulty step, even when that answer is "any difficulty".
  let courseSourceDifficultyPicked = false;
  let courseSourceCounts = null;
  let courseSourceCountsKey = "";
  let courseSourceCountsToken = 0;
  // Legacy single-scope view, derived from the first course source for older
  // generation helpers and cached surfaces.
  let answerScope = null;
  let answerScopeMode = "bound";
  let answerScopeAvailable = 0;
  let scopeLoaded = false;
  let answerDirty = false;
  // Custom scope draft may be partial while the parent picks step by step.
  let customScope = null;
  // A new level source starts on the edited member's grade/semester and shows no
  // other values. Editing an existing source leaves every step changeable.
  let courseSourceScopeLock = null;
  // Per-step facet cache: { counts, unrestricted, matched }. Values are keyed
  // by the upstream selections only, so changing one step never rewrites
  // earlier choices.
  let scopeFacets = {};
  let scopeOptionsLoading = {};
  let scopeRequestToken = 0;
  let scopeMatched = null;
  let selectedBatch = new Set();
  // The page lives under the TV's random token path. A leading "/" would make
  // fetch resolve the API from the host root, which PhoneServer answers with 404.
  const API_ROOT = location.pathname.replace(/\/quiz\/?$/, "/");
  const AUTH_KEY = "quiz.auth:" + location.host + API_ROOT;

  function idleGeneration() {
    return {
      phase: "idle",
      jobId: null,
      batchId: null,
      // Resolved when the job starts. Follow-the-TV mode must keep polling the
      // member the job was created for, even if the TV becomes active later.
      subjectUserId: "",
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

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = 0;
    }
  }

  function heartbeat() {
    if (!adminToken) return;
    api("/api/quiz/admin/heartbeat", { admin: true })
      .then((data) => {
        applyUnlockState(data && data.lock);
        applyManualState(data && data.manualStart);
      })
      .catch((error) => {
        if (error && error.code === "LOCAL_ONLY") {
          applyUnlockState({ eligible: false, reason: "NOT_LOCAL", unlocked: false });
          applyManualState({ eligible: false, reason: "NOT_LOCAL" });
        } else if (error && error.code !== "ADMIN_EXPIRED" && error.code !== "ADMIN_REQUIRED") {
          applyUnlockState({ eligible: false, reason: "OFFLINE", unlocked: false });
          applyManualState({ eligible: false, reason: "OFFLINE" });
        }
      });
  }

  function startHeartbeat() {
    stopHeartbeat();
    if (!adminToken) return;
    // Fetch once immediately: the "me" tab should not show "checking" for a
    // full heartbeat interval after the editor opens.
    heartbeat();
    heartbeatTimer = setInterval(() => {
      // WeChat keeps background pages alive on some versions; an explicit
      // visible check is what stops a forgotten page from holding the slot.
      if (document.hidden) return;
      heartbeat();
    }, HEARTBEAT_MS);
  }

  function clearAuth() {
    stopHeartbeat();
    adminToken = null;
    adminExpiresAt = 0;
    forceTakeover = false;
    try { localStorage.removeItem(AUTH_KEY); } catch (_) { /* ignore */ }
  }

  // Leaving the page should not force the parent to re-enter the PIN. The TV
  // only marks the slot idle; the remembered token still resumes this phone
  // unless another phone takes over first.
  function releaseSession() {
    if (!adminToken) return;
    stopHeartbeat();
    // keepalive can outlive the page; sendBeacon cannot carry the admin header.
    api("/api/quiz/auth/release", {
      method: "POST",
      admin: true,
      body: {},
      keepalive: true,
    }).catch(() => {});
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

  // --------------------------------------------------------- parent app download

  let parentAppDownloadMemory = null;

  function isParentAppShell() {
    return Boolean(window.ParentShell || window.__pytvParentShell);
  }

  function localDayKey() {
    const now = new Date();
    const month = ("0" + (now.getMonth() + 1)).slice(-2);
    const day = ("0" + now.getDate()).slice(-2);
    return now.getFullYear() + "-" + month + "-" + day;
  }

  function readParentAppDownload() {
    const today = localDayKey();
    if (parentAppDownloadMemory && parentAppDownloadMemory.day === today) {
      return parentAppDownloadMemory;
    }
    parentAppDownloadMemory = null;
    let raw = null;
    try {
      raw = localStorage.getItem(PARENT_APP_DOWNLOAD_KEY);
    } catch (_) {
      // Private mode: keep counting in memory for this page instead of blocking.
      return { day: today, count: 0, available: false };
    }
    if (!raw) return { day: today, count: 0, available: true };
    try {
      const parsed = JSON.parse(raw);
      if (!parsed || parsed.day !== today || typeof parsed.count !== "number" ||
          !isFinite(parsed.count) || parsed.count < 0) {
        return { day: today, count: 0, available: true };
      }
      return { day: today, count: Math.floor(parsed.count), available: true };
    } catch (_) {
      return { day: today, count: 0, available: true };
    }
  }

  function writeParentAppDownload(usage) {
    const next = { day: usage.day, count: usage.count, available: true };
    // Keep the count even when the write fails, so the cap still applies while the
    // page stays open. A reload clears the fallback; that is acceptable for a courtesy cap.
    parentAppDownloadMemory = next;
    try {
      localStorage.setItem(PARENT_APP_DOWNLOAD_KEY, JSON.stringify({
        day: next.day,
        count: next.count,
      }));
    } catch (_) { /* private mode */ }
  }

  function parentAppDownloadStatus() {
    const usage = readParentAppDownload();
    const count = usage.available ? usage.count : 0;
    return {
      remaining: Math.max(0, PARENT_APP_DOWNLOAD_LIMIT - count),
      limited: usage.available && count >= PARENT_APP_DOWNLOAD_LIMIT,
    };
  }

  function updateParentAppDownloadRow() {
    const card = $("parentAppDownloadCard");
    const row = $("parentAppDownload");
    const state = $("parentAppDownloadState");
    if (!card || !row || !state) return;
    if (isParentAppShell()) {
      card.classList.add("hidden");
      return;
    }
    card.classList.remove("hidden");
    const status = parentAppDownloadStatus();
    if (status.limited) {
      row.setAttribute("aria-disabled", "true");
      state.textContent = t("parentAppDownloadLimit");
      return;
    }
    row.removeAttribute("aria-disabled");
    state.textContent = status.remaining <= 2
      ? t("parentAppDownloadRemaining", { n: status.remaining })
      : "Android 7.0+";
  }

  function openParentAppDownload() {
    if (isParentAppShell()) return "unavailable";
    const usage = readParentAppDownload();
    if (usage.available && usage.count >= PARENT_APP_DOWNLOAD_LIMIT) {
      updateParentAppDownloadRow();
      return "limited";
    }
    writeParentAppDownload({
      day: usage.day,
      count: (usage.available ? usage.count : 0) + 1,
    });
    updateParentAppDownloadRow();
    let opened = null;
    try {
      opened = window.open(PARENT_APP_DOWNLOAD_URL, "_blank");
    } catch (_) {
      opened = null;
    }
    if (opened) {
      try { opened.opener = null; } catch (_) { /* ignore */ }
    } else {
      try { location.href = PARENT_APP_DOWNLOAD_URL; } catch (_) { /* ignore */ }
    }
    return "opened";
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
    const subjects = SCOPE_SUBJECTS.map((id) => {
      const textbooks = (TEXTBOOKS[id] || ["general"]).slice();
      const options = id === "english"
        ? [
            {
              minGrade: 1,
              maxGrade: 2,
              defaultTextbook: DEFAULT_TEXTBOOK[id] || "general",
              textbooks: textbooks.filter((textbook) => textbook !== "yilin"),
            },
            {
              minGrade: 3,
              maxGrade: 6,
              defaultTextbook: DEFAULT_TEXTBOOK[id] || "general",
              textbooks,
            },
          ]
        : [{
            minGrade: 1,
            maxGrade: 6,
            defaultTextbook: DEFAULT_TEXTBOOK[id] || "general",
            textbooks,
          }];
      return { id, labels: {}, aliases: [], options };
    });
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

  function applyClearStats(data) {
    if (!data) return;
    applyBankCounts(data.counts);
    if (data.scopeCounts) bankScopeCounts = subjectScopeCounts(data.scopeCounts);
    if (data.privateCapacity !== undefined) {
      privateCapacity = Math.max(0, Number(data.privateCapacity || 0));
    }
    if (data.privateRemaining !== undefined) {
      privateRemaining = Math.max(0, Number(data.privateRemaining || 0));
    }
    if (data.sourceCounts) {
      bankSourceCounts = {
        course: Math.max(0, Number(data.sourceCounts.course || 0)),
        custom: Math.max(0, Number(data.sourceCounts.custom || 0)),
      };
    }
    applyPresetBank(data.presetBank);
    if (publicState && data.bankState) publicState.bankState = data.bankState;
  }

  function readEditingSubject() {
    try {
      return localStorage.getItem(EDITING_SUBJECT_KEY) || "";
    } catch (_) {
      return "";
    }
  }

  function saveEditingSubject(userId) {
    const next = userId || "";
    // A debounced preference save was captured for the old member, but api() attaches
    // whatever subject the page points at when the request finally fires. Drop it on an
    // identity change so it can never be written into the next member's settings.
    if (next !== editingSubjectUserId && genPrefsSaveTimer) {
      clearTimeout(genPrefsSaveTimer);
      genPrefsSaveTimer = null;
    }
    editingSubjectUserId = next;
    try {
      if (editingSubjectUserId) localStorage.setItem(EDITING_SUBJECT_KEY, editingSubjectUserId);
      else localStorage.removeItem(EDITING_SUBJECT_KEY);
    } catch (_) {
      // Private mode still allows editing for this page load.
    }
  }

  function findFamilyMember(userId) {
    return familyMembers.find((member) => member.userId === userId) || null;
  }

  function editingMember() {
    return findFamilyMember(editingSubjectUserId);
  }

  function editingMemberName() {
    const member = editingMember();
    return member && member.name ? member.name : "";
  }

  function activeMember() {
    return findFamilyMember(activeUserId);
  }

  function activeMemberName() {
    const member = activeMember();
    return member && member.name ? member.name : "";
  }

  /**
   * The edited member's optional study scope. A missing value means "all grades" or
   * "all semesters", which is also how members created before these fields existed
   * behave, so an old member keeps today's unrestricted pickers.
   */
  function memberScopeChoice() {
    const member = editingMember() || activeMember();
    if (!member) return { grade: null, semester: null };
    const grade = Number(member.grade);
    const semester = Number(member.semester);
    const usableGrade = Number.isInteger(grade) && grade >= 1 &&
      gradeNos().includes(grade) && !cloudJuniorBlocked(grade);
    if (!usableGrade) return { grade: null, semester: null };
    return {
      grade: grade,
      semester: semester === 1 || semester === 2 ? semester : null,
    };
  }

  function privateUsed() {
    return Math.max(0, Number(privateCapacity || 0) - Number(privateRemaining || 0));
  }

  function subjectSwitchBlocked() {
    return subjectSwitching ||
      importRunning ||
      genState.phase === "running" ||
      genState.phase === "adding" ||
      genState.stopping;
  }

  function subjectWriteScope() {
    return editingSubjectUserId ? "member" : "default";
  }

  function subjectScopeCounts(value) {
    const source = value || {};
    return {
      builtin: Math.max(0, Number(source.builtin || 0)),
      ai: Math.max(0, Number(source.ai || 0)),
      manual: Math.max(0, Number(source.manual || 0)),
      wrong: Math.max(0, Number(source.wrong || 0)),
    };
  }

  function applyPresetBank(value) {
    if (!value || typeof value !== "object") return;
    const bankId = String(value.bankId || "").trim();
    if (!bankId) return;
    presetBank = {
      bankId,
      total: Math.max(0, Number(value.total || 0)),
      available: Math.max(0, Number(value.available || 0)),
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

  function applyUnlockState(lock) {
    unlockState = lock && typeof lock === "object"
      ? {
        eligible: Boolean(lock.eligible),
        reason: String(lock.reason || ""),
        unlocked: Boolean(lock.unlocked),
      }
      : null;
    renderUnlockState();
  }

  function unlockReasonText(reason) {
    const key = "unlockReason_" + (reason || "NOT_READY");
    const value = t(key);
    return value === key ? t("unlockReason_NOT_READY") : value;
  }

  function renderUnlockState() {
    if (!$("unlockAction")) return;
    const state = unlockState;
    let text = t("unlockChecking");
    if (state) {
      text = state.unlocked
        ? unlockReasonText("ALREADY_UNLOCKED")
        : unlockReasonText(state.reason);
    }
    $("unlockState").textContent = text;
    $("unlockState").classList.toggle("ok", Boolean(state && (state.eligible || state.unlocked)));
    $("unlockAction").disabled = !state || !state.eligible || unlockBusy;
    $("unlockAction").classList.toggle("hidden", Boolean(state && state.unlocked));
  }

  function openUnlockDialog() {
    if (!unlockState || !unlockState.eligible) return;
    $("unlockError").textContent = "";
    $("unlockPin").value = "";
    openModal("unlockDialog");
    setTimeout(() => {
      if (activeModal === "unlockDialog") $("unlockPin").focus();
    }, 50);
  }

  /**
   * V27 host bridge. Resolves true only after the TV accepted the PIN via the normal quiz
   * unlock endpoint (which marks the same parent proof the parent center needs), and false
   * when the dialog was closed or this page cannot offer it. parent.js falls back to its own
   * sheet when false, so an older tab still works.
   */
  function parentUnlockDialog() {
    if (!adminToken || Date.now() >= adminExpiresAt) return Promise.resolve(false);
    if (!unlockState || !unlockState.eligible || unlockState.unlocked) return Promise.resolve(false);
    if (parentUnlockWaiter || activeModal) return Promise.resolve(false);
    return new Promise((resolve) => {
      parentUnlockWaiter = resolve;
      openUnlockDialog();
    });
  }

  function settleParentUnlock(ok) {
    const resolve = parentUnlockWaiter;
    parentUnlockWaiter = null;
    if (resolve) resolve(Boolean(ok));
  }

  async function submitUnlock(event) {
    event.preventDefault();
    if (unlockBusy) return;
    const pin = $("unlockPin").value.trim();
    if (!/^\d{6}$/.test(pin)) {
      $("unlockError").textContent = t("unlockPinLength");
      return;
    }
    unlockBusy = true;
    $("unlockError").textContent = "";
    $("unlockSubmit").disabled = true;
    renderUnlockState();
    try {
      const data = await api("/api/quiz/admin/unlock", {
        method: "POST",
        admin: true,
        body: { pin },
      });
      applyUnlockState(data && data.lock);
      settleParentUnlock(true);
      closeModal();
    } catch (error) {
      const lock = error.data && error.data.lock;
      if (lock) applyUnlockState(lock);
      const key = "unlockReason_" + (error.code || "");
      const reasonText = t(key);
      $("unlockError").textContent = reasonText === key
        ? errorText(error)
        : reasonText;
      setTimeout(() => {
        if (activeModal === "unlockDialog") $("unlockPin").focus();
      }, 50);
    } finally {
      unlockBusy = false;
      $("unlockSubmit").disabled = false;
      $("unlockPin").value = "";
      renderUnlockState();
    }
  }

  // Both page actions share the same PIN limiter on the TV, but they mirror
  // different eligibility states: temporary unlock versus a timed manual start.
  const MANUAL_SHARED_REASON_KEYS = {
    NOT_LOCAL: "unlockReason_NOT_LOCAL",
    LOCAL_ONLY: "unlockReason_NOT_LOCAL",
    NOT_READY: "unlockReason_NOT_READY",
    SETUP_REQUIRED: "unlockReason_SETUP_REQUIRED",
    PIN_NOT_SET: "unlockReason_PIN_NOT_SET",
    STUDY_MODE: "unlockReason_STUDY_MODE",
    QUIZ_ACTIVE: "unlockReason_QUIZ_ACTIVE",
    SCREEN_OFF: "unlockReason_SCREEN_OFF",
    NOT_ON_LOCK_SCREEN: "unlockReason_NOT_ON_LOCK_SCREEN",
    DEFAULT_PIN: "unlockReason_DEFAULT_PIN",
    STATE_CHANGED: "unlockReason_STATE_CHANGED",
    OFFLINE: "unlockReason_OFFLINE",
  };

  function clampManualMinutes(value, state) {
    const min = state ? state.minMinutes : 5;
    const max = state ? Math.max(min, state.maxMinutes) : 120;
    const step = state ? state.stepMinutes : 5;
    const stepped = Math.round((Number(value) || min) / step) * step;
    return Math.min(max, Math.max(min, stepped));
  }

  function applyManualState(manual) {
    manualState = manual && typeof manual === "object"
      ? {
        eligible: Boolean(manual.eligible),
        reason: String(manual.reason || ""),
        minMinutes: Math.max(1, Number(manual.minMinutes) || 5),
        maxMinutes: Math.max(1, Number(manual.maxMinutes) || 120),
        stepMinutes: Math.max(1, Number(manual.stepMinutes) || 5),
        defaultMinutes: Math.max(1, Number(manual.defaultMinutes) || 20),
        restRemainingSeconds: Math.max(0, Number(manual.restRemainingSeconds) || 0),
        remainingSeconds: Math.max(0, Number(manual.remainingSeconds) || 0),
        outsidePlan: Boolean(manual.outsidePlan),
      }
      : null;
    if (manualState) {
      manualMinutes = clampManualMinutes(
        manualMinutesTouched ? manualMinutes : manualState.defaultMinutes,
        manualState,
      );
    }
    renderManualState();
  }

  function manualReasonText(reason, state) {
    const manual = state || manualState;
    if (reason === "SESSION_ACTIVE" && manual && manual.outsidePlan && manual.remainingSeconds > 0) {
      return t("manualSessionActive", {
        minutes: Math.max(1, Math.ceil(manual.remainingSeconds / 60)),
      });
    }
    if (reason === "RESTING" && manual && manual.restRemainingSeconds > 0) {
      return t("manualRestWait", {
        minutes: Math.max(1, Math.ceil(manual.restRemainingSeconds / 60)),
      });
    }
    const shared = MANUAL_SHARED_REASON_KEYS[reason];
    if (shared) return t(shared);
    const key = "manualReason_" + (reason || "NOT_READY");
    const value = t(key);
    return value === key ? t("unlockReason_STATE_CHANGED") : value;
  }

  function renderManualState() {
    if (!$("manualAction")) return;
    const state = manualState;
    const text = state ? manualReasonText(state.reason) : t("manualChecking");
    $("manualState").textContent = text;
    $("manualState").classList.toggle("ok", Boolean(state && state.reason === "OK"));
    $("manualAction").disabled = !state || !state.eligible || manualBusy;
    $("manualMinutes").textContent = String(manualMinutes);
    if (activeModal === "manualDialog") {
      $("manualDialogLead").textContent = t("manualDialogLead", { minutes: manualMinutes });
    }
    const min = state ? state.minMinutes : manualMinutes;
    const max = state ? state.maxMinutes : manualMinutes;
    $("manualMinus").disabled = !state || manualBusy || manualMinutes <= min;
    $("manualPlus").disabled = !state || manualBusy || manualMinutes >= max;
  }

  function adjustManualMinutes(delta) {
    if (!manualState || manualBusy) return;
    manualMinutesTouched = true;
    manualMinutes = clampManualMinutes(
      manualMinutes + delta * manualState.stepMinutes,
      manualState,
    );
    renderManualState();
  }

  function openManualDialog() {
    if (!manualState || !manualState.eligible || manualBusy) return;
    $("manualError").textContent = "";
    $("manualPin").value = "";
    $("manualDialogLead").textContent = t("manualDialogLead", { minutes: manualMinutes });
    openModal("manualDialog");
    setTimeout(() => {
      if (activeModal === "manualDialog") $("manualPin").focus();
    }, 50);
  }

  async function submitManualStart(event) {
    event.preventDefault();
    if (manualBusy || !manualState || !manualState.eligible) return;
    const pin = $("manualPin").value.trim();
    if (!/^\d{6}$/.test(pin)) {
      $("manualError").textContent = t("unlockPinLength");
      return;
    }
    manualBusy = true;
    $("manualError").textContent = "";
    $("manualSubmit").disabled = true;
    renderManualState();
    try {
      const data = await api("/api/quiz/admin/manual/start", {
        method: "POST",
        admin: true,
        body: { pin, minutes: manualMinutes },
      });
      applyManualState(data && data.manualStart);
      closeModal();
    } catch (error) {
      const manual = error.data && error.data.manualStart;
      if (manual) applyManualState(manual);
      const shared = MANUAL_SHARED_REASON_KEYS[error.code];
      if (shared) {
        $("manualError").textContent = t(shared);
      } else if (error.code === "INVALID_DURATION") {
        $("manualError").textContent = t("manualError_INVALID_DURATION");
      } else {
        const key = "manualReason_" + (error.code || "");
        const value = t(key);
        $("manualError").textContent = value === key ? errorText(error) : value;
      }
      setTimeout(() => {
        if (activeModal === "manualDialog") $("manualPin").focus();
      }, 50);
    } finally {
      manualBusy = false;
      $("manualSubmit").disabled = false;
      $("manualPin").value = "";
      renderManualState();
    }
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
    // V41: language moved into 系统配置; the old header select is gone.
    if ($("langSelectMe")) $("langSelectMe").value = lang;
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
    renderBankManage();
    renderBankCounts();
    renderBank();
    renderBankImport();
    renderSubjectChip();
    renderSubjectDialog();
    renderUnlockState();
    renderManualState();
    if (activeModal === "manualDialog") {
      $("manualDialogLead").textContent = t("manualDialogLead", { minutes: manualMinutes });
    }
    if (genPage === "answer") renderAnswerPage();
    if (genPage === "level") renderLevelEditor();
    if (genPage === "questions") renderQuestions();
    updateParentAppDownloadRow();
    if (webDisabled) renderWebDisabled();
    if (window.ParentCenterUI && window.ParentCenterUI.setLang) {
      window.ParentCenterUI.setLang(lang);
    }
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

  function generationQuestionTypeText() {
    return t(GEN_QUESTION_TYPE_LABEL_KEYS[genQuestionTypeValue] || "genTypeSingle");
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

    const entries = levels.map((level, index) => {
      const base = String(level.name || "").trim() ||
        packageSummaryText(level) ||
        t("levelFallbackName", { n: index + 1 });
      return {
        ordinal: index + 1,
        // Keep paused levels in the parent's overview but label them so this
        // card does not imply the child can still pick them.
        name: level.enabled === false
          ? base + " · " + t("levelPausedLabel")
          : base,
      };
    });
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
    if (!generationScopeReady()) {
      // A later member has not picked a grade/subject yet. Never show the
      // gallery defaults as if they belonged to this child.
      $("homeGenMain").textContent = t("genScopeUnsetTitle");
      $("homeGenSub").textContent = t("genScopeUnsetHint");
    } else {
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
        generationQuestionTypeText(),
        generationDifficultyText(),
        t("targetN", { n: targetCount }),
        genAutoAddValue ? t("genAutoAddShort") : t("genManualAddShort"),
      ].join(" · ");
    }

    const config = currentConfig();
    const pull = Number(config.pullCount || 5);
    const scopeModeLabel = $("homeAnswerScopeMode");
    const totalLevels = levelPackages.length;
    if (scopeModeLabel) {
      scopeModeLabel.textContent = totalLevels
        ? t("levelCountLabel", { n: totalLevels })
        : t("levelNoneLabel");
    }
    renderAnswerSummary(levelPackages);
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
    const scopeReady = generationScopeReady();
    $("generate").disabled = running || !scopeReady;
    const providerBlocked = providerState.aiSupported === false ||
      !providerState.consentAccepted || !providerState.configured;
    if ($("generateSetupHint")) {
      const showScope = !scopeReady && !running && !done;
      const showProvider = providerBlocked && !running && !done;
      const showHint = showScope || showProvider;
      $("generateSetupHint").classList.toggle("hidden", !showHint);
      $("generateSetupHint").textContent = !showHint ? ""
        : showScope
          ? t("genScopeSetupHint")
          : providerState.aiSupported === false
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

  function optionLetter(index) {
    const value = Number(index);
    return value >= 0 && value < 26 ? String.fromCharCode(65 + value) : String(value + 1);
  }

  function questionTypeBadge(item) {
    const multiple = Array.isArray(item.answerIndices) && item.answerIndices.length > 1 ||
      item.type === "multiple";
    const badge = document.createElement("span");
    badge.className = "badge";
    badge.textContent = t(multiple ? "genTypeMultiple" : "genTypeSingle");
    return badge;
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
    if ($("genMultiWarning")) {
      const hasMultiple = items.some((item) => item.type === "multiple");
      $("genMultiWarning").classList.toggle("hidden", !hasMultiple);
    }
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
      const answerIndices = Array.isArray(item.answerIndices) && item.answerIndices.length
        ? item.answerIndices.map(Number)
        : [Number(item.answerIndex)];
      const answerTexts = Array.isArray(item.answerTexts) && item.answerTexts.length
        ? item.answerTexts.map(String)
        : [String(item.answerText || "")];
      answer.textContent = t("questionAnswer") + ": " + answerIndices
        .map((index, position) => optionLetter(index) + " " + (answerTexts[position] || ""))
        .join("；");
      meta.appendChild(answer);
      meta.appendChild(questionTypeBadge(item));
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

  // The generation flow and the bank import page share the same full-screen
  // subpage look. A generate subpage can stay open in the background when the
  // custom-level entry jumps to the bank import page, so only the subpage of
  // the tab actually on screen may hide the top bar and bottom navigation.
  function syncSubpageClass() {
    const bankSubpage = activeTab === "bank" && bankPage !== "home";
    const genSubpage = activeTab === "generate" && genPage !== "home";
    document.body.classList.toggle("subpage-open", aiPageOpen || genSubpage || bankSubpage);
  }

  function showGenPage(page) {
    if (!GEN_PAGE_IDS[page]) page = "home";
    genPage = page;
    for (const [name, id] of Object.entries(GEN_PAGE_IDS)) {
      const el = $(id);
      if (el) el.classList.toggle("hidden", name !== page);
    }
    syncSubpageClass();
    if (page === "questions") renderQuestions();
    if (page === "answer") renderAnswerPage();
    if (page === "package") renderPackageEditor();
    if (page === "tagSource") renderTagSourcePage();
    if (page === "level") renderLevelEditor();
    window.scrollTo({ top: 0 });
  }

  function showBankPage(page) {
    bankPage = ["import", "manage"].includes(page) ? page : "home";
    if ($("bankHome")) $("bankHome").classList.toggle("hidden", bankPage !== "home");
    if ($("bankImportPage")) {
      $("bankImportPage").classList.toggle("hidden", bankPage !== "import");
    }
    if ($("bankManagePage")) {
      $("bankManagePage").classList.toggle("hidden", bankPage !== "manage");
    }
    syncSubpageClass();
    window.scrollTo({ top: 0 });
  }

  function backToGenerateHome() {
    $("questionError").textContent = "";
    showGenPage("home");
    renderHome();
    renderGenerate();
  }

  function discardPackageDraft() {
    packageEditingId = null;
    packageDraftSources = [];
    packageNameDraft = "";
    packageEditorActive = false;
    tagSourceSelected = new Set();
    tagSourceReturnAfterImport = false;
    courseSourceEditingIndex = null;
    courseSourceAnswerKind = "any";
    courseSourceDifficultyPicked = false;
    courseSourceCounts = null;
    courseSourceCountsKey = "";
    courseSourceCountsToken++;
    customScope = null;
    answerScopeMode = levelPackages.length ? "custom" : "bound";
    resetScopeOptions();
  }

  // Package changes are local until the parent taps Save, so leaving the
  // answer page drops an unfinished draft. Rule changes still wait for the
  // Save button on the rules tab.
  function backFromAnswerPage() {
    $("settingsError").textContent = "";
    discardPackageDraft();
    if (answerDirty) {
      answerDirty = false;
      loadAdminSession().catch(() => {});
    }
    backToGenerateHome();
  }

  function backFromLevelEditor() {
    $("levelEditorError").textContent = "";
    if (packageEditorActive) {
      // The draft panel is already the parent view; returning to it must keep
      // the sources configured so far instead of opening another page.
      courseSourceEditingIndex = null;
      customScope = null;
      courseSourceAnswerKind = "any";
      courseSourceDifficultyPicked = false;
      courseSourceCounts = null;
      courseSourceCountsKey = "";
      courseSourceCountsToken++;
      resetScopeOptions();
      showGenPage("package");
      return;
    }
    discardPackageDraft();
    showGenPage("answer");
  }

  function openGenConfigPage() {
    $("rangeError").textContent = "";
    if (packageEditorActive) discardPackageDraft();
    syncGenerateComposer(true);
    showGenPage("gen");
  }

  function openAnswerConfigPage() {
    answerTab = "scope";
    discardPackageDraft();
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
    const jobId = genState.jobId;
    const subjectUserId = genState.subjectUserId;
    const token = generationToken;
    try {
      const data = await api("/api/quiz/admin/generate/add", {
        method: "POST",
        admin: true,
        subjectUserId,
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

  function normalizeTimeLimit(value, fallback) {
    if (value === null || value === undefined || String(value).trim() === "") return fallback;
    const number = Number(value);
    if (!Number.isInteger(number)) return fallback;
    return Math.min(ANSWER_TIME_MAX_SEC, Math.max(ANSWER_TIME_MIN_SEC, number));
  }

  function configSeconds(config) {
    return normalizeTimeLimit(
      config.defaultTimeLimitSec,
      DEFAULT_CONFIG.defaultTimeLimitSec,
    );
  }

  function normalizeRoundCount(value, fallback) {
    if (value === null || value === undefined || String(value).trim() === "") return fallback;
    const number = Number(value);
    if (!Number.isInteger(number)) return fallback;
    return Math.min(ROUND_MAX, Math.max(ROUND_MIN, number));
  }

  function configRoundCount(config) {
    return normalizeRoundCount(config.pullCount, DEFAULT_CONFIG.pullCount);
  }

  function normalizeFeedbackHold(value, fallback) {
    if (value === null || value === undefined || String(value).trim() === "") return fallback;
    const number = Number(value);
    if (!Number.isInteger(number)) return fallback;
    return Math.min(FEEDBACK_HOLD_MAX_SEC, Math.max(FEEDBACK_HOLD_MIN_SEC, number));
  }

  function configFeedbackHold(config) {
    return normalizeFeedbackHold(
      config.feedbackExtraHoldSec,
      DEFAULT_CONFIG.feedbackExtraHoldSec,
    );
  }

  function normalizeMaxEarned(value, fallback) {
    if (value === null || value === undefined || String(value).trim() === "") return fallback;
    const number = Number(value);
    if (!Number.isInteger(number)) return fallback;
    return Math.min(MAX_EARNED_MAX_MINUTES, Math.max(MAX_EARNED_MIN_MINUTES, number));
  }

  function configMaxEarned(config) {
    return normalizeMaxEarned(config.maxEarnedMinutes, DEFAULT_CONFIG.maxEarnedMinutes);
  }

  // A saved value may predate stepping (the old round chips offered 8). Move
  // it to the next grid point in the pressed direction before stepping.
  function stepNumber(current, delta, min, max, step) {
    const start = Math.min(max, Math.max(min, Number.isInteger(current) ? current : min));
    const remainder = ((start % step) + step) % step;
    const next = remainder === 0
      ? start + delta
      : (delta > 0 ? start + (step - remainder) : start - remainder);
    return Math.min(max, Math.max(min, next));
  }

  // Most steppers use <prefix>Input, but the scoring rows keep their legacy field ids.
  function renderStepper(prefix, value, min, max, step, inputId) {
    const input = $(inputId || (prefix + "Input"));
    if (!input) return;
    // Do not rewrite the field while it is being typed into.
    if (document.activeElement !== input) input.value = String(value);
    input.min = String(min);
    input.max = String(max);
    input.step = String(step);
    const minus = $(prefix + "Minus");
    const plus = $(prefix + "Plus");
    if (minus) minus.disabled = value <= min;
    if (plus) plus.disabled = value >= max;
  }

  function bindStepper(prefix, min, max, step, configValue, patch, inputId) {
    const input = $(inputId || (prefix + "Input"));
    if (!input) return;
    const read = () => {
      const raw = input.value.trim();
      const number = Number(raw);
      return raw !== "" && Number.isInteger(number) ? number : configValue();
    };
    const apply = (value) => {
      const next = Math.min(max, Math.max(min, value));
      input.value = String(next);
      patch(next);
    };
    $(prefix + "Minus").addEventListener("click", () => {
      apply(stepNumber(read(), -step, min, max, step));
    });
    $(prefix + "Plus").addEventListener("click", () => {
      apply(stepNumber(read(), step, min, max, step));
    });
    input.addEventListener("input", () => {
      const raw = input.value.trim();
      if (raw === "") return;
      const number = Number(raw);
      if (!Number.isInteger(number) || number < min || number > max) return;
      patch(number);
    });
    input.addEventListener("change", () => {
      const raw = input.value.trim();
      const number = Number(raw);
      apply(raw !== "" && Number.isInteger(number) ? number : configValue());
    });
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
      minUnlockMinutes: scoringNumber(source.minUnlockMinutes, 0, UNLOCK_MAX_MINUTES),
      maxEarnedMinutes: normalizeMaxEarned(
        source.maxEarnedMinutes, DEFAULT_CONFIG.maxEarnedMinutes),
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
      minUnlockMinutes: numberField("minUnlock", 0, UNLOCK_MAX_MINUTES, 0),
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
    // Local rounds have no per-round reward cap; the daily quota limits the session.
    const example = penalty > 0
      ? t("scoringExample", { gross, penalty, net })
      : t("scoringExampleNoPenalty", { net });
    $("scoringExample").textContent = example;

    const warning = $("scoringWarning");
    if (!warning) return;
    let text = "";
    if (scoring.easyRewardMinutes + scoring.mediumRewardMinutes +
        scoring.hardRewardMinutes === 0) {
      text = t("scoringWarnNoReward");
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
    if (!$("roundInput")) return;
    const config = currentConfig();
    renderStepper(
      "round",
      configRoundCount(config),
      ROUND_MIN, ROUND_MAX, ROUND_STEP,
    );
    renderStepper(
      "time",
      configSeconds(config),
      ANSWER_TIME_MIN_SEC, ANSWER_TIME_MAX_SEC, ANSWER_TIME_STEP_SEC,
    );
    renderStepper(
      "feedbackHold",
      configFeedbackHold(config),
      FEEDBACK_HOLD_MIN_SEC, FEEDBACK_HOLD_MAX_SEC, FEEDBACK_HOLD_STEP_SEC,
    );
    // The unlock threshold is a settings stepper, not a chip row: it starts at
    // 0 and has no product-level upper limit.
    renderStepper(
      "unlock",
      scoringValues(config).minUnlockMinutes,
      0, UNLOCK_MAX_MINUTES, UNLOCK_STEP_MINUTES, "minUnlock",
    );
    renderScoringSummary();
    if ($("ruleScope")) {
      const totalLevels = allLevelCount();
      $("ruleScope").textContent = totalLevels
        ? t("levelCountLabel", { n: totalLevels })
        : t("levelNoneLabel");
    }
  }

  function renderSettings() {
    if (!$("easyReward")) return;
    const config = currentConfig();
    renderScoringInputs(config);
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

  function normalizeSpeech(raw) {
    const src = raw && typeof raw === "object" ? raw : {};
    return {
      initialized: src.initialized === true,
      enabled: src.enabled !== false,
      readPrompt: src.readPrompt !== false,
      readOptions: src.readOptions !== false,
      readAnswer: src.readAnswer !== false,
    };
  }

  // The key may only go to DeepSeek's own TTS endpoint, so the voice module is
  // available only for the official host. A compatible gateway is not enough:
  // it does not implement the speech API and must never receive the key.
  function isOfficialDeepSeek(value) {
    let url;
    try {
      url = new URL(String(value || "").trim());
    } catch {
      return false;
    }
    if (url.protocol !== "https:") return false;
    if (url.username || url.password || url.search || url.hash) return false;
    if (url.host.toLowerCase() !== "api.deepseek.com") return false;
    const path = url.pathname.replace(/\/+$/, "");
    return path === "" || path === "/v1";
  }

  function renderSpeech() {
    if (!$("speechSettings")) return;
    const visible = providerState.aiSupported !== false && isOfficialDeepSeek($("baseUrl").value);
    $("speechSettings").classList.toggle("hidden", !visible);
    if (!visible) return;
    $("speechEnabled").checked = speechState.enabled;
    $("speechPrompt").checked = speechState.readPrompt;
    $("speechOptions").checked = speechState.readOptions;
    $("speechAnswer").checked = speechState.readAnswer;
    for (const id of ["speechPrompt", "speechOptions", "speechAnswer"]) {
      $(id).disabled = !speechState.enabled;
    }
    $("speechContent").classList.toggle("is-off", !speechState.enabled);
    // The saved switch is default-on, but an upgraded TV must not start
    // uploading quiz text until the parent saves this page once.
    $("speechSaveHint").classList.toggle("hidden", speechState.initialized && !speechDirty);
  }

  function speechHasContent() {
    return speechState.readPrompt || speechState.readOptions || speechState.readAnswer;
  }

  function renderProvider() {
    if (!$("providerBadge")) return;
    const supported = providerState.aiSupported !== false;
    for (const id of ["baseUrl", "model", "apiKey", "saveProvider", "testProvider"]) {
      const el = $(id);
      if (el) el.disabled = !supported;
    }
    document.querySelectorAll("[data-provider-preset]").forEach((el) => {
      el.disabled = !supported;
    });
    if (!supported) {
      $("providerBadge").textContent = t("aiUnavailableShort");
      $("providerBadge").className = "setting-value";
      $("keyHint").textContent = "";
      renderSpeech();
      return;
    }
    $("providerBadge").textContent = t(providerState.configured ? "aiConfigured" : "aiNotConfigured");
    $("providerBadge").className = "setting-value" + (providerState.configured ? " good" : "");
    $("keyHint").textContent = t(providerState.configured ? "keyHintSaved" : "keyHintNew");
    updateProviderModelHint();
    renderSpeech();
    // Keep the generate card honest when the model key is cleared elsewhere.
    renderGenerate();
  }

  function renderProviderMessages() {
    if (!$("providerError")) return;
    const msg = providerMsg;
    $("providerError").textContent =
      msg && msg.kind === "error" ? (msg.key ? t(msg.key) : codeText(msg.code)) : "";
    $("providerWarn").textContent =
      msg && msg.kind === "warn" ? (msg.key ? t(msg.key) : codeText(msg.code)) : "";
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

  // ------------------------------------------------------- V41 editing subject
  // The chip is the only place this page changes which member it edits. It
  // never moves the TV's active member; that is an explicit TV-side action.

  // Uploaded avatars: the quiz page holds a quiz-admin session, not the parent
  // editor lease, so /api/parent/avatar cannot be used here. This cache keeps one
  // object URL per member and refetches only when the TV reports a new revision.
  const subjectAvatarCache = new Map();
  const subjectAvatarPending = new Map();

  function subjectAvatarObjectUrl(member, revision) {
    const cached = subjectAvatarCache.get(member.userId);
    if (cached && cached.revision === revision) return Promise.resolve(cached.url);
    const key = member.userId + ":" + revision;
    const pending = subjectAvatarPending.get(key);
    if (pending) return pending;
    if (!adminToken) return Promise.resolve(null);
    const task = fetch(
      API_ROOT + "api/quiz/admin/member/avatar?userId=" +
        encodeURIComponent(member.userId) + "&rev=" + encodeURIComponent(revision),
      {
        headers: {
          "X-Quiz-Admin": "1",
          "X-Quiz-Admin-Token": adminToken,
        },
        cache: "no-store",
        credentials: "omit",
      }
    ).then((response) => response.ok ? response.blob() : null)
      .then((blob) => {
        if (!blob) return null;
        const url = URL.createObjectURL(blob);
        const previous = subjectAvatarCache.get(member.userId);
        if (previous && previous.url !== url) URL.revokeObjectURL(previous.url);
        subjectAvatarCache.set(member.userId, { revision: revision, url: url });
        return url;
      })
      .catch(() => null)
      .finally(() => subjectAvatarPending.delete(key));
    subjectAvatarPending.set(key, task);
    return task;
  }

  function paintSubjectAvatar(node, member) {
    if (!node) return;
    const preset = String((member && member.avatarPreset) || "boy");
    const revision = String((member && member.avatarRevision) || 0);
    node.dataset.member = member ? member.userId : "";
    node.dataset.revision = revision;
    node.dataset.avatar = member && member.avatarUrl ? "1" : "0";
    node.dataset.preset = preset;

    function paintPreset() {
      // parent.js owns the family avatar artwork; reusing it keeps the header
      // consistent with the picker and the user page. The fallback keeps the
      // header usable if either script is served on its own.
      const shared = window.ParentCenterUI && window.ParentCenterUI.avatarHtml;
      if (typeof shared === "function") {
        node.innerHTML = shared(preset);
      } else {
        const name = String((member && member.name) || "").trim();
        node.textContent = name ? Array.from(name)[0].toUpperCase() : "?";
      }
    }

    function paintImage(url) {
      const image = document.createElement("img");
      image.src = url;
      image.alt = "";
      node.replaceChildren(image);
    }

    if (!member || !member.avatarUrl) {
      const stale = member && subjectAvatarCache.get(member.userId);
      if (stale) {
        URL.revokeObjectURL(stale.url);
        subjectAvatarCache.delete(member.userId);
      }
      paintPreset();
      return;
    }
    const cached = subjectAvatarCache.get(member.userId);
    if (cached && cached.revision === revision) {
      paintImage(cached.url);
      return;
    }
    paintPreset();
    subjectAvatarObjectUrl(member, revision).then((url) => {
      if (!url || !node.isConnected) return;
      if (node.dataset.member !== member.userId || node.dataset.revision !== revision ||
          node.dataset.avatar !== "1") return;
      paintImage(url);
    });
  }

  function subjectAvatarNode(member) {
    const node = document.createElement("span");
    node.className = "subject-avatar";
    paintSubjectAvatar(node, member);
    return node;
  }

  function renderSubjectChip() {
    const chip = $("subjectChip");
    if (!chip) return;
    // System settings belong to the TV, not to a member, so the device tab
    // never shows a member header.
    const visible = !webDisabled &&
      document.body.classList.contains("editor-open") &&
      familyMembers.length > 0 &&
      activeTab !== "system";
    chip.classList.toggle("hidden", !visible);
    if (!visible) return;
    const member = editingMember() || activeMember();
    // The chat-style header carries the page title beside the avatar; the
    // member name and the member the TV is actually running sit below it.
    const titleNode = chip.querySelector("[data-page-title]");
    if (titleNode) {
      titleNode.textContent = t(TAB_TITLE_KEYS[activeTab] || "tabGenerate");
    }
    const subNode = chip.querySelector("[data-subject-sub]");
    if (subNode) {
      subNode.textContent = t("memberHeaderSubtitle", {
        member: member ? member.name : "—",
        name: activeMemberName() || "—",
      });
    }
    const avatar = chip.querySelector("[data-subject-avatar]");
    if (avatar) {
      // The ring means "the member edited here is the member the TV is running".
      // Compare member identity, not the avatar image: two members can share a preset.
      avatar.classList.toggle("is-tv-current", Boolean(member && member.isActive));
      paintSubjectAvatar(avatar, member);
    }
    chip.setAttribute("aria-label", member
      ? t("subjectDialogTitle") + " · " + member.name
      : t("subjectDialogTitle"));
  }

  function renderSubjectDialog() {
    const list = $("subjectList");
    if (!list) return;
    list.replaceChildren();
    if (!familyMembers.length) {
      list.appendChild(elNode("p", "empty", t("subjectNoMembers")));
      return;
    }
    familyMembers.forEach((member) => {
      const row = document.createElement("button");
      row.type = "button";
      row.className = "subject-row";
      row.setAttribute("role", "option");
      // An empty preference means "follow the TV's active member", so that member is
      // the one currently selected in the dialog.
      row.setAttribute("aria-selected", String(
        member.userId === (editingSubjectUserId || activeUserId)
      ));
      row.dataset.subjectUserId = member.userId;
      const avatar = subjectAvatarNode(member);
      // The TV's current member keeps the same green ring as the header; the row's
      // selected border already marks which member this page edits.
      avatar.classList.toggle("is-tv-current", Boolean(member.isActive));
      row.appendChild(avatar);

      const main = elNode("span", "subject-row-main");
      main.appendChild(elNode("span", "subject-row-name", member.name || ""));
      const subParts = [];
      if (member.isActive) subParts.push(t("subjectCurrentTag"));
      if (member.isDefault) subParts.push(t("subjectDefaultTag"));
      if (subParts.length) main.appendChild(elNode("span", "subject-row-sub", subParts.join(" · ")));
      row.appendChild(main);

      row.addEventListener("click", () => switchEditingSubject(member.userId));
      list.appendChild(row);
    });
  }

  // Small DOM helper kept local so bank/subject code does not depend on parent.js.
  function elNode(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text);
    return node;
  }

  function openSubjectDialog() {
    if (!familyMembers.length) return;
    $("subjectDialogError").textContent = "";
    renderSubjectDialog();
    openModal("subjectDialog");
  }

  function setSubjectDialogBusy(busy) {
    document.querySelectorAll("#subjectList .subject-row").forEach((row) => {
      row.disabled = busy;
    });
  }

  async function switchEditingSubject(userId) {
    if (!userId || userId === editingSubjectUserId || subjectSwitching) {
      if (userId === editingSubjectUserId) closeModal();
      return;
    }
    if (subjectSwitchBlocked()) {
      $("subjectDialogError").textContent = t("subjectSwitchBlocked");
      return;
    }
    const previous = editingSubjectUserId;
    subjectSwitching = true;
    setSubjectDialogBusy(true);
    $("subjectDialogError").textContent = "";
    // saveEditingSubject drops a debounced preference save owned by the old member.
    saveEditingSubject(userId);
    try {
      await loadAdminSession();
      try {
        await loadGenerationPrefsFromTv(true);
      } catch (_) {
        // Preferences are a convenience; the session switch already succeeded.
      }
      // A generation batch or an import preview belongs to the previous member.
      // It is never carried across, and no request is in flight here.
      clearGenTimer();
      generationToken += 1;
      genState = idleGeneration();
      pendingGenerate = false;
      resetImportState();
      bankItems = [];
      bankOffset = 0;
      bankHasMore = false;
      bankManualCollectionId = "";
      bankRequest += 1;
      showGenPage("home");
      showBankPage("home");
      renderSubjectChip();
      renderGenerate();
      renderHome();
      renderBank();
      if (activeTab === "bank") loadBank(true);
      if (window.ParentCenterUI && window.ParentCenterUI.onSubjectChanged) {
        window.ParentCenterUI.onSubjectChanged();
      }
      closeModal();
    } catch (error) {
      saveEditingSubject(previous);
      $("subjectDialogError").textContent = errorText(error);
      try {
        await loadAdminSession();
      } catch (_) {
        // The old data stays on screen; the next heartbeat or refresh retries.
      }
    } finally {
      subjectSwitching = false;
      setSubjectDialogBusy(false);
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
    if ($("bankManageEntryState")) {
      $("bankManageEntryState").textContent = t("bankManageEntryState", {
        course: bankSourceCounts.course,
        custom: bankSourceCounts.custom,
        wrong: counts.wrong,
      });
    }
    if ($("bankSourceCounts")) {
      $("bankSourceCounts").textContent = t("bankSourceLine", {
        course: bankSourceCounts.course,
        custom: bankSourceCounts.custom,
        wrong: counts.wrong,
        total: counts.total,
        capacity: bankCapacity,
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
    const badges = document.createElement("span");
    badges.className = "bank-badges";
    if (item.collectionId) {
      const source = document.createElement("span");
      source.className = "source-badge custom";
      source.textContent = t("bankSourceCustomTag", {
        name: item.collectionName || item.collectionId,
      });
      badges.appendChild(source);
    }
    badges.appendChild(questionTypeBadge(item));
    badges.appendChild(statusChip(item));
    head.append(prompt, badges);
    card.appendChild(head);

    const answerIndices = Array.isArray(item.answerIndices) && item.answerIndices.length
      ? item.answerIndices.map(Number)
      : [Number(item.answerIndex)];
    const correct = new Set(answerIndices);
    const selectedIndices = Array.isArray(item.selectedIndices) && item.selectedIndices.length
      ? item.selectedIndices.map(Number)
      : (item.selectedIndex !== null && item.selectedIndex !== undefined
        ? [Number(item.selectedIndex)]
        : []);
    const selected = new Set(selectedIndices);
    const options = document.createElement("ol");
    options.className = "bank-options";
    (item.options || []).forEach((option, index) => {
      const li = document.createElement("li");
      li.textContent = option;
      if (correct.has(index)) li.className = "correct";
      if (selected.has(index) && !correct.has(index)) li.classList.add("picked");
      options.appendChild(li);
    });
    card.appendChild(options);
    appendSolution(card, item);

    const meta = document.createElement("div");
    meta.className = "meta";
    if (item.status === "correct" || item.status === "wrong" || item.status === "legacy") {
      let answered = "";
      const selectedTexts = Array.isArray(item.selectedTexts)
        ? item.selectedTexts.map(String)
        : [];
      if (Array.isArray(item.selectedIndices) && item.selectedIndices.length) {
        answered = item.selectedIndices
          .map((index, position) =>
            optionLetter(index) + " " + (selectedTexts[position] || ""))
          .join("；");
      } else if (item.selectedIndex !== null && item.selectedIndex !== undefined &&
                 item.selectedText) {
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
    if (bankScope === "wrong" && item.cacheId) {
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "btn danger small bank-wrong-remove";
      remove.textContent = t("bankWrongRemove");
      remove.addEventListener("click", () => removeWrongItem(item));
      card.appendChild(remove);
    }
    return card;
  }

  /**
   * Removing one row from the wrong book clears only that member's answer
   * state. The question itself stays in whichever bank owns it.
   */
  async function removeWrongItem(item) {
    if (!item || !item.cacheId || bankClearBusy) return;
    bankClearBusy = true;
    try {
      const data = await api("/api/quiz/admin/bank/clear", {
        method: "POST",
        admin: true,
        body: { scope: "wrong", mode: "wrong", cacheId: item.cacheId },
      });
      applyClearStats(data);
      $("bankError").textContent = t("clearWrongOneDone");
      await loadAdminSession();
      if (activeTab === "bank") loadBank(true);
    } catch (error) {
      $("bankError").textContent = errorText(error);
    } finally {
      bankClearBusy = false;
    }
  }

  function renderBankList(list, items, emptyKey, hasMore, loading) {
    if (!list) return;
    list.replaceChildren();
    if (!items.length) {
      // AI and manual scopes show their single empty-state action below; a
      // second card with the member's name would only repeat the summary.
      if (!loading && !emptyKey) return;
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
    if (!["builtin", "ai", "manual", "wrong"].includes(bankScope)) bankScope = "builtin";
    document.querySelectorAll("#bankScopes [data-bank-scope]").forEach((button) => {
      button.setAttribute("aria-selected", String(button.dataset.bankScope === bankScope));
    });
    const name = editingMemberName() || activeMemberName();
    const count = bankScopeCounts[bankScope] || 0;
    const summaryKey = bankScope === "builtin" ? "bankScopeSummaryBuiltin"
      : bankScope === "ai" ? "bankScopeSummaryAi"
        : bankScope === "manual" ? "bankScopeSummaryManual" : "bankScopeSummaryWrong";
    if ($("bankScopeSummary")) {
      $("bankScopeSummary").textContent = t(summaryKey, {
        name,
        total: count,
        tags: importCollections.length,
        n: bankScopeCounts.wrong,
      });
    }
    renderBankManualFilters();
    renderBankScopeActions();
    if ($("bankSourceCounts")) {
      $("bankSourceCounts").textContent =
        bankScope === "ai" || bankScope === "manual"
          ? t("bankPrivateCapacity", {
            used: privateUsed(),
            capacity: privateCapacity,
            remaining: privateRemaining,
          })
          : "";
    }
    // Built-in and wrong-book scopes keep a plain empty line. AI and manual
    // scopes replace it with one action: generate or import.
    const emptyKey = bankScope === "builtin" ? "bankEmptyBuiltin"
      : bankScope === "wrong" ? "bankEmptyWrong" : "";
    renderBankList($("bankList"), bankItems, emptyKey, bankHasMore, bankLoading);
    $("bankMore").classList.toggle("hidden", !bankHasMore);
    $("bankMore").disabled = bankLoading;
    const empty = !bankItems.length && !bankLoading;
    if ($("bankEmptyActions")) {
      const actionScope = bankScope === "ai" || bankScope === "manual";
      $("bankEmptyActions").classList.toggle(
        "hidden",
        !empty || !actionScope,
      );
      const generate = $("bankEmptyGenerate");
      if (generate) generate.classList.toggle("hidden", bankScope !== "ai");
      const importButton = $("bankEmptyImport");
      if (importButton) importButton.classList.toggle("hidden", bankScope !== "manual");
    }
  }

  function renderBankManualFilters() {
    const row = $("bankManualFilters");
    if (!row) return;
    row.replaceChildren();
    if (bankScope !== "manual") return;
    const makeChip = (id, label, count) => {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = "choice-chip";
      chip.setAttribute("aria-pressed", String((bankManualCollectionId || "") === id));
      chip.textContent = label + " · " + count;
      chip.addEventListener("click", () => {
        bankManualCollectionId = id;
        renderBank();
        loadBank(true);
      });
      row.appendChild(chip);
    };
    makeChip("", t("bankScopeTagAll"), bankScopeCounts.manual);
    importCollections.forEach((collection) => {
      makeChip(collection.id, collection.name, collection.total);
    });
  }

  function renderBankScopeActions() {
    const actions = $("bankScopeActions");
    if (!actions) return;
    actions.replaceChildren();
    const add = (label, className, count, onClick) => {
      // An empty scope has nothing to clear; do not leave a dead red button
      // above the empty-state action.
      if (count <= 0) return;
      const node = document.createElement("button");
      node.type = "button";
      node.className = "btn " + className + " small";
      node.textContent = label;
      node.addEventListener("click", onClick);
      actions.appendChild(node);
    };
    if (bankScope === "builtin") {
      add(t("bankScopeClear"), "danger", bankScopeCounts.builtin, () => requestBankClear({
        scope: "builtin", count: bankScopeCounts.builtin,
      }));
      return;
    }
    if (bankScope === "ai") {
      add(t("bankScopeClear"), "danger", bankScopeCounts.ai, () => requestBankClear({
        scope: "ai", count: bankScopeCounts.ai,
      }));
      return;
    }
    if (bankScope === "manual") {
      const selected = importCollections.find((item) => item.id === bankManualCollectionId);
      add(t("bankScopeClear"), "danger",
        selected ? selected.total : bankScopeCounts.manual,
        () => requestBankClear({
          scope: "manual",
          collectionId: selected ? selected.id : null,
          name: selected ? selected.name : "",
          count: selected ? selected.total : bankScopeCounts.manual,
        }));
      return;
    }
    add(t("bankScopeClear"), "danger", bankScopeCounts.wrong, () => requestBankClear({
      scope: "wrong", count: bankScopeCounts.wrong,
    }));
  }

  function renderBankManage() {
    if (!$("bankManagePage")) return;
    const manual = bankManageScope === "manual";
    const name = editingMemberName() || activeMemberName();
    $("bankManageTitle").textContent =
      t(manual ? "bankManageTitleManual" : "bankManageTitleBuiltin");
    $("bankManageLead").textContent =
      t(manual ? "bankManageLeadManual" : "bankManageLeadBuiltin", { name });
    $("bankManageBuiltinCard").classList.toggle("hidden", manual);
    $("bankManageManualCard").classList.toggle("hidden", !manual);
    if (manual) {
      $("bankManageManualMeta").textContent = t("bankManageManualMeta", {
        n: bankScopeCounts.manual,
        tags: importCollections.length,
      });
      const list = $("bankManageTagList");
      list.replaceChildren();
      importCollections.forEach((collection) => {
        const row = document.createElement("div");
        row.className = "setting-row static";
        const main = document.createElement("span");
        main.className = "setting-main";
        const label = document.createElement("span");
        label.textContent = collection.name;
        const meta = document.createElement("span");
        meta.className = "setting-value";
        meta.textContent = t("bankManageTagMeta", {
          n: collection.total,
          unused: collection.unused,
        });
        main.append(label, meta);
        const clear = document.createElement("button");
        clear.type = "button";
        clear.className = "btn danger small";
        clear.textContent = t("bankManageTagClear");
        clear.setAttribute("aria-label", t("bankClearScopeManualTag", { name: collection.name }));
        clear.disabled = collection.total <= 0;
        clear.addEventListener("click", () => requestBankClear({
          scope: "manual",
          collectionId: collection.id,
          name: collection.name,
          count: collection.total,
        }));
        row.append(main, clear);
        list.appendChild(row);
      });
      $("bankManageTagEmpty").classList.toggle("hidden", importCollections.length > 0);
      const clearAll = $("bankManageManualClear");
      clearAll.classList.toggle("hidden", bankScopeCounts.manual <= 0);
      clearAll.disabled = bankScopeCounts.manual <= 0;
    } else {
      $("bankManageBuiltinMeta").textContent = t("bankManageBuiltinMeta", {
        n: bankScopeCounts.builtin,
      });
      $("bankManageBuiltinClear").disabled = bankScopeCounts.builtin <= 0;
    }
    renderBankCounts();
  }

  function openBankManage(scope) {
    bankManageScope = scope === "manual" ? "manual" : "builtin";
    bankManageReturnOverlay = parentTabOpen();
    if (bankManageReturnOverlay) {
      if (window.ParentCenterUI && window.ParentCenterUI.suspend) {
        window.ParentCenterUI.suspend();
      } else if (window.ParentCenterUI && window.ParentCenterUI.close) {
        window.ParentCenterUI.close();
      }
    }
    setTab("bank", true);
    showBankPage("manage");
    $("bankManageState").textContent = "";
    $("bankManageError").textContent = "";
    renderBankManage();
    loadAdminSession().catch((error) => {
      if ($("bankManageError")) $("bankManageError").textContent = errorText(error);
    });
    window.scrollTo({ top: 0 });
  }

  function backFromBankManage() {
    $("bankManageState").textContent = "";
    $("bankManageError").textContent = "";
    if (bankManageReturnOverlay) {
      bankManageReturnOverlay = false;
      // Reopen the suspended parent overlay; the quiz session is still valid.
      if (window.ParentCenterUI && window.ParentCenterUI.open) {
        window.ParentCenterUI.open();
        return;
      }
    }
    showBankPage("home");
    loadBank(true);
    window.scrollTo({ top: 0 });
  }

  // Manual import keeps the tag lifecycle separate from the question write:
  // a dry run never creates a tag, and a real import removes a just-created
  // tag again if the strict cache write fails.
  // The tag input is the only target source. Suggestions are a shortcut that
  // fills the input with an existing name; there is no hidden selected tag id.
  function normalizeTagName(value) {
    return String(value || "").trim();
  }

  function tagNameLength(value) {
    return Array.from(String(value || "")).length;
  }

  function importTagName() {
    return $("importTagName") ? normalizeTagName($("importTagName").value) : "";
  }

  function importTagState() {
    const name = importTagName();
    if (!name) return { name: "", empty: true };
    const collection = importCollections.find(
      (item) => item.name.toLowerCase() === name.toLowerCase(),
    );
    if (collection) return { collection, name: collection.name, isNew: false };
    const error = validateNewTagName(name);
    return error ? { name, isNew: true, error } : { name, isNew: true };
  }

  function importTarget() {
    const state = importTagState();
    if (state.empty || state.error) return null;
    return state.collection
      ? { collection: state.collection, name: state.collection.name, isNew: false }
      : { name: state.name, isNew: true };
  }

  function updateImportTargetHint() {
    if (!$("importTargetHint")) return;
    const state = importTagState();
    const hint = $("importTargetHint");
    hint.classList.remove("empty", "warn");
    if (state.error) {
      hint.textContent = state.error;
      hint.classList.add("warn");
      return;
    }
    if (state.collection) {
      hint.textContent = t("importTargetHint", { name: state.name });
      return;
    }
    if (state.isNew) {
      hint.textContent = t("importTargetNewHint", { name: state.name });
      return;
    }
    hint.classList.add("empty");
    hint.textContent = importCollections.length
      ? t("importTargetNone")
      : t("importNoTagsHint");
  }

  function importTagMatches() {
    const query = importTagName().toLowerCase();
    if (!query) return importCollections.slice();
    const matches = importCollections.filter(
      (item) => item.name.toLowerCase().includes(query),
    );
    const prefix = matches.filter(
      (item) => item.name.toLowerCase().startsWith(query),
    );
    const rest = matches.filter(
      (item) => !item.name.toLowerCase().startsWith(query),
    );
    return prefix.concat(rest);
  }

  function renderImportTagSuggest() {
    const list = $("importTagSuggestList");
    if (!list) return;
    const state = importTagState();
    importTagSuggestItems = importTagMatches();
    importTagActiveIndex = -1;
    list.replaceChildren();
    importTagSuggestItems.forEach((collection, index) => {
      const selected = Boolean(
        state.collection && state.collection.id === collection.id,
      );
      const row = document.createElement("button");
      row.type = "button";
      row.id = "importTagOption" + index;
      row.className = "tag-suggest-row";
      row.dataset.tagId = collection.id;
      row.setAttribute("role", "option");
      row.setAttribute("tabindex", "-1");
      row.setAttribute("aria-selected", String(selected));
      row.title = collection.name;
      const name = document.createElement("span");
      name.className = "tag-suggest-name";
      name.textContent = collection.name;
      const meta = document.createElement("span");
      meta.className = "tag-suggest-meta";
      meta.textContent = t("importTagCount", { n: collection.total });
      if (selected) {
        const check = document.createElement("span");
        check.className = "tag-suggest-check";
        check.textContent = "✓";
        check.setAttribute("aria-hidden", "true");
        row.append(name, check, meta);
      } else {
        row.append(name, meta);
      }
      row.addEventListener("click", () => chooseImportTag(collection));
      list.appendChild(row);
    });
    const foot = $("importTagSuggestFoot");
    if (foot) {
      foot.classList.remove("warn", "hidden");
      if (state.error) {
        foot.textContent = state.error;
        foot.classList.add("warn");
      } else if (!importTagName()) {
        foot.textContent = t("importTagSuggestEmpty");
      } else if (state.isNew) {
        foot.textContent = t("importTagSuggestNew", { name: state.name });
      } else {
        foot.textContent = "";
        foot.classList.add("hidden");
      }
    }
    if ($("importTagName")) $("importTagName").removeAttribute("aria-activedescendant");
  }

  function fitImportTagSuggest() {
    const panel = $("importTagSuggest");
    const field = $("importTagField");
    if (!panel || !field || panel.classList.contains("hidden")) return;
    const viewport = window.visualViewport;
    const bottom = viewport
      ? viewport.offsetTop + viewport.height
      : window.innerHeight;
    const rect = field.getBoundingClientRect();
    const available = Math.max(
      96,
      Math.min(220, Math.floor(bottom - rect.bottom - 12)),
    );
    panel.style.setProperty("--tag-suggest-max", available + "px");
  }

  function openImportTagSuggest() {
    const panel = $("importTagSuggest");
    const input = $("importTagName");
    if (!panel || !input || !$("importTagSuggestList")) return;
    if (!importCollections.length && !importTagName()) {
      closeImportTagSuggest();
      return;
    }
    panel.classList.remove("hidden");
    input.setAttribute("aria-expanded", "true");
    renderImportTagSuggest();
    fitImportTagSuggest();
  }

  function closeImportTagSuggest() {
    const panel = $("importTagSuggest");
    const input = $("importTagName");
    if (!panel) return;
    panel.classList.add("hidden");
    if (input) {
      input.setAttribute("aria-expanded", "false");
      input.removeAttribute("aria-activedescendant");
    }
    importTagSuggestItems = [];
    importTagActiveIndex = -1;
  }

  function setImportTagActive(index) {
    importTagActiveIndex = index;
    const rows = $("importTagSuggestList")
      ? $("importTagSuggestList").querySelectorAll(".tag-suggest-row")
      : [];
    rows.forEach((row, rowIndex) => {
      row.classList.toggle("active", rowIndex === index);
    });
    const input = $("importTagName");
    if (!input) return;
    if (index >= 0 && rows[index]) {
      input.setAttribute("aria-activedescendant", rows[index].id);
    } else {
      input.removeAttribute("aria-activedescendant");
    }
  }

  function chooseImportTag(collection) {
    const input = $("importTagName");
    if (!input || !collection) return;
    input.value = collection.name;
    $("clearImportTag").classList.remove("hidden");
    closeImportTagSuggest();
    input.blur();
    invalidateImportPreview();
    updateImportTargetHint();
  }

  function clearImportTagName() {
    const input = $("importTagName");
    if (!input) return;
    input.value = "";
    $("clearImportTag").classList.add("hidden");
    closeImportTagSuggest();
    invalidateImportPreview();
    updateImportTargetHint();
    input.focus();
    openImportTagSuggest();
  }

  function onImportTagInput() {
    const input = $("importTagName");
    if (!input) return;
    $("clearImportTag").classList.toggle("hidden", !normalizeTagName(input.value));
    // Filtering on pinyin letters would show a misleading "new tag" state;
    // wait for compositionend before resolving the target.
    if (importTagComposing) return;
    invalidateImportPreview();
    updateImportTargetHint();
    if (document.activeElement === input ||
        !$("importTagSuggest").classList.contains("hidden")) {
      openImportTagSuggest();
    }
  }

  function onImportTagBlur() {
    setTimeout(() => {
      const field = $("importTagField");
      const active = document.activeElement;
      if (!field || !active || !field.contains(active)) {
        closeImportTagSuggest();
      }
    }, 140);
  }

  function onImportTagKeydown(event) {
    if (event.isComposing) return;
    const panelOpen = !$("importTagSuggest").classList.contains("hidden");
    if (event.key === "Escape") {
      if (panelOpen) {
        event.preventDefault();
        closeImportTagSuggest();
      }
      return;
    }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      if (!panelOpen) openImportTagSuggest();
      if (!importTagSuggestItems.length) return;
      event.preventDefault();
      const delta = event.key === "ArrowDown" ? 1 : -1;
      const next = importTagActiveIndex < 0
        ? (delta > 0 ? 0 : importTagSuggestItems.length - 1)
        : (importTagActiveIndex + delta + importTagSuggestItems.length) %
          importTagSuggestItems.length;
      setImportTagActive(next);
      const rows = $("importTagSuggestList")
        ? $("importTagSuggestList").querySelectorAll(".tag-suggest-row")
        : [];
      if (rows[next] && rows[next].scrollIntoView) {
        rows[next].scrollIntoView({ block: "nearest" });
      }
      return;
    }
    if (event.key === "Enter" && panelOpen) {
      if (importTagActiveIndex >= 0 && importTagSuggestItems[importTagActiveIndex]) {
        event.preventDefault();
        chooseImportTag(importTagSuggestItems[importTagActiveIndex]);
      } else {
        closeImportTagSuggest();
      }
    }
  }

  // The prompt panel is shared by both copy buttons: only the prompt that was
  // last copied is shown, so the page never grows two long blocks.
  function renderImportPrompt() {
    const kind = importPromptKind;
    const visible = kind === "convert" || kind === "generate";
    $("importPromptPanel").classList.toggle("hidden", !visible);
    $("copyConvertPrompt").setAttribute("aria-pressed", String(kind === "convert"));
    $("copyGeneratePrompt").setAttribute("aria-pressed", String(kind === "generate"));
    $("importPromptTitle").textContent = visible
      ? t(kind === "generate" ? "importPromptGenerateTitle" : "importPromptConvertTitle")
      : "";
    $("importPromptText").textContent = visible
      ? t(kind === "generate" ? "importGeneratePrompt" : "importConvertPrompt")
      : "";
  }

  function renderBankImport() {
    if (!$("importTagName")) return;
    $("clearImportTag").classList.toggle("hidden", !importTagName());
    $("importCloudNotice").classList.toggle("hidden", localBankActive);
    $("importFileName").textContent = importFileName;
    renderImportPrompt();
    document.querySelectorAll("#importDifficultyChips [data-import-difficulty]").forEach((button) => {
      button.setAttribute(
        "aria-pressed",
        String(button.dataset.importDifficulty === importDefaultDifficulty),
      );
    });
    $("parseImport").disabled = importBusy;
    $("parseImport").textContent = t(importBusy ? "importParsing" : "importParse");
    // Leaving the page mid-import would drop the remaining batches and hide
    // the progress, so the whole form stays frozen until the run finishes.
    ["importText", "importFile", "importTagName", "backFromImport", "clearImportText"]
      .forEach((id) => {
        if ($(id)) $(id).disabled = importRunning;
      });
    document.querySelectorAll("#importDifficultyChips [data-import-difficulty]").forEach((button) => {
      button.disabled = importRunning;
    });
    updateImportTargetHint();
    if (!$("importTagSuggest").classList.contains("hidden")) {
      renderImportTagSuggest();
    }
    renderImportPreview();
    renderImportProgress();
    renderImportResult();
  }

  function clearImportPayload() {
    importFileName = "";
    importFileText = null;
    importPromptKind = "";
    importPreviewPage = 0;
    importProgress = null;
    importCancelRequested = false;
    closeImportTagSuggest();
    $("importText").value = "";
    $("importFile").value = "";
    $("importFileName").textContent = "";
    $("importCopyState").textContent = "";
    setImportError("", []);
    renderImportPrompt();
  }

  // Clear both the paste box and any chosen file so a rejected batch can be
  // regenerated and pasted without the old source coming back. The target tag
  // and default difficulty stay selected for the retry.
  function clearImportInput() {
    if (importRunning) return;
    clearImportPayload();
    invalidateImportPreview();
    renderBankImport();
    $("importText").focus();
  }

  function openBankImport() {
    if (importRunning) return;
    // Every entry starts with a clean payload. Keep the target tag and default
    // difficulty so several batches can be imported to the same tag in a row.
    importPreview = null;
    importResult = null;
    clearImportPayload();
    showBankPage("import");
    renderBankImport();
  }

  function backFromImport() {
    if (importRunning) return;
    closeImportTagSuggest();
    setImportError("", []);
    if (importResult) resetImportState();
    const returnToTagSource = tagSourceReturnAfterImport;
    tagSourceReturnAfterImport = false;
    if (returnToTagSource) {
      // Import success already reloaded the session, so the tag list is fresh.
      // The selected ids are intentionally not cleared: the parent can pick the
      // imported tag immediately, and nothing is written until "Save level".
      setTab("generate", true);
      showGenPage("tagSource");
      return;
    }
    showBankPage("home");
    loadBank(true);
  }

  function importErrorLine(item) {
    const field = importFieldLabel(item.field);
    const code = importCodeText(item.code);
    const wholeFile = item.field === "items" || item.field === "text";
    const prefix = !wholeFile && Number(item.index) >= 0
      ? t("importErrorAt", { n: Number(item.index) + 1 })
      : "";
    return [prefix, field, code].filter(Boolean).join(" · ");
  }

  function renderImportErrors(errors) {
    const list = $("importErrorList");
    if (!list) return;
    list.replaceChildren();
    (errors || []).forEach((item) => {
      const li = document.createElement("li");
      li.textContent = importErrorLine(item);
      list.appendChild(li);
    });
    list.classList.toggle("hidden", !errors || !errors.length);
  }

  function updateImportErrorCopy() {
    const actions = $("importErrorActions");
    if (!actions) return;
    const show = importErrorInfo.copyable &&
      Boolean(importErrorInfo.message || importErrorInfo.errors.length);
    actions.classList.toggle("hidden", !show);
    if (!show) $("importErrorCopyState").textContent = "";
  }

  function setImportError(message, errors, copyable) {
    importErrorInfo = {
      message: String(message || ""),
      errors: Array.isArray(errors) ? errors : [],
      // Any structured validation error is about the question data; parse
      // failures that a model can repair pass copyable explicitly.
      copyable: Boolean(copyable) || (Array.isArray(errors) && errors.length > 0),
    };
    $("importError").textContent = importErrorInfo.message;
    renderImportErrors(importErrorInfo.errors);
    updateImportErrorCopy();
  }

  function importFieldLabel(field) {
    const key = "importField_" + String(field || "");
    const text = t(key);
    return text === key ? String(field || "") : text;
  }

  function importCodeText(code) {
    const key = "importCode_" + String(code || "");
    const text = t(key);
    return text === key ? String(code || "") : text;
  }

  function showImportError(error) {
    const data = error && error.data ? error.data : null;
    let message = errorText(error);
    if (error && error.code === "BANK_FULL" && data && data.remaining !== undefined) {
      message = t("err_BANK_FULL") + " " + t("importPreviewRemainingValue", {
        remaining: Number(data.remaining || 0),
        capacity: Number(data.capacity || bankCapacity),
      });
    }
    setImportError(message, data && Array.isArray(data.errors) ? data.errors : []);
  }

  // A repaired answer must be complete. The error lines say what is wrong but not
  // which data to fix, so the copy includes the original payload when it is small
  // enough for a clipboard; a whole 16 MB file would be too large to paste.
  const IMPORT_COPY_SOURCE_MAX = 200000;

  function importErrorCopyText() {
    if (!importErrorInfo.copyable) return "";
    const lines = importErrorInfo.errors.map(importErrorLine);
    const source = normalizeImportText(importSourceRaw());
    const parts = [t("importErrorCopyIntro")];
    if (importErrorInfo.message) parts.push(importErrorInfo.message);
    if (lines.length) parts.push(lines.map((line) => "- " + line).join("\n"));
    parts.push(t("importErrorCopyAsk"));
    if (source) {
      parts.push(source.length <= IMPORT_COPY_SOURCE_MAX
        ? t("importErrorCopySource") + "\n" + source
        : t("importErrorCopySourceOmitted"));
    }
    return parts.join("\n\n");
  }

  async function copyImportErrors() {
    const text = importErrorCopyText();
    if (!text) return;
    const state = $("importErrorCopyState");
    try {
      await copyText(text);
      state.textContent = t("importErrorCopied");
      setTimeout(() => {
        if (state.textContent === t("importErrorCopied")) state.textContent = "";
      }, 3200);
    } catch (_) {
      state.textContent = t("importErrorCopyFailed");
    }
  }

  function normalizeImportText(raw) {
    let text = String(raw || "").replace(/^﻿/, "").trim();
    const fence = /^```(?:json)?\s*\n?([\s\S]*?)\n?```$/i.exec(text);
    if (fence) text = fence[1].trim();
    return text;
  }

  function utf8Bytes(text) {
    try {
      return new Blob([text]).size;
    } catch (_) {
      return unescape(encodeURIComponent(text)).length;
    }
  }

  function importSourceRaw() {
    return importFileText !== null ? importFileText : String($("importText").value || "");
  }

  const TEXT_QUESTION_NUMBER = /^\s*(?:(\d+)[.、．)）]|【(\d+)】)\s*(.*)$/;
  const TEXT_OPTION_LINE = /^\s*([A-Fa-f])[.、．:：)）]\s*(.*)$/;
  const TEXT_ANSWER_LINE = /^\s*(?:参考答案|正确答案|答案)\s*[:：]\s*(.*)$/;

  // Presentation-only splitter. The TV parser remains authoritative; this
  // only decides batch boundaries so a batch never starts mid-question.
  function parseTextBlocks(text) {
    const normalized = String(text || "")
      .replace(/\r\n/g, "\n").replace(/\r/g, "\n").replace(/^﻿/, "").trim();
    if (!normalized) return null;
    const blocks = [];
    let current = null;
    for (const rawLine of normalized.split("\n")) {
      const line = rawLine.replace(/\s+$/, "");
      const match = TEXT_QUESTION_NUMBER.exec(line);
      if (match) {
        if (current) blocks.push(current);
        current = { lines: [line], promptLine: String(match[3] || "").trim() };
      } else if (!current) {
        if (line.trim()) return null;
      } else {
        current.lines.push(line);
      }
    }
    if (current) blocks.push(current);
    return blocks.length ? blocks : null;
  }

  // Mirrors the server's text-answer rule closely enough for a preview. The
  // server parser stays authoritative; this never rewrites the answer.
  const PREVIEW_MULTI_ANSWER = /^[A-F](?:\s*(?:[、,，/&+;；和与及]|and|or)?\s*[A-F])+$/i;

  function parsePreviewAnswer(raw, letters, options) {
    const value = String(raw || "").trim();
    const exact = options.indexOf(value);
    if (exact >= 0) {
      return { answerIndex: exact, answerIndices: [], type: "single" };
    }
    if (/^[A-Fa-f]$/.test(value)) {
      const index = letters.indexOf(value.toUpperCase());
      if (index >= 0) {
        return { answerIndex: index, answerIndices: [], type: "single" };
      }
    }
    if (PREVIEW_MULTI_ANSWER.test(value)) {
      const selected = value.toUpperCase().match(/[A-F]/g) || [];
      const indices = selected.map((letter) => letters.indexOf(letter));
      if (indices.length >= 2 &&
          indices.every((index) => index >= 0) &&
          new Set(indices).size === indices.length) {
        const sorted = indices.sort((a, b) => a - b);
        return { answerIndex: sorted[0], answerIndices: sorted, type: "multiple" };
      }
    }
    return { answerIndex: 0, answerIndices: [], type: "single" };
  }

  function previewTextBlock(block) {
    const lines = (block.lines || []).slice();
    const first = lines.shift() || "";
    const match = TEXT_QUESTION_NUMBER.exec(first);
    const promptLines = match && match[3].trim() ? [match[3].trim()] : [];
    const options = [];
    const letters = [];
    let answerRaw = "";
    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line) continue;
      const answer = TEXT_ANSWER_LINE.exec(line);
      if (answer) {
        answerRaw = answer[1].trim();
        break;
      }
      const option = TEXT_OPTION_LINE.exec(line);
      if (option) {
        letters.push(option[1].toUpperCase());
        options.push(option[2].trim());
      } else if (!options.length) {
        promptLines.push(line);
      }
    }
    const answer = parsePreviewAnswer(answerRaw, letters, options);
    return {
      prompt: promptLines.join("\n"),
      options,
      type: answer.type,
      answerIndex: answer.answerIndex,
      answerIndices: answer.answerIndices,
      explanation: "",
      difficulty: importDefaultDifficulty,
    };
  }

  function buildImportBatches(format, items, blocks) {
    const batches = [];
    if (format === "json") {
      let current = [];
      let bytes = 0;
      let startIndex = 0;
      const flush = () => {
        if (!current.length) return;
        batches.push({ format: "json", items: current, count: current.length, startIndex, bytes });
        startIndex += current.length;
        current = [];
        bytes = 0;
      };
      for (const item of items) {
        const itemBytes = utf8Bytes(JSON.stringify(item));
        if (current.length && (
          current.length >= IMPORT_MAX_BATCH_ITEMS ||
          bytes + itemBytes > IMPORT_MAX_BATCH_BYTES
        )) {
          flush();
        }
        current.push(item);
        bytes += itemBytes;
      }
      flush();
      return batches;
    }
    let current = [];
    let bytes = 0;
    let startIndex = 0;
    const flush = () => {
      if (!current.length) return;
      const text = current.map((block) => block.lines.join("\n")).join("\n\n");
      batches.push({ format: "text", text, count: current.length, startIndex, bytes });
      startIndex += current.length;
      current = [];
      bytes = 0;
    };
    for (const block of blocks) {
      const blockBytes = utf8Bytes(block.lines.join("\n")) + 2;
      if (current.length && (
        current.length >= IMPORT_MAX_BATCH_ITEMS ||
        bytes + blockBytes > IMPORT_MAX_BATCH_BYTES
      )) {
        flush();
      }
      current.push(block);
      bytes += blockBytes;
    }
    flush();
    return batches;
  }

  function formatImportSize(bytes) {
    const value = Number(bytes || 0);
    if (value >= 1024 * 1024) return (value / (1024 * 1024)).toFixed(1) + " MB";
    if (value >= 1024) return Math.round(value / 1024) + " KB";
    return value + " B";
  }

  function readImportPayload() {
    const raw = importSourceRaw();
    if (!raw.trim()) return { error: t("importEmptyInput") };
    const bytes = utf8Bytes(raw);
    if (bytes > IMPORT_MAX_BYTES) return { error: t("importTooLarge") };
    const text = normalizeImportText(raw);
    let format = "text";
    let items = null;
    let blocks = null;
    if (text.startsWith("{") || text.startsWith("[")) {
      format = "json";
      let parsed;
      try {
        parsed = JSON.parse(text);
      } catch (_) {
        return { error: t("importJsonInvalid"), copyable: true };
      }
      items = Array.isArray(parsed)
        ? parsed
        : (parsed && Array.isArray(parsed.items) ? parsed.items : null);
      if (!items) return { error: t("importJsonInvalid"), copyable: true };
      if (items.length === 0) return { error: t("importNoQuestions"), copyable: true };
      if (items.length > IMPORT_MAX_ITEMS) {
        return { error: t("importTooMany", { max: IMPORT_MAX_ITEMS }), copyable: true };
      }
    } else {
      blocks = parseTextBlocks(text);
      if (!blocks) return { error: t("importNoQuestions"), copyable: true };
      if (blocks.length > IMPORT_MAX_ITEMS) {
        return { error: t("importTooMany", { max: IMPORT_MAX_ITEMS }), copyable: true };
      }
    }
    const total = format === "json" ? items.length : blocks.length;
    const batches = buildImportBatches(format, items, blocks);
    return { format, items, blocks, total, bytes, batches };
  }

  async function loadImportFile(file) {
    if (!file) return;
    if (file.size > IMPORT_MAX_BYTES) {
      $("importFile").value = "";
      importFileName = "";
      importFileText = null;
      $("importFileName").textContent = "";
      setImportError(t("importTooLarge"), []);
      return;
    }
    try {
      const text = await file.text();
      importFileText = text;
      // Keep the textarea empty for a file-backed import. Putting a multi-MB
      // string into an editable field makes the phone freeze before parsing.
      $("importText").value = "";
      importFileName = file.name || "";
      $("importFileName").textContent = importFileName;
      setImportError("", []);
      invalidateImportPreview();
      updateImportTargetHint();
    } catch (_) {
      importFileText = null;
      setImportError(t("importFileReadFailed"), []);
    }
  }

  function importBatchBody(target, batch) {
    const body = {
      defaultDifficulty: importDefaultDifficulty,
      dryRun: false,
      batchMode: true,
    };
    if (target.isNew) body.newCollectionName = target.name;
    else body.collectionId = target.collection.id;
    if (batch.format === "json") {
      body.format = "json";
      body.items = batch.items;
    } else {
      body.format = "text";
      body.text = batch.text;
    }
    return body;
  }

  function invalidateImportPreview() {
    if (importRunning) return;
    if (!importPreview && !importResult && !importProgress && !importErrorInfo.message) return;
    importPreview = null;
    importResult = null;
    importProgress = null;
    importPreviewPage = 0;
    setImportError("", []);
    renderImportPreview();
    renderImportProgress();
    renderImportResult();
    updateImportTargetHint();
  }

  async function parseImport() {
    if (importBusy || importRunning) return;
    setImportError("", []);
    const targetState = importTagState();
    if (targetState.empty || targetState.error) {
      setImportError(targetState.error || t("importSelectTagFirst"), []);
      $("importTagName").focus();
      openImportTagSuggest();
      return;
    }
    const target = importTarget();
    if (!target) {
      setImportError(t("importSelectTagFirst"), []);
      return;
    }
    if (target.isNew) {
      const nameError = validateNewTagName(target.name);
      if (nameError) {
        setImportError(nameError, []);
        return;
      }
    }
    const payload = readImportPayload();
    if (payload.error) {
      setImportError(payload.error, [], payload.copyable);
      return;
    }
    importPreview = {
      target,
      format: payload.format,
      items: payload.items,
      blocks: payload.blocks,
      total: payload.total,
      bytes: payload.bytes,
      batches: payload.batches,
    };
    // Re-parsing after a partial run starts a new preview; the old result and
    // its error list must not linger next to the new confirm button.
    importResult = null;
    importProgress = null;
    importPreviewPage = 0;
    setImportError("", []);
    renderBankImport();
  }

  async function confirmImport() {
    if (importBusy || importRunning || !importPreview) return;
    const preview = importPreview;
    if (!preview.batches || !preview.batches.length) return;
    importRunning = true;
    importBusy = true;
    importCancelRequested = false;
    setImportError("", []);
    let target = preview.target;
    const aggregate = {
      collection: target.isNew ? null : target.collection,
      targetName: target.name,
      appended: 0,
      fileDuplicates: 0,
      collectionDuplicates: 0,
      crossSourceDuplicates: 0,
      rejected: 0,
      remaining: Math.max(0, bankCapacity - currentCounts().total),
      capacity: bankCapacity,
    };
    const progress = {
      processed: 0,
      total: preview.total,
      batch: 0,
      batches: preview.batches.length,
      appended: 0,
      fileDuplicates: 0,
      collectionDuplicates: 0,
      crossSourceDuplicates: 0,
      rejected: 0,
    };
    importProgress = progress;
    let failure = null;
    let cancelled = false;
    renderBankImport();
    try {
      for (let index = 0; index < preview.batches.length; index += 1) {
        if (importCancelRequested) {
          cancelled = true;
          break;
        }
        const batch = preview.batches[index];
        progress.batch = index + 1;
        renderImportProgress();
        try {
          const data = await api("/api/quiz/admin/bank/import", {
            method: "POST",
            admin: true,
            body: importBatchBody(target, batch),
          });
          if (index === 0 && target.isNew && data.collection) {
            target = {
              isNew: false,
              collection: data.collection,
              name: data.collection.name,
            };
            preview.target = target;
          }
          if (data.collection) {
            aggregate.collection = data.collection;
            aggregate.targetName = data.collection.name;
          }
          const added = Number(data.appended || 0);
          const fileDup = Number(data.fileDuplicates || 0);
          const collectionDup = Number(data.collectionDuplicates || 0);
          const crossDup = Number(data.crossSourceDuplicates || 0);
          const rejected = Number(data.rejected || 0);
          aggregate.appended += added;
          aggregate.fileDuplicates += fileDup;
          aggregate.collectionDuplicates += collectionDup;
          aggregate.crossSourceDuplicates += crossDup;
          aggregate.rejected += rejected;
          aggregate.remaining = Number(data.remaining ?? aggregate.remaining);
          aggregate.capacity = Number(data.capacity || aggregate.capacity);
          progress.appended += added;
          progress.fileDuplicates += fileDup;
          progress.collectionDuplicates += collectionDup;
          progress.crossSourceDuplicates += crossDup;
          progress.rejected += rejected;
          progress.processed += batch.count;
        } catch (error) {
          if (error && error.data && Array.isArray(error.data.errors)) {
            const offset = Number(batch.startIndex || 0);
            error.data.errors = error.data.errors.map((item) => Object.assign({}, item, {
              index: Number(item.index) >= 0 ? Number(item.index) + offset : Number(item.index),
            }));
          }
          failure = { error, batch: index + 1 };
          break;
        }
        renderImportProgress();
      }
    } finally {
      const completed = !failure && !cancelled;
      importResult = Object.assign({}, aggregate, {
        totalQuestions: preview.total,
        processed: progress.processed,
        cancelled,
        partial: Boolean(failure || cancelled),
        failedBatch: failure ? failure.batch : 0,
      });
      importPreview = null;
      importProgress = null;
      importRunning = false;
      importBusy = false;
      if (completed) {
        // Full success is one-shot. Partial runs keep the source and target so
        // the next attempt can retry; duplicate fingerprints make it safe.
        clearImportPayload();
        if ($("importTagName")) $("importTagName").value = "";
      }
      renderBankImport();
      if (failure) showImportError(failure.error);
      try {
        await loadAdminSession();
      } catch (_) {
        // Questions are already stored; a stale session only delays the tag
        // list and counts until the next refresh.
      }
      renderBankImport();
    }
  }

  function renderImportActions() {
    const bar = $("importActions");
    if (!bar) return;
    const hasPreview = Boolean(importPreview);
    const complete = Boolean(importResult) && !importResult.partial;
    // A partial run keeps the source text and target, so leave "Parse and
    // preview" available to retry. A complete run hides the bar entirely.
    bar.classList.toggle("hidden", complete || importRunning);
    $("parseImport").classList.toggle("hidden", hasPreview || importRunning);
    $("confirmImport").classList.toggle("hidden", !hasPreview || importRunning);
  }

  function renderImportPreview() {
    const card = $("importPreview");
    if (!card) return;
    const preview = importPreview;
    card.classList.toggle("hidden", !preview);
    renderImportActions();
    if (!preview) return;
    const target = preview.target || {};
    const targetName = target.name || (target.collection && target.collection.name) || "";
    $("previewTarget").textContent = targetName;
    if ($("previewTargetBadge")) {
      $("previewTargetBadge").textContent = target.isNew
        ? t("importTargetBadgeNew")
        : t("importTargetBadgeExisting");
      $("previewTargetBadge").classList.toggle("new", Boolean(target.isNew));
      $("previewTargetBadge").classList.toggle("hidden", !targetName);
    }
    $("previewTotal").textContent = String(Number(preview.total || 0));
    $("previewFormat").textContent = t(
      preview.format === "json" ? "importFormatJson" : "importFormatText",
    );
    $("previewFileSize").textContent = formatImportSize(preview.bytes || 0);
    $("previewBatches").textContent = String(
      Array.isArray(preview.batches) ? preview.batches.length : 0,
    );
    const remaining = Math.max(0, bankCapacity - currentCounts().total);
    $("previewRemaining").textContent = t("importPreviewRemainingValue", {
      remaining,
      capacity: bankCapacity,
    });
    $("importPreviewNote").textContent = t("importPreviewLocalNote", {
      max: IMPORT_MAX_ITEMS,
      size: formatImportSize(IMPORT_MAX_BYTES),
    });
    const pageCount = Math.max(1, Math.ceil(Number(preview.total || 0) / IMPORT_PREVIEW_LIMIT));
    importPreviewPage = Math.min(Math.max(0, importPreviewPage), pageCount - 1);
    const start = importPreviewPage * IMPORT_PREVIEW_LIMIT;
    const items = preview.format === "json"
      ? (preview.items || []).slice(start, start + IMPORT_PREVIEW_LIMIT)
      : (preview.blocks || []).slice(start, start + IMPORT_PREVIEW_LIMIT).map(previewTextBlock);
    renderImportPreviewItems(items);
    if ($("importPreviewPager")) {
      $("importPreviewPager").classList.toggle("hidden", pageCount <= 1);
      $("importPreviewPageLabel").textContent = t("importPreviewPage", {
        page: importPreviewPage + 1,
        pages: pageCount,
      });
      $("importPreviewPrev").disabled = importBusy || importPreviewPage <= 0;
      $("importPreviewNext").disabled = importBusy || importPreviewPage >= pageCount - 1;
      const input = $("importPreviewPageInput");
      if (input) {
        input.max = String(pageCount);
        if (document.activeElement !== input) input.value = String(importPreviewPage + 1);
      }
    }
    $("confirmImport").textContent = t("importConfirm", {
      n: Number(preview.total || 0),
    });
    $("confirmImport").disabled = importBusy || !preview.batches || !preview.batches.length;
  }

  function renderImportPreviewItems(items) {
    const host = $("importPreviewList");
    if (!host) return;
    host.replaceChildren();
    (items || []).forEach((item) => {
      // Preview is non-authoritative; a malformed entry is shown as empty and
      // rejected by the server parser rather than crashing the page.
      if (!item || typeof item !== "object") item = {};
      const card = document.createElement("article");
      card.className = "import-preview-item";
      const prompt = document.createElement("p");
      prompt.className = "prompt";
      prompt.textContent = String(item.prompt || "");
      card.appendChild(prompt);
      const answerIndices = Array.isArray(item.answerIndices) && item.answerIndices.length
        ? item.answerIndices.map(Number)
        : (item.answerIndex !== null && item.answerIndex !== undefined
          ? [Number(item.answerIndex)]
          : []);
      const correct = new Set(answerIndices);
      const options = document.createElement("ol");
      options.className = "bank-options";
      (Array.isArray(item.options) ? item.options : []).forEach((option, index) => {
        const li = document.createElement("li");
        li.textContent = String(option || "");
        if (correct.has(index)) li.className = "correct";
        options.appendChild(li);
      });
      card.appendChild(options);
      const meta = document.createElement("div");
      meta.className = "meta";
      meta.appendChild(questionTypeBadge(item));
      const difficulty = document.createElement("span");
      difficulty.textContent = t(
        DIFFICULTY_LABEL_KEYS[item.difficulty] || "difficultyMedium",
      );
      meta.appendChild(difficulty);
      card.appendChild(meta);
      const explanation = String(item.explanation || "").trim();
      if (explanation) {
        const details = document.createElement("details");
        details.className = "solution";
        const summary = document.createElement("summary");
        summary.textContent = t("solution");
        const body = document.createElement("p");
        body.textContent = explanation;
        details.append(summary, body);
        card.appendChild(details);
      }
      host.appendChild(card);
    });
  }

  function renderImportProgress() {
    const card = $("importProgress");
    if (!card) return;
    const progress = importProgress;
    card.classList.toggle("hidden", !progress || !importRunning);
    if (!progress || !importRunning) return;
    const percent = progress.total > 0
      ? Math.min(100, Math.round((progress.processed / progress.total) * 100))
      : 0;
    $("importProgressFill").style.width = percent + "%";
    $("importProgressCount").textContent = t("importProgressCount", {
      processed: progress.processed,
      total: progress.total,
    });
    $("importProgressBatch").textContent = t("importProgressBatch", {
      batch: progress.batch,
      batches: progress.batches,
    });
    $("importProgressStats").textContent = t("importProgressStats", {
      appended: progress.appended,
      file: progress.fileDuplicates,
      collection: progress.collectionDuplicates,
      cross: progress.crossSourceDuplicates,
      rejected: progress.rejected,
    });
    $("cancelImport").textContent = t(
      importCancelRequested ? "importCancelRequested" : "importCancel",
    );
    $("cancelImport").disabled = importCancelRequested;
  }

  function renderImportResult() {
    const card = $("importResult");
    if (!card) return;
    const result = importResult;
    card.classList.toggle("hidden", !result);
    renderImportActions();
    if (!result) return;
    const name = result.collection
      ? result.collection.name
      : (result.targetName || "");
    $("importResultTitle").textContent = t(
      result.partial ? "importResultPartialTitle" : "importResultTitle",
      { n: Number(result.appended || 0), name },
    );
    $("importResultFileDup").textContent = t("importResultDetail", {
      file: Number(result.fileDuplicates || 0),
      collection: Number(result.collectionDuplicates || 0),
    });
    const cross = Number(result.crossSourceDuplicates || 0);
    $("importResultCross").textContent = cross > 0
      ? t("importResultCross", { n: cross })
      : "";
    $("importResultRemaining").textContent = t("importResultRemaining", {
      remaining: Number(result.remaining || 0),
      capacity: Number(result.capacity || bankCapacity),
    });
    $("importResultProcessed").textContent = t("importResultProcessed", {
      processed: Number(result.processed || 0),
      total: Number(result.totalQuestions || 0),
    });
    let note = "";
    if (result.cancelled) {
      note = t("importResultCancelled");
    } else if (result.failedBatch) {
      note = t("importResultFailed", { batch: Number(result.failedBatch) });
    }
    $("importResultNote").textContent = note;
    $("importResultNote").classList.toggle("hidden", !note);
  }

  function changeImportPreviewPage(delta) {
    if (!importPreview || importBusy || importRunning) return;
    const pageCount = Math.max(
      1,
      Math.ceil(Number(importPreview.total || 0) / IMPORT_PREVIEW_LIMIT),
    );
    importPreviewPage = Math.min(
      Math.max(0, importPreviewPage + Number(delta || 0)),
      pageCount - 1,
    );
    renderImportPreview();
    $("importPreview").scrollIntoView({ block: "nearest" });
  }

  function jumpImportPreviewPage() {
    if (!importPreview || importBusy || importRunning) return;
    const pageCount = Math.max(
      1,
      Math.ceil(Number(importPreview.total || 0) / IMPORT_PREVIEW_LIMIT),
    );
    const requested = Number($("importPreviewPageInput").value);
    if (!Number.isInteger(requested)) return;
    importPreviewPage = Math.min(Math.max(0, requested - 1), pageCount - 1);
    renderImportPreview();
  }

  function requestImportCancel() {
    if (!importRunning) return;
    importCancelRequested = true;
    renderImportProgress();
  }

  function validateNewTagName(raw) {
    const name = normalizeTagName(raw);
    const length = tagNameLength(name);
    if (length < 1 || length > TAG_NAME_MAX) return t("importNewTagInvalid");
    if (importCollections.some(
      (item) => item.name.toLowerCase() === name.toLowerCase(),
    )) {
      return t("importNewTagExists");
    }
    if (importCollections.length >= MAX_TAGS) return t("importNewTagLimit");
    return "";
  }

  function resetImportState() {
    importPreview = null;
    importResult = null;
    importBusy = false;
    importRunning = false;
    importTagComposing = false;
    importDefaultDifficulty = "medium";
    clearImportPayload();
  }

  async function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      try {
        await navigator.clipboard.writeText(text);
        return;
      } catch (_) {
        // Plain http on the LAN can expose the API but reject the write.
      }
    }
    const area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand("copy");
    area.remove();
    if (!ok) throw new Error("COPY_FAILED");
  }

  async function copyImportPrompt(kind) {
    const generate = kind === "generate";
    // Copying is also the reveal: the same two buttons show their prompt in
    // the panel below, so there is no separate "view" action.
    importPromptKind = kind;
    renderImportPrompt();
    try {
      await copyText(t(generate ? "importGeneratePrompt" : "importConvertPrompt"));
      $("importCopyState").textContent = t("importPromptCopied");
      setTimeout(() => {
        if ($("importCopyState")) $("importCopyState").textContent = "";
      }, 2600);
    } catch (_) {
      $("importCopyState").textContent = "";
      setImportError(t("importCopyFailed"), []);
    }
  }

  function startImportFromCustomLevel() {
    setTab("bank", true);
    openBankImport();
  }

  // Return to the level's tag page after importing, not to the bank. The draft
  // stays in memory, so the imported tag can be selected without rebuilding it.
  function startImportFromTagSource() {
    if (packageSaving || !packageEditorActive) return;
    tagSourceReturnAfterImport = true;
    startImportFromCustomLevel();
  }

  function bankClearScopeText(item) {
    switch (item.scope) {
      case "builtin": return t("bankClearScopeBuiltin");
      case "ai": return t("bankClearScopeAi");
      case "manual":
        return item.collectionId
          ? t("bankClearScopeManualTag", { name: item.name || "" })
          : t("bankClearScopeManual");
      case "wrong": return t("bankClearScopeWrong");
      default: return t("bankScopeClear");
    }
  }

  function renderBankClearDialog() {
    if (!bankClearPending) return;
    const item = bankClearPending;
    const scope = bankClearScopeText(item);
    const detailKey = item.scope === "builtin" ? "bankClearBuiltinDetail"
      : item.scope === "ai" ? "bankClearAiDetail"
        : item.scope === "manual" ? "bankClearManualDetail" : "bankClearWrongDetail";
    $("bankClearTitle").textContent = t(
      item.step === 1 ? "bankClearTitleFirst" : "bankClearTitleFinal",
      { scope },
    );
    $("bankClearLead").textContent = t(detailKey, {
      scope,
      name: editingMemberName() || activeMemberName(),
      n: Math.max(0, Number(item.count || 0)),
    });
    $("bankClearWarning").textContent = t(
      item.step === 1 ? "bankClearFirstWarning" : "bankClearFinalWarning",
    );
    const next = $("bankClearNext");
    next.textContent = t(item.step === 1 ? "bankClearContinue" : "bankClearConfirm");
    next.classList.toggle("primary", item.step === 1);
    next.classList.toggle("danger", item.step !== 1);
    next.disabled = bankClearBusy;
  }

  function requestBankClear(action) {
    if (bankClearBusy) return;
    bankClearPending = Object.assign({ step: 1, collectionId: null, name: "", count: 0 }, action);
    $("bankManageState").textContent = "";
    $("bankManageError").textContent = "";
    renderBankClearDialog();
    openModal("bankClearDialog");
  }

  async function advanceBankClear() {
    if (!bankClearPending || bankClearBusy) return;
    if (bankClearPending.step === 1) {
      bankClearPending.step = 2;
      renderBankClearDialog();
      return;
    }
    const item = bankClearPending;
    bankClearBusy = true;
    renderBankClearDialog();
    try {
      const body = {
        scope: item.scope,
        mode: item.scope === "wrong" ? "wrong" : "all",
      };
      if (item.collectionId) body.collectionId = item.collectionId;
      const data = await api("/api/quiz/admin/bank/clear", {
        method: "POST",
        admin: true,
        body,
      });
      applyClearStats(data);
      closeModal();
      bankClearPending = null;
      bankClearBusy = false;
      $("bankManageState").textContent = t("clearDone");
      $("bankManageError").textContent = "";
      renderBankManage();
      renderBank();
      renderStock();
      await refreshStatus();
      try {
        await loadAdminSession();
      } catch (_) {
        // The clear already succeeded; tag counts catch up on the next refresh.
      }
      if (activeTab === "bank") loadBank(true);
    } catch (error) {
      bankClearBusy = false;
      bankClearPending = null;
      closeModal();
      $("bankManageError").textContent = errorText(error);
      $("bankError").textContent = errorText(error);
    }
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
    let requestPath = path.startsWith("/api/") ? API_ROOT + path.slice(1) : path;
    // Every admin endpoint resolves its subject from this query parameter. The
    // TV still answers with the active member by default, so an old page and a
    // first load before the roster arrives keep working. A generation job
    // passes its pinned subject so a TV-side member change cannot retarget it.
    const subjectUserId = Object.prototype.hasOwnProperty.call(options, "subjectUserId")
      ? (options.subjectUserId || "")
      : editingSubjectUserId;
    if (options.admin && subjectUserId && requestPath.indexOf("subjectUserId=") < 0) {
      requestPath += (requestPath.indexOf("?") >= 0 ? "&" : "?") +
        "subjectUserId=" + encodeURIComponent(subjectUserId);
    }
    let response;
    try {
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
      } else if (error.code === "WEB_LOCAL_ONLY" || error.code === "LOCAL_ONLY") {
        onParentWebUnavailable();
      }
      throw error;
    }
    return data;
  }

  // --------------------------------------------------------------- V27 parent host
  // parent.js owns the parent-center UI, but it never reads the quiz token or builds URLs
  // itself. Everything goes through these two functions so same-origin handling, the admin
  // token, the single-editor headers and JSON errors stay in one place.

  async function loadParentBootstrap() {
    try {
      const response = await fetch(API_ROOT + "api/parent/bootstrap", {
        cache: "no-store",
        credentials: "omit",
      });
      if (!response.ok) return null;
      const data = await response.json();
      if (!data || data.enabled !== true || !data.gate) return null;
      return data;
    } catch (_) {
      return null;
    }
  }

  function parentHeaders(options) {
    const headers = { "X-Parent": "1" };
    if (options.editorToken) headers["X-Parent-Editor-Token"] = options.editorToken;
    if (options.sensitiveToken) headers["X-Parent-Sensitive-Token"] = options.sensitiveToken;
    if (options.entryToken) headers["X-Parent-Entry"] = options.entryToken;
    if (options.takeover) headers["X-Parent-Takeover"] = "1";
    if (options.quizAuth !== false && adminToken && Date.now() < adminExpiresAt) {
      headers["X-Quiz-Admin-Token"] = adminToken;
    }
    return headers;
  }

  async function parentRequest(path, options = {}) {
    const method = options.method || "GET";
    const headers = parentHeaders(options);
    if (options.body !== undefined || method !== "GET") {
      headers["Content-Type"] = "application/json";
    }
    const requestPath = path.startsWith("/api/") ? API_ROOT + path.slice(1) : API_ROOT + path;
    let response;
    try {
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
      error.code = (data && data.error) || ("HTTP_" + response.status);
      error.data = data;
      error.status = response.status;
      if (error.code === "WEB_LOCAL_ONLY" || error.status === 423) onParentWebUnavailable();
      throw error;
    }
    return data;
  }

  async function parentBlob(path, editorToken) {
    const response = await fetch(API_ROOT + path.slice(1), {
      headers: parentHeaders({ editorToken }),
      cache: "no-store",
      credentials: "omit",
    });
    if (!response.ok) throw new Error("HTTP_" + response.status);
    return response.blob();
  }

  /**
   * Sends a raw File as the request body. parentRequest always JSON-encodes, which would
   * base64 the film and multiply its size; this keeps the upload a single binary stream so
   * the TV can write it straight to disk. Content-Length is set by the browser from the File.
   * XHR is used instead of fetch because only XHR reports upload progress to the page.
   */
  function parentUpload(path, file, editorToken, onProgress, contentType, sensitiveToken) {
    const headers = parentHeaders({ editorToken, sensitiveToken });
    // Media uploads keep the octet-stream contract; the avatar route accepts image/* only and
    // gets the selected file's own mime type so the TV can reject a non-image before decoding.
    headers["Content-Type"] = contentType || "application/octet-stream";
    const requestPath = path.startsWith("/api/") ? API_ROOT + path.slice(1) : API_ROOT + path;
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", requestPath, true);
      for (const name of Object.keys(headers)) xhr.setRequestHeader(name, headers[name]);
      xhr.responseType = "text";
      // A 128 MB wallpaper can take minutes on a phone backing away from the TV; this is
      // a stall guard, not a throughput target.
      xhr.timeout = 900000;
      if (typeof onProgress === "function") {
        xhr.upload.onprogress = (event) => {
          if (event && event.lengthComputable) onProgress(event.loaded, event.total);
        };
      }
      xhr.onload = () => {
        let data = null;
        try { data = xhr.responseText ? JSON.parse(xhr.responseText) : null; } catch (_) { data = null; }
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(data);
          return;
        }
        const error = new Error((data && data.error) || ("HTTP_" + xhr.status));
        error.code = (data && data.error) || ("HTTP_" + xhr.status);
        error.data = data;
        error.status = xhr.status;
        if (error.code === "WEB_LOCAL_ONLY" || error.status === 423) onParentWebUnavailable();
        reject(error);
      };
      xhr.onerror = () => reject(new Error("NETWORK"));
      xhr.ontimeout = () => reject(new Error("NETWORK"));
      xhr.onabort = () => reject(new Error("NETWORK"));
      xhr.send(file);
    });
  }

  function parentHost() {
    return {
      lang: () => lang,
      t: (key, vars) => t(key, vars),
      request: parentRequest,
      upload: parentUpload,
      blob: parentBlob,
      hasQuizSession: () => Boolean(adminToken && Date.now() < adminExpiresAt),
      refreshQuizState: () => {
        refreshStatus();
        heartbeat();
      },
      // The parent center edits a member's grade/semester in place; reload the
      // session so the generation and level pickers see the new default scope.
      // A deletion can also discard the member this page was editing; in that case
      // reload the generation form for whichever member the session now points at.
      memberProfileChanged: () => {
        const previous = editingSubjectUserId;
        loadAdminSession()
          .then(() => {
            if (editingSubjectUserId !== previous) {
              return loadGenerationPrefsFromTv(true);
            }
            return null;
          })
          .catch(() => {});
      },
      openQuizLogin: (mode) => {
        openOverlayAfterLogin = mode === "user" || mode === "system" ? mode : "system";
        openParentAfterLogin = false;
        if (!gateShown) showPinGate();
      },
      openSubjectPicker: () => openSubjectDialog(),
      editingSubjectId: () => editingSubjectUserId,
      suspendParent: () => {
        if (window.ParentCenterUI && window.ParentCenterUI.suspend) {
          window.ParentCenterUI.suspend();
        } else if (window.ParentCenterUI && window.ParentCenterUI.close) {
          window.ParentCenterUI.close();
        }
      },
      closeOverlayTab: () => setTab("generate", true),
      openUnlockDialog: () => parentUnlockDialog(),
      openManualDialog: () => openManualDialog(),
      controlManualReason: (state) => (state ? manualReasonText(state.reason, state) : ""),
      openParentAppDownload: () => openParentAppDownload(),
      openPrivacy: () => openModal("privacyInfoDialog"),
      openAi: () => openProviderDialog(false),
      closeAi: () => closeAiPage(),
      changeLanguage: (value) => applyLanguage(value),
      logout: () => {
        clearAuth();
        stopHeartbeat();
        showPinGate();
      },
      onWebUnavailable: () => onParentWebUnavailable(),
    };
  }

  function parentWebAvailable(bootstrap) {
    if (!bootstrap || !bootstrap.gate) return false;
    if (bootstrap.webAvailable === false || bootstrap.gate.webAvailable === false) return false;
    return bootstrap.gate.mode === "LOCAL";
  }

  function webDisabledCopy(reason) {
    switch (reason) {
      case "LOCAL_MODE_REQUIRED":
        return {
          title: t("webDisabledTitleNoChild"),
          text: t("webDisabledNoChildText"),
          state: t("webDisabledStateNoChild"),
        };
      case "REMOTE_MODE":
        return {
          title: t("webDisabledTitleBinding"),
          text: t("webDisabledBindingText"),
          state: t("webDisabledStateBinding"),
        };
      case "NOT_READY":
        return {
          title: t("webDisabledTitleNotReady"),
          text: t("webDisabledNotReadyText"),
          state: t("webDisabledStateNotReady"),
        };
      default:
        return {
          title: t("webDisabledTitleBound"),
          text: t("webDisabledBoundText"),
          state: t("webDisabledStateBound"),
        };
    }
  }

  function renderWebDisabled(bootstrap) {
    if (bootstrap) webDisabledBootstrap = bootstrap;
    const data = webDisabledBootstrap || {};
    const reason = String(data.reason || (data.gate && data.gate.reason) || "REMOTE_BOUND");
    const copy = webDisabledCopy(reason);
    $("webDisabledTitle").textContent = copy.title;
    $("webDisabledText").textContent = copy.text;
    $("webDisabledState").textContent = copy.state;
  }

  function enterWebDisabled(bootstrap) {
    if (!webDisabled) {
      // A blocked page keeps no credentials: the next successful LOCAL login must be explicit.
      clearAuth();
    }
    webDisabled = true;
    stopHeartbeat();
    if (statusTimer) {
      clearInterval(statusTimer);
      statusTimer = 0;
    }
    if (activeModal) closeModal();
    document.body.classList.add("web-disabled", "editor-open");
    document.body.classList.remove("modal-open", "parent-only");
    $("gateCard").classList.add("hidden");
    $("editor").classList.add("hidden");
    $("bottomNav").classList.add("hidden");
    $("modalRoot").classList.add("hidden");
    renderSubjectChip();
    $("webDisabledCard").classList.remove("hidden");
    $("webDisabledError").classList.add("hidden");
    renderWebDisabled(bootstrap);
    window.scrollTo({ top: 0 });
    if (!webDisabledTimer) {
      webDisabledTimer = setInterval(() => {
        if (!document.hidden) refreshWebDisabled(false);
      }, 15000);
    }
    if (!webDisabledVisibilityBound) {
      webDisabledVisibilityBound = true;
      document.addEventListener("visibilitychange", () => {
        if (webDisabled && !document.hidden) refreshWebDisabled(false);
      });
    }
  }

  async function refreshWebDisabled(manual) {
    if (!webDisabled) return;
    if (webDisabledBusy) return;
    webDisabledBusy = true;
    const button = $("webDisabledRefresh");
    if (button) button.disabled = true;
    if ($("webDisabledError")) $("webDisabledError").classList.add("hidden");
    const data = await loadParentBootstrap();
    if (data && parentWebAvailable(data)) {
      // Back to LOCAL and unbound: reload so no disabled-page state leaks into the login page.
      location.reload();
      return;
    }
    if (data) {
      renderWebDisabled(data);
    } else if (manual || !webDisabledBootstrap) {
      $("webDisabledError").textContent = t("webDisabledRefreshFailed");
      $("webDisabledError").classList.remove("hidden");
    }
    if (button) button.disabled = false;
    webDisabledBusy = false;
  }

  function onParentWebUnavailable() {
    if (webDisabled) return;
    loadParentBootstrap().then((data) => {
      if (data) enterWebDisabled(data);
    });
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
    if (activeModal === "levelDeleteDialog") pendingLevelDeleteId = null;
    if (activeModal === "unlockDialog") settleParentUnlock(false);
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
    $("pinForm").classList.toggle("hidden", !showPin || recoveryOpen);
    $("forgotPin").classList.toggle("hidden", !showPin || recoveryOpen);
    if (showPin && !recoveryOpen) setTimeout(() => $("pin").focus(), 50);
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
    bankCapacity = 100000;
    bankItems = [];
    bankOffset = 0;
    bankHasMore = false;
    bankLoading = false;
    bankRequest++;
    bankPage = "home";
    importCollections = [];
    resetImportState();
    if ($("importTagName")) $("importTagName").value = "";
    if ($("clearImportTag")) $("clearImportTag").classList.add("hidden");
    levelPackages = [];
    levelLimits = { maxLevels: MAX_LEVELS, maxSources: MAX_LEVEL_SOURCES, maxNameCodePoints: LEVEL_NAME_MAX };
    levelPackagesUnreadable = false;
    packageEditingId = null;
    packageDraftSources = [];
    packageNameDraft = "";
    packageSaving = false;
    packageEditorActive = false;
    tagSourceSelected = new Set();
    tagSourceReturnAfterImport = false;
    courseSourceEditingIndex = null;
    courseSourceAnswerKind = "any";
    courseSourceDifficultyPicked = false;
    courseSourceCounts = null;
    courseSourceCountsKey = "";
    courseSourceCountsToken++;
    bankSourceCounts = { course: 0, custom: 0 };
    presetBank = { bankId: "", total: 0, available: 0 };
    localBankActive = true;
    providerState = { aiSupported: true, consentAccepted: false, configured: false, baseUrl: "", model: "", speech: null };
    speechState = Object.assign({}, DEFAULT_SPEECH);
    speechDirty = false;
    providerMsg = null;
    pendingGenerate = false;
    pendingProviderAction = "";
    genState = idleGeneration();
    targetCount = 20;
    levelSaving = false;
    pendingLevelDeleteId = null;
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
    genScopeSetup = false;
    genGradeValue = 4;
    genSemesterValue = 1;
    genSubjectValue = "math";
    genTextbookId = "renjiao";
    genRequirementValue = DEFAULT_GEN_REQUIREMENT;
    genQuestionTypeValue = "single";
    genOptionsValue = 4;
    genAutoAddValue = true;
    genScopeValue = "unit";
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
    $("bankManageState").textContent = "";
    $("bankManageError").textContent = "";
    bankClearPending = null;
    bankClearBusy = false;
    $("bankError").textContent = "";
    $("generateError").textContent = "";
    $("generateOk").textContent = "";
    $("generateProgress").textContent = "";
    $("questionError").textContent = "";
    aiPageOpen = false;
    aiReturnTab = "generate";
    aiReturnParentCenter = false;
    $("aiConfigPage").classList.add("hidden");
    showGenPage("home");
    showBankPage("home");
    renderSettings();
    renderGenerate();
    renderHome();
  }

  function showPinGate() {
    stopHeartbeat();
    forceTakeover = false;
    if (activeModal) closeModal();
    resetSessionState();
    recoveryOpen = false;
    $("recoveryForm").classList.add("hidden");
    $("recoveryError").textContent = "";
    $("recoveryCode").value = "";
    $("recoveryNewPin").value = "";
    $("recoveryConfirmPin").value = "";
    $("gateOk").textContent = "";
    document.body.classList.remove("editor-open");
    $("editor").classList.add("hidden");
    $("bottomNav").classList.add("hidden");
    renderSubjectChip();
    gateShown = true;
    unlockState = null;
    unlockBusy = false;
    manualState = null;
    manualBusy = false;
    manualMinutes = 20;
    manualMinutesTouched = false;
    setGate("gate_pair_title", "gate_pair_text", true);
    renderGateOccupation();
  }

  function showEditor() {
    forceTakeover = false;
    gateShown = false;
    gateOccupied = false;
    recoveryOpen = false;
    $("recoveryForm").classList.add("hidden");
    document.body.classList.add("editor-open");
    $("gateCard").classList.add("hidden");
    $("editor").classList.remove("hidden");
    $("bottomNav").classList.remove("hidden");
    renderSubjectChip();
    // The post-login tab is decided by enterPostLoginTab; never auto-open an
    // overlay tab left over from the previous session.
    setTab(activeTab === "user" || activeTab === "system" ? "generate" : activeTab, true);
  }

  // A second phone sees the same PIN gate. When the first phone still owns
  // the session, replace the form with a calm notice; the 5-second status
  // poll reveals the form again as soon as that phone logs out.
  function renderGateOccupation() {
    if (!gateShown) return;
    const occupied = !adminToken && Boolean(publicState && publicState.parentInUse);
    // After the second parent confirms takeover, keep the PIN form visible
    // across the five-second status poll until the request finishes.
    const inUse = occupied && !forceTakeover;
    const showRecovery = recoveryOpen && !inUse;
    $("gateTitle").textContent = t(
      inUse ? "gate_in_use_heading" : (showRecovery ? "recoveryTitle" : "gate_pair_title")
    );
    $("gateText").textContent = t(
      inUse ? "gate_in_use_lead" : (showRecovery ? "recoveryLead" : "gate_pair_text")
    );
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
    $("gateNotice").classList.toggle("wait", inUse);
    $("gateStatus").classList.toggle("hidden", !inUse);
    $("gateTakeover").classList.toggle("hidden", !inUse);
    $("gateTakeoverNote").classList.toggle("hidden", !occupied);
    $("pinForm").classList.toggle("hidden", inUse || showRecovery);
    $("forgotPin").classList.toggle("hidden", inUse || showRecovery);
    $("recoveryForm").classList.toggle("hidden", !showRecovery);
    const released = gateOccupied && !inUse;
    gateOccupied = inUse;
    if (released) {
      setTimeout(() => {
        const active = document.activeElement;
        const busy = active && ["INPUT", "SELECT", "TEXTAREA", "BUTTON"].includes(active.tagName);
        if (!busy) $(showRecovery ? "recoveryCode" : "pin").focus();
      }, 50);
    }
  }

  // The recovery form is reachable without a PIN session: a parent who forgot the password,
  // or whose login is temporarily locked out, can reset it with the one-time setup code.
  function openRecovery() {
    if (adminToken && Date.now() < adminExpiresAt) return;
    recoveryOpen = true;
    $("gateError").textContent = "";
    $("gateOk").textContent = "";
    $("recoveryError").textContent = "";
    $("recoveryCode").value = "";
    $("recoveryNewPin").value = "";
    $("recoveryConfirmPin").value = "";
    $("gateTitle").textContent = t("recoveryTitle");
    $("gateText").textContent = t("recoveryLead");
    $("pinForm").classList.add("hidden");
    $("forgotPin").classList.add("hidden");
    $("recoveryForm").classList.remove("hidden");
    setTimeout(() => $("recoveryCode").focus(), 50);
  }

  function closeRecovery() {
    recoveryOpen = false;
    $("recoveryForm").classList.add("hidden");
    $("recoveryError").textContent = "";
    $("recoveryCode").value = "";
    $("recoveryNewPin").value = "";
    $("recoveryConfirmPin").value = "";
    renderGateOccupation();
  }

  function recoveryErrorText(error) {
    if (error && error.code === "RECOVERY_LOCKED") {
      const seconds = Number(error.data && error.data.retryAfterSeconds) || 0;
      return seconds > 0
        ? t("err_RECOVERY_LOCKED_RETRY", { n: seconds })
        : t("err_RECOVERY_LOCKED");
    }
    return errorText(error);
  }

  async function submitRecovery(event) {
    event.preventDefault();
    $("recoveryError").textContent = "";
    const recoveryCode = $("recoveryCode").value.replace(/\D/g, "");
    const newPin = $("recoveryNewPin").value.trim();
    const confirmPin = $("recoveryConfirmPin").value.trim();
    if (recoveryCode.length !== 12) {
      $("recoveryError").textContent = t("recoveryFormat");
      return;
    }
    if (!/^\d{6}$/.test(newPin)) {
      $("recoveryError").textContent = t("err_NEW_PIN_INVALID");
      return;
    }
    if (newPin !== confirmPin) {
      $("recoveryError").textContent = t("recoveryMismatch");
      return;
    }
    $("recoveryBtn").disabled = true;
    try {
      await parentRequest("/api/parent/recover", {
        method: "POST",
        body: { recoveryCode, newPin },
        quizAuth: false,
      });
      // The TV revoked every web session opened under the old PIN.
      stopHeartbeat();
      adminToken = null;
      adminExpiresAt = 0;
      clearAuth();
      if (publicState) publicState.parentInUse = false;
      recoveryOpen = false;
      $("recoveryForm").classList.add("hidden");
      $("recoveryCode").value = "";
      $("recoveryNewPin").value = "";
      $("recoveryConfirmPin").value = "";
      $("recoveryError").textContent = "";
      $("gateError").textContent = "";
      $("gateOk").textContent = t("recoveryDone");
      renderGateOccupation();
      setTimeout(() => $("pin").focus(), 50);
    } catch (error) {
      $("recoveryError").textContent = recoveryErrorText(error);
    } finally {
      $("recoveryBtn").disabled = false;
    }
  }

  function parentTabOpen() {
    return Boolean(window.ParentCenterUI && window.ParentCenterUI.isOpen &&
      window.ParentCenterUI.isOpen());
  }

  function setTab(tab, force) {
    if (!TAB_VIEWS[tab]) tab = "generate";
    const overlayTab = tab === "user" || tab === "system";
    if (!overlayTab && parentTabOpen()) {
      // Leaving the overlay releases the editor lease; returning reacquires it
      // with the same quiz PIN session, so no second password prompt is needed.
      window.ParentCenterUI.close();
    }
    if (!force && activeTab === tab) {
      if (tab === "user") openUserTab();
      else if (tab === "system") openSystemTab();
      return;
    }
    if (!force) showGenPage("home");
    // Leaving or re-entering the bank always returns to its list; the import
    // and manage subpages are reached only through their own buttons.
    showBankPage("home");
    activeTab = tab;
    for (const name of Object.keys(TAB_VIEWS)) {
      $(TAB_VIEWS[name]).classList.toggle("hidden", name !== tab);
    }
    document.querySelectorAll(".nav-item[data-tab]").forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.tab === tab));
    });
    renderSubjectChip();
    if (tab === "bank") {
      loadBank(true);
    } else if (tab === "user") {
      openUserTab();
    } else if (tab === "system") {
      openSystemTab();
    }
    window.scrollTo({ top: 0 });
  }

  function openUserTab() {
    if (parentBootstrap && parentWebAvailable(parentBootstrap) &&
        window.ParentCenterUI && window.ParentCenterUI.openUser) {
      window.ParentCenterUI.openUser();
      return;
    }
    // Without a working parent center there is nothing to configure here.
    activeTab = "generate";
    setTab("generate", true);
  }

  function openSystemTab() {
    if (parentBootstrap && parentWebAvailable(parentBootstrap) &&
        window.ParentCenterUI && window.ParentCenterUI.openSystem) {
      window.ParentCenterUI.openSystem();
      return;
    }
    activeTab = "generate";
    setTab("generate", true);
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
    try {
      return await loadAdminSessionOnce();
    } catch (error) {
      // A member removed or disabled on the TV must not dead-end the page. Drop
      // the stale preference and fall back to the TV's active member once.
      if (error && error.code === "INVALID_SUBJECT" && editingSubjectUserId) {
        saveEditingSubject("");
        return loadAdminSessionOnce();
      }
      throw error;
    }
  }

  async function loadAdminSessionOnce() {
    const [data] = await Promise.all([
      api("/api/quiz/admin/session", { admin: true }),
      loadUnitCatalog(),
    ]);
    adminSession = data.session || null;
    familyMembers = Array.isArray(data.members) ? data.members : [];
    activeUserId = data.activeUserId ? String(data.activeUserId) : "";
    // No stored preference means "follow the TV's active member". Do not persist the
    // resolved id here, or the phone would stay pinned to that member after the TV
    // switches. A real explicit choice is rewritten only when the server resolves it
    // to a different member (for example after a migration).
    if (editingSubjectUserId && data.subjectUserId) {
      const resolved = String(data.subjectUserId);
      if (resolved !== editingSubjectUserId) saveEditingSubject(resolved);
    }
    privateCapacity = Math.max(0, Number(data.privateCapacity || privateCapacity || 0));
    privateRemaining = Math.max(0, Number(data.privateRemaining || 0));
    bankScopeCounts = subjectScopeCounts(data.bankScopeCounts);
    renderSubjectChip();
    renderSubjectDialog();
    applyCurriculum(data.curriculum, data.cloudJuniorReady);
    // Re-resolve a saved requirement now that unit titles are available; a
    // title without "第N单元" could not map before this point.
    applyRequirementScope(genRequirementValue, false);
    applyConfig(data.challengeConfig || DEFAULT_CONFIG);
    const scopeBusy = settingsSaving || answerDirty || levelSaving ||
      packageSaving || Boolean(packageEditingId) ||
      courseSourceEditingIndex !== null;
    if (Number(data.levelModel) !== LEVEL_MODEL) {
      // A TV older than this page would answer with the legacy fields. The page
      // is served by the TV, so this only happens mid-update; never write the
      // legacy model back from a new page.
      console.error("[quiz] admin session is missing levelPackages");
      levelPackages = [];
      levelPackagesUnreadable = true;
      applyLevelPackageProjection(data);
    } else {
      if (data.levelLimits && typeof data.levelLimits === "object") {
        levelLimits = {
          maxLevels: Math.max(1, Number(data.levelLimits.maxLevels || MAX_LEVELS)),
          maxSources: Math.max(1, Number(data.levelLimits.maxSources || MAX_LEVEL_SOURCES)),
          maxNameCodePoints: Math.max(1, Number(
            data.levelLimits.maxNameCodePoints || LEVEL_NAME_MAX)),
        };
      }
      levelPackagesUnreadable = Boolean(data.levelPackagesUnreadable);
      if (!scopeLoaded || !scopeBusy) {
        applyLevelPackages(data.levelPackages);
      }
      applyLevelPackageProjection(data);
    }
    // The tag list lives outside the package protocol; an older TV that omits
    // it leaves the local list empty.
    importCollections = Array.isArray(data.collections)
      ? data.collections.map((item) => ({
        id: String(item && item.id || ""),
        name: String(item && item.name || ""),
        total: Math.max(0, Number(item && item.total || 0)),
        unused: Math.max(0, Number(item && item.unused || 0)),
      })).filter((item) => item.id && item.name)
      : [];
    bankSourceCounts = {
      course: Math.max(0, Number(data.bankSourceCounts && data.bankSourceCounts.course || 0)),
      custom: Math.max(0, Number(data.bankSourceCounts && data.bankSourceCounts.custom || 0)),
    };
    applyPresetBank(data.presetBank);
    localBankActive = data.localBankActive !== false;
    syncState = data.syncState || null;
    cloudManaged = Boolean(data.cloudManaged);
    bankCapacity = Number(data.bankCapacity || 100000);
    applyBankCounts(data.bank);
    renderSync();
    renderStock();
    renderBankCounts();
    renderBank();
    renderBankManage();
    renderGenConfig(false);
    renderGenerate();
    renderHome();
    renderBankImport();
    if (genPage === "answer") renderAnswerPage();
    syncDraftCollectionCounts();
  }

  async function loadProvider() {
    const data = await api("/api/quiz/admin/provider", { admin: true });
    providerState = {
      aiSupported: data.aiSupported !== false,
      consentAccepted: Boolean(data.consentAccepted),
      configured: Boolean(data.configured),
      baseUrl: data.baseUrl || "",
      model: data.model || "",
      speech: normalizeSpeech(data.speech),
    };
    speechState = Object.assign({}, providerState.speech);
    speechDirty = false;
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
    // Sending speech only for the official host preserves a previous DeepSeek
    // choice when the parent switches to another provider, and keeps a foreign
    // gateway from being mistaken for a voice-capable endpoint.
    if (isOfficialDeepSeek(payload.baseUrl)) {
      payload.speech = {
        enabled: speechState.enabled,
        readPrompt: speechState.readPrompt,
        readOptions: speechState.readOptions,
        readAnswer: speechState.readAnswer,
      };
    }
    return payload;
  }

  // Presets are examples, not endorsements. The base URL is the prefix this client
  // appends "/chat/completions" to, so it must include the provider's version path.
  // Model IDs rotate quickly; hints under the model field repeat the samples and
  // tell the parent to verify them in the provider console or through /models.
  const PROVIDER_PRESETS = {
    deepseek: { baseUrl: "https://api.deepseek.com", sampleModel: "deepseek-flash" },
    // New Qwen accounts may use a workspace host such as
    // https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1;
    // the classic Beijing host below still works. API keys are region-bound.
    qwen: { baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1", sampleModel: "qwen-plus" },
    kimi: { baseUrl: "https://api.moonshot.cn/v1", sampleModel: "kimi-k3.1-pro" },
    glm: { baseUrl: "https://open.bigmodel.cn/api/paas/v4", sampleModel: "glm-4.7-flash" },
    // Ark also accepts a console endpoint ID (ep-...). Dated model IDs change often.
    // The Coding Plan endpoint is /api/coding/v3 and must not be used here.
    doubao: { baseUrl: "https://ark.cn-beijing.volces.com/api/v3", sampleModel: "doubao-seed-2-1-pro-260915" },
    // Legacy direct Hunyuan endpoint; Tencent is moving new accounts to TokenHub
    // (https://tokenhub.tencentmaas.com/v1). The two platforms' keys are not interchangeable.
    hunyuan: { baseUrl: "https://api.hunyuan.cloud.tencent.com/v1", sampleModel: "hunyuan-turbos-latest" },
    // Some ERNIE models are retired over time; the international host is
    // https://api.baiduqianfan.ai/v1 and uses a separate key.
    ernie: { baseUrl: "https://qianfan.baidubce.com/v2", sampleModel: "ernie-5.1" },
    // Spark keys are the console APIPassword in "APIKey:APISecret" form. Spark has no
    // GET /models, so the test button reports "cannot auto-test". X1.5/X2 use another host.
    spark: { baseUrl: "https://spark-api-open.xf-yun.com/v1", sampleModel: "generalv3.5" },
    // MiniMax may ignore response_format, M2.x cannot disable thinking, and /models
    // support is unconfirmed. The international host is https://api.minimax.io/v1.
    minimax: { baseUrl: "https://api.minimaxi.com/v1", sampleModel: "MiniMax-M2" },
    // LongCat SDK examples use https://api.longcat.chat/openai and let the SDK add /v1;
    // this client only appends /chat/completions, so keep /v1 in the preset.
    longcat: { baseUrl: "https://api.longcat.chat/openai/v1", sampleModel: "LongCat-Flash-Chat" },
    // Step Plan subscriptions use https://api.stepfun.com/step_plan/v1 instead; the
    // international host is https://api.stepfun.ai/v1.
    stepfun: { baseUrl: "https://api.stepfun.com/v1", sampleModel: "step-3.5-flash" },
  };

  // Values already written by a preset may be replaced when another preset is chosen;
  // a model the parent typed (or saved) is kept.
  const PROVIDER_SAMPLE_MODELS = new Set(
    Object.values(PROVIDER_PRESETS).map((preset) => preset.sampleModel).filter(Boolean),
  );

  function normalizedBaseUrl(value) {
    return String(value || "").trim().replace(/\/+$/, "").toLowerCase();
  }

  function providerHintKey(value) {
    const base = normalizedBaseUrl(value);
    if (base.includes("moonshot.cn") || base.includes("moonshot.ai")) return "providerPresetHintKimi";
    if (base.includes("bigmodel.cn") || base.includes("z.ai")) return "providerPresetHintGlm";
    if (base.includes("deepseek.com")) return "providerPresetHintDeepSeek";
    if (base.includes("dashscope.aliyuncs.com") || base.includes("maas.aliyuncs.com")) return "providerPresetHintQwen";
    if (base.includes("volces.com") || base.includes("bytepluses.com")) return "providerPresetHintDoubao";
    if (base.includes("hunyuan.cloud.tencent.com") || base.includes("tokenhub.tencentmaas.com")) return "providerPresetHintHunyuan";
    if (base.includes("qianfan.baidubce.com") || base.includes("baiduqianfan.ai")) return "providerPresetHintErnie";
    if (base.includes("spark-api-open.xf-yun.com")) return "providerPresetHintSpark";
    if (base.includes("minimaxi.com") || base.includes("minimax.io") || base.includes("minimax.chat")) return "providerPresetHintMiniMax";
    if (base.includes("longcat.chat")) return "providerPresetHintLongCat";
    if (base.includes("stepfun.com") || base.includes("stepfun.ai")) return "providerPresetHintStepFun";
    return "providerPresetHintCustom";
  }

  function updateProviderModelHint() {
    if (!$("providerModelHint")) return;
    const base = normalizedBaseUrl($("baseUrl").value);
    $("providerModelHint").textContent = t(providerHintKey(base));
    document.querySelectorAll("[data-provider-preset]").forEach((button) => {
      const preset = PROVIDER_PRESETS[button.dataset.providerPreset];
      const active = Boolean(preset) && normalizedBaseUrl(preset.baseUrl) === base;
      button.setAttribute("aria-pressed", active ? "true" : "false");
    });
  }

  function applyProviderPreset(id) {
    const preset = PROVIDER_PRESETS[id];
    if (!preset) return;
    const currentModel = $("model").value.trim();
    $("baseUrl").value = preset.baseUrl;
    // Keep a model the parent typed; replace an empty field or a sample left by
    // another preset so a provider never receives the previous vendor's model ID.
    if (!currentModel || PROVIDER_SAMPLE_MODELS.has(currentModel)) {
      $("model").value = preset.sampleModel || "";
    }
    providerMsg = null;
    renderProviderMessages();
    updateProviderModelHint();
    renderSpeech();
  }

  function openProviderDialog(pending) {
    pendingGenerate = Boolean(pending);
    providerMsg = null;
    $("apiKey").value = "";
    renderProviderMessages();
    renderProvider();
    aiReturnTab = TAB_VIEWS[activeTab] ? activeTab : "generate";
    aiReturnParentCenter = parentTabOpen();
    if (aiReturnParentCenter && window.ParentCenterUI) {
      // The overlay would otherwise cover the full-page form. Suspend it so the
      // close button returns to the same page it was opened from.
      if (window.ParentCenterUI.suspend) window.ParentCenterUI.suspend();
      else if (window.ParentCenterUI.close) window.ParentCenterUI.close();
    }
    aiPageOpen = true;
    document.querySelectorAll("#editor > .view").forEach((view) => view.classList.add("hidden"));
    $("aiConfigPage").classList.remove("hidden");
    syncSubpageClass();
    window.scrollTo({ top: 0 });
  }

  function closeAiPage() {
    if (!aiPageOpen) return;
    aiPageOpen = false;
    $("aiConfigPage").classList.add("hidden");
    const tab = TAB_VIEWS[aiReturnTab] ? aiReturnTab : "generate";
    activeTab = tab;
    for (const name of Object.keys(TAB_VIEWS)) {
      $(TAB_VIEWS[name]).classList.toggle("hidden", name !== tab);
    }
    document.querySelectorAll(".nav-item[data-tab]").forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.tab === tab));
    });
    aiReturnTab = "generate";
    syncSubpageClass();
    const reopenParent = aiReturnParentCenter;
    aiReturnParentCenter = false;
    if (reopenParent && window.ParentCenterUI && window.ParentCenterUI.open) {
      window.ParentCenterUI.open();
    }
    window.scrollTo({ top: 0 });
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
      pendingProviderAction = "save";
      openConsent();
      return;
    }
    if (!$("apiKey").value.trim() && !providerState.configured) {
      providerMsg = { kind: "error", key: "err_PROVIDER_KEY_REQUIRED" };
      renderProviderMessages();
      return;
    }
    if (isOfficialDeepSeek($("baseUrl").value) &&
        speechState.enabled && !speechHasContent()) {
      providerMsg = { kind: "error", key: "speechSelectAtLeastOne" };
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
        speech: normalizeSpeech(data.speech),
      };
      speechState = Object.assign({}, providerState.speech);
      speechDirty = false;
      $("apiKey").value = "";
      providerMsg = { kind: "ok", key: "providerSaved" };
      renderProvider();
      renderProviderMessages();
      const shouldGenerate = pendingGenerate;
      pendingGenerate = false;
      closeAiPage();
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
      pendingProviderAction = "test";
      openConsent();
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
      // Some OpenAI-compatible services (for example Spark) have no GET /models.
      // That is not a bad key, so show a warning instead of a red error.
      const code = (error && (error.code || error.message)) || "";
      if (code === "PROVIDER_TEST_UNSUPPORTED") {
        providerMsg = { kind: "warn", code };
        renderProviderMessages();
      } else {
        setProviderError(error);
      }
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
      // A save/test that opened this notice resumes once consent is recorded.
      const providerAction = pendingProviderAction;
      pendingProviderAction = "";
      if (providerAction === "save") {
        saveProvider();
        return;
      }
      if (providerAction === "test") {
        testProviderConnection();
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

  /**
   * Declining AI consent only cancels the AI action. The editor stays open:
   * manual banks, imports and preset levels do not need a provider key.
   */
  function dismissConsent() {
    pendingGenerate = false;
    pendingProviderAction = "";
    closeModal();
    renderGenerate();
  }

  function clearGenTimer() {
    if (genTimer) clearTimeout(genTimer);
    genTimer = null;
  }

  // ---- 出题配置（年级 + 上下册是同一组，教材随科目变化）----

  function generationGradeCode() {
    return String(genGradeValue) + (genSemesterValue === 2 ? "b" : "a");
  }

  // The scope is ready only when every curriculum choice comes from a real
  // picker value. Grade 0 / semester 0 / empty subject mean the new member
  // has not been configured yet; nothing may fall back to grade 4 here.
  function generationScopeReady() {
    const grade = Number(genGradeValue);
    if (!Number.isInteger(grade) || !gradeNos().includes(grade) ||
        cloudJuniorBlocked(grade)) {
      return false;
    }
    if (genSemesterValue !== 1 && genSemesterValue !== 2) return false;
    if (!subjectIdsForGrade(grade).includes(genSubjectValue)) return false;
    if (genSubjectValue === SUBJECT_ALL) return true;
    return textbooksFor(grade, genSubjectValue).includes(genTextbookId);
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
    // Saved preferences are already an explicit choice for this member.
    genScopeSetup = false;
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
    if (GEN_QUESTION_TYPE_VALUES.includes(prefs.questionType)) {
      genQuestionTypeValue = prefs.questionType;
    } else {
      genQuestionTypeValue = "single";
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

  /**
   * A new non-default member has no generation preferences of its own. Later
   * members start with an empty scope and an empty topic instead of inheriting
   * the previous member's choices or the install defaults: a second child can
   * be in any grade, so a guessed grade is the easiest way to generate the
   * wrong homework. Only the first/default member keeps the install defaults.
   * Nothing is persisted here: the first generation save belongs to this
   * member only.
   */
  function resetGenerationPrefsForMember() {
    const member = editingMember() || activeMember();
    const setup = Boolean(member && !member.isDefault);
    const grade = Number(member && member.grade);
    const semester = Number(member && member.semester);
    const memberGrade = gradeNos().includes(grade) && !cloudJuniorBlocked(grade)
      ? grade
      : 0;
    genScopeSetup = setup;
    if (setup) {
      // The member's own profile grade is an explicit choice, so keep it and
      // pin the picker. Everything else waits for a parent choice.
      genGradeValue = memberGrade;
      genSemesterValue = memberGrade && (semester === 1 || semester === 2)
        ? semester
        : 0;
      genSubjectValue = "";
      genTextbookId = "";
    } else {
      genGradeValue = memberGrade || 4;
      genSemesterValue = semester === 2 ? 2 : 1;
      const subjects = subjectIdsForGrade(genGradeValue);
      genSubjectValue = subjects.includes("math") ? "math" : (subjects[0] || "math");
      genTextbookId = defaultTextbookFor(genGradeValue, genSubjectValue) || "general";
    }
    genScopeValue = "review";
    genUnitValue = 1;
    genDifficultyValue = "";
    genRequirementValue = "";
    genQuestionTypeValue = "single";
    genOptionsValue = 4;
    genAutoAddValue = true;
    targetCount = 20;
    genPromptDirty = false;
    if (genPrefsSaveTimer) {
      clearTimeout(genPrefsSaveTimer);
      genPrefsSaveTimer = null;
    }
    try {
      localStorage.removeItem(GEN_PREFS_KEY);
    } catch (_) { /* private mode */ }
    syncGenerateComposer(true);
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
      questionType: genQuestionTypeValue,
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
      GEN_QUESTION_TYPE_VALUES.includes(prefs.questionType) &&
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
    // A complete scope means the parent has made every choice; normal
    // auto-fill may resume on the next render.
    genScopeSetup = false;
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
    genScopeSetup = false;
    try {
      localStorage.setItem(GEN_PREFS_KEY, JSON.stringify(prefs));
    } catch (_) { /* private mode */ }
    if (genPrefsSaveTimer) {
      clearTimeout(genPrefsSaveTimer);
      genPrefsSaveTimer = null;
    }
    return postGenerationPrefs(prefs);
  }

  async function loadGenerationPrefsFromTv(resetWhenAbsent) {
    try {
      const data = await api("/api/quiz/admin/generate/prefs", { admin: true });
      if (!data || !data.prefs || typeof data.prefs !== "object") {
        const member = editingMember();
        if (resetWhenAbsent || (member && !member.isDefault)) {
          resetGenerationPrefsForMember();
        }
        return;
      }
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
      // Older TV builds have no prefs endpoint; local memory still applies. After an
      // explicit member switch, reset instead of showing the previous member's form.
      if (resetWhenAbsent) {
        resetGenerationPrefsForMember();
      } else {
        const member = editingMember();
        // A later member must never inherit the default/previous form when
        // its own preferences cannot be read.
        if (member && !member.isDefault) resetGenerationPrefsForMember();
      }
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
    if (hint && !generationScopeReady()) {
      // Do not classify a topic against a grade the parent has not chosen.
      hint.classList.remove("error");
      hint.textContent = "";
    } else if (hint) {
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
      $("genPrompt").value = generationScopeReady() ? composeGenerationPrompt() : "";
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
    // A member profile with a grade pins generation to that grade (and semester when
    // set). Members created before this feature have no grade and keep every chip.
    const memberScope = memberScopeChoice();
    if (memberScope.grade !== null && genGradeValue !== memberScope.grade) {
      genGradeValue = memberScope.grade;
      genUnitValue = 1;
    }
    if (memberScope.semester !== null && genSemesterValue !== memberScope.semester) {
      genSemesterValue = memberScope.semester;
      genUnitValue = 1;
    }
    if (genScopeSetup) {
      // Setup is explicit: never fill a subject/textbook the parent did not
      // choose. Grade changes invalidate the downstream picks, so clear them.
      if (genSubjectValue === SUBJECT_ALL) {
        genTextbookId = "general";
      } else if (genSubjectValue &&
          !subjectIdsForGrade(genGradeValue).includes(genSubjectValue)) {
        genSubjectValue = "";
        genTextbookId = "";
      } else if (genSubjectValue && genTextbookId &&
          !generationTextbookBooks().includes(genTextbookId)) {
        genTextbookId = "";
      }
    } else {
      keepGenerationSubject();
      keepGenerationTextbook();
    }

    const hasGrade = Number.isInteger(genGradeValue) && genGradeValue >= 1 &&
      gradeNos().includes(genGradeValue);
    if ($("genSemesterField")) {
      $("genSemesterField").classList.toggle("hidden", !hasGrade);
    }
    if ($("genSubjectField")) {
      $("genSubjectField").classList.toggle("hidden", !hasGrade);
    }
    const setupHint = $("genSetupHint");
    if (setupHint) {
      const showSetup = !generationScopeReady();
      setupHint.classList.toggle("hidden", !showSetup);
      setupHint.textContent = showSetup ? t("genScopeSetupHint") : "";
    }

    renderChipRow(
      $("genGradeChips"),
      memberScope.grade !== null ? [memberScope.grade] : gradeNos(),
      genGradeValue,
      "data-grade",
      (value) => gradeLabel(value),
      (value) => cloudJuniorBlocked(value));
    const gradeHint = $("genGradeHint");
    if (gradeHint) {
      const blocked = cloudJuniorBlocked(genGradeValue);
      gradeHint.textContent = blocked ? t("cloudJuniorUnsupported") : "";
      gradeHint.classList.toggle("hidden", !blocked);
    }
    renderChipRow(
      $("genSemester"),
      memberScope.semester !== null ? [memberScope.semester] : [1, 2],
      genSemesterValue,
      "data-semester",
      (value) => t(Number(value) === 2 ? "semesterSecond" : "semesterFirst"));

    renderGenerationSubjects();
    // All-subject generation forces textbook=ALL on the wire, so a textbook
    // picker would be misleading; the field (and its curation hint) is hidden.
    const textbookField = $("genTextbookField");
    if (textbookField) {
      const showTextbook = hasGrade && Boolean(genSubjectValue) &&
        genSubjectValue !== SUBJECT_ALL;
      textbookField.classList.toggle("hidden", !showTextbook);
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
    renderChipRow($("genQuestionTypeChips"), GEN_QUESTION_TYPE_VALUES, genQuestionTypeValue,
      "data-question-type",
      (value) => t(GEN_QUESTION_TYPE_LABEL_KEYS[value] || "genTypeSingle"));
    renderChipRow($("genOptionsChips"), GEN_OPTIONS_VALUES, genOptionsValue, "data-options",
      (value) => String(value));
    if (recompose && !genPromptDirty) {
      $("genPrompt").value = generationScopeReady() ? composeGenerationPrompt() : "";
    }
    updatePromptBadge();
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

  // A course source also filters by answer kind, so generating only single
  // choice questions cannot fill a multiple-choice source.
  function generationFitsSource(source) {
    if (!source || source.type !== "course") return true;
    if (!generationFitsAnswerScope(source.scope)) return false;
    if (source.answerKind === "single" && genQuestionTypeValue === "multiple") return false;
    if (source.answerKind === "multiple" && genQuestionTypeValue === "single") return false;
    return true;
  }

  function generationTargetCourseSource(level) {
    const courses = (level && Array.isArray(level.sources) ? level.sources : [])
      .filter((source) => source.type === "course");
    return courses.find((source) => !generationFitsSource(source)) || courses[0] || null;
  }

  function canAlignGenerationToAnswerScope(scope) {
    const ans = normalizeAnswerScope(scope);
    if (!ans || cloudJuniorBlocked(ans.grade)) return false;
    // Generation stays inside the member's grade/semester; a level that conflicts
    // with it is edited in the level source instead of silently pulling the AI out.
    const memberScope = memberScopeChoice();
    if (memberScope.grade !== null &&
        (ans.grade !== memberScope.grade ||
         (memberScope.semester !== null && ans.semester !== memberScope.semester))) {
      return false;
    }
    if (ans.subject === SUBJECT_ALL) return true;
    if (!subjectIdsForGrade(ans.grade).includes(ans.subject)) return false;
    return ans.textbook === TEXTBOOK_ALL ||
      textbooksFor(ans.grade, ans.subject).includes(ans.textbook);
  }

  function updateGenerationScopeStatus() {
    const box = $("genScopeStatus");
    if (!box) return;
    // No range chosen yet, so there is nothing to compare with the TV.
    if (!generationScopeReady()) {
      box.classList.add("hidden");
      return;
    }
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
    const source = target ? generationTargetCourseSource(target) : null;
    const mismatch = Boolean(source) && !generationFitsSource(source);
    box.classList.remove("hidden");
    // 不足和范围不一致都属于需要处理的异常，统一用警示色；
    // 蓝色只留给两张卡的数据流标签。
    box.classList.add("warn");
    if (mismatch && target && source) {
      const index = levelPackages.indexOf(target);
      $("genScopeStatusText").textContent = t("genScopeStatusLevelMismatch", {
        level: levelLabel(target, index),
      });
    } else {
      $("genScopeStatusText").textContent =
        t("genScopeStatusLevelsInsufficient", { n: short.length, pull });
    }
    const canAlign = mismatch && Boolean(source) &&
      canAlignGenerationToAnswerScope(source.scope);
    $("genScopeStatusActions").classList.toggle("hidden", !mismatch);
    $("alignGenToAnswer").classList.toggle("hidden", !canAlign);
    $("genScopeStatusNote").classList.toggle("hidden", !canAlign);
  }

  function alignGenerationToAnswerScope() {
    const target = generationTargetLevel();
    const source = target ? generationTargetCourseSource(target) : null;
    const ans = source ? source.scope : null;
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
    renderHome();
    renderGenerate();
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
      if (courseSourceScopeLock && courseSourceScopeLock.grade) {
        return [String(courseSourceScopeLock.grade)];
      }
      return gradeNos().filter((no) => !cloudJuniorBlocked(no)).map(String);
    }
    if (step === "semester") {
      if (courseSourceScopeLock && courseSourceScopeLock.semester) {
        return [String(courseSourceScopeLock.semester)];
      }
      return ["1", "2"];
    }
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
      // "Any difficulty" is selected in the model from the start, but a new
      // source has not answered that step yet. Leave every chip unpressed so
      // the parent makes the final choice and then sees the type filter.
      const pressed = String(entry.value) === String(current) &&
        (step !== "difficulty" || courseSourceDifficultyPicked);
      button.setAttribute("aria-pressed", String(pressed));
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
    // The chosen answer type narrows the hierarchy exactly like the other
    // filters. The response still reports every backed type in
    // facets.answerKind so the chips never hide an option that has data.
    if (courseSourceAnswerKind !== "any") add("answerKind", courseSourceAnswerKind);
    // The picker offers only branches the chosen bank can fill.
    if (courseSourceOrigin === "preset" || courseSourceOrigin === "ai") {
      add("origin", courseSourceOrigin);
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
        kindCounts: normalizeAnswerKindCounts(
          data && data.facets && data.facets.answerKind),
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
        if (courseSourceScopeLock && courseSourceScopeLock.grade &&
            number !== courseSourceScopeLock.grade) {
          return;
        }
      } else {
        if (number !== 1 && number !== 2) return;
        if (courseSourceScopeLock && courseSourceScopeLock.semester &&
            number !== courseSourceScopeLock.semester) {
          return;
        }
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
    if (String(value) === String(scopeDraftValue(step, draft))) {
      // Tapping the already-selected "any difficulty" confirms the last
      // hierarchy step; that tap is what reveals the type filter. Without
      // this a new source would show difficulty and type at the same time.
      if (step === "difficulty" && !courseSourceDifficultyPicked) {
        courseSourceDifficultyPicked = true;
        renderLevelEditor();
      }
      return;
    }

    // Changing any upstream step clears difficulty, so the type filter must
    // wait until the parent answers the difficulty step again.
    if (step !== "difficulty") courseSourceDifficultyPicked = false;

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
      courseSourceDifficultyPicked = true;
    }

    customScope = draft;
    $("levelEditorError").textContent = "";
    invalidateScopeAfter(step);
    renderLevelEditor();
  }

  function levelLabel(level, index) {
    const name = String(level && level.name || "").trim();
    return name || packageSummaryText(level) || t("levelFallbackName", { n: Number(index) + 1 });
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

  function answerKindLabel(kind) {
    if (kind === "single") return t("levelTypeSingle");
    if (kind === "multiple") return t("levelTypeMultiple");
    return t("levelTypeAny");
  }

  function answerKindCount(counts, kind) {
    if (!counts) return null;
    const value = kind === "single" ? counts.single
      : kind === "multiple" ? counts.multiple : counts.total;
    return Math.max(0, Number(value || 0));
  }

  // The facets endpoint reports every backed type under the upstream scope
  // filters. It is the same shape as /level-counts after normalization, so the
  // chips and the saved typeCounts share one representation.
  function normalizeAnswerKindCounts(raw) {
    if (!raw || typeof raw !== "object") return null;
    const single = Math.max(0, Number(raw.single || 0));
    const multiple = Math.max(0, Number(raw.multiple || 0));
    return { total: single + multiple, single, multiple };
  }

  function courseOriginLabel(origin) {
    if (origin === "preset") return t("levelSourcePresetTag");
    if (origin === "ai") return t("levelSourceAiTag");
    return t("levelSourceLegacyTag");
  }

  function sourceShortLabel(source) {
    if (!source) return "";
    if (source.type === "wrong") return t("wrongLevelFallbackName");
    if (source.type === "collection") {
      return String(source.collectionName || "").trim() || t("customLevelFallbackName");
    }
    if (source.type === "preset") return t("levelSourceLife");
    const parts = [courseOriginLabel(source.origin)];
    const summary = scopeSummaryText(source.scope);
    if (summary) parts.push(summary);
    if (source.answerKind && source.answerKind !== "any") {
      parts.push(answerKindLabel(source.answerKind));
    }
    return parts.join(" · ");
  }

  function packageSummaryText(level) {
    return (level && Array.isArray(level.sources) ? level.sources : [])
      .map(sourceShortLabel)
      .filter(Boolean)
      .join(" + ");
  }

  function sourceAvailable(source) {
    const value = Number(source && source.available);
    return Number.isFinite(value) ? Math.max(0, value) : null;
  }

  // "Total" is what the source holds, while "available" is what the level can
  // still draw now. A custom tag's total stays visible after a round consumes
  // some rows, so a successful import can be checked at a glance.
  function sourceTotal(source) {
    if (!source) return null;
    if (source.type === "collection") {
      if (source.total !== undefined && source.total !== null) {
        const stored = Number(source.total);
        if (Number.isFinite(stored)) return Math.max(0, stored);
      }
      const collection = importCollections.find((item) => item.id === source.collectionId);
      const value = Number(collection && collection.total);
      return Number.isFinite(value) ? Math.max(0, value) : null;
    }
    if (source.type === "course" && source.typeCounts) {
      const value = Number(source.typeCounts.total);
      return Number.isFinite(value) ? Math.max(0, value) : null;
    }
    if (source.type === "preset") {
      if (source.total !== undefined && source.total !== null) {
        const stored = Number(source.total);
        if (Number.isFinite(stored)) return Math.max(0, stored);
      }
      const fromBank = Number(presetBank.total);
      return Number.isFinite(fromBank) ? Math.max(0, fromBank) : null;
    }
    if (source.type === "wrong") return sourceAvailable(source);
    return null;
  }

  // Custom tags and the bundled bank keep a stable total even after consumed
  // rows reduce what the next round can draw.
  function sourceShowsTotal(source) {
    return Boolean(source) && (source.type === "collection" || source.type === "preset");
  }

  function sourceTotalSum(sources) {
    return (sources || []).reduce((sum, source) => {
      const total = sourceTotal(source);
      if (total !== null) return sum + total;
      const available = sourceAvailable(source);
      return sum + (available === null ? 0 : available);
    }, 0);
  }

  function sourceAvailableSum(sources) {
    return (sources || []).reduce((sum, source) => {
      const available = sourceAvailable(source);
      return sum + (available === null ? 0 : available);
    }, 0);
  }

  function packageAvailable(level) {
    const value = Number(level && level.available);
    return Number.isFinite(value) ? Math.max(0, value) : 0;
  }

  function shortLevels(pull) {
    // Paused levels are deliberately out of the child's rotation, so they must
    // not raise the "not enough questions" warning or become a generation target.
    return levelPackages.filter((level) =>
      level.enabled !== false && packageAvailable(level) < pull);
  }

  // The generation hint targets the first short package with a course source
  // that the current generator cannot fill, falling back to the first short
  // package. Tag and wrong-review sources are generated directly by the
  // import flow, so only course sources can be aligned.
  function generationTargetLevel() {
    const pull = Number(currentConfig().pullCount || 5);
    const short = shortLevels(pull);
    if (!short.length) return null;
    return short.find((level) => {
      const source = generationTargetCourseSource(level);
      return source && !generationFitsSource(source);
    }) || short[0];
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
    const pull = Number(currentConfig().pullCount || 5);
    levelPackages.forEach((level, index) => {
      const availableCount = packageAvailable(level);
      const enabled = level.enabled !== false;
      const row = document.createElement("div");
      row.className = "level-row";
      row.dataset.levelId = level.id;
      if (enabled && availableCount < pull) row.classList.add("short");
      if (!enabled) row.classList.add("paused");

      const main = document.createElement("button");
      main.type = "button";
      main.className = "level-main";
      main.dataset.packageEdit = level.id;

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
      const name = String(level.name || "").trim();
      if (name) {
        title.textContent = name;
        summary.textContent = t("levelSourcesCount", {
          n: level.sources.length,
          max: levelLimits.maxSources,
        });
      } else {
        title.textContent = packageSummaryText(level) || t("levelFallbackName", { n: index + 1 });
      }
      const label = levelLabel(level, index);
      // Show the package contents under the level, not just a joined summary:
      // one level can hold several independently configured question sources.
      const sourceList = document.createElement("span");
      sourceList.className = "level-source-list";
      level.sources.forEach((source, sourceIndex) => {
        const line = document.createElement("span");
        line.className = "level-source-line";
        const count = sourceAvailable(source);
        const total = sourceTotal(source);
        const countText = count === null
          ? ""
          : " · " + (sourceShowsTotal(source) && total !== null
            ? t("levelSourceTotalAvailable", { total, available: count })
            : t("levelSourceAvailable", { n: count }));
        line.textContent = t("levelSourceIndex", { n: sourceIndex + 1 }) +
          " · " + sourceShortLabel(source) + countText;
        sourceList.appendChild(line);
      });
      const available = document.createElement("span");
      available.className = "level-available";
      const totalCount = sourceTotalSum(level.sources);
      available.textContent = enabled && availableCount < pull
        ? t("levelShort", { total: totalCount, available: availableCount, pull })
        : t("levelAvailable", { total: totalCount, available: availableCount });
      if (enabled && availableCount < pull) available.classList.add("warn");
      copy.appendChild(title);
      if (!enabled) {
        const pausedNote = document.createElement("span");
        pausedNote.className = "level-paused-note";
        pausedNote.textContent = t("levelPausedHint");
        copy.appendChild(pausedNote);
      }
      if (summary.textContent) copy.appendChild(summary);
      copy.appendChild(sourceList);
      copy.appendChild(available);
      main.appendChild(copy);
      row.appendChild(main);

      const actions = document.createElement("div");
      actions.className = "level-actions";
      // A paused level stays in the list so the parent can edit or re-enable
      // it, but the TV's answer-challenge wheel only receives enabled levels.
      const toggle = document.createElement("label");
      toggle.className = "level-switch";
      toggle.title = t(enabled ? "levelToggleOnAria" : "levelToggleOffAria", { name: label });
      const toggleInput = document.createElement("input");
      toggleInput.type = "checkbox";
      toggleInput.checked = enabled;
      toggleInput.dataset.levelToggle = level.id;
      toggleInput.setAttribute("aria-label", toggle.title);
      toggleInput.disabled = levelSaving || packageSaving;
      const toggleTrack = document.createElement("span");
      toggleTrack.className = "level-switch-track";
      toggleTrack.setAttribute("aria-hidden", "true");
      toggle.append(toggleInput, toggleTrack);
      actions.appendChild(toggle);
      const addAction = (attr, text, aria) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "level-action";
        button.setAttribute(attr, level.id);
        button.setAttribute("aria-label", t(aria, { name: label }));
        button.textContent = text;
        button.disabled = levelSaving || packageSaving;
        actions.appendChild(button);
        return button;
      };
      const up = addAction("data-level-up", "↑", "levelMoveUpAria");
      up.disabled = levelSaving || packageSaving || index === 0;
      const down = addAction("data-level-down", "↓", "levelMoveDownAria");
      down.disabled = levelSaving || packageSaving || index === levelPackages.length - 1;
      addAction("data-level-delete", "×", "levelDeleteAria");
      row.appendChild(actions);
      list.appendChild(row);
    });

    $("levelEmpty").classList.toggle(
      "hidden", levelPackages.length > 0 || packageEditorActive);
    $("levelListCount").textContent = t("levelListCount", {
      n: levelPackages.length,
      max: levelLimits.maxLevels,
    });
    if ($("levelsTotalHint")) {
      $("levelsTotalHint").textContent = t("levelsTotalHint", {
        used: levelPackages.length,
        max: levelLimits.maxLevels,
      });
      $("levelsTotalHint").classList.toggle(
        "warn",
        levelPackages.length > levelLimits.maxLevels,
      );
    }
    const atMax = levelPackages.length >= levelLimits.maxLevels;
    if ($("addLevel")) $("addLevel").disabled = levelSaving || packageSaving || atMax;
    if ($("levelMaxHint")) {
      $("levelMaxHint").classList.toggle("hidden", !atMax);
      $("levelMaxHint").textContent = t("levelMaxHint", { n: levelLimits.maxLevels });
    }
    if ($("levelError") && levelPackagesUnreadable) {
      $("levelError").textContent = t("levelPackagesUnreadable");
    }
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

  function normalizeLevelSource(raw) {
    if (!raw || typeof raw !== "object") return null;
    const available = Math.max(0, Number(raw.available || 0));
    if (raw.type === "course") {
      const scope = normalizeAnswerScope(raw.scope);
      if (!scope) return null;
      const answerKind = ANSWER_KIND_VALUES.includes(raw.answerKind)
        ? raw.answerKind : "any";
      const rawCounts = raw.typeCounts && typeof raw.typeCounts === "object"
        ? raw.typeCounts : null;
      const typeCounts = rawCounts
        ? {
          total: Math.max(0, Number(rawCounts.total || 0)),
          single: Math.max(0, Number(rawCounts.single || 0)),
          multiple: Math.max(0, Number(rawCounts.multiple || 0)),
        }
        : null;
      // Documents written before the preset/AI split have no origin; they keep
      // the combined preset + AI behavior instead of silently becoming preset.
      const origin = raw.origin === "preset" || raw.origin === "ai" ? raw.origin : "all";
      return { type: "course", scope, answerKind, origin, available, typeCounts };
    }
    if (raw.type === "collection") {
      const collectionId = String(raw.collectionId || "");
      if (!collectionId) return null;
      return {
        type: "collection",
        collectionId,
        collectionName: String(raw.collectionName || ""),
        available,
      };
    }
    if (raw.type === "preset") {
      const bankId = String(raw.bankId || "").trim();
      if (!bankId) return null;
      const total = Number(raw.total);
      return {
        type: "preset",
        bankId,
        available,
        total: Number.isFinite(total) ? Math.max(0, total) : null,
      };
    }
    if (raw.type === "wrong") return { type: "wrong", available };
    return null;
  }

  function firstCourseScope() {
    for (const level of levelPackages) {
      for (const source of level.sources) {
        if (source.type === "course") return source.scope;
      }
    }
    return null;
  }

  function applyLevelPackages(rawPackages, options) {
    const quiet = Boolean(options && options.quiet);
    levelPackages = (Array.isArray(rawPackages) ? rawPackages : [])
      .map((item) => {
        const id = String(item && item.id || "");
        if (!id) return null;
        const sources = (Array.isArray(item.sources) ? item.sources : [])
          .map(normalizeLevelSource)
          .filter(Boolean);
        if (!sources.length) return null;
        return {
          id,
          name: String(item.name || "").trim(),
          // Missing on records written before the pause switch; those levels
          // stay enabled so an upgrade never hides a parent's work.
          enabled: item.enabled !== false,
          available: Math.max(0, Number(item.available || 0)),
          sources,
        };
      })
      .filter(Boolean)
      .slice(0, levelLimits.maxLevels);
    const scope = firstCourseScope();
    answerScope = scope ? Object.assign({}, scope) : null;
    answerScopeMode = levelPackages.length ? "custom" : "bound";
    answerScopeAvailable = levelPackages.reduce(
      (max, level) => Math.max(max, level.available),
      0,
    );
    scopeLoaded = true;
    if (!quiet) {
      if ($("answerTabs")) renderAnswerPage();
      renderHome();
    }
  }

  // Legacy projection from the TV, used only by an old page or a TV mid-update.
  // It never becomes editable input.
  function applyLevelPackageProjection(data) {
    if (levelPackages.length) return;
    const scope = normalizeAnswerScope(data && data.answerScope);
    answerScope = scope ? Object.assign({}, scope) : null;
    answerScopeMode = data && data.answerScopeMode === "custom" ? "custom" : "bound";
    answerScopeAvailable = Math.max(0, Number(data && data.answerScopeAvailable || 0));
  }

  function allLevelCount() {
    return levelPackages.length;
  }

  // ---- Course source editor ----

  // The deepest loaded step has the most upstream filters applied, so its
  // answerKind facet is the best current count for the draft. Partial data is
  // only a fallback until /level-counts returns the exact complete-scope split.
  function scopeKindSupportCounts() {
    const visible = scopeVisibleSteps(customScope || {});
    for (let index = visible.length - 1; index >= 0; index--) {
      const data = scopeFacets[visible[index]];
      if (data && !data.failed && data.kindCounts) return data.kindCounts;
    }
    return null;
  }

  function courseSourceKindSupport() {
    return courseSourceCounts || scopeKindSupportCounts();
  }

  function courseSourceCountsText() {
    const support = courseSourceKindSupport();
    if (!support) return "";
    if (courseSourceAnswerKind === "any") {
      return t("levelTypeCountsAll", {
        total: support.total,
        single: support.single,
        multiple: support.multiple,
      });
    }
    const count = answerKindCount(support, courseSourceAnswerKind);
    if (count !== null && count <= 0) {
      return t("levelTypeEmpty", { kind: answerKindLabel(courseSourceAnswerKind) });
    }
    return t("levelTypeCountsWith", {
      kind: answerKindLabel(courseSourceAnswerKind),
      n: count,
      total: support.total,
    });
  }

  function renderCourseSourceKindControls() {
    const host = $("courseSourceKindChips");
    if (!host) return;
    const support = courseSourceKindSupport();
    const kinds = ANSWER_KIND_VALUES.filter((kind) => {
      // Keep an existing selection visible even at 0 so a parent editing a
      // now-empty type sees why saving is blocked instead of a disappearing
      // chip. Everything else is shown only when the bank proves it has data.
      if (kind === courseSourceAnswerKind) return true;
      if (!support) return kind === "any";
      return answerKindCount(support, kind) > 0;
    });
    host.replaceChildren();
    kinds.forEach((kind) => {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = "choice-chip";
      chip.dataset.courseKind = kind;
      chip.setAttribute("aria-pressed", String(kind === courseSourceAnswerKind));
      chip.appendChild(document.createTextNode(answerKindLabel(kind)));
      const count = answerKindCount(support, kind);
      if (count !== null) {
        const badge = document.createElement("span");
        badge.className = "chip-count";
        badge.textContent = String(count);
        chip.appendChild(badge);
      }
      host.appendChild(chip);
    });
    if ($("courseSourceKindCounts")) {
      $("courseSourceKindCounts").textContent = courseSourceCountsText();
      const selected = answerKindCount(support, courseSourceAnswerKind);
      $("courseSourceKindCounts").classList.toggle(
        "warn", support !== null && selected !== null && selected <= 0);
    }
    const count = answerKindCount(support, courseSourceAnswerKind);
    if ($("saveLevel")) {
      $("saveLevel").disabled = levelSaving || packageSaving ||
        Boolean(scopeOptionsLoading.courseCounts) ||
        (count !== null && count <= 0);
    }
  }

  function ensureCourseSourceCounts() {
    const scope = normalizeAnswerScope(customScope);
    if (!scope) {
      // Do not leave a previous scope's split next to an incomplete draft.
      courseSourceCounts = null;
      courseSourceCountsKey = "";
      courseSourceCountsToken++;
      delete scopeOptionsLoading.courseCounts;
      return;
    }
    const key = levelScopeSignature(scope) + "|" + courseSourceOrigin;
    if (key === courseSourceCountsKey &&
        (courseSourceCounts !== null || scopeOptionsLoading.courseCounts)) {
      return;
    }
    courseSourceCountsKey = key;
    courseSourceCounts = null;
    scopeOptionsLoading.courseCounts = true;
    const token = ++courseSourceCountsToken;
    api("/api/quiz/admin/level-counts", {
      method: "POST",
      admin: true,
      body: { scopes: [scopeToCanonical(scope)], origin: courseSourceOrigin },
    })
      .then((data) => {
        if (token !== courseSourceCountsToken || courseSourceCountsKey !== key) return;
        const first = Array.isArray(data && data.counts) ? data.counts[0] : null;
        courseSourceCounts = {
          total: Math.max(0, Number(first && first.total || 0)),
          single: Math.max(0, Number(first && first.single || 0)),
          multiple: Math.max(0, Number(first && first.multiple || 0)),
        };
        if (levelEditorOpen()) renderCourseSourceKindControls();
      })
      .catch((error) => {
        if (token !== courseSourceCountsToken || courseSourceCountsKey !== key) return;
        courseSourceCounts = null;
        if (levelEditorOpen() && error && error.code !== "NOT_FOUND") {
          $("levelEditorError").textContent = errorText(error);
        }
      })
      .finally(() => {
        if (token === courseSourceCountsToken) {
          delete scopeOptionsLoading.courseCounts;
          if (levelEditorOpen()) renderCourseSourceKindControls();
        }
      });
  }

  function renderLevelEditor() {
    if (!$("levelEditorPage")) return;
    if (!customScope || typeof customScope !== "object") customScope = {};
    $("levelEditorTitle").textContent = t(
      courseSourceOrigin === "preset" ? "levelSourcePreset"
        : courseSourceOrigin === "ai" ? "levelSourceAi"
          : "levelSourceLegacy",
    );
    renderCustomScopeControls();
    ensureScopeOptions();
    ensureCourseSourceCounts();
    // The type filter is the last step: the difficulty step must be answered
    // first, even when that answer is "any difficulty". Otherwise selecting a
    // topic would reveal the difficulty and type filters in the same frame.
    if ($("courseSourceKindField")) {
      $("courseSourceKindField").classList.toggle(
        "hidden",
        !scopeRequiredComplete(customScope) || !courseSourceDifficultyPicked,
      );
    }
    renderCourseSourceKindControls();
    $("levelEditorAvailable").textContent = scopeAvailableText(customScope);
    $("levelEditorEmpty").classList.toggle("hidden", !scopeIsEmpty(customScope));
  }

  function selectCourseSourceAnswerKind(kind) {
    if (!ANSWER_KIND_VALUES.includes(kind) || kind === courseSourceAnswerKind) return;
    courseSourceAnswerKind = kind;
    $("levelEditorError").textContent = "";
    // The type applies to every step, unlike a grade change that only
    // invalidates downstream facets. Reload the whole hierarchy so only
    // scopes with data of the chosen type remain selectable.
    resetScopeOptions();
    ensureScopeOptions();
    renderLevelEditor();
  }

  function openCourseSourceEditor(index, presetScope, origin) {
    let source = null;
    if (index !== null && index !== undefined) {
      source = packageDraftSources[index];
      if (!source || source.type !== "course") return false;
      courseSourceEditingIndex = index;
    } else {
      courseSourceEditingIndex = null;
    }
    courseSourceOrigin = source
      ? (source.origin === "preset" || source.origin === "ai" ? source.origin : "all")
      : (origin === "ai" ? "ai" : "preset");
    // A new source starts on the first hierarchy step only; every later step
    // appears after the parent picks an option that the bank proves has data.
    // Editing keeps the full stored scope so any step stays changeable.
    const memberScope = source ? null : memberScopeChoice();
    const memberPreset = memberScope && memberScope.grade !== null
      ? Object.assign(
        { grade: memberScope.grade },
        memberScope.semester !== null ? { semester: memberScope.semester } : {})
      : null;
    customScope = source
      ? Object.assign({}, source.scope)
      : Object.assign({}, presetScope || {}, memberPreset || {});
    courseSourceScopeLock = memberPreset;
    courseSourceAnswerKind = source ? source.answerKind : "any";
    // Editing an existing source already has every step answered; a new
    // source waits for an explicit difficulty choice before showing the type.
    courseSourceDifficultyPicked = Boolean(source);
    courseSourceCounts = null;
    courseSourceCountsKey = "";
    courseSourceCountsToken++;
    answerScopeMode = "custom";
    $("levelEditorError").textContent = "";
    resetScopeOptions();
    showGenPage("level");
    ensureScopeOptions();
    ensureCourseSourceCounts();
    renderLevelEditor();
    return true;
  }

  function courseSourceDuplicate(scope, kind) {
    const signature = levelScopeSignature(scope);
    return packageDraftSources.some((source, index) =>
      source.type === "course" &&
      index !== courseSourceEditingIndex &&
      levelScopeSignature(source.scope) === signature &&
      (source.answerKind || "any") === kind &&
      (source.origin || "all") === courseSourceOrigin);
  }

  function saveLevelDraft() {
    if (levelSaving || packageSaving) return;
    $("levelEditorError").textContent = "";
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
    const kind = courseSourceAnswerKind;
    if (courseSourceCounts === null && scopeOptionsLoading.courseCounts) {
      $("levelEditorError").textContent = t("scopeLoading");
      return;
    }
    const support = courseSourceKindSupport();
    const count = answerKindCount(support, kind);
    if (support && count !== null && count <= 0) {
      $("levelEditorError").textContent = t("levelTypeEmpty", {
        kind: answerKindLabel(kind),
      });
      return;
    }
    if (courseSourceDuplicate(scope, kind)) {
      $("levelEditorError").textContent = t("levelSourceDuplicateCourse");
      return;
    }
    const source = {
      type: "course",
      scope,
      origin: courseSourceOrigin,
      answerKind: kind,
      available: count === null ? 0 : count,
      typeCounts: courseSourceCounts
        ? Object.assign({}, courseSourceCounts)
        : (support ? Object.assign({}, support) : null),
    };
    if (courseSourceEditingIndex === null) {
      packageDraftSources = packageDraftSources.concat([source]);
    } else {
      packageDraftSources = packageDraftSources.map((item, index) =>
        index === courseSourceEditingIndex ? source : item);
    }
    const added = courseSourceEditingIndex === null;
    courseSourceEditingIndex = null;
    customScope = null;
    courseSourceScopeLock = null;
    courseSourceAnswerKind = "any";
    courseSourceOrigin = "all";
    courseSourceDifficultyPicked = false;
    courseSourceCounts = null;
    courseSourceCountsKey = "";
    courseSourceCountsToken++;
    resetScopeOptions();
    // Return to the level draft that owns this source. Saving a source is one
    // step inside the draft, never the creation of the level itself.
    showGenPage("package");
    // Bring the newly added source into view so the parent sees the result
    // of the save immediately, without hunting for the draft panel.
    const sourceList = $("packageSourceList");
    if (sourceList && sourceList.lastElementChild) {
      sourceList.lastElementChild.scrollIntoView({ block: "center" });
    }
    if ($("packageEditorError")) {
      $("packageEditorError").textContent = t(added ? "levelSourceAdded" : "levelSourceUpdated");
      $("packageEditorError").classList.remove("error");
      $("packageEditorError").classList.add("ok");
      setTimeout(() => {
        if ($("packageEditorError")) {
          $("packageEditorError").textContent = "";
          $("packageEditorError").classList.remove("ok");
          $("packageEditorError").classList.add("error");
        }
      }, 2400);
    }
    return true;
  }

  // ---- Package editor ----

  function packageDraftPayload(level) {
    const payload = {
      name: String(level.name || ""),
      // Saves triggered by reorder, delete, edit or the pause switch must all
      // keep the field; a missing value would resume a paused level.
      enabled: level.enabled !== false,
      sources: (level.sources || []).map((source) => {
        if (source.type === "course") {
          const payload = {
            type: "course",
            scope: scopeToCanonical(source.scope),
            answerKind: source.answerKind || "any",
          };
          // "all" is the pre-split combined source; omit it so old TV builds
          // and old documents keep their exact shape.
          if (source.origin === "preset" || source.origin === "ai") {
            payload.origin = source.origin;
          }
          return payload;
        }
        if (source.type === "collection") {
          return { type: "collection", collectionId: source.collectionId };
        }
        if (source.type === "preset") {
          return { type: "preset", bankId: source.bankId };
        }
        return { type: "wrong" };
      }),
    };
    if (level.id) payload.id = level.id;
    return payload;
  }

  function packageSourceMeta(source) {
    if (source.type === "course") {
      const counts = source.typeCounts;
      if (counts) {
        return t("levelTypeCountsWith", {
          kind: answerKindLabel(source.answerKind),
          n: answerKindCount(counts, source.answerKind),
          total: counts.total,
        });
      }
      return answerKindLabel(source.answerKind);
    }
    if (source.type === "collection") return t("customLevelBadge");
    if (source.type === "preset") return t("levelSourceLifeMeta");
    return t("wrongLevelMeta");
  }

  function renderPackageSources() {
    const list = $("packageSourceList");
    if (!list) return;
    list.replaceChildren();
    packageDraftSources.forEach((source, index) => {
      const row = document.createElement("div");
      row.className = "level-row package-source-row"
        + (source.type === "collection" ? " custom" : "")
        + (source.type === "wrong" ? " wrong" : "");

      const main = document.createElement("button");
      main.type = "button";
      main.className = "level-main";
      if (source.type === "course") main.dataset.packageSourceEdit = String(index);

      const copy = document.createElement("span");
      copy.className = "level-copy";
      const eyebrow = document.createElement("span");
      eyebrow.className = "level-source-eyebrow";
      eyebrow.textContent = t("levelSourceIndex", { n: index + 1 });
      const title = document.createElement("span");
      title.className = "level-title";
      title.textContent = sourceShortLabel(source);
      const meta = document.createElement("span");
      meta.className = "level-meta";
      meta.textContent = packageSourceMeta(source);
      const available = document.createElement("span");
      available.className = "level-available";
      const count = sourceAvailable(source);
      const total = sourceTotal(source);
      if (count !== null) {
        available.textContent = sourceShowsTotal(source) && total !== null
          ? t("levelSourceTotalAvailable", { total, available: count })
          : t("levelSourceAvailable", { n: count });
        if (count <= 0) available.classList.add("warn");
      }
      copy.append(eyebrow, title, meta, available);
      main.appendChild(copy);
      row.appendChild(main);

      const actions = document.createElement("div");
      actions.className = "level-actions";
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "level-action";
      remove.dataset.packageSourceRemove = String(index);
      remove.setAttribute("aria-label", t("levelDeleteAria", {
        name: sourceShortLabel(source),
      }));
      remove.textContent = "×";
      remove.disabled = packageSaving;
      actions.appendChild(remove);
      row.appendChild(actions);
      list.appendChild(row);
    });
    $("packageSourcesEmpty").classList.toggle("hidden", packageDraftSources.length > 0);
    renderPackageTotals();
  }

  // The summary sits above the draft rows so three imported sources can be
  // checked without opening each one. Total counts source contents; available
  // counts what the next round can still draw after cross-source de-duplication.
  function renderPackageTotals() {
    const el = $("packageTotals");
    if (!el) return;
    if (!packageDraftSources.length) {
      el.textContent = "";
      el.classList.remove("warn");
      return;
    }
    const total = sourceTotalSum(packageDraftSources);
    const available = sourceAvailableSum(packageDraftSources);
    const wrong = sourceAvailableSum(
      packageDraftSources.filter((source) => source.type === "wrong"),
    );
    const parts = [
      t("levelSourcesCount", {
        n: packageDraftSources.length,
        max: levelLimits.maxSources,
      }),
      t("levelSourcesTotal", { n: total }),
      t("levelSourceAvailable", { n: available }),
    ];
    if (wrong > 0) parts.push(t("levelWrongIncluded", { n: wrong }));
    el.textContent = parts.join(" · ");
    el.classList.toggle(
      "warn",
      packageDraftSources.some((source) => {
        const count = sourceAvailable(source);
        return count !== null && count <= 0;
      }),
    );
  }

  // Importing questions refreshes importCollections, not an open level draft.
  // Pull the new tag total/unused counts in without touching unsaved sources.
  function syncDraftCollectionCounts() {
    let changed = false;
    packageDraftSources = packageDraftSources.map((source) => {
      if (source.type !== "collection") return source;
      const collection = importCollections.find(
        (item) => item.id === source.collectionId,
      );
      if (!collection) return source;
      const next = Object.assign({}, source, {
        collectionName: collection.name || source.collectionName,
        total: collection.total,
        available: collection.unused,
      });
      if (next.collectionName === source.collectionName &&
          next.total === source.total &&
          next.available === source.available) {
        return source;
      }
      changed = true;
      return next;
    });
    if (changed) renderPackageSources();
  }

  function renderPackageEditor() {
    if (!$("packageDraftPanel")) return;
    if (!packageEditorActive) {
      tagSourceSelected = new Set();
      if ($("sourcePickerError")) $("sourcePickerError").textContent = "";
      return;
    }
    $("packageEditorTitle").textContent = t(
      packageEditingId ? "levelEditTitle" : "levelAddTitle",
    );
    if ($("packageName")) {
      $("packageName").maxLength = levelLimits.maxNameCodePoints;
      if (document.activeElement !== $("packageName")) {
        $("packageName").value = packageNameDraft;
      }
    }
    if ($("packageEditorError") && $("packageEditorError").classList.contains("ok")) {
      // Keep a short "source added" notice across the render.
    } else if ($("packageEditorError")) {
      $("packageEditorError").textContent = "";
    }
    renderPackageSources();
    renderSourcePicker();
    if ($("savePackage")) {
      // One wording for create and edit: adding sources only builds a draft,
      // and the level exists only after this button is pressed.
      $("savePackage").textContent = t("levelSave");
      $("savePackage").disabled = packageSaving || packageDraftSources.length === 0;
    }
  }

  function openPackageEditor(id) {
    const level = id ? levelPackages.find((item) => item.id === id) : null;
    if (id && !level) return false;
    if (!id && levelPackages.length >= levelLimits.maxLevels) return false;
    packageEditingId = level ? level.id : null;
    packageNameDraft = level ? level.name : "";
    packageDraftSources = level
      ? level.sources.map((source) => Object.assign({}, source, {
        scope: source.scope ? Object.assign({}, source.scope) : undefined,
      }))
      : [];
    packageEditorActive = true;
    tagSourceSelected = new Set();
    tagSourceReturnAfterImport = false;
    answerTab = "scope";
    $("packageEditorError").textContent = "";
    $("packageEditorError").classList.remove("ok");
    $("packageEditorError").classList.add("error");
    $("packageName").value = packageNameDraft;
    showGenPage("package");
    return true;
  }

  function closePackageEditor() {
    packageEditingId = null;
    packageDraftSources = [];
    packageNameDraft = "";
    packageEditorActive = false;
    tagSourceSelected = new Set();
    tagSourceReturnAfterImport = false;
    courseSourceEditingIndex = null;
    courseSourceAnswerKind = "any";
    courseSourceOrigin = "all";
    courseSourceDifficultyPicked = false;
    customScope = null;
    courseSourceScopeLock = null;
    resetScopeOptions();
    answerTab = "scope";
    showGenPage("answer");
  }

  function renderSourcePicker() {
    const hasLife = packageDraftSources.some((source) => source.type === "preset");
    const hasWrong = packageDraftSources.some((source) => source.type === "wrong");
    const atLimit = packageDraftSources.length >= levelLimits.maxSources;
    $("pickPresetSource").disabled = packageSaving || atLimit;
    $("pickAiSource").disabled = packageSaving || atLimit;
    $("pickTagSource").disabled = packageSaving || atLimit;
    $("pickLifeSource").disabled = packageSaving || atLimit || hasLife || !presetBank.bankId;
    $("pickWrongSource").disabled = packageSaving || atLimit || hasWrong;
    // Every source card carries a count so the picker is readable at a glance.
    // The curriculum cards use the bank-tab buckets; the life bank has its own
    // non-curriculum total, and wrong review shows the rows available now.
    [
      ["presetSourceBankCount", bankScopeCounts.builtin, "levelSourceBankCount"],
      ["aiSourceBankCount", bankScopeCounts.ai, "levelSourceBankCount"],
      ["customSourceBankCount", bankScopeCounts.manual, "levelSourceBankCount"],
      ["lifeSourceBankCount", presetBank.available, "levelSourceLifeCount"],
      [
        "wrongSourceAvailable",
        Math.max(0, Number(bankCounts && bankCounts.wrong || 0)),
        "levelSourceWrongCount",
      ],
    ].forEach(([id, raw, key]) => {
      const el = $(id);
      if (!el) return;
      const count = Math.max(0, Number(raw || 0));
      el.textContent = t(key, { n: count });
      el.classList.toggle("warn", count <= 0);
    });
  }

  function collectionSourceInDraft(collectionId) {
    return packageDraftSources.some((source) =>
      source.type === "collection" && source.collectionId === collectionId);
  }

  // Manual import is a child page of the level draft, not an inline expander.
  // It never posts anything; selecting tags only changes the draft.
  function openTagSourcePage() {
    if (packageSaving || !packageEditorActive) return;
    tagSourceSelected = new Set();
    $("tagSourcePageError").textContent = "";
    $("sourcePickerError").textContent = "";
    showGenPage("tagSource");
  }

  function backFromTagSource() {
    if (packageSaving) return;
    tagSourceSelected = new Set();
    $("tagSourcePageError").textContent = "";
    showGenPage("package");
  }

  function tagSourceRemaining() {
    return Math.max(0, levelLimits.maxSources - packageDraftSources.length);
  }

  function renderTagSourcePage() {
    if (!$("tagSourcePageList")) return;
    if (!packageEditorActive) {
      showGenPage("package");
      return;
    }
    const remaining = tagSourceRemaining();
    if (tagSourceSelected.size > remaining) {
      tagSourceSelected = new Set(Array.from(tagSourceSelected).slice(0, remaining));
    }
    const host = $("tagSourcePageList");
    host.replaceChildren();
    importCollections.forEach((collection) => {
      const already = collectionSourceInDraft(collection.id);
      const selected = tagSourceSelected.has(collection.id);
      const button = document.createElement("button");
      button.type = "button";
      button.className = "tag-choice tag-source-choice";
      button.dataset.tagSourceChoice = collection.id;
      button.setAttribute("aria-pressed", String(selected));
      button.disabled = packageSaving || already ||
        (!selected && tagSourceSelected.size >= remaining);
      const copy = document.createElement("span");
      copy.className = "tag-choice-copy";
      const name = document.createElement("span");
      name.className = "tag-choice-name";
      name.textContent = collection.name;
      const meta = document.createElement("span");
      meta.className = "tag-choice-meta";
      const counts = collection.unused > 0
        ? t("customLevelTagCounts", {
          total: collection.total,
          unused: collection.unused,
        })
        : t("customLevelTagCountsEmpty", { total: collection.total });
      meta.textContent = already
        ? counts + " · " + t("levelTagSourceAlreadyAdded")
        : counts;
      copy.append(name, meta);
      const mark = document.createElement("span");
      mark.className = "tag-source-choice-check";
      mark.setAttribute("aria-hidden", "true");
      mark.textContent = selected ? "✓" : "";
      button.append(copy, mark);
      host.appendChild(button);
    });
    $("tagSourcePageList").classList.toggle("hidden", importCollections.length === 0);
    $("tagSourcePageEmpty").classList.toggle("hidden", importCollections.length > 0);
    $("tagSourceSelectedCount").textContent = tagSourceSelected.size
      ? t("levelTagSourceSelected", { n: tagSourceSelected.size })
      : "";
    $("tagSourcePageHint").textContent = importCollections.length
      ? (remaining > 0
        ? t("levelTagSourceRemaining", { n: remaining })
        : t("levelSourceLimit", { n: levelLimits.maxSources }))
      : "";
    $("tagSourcePageHint").classList.toggle("warn", remaining === 0);
    $("addTagSources").textContent = t("levelTagSourceAdd", { n: tagSourceSelected.size });
    $("addTagSources").disabled = packageSaving || tagSourceSelected.size === 0;
  }

  function toggleTagSourceSelection(collectionId) {
    if (packageSaving) return;
    if (collectionSourceInDraft(collectionId)) return;
    const collection = importCollections.find((item) => item.id === collectionId);
    if (!collection) {
      $("tagSourcePageError").textContent = t("levelSourceMissingTag");
      return;
    }
    if (tagSourceSelected.has(collectionId)) {
      tagSourceSelected.delete(collectionId);
    } else if (tagSourceSelected.size >= tagSourceRemaining()) {
      $("tagSourcePageError").textContent = t("levelSourceLimit", {
        n: levelLimits.maxSources,
      });
      return;
    } else {
      tagSourceSelected.add(collectionId);
    }
    $("tagSourcePageError").textContent = "";
    renderTagSourcePage();
  }

  function addSelectedTagSources() {
    if (packageSaving || !tagSourceSelected.size) return;
    const remaining = tagSourceRemaining();
    if (tagSourceSelected.size > remaining) {
      $("tagSourcePageError").textContent = t("levelSourceLimit", {
        n: levelLimits.maxSources,
      });
      return;
    }
    const sources = [];
    tagSourceSelected.forEach((collectionId) => {
      if (collectionSourceInDraft(collectionId)) return;
      const collection = importCollections.find((item) => item.id === collectionId);
      if (!collection) return;
      sources.push({
        type: "collection",
        collectionId: collection.id,
        collectionName: collection.name,
        total: collection.total,
        available: collection.unused,
      });
    });
    if (!sources.length) {
      $("tagSourcePageError").textContent = t("levelSourceMissingTag");
      return;
    }
    packageDraftSources = packageDraftSources.concat(sources);
    tagSourceSelected = new Set();
    $("tagSourcePageError").textContent = "";
    $("sourcePickerError").textContent = "";
    // Return to the level draft; savePackageDraft is still the only writer.
    showGenPage("package");
    const sourceList = $("packageSourceList");
    if (sourceList && sourceList.lastElementChild) {
      sourceList.lastElementChild.scrollIntoView({ block: "center" });
    }
    if ($("packageEditorError")) {
      const key = sources.length === 1 ? "levelSourceAdded" : "levelSourcesAdded";
      const params = sources.length === 1 ? undefined : { n: sources.length };
      $("packageEditorError").textContent = t(key, params);
      $("packageEditorError").classList.remove("error");
      $("packageEditorError").classList.add("ok");
      setTimeout(() => {
        if ($("packageEditorError")) {
          $("packageEditorError").textContent = "";
          $("packageEditorError").classList.remove("ok");
          $("packageEditorError").classList.add("error");
        }
      }, 2600);
    }
  }

  function addPresetSource() {
    if (packageDraftSources.some((source) => source.type === "preset")) {
      $("sourcePickerError").textContent = t("levelSourceDuplicatePreset");
      return false;
    }
    if (!presetBank.bankId) return false;
    packageDraftSources = packageDraftSources.concat([{
      type: "preset",
      bankId: presetBank.bankId,
      total: presetBank.total,
      available: presetBank.available,
    }]);
    renderPackageEditor();
    return true;
  }

  function addWrongSource() {
    if (packageDraftSources.some((source) => source.type === "wrong")) {
      $("sourcePickerError").textContent = t("levelSourceDuplicateWrong");
      return false;
    }
    const wrongCount = Math.max(0, Number(bankCounts && bankCounts.wrong || 0));
    packageDraftSources = packageDraftSources.concat([{
      type: "wrong",
      available: wrongCount,
    }]);
    renderPackageEditor();
    return true;
  }

  async function postPackages(packages, successKey) {
    if (levelSaving || packageSaving) return false;
    if (!challengeConfig) {
      try {
        await loadAdminSession();
      } catch (error) {
        $("levelError").textContent = errorText(error);
        return false;
      }
      if (!challengeConfig) return false;
    }
    levelSaving = true;
    packageSaving = true;
    $("levelError").textContent = "";
    if ($("packageEditorError") && !$("packageEditorError").classList.contains("ok")) {
      $("packageEditorError").textContent = "";
    }
    if ($("savePackage")) $("savePackage").disabled = true;
    renderAnswerLevels();
    renderPackageSources();
    try {
      const data = await api("/api/quiz/admin/challenge-config", {
        method: "POST",
        admin: true,
        body: {
          config: readSettingsConfig(),
          levelPackages: packages.map(packageDraftPayload),
        },
      });
      challengeConfig = normalizeConfig(data.config || currentConfig());
      syncState = data.syncState || syncState;
      answerDirty = false;
      if (Number(data.levelModel) === LEVEL_MODEL) {
        if (data.levelLimits && typeof data.levelLimits === "object") {
          levelLimits = {
            maxLevels: Math.max(1, Number(data.levelLimits.maxLevels || levelLimits.maxLevels)),
            maxSources: Math.max(1, Number(data.levelLimits.maxSources || levelLimits.maxSources)),
            maxNameCodePoints: Math.max(1, Number(
              data.levelLimits.maxNameCodePoints || levelLimits.maxNameCodePoints)),
          };
        }
        levelPackagesUnreadable = Boolean(data.levelPackagesUnreadable);
        applyLevelPackages(data.levelPackages, { quiet: true });
      } else {
        await loadAdminSession();
      }
      renderSync();
      renderSettings();
      renderHome();
      renderAnswerPage();
      if (successKey) scheduleLevelMessage(t(successKey));
      return true;
    } catch (error) {
      const message = errorText(error);
      $("levelError").textContent = message;
      if ($("packageEditorError")) {
        $("packageEditorError").classList.remove("ok");
        $("packageEditorError").classList.add("error");
        $("packageEditorError").textContent = message;
      }
      if ($("sourcePickerError")) $("sourcePickerError").textContent = message;
      return false;
    } finally {
      levelSaving = false;
      packageSaving = false;
      renderAnswerLevels();
      if (packageEditorActive) renderPackageEditor();
    }
  }

  async function savePackageDraft() {
    if (levelSaving || packageSaving) return;
    $("packageEditorError").classList.remove("ok");
    $("packageEditorError").classList.add("error");
    $("packageEditorError").textContent = "";
    const name = String($("packageName").value || "").trim();
    if (Array.from(name).length > levelLimits.maxNameCodePoints) {
      $("packageEditorError").textContent = t("levelNameTooLong");
      return;
    }
    if (!packageDraftSources.length) {
      $("packageEditorError").textContent = t("levelAtLeastOneSource");
      return;
    }
    if (packageDraftSources.length > levelLimits.maxSources) {
      $("packageEditorError").textContent = t("levelSourceLimit", {
        n: levelLimits.maxSources,
      });
      return;
    }
    if (!packageEditingId && levelPackages.length >= levelLimits.maxLevels) {
      $("packageEditorError").textContent = t("levelMaxHint", { n: levelLimits.maxLevels });
      return;
    }
    let next;
    if (packageEditingId) {
      next = levelPackages.map((level) => level.id === packageEditingId
        ? {
          id: level.id,
          name,
          enabled: level.enabled !== false,
          sources: packageDraftSources,
        }
        : level);
    } else {
      // A new level starts enabled; pausing is a later, deliberate action.
      next = levelPackages.concat([{
        id: "",
        name,
        enabled: true,
        sources: packageDraftSources,
      }]);
    }
    const saved = await postPackages(next, "levelSaved");
    if (!saved) return;
    closePackageEditor();
  }

  function requestDeletePackage(id) {
    if (levelSaving || packageSaving) return;
    const index = levelPackages.findIndex((level) => level.id === id);
    if (index < 0) return;
    pendingLevelDeleteId = id;
    $("levelDeleteLead").textContent = t("levelDeleteConfirmText", {
      name: levelLabel(levelPackages[index], index),
    });
    openModal("levelDeleteDialog");
  }

  async function confirmDeletePackage() {
    const id = pendingLevelDeleteId;
    if (!id) return;
    pendingLevelDeleteId = null;
    closeModal();
    const index = levelPackages.findIndex((level) => level.id === id);
    if (index < 0) return;
    const name = levelLabel(levelPackages[index], index);
    const saved = await postPackages(
      levelPackages.filter((level) => level.id !== id),
      "",
    );
    if (!saved) return;
    scheduleLevelMessage(t("levelDeleted", { name }));
  }

  async function movePackage(id, delta) {
    if (levelSaving || packageSaving) return;
    const index = levelPackages.findIndex((level) => level.id === id);
    const target = index + delta;
    if (index < 0 || target < 0 || target >= levelPackages.length) return;
    const next = levelPackages.slice();
    const moved = next.splice(index, 1)[0];
    next.splice(target, 0, moved);
    await postPackages(next, "levelOrderSaved");
  }

  async function togglePackageEnabled(id, enabled) {
    if (levelSaving || packageSaving) return;
    const index = levelPackages.findIndex((level) => level.id === id);
    if (index < 0) return;
    if ((levelPackages[index].enabled !== false) === enabled) return;
    const name = levelLabel(levelPackages[index], index);
    const previous = levelPackages;
    // Flip locally first so the switch and the paused note respond at once;
    // postPackages rebuilds the row from this state while the request runs.
    levelPackages = levelPackages.map((level) => level.id === id
      ? Object.assign({}, level, { enabled })
      : level);
    const saved = await postPackages(levelPackages, "");
    if (!saved) {
      levelPackages = previous;
      renderAnswerLevels();
      return;
    }
    scheduleLevelMessage(t(enabled ? "levelEnabledToast" : "levelPausedToast", { name }));
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
    const auto = generationScopeReady() ? composeGenerationPrompt() : "";
    const edited = genPromptDirty && $("genPrompt").value.trim() !== auto;
    $("genPromptBadge").textContent = t(edited ? "genPromptBadgeEdited" : "genPromptBadgeAuto");
    if ($("copyGenPrompt")) {
      $("copyGenPrompt").disabled = !$("genPrompt").value.trim();
    }
  }

  // The import page's generation prompt ends with a localized heading and a
  // sample requirement block. Keep the format spec and heading, drop the
  // sample so the copied text contains only the live generation settings.
  function generationFormatPromptSpec() {
    const full = t("importGeneratePrompt");
    const heading = t("importGeneratePromptHeading");
    const at = full.lastIndexOf(heading);
    return at >= 0 ? full.slice(0, at + heading.length) : full;
  }

  function composeGenerationCopy() {
    const prompt = $("genPrompt").value.trim() || composeGenerationPrompt();
    const separator = lang === "zh" ? "：" : ": ";
    const questionType = genQuestionTypeValue === "mixed"
      ? `${generationQuestionTypeText()} (single + multiple)`
      : `${generationQuestionTypeText()} (${genQuestionTypeValue})`;
    const difficulty = genDifficultyValue
      ? `${generationDifficultyText()} (${genDifficultyValue})`
      : generationDifficultyText();
    return [
      generationFormatPromptSpec(),
      prompt,
      `${t("genQuestionType")}${separator}${questionType}`,
      `${t("genCount")}${separator}${targetCount}`,
      `${t("genDifficulty")}${separator}${difficulty}`,
      `${t("genOptions")}${separator}${genOptionsValue}`,
      `${t("languageLabel")}${separator}${LANGUAGE_LABELS[lang] || lang}`,
    ].join("\n");
  }

  let generationCopyTimer = 0;

  function flashGenerationCopyButton(key) {
    const button = $("copyGenPrompt");
    if (!button) return;
    window.clearTimeout(generationCopyTimer);
    button.textContent = t(key);
    generationCopyTimer = window.setTimeout(() => {
      if ($("copyGenPrompt")) $("copyGenPrompt").textContent = t("genCopyPrompt");
    }, 2200);
  }

  async function copyGenerationPrompt() {
    if (!$("genPrompt") || !$("genPrompt").value.trim()) return;
    try {
      await copyText(composeGenerationCopy());
      flashGenerationCopyButton("genCopyPromptDone");
    } catch (_) {
      flashGenerationCopyButton("genCopyPromptFailed");
    }
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
    let pointerDownAt = 0;
    let lastPointerAt = 0;
    element.addEventListener("pointerdown", (event) => {
      if (event.pointerType === "mouse" && event.button !== 0) return;
      if (element.disabled) return;
      pointerDownAt = Date.now();
      start = { id: event.pointerId, x: event.clientX, y: event.clientY };
      // 键盘收起会让底部按钮位移；捕获指针保证 pointerup 仍落在按钮上。
      try { element.setPointerCapture(event.pointerId); } catch (_) { /* ignore */ }
    });
    element.addEventListener("pointerup", (event) => {
      if (!start || start.id !== event.pointerId) return;
      const moved = Math.hypot(event.clientX - start.x, event.clientY - start.y);
      start = null;
      pointerDownAt = 0;
      if (moved > 12 || element.disabled) return;
      lastPointerAt = Date.now();
      handler(event);
    });
    element.addEventListener("pointercancel", () => {
      start = null;
      pointerDownAt = 0;
    });
    element.addEventListener("click", (event) => {
      // A page can swap the button under the finger between pointerup and the
      // synthesized click; that ghost click has no pointerdown on this element
      // and must never trigger the next page's save. Keyboard activation
      // (detail 0) has no pointer gesture and stays allowed.
      const pointerGesture = event.detail !== 0;
      if (pointerGesture && Date.now() - pointerDownAt > 1000) return;
      if (Date.now() - lastPointerAt < 700 || element.disabled) return;
      pointerDownAt = 0;
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
      $("rangeError").textContent = t("err_GEN_SCOPE_REQUIRED");
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
      ["", "easy", "medium", "hard"].includes(payload.difficulty) &&
      GEN_QUESTION_TYPE_VALUES.includes(payload.questionType);
  }

  async function startGeneration() {
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
    // A new member with an unset grade must not fall back to the gallery
    // defaults. This is also the safety net if the button was re-enabled.
    if (!readGenerationPrefs()) {
      genState = idleGeneration();
      genState.phase = "failed";
      genState.errorCode = "GEN_SCOPE_REQUIRED";
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
    // Generate directly from the current scope. The TV's levels are a separate
    // consumer of the bank, so a short level must not block or interrupt this.
    const prompt = $("genPrompt").value.trim() || composeGenerationPrompt();
    const payload = {
      targetCount,
      options: genOptionsValue,
      language: lang,
      subject: subjectLabel(genSubjectValue),
      grade: gradeText(genGradeValue, genSemesterValue),
      prompt,
      difficulty: genDifficultyValue,
      questionType: genQuestionTypeValue,
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
          subjectUserId: data.subjectUserId ? String(data.subjectUserId) : "",
          body: { jobId: data.jobId },
        }).catch(() => {});
        return;
      }
      // Pin the member resolved for this job. Later status/add requests must
      // not follow a newly active TV member while this batch is still running.
      genState.subjectUserId = data.subjectUserId
        ? String(data.subjectUserId)
        : (editingSubjectUserId || activeUserId || "");
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
    if (data.subjectUserId) genState.subjectUserId = String(data.subjectUserId);
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
      renderBankManage();
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
    const subjectUserId = genState.subjectUserId;
    genState.phase = "adding";
    genState.stopping = false;
    renderGenerate();
    try {
      const data = await api("/api/quiz/admin/generate/add", {
        method: "POST",
        admin: true,
        subjectUserId,
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
        { admin: true, subjectUserId: genState.subjectUserId },
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
      if (["JOB_EXPIRED", "JOB_LOST", "INVALID_JOB", "INVALID_SUBJECT"].includes(error.code) ||
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
    const subjectUserId = genState.subjectUserId;
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
        subjectUserId,
        body: { jobId },
      });
    } catch (_) {
      // The job also expires on its own; the status read below recovers what it can.
    }
    try {
      const data = await api(
        "/api/quiz/admin/generate/status?jobId=" + encodeURIComponent(jobId),
        { admin: true, subjectUserId },
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
    // A batch import is already writing the database and reports its own
    // progress. The status endpoint recounts the whole bank, so polling it
    // every 3 seconds here only slows the import down.
    if (importRunning) return;
    try {
      const previous = publicState;
      publicState = await api("/api/quiz/status");
      applyBankCounts(publicState.bank);
      renderStock();
      renderGenerate();
      renderBankCounts();
      renderBankManage();
      renderSettingsAttention();
      renderGateOccupation();
      if (adminToken && previous && previous.sessionState !== publicState.sessionState) {
        loadAdminSession().catch((error) => {
          if (error.code !== "ADMIN_EXPIRED" && error.code !== "ADMIN_REQUIRED") {
            $("settingsError").textContent = errorText(error);
          }
        });
      }
    } catch (_) {
      // Keep the last known state; the next refresh retries.
    }
  }

  async function submitPin(event) {
    event.preventDefault();
    $("gateError").textContent = "";
    $("pinBtn").disabled = true;
    try {
      const data = await api("/api/quiz/auth/pin", {
        method: "POST",
        body: { pin: $("pin").value.trim(), takeover: forceTakeover },
      });
      adminToken = data.adminToken;
      adminExpiresAt = data.expiresAt;
      forceTakeover = false;
      saveAuth();
      startHeartbeat();
      $("pin").value = "";
      await enterEditor();
    } catch (error) {
      if (error.code === "PARENT_IN_USE") {
        // The first phone logged in between the last status poll and this
        // submit; show the same notice instead of a misleading PIN error.
        forceTakeover = false;
        if (publicState) publicState.parentInUse = true;
        renderGateOccupation();
      } else {
        $("gateError").textContent = errorText(error);
      }
    } finally {
      $("pinBtn").disabled = false;
    }
  }

  /**
   * The login gate is shared by More and Control. Returning to Control must not re-open it:
   * showEditor already selected the current tab, and a second open would race the first
   * editor acquire and surface the two-page takeover gate.
   */
  function enterPostLoginTab() {
    const overlay = openOverlayAfterLogin;
    openOverlayAfterLogin = null;
    // The QR opens the home tab: the avatar in its corner is the entry to the
    // member picker, and the bottom nav carries the other three tabs.
    openParentAfterLogin = false;
    if (overlay && parentBootstrap && parentWebAvailable(parentBootstrap)) {
      setTab(overlay, true);
      return;
    }
    setTab("generate", true);
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
    // The editor now owns the slot; keep it alive while this page is visible.
    startHeartbeat();
    // The TV parent center already shows its one-time privacy notice, and the
    // read-only copy lives under settings. Do not gate the editor on AI consent:
    // manual banks, imports and preset levels need no API key.
    showEditor();
    enterPostLoginTab();
  }

  function logout() {
    // Drop local auth immediately; revoking the token on the TV is best-effort
    // so logout never waits on the network.
    openOverlayAfterLogin = null;
    stopHeartbeat();
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
      let query = "/api/quiz/admin/bank?scope=" + encodeURIComponent(bankScope) +
        "&filter=all&offset=" + bankOffset + "&limit=" + BANK_PAGE_SIZE;
      if (bankScope === "manual" && bankManualCollectionId) {
        query += "&collectionId=" + encodeURIComponent(bankManualCollectionId);
      }
      const data = await api(query, { admin: true });
      if (request !== bankRequest) return;
      const items = Array.isArray(data.items) ? data.items : [];
      bankItems = reset ? items : bankItems.concat(items);
      bankOffset = bankItems.length;
      bankHasMore = items.length >= Number(data.limit || BANK_PAGE_SIZE);
      // Counts in this response are scoped to the selected tab. Keep them
      // separate from the header's all-source counts and the manage page's
      // global source counts.
      bankScopeStats = data.counts || null;
      if (data.scopeCounts) bankScopeCounts = subjectScopeCounts(data.scopeCounts);
      if (data.privateCapacity !== undefined) {
        privateCapacity = Math.max(0, Number(data.privateCapacity || 0));
      }
      if (data.privateRemaining !== undefined) {
        privateRemaining = Math.max(0, Number(data.privateRemaining || 0));
      }
      renderBankCounts();
      renderBankManage();
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

  function readSettingsConfig() {
    const base = currentConfig();
    // Read the fields rather than the cached config: the parent may press Save
    // while an input still has focus and its change event has not fired yet.
    const pull = normalizeRoundCount($("roundInput").value, configRoundCount(base));
    const time = normalizeTimeLimit($("timeInput").value, configSeconds(base));
    const feedbackHold = normalizeFeedbackHold(
      $("feedbackHoldInput").value,
      configFeedbackHold(base),
    );
    const scoring = readScoringPatch();
    return Object.assign({}, base, {
      defaultTimeLimitSec: time,
      pullCount: pull,
      // Legacy single-rate fields stay for cloud compatibility; local v5
      // rounds settle from the difficulty table below.
      minutesPerPoint: Number(base.minutesPerPoint || DEFAULT_CONFIG.minutesPerPoint),
      minutesPerQuestion: Number(base.minutesPerQuestion || DEFAULT_CONFIG.minutesPerQuestion),
      // The per-round reward cap is no longer edited; keep the stored legacy value so an
      // older build or the hosted backend still sees a valid config. Local rounds ignore it.
      maxEarnedMinutes: configMaxEarned(base),
      // Answers are always shuffled; older documents may still store false.
      shuffleOptions: true,
      // Legacy echo only. The v6 hold is feedbackExtraHoldSec.
      answerDisplaySec: base.answerDisplaySec,
      feedbackExtraHoldSec: feedbackHold,
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
        body: {
          config,
          // Explicit so the adapter updates the v6 hold; everything the form
          // does not send (weekday/weekend profiles) is preserved server side.
          feedback: { extraHoldSec: config.feedbackExtraHoldSec },
        },
      });
      challengeConfig = normalizeConfig(data.config || config);
      if (Number(data.levelModel) === LEVEL_MODEL && Array.isArray(data.levelPackages)) {
        applyLevelPackages(data.levelPackages, { quiet: true });
      } else {
        await loadAdminSession();
      }
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

  function updateRuleConfig(patch) {
    answerDirty = true;
    challengeConfig = Object.assign(currentConfig(), patch);
    renderSettingsChrome();
    renderHome();
  }

  function bind() {
    $("langSelectMe").addEventListener("change", () => applyLanguage($("langSelectMe").value));
    $("pinForm").addEventListener("submit", submitPin);
    $("pin").addEventListener("input", () => {
      $("pin").value = $("pin").value.replace(/\D/g, "").slice(0, 6);
    });
    $("forgotPin").addEventListener("click", openRecovery);
    $("recoveryCancel").addEventListener("click", closeRecovery);
    $("recoveryForm").addEventListener("submit", submitRecovery);
    $("recoveryCode").addEventListener("input", () => {
      $("recoveryCode").value = $("recoveryCode").value.replace(/\D/g, "").slice(0, 12);
    });
    for (const id of ["recoveryNewPin", "recoveryConfirmPin"]) {
      $(id).addEventListener("input", () => {
        $(id).value = $(id).value.replace(/\D/g, "").slice(0, 6);
      });
    }
    $("gateTakeover").addEventListener("click", () => {
      forceTakeover = true;
      renderGateOccupation();
      $("pin").focus();
    });
    $("meAi").addEventListener("click", () => openProviderDialog(false));
    $("backFromAi").addEventListener("click", closeAiPage);
    $("mePrivacy").addEventListener("click", () => openModal("privacyInfoDialog"));
    $("unlockForm").addEventListener("submit", submitUnlock);
    $("manualMinus").addEventListener("click", () => adjustManualMinutes(-1));
    $("manualPlus").addEventListener("click", () => adjustManualMinutes(1));
    $("manualForm").addEventListener("submit", submitManualStart);
    $("unlockPin").addEventListener("input", () => {
      $("unlockPin").value = $("unlockPin").value.replace(/\D/g, "").slice(0, 6);
    });
    $("logout").addEventListener("click", logout);
    $("acceptConsent").addEventListener("click", acceptConsent);
    $("declineConsent").addEventListener("click", dismissConsent);
    $("saveProvider").addEventListener("click", saveProvider);
    $("testProvider").addEventListener("click", testProviderConnection);
    $("baseUrl").addEventListener("input", () => {
      updateProviderModelHint();
      renderSpeech();
    });
    $("speechEnabled").addEventListener("change", () => {
      speechState.enabled = $("speechEnabled").checked;
      speechDirty = true;
      renderSpeech();
    });
    for (const [id, field] of [
      ["speechPrompt", "readPrompt"],
      ["speechOptions", "readOptions"],
      ["speechAnswer", "readAnswer"],
    ]) {
      $(id).addEventListener("change", () => {
        speechState[field] = $(id).checked;
        speechDirty = true;
        renderSpeech();
      });
    }
    document.querySelectorAll("[data-provider-preset]").forEach((button) => {
      button.addEventListener("click", () => applyProviderPreset(button.dataset.providerPreset));
    });
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
      startGeneration();
    });
    $("editGenScope").addEventListener("click", openGenConfigPage);

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
      if (memberScopeChoice().grade !== null) return;
      const grade = Number(chip.dataset.grade);
      if (!gradeNos().includes(grade) || cloudJuniorBlocked(grade)) return;
      genGradeValue = grade;
      if (genScopeSetup) {
        // The subject/textbook list belongs to the old grade.
        genSubjectValue = "";
        genTextbookId = "";
      }
      genPromptDirty = false;
      renderGenConfig(true);
      saveGeneratePrefs();
    });
    $("genSemester").addEventListener("click", (event) => {
      const button = event.target.closest("[data-semester]");
      if (!button) return;
      if (memberScopeChoice().semester !== null) return;
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
      if (genScopeSetup) {
        // Keep the textbook pick explicit too; all-subject generation has no
        // single textbook and uses the wire wildcard instead.
        genTextbookId = genSubjectValue === SUBJECT_ALL ? "general" : "";
      } else {
        keepGenerationTextbook();
      }
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
    $("copyGenPrompt").addEventListener("click", copyGenerationPrompt);
    $("genQuestionTypeChips").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-question-type]");
      if (!chip) return;
      if (!GEN_QUESTION_TYPE_VALUES.includes(chip.dataset.questionType)) return;
      genQuestionTypeValue = chip.dataset.questionType;
      genPromptDirty = false;
      renderGenConfig(true);
      saveGeneratePrefs();
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
    $("addLevel").addEventListener("click", () => openPackageEditor(null));
    $("backFromLevel").addEventListener("click", backFromLevelEditor);
    $("levelList").addEventListener("click", (event) => {
      const edit = event.target.closest("[data-package-edit]");
      if (edit) {
        openPackageEditor(edit.dataset.packageEdit);
        return;
      }
      const up = event.target.closest("[data-level-up]");
      if (up && !up.disabled) {
        movePackage(up.dataset.levelUp, -1);
        return;
      }
      const down = event.target.closest("[data-level-down]");
      if (down && !down.disabled) {
        movePackage(down.dataset.levelDown, 1);
        return;
      }
      const remove = event.target.closest("[data-level-delete]");
      if (remove && !remove.disabled) requestDeletePackage(remove.dataset.levelDelete);
    });
    $("levelList").addEventListener("change", (event) => {
      const toggle = event.target.closest("[data-level-toggle]");
      if (!toggle) return;
      togglePackageEnabled(toggle.dataset.levelToggle, toggle.checked);
    });
    $("levelDeleteConfirm").addEventListener("click", confirmDeletePackage);
    bindReliableTap($("saveLevel"), saveLevelDraft);
    $("levelEditorPage").addEventListener("click", (event) => {
      const kind = event.target.closest("[data-course-kind]");
      if (kind) {
        selectCourseSourceAnswerKind(kind.dataset.courseKind);
        return;
      }
      const chip = event.target.closest("[data-scope-step]");
      if (!chip) return;
      selectScopeValue(chip.dataset.scopeStep, chip.dataset.scopeValue);
    });
    $("levelEmptyGen").addEventListener("click", () => {
      discardPackageDraft();
      backToGenerateHome();
    });
    $("levelEmptyConfig").addEventListener("click", openGenConfigPage);

    $("backFromPackage").addEventListener("click", closePackageEditor);
    $("packageName").addEventListener("input", () => {
      packageNameDraft = $("packageName").value;
      $("packageEditorError").classList.remove("ok");
      $("packageEditorError").classList.add("error");
      $("packageEditorError").textContent = "";
    });
    $("packageSourceList").addEventListener("click", (event) => {
      const edit = event.target.closest("[data-package-source-edit]");
      if (edit && !edit.disabled) {
        openCourseSourceEditor(Number(edit.dataset.packageSourceEdit), null);
        return;
      }
      const remove = event.target.closest("[data-package-source-remove]");
      if (!remove || remove.disabled) return;
      const index = Number(remove.dataset.packageSourceRemove);
      packageDraftSources = packageDraftSources.filter((_, position) => position !== index);
      renderPackageEditor();
    });
    bindReliableTap($("savePackage"), savePackageDraft);

    $("pickPresetSource").addEventListener("click", () => {
      openCourseSourceEditor(null, null, "preset");
    });
    $("pickAiSource").addEventListener("click", () => {
      openCourseSourceEditor(null, null, "ai");
    });
    $("pickTagSource").addEventListener("click", () => {
      if ($("pickTagSource").disabled) return;
      openTagSourcePage();
    });
    $("pickLifeSource").addEventListener("click", () => {
      if ($("pickLifeSource").disabled) return;
      $("sourcePickerError").textContent = "";
      addPresetSource();
    });
    $("pickWrongSource").addEventListener("click", () => {
      $("sourcePickerError").textContent = "";
      addWrongSource();
    });
    $("backFromTagSource").addEventListener("click", backFromTagSource);
    $("tagSourcePageList").addEventListener("click", (event) => {
      const choice = event.target.closest("[data-tag-source-choice]");
      if (!choice || choice.disabled) return;
      toggleTagSourceSelection(choice.dataset.tagSourceChoice);
    });
    $("tagSourcePageGoImport").addEventListener("click", startImportFromTagSource);
    bindReliableTap($("addTagSources"), addSelectedTagSources);

    bindStepper(
      "round", ROUND_MIN, ROUND_MAX, ROUND_STEP,
      () => configRoundCount(currentConfig()),
      (value) => updateRuleConfig({ pullCount: value }),
    );
    bindStepper(
      "time", ANSWER_TIME_MIN_SEC, ANSWER_TIME_MAX_SEC, ANSWER_TIME_STEP_SEC,
      () => configSeconds(currentConfig()),
      (value) => updateRuleConfig({ defaultTimeLimitSec: value }),
    );
    bindStepper(
      "feedbackHold",
      FEEDBACK_HOLD_MIN_SEC, FEEDBACK_HOLD_MAX_SEC, FEEDBACK_HOLD_STEP_SEC,
      () => configFeedbackHold(currentConfig()),
      (value) => updateRuleConfig({ feedbackExtraHoldSec: value }),
    );
    for (const id of [
      "easyReward", "mediumReward", "hardReward",
      "easyPenalty", "mediumPenalty", "hardPenalty",
    ]) {
      $(id).addEventListener("input", () => updateRuleConfig(readScoringPatch()));
    }
    bindStepper(
      "unlock",
      0, UNLOCK_MAX_MINUTES, UNLOCK_STEP_MINUTES,
      () => scoringValues(currentConfig()).minUnlockMinutes,
      (value) => updateRuleConfig({ minUnlockMinutes: value }),
      "minUnlock",
    );
    $("resetScoring").addEventListener("click", resetScoringDefaults);

    $("refreshBank").addEventListener("click", () => loadBank(true));
    $("bankMore").addEventListener("click", () => loadBank(false));
    $("openBankImport").addEventListener("click", openBankImport);
    $("bankEmptyImport").addEventListener("click", openBankImport);
    $("bankEmptyGenerate").addEventListener("click", () => {
      setTab("generate", true);
      openGenConfigPage();
    });
    $("backFromImport").addEventListener("click", backFromImport);
    $("importTagName").addEventListener("focus", openImportTagSuggest);
    // Escape or a blur leaves the input focused in some browsers; a tap on the
    // already-focused input must still reopen the suggestion list.
    $("importTagName").addEventListener("click", openImportTagSuggest);
    $("importTagName").addEventListener("input", onImportTagInput);
    $("importTagName").addEventListener("keydown", onImportTagKeydown);
    $("importTagName").addEventListener("blur", onImportTagBlur);
    $("importTagName").addEventListener("compositionstart", () => {
      importTagComposing = true;
    });
    $("importTagName").addEventListener("compositionend", () => {
      importTagComposing = false;
      onImportTagInput();
    });
    $("clearImportTag").addEventListener("click", clearImportTagName);
    document.addEventListener("pointerdown", (event) => {
      const field = $("importTagField");
      if (!field || !field.contains(event.target)) closeImportTagSuggest();
    });
    window.addEventListener("resize", fitImportTagSuggest);
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", fitImportTagSuggest);
    }
    $("importText").addEventListener("input", () => {
      importFileName = "";
      importFileText = null;
      $("importFileName").textContent = "";
      if ($("importFile").value) $("importFile").value = "";
      invalidateImportPreview();
    });
    $("importFile").addEventListener("change", () => {
      loadImportFile($("importFile").files && $("importFile").files[0]);
    });
    $("clearImportText").addEventListener("click", clearImportInput);
    $("importPreviewPrev").addEventListener("click", () => changeImportPreviewPage(-1));
    $("importPreviewNext").addEventListener("click", () => changeImportPreviewPage(1));
    $("importPreviewGo").addEventListener("click", jumpImportPreviewPage);
    $("importPreviewPageInput").addEventListener("keydown", (event) => {
      if (event.key !== "Enter") return;
      event.preventDefault();
      jumpImportPreviewPage();
    });
    $("cancelImport").addEventListener("click", requestImportCancel);
    $("copyConvertPrompt").addEventListener("click", () => copyImportPrompt("convert"));
    $("copyGeneratePrompt").addEventListener("click", () => copyImportPrompt("generate"));
    $("copyImportErrors").addEventListener("click", copyImportErrors);
    $("importDifficultyChips").addEventListener("click", (event) => {
      const chip = event.target.closest("[data-import-difficulty]");
      if (!chip) return;
      importDefaultDifficulty = chip.dataset.importDifficulty;
      invalidateImportPreview();
      renderBankImport();
    });
    bindReliableTap($("parseImport"), parseImport);
    bindReliableTap($("confirmImport"), confirmImport);
    $("bankScopes").addEventListener("click", (event) => {
      const tab = event.target.closest("[data-bank-scope]");
      if (!tab || tab.dataset.bankScope === bankScope) return;
      bankScope = tab.dataset.bankScope;
      bankManualCollectionId = "";
      bankItems = [];
      renderBank();
      loadBank(true);
    });
    $("bankManageEntry").addEventListener("click", () => openBankManage("builtin"));
    $("backFromBankManage").addEventListener("click", backFromBankManage);
    $("bankManageBuiltinClear").addEventListener("click", () => requestBankClear({
      scope: "builtin", count: bankScopeCounts.builtin,
    }));
    $("bankManageManualClear").addEventListener("click", () => requestBankClear({
      scope: "manual", count: bankScopeCounts.manual,
    }));
    $("bankClearNext").addEventListener("click", advanceBankClear);
    $("subjectChip").addEventListener("click", openSubjectDialog);

    document.querySelectorAll(".nav-item[data-tab]").forEach((button) => {
      button.addEventListener("click", () => setTab(button.dataset.tab, false));
    });

    $("modalRoot").addEventListener("click", (event) => {
      if (activeModal && $(activeModal).dataset.staticModal === "true") return;
      if (event.target.closest("[data-close-modal]")) closeModal();
    });
    document.addEventListener("keydown", modalKeydown);
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden && adminToken) {
        // Reclaim the slot immediately if it is still free. If another phone
        // took over while this page was hidden, the heartbeat returns
        // ADMIN_EXPIRED and the PIN gate opens.
        heartbeat();
        refreshStatus();
      }
    });
    // Best-effort immediate release when the page is torn down. WeChat may kill
    // the process without delivering this; the TV's idle timeout covers that.
    window.addEventListener("pagehide", releaseSession);
    window.addEventListener("pageshow", (event) => {
      if (!event.persisted || !adminToken) return;
      startHeartbeat();
      heartbeat();
      refreshStatus();
    });
    statusTimer = setInterval(() => {
      if (!document.hidden && !webDisabled) refreshStatus();
    }, 3000);
    if ($("parentCenterEntry")) {
      $("parentCenterEntry").addEventListener("click", () => {
        if (window.ParentCenterUI) window.ParentCenterUI.open();
      });
    }
    if ($("openControlPanel")) {
      $("openControlPanel").addEventListener("click", () => setTab("system", true));
    }
    if ($("parentAppDownload")) {
      $("parentAppDownload").addEventListener("click", () => {
        openParentAppDownload();
      });
    }
    if ($("webDisabledRefresh")) {
      $("webDisabledRefresh").addEventListener("click", () => refreshWebDisabled(true));
    }
  }

  async function init() {
    const wantsParent = window.location.hash === "#parent";
    if (wantsParent) {
      history.replaceState(null, "", window.location.pathname + window.location.search);
    }
    loadAuth();
    applyI18n();
    bind();
    loadGeneratePrefs();
    syncGenerateComposer(true);
    parentBootstrap = await loadParentBootstrap();
    if (parentBootstrap && !parentWebAvailable(parentBootstrap)) {
      // V29: remote/bound/child-mode-off is a terminal disabled page. Do not initialize the
      // parent center or the quiz login, and do not call /api/quiz/auth/*.
      enterWebDisabled(parentBootstrap);
      return;
    }
    if (parentBootstrap) {
      // The entry card is revealed by parent.js only after bootstrap succeeded, so an old TV
      // (or PARENT_WEB_CENTER_ENABLED=false) shows exactly the old page with no dead button.
      if (window.ParentCenterUI) {
        window.ParentCenterUI.init(parentHost(), parentBootstrap);
        window.ParentCenterUI.refreshEntry();
      }
      openParentAfterLogin = wantsParent;
    }
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
