/*
 * V27 web parent center.
 *
 * This file is only a second front end for the same policy state the TV parent center edits.
 * It never computes policy locally: every write goes to /api/parent/* and re-renders the
 * snapshot the TV returns. The page is loaded inside /quiz and appends a full-screen overlay,
 * so there is still one document, one address and one PIN.
 */
(function () {
  "use strict";

  const LANGS = ["zh", "en", "ru", "uk", "kk"];
  const ROOT_ID = "parentCenterRoot";
  const SHEET_ID = "parentSheetRoot";
  const MAX_EDIT_REST = 1440;
  const MIN_BUFFER = 0;
  const MAX_BUFFER = 60;
  const STEP_REST = 5;
  const STEP_BUFFER = 5;
  // Common values only; the steppers still reach every STEP-sized value in between.
  // 1 minute is an explicit test/short-session stop below the normal 5-minute step.
  const REST_OPTIONS = [0, 1, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 600, 720, 960, 1200, 1440];
  const BUFFER_OPTIONS = [0, 1, 5, 10, 15, 20, 30, 45, 60];
  // V44 new writes: X 1–180; Y 1..public cap. X and Y are independent inputs (Y may exceed X).
  const MIN_STUDY_EARN_MINUTES = 1;
  const MAX_STUDY_EARN_MINUTES = 180;
  const STEP_STUDY_EARN_MINUTES = 5;
  const DEFAULT_STUDY_EARN_STUDY_MINUTES = 10;
  const DEFAULT_STUDY_EARN_REWARD_MINUTES = 5;
  // Direct is its own "each viewing" value; the public cap is only its ceiling.
  const MIN_DIRECT_MINUTES = 1;
  const MAX_DIRECT_MINUTES = 120;
  const STEP_DIRECT_MINUTES = 5;
  const DIRECT_OPTIONS = [1, 5, 10, 15, 20, 25, 30, 40, 45, 60, 90, 120];
  const MIN_SESSION_CAP_MINUTES = 1;
  const MAX_SESSION_CAP_MINUTES = 120;
  const STEP_SESSION_CAP_MINUTES = 5;
  const SESSION_CAP_OPTIONS = [1, 5, 10, 15, 20, 25, 30, 40, 45, 60, 90, 120];
  const DEFAULT_SESSION_CAP_MINUTES = 30;
  const DEFAULT_DIRECT_MINUTES = 20;
  const MAX_ROUTE_DAILY_COUNT = 10;
  const DEFAULT_ROUTE_DAILY_COUNT = 3;
  // V5 makes the three child unlock routes independent and adds the shared public cap and
  // per-route daily counts. A V3/V4 page cannot express them, so every writer must send V5.
  const RULES_CONTRACT_VERSION = 5;
  const HEARTBEAT_MS = 3000;
  const RULES_DEBOUNCE_MS = 200;
  const APP_PROMPT_KEY = "ptv.parentAppPromptSeen";

  const TEXTS = {
    zh: {
      parentCenterTitle: "家长中心",
      moreTitle: "更多",
      familyGroup: "家庭组",
      familyMemberCount: "{n} 位成员",
      familyActiveOnTv: "电视当前",
      familyAddChild: "添加孩子",
      familyAddChildDisabled: "当前无法添加成员。",
      familySingleNote: "点成员可修改称呼和头像；电视同一时间只按当前成员的设置计时。",
      memberAddAction: "添加",
      memberNewLabel: "新成员",
      memberAdded: "成员已添加",
      memberAddFailed: "添加失败，请重试",
      familyCloudReadOnly: "已绑定云端，家庭成员请在手机或云端管理。",
      familyLocalOnly: "资料只保存在本机，不影响今日用量和答题进度。",
      memberEditTitle: "编辑成员",
      memberNameLabel: "怎么称呼",
      memberNamePlaceholder: "例如：大宝",
      memberAvatarLabel: "头像",
      memberUploadPhoto: "上传照片",
      memberChangePhoto: "更换照片",
      memberRemovePhoto: "删除照片",
      memberSaveAction: "保存修改",
      memberSaved: "成员资料已保存",
      memberDeleteAction: "删除成员",
      memberDeleteTitle: "删除成员？",
      memberDeleteText: "将删除「{name}」的资料、设置和头像，无法恢复。电视当前成员和默认成员不能删除。",
      memberDeleted: "成员已删除",
      memberDeleteFailed: "删除失败，请重试",
      memberDeleteLast: "至少需要保留一位成员",
      familyDeleteActiveHint: "删除前请先把电视切换到其他成员。",
      familyDeleteDefaultHint: "默认成员保存着本机历史数据，不能删除；可改名或更换头像。",
      memberNameInvalid: "称呼不能为空，最多 12 个字",
      memberImageType: "请选择图片文件",
      memberImageTooLarge: "图片不能超过 25 MB",
      memberImageInvalid: "图片无法读取，请换一张试试",
      memberImageSaveFailed: "照片保存失败，请重试",
      avatarCropTitle: "调整头像",
      avatarCropHint: "拖动照片选择位置，双指或滑块缩放",
      avatarCropZoom: "缩放",
      avatarCropApply: "使用照片",
      avatarCropProcessing: "正在处理照片…",
      avatarCropFailed: "这张图片无法用作头像，请换一张试试",
      memberNotFound: "没有找到这个成员",
      familyNotReady: "成员资料暂不可用",
      avatarRabbit: "小兔",
      avatarFox: "狐狸",
      avatarBear: "小熊",
      avatarPanda: "熊猫",
      memberSourceLocal: "本机",
      memberSourceCloud: "云端",
      timeAndApps: "娱乐应用时间计划",
      timeAndAppsSummary: "{groups} 个日期分组 · {apps}",
      timeTabManage: "娱乐应用管理",
      timeTabPlan: "娱乐时间计划",
      timeTabsLabel: "娱乐应用时间计划分页",
      day1: "周一",
      day2: "周二",
      day3: "周三",
      day4: "周四",
      day5: "周五",
      day6: "周六",
      day7: "周日",
      daySeparator: "、",
      groupWeekdays: "周一–周五",
      groupWeekend: "周六–周日",
      addGroup: "添加分组",
      addGroupTitle: "把某天移入新分组",
      addGroupHint: "新分组会复制当前分组的时段和额度，之后可单独修改。",
      deleteGroup: "删除分组",
      deleteGroupTitle: "删除这个分组？",
      deleteGroupBody: "组内日期会合并到相邻分组。",
      dayGroupsSection: "日期分组",
      publicParams: "公共参数",
      dayAssignment: "日期分配",
      dayAssignmentHint: "点某一天，可把它移到当前分组。",
      segmentHint: "点日期开始新的一段；再点段首可合并到上一段。",
      segmentSplitHint: "从此开始新一段",
      segmentMergeHint: "合并到上一段",
      segmentFirstHint: "第一段起点",
      segmentNonContiguous: "原分组含不连续日期，已按连续日期展示，规则不变。",
      presetWholeWeek: "整周",
      presetEveryDay: "每天",
      studyShort: "学习",
      entertainmentShort: "娱乐",
      mergeSegmentsTitle: "合并分段？",
      mergeSegmentsBody: "合并后使用左侧分段的设置，其余分段的设置将被清除。",
      mergeSegmentsAction: "合并",
      studySchedule: "学习时间",
      entertainmentSchedule: "娱乐时间",
      studyLimit: "学习额度",
      entertainmentLimit: "娱乐额度",
      unlimited: "不限",
      unlimitedApps: "不限时应用",
      unlimitedAppsHint: "不扣学习或娱乐时间，也不受时段限制",
      unlimitedNone: "未设置不限时应用",
      unlimitedEmpty: "未设置不限时应用",
      selectAllApps: "全选",
      clearAllApps: "取消全选",
      unlimitedAllWarning: "已将所有可用应用设为不限时，娱乐应用时间计划将不再限制这些应用。",
      unlimitedManageHint: "点击任意图标直接管理",
      appIconHint: "点击图标或 + 直接筛选",
      appPickerManage: "管理应用",
      studyEarnEmptyHint: "添加孩子不太愿意主动使用的学习应用",
      moveToUnlimitedConfirm: "加入不限时后将移出学习换娱乐。",
      moveToUnlimitedAction: "仍然加入",
      moveToStudyConfirm: "加入学习应用后将移出不限时应用。",
      studyAppsHint: "受学习时段和学习额度限制",
      appPickerUnlimited: "不限时应用",
      appPickerUnlimitedHint: "这些应用不扣学习或娱乐时间",
      appPickerTakenStudy: "已是学习应用",
      appPickerTakenUnlimited: "已是不限时应用",
      restAndBuffer: "公共参数",
      onlyUnlimitedNoTimer: "当前只有不限时应用，无需开始计时。",
      rulesDayGroups: "日期分组必须覆盖七天，且时段和额度有效",
      rulesUnlimitedApps: "不限时应用列表无效，请重新选择",
      rulesClientOutdated: "页面版本过旧，请刷新后重试",
      localSettings: "本机设置",
      pinSet: "已设置",
      pinUnset: "未设置",
      quizAndAi: "题库与 AI",
      otherOptions: "其他",
      privacyHint: "本机数据与权限说明",
      aiTitle: "AI 出题",
      privacyInfo: "隐私说明",
      downloadParentApp: "下载家长端 App",
      downloadParentAppHint: "Android 7.0 及以上",
      viewAction: "查看",
      manageAction: "管理",
      logoutAccount: "退出登录",
      timePageHint: "配置哪些应用需要娱乐时间、如何获得，以及什么时段可用。娱乐应用全周共用、立即生效；公共参数全周共用；日期分组可分别设置学习/娱乐时段与额度，从下次计时生效。",
      tvInputSources: "电视输入源",
      tvInputSourcesHint: "只影响桌面入口，不是强制锁定",
      entryChecking: "正在读取电视状态…",
      entryPair: "未开启 · 需电视配对",
      entryNotReady: "电视尚未就绪",
      entryLocalUnlock: "本地模式 · 需临时解锁",
      entryLocalVerify: "本地模式 · 需验证家长密码",
      entryLocalReady: "本地模式 · 可编辑",
      entryLocalBlocked: "本地模式 · 当前不可解锁",
      entryCloudUnlock: "远程模式 · 需在电视上解锁",
      entryCloudVerify: "远程模式 · 需验证家长密码",
      entryCloudReady: "远程模式 · 可编辑",
      back: "返回",
      loading: "正在载入…",
      retry: "重试",
      retryIn: "请在 {n} 秒后重试",
      gateLockedTitle: "家长中心已锁定",
      gateLockedLead: "和电视家长中心使用同一把锁。电视锁屏后点「临时解锁电视」，或在电视上完成家长临时解锁。",
      gateUnlockButton: "输入密码并临时解锁",
      gatePairTitle: "需要电视上的配对码",
      gatePairLead: "儿童模式未开启时没有家长密码可验证。请在电视家长中心点「网页配置」，然后把电视上的 8 位配对码输入这里。",
      pairCodeLabel: "8 位配对码",
      pairSubmit: "连接电视",
      gateVerifyTitle: "验证家长密码",
      gateVerifyLead: "电视刚刚完成临时解锁，请输入同一位家长密码继续。",
      pinLabel: "6 位家长密码",
      pinSubmit: "继续",
      pinCancel: "取消",
      gateBlockedTitle: "现在不能进入家长中心",
      gateNotReadyTitle: "电视尚未就绪",
      gateNotReadyLead: "请稍后重试。家长中心不会在电视策略加载完成前开放。",
      gateQuizLogin: "当前手机还没有登录题库。请先回到题库页面登录，再打开家长中心。",
      gateQuizLoginButton: "回到题库登录",
      gateWebEditing: "另一部手机正在配置家长中心。请关闭那边的页面，或等待 20 秒后重试。",
      gateTakenOver: "编辑权已被另一部手机收回。页面已变为只读。",
      gateTakenOverHint: "接管后另一部手机将立即退出配置，本机成为唯一编辑端。",
      gateTakeoverButton: "仍要接管",
      leaveTitle: "退出家长中心？",
      leaveText: "编辑权会立即释放，另一部手机可以马上继续配置。",
      leaveConfirm: "退出",
      cancel: "取消",
      childMode: "儿童模式",
      childOn: "已开启",
      childOff: "未开启",
      localMode: "本地模式",
      remoteMode: "远程模式",
      todayUsed: "今天已使用 {n} 分钟",
      localRules: "本地规则",
      cloudRules: "云端规则 · 只读",
      appScope: "娱乐应用",
      entertainmentApps: "娱乐应用",
      studyApps: "学习应用",
      allApps: "全部应用",
      appsSelected: "已选 {n} 个应用",
      noEntertainment: "未选择（不放行娱乐应用）",
      studyNone: "未设置（没有学习模式）",
      watchMethods: "娱乐应用启动条件",
      quizGateSwitchHint: "看电视前先答题，本次时长由答题结果决定",
      studyEarnSwitchHint: "学习应用按学习时段计时，达到设定时长换取娱乐时间；关闭后学习应用暂停",
      directStartTitle: "直接启动",
      directStartSwitchHint: "不答题、不学习，点击娱乐应用直接观看",
      directDuration: "每次观看",
      directPauseNote: "三个观看入口相互独立，可同时开启。每次观看仍受当前日期组的娱乐额度、时段和单次使用时间上限限制。",
      unifiedLimits: "公共参数",
      sessionCapTitle: "单次使用时间上限",
      sessionCapHint: "三种方式各自一次会话的天花板；每日总额度在日期分组中设置。",
      directMaxHint: "每次观看时长，最多 {n} 分钟",
      quizAutoLength: "按答题结果自动计算，单次最多 {n} 分钟",
      routeDailyCount: "每日次数",
      countTimes: "{n} 次",
      studyRewardMaxHint: "受公共单次上限限制，最多 {n} 分钟；与学习时长独立",
      allRoutesOffTitle: "关闭全部观看入口？",
      allRoutesOffBody: "保存后孩子将没有可用的娱乐入口，电视会显示「没有可用的观看入口」。家长临时解锁不受影响。",
      allRoutesOffConfirm: "仍然保存",
      routesOffHint: "三个观看入口都已关闭。",
      capClampedNotice: "已按公共单次上限调整为 {n} 分钟",
      studyAppsRetained: "已关闭 · {n} 个学习应用已保留，重新开启后恢复",
      manageStudyApps: "管理学习应用",
      studyMovedHint: "学习应用已并入上方「学习换娱乐」，不再单独设置。",
      studyEarnTitle: "学习换娱乐",
      studyEarnIntro: "开启奖励的学习应用，专注达到设定时长后可获得娱乐时间。学习本身始终受学习时段和额度限制；奖励时还会再次检查娱乐时段和额度。只计时，不监控学习内容。",
      studyEarnRatio: "每学习 {study} 分钟，兑换 {reward} 分钟",
      studyEarnStudyMinutes: "学习时长",
      studyEarnRewardMinutes: "奖励娱乐时长",
      studyEarnApps: "学习应用（全部参与奖励）",
      studyEarnNoApps: "还没有学习应用。",
      studyEarnHint: "所有学习应用都按学习时段和额度计时并参与奖励。离开学习应用会暂停，熄灭屏幕也会暂停。",
      addStudyApps: "添加学习应用",
      studyEarnEmptyHint: "添加孩子不太愿意主动使用的学习应用，用奖励促使他们使用。",
      studyEarnEnabledCount: "已开启 {n} 个",
      studyEarnDisabled: "未开启",
      studyEarnInvalid: "学习换娱乐设置不合法",
      sourcesAndQuiz: "来源与答题",
      sourceEntries: "来源入口",
      sourceHint: "只影响桌面入口，不是强制锁定",
      quizChallenge: "答题闯关",
      quizEntryVisible: "展示答题闯关入口",
      quizEntryHint: "开启后，锁屏最前面显示答题闯关图标；关闭不影响点击娱乐应用进入答题。",
      quizManage: "题库管理",
      quizManageHint: "在题库页面管理题目与答题设置",
      quizPageQrTitle: "扫码打开题库页",
      quizPageQrLead: "用手机相机扫码，在同一局域网打开这台电视的题库页",
      parentAppQrTitle: "下载家长端 App",
      parentAppQrLead: "扫描下载官方家长端；也可以用手机浏览器打开下载页",
      parentAppPromptLead: "装到手机上，不在家也能远程查看和管理；Android 7.0 及以上。",
      parentAppPromptDownload: "去下载",
      parentAppPromptLater: "以后再说",
      parentAppDownloadLimit: "今天已达下载上限，明天再试",
      quizQrFailed: "二维码生成失败，请重试",
      unlockFineprint: "临时解锁与电视同步：解锁后在电视锁屏上看到的家长验证同样生效，10 分钟后自动重新锁定。",
      pairHint: "配对码 5 分钟内有效。输入错误 5 次后需要回电视重新生成。",
      passwordHint: "修改家长密码需要输入当前密码；保存后立即生效。",
      pinLockedRetry: "密码错误次数过多，请在 {n} 秒后重试",
      sensitivePinTitle: "验证家长密码",
      sensitivePinLeadRules: "输入家长密码后编辑时间与应用",
      sensitivePinLeadTakeOver: "输入家长密码后临时退出儿童模式",
      sensitivePinLeadDelete: "输入家长密码后删除成员",
      sensitivePinDefault: "请先在电视上修改初始家长密码",
      sensitivePinExpired: "验证已过期，请重新输入",
      sensitivePinRequired: "请先验证家长密码",
      confirmAutoUpdateOffTitle: "关闭自动升级？",
      confirmAutoUpdateOffBody: "关闭后电视不再自动获取新版本，需要手动安装更新。",
      confirmAutoUpdateOffAction: "关闭",
      remoteRefreshFailed: "刷新远程状态失败，请检查电视网络后重试",
      timeRules: "时间规则",
      nextSession: "下次计时生效",
      weekday: "工作日",
      weekend: "周末",
      from: "开始",
      to: "结束",
      remove: "删除",
      addWindow: "+ 添加时段",
      noWindows: "未设置时段",
      windowsFull: "每天只能设置一个时间段",
      limits: "额度与休息",
      dailyLimit: "日额度上限",
      dailyLimitDay: "{day}每日额度",
      rest: "休息时间",
      buffer: "暂停计时上限",
      minutes: "{n} 分钟",
      deviceSecurity: "设备与安全",
      parentPassword: "家长密码",
      passwordDefault: "当前仍是默认密码，请尽快修改",
      passwordSyncPending: "云端同步中",
      autoUpdate: "自动升级",
      autoUpdateHint: "有新版本时自动更新",
      softwareUpdate: "软件更新",
      softwareUpdateHint: "查看电视版本，有新版本时可升级",
      updateCurrent: "当前版本",
      updateNotChecked: "尚未检查更新",
      updateChecking: "正在检查更新…",
      updateLatest: "已是最新版本",
      updateFound: "发现新版本 {version}",
      updateCheckFailed: "检查失败，请确认电视已联网",
      updateCheckAction: "检查更新",
      updateInstallAction: "下载并安装",
      updateDownloading: "正在下载 {percent}%",
      updateWaiting: "正在等待系统确认…",
      updateOpened: "已打开系统安装界面，请在电视上确认",
      updateNeedsPermission: "请在电视上允许安装应用，然后重试",
      updateNeedsStoragePermission: "请在电视上允许存储权限，然后重试",
      updateFailedDownload: "下载失败，请稍后重试",
      updateFailedBlocked: "系统拒绝了安装，请在电视上手动升级",
      updateFailedNotOfficial: "安装包与当前版本不匹配，请从官网重新下载",
      updateFailedNotNewer: "安装包不比当前版本新，请稍后再试",
      updateConfirmTitle: "升级这台电视？",
      updateConfirmText: "电视会下载并安装新版本；安装期间可能重启，当前观看会中断。",
      updateConfirmAction: "开始升级",
      updateAutoReady: "新版本已下载，将在后台自动安装；也可以现在安装",
      updateAutoDownloading: "自动升级正在后台下载新版本",
      updateAutoFailedDownload: "自动升级下载失败，请手动升级",
      updateAutoFailedInstall: "自动安装未完成，请手动升级",
      updateAutoUnsupported: "这台电视需要手动确认升级，自动安装不可用",
      updateAutoPermission: "电视尚未允许安装应用，自动升级已暂停",
      updateNotes: "更新内容",
      updateClose: "关闭",
      updateUnavailable: "这个版本不支持在线升级，请从官网下载",
      updateBusy: "升级正在进行，请稍候",
      remoteManagement: "远程管理",
      notBound: "未绑定",
      bound: "已绑定",
      handoffPending: "规则迁移待完成",
      endSession: "结束当前计时段",
      endSessionHint: "立即停止本次计时",
      endSessionTitle: "结束当前计时段？",
      endSessionText: "孩子当前的本次计时段会立即结束，之后重新开始计时。",
      endSessionConfirm: "结束",
      saved: "已保存",
      savedNextSession: "已保存，下一次计时生效",
      savedAppScope: "应用范围已保存，立即生效",
      savedQuiz: "答题设置已保存",
      savedSource: "来源入口已保存",
      savedPassword: "家长密码已保存",
      savedPasswordPending: "家长密码已保存；云端同步中",
      savedAutoUpdate: "自动升级设置已保存",
      savedSessionEnded: "当前计时段已结束",
      childOnSaved: "儿童模式已开启",
      childOffSaved: "儿童模式已关闭",
      childEnableTitle: "开启儿童模式",
      childEnableLead: "儿童模式会按你设置的时间规则守护这台电视。开启前请先设置家长密码。",
      childSetupPinTitle: "设置家长密码",
      childSetupPinLead: "首次开启儿童模式需要 6 位家长密码。密码只保存在电视上；请记住它。",
      newPin: "新的 6 位家长密码",
      confirmPin: "再次输入密码",
      currentPin: "当前家长密码",
      wrongPin: "当前密码不正确",
      pinUnchanged: "新密码不能与当前密码相同",
      savePin: "保存密码",
      pinMismatch: "两次输入的密码不一致",
      pinLength: "请输入 6 位数字密码",
      pinTooSimple: "密码过于简单，请换一个",
      recoveryTitle: "请保存恢复码",
      recoveryText: "这是恢复码，只显示这一次。忘记家长密码时用它恢复；请抄下来，不要截图保存在孩子能看到的设备上。",
      recoveryDone: "我已保存",
      childOffTitle: "关闭儿童模式？",
      childOffText: "关闭会立即结束当前计时段。云端模式可能需要联网结算。",
      childOffConfirm: "关闭儿童模式",
      disablePinTitle: "输入家长密码",
      disablePinLead: "关闭儿童模式需要家长密码。",
      appPickerEntertainment: "娱乐应用",
      appPickerStudy: "学习应用",
      appPickerSearch: "搜索应用",
      appPickerAll: "全部应用",
      appPickerOnly: "仅选中的应用",
      appPickerEmpty: "没有匹配的应用",
      appPickerHint: "一个都不选 = 不放行任何娱乐应用",
      appPickerStudyHint: "不选 = 没有学习模式；学习应用按学习时段和额度计时，奖励开关在「学习换娱乐」里设置。",
      appPickerDone: "完成",
      appPickerCancel: "取消",
      appPickerLoading: "正在读取应用列表…",
      passwordSheetTitle: "修改家长密码",
      remoteTitle: "远程管理",
      remoteLead: "绑定家庭云端后，本地规则会上传云端，本页规则变为只读。",
      remoteConsentLead: "绑定会把本机的规则、用量和设备编号上传到你的家庭云端。请确认你是这台电视的家长。",
      remoteConsentButton: "我已知晓并继续",
      remoteStart: "生成绑定二维码",
      remoteWaiting: "等待手机扫码绑定…",
      remoteRetrying: "正在重试连接…",
      remoteSuccess: "绑定成功",
      remoteFailed: "绑定失败：{reason}",
      remotePairCode: "绑定码",
      remoteDeviceNo: "电视编号",
      remoteStop: "停止等待",
      remoteRefresh: "刷新状态",
      remoteMigrateTitle: "规则迁移",
      remoteMigrateLead: "绑定已完成。把本地规则迁移到云端后，本页本地编辑会变为只读。",
      remoteMigrateButton: "开始迁移",
      remoteMigrateWaiting: "迁移进行中…",
      remoteDone: "规则已在云端托管。此页只能查看摘要；解绑请在家长端完成。",
      remoteNoChild: "请先开启儿童模式，再绑定远程管理。",
      remoteSummary: "云规则摘要",
      rulesRevisionLabel: "规则版本",
      cloudConfigVersion: "云端配置版本 {n}",
      sessionActive: "计时进行中",
      sessionIdle: "未在计时",
      guardEnforcing: "守护中",
      guardOverride: "已临时解锁",
      guardOther: "未守护",
      readOnlyCloud: "云端已接管，本地规则只读",
      remoteUnavailable: "此电视未启用远程服务",
      pinLocked: "密码错误次数过多，请稍后重试",
      network: "无法连接电视，请确认手机和电视在同一局域网",
      unknownError: "操作失败，请重试",
      stateChanged: "电视端状态已变化，已刷新",
      quizTakenOver: "题库已被另一台手机接管，请重新登录",
      editorLost: "编辑权已失效，请重新进入",
      rulesOverlap: "时间段重叠，请调整",
      rulesTooMany: "每天只能设置一个时间段",
      rulesApps: "应用列表无效，请重新选择",
      rulesInvalid: "时间规则无效，请检查",
      quizRunning: "答题进行中，暂时不能修改答题设置",
      pendingSwitch: "有未完成的云端切换，请先在电视上处理",
      cloudSettlement: "云端结算需要联网，请检查网络后重试",
      timeout: "电视处理超时，请重试",
      openQuizLogin: "去题库登录",
      emptyValue: "—",
      guardLabel: "守护状态",
      configVersion: "配置版本",
      saveRetry: "保存失败，已恢复电视上的最新设置",
      pairingExpired: "配对码已失效，请在电视上重新生成",
      pairUnavailable: "电视还没有生成配对码",
      pairInvalid: "配对码不正确",
      verifyExpired: "验证已过期，请重新输入家长密码",
      unlockRequired: "请先完成家长临时解锁",
      quizLoginRequired: "请先登录题库，再进入本地家长中心",
      personalize: "儿童模式壁纸",
      personalizeHint: "云端片库、本地上传与默认壁纸",
      introVideoTitle: "闯关启动视频",
      introVideoHint: "仅保存在本机，不上传云端",
      introVideoLimit: "MP4/MOV · H.264/H.265 等（需电视支持）· 单段 ≤128 MB，不限时长",
      introVideoBuiltin: "内置片头",
      introVideoActive: "当前使用",
      introVideoSetActive: "设为闯关片头",
      introVideoRestore: "恢复内置片头",
      introVideoUpload: "上传视频或图片",
      introVideoUploading: "正在上传…",
      introVideoEmpty: "还没有上传视频",
      introVideoDelete: "删除",
      introVideoDeleteTitle: "删除这段视频？",
      introVideoDeleteText: "删除后不能恢复；如果正在使用，会自动回到内置片头。",
      introVideoSelected: "已设为闯关片头",
      introVideoDeleted: "视频已删除",
      introVideoRestored: "已恢复内置片头",
      introVideoUploaded: "视频已上传，点「设为闯关片头」使用",
      introVideoTooLarge: "视频超过 128 MB，请压缩后再试",
      introVideoBadDuration: "闯关片头需 ≤15 秒；这段视频可以设为儿童模式壁纸",
      introVideoBadFormat: "只支持 JPG/PNG/WebP 图片，或 16:9 MP4/MOV 视频（编码需电视支持）",
      introVideoInvalid: "视频无法读取或格式不受支持",
      introVideoNoSpace: "电视存储空间不足",
      introVideoMeta: "{size} MB · {seconds} 秒 · {width}×{height}",
      introVideoListTitle: "视频库",
      introVideoUploadHint: "可一次选择多段视频；上传完成后点「设为片头」",
      introVideoUploadProgress: "正在上传 {index}/{total}：{name} · {percent}%",
      introVideoBatchDone: "已上传 {added} 个壁纸",
      introVideoBatchFailed: "有 {failed} 个未上传：{reason}",
      introVideoUse: "设为片头",
      introVideoHevc: "此电视无法解码 H.265/HEVC；请转换为 H.264 后重试",
      introVideoUnsupportedCodec: "此电视无法解码该视频编码；请转换为 H.264 后重试",
      mediaPageTitle: "儿童模式壁纸",
      mediaCurrentTitle: "当前选择",
      mediaRoleQuiz: "闯关片头壁纸",
      mediaRoleLock: "儿童模式壁纸",
      mediaRoleBoth: "两者都使用",
      mediaRoleQuizHint: "答题开始时显示",
      mediaRoleLockHint: "儿童模式锁屏和自律桌面显示；视频循环播放，可开关声音",
      mediaRoleLabel: "使用位置",
      mediaSourceDefault: "儿童模式默认壁纸",
      mediaSourceOfficial: "云端片库",
      mediaSourceLocal: "本地上传",
      mediaKindVideo: "视频",
      mediaKindImage: "图片",
      mediaKindTabsLabel: "壁纸类型",
      mediaSourceTabsLabel: "壁纸来源",
      mediaMetaImage: "{size} MB · {width}×{height}",
      mediaDefaultBadge: "默认",
      mediaImageRoleNote: "图片只能用于儿童模式壁纸。",
      mediaChoose: "选择",
      mediaChange: "更换",
      mediaCustomized: "已自定义",
      mediaTabCloud: "云端片库",
      mediaTabLocal: "本地上传",
      mediaEmpty: "片库暂时没有壁纸",
      mediaCatalogOff: "此版本未配置云端片库",
      mediaCatalogUnavailable: "云端片库暂时不可用，仍可使用本地上传或儿童模式默认壁纸",
      mediaRefresh: "刷新片库",
      mediaRefreshing: "正在刷新…",
      mediaLoading: "正在读取电视片库…",
      mediaStateNotDownloaded: "未下载",
      mediaStateDownloading: "下载中 {percent}%",
      mediaStateVerifying: "正在校验…",
      mediaStateReady: "已下载",
      mediaStateLocalReady: "已就绪",
      mediaStateFailed: "下载失败",
      mediaStateUpdate: "有新版本",
      mediaMeta: "{duration} · {size} MB · {width}×{height}",
      mediaDownload: "下载这个壁纸",
      mediaDownloadDone: "已下载，可在「当前选择」中使用",
      mediaPickerTitle: "选择{module}",
      mediaPickerEmpty: "还没有已下载的壁纸",
      mediaPickerEmptyVideo: "还没有已下载的视频。",
      mediaPickerGoLibrary: "去云端片库",
      mediaCurrentBadge: "当前",
      mediaRetryDownload: "重试下载",
      mediaUpdateLocal: "更新本地副本",
      mediaClose: "关闭",
      mediaBadgeOfficial: "官方",
      mediaBadgeLocal: "本地",
      mediaDownloadUse: "下载并使用",
      mediaUse: "使用这个壁纸",
      mediaInUse: "当前使用",
      mediaManage: "管理",
      mediaCancelDownload: "取消下载",
      mediaRestoreDefault: "恢复儿童模式默认壁纸",
      mediaRestoreTitle: "恢复儿童模式默认壁纸？",
      mediaRestoreText: "清除后使用儿童模式默认壁纸；家长模式的 Aerial 壁纸不变。",
      mediaRestoreDone: "已恢复儿童模式默认壁纸",
      mediaDeleteLocal: "删除本地文件",
      mediaDeleteUpload: "删除上传",
      mediaDeleteTitle: "删除这个壁纸？",
      mediaDeleteText: "删除本地副本；使用它的位置会恢复儿童模式默认壁纸。",
      mediaDeleted: "壁纸已删除",
      mediaSelected: "壁纸设置已更新",
      mediaDownloadStarted: "已开始下载",
      mediaDownloading: "正在下载…",
      mediaCancelled: "已取消下载",
      mediaNotFound: "壁纸不存在或已不可用",
      mediaNotReady: "壁纸尚未下载完成",
      mediaBusy: "另一个壁纸正在下载，请稍后",
      mediaRoleInvalid: "壁纸不支持所选角色",
      mediaNoSpace: "电视存储空间不足",
      mediaDeleteFailed: "删除失败，请重试",
      mediaDownloadFailed: "下载失败，请检查电视网络后重试",
      mediaLocalEmpty: "还没有上传壁纸",
      mediaSoundTitle: "壁纸声音",
      mediaSoundHint: "儿童模式声音总开关：有背景音乐时优先播放音乐、视频静音；家长模式无声",
      mediaSoundUnavailable: "选择带声音的视频或背景音乐后才能开启",
      mediaSoundSaved: "壁纸声音设置已更新",
      mediaLockSummary: "{count} 项 · 图片每 {duration} 切换",
      mediaLockPickerHint: "按点选顺序轮播；图片按设定时间停留，视频播放完自动切换",
      mediaLockEmptyHint: "未选择时使用默认壁纸；可多选轮播",
      mediaLockSingleHint: "再选一张即可开始轮播；再次点击可取消",
      mediaLockInterval: "图片停留",
      audioTitle: "背景音乐",
      audioHint: "在所有儿童模式壁纸下循环播放，受「壁纸声音」开关控制；音乐开启时壁纸视频自动静音，答题或家长操作时会暂停",
      audioNone: "无背景音乐",
      audioNoneHint: "只播放壁纸视频自带的声音",
      audioUpload: "上传音频",
      audioUploading: "正在上传音频…",
      audioUploaded: "音频已上传",
      audioSelected: "背景音乐已更新",
      audioDeleted: "音频已删除",
      audioDelete: "删除",
      audioDeleteTitle: "删除这首音频？",
      audioDeleteText: "删除后不能恢复；使用它的儿童模式会恢复为无背景音乐。",
      audioMeta: "{size} MB · {duration}",
      audioBadFormat: "只支持 MP3、M4A/AAC、OGG 音频文件。",
      audioTooLarge: "音频超过 20 MB，请压缩后再试。",
      audioBadDuration: "音频时长需在 30 分钟以内。",
      audioUnsupported: "此电视无法解码该音频编码。",
      audioNoSpace: "电视存储空间不足。",
      audioEmpty: "还没有上传音频",
      audioSourceCloud: "云端曲库",
      audioSourceLocal: "本地上传",
      audioSourceTabsLabel: "背景音乐来源",
      audioCloudEmpty: "云端曲库还没有音乐",
      audioCatalogOff: "此版本未配置云端曲库",
      audioCatalogUnavailable: "云端曲库暂时不可用，已下载的音乐仍可播放",
      audioRefresh: "刷新曲库",
      audioRefreshing: "正在刷新…",
      audioDownloadUse: "下载并使用",
      audioUse: "使用",
      audioDownload: "下载",
      audioRetry: "重试",
      audioUpdate: "更新",
      audioReady: "已下载",
      audioNotDownloaded: "未下载",
      audioStateDownloading: "下载中 {percent}%",
      audioStateVerifying: "正在校验…",
      audioStateFailed: "下载失败",
      audioStateUpdate: "有新版本",
      audioDownloadStarted: "已开始下载，完成后自动设为背景音乐",
      audioDownloadDone: "已下载并设为背景音乐",
      audioDownloaded: "音乐已下载",
      audioDownloadCancelled: "已取消下载",
      audioDownloadFailed: "下载失败，请检查电视网络后重试",
      audioBusy: "另一首音乐正在下载，请稍后再试",
      audioDeleteDownload: "删除本地副本",
      audioDeleteDownloadTitle: "删除已下载的音乐？",
      audioDeleteDownloadText: "只删除电视上的副本，云端曲库里的音乐仍可重新下载；如果正在使用，背景音乐会恢复为无。",
      audioDownloadDeleted: "本地副本已删除",
      mediaIntervalSeconds: "{n} 秒",
      mediaIntervalMinutes: "{n} 分钟",
      mediaLockLimitHint: "已达壁纸数量上限，请先取消一张",
      mediaLockInvalid: "轮播设置无效，请重新选择",
      mediaSave: "保存",
      mediaSaveCount: "保存（{n} 张）",
      mediaUseDefault: "使用默认壁纸",
      mediaPlaylistSaved: "轮播设置已更新",
      mediaOnlyWallpaper: "仅壁纸",
      mediaHasAudio: "有声",
      mediaTimingLead: "单次计时",
      mediaSingleTimingNote: "跟随儿童模式壁纸",
      mediaReadonly: "只读",
      mediaImageTooLarge: "图片超过 12 MB，请压缩后再试。",
      mediaImageBadFormat: "只支持 JPG/PNG/WebP 图片。",
      mediaImageInvalid: "图片无法读取或格式不受支持。",
      mediaBadFormat: "文件格式不受支持。",
      mediaTooLarge: "文件超过大小限制。",
      remoteQrAlt: "远程管理绑定二维码",
      featureUnavailable: "当前版本未启用网页家长中心",
      openFailed: "家长中心暂时不可用"
    },
    en: {
      parentCenterTitle: "Parent center",
      moreTitle: "More",
      familyGroup: "Family",
      familyMemberCount: "{n} members",
      familyActiveOnTv: "On TV now",
      familyAddChild: "Add child",
      familyAddChildDisabled: "Adding a member is unavailable right now.",
      familySingleNote: "Tap a member to change the name and avatar; the TV only runs the current member's settings.",
      memberAddAction: "Add",
      memberNewLabel: "New member",
      memberAdded: "Member added",
      memberAddFailed: "Could not add the member. Try again.",
      familyCloudReadOnly: "Bound to the cloud. Manage family members on your phone or in the cloud.",
      familyLocalOnly: "The profile stays on this TV and does not affect today's usage or quiz progress.",
      memberEditTitle: "Edit member",
      memberNameLabel: "Name",
      memberNamePlaceholder: "For example: Child 1",
      memberAvatarLabel: "Avatar",
      memberUploadPhoto: "Upload photo",
      memberChangePhoto: "Change photo",
      memberRemovePhoto: "Remove photo",
      memberSaveAction: "Save changes",
      memberSaved: "Member profile saved",
      memberDeleteAction: "Delete member",
      memberDeleteTitle: "Delete member?",
      memberDeleteText: "This removes {name}'s profile, settings and photo. It cannot be undone. The TV's current member and the default member cannot be deleted.",
      memberDeleted: "Member deleted",
      memberDeleteFailed: "Could not delete the member. Try again.",
      memberDeleteLast: "At least one member must remain",
      familyDeleteActiveHint: "Switch the TV to another member before deleting this one.",
      familyDeleteDefaultHint: "The default member holds this TV's historical data and cannot be deleted; rename it or change its avatar instead.",
      memberNameInvalid: "Enter a name with at most 12 characters",
      memberImageType: "Choose an image file",
      memberImageTooLarge: "The image must be 25 MB or smaller",
      memberImageInvalid: "This image cannot be read. Try another one.",
      memberImageSaveFailed: "Could not save the photo. Try again.",
      avatarCropTitle: "Adjust avatar",
      avatarCropHint: "Drag the photo to position it; pinch or use the slider to zoom",
      avatarCropZoom: "Zoom",
      avatarCropApply: "Use photo",
      avatarCropProcessing: "Processing photo…",
      avatarCropFailed: "This photo cannot be used as an avatar. Try another one.",
      memberNotFound: "Member not found",
      familyNotReady: "Member profiles are not available yet",
      avatarRabbit: "Rabbit",
      avatarFox: "Fox",
      avatarBear: "Bear",
      avatarPanda: "Panda",
      memberSourceLocal: "This TV",
      memberSourceCloud: "Cloud",
      timeAndApps: "Entertainment time plan",
      timeAndAppsSummary: "{groups} day groups · {apps}",
      timeTabManage: "App management",
      timeTabPlan: "Time plan",
      timeTabsLabel: "Entertainment time plan sections",
      day1: "Mon",
      day2: "Tue",
      day3: "Wed",
      day4: "Thu",
      day5: "Fri",
      day6: "Sat",
      day7: "Sun",
      daySeparator: ", ",
      groupWeekdays: "Mon–Fri",
      groupWeekend: "Sat–Sun",
      addGroup: "Add group",
      addGroupTitle: "Move a day into a new group",
      addGroupHint: "The new group copies this group's windows and quotas; you can edit it separately.",
      deleteGroup: "Delete group",
      deleteGroupTitle: "Delete this group?",
      deleteGroupBody: "Its days move to a neighbouring group.",
      dayGroupsSection: "Day groups",
      publicParams: "Common settings",
      dayAssignment: "Day assignment",
      dayAssignmentHint: "Tap a day to move it to the selected group.",
      segmentHint: "Tap a day to start a new segment; tap a segment start to merge it into the previous one.",
      segmentSplitHint: "Start a new segment here",
      segmentMergeHint: "Merge into previous segment",
      segmentFirstHint: "First segment starts here",
      segmentNonContiguous: "The saved groups contain non-adjacent days; they are shown as adjacent runs. The rules are unchanged.",
      presetWholeWeek: "Whole week",
      presetEveryDay: "Every day",
      studyShort: "Study",
      entertainmentShort: "Entertainment",
      mergeSegmentsTitle: "Merge segments?",
      mergeSegmentsBody: "The merged segment uses the left settings; settings from the other segments will be cleared.",
      mergeSegmentsAction: "Merge",
      studySchedule: "Study time",
      entertainmentSchedule: "Entertainment time",
      studyLimit: "Study quota",
      entertainmentLimit: "Entertainment quota",
      unlimited: "Unlimited",
      unlimitedApps: "Unlimited apps",
      unlimitedAppsHint: "Never uses study or entertainment time, and ignores time windows",
      unlimitedNone: "No unlimited apps set",
      unlimitedEmpty: "No unlimited apps set",
      selectAllApps: "Select all",
      clearAllApps: "Clear all",
      unlimitedAllWarning: "Every available app is now unlimited; the entertainment time plan will no longer limit them.",
      unlimitedManageHint: "Tap any icon to manage these apps",
      appIconHint: "Tap an icon or + to pick apps",
      appPickerManage: "Manage apps",
      studyEarnEmptyHint: "Add study apps your child doesn't open on their own",
      moveToUnlimitedConfirm: "Adding it to unlimited apps removes it from study reward.",
      moveToUnlimitedAction: "Add anyway",
      moveToStudyConfirm: "Adding it to study apps removes it from unlimited apps.",
      studyAppsHint: "Timed by the study windows and quota",
      appPickerUnlimited: "Unlimited apps",
      appPickerUnlimitedHint: "These apps never use study or entertainment time",
      appPickerTakenStudy: "Study app",
      appPickerTakenUnlimited: "Unlimited app",
      restAndBuffer: "Common settings",
      onlyUnlimitedNoTimer: "Only unlimited apps are in scope; no timer is needed.",
      rulesDayGroups: "Day groups must cover all seven days with valid windows and quotas",
      rulesUnlimitedApps: "The unlimited app list is invalid; select the apps again",
      rulesClientOutdated: "This page is out of date. Refresh and try again.",
      localSettings: "This TV",
      pinSet: "Set",
      pinUnset: "Not set",
      quizAndAi: "Question bank & AI",
      otherOptions: "Other",
      privacyHint: "On-device data and permissions",
      aiTitle: "AI questions",
      privacyInfo: "Privacy notice",
      downloadParentApp: "Download parent app",
      downloadParentAppHint: "Android 7.0+",
      viewAction: "View",
      manageAction: "Manage",
      logoutAccount: "Log out",
      timePageHint: "Choose which apps need entertainment time, how the child earns it, and when it is available. Entertainment apps and common settings are shared all week and apply now; each day group sets its own study and entertainment windows and quotas, effective from the next session.",
      tvInputSources: "TV input sources",
      tvInputSourcesHint: "Only affects the home-screen entries; it is not a hard lock",
      entryChecking: "Reading the TV state…",
      entryPair: "Off · pair on the TV",
      entryNotReady: "The TV is not ready yet",
      entryLocalUnlock: "Local mode · temporary unlock required",
      entryLocalVerify: "Local mode · parent password required",
      entryLocalReady: "Local mode · editable",
      entryLocalBlocked: "Local mode · cannot enter now",
      entryCloudUnlock: "Cloud mode · unlock on the TV",
      entryCloudVerify: "Cloud mode · parent password required",
      entryCloudReady: "Cloud mode · editable",
      back: "Back",
      loading: "Loading…",
      retry: "Retry",
      retryIn: "Try again in {n}s",
      gateLockedTitle: "Parent center locked",
      gateLockedLead: "It uses the same lock as the TV parent center. Temporarily unlock the TV from its lock screen, then continue here.",
      gateUnlockButton: "Unlock with parent password",
      gatePairTitle: "Pair on the TV first",
      gatePairLead: "Child mode is off, so there is no parent password to verify. In the TV parent center choose “Configure on phone”, then enter the 8-digit code shown on the TV.",
      pairCodeLabel: "8-digit pairing code",
      pairSubmit: "Connect",
      gateVerifyTitle: "Verify parent password",
      gateVerifyLead: "The TV has just been temporarily unlocked. Enter the same parent password to continue.",
      pinLabel: "6-digit parent password",
      pinSubmit: "Continue",
      pinCancel: "Cancel",
      gateBlockedTitle: "The parent center is unavailable",
      gateNotReadyTitle: "The TV is not ready",
      gateNotReadyLead: "Try again shortly. The parent center stays closed while the TV loads its policy.",
      gateQuizLogin: "This phone is not signed in to the quiz page. Go back to the quiz page, sign in, then open the parent center.",
      gateQuizLoginButton: "Go to quiz sign-in",
      gateWebEditing: "Another phone is editing the parent center. Close that page or wait 20 seconds.",
      gateTakenOver: "Another phone took the editor back. This page is read-only.",
      gateTakenOverHint: "Taking over signs the other phone out of this editor immediately; this phone becomes the only editor.",
      gateTakeoverButton: "Take over anyway",
      leaveTitle: "Leave the parent center?",
      leaveText: "The editor is released immediately and another phone can continue.",
      leaveConfirm: "Leave",
      cancel: "Cancel",
      childMode: "Child mode",
      childOn: "On",
      childOff: "Off",
      localMode: "Local mode",
      remoteMode: "Cloud mode",
      todayUsed: "{n} min used today",
      localRules: "Local rules",
      cloudRules: "Cloud rules · read-only",
      appScope: "Entertainment apps",
      entertainmentApps: "Entertainment apps",
      studyApps: "Study apps",
      allApps: "All apps",
      appsSelected: "{n} apps selected",
      noEntertainment: "None (no entertainment allowed)",
      studyNone: "Not set (no study mode)",
      watchMethods: "Entertainment start conditions",
      quizGateSwitchHint: "Answer questions first; the quiz result sets this session's time",
      studyEarnSwitchHint: "Study apps earn entertainment time at the set ratio; turning this off pauses study apps too",
      directStartTitle: "Direct start",
      directStartSwitchHint: "Tap an entertainment app and watch without a quiz or study",
      directDuration: "Time per session",
      directPauseNote: "The three routes are independent and can all be on. Each session is still limited by the day group's entertainment quota, window and the single-session time limit.",
      unifiedLimits: "Common settings",
      sessionCapTitle: "Single-session time limit",
      sessionCapHint: "Ceiling for one session of any of the three routes; the daily total is set per day group.",
      directMaxHint: "Time per session, at most {n} min",
      quizAutoLength: "Calculated from the quiz result, up to {n} min per session",
      routeDailyCount: "Daily count",
      countTimes: "{n} times",
      studyRewardMaxHint: "Limited only by the public cap, up to {n} min; independent of study time",
      allRoutesOffTitle: "Turn off every viewing route?",
      allRoutesOffBody: "After saving, the child has no entertainment route and the TV shows “No available viewing route”. Parent override is unchanged.",
      allRoutesOffConfirm: "Save anyway",
      routesOffHint: "All three viewing routes are off.",
      capClampedNotice: "Adjusted to the public cap: {n} min",
      studyAppsRetained: "Off · {n} study apps kept; they return when you turn it on",
      manageStudyApps: "Manage study apps",
      studyMovedHint: "Study apps are now managed in “Study to earn” above; there is no separate study app setting.",
      studyEarnTitle: "Study to earn",
      studyEarnIntro: "Focused time in a reward-enabled study app earns entertainment time. Study time is always limited by the study windows and quota; the reward also rechecks the entertainment window and quota. Only time is tracked; content is not monitored.",
      studyEarnRatio: "Study {study} min → earn {reward} min",
      studyEarnStudyMinutes: "Study minutes",
      studyEarnRewardMinutes: "Reward minutes",
      studyEarnApps: "Study apps (all earn the reward)",
      studyEarnNoApps: "No study apps yet.",
      studyEarnHint: "Every study app is timed by the study windows and quota and earns the reward. Leaving the app pauses the timer; screen off also pauses.",
      addStudyApps: "Add study apps",
      studyEarnEmptyHint: "Add study apps your child is less willing to use, and use the reward to encourage them.",
      studyEarnEnabledCount: "{n} enabled",
      studyEarnDisabled: "Off",
      studyEarnInvalid: "Invalid study-to-earn settings",
      sourcesAndQuiz: "Sources and quiz",
      sourceEntries: "Source entries",
      sourceHint: "Only changes the TV home entries; it is not a lock",
      quizChallenge: "Quiz challenge",
      quizEntryVisible: "Show quiz entry on lock screen",
      quizEntryHint: "When on, the quiz icon appears first on the lock screen. Turning it off does not affect entering the quiz from entertainment apps.",
      quizManage: "Quiz management",
      quizManageHint: "Manage questions and quiz settings on the quiz page",
      quizPageQrTitle: "Scan to open the quiz page",
      quizPageQrLead: "Scan with the phone camera to open this TV's quiz page on the same network",
      parentAppQrTitle: "Download the parent app",
      parentAppQrLead: "Scan to download the official parent app, or open the download page in the phone browser",
      parentAppPromptLead: "Install it on your phone to check and manage the TV remotely, even when you are away; Android 7.0 and later.",
      parentAppPromptDownload: "Download",
      parentAppPromptLater: "Not now",
      parentAppDownloadLimit: "Daily download limit reached; try again tomorrow",
      quizQrFailed: "Could not create the QR code; try again",
      unlockFineprint: "Unlock is shared with the TV: the same parent verification used on the TV lock screen applies here and relocks after 10 minutes.",
      pairHint: "The pairing code is valid for 5 minutes. After 5 wrong entries, create a new one on the TV.",
      passwordHint: "Changing the parent PIN requires the current PIN; it takes effect immediately.",
      pinLockedRetry: "Too many wrong passwords; try again in {n}s",
      sensitivePinTitle: "Verify parent PIN",
      sensitivePinLeadRules: "Enter the parent PIN to edit time and apps",
      sensitivePinLeadTakeOver: "Enter the parent PIN to leave child mode temporarily",
      sensitivePinLeadDelete: "Enter the parent PIN to delete a member",
      sensitivePinDefault: "Change the initial parent PIN on the TV first",
      sensitivePinExpired: "Verification expired; enter the PIN again",
      sensitivePinRequired: "Verify the parent PIN first",
      confirmAutoUpdateOffTitle: "Turn off auto-update?",
      confirmAutoUpdateOffBody: "The TV will stop fetching new versions automatically; updates must be installed manually.",
      confirmAutoUpdateOffAction: "Turn off",
      remoteRefreshFailed: "Could not refresh the remote state; check the TV network and retry",
      timeRules: "Time rules",
      nextSession: "Applies to the next session",
      weekday: "Weekdays",
      weekend: "Weekends",
      from: "Start",
      to: "End",
      remove: "Remove",
      addWindow: "+ Add window",
      noWindows: "No time windows",
      windowsFull: "One time window per day",
      limits: "Limits and breaks",
      dailyLimit: "Daily limit",
      dailyLimitDay: "{day} daily limit",
      rest: "Rest time",
      buffer: "Max pause time",
      minutes: "{n} min",
      deviceSecurity: "Device and security",
      parentPassword: "Parent password",
      passwordDefault: "Still using the default password; change it soon",
      passwordSyncPending: "Syncing to the cloud",
      autoUpdate: "Auto update",
      autoUpdateHint: "Update automatically when a new version is available",
      softwareUpdate: "Software update",
      softwareUpdateHint: "View the TV version and update when one is available",
      updateCurrent: "Current version",
      updateNotChecked: "Not checked yet",
      updateChecking: "Checking for updates…",
      updateLatest: "You're on the latest version",
      updateFound: "New version {version} is available",
      updateCheckFailed: "Check failed; make sure the TV is online",
      updateCheckAction: "Check for updates",
      updateInstallAction: "Download and install",
      updateDownloading: "Downloading {percent}%",
      updateWaiting: "Waiting for the system to confirm…",
      updateOpened: "The system installer is open; confirm on the TV",
      updateNeedsPermission: "Allow app installs on the TV, then retry",
      updateNeedsStoragePermission: "Allow storage access on the TV, then retry",
      updateFailedDownload: "Download failed; try again later",
      updateFailedBlocked: "The system refused the install; update manually on the TV",
      updateFailedNotOfficial: "The package does not match this app; download it from the official site",
      updateFailedNotNewer: "The package is not newer than this version; try again later",
      updateConfirmTitle: "Update this TV?",
      updateConfirmText: "The TV will download and install the new version. It may restart and interrupt what is playing.",
      updateConfirmAction: "Update",
      updateAutoReady: "The new version is downloaded and will install automatically; you can also install it now",
      updateAutoDownloading: "Auto update is downloading the new version",
      updateAutoFailedDownload: "Auto update could not download; update manually",
      updateAutoFailedInstall: "The automatic install did not finish; update manually",
      updateAutoUnsupported: "This TV needs manual confirmation, so automatic install is unavailable",
      updateAutoPermission: "The TV does not allow app installs yet; auto update is paused",
      updateNotes: "What's new",
      updateClose: "Close",
      updateUnavailable: "This copy cannot install updates; download it from the official site",
      updateBusy: "An update is already in progress; wait a moment",
      remoteManagement: "Remote management",
      notBound: "Not bound",
      bound: "Bound",
      handoffPending: "Rule migration pending",
      endSession: "End the current session",
      endSessionHint: "Stops this timed session now",
      endSessionTitle: "End the current session?",
      endSessionText: "The child's current session stops immediately; a later session starts fresh.",
      endSessionConfirm: "End",
      saved: "Saved",
      savedNextSession: "Saved; it applies to the next session",
      savedAppScope: "App scope saved and active now",
      savedQuiz: "Quiz setting saved",
      savedSource: "Source entries saved",
      savedPassword: "Parent password saved",
      savedPasswordPending: "Parent password saved; syncing to the cloud",
      savedAutoUpdate: "Auto-update setting saved",
      savedSessionEnded: "The current session has ended",
      childOnSaved: "Child mode is on",
      childOffSaved: "Child mode is off",
      childEnableTitle: "Turn on child mode",
      childEnableLead: "Child mode guards this TV using your time rules. Set a parent password first.",
      childSetupPinTitle: "Set the parent password",
      childSetupPinLead: "Child mode needs a 6-digit parent password the first time. It stays on the TV; remember it.",
      newPin: "New 6-digit parent password",
      confirmPin: "Enter the password again",
      currentPin: "Current parent PIN",
      wrongPin: "The current PIN is incorrect",
      pinUnchanged: "The new PIN must differ from the current PIN",
      savePin: "Save password",
      pinMismatch: "The passwords do not match",
      pinLength: "Enter a 6-digit password",
      pinTooSimple: "That password is too easy; choose another",
      recoveryTitle: "Save the recovery code",
      recoveryText: "This recovery code is shown once. Use it if the parent password is forgotten. Write it down; do not keep it where the child can see it.",
      recoveryDone: "I saved it",
      childOffTitle: "Turn off child mode?",
      childOffText: "This ends the current session immediately. Cloud mode may need a network settlement.",
      childOffConfirm: "Turn off child mode",
      disablePinTitle: "Enter the parent password",
      disablePinLead: "Turning off child mode needs the parent password.",
      appPickerEntertainment: "Entertainment apps",
      appPickerStudy: "Study apps",
      appPickerSearch: "Search apps",
      appPickerAll: "All apps",
      appPickerOnly: "Only selected apps",
      appPickerEmpty: "No matching apps",
      appPickerHint: "Selecting nothing allows no entertainment apps",
      appPickerStudyHint: "Selecting nothing means no study mode. Study apps are timed by the study windows and quota; reward switches are set in Study to earn.",
      appPickerDone: "Done",
      appPickerCancel: "Cancel",
      appPickerLoading: "Reading the app list…",
      passwordSheetTitle: "Change parent password",
      remoteTitle: "Remote management",
      remoteLead: "After binding to the family cloud, local rules are uploaded and this page becomes read-only.",
      remoteConsentLead: "Binding uploads this TV's rules, usage and device number to your family cloud. Confirm that you are this TV's parent.",
      remoteConsentButton: "I understand and continue",
      remoteStart: "Create binding QR code",
      remoteWaiting: "Waiting for the phone to scan…",
      remoteRetrying: "Retrying the connection…",
      remoteSuccess: "Binding succeeded",
      remoteFailed: "Binding failed: {reason}",
      remotePairCode: "Binding code",
      remoteDeviceNo: "TV number",
      remoteStop: "Stop waiting",
      remoteRefresh: "Refresh state",
      remoteMigrateTitle: "Rule migration",
      remoteMigrateLead: "The binding is complete. Migrating local rules to the cloud makes local editing read-only.",
      remoteMigrateButton: "Start migration",
      remoteMigrateWaiting: "Migrating…",
      remoteDone: "Rules are hosted in the cloud. This page shows a summary only; unbind from the parent app.",
      remoteNoChild: "Turn on child mode before binding remote management.",
      remoteSummary: "Cloud rule summary",
      rulesRevisionLabel: "Rules revision",
      cloudConfigVersion: "Cloud config version {n}",
      sessionActive: "Session running",
      sessionIdle: "No active session",
      guardEnforcing: "Guarding",
      guardOverride: "Temporarily unlocked",
      guardOther: "Not guarding",
      readOnlyCloud: "Cloud managed; local rules are read-only",
      remoteUnavailable: "Remote service is not enabled on this TV",
      pinLocked: "Too many wrong passwords; try again later",
      network: "Cannot reach the TV. Keep the phone and TV on the same network",
      unknownError: "The action failed; try again",
      stateChanged: "The TV state changed; refreshed",
      quizTakenOver: "Another phone took over the quiz page; sign in again",
      editorLost: "The editor lease ended; open the parent center again",
      rulesOverlap: "Time windows overlap; adjust them",
      rulesTooMany: "One time window per day",
      rulesApps: "The app selection is invalid; select again",
      rulesInvalid: "The time rules are invalid; check them",
      quizRunning: "A quiz is running; quiz settings cannot change now",
      pendingSwitch: "A cloud switch is pending; finish it on the TV first",
      cloudSettlement: "Cloud settlement needs a network connection; check it and retry",
      timeout: "The TV timed out; try again",
      openQuizLogin: "Open quiz sign-in",
      emptyValue: "—",
      guardLabel: "Guard",
      configVersion: "Config version",
      saveRetry: "Save failed; restored the latest settings from the TV",
      pairingExpired: "The pairing code expired; create a new one on the TV",
      pairUnavailable: "The TV has not created a pairing code yet",
      pairInvalid: "That pairing code is wrong",
      verifyExpired: "Verification expired; enter the parent password again",
      unlockRequired: "Temporarily unlock the TV first",
      quizLoginRequired: "Sign in to the quiz page before opening the local parent center",
      personalize: "Child-mode wallpapers",
      personalizeHint: "Cloud library, local uploads and the default wallpaper",
      introVideoTitle: "Quiz start video",
      introVideoHint: "Stored on this TV only; never uploaded",
      introVideoLimit: "MP4/MOV · H.264/H.265, etc. (TV decoder required) · ≤128 MB each, any length",
      introVideoBuiltin: "Built-in intro",
      introVideoActive: "In use",
      introVideoSetActive: "Use as quiz intro",
      introVideoRestore: "Restore built-in intro",
      introVideoUpload: "Upload video or image",
      introVideoUploading: "Uploading…",
      introVideoEmpty: "No uploaded videos yet",
      introVideoDelete: "Delete",
      introVideoDeleteTitle: "Delete this video?",
      introVideoDeleteText: "This cannot be undone. If it is in use, the built-in intro will be restored.",
      introVideoSelected: "Set as the quiz intro",
      introVideoDeleted: "Video deleted",
      introVideoRestored: "Built-in intro restored",
      introVideoUploaded: "Video uploaded. Tap \"Use as quiz intro\" to use it.",
      introVideoTooLarge: "The video is over 128 MB; compress it and try again",
      introVideoBadDuration: "Quiz intros must be ≤15 seconds; this video can still be used as a child-mode wallpaper",
      introVideoBadFormat: "Only JPG/PNG/WebP images, or 16:9 MP4/MOV videos with a codec this TV can decode",
      introVideoInvalid: "The video cannot be read or is unsupported",
      introVideoNoSpace: "The TV is low on storage",
      introVideoMeta: "{size} MB · {seconds} s · {width}×{height}",
      introVideoListTitle: "Video library",
      introVideoUploadHint: "You can select several videos at once. After uploading, tap Use as the intro.",
      introVideoUploadProgress: "Uploading {index}/{total}: {name} · {percent}%",
      introVideoBatchDone: "Uploaded {added} wallpaper(s)",
      introVideoBatchFailed: "{failed} item(s) not uploaded: {reason}",
      introVideoUse: "Use as intro",
      introVideoHevc: "This TV cannot decode H.265/HEVC; convert it to H.264 and retry.",
      introVideoUnsupportedCodec: "This TV cannot decode this video codec; convert it to H.264 and retry.",
      mediaPageTitle: "Child-mode wallpapers",
      mediaCurrentTitle: "Current selection",
      mediaRoleQuiz: "Quiz intro wallpaper",
      mediaRoleLock: "Child-mode wallpaper",
      mediaRoleBoth: "Both",
      mediaRoleQuizHint: "Shown when a quiz starts",
      mediaRoleLockHint: "Shown on the child-mode lock screen and self-discipline desktop; videos loop and sound can be enabled",
      mediaRoleLabel: "Use for",
      mediaSourceDefault: "Default child-mode wallpaper",
      mediaSourceOfficial: "Cloud library",
      mediaSourceLocal: "Local upload",
      mediaKindVideo: "Video",
      mediaKindImage: "Image",
      mediaKindTabsLabel: "Wallpaper type",
      mediaSourceTabsLabel: "Wallpaper source",
      mediaMetaImage: "{size} MB · {width}×{height}",
      mediaDefaultBadge: "Default",
      mediaImageRoleNote: "Images can only be used as the child-mode wallpaper.",
      mediaChoose: "Choose",
      mediaChange: "Change",
      mediaCustomized: "Customized",
      mediaTabCloud: "Cloud library",
      mediaTabLocal: "Local upload",
      mediaEmpty: "No wallpapers in the library yet",
      mediaCatalogOff: "The cloud library is not configured in this build",
      mediaCatalogUnavailable: "The cloud library is unavailable. Local uploads and the default child-mode wallpaper still work.",
      mediaRefresh: "Refresh library",
      mediaRefreshing: "Refreshing…",
      mediaLoading: "Reading the TV library…",
      mediaStateNotDownloaded: "Not downloaded",
      mediaStateDownloading: "Downloading {percent}%",
      mediaStateVerifying: "Verifying…",
      mediaStateReady: "Downloaded",
      mediaStateLocalReady: "Ready",
      mediaStateFailed: "Download failed",
      mediaStateUpdate: "Update available",
      mediaMeta: "{duration} · {size} MB · {width}×{height}",
      mediaDownload: "Download this wallpaper",
      mediaDownloadDone: "Downloaded. Choose it under Current selection.",
      mediaPickerTitle: "Choose {module}",
      mediaPickerEmpty: "No downloaded wallpapers yet",
      mediaPickerEmptyVideo: "No downloaded videos yet.",
      mediaPickerGoLibrary: "Open cloud library",
      mediaCurrentBadge: "Current",
      mediaRetryDownload: "Retry download",
      mediaUpdateLocal: "Update local copy",
      mediaClose: "Close",
      mediaBadgeOfficial: "Official",
      mediaBadgeLocal: "Local",
      mediaDownloadUse: "Download and use",
      mediaUse: "Use this wallpaper",
      mediaInUse: "In use",
      mediaManage: "Manage",
      mediaCancelDownload: "Cancel download",
      mediaRestoreDefault: "Restore the default child-mode wallpaper",
      mediaRestoreTitle: "Restore the default child-mode wallpaper?",
      mediaRestoreText: "Clearing it restores the default child-mode wallpaper; parent mode's Aerial wallpaper is unchanged.",
      mediaRestoreDone: "Default child-mode wallpaper restored",
      mediaDeleteLocal: "Delete local file",
      mediaDeleteUpload: "Delete upload",
      mediaDeleteTitle: "Delete this wallpaper?",
      mediaDeleteText: "The local copy will be deleted; any role using it returns to the default child-mode wallpaper.",
      mediaDeleted: "Wallpaper deleted",
      mediaSelected: "Wallpaper settings updated",
      mediaDownloadStarted: "Download started",
      mediaDownloading: "Downloading…",
      mediaCancelled: "Download cancelled",
      mediaNotFound: "The wallpaper is missing or no longer available",
      mediaNotReady: "The wallpaper has not finished downloading",
      mediaBusy: "Another wallpaper is downloading; try again shortly",
      mediaRoleInvalid: "This wallpaper cannot be used for the selected role",
      mediaNoSpace: "The TV is low on storage",
      mediaDeleteFailed: "Delete failed; try again",
      mediaDownloadFailed: "Download failed; check the TV network and retry",
      mediaLocalEmpty: "No uploaded wallpapers yet",
      mediaSoundTitle: "Wallpaper sound",
      mediaSoundHint: "Master child-mode sound switch: background music takes priority and videos stay muted; parent mode stays silent",
      mediaSoundUnavailable: "Choose a video with sound or a background track to enable it",
      mediaSoundSaved: "Wallpaper sound updated",
      mediaLockSummary: "{count} items · images every {duration}",
      mediaLockPickerHint: "Plays in the order you tap; images hold for the interval, videos play to the end",
      mediaLockEmptyHint: "Nothing selected uses the default. Select two or more to rotate.",
      mediaLockSingleHint: "Select one more to start the rotation; tap again to remove.",
      mediaLockInterval: "Image hold",
      audioTitle: "Background music",
      audioHint: "Loops under every child-mode wallpaper and follows the Wallpaper sound switch; wallpaper videos are muted while it plays, and it pauses during a quiz or while a parent is working",
      audioNone: "No background music",
      audioNoneHint: "Only sound from wallpaper videos",
      audioUpload: "Upload audio",
      audioUploading: "Uploading audio…",
      audioUploaded: "Audio uploaded",
      audioSelected: "Background music updated",
      audioDeleted: "Audio deleted",
      audioDelete: "Delete",
      audioDeleteTitle: "Delete this audio?",
      audioDeleteText: "This cannot be undone; child mode will return to no background music.",
      audioMeta: "{size} MB · {duration}",
      audioBadFormat: "Only MP3, M4A/AAC and OGG audio files are supported.",
      audioTooLarge: "Audio is over 20 MB; compress it and try again.",
      audioBadDuration: "Audio must be 30 minutes or shorter.",
      audioUnsupported: "This TV cannot decode that audio codec.",
      audioNoSpace: "The TV is low on storage.",
      audioEmpty: "No audio uploaded yet",
      audioSourceCloud: "Cloud music",
      audioSourceLocal: "Local upload",
      audioSourceTabsLabel: "Music source",
      audioCloudEmpty: "No music in the cloud library yet",
      audioCatalogOff: "Cloud music is not configured in this build",
      audioCatalogUnavailable: "Cloud music is unavailable; downloaded tracks still play",
      audioRefresh: "Refresh music",
      audioRefreshing: "Refreshing…",
      audioDownloadUse: "Download and use",
      audioUse: "Use",
      audioDownload: "Download",
      audioRetry: "Retry",
      audioUpdate: "Update",
      audioReady: "Downloaded",
      audioNotDownloaded: "Not downloaded",
      audioStateDownloading: "Downloading {percent}%",
      audioStateVerifying: "Verifying…",
      audioStateFailed: "Download failed",
      audioStateUpdate: "New version available",
      audioDownloadStarted: "Download started; it will become the background music when ready",
      audioDownloadDone: "Downloaded and set as background music",
      audioDownloaded: "Music downloaded",
      audioDownloadCancelled: "Download cancelled",
      audioDownloadFailed: "Download failed; check the TV network and retry",
      audioBusy: "Another track is downloading; try again shortly",
      audioDeleteDownload: "Delete local copy",
      audioDeleteDownloadTitle: "Delete the downloaded music?",
      audioDeleteDownloadText: "Only the copy on the TV is removed; you can download it again from the cloud library. If it is in use, background music returns to none.",
      audioDownloadDeleted: "Local copy deleted",
      mediaIntervalSeconds: "{n} sec",
      mediaIntervalMinutes: "{n} min",
      mediaLockLimitHint: "The wallpaper limit is reached; remove one first",
      mediaLockInvalid: "The rotation settings are invalid; select again",
      mediaSave: "Save",
      mediaSaveCount: "Save ({n})",
      mediaUseDefault: "Use the default",
      mediaPlaylistSaved: "Wallpaper rotation updated",
      mediaOnlyWallpaper: "Wallpaper only",
      mediaHasAudio: "Sound",
      mediaTimingLead: "Single-session timer",
      mediaSingleTimingNote: "follows the child-mode wallpaper",
      mediaReadonly: "Read-only",
      mediaImageTooLarge: "The image is over 12 MB; compress it and try again.",
      mediaImageBadFormat: "Only JPG/PNG/WebP images are supported.",
      mediaImageInvalid: "The image cannot be read or is unsupported.",
      mediaBadFormat: "Unsupported file format.",
      mediaTooLarge: "The file is too large.",
      remoteQrAlt: "Remote management binding QR code",
      featureUnavailable: "This build does not enable the web parent center",
      openFailed: "The parent center is unavailable right now"
    },
    ru: {
      parentCenterTitle: "Центр родителей",
      moreTitle: "Ещё",
      familyGroup: "Семья",
      familyMemberCount: "Участников: {n}",
      familyActiveOnTv: "Сейчас на ТВ",
      familyAddChild: "Добавить ребёнка",
      familyAddChildDisabled: "Сейчас добавить участника нельзя.",
      familySingleNote: "Нажмите на участника, чтобы изменить имя и аватар; телевизор учитывает настройки только текущего участника.",
      memberAddAction: "Добавить",
      memberNewLabel: "Новый участник",
      memberAdded: "Участник добавлен",
      memberAddFailed: "Не удалось добавить участника. Повторите попытку.",
      familyCloudReadOnly: "Привязано к облаку. Управляйте участниками на телефоне или в облаке.",
      familyLocalOnly: "Профиль хранится на этом телевизоре и не влияет на сегодняшний расход и прогресс викторины.",
      memberEditTitle: "Изменить участника",
      memberNameLabel: "Имя",
      memberNamePlaceholder: "Например: Ребёнок 1",
      memberAvatarLabel: "Аватар",
      memberUploadPhoto: "Загрузить фото",
      memberChangePhoto: "Заменить фото",
      memberRemovePhoto: "Удалить фото",
      memberSaveAction: "Сохранить",
      memberSaved: "Профиль участника сохранён",
      memberDeleteAction: "Удалить участника",
      memberDeleteTitle: "Удалить участника?",
      memberDeleteText: "Будут удалены профиль, настройки и фото «{name}». Это нельзя отменить. Текущего участника и участника по умолчанию удалить нельзя.",
      memberDeleted: "Участник удалён",
      memberDeleteFailed: "Не удалось удалить участника. Повторите попытку.",
      memberDeleteLast: "Должен остаться хотя бы один участник",
      familyDeleteActiveHint: "Перед удалением переключите ТВ на другого участника.",
      familyDeleteDefaultHint: "Участник по умолчанию хранит исторические данные этого ТВ и не может быть удалён; измените имя или аватар.",
      memberNameInvalid: "Введите имя не длиннее 12 символов",
      memberImageType: "Выберите файл изображения",
      memberImageTooLarge: "Изображение должно быть не больше 25 МБ",
      memberImageInvalid: "Не удалось прочитать изображение. Попробуйте другое.",
      memberImageSaveFailed: "Не удалось сохранить фото. Повторите попытку.",
      avatarCropTitle: "Настройка аватара",
      avatarCropHint: "Перетащите фото, чтобы выбрать кадр; масштаб — жестом или ползунком",
      avatarCropZoom: "Масштаб",
      avatarCropApply: "Использовать фото",
      avatarCropProcessing: "Обработка фото…",
      avatarCropFailed: "Это фото не подходит для аватара. Попробуйте другое.",
      memberNotFound: "Участник не найден",
      familyNotReady: "Профили участников пока недоступны",
      avatarRabbit: "Кролик",
      avatarFox: "Лиса",
      avatarBear: "Медведь",
      avatarPanda: "Панда",
      memberSourceLocal: "Этот ТВ",
      memberSourceCloud: "Облако",
      timeAndApps: "План времени развлечений",
      timeAndAppsSummary: "{groups} групп дней · {apps}",
      timeTabManage: "Приложения",
      timeTabPlan: "План времени",
      timeTabsLabel: "Разделы плана времени развлечений",
      day1: "Пн",
      day2: "Вт",
      day3: "Ср",
      day4: "Чт",
      day5: "Пт",
      day6: "Сб",
      day7: "Вс",
      daySeparator: ", ",
      groupWeekdays: "Пн–Пт",
      groupWeekend: "Сб–Вс",
      addGroup: "Добавить группу",
      addGroupTitle: "Перенести день в новую группу",
      addGroupHint: "Новая группа скопирует интервалы и лимиты текущей; затем их можно изменить отдельно.",
      deleteGroup: "Удалить группу",
      deleteGroupTitle: "Удалить эту группу?",
      deleteGroupBody: "Её дни перейдут в соседнюю группу.",
      dayGroupsSection: "Группы дней",
      publicParams: "Общие параметры",
      dayAssignment: "Распределение дней",
      dayAssignmentHint: "Нажмите день, чтобы перенести его в выбранную группу.",
      segmentHint: "Нажмите день, чтобы начать новый сегмент; нажмите начало сегмента, чтобы объединить его с предыдущим.",
      segmentSplitHint: "Начать новый сегмент здесь",
      segmentMergeHint: "Объединить с предыдущим",
      segmentFirstHint: "Здесь начинается первый сегмент",
      segmentNonContiguous: "В сохранённых группах есть несмежные дни; они показаны как отдельные смежные сегменты. Правила не изменены.",
      presetWholeWeek: "Вся неделя",
      presetEveryDay: "Каждый день",
      studyShort: "Учёба",
      entertainmentShort: "Развлечения",
      mergeSegmentsTitle: "Объединить сегменты?",
      mergeSegmentsBody: "Объединённый сегмент получит настройки левого; настройки остальных сегментов будут удалены.",
      mergeSegmentsAction: "Объединить",
      studySchedule: "Учебное время",
      entertainmentSchedule: "Время развлечений",
      studyLimit: "Учебный лимит",
      entertainmentLimit: "Лимит развлечений",
      unlimited: "Без ограничений",
      unlimitedApps: "Приложения без ограничений",
      unlimitedAppsHint: "Не расходуют учебное или развлекательное время и не зависят от интервалов",
      unlimitedNone: "Приложения без ограничений не заданы",
      unlimitedEmpty: "Приложения без ограничений не заданы",
      selectAllApps: "Выбрать все",
      clearAllApps: "Снять выбор",
      unlimitedAllWarning: "Все доступные приложения стали без ограничений; план времени развлечений больше не будет их ограничивать.",
      unlimitedManageHint: "Нажмите любой значок, чтобы управлять",
      appIconHint: "Нажмите значок или +, чтобы выбрать приложения",
      appPickerManage: "Управление приложениями",
      studyEarnEmptyHint: "Добавьте учебные приложения, которые ребёнок не открывает сам",
      moveToUnlimitedConfirm: "После добавления в без ограничений приложение будет убрано из учёбы с наградой.",
      moveToUnlimitedAction: "Всё равно добавить",
      moveToStudyConfirm: "После добавления в учебные приложение будет убрано из без ограничений.",
      studyAppsHint: "Ограничены учебными интервалами и лимитом",
      appPickerUnlimited: "Приложения без ограничений",
      appPickerUnlimitedHint: "Эти приложения не расходуют учебное или развлекательное время",
      appPickerTakenStudy: "Учебное приложение",
      appPickerTakenUnlimited: "Без ограничений",
      restAndBuffer: "Общие параметры",
      onlyUnlimitedNoTimer: "В области только приложения без ограничений; таймер не нужен.",
      rulesDayGroups: "Группы должны покрывать все семь дней, а интервалы и лимиты должны быть корректны",
      rulesUnlimitedApps: "Список приложений без ограничений неверен; выберите их снова",
      rulesClientOutdated: "Страница устарела. Обновите её и повторите.",
      localSettings: "Этот телевизор",
      pinSet: "Задан",
      pinUnset: "Не задан",
      quizAndAi: "Банк вопросов и ИИ",
      otherOptions: "Другое",
      privacyHint: "Данные и разрешения на этом телевизоре",
      aiTitle: "ИИ-вопросы",
      privacyInfo: "Конфиденциальность",
      downloadParentApp: "Скачать приложение родителя",
      downloadParentAppHint: "Android 7.0 и выше",
      viewAction: "Открыть",
      manageAction: "Управлять",
      logoutAccount: "Выйти",
      timePageHint: "Выберите, какие приложения требуют времени развлечений, как ребёнок его получает и когда оно доступно. Приложения развлечений и общие параметры действуют всю неделю и сразу; каждая группа дней задаёт свои интервалы и лимиты, они применяются со следующего сеанса.",
      tvInputSources: "Источники ТВ",
      tvInputSourcesHint: "Влияет только на входы на главном экране, это не жёсткая блокировка",
      entryChecking: "Чтение состояния телевизора…",
      entryPair: "Выключен · нужно сопряжение на ТВ",
      entryNotReady: "Телевизор ещё не готов",
      entryLocalUnlock: "Локальный режим · нужно временное разблокирование",
      entryLocalVerify: "Локальный режим · нужен пароль родителя",
      entryLocalReady: "Локальный режим · можно редактировать",
      entryLocalBlocked: "Локальный режим · вход сейчас недоступен",
      entryCloudUnlock: "Облачный режим · разблокируйте на ТВ",
      entryCloudVerify: "Облачный режим · нужен пароль родителя",
      entryCloudReady: "Облачный режим · можно редактировать",
      back: "Назад",
      loading: "Загрузка…",
      retry: "Повторить",
      retryIn: "Повторите через {n} с",
      gateLockedTitle: "Центр родителей заблокирован",
      gateLockedLead: "Используется тот же замок, что и в центре родителей на ТВ. Временно разблокируйте телевизор с экрана блокировки и продолжите здесь.",
      gateUnlockButton: "Разблокировать по паролю",
      gatePairTitle: "Сначала сопрягите на ТВ",
      gatePairLead: "Детский режим выключен, поэтому пароля нет. В центре родителей на ТВ выберите «Настроить на телефоне» и введите здесь 8-значный код.",
      pairCodeLabel: "8-значный код сопряжения",
      pairSubmit: "Подключиться",
      gateVerifyTitle: "Проверьте пароль родителя",
      gateVerifyLead: "Телевизор только что временно разблокирован. Введите тот же пароль.",
      pinLabel: "6-значный пароль родителя",
      pinSubmit: "Продолжить",
      pinCancel: "Отмена",
      gateBlockedTitle: "Центр родителей недоступен",
      gateNotReadyTitle: "Телевизор не готов",
      gateNotReadyLead: "Повторите позже. Центр откроется после загрузки политики ТВ.",
      gateQuizLogin: "Этот телефон не вошёл в страницу тестов. Вернитесь, войдите и снова откройте центр родителей.",
      gateQuizLoginButton: "Войти в тесты",
      gateWebEditing: "Другой телефон редактирует центр. Закройте ту страницу или подождите 20 секунд.",
      gateTakenOver: "Другой телефон забрал редактирование. Страница только для чтения.",
      gateTakenOverHint: "После перехвата другой телефон сразу выйдет из редактора; этот телефон станет единственным редактором.",
      gateTakeoverButton: "Всё равно перехватить",
      leaveTitle: "Выйти из центра родителей?",
      leaveText: "Право редактирования освободится сразу, и другой телефон сможет продолжить.",
      leaveConfirm: "Выйти",
      cancel: "Отмена",
      childMode: "Детский режим",
      childOn: "Включён",
      childOff: "Выключен",
      localMode: "Локальный режим",
      remoteMode: "Облачный режим",
      todayUsed: "Сегодня использовано {n} мин",
      localRules: "Локальные правила",
      cloudRules: "Облачные правила · только чтение",
      appScope: "Развлечения",
      entertainmentApps: "Развлечения",
      studyApps: "Учебные приложения",
      allApps: "Все приложения",
      appsSelected: "Выбрано приложений: {n}",
      noEntertainment: "Ничего не выбрано (развлечения запрещены)",
      studyNone: "Не задано (учебного режима нет)",
      watchMethods: "Условия запуска развлечений",
      quizGateSwitchHint: "Сначала тест: его результат определяет длительность сеанса",
      studyEarnSwitchHint: "Учебные приложения дают время на развлечения по заданной пропорции; при выключении учёба тоже приостанавливается",
      directStartTitle: "Прямой запуск",
      directStartSwitchHint: "Без теста и учёбы: нажмите приложение развлечений и смотрите",
      directDuration: "Время за сеанс",
      directPauseNote: "Три способа просмотра независимы и могут быть включены одновременно. Каждая сессия по-прежнему ограничена квотой, интервалом текущей группы дней и лимитом времени одной сессии.",
      unifiedLimits: "Общие параметры",
      sessionCapTitle: "Лимит времени одной сессии",
      sessionCapHint: "Верхний предел одной сессии для любого из трёх способов; дневная квота задаётся в группе дней.",
      directMaxHint: "Время за один просмотр, не более {n} мин",
      quizAutoLength: "Рассчитывается по результату теста, до {n} мин за сессию",
      routeDailyCount: "Раз в день",
      countTimes: "{n} раз",
      studyRewardMaxHint: "Ограничено только общим лимитом, не более {n} мин; время учёбы не влияет",
      allRoutesOffTitle: "Отключить все способы просмотра?",
      allRoutesOffBody: "После сохранения у ребёнка не останется способа смотреть развлечения, а телевизор покажет «Нет доступного способа просмотра». Родительский доступ не меняется.",
      allRoutesOffConfirm: "Всё равно сохранить",
      routesOffHint: "Все три способа просмотра отключены.",
      capClampedNotice: "Скорректировано по общему лимиту: {n} мин",
      studyAppsRetained: "Выключено · сохранено учебных приложений: {n}; они вернутся после включения",
      manageStudyApps: "Управлять учебными приложениями",
      studyMovedHint: "Учебные приложения теперь в «Учёба в обмен на развлечения» выше; отдельной настройки больше нет.",
      studyEarnTitle: "Учёба в обмен на развлечения",
      studyEarnIntro: "Время в учебном приложении с включённой наградой даёт время на развлечения. Учёба всегда ограничена учебными интервалами и лимитом; при награде развлекательные интервал и лимит проверяются ещё раз. Учитывается только время, содержимое не отслеживается.",
      studyEarnRatio: "Учёба {study} мин → {reward} мин",
      studyEarnStudyMinutes: "Минуты учёбы",
      studyEarnRewardMinutes: "Минуты награды",
      studyEarnApps: "Учебные приложения (все дают награду)",
      studyEarnNoApps: "Учебных приложений пока нет.",
      studyEarnHint: "Все учебные приложения считаются по учебным интервалам и лимиту и дают награду. Выход из приложения и выключение экрана ставят таймер на паузу.",
      addStudyApps: "Добавить учебные приложения",
      studyEarnEmptyHint: "Добавьте приложения, которыми ребёнок не любит заниматься, и поощряйте его наградой.",
      studyEarnEnabledCount: "Включено: {n}",
      studyEarnDisabled: "Выключено",
      studyEarnInvalid: "Неверные настройки учёбы в обмен на развлечения",
      sourcesAndQuiz: "Источники и тесты",
      sourceEntries: "Пункты источников",
      sourceHint: "Меняет только пункты на главном экране, это не блокировка",
      quizChallenge: "Тесты",
      quizEntryVisible: "Показывать вход в тесты на экране блокировки",
      quizEntryHint: "Если включено, значок тестов показывается первым на экране блокировки. Выключение не мешает входить в тесты из приложений развлечений.",
      quizManage: "Управление тестами",
      quizManageHint: "Вопросы и настройки тестов — на странице тестов",
      quizPageQrTitle: "Сканируйте, чтобы открыть страницу тестов",
      quizPageQrLead: "Отсканируйте камерой телефона, чтобы открыть страницу тестов этого ТВ в той же сети",
      parentAppQrTitle: "Скачать приложение родителя",
      parentAppQrLead: "Отсканируйте, чтобы скачать официальное приложение, или откройте страницу загрузки в браузере",
      parentAppPromptLead: "Установите на телефон, чтобы проверять телевизор и управлять им удалённо, даже вне дома; Android 7.0 и новее.",
      parentAppPromptDownload: "Скачать",
      parentAppPromptLater: "Позже",
      parentAppDownloadLimit: "Дневной лимит загрузок исчерпан; повторите завтра",
      quizQrFailed: "Не удалось создать QR-код; повторите",
      unlockFineprint: "Разблокировка общая с телевизором: та же проверка родителя, что и на экране блокировки ТВ, действует здесь и снова блокируется через 10 минут.",
      pairHint: "Код сопряжения действует 5 минут. После 5 ошибок создайте новый код на ТВ.",
      passwordHint: "Для смены пароля родителя нужен текущий пароль; изменения применяются сразу.",
      pinLockedRetry: "Слишком много неверных паролей; повторите через {n} с",
      sensitivePinTitle: "Подтвердите пароль родителя",
      sensitivePinLeadRules: "Введите пароль родителя, чтобы изменить время и приложения",
      sensitivePinLeadTakeOver: "Введите пароль родителя, чтобы временно выйти из детского режима",
      sensitivePinLeadDelete: "Введите пароль родителя, чтобы удалить участника",
      sensitivePinDefault: "Сначала измените начальный пароль родителя на телевизоре",
      sensitivePinExpired: "Подтверждение истекло; введите пароль ещё раз",
      sensitivePinRequired: "Сначала подтвердите пароль родителя",
      confirmAutoUpdateOffTitle: "Отключить автообновление?",
      confirmAutoUpdateOffBody: "Телевизор перестанет автоматически получать новые версии; обновления нужно устанавливать вручную.",
      confirmAutoUpdateOffAction: "Отключить",
      remoteRefreshFailed: "Не удалось обновить удалённое состояние; проверьте сеть ТВ и повторите",
      timeRules: "Правила времени",
      nextSession: "Со следующего сеанса",
      weekday: "Будни",
      weekend: "Выходные",
      from: "Начало",
      to: "Конец",
      remove: "Удалить",
      addWindow: "+ Добавить период",
      noWindows: "Периоды не заданы",
      windowsFull: "Один период в день",
      limits: "Лимиты и перерывы",
      dailyLimit: "Дневной лимит",
      dailyLimitDay: "{day} · дневной лимит",
      rest: "Время перерыва",
      buffer: "Лимит паузы",
      minutes: "{n} мин",
      deviceSecurity: "Устройство и безопасность",
      parentPassword: "Пароль родителя",
      passwordDefault: "Используется пароль по умолчанию; смените его",
      passwordSyncPending: "Синхронизация с облаком",
      autoUpdate: "Автообновление",
      autoUpdateHint: "Обновлять автоматически при выходе новой версии",
      softwareUpdate: "Обновление ПО",
      softwareUpdateHint: "Версия телевизора и обновление при наличии новой",
      updateCurrent: "Текущая версия",
      updateNotChecked: "Ещё не проверялось",
      updateChecking: "Проверка обновлений…",
      updateLatest: "Установлена последняя версия",
      updateFound: "Доступна новая версия {version}",
      updateCheckFailed: "Не удалось проверить; убедитесь, что ТВ в сети",
      updateCheckAction: "Проверить обновления",
      updateInstallAction: "Скачать и установить",
      updateDownloading: "Загрузка {percent}%",
      updateWaiting: "Ожидание подтверждения системы…",
      updateOpened: "Открыт системный установщик; подтвердите на ТВ",
      updateNeedsPermission: "Разрешите установку приложений на ТВ и повторите",
      updateNeedsStoragePermission: "Разрешите доступ к памяти на ТВ и повторите",
      updateFailedDownload: "Ошибка загрузки; повторите позже",
      updateFailedBlocked: "Система отклонила установку; обновите вручную на ТВ",
      updateFailedNotOfficial: "Пакет не соответствует приложению; скачайте с официального сайта",
      updateFailedNotNewer: "Пакет не новее текущей версии; повторите позже",
      updateConfirmTitle: "Обновить телевизор?",
      updateConfirmText: "ТВ скачает и установит новую версию. Во время установки он может перезагрузиться, а просмотр прервётся.",
      updateConfirmAction: "Обновить",
      updateAutoReady: "Новая версия загружена и установится автоматически; можно установить сейчас",
      updateAutoDownloading: "Автообновление скачивает новую версию",
      updateAutoFailedDownload: "Автообновление не смогло скачать; обновите вручную",
      updateAutoFailedInstall: "Автоустановка не завершилась; обновите вручную",
      updateAutoUnsupported: "На этом ТВ нужно подтверждение вручную; автоустановка недоступна",
      updateAutoPermission: "ТВ пока не разрешает установку приложений; автообновление приостановлено",
      updateNotes: "Что нового",
      updateClose: "Закрыть",
      updateUnavailable: "Эта копия не может устанавливать обновления; скачайте с официального сайта",
      updateBusy: "Обновление уже выполняется; подождите",
      remoteManagement: "Удалённое управление",
      notBound: "Не привязано",
      bound: "Привязано",
      handoffPending: "Ожидается перенос правил",
      endSession: "Завершить текущий сеанс",
      endSessionHint: "Останавливает текущий сеанс",
      endSessionTitle: "Завершить текущий сеанс?",
      endSessionText: "Текущий сеанс ребёнка сразу закончится; следующий начнётся заново.",
      endSessionConfirm: "Завершить",
      saved: "Сохранено",
      savedNextSession: "Сохранено; начнёт действовать со следующего сеанса",
      savedAppScope: "Список приложений сохранён и действует сразу",
      savedQuiz: "Настройка тестов сохранена",
      savedSource: "Пункты источников сохранены",
      savedPassword: "Пароль родителя сохранён",
      savedPasswordPending: "Пароль сохранён; идёт синхронизация с облаком",
      savedAutoUpdate: "Настройка автообновления сохранена",
      savedSessionEnded: "Текущий сеанс завершён",
      childOnSaved: "Детский режим включён",
      childOffSaved: "Детский режим выключен",
      childEnableTitle: "Включить детский режим",
      childEnableLead: "Детский режим охраняет телевизор по вашим правилам времени. Сначала задайте пароль родителя.",
      childSetupPinTitle: "Задайте пароль родителя",
      childSetupPinLead: "Для первого включения нужен 6-значный пароль. Он хранится только на ТВ; запомните его.",
      newPin: "Новый 6-значный пароль",
      confirmPin: "Повторите пароль",
      currentPin: "Текущий пароль родителя",
      wrongPin: "Текущий пароль неверен",
      pinUnchanged: "Новый пароль должен отличаться от текущего",
      savePin: "Сохранить пароль",
      pinMismatch: "Пароли не совпадают",
      pinLength: "Введите 6 цифр",
      pinTooSimple: "Пароль слишком простой; выберите другой",
      recoveryTitle: "Сохраните код восстановления",
      recoveryText: "Код восстановления показывается один раз. Он нужен, если пароль забыт. Запишите его и не храните там, где ребёнок сможет увидеть.",
      recoveryDone: "Я сохранил",
      childOffTitle: "Выключить детский режим?",
      childOffText: "Текущий сеанс сразу завершится. Облачному режиму может понадобиться сеть для расчёта.",
      childOffConfirm: "Выключить детский режим",
      disablePinTitle: "Введите пароль родителя",
      disablePinLead: "Для выключения детского режима нужен пароль родителя.",
      appPickerEntertainment: "Развлечения",
      appPickerStudy: "Учебные приложения",
      appPickerSearch: "Поиск приложений",
      appPickerAll: "Все приложения",
      appPickerOnly: "Только выбранные",
      appPickerEmpty: "Нет подходящих приложений",
      appPickerHint: "Если ничего не выбрать, развлечения запрещены",
      appPickerStudyHint: "Если ничего не выбрать, учебного режима нет. Учебные приложения считаются по учебным интервалам и лимиту; награда настраивается в «Учёба в обмен на развлечения».",
      appPickerDone: "Готово",
      appPickerCancel: "Отмена",
      appPickerLoading: "Чтение списка приложений…",
      passwordSheetTitle: "Смена пароля родителя",
      remoteTitle: "Удалённое управление",
      remoteLead: "После привязки к облаку локальные правила загружаются, и эта страница становится только для чтения.",
      remoteConsentLead: "Привязка отправит правила, использование и номер устройства в ваше семейное облако. Подтвердите, что вы родитель этого ТВ.",
      remoteConsentButton: "Понятно, продолжить",
      remoteStart: "Создать QR-код привязки",
      remoteWaiting: "Ожидание сканирования телефоном…",
      remoteRetrying: "Повторное подключение…",
      remoteSuccess: "Привязка выполнена",
      remoteFailed: "Ошибка привязки: {reason}",
      remotePairCode: "Код привязки",
      remoteDeviceNo: "Номер ТВ",
      remoteStop: "Остановить ожидание",
      remoteRefresh: "Обновить состояние",
      remoteMigrateTitle: "Перенос правил",
      remoteMigrateLead: "Привязка завершена. После переноса локальных правил в облако местное редактирование станет только для чтения.",
      remoteMigrateButton: "Начать перенос",
      remoteMigrateWaiting: "Перенос…",
      remoteDone: "Правила хранятся в облаке. Здесь только сводка; отвязка — в приложении родителя.",
      remoteNoChild: "Сначала включите детский режим, затем привязывайте удалённое управление.",
      remoteSummary: "Сводка облачных правил",
      rulesRevisionLabel: "Версия правил",
      cloudConfigVersion: "Версия облачной конфигурации {n}",
      sessionActive: "Сеанс идёт",
      sessionIdle: "Сеанс не идёт",
      guardEnforcing: "Охрана",
      guardOverride: "Временно разблокировано",
      guardOther: "Без охраны",
      readOnlyCloud: "Управляется облаком; локальные правила только для чтения",
      remoteUnavailable: "Удалённый сервис на этом ТВ не включён",
      pinLocked: "Слишком много неверных паролей; повторите позже",
      network: "Нет связи с ТВ. Телефон и ТВ должны быть в одной сети",
      unknownError: "Не удалось выполнить действие; повторите",
      stateChanged: "Состояние ТВ изменилось; обновлено",
      quizTakenOver: "Другой телефон занял страницу тестов; войдите снова",
      editorLost: "Право редактирования истекло; откройте центр снова",
      rulesOverlap: "Периоды пересекаются; измените их",
      rulesTooMany: "Один период в день",
      rulesApps: "Список приложений неверен; выберите снова",
      rulesInvalid: "Правила времени неверны; проверьте",
      quizRunning: "Идёт тест; настройки тестов менять нельзя",
      pendingSwitch: "Есть незавершённое облачное переключение; завершите его на ТВ",
      cloudSettlement: "Для облачного расчёта нужна сеть; проверьте её и повторите",
      timeout: "ТВ не ответил вовремя; повторите",
      openQuizLogin: "Открыть вход в тесты",
      emptyValue: "—",
      guardLabel: "Охрана",
      configVersion: "Версия конфигурации",
      saveRetry: "Не сохранено; восстановлены последние настройки с ТВ",
      pairingExpired: "Код сопряжения истёк; создайте новый на ТВ",
      pairUnavailable: "ТВ ещё не создал код сопряжения",
      pairInvalid: "Неверный код сопряжения",
      verifyExpired: "Проверка истекла; введите пароль родителя снова",
      unlockRequired: "Сначала временно разблокируйте ТВ",
      quizLoginRequired: "Войдите на страницу тестов, затем откройте локальный центр",
      personalize: "Обои детского режима",
      personalizeHint: "Облачная видеотека, локальная загрузка и обои по умолчанию",
      introVideoTitle: "Видео начала теста",
      introVideoHint: "Хранится только на этом ТВ; не загружается в облако",
      introVideoLimit: "MP4/MOV · H.264/H.265 и др. (нужен декодер ТВ) · ≤128 МБ, без ограничения длины",
      introVideoBuiltin: "Встроенное вступление",
      introVideoActive: "Используется",
      introVideoSetActive: "Сделать вступлением теста",
      introVideoRestore: "Вернуть встроенное вступление",
      introVideoUpload: "Загрузить видео или изображение",
      introVideoUploading: "Загрузка…",
      introVideoEmpty: "Загруженных видео пока нет",
      introVideoDelete: "Удалить",
      introVideoDeleteTitle: "Удалить это видео?",
      introVideoDeleteText: "Отменить нельзя. Если оно используется, вернётся встроенное вступление.",
      introVideoSelected: "Видео выбрано для теста",
      introVideoDeleted: "Видео удалено",
      introVideoRestored: "Встроенное вступление восстановлено",
      introVideoUploaded: "Видео загружено. Нажмите «Сделать вступлением теста».",
      introVideoTooLarge: "Видео больше 128 МБ; сожмите его и повторите",
      introVideoBadDuration: "Вступление теста должно быть ≤15 секунд; это видео можно использовать как обои детского режима",
      introVideoBadFormat: "Поддерживаются только изображения JPG/PNG/WebP или видео MP4/MOV 16:9 с кодеком, который декодирует этот ТВ",
      introVideoInvalid: "Видео не читается или формат не поддерживается",
      introVideoNoSpace: "На ТВ недостаточно места",
      introVideoMeta: "{size} МБ · {seconds} с · {width}×{height}",
      introVideoListTitle: "Видеотека",
      introVideoUploadHint: "Можно выбрать несколько видео сразу. После загрузки нажмите «Использовать».",
      introVideoUploadProgress: "Загрузка {index}/{total}: {name} · {percent}%",
      introVideoBatchDone: "Загружено обоев: {added}",
      introVideoBatchFailed: "Не загружено элементов: {failed}. Причина: {reason}",
      introVideoUse: "Использовать",
      introVideoHevc: "Этот ТВ не может декодировать H.265/HEVC; преобразуйте видео в H.264 и повторите.",
      introVideoUnsupportedCodec: "Этот ТВ не может декодировать этот видеокодек; преобразуйте видео в H.264 и повторите.",
      mediaPageTitle: "Обои детского режима",
      mediaCurrentTitle: "Текущий выбор",
      mediaRoleQuiz: "Обои теста",
      mediaRoleLock: "Обои детского режима",
      mediaRoleBoth: "Оба",
      mediaRoleQuizHint: "Показывается при запуске теста",
      mediaRoleLockHint: "Показывается на экране блокировки детского режима и на рабочем столе самодисциплины; видео повторяется, звук можно включить",
      mediaRoleLabel: "Где использовать",
      mediaSourceDefault: "Обои детского режима по умолчанию",
      mediaSourceOfficial: "Облачная библиотека",
      mediaSourceLocal: "Локальная загрузка",
      mediaKindVideo: "Видео",
      mediaKindImage: "Изображение",
      mediaKindTabsLabel: "Тип обоев",
      mediaSourceTabsLabel: "Источник обоев",
      mediaMetaImage: "{size} МБ · {width}×{height}",
      mediaDefaultBadge: "По умолчанию",
      mediaImageRoleNote: "Изображения можно использовать только как обои детского режима.",
      mediaChoose: "Выбрать",
      mediaChange: "Заменить",
      mediaCustomized: "Настроено",
      mediaTabCloud: "Облако",
      mediaTabLocal: "Локальные",
      mediaEmpty: "В библиотеке пока нет обоев",
      mediaCatalogOff: "Облачная библиотека не настроена в этой сборке",
      mediaCatalogUnavailable: "Облачная библиотека недоступна. Локальная загрузка и обои детского режима по умолчанию по-прежнему работают.",
      mediaRefresh: "Обновить библиотеку",
      mediaRefreshing: "Обновление…",
      mediaLoading: "Чтение библиотеки ТВ…",
      mediaStateNotDownloaded: "Не загружено",
      mediaStateDownloading: "Загрузка {percent}%",
      mediaStateVerifying: "Проверка…",
      mediaStateReady: "Загружено",
      mediaStateLocalReady: "Готово",
      mediaStateFailed: "Ошибка загрузки",
      mediaStateUpdate: "Есть обновление",
      mediaMeta: "{duration} · {size} МБ · {width}×{height}",
      mediaDownload: "Скачать эти обои",
      mediaDownloadDone: "Загружено. Выберите в разделе «Текущий выбор».",
      mediaPickerTitle: "Выберите {module}",
      mediaPickerEmpty: "Загруженных обоев пока нет",
      mediaPickerEmptyVideo: "Пока нет загруженных видео.",
      mediaPickerGoLibrary: "Открыть облачную библиотеку",
      mediaCurrentBadge: "Текущий",
      mediaRetryDownload: "Повторить загрузку",
      mediaUpdateLocal: "Обновить локальную копию",
      mediaClose: "Закрыть",
      mediaBadgeOfficial: "Официальное",
      mediaBadgeLocal: "Локальное",
      mediaDownloadUse: "Скачать и использовать",
      mediaUse: "Использовать эти обои",
      mediaInUse: "Используется",
      mediaManage: "Управление",
      mediaCancelDownload: "Отменить загрузку",
      mediaRestoreDefault: "Вернуть обои детского режима по умолчанию",
      mediaRestoreTitle: "Вернуть обои детского режима по умолчанию?",
      mediaRestoreText: "После очистки используются обои детского режима по умолчанию; обои Aerial в родительском режиме не меняются.",
      mediaRestoreDone: "Обои детского режима по умолчанию восстановлены",
      mediaDeleteLocal: "Удалить локальный файл",
      mediaDeleteUpload: "Удалить загруженное",
      mediaDeleteTitle: "Удалить эти обои?",
      mediaDeleteText: "Локальная копия будет удалена; роли, использующие её, вернутся к обоям детского режима по умолчанию.",
      mediaDeleted: "Обои удалены",
      mediaSelected: "Настройки обоев обновлены",
      mediaDownloadStarted: "Загрузка началась",
      mediaDownloading: "Загрузка…",
      mediaCancelled: "Загрузка отменена",
      mediaNotFound: "Обои не найдены или больше недоступны",
      mediaNotReady: "Обои ещё не загружены",
      mediaBusy: "Загружаются другие обои; повторите позже",
      mediaRoleInvalid: "Обои нельзя использовать для выбранной роли",
      mediaNoSpace: "На ТВ недостаточно места",
      mediaDeleteFailed: "Не удалось удалить; повторите",
      mediaDownloadFailed: "Ошибка загрузки; проверьте сеть ТВ и повторите",
      mediaLocalEmpty: "Загруженных обоев пока нет",
      mediaSoundTitle: "Звук обоев",
      mediaSoundHint: "Общий звук детского режима: при наличии музыки она звучит вместо видео; в родительском режиме без звука",
      mediaSoundUnavailable: "Сначала выберите видео со звуком или фоновую музыку",
      mediaSoundSaved: "Настройка звука обоев обновлена",
      mediaLockSummary: "{count} элем. · изображения каждые {duration}",
      mediaLockPickerHint: "Порядок — порядок нажатий; изображения держатся заданное время, видео играет до конца",
      mediaLockEmptyHint: "Без выбора используются стандартные обои. Выберите несколько для смены.",
      mediaLockSingleHint: "Выберите ещё одни, чтобы включить смену; нажмите ещё раз, чтобы убрать.",
      mediaLockInterval: "Показ изображения",
      audioTitle: "Фоновая музыка",
      audioHint: "Играет под всеми обоями детского режима и подчиняется переключателю «Звук обоев»; пока звучит музыка, видео обоев без звука; пауза во время теста или работы родителя",
      audioNone: "Без фоновой музыки",
      audioNoneHint: "Только звук из видеообоев",
      audioUpload: "Загрузить аудио",
      audioUploading: "Загрузка аудио…",
      audioUploaded: "Аудио загружено",
      audioSelected: "Фоновая музыка обновлена",
      audioDeleted: "Аудио удалено",
      audioDelete: "Удалить",
      audioDeleteTitle: "Удалить это аудио?",
      audioDeleteText: "Это нельзя отменить; детский режим вернётся к работе без фоновой музыки.",
      audioMeta: "{size} МБ · {duration}",
      audioBadFormat: "Поддерживаются только MP3, M4A/AAC и OGG.",
      audioTooLarge: "Аудио больше 20 МБ; сожмите его и попробуйте снова.",
      audioBadDuration: "Длительность аудио — не более 30 минут.",
      audioUnsupported: "Этот телевизор не декодирует такой аудиокодек.",
      audioNoSpace: "На телевизоре недостаточно места.",
      audioEmpty: "Аудио ещё не загружено",
      audioSourceCloud: "Облачная музыка",
      audioSourceLocal: "Своя загрузка",
      audioSourceTabsLabel: "Источник музыки",
      audioCloudEmpty: "В облачной музыке пока нет треков",
      audioCatalogOff: "В этой сборке облачная музыка не настроена",
      audioCatalogUnavailable: "Облачная музыка недоступна; скачанные треки продолжают работать",
      audioRefresh: "Обновить музыку",
      audioRefreshing: "Обновление…",
      audioDownloadUse: "Скачать и использовать",
      audioUse: "Использовать",
      audioDownload: "Скачать",
      audioRetry: "Повторить",
      audioUpdate: "Обновить",
      audioReady: "Скачано",
      audioNotDownloaded: "Не скачано",
      audioStateDownloading: "Загрузка {percent}%",
      audioStateVerifying: "Проверка…",
      audioStateFailed: "Ошибка загрузки",
      audioStateUpdate: "Есть новая версия",
      audioDownloadStarted: "Загрузка началась; после проверки трек станет фоновой музыкой",
      audioDownloadDone: "Трек скачан и выбран фоновой музыкой",
      audioDownloaded: "Трек скачан",
      audioDownloadCancelled: "Загрузка отменена",
      audioDownloadFailed: "Ошибка загрузки; проверьте сеть ТВ и повторите",
      audioBusy: "Другой трек уже скачивается; попробуйте позже",
      audioDeleteDownload: "Удалить локальную копию",
      audioDeleteDownloadTitle: "Удалить скачанный трек?",
      audioDeleteDownloadText: "С телевизора удалится только копия; трек можно снова скачать из облачной музыки. Если он используется, фоновая музыка вернётся к «без музыки».",
      audioDownloadDeleted: "Локальная копия удалена",
      mediaIntervalSeconds: "{n} с",
      mediaIntervalMinutes: "{n} мин",
      mediaLockLimitHint: "Достигнут лимит обоев; сначала удалите одни из выбранных",
      mediaLockInvalid: "Неверные настройки смены; выберите заново",
      mediaSave: "Сохранить",
      mediaSaveCount: "Сохранить ({n})",
      mediaUseDefault: "Использовать стандартные",
      mediaPlaylistSaved: "Смена обоев обновлена",
      mediaOnlyWallpaper: "Только обои",
      mediaHasAudio: "Со звуком",
      mediaTimingLead: "Разовый таймер",
      mediaSingleTimingNote: "использует обои детского режима",
      mediaReadonly: "Только чтение",
      mediaImageTooLarge: "Изображение больше 12 МБ; сожмите его и повторите.",
      mediaImageBadFormat: "Поддерживаются только изображения JPG/PNG/WebP.",
      mediaImageInvalid: "Изображение не читается или формат не поддерживается.",
      mediaBadFormat: "Формат файла не поддерживается.",
      mediaTooLarge: "Файл слишком большой.",
      remoteQrAlt: "QR-код привязки удалённого управления",
      featureUnavailable: "В этой сборке веб-центр родителей не включён",
      openFailed: "Центр родителей сейчас недоступен"
    },
    uk: {
      parentCenterTitle: "Центр батьків",
      moreTitle: "Ще",
      familyGroup: "Сім’я",
      familyMemberCount: "Учасників: {n}",
      familyActiveOnTv: "Зараз на ТБ",
      familyAddChild: "Додати дитину",
      familyAddChildDisabled: "Зараз додати учасника неможливо.",
      familySingleNote: "Натисніть на учасника, щоб змінити ім’я та аватар; телевізор враховує налаштування лише поточного учасника.",
      memberAddAction: "Додати",
      memberNewLabel: "Новий учасник",
      memberAdded: "Учасника додано",
      memberAddFailed: "Не вдалося додати учасника. Спробуйте ще раз.",
      familyCloudReadOnly: "Прив’язано до хмари. Керуйте учасниками на телефоні або в хмарі.",
      familyLocalOnly: "Профіль зберігається на цьому телевізорі й не впливає на сьогоднішні витрати та прогрес вікторини.",
      memberEditTitle: "Змінити учасника",
      memberNameLabel: "Ім’я",
      memberNamePlaceholder: "Наприклад: Дитина 1",
      memberAvatarLabel: "Аватар",
      memberUploadPhoto: "Завантажити фото",
      memberChangePhoto: "Замінити фото",
      memberRemovePhoto: "Видалити фото",
      memberSaveAction: "Зберегти",
      memberSaved: "Профіль учасника збережено",
      memberDeleteAction: "Видалити учасника",
      memberDeleteTitle: "Видалити учасника?",
      memberDeleteText: "Буде видалено профіль, налаштування й фото «{name}». Це неможливо скасувати. Поточного учасника та учасника за замовчуванням видалити не можна.",
      memberDeleted: "Учасника видалено",
      memberDeleteFailed: "Не вдалося видалити учасника. Спробуйте ще раз.",
      memberDeleteLast: "Повинен залишитися хоча б один учасник",
      familyDeleteActiveHint: "Перед видаленням перемкніть ТБ на іншого учасника.",
      familyDeleteDefaultHint: "Учасник за замовчуванням зберігає історичні дані цього ТБ і не може бути видалений; змініть ім’я або аватар.",
      memberNameInvalid: "Введіть ім’я не довше 12 символів",
      memberImageType: "Виберіть файл зображення",
      memberImageTooLarge: "Зображення має бути не більше 25 МБ",
      memberImageInvalid: "Не вдалося прочитати зображення. Спробуйте інше.",
      memberImageSaveFailed: "Не вдалося зберегти фото. Спробуйте ще раз.",
      avatarCropTitle: "Налаштування аватара",
      avatarCropHint: "Перетягніть фото, щоб вибрати кадр; масштаб — жестом або повзунком",
      avatarCropZoom: "Масштаб",
      avatarCropApply: "Використати фото",
      avatarCropProcessing: "Обробка фото…",
      avatarCropFailed: "Це фото не підходить для аватара. Спробуйте інше.",
      memberNotFound: "Учасника не знайдено",
      familyNotReady: "Профілі учасників поки недоступні",
      avatarRabbit: "Кролик",
      avatarFox: "Лис",
      avatarBear: "Ведмідь",
      avatarPanda: "Панда",
      memberSourceLocal: "Цей ТБ",
      memberSourceCloud: "Хмара",
      timeAndApps: "План часу розваг",
      timeAndAppsSummary: "{groups} груп днів · {apps}",
      timeTabManage: "Застосунки",
      timeTabPlan: "План часу",
      timeTabsLabel: "Розділи плану часу розваг",
      day1: "Пн",
      day2: "Вт",
      day3: "Ср",
      day4: "Чт",
      day5: "Пт",
      day6: "Сб",
      day7: "Нд",
      daySeparator: ", ",
      groupWeekdays: "Пн–Пт",
      groupWeekend: "Сб–Нд",
      addGroup: "Додати групу",
      addGroupTitle: "Перенести день у нову групу",
      addGroupHint: "Нова група скопіює інтервали й ліміти поточної; потім їх можна змінити окремо.",
      deleteGroup: "Видалити групу",
      deleteGroupTitle: "Видалити цю групу?",
      deleteGroupBody: "Її дні перейдуть до сусідньої групи.",
      dayGroupsSection: "Групи днів",
      publicParams: "Спільні параметри",
      dayAssignment: "Розподіл днів",
      dayAssignmentHint: "Натисніть день, щоб перенести його до вибраної групи.",
      segmentHint: "Натисніть день, щоб почати новий сегмент; натисніть початок сегмента, щоб об'єднати його з попереднім.",
      segmentSplitHint: "Почати новий сегмент тут",
      segmentMergeHint: "Об'єднати з попереднім",
      segmentFirstHint: "Тут починається перший сегмент",
      segmentNonContiguous: "У збережених групах є несуміжні дні; їх показано як окремі суміжні сегменти. Правила не змінено.",
      presetWholeWeek: "Весь тиждень",
      presetEveryDay: "Щодня",
      studyShort: "Навчання",
      entertainmentShort: "Розваги",
      mergeSegmentsTitle: "Об'єднати сегменти?",
      mergeSegmentsBody: "Об'єднаний сегмент використовуватиме налаштування лівого; налаштування інших сегментів буде очищено.",
      mergeSegmentsAction: "Об'єднати",
      studySchedule: "Навчальний час",
      entertainmentSchedule: "Час розваг",
      studyLimit: "Навчальний ліміт",
      entertainmentLimit: "Ліміт розваг",
      unlimited: "Без обмежень",
      unlimitedApps: "Застосунки без обмежень",
      unlimitedAppsHint: "Не витрачають навчальний або розважальний час і не залежать від інтервалів",
      unlimitedNone: "Застосунки без обмежень не задано",
      unlimitedEmpty: "Застосунки без обмежень не задано",
      selectAllApps: "Вибрати всі",
      clearAllApps: "Зняти вибір",
      unlimitedAllWarning: "Усі доступні застосунки стали без обмежень; план часу розваг більше не обмежуватиме їх.",
      unlimitedManageHint: "Торкніться будь-якого значка, щоб керувати",
      appIconHint: "Торкніться значка або +, щоб вибрати застосунки",
      appPickerManage: "Керування застосунками",
      studyEarnEmptyHint: "Додайте навчальні застосунки, які дитина не відкриває сама",
      moveToUnlimitedConfirm: "Після додавання до без обмежень застосунок буде прибрано з навчання з нагородою.",
      moveToUnlimitedAction: "Усе одно додати",
      moveToStudyConfirm: "Після додавання до навчальних застосунок буде прибрано з без обмежень.",
      studyAppsHint: "Обмежені навчальними інтервалами й лімітом",
      appPickerUnlimited: "Застосунки без обмежень",
      appPickerUnlimitedHint: "Ці застосунки не витрачають навчальний або розважальний час",
      appPickerTakenStudy: "Навчальний застосунок",
      appPickerTakenUnlimited: "Без обмежень",
      restAndBuffer: "Спільні параметри",
      onlyUnlimitedNoTimer: "У зоні лише застосунки без обмежень; таймер не потрібен.",
      rulesDayGroups: "Групи мають покривати всі сім днів, а інтервали й ліміти мають бути коректними",
      rulesUnlimitedApps: "Список застосунків без обмежень некоректний; виберіть їх знову",
      rulesClientOutdated: "Сторінка застаріла. Оновіть її та повторіть.",
      localSettings: "Цей телевізор",
      pinSet: "Задано",
      pinUnset: "Не задано",
      quizAndAi: "Банк питань і ШІ",
      otherOptions: "Інше",
      privacyHint: "Дані та дозволи на цьому телевізорі",
      aiTitle: "ШІ-питання",
      privacyInfo: "Конфіденційність",
      downloadParentApp: "Завантажити застосунок батьків",
      downloadParentAppHint: "Android 7.0 і вище",
      viewAction: "Відкрити",
      manageAction: "Керувати",
      logoutAccount: "Вийти",
      timePageHint: "Виберіть, які застосунки потребують часу розваг, як дитина його отримує і коли він доступний. Застосунки розваг і спільні параметри діють увесь тиждень і одразу; кожна група днів задає свої інтервали й ліміти, вони застосовуються з наступного сеансу.",
      tvInputSources: "Джерела ТБ",
      tvInputSourcesHint: "Впливає лише на входи на головному екрані, це не жорстке блокування",
      entryChecking: "Читання стану телевізора…",
      entryPair: "Вимкнено · потрібне сполучення на ТБ",
      entryNotReady: "Телевізор ще не готовий",
      entryLocalUnlock: "Локальний режим · потрібне тимчасове розблокування",
      entryLocalVerify: "Локальний режим · потрібен пароль батьків",
      entryLocalReady: "Локальний режим · можна редагувати",
      entryLocalBlocked: "Локальний режим · вхід зараз недоступний",
      entryCloudUnlock: "Хмарний режим · розблокуйте на ТБ",
      entryCloudVerify: "Хмарний режим · потрібен пароль батьків",
      entryCloudReady: "Хмарний режим · можна редагувати",
      back: "Назад",
      loading: "Завантаження…",
      retry: "Повторити",
      retryIn: "Повторіть через {n} с",
      gateLockedTitle: "Центр батьків заблоковано",
      gateLockedLead: "Використовується той самий замок, що й у центрі батьків на ТБ. Тимчасово розблокуйте телевізор з екрана блокування та продовжте тут.",
      gateUnlockButton: "Розблокувати паролем",
      gatePairTitle: "Спочатку сполучіть на ТБ",
      gatePairLead: "Дитячий режим вимкнено, тому пароля немає. У центрі батьків на ТБ виберіть «Налаштувати на телефоні» й введіть тут 8-значний код.",
      pairCodeLabel: "8-значний код сполучення",
      pairSubmit: "Підключитися",
      gateVerifyTitle: "Перевірте пароль батьків",
      gateVerifyLead: "Телевізор щойно тимчасово розблоковано. Введіть той самий пароль.",
      pinLabel: "6-значний пароль батьків",
      pinSubmit: "Продовжити",
      pinCancel: "Скасувати",
      gateBlockedTitle: "Центр батьків недоступний",
      gateNotReadyTitle: "Телевізор не готовий",
      gateNotReadyLead: "Повторіть пізніше. Центр відкриється після завантаження політики ТБ.",
      gateQuizLogin: "Цей телефон не увійшов на сторінку тестів. Поверніться, увійдіть і знову відкрийте центр батьків.",
      gateQuizLoginButton: "Увійти в тести",
      gateWebEditing: "Інший телефон редагує центр. Закрийте ту сторінку або зачекайте 20 секунд.",
      gateTakenOver: "Інший телефон забрав редагування. Сторінка лише для читання.",
      gateTakenOverHint: "Після перехоплення інший телефон одразу вийде з редактора; цей телефон стане єдиним редактором.",
      gateTakeoverButton: "Усе одно перехопити",
      leaveTitle: "Вийти з центру батьків?",
      leaveText: "Право редагування звільниться одразу, і інший телефон зможе продовжити.",
      leaveConfirm: "Вийти",
      cancel: "Скасувати",
      childMode: "Дитячий режим",
      childOn: "Увімкнено",
      childOff: "Вимкнено",
      localMode: "Локальний режим",
      remoteMode: "Хмарний режим",
      todayUsed: "Сьогодні використано {n} хв",
      localRules: "Локальні правила",
      cloudRules: "Хмарні правила · лише читання",
      appScope: "Розваги",
      entertainmentApps: "Розваги",
      studyApps: "Навчальні застосунки",
      allApps: "Усі застосунки",
      appsSelected: "Вибрано застосунків: {n}",
      noEntertainment: "Нічого не вибрано (розваги заборонені)",
      studyNone: "Не задано (навчального режиму немає)",
      watchMethods: "Умови запуску розваг",
      quizGateSwitchHint: "Спочатку тест: його результат визначає тривалість сеансу",
      studyEarnSwitchHint: "Навчальні застосунки дають час на розваги за заданою пропорцією; після вимкнення навчання теж призупиняється",
      directStartTitle: "Прямий запуск",
      directStartSwitchHint: "Без тесту й навчання: торкніться застосунку розваг і дивіться",
      directDuration: "Час за сеанс",
      directPauseNote: "Три способи перегляду незалежні й можуть бути ввімкнені одночасно. Кожна сесія й далі обмежена квотою, інтервалом поточної групи днів і лімітом часу однієї сесії.",
      unifiedLimits: "Спільні параметри",
      sessionCapTitle: "Ліміт часу однієї сесії",
      sessionCapHint: "Верхня межа однієї сесії для будь-якого з трьох способів; денна квота задається в групі днів.",
      directMaxHint: "Час за один перегляд, не більше {n} хв",
      quizAutoLength: "Розраховується за результатом тесту, до {n} хв за сесію",
      routeDailyCount: "Разів на день",
      countTimes: "{n} разів",
      studyRewardMaxHint: "Обмежено лише спільним лімітом, не більше {n} хв; час навчання не впливає",
      allRoutesOffTitle: "Вимкнути всі способи перегляду?",
      allRoutesOffBody: "Після збереження дитина не матиме способу дивитися розваги, а телевізор покаже «Немає доступного способу перегляду». Батьківський доступ не змінюється.",
      allRoutesOffConfirm: "Все одно зберегти",
      routesOffHint: "Усі три способи перегляду вимкнено.",
      capClampedNotice: "Скориговано за спільним лімітом: {n} хв",
      studyAppsRetained: "Вимкнено · збережено навчальних застосунків: {n}; вони повернуться після ввімкнення",
      manageStudyApps: "Керувати навчальними застосунками",
      studyMovedHint: "Навчальні застосунки тепер у «Навчання в обмін на розваги» вище; окремого налаштування більше немає.",
      studyEarnTitle: "Навчання в обмін на розваги",
      studyEarnIntro: "Час у навчальному застосунку з увімкненою нагородою дає час на розваги. Навчання завжди обмежене навчальними інтервалами й лімітом; під час нагороди розважальні інтервал і ліміт перевіряються ще раз. Враховується лише час, вміст не відстежується.",
      studyEarnRatio: "Навчання {study} хв → {reward} хв",
      studyEarnStudyMinutes: "Хвилини навчання",
      studyEarnRewardMinutes: "Хвилини винагороди",
      studyEarnApps: "Навчальні застосунки (усі дають нагороду)",
      studyEarnNoApps: "Навчальних застосунків ще немає.",
      studyEarnHint: "Усі навчальні застосунки рахуються за навчальними інтервалами й лімітом та дають нагороду. Вихід із застосунку та вимкнення екрана ставлять таймер на паузу.",
      addStudyApps: "Додати навчальні застосунки",
      studyEarnEmptyHint: "Додайте застосунки, якими дитина не любить займатися, і заохочуйте її нагородою.",
      studyEarnEnabledCount: "Увімкнено: {n}",
      studyEarnDisabled: "Вимкнено",
      studyEarnInvalid: "Некоректні налаштування навчання в обмін на розваги",
      sourcesAndQuiz: "Джерела й тести",
      sourceEntries: "Пункти джерел",
      sourceHint: "Змінює лише пункти на головному екрані, це не блокування",
      quizChallenge: "Тести",
      quizEntryVisible: "Показувати вхід у тести на екрані блокування",
      quizEntryHint: "Якщо ввімкнено, значок тестів показується першим на екрані блокування. Вимкнення не заважає входити в тести із застосунків розваг.",
      quizManage: "Керування тестами",
      quizManageHint: "Питання й налаштування тестів — на сторінці тестів",
      quizPageQrTitle: "Скануйте, щоб відкрити сторінку тестів",
      quizPageQrLead: "Відскануйте камерою телефона, щоб відкрити сторінку тестів цього ТБ у тій самій мережі",
      parentAppQrTitle: "Завантажити застосунок батьків",
      parentAppQrLead: "Відскануйте, щоб завантажити офіційний застосунок, або відкрийте сторінку завантаження в браузері",
      parentAppPromptLead: "Встановіть на телефон, щоб перевіряти телевізор і керувати ним віддалено, навіть поза домом; Android 7.0 і новіше.",
      parentAppPromptDownload: "Завантажити",
      parentAppPromptLater: "Пізніше",
      parentAppDownloadLimit: "Денний ліміт завантажень вичерпано; повторіть завтра",
      quizQrFailed: "Не вдалося створити QR-код; повторіть",
      unlockFineprint: "Розблокування спільне з телевізором: та сама перевірка батьків, що й на екрані блокування ТБ, діє тут і знову блокується через 10 хвилин.",
      pairHint: "Код сполучення діє 5 хвилин. Після 5 помилок створіть новий код на ТБ.",
      passwordHint: "Для зміни пароля батьків потрібен поточний пароль; зміни застосовуються одразу.",
      pinLockedRetry: "Забагато неправильних паролів; повторіть через {n} с",
      sensitivePinTitle: "Підтвердьте пароль батьків",
      sensitivePinLeadRules: "Введіть пароль батьків, щоб змінити час і застосунки",
      sensitivePinLeadTakeOver: "Введіть пароль батьків, щоб тимчасово вийти з дитячого режиму",
      sensitivePinLeadDelete: "Введіть пароль батьків, щоб видалити учасника",
      sensitivePinDefault: "Спочатку змініть початковий пароль батьків на телевізорі",
      sensitivePinExpired: "Підтвердження минуло; введіть пароль ще раз",
      sensitivePinRequired: "Спочатку підтвердьте пароль батьків",
      confirmAutoUpdateOffTitle: "Вимкнути автооновлення?",
      confirmAutoUpdateOffBody: "Телевізор перестане автоматично отримувати нові версії; оновлення доведеться встановлювати вручну.",
      confirmAutoUpdateOffAction: "Вимкнути",
      remoteRefreshFailed: "Не вдалося оновити віддалений стан; перевірте мережу ТБ і повторіть",
      timeRules: "Правила часу",
      nextSession: "З наступного сеансу",
      weekday: "Будні",
      weekend: "Вихідні",
      from: "Початок",
      to: "Кінець",
      remove: "Видалити",
      addWindow: "+ Додати період",
      noWindows: "Періоди не задані",
      windowsFull: "Один інтервал на день",
      limits: "Ліміти й перерви",
      dailyLimit: "Денний ліміт",
      dailyLimitDay: "{day} · денний ліміт",
      rest: "Час перерви",
      buffer: "Ліміт паузи",
      minutes: "{n} хв",
      deviceSecurity: "Пристрій і безпека",
      parentPassword: "Пароль батьків",
      passwordDefault: "Використовується пароль за замовчуванням; змініть його",
      passwordSyncPending: "Синхронізація з хмарою",
      autoUpdate: "Автооновлення",
      autoUpdateHint: "Оновлювати автоматично, коли є нова версія",
      softwareUpdate: "Оновлення ПЗ",
      softwareUpdateHint: "Версія телевізора та оновлення, коли є нова",
      updateCurrent: "Поточна версія",
      updateNotChecked: "Ще не перевірено",
      updateChecking: "Перевірка оновлень…",
      updateLatest: "Установлено останню версію",
      updateFound: "Доступна нова версія {version}",
      updateCheckFailed: "Не вдалося перевірити; переконайтеся, що ТБ у мережі",
      updateCheckAction: "Перевірити оновлення",
      updateInstallAction: "Завантажити й установити",
      updateDownloading: "Завантаження {percent}%",
      updateWaiting: "Очікування підтвердження системи…",
      updateOpened: "Відкрито системний інсталятор; підтвердьте на ТБ",
      updateNeedsPermission: "Дозвольте встановлення програм на ТБ і повторіть",
      updateNeedsStoragePermission: "Дозвольте доступ до пам’яті на ТБ і повторіть",
      updateFailedDownload: "Помилка завантаження; повторіть пізніше",
      updateFailedBlocked: "Система відхилила встановлення; оновіть вручну на ТБ",
      updateFailedNotOfficial: "Пакет не відповідає програмі; завантажте з офіційного сайту",
      updateFailedNotNewer: "Пакет не новіший за поточну версію; повторіть пізніше",
      updateConfirmTitle: "Оновити телевізор?",
      updateConfirmText: "ТБ завантажить і встановить нову версію. Під час встановлення він може перезавантажитися, а перегляд перерветься.",
      updateConfirmAction: "Оновити",
      updateAutoReady: "Нову версію завантажено, її буде встановлено автоматично; можна встановити зараз",
      updateAutoDownloading: "Автооновлення завантажує нову версію",
      updateAutoFailedDownload: "Автооновлення не змогло завантажити; оновіть вручну",
      updateAutoFailedInstall: "Автовстановлення не завершилося; оновіть вручну",
      updateAutoUnsupported: "На цьому ТБ потрібне ручне підтвердження; автовстановлення недоступне",
      updateAutoPermission: "ТБ ще не дозволяє встановлення програм; автооновлення призупинено",
      updateNotes: "Що нового",
      updateClose: "Закрити",
      updateUnavailable: "Ця копія не може встановлювати оновлення; завантажте з офіційного сайту",
      updateBusy: "Оновлення вже виконується; зачекайте",
      remoteManagement: "Віддалене керування",
      notBound: "Не прив’язано",
      bound: "Прив’язано",
      handoffPending: "Очікується перенесення правил",
      endSession: "Завершити поточний сеанс",
      endSessionHint: "Зупиняє поточний сеанс",
      endSessionTitle: "Завершити поточний сеанс?",
      endSessionText: "Поточний сеанс дитини одразу закінчиться; наступний почнеться заново.",
      endSessionConfirm: "Завершити",
      saved: "Збережено",
      savedNextSession: "Збережено; почне діяти з наступного сеансу",
      savedAppScope: "Список застосунків збережено й діє одразу",
      savedQuiz: "Налаштування тестів збережено",
      savedSource: "Пункти джерел збережено",
      savedPassword: "Пароль батьків збережено",
      savedPasswordPending: "Пароль збережено; триває синхронізація з хмарою",
      savedAutoUpdate: "Налаштування автооновлення збережено",
      savedSessionEnded: "Поточний сеанс завершено",
      childOnSaved: "Дитячий режим увімкнено",
      childOffSaved: "Дитячий режим вимкнено",
      childEnableTitle: "Увімкнути дитячий режим",
      childEnableLead: "Дитячий режим охороняє телевізор за вашими правилами часу. Спершу задайте пароль батьків.",
      childSetupPinTitle: "Задайте пароль батьків",
      childSetupPinLead: "Для першого ввімкнення потрібен 6-значний пароль. Він зберігається лише на ТБ; запам’ятайте його.",
      newPin: "Новий 6-значний пароль",
      confirmPin: "Повторіть пароль",
      currentPin: "Поточний пароль батьків",
      wrongPin: "Поточний пароль неправильний",
      pinUnchanged: "Новий пароль має відрізнятися від поточного",
      savePin: "Зберегти пароль",
      pinMismatch: "Паролі не збігаються",
      pinLength: "Введіть 6 цифр",
      pinTooSimple: "Пароль занадто простий; виберіть інший",
      recoveryTitle: "Збережіть код відновлення",
      recoveryText: "Код відновлення показується один раз. Він потрібен, якщо пароль забуто. Запишіть його й не зберігайте там, де дитина зможе побачити.",
      recoveryDone: "Я зберіг",
      childOffTitle: "Вимкнути дитячий режим?",
      childOffText: "Поточний сеанс одразу завершиться. Хмарному режиму може знадобитися мережа для розрахунку.",
      childOffConfirm: "Вимкнути дитячий режим",
      disablePinTitle: "Введіть пароль батьків",
      disablePinLead: "Для вимкнення дитячого режиму потрібен пароль батьків.",
      appPickerEntertainment: "Розваги",
      appPickerStudy: "Навчальні застосунки",
      appPickerSearch: "Пошук застосунків",
      appPickerAll: "Усі застосунки",
      appPickerOnly: "Лише вибрані",
      appPickerEmpty: "Немає відповідних застосунків",
      appPickerHint: "Якщо нічого не вибрати, розваги заборонені",
      appPickerStudyHint: "Якщо нічого не вибрати, навчального режиму немає. Навчальні застосунки рахуються за навчальними інтервалами й лімітом; нагорода налаштовується в «Навчання в обмін на розваги».",
      appPickerDone: "Готово",
      appPickerCancel: "Скасувати",
      appPickerLoading: "Читання списку застосунків…",
      passwordSheetTitle: "Зміна пароля батьків",
      remoteTitle: "Віддалене керування",
      remoteLead: "Після прив’язки до хмари локальні правила завантажуються, і ця сторінка стає лише для читання.",
      remoteConsentLead: "Прив’язка надішле правила, використання й номер пристрою до вашої сімейної хмари. Підтвердьте, що ви батьки цього ТБ.",
      remoteConsentButton: "Зрозуміло, продовжити",
      remoteStart: "Створити QR-код прив’язки",
      remoteWaiting: "Очікування сканування телефоном…",
      remoteRetrying: "Повторне підключення…",
      remoteSuccess: "Прив’язку виконано",
      remoteFailed: "Помилка прив’язки: {reason}",
      remotePairCode: "Код прив’язки",
      remoteDeviceNo: "Номер ТБ",
      remoteStop: "Зупинити очікування",
      remoteRefresh: "Оновити стан",
      remoteMigrateTitle: "Перенесення правил",
      remoteMigrateLead: "Прив’язку завершено. Після перенесення локальних правил у хмару місцеве редагування стане лише для читання.",
      remoteMigrateButton: "Почати перенесення",
      remoteMigrateWaiting: "Перенесення…",
      remoteDone: "Правила зберігаються в хмарі. Тут лише підсумок; відв’язка — у застосунку батьків.",
      remoteNoChild: "Спочатку увімкніть дитячий режим, потім прив’язуйте віддалене керування.",
      remoteSummary: "Підсумок хмарних правил",
      rulesRevisionLabel: "Версія правил",
      cloudConfigVersion: "Версія хмарної конфігурації {n}",
      sessionActive: "Сеанс триває",
      sessionIdle: "Сеанс не триває",
      guardEnforcing: "Охорона",
      guardOverride: "Тимчасово розблоковано",
      guardOther: "Без охорони",
      readOnlyCloud: "Керується хмарою; локальні правила лише для читання",
      remoteUnavailable: "Віддалений сервіс на цьому ТБ не ввімкнено",
      pinLocked: "Забагато неправильних паролів; повторіть пізніше",
      network: "Немає зв’язку з ТБ. Телефон і ТБ мають бути в одній мережі",
      unknownError: "Не вдалося виконати дію; повторіть",
      stateChanged: "Стан ТБ змінився; оновлено",
      quizTakenOver: "Інший телефон зайняв сторінку тестів; увійдіть знову",
      editorLost: "Право редагування минуло; відкрийте центр знову",
      rulesOverlap: "Періоди перетинаються; змініть їх",
      rulesTooMany: "Один інтервал на день",
      rulesApps: "Список застосунків неправильний; виберіть знову",
      rulesInvalid: "Правила часу неправильні; перевірте",
      quizRunning: "Триває тест; налаштування тестів змінити не можна",
      pendingSwitch: "Є незавершене хмарне перемикання; завершіть його на ТБ",
      cloudSettlement: "Для хмарного розрахунку потрібна мережа; перевірте її й повторіть",
      timeout: "ТБ не відповів вчасно; повторіть",
      openQuizLogin: "Відкрити вхід у тести",
      emptyValue: "—",
      guardLabel: "Охорона",
      configVersion: "Версія конфігурації",
      saveRetry: "Не збережено; відновлено останні налаштування з ТБ",
      pairingExpired: "Код сполучення минув; створіть новий на ТБ",
      pairUnavailable: "ТБ ще не створив код сполучення",
      pairInvalid: "Неправильний код сполучення",
      verifyExpired: "Перевірка минула; введіть пароль батьків знову",
      unlockRequired: "Спочатку тимчасово розблокуйте ТБ",
      quizLoginRequired: "Увійдіть на сторінку тестів, потім відкрийте локальний центр",
      personalize: "Шпалери дитячого режиму",
      personalizeHint: "Хмарна відеотека, локальне завантаження та типові шпалери",
      introVideoTitle: "Відео початку тесту",
      introVideoHint: "Зберігається лише на цьому ТБ; не завантажується в хмару",
      introVideoLimit: "MP4/MOV · H.264/H.265 та ін. (потрібен декодер ТБ) · ≤128 МБ, без обмеження довжини",
      introVideoBuiltin: "Вбудований вступ",
      introVideoActive: "Використовується",
      introVideoSetActive: "Зробити вступом тесту",
      introVideoRestore: "Повернути вбудований вступ",
      introVideoUpload: "Завантажити відео або зображення",
      introVideoUploading: "Завантаження…",
      introVideoEmpty: "Завантажених відео ще немає",
      introVideoDelete: "Видалити",
      introVideoDeleteTitle: "Видалити це відео?",
      introVideoDeleteText: "Скасувати не можна. Якщо воно використовується, повернеться вбудований вступ.",
      introVideoSelected: "Відео вибрано для тесту",
      introVideoDeleted: "Відео видалено",
      introVideoRestored: "Вбудований вступ відновлено",
      introVideoUploaded: "Відео завантажено. Натисніть «Зробити вступом тесту».",
      introVideoTooLarge: "Відео більше 128 МБ; стисніть його й повторіть",
      introVideoBadDuration: "Вступ тесту має бути ≤15 секунд; це відео можна використати як шпалери дитячого режиму",
      introVideoBadFormat: "Підтримуються лише зображення JPG/PNG/WebP або відео MP4/MOV 16:9 з кодеком, який декодує цей ТБ",
      introVideoInvalid: "Відео не читається або формат не підтримується",
      introVideoNoSpace: "На ТБ недостатньо місця",
      introVideoMeta: "{size} МБ · {seconds} с · {width}×{height}",
      introVideoListTitle: "Відеотека",
      introVideoUploadHint: "Можна вибрати кілька відео одразу. Після завантаження натисніть «Використати».",
      introVideoUploadProgress: "Завантаження {index}/{total}: {name} · {percent}%",
      introVideoBatchDone: "Завантажено шпалер: {added}",
      introVideoBatchFailed: "Не завантажено елементів: {failed}. Причина: {reason}",
      introVideoUse: "Використати",
      introVideoHevc: "Цей ТБ не може декодувати H.265/HEVC; перетворіть відео на H.264 і повторіть.",
      introVideoUnsupportedCodec: "Цей ТБ не може декодувати цей відеокодек; перетворіть відео на H.264 і повторіть.",
      mediaPageTitle: "Шпалери дитячого режиму",
      mediaCurrentTitle: "Поточний вибір",
      mediaRoleQuiz: "Шпалери тесту",
      mediaRoleLock: "Шпалери дитячого режиму",
      mediaRoleBoth: "Обидва",
      mediaRoleQuizHint: "Показується на початку тесту",
      mediaRoleLockHint: "Показується на екрані блокування дитячого режиму та на робочому столі самодисципліни; відео повторюється, звук можна увімкнути",
      mediaRoleLabel: "Де використовувати",
      mediaSourceDefault: "Типові шпалери дитячого режиму",
      mediaSourceOfficial: "Хмарна бібліотека",
      mediaSourceLocal: "Локальне завантаження",
      mediaKindVideo: "Відео",
      mediaKindImage: "Зображення",
      mediaKindTabsLabel: "Тип шпалер",
      mediaSourceTabsLabel: "Джерело шпалер",
      mediaMetaImage: "{size} МБ · {width}×{height}",
      mediaDefaultBadge: "Типове",
      mediaImageRoleNote: "Зображення можна використовувати лише як шпалери дитячого режиму.",
      mediaChoose: "Вибрати",
      mediaChange: "Замінити",
      mediaCustomized: "Налаштовано",
      mediaTabCloud: "Хмара",
      mediaTabLocal: "Локальні",
      mediaEmpty: "У бібліотеці ще немає шпалер",
      mediaCatalogOff: "Хмарна бібліотека не налаштована в цій збірці",
      mediaCatalogUnavailable: "Хмарна бібліотека недоступна. Локальне завантаження й типові шпалери дитячого режиму працюють далі.",
      mediaRefresh: "Оновити бібліотеку",
      mediaRefreshing: "Оновлення…",
      mediaLoading: "Читання бібліотеки ТБ…",
      mediaStateNotDownloaded: "Не завантажено",
      mediaStateDownloading: "Завантаження {percent}%",
      mediaStateVerifying: "Перевірка…",
      mediaStateReady: "Завантажено",
      mediaStateLocalReady: "Готово",
      mediaStateFailed: "Помилка завантаження",
      mediaStateUpdate: "Є оновлення",
      mediaMeta: "{duration} · {size} МБ · {width}×{height}",
      mediaDownload: "Завантажити ці шпалери",
      mediaDownloadDone: "Завантажено. Виберіть у розділі «Поточний вибір».",
      mediaPickerTitle: "Виберіть {module}",
      mediaPickerEmpty: "Завантажених шпалер ще немає",
      mediaPickerEmptyVideo: "Ще немає завантажених відео.",
      mediaPickerGoLibrary: "Відкрити хмарну бібліотеку",
      mediaCurrentBadge: "Поточний",
      mediaRetryDownload: "Повторити завантаження",
      mediaUpdateLocal: "Оновити локальну копію",
      mediaClose: "Закрити",
      mediaBadgeOfficial: "Офіційне",
      mediaBadgeLocal: "Локальне",
      mediaDownloadUse: "Завантажити й використати",
      mediaUse: "Використати ці шпалери",
      mediaInUse: "Використовується",
      mediaManage: "Керування",
      mediaCancelDownload: "Скасувати завантаження",
      mediaRestoreDefault: "Повернути типові шпалери дитячого режиму",
      mediaRestoreTitle: "Повернути типові шпалери дитячого режиму?",
      mediaRestoreText: "Після очищення використовуються типові шпалери дитячого режиму; шпалери Aerial у батьківському режимі не змінюються.",
      mediaRestoreDone: "Типові шпалери дитячого режиму відновлено",
      mediaDeleteLocal: "Видалити локальний файл",
      mediaDeleteUpload: "Видалити завантажене",
      mediaDeleteTitle: "Видалити ці шпалери?",
      mediaDeleteText: "Локальну копію буде видалено; ролі, що її використовують, повернуться до типових шпалер дитячого режиму.",
      mediaDeleted: "Шпалери видалено",
      mediaSelected: "Налаштування шпалер оновлено",
      mediaDownloadStarted: "Завантаження почалося",
      mediaDownloading: "Завантаження…",
      mediaCancelled: "Завантаження скасовано",
      mediaNotFound: "Шпалери відсутні або більше недоступні",
      mediaNotReady: "Шпалери ще не завантажено",
      mediaBusy: "Завантажуються інші шпалери; повторіть пізніше",
      mediaRoleInvalid: "Шпалери не можна використати для вибраної ролі",
      mediaNoSpace: "На ТБ недостатньо місця",
      mediaDeleteFailed: "Не вдалося видалити; повторіть",
      mediaDownloadFailed: "Помилка завантаження; перевірте мережу ТБ і повторіть",
      mediaLocalEmpty: "Завантажених шпалер ще немає",
      mediaSoundTitle: "Звук шпалер",
      mediaSoundHint: "Загальний звук дитячого режиму: за наявності музики лунає вона, а відео без звуку; у батьківському режимі — без звуку",
      mediaSoundUnavailable: "Спершу виберіть відео зі звуком або фонову музику",
      mediaSoundSaved: "Налаштування звуку шпалер оновлено",
      mediaLockSummary: "{count} елем. · зображення кожні {duration}",
      mediaLockPickerHint: "Порядок — порядок натискань; зображення тримаються заданий час, відео грає до кінця",
      mediaLockEmptyHint: "Без вибору використовуються стандартні шпалери. Виберіть кілька для зміни.",
      mediaLockSingleHint: "Виберіть ще одні, щоб увімкнути зміну; натисніть ще раз, щоб прибрати.",
      mediaLockInterval: "Показ зображення",
      audioTitle: "Фонова музика",
      audioHint: "Грає під усіма шпалерами дитячого режиму та підпорядковується перемикачу «Звук шпалер»; поки лунає музика, відео шпалер без звуку; пауза під час тесту або роботи батька",
      audioNone: "Без фонової музики",
      audioNoneHint: "Лише звук із відеошпалер",
      audioUpload: "Завантажити аудіо",
      audioUploading: "Завантаження аудіо…",
      audioUploaded: "Аудіо завантажено",
      audioSelected: "Фонову музику оновлено",
      audioDeleted: "Аудіо видалено",
      audioDelete: "Видалити",
      audioDeleteTitle: "Видалити це аудіо?",
      audioDeleteText: "Це не можна скасувати; дитячий режим повернеться до роботи без фонової музики.",
      audioMeta: "{size} МБ · {duration}",
      audioBadFormat: "Підтримуються лише MP3, M4A/AAC та OGG.",
      audioTooLarge: "Аудіо більше 20 МБ; стисніть його та спробуйте знову.",
      audioBadDuration: "Тривалість аудіо — не більше 30 хвилин.",
      audioUnsupported: "Цей телевізор не декодує такий аудіокодек.",
      audioNoSpace: "На телевізорі недостатньо місця.",
      audioEmpty: "Аудіо ще не завантажено",
      audioSourceCloud: "Хмарна музика",
      audioSourceLocal: "Власне завантаження",
      audioSourceTabsLabel: "Джерело музики",
      audioCloudEmpty: "У хмарній музиці ще немає треків",
      audioCatalogOff: "У цій збірці хмарна музика не налаштована",
      audioCatalogUnavailable: "Хмарна музика недоступна; завантажені треки працюють далі",
      audioRefresh: "Оновити музику",
      audioRefreshing: "Оновлення…",
      audioDownloadUse: "Завантажити й використати",
      audioUse: "Використати",
      audioDownload: "Завантажити",
      audioRetry: "Повторити",
      audioUpdate: "Оновити",
      audioReady: "Завантажено",
      audioNotDownloaded: "Не завантажено",
      audioStateDownloading: "Завантаження {percent}%",
      audioStateVerifying: "Перевірка…",
      audioStateFailed: "Помилка завантаження",
      audioStateUpdate: "Є нова версія",
      audioDownloadStarted: "Завантаження почалося; після перевірки трек стане фоновою музикою",
      audioDownloadDone: "Трек завантажено й вибрано фоновою музикою",
      audioDownloaded: "Трек завантажено",
      audioDownloadCancelled: "Завантаження скасовано",
      audioDownloadFailed: "Помилка завантаження; перевірте мережу ТВ і повторіть",
      audioBusy: "Інший трек уже завантажується; спробуйте пізніше",
      audioDeleteDownload: "Видалити локальну копію",
      audioDeleteDownloadTitle: "Видалити завантажений трек?",
      audioDeleteDownloadText: "З телевізора видалиться лише копія; трек можна знову завантажити з хмарної музики. Якщо він використовується, фонова музика повернеться до «без музики».",
      audioDownloadDeleted: "Локальну копію видалено",
      mediaIntervalSeconds: "{n} с",
      mediaIntervalMinutes: "{n} хв",
      mediaLockLimitHint: "Досягнуто ліміту шпалер; спершу приберіть одні",
      mediaLockInvalid: "Неправильні налаштування зміни; виберіть знову",
      mediaSave: "Зберегти",
      mediaSaveCount: "Зберегти ({n})",
      mediaUseDefault: "Використовувати стандартні",
      mediaPlaylistSaved: "Зміну шпалер оновлено",
      mediaOnlyWallpaper: "Лише шпалери",
      mediaHasAudio: "Зі звуком",
      mediaTimingLead: "Разовий таймер",
      mediaSingleTimingNote: "використовує шпалери дитячого режиму",
      mediaReadonly: "Лише читання",
      mediaImageTooLarge: "Зображення більше 12 МБ; стисніть його й повторіть.",
      mediaImageBadFormat: "Підтримуються лише зображення JPG/PNG/WebP.",
      mediaImageInvalid: "Зображення не читається або формат не підтримується.",
      mediaBadFormat: "Формат файлу не підтримується.",
      mediaTooLarge: "Файл завеликий.",
      remoteQrAlt: "QR-код прив’язки віддаленого керування",
      featureUnavailable: "У цій збірці веб-центр батьків не ввімкнено",
      openFailed: "Центр батьків зараз недоступний"
    },
    kk: {
      parentCenterTitle: "Ата-ана орталығы",
      moreTitle: "Қосымша",
      familyGroup: "Отбасы",
      familyMemberCount: "Мүше саны: {n}",
      familyActiveOnTv: "Қазір ТВ-де",
      familyAddChild: "Бала қосу",
      familyAddChildDisabled: "Қазір мүше қосу мүмкін емес.",
      familySingleNote: "Аты мен аватарын өзгерту үшін мүшені түртіңіз; теледидар тек ағымдағы мүшенің параметрлерін ескереді.",
      memberAddAction: "Қосу",
      memberNewLabel: "Жаңа мүше",
      memberAdded: "Мүше қосылды",
      memberAddFailed: "Мүшені қосу мүмкін болмады. Қайталап көріңіз.",
      familyCloudReadOnly: "Бұлтқа байланған. Отбасы мүшелерін телефонда немесе бұлтта басқарыңыз.",
      familyLocalOnly: "Профиль осы теледидарда сақталады және бүгінгі пайдалану мен викторина прогресіне әсер етпейді.",
      memberEditTitle: "Мүшені өңдеу",
      memberNameLabel: "Аты",
      memberNamePlaceholder: "Мысалы: Бала 1",
      memberAvatarLabel: "Аватар",
      memberUploadPhoto: "Фото жүктеу",
      memberChangePhoto: "Фотоны ауыстыру",
      memberRemovePhoto: "Фотоны жою",
      memberSaveAction: "Сақтау",
      memberSaved: "Мүше профилі сақталды",
      memberDeleteAction: "Мүшені жою",
      memberDeleteTitle: "Мүшені жою керек пе?",
      memberDeleteText: "«{name}» мүшесінің профилі, параметрлері және фотосы жойылады. Бұны қайтару мүмкін емес. Ағымдағы және әдепкі мүшені жою мүмкін емес.",
      memberDeleted: "Мүше жойылды",
      memberDeleteFailed: "Мүшені жою мүмкін болмады. Қайталап көріңіз.",
      memberDeleteLast: "Кемінде бір мүше қалуы керек",
      familyDeleteActiveHint: "Жою алдында теледидарды басқа мүшеге ауыстырыңыз.",
      familyDeleteDefaultHint: "Әдепкі мүше осы теледидардың тарихи деректерін сақтайды және жойыла алмайды; атын немесе аватарын өзгертіңіз.",
      memberNameInvalid: "12 таңбадан аспайтын ат енгізіңіз",
      memberImageType: "Сурет файлын таңдаңыз",
      memberImageTooLarge: "Сурет 25 МБ-тан аспауы керек",
      memberImageInvalid: "Суретті оқу мүмкін болмады. Басқасын таңдап көріңіз.",
      memberImageSaveFailed: "Фотоны сақтау мүмкін болмады. Қайталап көріңіз.",
      avatarCropTitle: "Аватарды реттеу",
      avatarCropHint: "Фотоны сүйреп орнын таңдаңыз; масштабты қимылмен немесе сырғытпамен өзгертіңіз",
      avatarCropZoom: "Масштаб",
      avatarCropApply: "Фотоны қолдану",
      avatarCropProcessing: "Фото өңделуде…",
      avatarCropFailed: "Бұл фото аватарға жарамайды. Басқасын таңдап көріңіз.",
      memberNotFound: "Мүше табылмады",
      familyNotReady: "Мүше профильдері әзірге қолжетімсіз",
      avatarRabbit: "Қоян",
      avatarFox: "Түлкі",
      avatarBear: "Аю",
      avatarPanda: "Панда",
      memberSourceLocal: "Осы ТВ",
      memberSourceCloud: "Бұлт",
      timeAndApps: "Ойын-сауық уақытының жоспары",
      timeAndAppsSummary: "{groups} күн тобы · {apps}",
      timeTabManage: "Қолданбалар",
      timeTabPlan: "Уақыт жоспары",
      timeTabsLabel: "Ойын-сауық уақыты жоспарының бөлімдері",
      day1: "Дс",
      day2: "Сс",
      day3: "Ср",
      day4: "Бс",
      day5: "Жм",
      day6: "Сб",
      day7: "Жс",
      daySeparator: ", ",
      groupWeekdays: "Дс–Жм",
      groupWeekend: "Сб–Жс",
      addGroup: "Топ қосу",
      addGroupTitle: "Күнді жаңа топқа жылжыту",
      addGroupHint: "Жаңа топ ағымдағы топтың аралықтары мен шектеулерін көшіреді; кейін оларды бөлек өзгертуге болады.",
      deleteGroup: "Топты жою",
      deleteGroupTitle: "Осы топты жою керек пе?",
      deleteGroupBody: "Оның күндері көрші топқа өтеді.",
      dayGroupsSection: "Күн топтары",
      publicParams: "Жалпы параметрлер",
      dayAssignment: "Күндерді бөлу",
      dayAssignmentHint: "Күнді таңдалған топқа жылжыту үшін оны түртіңіз.",
      segmentHint: "Жаңа сегментті бастау үшін күнді түртіңіз; сегмент басын түртсеңіз, ол алдыңғы сегментке қосылады.",
      segmentSplitHint: "Осы жерден жаңа сегмент бастау",
      segmentMergeHint: "Алдыңғы сегментке қосу",
      segmentFirstHint: "Бірінші сегмент осы жерден басталады",
      segmentNonContiguous: "Сақталған топтарда іргелес емес күндер бар; олар жеке іргелес сегменттер ретінде көрсетіледі. Ережелер өзгерген жоқ.",
      presetWholeWeek: "Бүкіл апта",
      presetEveryDay: "Күн сайын",
      studyShort: "Оқу",
      entertainmentShort: "Ойын-сауық",
      mergeSegmentsTitle: "Сегменттерді қосу керек пе?",
      mergeSegmentsBody: "Қосылған сегмент сол жақтағы параметрлерді пайдаланады; қалған сегменттердің параметрлері жойылады.",
      mergeSegmentsAction: "Қосу",
      studySchedule: "Оқу уақыты",
      entertainmentSchedule: "Ойын-сауық уақыты",
      studyLimit: "Оқу шектеуі",
      entertainmentLimit: "Ойын-сауық шектеуі",
      unlimited: "Шектеусіз",
      unlimitedApps: "Шектеусіз қолданбалар",
      unlimitedAppsHint: "Оқу немесе ойын-сауық уақытын жұмсамайды және аралықтарға бағынбайды",
      unlimitedNone: "Шектеусіз қолданбалар орнатылмаған",
      unlimitedEmpty: "Шектеусіз қолданбалар орнатылмаған",
      selectAllApps: "Барлығын таңдау",
      clearAllApps: "Таңдауды алу",
      unlimitedAllWarning: "Барлық қолжетімді қолданба шектеусіз болды; ойын-сауық уақытының жоспары оларды енді шектемейді.",
      unlimitedManageHint: "Кез келген белгішені түртіп басқарыңыз",
      appIconHint: "Қолданбаларды таңдау үшін белгішені немесе + түртіңіз",
      appPickerManage: "Қолданбаларды басқару",
      studyEarnEmptyHint: "Бала өзі ашпайтын оқу қолданбаларын қосыңыз",
      moveToUnlimitedConfirm: "Шектеусізге қосқаннан кейін қолданба оқу → ойын-сауық тізімінен алынады.",
      moveToUnlimitedAction: "Бәрібір қосу",
      moveToStudyConfirm: "Оқу қолданбаларына қосқаннан кейін қолданба шектеусіздерден алынады.",
      studyAppsHint: "Оқу аралықтары мен шектеуіне бағынады",
      appPickerUnlimited: "Шектеусіз қолданбалар",
      appPickerUnlimitedHint: "Бұл қолданбалар оқу немесе ойын-сауық уақытын жұмсамайды",
      appPickerTakenStudy: "Оқу қолданбасы",
      appPickerTakenUnlimited: "Шектеусіз",
      restAndBuffer: "Жалпы параметрлер",
      onlyUnlimitedNoTimer: "Аумақта тек шектеусіз қолданбалар бар; таймер қажет емес.",
      rulesDayGroups: "Топтар жеті күнді толық қамтуы, аралықтар мен шектеулер жарамды болуы керек",
      rulesUnlimitedApps: "Шектеусіз қолданбалар тізімі жарамсыз; оларды қайта таңдаңыз",
      rulesClientOutdated: "Бет ескірген. Жаңартып, қайталаңыз.",
      localSettings: "Осы теледидар",
      pinSet: "Орнатылған",
      pinUnset: "Орнатылмаған",
      quizAndAi: "Сұрақ банкі және ИИ",
      otherOptions: "Басқа",
      privacyHint: "Осы теледидардағы деректер мен рұқсаттар",
      aiTitle: "AI сұрақтары",
      privacyInfo: "Құпиялық",
      downloadParentApp: "Ата-ана қолданбасын жүктеу",
      downloadParentAppHint: "Android 7.0 және одан жоғары",
      viewAction: "Ашу",
      manageAction: "Басқару",
      logoutAccount: "Шығу",
      timePageHint: "Қандай қолданбаларға ойын-сауық уақыты қажет, бала оны қалай алады және қашан қолжетімді екенін таңдаңыз. Ойын-сауық қолданбалары мен жалпы параметрлер бүкіл аптаға ортақ және бірден қолданылады; әр күн тобы өз аралықтары мен шектеулерін орнатады, олар келесі сеанстан бастап әсер етеді.",
      tvInputSources: "ТВ кірістері",
      tvInputSourcesHint: "Тек басты экрандағы кірістерге әсер етеді, бұл қатаң құлып емес",
      entryChecking: "Теледидар күйі оқылуда…",
      entryPair: "Өшірулі · ТВ-де жұптау керек",
      entryNotReady: "Теледидар әзір емес",
      entryLocalUnlock: "Жергілікті режим · уақытша ашу керек",
      entryLocalVerify: "Жергілікті режим · ата-ана құпиясөзі керек",
      entryLocalReady: "Жергілікті режим · өңдеуге болады",
      entryLocalBlocked: "Жергілікті режим · қазір кіру мүмкін емес",
      entryCloudUnlock: "Бұлттық режим · ТВ-де ашыңыз",
      entryCloudVerify: "Бұлттық режим · ата-ана құпиясөзі керек",
      entryCloudReady: "Бұлттық режим · өңдеуге болады",
      back: "Артқа",
      loading: "Жүктелуде…",
      retry: "Қайталау",
      retryIn: "{n} секундтан кейін қайталаңыз",
      gateLockedTitle: "Ата-ана орталығы құлыпталған",
      gateLockedLead: "Теледидардағы ата-ана орталығымен бір құлып қолданылады. Теледидарды құлып экранынан уақытша ашып, осында жалғастырыңыз.",
      gateUnlockButton: "Құпиясөзбен ашу",
      gatePairTitle: "Алдымен ТВ-де жұптаңыз",
      gatePairLead: "Балалар режимі өшірулі, сондықтан құпиясөз жоқ. ТВ-дегі ата-ана орталығында «Телефонда баптау» таңдап, ТВ-дегі 8 таңбалы кодты осында енгізіңіз.",
      pairCodeLabel: "8 таңбалы жұптау коды",
      pairSubmit: "Қосылу",
      gateVerifyTitle: "Ата-ана құпиясөзін тексеріңіз",
      gateVerifyLead: "Теледидар жаңа ғана уақытша ашылды. Сол құпиясөзді қайта енгізіңіз.",
      pinLabel: "6 таңбалы ата-ана құпиясөзі",
      pinSubmit: "Жалғастыру",
      pinCancel: "Болдырмау",
      gateBlockedTitle: "Ата-ана орталығы қолжетімсіз",
      gateNotReadyTitle: "Теледидар әзір емес",
      gateNotReadyLead: "Кейінірек қайталаңыз. Орталық ТВ саясаты жүктелгеннен кейін ашылады.",
      gateQuizLogin: "Бұл телефон тест бетіне кірмеген. Тест бетіне қайтып кіріп, ата-ана орталығын қайта ашыңыз.",
      gateQuizLoginButton: "Тестке кіру",
      gateWebEditing: "Басқа телефон орталықты өңдеп жатыр. Сол бетті жабыңыз немесе 20 секунд күтіңіз.",
      gateTakenOver: "Басқа телефон өңдеу құқығын қайтарды. Бет тек оқуға арналған.",
      gateTakenOverHint: "Басқа телефоннан басқарғаннан кейін ол бірден редактордан шығады; бұл телефон жалғыз редактор болады.",
      gateTakeoverButton: "Бәрібір басқаруға алу",
      leaveTitle: "Ата-ана орталығынан шығу керек пе?",
      leaveText: "Өңдеу құқығы бірден босатылады, басқа телефон жалғастыра алады.",
      leaveConfirm: "Шығу",
      cancel: "Болдырмау",
      childMode: "Балалар режимі",
      childOn: "Қосулы",
      childOff: "Өшірулі",
      localMode: "Жергілікті режим",
      remoteMode: "Бұлттық режим",
      todayUsed: "Бүгін {n} минут қолданылды",
      localRules: "Жергілікті ережелер",
      cloudRules: "Бұлттық ережелер · тек оқу",
      appScope: "Ойын-сауық қолданбалары",
      entertainmentApps: "Ойын-сауық қолданбалары",
      studyApps: "Оқу қолданбалары",
      allApps: "Барлық қолданбалар",
      appsSelected: "{n} қолданба таңдалды",
      noEntertainment: "Ештеңе таңдалмаған (ойын-сауыққа рұқсат жоқ)",
      studyNone: "Орнатылмаған (оқу режимі жоқ)",
      watchMethods: "Ойын-сауықты іске қосу шарттары",
      quizGateSwitchHint: "Алдымен тест: оның нәтижесі сеанс ұзақтығын анықтайды",
      studyEarnSwitchHint: "Оқу қолданбалары белгіленген пропорция бойынша ойын-сауық уақытын береді; өшіргенде оқу да тоқтатылады",
      directStartTitle: "Тікелей іске қосу",
      directStartSwitchHint: "Тестсіз және оқусыз: ойын-сауық қолданбасын басып, көріңіз",
      directDuration: "Бір сеанс уақыты",
      directPauseNote: "Үш көру жолы тәуелсіз және бірге қосулы бола алады. Әр сессия ағымдағы күн тобының ойын-сауық шектеуімен, аралығымен және бір сеанс уақытының шегімен шектеледі.",
      unifiedLimits: "Жалпы параметрлер",
      sessionCapTitle: "Бір сеанс уақытының шегі",
      sessionCapHint: "Кез келген үш жол үшін бір сеанстың жоғарғы шегі; күндік жалпы шек күн тобында орнатылады.",
      directMaxHint: "Бір көруге арналған уақыт, ең көбі {n} мин",
      quizAutoLength: "Тест нәтижесі бойынша есептеледі, сеанс үшін ең көбі {n} мин",
      routeDailyCount: "Күндік саны",
      countTimes: "{n} рет",
      studyRewardMaxHint: "Тек жалпы шекпен шектеледі, ең көбі {n} мин; оқу уақыты әсер етпейді",
      allRoutesOffTitle: "Барлық көру жолдарын өшіру керек пе?",
      allRoutesOffBody: "Сақтағаннан кейін баланың ойын-сауыққа кіретін жолы болмайды, теледидар «Қолжетімді көру жолы жоқ» деп көрсетеді. Ата-ананың уақытша ашуы өзгермейді.",
      allRoutesOffConfirm: "Бәрібір сақтау",
      routesOffHint: "Барлық үш көру жолы өшірулі.",
      capClampedNotice: "Жалпы шекке сәйкес реттелді: {n} мин",
      studyAppsRetained: "Өшірулі · {n} оқу қолданбасы сақталды; қосқанда қайта оралады",
      manageStudyApps: "Оқу қолданбаларын басқару",
      studyMovedHint: "Оқу қолданбалары енді жоғарыдағы «Оқуға уақыт бөліп, ойын уақытын алу» бөлімінде; жеке параметр жоқ.",
      studyEarnTitle: "Оқуға уақыт бөліп, ойын уақытын алу",
      studyEarnIntro: "Сыйлығы қосылған оқу қолданбасындағы уақыт ойын-сауық уақытын береді. Оқу әрдайым оқу аралықтарымен және шектеуімен шектеледі; сыйлық берілгенде ойын-сауық аралығы мен шектеуі қайта тексеріледі. Тек уақыт есептеледі, мазмұн бақыланбайды.",
      studyEarnRatio: "Оқу {study} мин → {reward} мин",
      studyEarnStudyMinutes: "Оқу минуттары",
      studyEarnRewardMinutes: "Сыйақы минуттары",
      studyEarnApps: "Оқу қолданбалары (барлығы сыйақы алады)",
      studyEarnNoApps: "Оқу қолданбалары әзірге жоқ.",
      studyEarnHint: "Барлық оқу қолданбалары оқу аралықтары мен шектеуі бойынша есептеледі және сыйақы алады. Қолданбадан шығу және экранды өшіру таймерді тоқтатады.",
      addStudyApps: "Оқу қолданбаларын қосу",
      studyEarnEmptyHint: "Бала құлықсыз қолданатын оқу қолданбаларын қосып, сыйақымен ынталандырыңыз.",
      studyEarnEnabledCount: "Қосылған: {n}",
      studyEarnDisabled: "Өшірулі",
      studyEarnInvalid: "Оқуға уақыт бөліп, ойын уақытын алу параметрлері жарамсыз",
      sourcesAndQuiz: "Дереккөздер мен тесттер",
      sourceEntries: "Дереккөз жазбалары",
      sourceHint: "Тек басты экрандағы жазбаларды өзгертеді, бұл құлып емес",
      quizChallenge: "Тесттер",
      quizEntryVisible: "Құлып экранында тест кірісін көрсету",
      quizEntryHint: "Қосулы болса, тест белгішесі құлып экранында бірінші көрсетіледі. Өшіру ойын-сауық қолданбаларынан тестке кіруге әсер етпейді.",
      quizManage: "Тесттерді басқару",
      quizManageHint: "Сұрақтар мен тест баптаулары тест бетінде",
      quizPageQrTitle: "Тест бетін ашу үшін сканерлеңіз",
      quizPageQrLead: "Осы теледидардың тест бетін бір желіде ашу үшін телефон камерасымен сканерлеңіз",
      parentAppQrTitle: "Ата-ана қолданбасын жүктеу",
      parentAppQrLead: "Ресми қолданбаны жүктеу үшін сканерлеңіз немесе телефон браузерінде жүктеу бетін ашыңыз",
      parentAppPromptLead: "Телефонға орнатып, үйде болмасаңыз да теледидарды қашықтан тексеріп, басқарыңыз; Android 7.0 және одан жаңа.",
      parentAppPromptDownload: "Жүктеу",
      parentAppPromptLater: "Кейінірек",
      parentAppDownloadLimit: "Күнделікті жүктеу шегіне жеттіңіз; ертең қайталаңыз",
      quizQrFailed: "QR код жасалмады; қайталаңыз",
      unlockFineprint: "Ашу теледидармен ортақ: ТВ құлып экранындағы ата-ана тексеруі осында да әсер етеді және 10 минуттан кейін қайта құлыпталады.",
      pairHint: "Жұптау коды 5 минут жарамды. 5 рет қате енгізсеңіз, ТВ-де жаңа код жасаңыз.",
      passwordHint: "Ата-ана құпиясөзін өзгерту үшін ағымдағы құпиясөз қажет; өзгеріс бірден әсер етеді.",
      pinLockedRetry: "Қате құпиясөз тым көп; {n} секундтан кейін қайталаңыз",
      sensitivePinTitle: "Ата-ана құпиясөзін растаңыз",
      sensitivePinLeadRules: "Уақыт пен қолданбаларды өңдеу үшін ата-ана құпиясөзін енгізіңіз",
      sensitivePinLeadTakeOver: "Бала режимінен уақытша шығу үшін ата-ана құпиясөзін енгізіңіз",
      sensitivePinLeadDelete: "Мүшені жою үшін ата-ана құпиясөзін енгізіңіз",
      sensitivePinDefault: "Алдымен теледидарда бастапқы ата-ана құпиясөзін өзгертіңіз",
      sensitivePinExpired: "Растау мерзімі бітті; құпиясөзді қайта енгізіңіз",
      sensitivePinRequired: "Алдымен ата-ана құпиясөзін растаңыз",
      confirmAutoUpdateOffTitle: "Автоматты жаңартуды өшіру керек пе?",
      confirmAutoUpdateOffBody: "Теледидар жаңа нұсқаларды автоматты түрде алмайды; жаңартуды қолмен орнату қажет.",
      confirmAutoUpdateOffAction: "Өшіру",
      remoteRefreshFailed: "Қашықтан күйді жаңарту мүмкін болмады; ТВ желісін тексеріп қайталаңыз",
      timeRules: "Уақыт ережелері",
      nextSession: "Келесі сеанстан бастап",
      weekday: "Жұмыс күндері",
      weekend: "Демалыс күндері",
      from: "Басталуы",
      to: "Аяқталуы",
      remove: "Жою",
      addWindow: "+ Аралық қосу",
      noWindows: "Аралықтар орнатылмаған",
      windowsFull: "Күніне бір уақыт аралығы",
      limits: "Шектеулер мен үзілістер",
      dailyLimit: "Күндік шектеу",
      dailyLimitDay: "{day} · күндік шектеу",
      rest: "Үзіліс уақыты",
      buffer: "Кідірту шегі",
      minutes: "{n} минут",
      deviceSecurity: "Құрылғы және қауіпсіздік",
      parentPassword: "Ата-ана құпиясөзі",
      passwordDefault: "Әдепкі құпиясөз қолданылуда; оны ауыстырыңыз",
      passwordSyncPending: "Бұлтпен синхрондалуда",
      autoUpdate: "Автоматты жаңарту",
      autoUpdateHint: "Жаңа нұсқа шыққанда автоматты түрде жаңарту",
      softwareUpdate: "Бағдарламалық жаңарту",
      softwareUpdateHint: "Теледидар нұсқасын көру және жаңа нұсқа болса жаңарту",
      updateCurrent: "Ағымдағы нұсқа",
      updateNotChecked: "Әзірге тексерілмеген",
      updateChecking: "Жаңартулар тексерілуде…",
      updateLatest: "Соңғы нұсқа орнатылған",
      updateFound: "Жаңа нұсқа қолжетімді: {version}",
      updateCheckFailed: "Тексеру сәтсіз; теледидардың желіде екенін растаңыз",
      updateCheckAction: "Жаңартуды тексеру",
      updateInstallAction: "Жүктеп, орнату",
      updateDownloading: "Жүктелуде {percent}%",
      updateWaiting: "Жүйенің растауы күтілуде…",
      updateOpened: "Жүйелік орнатушы ашылды; теледидарда растаңыз",
      updateNeedsPermission: "Теледидарда қолданба орнатуға рұқсат беріп, қайталаңыз",
      updateNeedsStoragePermission: "Теледидарда жадыға рұқсат беріп, қайталаңыз",
      updateFailedDownload: "Жүктеу сәтсіз; кейінірек қайталаңыз",
      updateFailedBlocked: "Жүйе орнатудан бас тартты; теледидарда қолмен жаңартыңыз",
      updateFailedNotOfficial: "Бума қолданбаға сәйкес емес; ресми сайттан жүктеңіз",
      updateFailedNotNewer: "Бума ағымдағы нұсқадан жаңа емес; кейінірек қайталаңыз",
      updateConfirmTitle: "Теледидарды жаңарту керек пе?",
      updateConfirmText: "Теледидар жаңа нұсқаны жүктеп орнатады. Орнату кезінде ол қайта іске қосылып, қазіргі көрініс үзілуі мүмкін.",
      updateConfirmAction: "Жаңарту",
      updateAutoReady: "Жаңа нұсқа жүктелді, автоматты түрде орнатылады; қазір де орнатуға болады",
      updateAutoDownloading: "Автоматты жаңарту жаңа нұсқаны жүктеуде",
      updateAutoFailedDownload: "Автоматты жаңарту жүктей алмады; қолмен жаңартыңыз",
      updateAutoFailedInstall: "Автоматты орнату аяқталмады; қолмен жаңартыңыз",
      updateAutoUnsupported: "Бұл теледидарда қолмен растау қажет; автоматты орнату қолжетімсіз",
      updateAutoPermission: "Теледидар әзірге қолданба орнатуға рұқсат бермейді; автоматты жаңарту тоқтатылды",
      updateNotes: "Не жаңарды",
      updateClose: "Жабу",
      updateUnavailable: "Бұл көшірме жаңартуларды орната алмайды; ресми сайттан жүктеңіз",
      updateBusy: "Жаңарту жүріп жатыр; күте тұрыңыз",
      remoteManagement: "Қашықтан басқару",
      notBound: "Байланбаған",
      bound: "Байланған",
      handoffPending: "Ережелерді көшіру күтілуде",
      endSession: "Ағымдағы сеансты аяқтау",
      endSessionHint: "Ағымдағы сеансты бірден тоқтатады",
      endSessionTitle: "Ағымдағы сеансты аяқтау керек пе?",
      endSessionText: "Баланың ағымдағы сеансы бірден аяқталады; келесісі қайтадан басталады.",
      endSessionConfirm: "Аяқтау",
      saved: "Сақталды",
      savedNextSession: "Сақталды; келесі сеанстан бастап қолданылады",
      savedAppScope: "Қолданбалар тізімі сақталды және бірден қолданылады",
      savedQuiz: "Тест баптауы сақталды",
      savedSource: "Дереккөз жазбалары сақталды",
      savedPassword: "Ата-ана құпиясөзі сақталды",
      savedPasswordPending: "Құпиясөз сақталды; бұлтпен синхрондалуда",
      savedAutoUpdate: "Автоматты жаңарту баптауы сақталды",
      savedSessionEnded: "Ағымдағы сеанс аяқталды",
      childOnSaved: "Балалар режимі қосылды",
      childOffSaved: "Балалар режимі өшірілді",
      childEnableTitle: "Балалар режимін қосу",
      childEnableLead: "Балалар режимі теледидарды сіздің уақыт ережелеріңізбен қорғайды. Алдымен ата-ана құпиясөзін орнатыңыз.",
      childSetupPinTitle: "Ата-ана құпиясөзін орнату",
      childSetupPinLead: "Алғаш қосу үшін 6 таңбалы құпиясөз керек. Ол тек ТВ-де сақталады; оны есте сақтаңыз.",
      newPin: "Жаңа 6 таңбалы құпиясөз",
      confirmPin: "Құпиясөзді қайталаңыз",
      currentPin: "Ағымдағы ата-ана құпиясөзі",
      wrongPin: "Ағымдағы құпиясөз дұрыс емес",
      pinUnchanged: "Жаңа құпиясөз ағымдағыдан өзгеше болуы керек",
      savePin: "Құпиясөзді сақтау",
      pinMismatch: "Құпиясөздер сәйкес емес",
      pinLength: "6 цифра енгізіңіз",
      pinTooSimple: "Құпиясөз тым қарапайым; басқасын таңдаңыз",
      recoveryTitle: "Қалпына келтіру кодын сақтаңыз",
      recoveryText: "Қалпына келтіру коды бір рет көрсетіледі. Құпиясөз ұмытылса, оны қолданыңыз. Жазып алыңыз және бала көретін жерде сақтамаңыз.",
      recoveryDone: "Мен сақтадым",
      childOffTitle: "Балалар режимін өшіру керек пе?",
      childOffText: "Ағымдағы сеанс бірден аяқталады. Бұлттық режимге есеп айырысу үшін желі керек болуы мүмкін.",
      childOffConfirm: "Балалар режимін өшіру",
      disablePinTitle: "Ата-ана құпиясөзін енгізіңіз",
      disablePinLead: "Балалар режимін өшіру үшін ата-ана құпиясөзі керек.",
      appPickerEntertainment: "Ойын-сауық қолданбалары",
      appPickerStudy: "Оқу қолданбалары",
      appPickerSearch: "Қолданба іздеу",
      appPickerAll: "Барлық қолданбалар",
      appPickerOnly: "Тек таңдалғандар",
      appPickerEmpty: "Сәйкес қолданба жоқ",
      appPickerHint: "Ештеңе таңдамасаңыз, ойын-сауыққа рұқсат жоқ",
      appPickerStudyHint: "Ештеңе таңдамасаңыз, оқу режимі жоқ. Оқу қолданбалары оқу аралықтары мен шектеуі бойынша есептеледі; сыйлық «Оқуға уақыт бөліп, ойын уақытын алу» бөлімінде орнатылады.",
      appPickerDone: "Дайын",
      appPickerCancel: "Болдырмау",
      appPickerLoading: "Қолданбалар тізімі оқылуда…",
      passwordSheetTitle: "Ата-ана құпиясөзін ауыстыру",
      remoteTitle: "Қашықтан басқару",
      remoteLead: "Отбасылық бұлтқа байлағаннан кейін жергілікті ережелер жүктеледі, бұл бет тек оқуға арналған болады.",
      remoteConsentLead: "Байлау осы ТВ-нің ережелерін, қолданылуын және құрылғы нөмірін отбасылық бұлтыңызға жібереді. Сіз осы ТВ-нің ата-анасы екеніңізді растаңыз.",
      remoteConsentButton: "Түсінікті, жалғастыру",
      remoteStart: "Байлау QR кодын жасау",
      remoteWaiting: "Телефон сканерлеуін күту…",
      remoteRetrying: "Қайта қосылу…",
      remoteSuccess: "Байлау сәтті",
      remoteFailed: "Байлау қатесі: {reason}",
      remotePairCode: "Байлау коды",
      remoteDeviceNo: "ТВ нөмірі",
      remoteStop: "Күтуді тоқтату",
      remoteRefresh: "Күйді жаңарту",
      remoteMigrateTitle: "Ережелерді көшіру",
      remoteMigrateLead: "Байлау аяқталды. Жергілікті ережелерді бұлтқа көшіргеннен кейін жергілікті өңдеу тек оқуға арналған болады.",
      remoteMigrateButton: "Көшіруді бастау",
      remoteMigrateWaiting: "Көшіру…",
      remoteDone: "Ережелер бұлтта сақталады. Бұл жерде тек қорытынды; ажыратуды ата-ана қолданбасында жасаңыз.",
      remoteNoChild: "Алдымен балалар режимін қосып, содан кейін қашықтан басқаруды байлаңыз.",
      remoteSummary: "Бұлттық ережелер қорытындысы",
      rulesRevisionLabel: "Ереже нұсқасы",
      cloudConfigVersion: "Бұлттық конфигурация нұсқасы {n}",
      sessionActive: "Сеанс жүріп жатыр",
      sessionIdle: "Сеанс жоқ",
      guardEnforcing: "Қорғауда",
      guardOverride: "Уақытша ашылған",
      guardOther: "Қорғаусыз",
      readOnlyCloud: "Бұлтпен басқарылады; жергілікті ережелер тек оқуға арналған",
      remoteUnavailable: "Бұл ТВ-де қашықтан қызмет қосылмаған",
      pinLocked: "Қате құпиясөз тым көп; кейінірек қайталаңыз",
      network: "ТВ-ге қосылу мүмкін емес. Телефон мен ТВ бір желіде болуы керек",
      unknownError: "Әрекет орындалмады; қайталаңыз",
      stateChanged: "ТВ күйі өзгерді; жаңартылды",
      quizTakenOver: "Басқа телефон тест бетін басып алды; қайта кіріңіз",
      editorLost: "Өңдеу құқығы бітті; орталықты қайта ашыңыз",
      rulesOverlap: "Уақыт аралықтары қабаттасады; түзетіңіз",
      rulesTooMany: "Күніне бір уақыт аралығы",
      rulesApps: "Қолданба тізімі жарамсыз; қайта таңдаңыз",
      rulesInvalid: "Уақыт ережелері жарамсыз; тексеріңіз",
      quizRunning: "Тест жүріп жатыр; тест баптауларын өзгертуге болмайды",
      pendingSwitch: "Аяқталмаған бұлттық ауысу бар; оны ТВ-де аяқтаңыз",
      cloudSettlement: "Бұлттық есеп айырысу үшін желі керек; тексеріп қайталаңыз",
      timeout: "ТВ уақытында жауап бермеді; қайталаңыз",
      openQuizLogin: "Тестке кіруді ашу",
      emptyValue: "—",
      guardLabel: "Қорғау",
      configVersion: "Конфигурация нұсқасы",
      saveRetry: "Сақталмады; ТВ-дегі соңғы баптаулар қалпына келтірілді",
      pairingExpired: "Жұптау кодының мерзімі бітті; ТВ-де жаңасын жасаңыз",
      pairUnavailable: "ТВ әлі жұптау кодын жасамаған",
      pairInvalid: "Жұптау коды дұрыс емес",
      verifyExpired: "Тексеру мерзімі бітті; ата-ана құпиясөзін қайта енгізіңіз",
      unlockRequired: "Алдымен ТВ-ні уақытша ашыңыз",
      quizLoginRequired: "Жергілікті орталықты ашу үшін тест бетіне кіріңіз",
      personalize: "Бала режимінің тұсқағаздары",
      personalizeHint: "Бұлттық бейнекітапша, жергілікті жүктеу және әдепкі тұсқағаз",
      introVideoTitle: "Тест басталу бейнесі",
      introVideoHint: "Тек осы ТВ-де сақталады; бұлтқа жүктелмейді",
      introVideoLimit: "MP4/MOV · H.264/H.265 және т.б. (ТВ декодері қажет) · ≤128 МБ, ұзақтығы шектелмейді",
      introVideoBuiltin: "Кірістірілген кіріспе",
      introVideoActive: "Қолданылуда",
      introVideoSetActive: "Тест кіріспесі ретінде қою",
      introVideoRestore: "Кірістірілген кіріспені қайтару",
      introVideoUpload: "Бейне немесе сурет жүктеу",
      introVideoUploading: "Жүктелуде…",
      introVideoEmpty: "Әзірге жүктелген бейне жоқ",
      introVideoDelete: "Жою",
      introVideoDeleteTitle: "Бұл бейнені жою керек пе?",
      introVideoDeleteText: "Қайтару мүмкін емес. Егер қолданылып жатса, кірістірілген кіріспе қайтарылады.",
      introVideoSelected: "Тест кіріспесі ретінде таңдалды",
      introVideoDeleted: "Бейне жойылды",
      introVideoRestored: "Кірістірілген кіріспе қайтарылды",
      introVideoUploaded: "Бейне жүктелді. Пайдалану үшін «Тест кіріспесі ретінде қою» түймесін басыңыз.",
      introVideoTooLarge: "Бейне 128 МБ-тан үлкен; оны сығымдап, қайталаңыз",
      introVideoBadDuration: "Тест кіріспесі ≤15 секунд болуы керек; бұл бейнені бала режимінің тұсқағазы ретінде қолдануға болады",
      introVideoBadFormat: "Тек JPG/PNG/WebP суреттері немесе 16:9 MP4/MOV бейнелері қолданылады; бұл ТВ бейне кодын декодтай алуы керек",
      introVideoInvalid: "Бейне оқылмайды немесе пішімі қолданылмайды",
      introVideoNoSpace: "ТВ-де орын жеткіліксіз",
      introVideoMeta: "{size} МБ · {seconds} с · {width}×{height}",
      introVideoListTitle: "Бейнекітапша",
      introVideoUploadHint: "Бірнеше бейнені бірден таңдауға болады. Жүктегеннен кейін «Қолдану» түймесін басыңыз.",
      introVideoUploadProgress: "Жүктелуде {index}/{total}: {name} · {percent}%",
      introVideoBatchDone: "{added} тұсқағаз жүктелді",
      introVideoBatchFailed: "{failed} элемент жүктелмеді: {reason}",
      introVideoUse: "Қолдану",
      introVideoHevc: "Бұл ТВ H.265/HEVC декодтай алмайды; бейнені H.264 етіп түрлендіріп, қайталаңыз.",
      introVideoUnsupportedCodec: "Бұл ТВ бұл бейне кодын декодтай алмайды; бейнені H.264 етіп түрлендіріп, қайталаңыз.",
      mediaPageTitle: "Бала режимінің тұсқағаздары",
      mediaCurrentTitle: "Ағымдағы таңдау",
      mediaRoleQuiz: "Тест кіріспесі тұсқағазы",
      mediaRoleLock: "Бала режимінің тұсқағазы",
      mediaRoleBoth: "Екеуі де",
      mediaRoleQuizHint: "Тест басталғанда көрсетіледі",
      mediaRoleLockHint: "Бала режимінің құлып экранында және өзіндік тәртіп жұмыс үстелінде көрсетіледі; бейне қайталанады, дыбысты қосуға болады",
      mediaRoleLabel: "Қайда қолдану",
      mediaSourceDefault: "Бала режимінің әдепкі тұсқағазы",
      mediaSourceOfficial: "Бұлттық кітапхана",
      mediaSourceLocal: "Жергілікті жүктеу",
      mediaKindVideo: "Бейне",
      mediaKindImage: "Сурет",
      mediaKindTabsLabel: "Тұсқағаз түрі",
      mediaSourceTabsLabel: "Тұсқағаз көзі",
      mediaMetaImage: "{size} МБ · {width}×{height}",
      mediaDefaultBadge: "Әдепкі",
      mediaImageRoleNote: "Суретті тек бала режимінің тұсқағазы ретінде қолдануға болады.",
      mediaChoose: "Таңдау",
      mediaChange: "Ауыстыру",
      mediaCustomized: "Бапталған",
      mediaTabCloud: "Бұлт",
      mediaTabLocal: "Жергілікті",
      mediaEmpty: "Кітапханада әзірге тұсқағаз жоқ",
      mediaCatalogOff: "Бұл жинақта бұлттық кітапхана бапталмаған",
      mediaCatalogUnavailable: "Бұлттық кітапхана қолжетімсіз. Жергілікті жүктеу мен бала режимінің әдепкі тұсқағазы жұмысын жалғастырады.",
      mediaRefresh: "Кітапхананы жаңарту",
      mediaRefreshing: "Жаңартылуда…",
      mediaLoading: "ТВ кітапханасы оқылуда…",
      mediaStateNotDownloaded: "Жүктелмеген",
      mediaStateDownloading: "Жүктелуде {percent}%",
      mediaStateVerifying: "Тексерілуде…",
      mediaStateReady: "Жүктелді",
      mediaStateLocalReady: "Дайын",
      mediaStateFailed: "Жүктеу қатесі",
      mediaStateUpdate: "Жаңа нұсқа бар",
      mediaMeta: "{duration} · {size} МБ · {width}×{height}",
      mediaDownload: "Осы тұсқағазды жүктеу",
      mediaDownloadDone: "Жүктелді. «Ағымдағы таңдау» бөлімінде таңдаңыз.",
      mediaPickerTitle: "{module} таңдаңыз",
      mediaPickerEmpty: "Әзірге жүктелген тұсқағаз жоқ",
      mediaPickerEmptyVideo: "Әзірге жүктелген бейне жоқ.",
      mediaPickerGoLibrary: "Бұлттық кітапхананы ашу",
      mediaCurrentBadge: "Ағымдағы",
      mediaRetryDownload: "Жүктеуді қайталау",
      mediaUpdateLocal: "Жергілікті көшірмені жаңарту",
      mediaClose: "Жабу",
      mediaBadgeOfficial: "Ресми",
      mediaBadgeLocal: "Жергілікті",
      mediaDownloadUse: "Жүктеп, қолдану",
      mediaUse: "Осы тұсқағазды қолдану",
      mediaInUse: "Қолданылуда",
      mediaManage: "Басқару",
      mediaCancelDownload: "Жүктеуді тоқтату",
      mediaRestoreDefault: "Бала режимінің әдепкі тұсқағазын қайтару",
      mediaRestoreTitle: "Бала режимінің әдепкі тұсқағазын қайтару керек пе?",
      mediaRestoreText: "Тазартқаннан кейін бала режимінің әдепкі тұсқағазы қолданылады; ата-ана режиміндегі Aerial тұсқағазы өзгермейді.",
      mediaRestoreDone: "Бала режимінің әдепкі тұсқағазы қайтарылды",
      mediaDeleteLocal: "Жергілікті файлды жою",
      mediaDeleteUpload: "Жүктелгенді жою",
      mediaDeleteTitle: "Осы тұсқағазды жою керек пе?",
      mediaDeleteText: "Жергілікті көшірме жойылады; оны қолданатын рөлдер бала режимінің әдепкі тұсқағазына оралады.",
      mediaDeleted: "Тұсқағаз жойылды",
      mediaSelected: "Тұсқағаз параметрлері жаңартылды",
      mediaDownloadStarted: "Жүктеу басталды",
      mediaDownloading: "Жүктелуде…",
      mediaCancelled: "Жүктеу тоқтатылды",
      mediaNotFound: "Тұсқағаз табылмайды немесе енді қолжетімсіз",
      mediaNotReady: "Тұсқағаз әлі жүктелмеген",
      mediaBusy: "Басқа тұсқағаз жүктелуде; кейінірек қайталаңыз",
      mediaRoleInvalid: "Тұсқағазды таңдалған рөлге қолдану мүмкін емес",
      mediaNoSpace: "ТВ-де орын жеткіліксіз",
      mediaDeleteFailed: "Жою сәтсіз; қайталаңыз",
      mediaDownloadFailed: "Жүктеу сәтсіз; ТВ желісін тексеріп, қайталаңыз",
      mediaLocalEmpty: "Әзірге жүктелген тұсқағаз жоқ",
      mediaSoundTitle: "Тұсқағаз дыбысы",
      mediaSoundHint: "Бала режимінің жалпы дыбысы: музыка таңдалса, сол ойнайды, бейне дыбыссыз; ата-ана режимінде дыбыссыз",
      mediaSoundUnavailable: "Қосу үшін дыбысы бар бейнені немесе фондық музыканы таңдаңыз",
      mediaSoundSaved: "Тұсқағаз дыбысы жаңартылды",
      mediaLockSummary: "{count} элемент · суреттер {duration} сайын",
      mediaLockPickerHint: "Реті таңдау ретімен; суреттер белгіленген уақыт тұрады, бейне соңына дейін ойнайды",
      mediaLockEmptyHint: "Таңдалмаса, әдепкі тұсқағаз қолданылады. Ауыстыру үшін бірнешеуін таңдаңыз.",
      mediaLockSingleHint: "Ауыстыруды бастау үшін тағы біреуін таңдаңыз; алып тастау үшін қайта басыңыз.",
      mediaLockInterval: "Сурет ұсталымы",
      audioTitle: "Фондық музыка",
      audioHint: "Бала режимінің барлық тұсқағаздары астында қайталанады және «Тұсқағаз дыбысы» қосқышына бағынады; музыка ойнап тұрғанда тұсқағаз бейнелері дыбыссыз; тест немесе ата-ана әрекеті кезінде үзіледі",
      audioNone: "Фондық музыка жоқ",
      audioNoneHint: "Тек бейне тұсқағаздың дыбысы",
      audioUpload: "Аудио жүктеу",
      audioUploading: "Аудио жүктелуде…",
      audioUploaded: "Аудио жүктелді",
      audioSelected: "Фондық музыка жаңартылды",
      audioDeleted: "Аудио жойылды",
      audioDelete: "Жою",
      audioDeleteTitle: "Осы аудионы жою керек пе?",
      audioDeleteText: "Мұны қайтару мүмкін емес; бала режимі фондық музыкасыз жалғасады.",
      audioMeta: "{size} МБ · {duration}",
      audioBadFormat: "Тек MP3, M4A/AAC және OGG қолданылады.",
      audioTooLarge: "Аудио 20 МБ-тан асады; қысып, қайталап көріңіз.",
      audioBadDuration: "Аудио ұзақтығы 30 минуттан аспауы керек.",
      audioUnsupported: "Бұл теледидар бұл аудиокодекті декодтай алмайды.",
      audioNoSpace: "Теледидарда орын жеткіліксіз.",
      audioEmpty: "Әзірге аудио жүктелмеген",
      audioSourceCloud: "Бұлттық музыка",
      audioSourceLocal: "Жергілікті жүктеу",
      audioSourceTabsLabel: "Музыка көзі",
      audioCloudEmpty: "Бұлттық музыкада әзірге трек жоқ",
      audioCatalogOff: "Бұл жинақта бұлттық музыка бапталмаған",
      audioCatalogUnavailable: "Бұлттық музыка қолжетімсіз; жүктелген тректер жұмысын жалғастырады",
      audioRefresh: "Музыканы жаңарту",
      audioRefreshing: "Жаңартылуда…",
      audioDownloadUse: "Жүктеп, қолдану",
      audioUse: "Қолдану",
      audioDownload: "Жүктеу",
      audioRetry: "Қайталау",
      audioUpdate: "Жаңарту",
      audioReady: "Жүктелді",
      audioNotDownloaded: "Жүктелмеген",
      audioStateDownloading: "Жүктелуде {percent}%",
      audioStateVerifying: "Тексерілуде…",
      audioStateFailed: "Жүктеу сәтсіз",
      audioStateUpdate: "Жаңа нұсқа бар",
      audioDownloadStarted: "Жүктеу басталды; тексерілген соң трек фондық музыка болады",
      audioDownloadDone: "Трек жүктеліп, фондық музыка етіп таңдалды",
      audioDownloaded: "Трек жүктелді",
      audioDownloadCancelled: "Жүктеу тоқтатылды",
      audioDownloadFailed: "Жүктеу сәтсіз; ТВ желісін тексеріп, қайталаңыз",
      audioBusy: "Басқа трек жүктелуде; сәл кейін қайталаңыз",
      audioDeleteDownload: "Жергілікті көшірмені жою",
      audioDeleteDownloadTitle: "Жүктелген тректі жою керек пе?",
      audioDeleteDownloadText: "Теледидардағы көшірме ғана жойылады; тректі бұлттық музыкадан қайта жүктеуге болады. Егер ол қолданылып жатса, фондық музыка «музыкасыз» күйіне оралады.",
      audioDownloadDeleted: "Жергілікті көшірме жойылды",
      mediaIntervalSeconds: "{n} с",
      mediaIntervalMinutes: "{n} мин",
      mediaLockLimitHint: "Тұсқағаз шегіне жеттіңіз; алдымен біреуін алып тастаңыз",
      mediaLockInvalid: "Ауыстыру параметрлері жарамсыз; қайта таңдаңыз",
      mediaSave: "Сақтау",
      mediaSaveCount: "Сақтау ({n})",
      mediaUseDefault: "Әдепкі тұсқағазды қолдану",
      mediaPlaylistSaved: "Тұсқағаз ауыстыруы жаңартылды",
      mediaOnlyWallpaper: "Тек тұсқағаз",
      mediaHasAudio: "Дыбысты",
      mediaTimingLead: "Бір реттік таймер",
      mediaSingleTimingNote: "бала режимінің тұсқағазын қолданады",
      mediaReadonly: "Тек оқу",
      mediaImageTooLarge: "Сурет 12 МБ-тан үлкен; оны сығымдап, қайталаңыз.",
      mediaImageBadFormat: "Тек JPG/PNG/WebP суреттері қолданылады.",
      mediaImageInvalid: "Сурет оқылмайды немесе пішімі қолданылмайды.",
      mediaBadFormat: "Файл пішімі қолданылмайды.",
      mediaTooLarge: "Файл тым үлкен.",
      remoteQrAlt: "Қашықтан басқаруды байлау QR коды",
      featureUnavailable: "Бұл жинақта веб-ата-ана орталығы қосылмаған",
      openFailed: "Ата-ана орталығы қазір қолжетімсіз"
    }
  };

  /*
   * V41 four-tab copy. The user tab edits one member's own settings; the system tab holds
   * everything that belongs to the TV rather than to a member. Merged into TEXTS so t()
   * keeps one lookup path.
   */
  const V41_TEXTS = {
    zh: {
      userConfigTitle: "用户配置",
      systemConfigTitle: "系统配置",
      userOnTv: "电视当前：{name}",
      userNoMember: "还没有家庭成员",
      memberHeaderSubtitle: "{member} · 电视当前：{name}",
      userSiblingNote: "正在查看「{name}」。把电视切换到这位用户后，可在这里修改 TA 的时间、计时和壁纸。",
      userActiveNote: "这些设置只影响「{name}」；电视同一时间只按一个成员计时。",
      userSwitchAction: "切换",
      userPinLabel: "密码",
      userPinHint: "6 位数字，切换成员时使用",
      userPinInitial: "初始 123456",
      userPinKeep: "不修改",
      userPinInvalid: "请输入 6 位数字密码",
      userPinRequired: "请为这位成员设置 6 位数字密码",
      systemMemberSwitch: "切换需要密码",
      systemMemberSwitchHint: "开启后需 6 位密码",
      userConfigPinInput: "用户配置密码输入",
      userConfigPinInputHint: "关闭后，用户配置中的修改和单次计时不再要求家长密码",
      sensitivePinLeadUserConfig: "关闭后，持有本页面的任何人都可以修改用户配置。请输入家长密码确认。",
      controlManualLeadPinless: "用户配置密码输入已关闭，本次不再要求家长密码。电视将立即开始计时。",
      controlManualPinRestored: "用户配置密码输入已重新开启，请重新打开单次计时并输入家长密码。",
      userConfigPinSaved: "已更新用户配置密码输入",
      userSwitchTo: "切换到当前用户",
      userSwitchEnabled: "电视将使用「{name}」",
      userSwitchDisabled: "电视当前用户",
      userSwitchConfirm: "切换到「{name}」？",
      memberSwitched: "已切换",
      memberSwitchSaved: "已保存",
      tvBusy: "电视正在使用，暂时不能切换",
      userTimeDisabled: "切到电视当前成员后可修改",
      systemAiHint: "模型、密钥与语音",
      systemGroupFamilyTv: "家庭与电视",
      systemGroupParent: "家长与内容",
      systemGroupAccount: "更新与账号",
      systemAppRecord: "APP 备案号：{number}",
      systemAppRecordLookup: "备案查询",
      systemLanguage: "语言",
      systemLanguageHint: "界面显示语言",
      systemFamily: "家庭成员",
      systemFamilyHint: "称呼、头像与新增孩子",
      systemDevice: "设备状态",
      systemDeviceHint: "权限、系统与内存",
      systemEntries: "入口显示",
      systemEntriesHint: "电视输入源与答题入口",
      systemUpdates: "软件更新",
      systemUpdatesHint: "自动更新与当前版本",
      entriesPageHint: "这些开关只影响电视上的入口，不改变已设定的规则。",
      familyPageHint: "点成员可修改称呼、年级和头像；新增成员从空白开始。",
      memberGradeLabel: "年级",
      memberSemesterLabel: "册次",
      memberGradeAll: "全部年级",
      memberSemesterAll: "全部册次",
      memberGradeHint: "作为默认出题和关卡范围；留空表示全部",
      memberGradeInvalid: "年级设置无效",
      memberGradeNumber: "{n}年级",
      semesterFirst: "上册",
      semesterSecond: "下册",
    },
    en: {
      userConfigTitle: "User settings",
      systemConfigTitle: "System settings",
      userOnTv: "On TV now: {name}",
      userNoMember: "No family members yet",
      memberHeaderSubtitle: "{member} · On TV: {name}",
      userSiblingNote: "Viewing {name}. Switch the TV to this user to edit their time, timer or wallpaper here.",
      userActiveNote: "These settings apply only to {name}; the TV enforces one member at a time.",
      userSwitchAction: "Switch",
      userPinLabel: "Password",
      userPinHint: "6 digits, used when switching members",
      userPinInitial: "Initial 123456",
      userPinKeep: "Keep unchanged",
      userPinInvalid: "Enter a 6-digit password",
      userPinRequired: "Set a 6-digit password for this member",
      systemMemberSwitch: "Password to switch",
      systemMemberSwitchHint: "Requires the 6-digit password",
      userConfigPinInput: "Password for user settings",
      userConfigPinInputHint: "When off, changes in user settings and the one-time timer no longer ask for the parent password",
      sensitivePinLeadUserConfig: "Turning this off lets anyone holding this page change user settings. Enter the parent PIN to confirm.",
      controlManualLeadPinless: "Password input for user settings is off, so this timer does not ask for the parent PIN. The TV will start immediately.",
      controlManualPinRestored: "Password input for user settings was turned back on. Reopen the one-time timer and enter the parent PIN.",
      userConfigPinSaved: "User settings password setting updated",
      userSwitchTo: "Switch to this user",
      userSwitchEnabled: "The TV will use {name}",
      userSwitchDisabled: "Current TV user",
      userSwitchConfirm: "Switch to {name}?",
      memberSwitched: "Switched",
      memberSwitchSaved: "Saved",
      tvBusy: "The TV is in use; try again later",
      userTimeDisabled: "Switch to the TV's current member to edit",
      systemAiHint: "Models, keys and speech",
      systemGroupFamilyTv: "Family & TV",
      systemGroupParent: "Parent & content",
      systemGroupAccount: "Updates & account",
      systemAppRecord: "App record number: {number}",
      systemAppRecordLookup: "Lookup",
      systemLanguage: "Language",
      systemLanguageHint: "Display language",
      systemFamily: "Family members",
      systemFamilyHint: "Names, avatars and adding a child",
      systemDevice: "Device status",
      systemDeviceHint: "Permissions, system and memory",
      systemEntries: "Entry visibility",
      systemEntriesHint: "TV input sources and quiz entry",
      systemUpdates: "Software update",
      systemUpdatesHint: "Automatic updates and current version",
      entriesPageHint: "These switches only change TV entry points; saved rules stay as they are.",
      familyPageHint: "Tap a member to change the name, grade or avatar; a new member starts empty.",
      memberGradeLabel: "Grade",
      memberSemesterLabel: "Semester",
      memberGradeAll: "All grades",
      memberSemesterAll: "All semesters",
      memberGradeHint: "Default scope for questions and levels; empty means all",
      memberGradeInvalid: "Invalid grade setting",
      memberGradeNumber: "Grade {n}",
      semesterFirst: "Semester 1",
      semesterSecond: "Semester 2",
    },
    ru: {
      userConfigTitle: "Настройки пользователя",
      systemConfigTitle: "Системные настройки",
      userOnTv: "Сейчас на ТВ: {name}",
      userNoMember: "Пока нет членов семьи",
      memberHeaderSubtitle: "{member} · Сейчас на ТВ: {name}",
      userSiblingNote: "Открыты настройки «{name}». Переключите ТВ на этого участника, чтобы менять его время, таймер и обои здесь.",
      userActiveNote: "Настройки относятся только к «{name}»; ТВ соблюдает правила одного члена.",
      userSwitchAction: "Сменить",
      userPinLabel: "Пароль",
      userPinHint: "6 цифр, нужен при смене участника",
      userPinInitial: "Начальный 123456",
      userPinKeep: "Не менять",
      userPinInvalid: "Введите пароль из 6 цифр",
      userPinRequired: "Задайте пароль из 6 цифр для этого участника",
      systemMemberSwitch: "Пароль для смены",
      systemMemberSwitchHint: "Нужен пароль из 6 цифр",
      userConfigPinInput: "Пароль для настроек пользователя",
      userConfigPinInputHint: "Если выключено, изменения в настройках пользователя и разовый таймер больше не требуют пароля родителя",
      sensitivePinLeadUserConfig: "После выключения любой, у кого открыта эта страница, сможет менять настройки пользователя. Введите пароль родителя для подтверждения.",
      controlManualLeadPinless: "Ввод пароля для настроек пользователя выключен, поэтому этот таймер не требует пароля родителя. Телевизор начнёт сразу.",
      controlManualPinRestored: "Ввод пароля для настроек пользователя снова включён. Откройте разовый таймер заново и введите пароль родителя.",
      userConfigPinSaved: "Настройка пароля для настроек пользователя обновлена",
      userSwitchTo: "Переключить на этого пользователя",
      userSwitchEnabled: "ТВ будет использовать {name}",
      userSwitchDisabled: "Текущий пользователь ТВ",
      userSwitchConfirm: "Переключить на {name}?",
      memberSwitched: "Переключено",
      memberSwitchSaved: "Сохранено",
      tvBusy: "ТВ используется; повторите позже",
      userTimeDisabled: "Переключитесь на текущего члена ТВ",
      systemAiHint: "Модели, ключи и озвучивание",
      systemGroupFamilyTv: "Семья и ТВ",
      systemGroupParent: "Родитель и контент",
      systemGroupAccount: "Обновления и аккаунт",
      systemAppRecord: "Номер регистрации приложения: {number}",
      systemAppRecordLookup: "Проверка",
      systemLanguage: "Язык",
      systemLanguageHint: "Язык интерфейса",
      systemFamily: "Члены семьи",
      systemFamilyHint: "Имена, аватары и новый ребёнок",
      systemDevice: "Состояние устройства",
      systemDeviceHint: "Разрешения, система и память",
      systemEntries: "Видимость входов",
      systemEntriesHint: "Источники входа и вход в викторину",
      systemUpdates: "Обновление ПО",
      systemUpdatesHint: "Автообновление и текущая версия",
      entriesPageHint: "Эти переключатели меняют только входы на ТВ; сохранённые правила не меняются.",
      familyPageHint: "Нажмите на члена, чтобы изменить имя, класс или аватар; новый член начинается с пустых настроек.",
      memberGradeLabel: "Класс",
      memberSemesterLabel: "Полугодие",
      memberGradeAll: "Все классы",
      memberSemesterAll: "Все полугодия",
      memberGradeHint: "Класс и полугодие по умолчанию для вопросов и уровней; пусто — все",
      memberGradeInvalid: "Неверный класс",
      memberGradeNumber: "{n} класс",
      semesterFirst: "1-е полугодие",
      semesterSecond: "2-е полугодие",
    },
    uk: {
      userConfigTitle: "Налаштування користувача",
      systemConfigTitle: "Системні налаштування",
      userOnTv: "Зараз на ТБ: {name}",
      userNoMember: "Ще немає членів родини",
      memberHeaderSubtitle: "{member} · Зараз на ТБ: {name}",
      userSiblingNote: "Відкрито налаштування «{name}». Перемкніть ТБ на цього учасника, щоб змінювати його час, таймер і шпалери тут.",
      userActiveNote: "Ці налаштування стосуються лише «{name}»; ТБ дотримується правил одного учасника.",
      userSwitchAction: "Змінити",
      userPinLabel: "Пароль",
      userPinHint: "6 цифр, потрібен під час зміни учасника",
      userPinInitial: "Початковий 123456",
      userPinKeep: "Не змінювати",
      userPinInvalid: "Введіть пароль із 6 цифр",
      userPinRequired: "Задайте пароль із 6 цифр для цього учасника",
      systemMemberSwitch: "Пароль для зміни",
      systemMemberSwitchHint: "Потрібен пароль із 6 цифр",
      userConfigPinInput: "Пароль для налаштувань користувача",
      userConfigPinInputHint: "Якщо вимкнено, зміни в налаштуваннях користувача та разовий таймер більше не потребують пароля батьків",
      sensitivePinLeadUserConfig: "Після вимкнення будь-хто, у кого відкрита ця сторінка, зможе змінювати налаштування користувача. Введіть пароль батьків для підтвердження.",
      controlManualLeadPinless: "Введення пароля для налаштувань користувача вимкнено, тому цей таймер не потребує пароля батьків. Телевізор почне одразу.",
      controlManualPinRestored: "Введення пароля для налаштувань користувача знову ввімкнено. Відкрийте разовий таймер ще раз і введіть пароль батьків.",
      userConfigPinSaved: "Налаштування пароля для налаштувань користувача оновлено",
      userSwitchTo: "Перемкнути на цього користувача",
      userSwitchEnabled: "ТБ використовуватиме {name}",
      userSwitchDisabled: "Поточний користувач ТБ",
      userSwitchConfirm: "Перемкнути на {name}?",
      memberSwitched: "Перемкнено",
      memberSwitchSaved: "Збережено",
      tvBusy: "ТБ зараз зайнятий; спробуйте пізніше",
      userTimeDisabled: "Перемкніться на поточного учасника ТБ",
      systemAiHint: "Моделі, ключі й озвучення",
      systemGroupFamilyTv: "Родина й ТБ",
      systemGroupParent: "Батьки та вміст",
      systemGroupAccount: "Оновлення й акаунт",
      systemAppRecord: "Номер реєстрації застосунку: {number}",
      systemAppRecordLookup: "Перевірка",
      systemLanguage: "Мова",
      systemLanguageHint: "Мова інтерфейсу",
      systemFamily: "Члени родини",
      systemFamilyHint: "Імена, аватари й додавання дитини",
      systemDevice: "Стан пристрою",
      systemDeviceHint: "Дозволи, система й пам'ять",
      systemEntries: "Видимість входів",
      systemEntriesHint: "Джерела входу й вхід у вікторину",
      systemUpdates: "Оновлення ПЗ",
      systemUpdatesHint: "Автооновлення й поточна версія",
      entriesPageHint: "Ці перемикачі змінюють лише входи на ТБ; збережені правила не змінюються.",
      familyPageHint: "Натисніть на учасника, щоб змінити ім'я, клас чи аватар; новий учасник починає з порожніх налаштувань.",
      memberGradeLabel: "Клас",
      memberSemesterLabel: "Семестр",
      memberGradeAll: "Усі класи",
      memberSemesterAll: "Усі семестри",
      memberGradeHint: "Клас і семестр типових питань та рівнів; порожньо — усі",
      memberGradeInvalid: "Некоректний клас",
      memberGradeNumber: "{n} клас",
      semesterFirst: "1-й семестр",
      semesterSecond: "2-й семестр",
    },
    kk: {
      userConfigTitle: "Пайдаланушы параметрлері",
      systemConfigTitle: "Жүйе параметрлері",
      userOnTv: "Қазір ТВ-де: {name}",
      userNoMember: "Отбасы мүшелері әлі жоқ",
      memberHeaderSubtitle: "{member} · Қазір ТВ-де: {name}",
      userSiblingNote: "«{name}» параметрлері ашық. Оның уақытын, таймерін және тұсқағазын осында өзгерту үшін ТВ-ні осы мүшеге ауыстырыңыз.",
      userActiveNote: "Бұл параметрлер тек «{name}» үшін; ТВ бір уақытта бір мүшенің ережесін ұстанады.",
      userSwitchAction: "Ауыстыру",
      userPinLabel: "Құпия сөз",
      userPinHint: "6 цифр, мүшені ауыстырғанда қолданылады",
      userPinInitial: "Бастапқы 123456",
      userPinKeep: "Өзгертпеу",
      userPinInvalid: "6 цифрлық құпия сөзді енгізіңіз",
      userPinRequired: "Бұл мүше үшін 6 цифрлық құпиясөз орнатыңыз",
      systemMemberSwitch: "Ауыстыру құпия сөзі",
      systemMemberSwitchHint: "6 цифрлық құпия сөз қажет",
      userConfigPinInput: "Пайдаланушы параметрлерінің құпиясөзі",
      userConfigPinInputHint: "Өшірілсе, пайдаланушы параметрлеріндегі өзгерістер мен бір реттік таймер ата-ана құпиясөзін сұрамайды",
      sensitivePinLeadUserConfig: "Өшіргеннен кейін бұл бетті ашқан кез келген адам пайдаланушы параметрлерін өзгерте алады. Растау үшін ата-ана құпиясөзін енгізіңіз.",
      controlManualLeadPinless: "Пайдаланушы параметрлерінің құпиясөзі өшірілген, сондықтан бұл таймер ата-ана құпиясөзін сұрамайды. Теледидар бірден басталады.",
      controlManualPinRestored: "Пайдаланушы параметрлерінің құпиясөзі қайта қосылды. Бір реттік таймерді қайта ашып, ата-ана құпиясөзін енгізіңіз.",
      userConfigPinSaved: "Пайдаланушы параметрлерінің құпиясөзі жаңартылды",
      userSwitchTo: "Осы пайдаланушыға ауысу",
      userSwitchEnabled: "ТВ {name} пайдаланады",
      userSwitchDisabled: "ТВ-дегі ағымдағы пайдаланушы",
      userSwitchConfirm: "{name} пайдаланушысына ауысу керек пе?",
      memberSwitched: "Ауыстырылды",
      memberSwitchSaved: "Сақталды",
      tvBusy: "ТВ қолданыста; кейінірек қайталаңыз",
      userTimeDisabled: "ТВ-дегі ағымдағы мүшеге ауысыңыз",
      systemAiHint: "Модельдер, кілттер және дыбыс",
      systemGroupFamilyTv: "Отбасы және ТВ",
      systemGroupParent: "Ата-ана және мазмұн",
      systemGroupAccount: "Жаңартулар және аккаунт",
      systemAppRecord: "Қолданбаны тіркеу нөмірі: {number}",
      systemAppRecordLookup: "Тексеру",
      systemLanguage: "Тіл",
      systemLanguageHint: "Интерфейс тілі",
      systemFamily: "Отбасы мүшелері",
      systemFamilyHint: "Аттары, аватарлары және бала қосу",
      systemDevice: "Құрылғы күйі",
      systemDeviceHint: "Рұқсаттар, жүйе және жады",
      systemEntries: "Кірулердің көрінуі",
      systemEntriesHint: "ТВ кіріс көздері және викторина кіруі",
      systemUpdates: "Бағдарламаны жаңарту",
      systemUpdatesHint: "Автоматты жаңарту және ағымдағы нұсқа",
      entriesPageHint: "Бұл қосқыштар тек ТВ кірулерін өзгертеді; сақталған ережелер өзгермейді.",
      familyPageHint: "Атын, сыныбын немесе аватарын өзгерту үшін мүшені түртіңіз; жаңа мүше бос параметрлерден басталады.",
      memberGradeLabel: "Сынып",
      memberSemesterLabel: "Жартыжылдық",
      memberGradeAll: "Барлық сыныптар",
      memberSemesterAll: "Барлық жартыжылдықтар",
      memberGradeHint: "Сұрақтар мен деңгейлер үшін әдепкі ауқым; бос — барлығы",
      memberGradeInvalid: "Сынып параметрі жарамсыз",
      memberGradeNumber: "{n}-сынып",
      semesterFirst: "1-жартыжылдық",
      semesterSecond: "2-жартыжылдық",
    }
  };
  for (const code of LANGS) Object.assign(TEXTS[code], V41_TEXTS[code]);

  /*
   * V31 remote control copy. Kept separate from TEXTS so the control surface can grow without
   * touching the configuration dictionaries; t() falls back here when TEXTS has no key.
   */
  const CONTROL_TEXTS = {
    zh: {
      controlTitle: "控制面板",
      controlChildSection: "儿童模式",
      controlTimerSection: "计划外计时",
      timeConfigSection: "时间配置",
      controlLocalOnly: "仅在本机局域网生效，电视须为本地儿童模式。",
      controlStatusChildOff: "儿童模式未开启",
      controlStatusEnforcing: "儿童模式 · 已锁定",
      controlStatusTimer: "计时中 · 剩余 {time}",
      controlStatusManualTimer: "计划外计时中 · 剩余 {minutes} 分钟",
      controlStatusRest: "休息中 · 剩余 {time}",
      controlStatusPending: "等待孩子确认家长授权",
      controlStatusOverride: "已临时退出 · 剩余 {time}",
      controlStatusOverrideNoLimit: "已临时退出儿童模式",
      controlStatusStudy: "学习模式",
      controlStatusQuiz: "答题闯关进行中",
      controlTakeOverTitle: "临时退出儿童模式",
      controlTakeOverHint: "立即解锁电视，家长使用不计时",
      controlTakeOverDiscardHint: "计时或答题中，退出会结束当前计时并清空本轮进度",
      controlTakeOverAlready: "电视已临时退出儿童模式",
      controlEnterTitle: "进入儿童模式",
      controlEnterHint: "结束临时退出，恢复儿童守护",
      controlEnterAlready: "电视已在儿童模式",
      controlStartTitle: "单次计时",
      controlStartHint: "计划外 · 不计入每日额度",
      controlStopTitle: "退出计时",
      controlStopHint: "当前计时剩余 {time}",
      controlStopNoTimer: "当前没有计时",
      controlBusy: "正在执行…",
      controlAppliedTakeOver: "已临时退出儿童模式",
      controlNoopTakeOver: "电视已在临时退出状态",
      controlAppliedEnter: "已进入儿童模式",
      controlNoopEnter: "电视已在儿童模式",
      controlAppliedStop: "已退出计时",
      controlNoopStop: "当前没有计时",
      controlAppliedManual: "单次计时已开始",
      controlStateChanged: "电视状态已变化，已刷新，请重新操作。",
      controlConfirmEndTitle: "退出本次计时？",
      controlConfirmEndBody: "已用时间会保留。结束后回到等待家长安排。",
      controlConfirmEndAction: "退出计时",
      controlConfirmTakeTimerTitle: "临时退出儿童模式？",
      controlConfirmTakeTimerBody: "当前计时会结束，已用时间会保留，本次休息会跳过。",
      controlConfirmTakeQuizTitle: "退出闯关并临时退出儿童模式？",
      controlConfirmTakeQuizBody: "本轮进度和已获得的时间会清空，电视将临时解锁。",
      controlConfirmTakeAction: "临时退出",
      controlConfirmQuizAction: "退出并解锁",
      controlManualTitle: "单次计时（计划外）",
      controlManualLead: "本次不受时间计划、时段、每日额度和入口次数限制。本次不计入孩子的娱乐时间。",
      controlManualDuration: "本次时长",
      controlManualMinutesValue: "{n} 分钟",
      controlManualStart: "开始",
      manualScopeTitle: "本次可用",
      manualScopeEntertainment: "娱乐应用 · {n} 个",
      manualScopeEntertainmentAll: "娱乐应用 · 全部",
      manualScopeUnlimited: "不限时应用 · {n} 个",
      manualScopeStudy: "学习换娱乐 · {n} 个",
      manualStudyNote: "学习换娱乐应用本次只作为普通应用打开，不会触发学习任务和奖励。",
      controlStartBlocked: "电视当前不能开始单次计时。",
      controlDeviceSection: "设备状态",
      controlDeviceTitle: "权限、系统与内存",
      controlDeviceSummaryOk: "必要项目均已开启",
      controlDeviceSummaryIssue: "有 {n} 项待开启",
      controlDeviceSummaryUnknown: "部分状态无法读取",
      controlDeviceSummaryHint: "点击查看权限、系统与内存",
      controlDeviceSummaryLoading: "正在读取…",
      controlDeviceSummaryError: "无法读取设备状态",
      controlDeviceReadFailed: "读取失败，请稍后重试",
      controlDeviceStale: "更新失败，显示上次读取的数据",
      controlDeviceRecheck: "重新检查",
      controlDeviceRefreshing: "正在刷新…",
      controlDevicePermissions: "权限状态",
      controlDevicePermissionsHint: "与电视桌面右上角的「状态检查」相同。",
      controlDeviceSetupDone: "服务配置完成",
      controlDeviceSetupMissing: "服务尚未配置完成 · {n} 项待开启",
      controlDeviceSetupUnknown: "部分项目无法读取，无法确认配置完成",
      controlDeviceSystem: "设备与系统",
      controlDeviceMemory: "内存",
      controlDeviceLowRam: "低内存机型",
      controlPermHome: "默认桌面",
      controlPermGuard: "桌面守护（无障碍）",
      controlPermSelfHeal: "开机/待机自动恢复",
      controlPermRevival: "待机后自动回来",
      controlPermUsage: "使用情况访问",
      controlPermOverlay: "悬浮窗（倒计时）",
      controlPermHomeDefault: "已设为默认",
      controlPermHomeGuarded: "由桌面守护接管",
      controlPermHomeMissing: "未设为默认",
      controlPermOn: "已开启",
      controlPermOff: "未开启",
      controlPermRunning: "运行中",
      controlPermNotRunning: "未运行",
      controlPermUnknown: "无法读取",
      controlDeviceModel: "设备型号",
      controlDeviceAndroid: "系统版本",
      controlDeviceSecurityPatch: "安全补丁",
      controlDeviceAdb: "ADB 调试",
      controlDeviceKernel: "内核版本",
      controlDeviceCpu: "架构 / CPU",
      controlDeviceSystemUptime: "系统已运行",
      controlDeviceAppVersion: "应用版本",
      controlDeviceProcessUptime: "应用已运行",
      controlDeviceStorageFree: "可用存储",
      controlDeviceCoreCount: "{n} 核",
      controlDeviceRamUsed: "已用内存",
      controlDeviceRamAvail: "可用内存",
      controlDeviceAppPss: "应用占用",
      controlDeviceHeap: "Java 堆",
      controlDeviceThreshold: "低内存阈值",
      controlDeviceMemoryState: "内存状态",
      controlDeviceMemoryNormal: "正常",
      controlDeviceMemoryLow: "紧张",
      controlDeviceUptimeDays: "{d} 天 {h} 小时",
      controlDeviceUptimeHours: "{h} 小时 {m} 分钟",
      controlDeviceUptimeMinutes: "{m} 分钟"
    },
    en: {
      controlTitle: "Control panel",
      controlChildSection: "Child mode",
      controlTimerSection: "Out-of-plan timer",
      timeConfigSection: "Time settings",
      controlLocalOnly: "Works only on this local network. The TV must be in local child mode.",
      controlStatusChildOff: "Child mode is off",
      controlStatusEnforcing: "Child mode · locked",
      controlStatusTimer: "Timing · {time} left",
      controlStatusManualTimer: "Out-of-plan timer · {minutes} min left",
      controlStatusRest: "Resting · {time} left",
      controlStatusPending: "Waiting for the child to confirm the parent grant",
      controlStatusOverride: "Temporarily unlocked · {time} left",
      controlStatusOverrideNoLimit: "Temporarily unlocked",
      controlStatusStudy: "Study mode",
      controlStatusQuiz: "Quiz challenge in progress",
      controlTakeOverTitle: "Temporarily leave child mode",
      controlTakeOverHint: "Unlock the TV now; parent use is not timed",
      controlTakeOverDiscardHint: "Timing or a quiz is running; leaving ends the timer and clears this round",
      controlTakeOverAlready: "The TV is already temporarily unlocked",
      controlEnterTitle: "Enter child mode",
      controlEnterHint: "End the temporary unlock and restore the child guard",
      controlEnterAlready: "The TV is already in child mode",
      controlStartTitle: "One-time timing",
      controlStartHint: "Out of plan · not counted against the daily quota",
      controlStopTitle: "Exit timing",
      controlStopHint: "Current timer has {time} left",
      controlStopNoTimer: "There is no active timer",
      controlBusy: "Working…",
      controlAppliedTakeOver: "The TV is temporarily unlocked",
      controlNoopTakeOver: "The TV is already temporarily unlocked",
      controlAppliedEnter: "Child mode is on",
      controlNoopEnter: "The TV is already in child mode",
      controlAppliedStop: "Timing ended",
      controlNoopStop: "There is no active timer",
      controlAppliedManual: "One-time timing started",
      controlStateChanged: "The TV state changed. Refreshed; try again.",
      controlConfirmEndTitle: "Exit this timing session?",
      controlConfirmEndBody: "Used time is kept. The TV returns to waiting for a parent.",
      controlConfirmEndAction: "Exit",
      controlConfirmTakeTimerTitle: "Temporarily leave child mode?",
      controlConfirmTakeTimerBody: "The current timer will end, used time is kept, and this rest is skipped.",
      controlConfirmTakeQuizTitle: "Exit the quiz and temporarily leave child mode?",
      controlConfirmTakeQuizBody: "This round's progress and earned time will be cleared, and the TV will be temporarily unlocked.",
      controlConfirmTakeAction: "Temporarily exit",
      controlConfirmQuizAction: "Exit and unlock",
      controlManualTitle: "One-time timer (out of plan)",
      controlManualLead: "This session ignores the time plan, windows, daily quota and route counts. It is not counted as the child's entertainment time.",
      controlManualDuration: "Session length",
      controlManualMinutesValue: "{n} min",
      controlManualStart: "Start",
      manualScopeTitle: "Apps in this session",
      manualScopeEntertainment: "Entertainment · {n}",
      manualScopeEntertainmentAll: "Entertainment · All",
      manualScopeUnlimited: "Unlimited · {n}",
      manualScopeStudy: "Study reward · {n}",
      manualStudyNote: "Study-reward apps open as ordinary apps in this session; no focus round or reward starts.",
      controlStartBlocked: "A one-time session cannot start on the TV right now.",
      controlDeviceSection: "Device status",
      controlDeviceTitle: "Permissions, system & memory",
      controlDeviceSummaryOk: "All required items are on",
      controlDeviceSummaryIssue: "{n} items need attention",
      controlDeviceSummaryUnknown: "Some states cannot be read",
      controlDeviceSummaryHint: "Tap to view permissions, system and memory",
      controlDeviceSummaryLoading: "Reading…",
      controlDeviceSummaryError: "Can't read device status",
      controlDeviceReadFailed: "Couldn't read the status. Try again later.",
      controlDeviceStale: "Update failed; showing the last reading",
      controlDeviceRecheck: "Check again",
      controlDeviceRefreshing: "Refreshing…",
      controlDevicePermissions: "Permissions",
      controlDevicePermissionsHint: "Same as 'Status check' in the TV's top-right corner.",
      controlDeviceSetupDone: "Service setup complete",
      controlDeviceSetupMissing: "Service setup incomplete · {n} items need attention",
      controlDeviceSetupUnknown: "Some items cannot be read, so setup is unconfirmed",
      controlDeviceSystem: "Device & system",
      controlDeviceMemory: "Memory",
      controlDeviceLowRam: "Low-RAM device",
      controlPermHome: "Default home screen",
      controlPermGuard: "Desktop guard (accessibility)",
      controlPermSelfHeal: "Restore after restart or standby",
      controlPermRevival: "Return after standby",
      controlPermUsage: "Usage access",
      controlPermOverlay: "Overlay for timers",
      controlPermHomeDefault: "Set as default",
      controlPermHomeGuarded: "Handled by the guard",
      controlPermHomeMissing: "Not set as default",
      controlPermOn: "On",
      controlPermOff: "Off",
      controlPermRunning: "Running",
      controlPermNotRunning: "Not running",
      controlPermUnknown: "Unknown",
      controlDeviceModel: "Device model",
      controlDeviceAndroid: "Android version",
      controlDeviceSecurityPatch: "Security patch",
      controlDeviceAdb: "ADB debugging",
      controlDeviceKernel: "Kernel",
      controlDeviceCpu: "Architecture / CPU",
      controlDeviceSystemUptime: "System uptime",
      controlDeviceAppVersion: "App version",
      controlDeviceProcessUptime: "App uptime",
      controlDeviceStorageFree: "Free storage",
      controlDeviceCoreCount: "{n} cores",
      controlDeviceRamUsed: "Used memory",
      controlDeviceRamAvail: "Available memory",
      controlDeviceAppPss: "App usage",
      controlDeviceHeap: "Java heap",
      controlDeviceThreshold: "Low-memory threshold",
      controlDeviceMemoryState: "Memory state",
      controlDeviceMemoryNormal: "Normal",
      controlDeviceMemoryLow: "Low",
      controlDeviceUptimeDays: "{d}d {h}h",
      controlDeviceUptimeHours: "{h}h {m}m",
      controlDeviceUptimeMinutes: "{m}m"
    },
    ru: {
      controlTitle: "Панель управления",
      controlChildSection: "Детский режим",
      controlTimerSection: "Таймер вне плана",
      timeConfigSection: "Настройки времени",
      controlLocalOnly: "Работает только в локальной сети. Телевизор должен быть в локальном детском режиме.",
      controlStatusChildOff: "Детский режим выключен",
      controlStatusEnforcing: "Детский режим · заблокирован",
      controlStatusTimer: "Таймер · осталось {time}",
      controlStatusManualTimer: "Таймер вне плана · осталось {minutes} мин",
      controlStatusRest: "Перерыв · осталось {time}",
      controlStatusPending: "Ожидание подтверждения ребёнком родительского доступа",
      controlStatusOverride: "Временно разблокирован · осталось {time}",
      controlStatusOverrideNoLimit: "Временно разблокирован",
      controlStatusStudy: "Режим учёбы",
      controlStatusQuiz: "Идёт викторина",
      controlTakeOverTitle: "Временно выйти из детского режима",
      controlTakeOverHint: "Разблокировать телевизор сейчас; время родителя не учитывается",
      controlTakeOverDiscardHint: "Идёт таймер или викторина: выход завершит таймер и сбросит текущий раунд",
      controlTakeOverAlready: "Телевизор уже временно разблокирован",
      controlEnterTitle: "Войти в детский режим",
      controlEnterHint: "Завершить временную разблокировку и вернуть защиту",
      controlEnterAlready: "Телевизор уже в детском режиме",
      controlStartTitle: "Разовый таймер",
      controlStartHint: "Вне плана · не расходует дневную квоту",
      controlStopTitle: "Выйти из таймера",
      controlStopHint: "До конца таймера осталось {time}",
      controlStopNoTimer: "Активного таймера нет",
      controlBusy: "Выполняется…",
      controlAppliedTakeOver: "Телевизор временно разблокирован",
      controlNoopTakeOver: "Телевизор уже временно разблокирован",
      controlAppliedEnter: "Детский режим включён",
      controlNoopEnter: "Телевизор уже в детском режиме",
      controlAppliedStop: "Таймер завершён",
      controlNoopStop: "Активного таймера нет",
      controlAppliedManual: "Разовый таймер запущен",
      controlStateChanged: "Состояние телевизора изменилось. Данные обновлены; повторите действие.",
      controlConfirmEndTitle: "Завершить этот сеанс?",
      controlConfirmEndBody: "Использованное время сохранится. Телевизор вернётся в режим ожидания родителя.",
      controlConfirmEndAction: "Выйти",
      controlConfirmTakeTimerTitle: "Временно выйти из детского режима?",
      controlConfirmTakeTimerBody: "Текущий таймер завершится, использованное время сохранится, этот перерыв будет пропущен.",
      controlConfirmTakeAction: "Выйти",
      controlConfirmTakeQuizTitle: "Выйти из викторины и временно выйти из детского режима?",
      controlConfirmTakeQuizBody: "Прогресс этого раунда и полученное время будут сброшены, телевизор временно разблокируется.",
      controlConfirmQuizAction: "Выйти и разблокировать",
      controlManualTitle: "Разовый таймер (вне плана)",
      controlManualLead: "Этот сеанс не подчиняется плану, интервалам, дневной квоте и дневным лимитам запусков. Он не засчитывается как время развлечений ребёнка.",
      controlManualDuration: "Длительность",
      controlManualMinutesValue: "{n} мин",
      controlManualStart: "Начать",
      manualScopeTitle: "Приложения в этом сеансе",
      manualScopeEntertainment: "Развлечения · {n}",
      manualScopeEntertainmentAll: "Развлечения · все",
      manualScopeUnlimited: "Без ограничений · {n}",
      manualScopeStudy: "Учёба → развлечения · {n}",
      manualStudyNote: "Приложения учёбы с наградой в этом сеансе открываются как обычные; учебный раунд и награда не запускаются.",
      controlStartBlocked: "Сейчас нельзя запустить разовый таймер.",
      controlDeviceSection: "Состояние устройства",
      controlDeviceTitle: "Разрешения, система и память",
      controlDeviceSummaryOk: "Все необходимые пункты включены",
      controlDeviceSummaryIssue: "Требуют внимания: {n}",
      controlDeviceSummaryUnknown: "Некоторые состояния недоступны",
      controlDeviceSummaryHint: "Нажмите, чтобы посмотреть разрешения, систему и память",
      controlDeviceSummaryLoading: "Чтение…",
      controlDeviceSummaryError: "Не удалось прочитать состояние устройства",
      controlDeviceReadFailed: "Не удалось прочитать. Повторите позже.",
      controlDeviceStale: "Обновление не удалось; показаны последние данные",
      controlDeviceRecheck: "Проверить снова",
      controlDeviceRefreshing: "Обновление…",
      controlDevicePermissions: "Разрешения",
      controlDevicePermissionsHint: "Как «Проверка состояния» в правом верхнем углу телевизора.",
      controlDeviceSetupDone: "Настройка служб завершена",
      controlDeviceSetupMissing: "Настройка служб не завершена · требуют внимания: {n}",
      controlDeviceSetupUnknown: "Некоторые пункты недоступны, завершение настройки не подтверждено",
      controlDeviceSystem: "Устройство и система",
      controlDeviceMemory: "Память",
      controlDeviceLowRam: "Малый объём ОЗУ",
      controlPermHome: "Домашний экран по умолчанию",
      controlPermGuard: "Защита рабочего стола (спец. возможности)",
      controlPermSelfHeal: "Восстановление после перезапуска или ожидания",
      controlPermRevival: "Возврат после ожидания",
      controlPermUsage: "Доступ к использованию",
      controlPermOverlay: "Наложение для таймеров",
      controlPermHomeDefault: "Установлен по умолчанию",
      controlPermHomeGuarded: "Управляется защитой",
      controlPermHomeMissing: "Не установлен по умолчанию",
      controlPermOn: "Включено",
      controlPermOff: "Выключено",
      controlPermRunning: "Работает",
      controlPermNotRunning: "Не работает",
      controlPermUnknown: "Недоступно",
      controlDeviceModel: "Модель устройства",
      controlDeviceAndroid: "Версия Android",
      controlDeviceSecurityPatch: "Патч безопасности",
      controlDeviceAdb: "Отладка ADB",
      controlDeviceKernel: "Ядро",
      controlDeviceCpu: "Архитектура / ЦП",
      controlDeviceSystemUptime: "Система работает",
      controlDeviceAppVersion: "Версия приложения",
      controlDeviceProcessUptime: "Приложение работает",
      controlDeviceStorageFree: "Свободно на диске",
      controlDeviceCoreCount: "Ядер: {n}",
      controlDeviceRamUsed: "Использовано памяти",
      controlDeviceRamAvail: "Доступно памяти",
      controlDeviceAppPss: "Занято приложением",
      controlDeviceHeap: "Куча Java",
      controlDeviceThreshold: "Порог нехватки памяти",
      controlDeviceMemoryState: "Состояние памяти",
      controlDeviceMemoryNormal: "Норма",
      controlDeviceMemoryLow: "Мало памяти",
      controlDeviceUptimeDays: "{d} д {h} ч",
      controlDeviceUptimeHours: "{h} ч {m} мин",
      controlDeviceUptimeMinutes: "{m} мин"
    },
    uk: {
      controlTitle: "Панель керування",
      controlChildSection: "Дитячий режим",
      controlTimerSection: "Таймер поза планом",
      timeConfigSection: "Налаштування часу",
      controlLocalOnly: "Працює лише в локальній мережі. Телевізор має бути в локальному дитячому режимі.",
      controlStatusChildOff: "Дитячий режим вимкнено",
      controlStatusEnforcing: "Дитячий режим · заблоковано",
      controlStatusTimer: "Таймер · залишилося {time}",
      controlStatusManualTimer: "Таймер поза планом · залишилося {minutes} хв",
      controlStatusRest: "Перерва · залишилося {time}",
      controlStatusPending: "Очікування підтвердження дитиною батьківського доступу",
      controlStatusOverride: "Тимчасово розблоковано · залишилося {time}",
      controlStatusOverrideNoLimit: "Тимчасово розблоковано",
      controlStatusStudy: "Режим навчання",
      controlStatusQuiz: "Триває вікторина",
      controlTakeOverTitle: "Тимчасово вийти з дитячого режиму",
      controlTakeOverHint: "Розблокувати телевізор зараз; час батьків не враховується",
      controlTakeOverDiscardHint: "Триває таймер або вікторина: вихід завершить таймер і скине поточний раунд",
      controlTakeOverAlready: "Телевізор уже тимчасово розблоковано",
      controlEnterTitle: "Увійти в дитячий режим",
      controlEnterHint: "Завершити тимчасове розблокування й повернути захист",
      controlEnterAlready: "Телевізор уже в дитячому режимі",
      controlStartTitle: "Разовий таймер",
      controlStartHint: "Поза планом · не витрачає денну квоту",
      controlStopTitle: "Вийти з таймера",
      controlStopHint: "До кінця таймера залишилося {time}",
      controlStopNoTimer: "Активного таймера немає",
      controlBusy: "Виконується…",
      controlAppliedTakeOver: "Телевізор тимчасово розблоковано",
      controlNoopTakeOver: "Телевізор уже тимчасово розблоковано",
      controlAppliedEnter: "Дитячий режим увімкнено",
      controlNoopEnter: "Телевізор уже в дитячому режимі",
      controlAppliedStop: "Таймер завершено",
      controlNoopStop: "Активного таймера немає",
      controlAppliedManual: "Разовий таймер запущено",
      controlStateChanged: "Стан телевізора змінився. Дані оновлено; повторіть дію.",
      controlConfirmEndTitle: "Завершити цей сеанс?",
      controlConfirmEndBody: "Використаний час збережеться. Телевізор повернеться до очікування батьків.",
      controlConfirmEndAction: "Вийти",
      controlConfirmTakeTimerTitle: "Тимчасово вийти з дитячого режиму?",
      controlConfirmTakeTimerBody: "Поточний таймер завершиться, використаний час збережеться, ця перерва буде пропущена.",
      controlConfirmTakeAction: "Вийти",
      controlConfirmTakeQuizTitle: "Вийти з вікторини й тимчасово вийти з дитячого режиму?",
      controlConfirmTakeQuizBody: "Прогрес цього раунду та отриманий час буде очищено, телевізор тимчасово розблокується.",
      controlConfirmQuizAction: "Вийти й розблокувати",
      controlManualTitle: "Разовий таймер (поза планом)",
      controlManualLead: "Цей сеанс не підкоряється плану, інтервалам, денній квоті та денним лімітам запусків. Він не зараховується як час розваг дитини.",
      controlManualDuration: "Тривалість",
      controlManualMinutesValue: "{n} хв",
      controlManualStart: "Почати",
      manualScopeTitle: "Застосунки в цьому сеансі",
      manualScopeEntertainment: "Розваги · {n}",
      manualScopeEntertainmentAll: "Розваги · усі",
      manualScopeUnlimited: "Без обмежень · {n}",
      manualScopeStudy: "Навчання → розваги · {n}",
      manualStudyNote: "Навчальні застосунки з нагородою в цьому сеансі відкриваються як звичайні; навчальний раунд і нагорода не запускаються.",
      controlStartBlocked: "Зараз не можна запустити разовий таймер.",
      controlDeviceSection: "Стан пристрою",
      controlDeviceTitle: "Дозволи, система й пам'ять",
      controlDeviceSummaryOk: "Усі потрібні пункти ввімкнено",
      controlDeviceSummaryIssue: "Потребують уваги: {n}",
      controlDeviceSummaryUnknown: "Деякі стани недоступні",
      controlDeviceSummaryHint: "Торкніться, щоб переглянути дозволи, систему й пам'ять",
      controlDeviceSummaryLoading: "Читання…",
      controlDeviceSummaryError: "Не вдалося прочитати стан пристрою",
      controlDeviceReadFailed: "Не вдалося прочитати. Спробуйте пізніше.",
      controlDeviceStale: "Оновлення не вдалося; показано останні дані",
      controlDeviceRecheck: "Перевірити ще раз",
      controlDeviceRefreshing: "Оновлення…",
      controlDevicePermissions: "Дозволи",
      controlDevicePermissionsHint: "Як «Перевірка стану» у правому верхньому куті телевізора.",
      controlDeviceSetupDone: "Налаштування служб завершено",
      controlDeviceSetupMissing: "Налаштування служб не завершено · потребують уваги: {n}",
      controlDeviceSetupUnknown: "Деякі пункти недоступні, завершення налаштування не підтверджено",
      controlDeviceSystem: "Пристрій і система",
      controlDeviceMemory: "Пам'ять",
      controlDeviceLowRam: "Малий обсяг пам'яті",
      controlPermHome: "Домашній екран за замовчуванням",
      controlPermGuard: "Захист робочого столу (доступність)",
      controlPermSelfHeal: "Відновлення після перезапуску чи очікування",
      controlPermRevival: "Повернення після очікування",
      controlPermUsage: "Доступ до використання",
      controlPermOverlay: "Накладення для таймерів",
      controlPermHomeDefault: "Встановлено за замовчуванням",
      controlPermHomeGuarded: "Керується захистом",
      controlPermHomeMissing: "Не встановлено за замовчуванням",
      controlPermOn: "Увімкнено",
      controlPermOff: "Вимкнено",
      controlPermRunning: "Працює",
      controlPermNotRunning: "Не працює",
      controlPermUnknown: "Невідомо",
      controlDeviceModel: "Модель пристрою",
      controlDeviceAndroid: "Версія Android",
      controlDeviceSecurityPatch: "Патч безпеки",
      controlDeviceAdb: "Налагодження ADB",
      controlDeviceKernel: "Ядро",
      controlDeviceCpu: "Архітектура / ЦП",
      controlDeviceSystemUptime: "Система працює",
      controlDeviceAppVersion: "Версія застосунку",
      controlDeviceProcessUptime: "Застосунок працює",
      controlDeviceStorageFree: "Вільно на диску",
      controlDeviceCoreCount: "Ядер: {n}",
      controlDeviceRamUsed: "Використано пам'яті",
      controlDeviceRamAvail: "Доступно пам'яті",
      controlDeviceAppPss: "Зайнято застосунком",
      controlDeviceHeap: "Купа Java",
      controlDeviceThreshold: "Поріг нестачі пам'яті",
      controlDeviceMemoryState: "Стан пам'яті",
      controlDeviceMemoryNormal: "Норма",
      controlDeviceMemoryLow: "Мало пам'яті",
      controlDeviceUptimeDays: "{d} д {h} год",
      controlDeviceUptimeHours: "{h} год {m} хв",
      controlDeviceUptimeMinutes: "{m} хв"
    },
    kk: {
      controlTitle: "Басқару панелі",
      controlChildSection: "Бала режимі",
      controlTimerSection: "Жоспардан тыс таймер",
      timeConfigSection: "Уақыт параметрлері",
      controlLocalOnly: "Тек жергілікті желіде жұмыс істейді. Теледидар жергілікті бала режимінде болуы керек.",
      controlStatusChildOff: "Бала режимі өшірулі",
      controlStatusEnforcing: "Бала режимі · құлыпталған",
      controlStatusTimer: "Таймер · {time} қалды",
      controlStatusManualTimer: "Жоспардан тыс таймер · {minutes} мин қалды",
      controlStatusRest: "Демалыс · {time} қалды",
      controlStatusPending: "Баланың ата-ана рұқсатын растауын күту",
      controlStatusOverride: "Уақытша ашылған · {time} қалды",
      controlStatusOverrideNoLimit: "Уақытша ашылған",
      controlStatusStudy: "Оқу режимі",
      controlStatusQuiz: "Викторина жүріп жатыр",
      controlTakeOverTitle: "Бала режимінен уақытша шығу",
      controlTakeOverHint: "Теледидарды қазір ашу; ата-ана уақыты есептелмейді",
      controlTakeOverDiscardHint: "Таймер немесе викторина жүріп жатыр: шығу таймерді аяқтап, осы раундты тазартады",
      controlTakeOverAlready: "Теледидар уақытша ашылған",
      controlEnterTitle: "Бала режиміне кіру",
      controlEnterHint: "Уақытша ашуды аяқтап, бала қорғанысын қалпына келтіру",
      controlEnterAlready: "Теледидар бала режимінде",
      controlStartTitle: "Бір реттік таймер",
      controlStartHint: "Жоспардан тыс · күндік шектеуді жұмсамайды",
      controlStopTitle: "Таймерден шығу",
      controlStopHint: "Таймер аяқталуына {time} қалды",
      controlStopNoTimer: "Белсенді таймер жоқ",
      controlBusy: "Орындалуда…",
      controlAppliedTakeOver: "Теледидар уақытша ашылды",
      controlNoopTakeOver: "Теледидар уақытша ашық",
      controlAppliedEnter: "Бала режимі қосылды",
      controlNoopEnter: "Теледидар бала режимінде",
      controlAppliedStop: "Таймер аяқталды",
      controlNoopStop: "Белсенді таймер жоқ",
      controlAppliedManual: "Бір реттік таймер басталды",
      controlStateChanged: "Теледидар күйі өзгерді. Деректер жаңартылды; әрекетті қайталаңыз.",
      controlConfirmEndTitle: "Осы сеанстан шығу керек пе?",
      controlConfirmEndBody: "Пайдаланылған уақыт сақталады. Теледидар ата-ана күтуіне оралады.",
      controlConfirmEndAction: "Шығу",
      controlConfirmTakeTimerTitle: "Бала режимінен уақытша шығу керек пе?",
      controlConfirmTakeTimerBody: "Ағымдағы таймер аяқталады, пайдаланылған уақыт сақталады, бұл демалыс өткізіледі.",
      controlConfirmTakeAction: "Шығу",
      controlConfirmTakeQuizTitle: "Викторинадан шығып, бала режимінен уақытша шығу керек пе?",
      controlConfirmTakeQuizBody: "Осы раундтың прогресі мен алынған уақыт тазартылады, теледидар уақытша ашылады.",
      controlConfirmQuizAction: "Шығып, ашу",
      controlManualTitle: "Бір реттік таймер (жоспардан тыс)",
      controlManualLead: "Бұл сеанс жоспарға, аралықтарға, күндік шектеуге және күндік іске қосу шектеріне бағынбайды. Ол баланың ойын-сауық уақытына есептелмейді.",
      controlManualDuration: "Ұзақтық",
      controlManualMinutesValue: "{n} мин",
      controlManualStart: "Бастау",
      manualScopeTitle: "Осы сеанстағы қолданбалар",
      manualScopeEntertainment: "Ойын-сауық · {n}",
      manualScopeEntertainmentAll: "Ойын-сауық · барлығы",
      manualScopeUnlimited: "Шектеусіз · {n}",
      manualScopeStudy: "Оқу → ойын-сауық · {n}",
      manualStudyNote: "Оқу → ойын-сауық қолданбалары бұл сеанста қарапайым қолданба ретінде ашылады; оқу раунды мен сыйақы басталмайды.",
      controlStartBlocked: "Қазір бір реттік таймерді бастау мүмкін емес.",
      controlDeviceSection: "Құрылғы күйі",
      controlDeviceTitle: "Рұқсаттар, жүйе және жады",
      controlDeviceSummaryOk: "Барлық қажетті тармақтар қосылған",
      controlDeviceSummaryIssue: "Назар аударуды қажет ететін тармақтар: {n}",
      controlDeviceSummaryUnknown: "Кейбір күйлерді оқу мүмкін емес",
      controlDeviceSummaryHint: "Рұқсаттарды, жүйе мен жадыны көру үшін түртіңіз",
      controlDeviceSummaryLoading: "Оқылуда…",
      controlDeviceSummaryError: "Құрылғы күйін оқу мүмкін емес",
      controlDeviceReadFailed: "Оқу сәтсіз. Кейінірек қайталаңыз.",
      controlDeviceStale: "Жаңарту сәтсіз; соңғы деректер көрсетілді",
      controlDeviceRecheck: "Қайта тексеру",
      controlDeviceRefreshing: "Жаңартылуда…",
      controlDevicePermissions: "Рұқсаттар",
      controlDevicePermissionsHint: "Теледидардың жоғарғы оң жағындағы «Күй тексерумен» бірдей.",
      controlDeviceSetupDone: "Қызметтерді баптау аяқталды",
      controlDeviceSetupMissing: "Қызметтерді баптау аяқталмады · назар аударуды қажет ететін: {n}",
      controlDeviceSetupUnknown: "Кейбір тармақтарды оқу мүмкін емес, баптау аяқталғаны расталмады",
      controlDeviceSystem: "Құрылғы және жүйе",
      controlDeviceMemory: "Жады",
      controlDeviceLowRam: "Аз жадылы құрылғы",
      controlPermHome: "Әдепкі үй экраны",
      controlPermGuard: "Жұмыс үстелі қорғанысы (қолжетімділік)",
      controlPermSelfHeal: "Қайта іске қосу немесе күтуден кейін қалпына келтіру",
      controlPermRevival: "Күтуден кейін қайта оралу",
      controlPermUsage: "Пайдалануға рұқсат",
      controlPermOverlay: "Таймерлерге жапсырма",
      controlPermHomeDefault: "Әдепкі етіп орнатылған",
      controlPermHomeGuarded: "Қорғаныс басқарады",
      controlPermHomeMissing: "Әдепкі етіп орнатылмаған",
      controlPermOn: "Қосылған",
      controlPermOff: "Өшірулі",
      controlPermRunning: "Жұмыс істеп тұр",
      controlPermNotRunning: "Жұмыс істемейді",
      controlPermUnknown: "Белгісіз",
      controlDeviceModel: "Құрылғы үлгісі",
      controlDeviceAndroid: "Android нұсқасы",
      controlDeviceSecurityPatch: "Қауіпсіздік жамауы",
      controlDeviceAdb: "ADB жөндеу",
      controlDeviceKernel: "Ядро",
      controlDeviceCpu: "Архитектура / CPU",
      controlDeviceSystemUptime: "Жүйе жұмыс істеп тұр",
      controlDeviceAppVersion: "Қолданба нұсқасы",
      controlDeviceProcessUptime: "Қолданба жұмыс істеп тұр",
      controlDeviceStorageFree: "Дискіде бос",
      controlDeviceCoreCount: "Ядро саны: {n}",
      controlDeviceRamUsed: "Пайдаланылған жады",
      controlDeviceRamAvail: "Қолжетімді жады",
      controlDeviceAppPss: "Қолданба алатын жады",
      controlDeviceHeap: "Java үйіндісі",
      controlDeviceThreshold: "Жады тапшылығы шегі",
      controlDeviceMemoryState: "Жады күйі",
      controlDeviceMemoryNormal: "Қалыпты",
      controlDeviceMemoryLow: "Таршылық",
      controlDeviceUptimeDays: "{d} күн {h} сағ",
      controlDeviceUptimeHours: "{h} сағ {m} мин",
      controlDeviceUptimeMinutes: "{m} мин"
    }
  };

  const successKeys = {
    SAVED: "saved",
    SUBJECT_SAVED: "memberSaved",
    SUBJECT_ADDED: "memberAdded",
    SUBJECT_DELETED: "memberDeleted",
    SUBJECT_SWITCHED: "memberSwitched",
    MEMBER_SWITCH_SAVED: "memberSwitchSaved",
    USER_CONFIG_PIN_SAVED: "userConfigPinSaved",
    SAVED_NEXT_SESSION: "savedNextSession",
    APP_SCOPE_SAVED: "savedAppScope",
    QUIZ_ENTRY_SAVED: "savedQuiz",
    SOURCE_SAVED: "savedSource",
    PIN_SAVED: "savedPassword",
    PIN_SAVED_SYNC_PENDING: "savedPasswordPending",
    AUTO_UPDATE_SAVED: "savedAutoUpdate",
    SESSION_ENDED: "savedSessionEnded",
    CHILD_MODE_ON: "childOnSaved",
    CHILD_MODE_OFF: "childOffSaved",
    INTRO_VIDEO_SELECTED: "introVideoSelected",
    INTRO_VIDEO_DELETED: "introVideoDeleted",
    INTRO_VIDEO_RESTORED: "introVideoRestored",
    MEDIA_DOWNLOAD_STARTED: "mediaDownloadStarted",
    MEDIA_ALREADY_READY: "mediaSelected",
    MEDIA_DOWNLOADING: "mediaDownloading",
    MEDIA_SELECTED: "mediaSelected",
    MEDIA_DELETED: "mediaDeleted",
    MEDIA_CANCELLED: "mediaCancelled",
    MEDIA_REFRESHING: "mediaRefreshing",
    MEDIA_PLAYLIST_SAVED: "mediaPlaylistSaved",
    MEDIA_SOUND_SAVED: "mediaSoundSaved",
    AUDIO_SELECTED: "audioSelected",
    AUDIO_DELETED: "audioDeleted",
    AUDIO_REFRESHING: "audioRefreshing",
    AUDIO_DOWNLOAD_STARTED: "audioDownloadStarted",
    AUDIO_ALREADY_READY: "audioDownloaded",
    AUDIO_DOWNLOADING: "audioDownloadStarted",
    AUDIO_CANCELLED: "audioDownloadCancelled",
    CONSENT_SAVED: "saved",
    REMOTE_STARTED: "remoteWaiting",
    REMOTE_STOPPED: "saved",
    REMOTE_REFRESHED: "saved",
    HANDOFF_DONE: "remoteDone"
  };

  const errorKeys = {
    PIN_LOCKED: "pinLocked",
    PIN_INVALID: "pinLength",
    PIN_REQUIRED: "pinLength",
    OLD_PIN_REQUIRED: "pinLength",
    SETUP_PIN_REQUIRED: "pinLength",
    WRONG_PIN: "wrongPin",
    SENSITIVE_REQUIRED: "sensitivePinRequired",
    SENSITIVE_EXPIRED: "sensitivePinExpired",
    SENSITIVE_SCOPE: "sensitivePinRequired",
    PIN_UNCHANGED: "pinUnchanged",
    PIN_TOO_SIMPLE: "pinTooSimple",
    PIN_SETUP_FAILED: "unknownError",
    PIN_NOT_SET: "childSetupPinLead",
    STATE_CHANGED: "stateChanged",
    READ_ONLY: "readOnlyCloud",
    LOCAL_ONLY: "stateChanged",
    FAMILY_NOT_READY: "familyNotReady",
    SUBJECT_REQUIRED: "memberNotFound",
    SUBJECT_NOT_FOUND: "memberNotFound",
    SUBJECT_NAME_INVALID: "memberNameInvalid",
    SUBJECT_AVATAR_INVALID: "memberImageInvalid",
    SUBJECT_AVATAR_EMPTY: "memberImageInvalid",
    SUBJECT_AVATAR_TOO_LARGE: "memberImageTooLarge",
    SUBJECT_AVATAR_WRITE_FAILED: "memberImageSaveFailed",
    SUBJECT_AVATAR_NOT_FOUND: "memberImageInvalid",
    SUBJECT_GRADE_INVALID: "memberGradeInvalid",
    SUBJECT_PIN_INVALID: "userPinInvalid",
    SUBJECT_PIN_REQUIRED: "userPinRequired",
    SUBJECT_ACTIVE: "familyDeleteActiveHint",
    SUBJECT_DEFAULT: "familyDeleteDefaultHint",
    SUBJECT_LAST: "memberDeleteLast",
    TV_BUSY: "tvBusy",
    SUBJECT_UPDATE_FAILED: "memberImageSaveFailed",
    SUBJECT_ADD_FAILED: "memberAddFailed",
    SUBJECT_DELETE_FAILED: "memberDeleteFailed",
    CONTENT_TYPE: "memberImageType",
    REQUEST_HEADER: "unknownError",
    RULES_OVERLAP: "rulesOverlap",
    RULES_TOO_MANY_WINDOWS: "rulesTooMany",
    RULES_INVALID_APPS: "rulesApps",
    RULES_INVALID_STUDY_APPS: "rulesApps",
    RULES_INVALID_STUDY_EARN: "studyEarnInvalid",
    RULES_INVALID_DAY_GROUPS: "rulesDayGroups",
    RULES_INVALID_UNLIMITED_APPS: "rulesUnlimitedApps",
    RULES_INVALID_ROUTE_COUNTS: "rulesInvalid",
    RULES_INVALID_DIRECT_SESSION: "rulesInvalid",
    RULES_INVALID_SESSION_CAP: "rulesInvalid",
    RULES_CLIENT_OUTDATED: "rulesClientOutdated",
    RULES_INVALID: "rulesInvalid",
    QUIZ_SESSION_RUNNING: "quizRunning",
    SAVE_FAILED: "unknownError",
    TIMEOUT: "timeout",
    PENDING_MUST_BE_CANCELED: "pendingSwitch",
    CLOUD_SETTLEMENT_FAILED: "cloudSettlement",
    WEB_EDITING: "gateWebEditing",
    LEASE_TAKEN_OVER: "gateTakenOver",
    LEASE_EXPIRED: "editorLost",
    EDITOR_REQUIRED: "editorLost",
    QUIZ_TAKEN_OVER: "quizTakenOver",
    PAIR_UNAVAILABLE: "pairUnavailable",
    PAIR_CODE_INVALID: "pairInvalid",
    PAIR_REQUIRED: "pairUnavailable",
    VERIFY_REQUIRED: "verifyExpired",
    UNLOCK_REQUIRED: "unlockRequired",
    QUIZ_LOGIN_REQUIRED: "quizLoginRequired",
    CHILD_MODE_REQUIRED: "remoteNoChild",
    CONSENT_REQUIRED: "remoteConsentLead",
    CLOUD_DISABLED: "remoteUnavailable",
    DEVICE_NOT_READY: "gateNotReadyLead",
    NOT_BOUND: "unknownError",
    HANDOFF_BUSY: "pendingSwitch",
    HANDOFF_NO_RULES: "rulesInvalid",
    HANDOFF_FAILED: "unknownError",
    SESSION_NOT_ACTIVE: "sessionIdle",
    UPDATE_BUSY: "updateBusy",
    UPDATE_NONE: "updateNotChecked",
    UPDATE_UNSUPPORTED: "updateUnavailable",
    CONFIRM_REQUIRED: "unknownError",
    MODE_NOT_NONE: "stateChanged",
    NOT_READY: "gateNotReadyLead",
    TV_UNLOCK_REQUIRED: "entryCloudUnlock",
    OFFLINE: "network",
    SCREEN_OFF: "entryLocalBlocked",
    STUDY_MODE: "entryLocalBlocked",
    QUIZ_ACTIVE: "entryLocalBlocked",
    NOT_ON_LOCK_SCREEN: "entryLocalUnlock",
    DEFAULT_PIN: "passwordDefault",
    NETWORK: "network",
    REMOTE_REFRESH_FAILED: "remoteRefreshFailed",
    INTRO_VIDEO_TOO_LARGE: "introVideoTooLarge",
    INTRO_VIDEO_BAD_DURATION: "introVideoBadDuration",
    INTRO_VIDEO_BAD_FORMAT: "introVideoBadFormat",
    INTRO_VIDEO_BAD_CODEC: "introVideoBadFormat",
    INTRO_VIDEO_HEVC: "introVideoHevc",
    INTRO_VIDEO_UNSUPPORTED_CODEC: "introVideoUnsupportedCodec",
    INTRO_VIDEO_BAD_RATIO: "introVideoBadFormat",
    INTRO_VIDEO_BAD_FPS: "introVideoBadFormat",
    INTRO_VIDEO_BAD_RESOLUTION: "introVideoBadFormat",
    INTRO_VIDEO_INVALID: "introVideoInvalid",
    INTRO_VIDEO_NO_SPACE: "introVideoNoSpace",
    INTRO_VIDEO_NOT_FOUND: "introVideoInvalid",
    INTRO_IMAGE_TOO_LARGE: "mediaImageTooLarge",
    INTRO_IMAGE_BAD_FORMAT: "mediaImageBadFormat",
    INTRO_IMAGE_BAD_RATIO: "mediaImageInvalid",
    INTRO_IMAGE_INVALID: "mediaImageInvalid",
    LENGTH_REQUIRED: "introVideoInvalid",
    MEDIA_NOT_FOUND: "mediaNotFound",
    MEDIA_NOT_READY: "mediaNotReady",
    MEDIA_BUSY: "mediaBusy",
    MEDIA_IN_USE: "mediaBusy",
    MEDIA_ROLE_INVALID: "mediaRoleInvalid",
    MEDIA_NO_SPACE: "mediaNoSpace",
    MEDIA_DELETE_FAILED: "mediaDeleteFailed",
    MEDIA_DOWNLOAD_FAILED: "mediaDownloadFailed",
    MEDIA_PLAYLIST_TOO_LONG: "mediaLockLimitHint",
    MEDIA_PLAYLIST_INVALID: "mediaLockInvalid",
    MEDIA_SOUND_UNAVAILABLE: "mediaSoundUnavailable",
    MEDIA_BAD_FORMAT: "mediaBadFormat",
    MEDIA_TOO_LARGE: "mediaTooLarge",
    AUDIO_TOO_LARGE: "audioTooLarge",
    AUDIO_BAD_FORMAT: "audioBadFormat",
    AUDIO_BAD_DURATION: "audioBadDuration",
    AUDIO_UNSUPPORTED_CODEC: "audioUnsupported",
    AUDIO_INVALID: "audioBadFormat",
    AUDIO_NO_SPACE: "audioNoSpace",
    AUDIO_NOT_FOUND: "mediaNotFound",
    AUDIO_DELETE_FAILED: "mediaDeleteFailed",
    AUDIO_CATALOG_UNAVAILABLE: "audioCatalogOff",
    AUDIO_BUSY: "audioBusy",
    AUDIO_NETWORK: "audioDownloadFailed",
    AUDIO_LENGTH_REQUIRED: "audioDownloadFailed",
    AUDIO_CHECKSUM_MISMATCH: "audioDownloadFailed",
    AUDIO_DOWNLOAD_FAILED: "audioDownloadFailed",
    AUDIO_CANCELLED: "audioDownloadCancelled",
    CATALOG_UNAVAILABLE: "mediaCatalogUnavailable"
  };

  let host = null;
  let bootstrap = null;
  let lang = "zh";
  let gate = null;
  let snap = null;
  let editorToken = null;
  let entryToken = null;
  let heartbeatTimer = 0;
  let rulesTimer = 0;
  let pendingRules = null;
  let savingRules = false;
  // A cancelled PIN re-verification must not pop the sheet again on its own; the next
  // deliberate edit clears the hold. The release flag defers dropping the proof until the
  // last pending rules write has settled.
  let rulesHold = false;
  let releaseRulesWhenIdle = false;
  let picker = null;
  let studyEarnCatalog = null;
  let studyEarnLoading = false;
  let subpage = null;
  // V48: the entertainment page splits into app management and the time plan. Both panels
  // are rendered so a save can repaint either side; only the selected one is visible.
  let timeTab = "manage";
  let personalizeLoading = false;
  let personalizeRefreshing = false;
  let mediaPollTimer = 0;
  let mediaSheetMode = null;
  let mediaSheetId = null;
  let mediaPickerRole = "quiz";
  let mediaPickerChoice = "";
  // The lock picker is multi-select; order is the order the child sees. quiz keeps one choice.
  let mediaLockChoices = [];
  let mediaLockInterval = 0;
  let mediaPickerScrollPending = false;
  let audioUploading = false;
  let mediaDownloadWatch = null;
  let audioDownloadWatch = null;
  let audioDownloadWatchFrom = "";
  let mediaPosterEpoch = 0;
  let mediaKind = "video";
  let mediaSource = "cloud";
  let audioSource = "cloud";
  const mediaPosterUrls = new Map();
  const mediaPosterWaiters = new Map();
  const mediaPosterLoading = new Set();
  let uploadState = null;
  let notice = null;
  let errorNotice = null;
  let rootOpen = false;
  let lastActiveElement = null;
  let pendingPairCode = "";
  let appPromptSeenMemory = false;
  // V41: the overlay is one of two top-level tabs. "system" is the old More surface;
  // "user" edits the selected member's own settings.
  let rootMode = "user";
  // The quiz page can open a full-page form above the overlay (AI setup, bank manage).
  // Suspending keeps the editor lease and the exact page alive underneath.
  let rootSuspended = false;
  let suspendedMode = "user";
  let suspendedSubpage = null;
  let leaseRecovering = false;
  let leaseRecoverySerial = 0;
  let controlBusy = null;
  let controlNotice = null;
  let controlNoticeError = false;
  let controlManualMinutes = 20;
  let controlRefs = null;
  let controlDeviceOpen = false;
  let controlDeviceData = null;
  let controlDeviceLoading = false;
  let controlDeviceError = false;
  let controlDeviceTimer = 0;
  let controlDeviceRefs = null;
  let controlDeviceEpoch = 0;
  const controlRequestIds = Object.create(null);
  // Short-lived PIN proofs live only in this tab's memory. They are never written to
  // localStorage, the URL, or a request log; the server stores only their hashes.
  const sensitiveTokens = Object.create(null);
  let sheetDismiss = null;

  function t(key, vars) {
    const table = TEXTS[lang] || TEXTS.en;
    const control = CONTROL_TEXTS[lang] || CONTROL_TEXTS.en;
    let value = table[key] || control[key] || TEXTS.en[key] || CONTROL_TEXTS.en[key] || key;
    if (vars) {
      for (const name of Object.keys(vars)) {
        value = value.split("{" + name + "}").join(String(vars[name]));
      }
    }
    return value;
  }

  function $(id) { return document.getElementById(id); }

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text);
    return node;
  }

  function button(text, className, onClick) {
    const node = el("button", className || "parent-btn", text);
    node.type = "button";
    if (onClick) node.addEventListener("click", onClick);
    return node;
  }

  function clear(node) {
    while (node && node.firstChild) node.removeChild(node.firstChild);
  }

  function storageKey() {
    return "parent.editor:" + location.host + location.pathname.replace(/\/quiz\/?$/, "/");
  }

  function storeEditor(token) {
    editorToken = token;
    try {
      if (token) sessionStorage.setItem(storageKey(), token);
      else sessionStorage.removeItem(storageKey());
    } catch (_) { /* private mode */ }
  }

  function loadStoredEditor() {
    try { return sessionStorage.getItem(storageKey()) || ""; } catch (_) { return ""; }
  }

  function errorText(error) {
    if (!error) return t("unknownError");
    // quiz.js's host bridge throws plain Error("NETWORK") for transport failures and
    // Error(code) for HTTP errors, so the code may live on message instead of code.
    const code = error.code || error.message;
    if (code === "PIN_LOCKED") {
      const seconds = Number(error.data && error.data.retryAfterSeconds) || 0;
      return seconds > 0 ? t("pinLockedRetry", { n: seconds }) : t("pinLocked");
    }
    const key = errorKeys[code];
    if (key) return t(key);
    if (code === "HTTP_404") return t("featureUnavailable");
    if (code && String(code).indexOf("HTTP_") === 0) return t("network");
    return t("unknownError");
  }

  function successText(messageKey) {
    const key = successKeys[messageKey];
    return key ? t(key) : t("saved");
  }

  function statusLine(entryGate) {
    const g = entryGate || gate;
    if (!g) return t("entryChecking");
    if (g.mode === "NONE") return t("entryPair");
    if (g.status === "NOT_READY") return t("entryNotReady");
    if (g.mode === "CLOUD") {
      if (g.status === "READY") return t("entryCloudReady");
      if (g.status === "VERIFY") return t("entryCloudVerify");
      return t("entryCloudUnlock");
    }
    if (g.status === "READY") return t("entryLocalReady");
    if (g.status === "UNLOCK") return t("entryLocalUnlock");
    if (g.status === "VERIFY") return t("entryLocalVerify");
    return t("entryLocalBlocked");
  }

  function guardText(value) {
    if (value === "ENFORCING" || value === "ACTIVE") return t("guardEnforcing");
    if (value === "PARENT_OVERRIDE") return t("guardOverride");
    return t("guardOther");
  }

  function modeText(value) {
    return value === "CLOUD" ? t("remoteMode") : value === "NONE" ? t("childOff") : t("localMode");
  }

  function minutes(seconds) {
    return Math.max(0, Math.round((Number(seconds) || 0) / 60));
  }

  /**
   * A mouse-friendly clock field: an hour dropdown and a minute dropdown instead of the
   * browser's dial. Minutes are listed one by one so a stored value such as 01:01 survives.
   * The end field may also pick 24:00, the rule engine's end-of-day bound.
   */
  function timeField(minutesValue, label, allowEndOfDay) {
    const wrap = el("div", "parent-time-pick");
    const hour = el("select", "parent-time-select");
    hour.setAttribute("aria-label", label);
    const lastHour = allowEndOfDay ? 24 : 23;
    for (let h = 0; h <= lastHour; h++) {
      const option = el("option", null, String(h).padStart(2, "0"));
      option.value = String(h);
      hour.appendChild(option);
    }
    const minute = el("select", "parent-time-select");
    minute.setAttribute("aria-label", label);
    for (let m = 0; m < 60; m++) {
      const option = el("option", null, String(m).padStart(2, "0"));
      option.value = String(m);
      minute.appendChild(option);
    }
    wrap.appendChild(hour);
    wrap.appendChild(el("span", "parent-time-colon", ":"));
    wrap.appendChild(minute);
    const sync = (value) => {
      const total = Math.max(0, Math.min(allowEndOfDay ? 1440 : 1439, Number(value) || 0));
      hour.value = String(Math.floor(total / 60));
      minute.value = String(total % 60);
      minute.disabled = total === 1440;
    };
    sync(minutesValue);
    hour.addEventListener("change", () => {
      const endOfDay = Number(hour.value) === 24;
      if (endOfDay) minute.value = "0";
      minute.disabled = endOfDay;
    });
    return {
      node: wrap,
      get: () => Number(hour.value) * 60 + (Number(hour.value) === 24 ? 0 : Number(minute.value)),
      set: sync,
      onchange: (handler) => {
        hour.addEventListener("change", handler);
        minute.addEventListener("change", handler);
      },
    };
  }

  function setNotice(message, isError) {
    if (isError) {
      errorNotice = message;
      notice = null;
    } else {
      notice = message;
      errorNotice = null;
    }
    const ok = $("parentNotice");
    const bad = $("parentErrorNotice");
    if (ok) {
      ok.textContent = isError ? "" : (message || "");
      ok.classList.toggle("hidden", !message || isError);
    }
    if (bad) {
      bad.textContent = isError ? (message || "") : "";
      bad.classList.toggle("hidden", !message || !isError);
    }
  }

  function clearNotices() {
    notice = null;
    errorNotice = null;
    const ok = $("parentNotice");
    const bad = $("parentErrorNotice");
    if (ok) { ok.textContent = ""; ok.classList.add("hidden"); }
    if (bad) { bad.textContent = ""; bad.classList.add("hidden"); }
  }

  /** Recreates the pair of notice nodes on whichever page is currently rendered. */
  function noticeArea() {
    const fragment = document.createDocumentFragment();
    const ok = el("p", "parent-ok hidden");
    ok.id = "parentNotice";
    const bad = el("p", "parent-error hidden");
    bad.id = "parentErrorNotice";
    fragment.appendChild(ok);
    fragment.appendChild(bad);
    if (notice) {
      ok.textContent = notice;
      ok.classList.remove("hidden");
    }
    if (errorNotice) {
      bad.textContent = errorNotice;
      bad.classList.remove("hidden");
    }
    return fragment;
  }

  // ---------------------------------------------------------------- entry card

  function init(h, b) {
    if (!h || !b) return;
    host = h;
    bootstrap = b;
    lang = LANGS.includes(h.lang()) ? h.lang() : "zh";
    gate = b.gate || null;
    const card = $("parentEntryCard");
    if (card) card.classList.remove("hidden");
    refreshEntry();
  }

  async function refreshEntry() {
    if (!host) return;
    const target = $("parentEntryState");
    if (!target) return;
    if (!gate) target.textContent = t("entryChecking");
    try {
      const data = await host.request("/api/parent/gate");
      if (data && data.gate) {
        gate = data.gate;
        target.textContent = statusLine(gate);
      }
    } catch (_) {
      target.textContent = t("entryChecking");
    }
  }

  function setLang(value) {
    const next = LANGS.includes(value) ? value : "zh";
    if (next === lang) return;
    lang = next;
    applyStaticText();
    const target = $("parentEntryState");
    if (target) target.textContent = statusLine(gate);
    if (rootOpen) render();
  }

  function applyStaticText() {
    document.querySelectorAll("[data-parent-i18n]").forEach((node) => {
      node.textContent = t(node.dataset.parentI18n);
    });
  }

  // -------------------------------------------------------------------- open / close

  function normalizeRootMode(mode) {
    // "control" is the pre-V41 name for the TV-control surface, which now lives in system.
    return mode === "system" || mode === "control" ? "system" : "user";
  }

  function resumeOverlay() {
    if (!rootOpen) return;
    rootSuspended = false;
    const root = $(ROOT_ID);
    if (root) root.classList.remove("hidden");
    document.body.classList.add("modal-open", "parent-open");
  }

  function suspend() {
    if (!rootOpen || rootSuspended) return;
    rootSuspended = true;
    suspendedMode = rootMode;
    suspendedSubpage = subpage;
    const root = $(ROOT_ID);
    if (root) root.classList.add("hidden");
    document.body.classList.remove("modal-open", "parent-open");
  }

  /**
   * Resumes a suspended overlay on the page it had. AI setup and bank maintenance call
   * suspend(), then open() when their full-page form closes, so the parent never loses the
   * page they were on. A plain open with nothing suspended lands on the user tab.
   */
  async function open() {
    if (!host) return;
    if (rootOpen && rootSuspended) {
      resumeOverlay();
      rootMode = normalizeRootMode(suspendedMode);
      subpage = suspendedSubpage;
      suspendedSubpage = null;
      clearNotices();
      if (editorToken) {
        try {
          await refreshSnapshot();
        } catch (error) {
          handleLeaseError(error);
          return;
        }
        render();
      } else {
        await openGate();
      }
      return;
    }
    return openRoot("user");
  }

  async function openUser() {
    return openRoot("user");
  }

  async function openSystem() {
    return openRoot("system");
  }

  async function openControl() {
    // Child mode actions live on the system page itself now; the native shell's entry
    // should land there instead of opening a page that no longer exists.
    return openRoot("system");
  }

  async function openRoot(mode, targetSubpage) {
    if (!host) return;
    const nextMode = normalizeRootMode(mode);
    rootMode = nextMode;
    controlNotice = null;
    controlNoticeError = false;
    resetControlDevice();
    if (rootOpen) {
      if (rootSuspended) resumeOverlay();
      // Switching top-level tabs reuses the editor lease, but neither surface may inherit
      // a PIN proof or a subpage from the other.
      releaseAllSensitive();
      clearSensitiveMemory();
      subpage = targetSubpage || null;
      if (editorToken) {
        try {
          await refreshSnapshot();
        } catch (error) {
          handleLeaseError(error);
          return;
        }
        render();
      } else {
        await openGate();
      }
      return;
    }
    const root = $(ROOT_ID);
    if (!root) return;
    lastActiveElement = document.activeElement;
    rootOpen = true;
    rootSuspended = false;
    subpage = targetSubpage || null;
    root.classList.remove("hidden");
    document.body.classList.add("modal-open", "parent-open");
    clearNotices();
    renderLoading();
    const stored = loadStoredEditor();
    if (stored) {
      editorToken = stored;
      try {
        await refreshSnapshot();
        startHeartbeat();
        render();
        return;
      } catch (error) {
        // A stored editor token cannot survive a TV process restart. Fall through to a
        // fresh gate/acquire instead of a dead-end "editor lost" card.
        stopHeartbeat();
        storeEditor("");
      }
    }
    await openGate();
  }

  function renderLoading() {
    const root = $(ROOT_ID);
    if (!root) return;
    clear(root);
    const shell = el("div", "parent-shell");
    // Loading is a top-level tab, not the parent center: show the tab's own title so
    // entering user/system settings never flashes "家长中心" and never draws a back button.
    const content = el("div", "parent-content parent-more-content");
    const head = el("div", "parent-more-head");
    head.appendChild(el(
      "h1",
      "parent-more-title",
      t(rootMode === "system" ? "systemConfigTitle" : "userConfigTitle"),
    ));
    content.appendChild(head);
    content.appendChild(el("p", "parent-empty", t("loading")));
    shell.appendChild(content);
    root.appendChild(shell);
  }

  async function close() {
    stopHeartbeat();
    resetControlDevice();
    leaseRecoverySerial += 1;
    leaseRecovering = false;
    stopMediaPoll();
    mediaSheetId = null;
    revokeMediaPosters();
    if (rulesTimer) { clearTimeout(rulesTimer); rulesTimer = 0; }
    pendingRules = null;
    rulesHold = false;
    releaseRulesWhenIdle = false;
    // The proofs are bound to this editor lease, but releasing them explicitly means a
    // reopened page can never reuse a proof the parent granted for the previous visit.
    releaseAllSensitive(true);
    clearSensitiveMemory();
    const token = editorToken;
    stopEditor(token, true);
    closeSheet();
    const root = $(ROOT_ID);
    if (root) {
      root.classList.add("hidden");
      clear(root);
    }
    rootOpen = false;
    rootSuspended = false;
    suspendedSubpage = null;
    document.body.classList.remove("modal-open", "parent-open");
    revokeAppIcons();
    subpage = null;
    picker = null;
    host.refreshQuizState();
    if (lastActiveElement && document.body.contains(lastActiveElement)) {
      try { lastActiveElement.focus(); } catch (_) { /* ignore */ }
    }
  }

  function confirmClose() {
    if (editorToken) {
      confirmSheet(t("leaveTitle"), t("leaveText"), t("leaveConfirm"), close, t("cancel"));
    } else {
      close();
    }
  }

  function stopEditor(token, release) {
    storeEditor("");
    if (token && release && host) {
      host.request("/api/parent/release", {
        method: "POST",
        body: {},
        editorToken: token,
        keepalive: true,
        quizAuth: false,
      }).catch(() => { /* best effort; the TTL covers it */ });
    }
  }

  // --------------------------------------------------------------------- gate flow

  async function openGate() {
    entryToken = null;
    try {
      const data = await host.request("/api/parent/gate");
      gate = data && data.gate ? data.gate : null;
    } catch (_) {
      gate = null;
    }
    if (!gate) {
      renderGateError(t("network"), () => openGate());
      return;
    }
    // V29 has exactly one login: the normal six-digit quiz parent PIN. A READY gate without a
    // quiz session is rejected by acquire() with QUIZ_LOGIN_REQUIRED and shows that prompt.
    if (gate.status === "READY") {
      await acquire(null);
      return;
    }
    if (gate.status === "NOT_READY") {
      renderGateError(t("gateNotReadyLead"), () => openGate(), t("gateNotReadyTitle"));
      return;
    }
    return renderBlockedGate(reasonText(gate.reason));
  }

  function reasonText(reason) {
    const key = errorKeys[reason];
    if (key) return t(key);
    if (reason === "SCREEN_OFF" || reason === "STUDY_MODE" || reason === "QUIZ_ACTIVE" ||
        reason === "NOT_ON_LOCK_SCREEN" || reason === "DEFAULT_PIN" ||
        reason === "PIN_NOT_SET" || reason === "SETUP_REQUIRED") {
      return t("entryLocalBlocked");
    }
    return t("entryLocalBlocked");
  }

  function gateShell(title, lead, icon) {
    const root = $(ROOT_ID);
    clear(root);
    const shell = el("div", "parent-shell");
    // The gate keeps the identity of the tab that opened it, but both tabs can always
    // close back to the quiz page.
    const barTitle = rootMode === "system" ? t("systemConfigTitle") : t("userConfigTitle");
    shell.appendChild(barNode(barTitle, statusLine(gate), confirmClose));
    const content = el("div", "parent-content");
    const card = el("div", "parent-card parent-gate-card");
    card.appendChild(el("div", "parent-gate-icon", icon || "🔒"));
    card.appendChild(el("h1", "parent-gate-title", title));
    card.appendChild(el("p", "parent-gate-lead", lead));
    content.appendChild(card);
    shell.appendChild(content);
    root.appendChild(shell);
    return content;
  }

  function barNode(title, state, onBack) {
    const bar = el("div", "parent-bar");
    if (onBack) {
      const back = button("‹", "parent-back", onBack);
      back.setAttribute("aria-label", t("back"));
      bar.appendChild(back);
    }
    const main = el("div", "parent-bar-main");
    main.appendChild(el("span", "parent-bar-title", title));
    if (state) main.appendChild(el("span", "parent-bar-state", state));
    bar.appendChild(main);
    return bar;
  }

  function renderUnlockGate() {
    const content = gateShell(t("gateLockedTitle"), t("gateLockedLead"), "🔒");
    const actions = el("div", "parent-actions");
    actions.appendChild(button(t("gateUnlockButton"), "parent-btn primary", () => promptUnlock()));
    content.appendChild(actions);
    content.appendChild(el("p", "parent-fineprint", t("unlockFineprint")));
  }

  function renderVerifyGate() {
    const content = gateShell(t("gateVerifyTitle"), t("gateVerifyLead"), "🔑");
    const actions = el("div", "parent-actions");
    actions.appendChild(button(t("pinSubmit"), "parent-btn primary", () => promptVerify()));
    content.appendChild(actions);
  }

  function renderPairGate() {
    const content = gateShell(t("gatePairTitle"), t("gatePairLead"), "🔗");
    const field = el("div", "parent-field");
    const label = el("label", "parent-field-label", t("pairCodeLabel"));
    label.setAttribute("for", "parentPairCode");
    const input = el("input", "parent-code-input");
    input.id = "parentPairCode";
    input.type = "text";
    input.inputMode = "numeric";
    input.autocomplete = "one-time-code";
    input.maxLength = 8;
    input.pattern = "[0-9]*";
    if (pendingPairCode) {
      input.value = pendingPairCode;
      pendingPairCode = "";
    }
    field.appendChild(label);
    field.appendChild(input);
    content.appendChild(field);
    const actions = el("div", "parent-actions");
    const submit = button(t("pairSubmit"), "parent-btn primary", async () => {
      const code = input.value.replace(/\D/g, "");
      if (code.length !== 8) {
        setNotice(t("pairInvalid"), true);
        return;
      }
      submit.disabled = true;
      clearNotices();
      try {
        const data = await host.request("/api/parent/pair", {
          method: "POST",
          body: { code: code },
          quizAuth: false,
        });
        entryToken = data.entryToken;
        await acquire(entryToken);
      } catch (error) {
        setNotice(pairError(error), true);
        if (error && (error.code === "PAIR_UNAVAILABLE" || error.code === "PAIR_REQUIRED")) {
          // The TV may have rotated to a new code; keep the field but refresh the gate.
          refreshEntry();
        }
      } finally {
        submit.disabled = false;
      }
    });
    actions.appendChild(submit);
    content.appendChild(actions);
    content.appendChild(el("p", "parent-fineprint", t("pairHint")));
    setTimeout(() => { if (rootOpen && document.getElementById("parentPairCode")) input.focus(); }, 60);
  }

  function pairError(error) {
    if (!error) return t("unknownError");
    if (error.code === "PAIR_UNAVAILABLE" || error.code === "PAIR_REQUIRED") {
      return t("pairUnavailable");
    }
    if (error.code === "PAIR_CODE_INVALID") return t("pairInvalid");
    return errorText(error);
  }

  function renderBlockedGate(reason) {
    const content = gateShell(t("gateBlockedTitle"), reason, "⏳");
    const actions = el("div", "parent-actions");
    actions.appendChild(button(t("retry"), "parent-btn primary", () => openGate()));
    content.appendChild(actions);
    if (gate && gate.mode === "CLOUD") {
      content.appendChild(el("p", "parent-fineprint", t("gateLockedLead")));
    }
  }

  function renderQuizLoginGate(message) {
    const content = gateShell(t("gateBlockedTitle"), message, "🔑");
    const actions = el("div", "parent-actions");
    actions.appendChild(button(t("gateQuizLoginButton"), "parent-btn primary", () => {
      close().then(() => {
        if (host && host.openQuizLogin) host.openQuizLogin(rootMode);
      });
    }));
    content.appendChild(actions);
  }

  /**
   * Two phones may edit the same TV. The second one takes over explicitly; the first page
   * keeps its token until its next request and then lands on the editor-lost gate.
   */
  function renderWebTakeoverGate() {
    const content = gateShell(t("gateWebEditing"), t("gateTakenOverHint"), "👤");
    const actions = el("div", "parent-actions");
    actions.appendChild(button(t("gateTakeoverButton"), "parent-btn primary", () => acquire(null, true)));
    actions.appendChild(button(t("cancel"), "parent-btn ghost", confirmClose));
    content.appendChild(actions);
  }

  function renderGateError(message, retry, title) {
    const content = gateShell(title || t("gateNotReadyTitle"), message, "⚠️");
    const actions = el("div", "parent-actions");
    actions.appendChild(button(t("retry"), "parent-btn primary", retry));
    content.appendChild(actions);
  }

  function appPromptSeen() {
    if (appPromptSeenMemory) return true;
    try { return localStorage.getItem(APP_PROMPT_KEY) === "1"; } catch (_) { return false; }
  }

  function markAppPromptSeen() {
    appPromptSeenMemory = true;
    try { localStorage.setItem(APP_PROMPT_KEY, "1"); } catch (_) { /* private mode */ }
  }

  function maybePromptParentApp() {
    if (!host || typeof host.openParentAppDownload !== "function") return;
    // The system tab already has an explicit parent-app row; do not cover it with a nudge.
    if (rootMode === "system") return;
    if (window.ParentShell || window.__pytvParentShell) return;
    if (appPromptSeen()) return;
    // Let the editor paint first; this is a first-entry nudge, not a gate.
    setTimeout(() => {
      if (!rootOpen || appPromptSeen() || $(SHEET_ID)) return;
      markAppPromptSeen();
      openSheet((sheet, close) => {
        sheet.appendChild(el("h2", null, t("parentAppQrTitle")));
        sheet.appendChild(el("p", null, t("parentAppPromptLead")));
        let limitNote = null;
        const actions = el("div", "parent-actions");
        actions.appendChild(button(t("parentAppPromptLater"), "parent-btn ghost", close));
        actions.appendChild(button(t("parentAppPromptDownload"), "parent-btn primary", () => {
          let result = "unavailable";
          try { result = host.openParentAppDownload(); } catch (_) { result = "unavailable"; }
          if (result === "limited") {
            if (!limitNote) {
              limitNote = el("p", "parent-error");
              sheet.appendChild(limitNote);
            }
            limitNote.textContent = t("parentAppDownloadLimit");
            return;
          }
          if (result === "opened") close();
        }));
        sheet.appendChild(actions);
      });
    }, 350);
  }

  async function acquire(token, takeover) {
    try {
      const data = await host.request("/api/parent/acquire", {
        method: "POST",
        body: {},
        editorToken: "",
        entryToken: token || "",
        quizAuth: true,
        takeover: Boolean(takeover),
      });
      storeEditor(data.editorToken || "");
      if (data.gate) gate = data.gate;
      await refreshSnapshot();
      startHeartbeat();
      subpage = null;
      render();
      maybePromptParentApp();
    } catch (error) {
      if (!error) {
        renderGateError(t("unknownError"), () => openGate());
        return;
      }
      switch (error.code) {
        case "WEB_EDITING":
          renderWebTakeoverGate();
          break;
        case "QUIZ_LOGIN_REQUIRED":
          renderQuizLoginGate(t("quizLoginRequired"));
          break;
        case "PAIR_REQUIRED":
          renderBlockedGate(t("pairUnavailable"));
          break;
        case "UNLOCK_REQUIRED":
          renderUnlockGate();
          break;
        case "VERIFY_REQUIRED":
          renderVerifyGate();
          break;
        case "NOT_READY":
          renderGateError(t("gateNotReadyLead"), () => openGate());
          break;
        default:
          renderGateError(errorText(error), () => openGate());
      }
    }
  }

  function promptPin(title, lead, onSubmit, submitText) {
    openSheet((sheet, close) => {
      sheet.appendChild(el("h2", null, title));
      sheet.appendChild(el("p", null, lead));
      const field = el("div", "parent-field");
      const label = el("label", "parent-field-label", t("pinLabel"));
      label.setAttribute("for", "parentPinInput");
      const input = el("input", "parent-pin-input");
      input.id = "parentPinInput";
      input.type = "password";
      input.inputMode = "numeric";
      input.autocomplete = "one-time-code";
      input.maxLength = 6;
      input.pattern = "[0-9]*";
      field.appendChild(label);
      field.appendChild(input);
      sheet.appendChild(field);
      const error = el("p", "parent-error hidden");
      sheet.appendChild(error);
      const actions = el("div", "parent-actions");
      const submit = button(submitText || t("pinSubmit"), "parent-btn primary", async () => {
        const pin = input.value.replace(/\D/g, "");
        if (pin.length !== 6) {
          error.textContent = t("pinLength");
          error.classList.remove("hidden");
          return;
        }
        submit.disabled = true;
        error.classList.add("hidden");
        try {
          await onSubmit(pin, close);
        } catch (err) {
          error.textContent = errorText(err);
          error.classList.remove("hidden");
        } finally {
          submit.disabled = false;
        }
      });
      actions.appendChild(submit);
      actions.appendChild(button(t("pinCancel"), "parent-btn ghost", close));
      sheet.appendChild(actions);
      setTimeout(() => { if (document.getElementById("parentPinInput")) input.focus(); }, 60);
    });
  }

  // --------------------------------------------------- V32 sensitive PIN proof

  function isSensitiveError(error) {
    const code = error && (error.code || error.message);
    return code === "SENSITIVE_REQUIRED" || code === "SENSITIVE_EXPIRED" ||
      code === "SENSITIVE_SCOPE";
  }

  function sensitiveToken(scope) {
    const record = sensitiveTokens[scope];
    if (!record || !record.token) return "";
    if (!(Number(record.expiresAtMs) > Date.now())) {
      delete sensitiveTokens[scope];
      return "";
    }
    return record.token;
  }

  function clearSensitiveMemory() {
    for (const scope of Object.keys(sensitiveTokens)) delete sensitiveTokens[scope];
  }

  function releaseSensitive(scope, keepalive) {
    const record = sensitiveTokens[scope];
    if (!record || !record.token) return;
    delete sensitiveTokens[scope];
    if (!host || !editorToken) return;
    host.request("/api/parent/sensitive/release", {
      method: "POST",
      body: { token: record.token },
      editorToken: editorToken,
      keepalive: Boolean(keepalive),
      quizAuth: false,
    }).catch(() => { /* TTL is the fallback */ });
  }

  function releaseAllSensitive(keepalive) {
    releaseSensitive("RULES_EDIT", keepalive);
    releaseSensitive("TAKE_OVER", keepalive);
    releaseSensitive("USER_CONFIG", keepalive);
  }

  /**
   * PIN sheet for one sensitive action. Returns the proof token, or "" when the parent
   * cancels. Errors stay in the sheet; a lockout counts down in place and disables submit.
   */
  function promptSensitivePin(scope, leadKey, expired) {
    return new Promise((resolve) => {
      let settled = false;
      let busy = false;
      let lockedFor = 0;
      let ticker = 0;
      const settle = (value, closeNow) => {
        if (settled) return;
        settled = true;
        if (ticker) { clearInterval(ticker); ticker = 0; }
        if (closeNow) {
          sheetDismiss = null;
          closeSheet();
        }
        resolve(value || "");
      };
      openSheet((sheet, close) => {
        sheet.appendChild(el("h2", null, t("sensitivePinTitle")));
        sheet.appendChild(el("p", null, expired ? t("sensitivePinExpired") : t(leadKey)));
        const field = el("div", "parent-field");
        const label = el("label", "parent-field-label", t("pinLabel"));
        label.setAttribute("for", "parentSensitivePinInput");
        const input = el("input", "parent-pin-input");
        input.id = "parentSensitivePinInput";
        input.type = "password";
        input.inputMode = "numeric";
        input.autocomplete = "one-time-code";
        input.maxLength = 6;
        input.pattern = "[0-9]*";
        input.addEventListener("input", () => {
          input.value = input.value.replace(/\D/g, "").slice(0, 6);
        });
        field.appendChild(label);
        field.appendChild(input);
        sheet.appendChild(field);
        const error = el("p", "parent-error hidden");
        sheet.appendChild(error);
        const actions = el("div", "parent-actions");
        const cancel = button(t("pinCancel"), "parent-btn ghost", () => settle("", true));
        const submit = button(t("pinSubmit"), "parent-btn primary", async () => {
          if (busy || lockedFor > 0) return;
          const pin = input.value.replace(/\D/g, "");
          if (pin.length !== 6) {
            error.textContent = t("pinLength");
            error.classList.remove("hidden");
            return;
          }
          busy = true;
          error.classList.add("hidden");
          refreshControls();
          try {
            const data = await host.request("/api/parent/sensitive/verify", {
              method: "POST",
              body: { scope: scope, pin: pin },
              editorToken: editorToken,
              quizAuth: true,
            });
            if (!data || !data.token) throw new Error("SENSITIVE_REQUIRED");
            sensitiveTokens[scope] = {
              token: data.token,
              expiresAtMs: Number(data.expiresAtMs) || 0,
            };
            settle(data.token, true);
          } catch (err) {
            if (isLeaseError(err)) {
              settle("", true);
              handleLeaseError(err);
              return;
            }
            const code = err && (err.code || err.message);
            if (code === "PIN_LOCKED") {
              lockFor(Number(err.data && err.data.retryAfterSeconds) || 600);
            } else if (code === "DEFAULT_PIN") {
              error.textContent = t("sensitivePinDefault");
              error.classList.remove("hidden");
            } else {
              error.textContent = errorText(err);
              error.classList.remove("hidden");
            }
          } finally {
            busy = false;
            if (!settled) refreshControls();
          }
        });
        actions.appendChild(cancel);
        actions.appendChild(submit);
        sheet.appendChild(actions);

        function refreshControls() {
          const locked = lockedFor > 0;
          input.disabled = busy || locked;
          submit.disabled = busy || locked;
          cancel.disabled = false;
        }

        function lockFor(seconds) {
          lockedFor = Math.max(1, Math.ceil(Number(seconds) || 1));
          error.textContent = t("pinLockedRetry", { n: lockedFor });
          error.classList.remove("hidden");
          refreshControls();
          if (ticker) clearInterval(ticker);
          ticker = setInterval(() => {
            if (!document.body.contains(sheet)) {
              clearInterval(ticker);
              ticker = 0;
              return;
            }
            lockedFor -= 1;
            if (lockedFor <= 0) {
              lockedFor = 0;
              clearInterval(ticker);
              ticker = 0;
              error.textContent = "";
              error.classList.add("hidden");
              if (!settled) {
                refreshControls();
                try { input.focus(); } catch (_) { /* ignore */ }
              }
            } else {
              error.textContent = t("pinLockedRetry", { n: lockedFor });
              refreshControls();
            }
          }, 1000);
        }

        refreshControls();
        setTimeout(() => {
          if (document.body.contains(input)) {
            try { input.focus(); } catch (_) { /* ignore */ }
          }
        }, 60);
      }, () => settle("", false));
    });
  }

  async function requestSensitiveToken(scope, leadKey) {
    const existing = sensitiveToken(scope);
    if (existing) return existing;
    if (!host || !editorToken) return "";
    return promptSensitivePin(scope, leadKey, false);
  }

  /** Writes one request with a scope proof; re-verifies once if the TV says it expired. */
  async function requestWithSensitive(path, options, scope, leadKey) {
    const token = await requestSensitiveToken(scope, leadKey);
    if (!token) return null;
    try {
      return await host.request(path, Object.assign({}, options, { sensitiveToken: token }));
    } catch (error) {
      if (!isSensitiveError(error)) throw error;
      delete sensitiveTokens[scope];
      const renewed = await promptSensitivePin(scope, leadKey, true);
      if (!renewed) throw error;
      return host.request(path, Object.assign({}, options, { sensitiveToken: renewed }));
    }
  }

  /**
   * Rules writes follow the V47 user-configuration switch. A pinless page goes straight to the
   * TV; if another phone just re-enabled the prompts, the server's SENSITIVE_* answer falls
   * back to the normal RULES_EDIT sheet. The server is always the authority.
   */
  async function requestUserConfigWrite(path, options) {
    if (host && snap && snap.userConfigRequirePin === false) {
      try {
        return await host.request(path, options);
      } catch (error) {
        if (!isSensitiveError(error)) throw error;
      }
    }
    return requestWithSensitive(path, options, "RULES_EDIT", "sensitivePinLeadRules");
  }

  async function promptUnlock() {
    // One unlock for both surfaces: prefer the quiz page's existing temporary-unlock dialog
    // so the phone never shows two different PIN sheets. The local sheet below is the
    // fallback for an older tab whose host bridge has no dialog, and it posts the same PIN
    // to the parent endpoint.
    if (host && typeof host.openUnlockDialog === "function") {
      let done = false;
      try { done = await host.openUnlockDialog(); } catch (_) { done = false; }
      if (done) {
        closeSheet();
        await openGate();
        return;
      }
    }
    promptPin(t("gateLockedTitle"), t("gateLockedLead"), async (pin) => {
      const data = await host.request("/api/parent/unlock", {
        method: "POST",
        body: { pin: pin },
        quizAuth: false,
      });
      if (data && data.gate) gate = data.gate;
      closeSheet();
      await acquire(null);
    }, t("gateUnlockButton"));
  }

  function promptVerify() {
    promptPin(t("gateVerifyTitle"), t("gateVerifyLead"), async (pin) => {
      const data = await host.request("/api/parent/verify", {
        method: "POST",
        body: { pin: pin },
        quizAuth: false,
      });
      if (data && data.gate) gate = data.gate;
      closeSheet();
      await acquire(data.entryToken || null);
    });
  }

  // ------------------------------------------------------------------- snapshot / lease

  async function refreshSnapshot() {
    const data = await host.request("/api/parent/snapshot", {
      editorToken: editorToken,
      quizAuth: true,
    });
    if (!data || !data.snapshot) throw new Error("INVALID_SNAPSHOT");
    // A heartbeat refresh must not visually clobber an unsaved rules draft; the next write
    // carries the refreshed revision and the debounce flush still owns the draft.
    adoptSnapshot(data.snapshot, Boolean(pendingRules));
    return snap;
  }

  function startHeartbeat() {
    stopHeartbeat();
    if (!editorToken) return;
    heartbeatTimer = setInterval(() => {
      if (document.hidden) return;
      heartbeat();
    }, HEARTBEAT_MS);
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = 0;
    }
  }

  async function heartbeat() {
    if (!editorToken) return;
    if (document.hidden || !rootOpen) return;
    const previous = snap;
    try {
      const data = await host.request("/api/parent/heartbeat", {
        method: "POST",
        body: {},
        editorToken: editorToken,
        quizAuth: true,
      });
      if (!previous || !snap) {
        await refreshSnapshot();
        render();
        return;
      }
      const changed = data.rulesRevision !== snap.rulesRevision ||
        data.modeRevision !== snap.modeRevision;
      // Only the pages that show live control data need a repaint every heartbeat.
      const controlSurface = (rootMode === "system" && !subpage) ||
        (rootMode === "user" && !subpage);
      if (controlSurface) {
        if (data && data.control) {
          if (!snap) await refreshSnapshot();
          else snap.control = data.control;
        }
        if (changed) {
          await refreshSnapshot();
          render();
        } else {
          paintControl();
        }
        return;
      }
      if (changed) {
        await refreshSnapshot();
        render();
      }
    } catch (error) {
      handleLeaseError(error);
    }
  }

  function isLeaseError(error) {
    const code = error && (error.code || error.message);
    return code === "QUIZ_TAKEN_OVER" || code === "LEASE_TAKEN_OVER" ||
      code === "EDITOR_REQUIRED" || code === "LEASE_EXPIRED" ||
      Boolean(error && error.status === 401);
  }

  /**
   * The TV keeps the editor lease and its quiz-token binding in memory, so a TV process
   * restart clears them while this phone still holds a valid quiz login. Re-acquire once
   * instead of showing a dead-end takeover error: a real takeover ends at the quiz login
   * gate or at the explicit two-tab takeover gate.
   */
  function handleLeaseError(error) {
    if (!error) return;
    // The TV invalidates every proof when the lease dies; forget them even if the request
    // that reported the failure never reached it.
    clearSensitiveMemory();
    if (!rootOpen || !isLeaseError(error)) return;
    stopHeartbeat();
    resetControlDevice();
    storeEditor("");
    if (leaseRecovering) return;
    leaseRecovering = true;
    const serial = ++leaseRecoverySerial;
    renderLoading();
    openGate()
      .catch(() => {
        if (rootOpen && serial === leaseRecoverySerial) {
          renderGateError(t("network"), () => openGate(), t("gateBlockedTitle"));
        }
      })
      .finally(() => {
        if (serial === leaseRecoverySerial) leaseRecovering = false;
      });
  }

  // ---------------------------------------------------------------------- rendering

  function render() {
    if (!rootOpen || rootSuspended) return;
    if (subpage !== "personalize") stopMediaPoll();
    if (!(rootMode === "system" && subpage === "device")) {
      stopControlDevicePolling();
      controlDeviceRefs = null;
    }
    applyStaticText();
    if (!snap) return renderLoading();
    // The parent backend and TV runtime are still active-member only. Opening a sibling's
    // time or wallpaper page would silently write to the member the TV is running, so those
    // pages stay closed until the TV itself switches.
    if (rootMode === "user" && editingContext().sibling &&
        (subpage === "time" || subpage === "personalize" || subpage === "apps")) {
      subpage = null;
    }
    if (rootMode === "system") {
      if (subpage === "family") return renderFamilyPage();
      if (subpage === "device") return renderDevicePage();
      if (subpage === "entries") return renderEntriesPage();
      if (subpage === "updates") return renderUpdatesPage();
      if (subpage === "password") return renderPassword();
      return renderSystemIndex();
    }
    if (subpage === "apps") return renderAppPicker();
    if (subpage === "personalize") return renderPersonalize();
    if (subpage === "time") return renderTimeAndApps();
    return renderUserIndex();
  }

  function sectionLabel(text) {
    return el("p", "parent-section-label", text);
  }

  function switchRow(title, subtitle, checked, disabled, onChange) {
    const row = el("label", "parent-row parent-switch-row");
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", title));
    if (subtitle) main.appendChild(el("span", "parent-row-sub", subtitle));
    row.appendChild(main);
    const label = el("label", "parent-switch");
    const input = el("input");
    input.type = "checkbox";
    input.checked = Boolean(checked);
    input.disabled = Boolean(disabled);
    input.setAttribute("aria-label", title);
    input.addEventListener("change", () => onChange(input));
    label.appendChild(input);
    label.appendChild(el("span", "parent-switch-track"));
    row.appendChild(label);
    return row;
  }

  function rulesAppSummary() {
    const rules = snap.rules || {};
    const allowAll = Boolean(rules.allApps);
    const allowed = Array.isArray(rules.allowedPackages) ? rules.allowedPackages.length : 0;
    return allowAll ? t("allApps")
      : allowed > 0 ? t("appsSelected", { n: allowed }) : t("noEntertainment");
  }

  function moreStatusText() {
    const parts = [modeText(snap.mode), snap.bound ? t("bound") : t("notBound")];
    parts.push(snap.childModeOn ? t("childOn") : t("childOff"));
    return parts.join(" · ");
  }

  function editingContext() {
    const members = familyMembers();
    const active = members.find((member) => member.isActive) || members[0] || null;
    const selected = host && typeof host.editingSubjectId === "function"
      ? String(host.editingSubjectId() || "")
      : "";
    const member = selected ? (findMember(selected) || active) : active;
    return {
      member: member,
      active: active,
      sibling: Boolean(member && active && member.userId !== active.userId)
    };
  }

  function activeMemberName() {
    const active = familyMembers().find((member) => member.isActive) || familyMembers()[0] || null;
    return active && active.name ? active.name : "—";
  }

  /**
   * The user tab header follows the chat-app pattern the quiz page uses: squared
   * avatar on the left, member name over the TV's current member. Tapping it chooses
   * which member this page edits; the TV's active member has its own explicit row.
   */
  function userHeaderNode(context) {
    const bar = el("div", "parent-bar parent-member-bar");
    const head = el("button", "parent-member-head");
    head.type = "button";
    head.setAttribute("aria-haspopup", "dialog");
    head.setAttribute("aria-label",
      t("userSwitchAction") + " · " +
      (context.member ? context.member.name : t("userNoMember")));
    if (context.member) {
      const avatar = memberAvatarNode(context.member, "parent-avatar-head");
      // Same status ring as the quiz header; member identity, not image equality.
      avatar.classList.toggle("is-tv-current", Boolean(context.member.isActive));
      head.appendChild(avatar);
    } else {
      head.appendChild(el("span", "parent-avatar parent-avatar-head parent-avatar-empty", "?"));
    }
    const copy = el("div", "parent-member-copy");
    // Same hierarchy as DingTalk: page title on the first line beside the
    // avatar, the editing member and the TV's current member below it.
    copy.appendChild(el("span", "parent-member-head-name", t("userConfigTitle")));
    copy.appendChild(el("span", "parent-member-head-sub",
      t("memberHeaderSubtitle", {
        member: context.member ? context.member.name : t("userNoMember"),
        name: activeMemberName(),
      })));
    head.appendChild(copy);
    head.appendChild(el("span", "chevron", "›"));
    const canPick = familyMembers().length > 0 && host &&
      typeof host.openSubjectPicker === "function";
    head.disabled = !canPick;
    if (canPick) head.addEventListener("click", () => host.openSubjectPicker());
    bar.appendChild(head);
    return bar;
  }

  /**
   * User settings is an index: the Time & Apps and wallpaper rows open their existing pages,
   * while the timer block is inline because it is only a status line and two actions. The
   * header names the member being edited and the member the TV is actually running, because
   * V41 keeps those two pointers separate.
   */
  function renderUserIndex() {
    const root = $(ROOT_ID);
    if (!root) return;
    clear(root);
    const shell = el("div", "parent-shell");
    const context = editingContext();
    shell.appendChild(userHeaderNode(context));
    const content = el("div", "parent-content parent-more-content");
    content.appendChild(noticeArea());

    content.appendChild(el(
      "p",
      "parent-hint parent-user-note",
      context.sibling
        ? t("userSiblingNote", { name: context.member ? context.member.name : "" })
        : t("userActiveNote", { name: context.member ? context.member.name : activeMemberName() })
    ));

    // Remote switching shares the TV's own gate: the server refuses while a timer, quiz,
    // study round or temporary unlock owns the TV, so this row only reflects the active
    // member and lets the click report "busy" instead of pretending the TV is free.
    if (context.member) {
      const isActive = Boolean(context.active &&
        context.member.userId === context.active.userId);
      const switchCard = el("div", "parent-card");
      switchCard.appendChild(row(
        t("userSwitchTo"),
        isActive
          ? t("userSwitchDisabled")
          : t("userSwitchEnabled", { name: context.member.name || "" }),
        null,
        () => confirmRemoteSwitch(context.member),
        isActive || !(snap.capabilities && snap.capabilities.canSwitchSubject),
        "parent-row-data"
      ));
      content.appendChild(switchCard);
    }

    const blocked = context.sibling || !context.member;
    // The timer is short enough to live on this page: a status line and the two actions,
    // instead of a second-level page the parent has to open first. It only makes sense for
    // the member the TV is actually running, so a sibling being viewed gets no timer block.
    let timerRefs = null;
    if (!blocked) {
      content.appendChild(sectionLabel(t("controlTimerSection")));
      const timerStatus = el("p", "parent-more-status");
      const timerDot = el("span", "parent-more-dot");
      const timerStatusLabel = el("span", null, "");
      timerStatus.appendChild(timerDot);
      timerStatus.appendChild(timerStatusLabel);
      content.appendChild(timerStatus);
      const timerCard = el("div", "parent-card");
      const startManual = controlActionRow(
        t("controlStartTitle"),
        () => openControlManual(),
        false
      );
      const stopTimer = controlActionRow(
        t("controlStopTitle"),
        () => sendControl("STOP_TIMER"),
        true
      );
      timerCard.appendChild(startManual.button);
      timerCard.appendChild(stopTimer.button);
      content.appendChild(timerCard);
      const timerNotice = el("p", "parent-hint parent-control-notice");
      timerNotice.setAttribute("role", "status");
      content.appendChild(timerNotice);
      content.appendChild(el("p", "parent-hint parent-control-note", t("controlLocalOnly")));
      timerRefs = {
        dot: timerDot,
        statusText: timerStatusLabel,
        notice: timerNotice,
        startManual: startManual,
        stopTimer: stopTimer
      };
    }

    content.appendChild(sectionLabel(t("timeConfigSection")));
    const card = el("div", "parent-card");
    card.appendChild(row(
      t("timeAndApps"),
      t("timeAndAppsSummary", { groups: String(dayGroups().length), apps: rulesAppSummary() }),
      blocked ? t("userTimeDisabled") : null,
      enterTimeAndApps,
      blocked || !(snap.capabilities && snap.capabilities.canEditRules),
      "parent-row-data"
    ));
    content.appendChild(card);

    // V45: unlimited apps stay a first-level object but no longer need a summary page of
    // their own. The icons are the entry — one click opens the picker and Save returns here.
    content.appendChild(renderUnlimitedCard(blocked));

    const personalizeCard = el("div", "parent-card");
    personalizeCard.appendChild(row(
      t("personalize"),
      t("personalizeHint"),
      mediaSummary(),
      openPersonalize,
      blocked
    ));
    content.appendChild(personalizeCard);

    shell.appendChild(content);
    root.appendChild(shell);
    controlRefs = timerRefs;
    if (controlRefs) paintControl();
  }

  /** Confirms and moves the TV's active pointer; the selected member is already not active. */
  function confirmRemoteSwitch(member) {
    if (!member) return;
    const name = member.name || "";
    confirmSheet(
      t("userSwitchConfirm", { name: name }),
      t("userSwitchEnabled", { name: name }),
      t("userSwitchAction"),
      async () => {
        const family = snap.family || {};
        try {
          const data = await host.request("/api/parent/subjects/activate", {
            method: "POST",
            body: {
              userId: member.userId,
              expectedActiveEpoch: Number(family.activeEpoch) || 0
            },
            editorToken: editorToken,
            quizAuth: true
          });
          if (data && data.snapshot) adoptSnapshot(data.snapshot, false);
          else await refreshSnapshot();
          setNotice(successText((data && data.messageKey) || "SUBJECT_SWITCHED"), false);
          render();
          if (host.memberProfileChanged) host.memberProfileChanged();
        } catch (error) {
          await recoverWrite(error);
          render();
        }
      },
      t("cancel")
    );
  }

  function systemGroup(title, rows) {
    const wrap = el("div", "parent-system-group");
    wrap.appendChild(sectionLabel(title));
    const card = el("div", "parent-card");
    for (const item of rows) card.appendChild(item);
    wrap.appendChild(card);
    return wrap;
  }

  function openSystemSubpage(next) {
    subpage = next;
    clearNotices();
    render();
    window.scrollTo({ top: 0 });
  }

  function renderSystemIndex() {
    const root = $(ROOT_ID);
    if (!root) return;
    clear(root);
    const shell = el("div", "parent-shell");
    // System config is a top-level tab; the bottom nav is the way out, so no back
    // button in its header.
    shell.appendChild(barNode(t("systemConfigTitle"), moreStatusText()));
    const content = el("div", "parent-content parent-more-content");
    content.appendChild(noticeArea());

    content.appendChild(systemGroup(t("systemGroupFamilyTv"), [
      row(
        t("systemFamily"),
        t("systemFamilyHint"),
        t("familyMemberCount", { n: familyMembers().length }),
        () => openSystemSubpage("family")
      ),
      switchRow(
        t("systemMemberSwitch"),
        t("systemMemberSwitchHint"),
        snap.memberSwitchRequired,
        !(snap.capabilities && snap.capabilities.canToggleMemberSwitch),
        (input) => saveSimple("/api/parent/member-switch", {
          enabled: input.checked
        }, "memberSwitchRequired", input.checked)
      ),
      row(
        t("systemDevice"),
        t("systemDeviceHint"),
        null,
        () => openSystemSubpage("device")
      ),
      row(
        t("systemEntries"),
        t("systemEntriesHint"),
        null,
        () => openSystemSubpage("entries")
      )
    ]));

    // TV control is short enough to live on this page: a status line and the two child
    // mode actions, instead of a second-level page the parent has to open first.
    content.appendChild(sectionLabel(t("controlChildSection")));
    const controlStatus = el("p", "parent-more-status");
    const controlDot = el("span", "parent-more-dot");
    const controlStatusLabel = el("span", null, "");
    controlStatus.appendChild(controlDot);
    controlStatus.appendChild(controlStatusLabel);
    content.appendChild(controlStatus);
    const childCard = el("div", "parent-card");
    const takeOver = controlActionRow(
      t("controlTakeOverTitle"),
      () => sendControl("TAKE_OVER"),
      false
    );
    const enterChild = controlActionRow(
      t("controlEnterTitle"),
      () => sendControl("ENTER_CHILD_MODE"),
      false
    );
    childCard.appendChild(takeOver.button);
    childCard.appendChild(enterChild.button);
    content.appendChild(childCard);
    const controlNoticeNode = el("p", "parent-hint parent-control-notice");
    controlNoticeNode.setAttribute("role", "status");
    content.appendChild(controlNoticeNode);
    content.appendChild(el("p", "parent-hint parent-control-note", t("controlLocalOnly")));

    content.appendChild(systemGroup(t("systemGroupParent"), [
      switchRow(
        t("userConfigPinInput"),
        t("userConfigPinInputHint"),
        snap.userConfigRequirePin !== false,
        !(snap.capabilities && snap.capabilities.canEditRules),
        saveUserConfigPinSwitch
      ),
      row(
        t("parentPassword"),
        null,
        snap.pinConfigured ? t("pinSet") : t("pinUnset"),
        () => openSystemSubpage("password"),
        !(snap.capabilities && snap.capabilities.canChangePin)
      ),
      row(t("aiTitle"), t("systemAiHint"), t("manageAction"), () => {
        if (host && host.openAi) host.openAi();
      }),
      row(
        t("downloadParentApp"),
        t("downloadParentAppHint"),
        t("viewAction"),
        () => {
          if (host && host.openParentAppDownload) host.openParentAppDownload();
        }
      )
    ]));

    content.appendChild(systemGroup(t("systemGroupAccount"), [
      languageRow(),
      row(
        t("systemUpdates"),
        updateSubtitle(),
        snap.update && snap.update.current ? "v" + snap.update.current : null,
        () => openSystemSubpage("updates")
      ),
      row(
        t("privacyInfo"),
        t("privacyHint"),
        t("viewAction"),
        () => {
          if (host && host.openPrivacy) host.openPrivacy();
        }
      )
    ]));

    // The TV filing covers com.planyourtv.tv only. The parent shell is a separate package
    // and has not been filed yet, so do not show the TV's number inside it.
    const recordNumber = String(snap.appRecordNumber || "").trim();
    const inParentShell = Boolean(window.ParentShell || window.__pytvParentShell);
    if (recordNumber && !inParentShell) {
      const record = el("p", "parent-hint parent-app-record");
      record.appendChild(document.createTextNode(t("systemAppRecord", { number: recordNumber }) + " · "));
      const lookup = el("a", null, t("systemAppRecordLookup"));
      lookup.href = "https://beian.miit.gov.cn/";
      lookup.target = "_blank";
      lookup.rel = "noopener";
      record.appendChild(lookup);
      content.appendChild(record);
    }

    const footer = el("div", "parent-logout-wrap");
    footer.appendChild(button(t("logoutAccount"), "parent-logout-btn", () => {
      close().then(() => {
        if (host && host.logout) host.logout();
        // The phone shell's real login screen is the native pairing/QR view. On
        // the TV there is no ParentShell, so this only affects the phone app.
        if (window.ParentShell && typeof window.ParentShell.logout === "function") {
          try { window.ParentShell.logout(); } catch (_) { /* keep the TV gate */ }
        }
      });
    }));
    content.appendChild(footer);

    shell.appendChild(content);
    root.appendChild(shell);
    controlRefs = {
      dot: controlDot,
      statusText: controlStatusLabel,
      notice: controlNoticeNode,
      takeOver: takeOver,
      enterChild: enterChild
    };
    paintControl();
  }

  function backToRoot() {
    subpage = null;
    clearNotices();
    render();
    window.scrollTo({ top: 0 });
  }

  function renderFamilyPage() {
    const root = $(ROOT_ID);
    if (!root) return;
    clear(root);
    const shell = el("div", "parent-shell");
    shell.appendChild(barNode(t("systemFamily"), moreStatusText(), backToRoot));
    const content = el("div", "parent-content");
    content.appendChild(noticeArea());
    content.appendChild(el("p", "parent-hint", t("familyPageHint")));
    content.appendChild(renderFamilyCard());
    shell.appendChild(content);
    root.appendChild(shell);
  }

  function renderEntriesPage() {
    const root = $(ROOT_ID);
    if (!root) return;
    clear(root);
    const shell = el("div", "parent-shell");
    shell.appendChild(barNode(t("systemEntries"), moreStatusText(), backToRoot));
    const content = el("div", "parent-content");
    content.appendChild(noticeArea());
    content.appendChild(el("p", "parent-hint", t("entriesPageHint")));
    content.appendChild(sectionLabel(t("tvInputSources")));
    content.appendChild(renderInputSources());
    content.appendChild(sectionLabel(t("quizChallenge")));
    content.appendChild(renderQuizChallenge());
    shell.appendChild(content);
    root.appendChild(shell);
  }

  function renderUpdatesPage() {
    const root = $(ROOT_ID);
    if (!root) return;
    clear(root);
    const shell = el("div", "parent-shell");
    shell.appendChild(barNode(t("systemUpdates"), moreStatusText(), backToRoot));
    const content = el("div", "parent-content");
    content.appendChild(noticeArea());
    const card = el("div", "parent-card");
    card.appendChild(switchRow(
      t("autoUpdate"),
      t("autoUpdateHint"),
      snap.autoUpdate,
      false,
      (input) => saveAutoUpdateSwitch(input)
    ));
    card.appendChild(row(
      t("softwareUpdate"),
      updateSubtitle(),
      snap.update && snap.update.current ? "v" + snap.update.current : null,
      openUpdateSheet
    ));
    content.appendChild(card);
    shell.appendChild(content);
    root.appendChild(shell);
  }

  function renderDevicePage() {
    if (!snap) return renderLoading();
    const root = $(ROOT_ID);
    if (!root) return;
    clear(root);
    controlDeviceOpen = true;
    const shell = el("div", "parent-shell");
    shell.appendChild(barNode(t("systemDevice"), moreStatusText(), backToRoot));
    const content = el("div", "parent-content");
    content.appendChild(noticeArea());
    content.appendChild(el("p", "parent-hint", t("controlDevicePermissionsHint")));

    const card = el("div", "parent-card parent-device-card");
    const deviceHead = el("div", "parent-row parent-device-head");
    deviceHead.setAttribute("aria-expanded", "true");
    const deviceDot = el("span", "parent-device-dot");
    const deviceMain = el("div", "parent-row-main");
    deviceMain.appendChild(el("span", "parent-row-title", t("controlDeviceTitle")));
    const deviceSummary = el("span", "parent-row-sub", t("controlDeviceSummaryHint"));
    deviceMain.appendChild(deviceSummary);
    deviceHead.appendChild(deviceDot);
    deviceHead.appendChild(deviceMain);
    const deviceBody = el("div", "parent-device-body");
    deviceBody.id = "parentDeviceBody";
    card.appendChild(deviceHead);
    card.appendChild(deviceBody);
    content.appendChild(card);

    shell.appendChild(content);
    root.appendChild(shell);
    controlDeviceRefs = {
      head: deviceHead,
      dot: deviceDot,
      summary: deviceSummary,
      body: deviceBody
    };
    paintControlDevice();
    syncControlDevicePolling();
    loadControlDevice();
  }

  function mediaSummary() {
    const media = snap.media || {};
    const roles = media.roles || {};
    const count = (roles.quiz ? 1 : 0) + (roles.lock ? 1 : 0);
    return count > 0 ? t("mediaCustomized") : t("mediaSourceDefault");
  }

  function updateSubtitle() {
    const update = snap.update || {};
    return update.available
      ? t("updateFound", { version: "v" + update.available })
      : t("softwareUpdateHint");
  }

  function updateFailureText(reason) {
    if (reason === "blocked") return t("updateFailedBlocked");
    if (reason === "notOfficial") return t("updateFailedNotOfficial");
    if (reason === "notNewer") return t("updateFailedNotNewer");
    return t("updateFailedDownload");
  }

  /**
   * Software update: the same manual download/install Control Center uses, with the phone
   * showing the TV's progress. The sheet stays open and polls the snapshot; the TV's
   * Updates.install state is the single source of truth, so closing this page never stops it.
   */
  function openUpdateSheet() {
    let timer = 0;
    let closed = false;
    let primaryAction = null;
    openSheet((sheet, close) => {
      sheet.appendChild(el("h2", null, t("softwareUpdate")));

      const hero = el("div", "parent-update-hero");
      hero.appendChild(el("span", "parent-update-caption", t("updateCurrent")));
      const version = el("strong", "parent-update-version", "");
      hero.appendChild(version);
      sheet.appendChild(hero);

      const status = el("p", "parent-update-status", "");
      sheet.appendChild(status);

      const progress = el("div", "parent-update-progress");
      const track = el("div", "parent-progress-track");
      const fill = el("div", "parent-progress-fill");
      track.appendChild(fill);
      const progressText = el("p", "parent-progress-text", "");
      progress.appendChild(track);
      progress.appendChild(progressText);
      sheet.appendChild(progress);

      const autoHint = el("p", "parent-update-auto", "");
      sheet.appendChild(autoHint);

      const notes = el("div", "parent-update-notes");
      sheet.appendChild(notes);

      const actions = el("div", "parent-actions");
      const primary = button("", "parent-btn primary", () => {
        if (!primary.disabled && primaryAction) primaryAction();
      });
      actions.appendChild(primary);
      actions.appendChild(button(t("updateClose"), "parent-btn ghost", close));
      sheet.appendChild(actions);

      let notesSignature = null;

      function paintNotes(update) {
        const list = Array.isArray(update.notes) ? update.notes : [];
        const changes = list[0] && Array.isArray(list[0].changes) ? list[0].changes : [];
        const signature = changes.join("\n");
        if (signature === notesSignature) return;
        notesSignature = signature;
        clear(notes);
        notes.hidden = changes.length === 0;
        if (!changes.length) return;
        notes.appendChild(el("p", "parent-update-notes-title", t("updateNotes")));
        const items = el("ul", "parent-update-notes-list");
        for (const change of changes.slice(0, 6)) items.appendChild(el("li", null, change));
        notes.appendChild(items);
      }

      function paint() {
        const update = snap.update || {};
        const state = update.installState || "idle";
        const busy = state === "downloading" || state === "waiting" || state === "opened";
        const available = update.available || "";
        version.textContent = update.current ? "v" + update.current : "—";

        status.className = "parent-update-status";
        if (state === "downloading") {
          status.textContent = t("updateDownloading", {
            percent: Math.round((Number(update.installFraction) || 0) * 100),
          });
        } else if (state === "waiting") {
          status.textContent = t("updateWaiting");
        } else if (state === "opened") {
          status.textContent = t("updateOpened");
        } else if (state === "needsPermission") {
          status.textContent = t("updateNeedsPermission");
          status.classList.add("is-warning");
        } else if (state === "needsStoragePermission") {
          status.textContent = t("updateNeedsStoragePermission");
          status.classList.add("is-warning");
        } else if (state === "failed") {
          status.textContent = updateFailureText(update.installFailure);
          status.classList.add("is-warning");
        } else if (available) {
          status.textContent = t("updateFound", { version: "v" + available });
          status.classList.add("is-ready");
        } else if (update.checking) {
          status.textContent = t("updateChecking");
        } else if (update.checkFailed) {
          status.textContent = t("updateCheckFailed");
          status.classList.add("is-warning");
        } else if (update.lastCheckedAt) {
          status.textContent = t("updateLatest");
          status.classList.add("is-ready");
        } else {
          status.textContent = t("updateNotChecked");
        }

        progress.hidden = state !== "downloading";
        if (state === "downloading") {
          const percent = Math.max(0, Math.min(100, Math.round((Number(update.installFraction) || 0) * 100)));
          fill.style.width = percent + "%";
          progressText.textContent = t("updateDownloading", { percent: percent });
        }

        let autoText = "";
        if (update.auto && !update.manualSupported) autoText = t("updateUnavailable");
        else if (update.auto && update.autoBusy) autoText = t("updateAutoDownloading");
        else if (update.auto && update.autoReady) autoText = t("updateAutoReady");
        else if (update.auto && update.autoFailure === "download") autoText = t("updateAutoFailedDownload");
        else if (update.auto && update.autoFailure === "install") autoText = t("updateAutoFailedInstall");
        else if (update.auto && available && update.installPermissionMissing) autoText = t("updateAutoPermission");
        else if (update.auto && available && !update.autoSupported) autoText = t("updateAutoUnsupported");
        autoHint.hidden = !autoText;
        autoHint.textContent = autoText;

        paintNotes(update);

        primary.hidden = false;
        primary.disabled = false;
        primaryAction = null;
        if (busy) {
          primary.hidden = true;
        } else if (state === "needsPermission" || state === "needsStoragePermission" || state === "failed") {
          primary.textContent = t("retry");
          primaryAction = requestInstall;
        } else if (!update.manualSupported) {
          primary.hidden = true;
        } else if (available) {
          primary.textContent = t("updateInstallAction");
          primaryAction = requestInstall;
        } else if (update.checking) {
          primary.textContent = t("updateChecking");
          primary.disabled = true;
        } else {
          primary.textContent = t("updateCheckAction");
          primaryAction = requestCheck;
        }
      }

      async function requestCheck() {
        primary.disabled = true;
        try {
          const data = await host.request("/api/parent/update/check", {
            method: "POST",
            body: {},
            editorToken: editorToken,
            quizAuth: true,
          });
          if (data && data.snapshot) adoptSnapshot(data.snapshot, false);
        } catch (error) {
          if (isLeaseError(error)) {
            handleLeaseError(error);
            return;
          }
          status.textContent = errorText(error);
          status.className = "parent-update-status is-warning";
          primary.disabled = false;
          return;
        }
        paint();
      }

      function requestInstall() {
        confirmSheet(
          t("updateConfirmTitle"),
          t("updateConfirmText"),
          t("updateConfirmAction"),
          async () => {
            try {
              const data = await host.request("/api/parent/update/install", {
                method: "POST",
                body: {},
                editorToken: editorToken,
                quizAuth: true,
              });
              if (data && data.snapshot) adoptSnapshot(data.snapshot, false);
            } catch (error) {
              if (isLeaseError(error)) {
                handleLeaseError(error);
                return;
              }
              setNotice(errorText(error), true);
            }
            openUpdateSheet();
          },
          t("cancel")
        );
      }

      paint();
      timer = setInterval(async () => {
        if (document.hidden) return;
        try {
          await refreshSnapshot();
          if (!closed) paint();
        } catch (error) {
          handleLeaseError(error);
        }
      }, 2000);
    }, () => {
      closed = true;
      if (timer) clearInterval(timer);
      render();
    });
  }

  /** Back navigation inside the open page. Returns false when there is nowhere left to go. */
  function closeSubpage() {
    if (subpage === "apps") {
      // The picker is part of Time & Apps; going back must not drop its rules proof.
      exitPicker();
      return true;
    }
    if (subpage) {
      if (subpage === "time") {
        leaveTimeAndApps();
      } else {
        subpage = null;
      }
      picker = null;
      render();
      return true;
    }
    return false;
  }

  function confirmShouldLeave() {
    if (closeSubpage()) return;
    confirmClose();
  }

  function renderHero() {
    const card = el("div", "parent-card parent-hero");
    const row = el("div", "parent-row parent-switch-row");
    const main = el("div", "parent-hero-main");
    main.appendChild(el("div", "parent-hero-title", t("childMode")));
    const readings = [];
    if (snap.mode === "LOCAL") readings.push(t("localRules"));
    if (snap.mode === "CLOUD") readings.push(t("cloudRules"));
    if (snap.sessionState === "ACTIVE") readings.push(t("sessionActive"));
    const used = minutes(snap.usage && snap.usage.dailyUsedSeconds);
    if (snap.mode !== "NONE") readings.push(t("todayUsed", { n: used }));
    main.appendChild(el("div", "parent-hero-sub", readings.join(" · ")));
    row.appendChild(main);
    const label = el("label", "parent-switch");
    const input = el("input");
    input.type = "checkbox";
    input.checked = Boolean(snap.childModeOn);
    input.disabled = !(snap.capabilities && snap.capabilities.canToggleChildMode);
    input.setAttribute("aria-label", t("childMode"));
    input.addEventListener("change", () => {
      if (input.checked) {
        input.checked = false;
        startEnableChildMode();
      } else {
        input.checked = true;
        startDisableChildMode();
      }
    });
    label.appendChild(input);
    label.appendChild(el("span", "parent-switch-track"));
    row.appendChild(label);
    card.appendChild(row);
    if (snap.mode === "CLOUD") {
      card.appendChild(el("div", "parent-hint parent-hero-note", t("readOnlyCloud")));
    }
    return card;
  }

  function row(title, subtitle, value, onClick, disabled, subtitleClass) {
    const node = el(onClick ? "button" : "div", "parent-row");
    if (onClick) node.type = "button";
    if (disabled) node.disabled = true;
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", title));
    if (subtitle) {
      main.appendChild(el("span", "parent-row-sub" + (subtitleClass ? " " + subtitleClass : ""), subtitle));
    }
    node.appendChild(main);
    if (value !== undefined && value !== null) node.appendChild(el("span", "parent-row-value", value));
    if (onClick) node.appendChild(el("span", "chevron", "›"));
    if (onClick && !disabled) node.addEventListener("click", onClick);
    return node;
  }

  const LANGUAGE_NAMES = {
    zh: "中文",
    en: "English",
    ru: "Русский",
    uk: "Українська",
    kk: "Қазақша",
  };

  /** V41: language is an ordinary system preference, not a header control. */
  function languageRow() {
    const node = el("label", "parent-row");
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", t("systemLanguage")));
    main.appendChild(el("span", "parent-row-sub", t("systemLanguageHint")));
    node.appendChild(main);
    const select = el("select", "parent-time-select parent-language-select");
    select.setAttribute("aria-label", t("systemLanguage"));
    for (const code of LANGS) {
      const option = el("option", "", LANGUAGE_NAMES[code] || code);
      option.value = code;
      if (code === lang) option.selected = true;
      select.appendChild(option);
    }
    select.addEventListener("change", () => {
      if (host && typeof host.changeLanguage === "function") {
        host.changeLanguage(select.value);
      } else {
        setLang(select.value);
      }
    });
    node.appendChild(select);
    return node;
  }

  // ------------------------------------------------------------------ family members
  // The four presets are the approved V40 visual set. Uploaded photos are fetched through
  // host.blob() because /api/parent/avatar requires the same editor lease as the rest of the
  // page; an <img src> would be an unauthenticated read. Blob URLs are cached per revision so
  // a rename or a rules save does not refetch every avatar.
  const MEMBER_AVATARS = {
    rabbit: { bg: "#f3ddc9", face: "#f7e7d6", ear: "#efc3a8", inner: "#fff8ef", eye: "#3d3029", cheek: "rgba(232,115,101,.28)" },
    fox: { bg: "#f8dfca", face: "#eeb170", ear: "#d9824a", inner: "#f9d3ae", eye: "#4b3021", cheek: "rgba(226,109,77,.25)" },
    bear: { bg: "#e9e1d4", face: "#c99769", ear: "#a36d45", inner: "#e4bb8f", eye: "#3d2c23", cheek: "rgba(206,102,90,.22)" },
    panda: { bg: "#e3e8e1", face: "#fbf8f0", ear: "#303630", inner: "#d7ddd5", eye: "#2f342e", cheek: "rgba(232,143,143,.22)" }
  };
  const MEMBER_AVATAR_KEYS = ["rabbit", "fox", "bear", "panda"];
  const MEMBER_AVATAR_LABELS = {
    rabbit: "avatarRabbit",
    fox: "avatarFox",
    bear: "avatarBear",
    panda: "avatarPanda"
  };
  const memberAvatarUrls = new Map();

  function avatarSvg(key) {
    const a = MEMBER_AVATARS[key] || MEMBER_AVATARS.rabbit;
    const eye = (cx, cy) => '<circle cx="' + cx + '" cy="' + cy + '" r="2.1" fill="' + a.eye + '"/>';
    if (key === "fox") {
      return '<svg viewBox="0 0 64 64" aria-hidden="true">'
        + '<rect width="64" height="64" fill="' + a.bg + '"/>'
        + '<path d="M14 24 18 8l12 12z" fill="' + a.ear + '"/><path d="M50 24 46 8 34 20z" fill="' + a.ear + '"/>'
        + '<path d="M18 14l3 8" stroke="' + a.inner + '" stroke-width="3" stroke-linecap="round"/>'
        + '<circle cx="32" cy="35" r="18" fill="' + a.face + '"/>'
        + '<path d="M20 33q12-10 24 0" fill="none" stroke="' + a.ear + '" stroke-width="3" stroke-linecap="round"/>'
        + eye(25, 35) + eye(39, 35)
        + '<path d="M29 41q3 3 6 0" fill="none" stroke="' + a.eye + '" stroke-width="1.7" stroke-linecap="round"/>'
        + '<circle cx="19" cy="41" r="3" fill="' + a.cheek + '"/><circle cx="45" cy="41" r="3" fill="' + a.cheek + '"/>'
        + '</svg>';
    }
    if (key === "panda") {
      return '<svg viewBox="0 0 64 64" aria-hidden="true">'
        + '<rect width="64" height="64" fill="' + a.bg + '"/>'
        + '<circle cx="20" cy="19" r="10" fill="' + a.ear + '"/><circle cx="44" cy="19" r="10" fill="' + a.ear + '"/>'
        + '<circle cx="32" cy="35" r="18" fill="' + a.face + '"/>'
        + '<ellipse cx="24" cy="34" rx="6" ry="7" fill="' + a.ear + '" transform="rotate(28 24 34)"/>'
        + '<ellipse cx="40" cy="34" rx="6" ry="7" fill="' + a.ear + '" transform="rotate(-28 40 34)"/>'
        + '<circle cx="24" cy="34" r="2" fill="#fff8ef"/><circle cx="40" cy="34" r="2" fill="#fff8ef"/>'
        + '<ellipse cx="32" cy="42" rx="3" ry="2.2" fill="' + a.eye + '"/>'
        + '<path d="M29 46q3 2 6 0" fill="none" stroke="' + a.eye + '" stroke-width="1.4" stroke-linecap="round"/>'
        + '</svg>';
    }
    const isBear = key === "bear";
    return '<svg viewBox="0 0 64 64" aria-hidden="true">'
      + '<rect width="64" height="64" fill="' + a.bg + '"/>'
      + (isBear
        ? '<circle cx="20" cy="19" r="8" fill="' + a.ear + '"/><circle cx="44" cy="19" r="8" fill="' + a.ear + '"/>'
        : '<ellipse cx="23" cy="17" rx="6" ry="12" fill="' + a.ear + '" transform="rotate(-17 23 17)"/>'
          + '<ellipse cx="41" cy="17" rx="6" ry="12" fill="' + a.ear + '" transform="rotate(17 41 17)"/>'
          + '<ellipse cx="23" cy="18" rx="2.6" ry="7" fill="' + a.inner + '" transform="rotate(-17 23 18)"/>'
          + '<ellipse cx="41" cy="18" rx="2.6" ry="7" fill="' + a.inner + '" transform="rotate(17 41 18)"/>')
      + '<circle cx="32" cy="35" r="18" fill="' + a.face + '"/>'
      + (isBear ? '<circle cx="26" cy="30" r="2" fill="' + a.ear + '"/><circle cx="38" cy="30" r="2" fill="' + a.ear + '"/>' : '')
      + eye(25, 35) + eye(39, 35)
      + '<path d="M29 41q3 3 6 0" fill="none" stroke="' + a.eye + '" stroke-width="1.7" stroke-linecap="round"/>'
      + '<circle cx="19" cy="41" r="3" fill="' + a.cheek + '"/><circle cx="45" cy="41" r="3" fill="' + a.cheek + '"/>'
      + '</svg>';
  }

  function memberAvatarNode(member, extraClass) {
    const node = el("span", "parent-avatar" + (extraClass ? " " + extraClass : ""));
    node.dataset.member = member.userId;
    node.dataset.revision = String(member.avatarRevision || 0);
    paintMemberAvatar(node, member);
    return node;
  }

  function paintMemberAvatar(node, member) {
    clear(node);
    const cached = member.avatarUrl ? memberAvatarUrls.get(member.userId) : null;
    if (cached && cached.revision === String(member.avatarRevision || 0)) {
      const image = document.createElement("img");
      image.src = cached.url;
      image.alt = "";
      node.appendChild(image);
      return;
    }
    node.innerHTML = avatarSvg(member.avatarPreset);
    if (!member.avatarUrl || !host || !editorToken) return;
    const revision = String(member.avatarRevision || 0);
    host.blob(member.avatarUrl, editorToken).then((blob) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const previous = memberAvatarUrls.get(member.userId);
      if (previous && previous.url !== url) URL.revokeObjectURL(previous.url);
      memberAvatarUrls.set(member.userId, { revision: revision, url: url });
      if (!node.isConnected) return;
      if (node.dataset.member !== member.userId || node.dataset.revision !== revision) return;
      clear(node);
      const image = document.createElement("img");
      image.src = url;
      image.alt = "";
      node.appendChild(image);
    }).catch(() => { /* the preset remains visible */ });
  }

  const MEMBER_AVATAR_SOURCE_BYTES = 25 * 1024 * 1024;
  const MEMBER_AVATAR_OUTPUT_PX = 512;
  const MEMBER_AVATAR_MAX_ZOOM = 4;

  function clampNumber(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function canvasJpegBlob(canvas, done) {
    if (typeof canvas.toBlob === "function") {
      canvas.toBlob((blob) => done(blob), "image/jpeg", 0.9);
      return;
    }
    try {
      const encoded = canvas.toDataURL("image/jpeg", 0.9);
      const raw = atob(encoded.slice(encoded.indexOf(",") + 1));
      const bytes = new Uint8Array(raw.length);
      for (let i = 0; i < raw.length; i += 1) bytes[i] = raw.charCodeAt(i);
      done(new Blob([bytes], { type: "image/jpeg" }));
    } catch (_) {
      done(null);
    }
  }

  /**
   * Square avatar cropper. The photo is decoded on the phone, panned and zoomed under a
   * square mask, then redrawn at 512x512 and sent as JPEG. The TV therefore receives a small,
   * already-oriented image even when the source is a 25 MB camera photo, and the parent
   * chooses the crop instead of the server center-cropping it.
   */
  function openAvatarCropper(file, onApply) {
    const existing = document.querySelector(".parent-crop-root");
    if (existing) existing.remove();
    const root = el("div", "parent-crop-root");
    const backdrop = el("div", "parent-crop-backdrop");
    const panel = el("div", "parent-crop-panel");
    panel.setAttribute("role", "dialog");
    panel.setAttribute("aria-modal", "true");
    panel.appendChild(el("h2", null, t("avatarCropTitle")));
    panel.appendChild(el("p", "parent-crop-hint", t("avatarCropHint")));

    const viewport = el("div", "parent-crop-viewport");
    const image = document.createElement("img");
    image.alt = "";
    image.draggable = false;
    viewport.appendChild(image);
    panel.appendChild(viewport);

    const zoomRow = el("div", "parent-crop-zoom");
    zoomRow.appendChild(el("span", "parent-field-label", t("avatarCropZoom")));
    const range = document.createElement("input");
    range.type = "range";
    range.min = "1";
    range.max = String(MEMBER_AVATAR_MAX_ZOOM);
    range.step = "0.01";
    range.value = "1";
    range.disabled = true;
    zoomRow.appendChild(range);
    panel.appendChild(zoomRow);

    const error = el("p", "parent-error hidden");
    panel.appendChild(error);
    const actions = el("div", "parent-actions");
    const cancel = button(t("cancel"), "parent-btn ghost", () => close());
    const apply = button(t("avatarCropApply"), "parent-btn primary", () => applyCrop());
    apply.disabled = true;
    actions.appendChild(cancel);
    actions.appendChild(apply);
    panel.appendChild(actions);
    root.appendChild(backdrop);
    root.appendChild(panel);
    document.body.appendChild(root);

    const url = URL.createObjectURL(file);
    const state = { zoom: 1, x: 0, y: 0, base: 1, view: 0, ready: false };
    let busy = false;
    let closed = false;
    const pointers = new Map();
    let dragPoint = null;
    let pinch = null;
    let resize = null;
    let previousFocus = document.activeElement;

    function close() {
      if (closed) return;
      closed = true;
      document.removeEventListener("keydown", onKey, true);
      if (resize) window.removeEventListener("resize", resize);
      URL.revokeObjectURL(url);
      root.remove();
      if (previousFocus && typeof previousFocus.focus === "function") {
        try { previousFocus.focus(); } catch (_) { /* ignore */ }
      }
    }

    function onKey(event) {
      if (event.key !== "Escape") return;
      event.preventDefault();
      event.stopPropagation();
      close();
    }
    document.addEventListener("keydown", onKey, true);
    backdrop.addEventListener("click", close);

    function layoutImage() {
      if (!state.ready) return;
      const view = viewport.clientWidth;
      if (!view) return;
      state.view = view;
      state.base = Math.max(
        view / image.naturalWidth,
        view / image.naturalHeight
      );
      image.style.width = (image.naturalWidth * state.base) + "px";
      image.style.height = (image.naturalHeight * state.base) + "px";
      render();
    }

    function render() {
      if (!state.ready) return;
      const view = state.view || viewport.clientWidth;
      const width = image.naturalWidth * state.base * state.zoom;
      const height = image.naturalHeight * state.base * state.zoom;
      const maxX = Math.max(0, (width - view) / 2);
      const maxY = Math.max(0, (height - view) / 2);
      state.x = clampNumber(state.x, -maxX, maxX);
      state.y = clampNumber(state.y, -maxY, maxY);
      image.style.left = "50%";
      image.style.top = "50%";
      image.style.transform =
        "translate(calc(-50% + " + state.x + "px), calc(-50% + " + state.y + "px)) " +
        "scale(" + state.zoom + ")";
      range.value = String(state.zoom);
    }

    function setZoom(next) {
      state.zoom = clampNumber(Number(next) || 1, 1, MEMBER_AVATAR_MAX_ZOOM);
      render();
    }

    function pointerCenterDistance() {
      const points = Array.from(pointers.values());
      if (points.length < 2) return 0;
      return Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y);
    }

    viewport.addEventListener("pointerdown", (event) => {
      if (!state.ready || busy) return;
      viewport.setPointerCapture(event.pointerId);
      pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (pointers.size === 1) {
        dragPoint = { x: event.clientX, y: event.clientY };
        pinch = null;
      } else if (pointers.size === 2) {
        dragPoint = null;
        pinch = { distance: pointerCenterDistance(), zoom: state.zoom };
      }
      event.preventDefault();
    });

    viewport.addEventListener("pointermove", (event) => {
      if (!pointers.has(event.pointerId) || busy) return;
      const previous = pointers.get(event.pointerId);
      pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (pointers.size === 1 && dragPoint) {
        state.x += event.clientX - dragPoint.x;
        state.y += event.clientY - dragPoint.y;
        dragPoint = { x: event.clientX, y: event.clientY };
        render();
      } else if (pointers.size === 2 && pinch) {
        const distance = pointerCenterDistance();
        if (pinch.distance > 0) {
          state.zoom = clampNumber(
            pinch.zoom * distance / pinch.distance,
            1,
            MEMBER_AVATAR_MAX_ZOOM
          );
          // Render with a temporary view equal to the previous pointer position: the
          // scale is about the crop center, which keeps the gesture predictable.
          render();
        }
      }
      event.preventDefault();
    });

    function releasePointer(event) {
      pointers.delete(event.pointerId);
      pinch = null;
      if (pointers.size === 1) {
        const only = Array.from(pointers.values())[0];
        dragPoint = { x: only.x, y: only.y };
      } else {
        dragPoint = null;
      }
    }
    viewport.addEventListener("pointerup", releasePointer);
    viewport.addEventListener("pointercancel", releasePointer);
    viewport.addEventListener("wheel", (event) => {
      if (!state.ready || busy) return;
      event.preventDefault();
      setZoom(state.zoom * Math.exp(-event.deltaY * 0.0015));
    }, { passive: false });
    range.addEventListener("input", () => setZoom(range.value));

    function fail() {
      error.textContent = t("avatarCropFailed");
      error.classList.remove("hidden");
      apply.disabled = true;
    }

    image.onload = () => {
      if (closed) return;
      state.ready = true;
      range.disabled = false;
      apply.disabled = false;
      layoutImage();
    };
    image.onerror = fail;
    image.src = url;

    resize = () => layoutImage();
    window.addEventListener("resize", resize);

    function applyCrop() {
      if (!state.ready || busy) return;
      busy = true;
      apply.disabled = true;
      cancel.disabled = true;
      range.disabled = true;
      error.classList.add("hidden");
      apply.textContent = t("avatarCropProcessing");
      const view = state.view || viewport.clientWidth;
      const scale = state.base * state.zoom;
      const size = Math.min(
        view / scale,
        image.naturalWidth,
        image.naturalHeight
      );
      const sourceX = clampNumber(
        image.naturalWidth / 2 - (view / 2 + state.x) / scale,
        0,
        image.naturalWidth - size
      );
      const sourceY = clampNumber(
        image.naturalHeight / 2 - (view / 2 + state.y) / scale,
        0,
        image.naturalHeight - size
      );
      const canvas = document.createElement("canvas");
      canvas.width = MEMBER_AVATAR_OUTPUT_PX;
      canvas.height = MEMBER_AVATAR_OUTPUT_PX;
      const context = canvas.getContext("2d");
      if (!context) {
        busy = false;
        apply.disabled = false;
        cancel.disabled = false;
        range.disabled = false;
        apply.textContent = t("avatarCropApply");
        fail();
        return;
      }
      context.fillStyle = "#000";
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.imageSmoothingEnabled = true;
      context.imageSmoothingQuality = "high";
      try {
        context.drawImage(
          image,
          sourceX,
          sourceY,
          size,
          size,
          0,
          0,
          canvas.width,
          canvas.height
        );
      } catch (_) {
        busy = false;
        apply.disabled = false;
        cancel.disabled = false;
        range.disabled = false;
        apply.textContent = t("avatarCropApply");
        fail();
        return;
      }
      canvasJpegBlob(canvas, (blob) => {
        if (closed) return;
        if (!blob) {
          busy = false;
          apply.disabled = false;
          cancel.disabled = false;
          range.disabled = false;
          apply.textContent = t("avatarCropApply");
          fail();
          return;
        }
        close();
        onApply(blob);
      });
    }

    apply.focus();
  }

  function familyMembers() {
    return (Array.isArray(snap && snap.members) ? snap.members : [])
      .filter((member) => member && member.status === "ACTIVE")
      .slice()
      .sort((a, b) => (Number(a.sortOrder) || 0) - (Number(b.sortOrder) || 0));
  }

  function findMember(userId) {
    return familyMembers().find((member) => member.userId === userId) || null;
  }

  // The TV sends the curriculum's grade labels, so the member sheet uses exactly the same
  // wording as the quiz page. An older cached page falls back to a plain list.
  function memberGradeChoices() {
    const raw = Array.isArray(snap && snap.gradeOptions) ? snap.gradeOptions : [];
    const choices = raw
      .map((option) => ({
        no: Number(option && option.no),
        labels: (option && option.labels) || {}
      }))
      .filter((option) => Number.isInteger(option.no) && option.no >= 1);
    if (choices.length) return choices;
    return [1, 2, 3, 4, 5, 6].map((no) => ({ no, labels: {} }));
  }

  function memberGradeLabel(no) {
    const number = Number(no);
    const option = memberGradeChoices().find((choice) => choice.no === number);
    return (option && option.labels && option.labels[lang])
      || t("memberGradeNumber", { n: number });
  }

  /** A member with no grade/semester is unrestricted, so both fields stay empty. */
  function memberScopeSummary(member) {
    const grade = Number(member && member.grade);
    if (!Number.isInteger(grade) || grade < 1) return "";
    const semester = Number(member && member.semester);
    const label = memberGradeLabel(grade);
    if (semester === 1 || semester === 2) {
      return label + " · " + t(semester === 2 ? "semesterSecond" : "semesterFirst");
    }
    return label;
  }

  function memberScopeFieldset(initialGrade, initialSemester, editable = true) {
    const field = el("div", "parent-field");
    const row = el("div", "parent-scope-row");

    function selectField(labelKey) {
      const box = el("div", "parent-scope-field");
      box.appendChild(el("span", "parent-field-label", t(labelKey)));
      const select = el("select", "parent-name-input parent-member-select");
      select.disabled = !editable;
      box.appendChild(select);
      return { box, select };
    }

    const grade = selectField("memberGradeLabel");
    const allGrades = el("option", null, t("memberGradeAll"));
    allGrades.value = "";
    grade.select.appendChild(allGrades);
    for (const choice of memberGradeChoices()) {
      const option = el("option", null, memberGradeLabel(choice.no));
      option.value = String(choice.no);
      grade.select.appendChild(option);
    }

    const semester = selectField("memberSemesterLabel");
    const allSemesters = el("option", null, t("memberSemesterAll"));
    allSemesters.value = "";
    semester.select.appendChild(allSemesters);
    [1, 2].forEach((value) => {
      const option = el("option", null, t(value === 2 ? "semesterSecond" : "semesterFirst"));
      option.value = String(value);
      semester.select.appendChild(option);
    });

    function syncSemester() {
      const hasGrade = grade.select.value !== "";
      semester.select.disabled = !editable || !hasGrade;
      if (!hasGrade) semester.select.value = "";
    }

    grade.select.value = Number(initialGrade) >= 1 ? String(Number(initialGrade)) : "";
    semester.select.value = Number(initialSemester) === 1 || Number(initialSemester) === 2
      ? String(Number(initialSemester))
      : "";
    grade.select.addEventListener("change", syncSemester);
    syncSemester();

    row.appendChild(grade.box);
    row.appendChild(semester.box);
    field.appendChild(row);
    field.appendChild(el("p", "parent-hint", t("memberGradeHint")));

    return {
      field,
      read: () => ({
        grade: Number(grade.select.value) || 0,
        semester: Number(semester.select.value) || 0
      })
    };
  }

  /**
   * The 6-digit switch password. The avatar beside the input names whose password it is;
   * the stored value is never rendered. A legacy member (no stored credential) shows the
   * initial-password placeholder, an existing custom one shows "leave unchanged".
   */
  function memberSwitchPinField(options) {
    const opts = options || {};
    const field = el("div", "parent-field");
    field.appendChild(el("label", "parent-field-label", t("userPinLabel")));
    const line = el("div", "parent-pin-line");
    const avatar = el("span", "parent-avatar");
    const input = el("input", "parent-pin-input");
    const seeded = opts.value ? String(opts.value) : "";
    // The built-in 123456 is a documented first-run credential, not a secret: show it
    // directly for the default member so the parent can see what the TV expects. As
    // soon as the field differs from that default, mask the custom password.
    input.type = opts.reveal && seeded ? "text" : "password";
    input.inputMode = "numeric";
    input.autocomplete = "new-password";
    input.maxLength = 6;
    input.placeholder = opts.placeholderKey
      ? t(opts.placeholderKey)
      : opts.initial ? t("userPinInitial") : t("userPinKeep");
    input.disabled = Boolean(opts.disabled);
    if (seeded) input.value = seeded;
    input.addEventListener("input", () => {
      input.value = input.value.replace(/\D/g, "").slice(0, 6);
      if (opts.reveal && seeded) {
        input.type = input.value === seeded ? "text" : "password";
      }
    });
    line.appendChild(avatar);
    line.appendChild(input);
    field.appendChild(line);
    field.appendChild(el("p", "parent-hint", t("userPinHint")));

    function paint(preset) {
      avatar.innerHTML = avatarSvg(preset || "rabbit");
    }
    paint(opts.preset);

    return {
      field,
      input,
      paint,
      read: () => input.value.trim()
    };
  }

  function memberSourceText(member) {
    return member.source === "CLOUD" ? t("memberSourceCloud") : t("memberSourceLocal");
  }

  function familySectionHead(count) {
    const head = el("div", "parent-family-head");
    head.appendChild(el("p", "parent-section-label", t("familyGroup")));
    head.appendChild(el("span", "parent-family-count", t("familyMemberCount", { n: count })));
    return head;
  }

  function renderFamilyCard() {
    const card = el("div", "parent-card parent-family-card");
    const members = familyMembers();
    for (const member of members) {
      const node = el("button", "parent-row parent-member-row");
      node.type = "button";
      node.appendChild(memberAvatarNode(member, "parent-avatar-member"));
      const main = el("div", "parent-row-main");
      const title = el("span", "parent-member-name");
      title.appendChild(document.createTextNode(member.name || ""));
      if (member.isActive) title.appendChild(el("span", "parent-member-badge", t("familyActiveOnTv")));
      main.appendChild(title);
      const scopeText = memberScopeSummary(member);
      main.appendChild(el(
        "span",
        "parent-row-sub",
        [scopeText, memberSourceText(member)].filter(Boolean).join(" · ")
      ));
      node.appendChild(main);
      node.appendChild(el("span", "chevron", "›"));
      node.addEventListener("click", () => openMemberSheet(member.userId));
      card.appendChild(node);
    }
    const canAdd = Boolean(snap.capabilities && snap.capabilities.canAddMember) && snap.mode !== "CLOUD";
    const add = el("button", "parent-row parent-add-member");
    add.type = "button";
    add.disabled = !canAdd;
    add.appendChild(el("span", "parent-add-icon", "+"));
    const addMain = el("div", "parent-row-main");
    addMain.appendChild(el("span", "parent-row-title", t("familyAddChild")));
    addMain.appendChild(el("span", "parent-row-sub", canAdd ? null : t("familyAddChildDisabled")));
    add.appendChild(addMain);
    if (canAdd) add.addEventListener("click", openAddMemberSheet);
    card.appendChild(add);
    card.appendChild(el(
      "p",
      "parent-family-note",
      snap.mode === "CLOUD" ? t("familyCloudReadOnly") : t("familySingleNote")
    ));
    return card;
  }

  function openAddMemberSheet() {
    const editable = Boolean(snap.capabilities && snap.capabilities.canEditFamily) && snap.mode !== "CLOUD";
    if (!editable) return;
    // The add sheet matches the edit sheet: the chosen preset or a cropped phone photo is
    // previewed in the same hero. The photo stays on the phone until the member exists,
    // because the avatar route needs a userId; it is uploaded immediately after creation.
    // Historical installs with no roster are represented by a first member named 大宝 and
    // the documented 123456 default; later members must choose their own password.
    const firstMember = familyMembers().length === 0;
    let draftPhotoBlob = null;
    let draftPhotoUrl = null;
    openSheet((sheet, close) => {
      sheet.appendChild(el("h2", null, t("familyAddChild")));

      const hero = el("div", "parent-member-hero");
      const avatarSlot = el("span", "parent-avatar-slot");
      hero.appendChild(avatarSlot);
      const heroMain = el("div", "parent-member-hero-main");
      const heroName = el("strong", "parent-member-hero-name", "");
      const heroSub = el("p", "parent-member-hero-sub", t("memberSourceLocal"));
      heroMain.appendChild(heroName);
      heroMain.appendChild(heroSub);
      hero.appendChild(heroMain);
      sheet.appendChild(hero);

      const field = el("div", "parent-field");
      field.appendChild(el("label", "parent-field-label", t("memberNameLabel")));
      const nameInput = el("input", "parent-name-input");
      nameInput.type = "text";
      nameInput.maxLength = 12;
      nameInput.autocomplete = "off";
      nameInput.placeholder = t("memberNamePlaceholder");
      if (firstMember) nameInput.value = "大宝";
      field.appendChild(nameInput);
      sheet.appendChild(field);

      const scopeFields = memberScopeFieldset(0, 0);
      sheet.appendChild(scopeFields.field);

      let draftPreset = "rabbit";
      let pinField = null;
      let uploadPhoto = null;
      let removePhoto = null;
      let submitting = false;

      function clearDraftPhoto() {
        if (draftPhotoUrl) URL.revokeObjectURL(draftPhotoUrl);
        draftPhotoUrl = null;
        draftPhotoBlob = null;
      }

      function paintMediaActions() {
        if (!uploadPhoto || !removePhoto) return;
        removePhoto.hidden = !draftPhotoBlob;
        uploadPhoto.textContent = draftPhotoBlob ? t("memberChangePhoto") : t("memberUploadPhoto");
      }

      function setSubmitting(value) {
        submitting = value;
        if (!uploadPhoto) return;
        uploadPhoto.disabled = value;
        removePhoto.disabled = value;
        fileInput.disabled = value;
        for (const choice of options.querySelectorAll(".parent-avatar-option")) {
          choice.disabled = value;
        }
      }

      function paintHero() {
        clear(avatarSlot);
        const preview = el("span", "parent-avatar parent-avatar-sheet");
        if (draftPhotoUrl) {
          const image = document.createElement("img");
          image.src = draftPhotoUrl;
          image.alt = "";
          preview.appendChild(image);
        } else {
          preview.innerHTML = avatarSvg(draftPreset);
        }
        avatarSlot.appendChild(preview);
        heroName.textContent = nameInput.value.trim() || t("memberNewLabel");
      }

      sheet.appendChild(el("span", "parent-field-label", t("memberAvatarLabel")));
      const options = el("div", "parent-avatar-options");
      function paintOptions() {
        clear(options);
        for (const key of MEMBER_AVATAR_KEYS) {
          const choice = el("button", "parent-avatar-option" + (key === draftPreset ? " is-selected" : ""));
          choice.type = "button";
          choice.setAttribute("aria-label", t(MEMBER_AVATAR_LABELS[key]));
          choice.innerHTML = avatarSvg(key);
          choice.addEventListener("click", () => {
            if (submitting) return;
            draftPreset = key;
            clearDraftPhoto();
            paintOptions();
            paintHero();
            paintMediaActions();
            if (pinField) pinField.paint(draftPreset);
          });
          options.appendChild(choice);
        }
      }
      paintOptions();
      sheet.appendChild(options);

      // Only the first member keeps the historical 123456 default, shown directly so the
      // parent can see it. A later member starts empty and cannot be added without a
      // 6-digit password of its own.
      pinField = memberSwitchPinField({
        preset: draftPreset,
        initial: firstMember,
        value: firstMember ? "123456" : "",
        reveal: firstMember,
        placeholderKey: "userPinRequired"
      });
      sheet.appendChild(pinField.field);

      const mediaActions = el("div", "parent-avatar-actions");
      const fileInput = el("input", "hidden");
      fileInput.type = "file";
      fileInput.accept = "image/*";
      uploadPhoto = button(t("memberUploadPhoto"), "parent-btn ghost", () => {
        if (!submitting) fileInput.click();
      });
      removePhoto = button(t("memberRemovePhoto"), "parent-btn ghost danger-text", () => {
        if (submitting) return;
        clearDraftPhoto();
        paintHero();
        paintMediaActions();
      });
      mediaActions.appendChild(uploadPhoto);
      mediaActions.appendChild(removePhoto);
      mediaActions.appendChild(fileInput);
      sheet.appendChild(mediaActions);
      sheet.appendChild(el("p", "parent-hint", t("familyLocalOnly")));

      const error = el("p", "parent-error hidden");
      sheet.appendChild(error);
      function showError(text) {
        error.textContent = text;
        error.classList.remove("hidden");
      }

      fileInput.addEventListener("change", () => {
        const file = fileInput.files && fileInput.files[0];
        fileInput.value = "";
        if (!file || submitting) return;
        error.classList.add("hidden");
        if (!/^image\//.test(file.type || "")) {
          showError(t("memberImageType"));
          return;
        }
        if (file.size > MEMBER_AVATAR_SOURCE_BYTES) {
          showError(t("memberImageTooLarge"));
          return;
        }
        if (typeof host.upload !== "function") {
          showError(t("featureUnavailable"));
          return;
        }
        // Same phone-side crop as the edit sheet: only the 512px JPEG reaches the TV.
        openAvatarCropper(file, (blob) => {
          clearDraftPhoto();
          draftPhotoBlob = blob;
          draftPhotoUrl = URL.createObjectURL(blob);
          paintHero();
          paintMediaActions();
        });
      });

      const actions = el("div", "parent-actions");
      actions.appendChild(button(t("cancel"), "parent-btn ghost", close));
      const add = button(t("memberAddAction"), "parent-btn primary", async () => {
        const name = nameInput.value.trim();
        error.classList.add("hidden");
        if (!name) {
          showError(t("memberNameInvalid"));
          nameInput.focus();
          return;
        }
        const pin = pinField.read();
        if (!pin) {
          showError(t("userPinRequired"));
          pinField.input.focus();
          return;
        }
        if (!/^\d{6}$/.test(pin)) {
          showError(t("userPinInvalid"));
          pinField.input.focus();
          return;
        }
        setSubmitting(true);
        add.disabled = true;
        const pendingPhoto = draftPhotoBlob;
        const beforeIds = new Set(familyMembers().map((member) => member.userId));
        try {
          const scope = scopeFields.read();
          const body = {
            name: name,
            avatarPreset: draftPreset,
            grade: scope.grade,
            semester: scope.semester,
            switchPin: pin
          };
          const data = await host.request("/api/parent/subjects/add", {
            method: "POST",
            body: body,
            editorToken: editorToken,
            quizAuth: true
          });
          if (data && data.snapshot) adoptSnapshot(data.snapshot, false);
          else await refreshSnapshot();
          const added = familyMembers().find((member) => !beforeIds.has(member.userId)) || null;
          if (pendingPhoto && !added) {
            close();
            setNotice(t("memberAdded") + " · " + t("memberImageSaveFailed"), true);
            render();
            if (host.memberProfileChanged) host.memberProfileChanged();
            return;
          }
          if (pendingPhoto && added) {
            try {
              const uploaded = await host.upload(
                "/api/parent/subjects/avatar?userId=" + encodeURIComponent(added.userId),
                pendingPhoto,
                editorToken,
                null,
                "image/jpeg"
              );
              if (uploaded && uploaded.snapshot) adoptSnapshot(uploaded.snapshot, false);
              else await refreshSnapshot();
            } catch (uploadError) {
              if (isLeaseError(uploadError)) {
                close();
                handleLeaseError(uploadError);
                return;
              }
              // The member exists with the selected icon; the photo is the only failed
              // part, so report that instead of asking the parent to add the child again.
              try { await refreshSnapshot(); } catch (_) { /* keep the added snapshot */ }
              close();
              setNotice(t("memberAdded") + " · " + t("memberImageSaveFailed"), true);
              render();
              if (host.memberProfileChanged) host.memberProfileChanged();
              return;
            }
          }
          close();
          setNotice(successText("SUBJECT_ADDED"), false);
          render();
          if (host.memberProfileChanged) host.memberProfileChanged();
        } catch (err) {
          if (isLeaseError(err)) {
            close();
            handleLeaseError(err);
            return;
          }
          setSubmitting(false);
          add.disabled = false;
          showError(errorText(err) || t("memberAddFailed"));
        }
      });
      actions.appendChild(add);
      sheet.appendChild(actions);
      nameInput.addEventListener("input", paintHero);
      paintHero();
      paintMediaActions();
      nameInput.focus();
    }, () => {
      if (draftPhotoUrl) URL.revokeObjectURL(draftPhotoUrl);
      draftPhotoUrl = null;
      draftPhotoBlob = null;
    });
  }

  function openMemberSheet(userId) {
    const initial = findMember(userId);
    if (!initial) return;
    const editable = Boolean(snap.capabilities && snap.capabilities.canEditFamily) && snap.mode !== "CLOUD";
    openSheet((sheet, close) => {
      sheet.appendChild(el("h2", null, t("memberEditTitle")));
      const hero = el("div", "parent-member-hero");
      const avatarSlot = el("span", "parent-avatar-slot");
      hero.appendChild(avatarSlot);
      const heroMain = el("div", "parent-member-hero-main");
      const heroName = el("strong", "parent-member-hero-name", "");
      const heroSub = el("p", "parent-member-hero-sub", "");
      heroMain.appendChild(heroName);
      heroMain.appendChild(heroSub);
      hero.appendChild(heroMain);
      sheet.appendChild(hero);

      let draftPreset = initial.avatarPreset || "rabbit";
      // Choosing a preset over an uploaded photo is a draft until Save; cancelling must not
      // delete the family photo. The delete request is sent only after the profile write.
      let dropPhoto = false;

      function currentMember() {
        return findMember(userId) || initial;
      }

      function paintHero() {
        const member = currentMember();
        clear(avatarSlot);
        if (dropPhoto) {
          const preview = el("span", "parent-avatar parent-avatar-sheet");
          preview.innerHTML = avatarSvg(draftPreset);
          avatarSlot.appendChild(preview);
        } else {
          avatarSlot.appendChild(memberAvatarNode(member, "parent-avatar-sheet"));
        }
        heroName.textContent = member.name || "";
        heroSub.textContent = member.isActive
          ? t("familyActiveOnTv")
          : memberSourceText(member);
        removePhoto.hidden = !member.avatarUrl || !editable;
        uploadPhoto.textContent = member.avatarUrl && !dropPhoto
          ? t("memberChangePhoto")
          : t("memberUploadPhoto");
      }

      const field = el("div", "parent-field");
      field.appendChild(el("label", "parent-field-label", t("memberNameLabel")));
      const nameInput = el("input", "parent-name-input");
      nameInput.type = "text";
      nameInput.maxLength = 12;
      nameInput.autocomplete = "off";
      nameInput.placeholder = t("memberNamePlaceholder");
      nameInput.value = initial.name || "";
      nameInput.disabled = !editable;
      field.appendChild(nameInput);
      sheet.appendChild(field);

      const scopeFields = memberScopeFieldset(initial.grade, initial.semester, editable);
      sheet.appendChild(scopeFields.field);

      let pinField = null;
      sheet.appendChild(el("span", "parent-field-label", t("memberAvatarLabel")));
      const options = el("div", "parent-avatar-options");
      function paintOptions() {
        clear(options);
        for (const key of MEMBER_AVATAR_KEYS) {
          const choice = el("button", "parent-avatar-option" + (key === draftPreset ? " is-selected" : ""));
          choice.type = "button";
          choice.disabled = !editable;
          choice.setAttribute("aria-label", t(MEMBER_AVATAR_LABELS[key]));
          choice.innerHTML = avatarSvg(key);
          choice.addEventListener("click", () => {
            draftPreset = key;
            dropPhoto = Boolean(currentMember().avatarUrl);
            paintOptions();
            paintHero();
            if (pinField) pinField.paint(draftPreset);
          });
          options.appendChild(choice);
        }
      }
      paintOptions();
      sheet.appendChild(options);

      // The default member still has the documented 123456 credential; show it so the
      // parent is not left guessing. A custom password is never rendered: the field stays
      // empty and "keep unchanged" is the placeholder.
      const initialPin = Boolean(initial.switchPinInitial) ? "123456" : "";
      pinField = memberSwitchPinField({
        preset: draftPreset,
        initial: Boolean(initial.switchPinInitial),
        disabled: !editable,
        value: initialPin,
        reveal: Boolean(initial.switchPinInitial)
      });
      sheet.appendChild(pinField.field);

      const mediaActions = el("div", "parent-avatar-actions");
      const fileInput = el("input", "hidden");
      fileInput.type = "file";
      fileInput.accept = "image/*";
      const uploadPhoto = button(t("memberUploadPhoto"), "parent-btn ghost", () => fileInput.click());
      uploadPhoto.disabled = !editable;
      const removePhoto = button(t("memberRemovePhoto"), "parent-btn ghost danger-text", async () => {
        if (!editable) return;
        hideError();
        try {
          await host.request("/api/parent/subjects/avatar/delete", {
            method: "POST",
            body: { userId: userId },
            editorToken: editorToken,
            quizAuth: true
          });
          dropPhoto = false;
          await refreshSnapshot();
          render();
          paintHero();
          // The family card, member header and the quiz page all show this avatar too;
          // refresh them from the new revision instead of waiting for a page reload.
          if (host.memberProfileChanged) host.memberProfileChanged();
        } catch (error) {
          if (isLeaseError(error)) {
            close();
            handleLeaseError(error);
            return;
          }
          showError(errorText(error));
        }
      });
      mediaActions.appendChild(uploadPhoto);
      mediaActions.appendChild(removePhoto);
      mediaActions.appendChild(fileInput);
      sheet.appendChild(mediaActions);

      const hint = el(
        "p",
        "parent-hint",
        snap.mode === "CLOUD" ? t("familyCloudReadOnly") : t("familyLocalOnly")
      );
      sheet.appendChild(hint);

      if (editable) {
        const deleteArea = el("div", "parent-member-delete");
        // Keep the action visible even when blocked; a hint-only area made the feature
        // hard to find for the TV's current or default member.
        const deleteButton = button(
          t("memberDeleteAction"),
          "parent-btn ghost danger-text",
          () => {
            const member = currentMember();
            confirmSheet(
              t("memberDeleteTitle"),
              t("memberDeleteText", { name: member.name || "" }),
              t("memberDeleteAction"),
              async () => {
                try {
                  const data = await requestWithSensitive(
                    "/api/parent/subjects/delete",
                    {
                      method: "POST",
                      body: { userId: userId },
                      editorToken: editorToken,
                      quizAuth: true
                    },
                    "RULES_EDIT",
                    "sensitivePinLeadDelete"
                  );
                  if (!data) return;
                  const cached = memberAvatarUrls.get(userId);
                  if (cached) {
                    URL.revokeObjectURL(cached.url);
                    memberAvatarUrls.delete(userId);
                  }
                  if (data.snapshot) adoptSnapshot(data.snapshot, false);
                  else await refreshSnapshot();
                  setNotice(successText((data && data.messageKey) || "SUBJECT_DELETED"), false);
                  render();
                  if (host.memberProfileChanged) host.memberProfileChanged();
                } catch (error) {
                  if (isLeaseError(error)) {
                    handleLeaseError(error);
                    return;
                  }
                  setNotice(errorText(error) || t("memberDeleteFailed"), true);
                  render();
                }
              }
            );
          }
        );
        deleteButton.disabled = initial.isActive || initial.isDefault;
        deleteArea.appendChild(deleteButton);
        if (initial.isActive) {
          deleteArea.appendChild(el("p", "parent-hint", t("familyDeleteActiveHint")));
        } else if (initial.isDefault) {
          deleteArea.appendChild(el("p", "parent-hint", t("familyDeleteDefaultHint")));
        }
        sheet.appendChild(deleteArea);
      }

      const error = el("p", "parent-error hidden");
      const ok = el("p", "parent-ok hidden");
      sheet.appendChild(error);
      sheet.appendChild(ok);

      function showError(text) {
        ok.classList.add("hidden");
        error.textContent = text;
        error.classList.remove("hidden");
      }

      function hideError() {
        error.classList.add("hidden");
      }

      fileInput.addEventListener("change", () => {
        const file = fileInput.files && fileInput.files[0];
        fileInput.value = "";
        if (!file || !editable) return;
        hideError();
        ok.classList.add("hidden");
        if (!/^image\//.test(file.type || "")) {
          showError(t("memberImageType"));
          return;
        }
        if (file.size > MEMBER_AVATAR_SOURCE_BYTES) {
          showError(t("memberImageTooLarge"));
          return;
        }
        if (typeof host.upload !== "function") {
          showError(t("featureUnavailable"));
          return;
        }
        // The source photo never goes to the TV. The phone crops it to a square and sends a
        // 512px JPEG, so a large camera photo works without making the TV decode it.
        openAvatarCropper(file, async (blob) => {
          uploadPhoto.disabled = true;
          uploadPhoto.textContent = t("avatarCropProcessing");
          try {
            const data = await host.upload(
              "/api/parent/subjects/avatar?userId=" + encodeURIComponent(userId),
              blob,
              editorToken,
              null,
              "image/jpeg"
            );
            dropPhoto = false;
            if (data && data.snapshot) adoptSnapshot(data.snapshot, false);
            else await refreshSnapshot();
            // Repaint the page behind the sheet and tell the quiz header about the new
            // avatar revision; otherwise only this hero would show the new photo.
            render();
            paintHero();
            if (host.memberProfileChanged) host.memberProfileChanged();
            ok.textContent = t("memberSaved");
            ok.classList.remove("hidden");
          } catch (err) {
            if (isLeaseError(err)) {
              close();
              handleLeaseError(err);
              return;
            }
            showError(errorText(err));
          } finally {
            uploadPhoto.disabled = !editable;
            uploadPhoto.textContent = currentMember().avatarUrl && !dropPhoto
              ? t("memberChangePhoto")
              : t("memberUploadPhoto");
          }
        });
      });

      const actions = el("div", "parent-actions");
      actions.appendChild(button(t("cancel"), "parent-btn ghost", close));
      if (editable) {
        const save = button(t("memberSaveAction"), "parent-btn primary", async () => {
          const name = nameInput.value.trim();
          hideError();
          if (!name) {
            showError(t("memberNameInvalid"));
            nameInput.focus();
            return;
          }
          const pin = pinField.read();
          if (pin && !/^\d{6}$/.test(pin)) {
            showError(t("userPinInvalid"));
            pinField.input.focus();
            return;
          }
          save.disabled = true;
          try {
            const scope = scopeFields.read();
            const body = {
              userId: userId,
              name: name,
              avatarPreset: draftPreset,
              grade: scope.grade,
              semester: scope.semester
            };
            // Leaving the visible 123456 default untouched must keep the initial
            // credential instead of rewriting it as a custom PBKDF2 hash.
            if (pin && !(initial.switchPinInitial && pin === "123456")) {
              body.switchPin = pin;
            }
            const data = await host.request("/api/parent/subjects/profile", {
              method: "POST",
              body: body,
              editorToken: editorToken,
              quizAuth: true
            });
            if (dropPhoto && currentMember().avatarUrl) {
              await host.request("/api/parent/subjects/avatar/delete", {
                method: "POST",
                body: { userId: userId },
                editorToken: editorToken,
                quizAuth: true
              });
            }
            await refreshSnapshot();
            close();
            setNotice(successText((data && data.messageKey) || "SUBJECT_SAVED"), false);
            render();
            // The quiz page mirrors the member list; refresh it so a changed grade or
            // semester becomes the default scope without a page reload.
            if (host.memberProfileChanged) host.memberProfileChanged();
          } catch (err) {
            if (isLeaseError(err)) {
              close();
              handleLeaseError(err);
              return;
            }
            save.disabled = false;
            showError(errorText(err));
          }
        });
        actions.appendChild(save);
      }
      sheet.appendChild(actions);
      paintHero();
    });
  }

  function unlimitedPackages() {
    const value = snap && snap.rules && snap.rules.unlimitedPackages;
    return Array.isArray(value) ? value : [];
  }

  /**
   * One tappable app tile. V45 routes every icon click straight to its picker, so the tile
   * is the entry and `+`/`+N` is the same entry; there is no intermediate summary page.
   */
  function appIconTile(options) {
    const tile = el("button", "parent-icon-tile");
    tile.type = "button";
    const icon = el("span", "parent-app-icon");
    const label = options.label || "";
    if (options.pkg) {
      icon.appendChild(el("span", "parent-app-initial",
        (label.trim()[0] || "?").toUpperCase()));
      requestAppIcon(options.pkg, icon);
    } else {
      tile.classList.add("is-add");
      icon.appendChild(el("span", "parent-app-initial", options.marker || "+"));
    }
    tile.appendChild(icon);
    if (options.showLabel && label) {
      const name = el("span", "parent-app-name", label);
      if (options.pkg) name.dataset.studyEarnLabel = options.pkg;
      tile.appendChild(name);
    }
    tile.disabled = Boolean(options.disabled);
    tile.setAttribute("aria-label", options.ariaLabel || label || t("appPickerManage"));
    if (options.onClick) tile.addEventListener("click", options.onClick);
    return tile;
  }

  function appScopeGrid(packages, open, canEdit, maxShown) {
    const grid = el("div", "parent-icon-grid");
    packages.slice(0, maxShown).forEach((pkg) => {
      grid.appendChild(appIconTile({
        pkg,
        label: studyEarnAppLabel(pkg),
        onClick: open,
        disabled: !canEdit,
      }));
    });
    grid.appendChild(appIconTile({
      marker: packages.length > maxShown ? "+" + (packages.length - maxShown) : "+",
      onClick: open,
      disabled: !canEdit,
      ariaLabel: t("appPickerManage"),
    }));
    return grid;
  }

  function appEmptyAdd(label, open, canEdit) {
    const empty = el("button", "parent-empty-add");
    empty.type = "button";
    empty.disabled = !canEdit;
    empty.appendChild(el("span", "parent-empty-add-mark", "+"));
    empty.appendChild(el("span", null, label));
    empty.addEventListener("click", open);
    return empty;
  }

  /** V45 first-level object: the unlimited icons themselves are the way into the picker. */
  function renderUnlimitedCard(blocked) {
    const card = el("div", "parent-card parent-icon-card");
    const head = el("div", "parent-icon-card-head");
    head.appendChild(el("span", "parent-row-title", t("unlimitedApps")));
    head.appendChild(el("span", "parent-row-sub", t("unlimitedAppsHint")));
    card.appendChild(head);
    const canEdit = !blocked && Boolean(snap.capabilities && snap.capabilities.canEditRules);
    const open = () => openPicker("unlimited", "user");
    const packages = unlimitedPackages();
    if (!packages.length) {
      card.appendChild(appEmptyAdd(t("unlimitedEmpty"), open, canEdit));
    } else {
      card.appendChild(appScopeGrid(packages, open, canEdit, 4));
    }
    card.appendChild(el("p", "parent-hint parent-icon-card-hint", t("unlimitedManageHint")));
    return card;
  }

  function renderAppRows() {
    const card = el("div", "parent-card parent-icon-card");
    const rules = snap.rules || {};
    const canEdit = Boolean(snap.capabilities && snap.capabilities.canEditRules);
    const open = () => openPicker("entertainment", "time");
    const allApps = Boolean(rules.allApps);
    const allowed = Array.isArray(rules.allowedPackages) ? rules.allowedPackages : [];
    if (allApps) {
      const grid = el("div", "parent-icon-grid");
      const tile = appIconTile({
        marker: "∞",
        label: t("allApps"),
        showLabel: true,
        onClick: open,
        disabled: !canEdit,
        ariaLabel: t("allApps"),
      });
      tile.classList.add("is-all");
      grid.appendChild(tile);
      card.appendChild(grid);
    } else if (!allowed.length) {
      card.appendChild(appEmptyAdd(t("noEntertainment"), open, canEdit));
    } else {
      card.appendChild(appScopeGrid(allowed, open, canEdit, 4));
    }
    card.appendChild(el("p", "parent-hint parent-icon-card-hint", t("appIconHint")));
    return card;
  }

  /** Stored switch values. All three routes are independent and may be on together. */
  function routeFlags(rules) {
    return {
      quiz: rules.quizGateEnabled !== false,
      study: rules.studyEarnEnabled !== false,
      direct: rules.directStartEnabled === true,
    };
  }

  function sessionCapMinutes() {
    const value = Number(snap && snap.rules && snap.rules.sessionCapMinutes);
    const bounded = Number.isFinite(value) ? value : DEFAULT_SESSION_CAP_MINUTES;
    return Math.min(MAX_SESSION_CAP_MINUTES, Math.max(MIN_SESSION_CAP_MINUTES, bounded));
  }

  function routeCount(field) {
    const value = Number(snap && snap.rules && snap.rules[field]);
    if (!Number.isFinite(value)) return DEFAULT_ROUTE_DAILY_COUNT;
    return Math.min(MAX_ROUTE_DAILY_COUNT, Math.max(0, Math.round(value)));
  }

  function routeCountRow(field, label, canEdit) {
    // The stepper is fixed-width; on a 360px phone the row wraps it under the label instead
    // of squeezing either side.
    const node = el("div", "parent-row parent-route-count");
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", label));
    node.appendChild(main);
    const controls = el("div", "parent-stepper");
    const minus = button("−", "parent-step", () => adjustRouteCount(field, -1));
    const value = button(t("countTimes", { n: routeCount(field) }), "parent-step-value",
      () => setRouteCount(field, routeCount(field)));
    value.dataset.routeCount = field;
    const plus = button("+", "parent-step", () => adjustRouteCount(field, 1));
    [minus, value, plus].forEach((control) => {
      control.disabled = !canEdit;
      controls.appendChild(control);
    });
    node.appendChild(controls);
    return node;
  }

  function adjustRouteCount(field, delta) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    const current = routeCount(field);
    const next = Math.min(MAX_ROUTE_DAILY_COUNT, Math.max(0, current + delta));
    if (next === current) return;
    setRouteCount(field, next);
  }

  function setRouteCount(field, value) {
    const next = clone(snap.rules);
    next.rulesContractVersion = RULES_CONTRACT_VERSION;
    next[field] = Math.min(MAX_ROUTE_DAILY_COUNT, Math.max(0, value));
    if (!saveRules(next)) return;
    paintRouteCounts();
  }

  function paintRouteCounts() {
    document.querySelectorAll(".parent-step-value[data-route-count]").forEach((node) => {
      node.textContent = t("countTimes", { n: routeCount(node.dataset.routeCount) });
    });
  }

  function paintRouteHints() {
    const cap = sessionCapMinutes();
    document.querySelectorAll("[data-route-hint]").forEach((node) => {
      const key = node.dataset.routeHint;
      if (key === "quiz") node.textContent = t("quizAutoLength", { n: cap });
      else if (key === "direct") node.textContent = t("directMaxHint", { n: cap });
      else if (key === "studyReward") {
        node.textContent = t("studyRewardMaxHint", { n: cap });
      }
    });
  }

  function renderWatchMethods() {
    const canEdit = Boolean(snap.capabilities && snap.capabilities.canEditRules);
    const rules = snap.rules || {};
    const flags = routeFlags(rules);
    const cap = sessionCapMinutes();
    const card = el("div", "parent-card");
    card.appendChild(switchRow(
      t("quizChallenge"), t("quizGateSwitchHint"), flags.quiz, !canEdit,
      (input) => setRouteSwitch("quizGateEnabled", input)
    ));
    // V45: a route that is off shows only its switch row. Its body stays out of the page
    // entirely — no retained counts, no manage buttons, no half-configured controls.
    if (flags.quiz) {
      card.appendChild(routeCountRow("quizDailyLimitCount", t("routeDailyCount"), canEdit));
      const quizHint = el("p", "parent-hint", t("quizAutoLength", { n: cap }));
      quizHint.dataset.routeHint = "quiz";
      card.appendChild(quizHint);
    }
    card.appendChild(switchRow(
      t("studyEarnTitle"), t("studyEarnSwitchHint"), flags.study, !canEdit,
      (input) => setRouteSwitch("studyEarnEnabled", input)
    ));
    if (flags.study) card.appendChild(renderStudyMethodBody(canEdit));
    card.appendChild(switchRow(
      t("directStartTitle"), t("directStartSwitchHint"), flags.direct, !canEdit,
      (input) => setRouteSwitch("directStartEnabled", input)
    ));
    if (flags.direct) card.appendChild(renderDirectMethodBody(canEdit));
    if (!flags.quiz && !flags.study && !flags.direct) {
      card.appendChild(el("p", "parent-hint", t("routesOffHint")));
    }
    return card;
  }

  function renderStudyMethodBody(canEdit) {
    const rules = snap.rules || {};
    // studyPackages is the only study list in V45; studyEarnPackages is normalized to match.
    const study = Array.isArray(rules.studyPackages) ? rules.studyPackages : [];
    const body = el("div", "parent-method-body");
    body.appendChild(studyEarnStepRow("study", t("studyEarnStudyMinutes"), canEdit));
    body.appendChild(studyEarnStepRow("reward", t("studyEarnRewardMinutes"), canEdit));
    const rewardHint = el("p", "parent-hint", t("studyRewardMaxHint", {
      n: sessionCapMinutes(),
    }));
    rewardHint.dataset.routeHint = "studyReward";
    body.appendChild(rewardHint);
    body.appendChild(routeCountRow("studyRewardDailyLimitCount", t("routeDailyCount"), canEdit));
    body.appendChild(el("div", "parent-method-title", t("studyEarnApps")));
    const open = () => openPicker("study", "time");
    const shown = study.length > 3 ? study.slice(0, 3) : study;
    if (!study.length) {
      const grid = el("div", "parent-icon-grid");
      grid.appendChild(appIconTile({
        marker: "+",
        onClick: open,
        disabled: !canEdit,
        ariaLabel: t("addStudyApps"),
      }));
      body.appendChild(grid);
      body.appendChild(el("p", "parent-hint", t("studyEarnEmptyHint")));
    } else {
      const grid = el("div", "parent-icon-grid");
      shown.forEach((pkg) => {
        grid.appendChild(appIconTile({
          pkg,
          label: studyEarnAppLabel(pkg),
          showLabel: true,
          onClick: open,
          disabled: !canEdit,
        }));
      });
      grid.appendChild(appIconTile({
        marker: study.length > 3 ? "+" + (study.length - 3) : "+",
        onClick: open,
        disabled: !canEdit,
        ariaLabel: t("appPickerManage"),
      }));
      body.appendChild(grid);
    }
    return body;
  }

  function renderDirectMethodBody(canEdit) {
    const body = el("div", "parent-method-body");
    const node = el("div", "parent-row");
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", t("directDuration")));
    node.appendChild(main);
    const controls = el("div", "parent-stepper");
    const minus = button("−", "parent-step",
      () => adjustDirectMinutes(-STEP_DIRECT_MINUTES));
    const value = directMinutes();
    const valueBtn = button(t("minutes", { n: value }), "parent-step-value",
      () => openDirectMinutesSheet(value));
    valueBtn.dataset.directMinutes = "1";
    const plus = button("+", "parent-step",
      () => adjustDirectMinutes(STEP_DIRECT_MINUTES));
    [minus, valueBtn, plus].forEach((control) => {
      control.disabled = !canEdit;
      controls.appendChild(control);
    });
    node.appendChild(controls);
    body.appendChild(node);
    const directHint = el("p", "parent-hint", t("directMaxHint", { n: sessionCapMinutes() }));
    directHint.dataset.routeHint = "direct";
    body.appendChild(directHint);
    body.appendChild(routeCountRow("directDailyFreeCount", t("routeDailyCount"), canEdit));
    body.appendChild(el("p", "parent-hint", t("directPauseNote")));
    return body;
  }

  function directMinutes() {
    const rules = snap && snap.rules;
    const directValue = Number(rules && rules.directSessionMinutes);
    const alias = Number(rules && rules.sessionLimitMinutes);
    const value = Number.isFinite(directValue) ? directValue
      : (Number.isFinite(alias) ? alias : DEFAULT_DIRECT_MINUTES);
    const cap = sessionCapMinutes();
    return Math.min(cap, Math.max(MIN_DIRECT_MINUTES, Math.round(value)));
  }

  /** Switches never pause each other; turning all three off asks for confirmation. */
  function setRouteSwitch(field, input) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    const next = clone(snap.rules);
    const wanted = Boolean(input.checked);
    next[field] = wanted;
    const flags = routeFlags(next);
    if (!wanted && !flags.quiz && !flags.study && !flags.direct) {
      // Keep the switch visually on until the parent confirms; cancel leaves it untouched.
      input.checked = true;
      confirmSheet(
        t("allRoutesOffTitle"), t("allRoutesOffBody"), t("allRoutesOffConfirm"),
        () => {
          next.rulesContractVersion = RULES_CONTRACT_VERSION;
          if (!saveRules(next)) return;
          clearNotices();
          rerenderTimePage();
        }
      );
      return;
    }
    next.rulesContractVersion = RULES_CONTRACT_VERSION;
    if (!saveRules(next)) {
      input.checked = !wanted;
      return;
    }
    clearNotices();
    rerenderTimePage();
  }

  /**
   * 1 minute is kept as an explicit stop below the normal 5-minute grid, so stepping from
   * 1 goes to 5 and back to 1 (or 0 for fields that allow a disabled value).
   */
  function stepMinuteValue(value, delta, min, max) {
    const current = Number(value);
    if (delta > 0) {
      if (current < 1) return Math.min(max, 1);
      if (current < 5) return Math.min(max, 5);
    } else if (delta < 0) {
      if (current <= 1) return min;
      if (current <= 5) return Math.max(min, 1);
    } else {
      return current;
    }
    return Math.min(max, Math.max(min, current + delta));
  }

  function adjustDirectMinutes(delta) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    const current = directMinutes();
    const next = stepMinuteValue(current, delta, MIN_DIRECT_MINUTES, sessionCapMinutes());
    if (next === current) return;
    setDirectMinutes(next);
  }

  function setDirectMinutes(value) {
    const next = clone(snap.rules);
    next.rulesContractVersion = RULES_CONTRACT_VERSION;
    const capped = Math.min(sessionCapMinutes(), Math.max(MIN_DIRECT_MINUTES, value));
    next.sessionLimitMinutes = capped;
    next.directSessionMinutes = capped;
    if (!saveRules(next)) return;
    paintDirectMinutes();
  }

  function paintDirectMinutes() {
    const node = document.querySelector(".parent-step-value[data-direct-minutes]");
    if (node) node.textContent = t("minutes", { n: directMinutes() });
  }

  function openDirectMinutesSheet(current) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    const cap = sessionCapMinutes();
    const allowed = DIRECT_OPTIONS.filter((option) => option <= cap);
    const options = allowed.indexOf(current) >= 0
      ? allowed : allowed.concat(current).sort((a, b) => a - b);
    openLimitSheet(t("directDuration"), current, options, setDirectMinutes);
  }

  function studyEarnMinutes(field) {
    const rules = snap && snap.rules;
    if (field === "studyEarnStudyMinutes") {
      const value = Number(rules && rules.studyEarnStudyMinutes);
      const bounded = Number.isFinite(value) ? value : DEFAULT_STUDY_EARN_STUDY_MINUTES;
      return Math.min(MAX_STUDY_EARN_MINUTES,
        Math.max(MIN_STUDY_EARN_MINUTES, Math.round(bounded)));
    }
    const value = Number(rules && rules.studyEarnRewardMinutes);
    const bounded = Number.isFinite(value) ? value : DEFAULT_STUDY_EARN_REWARD_MINUTES;
    // Reward minutes are independent of study minutes; only the public cap bounds them.
    return Math.min(sessionCapMinutes(),
      Math.max(MIN_STUDY_EARN_MINUTES, Math.round(bounded)));
  }

  function studyEarnField(key) {
    return key === "study" ? "studyEarnStudyMinutes" : "studyEarnRewardMinutes";
  }

  function studyPackagesAll() {
    const value = snap && snap.rules && snap.rules.studyPackages;
    return Array.isArray(value) ? value : [];
  }

  function studyEarnAppLabel(pkg) {
    const apps = studyEarnCatalog || [];
    const found = apps.find((app) => String(app.packageName || "") === pkg);
    return found && found.label ? String(found.label) : pkg;
  }

  /** Labels come from the installed-app list; fetching once keeps the inline list readable. */
  async function ensureStudyEarnCatalog() {
    if (studyEarnCatalog || studyEarnLoading) return;
    studyEarnLoading = true;
    try {
      const data = await host.request("/api/parent/apps", {
        editorToken: editorToken,
        quizAuth: true,
      });
      studyEarnCatalog = Array.isArray(data.apps) ? data.apps : [];
    } catch (_) {
      studyEarnCatalog = [];
    }
    studyEarnLoading = false;
    document.querySelectorAll("[data-study-earn-label]").forEach((node) => {
      node.textContent = studyEarnAppLabel(node.dataset.studyEarnLabel);
    });
  }

  function studyEarnStepRow(key, label, canEdit) {
    const value = studyEarnMinutes(studyEarnField(key));
    const node = el("div", "parent-row");
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", label));
    node.appendChild(main);
    const controls = el("div", "parent-stepper");
    const minus = button("−", "parent-step", () => adjustStudyEarn(key, -STEP_STUDY_EARN_MINUTES));
    const valueBtn = button(t("minutes", { n: value }), "parent-step-value", () => {
      openStudyEarnSheet(key, label, studyEarnMinutes(studyEarnField(key)));
    });
    valueBtn.dataset.studyEarnKey = key;
    const plus = button("+", "parent-step", () => adjustStudyEarn(key, STEP_STUDY_EARN_MINUTES));
    [minus, valueBtn, plus].forEach((control) => {
      control.disabled = !canEdit;
      controls.appendChild(control);
    });
    node.appendChild(controls);
    return node;
  }

  function adjustStudyEarn(key, delta) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    const field = studyEarnField(key);
    const current = studyEarnMinutes(field);
    const max = key === "study" ? MAX_STUDY_EARN_MINUTES : sessionCapMinutes();
    const next = stepMinuteValue(current, delta, MIN_STUDY_EARN_MINUTES, max);
    if (next === current) return;
    setStudyEarn(key, next);
  }

  function setStudyEarn(key, value) {
    const next = clone(snap.rules);
    next.rulesContractVersion = RULES_CONTRACT_VERSION;
    const max = key === "study" ? MAX_STUDY_EARN_MINUTES : sessionCapMinutes();
    next[studyEarnField(key)] = Math.min(max, Math.max(MIN_STUDY_EARN_MINUTES, value));
    if (!saveRules(next)) return;
    paintStudyEarnControls();
    paintRouteHints();
  }

  function paintStudyEarnControls() {
    document.querySelectorAll(".parent-step-value[data-study-earn-key]").forEach((node) => {
      const value = studyEarnMinutes(studyEarnField(node.dataset.studyEarnKey));
      node.textContent = t("minutes", { n: value });
    });
  }

  function openStudyEarnSheet(key, label, value) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    openSheet((sheet, close) => {
      sheet.appendChild(el("h2", null, label));
      const list = el("div", "parent-choice-list");
      const max = key === "study" ? MAX_STUDY_EARN_MINUTES : sessionCapMinutes();
      // Show the 1-minute test stop next to the normal 5-minute grid, never 1, 6, 11...
      const options = MIN_STUDY_EARN_MINUTES <= 1 ? [MIN_STUDY_EARN_MINUTES] : [];
      const firstStep = Math.max(5, MIN_STUDY_EARN_MINUTES);
      for (let option = firstStep; option <= max; option += STEP_STUDY_EARN_MINUTES) {
        options.push(option);
      }
      if (options.indexOf(Number(value)) < 0) options.push(Number(value));
      options.sort((a, b) => a - b);
      options.forEach((option) => {
        const choice = button(t("minutes", { n: option }), "parent-choice", () => {
          setStudyEarn(key, option);
          close();
        });
        choice.setAttribute("aria-pressed", String(option === Number(value)));
        list.appendChild(choice);
      });
      sheet.appendChild(list);
      const actions = el("div", "parent-actions");
      actions.appendChild(button(t("cancel"), "parent-btn ghost", close));
      sheet.appendChild(actions);
    });
  }

  async function enterTimeAndApps() {
    if (!snap) return;
    const context = editingContext();
    if (context.sibling || !context.member) {
      setNotice(t("userSiblingNote", {
        name: context.member ? context.member.name : t("userNoMember"),
      }), true);
      subpage = null;
      render();
      return;
    }
    if (!(snap.capabilities && snap.capabilities.canEditRules)) return;
    // Every visit starts with a fresh proof, even if an old tab still holds one.
    releaseSensitive("RULES_EDIT");
    if (snap.userConfigRequirePin !== false) {
      const token = await requestSensitiveToken("RULES_EDIT", "sensitivePinLeadRules");
      if (!token) return;
    }
    rulesHold = false;
    timeTab = "manage";
    subpage = "time";
    clearNotices();
    render();
    window.scrollTo({ top: 0 });
    // A draft held back by an expired proof can now finish saving under the new proof.
    if (pendingRules && !savingRules) flushRules();
  }

  function leaveTimeAndApps() {
    if (subpage !== "time") return;
    if (rulesTimer) { clearTimeout(rulesTimer); rulesTimer = 0; }
    if (pendingRules || savingRules) {
      // Let the last edit finish under the proof that is still valid, then drop it.
      releaseRulesWhenIdle = true;
      if (pendingRules && !savingRules) flushRules();
    } else {
      releaseSensitive("RULES_EDIT");
    }
    subpage = null;
  }

  /**
   * V48: the page keeps its two top tabs independent of subpage routing. A picker or a
   * save can rebuild the page, but the chosen tab survives until the parent leaves.
   */
  function timeTabButton(tab, label, panelId) {
    const node = button(label, "parent-time-tab", () => selectTimeTab(tab));
    node.id = tab === "manage" ? "timeTabManage" : "timeTabPlan";
    node.dataset.timeTab = tab;
    node.setAttribute("role", "tab");
    node.setAttribute("aria-selected", timeTab === tab ? "true" : "false");
    node.setAttribute("aria-controls", panelId);
    node.tabIndex = timeTab === tab ? 0 : -1;
    return node;
  }

  function selectTimeTab(next) {
    if (next !== "manage" && next !== "plan") return;
    if (timeTab === next) return;
    timeTab = next;
    document.querySelectorAll(".parent-time-tab").forEach((node) => {
      const active = node.dataset.timeTab === timeTab;
      node.setAttribute("aria-selected", active ? "true" : "false");
      node.tabIndex = active ? 0 : -1;
    });
    document.querySelectorAll(".parent-time-panel").forEach((node) => {
      node.hidden = node.dataset.timePanel !== timeTab;
    });
    // The hidden panel stayed in the DOM, so a cap change made on the other tab may not
    // have repainted its hints yet. Refresh both sides from the current draft.
    paintRulesControls();
    // The two panels share one scroll container; starting at its top avoids landing in
    // the middle of the other tab's content.
    const root = $(ROOT_ID);
    if (root) root.scrollTop = 0;
  }

  function timeTabBar() {
    const bar = el("div", "parent-time-tabs");
    bar.setAttribute("role", "tablist");
    bar.setAttribute("aria-label", t("timeTabsLabel"));
    bar.addEventListener("keydown", (event) => {
      if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      event.preventDefault();
      const next = timeTab === "manage" ? "plan" : "manage";
      selectTimeTab(next);
      const nextTab = bar.querySelector(`.parent-time-tab[data-time-tab="${next}"]`);
      if (nextTab) nextTab.focus();
    });
    bar.appendChild(timeTabButton("manage", t("timeTabManage"), "timePanelManage"));
    bar.appendChild(timeTabButton("plan", t("timeTabPlan"), "timePanelPlan"));
    return bar;
  }

  function timeTabPanel(tab, labelledBy) {
    const panel = el("div", "parent-time-panel");
    panel.id = tab === "manage" ? "timePanelManage" : "timePanelPlan";
    panel.dataset.timePanel = tab;
    panel.setAttribute("role", "tabpanel");
    panel.setAttribute("aria-labelledby", labelledBy);
    panel.hidden = timeTab !== tab;
    return panel;
  }

  function renderTimeAndApps() {
    const root = $(ROOT_ID);
    if (!root) return;
    clear(root);
    const shell = el("div", "parent-shell");
    shell.appendChild(barNode(t("timeAndApps"), moreStatusText(), () => {
      leaveTimeAndApps();
      clearNotices();
      render();
      window.scrollTo({ top: 0 });
    }));
    shell.appendChild(timeTabBar());
    const content = el("div", "parent-content");
    content.appendChild(noticeArea());
    content.appendChild(el("p", "parent-hint", t("timePageHint")));
    // V48 keeps V45's relative order inside each tab. The app grid and the unlock routes
    // are app management; shared limits and day groups are the time plan. Unlimited apps
    // stay on the user index, not here.
    const managePanel = timeTabPanel("manage", "timeTabManage");
    managePanel.appendChild(sectionLabel(t("appScope")));
    managePanel.appendChild(renderAppRows());
    managePanel.appendChild(sectionLabel(t("watchMethods")));
    managePanel.appendChild(renderWatchMethods());

    const planPanel = timeTabPanel("plan", "timeTabPlan");
    planPanel.appendChild(sectionLabel(t("publicParams")));
    planPanel.appendChild(renderPublicParams());
    planPanel.appendChild(sectionLabel(t("dayGroupsSection")));
    planPanel.appendChild(renderSegmentEditor());
    planPanel.appendChild(renderSegmentCards());

    content.appendChild(managePanel);
    content.appendChild(planPanel);
    shell.appendChild(content);
    root.appendChild(shell);
    // The tab bar sticks under the page bar; measure the bar because long translations
    // and the safe-area inset change its height.
    const pageBar = shell.querySelector(".parent-bar");
    if (pageBar) root.style.setProperty("--parent-bar-h", pageBar.offsetHeight + "px");
    if (routeFlags(snap.rules || {}).study) ensureStudyEarnCatalog();
  }

  function renderInputSources() {
    const card = el("div", "parent-card");
    const canSources = Boolean(snap.capabilities && snap.capabilities.canToggleSources);
    card.appendChild(switchRow(
      t("tvInputSources"),
      t("tvInputSourcesHint"),
      snap.sourceEntriesVisible,
      !canSources,
      (input) => saveSimple("/api/parent/source-entries", {
        enabled: input.checked
      }, "sourceEntriesVisible", input.checked, {
        sensitiveScope: "RULES_EDIT",
        leadKey: "sensitivePinLeadRules",
      })
    ));
    return card;
  }

  function renderQuizChallenge() {
    const card = el("div", "parent-card");
    card.appendChild(switchRow(
      t("quizEntryVisible"),
      t("quizEntryHint"),
      snap.quizEntryVisible,
      !(snap.capabilities && snap.capabilities.canToggleQuizEntry),
      (input) => saveSimple("/api/parent/quiz-entry", {
        visible: input.checked
      }, "quizEntryVisible", input.checked, {
        sensitiveScope: "RULES_EDIT",
        leadKey: "sensitivePinLeadRules",
      })
    ));
    return card;
  }

  /** Turning auto-update off is a deliberate downgrade of the household's update path. */
  function saveAutoUpdateSwitch(input) {
    if (input.checked) {
      saveSimple("/api/parent/auto-update", { enabled: true }, "autoUpdate", true);
      return;
    }
    input.checked = true;
    confirmSheet(
      t("confirmAutoUpdateOffTitle"),
      t("confirmAutoUpdateOffBody"),
      t("confirmAutoUpdateOffAction"),
      () => saveSimple("/api/parent/auto-update", { enabled: false }, "autoUpdate", false),
      t("cancel")
    );
  }

  /**
   * V47: turning the user-configuration PIN prompts off is a deliberate reduction of
   * protection, so it always asks for a fresh single-use USER_CONFIG proof. Turning them back
   * on only restores protection; no PIN sheet. The server repeats both rules.
   */
  function saveUserConfigPinSwitch(input) {
    const enabled = Boolean(input && input.checked);
    releaseSensitive("USER_CONFIG");
    if (enabled) {
      saveSimple(
        "/api/parent/user-config-pin/enable",
        {},
        "userConfigRequirePin",
        true
      );
      return;
    }
    saveSimple(
      "/api/parent/user-config-pin/disable",
      {},
      "userConfigRequirePin",
      false,
      {
        sensitiveScope: "USER_CONFIG",
        leadKey: "sensitivePinLeadUserConfig"
      }
    ).finally(() => releaseSensitive("USER_CONFIG"));
  }

  function renderPersonalizeEntry() {
    const card = el("div", "parent-card");
    const media = snap.media || {};
    const roles = media.roles || {};
    const customized = Boolean(roles.quiz || roles.lock);
    // A full page keeps the cover library, the upload progress and both role choices
    // together; the main page only needs one stable entry point.
    card.appendChild(row(
      t("personalize"),
      t("personalizeHint"),
      customized ? t("mediaCustomized") : t("mediaSourceDefault"),
      openPersonalize
    ));
    return card;
  }

  function openPersonalize() {
    const context = editingContext();
    if (context.sibling || !context.member) {
      setNotice(t("userSiblingNote", {
        name: context.member ? context.member.name : t("userNoMember"),
      }), true);
      subpage = null;
      render();
      return;
    }
    subpage = "personalize";
    personalizeLoading = !snap.media;
    mediaSheetMode = null;
    mediaSheetId = null;
    mediaPickerRole = "quiz";
    mediaPickerChoice = "";
    mediaDownloadWatch = null;
    mediaKind = "video";
    mediaSource = "cloud";
    clearNotices();
    render();
    window.scrollTo({ top: 0 });
    refreshMediaPage(false);
  }

  async function refreshMediaPage(quiet) {
    if (!editorToken) {
      personalizeLoading = false;
      setNotice(t("editorLost"), true);
      render();
      return;
    }
    if (personalizeRefreshing) return;
    personalizeRefreshing = true;
    try {
      const data = await host.request("/api/parent/media", {
        editorToken: editorToken,
        quizAuth: true,
      });
      if (data && data.media) snap.media = data.media;
    } catch (error) {
      if (isLeaseError(error)) {
        personalizeRefreshing = false;
        stopMediaPoll();
        handleLeaseError(error);
        return;
      }
      if (!quiet) setNotice(errorText(error), true);
    }
    personalizeRefreshing = false;
    personalizeLoading = false;
    if (subpage === "personalize" && rootOpen) {
      render();
      paintMediaSheet();
      scheduleMediaPoll();
    }
  }

  function mediaActiveDownloads(media) {
    const downloads = (media && media.downloads) || {};
    return Object.keys(downloads).some((id) => {
      const state = downloads[id] && downloads[id].state;
      return state === "DOWNLOADING" || state === "VERIFYING";
    });
  }

  function audioActiveDownloads(media) {
    const downloads = (media && media.audio && media.audio.downloads) || {};
    return Object.keys(downloads).some((id) => {
      const state = downloads[id] && downloads[id].state;
      return state === "DOWNLOADING" || state === "VERIFYING";
    });
  }

  function mediaNeedsPoll(media) {
    return mediaActiveDownloads(media) || audioActiveDownloads(media) ||
      Boolean(media && media.catalog && media.catalog.refreshing) ||
      Boolean(media && media.audio && media.audio.catalog && media.audio.catalog.refreshing);
  }

  function stopMediaPoll() {
    if (mediaPollTimer) {
      clearTimeout(mediaPollTimer);
      mediaPollTimer = 0;
    }
  }

  function scheduleMediaPoll() {
    stopMediaPoll();
    if (!rootOpen || subpage !== "personalize" || !editorToken) return;
    if (!mediaNeedsPoll(snap && snap.media)) return;
    mediaPollTimer = setTimeout(() => {
      mediaPollTimer = 0;
      if (!rootOpen || subpage !== "personalize") return;
      if (document.hidden) {
        scheduleMediaPoll();
        return;
      }
      refreshMediaPage(true);
    }, 1000);
  }

  function renderPersonalize() {
    const root = $(ROOT_ID);
    if (!root) return;
    const scrollTop = root.scrollTop;
    clear(root);
    const shell = el("div", "parent-shell");
    const bar = barNode(t("mediaPageTitle"), mediaStatusText(), () => {
      subpage = null;
      render();
    });
    const barState = bar.querySelector(".parent-bar-state");
    if (barState) barState.classList.add("is-media");
    shell.appendChild(bar);
    const content = el("div", "parent-subpage");
    content.appendChild(noticeArea());

    const media = snap.media || {};
    const catalog = media.catalog || {};
    const items = Array.isArray(media.items) ? media.items : [];
    const limits = media.limits || {};
    const canEdit = mediaCanEdit();
    const kindItems = items.filter((item) => mediaKindOf(item) === mediaKind);
    const officialItems = kindItems.filter((item) => item.source === "official");
    const localItems = kindItems.filter((item) => item.source === "local");
    const readyOfficial = officialItems.filter(mediaIsPlayable).length;

    // The header refresh works from either source: it pulls a new cloud snapshot, which is
    // also what tells the local list that a download finished on the TV.
    const headerRefreshBusy = Boolean(personalizeRefreshing || catalog.refreshing);
    const headerRefresh = button(headerRefreshBusy ? "…" : "↻",
      "parent-media-refresh-icon parent-media-bar-refresh", () => refreshMediaLibrary());
    headerRefresh.setAttribute("aria-label",
      headerRefreshBusy ? t("mediaRefreshing") : t("mediaRefresh"));
    headerRefresh.title = headerRefreshBusy ? t("mediaRefreshing") : t("mediaRefresh");
    headerRefresh.disabled = headerRefreshBusy || !editorToken;
    bar.appendChild(headerRefresh);

    const modulesHead = el("div", "parent-media-head");
    modulesHead.appendChild(el("p", "parent-section-label", t("mediaCurrentTitle")));
    content.appendChild(modulesHead);
    const current = el("div", "parent-card parent-media-modules");
    current.appendChild(mediaModuleRow("quiz"));
    current.appendChild(mediaModuleRow("lock"));
    const lockHasAudio = mediaLockHasAudio();
    const soundAvailable = lockHasAudio || Boolean(mediaAudioSelected());
    current.appendChild(switchRow(
      t("mediaSoundTitle"),
      soundAvailable ? t("mediaSoundHint") : t("mediaSoundUnavailable"),
      Boolean(media.lockSound),
      !canEdit || !soundAvailable,
      (input) => {
        mediaWrite("/api/parent/media/sound", { enabled: input.checked }, "mediaSoundSaved");
      }
    ));
    current.appendChild(mediaAudioRow());
    content.appendChild(current);
    const timing = el("p", "parent-media-timing-note");
    timing.appendChild(el("span", "parent-media-note-icon", "◷"));
    const timingText = el("span", "parent-media-timing-text");
    timingText.appendChild(el("strong", "", t("mediaTimingLead")));
    timingText.appendChild(document.createTextNode(" " + t("mediaSingleTimingNote")));
    timing.appendChild(timingText);
    timing.appendChild(el("span", "parent-media-readonly", t("mediaReadonly")));
    content.appendChild(timing);

    // Level one: what the wallpaper is. Level two, below, only filters where it comes from.
    const kindTabs = el("div", "parent-media-tabs");
    kindTabs.id = "mediaKindTabs";
    kindTabs.setAttribute("role", "tablist");
    kindTabs.setAttribute("aria-label", t("mediaKindTabsLabel"));
    kindTabs.addEventListener("keydown", (event) => {
      if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      event.preventDefault();
      selectMediaKind(mediaKind === "video" ? "image" : "video");
    });
    kindTabs.appendChild(mediaKindTabButton("video", mediaKindCount(items, "video")));
    kindTabs.appendChild(mediaKindTabButton("image", mediaKindCount(items, "image")));
    content.appendChild(kindTabs);

    const library = el("div", "parent-media-panel");
    const sourceRow = el("div", "parent-media-source-row");
    const sourceTabs = el("div", "parent-media-source-tabs");
    sourceTabs.setAttribute("role", "tablist");
    sourceTabs.setAttribute("aria-label", t("mediaSourceTabsLabel"));
    sourceTabs.addEventListener("keydown", (event) => {
      if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      event.preventDefault();
      selectMediaSource(mediaSource === "cloud" ? "local" : "cloud");
    });
    sourceTabs.appendChild(sourceTabButton(
      "cloud",
      t("mediaSourceOfficial"),
      officialItems.length ? readyOfficial + "/" + officialItems.length : "0"
    ));
    sourceTabs.appendChild(sourceTabButton(
      "local",
      t("mediaSourceLocal"),
      localItems.length
    ));
    sourceRow.appendChild(sourceTabs);
    if (mediaSource === "cloud") {
      const refreshBusy = Boolean(personalizeRefreshing || catalog.refreshing);
      const refresh = button(refreshBusy ? "…" : "↻", "parent-media-refresh-icon",
        () => refreshMediaLibrary());
      refresh.setAttribute("aria-label", refreshBusy ? t("mediaRefreshing") : t("mediaRefresh"));
      refresh.title = refreshBusy ? t("mediaRefreshing") : t("mediaRefresh");
      refresh.disabled = refreshBusy || !editorToken;
      sourceRow.appendChild(refresh);
    }
    library.appendChild(sourceRow);

    if (personalizeLoading) {
      library.appendChild(el("div", "parent-empty", t("mediaLoading")));
    } else if (mediaSource === "cloud") {
      if (catalog.state === "ERROR" && officialItems.length) {
        library.appendChild(el("div", "parent-media-notice", t("mediaCatalogUnavailable")));
      }
      if (!officialItems.length) {
        library.appendChild(el("div", "parent-empty",
          catalog.configured === false ? t("mediaCatalogOff") : t("mediaEmpty")));
      } else {
        const grid = el("div", "parent-media-grid");
        officialItems.forEach((item) => grid.appendChild(mediaCard(item)));
        library.appendChild(grid);
      }
    } else {
      const uploadZone = el("div", "parent-media-upload-zone");
      const uploadWrap = el("div", "parent-upload parent-media-upload");
      const input = el("input");
      input.type = "file";
      input.accept = "image/jpeg,image/png,image/webp,video/mp4,video/quicktime," +
        ".jpg,.jpeg,.png,.webp,.mp4,.mov";
      input.multiple = true;
      input.className = "parent-upload-file";
      input.setAttribute("aria-label", t("introVideoUpload"));
      const upload = button(
        uploadState ? t("introVideoUploading") : "＋ " + t("introVideoUpload"),
        "parent-btn parent-media-upload-btn",
        () => input.click()
      );
      upload.disabled = !canEdit || Boolean(uploadState);
      input.addEventListener("change", () => {
        const files = input.files ? Array.prototype.slice.call(input.files) : [];
        input.value = "";
        if (files.length) uploadMediaFiles(files);
      });
      uploadWrap.appendChild(upload);
      uploadWrap.appendChild(input);
      uploadZone.appendChild(uploadWrap);
      library.appendChild(uploadZone);
      if (uploadState) {
        const progress = el("div", "parent-upload-progress parent-media-upload-progress");
        const track = el("div", "parent-progress-track");
        track.setAttribute("role", "progressbar");
        track.setAttribute("aria-valuemin", "0");
        track.setAttribute("aria-valuemax", "100");
        track.id = "parentUploadProgressBar";
        const fill = el("div", "parent-progress-fill");
        fill.id = "parentUploadProgressFill";
        track.appendChild(fill);
        const label = el("div", "parent-progress-text");
        label.id = "parentUploadProgressText";
        progress.appendChild(track);
        progress.appendChild(label);
        library.appendChild(progress);
      }
      if (!localItems.length) {
        library.appendChild(el("div", "parent-empty", t("mediaLocalEmpty")));
      } else {
        const localGrid = el("div", "parent-media-grid");
        localItems.forEach((item) => localGrid.appendChild(mediaCard(item)));
        library.appendChild(localGrid);
      }
    }
    content.appendChild(library);

    shell.appendChild(content);
    root.appendChild(shell);
    const pageBar = shell.querySelector(".parent-bar");
    if (pageBar) root.style.setProperty("--parent-bar-h", pageBar.offsetHeight + "px");
    if (scrollTop) root.scrollTop = scrollTop;
    if (uploadState) updateUploadProgressDom();
  }

  /** Global library counts in the page bar; the per-kind counts sit on the type tabs. */
  function mediaStatusText() {
    const media = snap && snap.media;
    if (!media) return statusLine(gate);
    const items = Array.isArray(media.items) ? media.items : [];
    const official = items.filter((item) => item.source === "official");
    const ready = official.filter(mediaIsPlayable).length;
    const local = items.filter((item) => item.source === "local").length;
    return statusLine(gate) + " · " + t("mediaSourceOfficial") + " " + ready + "/" +
      official.length + " · " + t("mediaSourceLocal") + " " + local;
  }

  /** One segment of the video/image switch: the first-level choice of what to use. */
  function mediaKindTabButton(kind, count) {
    const node = button("", "parent-media-tab" + (mediaKind === kind ? " is-active" : ""),
      () => selectMediaKind(kind));
    node.id = kind === "video" ? "mediaVideoKindTab" : "mediaImageKindTab";
    node.dataset.kind = kind;
    node.setAttribute("role", "tab");
    node.setAttribute("aria-selected", mediaKind === kind ? "true" : "false");
    node.tabIndex = mediaKind === kind ? 0 : -1;
    node.appendChild(el("span", "parent-media-tab-label", mediaKindText(kind)));
    node.appendChild(el("span", "parent-media-tab-count", String(count)));
    return node;
  }

  /** One segment of the second-level cloud/local filter inside the chosen kind. */
  function sourceTabButton(source, label, count) {
    const node = button("", "parent-media-source-tab" + (mediaSource === source ? " is-active" : ""),
      () => selectMediaSource(source));
    node.id = source === "cloud" ? "mediaCloudSourceTab" : "mediaLocalSourceTab";
    node.dataset.source = source;
    node.setAttribute("role", "tab");
    node.setAttribute("aria-selected", mediaSource === source ? "true" : "false");
    node.tabIndex = mediaSource === source ? 0 : -1;
    node.appendChild(el("span", "parent-media-source-label", label));
    node.appendChild(el("span", "parent-media-source-count", count));
    return node;
  }

  function selectMediaKind(kind) {
    if ((kind !== "video" && kind !== "image") || kind === mediaKind) return;
    mediaKind = kind;
    render();
    const tab = $(kind === "video" ? "mediaVideoKindTab" : "mediaImageKindTab");
    if (tab) tab.focus({ preventScroll: true });
  }

  function selectMediaSource(source) {
    if ((source !== "cloud" && source !== "local") || source === mediaSource) return;
    mediaSource = source;
    render();
    const tab = $(source === "cloud" ? "mediaCloudSourceTab" : "mediaLocalSourceTab");
    if (tab) tab.focus({ preventScroll: true });
  }

  function mediaCanEdit() {
    return !snap.capabilities || snap.capabilities.canEditIntroVideos !== false;
  }

  function mediaItem(id) {
    const items = (snap && snap.media && snap.media.items) || [];
    return items.find((item) => item.id === id) || null;
  }

  /** An old snapshot has no kind; every pre-V34 item was a video. */
  function mediaKindOf(item) {
    return item && item.kind === "image" ? "image" : "video";
  }

  /** A video the TV can decode an audio track from; the wallpaper-sound switch requires it. */
  function mediaHasAudio(item) {
    return Boolean(item) && mediaKindOf(item) === "video" && item.hasAudio === true;
  }

  /** A long local/cloud video that is too big or long for the 15-second quiz intro. */
  function mediaIsWallpaperOnly(item) {
    return Boolean(item) && mediaKindOf(item) === "video" && !mediaCanUseInRole(item, "quiz");
  }

  /**
   * Capability marks for a poster. They explain why a video cannot be used as the quiz intro
   * and whether the child-mode sound switch can be turned on for it.
   */
  function mediaCapabilityBadges(item) {
    const labels = [];
    if (mediaHasAudio(item)) labels.push(t("mediaHasAudio"));
    if (mediaIsWallpaperOnly(item)) labels.push(t("mediaOnlyWallpaper"));
    if (!labels.length) return null;
    const wrap = el("div", "parent-media-cap-badges");
    labels.forEach((label) => wrap.appendChild(el("span", "parent-media-cap-badge", label)));
    return wrap;
  }

  function mediaKindText(kind) {
    return kind === "image" ? t("mediaKindImage") : t("mediaKindVideo");
  }

  function mediaKindCount(items, kind) {
    return items.filter((item) => mediaKindOf(item) === kind).length;
  }

  function mediaKindLabel(item) {
    return mediaKindText(mediaKindOf(item));
  }

  function mediaName(item) {
    if (!item) return "";
    if (lang === "zh") return item.name || item.nameEn || item.id;
    return item.nameEn || item.name || item.id;
  }

  function mediaSourceText(item) {
    return item && item.source === "local" ? t("mediaSourceLocal") : t("mediaSourceOfficial");
  }

  function mediaDuration(ms) {
    const seconds = Math.max(0, Math.round((Number(ms) || 0) / 1000));
    return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
  }

  function mediaMeta(item) {
    const values = {
      size: ((Number(item.sizeBytes) || 0) / (1024 * 1024)).toFixed(1),
      width: Number(item.width) || "—",
      height: Number(item.height) || "—"
    };
    if (mediaKindOf(item) === "image") return t("mediaMetaImage", values);
    values.duration = mediaDuration(item.durationMs);
    return t("mediaMeta", values);
  }

  /** The picker stage names the type, source and the one number that matters for that type. */
  function mediaStageMeta(item) {
    if (!item) return t("mediaKindImage") + " · " + t("mediaSourceDefault");
    const detail = mediaKindOf(item) === "image"
      ? (Number(item.width) || "—") + "×" + (Number(item.height) || "—")
      : mediaDuration(item.durationMs);
    return mediaKindLabel(item) + " · " + mediaSourceText(item) + " · " + detail;
  }

  /** The single corner mark on a poster: downloaded state plus its screen-reader label. */
  function mediaCardMarker(item) {
    const state = mediaState(item);
    if (item.source === "local") {
      return {
        glyph: "✓",
        tone: "is-ready",
        label: t("mediaStateLocalReady") + " · " + t("mediaManage")
      };
    }
    if (state === "DOWNLOADING") {
      const percent = Number(item.download && item.download.percent) || 0;
      return { glyph: percent + "%", tone: "is-loading", label: mediaStateText(item) };
    }
    if (state === "VERIFYING") {
      return { glyph: "…", tone: "is-loading", label: mediaStateText(item) };
    }
    if (state === "FAILED") {
      return {
        glyph: "↻",
        tone: "is-warning",
        label: mediaStateText(item) + " · " + t("mediaRetryDownload")
      };
    }
    if (state === "UPDATE_AVAILABLE") {
      return {
        glyph: "↻",
        tone: "is-update",
        label: mediaStateText(item) + " · " + t("mediaUpdateLocal")
      };
    }
    if (state === "READY" || mediaIsPlayable(item)) {
      return {
        glyph: "✓",
        tone: "is-ready",
        label: t("mediaStateReady") + " · " + t("mediaManage")
      };
    }
    return { glyph: "↓", tone: "is-action", label: t("mediaDownload") };
  }

  function mediaState(item) {
    return (item && ((item.download && item.download.state) || item.state)) || "";
  }

  /** A playable copy exists on the TV: local uploads, or a downloaded cloud film. */
  function mediaIsPlayable(item) {
    return Boolean(item) && (item.source === "local" || Number(item.downloadedAt) > 0);
  }

  function mediaIsBusy(item) {
    const state = mediaState(item);
    return state === "DOWNLOADING" || state === "VERIFYING";
  }

  function mediaStateText(item) {
    const state = mediaState(item);
    if (state === "DOWNLOADING") {
      return t("mediaStateDownloading", { percent: Number(item.download && item.download.percent) || 0 });
    }
    if (state === "VERIFYING") return t("mediaStateVerifying");
    if (state === "FAILED") return t("mediaStateFailed");
    if (state === "UPDATE_AVAILABLE") return t("mediaStateUpdate");
    if (state === "READY") return item.source === "local" ? t("mediaStateLocalReady") : t("mediaStateReady");
    return t("mediaStateNotDownloaded");
  }

  function mediaStateTone(item) {
    const state = mediaState(item);
    if (item.source === "local" || state === "READY") return "is-ready";
    if (state === "DOWNLOADING" || state === "VERIFYING") return "is-loading";
    if (state === "FAILED" || state === "UPDATE_AVAILABLE") return "is-warning";
    return "";
  }

  function mediaModuleTitle(role) {
    return role === "lock" ? t("mediaRoleLock") : t("mediaRoleQuiz");
  }

  function mediaRolesState() {
    return (snap && snap.media && snap.media.roles) || {};
  }

  function mediaLockPlaylist() {
    return (snap && snap.media && snap.media.lockPlaylist) || {};
  }

  function mediaLockLimits() {
    return (snap && snap.media && snap.media.limits) || {};
  }

  function mediaLockMaxItems() {
    return Math.max(1, Number(mediaLockLimits().maxWallpaperItems) || 20);
  }

  function mediaLockMinInterval() {
    return Number(mediaLockLimits().minWallpaperIntervalMs) || 15000;
  }

  function mediaLockMaxInterval() {
    return Number(mediaLockLimits().maxWallpaperIntervalMs) || 300000;
  }

  function mediaLockStoredInterval() {
    const fallback = Number(mediaLockLimits().defaultWallpaperIntervalMs) || 30000;
    const value = Number(mediaLockPlaylist().intervalMs) || fallback;
    return Math.min(mediaLockMaxInterval(), Math.max(mediaLockMinInterval(), value));
  }

  /** Compact interval labels; the carousel only exposes a few familiar choices. */
  function mediaIntervalText(ms) {
    const seconds = Math.max(1, Math.round((Number(ms) || 0) / 1000));
    if (seconds >= 60 && seconds % 60 === 0) {
      return t("mediaIntervalMinutes", { n: seconds / 60 });
    }
    return t("mediaIntervalSeconds", { n: seconds });
  }

  function mediaIntervalOptions() {
    const values = [15000, 30000, 60000, 120000, 300000,
      mediaLockInterval, mediaLockMinInterval(), mediaLockMaxInterval()];
    return values.filter((value, index) =>
      value >= mediaLockMinInterval() && value <= mediaLockMaxInterval() &&
      values.indexOf(value) === index
    ).sort((left, right) => left - right);
  }

  /**
   * The lock rotation in display order. Older snapshots only carry the single legacy role;
   * treating it as a one-item rotation keeps those TVs fully editable.
   */
  function mediaLockIds() {
    const ids = mediaLockPlaylist().ids;
    const list = Array.isArray(ids) ? ids.filter((id) => typeof id === "string" && id) : [];
    if (list.length) return list;
    const legacy = mediaRolesState().lock;
    return legacy ? [legacy] : [];
  }

  /** Items in the rotation that can actually be drawn; missing files are skipped like on TV. */
  function mediaLockItems() {
    return mediaLockIds().map(mediaItem).filter((item) => item && mediaIsPlayable(item));
  }

  /** Every selectable item in the rotation, not just the first one, decides sound availability. */
  function mediaLockHasAudio() {
    return mediaLockItems().some(mediaHasAudio);
  }

  function mediaAudio() {
    return (snap && snap.media && snap.media.audio) || {};
  }

  function mediaAudioItems() {
    const items = mediaAudio().items;
    return Array.isArray(items) ? items : [];
  }

  function mediaAudioCloudItems() {
    const items = mediaAudio().cloudItems;
    return Array.isArray(items) ? items : [];
  }

  function mediaAudioCloudItem(id) {
    return mediaAudioCloudItems().find((item) => item.id === id) || null;
  }

  /** A selection can point at an upload or at a verified cloud download. */
  function mediaAudioSelected() {
    const id = mediaAudio().selectedId || "";
    if (!id) return null;
    return mediaAudioItems().find((item) => item.id === id) ||
      mediaAudioCloudItems().find((item) => item.id === id) || null;
  }

  function audioDurationText(ms) {
    const seconds = Math.max(0, Math.round((Number(ms) || 0) / 1000));
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return minutes + ":" + String(rest).padStart(2, "0");
  }

  function audioMetaText(item) {
    const bytes = Number(item && item.sizeBytes) || 0;
    const megabytes = bytes / (1024 * 1024);
    return t("audioMeta", {
      size: bytes ? (megabytes < 10 ? megabytes.toFixed(1) : Math.round(megabytes)) : 0,
      duration: audioDurationText(item && item.durationMs)
    });
  }

  function audioCloudState(item) {
    return (item && ((item.download && item.download.state) || item.state)) || "NOT_DOWNLOADED";
  }

  function audioCloudBusy(item) {
    const state = audioCloudState(item);
    return state === "DOWNLOADING" || state === "VERIFYING";
  }

  function audioCloudSub(item) {
    const parts = [];
    if (item && item.artist) parts.push(item.artist);
    parts.push(audioMetaText(item));
    if (item && item.license) parts.push(item.license);
    return parts.join(" · ");
  }

  function audioCloudAction(item, canEdit) {
    if (!canEdit) return "";
    const state = audioCloudState(item);
    if (state === "DOWNLOADING") {
      return t("audioStateDownloading", {
        percent: Number(item.download && item.download.percent) || 0
      });
    }
    if (state === "VERIFYING") return t("audioStateVerifying");
    if (state === "FAILED") return t("audioRetry");
    if (state === "UPDATE_AVAILABLE") return t("audioUpdate");
    if (state === "READY") return t("audioUse");
    return t("audioDownload");
  }

  function mediaLockStoredSelectableIds() {
    return mediaLockIds().filter((id) => {
      const item = mediaItem(id);
      return Boolean(item) && mediaIsPlayable(item) && mediaCanUseInRole(item, "lock");
    });
  }

  /** Roles that currently point at this exact item, so a change never clears another film. */
  function mediaItemRoles(item) {
    const stored = mediaRolesState();
    const lockIds = mediaLockIds();
    return ["quiz", "lock"].filter((role) =>
      stored[role] === item.id || (role === "lock" && lockIds.indexOf(item.id) >= 0)
    );
  }

  function mediaDefaultThumb(className) {
    const holder = el("div", className || "parent-media-default-thumb");
    holder.classList.add("parent-media-default-thumb");
    const img = el("img", "parent-media-default-img");
    img.src = "parent-kids-default.jpg";
    img.alt = "";
    img.decoding = "async";
    holder.appendChild(img);
    holder.appendChild(el("span", "parent-media-default-mark", t("mediaDefaultBadge")));
    return holder;
  }

  function mediaModuleRow(role) {
    const lockItems = role === "lock" ? mediaLockItems() : [];
    const item = lockItems.length ? lockItems[0] : mediaItem(mediaRolesState()[role]);
    const lockCount = role === "lock" ? lockItems.length : 0;
    const node = el("button", "parent-row parent-media-module-row");
    node.type = "button";
    node.appendChild(item
      ? mediaCover(item, "parent-media-module-thumb")
      : mediaDefaultThumb("parent-media-module-thumb"));
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", mediaModuleTitle(role)));
    main.appendChild(el("span", "parent-row-sub",
      lockCount > 1
        ? t("mediaLockSummary", {
          count: lockCount,
          duration: mediaIntervalText(mediaLockStoredInterval())
        })
        : item
          ? mediaKindLabel(item) + " · " + mediaSourceText(item) + " · " + mediaName(item)
          : t("mediaKindImage") + " · " + t("mediaSourceDefault")));
    node.appendChild(main);
    const action = el("span", "parent-media-module-action");
    action.appendChild(el("span", "parent-media-module-action-label",
      t(lockCount > 1 ? "mediaManage" : "mediaChange")));
    action.appendChild(el("span", "chevron", "›"));
    node.appendChild(action);
    node.addEventListener("click", () => openMediaPicker(role));
    return node;
  }

  /**
 * The background-music entry sits with the wallpaper choices: the track is chosen once and
 * then plays under every item in the rotation instead of being tied to one video.
 */
  function mediaAudioRow() {
    const selected = mediaAudioSelected();
    const node = el("button", "parent-row parent-media-module-row");
    node.type = "button";
    const thumb = el("div", "parent-media-audio-thumb");
    thumb.appendChild(el("span", "parent-media-audio-note", "♪"));
    node.appendChild(thumb);
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", t("audioTitle")));
    main.appendChild(el("span", "parent-row-sub", selected
      ? selected.name + " · " + audioMetaText(selected)
      : t("audioNone")));
    node.appendChild(main);
    const action = el("span", "parent-media-module-action");
    action.appendChild(el("span", "parent-media-module-action-label",
      t(selected ? "mediaChange" : "mediaChoose")));
    action.appendChild(el("span", "chevron", "›"));
    node.appendChild(action);
    node.addEventListener("click", openAudioPicker);
    return node;
  }

  function openAudioPicker() {
    mediaSheetMode = "audio";
    mediaSheetId = null;
    mediaDownloadWatch = null;
    mediaPickerScrollPending = false;
    audioDownloadWatch = null;
    audioDownloadWatchFrom = "";
    const catalog = mediaAudio().catalog || {};
    audioSource = catalog.configured === false ? "local" : "cloud";
    openSheet((sheet) => renderMediaSheetContent(sheet, closeMediaSheet));
    // Opening the picker is the point where a stale cloud catalogue becomes visible; ask for
    // a refresh only when there is nothing valid cached, not on every open.
    if (catalog.configured !== false && catalog.state !== "READY" && !catalog.refreshing) {
      mediaWrite("/api/parent/media/audio/refresh", {}, null, true);
    }
  }

  function mediaCover(item, className) {
    const holder = el("div", className || "parent-media-cover");
    const url = item.posterUrl;
    if (!url) {
      holder.classList.add("is-empty");
      return holder;
    }
    if (url.charAt(0) === "/") {
      loadLocalMediaPoster(item, holder);
      return holder;
    }
    const img = el("img", "parent-media-img");
    img.alt = mediaName(item);
    img.loading = "lazy";
    img.decoding = "async";
    img.addEventListener("load", () => holder.classList.add("has-image"));
    img.addEventListener("error", () => img.remove());
    img.src = url;
    holder.appendChild(img);
    return holder;
  }

  function loadLocalMediaPoster(item, holder) {
    const id = item.id;
    const epoch = mediaPosterEpoch;
    const cached = mediaPosterUrls.get(id);
    if (cached) {
      paintLocalMediaPoster(holder, cached);
      return;
    }
    let waiters = mediaPosterWaiters.get(id);
    if (!waiters) {
      waiters = new Set();
      mediaPosterWaiters.set(id, waiters);
    }
    waiters.add(holder);
    if (mediaPosterLoading.has(id)) return;
    mediaPosterLoading.add(id);
    host.blob(item.posterUrl, editorToken)
      .then((blob) => {
        if (epoch !== mediaPosterEpoch) return;
        const url = URL.createObjectURL(blob);
        mediaPosterUrls.set(id, url);
        const waiting = mediaPosterWaiters.get(id);
        if (waiting) {
          waiting.forEach((node) => {
            if (node.isConnected) paintLocalMediaPoster(node, url);
          });
          mediaPosterWaiters.delete(id);
        }
      })
      .catch(() => { /* the fallback stays behind the title */ })
      .finally(() => {
        if (epoch === mediaPosterEpoch) mediaPosterLoading.delete(id);
      });
  }

  function paintLocalMediaPoster(holder, url) {
    if (holder.querySelector(".parent-media-img")) return;
    holder.classList.add("has-image");
    const img = el("img", "parent-media-img");
    img.alt = "";
    img.decoding = "async";
    img.src = url;
    // Keep the title/shade/marker that the card adds after mediaCover() returns.
    holder.insertBefore(img, holder.firstChild);
  }

  function forgetMediaPoster(id) {
    const url = mediaPosterUrls.get(id);
    if (url) URL.revokeObjectURL(url);
    mediaPosterUrls.delete(id);
    mediaPosterWaiters.delete(id);
    mediaPosterLoading.delete(id);
  }

  function revokeMediaPosters() {
    mediaPosterEpoch += 1;
    mediaPosterUrls.forEach((url) => URL.revokeObjectURL(url));
    mediaPosterUrls.clear();
    mediaPosterWaiters.clear();
    mediaPosterLoading.clear();
  }

  function mediaCard(item) {
    const card = el("button", "parent-media-card");
    card.type = "button";
    const cover = mediaCover(item, "parent-media-cover");
    cover.appendChild(el("div", "parent-media-card-shade"));
    cover.appendChild(el("span", "parent-media-kind-badge", mediaKindLabel(item)));
    const capabilities = mediaCapabilityBadges(item);
    if (capabilities) cover.appendChild(capabilities);
    cover.appendChild(el("span", "parent-media-card-title", mediaName(item)));
    const marker = mediaCardMarker(item);
    const mark = el("span", "parent-media-card-marker " + marker.tone, marker.glyph);
    mark.setAttribute("aria-hidden", "true");
    cover.appendChild(mark);
    if (mediaIsBusy(item)) {
      const track = el("div", "parent-media-card-track");
      const fill = el("div", "parent-progress-fill");
      fill.style.width = (Number(item.download && item.download.percent) || 0) + "%";
      track.appendChild(fill);
      cover.appendChild(track);
    }
    card.appendChild(cover);
    const label = mediaName(item) + " · " + mediaKindLabel(item) + " · " + marker.label;
    card.title = label;
    card.setAttribute("aria-label", label);
    card.addEventListener("click", () => handleMediaCard(item));
    return card;
  }

  /** Downloading is the only action on an available poster; ready items open management. */
  function handleMediaCard(item) {
    if (!mediaCanEdit() || mediaIsPlayable(item) || mediaIsBusy(item)) {
      openMediaPreview(item.id);
      return;
    }
    mediaDownloadWatch = item.id;
    mediaWrite("/api/parent/media/download", { id: item.id }, "mediaDownloadStarted");
  }

  function openMediaPreview(id) {
    if (!mediaItem(id)) return;
    mediaSheetMode = "preview";
    mediaSheetId = id;
    // Opening the sheet for a download already in flight should keep its completion watcher.
    if (mediaDownloadWatch !== id) mediaDownloadWatch = null;
    mediaPickerScrollPending = false;
    openSheet((sheet) => renderMediaSheetContent(sheet, closeMediaSheet));
  }

  function openMediaPicker(role) {
    mediaSheetMode = "picker";
    mediaSheetId = null;
    mediaDownloadWatch = null;
    mediaPickerRole = role === "lock" ? "lock" : "quiz";
    if (mediaPickerRole === "lock") {
      mediaPickerChoice = "";
      mediaLockChoices = mediaLockStoredSelectableIds().slice(0, mediaLockMaxItems());
      mediaLockInterval = mediaLockStoredInterval();
    } else {
      mediaLockChoices = [];
      const stored = mediaRolesState()[mediaPickerRole] || "";
      const current = mediaItem(stored);
      mediaPickerChoice = current && mediaCanUseInRole(current, mediaPickerRole) &&
        mediaIsPlayable(current) ? stored : "";
    }
    mediaPickerScrollPending = true;
    openSheet((sheet) => renderMediaSheetContent(sheet, closeMediaSheet));
  }

  function paintMediaSheet() {
    const root = $(SHEET_ID);
    const sheet = root ? root.querySelector(".parent-sheet") : null;
    if (resolveMediaDownloadWatch(Boolean(sheet))) return;
    if (resolveAudioDownloadWatch()) return;
    if (!sheet) {
      mediaSheetMode = null;
      mediaSheetId = null;
      mediaPickerScrollPending = false;
      audioDownloadWatch = null;
      audioDownloadWatchFrom = "";
      return;
    }
    renderMediaSheetContent(sheet, closeMediaSheet);
  }

  /**
   * A download can be started from a poster without opening a sheet, so completion is resolved
   * from the polling snapshot. Only close the sheet when it is showing that same download.
   */
  function resolveMediaDownloadWatch(sheetOpen) {
    if (!mediaDownloadWatch) return false;
    const item = mediaItem(mediaDownloadWatch);
    if (!item) {
      mediaDownloadWatch = null;
      return false;
    }
    const state = mediaState(item);
    if (state === "FAILED") {
      const watchedSheet = sheetOpen && mediaSheetMode === "preview" && mediaSheetId === item.id;
      mediaDownloadWatch = null;
      if (!watchedSheet) setNotice(t("mediaDownloadFailed"), true);
      return false;
    }
    if (state !== "READY") return false;
    const watchedSheet = sheetOpen && mediaSheetMode === "preview" && mediaSheetId === item.id;
    mediaDownloadWatch = null;
    if (watchedSheet) {
      mediaSheetMode = null;
      mediaSheetId = null;
      closeSheet();
    }
    setNotice(t("mediaDownloadDone"), false);
    return watchedSheet;
  }

  function closeMediaSheet() {
    mediaSheetMode = null;
    mediaSheetId = null;
    mediaPickerChoice = "";
    mediaLockChoices = [];
    mediaLockInterval = 0;
    mediaDownloadWatch = null;
    audioDownloadWatch = null;
    audioDownloadWatchFrom = "";
    mediaPickerScrollPending = false;
    closeSheet();
  }

  function renderMediaSheetContent(sheet, close) {
    const scrollNode = sheet.querySelector(".parent-media-sheet-scroll");
    const stripNode = sheet.querySelector(".parent-media-strip");
    const sheetScrollTop = scrollNode ? scrollNode.scrollTop : 0;
    const stripScrollLeft = stripNode ? stripNode.scrollLeft : 0;
    clear(sheet);
    sheet.classList.add("parent-media-sheet");
    const closeButton = button("×", "parent-media-close", close);
    closeButton.setAttribute("aria-label", t("mediaClose"));
    sheet.appendChild(closeButton);
    if (mediaSheetMode === "picker") {
      renderMediaPickerSheet(sheet, close);
    } else if (mediaSheetMode === "audio") {
      renderAudioSheet(sheet, close);
    } else {
      renderMediaPreviewSheet(sheet, close);
    }
    const nextScroll = sheet.querySelector(".parent-media-sheet-scroll");
    if (nextScroll && sheetScrollTop) nextScroll.scrollTop = sheetScrollTop;
    const nextStrip = sheet.querySelector(".parent-media-strip");
    if (nextStrip && stripScrollLeft) nextStrip.scrollLeft = stripScrollLeft;
  }

  function mediaCanUseInRole(item, role) {
    const capable = Array.isArray(item.capabilityRoles) ? item.capabilityRoles : [];
    return !capable.length || capable.indexOf(role) >= 0;
  }

  function mediaPreviewFooter(item, state, canEdit) {
    if (state === "DOWNLOADING" || state === "VERIFYING") {
      const footer = el("div", "parent-media-sheet-footer");
      const percent = Number(item.download && item.download.percent) || 0;
      const track = el("div", "parent-progress-track");
      const fill = el("div", "parent-progress-fill");
      fill.style.width = percent + "%";
      track.appendChild(fill);
      footer.appendChild(track);
      footer.appendChild(el("div", "parent-progress-text", mediaStateText(item)));
      const cancel = button(t("mediaCancelDownload"), "parent-btn ghost", () => {
        mediaDownloadWatch = null;
        mediaWrite("/api/parent/media/cancel", { id: item.id }, "mediaCancelled");
      });
      cancel.disabled = !canEdit;
      footer.appendChild(cancel);
      return footer;
    }
    let key = "";
    if (state === "FAILED") key = "mediaRetryDownload";
    else if (state === "UPDATE_AVAILABLE") key = "mediaUpdateLocal";
    else if (!mediaIsPlayable(item)) key = "mediaDownload";
    if (!key) return null;
    const footer = el("div", "parent-media-sheet-footer");
    const primary = button(t(key), "parent-btn primary", () => {
      mediaDownloadWatch = item.id;
      mediaWrite("/api/parent/media/download", { id: item.id }, "mediaDownloadStarted");
    });
    primary.disabled = !canEdit;
    footer.appendChild(primary);
    return footer;
  }

  function renderMediaPreviewSheet(sheet, close) {
    const item = mediaItem(mediaSheetId);
    if (!item) {
      close();
      return;
    }
    const canEdit = mediaCanEdit();
    const state = mediaState(item);
    const scroll = el("div", "parent-media-sheet-scroll");
    const cover = mediaCover(item, "parent-media-sheet-cover");
    const caption = el("div", "parent-media-cover-caption");
    caption.appendChild(el("span", null, mediaKindLabel(item)));
    caption.appendChild(el("span", null, mediaSourceText(item)));
    cover.appendChild(caption);
    scroll.appendChild(cover);
    scroll.appendChild(el("h2", null, mediaName(item)));
    scroll.appendChild(el("p", "parent-media-sheet-meta", mediaMeta(item)));

    const badges = el("div", "parent-media-badges");
    badges.appendChild(el("span", "parent-media-badge-inline", mediaSourceText(item)));
    if (mediaHasAudio(item)) {
      badges.appendChild(el("span", "parent-media-badge-inline is-active", t("mediaHasAudio")));
    }
    if (mediaIsWallpaperOnly(item)) {
      badges.appendChild(el("span", "parent-media-badge-inline", t("mediaOnlyWallpaper")));
    }
    if (item.source !== "local" && !mediaIsBusy(item)) {
      badges.appendChild(el("span", "parent-media-badge-inline " + mediaStateTone(item),
        mediaStateText(item)));
    }
    mediaItemRoles(item).forEach((role) => {
      badges.appendChild(el("span", "parent-media-badge-inline is-active", mediaModuleTitle(role)));
    });
    scroll.appendChild(badges);
    if (mediaKindOf(item) === "image") {
      scroll.appendChild(el("p", "parent-hint", t("mediaImageRoleNote")));
    }
    if (state === "FAILED") {
      const code = item.download && item.download.error;
      scroll.appendChild(el("p", "parent-media-error",
        code ? errorText({ code: code }) : t("mediaDownloadFailed")));
    }
    if (!canEdit) {
      scroll.appendChild(el("p", "parent-hint", t("readOnlyCloud")));
    }
    if (mediaIsPlayable(item) && !mediaIsBusy(item)) {
      const deleteLabel = item.source === "local" ? t("mediaDeleteUpload") : t("mediaDeleteLocal");
      const remove = button(deleteLabel, "parent-media-delete-action", () => {
        const id = item.id;
        close();
        confirmSheet(t("mediaDeleteTitle"), t("mediaDeleteText"), deleteLabel, () => {
          forgetMediaPoster(id);
          mediaWrite("/api/parent/media/delete", { id: id }, "mediaDeleted");
        }, t("cancel"));
      });
      remove.disabled = !canEdit;
      if (canEdit) scroll.appendChild(remove);
    }
    sheet.appendChild(scroll);
    const footer = mediaPreviewFooter(item, state, canEdit);
    if (footer) sheet.appendChild(footer);
  }

  /** The background-music sheet: curated cloud tracks plus unchanged local uploads. */
  function renderAudioSheet(sheet, close) {
    const canEdit = mediaCanEdit();
    const audio = mediaAudio();
    const catalog = audio.catalog || {};
    const cloudItems = mediaAudioCloudItems();
    const localItems = mediaAudioItems();
    const cloudReady = cloudItems.filter((item) => audioCloudState(item) === "READY").length;
    const scroll = el("div", "parent-media-sheet-scroll");
    scroll.appendChild(el("h2", null, t("audioTitle")));
    scroll.appendChild(el("p", "parent-hint", t("audioHint")));

    // Silence is a choice independent of where a track comes from, so it stays on top.
    const noneList = el("div", "parent-media-audio-list parent-media-audio-none");
    noneList.appendChild(audioOption({ id: "", name: t("audioNone"), hint: t("audioNoneHint") }, canEdit));
    scroll.appendChild(noneList);

    const sourceRow = el("div", "parent-media-source-row");
    const sourceTabs = el("div", "parent-media-source-tabs");
    sourceTabs.setAttribute("role", "tablist");
    sourceTabs.setAttribute("aria-label", t("audioSourceTabsLabel"));
    sourceTabs.addEventListener("keydown", (event) => {
      if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
      event.preventDefault();
      selectAudioSource(audioSource === "cloud" ? "local" : "cloud");
    });
    sourceTabs.appendChild(audioSourceTabButton(
      "cloud",
      t("audioSourceCloud"),
      cloudItems.length ? cloudReady + "/" + cloudItems.length : "0"
    ));
    sourceTabs.appendChild(audioSourceTabButton(
      "local",
      t("audioSourceLocal"),
      localItems.length
    ));
    sourceRow.appendChild(sourceTabs);
    if (audioSource === "cloud") {
      const refreshBusy = Boolean(catalog.refreshing);
      const refresh = button(refreshBusy ? "…" : "↻", "parent-media-refresh-icon",
        () => refreshAudioLibrary());
      refresh.setAttribute("aria-label", refreshBusy ? t("audioRefreshing") : t("audioRefresh"));
      refresh.title = refreshBusy ? t("audioRefreshing") : t("audioRefresh");
      refresh.disabled = refreshBusy || !editorToken;
      sourceRow.appendChild(refresh);
    }
    scroll.appendChild(sourceRow);

    if (audioSource === "cloud") {
      if (catalog.state === "ERROR" && cloudItems.length) {
        scroll.appendChild(el("div", "parent-media-notice", t("audioCatalogUnavailable")));
      }
      if (!cloudItems.length) {
        let emptyText = t("audioCloudEmpty");
        if (catalog.configured === false) emptyText = t("audioCatalogOff");
        else if (catalog.state === "ERROR") emptyText = t("audioCatalogUnavailable");
        else if (catalog.refreshing) emptyText = t("audioRefreshing");
        scroll.appendChild(el("div", "parent-empty", emptyText));
      } else {
        const cloudList = el("div", "parent-media-audio-list");
        cloudItems.forEach((item) => cloudList.appendChild(audioCloudOption(item, canEdit)));
        scroll.appendChild(cloudList);
      }
    } else {
      const uploadZone = el("div", "parent-media-upload-zone");
      const uploadWrap = el("div", "parent-upload parent-media-upload");
      const input = el("input");
      input.type = "file";
      input.accept = "audio/mpeg,audio/mp4,audio/aac,audio/ogg,.mp3,.m4a,.aac,.ogg,.oga";
      input.className = "parent-upload-file";
      input.setAttribute("aria-label", t("audioUpload"));
      const upload = button(
        audioUploading ? t("audioUploading") : "＋ " + t("audioUpload"),
        "parent-btn parent-media-upload-btn",
        () => input.click()
      );
      upload.disabled = !canEdit || audioUploading;
      input.addEventListener("change", () => {
        const file = input.files && input.files[0];
        input.value = "";
        if (file) uploadAudioFile(file);
      });
      uploadWrap.appendChild(upload);
      uploadWrap.appendChild(input);
      uploadZone.appendChild(uploadWrap);
      scroll.appendChild(uploadZone);

      const localList = el("div", "parent-media-audio-list");
      localItems.forEach((item) => localList.appendChild(audioOption(item, canEdit)));
      if (localItems.length) scroll.appendChild(localList);
      if (!localItems.length) scroll.appendChild(el("p", "parent-hint", t("audioEmpty")));
    }
    sheet.appendChild(scroll);
  }

  function audioSourceTabButton(source, label, count) {
    const node = button("", "parent-media-source-tab" + (audioSource === source ? " is-active" : ""),
      () => selectAudioSource(source));
    node.id = source === "cloud" ? "audioCloudSourceTab" : "audioLocalSourceTab";
    node.dataset.source = source;
    node.setAttribute("role", "tab");
    node.setAttribute("aria-selected", audioSource === source ? "true" : "false");
    node.tabIndex = audioSource === source ? 0 : -1;
    node.appendChild(el("span", "parent-media-source-label", label));
    node.appendChild(el("span", "parent-media-source-count", count));
    return node;
  }

  function selectAudioSource(source) {
    if ((source !== "cloud" && source !== "local") || source === audioSource) return;
    audioSource = source;
    paintMediaSheet();
    const tab = $(source === "cloud" ? "audioCloudSourceTab" : "audioLocalSourceTab");
    if (tab) tab.focus({ preventScroll: true });
  }

  function refreshAudioLibrary() {
    if (!editorToken) {
      setNotice(t("editorLost"), true);
      return;
    }
    mediaWrite("/api/parent/media/audio/refresh", {}, null, true);
  }

  function audioCloudOption(item, canEdit) {
    const selected = item.id === (mediaAudio().selectedId || "");
    const state = audioCloudState(item);
    const busy = audioCloudBusy(item);
    const row = el("div", "parent-media-audio-row" + (selected ? " is-selected" : ""));
    const pick = el("button", "parent-media-audio-pick");
    pick.type = "button";
    pick.setAttribute("aria-pressed", String(selected));
    pick.appendChild(el("span", "parent-media-audio-mark", "♪"));
    const main = el("span", "parent-media-audio-main");
    main.appendChild(el("span", "parent-media-audio-name", mediaName(item)));
    main.appendChild(el("span", "parent-media-audio-sub", audioCloudSub(item)));
    pick.appendChild(main);
    if (selected) {
      pick.appendChild(el("span", "parent-media-audio-check", "✓"));
    } else {
      const action = audioCloudAction(item, canEdit);
      if (action) pick.appendChild(el("span", "parent-media-audio-action", action));
    }
    pick.disabled = !canEdit || selected || busy;
    pick.addEventListener("click", () => useOrDownloadCloudAudio(item));
    row.appendChild(pick);

    if (busy) {
      const cancel = button("×", "parent-media-audio-delete", () => {
        audioDownloadWatch = null;
        audioDownloadWatchFrom = "";
        mediaWrite("/api/parent/media/audio/cancel", { id: item.id });
      });
      cancel.setAttribute("aria-label", t("audioCancelDownload"));
      cancel.disabled = !canEdit;
      row.appendChild(cancel);
    } else if (state === "READY" || state === "UPDATE_AVAILABLE") {
      const remove = button("×", "parent-media-audio-delete", () => {
        confirmSheet(
          t("audioDeleteDownloadTitle"),
          t("audioDeleteDownloadText"),
          t("audioDeleteDownload"),
          async () => {
            if (audioDownloadWatch === item.id) {
              audioDownloadWatch = null;
              audioDownloadWatchFrom = "";
            }
            await mediaWrite("/api/parent/media/audio/delete", { id: item.id },
              "audioDownloadDeleted");
            paintMediaSheet();
            if (subpage === "personalize") render();
          },
          t("cancel")
        );
      });
      remove.setAttribute("aria-label", t("audioDeleteDownload"));
      remove.disabled = !canEdit;
      row.appendChild(remove);
    }
    return row;
  }

  /** A ready track is selected; anything else is downloaded first and selected on completion. */
  async function useOrDownloadCloudAudio(item) {
    if (!mediaCanEdit()) return;
    const state = audioCloudState(item);
    if (state === "READY") {
      await mediaWrite("/api/parent/media/audio/select", { id: item.id }, "audioSelected");
      return;
    }
    if (audioCloudBusy(item)) return;
    const from = mediaAudio().selectedId || "";
    const ok = await mediaWrite("/api/parent/media/audio/download", { id: item.id }, null, true);
    if (!ok) return;
    const fresh = mediaAudioCloudItem(item.id);
    if (fresh && audioCloudState(fresh) === "READY") {
      await mediaWrite("/api/parent/media/audio/select", { id: item.id }, "audioDownloadDone");
      return;
    }
    audioDownloadWatch = item.id;
    audioDownloadWatchFrom = from;
    setNotice(t("audioDownloadStarted"), false);
    paintMediaSheet();
    scheduleMediaPoll();
  }

  /**
   * Polling resolves a started download. Only auto-select when the parent has not chosen
   * another track while waiting; a manual choice always wins.
   */
  function resolveAudioDownloadWatch() {
    if (!audioDownloadWatch || mediaSheetMode !== "audio") return false;
    const item = mediaAudioCloudItem(audioDownloadWatch);
    if (!item) {
      audioDownloadWatch = null;
      audioDownloadWatchFrom = "";
      return false;
    }
    const state = audioCloudState(item);
    if (state === "DOWNLOADING" || state === "VERIFYING") return false;
    const from = audioDownloadWatchFrom;
    audioDownloadWatch = null;
    audioDownloadWatchFrom = "";
    if (state === "FAILED") {
      const code = item.download && item.download.error;
      setNotice(code ? errorText({ code: code }) : t("audioDownloadFailed"), true);
      return false;
    }
    if (state !== "READY") return false;
    if ((mediaAudio().selectedId || "") !== from) {
      setNotice(t("audioDownloaded"), false);
      return false;
    }
    mediaWrite("/api/parent/media/audio/select", { id: item.id }, "audioDownloadDone");
    return true;
  }

  function audioOption(item, canEdit) {
    const isNone = !item.id;
    const selectedId = mediaAudio().selectedId || "";
    const selected = isNone ? !selectedId : item.id === selectedId;
    const row = el("div", "parent-media-audio-row" + (selected ? " is-selected" : ""));
    const pick = el("button", "parent-media-audio-pick");
    pick.type = "button";
    pick.setAttribute("aria-pressed", String(selected));
    pick.appendChild(el("span", "parent-media-audio-mark", isNone ? "∅" : "♪"));
    const main = el("span", "parent-media-audio-main");
    main.appendChild(el("span", "parent-media-audio-name", item.name));
    main.appendChild(el("span", "parent-media-audio-sub",
      isNone ? item.hint : audioMetaText(item)));
    pick.appendChild(main);
    if (selected) pick.appendChild(el("span", "parent-media-audio-check", "✓"));
    pick.disabled = !canEdit || selected;
    pick.addEventListener("click", async () => {
      await mediaWrite("/api/parent/media/audio/select", { id: item.id || "" }, "audioSelected");
      paintMediaSheet();
      if (subpage === "personalize") render();
    });
    row.appendChild(pick);
    if (!isNone) {
      const remove = button("×", "parent-media-audio-delete", () => {
        confirmSheet(t("audioDeleteTitle"), t("audioDeleteText"), t("audioDelete"), async () => {
          await mediaWrite("/api/parent/media/audio/delete", { id: item.id }, "audioDeleted");
          paintMediaSheet();
          if (subpage === "personalize") render();
        }, t("cancel"));
      });
      remove.setAttribute("aria-label", t("audioDelete"));
      remove.disabled = !canEdit;
      row.appendChild(remove);
    }
    return row;
  }

  async function uploadAudioFile(file) {
    if (!editorToken || audioUploading) return;
    if (typeof host.upload !== "function") {
      setNotice(t("featureUnavailable"), true);
      return;
    }
    const name = file.name || "audio";
    const limit = Number(mediaAudio().maxBytes) || 20 * 1024 * 1024;
    if (!/\.(mp3|m4a|aac|ogg|oga)$/i.test(name)) {
      setNotice(t("audioBadFormat"), true);
      return;
    }
    if (!file.size || file.size > limit) {
      setNotice(t("audioTooLarge"), true);
      return;
    }
    // The upload route only stores the file. Remember the list and selection first, so the
    // first track can be adopted automatically; a track the parent already chose is kept.
    const knownIds = new Set(mediaAudioItems().map((item) => item.id));
    const hadSelection = !!mediaAudio().selectedId;
    clearNotices();
    audioUploading = true;
    paintMediaSheet();
    let noticeKey = "audioUploaded";
    let keepError = false;
    try {
      const data = await host.upload(
        "/api/parent/audio?name=" + encodeURIComponent(name),
        file,
        editorToken
      );
      if (data && data.snapshot) adoptSnapshot(data.snapshot, false);
      if (!hadSelection) {
        const fresh = mediaAudioItems().find((item) => !knownIds.has(item.id));
        if (fresh) {
          // If this fails, mediaWrite has already shown the error; do not hide it with the
          // upload notice.
          if (await mediaWrite(
            "/api/parent/media/audio/select", { id: fresh.id }, "audioSelected", true
          )) {
            noticeKey = "audioSelected";
          } else {
            keepError = true;
          }
        }
      }
    } catch (error) {
      if (isLeaseError(error)) {
        audioUploading = false;
        handleLeaseError(error);
        return;
      }
      if (error.data && error.data.snapshot) adoptSnapshot(error.data.snapshot, false);
      setNotice(errorText(error), true);
      keepError = true;
    }
    audioUploading = false;
    paintMediaSheet();
    if (subpage === "personalize") render();
    if (!keepError) setNotice(t(noticeKey), false);
  }

  function mediaPickerOptions() {
    const options = [{ id: "", item: null }];
    const items = (snap && snap.media && snap.media.items) || [];
    items.filter((item) => mediaIsPlayable(item) && mediaCanUseInRole(item, mediaPickerRole))
      .forEach((item) => options.push({ id: item.id, item: item }));
    return options;
  }

  function mediaPickerOption(option, currentId, multi) {
    const selectedIndex = multi && option.id ? mediaLockChoices.indexOf(option.id) : -1;
    const selected = multi
      ? (option.id ? selectedIndex >= 0 : mediaLockChoices.length === 0)
      : option.id === mediaPickerChoice;
    const card = el("button", "parent-media-option" + (selected ? " is-selected" : ""));
    card.type = "button";
    card.setAttribute("aria-pressed", String(selected));
    const cover = option.item
      ? mediaCover(option.item, "parent-media-option-cover")
      : mediaDefaultThumb("parent-media-option-cover");
    if (option.item) {
      cover.appendChild(el("span", "parent-media-kind-badge parent-media-option-kind",
        mediaKindLabel(option.item)));
    }
    if (!multi && option.id && option.id === currentId) {
      cover.appendChild(el("span", "parent-media-option-current", t("mediaCurrentBadge")));
    }
    if (selected) {
      // A multi-select badge is both the check and the position in the rotation.
      const check = el("span", "parent-media-option-check" +
        (selectedIndex >= 0 ? " is-order" : ""), selectedIndex >= 0 ? "✓ " + (selectedIndex + 1) : "✓");
      cover.appendChild(check);
    }
    card.appendChild(cover);
    let title = option.item ? mediaName(option.item) : t("mediaSourceDefault");
    if (mediaHasAudio(option.item)) title += " · " + t("mediaHasAudio");
    if (mediaIsWallpaperOnly(option.item)) title += " · " + t("mediaOnlyWallpaper");
    card.appendChild(el("span", "parent-media-option-title", title));
    card.title = title;
    // At the limit the unselected tiles stay visible but cannot join; selected ones can leave.
    card.disabled = Boolean(multi && option.id && !selected &&
      mediaLockChoices.length >= mediaLockMaxItems());
    card.addEventListener("click", () => {
      if (multi) {
        if (!option.id) {
          if (!mediaLockChoices.length) return;
          mediaLockChoices = [];
        } else {
          const index = mediaLockChoices.indexOf(option.id);
          if (index >= 0) mediaLockChoices.splice(index, 1);
          else if (mediaLockChoices.length < mediaLockMaxItems()) mediaLockChoices.push(option.id);
          else return;
        }
        paintMediaSheet();
        return;
      }
      if (mediaPickerChoice === option.id) return;
      mediaPickerChoice = option.id;
      paintMediaSheet();
    });
    return card;
  }

  function applyMediaPickerChoice() {
    const role = mediaPickerRole;
    const current = mediaRolesState()[role] || "";
    if (mediaPickerChoice === current) return;
    const id = mediaPickerChoice;
    closeMediaSheet();
    mediaWrite("/api/parent/media/select", { id: id, role: role }, "mediaSelected");
  }

  function mediaLockSelectionMatchesStored() {
    const stored = mediaLockStoredSelectableIds().slice(0, mediaLockMaxItems());
    const sameIds = stored.length === mediaLockChoices.length &&
      stored.every((id, index) => id === mediaLockChoices[index]);
    if (!sameIds) return false;
    // The interval only matters while there are at least two items to rotate.
    return mediaLockChoices.length < 2 || mediaLockInterval === mediaLockStoredInterval();
  }

  function applyMediaLockChoices() {
    const ids = mediaLockChoices.slice();
    const intervalMs = mediaLockInterval || mediaLockStoredInterval();
    closeMediaSheet();
    mediaWrite("/api/parent/media/playlist",
      { ids: ids, intervalMs: intervalMs }, "mediaPlaylistSaved");
  }

  function mediaLockIntervalRow(canEdit) {
    const wrap = el("div", "parent-media-interval");
    wrap.appendChild(el("span", "parent-media-interval-label", t("mediaLockInterval")));
    const choices = el("div", "parent-media-interval-options");
    mediaIntervalOptions().forEach((ms) => {
      const active = ms === mediaLockInterval;
      const chip = button(mediaIntervalText(ms),
        "parent-media-interval-chip" + (active ? " is-active" : ""), () => {
          if (mediaLockInterval === ms) return;
          mediaLockInterval = ms;
          paintMediaSheet();
        });
      chip.setAttribute("aria-pressed", String(active));
      chip.disabled = !canEdit;
      choices.appendChild(chip);
    });
    wrap.appendChild(choices);
    return wrap;
  }

  function renderMediaPickerSheet(sheet, close) {
    const canEdit = mediaCanEdit();
    const lockMode = mediaPickerRole === "lock";
    const stored = mediaRolesState()[mediaPickerRole] || "";
    const options = mediaPickerOptions();
    if (lockMode) {
      mediaLockChoices = mediaLockChoices.filter((id, index) =>
        options.some((option) => option.id === id) &&
        mediaLockChoices.indexOf(id) === index
      ).slice(0, mediaLockMaxItems());
      mediaLockInterval = Math.min(mediaLockMaxInterval(),
        Math.max(mediaLockMinInterval(), mediaLockInterval || mediaLockStoredInterval()));
    } else if (mediaPickerChoice && !options.some((option) => option.id === mediaPickerChoice)) {
      mediaPickerChoice = options.some((option) => option.id === stored) ? stored : "";
    }
    const selected = lockMode ? null : options.find((option) => option.id === mediaPickerChoice) || options[0];
    const scroll = el("div", "parent-media-sheet-scroll");
    scroll.appendChild(el("h2", null,
      t("mediaPickerTitle", { module: mediaModuleTitle(mediaPickerRole) })));

    if (lockMode) {
      const hintKey = mediaLockChoices.length === 0
        ? "mediaLockEmptyHint"
        : mediaLockChoices.length === 1
          ? "mediaLockSingleHint"
          : mediaLockChoices.length >= mediaLockMaxItems()
            ? "mediaLockLimitHint"
            : "mediaLockPickerHint";
      scroll.appendChild(el("p", "parent-hint parent-media-lock-hint", t(hintKey)));
    } else {
      const stage = el("div", "parent-media-stage");
      if (selected.item) {
        stage.appendChild(mediaCover(selected.item, "parent-media-stage-cover"));
      } else {
        stage.classList.add("is-default");
        stage.appendChild(mediaDefaultThumb("parent-media-stage-cover"));
      }
      const stageCaption = el("div", "parent-media-stage-caption");
      stageCaption.appendChild(el("strong", null,
        selected.item ? mediaName(selected.item) : t("mediaSourceDefault")));
      stageCaption.appendChild(el("span", null, mediaStageMeta(selected.item)));
      stage.appendChild(stageCaption);
      scroll.appendChild(stage);
    }

    const strip = el("div", lockMode ? "parent-media-lock-grid" : "parent-media-strip");
    options.forEach((option) => strip.appendChild(mediaPickerOption(option, stored, lockMode)));
    scroll.appendChild(strip);

    if (options.length === 1) {
      const empty = el("div", "parent-media-picker-empty");
      empty.appendChild(el("p", null,
        mediaPickerRole === "quiz" ? t("mediaPickerEmptyVideo") : t("mediaPickerEmpty")));
      empty.appendChild(button(t("mediaPickerGoLibrary"), "parent-media-link", () => {
        close();
        mediaSource = "cloud";
        mediaKind = mediaPickerRole === "quiz" ? "video" : "image";
        render();
        const target = $("mediaKindTabs");
        if (target) target.scrollIntoView({ behavior: "smooth", block: "start" });
      }));
      scroll.appendChild(empty);
    }
    sheet.appendChild(scroll);

    const footer = el("div", "parent-media-sheet-footer");
    if (lockMode) {
      if (mediaLockChoices.length >= 2) footer.appendChild(mediaLockIntervalRow(canEdit));
      const same = mediaLockSelectionMatchesStored();
      const label = mediaLockChoices.length === 0
        ? t("mediaUseDefault")
        : mediaLockChoices.length === 1
          ? t("mediaSave")
          : t("mediaSaveCount", { n: mediaLockChoices.length });
      const use = button(same ? t("mediaInUse") : label, "parent-btn primary",
        applyMediaLockChoices);
      use.disabled = !canEdit || same;
      footer.appendChild(use);
    } else {
      const same = mediaPickerChoice === (stored || "");
      const use = button(same ? t("mediaInUse") : t("mediaUse"), "parent-btn primary",
        applyMediaPickerChoice);
      use.disabled = !canEdit || same;
      footer.appendChild(use);
    }
    if (!canEdit) footer.appendChild(el("p", "parent-hint", t("readOnlyCloud")));
    sheet.appendChild(footer);

    if (mediaPickerScrollPending) {
      mediaPickerScrollPending = false;
      if (!lockMode) {
        requestAnimationFrame(() => {
          const active = strip.querySelector(".is-selected");
          if (active) strip.scrollLeft = Math.max(0, active.offsetLeft - 8);
        });
      }
    }
  }

  async function refreshMediaLibrary() {
    if (!editorToken) {
      setNotice(t("editorLost"), true);
      return;
    }
    // The button itself shows "refreshing" until the catalog snapshot settles; a notice here
    // would outlive the actual refresh and read as if it were stuck.
    mediaWrite("/api/parent/media/refresh", {}, null, true);
  }

  async function mediaWrite(path, body, noticeKey, silent) {
    if (!editorToken) {
      setNotice(t("editorLost"), true);
      return;
    }
    clearNotices();
    let failed = false;
    try {
      const data = await host.request(path, {
        method: "POST",
        body: body || {},
        editorToken: editorToken,
        quizAuth: true,
      });
      if (data.snapshot) adoptSnapshot(data.snapshot, false);
      if (!silent) setNotice(noticeKey ? t(noticeKey) : successText(data.messageKey), false);
    } catch (error) {
      if (isLeaseError(error)) {
        handleLeaseError(error);
        return;
      }
      if (error.data && error.data.snapshot) adoptSnapshot(error.data.snapshot, false);
      if (path === "/api/parent/media/download") mediaDownloadWatch = null;
      setNotice(errorText(error), true);
      failed = true;
    }
    if (subpage === "personalize" && rootOpen) {
      render();
      paintMediaSheet();
      scheduleMediaPoll();
    }
    // A download can be started far down the poster wall; bring its failure (for example the
    // 8-item limit) into view instead of leaving the top notice off-screen.
    if (failed && path === "/api/parent/media/download") {
      const bad = $("parentErrorNotice");
      if (bad) bad.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
    return !failed;
  }

  function isLeaseError(error) {
    const code = error && (error.code || error.message);
    return error && (error.status === 401 || code === "EDITOR_REQUIRED" ||
      code === "LEASE_EXPIRED" || code === "LEASE_TAKEN_OVER" || code === "QUIZ_TAKEN_OVER");
  }

  // ------------------------------------------------------------ V42 day segments

  const DEFAULT_WINDOW_START = 0; // 00:00
  const DEFAULT_WINDOW_END = 24 * 60; // 24:00, the rule engine's all-day bound
  const SEGMENT_COLORS = [
    "#2e7d6f", "#2f6fdb", "#8057c7", "#b45f2a",
    "#3f8a46", "#bd4f7a", "#9a7b16",
  ];

  function defaultWindow() {
    return { start: DEFAULT_WINDOW_START, end: DEFAULT_WINDOW_END };
  }

  /**
   * The snapshot always carries schema-3 groups. The fallback only keeps the page usable
   * while the first snapshot is missing; it is never saved back on its own.
   */
  function dayGroups() {
    const value = snap && snap.rules && snap.rules.dayGroups;
    if (Array.isArray(value) && value.length) return value;
    return [
      {
        days: [1, 2, 3, 4, 5],
        studyWindows: [defaultWindow()],
        studyDailyLimitMinutes: null,
        entertainmentWindows: [defaultWindow()],
        entertainmentDailyLimitMinutes: null,
      },
      {
        days: [6, 7],
        studyWindows: [defaultWindow()],
        studyDailyLimitMinutes: null,
        entertainmentWindows: [defaultWindow()],
        entertainmentDailyLimitMinutes: null,
      },
    ];
  }

  function dayLabel(day) {
    return t("day" + day);
  }

  /** Gives the draft the schema-3 shape without changing what the parent configured. */
  function normalizedGroups(rules) {
    if (!Array.isArray(rules.dayGroups)) rules.dayGroups = [];
    rules.dayGroups = rules.dayGroups.map((group) => ({
      days: (Array.isArray(group.days) ? group.days.slice() : []).sort((a, b) => a - b),
      studyWindows: Array.isArray(group.studyWindows) ? group.studyWindows : [],
      studyDailyLimitMinutes: group.studyDailyLimitMinutes === undefined
        ? null : group.studyDailyLimitMinutes,
      entertainmentWindows: Array.isArray(group.entertainmentWindows)
        ? group.entertainmentWindows : [],
      entertainmentDailyLimitMinutes: group.entertainmentDailyLimitMinutes === undefined
        ? null : group.entertainmentDailyLimitMinutes,
    }));
    return rules.dayGroups;
  }

  function segmentColor(index) {
    return SEGMENT_COLORS[index % SEGMENT_COLORS.length];
  }

  function copyWindows(windows) {
    return Array.isArray(windows) ? clone(windows) : [];
  }

  /** Copies one group's settings without its days; an empty window stays closed. */
  function cloneGroupSettings(source) {
    return {
      days: [],
      studyWindows: copyWindows(source.studyWindows),
      studyDailyLimitMinutes: source.studyDailyLimitMinutes === undefined
        ? null : source.studyDailyLimitMinutes,
      entertainmentWindows: copyWindows(source.entertainmentWindows),
      entertainmentDailyLimitMinutes: source.entertainmentDailyLimitMinutes === undefined
        ? null : source.entertainmentDailyLimitMinutes,
    };
  }

  function hasNonContiguousGroups(groups) {
    return (Array.isArray(groups) ? groups : []).some((group) => {
      const days = (Array.isArray(group.days) ? group.days.slice() : []).sort((a, b) => a - b);
      return days.some((day, index) => index > 0 && day !== days[index - 1] + 1);
    });
  }

  /**
   * The editor draws contiguous week runs. A group saved by an older client may contain a gap;
   * each run then becomes its own segment with a copy of the original settings. The effective
   * rules stay the same, and walking a seven-day week can never produce more than seven runs.
   */
  function contiguousGroups(groups) {
    const owner = new Array(8).fill(null);
    (groups || []).forEach((group) => {
      (Array.isArray(group.days) ? group.days : []).forEach((day) => {
        if (day >= 1 && day <= 7) owner[day] = group;
      });
    });
    const result = [];
    let current = null;
    for (let day = 1; day <= 7; day++) {
      const source = owner[day];
      if (!source) {
        current = null;
        continue;
      }
      if (!current || current.source !== source) {
        current = { source, group: cloneGroupSettings(source) };
        result.push(current.group);
      }
      current.group.days.push(day);
    }
    return result;
  }

  /** The contiguous segments the editor draws for the current snapshot. */
  function editorGroups() {
    const rules = { dayGroups: clone(dayGroups()) };
    return contiguousGroups(normalizedGroups(rules));
  }

  function segmentStarts(groups) {
    return (groups || []).map((group) => group.days[0]);
  }

  function sameStarts(left, right) {
    return left.length === right.length && left.every((day, index) => day === right[index]);
  }

  function segmentLabel(group) {
    const days = (Array.isArray(group.days) ? group.days.slice() : []).sort((a, b) => a - b);
    if (!days.length) return t("emptyValue");
    if (days.length === 7) return t("presetWholeWeek");
    if (days.length === 5 && days[0] === 1 && days[4] === 5) return t("groupWeekdays");
    if (days.length === 2 && days[0] === 6 && days[1] === 7) return t("groupWeekend");
    if (days.length === 1) return dayLabel(days[0]);
    return dayLabel(days[0]) + "–" + dayLabel(days[days.length - 1]);
  }

  function groupWindow(group, kind) {
    const windows = group[kind + "Windows"];
    return Array.isArray(windows) && windows.length ? windows[0] : null;
  }

  function groupQuota(group, kind) {
    const value = group[kind + "DailyLimitMinutes"];
    return value === null || value === undefined ? null : Number(value);
  }

  function quotaText(value) {
    return value === null || value === undefined || !Number.isFinite(Number(value))
      ? t("unlimited") : t("minutes", { n: Number(value) });
  }

  function quotaOptions() {
    return [null, 0, 1, 5, 15, 30, 45, 60, 90, 120, 180, 240, 300, 360, 480, 600, 720, 960, 1200, 1440];
  }

  function sameWindows(left, right) {
    const a = Array.isArray(left) ? left : [];
    const b = Array.isArray(right) ? right : [];
    if (a.length !== b.length) return false;
    return a.every((window, index) =>
      Number(window.start) === Number(b[index].start) &&
      Number(window.end) === Number(b[index].end));
  }

  function sameGroupSettings(left, right) {
    return sameWindows(left.studyWindows, right.studyWindows) &&
      sameWindows(left.entertainmentWindows, right.entertainmentWindows) &&
      groupQuota(left, "study") === groupQuota(right, "study") &&
      groupQuota(left, "entertainment") === groupQuota(right, "entertainment");
  }

  function updateGroupAt(startDay, mutate) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return false;
    const next = clone(snap.rules || {});
    if (!Array.isArray(next.dayGroups) || !next.dayGroups.length) {
      next.dayGroups = clone(dayGroups());
    }
    const groups = contiguousGroups(normalizedGroups(next));
    const group = groups.find((item) => item.days[0] === startDay);
    if (!group) return false;
    mutate(group);
    next.dayGroups = groups;
    next.rulesContractVersion = RULES_CONTRACT_VERSION;
    return saveRules(next);
  }

  function updateGroupWindowAt(startDay, kind, start, end) {
    if (start < 0 || end <= start || end > 1440) {
      setNotice(t("rulesInvalid"), true);
      return false;
    }
    return updateGroupAt(startDay, (group) => {
      group[kind + "Windows"] = [{ start: start, end: end }];
    });
  }

  function setGroupQuotaAt(startDay, kind, value) {
    if (!updateGroupAt(startDay, (group) => {
      group[kind + "DailyLimitMinutes"] = value === null ? null : Number(value);
    })) return;
    paintSegmentQuotas();
  }

  /** Repaints quota chips after a debounced save; the segment layout itself is unchanged. */
  function paintSegmentQuotas() {
    const rules = snap && snap.rules;
    if (!rules) return;
    const source = Array.isArray(rules.dayGroups) && rules.dayGroups.length
      ? rules.dayGroups : dayGroups();
    const groups = contiguousGroups(normalizedGroups({ dayGroups: clone(source) }));
    const canEdit = Boolean(snap.capabilities && snap.capabilities.canEditRules);
    document.querySelectorAll(".parent-seg-quota").forEach((node) => {
      const group = groups.find((item) => item.days[0] === Number(node.dataset.groupStart));
      if (!group) return;
      const kind = node.dataset.groupKind;
      const value = groupQuota(group, kind);
      const label = kind === "study" ? t("studyLimit") : t("entertainmentLimit");
      node.textContent = quotaText(value);
      node.setAttribute("aria-label", label + ": " + quotaText(value));
      node.disabled = !canEdit;
    });
  }

  function applySegmentGroups(groups) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return false;
    const normalized = contiguousGroups(normalizedGroups({ dayGroups: clone(groups) }));
    const seen = new Set();
    for (const group of normalized) {
      for (const day of group.days) {
        if (day < 1 || day > 7 || seen.has(day)) return false;
        seen.add(day);
      }
    }
    if (seen.size !== 7 || normalized.length < 1 || normalized.length > 7) return false;
    const next = clone(snap.rules || {});
    next.dayGroups = normalized;
    next.rulesContractVersion = RULES_CONTRACT_VERSION;
    if (!saveRules(next)) return false;
    rerenderTimePage();
    return true;
  }

  function toggleSegmentStart(day) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    if (day < 2 || day > 7) return;
    const groups = editorGroups();
    const index = groups.findIndex((group) => group.days.indexOf(day) >= 0);
    if (index < 0) return;
    const group = groups[index];
    const position = group.days.indexOf(day);
    if (position === 0) {
      if (index === 0) return;
      const merged = cloneGroupSettings(groups[index - 1]);
      merged.days = groups[index - 1].days.concat(group.days).sort((a, b) => a - b);
      const apply = () => {
        const next = groups.slice(0, index - 1)
          .concat([merged], groups.slice(index + 1));
        applySegmentGroups(next);
      };
      if (sameGroupSettings(groups[index - 1], group)) {
        apply();
      } else {
        confirmSheet(
          t("mergeSegmentsTitle"),
          t("mergeSegmentsBody"),
          t("mergeSegmentsAction"),
          apply
        );
      }
      return;
    }
    const right = cloneGroupSettings(group);
    right.days = group.days.slice(position);
    group.days = group.days.slice(0, position);
    applySegmentGroups(groups.slice(0, index).concat([group, right], groups.slice(index + 1)));
  }

  function applySegmentPreset(starts) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    const current = editorGroups();
    const targets = starts.slice().sort((a, b) => a - b);
    if (sameStarts(segmentStarts(current), targets)) return;
    const built = [];
    let lossy = false;
    targets.forEach((start, index) => {
      const end = index + 1 < targets.length ? targets[index + 1] - 1 : 7;
      const source = current.find((group) => group.days.indexOf(start) >= 0);
      if (!source) return;
      const group = cloneGroupSettings(source);
      for (let day = start; day <= end; day++) group.days.push(day);
      built.push(group);
      // A preset replaces every segment in its range with the settings at the range start.
      // Confirm when that would discard a different configuration.
      current.forEach((existing) => {
        if (existing.days.some((day) => day >= start && day <= end) &&
            !sameGroupSettings(existing, source)) {
          lossy = true;
        }
      });
    });
    if (built.length !== targets.length || built.some((group) => !group.days.length)) return;
    const apply = () => { applySegmentGroups(built); };
    if (lossy) {
      confirmSheet(
        t("mergeSegmentsTitle"),
        t("mergeSegmentsBody"),
        t("mergeSegmentsAction"),
        apply
      );
    } else {
      apply();
    }
  }

  function renderSegmentEditor() {
    const card = el("div", "parent-card");
    const canEdit = Boolean(snap.capabilities && snap.capabilities.canEditRules);
    const groups = editorGroups();
    const starts = segmentStarts(groups);
    const presets = [
      { starts: [1, 6], label: t("groupWeekdays") + " / " + t("groupWeekend") },
      { starts: [1], label: t("presetWholeWeek") },
      { starts: [1, 2, 3, 4, 5, 6, 7], label: t("presetEveryDay") },
    ];
    const presetRow = el("div", "parent-seg-presets");
    presets.forEach((preset) => {
      const chip = button(preset.label, "parent-seg-preset", () => {
        applySegmentPreset(preset.starts);
      });
      chip.setAttribute("aria-pressed", String(sameStarts(starts, preset.starts)));
      chip.disabled = !canEdit;
      presetRow.appendChild(chip);
    });
    card.appendChild(presetRow);

    const week = el("div", "parent-seg-week");
    const ownerByDay = new Map();
    groups.forEach((group, index) => {
      group.days.forEach((day) => ownerByDay.set(day, index));
    });
    for (let day = 1; day <= 7; day++) {
      const index = ownerByDay.has(day) ? ownerByDay.get(day) : 0;
      const group = groups[index];
      const isStart = Boolean(group && group.days[0] === day);
      const node = button(dayLabel(day), "parent-seg-day", () => toggleSegmentStart(day));
      node.dataset.segIndex = String(index + 1);
      node.style.setProperty("--seg-color", segmentColor(index));
      node.classList.toggle("is-start", isStart);
      node.classList.toggle("is-first", day === 1);
      node.setAttribute("aria-pressed", String(isStart));
      const hint = day === 1
        ? t("segmentFirstHint")
        : (isStart ? t("segmentMergeHint") : t("segmentSplitHint"));
      node.setAttribute("aria-label", dayLabel(day) + " · " + hint);
      node.disabled = !canEdit || day === 1;
      week.appendChild(node);
    }
    card.appendChild(week);
    if (canEdit) {
      card.appendChild(el("p", "parent-hint parent-seg-hint", t("segmentHint")));
    }
    if (hasNonContiguousGroups(dayGroups())) {
      card.appendChild(el("p", "parent-hint parent-inline-note", t("segmentNonContiguous")));
    }
    if (!canEdit) {
      card.appendChild(el("p", "parent-hint parent-inline-note", t("readOnlyCloud")));
    }
    return card;
  }

  function renderSegmentRow(group, startDay, kind, label, quotaLabel, canEdit) {
    const row = el("div", "parent-seg-row");
    row.appendChild(el("span", "parent-seg-label", label));
    const wrap = el("div", "parent-window parent-seg-window");
    const window = groupWindow(group, kind) || defaultWindow();
    const start = timeField(window.start, label + " " + t("from"), false);
    const end = timeField(window.end, label + " " + t("to"), true);
    let currentStart = Number(window.start);
    let currentEnd = Number(window.end);
    const commit = (nextStart, nextEnd) => {
      if (!updateGroupWindowAt(startDay, kind, nextStart, nextEnd)) {
        start.set(currentStart);
        end.set(currentEnd);
        return;
      }
      currentStart = nextStart;
      currentEnd = nextEnd;
    };
    start.onchange(() => commit(start.get(), currentEnd));
    end.onchange(() => commit(currentStart, end.get()));
    wrap.appendChild(start.node);
    wrap.appendChild(el("span", "parent-time-sep", "—"));
    wrap.appendChild(end.node);
    if (!canEdit) {
      start.node.querySelectorAll("select").forEach((select) => { select.disabled = true; });
      end.node.querySelectorAll("select").forEach((select) => { select.disabled = true; });
    }
    row.appendChild(wrap);
    const value = groupQuota(group, kind);
    const quota = button(quotaText(value), "parent-seg-quota", () => {
      openLimitSheet(quotaLabel, value, quotaOptions(),
        (picked) => setGroupQuotaAt(startDay, kind, picked));
    });
    quota.dataset.groupStart = String(startDay);
    quota.dataset.groupKind = kind;
    quota.setAttribute("aria-label", quotaLabel + ": " + quotaText(value));
    quota.disabled = !canEdit;
    const quotaWrap = el("div", "parent-seg-quota-wrap");
    quotaWrap.appendChild(el("span", "parent-seg-quota-label", t("dailyLimit")));
    quotaWrap.appendChild(quota);
    row.appendChild(quotaWrap);
    return row;
  }

  function renderSegmentCards() {
    const wrap = el("div", "parent-seg-cards");
    const canEdit = Boolean(snap.capabilities && snap.capabilities.canEditRules);
    editorGroups().forEach((group, index) => {
      const card = el("div", "parent-card parent-seg-card");
      card.style.setProperty("--seg-color", segmentColor(index));
      const head = el("div", "parent-seg-head");
      head.appendChild(el("span", "parent-seg-badge", String(index + 1)));
      head.appendChild(el("span", "parent-seg-range", segmentLabel(group)));
      card.appendChild(head);
      card.appendChild(renderSegmentRow(
        group, group.days[0], "study", t("studyShort"), t("studyLimit"), canEdit));
      card.appendChild(renderSegmentRow(
        group, group.days[0], "entertainment", t("entertainmentShort"),
        t("entertainmentLimit"), canEdit));
      wrap.appendChild(card);
    });
    return wrap;
  }

  function rerenderTimePage() {
    const top = window.scrollY;
    renderTimeAndApps();
    window.scrollTo({ top });
  }

  /**
   * V45 public parameters: one card with the shared session ceiling, the rest and the pause
   * cap. The per-day entertainment quota stays inside each day group below.
   */
  function renderPublicParams() {
    const card = el("div", "parent-card");
    const canEdit = Boolean(snap.capabilities && snap.capabilities.canEditRules);
    const rules = snap.rules || {};
    card.appendChild(limitRow(
      "sessionCapMinutes", t("sessionCapTitle"), sessionCapMinutes(),
      STEP_SESSION_CAP_MINUTES, MIN_SESSION_CAP_MINUTES, MAX_SESSION_CAP_MINUTES, canEdit,
      SESSION_CAP_OPTIONS
    ));
    const hint = el("p", "parent-hint", t("sessionCapHint", { n: sessionCapMinutes() }));
    hint.dataset.capHint = "1";
    card.appendChild(hint);
    card.appendChild(limitRow(
      "restMinutes", t("rest"), Number(rules.restMinutes) || 0,
      STEP_REST, 0, MAX_EDIT_REST, canEdit, REST_OPTIONS
    ));
    card.appendChild(limitRow(
      "sessionScreenOffBufferMinutes", t("buffer"),
      Number(rules.sessionScreenOffBufferMinutes) || 0,
      STEP_BUFFER, MIN_BUFFER, MAX_BUFFER, canEdit, BUFFER_OPTIONS
    ));
    return card;
  }

  function limitRow(key, label, value, step, min, max, canEdit, options) {
    const node = el("div", "parent-row");
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", label));
    node.appendChild(main);
    const controls = el("div", "parent-stepper");
    const minus = button("−", "parent-step", () => adjustLimit(key, -step, min, max));
    const valueBtn = button(t("minutes", { n: value }), "parent-step-value", () => {
      openLimitSheet(label, value, options, (picked) => setLimit(key, picked));
    });
    valueBtn.dataset.limitKey = key;
    const plus = button("+", "parent-step", () => adjustLimit(key, step, min, max));
    [minus, valueBtn, plus].forEach((control) => {
      control.disabled = !canEdit;
      controls.appendChild(control);
    });
    node.appendChild(controls);
    return node;
  }

  function adjustLimit(key, delta, min, max) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    // A legacy snapshot may omit the cap; read it through the same normalization the UI shows.
    const current = key === "sessionCapMinutes"
      ? sessionCapMinutes()
      : Number(snap.rules && snap.rules[key]);
    const value = Number.isFinite(current) ? current : min;
    const next = stepMinuteValue(value, delta, min, max);
    if (next === value) return;
    setLimit(key, next);
  }

  /** Repaints the rule controls already on screen; a save must not rebuild the whole page. */
  function paintRulesControls() {
    const rules = snap && snap.rules;
    if (!rules) return;
    document.querySelectorAll(".parent-step-value[data-limit-key]").forEach((node) => {
      const value = Number(rules[node.dataset.limitKey]);
      if (Number.isFinite(value)) node.textContent = t("minutes", { n: value });
    });
    paintSegmentQuotas();
    paintStudyEarnControls();
    paintDirectMinutes();
    paintRouteCounts();
    paintRouteHints();
    const capHint = document.querySelector("[data-cap-hint]");
    if (capHint) capHint.textContent = t("sessionCapHint", { n: sessionCapMinutes() });
  }

  function openLimitSheet(label, current, options, onPick) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    openSheet((sheet, close) => {
      sheet.appendChild(el("h2", null, label));
      const list = el("div", "parent-choice-list");
      options.forEach((option) => {
        const choice = button(
          option === null ? t("unlimited") : t("minutes", { n: option }),
          "parent-choice",
          () => {
            close();
            onPick(option);
          }
        );
        choice.setAttribute("aria-pressed", String(
          current === null ? option === null : Number(option) === Number(current)));
        list.appendChild(choice);
      });
      sheet.appendChild(list);
      const actions = el("div", "parent-actions");
      actions.appendChild(button(t("cancel"), "parent-btn ghost", close));
      sheet.appendChild(actions);
    });
  }

  function setLimit(key, value) {
    const next = clone(snap.rules);
    next.rulesContractVersion = RULES_CONTRACT_VERSION;
    next[key] = value;
    let clamped = false;
    if (key === "sessionCapMinutes") {
      const direct = directMinutes();
      const y = studyEarnMinutes("studyEarnRewardMinutes");
      if (direct > value) {
        next.sessionLimitMinutes = value;
        next.directSessionMinutes = value;
        clamped = true;
      }
      if (y > value) {
        next.studyEarnRewardMinutes = value;
        clamped = true;
      }
    }
    if (!saveRules(next)) return;
    const node = document.querySelector(`.parent-step-value[data-limit-key="${key}"]`);
    if (node) node.textContent = t("minutes", { n: value });
    if (key === "sessionCapMinutes") {
      paintRulesControls();
      if (clamped) setNotice(t("capClampedNotice", { n: value }), false);
    }
  }

  function renderDeviceRows() {
    const card = el("div", "parent-card");
    if (snap.capabilities && snap.capabilities.canChangePin) {
      const subtitle = snap.defaultPin ? t("passwordDefault")
        : snap.pinSyncPending ? t("passwordSyncPending") : null;
      card.appendChild(row(t("parentPassword"), subtitle, null, () => {
        subpage = "password";
        render();
        window.scrollTo({ top: 0 });
      }));
    }
    const autoRow = el("label", "parent-row parent-switch-row");
    const autoMain = el("div", "parent-row-main");
    autoMain.appendChild(el("span", "parent-row-title", t("autoUpdate")));
    autoMain.appendChild(el("span", "parent-row-sub", t("autoUpdateHint")));
    autoRow.appendChild(autoMain);
    const autoLabel = el("label", "parent-switch");
    const autoInput = el("input");
    autoInput.type = "checkbox";
    autoInput.checked = Boolean(snap.autoUpdate);
    autoInput.addEventListener("change", () => {
      saveSimple("/api/parent/auto-update", { enabled: autoInput.checked }, "autoUpdate", autoInput.checked);
    });
    autoLabel.appendChild(autoInput);
    autoLabel.appendChild(el("span", "parent-switch-track"));
    autoRow.appendChild(autoLabel);
    card.appendChild(autoRow);
    return card;
  }

  // ------------------------------------------------------------------------ writes

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  async function uploadMediaFiles(files) {
    if (!editorToken || uploadState || !files || !files.length) return;
    if (typeof host.upload !== "function") {
      setNotice(t("featureUnavailable"), true);
      render();
      return;
    }
    clearNotices();
    const media = snap.media || {};
    const limits = media.limits || {};
    // Local videos are limited by size and the decoder only; their duration is unrestricted.
    // The TV still reserves free space and enforces the same cap server-side.
    const videoLimit = Number(limits.maxWallpaperFileBytes) || 128 * 1024 * 1024;
    const imageLimit = Number(limits.maxImageUploadBytes) || 12 * 1024 * 1024;
    const firstImage = /\.(jpe?g|png|webp)$/i.test(files[0].name || "");
    mediaSource = "local";
    mediaKind = firstImage ? "image" : "video";
    uploadState = {
      index: 0,
      total: files.length,
      name: "",
      loaded: 0,
      totalBytes: 0,
      added: 0,
      errors: []
    };
    render();
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      const name = file.name || "wallpaper";
      const isImage = /\.(jpe?g|png|webp)$/i.test(name);
      const isVideo = /\.(mp4|mov)$/i.test(name);
      uploadState.index = i + 1;
      uploadState.name = name;
      uploadState.loaded = 0;
      uploadState.totalBytes = Number(file.size) || 0;
      updateUploadProgressDom();
      if (!isImage && !isVideo) {
        uploadState.errors.push(t("mediaBadFormat"));
        continue;
      }
      if (!file.size) {
        uploadState.errors.push(t("mediaBadFormat"));
        continue;
      }
      if (isImage && file.size > imageLimit) {
        uploadState.errors.push(t("mediaImageTooLarge"));
        continue;
      }
      if (isVideo && file.size > videoLimit) {
        uploadState.errors.push(t("introVideoTooLarge"));
        continue;
      }
      try {
        const response = await host.upload(
          "/api/parent/intro-video?name=" + encodeURIComponent(name),
          file,
          editorToken,
          (loaded, total) => {
            if (!uploadState) return;
            uploadState.loaded = Number(loaded) || 0;
            uploadState.totalBytes = Number(total) || file.size || 0;
            updateUploadProgressDom();
          }
        );
        if (response && response.snapshot) adoptSnapshot(response.snapshot, false);
        if (uploadState) {
          uploadState.added += 1;
          uploadState.loaded = uploadState.totalBytes;
          updateUploadProgressDom();
        }
      } catch (error) {
        if (uploadState) uploadState.errors.push(errorText(error));
      }
      // Show each finished wallpaper in the library while the rest of the batch keeps uploading.
      if (subpage === "personalize") render();
    }
    const added = uploadState.added;
    const errors = uploadState.errors.slice();
    uploadState = null;
    // The upload response updates the snapshot after every file; one fresh read also proves
    // to the parent that the list matches what the TV stored.
    await refreshMediaPage(true);
    if (errors.length) {
      const summary = (added > 0 ? t("introVideoBatchDone", { added: added }) + " " : "") +
        t("introVideoBatchFailed", { failed: errors.length, reason: errors[0] });
      setNotice(summary, true);
    } else {
      setNotice(t("introVideoBatchDone", { added: added }), false);
    }
    render();
  }

  function updateUploadProgressDom() {
    const text = $("parentUploadProgressText");
    const fill = $("parentUploadProgressFill");
    const bar = $("parentUploadProgressBar");
    if (!uploadState || !text || !fill) return;
    const total = Math.max(1, uploadState.totalBytes || 0);
    const percent = Math.max(0, Math.min(100, Math.round((uploadState.loaded / total) * 100)));
    if (bar) bar.setAttribute("aria-valuenow", String(percent));
    text.textContent = t("introVideoUploadProgress", {
      index: uploadState.index,
      total: uploadState.total,
      name: uploadState.name || "",
      percent: percent
    });
    fill.style.width = percent + "%";
  }

  async function saveSimple(path, body, localField, localValue, options) {
    if (!editorToken) return false;
    clearNotices();
    const opts = options || {};
    const request = {
      method: "POST",
      body: body,
      editorToken: editorToken,
      quizAuth: true,
    };
    try {
      const data = opts.sensitiveScope
        ? await requestWithSensitive(
          path,
          request,
          opts.sensitiveScope,
          opts.leadKey || "sensitivePinLeadRules"
        )
        : await host.request(path, request);
      if (!data) {
        // The parent cancelled the verification sheet; nothing was sent.
        render();
        return false;
      }
      if (data.snapshot) adoptSnapshot(data.snapshot, false);
      else if (localField) snap[localField] = localValue;
      setNotice(successText(data.messageKey), false);
      render();
      return true;
    } catch (error) {
      await recoverWrite(error);
      render();
      return false;
    }
  }

  /**
   * V5 server validation is strict: direct 1..cap, Y 1..cap, X 1..180 and counts 0..10.
   * X and Y are independent inputs, so Y is not clamped by X. A legacy snapshot can still hold
   * values outside those ranges, so clamp the outgoing draft; the clamp is what the next
   * snapshot reads back and displays.
   */
  function clampRules(next) {
    const capValue = Number(next.sessionCapMinutes);
    const cap = Math.min(MAX_SESSION_CAP_MINUTES, Math.max(MIN_SESSION_CAP_MINUTES,
      Number.isFinite(capValue) ? capValue : DEFAULT_SESSION_CAP_MINUTES));
    next.sessionCapMinutes = cap;
    const directValue = Number(next.directSessionMinutes);
    const directAlias = Number(next.sessionLimitMinutes);
    const rawDirect = Number.isFinite(directValue) ? directValue
      : (Number.isFinite(directAlias) ? directAlias : DEFAULT_DIRECT_MINUTES);
    const direct = Math.min(cap, Math.max(MIN_DIRECT_MINUTES, Math.round(rawDirect)));
    next.sessionLimitMinutes = direct;
    next.directSessionMinutes = direct;
    const xValue = Number(next.studyEarnStudyMinutes);
    const x = Math.min(MAX_STUDY_EARN_MINUTES, Math.max(MIN_STUDY_EARN_MINUTES,
      Number.isFinite(xValue) ? xValue : DEFAULT_STUDY_EARN_STUDY_MINUTES));
    next.studyEarnStudyMinutes = x;
    const yValue = Number(next.studyEarnRewardMinutes);
    const rawY = Number.isFinite(yValue) ? yValue : DEFAULT_STUDY_EARN_REWARD_MINUTES;
    next.studyEarnRewardMinutes = Math.min(cap,
      Math.max(MIN_STUDY_EARN_MINUTES, Math.round(rawY)));
    ["quizDailyLimitCount", "studyRewardDailyLimitCount", "directDailyFreeCount"]
      .forEach((field) => {
        const value = Number(next[field]);
        next[field] = Number.isFinite(value)
          ? Math.min(MAX_ROUTE_DAILY_COUNT, Math.max(0, Math.round(value)))
          : DEFAULT_ROUTE_DAILY_COUNT;
      });
    // V45: studyPackages is the single study list; the legacy per-app reward subset is
    // retired. Categories are exclusive, so a study app cannot also be unlimited or
    // entertainment (study > unlimited > entertainment).
    const study = Array.isArray(next.studyPackages) ? next.studyPackages : [];
    next.studyPackages = study;
    next.studyEarnPackages = study;
    const studySet = new Set(study);
    const unlimited = (Array.isArray(next.unlimitedPackages) ? next.unlimitedPackages : [])
      .filter((pkg) => !studySet.has(pkg));
    next.unlimitedPackages = unlimited;
    const unlimitedSet = new Set(unlimited);
    next.allowedPackages = (Array.isArray(next.allowedPackages) ? next.allowedPackages : [])
      .filter((pkg) => !studySet.has(pkg) && !unlimitedSet.has(pkg));
    return next;
  }

  function saveRules(next) {
    if (!editorToken) return false;
    // V42 rejects pages that cannot express day groups. Snapshot-driven drafts already carry
    // the version, but an old cached page must never pass by omitting it.
    next.rulesContractVersion = RULES_CONTRACT_VERSION;
    clampRules(next);
    snap.rules = next;
    pendingRules = next;
    rulesHold = false;
    clearNotices();
    if (!savingRules) {
      if (rulesTimer) clearTimeout(rulesTimer);
      rulesTimer = setTimeout(flushRules, RULES_DEBOUNCE_MS);
    }
    // The controls that made the edit already show the draft. Rebuilding the page here made a
    // held +/− flicker and jump; only an authoritative error path re-renders.
    return true;
  }

  async function flushRules() {
    rulesTimer = 0;
    if (!editorToken || !pendingRules) {
      releaseRulesProofIfIdle();
      return;
    }
    if (savingRules) return;
    const rules = pendingRules;
    pendingRules = null;
    savingRules = true;
    try {
      const data = await requestUserConfigWrite(
        "/api/parent/rules",
        {
          method: "POST",
          body: {
            expectedRulesRevision: snap.rulesRevision,
            rules: rules
          },
          editorToken: editorToken,
          quizAuth: true,
        }
      );
      if (!data) {
        // The parent closed the verification sheet. Keep the draft and wait for the next
        // deliberate edit instead of immediately asking for the PIN again.
        pendingRules = rules;
        rulesHold = true;
        return;
      }
      if (data.snapshot) adoptSnapshot(data.snapshot, Boolean(pendingRules));
      paintRulesControls();
      setNotice(successText(data.messageKey), false);
    } catch (error) {
      if (isSensitiveError(error)) {
        // A cancelled or rejected re-verification keeps the unsaved draft.
        pendingRules = rules;
        rulesHold = true;
        setNotice(errorText(error), true);
        return;
      }
      await recoverWrite(error);
    } finally {
      savingRules = false;
      if (pendingRules && editorToken && !rulesHold) {
        rulesTimer = setTimeout(flushRules, RULES_DEBOUNCE_MS);
      }
      releaseRulesProofIfIdle();
    }
  }

  function releaseRulesProofIfIdle() {
    if (!releaseRulesWhenIdle) return;
    if (savingRules || (pendingRules && !rulesHold)) return;
    releaseRulesWhenIdle = false;
    releaseSensitive("RULES_EDIT");
  }

  function discardPendingRules() {
    pendingRules = null;
    if (rulesTimer) { clearTimeout(rulesTimer); rulesTimer = 0; }
  }

  async function recoverWrite(error) {
    if (!error) return;
    if (error.code === "STATE_CHANGED" || error.code === "READ_ONLY" ||
        error.code === "LOCAL_ONLY" || error.code === "RULES_CLIENT_OUTDATED") {
      // The TV changed under us. The draft was built on a stale revision, so drop it and
      // show the authoritative snapshot; keeping it would immediately conflict again.
      discardPendingRules();
      const snapshot = error.data && error.data.snapshot;
      if (snapshot) adoptSnapshot(snapshot, false);
      else {
        try { await refreshSnapshot(); } catch (_) { /* keep the last view */ }
      }
      setNotice(errorText(error), true);
      render();
      return;
    }
    if (error.code === "QUIZ_TAKEN_OVER" || error.code === "LEASE_TAKEN_OVER" ||
        error.code === "EDITOR_REQUIRED" || error.code === "LEASE_EXPIRED") {
      handleLeaseError(error);
      return;
    }
    if (error.code === "PIN_REQUIRED" || error.code === "WRONG_PIN" ||
        error.code === "OLD_PIN_REQUIRED" || error.code === "SETUP_PIN_REQUIRED") {
      setNotice(errorText(error), true);
      return;
    }
    if (error.code === "RULES_OVERLAP" || error.code === "RULES_TOO_MANY_WINDOWS" ||
        error.code === "RULES_INVALID_APPS" || error.code === "RULES_INVALID_STUDY_APPS" ||
        error.code === "RULES_INVALID_STUDY_EARN" || error.code === "RULES_INVALID" ||
        error.code === "RULES_INVALID_DAY_GROUPS" ||
        error.code === "RULES_INVALID_UNLIMITED_APPS" ||
        error.code === "RULES_INVALID_ROUTE_COUNTS" ||
        error.code === "RULES_INVALID_DIRECT_SESSION" ||
        error.code === "RULES_INVALID_SESSION_CAP") {
      // Rejected rules cannot be fixed by retrying the same draft; fall back to the TV's
      // last accepted rules so the next edit starts from a valid base.
      discardPendingRules();
      const snapshot = error.data && error.data.snapshot;
      if (snapshot) adoptSnapshot(snapshot, false);
      else {
        try { await refreshSnapshot(); } catch (_) { /* keep the last view */ }
      }
      setNotice(errorText(error), true);
      render();
      return;
    }
    setNotice(errorText(error), true);
  }

  function adoptSnapshot(next, preserveRules) {
    if (!next) return;
    const pending = preserveRules ? pendingRules : null;
    const localRules = pending || (snap && snap.rules);
    snap = next;
    if (preserveRules && localRules) snap.rules = localRules;
  }

  // ---------------------------------------------------------------------- child mode

  async function startEnableChildMode() {
    clearNotices();
    await enableChildMode(null);
  }

  async function enableChildMode(pin) {
    clearNotices();
    try {
      const body = { enabled: true };
      if (snap.modeRevision) body.expectedModeRevision = snap.modeRevision;
      if (pin) body.pin = pin;
      const data = await host.request("/api/parent/child-mode", {
        method: "POST",
        body: body,
        editorToken: editorToken,
        quizAuth: true,
      });
      if (data.snapshot) adoptSnapshot(data.snapshot, false);
      if (data.recoveryCode) showRecovery(data.recoveryCode);
      else setNotice(successText(data.messageKey), false);
      render();
    } catch (error) {
      if (error && error.code === "SETUP_PIN_REQUIRED") {
        promptNewPin(async (pinValue) => {
          await enableChildMode(pinValue);
        });
        return;
      }
      await recoverWrite(error);
      render();
    }
  }

  function startDisableChildMode() {
    confirmSheet(t("childOffTitle"), t("childOffText"), t("childOffConfirm"), () => {
      disableChildMode(null);
    }, t("cancel"));
  }

  async function disableChildMode(pin) {
    clearNotices();
    try {
      const body = { enabled: false };
      if (snap.modeRevision) body.expectedModeRevision = snap.modeRevision;
      if (pin) body.pin = pin;
      const data = await host.request("/api/parent/child-mode", {
        method: "POST",
        body: body,
        editorToken: editorToken,
        quizAuth: true,
      });
      if (data.snapshot) adoptSnapshot(data.snapshot, false);
      setNotice(successText(data.messageKey), false);
      render();
    } catch (error) {
      if (error && (error.code === "PIN_REQUIRED" || error.code === "WRONG_PIN")) {
        promptPin(t("disablePinTitle"), t("disablePinLead"), async (pinValue, close) => {
          close();
          await disableChildMode(pinValue);
        });
        return;
      }
      await recoverWrite(error);
      render();
    }
  }

  function promptNewPin(onSave) {
    openSheet((sheet, close) => {
      sheet.appendChild(el("h2", null, t("childSetupPinTitle")));
      sheet.appendChild(el("p", null, t("childSetupPinLead")));
      const first = el("input", "parent-pin-input");
      first.type = "password";
      first.inputMode = "numeric";
      first.maxLength = 6;
      first.autocomplete = "new-password";
      const second = el("input", "parent-pin-input");
      second.type = "password";
      second.inputMode = "numeric";
      second.maxLength = 6;
      second.autocomplete = "new-password";
      const field1 = el("div", "parent-field");
      field1.appendChild(el("label", "parent-field-label", t("newPin")));
      field1.appendChild(first);
      const field2 = el("div", "parent-field");
      field2.appendChild(el("label", "parent-field-label", t("confirmPin")));
      field2.appendChild(second);
      sheet.appendChild(field1);
      sheet.appendChild(field2);
      const error = el("p", "parent-error hidden");
      sheet.appendChild(error);
      const actions = el("div", "parent-actions");
      const save = button(t("savePin"), "parent-btn primary", async () => {
        const a = first.value.replace(/\D/g, "");
        const b = second.value.replace(/\D/g, "");
        if (a.length !== 6) {
          error.textContent = t("pinLength");
          error.classList.remove("hidden");
          return;
        }
        if (a !== b) {
          error.textContent = t("pinMismatch");
          error.classList.remove("hidden");
          return;
        }
        save.disabled = true;
        try {
          close();
          await onSave(a);
        } catch (err) {
          setNotice(errorText(err), true);
          render();
        }
      });
      actions.appendChild(save);
      actions.appendChild(button(t("cancel"), "parent-btn ghost", close));
      sheet.appendChild(actions);
      setTimeout(() => first.focus(), 60);
    });
  }

  function showRecovery(code) {
    openSheet((sheet, close) => {
      sheet.appendChild(el("h2", null, t("recoveryTitle")));
      sheet.appendChild(el("p", null, t("recoveryText")));
      const box = el("div", "parent-card");
      const value = el("div", "parent-code-input parent-code-value", code);
      value.setAttribute("role", "text");
      box.appendChild(value);
      sheet.appendChild(box);
      const actions = el("div", "parent-actions");
      actions.appendChild(button(t("recoveryDone"), "parent-btn primary", close));
      sheet.appendChild(actions);
    });
  }

  // ------------------------------------------------------------------------ password

  function renderPassword() {
    const root = $(ROOT_ID);
    clear(root);
    const shell = el("div", "parent-shell");
    shell.appendChild(barNode(t("parentPassword"), statusLine(gate), () => {
      subpage = null;
      render();
    }));
    const content = el("div", "parent-content");
    content.appendChild(el("p", "parent-hint", t("passwordHint")));
    const card = el("div", "parent-card");
    const oldField = pinField(t("currentPin"), "parentOldPin");
    const newField = pinField(t("newPin"), "parentNewPin");
    const confirmField = pinField(t("confirmPin"), "parentConfirmPin");
    card.appendChild(oldField.wrap);
    card.appendChild(newField.wrap);
    card.appendChild(confirmField.wrap);
    content.appendChild(card);
    const error = el("p", "parent-error hidden");
    error.id = "parentPasswordError";
    content.appendChild(error);
    const actions = el("div", "parent-actions");
    const save = button(t("savePin"), "parent-btn primary", async () => {
      const oldPin = oldField.input.value.replace(/\D/g, "");
      const newPin = newField.input.value.replace(/\D/g, "");
      const confirm = confirmField.input.value.replace(/\D/g, "");
      error.classList.add("hidden");
      if (oldPin.length !== 6 || newPin.length !== 6) {
        error.textContent = t("pinLength");
        error.classList.remove("hidden");
        return;
      }
      if (oldPin === newPin) {
        error.textContent = t("pinUnchanged");
        error.classList.remove("hidden");
        return;
      }
      if (newPin !== confirm) {
        error.textContent = t("pinMismatch");
        error.classList.remove("hidden");
        return;
      }
      save.disabled = true;
      try {
        const data = await host.request("/api/parent/pin", {
          method: "POST",
          body: { oldPin: oldPin, newPin: newPin },
          editorToken: editorToken,
          quizAuth: true,
        });
        if (data.snapshot) adoptSnapshot(data.snapshot, false);
        setNotice(successText(data.messageKey), false);
        subpage = null;
        render();
      } catch (err) {
        error.textContent = errorText(err);
        error.classList.remove("hidden");
      } finally {
        save.disabled = false;
      }
    });
    actions.appendChild(save);
    actions.appendChild(button(t("cancel"), "parent-btn ghost", () => {
      subpage = null;
      render();
    }));
    content.appendChild(actions);
    shell.appendChild(content);
    root.appendChild(shell);
  }

  function pinField(labelText, id) {
    const wrap = el("div", "parent-field parent-field-inset");
    const label = el("label", "parent-field-label", labelText);
    label.setAttribute("for", id);
    const input = el("input", "parent-pin-input");
    input.id = id;
    input.type = "password";
    input.inputMode = "numeric";
    input.maxLength = 6;
    wrap.appendChild(label);
    wrap.appendChild(input);
    return { wrap, input };
  }

  // ---------------------------------------------------------------------- app picker

  // App icons come from the TV's authenticated endpoint. Load them lazily as the grid scrolls
  // and cache the object URLs for the whole page, so re-opening the picker or searching does
  // not ask the TV for the same icon again.
  const APP_ICON_CONCURRENCY = 4;
  const appIconUrls = new Map();
  const appIconWaiters = new Map();
  const appIconQueued = new Set();
  const appIconInFlight = new Set();
  let appIconQueue = [];
  let appIconActive = 0;
  let appIconObserver = null;
  let appIconEpoch = 0;

  function newAppIconObserver(scrollRoot) {
    if (typeof IntersectionObserver !== "function") return null;
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        observer.unobserve(entry.target);
        startAppIcon(entry.target.dataset.parentIcon || "");
      });
    }, { root: scrollRoot || null, rootMargin: "240px 0px" });
    return observer;
  }

  function requestAppIcon(pkg, holder) {
    if (!pkg || !holder) return;
    holder.dataset.parentIcon = pkg;
    const cached = appIconUrls.get(pkg);
    if (cached) {
      paintAppIcon(holder, cached);
      return;
    }
    let waiters = appIconWaiters.get(pkg);
    if (!waiters) {
      waiters = new Set();
      appIconWaiters.set(pkg, waiters);
    }
    waiters.add(holder);
    if (appIconObserver) appIconObserver.observe(holder);
    else startAppIcon(pkg);
  }

  function startAppIcon(pkg) {
    if (!pkg || appIconUrls.has(pkg) || appIconQueued.has(pkg) || appIconInFlight.has(pkg)) return;
    appIconQueued.add(pkg);
    appIconQueue.push(pkg);
    pumpAppIcons();
  }

  function pumpAppIcons() {
    while (appIconActive < APP_ICON_CONCURRENCY && appIconQueue.length) {
      const pkg = appIconQueue.shift();
      appIconQueued.delete(pkg);
      if (appIconUrls.has(pkg)) {
        paintAppIconWaiters(pkg);
        continue;
      }
      appIconActive += 1;
      appIconInFlight.add(pkg);
      const epoch = appIconEpoch;
      host.blob("/api/parent/icon?package=" + encodeURIComponent(pkg), editorToken)
        .then((blob) => {
          if (epoch !== appIconEpoch) return;
          appIconUrls.set(pkg, URL.createObjectURL(blob));
          paintAppIconWaiters(pkg);
        })
        .catch(() => { /* keep the letter fallback */ })
        .finally(() => {
          appIconInFlight.delete(pkg);
          if (epoch !== appIconEpoch) return;
          appIconActive = Math.max(0, appIconActive - 1);
          pumpAppIcons();
        });
    }
  }

  function paintAppIconWaiters(pkg) {
    const url = appIconUrls.get(pkg);
    const waiters = appIconWaiters.get(pkg);
    if (!url || !waiters) return;
    waiters.forEach((holder) => {
      if (holder.isConnected) paintAppIcon(holder, url);
    });
    appIconWaiters.delete(pkg);
  }

  function paintAppIcon(holder, url) {
    clear(holder);
    holder.classList.add("parent-app-icon-loaded");
    const img = el("img", "parent-app-icon-img");
    img.src = url;
    img.alt = "";
    img.decoding = "async";
    holder.appendChild(img);
  }

  function resetAppIconQueue() {
    appIconQueue = [];
    appIconQueued.clear();
    if (appIconObserver) {
      appIconObserver.disconnect();
      appIconObserver = null;
    }
  }

  function revokeAppIcons() {
    appIconEpoch += 1;
    appIconUrls.forEach((url) => URL.revokeObjectURL(url));
    appIconUrls.clear();
    appIconWaiters.clear();
    appIconInFlight.clear();
    appIconActive = 0;
    resetAppIconQueue();
  }

  function exitPicker() {
    resetAppIconQueue();
    const returnTo = picker && picker.returnTo;
    const scroll = picker ? picker.scroll : 0;
    picker = null;
    // V45: a picker remembers where it was opened. The unlimited card returns to the user
    // index; the entertainment and study grids return to the plan page.
    if (returnTo === "user") {
      subpage = null;
      if (pendingRules || savingRules) {
        releaseRulesWhenIdle = true;
        if (pendingRules && !savingRules) flushRules();
      } else {
        releaseSensitive("RULES_EDIT");
      }
    } else {
      subpage = "time";
    }
    render();
    window.scrollTo({ top: scroll || 0 });
  }

  function pickerPackages(kind) {
    const rules = snap.rules || {};
    if (kind === "study") return Array.isArray(rules.studyPackages) ? rules.studyPackages : [];
    if (kind === "unlimited") {
      return Array.isArray(rules.unlimitedPackages) ? rules.unlimitedPackages : [];
    }
    return !rules.allApps && Array.isArray(rules.allowedPackages)
      ? rules.allowedPackages : [];
  }

  function pickerTitle() {
    if (picker.kind === "study") return t("appPickerStudy");
    if (picker.kind === "unlimited") return t("appPickerUnlimited");
    return t("appPickerEntertainment");
  }

  function pickerHint() {
    if (picker.kind === "study") return t("appPickerStudyHint");
    if (picker.kind === "unlimited") return t("appPickerUnlimitedHint");
    return t("appPickerHint");
  }

  /**
   * The entertainment picker locks the other two categories. The unlimited and study
   * pickers take the app with a confirmation instead, because V45 must let the parent move
   * an app between categories without first removing it somewhere else.
   */
  function pickerConflict(pkg) {
    if (picker.kind !== "entertainment") return null;
    const rules = snap.rules || {};
    if ((rules.studyPackages || []).indexOf(pkg) >= 0) return "study";
    if ((rules.unlimitedPackages || []).indexOf(pkg) >= 0) return "unlimited";
    return null;
  }

  function pickerCategory(pkg) {
    const rules = snap.rules || {};
    if (picker.kind === "unlimited" &&
      (rules.studyPackages || []).indexOf(pkg) >= 0) return "study";
    if (picker.kind === "study" &&
      (rules.unlimitedPackages || []).indexOf(pkg) >= 0) return "unlimited";
    return null;
  }

  /** Batch candidates never include study apps: adding those must stay a deliberate move. */
  function unlimitedCandidates() {
    const study = new Set(studyPackagesAll());
    return (picker.apps || [])
      .map((app) => String(app.packageName || ""))
      .filter((pkg) => pkg && !study.has(pkg));
  }

  function unlimitedAllSelected(candidates) {
    return candidates.length > 0 && candidates.every((pkg) => picker.selected.has(pkg));
  }

  async function openPicker(kind, returnTo) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    subpage = "apps";
    picker = {
      kind: kind,
      returnTo: returnTo === "user" ? "user" : "time",
      scroll: window.scrollY,
      apps: null,
      query: "",
      loading: true,
      allApps: Boolean(snap.rules && snap.rules.allApps),
      selected: new Set(pickerPackages(kind)),
    };
    render();
    try {
      const data = await host.request("/api/parent/apps", {
        editorToken: editorToken,
        quizAuth: true,
      });
      picker.apps = Array.isArray(data.apps) ? data.apps : [];
    } catch (error) {
      picker.apps = [];
      setNotice(errorText(error), true);
    }
    picker.loading = false;
    if (subpage === "apps") render();
  }

  function renderAppPicker() {
    const root = $(ROOT_ID);
    clear(root);
    const shell = el("div", "parent-shell parent-picker-shell");
    shell.appendChild(barNode(pickerTitle(), statusLine(gate), exitPicker));
    const content = el("div", "parent-subpage parent-picker-content");
    content.appendChild(noticeArea());
    const search = el("input", "parent-search");
    search.type = "search";
    search.placeholder = t("appPickerSearch");
    search.setAttribute("aria-label", t("appPickerSearch"));
    search.value = picker.query;
    search.addEventListener("input", () => {
      picker.query = search.value.trim().toLowerCase();
      renderAppList(list);
    });
    content.appendChild(search);
    const list = el("div", "parent-list parent-list-spaced");
    content.appendChild(list);
    content.appendChild(el("p", "parent-hint", pickerHint()));
    renderAppList(list);
    shell.appendChild(content);
    const actions = el("div", "parent-sticky-actions");
    actions.appendChild(button(t("appPickerCancel"), "parent-btn ghost", exitPicker));
    const done = button(t("appPickerDone"), "parent-btn primary", () => {
      savePicker();
    });
    actions.appendChild(done);
    shell.appendChild(actions);
    root.appendChild(shell);
  }

  function renderAppList(list) {
    clear(list);
    resetAppIconQueue();
    if (!picker) return;
    appIconObserver = newAppIconObserver(list);
    if (picker.loading) {
      list.appendChild(el("div", "parent-empty", t("appPickerLoading")));
      return;
    }
    if (picker.kind === "entertainment") {
      const all = el("label", "parent-radio");
      const radio = el("input");
      radio.type = "radio";
      radio.name = "parentAppScope";
      radio.checked = picker.allApps;
      radio.addEventListener("change", () => {
        picker.allApps = true;
        renderAppList(list);
      });
      all.appendChild(radio);
      all.appendChild(el("span", "parent-app-label", t("appPickerAll")));
      list.appendChild(all);
      const only = el("label", "parent-radio");
      const radioOnly = el("input");
      radioOnly.type = "radio";
      radioOnly.name = "parentAppScope";
      radioOnly.checked = !picker.allApps;
      radioOnly.addEventListener("change", () => {
        picker.allApps = false;
        renderAppList(list);
      });
      only.appendChild(radioOnly);
      only.appendChild(el("span", "parent-app-label", t("appPickerOnly")));
      list.appendChild(only);
    }
    const apps = (picker.apps || []).filter((app) => {
      if (!picker.query) return true;
      const label = String(app.label || "").toLowerCase();
      const pkg = String(app.packageName || "").toLowerCase();
      return label.indexOf(picker.query) >= 0 || pkg.indexOf(picker.query) >= 0;
    });
    if (!apps.length) {
      list.appendChild(el("div", "parent-empty", t("appPickerEmpty")));
      return;
    }
    const grid = el("div", "parent-app-grid");
    if (picker.kind === "unlimited") {
      // V45: a virtual "select all" tile, never a real package. It only batches apps that
      // may join unlimited without moving a study app; those stay explicit choices.
      const candidates = unlimitedCandidates();
      const allSelected = unlimitedAllSelected(candidates);
      const selectAll = el("button", "parent-app-tile parent-select-all" +
        (allSelected ? " selected" : ""));
      selectAll.type = "button";
      selectAll.setAttribute("aria-label",
        allSelected ? t("clearAllApps") : t("selectAllApps"));
      const allIcon = el("span", "parent-app-icon parent-select-all-icon");
      for (let i = 0; i < 4; i += 1) {
        allIcon.appendChild(el("span", "parent-select-all-dot"));
      }
      selectAll.appendChild(allIcon);
      selectAll.appendChild(el("span", "parent-app-name",
        allSelected ? t("clearAllApps") : t("selectAllApps")));
      selectAll.addEventListener("click", () => {
        const now = unlimitedCandidates();
        if (unlimitedAllSelected(now)) {
          now.forEach((pkg) => picker.selected.delete(pkg));
        } else {
          now.forEach((pkg) => picker.selected.add(pkg));
        }
        renderAppList(list);
      });
      grid.appendChild(selectAll);
    }
    apps.slice(0, 300).forEach((app) => {
      const pkg = String(app.packageName || "");
      const labelText = String(app.label || pkg);
      const conflict = pickerConflict(pkg);
      const category = pickerCategory(pkg);
      const lockedByAllApps = picker.kind === "entertainment" && picker.allApps;
      const tile = el("label", "parent-app-tile");
      tile.dataset.parentIcon = pkg;
      const check = el("input");
      check.type = "checkbox";
      check.checked = picker.selected.has(pkg);
      check.disabled = lockedByAllApps || Boolean(conflict);
      const icon = el("span", "parent-app-icon");
      icon.appendChild(el("span", "parent-app-initial",
        (labelText.trim()[0] || "?").toUpperCase()));
      const name = el("span", "parent-app-name", labelText);
      const mark = el("span", "parent-app-check", "✓");
      mark.setAttribute("aria-hidden", "true");
      const sync = () => tile.classList.toggle("selected", check.checked);
      sync();
      if (check.disabled) tile.classList.add("disabled");
      if (conflict) {
        tile.classList.add("conflict");
        tile.appendChild(el("span", "parent-app-note",
          conflict === "study" ? t("appPickerTakenStudy") : t("appPickerTakenUnlimited")));
      } else if (category) {
        tile.classList.add("category-move");
        tile.appendChild(el("span", "parent-app-note",
          category === "study" ? t("appPickerTakenStudy") : t("appPickerTakenUnlimited")));
      }
      check.addEventListener("change", () => {
        if (check.checked && category) {
          // Moving an app between categories is allowed but never silent: the confirmation
          // spells out which list loses it, and Save applies the move.
          check.checked = false;
          sync();
          confirmSheet(
            labelText,
            t(category === "study" ? "moveToUnlimitedConfirm" : "moveToStudyConfirm"),
            t("moveToUnlimitedAction"),
            () => {
              picker.selected.add(pkg);
              check.checked = true;
              sync();
            }
          );
          return;
        }
        if (check.checked) picker.selected.add(pkg);
        else picker.selected.delete(pkg);
        sync();
      });
      tile.appendChild(check);
      tile.appendChild(icon);
      tile.appendChild(name);
      tile.appendChild(mark);
      grid.appendChild(tile);
      requestAppIcon(pkg, icon);
    });
    list.appendChild(grid);
  }

  async function savePicker() {
    if (!picker || !snap.capabilities || !snap.capabilities.canEditRules) return;
    const next = clone(snap.rules);
    next.rulesContractVersion = RULES_CONTRACT_VERSION;
    const selected = Array.from(picker.selected);
    if (picker.kind === "study") {
      next.studyPackages = selected;
      // V45: every study app participates; the retired per-app reward subset always matches.
      next.studyEarnPackages = selected;
      next.unlimitedPackages = (next.unlimitedPackages || [])
        .filter((pkg) => picker.selected.has(pkg) === false);
      if (!next.allApps) {
        next.allowedPackages = (next.allowedPackages || [])
          .filter((pkg) => picker.selected.has(pkg) === false);
      }
    } else if (picker.kind === "unlimited") {
      next.unlimitedPackages = selected;
      next.studyPackages = (next.studyPackages || [])
        .filter((pkg) => picker.selected.has(pkg) === false);
      next.studyEarnPackages = next.studyPackages;
      if (!next.allApps) {
        next.allowedPackages = (next.allowedPackages || [])
          .filter((pkg) => picker.selected.has(pkg) === false);
      }
      const installed = (picker.apps || [])
        .map((app) => String(app.packageName || ""))
        .filter(Boolean);
      const coversAll = installed.length > 0 &&
        installed.every((pkg) => picker.selected.has(pkg));
      if (coversAll) {
        // An empty entertainment scope is legal, but the parent must see the consequence.
        confirmSheet(
          t("unlimitedApps"),
          t("unlimitedAllWarning"),
          t("appPickerDone"),
          () => { commitPicker(next); }
        );
        return;
      }
    } else {
      next.allApps = picker.allApps;
      next.allowedPackages = picker.allApps ? [] : selected;
    }
    commitPicker(next);
  }

  async function commitPicker(next) {
    try {
      const data = await requestUserConfigWrite(
        "/api/parent/rules",
        {
          method: "POST",
          body: {
            expectedRulesRevision: snap.rulesRevision,
            rules: next
          },
          editorToken: editorToken,
          quizAuth: true,
        }
      );
      if (!data) return;
      if (data.snapshot) adoptSnapshot(data.snapshot, false);
      setNotice(successText(data.messageKey), false);
      exitPicker();
    } catch (error) {
      if (isSensitiveError(error)) {
        setNotice(errorText(error), true);
        return;
      }
      await recoverWrite(error);
      render();
    }
  }

  // ------------------------------------------------------------------ V31 control page

  function controlData() {
    const value = snap && snap.control;
    return value && typeof value === "object" ? value : null;
  }

  function adoptControl(next) {
    if (!next || typeof next !== "object") return;
    if (!snap) snap = {};
    snap.control = next;
  }

  function fmtSeconds(value) {
    const total = Math.max(0, Math.round(Number(value) || 0));
    const m = Math.floor(total / 60);
    const s = total % 60;
    return String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0");
  }

  function controlStatusText(control, manualPage) {
    if (!control) return t("loading");
    if (!control.childModeOn) return t("controlStatusChildOff");
    if (control.guardState === "PARENT_OVERRIDE") {
      const left = Math.max(0, Number(control.parentUnlockRemainingSeconds) || 0);
      return left > 0
        ? t("controlStatusOverride", { time: fmtSeconds(left) })
        : t("controlStatusOverrideNoLimit");
    }
    if (control.guardState === "STUDY_MODE") return t("controlStatusStudy");
    if (control.quiz && control.quiz.roundActive) return t("controlStatusQuiz");
    const timer = control.timer || {};
    if (control.sessionState === "ACTIVE" || timer.active) {
      // V45: only the user config page starts out-of-plan timers, so it labels that state
      // differently from a child-started entertainment session.
      if (manualPage && timer.outsidePlan) {
        const minutes = Math.max(1, Math.ceil((Number(timer.remainingSeconds) || 0) / 60));
        return t("controlStatusManualTimer", { minutes: minutes });
      }
      return t("controlStatusTimer", { time: fmtSeconds(timer.remainingSeconds) });
    }
    if (control.sessionState === "PENDING") return t("controlStatusPending");
    if (Number(timer.restRemainingSeconds) > 0) {
      return t("controlStatusRest", { time: fmtSeconds(timer.restRemainingSeconds) });
    }
    return t("controlStatusEnforcing");
  }

  function controlAction(control, name) {
    const actions = control && control.actions;
    const action = actions && actions[name];
    return action && typeof action === "object"
      ? action
      : { enabled: false, requiresConfirm: false, reason: "" };
  }

  function controlTimerLeft(control) {
    const timer = control && control.timer;
    return timer ? Math.max(0, Number(timer.remainingSeconds) || 0) : 0;
  }

  // ---------------------------------------------------------- control: device status

  function formatDeviceBytes(value) {
    const bytes = Math.max(0, Number(value) || 0);
    if (bytes < 1024) return bytes + " B";
    const units = ["KB", "MB", "GB", "TB"];
    let amount = bytes / 1024;
    let unit = 0;
    while (amount >= 1024 && unit < units.length - 1) {
      amount /= 1024;
      unit += 1;
    }
    const digits = amount >= 100 ? 0 : amount >= 10 ? 1 : 2;
    return amount.toFixed(digits) + " " + units[unit];
  }

  function formatDeviceDuration(value) {
    const total = Math.max(0, Math.round(Number(value) || 0));
    const days = Math.floor(total / 86400);
    const hours = Math.floor((total % 86400) / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    if (days > 0) return t("controlDeviceUptimeDays", { d: days, h: hours });
    if (hours > 0) return t("controlDeviceUptimeHours", { h: hours, m: minutes });
    return t("controlDeviceUptimeMinutes", { m: minutes });
  }

  function devicePercent(part, total) {
    const whole = Number(total) || 0;
    if (whole <= 0) return 0;
    return Math.min(100, Math.max(0, Math.round((Number(part) || 0) / whole * 100)));
  }

  function deviceBoolean(value) {
    return value === true ? true : value === false ? false : null;
  }

  function deviceOnOff(value) {
    const flag = deviceBoolean(value);
    return flag === null ? t("controlPermUnknown") : flag ? t("controlPermOn") : t("controlPermOff");
  }

  function deviceFlagState(value) {
    const flag = deviceBoolean(value);
    return flag === null ? "" : flag ? "ok" : "warn";
  }

  function stopControlDevicePolling() {
    if (controlDeviceTimer) {
      clearInterval(controlDeviceTimer);
      controlDeviceTimer = 0;
    }
  }

  function resetControlDevice() {
    stopControlDevicePolling();
    controlDeviceEpoch += 1;
    controlDeviceOpen = false;
    controlDeviceData = null;
    controlDeviceLoading = false;
    controlDeviceError = false;
    controlDeviceRefs = null;
  }

  function devicePageActive() {
    return rootOpen && !rootSuspended && rootMode === "system" && subpage === "device";
  }

  function syncControlDevicePolling() {
    stopControlDevicePolling();
    if (!controlDeviceOpen || !devicePageActive()) return;
    controlDeviceTimer = setInterval(() => {
      if (document.hidden) return;
      loadControlDevice();
    }, 5000);
  }

  /**
   * Samples the TV again. This goes through the same editor lease as the snapshot; the card
   * polls only while it is expanded, and a transient poll failure keeps the last reading
   * visible instead of blanking the panel.
   */
  async function loadControlDevice() {
    if (!host || !editorToken || controlDeviceLoading || !devicePageActive()) {
      return;
    }
    const epoch = controlDeviceEpoch;
    controlDeviceLoading = true;
    controlDeviceError = false;
    paintControlDevice();
    try {
      const data = await host.request("/api/parent/device", {
        editorToken: editorToken,
        quizAuth: true,
      });
      if (epoch !== controlDeviceEpoch) return;
      if (data && data.device && typeof data.device === "object") {
        controlDeviceData = data.device;
      } else {
        controlDeviceError = true;
      }
    } catch (error) {
      if (isLeaseError(error)) {
        handleLeaseError(error);
        return;
      }
      if (epoch !== controlDeviceEpoch) return;
      controlDeviceError = true;
    } finally {
      if (epoch === controlDeviceEpoch) {
        controlDeviceLoading = false;
        paintControlDevice();
      }
    }
  }

  function deviceGroupTitle(text, tag) {
    const row = el("div", "parent-device-group-title");
    row.appendChild(el("span", null, text));
    if (tag) row.appendChild(el("span", "parent-device-tag", tag));
    return row;
  }

  function devicePermissionRow(label, value, state) {
    const row = el("div", "parent-device-item" + (state ? " is-" + state : ""));
    row.appendChild(el("span", "parent-device-state" + (state ? " is-" + state : "")));
    row.appendChild(el("span", "parent-device-label", label));
    row.appendChild(el("span", "parent-device-value", value));
    return row;
  }

  function deviceKv(label, value, warn) {
    const row = el("div", "parent-kv" + (warn ? " is-warn" : ""));
    row.appendChild(el("span", null, label));
    row.appendChild(el("strong", null, value));
    return row;
  }

  /**
   * The single setup verdict. The server decides it from the same six rows the card shows;
   * the legacy fallback only matters if an old payload ever reaches a new page.
   */
  function deviceSetup(status) {
    const value = status && typeof status === "object" ? status : {};
    if (typeof value.setupComplete === "boolean") {
      const missing = Math.max(0, Number(value.setupMissing) || 0);
      const unknown = Math.max(0, Number(value.setupUnknown) || 0);
      return {
        state: value.setupComplete ? "ok" : missing > 0 ? "warn" : "",
        done: value.setupComplete === true,
        missing: missing,
        unknown: unknown
      };
    }
    if (value.healthy === true) return { state: "ok", done: true, missing: 0, unknown: 0 };
    const issues = Math.max(0, Number(value.issues) || 0);
    return { state: issues > 0 ? "warn" : "", done: false, missing: issues, unknown: 0 };
  }

  function deviceSetupBanner(status) {
    const setup = deviceSetup(status);
    const banner = el("div", "parent-device-setup");
    let text = t("controlDeviceSetupUnknown");
    if (setup.done) {
      banner.classList.add("is-done");
      text = t("controlDeviceSetupDone");
    } else if (setup.missing > 0) {
      banner.classList.add("is-missing");
      text = t("controlDeviceSetupMissing", { n: setup.missing });
    } else {
      banner.classList.add("is-unknown");
    }
    banner.appendChild(el("span", "parent-device-setup-mark"));
    banner.appendChild(el("span", "parent-device-setup-text", text));
    return banner;
  }

  function devicePermissions(container, status) {
    container.appendChild(deviceGroupTitle(t("controlDevicePermissions")));
    container.appendChild(deviceSetupBanner(status));
    container.appendChild(el("p", "parent-device-note", t("controlDevicePermissionsHint")));

    const homeDefault = deviceBoolean(status.homeDefault);
    if (homeDefault === true) {
      container.appendChild(devicePermissionRow(
        t("controlPermHome"),
        t("controlPermHomeDefault"),
        "ok"
      ));
    } else if (status.homeReady === true) {
      container.appendChild(devicePermissionRow(
        t("controlPermHome"),
        t("controlPermHomeGuarded"),
        "ok"
      ));
    } else if (homeDefault === false) {
      container.appendChild(devicePermissionRow(
        t("controlPermHome"),
        t("controlPermHomeMissing"),
        "warn"
      ));
    } else {
      container.appendChild(devicePermissionRow(
        t("controlPermHome"),
        t("controlPermUnknown"),
        ""
      ));
    }

    const guardValue = status.guardRunning === true
      ? t("controlPermRunning")
      : (!status.accessibilityOn || !status.guardListed)
        ? t("controlPermOff")
        : t("controlPermNotRunning");
    container.appendChild(devicePermissionRow(
      t("controlPermGuard"),
      guardValue,
      status.guardRunning === true ? "ok" : "warn"
    ));
    container.appendChild(devicePermissionRow(
      t("controlPermSelfHeal"),
      deviceOnOff(status.selfHeal),
      deviceFlagState(status.selfHeal)
    ));
    container.appendChild(devicePermissionRow(
      t("controlPermRevival"),
      deviceOnOff(status.revival),
      deviceFlagState(status.revival)
    ));
    container.appendChild(devicePermissionRow(
      t("controlPermUsage"),
      deviceOnOff(status.usageAccess),
      deviceFlagState(status.usageAccess)
    ));
    container.appendChild(devicePermissionRow(
      t("controlPermOverlay"),
      deviceOnOff(status.overlay),
      deviceFlagState(status.overlay)
    ));
  }

  function deviceSystem(container, system, appInfo, storage) {
    container.appendChild(deviceGroupTitle(t("controlDeviceSystem")));

    const manufacturer = String(system.manufacturer || "").trim();
    const model = String(system.model || "").trim();
    const modelText = model && manufacturer &&
      model.toLowerCase().indexOf(manufacturer.toLowerCase()) !== 0
      ? manufacturer + " " + model
      : model || manufacturer || "—";
    container.appendChild(deviceKv(t("controlDeviceModel"), modelText));

    const release = String(system.android || "").trim();
    const sdk = Number(system.sdk) || 0;
    container.appendChild(deviceKv(
      t("controlDeviceAndroid"),
      release ? "Android " + release + (sdk > 0 ? " · SDK " + sdk : "") : "—"
    ));
    container.appendChild(deviceKv(
      t("controlDeviceSecurityPatch"),
      String(system.securityPatch || "").trim() || "—"
    ));
    const adb = deviceBoolean(system.adbEnabled);
    container.appendChild(deviceKv(
      t("controlDeviceAdb"),
      adb === null ? t("controlPermUnknown") : adb ? t("controlPermOn") : t("controlPermOff")
    ));
    container.appendChild(deviceKv(
      t("controlDeviceKernel"),
      String(system.kernel || "").trim() || "—"
    ));

    const cpu = [];
    if (system.abi) cpu.push(String(system.abi));
    const cores = Number(system.cores) || 0;
    if (cores > 0) cpu.push(t("controlDeviceCoreCount", { n: cores }));
    container.appendChild(deviceKv(t("controlDeviceCpu"), cpu.join(" · ") || "—"));
    container.appendChild(deviceKv(
      t("controlDeviceSystemUptime"),
      formatDeviceDuration(system.uptimeSeconds)
    ));

    const versionName = String(appInfo.versionName || "").trim();
    const versionCode = Number(appInfo.versionCode) || 0;
    container.appendChild(deviceKv(
      t("controlDeviceAppVersion"),
      versionName ? versionName + (versionCode > 0 ? " (" + versionCode + ")" : "") : "—"
    ));
    if (appInfo.processUptimeSeconds !== null && appInfo.processUptimeSeconds !== undefined) {
      container.appendChild(deviceKv(
        t("controlDeviceProcessUptime"),
        formatDeviceDuration(appInfo.processUptimeSeconds)
      ));
    }

    const storageTotal = Number(storage.totalBytes) || 0;
    container.appendChild(deviceKv(
      t("controlDeviceStorageFree"),
      storageTotal > 0
        ? formatDeviceBytes(storage.availBytes) + " / " + formatDeviceBytes(storageTotal)
        : "—"
    ));
  }

  function deviceMemory(container, memory, system) {
    container.appendChild(deviceGroupTitle(
      t("controlDeviceMemory"),
      system.lowRamDevice === true ? t("controlDeviceLowRam") : ""
    ));

    const total = Number(memory.totalBytes) || 0;
    const available = Number(memory.availBytes) || 0;
    const used = Number(memory.usedBytes) || 0;
    const percent = devicePercent(used, total);
    const warn = memory.lowMemory === true || percent >= 85;

    const head = el("div", "parent-device-memory-head");
    head.appendChild(el("span", null, t("controlDeviceRamUsed")));
    head.appendChild(el("strong", null,
      total > 0 ? formatDeviceBytes(used) + " / " + formatDeviceBytes(total) : "—"));
    container.appendChild(head);

    const bar = el("div", "parent-device-bar" + (warn ? " is-warn" : ""));
    const fill = el("span", null, "");
    fill.style.width = percent + "%";
    bar.appendChild(fill);
    container.appendChild(bar);
    container.appendChild(el("div", "parent-device-meta", percent + "%"));

    container.appendChild(deviceKv(
      t("controlDeviceRamAvail"),
      total > 0 ? formatDeviceBytes(available) : "—"
    ));
    container.appendChild(deviceKv(
      t("controlDeviceAppPss"),
      Number(memory.appPssBytes) > 0 ? formatDeviceBytes(memory.appPssBytes) : "—"
    ));
    container.appendChild(deviceKv(
      t("controlDeviceHeap"),
      Number(memory.heapMaxBytes) > 0
        ? formatDeviceBytes(memory.heapUsedBytes) + " / " + formatDeviceBytes(memory.heapMaxBytes)
        : "—"
    ));
    container.appendChild(deviceKv(
      t("controlDeviceThreshold"),
      Number(memory.thresholdBytes) > 0 ? formatDeviceBytes(memory.thresholdBytes) : "—"
    ));
    container.appendChild(deviceKv(
      t("controlDeviceMemoryState"),
      memory.lowMemory === true ? t("controlDeviceMemoryLow") : t("controlDeviceMemoryNormal"),
      memory.lowMemory === true
    ));
  }

  function paintControlDevice() {
    const refs = controlDeviceRefs;
    if (!refs || !devicePageActive()) return;
    const data = controlDeviceData;
    let summaryKey = "controlDeviceSummaryHint";
    let state = "";
    let summaryMissing = 0;
    if (controlDeviceLoading && !data) {
      summaryKey = "controlDeviceSummaryLoading";
    } else if (!data && controlDeviceError) {
      summaryKey = "controlDeviceSummaryError";
      state = "warn";
    } else if (data) {
      const setup = deviceSetup(data.status);
      if (setup.done) {
        summaryKey = "controlDeviceSetupDone";
        state = "ok";
      } else if (setup.missing > 0) {
        summaryKey = "controlDeviceSummaryIssue";
        state = "warn";
        summaryMissing = setup.missing;
      } else {
        summaryKey = "controlDeviceSummaryUnknown";
      }
    }
    const summary = summaryKey === "controlDeviceSummaryIssue"
      ? t(summaryKey, { n: summaryMissing })
      : t(summaryKey);
    refs.summary.textContent = summary;
    refs.dot.className = "parent-device-dot" + (state ? " is-" + state : "");
    refs.head.classList.toggle("is-ok", state === "ok");
    refs.head.setAttribute("aria-expanded", controlDeviceOpen ? "true" : "false");
    refs.body.hidden = !controlDeviceOpen;
    clear(refs.body);
    if (!controlDeviceOpen) return;

    if (!data) {
      refs.body.appendChild(el(
        "p",
        "parent-device-empty",
        controlDeviceLoading
          ? t("controlDeviceSummaryLoading")
          : controlDeviceError
            ? t("controlDeviceReadFailed")
            : t("controlDeviceSummaryHint")
      ));
    } else {
      const status = data.status && typeof data.status === "object" ? data.status : {};
      const system = data.system && typeof data.system === "object" ? data.system : {};
      const memory = data.memory && typeof data.memory === "object" ? data.memory : {};
      const appInfo = data.app && typeof data.app === "object" ? data.app : {};
      const storage = data.storage && typeof data.storage === "object" ? data.storage : {};
      devicePermissions(refs.body, status);
      deviceSystem(refs.body, system, appInfo, storage);
      deviceMemory(refs.body, memory, system);
      if (controlDeviceError) {
        refs.body.appendChild(el(
          "p",
          "parent-device-hint",
          t("controlDeviceStale")
        ));
      }
    }

    const refresh = button(
      controlDeviceLoading ? t("controlDeviceRefreshing") : t("controlDeviceRecheck"),
      "parent-device-refresh",
      () => loadControlDevice()
    );
    refresh.disabled = controlDeviceLoading;
    refs.body.appendChild(refresh);
  }

  function controlActionRow(title, onClick, danger) {
    const node = el("button", "parent-row parent-control-row" + (danger ? " is-danger" : ""));
    node.type = "button";
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", title));
    const sub = el("span", "parent-row-sub", "");
    main.appendChild(sub);
    node.appendChild(main);
    node.appendChild(el("span", "chevron", "›"));
    node.addEventListener("click", onClick);
    return { button: node, sub: sub };
  }

  function paintControl() {
    const refs = controlRefs;
    if (!refs || !rootOpen || rootSuspended) return;
    const control = controlData();
    refs.statusText.textContent = controlStatusText(control, Boolean(refs.startManual));
    refs.dot.classList.toggle(
      "is-warn",
      Boolean(control && (control.guardState === "PARENT_OVERRIDE" ||
        (control.quiz && control.quiz.roundActive)))
    );

    const busy = Boolean(controlBusy);
    // The system page and the timer page share this painter; each page only owns its rows.
    if (refs.takeOver && refs.enterChild) {
      const takeOver = controlAction(control, "takeOver");
      const enterChild = controlAction(control, "enterChildMode");
      refs.takeOver.button.disabled = !takeOver.enabled || busy;
      // V48: warn in the row instead of blocking with a second confirmation. The server
      // still enforces the PIN and discards the timer/quiz round in one step.
      const takeOverDiscards = Boolean(control && (
        (control.timer && control.timer.active) ||
        (control.quiz && control.quiz.roundActive)
      ));
      refs.takeOver.sub.textContent = takeOver.enabled
        ? t(takeOverDiscards ? "controlTakeOverDiscardHint" : "controlTakeOverHint")
        : t("controlTakeOverAlready");
      refs.enterChild.button.disabled = !enterChild.enabled || busy;
      refs.enterChild.sub.textContent = enterChild.enabled
        ? t("controlEnterHint")
        : t("controlEnterAlready");
    }

    if (refs.startManual && refs.stopTimer) {
      const start = controlAction(control, "startManual");
      const stop = controlAction(control, "stopTimer");
      refs.startManual.button.disabled = !start.enabled || busy;
      const manual = control && control.manualStart;
      if (start.enabled) {
        refs.startManual.sub.textContent = t("controlStartHint");
      } else if (manual && typeof host.controlManualReason === "function") {
        refs.startManual.sub.textContent = host.controlManualReason(manual);
      } else {
        refs.startManual.sub.textContent = t("controlStartBlocked");
      }
      const timerLeft = controlTimerLeft(control);
      refs.stopTimer.button.disabled = !stop.enabled || busy;
      refs.stopTimer.sub.textContent = stop.enabled
        ? t("controlStopHint", { time: fmtSeconds(timerLeft) })
        : t("controlStopNoTimer");
    }

    if (busy) {
      refs.notice.textContent = t("controlBusy");
      refs.notice.className = "parent-hint parent-control-notice";
    } else if (controlNotice) {
      refs.notice.textContent = controlNotice;
      refs.notice.className = "parent-hint parent-control-notice" +
        (controlNoticeError ? " parent-control-notice-error" : "");
    } else {
      refs.notice.textContent = "";
      refs.notice.className = "parent-hint parent-control-notice";
    }
  }

  function newControlRequestId() {
    const bytes = new Uint8Array(16);
    if (window.crypto && typeof window.crypto.getRandomValues === "function") {
      window.crypto.getRandomValues(bytes);
    } else {
      for (let i = 0; i < bytes.length; i += 1) {
        bytes[i] = Math.floor(Math.random() * 256);
      }
    }
    return Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
  }

  function controlResultKey(data) {
    const code = data && data.code;
    switch (code) {
      case "TAKE_OVER_APPLIED": return "controlAppliedTakeOver";
      case "TAKE_OVER_NOOP": return "controlNoopTakeOver";
      case "ENTER_CHILD_APPLIED": return "controlAppliedEnter";
      case "ENTER_CHILD_NOOP": return "controlNoopEnter";
      case "STOP_TIMER_APPLIED": return "controlAppliedStop";
      case "STOP_TIMER_NOOP": return "controlNoopStop";
      case "START_MANUAL_APPLIED": return "controlAppliedManual";
      default: return "saved";
    }
  }

  function controlErrorText(error) {
    const code = error && (error.code || error.message);
    if (code === "START_BLOCKED") {
      const control = (error.data && error.data.control) || controlData();
      const manual = control && control.manualStart;
      if (manual && typeof host.controlManualReason === "function") {
        const text = host.controlManualReason(manual);
        if (text) return text;
      }
      return t("controlStartBlocked");
    }
    if (code === "STALE_CONFIRM" || code === "CONFIRM_EXPIRED") {
      return t("controlStateChanged");
    }
    return errorText(error);
  }

  async function sendControl(action, extra) {
    if (!editorToken || controlBusy) return null;
    // V32: leaving child mode needs the parent PIN. V48: the PIN is also the confirmation,
    // so a running timer or quiz round is discarded without a second dialog. The proof
    // lasts 60s, so a retry after a failed request does not ask again until it expires.
    let sensitive = "";
    if (action === "TAKE_OVER") {
      sensitive = sensitiveToken("TAKE_OVER");
      if (!sensitive) {
        controlBusy = "TAKE_OVER_VERIFY";
        paintControl();
        try {
          sensitive = await promptSensitivePin(
            "TAKE_OVER",
            "sensitivePinLeadTakeOver",
            false
          );
        } finally {
          controlBusy = null;
          paintControl();
        }
        if (!sensitive) return null;
      }
    }
    const requestId = controlRequestIds[action] || newControlRequestId();
    controlRequestIds[action] = requestId;
    controlBusy = action;
    controlNotice = null;
    controlNoticeError = false;
    paintControl();
    let data = null;
    try {
      data = await host.request("/api/parent/control", {
        method: "POST",
        body: Object.assign({ action: action, requestId: requestId }, extra || {}),
        editorToken: editorToken,
        sensitiveToken: sensitive,
        quizAuth: true
      });
    } catch (error) {
      const payload = error && error.data;
      if (payload && payload.result === "CONFIRM_REQUIRED" && payload.confirm &&
          payload.confirm.token) {
        controlBusy = null;
        paintControl();
        const confirm = payload.confirm;
        openControlConfirm(confirm, () => {
          sendControl(action, Object.assign({}, extra || {}, {
            confirmToken: confirm.token
          }));
        });
        return null;
      }
      if (action === "TAKE_OVER" && isSensitiveError(error)) {
        // The proof expired between verification and the request; ask once more and retry
        // the same requestId, so a concurrent execution still resolves idempotently.
        delete sensitiveTokens.TAKE_OVER;
        controlBusy = null;
        paintControl();
        const renewed = await promptSensitivePin(
          "TAKE_OVER",
          "sensitivePinLeadTakeOver",
          true
        );
        if (renewed) return sendControl(action, extra);
        controlNotice = errorText(error);
        controlNoticeError = true;
        paintControl();
        return null;
      }
      controlBusy = null;
      if (error && (error.code === "QUIZ_TAKEN_OVER" || error.code === "LEASE_TAKEN_OVER" ||
          error.code === "EDITOR_REQUIRED" || error.code === "LEASE_EXPIRED" ||
          error.status === 401)) {
        handleLeaseError(error);
        return null;
      }
      if (payload && payload.control) adoptControl(payload.control);
      if (error && (error.code === "STALE_CONFIRM" || error.code === "CONFIRM_EXPIRED")) {
        delete controlRequestIds[action];
        refreshSnapshot()
          .then(() => paintControl())
          .catch(() => { /* the lease error path owns the page */ });
      }
      controlNotice = controlErrorText(error);
      controlNoticeError = true;
      paintControl();
      return null;
    }
    controlBusy = null;
    if (action === "TAKE_OVER") {
      // The server consumes the proof on APPLIED/NOOP; forget it either way.
      delete sensitiveTokens.TAKE_OVER;
    }
    if (data && data.control) adoptControl(data.control);
    // A final result ends this requestId. A later, deliberate click is a fresh command.
    delete controlRequestIds[action];
    controlNotice = t(controlResultKey(data));
    controlNoticeError = false;
    paintControl();
    return data;
  }

  function openControlConfirm(confirm, onConfirm) {
    let titleKey = "controlConfirmTakeTimerTitle";
    let bodyKey = "controlConfirmTakeTimerBody";
    let actionKey = "controlConfirmTakeAction";
    if (confirm.reason === "END_TIMER") {
      titleKey = "controlConfirmEndTitle";
      bodyKey = "controlConfirmEndBody";
      actionKey = "controlConfirmEndAction";
    } else if (confirm.reason === "TAKE_OVER_QUIZ") {
      titleKey = "controlConfirmTakeQuizTitle";
      bodyKey = "controlConfirmTakeQuizBody";
      actionKey = "controlConfirmQuizAction";
    }
    confirmSheet(t(titleKey), t(bodyKey), t(actionKey), () => {
      onConfirm();
    });
  }

  function openControlManual() {
    const control = controlData();
    const manual = control && control.manualStart;
    if (!manual || !manual.eligible || controlBusy) return;
    const requiresPin = manual.requiresPin !== false;
    const min = Math.max(1, Number(manual.minMinutes) || 5);
    const max = Math.max(min, Number(manual.maxMinutes) || 120);
    const step = Math.max(1, Number(manual.stepMinutes) || 5);
    let minutes = Math.min(max, Math.max(min, Number(controlManualMinutes) || min));
    minutes = Math.min(max, min + Math.round((minutes - min) / step) * step);
    openSheet((sheet, close) => {
      let lockRemaining = 0;
      let lockTicker = 0;
      sheet.appendChild(el("h2", null, t("controlManualTitle")));
      sheet.appendChild(el(
        "p",
        null,
        t(requiresPin ? "controlManualLead" : "controlManualLeadPinless")
      ));

      // V45: show the exact scope the TV computed. Entertainment, unlimited and (only while
      // the study switch is on) the study-reward apps. The runtime re-checks this scope.
      const scope = manual.scope;
      if (scope && typeof scope === "object") {
        sheet.appendChild(el("p", "parent-field-label", t("manualScopeTitle")));
        const chips = el("div", "parent-manual-scope");
        const entCount = Number(scope.entertainmentCount) || 0;
        chips.appendChild(el("span", "parent-manual-chip",
          scope.entertainmentAll
            ? t("manualScopeEntertainmentAll")
            : t("manualScopeEntertainment", { n: entCount })));
        chips.appendChild(el("span", "parent-manual-chip",
          t("manualScopeUnlimited", { n: Number(scope.unlimitedCount) || 0 })));
        if (scope.studyRewardIncluded) {
          chips.appendChild(el("span", "parent-manual-chip",
            t("manualScopeStudy", { n: Number(scope.studyRewardCount) || 0 })));
        }
        sheet.appendChild(chips);
        if (scope.studyRewardIncluded) {
          sheet.appendChild(el("p", "parent-hint", t("manualStudyNote")));
        }
      }

      const field = el("div", "parent-field");
      const label = el("label", "parent-field-label", t("pinLabel"));
      const input = el("input", "parent-pin-input");
      input.type = "password";
      input.inputMode = "numeric";
      input.autocomplete = "one-time-code";
      input.maxLength = 6;
      input.pattern = "[0-9]*";
      input.addEventListener("input", () => {
        input.value = input.value.replace(/\D/g, "").slice(0, 6);
      });
      field.appendChild(label);
      field.appendChild(input);

      const stepper = el("div", "parent-control-stepper");
      const minus = button("−", "parent-btn ghost", () => {
        minutes = Math.max(min, minutes - step);
        refreshStepper();
      });
      const value = el("span", "parent-control-stepper-value", "");
      const plus = button("+", "parent-btn ghost", () => {
        minutes = Math.min(max, minutes + step);
        refreshStepper();
      });
      minus.setAttribute("aria-label", t("controlManualDuration"));
      plus.setAttribute("aria-label", t("controlManualDuration"));
      const stepperLabel = el("span", "parent-field-label", t("controlManualDuration"));
      stepper.appendChild(minus);
      stepper.appendChild(value);
      stepper.appendChild(plus);
      sheet.appendChild(stepperLabel);
      sheet.appendChild(stepper);
      // The PIN field follows the duration, matching the V45 sheet order: scope, length, PIN.
      // V47: when the parent turned the user-config prompts off, no PIN field is shown.
      if (requiresPin) sheet.appendChild(field);

      function refreshStepper() {
        value.textContent = t("controlManualMinutesValue", { n: minutes });
        const locked = lockRemaining > 0;
        input.disabled = locked;
        minus.disabled = locked || minutes <= min;
        plus.disabled = locked || minutes >= max;
        start.disabled = locked;
      }

      function stopLockTicker() {
        if (lockTicker) clearInterval(lockTicker);
        lockTicker = 0;
        lockRemaining = 0;
      }

      function lockFor(seconds) {
        stopLockTicker();
        lockRemaining = Math.max(1, Math.ceil(seconds));
        error.textContent = t("pinLockedRetry", { n: lockRemaining });
        error.classList.remove("hidden");
        refreshStepper();
        lockTicker = setInterval(() => {
          if (!document.body.contains(sheet)) {
            stopLockTicker();
            return;
          }
          lockRemaining -= 1;
          if (lockRemaining <= 0) {
            stopLockTicker();
            error.textContent = "";
            error.classList.add("hidden");
            try { input.focus(); } catch (_) { /* ignore */ }
          } else {
            error.textContent = t("pinLockedRetry", { n: lockRemaining });
          }
          refreshStepper();
        }, 1000);
      }

      const error = el("p", "parent-error hidden");
      sheet.appendChild(error);
      const actions = el("div", "parent-actions");
      const cancel = button(t("cancel"), "parent-btn ghost", () => {
        stopLockTicker();
        close();
      });
      const start = button(t("controlManualStart"), "parent-btn primary", async () => {
        if (lockRemaining > 0) return;
        let pin = "";
        if (requiresPin) {
          pin = input.value.replace(/\D/g, "");
          if (pin.length !== 6) {
            error.textContent = t("pinLength");
            error.classList.remove("hidden");
            input.focus();
            return;
          }
        }
        start.disabled = true;
        minus.disabled = true;
        plus.disabled = true;
        error.classList.add("hidden");
        try {
          const requestId = newControlRequestId();
          const requestBody = {
            action: "START_MANUAL",
            requestId: requestId,
            minutes: minutes
          };
          if (requiresPin) requestBody.pin = pin;
          const data = await host.request("/api/parent/control", {
            method: "POST",
            body: requestBody,
            editorToken: editorToken,
            quizAuth: true
          });
          if (data && data.control) adoptControl(data.control);
          controlManualMinutes = minutes;
          controlNotice = t("controlAppliedManual");
          controlNoticeError = false;
          stopLockTicker();
          close();
          paintControl();
        } catch (err) {
          if (err && (err.code === "QUIZ_TAKEN_OVER" || err.code === "LEASE_TAKEN_OVER" ||
              err.code === "EDITOR_REQUIRED" || err.code === "LEASE_EXPIRED" ||
              err.status === 401)) {
            stopLockTicker();
            close();
            handleLeaseError(err);
            return;
          }
          if (err && err.data && err.data.control) adoptControl(err.data.control);
          input.value = "";
          const code = err && (err.code || err.message);
          if (!requiresPin && (code === "WRONG_PIN" || code === "PIN_REQUIRED" ||
              code === "PIN_LOCKED" || code === "DEFAULT_PIN")) {
            // Another phone re-enabled the prompts after this sheet opened. Reopen with the
            // server's current state instead of showing a PIN error on a hidden field.
            stopLockTicker();
            close();
            try { await refreshSnapshot(); } catch (_) { /* keep the last view */ }
            setNotice(t("controlManualPinRestored"), true);
            render();
            return;
          }
          if (code === "PIN_LOCKED") {
            const retry = Number(err.data && err.data.retryAfterSeconds) || 600;
            lockFor(retry);
          } else {
            error.textContent = controlErrorText(err);
            error.classList.remove("hidden");
            input.focus();
            start.disabled = false;
            refreshStepper();
          }
          paintControl();
        }
      });
      actions.appendChild(cancel);
      actions.appendChild(start);
      sheet.appendChild(actions);
      refreshStepper();
      if (requiresPin) {
        setTimeout(() => { try { input.focus(); } catch (_) { /* ignore */ } }, 60);
      }
    });
  }

  // --------------------------------------------------------------------- sheet helpers

  function openSheet(build, onDismiss) {
    closeSheet();
    sheetDismiss = typeof onDismiss === "function" ? onDismiss : null;
    const root = el("div", "parent-sheet-root");
    root.id = SHEET_ID;
    const backdrop = el("div", "parent-sheet-backdrop");
    const sheet = el("div", "parent-sheet");
    sheet.setAttribute("role", "dialog");
    sheet.setAttribute("aria-modal", "true");
    backdrop.addEventListener("click", closeSheet);
    root.appendChild(backdrop);
    root.appendChild(sheet);
    document.body.appendChild(root);
    const close = () => closeSheet();
    build(sheet, close);
  }

  function closeSheet() {
    const dismiss = sheetDismiss;
    sheetDismiss = null;
    const root = $(SHEET_ID);
    if (root) root.remove();
    // Backdrop and Escape close the sheet without going through the prompt's own callback;
    // resolving the pending PIN sheet here keeps its Promise from hanging forever.
    if (dismiss) dismiss();
  }

  function confirmSheet(title, text, confirmText, onConfirm, cancelText) {
    openSheet((sheet, close) => {
      sheet.appendChild(el("h2", null, title));
      sheet.appendChild(el("p", null, text));
      const actions = el("div", "parent-actions");
      actions.appendChild(button(cancelText || t("cancel"), "parent-btn ghost", close));
      actions.appendChild(button(confirmText || t("cancel"), "parent-btn primary", () => {
        close();
        onConfirm();
      }));
      sheet.appendChild(actions);
    });
  }

  // ------------------------------------------------------------------------- events

  function onKeydown(event) {
    if (!rootOpen) return;
    if (event.key !== "Escape") return;
    const sheet = $(SHEET_ID);
    if (sheet) {
      closeSheet();
      event.preventDefault();
      return;
    }
    if (closeSubpage()) {
      event.preventDefault();
      return;
    }
    confirmClose();
    event.preventDefault();
  }

  function onPageHide() {
    if (!editorToken) return;
    const token = editorToken;
    releaseAllSensitive(true);
    clearSensitiveMemory();
    storeEditor("");
    host.request("/api/parent/release", {
      method: "POST",
      body: {},
      editorToken: token,
      keepalive: true,
      quizAuth: false,
    }).catch(() => { /* TTL covers it */ });
  }

  function onVisibilityChange() {
    if (document.hidden || !rootOpen) return;
    if (!editorToken) {
      // Backgrounding may have let the lease or the entry grant expire; re-run the gate
      // instead of showing a stale editor.
      openGate();
      return;
    }
    heartbeat();
  }

  function onPageShow(event) {
    if (event && event.persisted) onVisibilityChange();
  }

  document.addEventListener("keydown", onKeydown);
  document.addEventListener("visibilitychange", onVisibilityChange);
  window.addEventListener("pagehide", onPageHide);
  window.addEventListener("pageshow", onPageShow);

  /**
   * The quiz page owns the member picker; when the selected member changes, the user tab
   * returns to its index so no page keeps controls for the member that was selected before.
   */
  function onSubjectChanged() {
    if (!rootOpen || rootSuspended || rootMode !== "user") return;
    subpage = null;
    clearNotices();
    render();
  }

  window.ParentCenterUI = {
    init: init,
    open: open,
    openUser: openUser,
    openSystem: openSystem,
    openControl: openControl,
    suspend: suspend,
    onSubjectChanged: onSubjectChanged,
    close: close,
    isOpen: () => rootOpen,
    refreshEntry: refreshEntry,
    setLang: setLang,
    // The quiz page header reuses the family avatar artwork.
    avatarHtml: (preset) => avatarSvg(preset),
  };
})();
