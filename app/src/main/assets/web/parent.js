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
  const MIN_EDIT_DAILY = 0;
  const MAX_EDIT_DAILY = 240;
  const MAX_EDIT_REST = 60;
  const MIN_BUFFER = 0;
  const MAX_BUFFER = 60;
  const STEP_DAILY = 15;
  const STEP_REST = 5;
  const STEP_BUFFER = 5;
  const HEARTBEAT_MS = 3000;
  const RULES_DEBOUNCE_MS = 200;
  const APP_PROMPT_KEY = "ptv.parentAppPromptSeen";

  const TEXTS = {
    zh: {
      parentCenterTitle: "家长中心",
      moreTitle: "更多",
      timeAndApps: "时间与应用",
      timeAndAppsSummary: "工作日、周末各 1 个时段 · {apps}",
      localSettings: "本机设置",
      pinSet: "已设置",
      pinUnset: "未设置",
      quizAndAi: "题库与 AI",
      bankMaintenance: "题库管理",
      bankMaintenanceHint: "清空课程/AI、自定义题库，或调整答题设置",
      otherOptions: "其他",
      privacyHint: "本机数据与权限说明",
      aiTitle: "AI 出题",
      privacyInfo: "隐私说明",
      downloadParentApp: "下载家长端 App",
      downloadParentAppHint: "Android 7.0 及以上",
      viewAction: "查看",
      manageAction: "管理",
      logoutAccount: "退出登录",
      timePageHint: "应用范围立即生效；时段、额度、休息从下次计时生效",
      tvInputSources: "电视输入源",
      tvInputSourcesHint: "只影响桌面入口，不是强制锁定",
      singleWindowHint: "每天一个时间段",
      exemptSection: "豁免时段",
      exemptHint: "豁免时段内不限时间、不限App（老人在家时间）",
      exemptAdd: "+ 添加豁免时段",
      noExempt: "未设置豁免时段",
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
      appScope: "应用范围",
      entertainmentApps: "娱乐应用",
      studyApps: "学习应用",
      allApps: "全部应用",
      appsSelected: "已选 {n} 个应用",
      noEntertainment: "未选择（不放行娱乐应用）",
      studyNone: "未设置（没有学习模式）",
      sourcesAndQuiz: "来源与答题",
      sourceEntries: "来源入口",
      sourceHint: "只影响桌面入口，不是强制锁定",
      quizChallenge: "答题挑战",
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
      timing: "计时方式",
      autoMode: "自动",
      manualMode: "手动",
      activationHint: "切换会结束当前计时段，不会自动开始",
      unlockFineprint: "临时解锁与电视同步：解锁后在电视锁屏上看到的家长验证同样生效，10 分钟后自动重新锁定。",
      pairHint: "配对码 5 分钟内有效。输入错误 5 次后需要回电视重新生成。",
      passwordHint: "修改家长密码需要输入当前密码；保存后立即生效。",
      pinLockedRetry: "密码错误次数过多，请在 {n} 秒后重试",
      sensitivePinTitle: "验证家长密码",
      sensitivePinLeadRules: "输入家长密码后编辑时间与应用",
      sensitivePinLeadTakeOver: "输入家长密码后临时退出儿童模式",
      sensitivePinDefault: "请先在电视上修改初始家长密码",
      sensitivePinExpired: "验证已过期，请重新输入",
      sensitivePinRequired: "请先验证家长密码",
      confirmAutoUpdateOffTitle: "关闭自动升级？",
      confirmAutoUpdateOffBody: "关闭后电视不再自动获取新版本，需要手动安装更新。",
      confirmAutoUpdateOffAction: "关闭",
      activationInvalid: "计时方式切换未生效，请刷新后重试",
      remoteRefreshFailed: "刷新远程状态失败，请检查电视网络后重试",
      activationConfirmTitle: "切换计时方式？",
      activationConfirmText: "切换后当前计时段会结束，不会自动开始新的计时。",
      activationConfirm: "切换",
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
      dailyLimit: "每日额度",
      sessionLimit: "单次上限",
      rest: "休息",
      buffer: "息屏缓冲",
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
      appPickerStudyHint: "不选 = 没有学习模式；学习应用不受娱乐额度限制",
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
      introVideoLimit: "16:9 MP4/MOV · H.264/H.265 等（需电视支持）· 单段 ≤15 秒 / ≤4 MB · 上传视频最多 6 段 / 共 24 MB",
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
      introVideoTooLarge: "视频超过 4 MB，请压缩后再试",
      introVideoBadDuration: "视频超过 15 秒，请剪短后再试",
      introVideoBadFormat: "只支持 JPG/PNG/WebP 图片，或 16:9 MP4/MOV 视频（编码需电视支持）",
      introVideoInvalid: "视频无法读取或格式不受支持",
      introVideoFull: "上传已达上限（最多 6 个、共 24 MB）；请先删除一个",
      introVideoNoSpace: "电视存储空间不足",
      introVideoMeta: "{size} MB · {seconds} 秒 · {width}×{height}",
      introVideoListTitle: "视频库",
      introVideoUploadHint: "可一次选择多段视频；上传完成后点「设为片头」",
      introVideoUploadProgress: "正在上传 {index}/{total}：{name} · {percent}%",
      introVideoBatchDone: "已上传 {added} 个壁纸",
      introVideoBatchFailed: "有 {failed} 个未上传：{reason}",
      introVideoBatchFull: "上传上限 {max} 个，本次只上传了 {n} 个",
      introVideoUse: "设为片头",
      introVideoHevc: "此电视无法解码 H.265/HEVC；请转换为 H.264 后重试",
      introVideoUnsupportedCodec: "此电视无法解码该视频编码；请转换为 H.264 后重试",
      mediaPageTitle: "儿童模式壁纸",
      mediaCurrentTitle: "当前选择",
      mediaModulesNote: "两者互不影响",
      mediaRoleQuiz: "闯关片头壁纸",
      mediaRoleLock: "儿童模式壁纸",
      mediaRoleBoth: "两者都使用",
      mediaRoleQuizHint: "答题开始时显示",
      mediaRoleLockHint: "儿童模式锁屏和自律桌面显示；视频静音循环",
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
      mediaImageRoleNote: "图片只能用于儿童模式壁纸，不能用于闯关片头。",
      mediaIsolationNote: "未选择时使用儿童模式默认壁纸；家长模式始终使用 Aerial 壁纸。",
      mediaChoose: "选择",
      mediaChange: "更换",
      mediaCustomized: "已自定义",
      mediaTabCloud: "云端片库",
      mediaTabLocal: "本地上传",
      mediaOfficialHint: "↓ 下载 · ✓ 已下载；最多 8 个，满了先删一个再下载。",
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
      mediaPreviewHint: "这里只预览封面；视频下载后在电视上播放，图片下载后作为静态壁纸。",
      mediaDownloadHint: "下载后可以设为闯关片头或儿童模式壁纸。",
      mediaDownload: "下载这个壁纸",
      mediaDownloadedHint: "已下载。到上方「当前选择」中选择。",
      mediaLocalReadyHint: "已上传。到上方「当前选择」中选择。",
      mediaDownloadDone: "已下载，可在「当前选择」中使用",
      mediaPickerTitle: "选择{module}",
      mediaPickerHint: "只影响这个模块。",
      mediaPickerHintQuiz: "只影响闯关片头；这里只显示视频。",
      mediaPickerEmpty: "还没有已下载的壁纸",
      mediaPickerEmptyVideo: "还没有已下载的视频。",
      mediaPickerGoLibrary: "去云端片库",
      mediaCurrentBadge: "当前",
      mediaRetryDownload: "重试下载",
      mediaUpdateLocal: "更新本地副本",
      mediaUpdateHint: "有新版本，电视上的旧版仍可使用。",
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
      mediaDeleteText: "删除本地副本；使用它的位置会恢复儿童模式默认壁纸。云端片库的条目不会被删除。",
      mediaDeleted: "壁纸已删除",
      mediaSelected: "壁纸设置已更新",
      mediaDownloadStarted: "已开始下载",
      mediaDownloading: "正在下载…",
      mediaCancelled: "已取消下载",
      mediaNotFound: "壁纸不存在或已不可用",
      mediaNotReady: "壁纸尚未下载完成",
      mediaBusy: "另一个壁纸正在下载，请稍后",
      mediaRoleInvalid: "壁纸不支持所选角色",
      mediaLibraryFull: "已下载 8 个，请先删除一个再下载",
      mediaNoSpace: "电视存储空间不足",
      mediaDeleteFailed: "删除失败，请重试",
      mediaDownloadFailed: "下载失败，请检查电视网络后重试",
      mediaLocalHint: "仅保存在电视，不上传云端",
      mediaLocalEmpty: "还没有上传壁纸",
      mediaUploadHint: "JPG/PNG/WebP ≤12 MB；或 16:9 MP4/MOV ≤15 秒 / ≤4 MB（需电视支持）· 最多 6 个 / 共 24 MB",
      mediaLimit: "官方片库最多下载 8 个（图片和视频合计）；本地上传最多 6 个。满了请先删一个，避免电视存储紧张。",
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
      timeAndApps: "Time & apps",
      timeAndAppsSummary: "One window each on workdays and weekends · {apps}",
      localSettings: "This TV",
      pinSet: "Set",
      pinUnset: "Not set",
      quizAndAi: "Question bank & AI",
      bankMaintenance: "Manage banks",
      bankMaintenanceHint: "Clear the course/AI or custom banks, or edit quiz settings",
      otherOptions: "Other",
      privacyHint: "On-device data and permissions",
      aiTitle: "AI questions",
      privacyInfo: "Privacy notice",
      downloadParentApp: "Download parent app",
      downloadParentAppHint: "Android 7.0+",
      viewAction: "View",
      manageAction: "Manage",
      logoutAccount: "Log out",
      timePageHint: "App scope applies now; windows, limits and breaks apply from the next session",
      tvInputSources: "TV input sources",
      tvInputSourcesHint: "Only affects the home-screen entries; it is not a hard lock",
      singleWindowHint: "One time window per day",
      exemptSection: "Exempt windows",
      exemptHint: "Inside these windows nothing is limited (grandparents' time at home)",
      exemptAdd: "+ Add exempt window",
      noExempt: "No exempt windows",
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
      appScope: "App scope",
      entertainmentApps: "Entertainment apps",
      studyApps: "Study apps",
      allApps: "All apps",
      appsSelected: "{n} apps selected",
      noEntertainment: "None (no entertainment allowed)",
      studyNone: "Not set (no study mode)",
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
      timing: "Timing mode",
      autoMode: "Auto",
      manualMode: "Manual",
      activationHint: "Switching ends the current session; nothing starts automatically",
      unlockFineprint: "Unlock is shared with the TV: the same parent verification used on the TV lock screen applies here and relocks after 10 minutes.",
      pairHint: "The pairing code is valid for 5 minutes. After 5 wrong entries, create a new one on the TV.",
      passwordHint: "Changing the parent PIN requires the current PIN; it takes effect immediately.",
      pinLockedRetry: "Too many wrong passwords; try again in {n}s",
      sensitivePinTitle: "Verify parent PIN",
      sensitivePinLeadRules: "Enter the parent PIN to edit time and apps",
      sensitivePinLeadTakeOver: "Enter the parent PIN to leave child mode temporarily",
      sensitivePinDefault: "Change the initial parent PIN on the TV first",
      sensitivePinExpired: "Verification expired; enter the PIN again",
      sensitivePinRequired: "Verify the parent PIN first",
      confirmAutoUpdateOffTitle: "Turn off auto-update?",
      confirmAutoUpdateOffBody: "The TV will stop fetching new versions automatically; updates must be installed manually.",
      confirmAutoUpdateOffAction: "Turn off",
      activationInvalid: "The timing mode did not change; refresh and try again",
      remoteRefreshFailed: "Could not refresh the remote state; check the TV network and retry",
      activationConfirmTitle: "Switch timing mode?",
      activationConfirmText: "The current session ends now and no new session starts automatically.",
      activationConfirm: "Switch",
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
      sessionLimit: "Session limit",
      rest: "Break",
      buffer: "Screen-off buffer",
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
      appPickerStudyHint: "Selecting nothing means no study mode; study apps are not limited by entertainment time",
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
      introVideoLimit: "16:9 MP4/MOV · H.264/H.265, etc. (TV decoder required) · ≤15 s / ≤4 MB each · up to 6 uploaded videos / 24 MB",
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
      introVideoTooLarge: "The video is over 4 MB; compress it and try again",
      introVideoBadDuration: "The video is over 15 seconds; shorten it and try again",
      introVideoBadFormat: "Only JPG/PNG/WebP images, or 16:9 MP4/MOV videos with a codec this TV can decode",
      introVideoInvalid: "The video cannot be read or is unsupported",
      introVideoFull: "The upload library is full (up to 6 items / 24 MB); delete one first",
      introVideoNoSpace: "The TV is low on storage",
      introVideoMeta: "{size} MB · {seconds} s · {width}×{height}",
      introVideoListTitle: "Video library",
      introVideoUploadHint: "You can select several videos at once. After uploading, tap Use as the intro.",
      introVideoUploadProgress: "Uploading {index}/{total}: {name} · {percent}%",
      introVideoBatchDone: "Uploaded {added} wallpaper(s)",
      introVideoBatchFailed: "{failed} item(s) not uploaded: {reason}",
      introVideoBatchFull: "The library holds {max} items; only {n} were uploaded this time",
      introVideoUse: "Use as intro",
      introVideoHevc: "This TV cannot decode H.265/HEVC; convert it to H.264 and retry.",
      introVideoUnsupportedCodec: "This TV cannot decode this video codec; convert it to H.264 and retry.",
      mediaPageTitle: "Child-mode wallpapers",
      mediaCurrentTitle: "Current selection",
      mediaModulesNote: "Independent of each other",
      mediaRoleQuiz: "Quiz intro wallpaper",
      mediaRoleLock: "Child-mode wallpaper",
      mediaRoleBoth: "Both",
      mediaRoleQuizHint: "Shown when a quiz starts",
      mediaRoleLockHint: "Shown on the child-mode lock screen and self-discipline desktop; videos loop muted",
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
      mediaImageRoleNote: "Images can only be used as the child-mode wallpaper, not the quiz intro.",
      mediaIsolationNote: "Until you choose, the default child-mode wallpaper is used. Parent mode always keeps Aerial's wallpaper.",
      mediaChoose: "Choose",
      mediaChange: "Change",
      mediaCustomized: "Customized",
      mediaTabCloud: "Cloud library",
      mediaTabLocal: "Local upload",
      mediaOfficialHint: "↓ download · ✓ downloaded; up to 8 items — delete one before downloading more.",
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
      mediaPreviewHint: "Only the cover is shown here; videos play on the TV after download, images become still wallpapers.",
      mediaDownloadHint: "Download it, then use it as the quiz intro or a child-mode wallpaper.",
      mediaDownload: "Download this wallpaper",
      mediaDownloadedHint: "Downloaded. Choose it under Current selection above.",
      mediaLocalReadyHint: "Uploaded. Choose it under Current selection above.",
      mediaDownloadDone: "Downloaded. Choose it under Current selection.",
      mediaPickerTitle: "Choose {module}",
      mediaPickerHint: "This only changes this module.",
      mediaPickerHintQuiz: "Only affects the quiz intro; only videos are shown.",
      mediaPickerEmpty: "No downloaded wallpapers yet",
      mediaPickerEmptyVideo: "No downloaded videos yet.",
      mediaPickerGoLibrary: "Open cloud library",
      mediaCurrentBadge: "Current",
      mediaRetryDownload: "Retry download",
      mediaUpdateLocal: "Update local copy",
      mediaUpdateHint: "A newer version is available. The old copy on the TV still works.",
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
      mediaDeleteText: "The local copy will be deleted; any role using it returns to the default child-mode wallpaper. The cloud catalogue entry is not removed.",
      mediaDeleted: "Wallpaper deleted",
      mediaSelected: "Wallpaper settings updated",
      mediaDownloadStarted: "Download started",
      mediaDownloading: "Downloading…",
      mediaCancelled: "Download cancelled",
      mediaNotFound: "The wallpaper is missing or no longer available",
      mediaNotReady: "The wallpaper has not finished downloading",
      mediaBusy: "Another wallpaper is downloading; try again shortly",
      mediaRoleInvalid: "This wallpaper cannot be used for the selected role",
      mediaLibraryFull: "8 items downloaded; delete one before downloading another",
      mediaNoSpace: "The TV is low on storage",
      mediaDeleteFailed: "Delete failed; try again",
      mediaDownloadFailed: "Download failed; check the TV network and retry",
      mediaLocalHint: "Stored on the TV only; never uploaded",
      mediaLocalEmpty: "No uploaded wallpapers yet",
      mediaUploadHint: "JPG/PNG/WebP ≤12 MB; or 16:9 MP4/MOV ≤15 s / ≤4 MB (TV decoder required) · up to 6 items / 24 MB",
      mediaLimit: "Cloud library: up to 8 downloads (images and videos combined) · Local: up to 6. Delete one when full to avoid filling the TV.",
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
      timeAndApps: "Время и приложения",
      timeAndAppsSummary: "По одному окну в будни и выходные · {apps}",
      localSettings: "Этот телевизор",
      pinSet: "Задан",
      pinUnset: "Не задан",
      quizAndAi: "Банк вопросов и ИИ",
      bankMaintenance: "Управление банками",
      bankMaintenanceHint: "Очистка банка курса/ИИ и своих банков, настройки викторины",
      otherOptions: "Другое",
      privacyHint: "Данные и разрешения на этом телевизоре",
      aiTitle: "ИИ-вопросы",
      privacyInfo: "Конфиденциальность",
      downloadParentApp: "Скачать приложение родителя",
      downloadParentAppHint: "Android 7.0 и выше",
      viewAction: "Открыть",
      manageAction: "Управлять",
      logoutAccount: "Выйти",
      timePageHint: "Список приложений действует сразу; окна, лимиты и перерывы — со следующего сеанса",
      tvInputSources: "Источники ТВ",
      tvInputSourcesHint: "Влияет только на входы на главном экране, это не жёсткая блокировка",
      singleWindowHint: "Один интервал в день",
      exemptSection: "Исключающие периоды",
      exemptHint: "В эти периоды ограничения не действуют (время бабушек и дедушек)",
      exemptAdd: "+ Добавить период",
      noExempt: "Периоды не заданы",
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
      appScope: "Приложения",
      entertainmentApps: "Развлечения",
      studyApps: "Учебные приложения",
      allApps: "Все приложения",
      appsSelected: "Выбрано приложений: {n}",
      noEntertainment: "Ничего не выбрано (развлечения запрещены)",
      studyNone: "Не задано (учебного режима нет)",
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
      timing: "Режим времени",
      autoMode: "Авто",
      manualMode: "Вручную",
      activationHint: "Переключение завершит текущий сеанс; новый не начнётся автоматически",
      unlockFineprint: "Разблокировка общая с телевизором: та же проверка родителя, что и на экране блокировки ТВ, действует здесь и снова блокируется через 10 минут.",
      pairHint: "Код сопряжения действует 5 минут. После 5 ошибок создайте новый код на ТВ.",
      passwordHint: "Для смены пароля родителя нужен текущий пароль; изменения применяются сразу.",
      pinLockedRetry: "Слишком много неверных паролей; повторите через {n} с",
      sensitivePinTitle: "Подтвердите пароль родителя",
      sensitivePinLeadRules: "Введите пароль родителя, чтобы изменить время и приложения",
      sensitivePinLeadTakeOver: "Введите пароль родителя, чтобы временно выйти из детского режима",
      sensitivePinDefault: "Сначала измените начальный пароль родителя на телевизоре",
      sensitivePinExpired: "Подтверждение истекло; введите пароль ещё раз",
      sensitivePinRequired: "Сначала подтвердите пароль родителя",
      confirmAutoUpdateOffTitle: "Отключить автообновление?",
      confirmAutoUpdateOffBody: "Телевизор перестанет автоматически получать новые версии; обновления нужно устанавливать вручную.",
      confirmAutoUpdateOffAction: "Отключить",
      activationInvalid: "Режим времени не переключился; обновите и повторите",
      remoteRefreshFailed: "Не удалось обновить удалённое состояние; проверьте сеть ТВ и повторите",
      activationConfirmTitle: "Переключить режим времени?",
      activationConfirmText: "Текущий сеанс завершится сейчас, новый автоматически не начнётся.",
      activationConfirm: "Переключить",
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
      sessionLimit: "Лимит сеанса",
      rest: "Перерыв",
      buffer: "Буфер выключения экрана",
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
      appPickerStudyHint: "Если ничего не выбрать, учебного режима нет; учебные приложения не ограничены развлекательным временем",
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
      introVideoLimit: "MP4/MOV 16:9 · H.264/H.265 и др. (нужен декодер ТВ) · ≤15 с / ≤4 МБ · до 6 загруженных видео / 24 МБ",
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
      introVideoTooLarge: "Видео больше 4 МБ; сожмите его и повторите",
      introVideoBadDuration: "Видео длиннее 15 секунд; сократите его",
      introVideoBadFormat: "Поддерживаются только изображения JPG/PNG/WebP или видео MP4/MOV 16:9 с кодеком, который декодирует этот ТВ",
      introVideoInvalid: "Видео не читается или формат не поддерживается",
      introVideoFull: "Библиотека загрузок заполнена (до 6 элементов / 24 МБ); сначала удалите один",
      introVideoNoSpace: "На ТВ недостаточно места",
      introVideoMeta: "{size} МБ · {seconds} с · {width}×{height}",
      introVideoListTitle: "Видеотека",
      introVideoUploadHint: "Можно выбрать несколько видео сразу. После загрузки нажмите «Использовать».",
      introVideoUploadProgress: "Загрузка {index}/{total}: {name} · {percent}%",
      introVideoBatchDone: "Загружено обоев: {added}",
      introVideoBatchFailed: "Не загружено элементов: {failed}. Причина: {reason}",
      introVideoBatchFull: "В библиотеке помещается {max} элементов; в этот раз загружено {n}",
      introVideoUse: "Использовать",
      introVideoHevc: "Этот ТВ не может декодировать H.265/HEVC; преобразуйте видео в H.264 и повторите.",
      introVideoUnsupportedCodec: "Этот ТВ не может декодировать этот видеокодек; преобразуйте видео в H.264 и повторите.",
      mediaPageTitle: "Обои детского режима",
      mediaCurrentTitle: "Текущий выбор",
      mediaModulesNote: "Не влияют друг на друга",
      mediaRoleQuiz: "Обои теста",
      mediaRoleLock: "Обои детского режима",
      mediaRoleBoth: "Оба",
      mediaRoleQuizHint: "Показывается при запуске теста",
      mediaRoleLockHint: "Показывается на экране блокировки детского режима и на рабочем столе самодисциплины; видео повторяется без звука",
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
      mediaImageRoleNote: "Изображения можно использовать только как обои детского режима, но не как вступление теста.",
      mediaIsolationNote: "Пока ничего не выбрано, используются обои детского режима по умолчанию. В родительском режиме всегда остаются обои Aerial.",
      mediaChoose: "Выбрать",
      mediaChange: "Заменить",
      mediaCustomized: "Настроено",
      mediaTabCloud: "Облако",
      mediaTabLocal: "Локальные",
      mediaOfficialHint: "↓ — скачать · ✓ — загружено; до 8 элементов — сначала удалите один, чтобы скачать новый.",
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
      mediaPreviewHint: "Здесь показана только обложка; видео воспроизводится на телевизоре после загрузки, изображения становятся статичными обоями.",
      mediaDownloadHint: "Скачайте, чтобы использовать как вступление теста или обои детского режима.",
      mediaDownload: "Скачать эти обои",
      mediaDownloadedHint: "Загружено. Выберите в разделе «Текущий выбор» выше.",
      mediaLocalReadyHint: "Загружено с телефона. Выберите в разделе «Текущий выбор» выше.",
      mediaDownloadDone: "Загружено. Выберите в разделе «Текущий выбор».",
      mediaPickerTitle: "Выберите {module}",
      mediaPickerHint: "Изменяется только этот модуль.",
      mediaPickerHintQuiz: "Влияет только на вступление теста; здесь показаны только видео.",
      mediaPickerEmpty: "Загруженных обоев пока нет",
      mediaPickerEmptyVideo: "Пока нет загруженных видео.",
      mediaPickerGoLibrary: "Открыть облачную библиотеку",
      mediaCurrentBadge: "Текущий",
      mediaRetryDownload: "Повторить загрузку",
      mediaUpdateLocal: "Обновить локальную копию",
      mediaUpdateHint: "Доступна новая версия. Старая копия на телевизоре продолжает работать.",
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
      mediaDeleteText: "Локальная копия будет удалена; роли, использующие её, вернутся к обоям детского режима по умолчанию. Запись в облачной библиотеке не удаляется.",
      mediaDeleted: "Обои удалены",
      mediaSelected: "Настройки обоев обновлены",
      mediaDownloadStarted: "Загрузка началась",
      mediaDownloading: "Загрузка…",
      mediaCancelled: "Загрузка отменена",
      mediaNotFound: "Обои не найдены или больше недоступны",
      mediaNotReady: "Обои ещё не загружены",
      mediaBusy: "Загружаются другие обои; повторите позже",
      mediaRoleInvalid: "Обои нельзя использовать для выбранной роли",
      mediaLibraryFull: "Загружено 8 элементов; сначала удалите один, чтобы скачать новый",
      mediaNoSpace: "На ТВ недостаточно места",
      mediaDeleteFailed: "Не удалось удалить; повторите",
      mediaDownloadFailed: "Ошибка загрузки; проверьте сеть ТВ и повторите",
      mediaLocalHint: "Хранится только на ТВ; не загружается в облако",
      mediaLocalEmpty: "Загруженных обоев пока нет",
      mediaUploadHint: "JPG/PNG/WebP ≤12 МБ; или MP4/MOV 16:9 ≤15 с / ≤4 МБ (нужен декодер ТВ) · до 6 элементов / 24 МБ",
      mediaLimit: "Облачная библиотека: до 8 загрузок (изображения и видео вместе) · Локально: до 6. Если места нет, сначала удалите один элемент.",
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
      timeAndApps: "Час і застосунки",
      timeAndAppsSummary: "По одному вікну в будні та вихідні · {apps}",
      localSettings: "Цей телевізор",
      pinSet: "Задано",
      pinUnset: "Не задано",
      quizAndAi: "Банк питань і ШІ",
      bankMaintenance: "Керування банками",
      bankMaintenanceHint: "Очищення банку курсу/ШІ та власних банків, налаштування вікторини",
      otherOptions: "Інше",
      privacyHint: "Дані та дозволи на цьому телевізорі",
      aiTitle: "ШІ-питання",
      privacyInfo: "Конфіденційність",
      downloadParentApp: "Завантажити застосунок батьків",
      downloadParentAppHint: "Android 7.0 і вище",
      viewAction: "Відкрити",
      manageAction: "Керувати",
      logoutAccount: "Вийти",
      timePageHint: "Список застосунків діє одразу; вікна, ліміти й перерви — з наступного сеансу",
      tvInputSources: "Джерела ТБ",
      tvInputSourcesHint: "Впливає лише на входи на головному екрані, це не жорстке блокування",
      singleWindowHint: "Один інтервал на день",
      exemptSection: "Виключні періоди",
      exemptHint: "У ці періоди обмеження не діють (час бабусь і дідусів)",
      exemptAdd: "+ Додати період",
      noExempt: "Періоди не задані",
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
      appScope: "Застосунки",
      entertainmentApps: "Розваги",
      studyApps: "Навчальні застосунки",
      allApps: "Усі застосунки",
      appsSelected: "Вибрано застосунків: {n}",
      noEntertainment: "Нічого не вибрано (розваги заборонені)",
      studyNone: "Не задано (навчального режиму немає)",
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
      timing: "Режим часу",
      autoMode: "Авто",
      manualMode: "Вручну",
      activationHint: "Перемикання завершить поточний сеанс; новий не почнеться автоматично",
      unlockFineprint: "Розблокування спільне з телевізором: та сама перевірка батьків, що й на екрані блокування ТБ, діє тут і знову блокується через 10 хвилин.",
      pairHint: "Код сполучення діє 5 хвилин. Після 5 помилок створіть новий код на ТБ.",
      passwordHint: "Для зміни пароля батьків потрібен поточний пароль; зміни застосовуються одразу.",
      pinLockedRetry: "Забагато неправильних паролів; повторіть через {n} с",
      sensitivePinTitle: "Підтвердьте пароль батьків",
      sensitivePinLeadRules: "Введіть пароль батьків, щоб змінити час і застосунки",
      sensitivePinLeadTakeOver: "Введіть пароль батьків, щоб тимчасово вийти з дитячого режиму",
      sensitivePinDefault: "Спочатку змініть початковий пароль батьків на телевізорі",
      sensitivePinExpired: "Підтвердження минуло; введіть пароль ще раз",
      sensitivePinRequired: "Спочатку підтвердьте пароль батьків",
      confirmAutoUpdateOffTitle: "Вимкнути автооновлення?",
      confirmAutoUpdateOffBody: "Телевізор перестане автоматично отримувати нові версії; оновлення доведеться встановлювати вручну.",
      confirmAutoUpdateOffAction: "Вимкнути",
      activationInvalid: "Режим часу не змінився; оновіть і повторіть",
      remoteRefreshFailed: "Не вдалося оновити віддалений стан; перевірте мережу ТБ і повторіть",
      activationConfirmTitle: "Перемкнути режим часу?",
      activationConfirmText: "Поточний сеанс завершиться зараз, новий автоматично не почнеться.",
      activationConfirm: "Перемкнути",
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
      sessionLimit: "Ліміт сеансу",
      rest: "Перерва",
      buffer: "Буфер вимкнення екрана",
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
      appPickerStudyHint: "Якщо нічого не вибрати, навчального режиму немає; навчальні застосунки не обмежені часом розваг",
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
      introVideoLimit: "MP4/MOV 16:9 · H.264/H.265 та ін. (потрібен декодер ТБ) · ≤15 с / ≤4 МБ · до 6 завантажених відео / 24 МБ",
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
      introVideoTooLarge: "Відео більше 4 МБ; стисніть його й повторіть",
      introVideoBadDuration: "Відео довше 15 секунд; скоротіть його",
      introVideoBadFormat: "Підтримуються лише зображення JPG/PNG/WebP або відео MP4/MOV 16:9 з кодеком, який декодує цей ТБ",
      introVideoInvalid: "Відео не читається або формат не підтримується",
      introVideoFull: "Бібліотека завантажень заповнена (до 6 елементів / 24 МБ); спершу видаліть один",
      introVideoNoSpace: "На ТБ недостатньо місця",
      introVideoMeta: "{size} МБ · {seconds} с · {width}×{height}",
      introVideoListTitle: "Відеотека",
      introVideoUploadHint: "Можна вибрати кілька відео одразу. Після завантаження натисніть «Використати».",
      introVideoUploadProgress: "Завантаження {index}/{total}: {name} · {percent}%",
      introVideoBatchDone: "Завантажено шпалер: {added}",
      introVideoBatchFailed: "Не завантажено елементів: {failed}. Причина: {reason}",
      introVideoBatchFull: "У бібліотеці вміщується {max} елементів; цього разу завантажено {n}",
      introVideoUse: "Використати",
      introVideoHevc: "Цей ТБ не може декодувати H.265/HEVC; перетворіть відео на H.264 і повторіть.",
      introVideoUnsupportedCodec: "Цей ТБ не може декодувати цей відеокодек; перетворіть відео на H.264 і повторіть.",
      mediaPageTitle: "Шпалери дитячого режиму",
      mediaCurrentTitle: "Поточний вибір",
      mediaModulesNote: "Не впливають одне на одного",
      mediaRoleQuiz: "Шпалери тесту",
      mediaRoleLock: "Шпалери дитячого режиму",
      mediaRoleBoth: "Обидва",
      mediaRoleQuizHint: "Показується на початку тесту",
      mediaRoleLockHint: "Показується на екрані блокування дитячого режиму та на робочому столі самодисципліни; відео повторюється без звуку",
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
      mediaImageRoleNote: "Зображення можна використовувати лише як шпалери дитячого режиму, але не як вступ тесту.",
      mediaIsolationNote: "Доки нічого не вибрано, використовуються типові шпалери дитячого режиму. У батьківському режимі завжди залишаються шпалери Aerial.",
      mediaChoose: "Вибрати",
      mediaChange: "Замінити",
      mediaCustomized: "Налаштовано",
      mediaTabCloud: "Хмара",
      mediaTabLocal: "Локальні",
      mediaOfficialHint: "↓ — завантажити · ✓ — завантажено; до 8 елементів — спершу видаліть один, щоб завантажити новий.",
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
      mediaPreviewHint: "Тут показано лише обкладинку; відео відтворюється на телевізорі після завантаження, зображення стають статичними шпалерами.",
      mediaDownloadHint: "Завантажте, щоб використовувати як вступ тесту або шпалери дитячого режиму.",
      mediaDownload: "Завантажити ці шпалери",
      mediaDownloadedHint: "Завантажено. Виберіть у розділі «Поточний вибір» вище.",
      mediaLocalReadyHint: "Завантажено з телефона. Виберіть у розділі «Поточний вибір» вище.",
      mediaDownloadDone: "Завантажено. Виберіть у розділі «Поточний вибір».",
      mediaPickerTitle: "Виберіть {module}",
      mediaPickerHint: "Змінюється лише цей модуль.",
      mediaPickerHintQuiz: "Впливає лише на вступ тесту; тут показано лише відео.",
      mediaPickerEmpty: "Завантажених шпалер ще немає",
      mediaPickerEmptyVideo: "Ще немає завантажених відео.",
      mediaPickerGoLibrary: "Відкрити хмарну бібліотеку",
      mediaCurrentBadge: "Поточний",
      mediaRetryDownload: "Повторити завантаження",
      mediaUpdateLocal: "Оновити локальну копію",
      mediaUpdateHint: "Доступна нова версія. Стара копія на телевізорі працює далі.",
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
      mediaDeleteText: "Локальну копію буде видалено; ролі, що її використовують, повернуться до типових шпалер дитячого режиму. Запис у хмарній бібліотеці не видаляється.",
      mediaDeleted: "Шпалери видалено",
      mediaSelected: "Налаштування шпалер оновлено",
      mediaDownloadStarted: "Завантаження почалося",
      mediaDownloading: "Завантаження…",
      mediaCancelled: "Завантаження скасовано",
      mediaNotFound: "Шпалери відсутні або більше недоступні",
      mediaNotReady: "Шпалери ще не завантажено",
      mediaBusy: "Завантажуються інші шпалери; повторіть пізніше",
      mediaRoleInvalid: "Шпалери не можна використати для вибраної ролі",
      mediaLibraryFull: "Завантажено 8 елементів; спершу видаліть один, щоб завантажити новий",
      mediaNoSpace: "На ТБ недостатньо місця",
      mediaDeleteFailed: "Не вдалося видалити; повторіть",
      mediaDownloadFailed: "Помилка завантаження; перевірте мережу ТБ і повторіть",
      mediaLocalHint: "Зберігається лише на ТБ; не завантажується в хмару",
      mediaLocalEmpty: "Завантажених шпалер ще немає",
      mediaUploadHint: "JPG/PNG/WebP ≤12 МБ; або MP4/MOV 16:9 ≤15 с / ≤4 МБ (потрібен декодер ТБ) · до 6 елементів / 24 МБ",
      mediaLimit: "Хмарна бібліотека: до 8 завантажень (зображення й відео разом) · Локально: до 6. Якщо місця немає, спершу видаліть один елемент.",
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
      timeAndApps: "Уақыт және қолданбалар",
      timeAndAppsSummary: "Жұмыс күндері мен демалыста бір уақыт терезесінен · {apps}",
      localSettings: "Осы теледидар",
      pinSet: "Орнатылған",
      pinUnset: "Орнатылмаған",
      quizAndAi: "Сұрақ банкі және ИИ",
      bankMaintenance: "Банктерді басқару",
      bankMaintenanceHint: "Курс/AI және жеке банктерді тазалау, викторина баптаулары",
      otherOptions: "Басқа",
      privacyHint: "Осы теледидардағы деректер мен рұқсаттар",
      aiTitle: "AI сұрақтары",
      privacyInfo: "Құпиялық",
      downloadParentApp: "Ата-ана қолданбасын жүктеу",
      downloadParentAppHint: "Android 7.0 және одан жоғары",
      viewAction: "Ашу",
      manageAction: "Басқару",
      logoutAccount: "Шығу",
      timePageHint: "Қолданбалар тізімі бірден қолданылады; уақыт терезелері, шектеулер мен үзілістер келесі сеанстан бастап әсер етеді",
      tvInputSources: "ТВ кірістері",
      tvInputSourcesHint: "Тек басты экрандағы кірістерге әсер етеді, бұл қатаң құлып емес",
      singleWindowHint: "Күніне бір уақыт аралығы",
      exemptSection: "Шешімсіз кезеңдер",
      exemptHint: "Бұл кезеңдерде шектеулер жұмыс істемейді (әжелер мен аталар уақыты)",
      exemptAdd: "+ Кезең қосу",
      noExempt: "Кезеңдер орнатылмаған",
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
      appScope: "Қолданбалар",
      entertainmentApps: "Ойын-сауық қолданбалары",
      studyApps: "Оқу қолданбалары",
      allApps: "Барлық қолданбалар",
      appsSelected: "{n} қолданба таңдалды",
      noEntertainment: "Ештеңе таңдалмаған (ойын-сауыққа рұқсат жоқ)",
      studyNone: "Орнатылмаған (оқу режимі жоқ)",
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
      timing: "Уақыт режимі",
      autoMode: "Авто",
      manualMode: "Қолмен",
      activationHint: "Ауыстыру ағымдағы сеансты аяқтайды; жаңасы өздігінен басталмайды",
      unlockFineprint: "Ашу теледидармен ортақ: ТВ құлып экранындағы ата-ана тексеруі осында да әсер етеді және 10 минуттан кейін қайта құлыпталады.",
      pairHint: "Жұптау коды 5 минут жарамды. 5 рет қате енгізсеңіз, ТВ-де жаңа код жасаңыз.",
      passwordHint: "Ата-ана құпиясөзін өзгерту үшін ағымдағы құпиясөз қажет; өзгеріс бірден әсер етеді.",
      pinLockedRetry: "Қате құпиясөз тым көп; {n} секундтан кейін қайталаңыз",
      sensitivePinTitle: "Ата-ана құпиясөзін растаңыз",
      sensitivePinLeadRules: "Уақыт пен қолданбаларды өңдеу үшін ата-ана құпиясөзін енгізіңіз",
      sensitivePinLeadTakeOver: "Бала режимінен уақытша шығу үшін ата-ана құпиясөзін енгізіңіз",
      sensitivePinDefault: "Алдымен теледидарда бастапқы ата-ана құпиясөзін өзгертіңіз",
      sensitivePinExpired: "Растау мерзімі бітті; құпиясөзді қайта енгізіңіз",
      sensitivePinRequired: "Алдымен ата-ана құпиясөзін растаңыз",
      confirmAutoUpdateOffTitle: "Автоматты жаңартуды өшіру керек пе?",
      confirmAutoUpdateOffBody: "Теледидар жаңа нұсқаларды автоматты түрде алмайды; жаңартуды қолмен орнату қажет.",
      confirmAutoUpdateOffAction: "Өшіру",
      activationInvalid: "Уақыт режимі ауыспады; жаңартып, қайталаңыз",
      remoteRefreshFailed: "Қашықтан күйді жаңарту мүмкін болмады; ТВ желісін тексеріп қайталаңыз",
      activationConfirmTitle: "Уақыт режимін ауыстыру керек пе?",
      activationConfirmText: "Ағымдағы сеанс қазір аяқталады, жаңасы өздігінен басталмайды.",
      activationConfirm: "Ауыстыру",
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
      sessionLimit: "Сеанс шектеуі",
      rest: "Үзіліс",
      buffer: "Экранды өшіру буфері",
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
      appPickerStudyHint: "Ештеңе таңдамасаңыз, оқу режимі жоқ; оқу қолданбалары ойын-сауық уақытымен шектелмейді",
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
      introVideoLimit: "16:9 MP4/MOV · H.264/H.265 және т.б. (ТВ декодері қажет) · ≤15 с / ≤4 МБ · жүктелген 6 бейнеге / 24 МБ-қа дейін",
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
      introVideoTooLarge: "Бейне 4 МБ-тан үлкен; оны сығымдап, қайталаңыз",
      introVideoBadDuration: "Бейне 15 секундтан ұзын; оны қысқартыңыз",
      introVideoBadFormat: "Тек JPG/PNG/WebP суреттері немесе 16:9 MP4/MOV бейнелері қолданылады; бұл ТВ бейне кодын декодтай алуы керек",
      introVideoInvalid: "Бейне оқылмайды немесе пішімі қолданылмайды",
      introVideoFull: "Жүктеу кітапханасы толды (6 элементке / 24 МБ-қа дейін); алдымен біреуін жойыңыз",
      introVideoNoSpace: "ТВ-де орын жеткіліксіз",
      introVideoMeta: "{size} МБ · {seconds} с · {width}×{height}",
      introVideoListTitle: "Бейнекітапша",
      introVideoUploadHint: "Бірнеше бейнені бірден таңдауға болады. Жүктегеннен кейін «Қолдану» түймесін басыңыз.",
      introVideoUploadProgress: "Жүктелуде {index}/{total}: {name} · {percent}%",
      introVideoBatchDone: "{added} тұсқағаз жүктелді",
      introVideoBatchFailed: "{failed} элемент жүктелмеді: {reason}",
      introVideoBatchFull: "Кітапханаға {max} элемент сыяды; бұл жолы {n} жүктелді",
      introVideoUse: "Қолдану",
      introVideoHevc: "Бұл ТВ H.265/HEVC декодтай алмайды; бейнені H.264 етіп түрлендіріп, қайталаңыз.",
      introVideoUnsupportedCodec: "Бұл ТВ бұл бейне кодын декодтай алмайды; бейнені H.264 етіп түрлендіріп, қайталаңыз.",
      mediaPageTitle: "Бала режимінің тұсқағаздары",
      mediaCurrentTitle: "Ағымдағы таңдау",
      mediaModulesNote: "Бір-біріне әсер етпейді",
      mediaRoleQuiz: "Тест кіріспесі тұсқағазы",
      mediaRoleLock: "Бала режимінің тұсқағазы",
      mediaRoleBoth: "Екеуі де",
      mediaRoleQuizHint: "Тест басталғанда көрсетіледі",
      mediaRoleLockHint: "Бала режимінің құлып экранында және өзіндік тәртіп жұмыс үстелінде көрсетіледі; бейне дыбыссыз қайталанады",
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
      mediaImageRoleNote: "Суретті тек бала режимінің тұсқағазы ретінде қолдануға болады, тест кіріспесі ретінде емес.",
      mediaIsolationNote: "Таңдалмағанша бала режимінің әдепкі тұсқағазы қолданылады. Ата-ана режимінде әрқашан Aerial тұсқағазы қалады.",
      mediaChoose: "Таңдау",
      mediaChange: "Ауыстыру",
      mediaCustomized: "Бапталған",
      mediaTabCloud: "Бұлт",
      mediaTabLocal: "Жергілікті",
      mediaOfficialHint: "↓ — жүктеу · ✓ — жүктелді; 8 элементке дейін — жаңасын жүктеу үшін алдымен біреуін жойыңыз.",
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
      mediaPreviewHint: "Мұнда тек мұқаба көрсетіледі; бейне жүктелгеннен кейін теледидарда ойнатылады, суреттер статикалық тұсқағазға айналады.",
      mediaDownloadHint: "Тест кіріспесі немесе бала режимінің тұсқағазы ретінде қолдану үшін жүктеңіз.",
      mediaDownload: "Осы тұсқағазды жүктеу",
      mediaDownloadedHint: "Жүктелді. Жоғарыдағы «Ағымдағы таңдау» бөлімінде таңдаңыз.",
      mediaLocalReadyHint: "Телефоннан жүктелді. Жоғарыдағы «Ағымдағы таңдау» бөлімінде таңдаңыз.",
      mediaDownloadDone: "Жүктелді. «Ағымдағы таңдау» бөлімінде таңдаңыз.",
      mediaPickerTitle: "{module} таңдаңыз",
      mediaPickerHint: "Тек осы модуль өзгереді.",
      mediaPickerHintQuiz: "Тек тест кіріспесіне әсер етеді; мұнда тек бейнелер көрсетіледі.",
      mediaPickerEmpty: "Әзірге жүктелген тұсқағаз жоқ",
      mediaPickerEmptyVideo: "Әзірге жүктелген бейне жоқ.",
      mediaPickerGoLibrary: "Бұлттық кітапхананы ашу",
      mediaCurrentBadge: "Ағымдағы",
      mediaRetryDownload: "Жүктеуді қайталау",
      mediaUpdateLocal: "Жергілікті көшірмені жаңарту",
      mediaUpdateHint: "Жаңа нұсқа бар. Теледидардағы ескі көшірме жұмысын жалғастырады.",
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
      mediaDeleteText: "Жергілікті көшірме жойылады; оны қолданатын рөлдер бала режимінің әдепкі тұсқағазына оралады. Бұлттық кітапхана жазбасы жойылмайды.",
      mediaDeleted: "Тұсқағаз жойылды",
      mediaSelected: "Тұсқағаз параметрлері жаңартылды",
      mediaDownloadStarted: "Жүктеу басталды",
      mediaDownloading: "Жүктелуде…",
      mediaCancelled: "Жүктеу тоқтатылды",
      mediaNotFound: "Тұсқағаз табылмайды немесе енді қолжетімсіз",
      mediaNotReady: "Тұсқағаз әлі жүктелмеген",
      mediaBusy: "Басқа тұсқағаз жүктелуде; кейінірек қайталаңыз",
      mediaRoleInvalid: "Тұсқағазды таңдалған рөлге қолдану мүмкін емес",
      mediaLibraryFull: "8 элемент жүктелді; тағысын жүктеу үшін алдымен біреуін жойыңыз",
      mediaNoSpace: "ТВ-де орын жеткіліксіз",
      mediaDeleteFailed: "Жою сәтсіз; қайталаңыз",
      mediaDownloadFailed: "Жүктеу сәтсіз; ТВ желісін тексеріп, қайталаңыз",
      mediaLocalHint: "Тек ТВ-де сақталады; бұлтқа жүктелмейді",
      mediaLocalEmpty: "Әзірге жүктелген тұсқағаз жоқ",
      mediaUploadHint: "JPG/PNG/WebP ≤12 МБ; немесе 16:9 MP4/MOV ≤15 с / ≤4 МБ (ТВ декодері қажет) · 6 элементке / 24 МБ-қа дейін",
      mediaLimit: "Бұлттық кітапхана: 8 жүктеуге дейін (сурет пен бейне бірге) · Жергілікті: 6-ға дейін. Орын болмаса, алдымен бір элементті жойыңыз.",
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
   * V31 remote control copy. Kept separate from TEXTS so the control surface can grow without
   * touching the configuration dictionaries; t() falls back here when TEXTS has no key.
   */
  const CONTROL_TEXTS = {
    zh: {
      controlTitle: "控制面板",
      controlChildSection: "儿童模式",
      controlTimerSection: "计时",
      controlLocalOnly: "仅在本机局域网生效，电视须为本地儿童模式。",
      controlStatusChildOff: "儿童模式未开启",
      controlStatusEnforcing: "儿童模式 · 已锁定",
      controlStatusTimer: "计时中 · 剩余 {time}",
      controlStatusRest: "休息中 · 剩余 {time}",
      controlStatusPending: "等待孩子确认家长授权",
      controlStatusOverride: "已临时退出 · 剩余 {time}",
      controlStatusOverrideNoLimit: "已临时退出儿童模式",
      controlStatusStudy: "学习模式",
      controlStatusQuiz: "答题挑战进行中",
      controlTakeOverTitle: "临时退出儿童模式",
      controlTakeOverHint: "立即解锁电视，家长使用不计时",
      controlTakeOverConfirmHint: "计时或答题中，需要二次确认",
      controlTakeOverAlready: "电视已临时退出儿童模式",
      controlEnterTitle: "进入儿童模式",
      controlEnterHint: "结束临时退出，恢复儿童守护",
      controlEnterAlready: "电视已在儿童模式",
      controlStartTitle: "单次计时",
      controlStartHint: "输入家长密码并选择时长",
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
      controlConfirmEndBody: "已用时间会保留。自动模式会先休息，手动模式回到等待家长安排。",
      controlConfirmEndAction: "退出计时",
      controlConfirmTakeTimerTitle: "临时退出儿童模式？",
      controlConfirmTakeTimerBody: "当前计时会结束，已用时间会保留，本次休息会跳过。",
      controlConfirmTakeQuizTitle: "退出挑战并临时退出儿童模式？",
      controlConfirmTakeQuizBody: "本轮进度和已获得的时间会清空，电视将临时解锁。",
      controlConfirmTakeAction: "临时退出",
      controlConfirmQuizAction: "退出并解锁",
      controlManualTitle: "单次计时",
      controlManualLead: "输入家长密码并选择本次时长。",
      controlManualDuration: "本次时长",
      controlManualMinutesValue: "{n} 分钟",
      controlManualStart: "开始",
      controlStartBlocked: "电视当前不能开始单次计时。"
    },
    en: {
      controlTitle: "Control panel",
      controlChildSection: "Child mode",
      controlTimerSection: "Timing",
      controlLocalOnly: "Works only on this local network. The TV must be in local child mode.",
      controlStatusChildOff: "Child mode is off",
      controlStatusEnforcing: "Child mode · locked",
      controlStatusTimer: "Timing · {time} left",
      controlStatusRest: "Resting · {time} left",
      controlStatusPending: "Waiting for the child to confirm the parent grant",
      controlStatusOverride: "Temporarily unlocked · {time} left",
      controlStatusOverrideNoLimit: "Temporarily unlocked",
      controlStatusStudy: "Study mode",
      controlStatusQuiz: "Quiz challenge in progress",
      controlTakeOverTitle: "Temporarily leave child mode",
      controlTakeOverHint: "Unlock the TV now; parent use is not timed",
      controlTakeOverConfirmHint: "Timing or a quiz is running; confirmation is required",
      controlTakeOverAlready: "The TV is already temporarily unlocked",
      controlEnterTitle: "Enter child mode",
      controlEnterHint: "End the temporary unlock and restore the child guard",
      controlEnterAlready: "The TV is already in child mode",
      controlStartTitle: "One-time timing",
      controlStartHint: "Enter the parent password and choose a duration",
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
      controlConfirmEndBody: "Used time is kept. Automatic mode starts the usual rest; manual mode returns to waiting for a parent.",
      controlConfirmEndAction: "Exit",
      controlConfirmTakeTimerTitle: "Temporarily leave child mode?",
      controlConfirmTakeTimerBody: "The current timer will end, used time is kept, and this rest is skipped.",
      controlConfirmTakeQuizTitle: "Exit the quiz and temporarily leave child mode?",
      controlConfirmTakeQuizBody: "This round's progress and earned time will be cleared, and the TV will be temporarily unlocked.",
      controlConfirmTakeAction: "Temporarily exit",
      controlConfirmQuizAction: "Exit and unlock",
      controlManualTitle: "One-time timing",
      controlManualLead: "Enter the parent password and choose the duration.",
      controlManualDuration: "Session length",
      controlManualMinutesValue: "{n} min",
      controlManualStart: "Start",
      controlStartBlocked: "A one-time session cannot start on the TV right now."
    },
    ru: {
      controlTitle: "Панель управления",
      controlChildSection: "Детский режим",
      controlTimerSection: "Таймер",
      controlLocalOnly: "Работает только в локальной сети. Телевизор должен быть в локальном детском режиме.",
      controlStatusChildOff: "Детский режим выключен",
      controlStatusEnforcing: "Детский режим · заблокирован",
      controlStatusTimer: "Таймер · осталось {time}",
      controlStatusRest: "Перерыв · осталось {time}",
      controlStatusPending: "Ожидание подтверждения ребёнком родительского доступа",
      controlStatusOverride: "Временно разблокирован · осталось {time}",
      controlStatusOverrideNoLimit: "Временно разблокирован",
      controlStatusStudy: "Режим учёбы",
      controlStatusQuiz: "Идёт викторина",
      controlTakeOverTitle: "Временно выйти из детского режима",
      controlTakeOverHint: "Разблокировать телевизор сейчас; время родителя не учитывается",
      controlTakeOverConfirmHint: "Идёт таймер или викторина; нужно подтверждение",
      controlTakeOverAlready: "Телевизор уже временно разблокирован",
      controlEnterTitle: "Войти в детский режим",
      controlEnterHint: "Завершить временную разблокировку и вернуть защиту",
      controlEnterAlready: "Телевизор уже в детском режиме",
      controlStartTitle: "Разовый таймер",
      controlStartHint: "Введите пароль родителя и выберите длительность",
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
      controlConfirmEndBody: "Использованное время сохранится. В автоматическом режиме начнётся обычный перерыв; в ручном — ожидание родителя.",
      controlConfirmEndAction: "Выйти",
      controlConfirmTakeTimerTitle: "Временно выйти из детского режима?",
      controlConfirmTakeTimerBody: "Текущий таймер завершится, использованное время сохранится, этот перерыв будет пропущен.",
      controlConfirmTakeAction: "Выйти",
      controlConfirmTakeQuizTitle: "Выйти из викторины и временно выйти из детского режима?",
      controlConfirmTakeQuizBody: "Прогресс этого раунда и полученное время будут сброшены, телевизор временно разблокируется.",
      controlConfirmQuizAction: "Выйти и разблокировать",
      controlManualTitle: "Разовый таймер",
      controlManualLead: "Введите пароль родителя и выберите длительность.",
      controlManualDuration: "Длительность",
      controlManualMinutesValue: "{n} мин",
      controlManualStart: "Начать",
      controlStartBlocked: "Сейчас нельзя запустить разовый таймер."
    },
    uk: {
      controlTitle: "Панель керування",
      controlChildSection: "Дитячий режим",
      controlTimerSection: "Таймер",
      controlLocalOnly: "Працює лише в локальній мережі. Телевізор має бути в локальному дитячому режимі.",
      controlStatusChildOff: "Дитячий режим вимкнено",
      controlStatusEnforcing: "Дитячий режим · заблоковано",
      controlStatusTimer: "Таймер · залишилося {time}",
      controlStatusRest: "Перерва · залишилося {time}",
      controlStatusPending: "Очікування підтвердження дитиною батьківського доступу",
      controlStatusOverride: "Тимчасово розблоковано · залишилося {time}",
      controlStatusOverrideNoLimit: "Тимчасово розблоковано",
      controlStatusStudy: "Режим навчання",
      controlStatusQuiz: "Триває вікторина",
      controlTakeOverTitle: "Тимчасово вийти з дитячого режиму",
      controlTakeOverHint: "Розблокувати телевізор зараз; час батьків не враховується",
      controlTakeOverConfirmHint: "Триває таймер або вікторина; потрібне підтвердження",
      controlTakeOverAlready: "Телевізор уже тимчасово розблоковано",
      controlEnterTitle: "Увійти в дитячий режим",
      controlEnterHint: "Завершити тимчасове розблокування й повернути захист",
      controlEnterAlready: "Телевізор уже в дитячому режимі",
      controlStartTitle: "Разовий таймер",
      controlStartHint: "Введіть пароль батьків і виберіть тривалість",
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
      controlConfirmEndBody: "Використаний час збережеться. В автоматичному режимі почнеться звичайна перерва; у ручному — очікування батьків.",
      controlConfirmEndAction: "Вийти",
      controlConfirmTakeTimerTitle: "Тимчасово вийти з дитячого режиму?",
      controlConfirmTakeTimerBody: "Поточний таймер завершиться, використаний час збережеться, ця перерва буде пропущена.",
      controlConfirmTakeAction: "Вийти",
      controlConfirmTakeQuizTitle: "Вийти з вікторини й тимчасово вийти з дитячого режиму?",
      controlConfirmTakeQuizBody: "Прогрес цього раунду та отриманий час буде очищено, телевізор тимчасово розблокується.",
      controlConfirmQuizAction: "Вийти й розблокувати",
      controlManualTitle: "Разовий таймер",
      controlManualLead: "Введіть пароль батьків і виберіть тривалість.",
      controlManualDuration: "Тривалість",
      controlManualMinutesValue: "{n} хв",
      controlManualStart: "Почати",
      controlStartBlocked: "Зараз не можна запустити разовий таймер."
    },
    kk: {
      controlTitle: "Басқару панелі",
      controlChildSection: "Бала режимі",
      controlTimerSection: "Таймер",
      controlLocalOnly: "Тек жергілікті желіде жұмыс істейді. Теледидар жергілікті бала режимінде болуы керек.",
      controlStatusChildOff: "Бала режимі өшірулі",
      controlStatusEnforcing: "Бала режимі · құлыпталған",
      controlStatusTimer: "Таймер · {time} қалды",
      controlStatusRest: "Демалыс · {time} қалды",
      controlStatusPending: "Баланың ата-ана рұқсатын растауын күту",
      controlStatusOverride: "Уақытша ашылған · {time} қалды",
      controlStatusOverrideNoLimit: "Уақытша ашылған",
      controlStatusStudy: "Оқу режимі",
      controlStatusQuiz: "Викторина жүріп жатыр",
      controlTakeOverTitle: "Бала режимінен уақытша шығу",
      controlTakeOverHint: "Теледидарды қазір ашу; ата-ана уақыты есептелмейді",
      controlTakeOverConfirmHint: "Таймер немесе викторина жүріп жатыр; растау қажет",
      controlTakeOverAlready: "Теледидар уақытша ашылған",
      controlEnterTitle: "Бала режиміне кіру",
      controlEnterHint: "Уақытша ашуды аяқтап, бала қорғанысын қалпына келтіру",
      controlEnterAlready: "Теледидар бала режимінде",
      controlStartTitle: "Бір реттік таймер",
      controlStartHint: "Ата-ана құпиясөзін енгізіп, ұзақтықты таңдаңыз",
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
      controlConfirmEndBody: "Пайдаланылған уақыт сақталады. Автоматты режимде әдеттегі демалыс басталады; қол режимінде ата-ананы күтуге оралады.",
      controlConfirmEndAction: "Шығу",
      controlConfirmTakeTimerTitle: "Бала режимінен уақытша шығу керек пе?",
      controlConfirmTakeTimerBody: "Ағымдағы таймер аяқталады, пайдаланылған уақыт сақталады, бұл демалыс өткізіледі.",
      controlConfirmTakeAction: "Шығу",
      controlConfirmTakeQuizTitle: "Викторинадан шығып, бала режимінен уақытша шығу керек пе?",
      controlConfirmTakeQuizBody: "Осы раундтың прогресі мен алынған уақыт тазартылады, теледидар уақытша ашылады.",
      controlConfirmQuizAction: "Шығып, ашу",
      controlManualTitle: "Бір реттік таймер",
      controlManualLead: "Ата-ана құпиясөзін енгізіп, ұзақтықты таңдаңыз.",
      controlManualDuration: "Ұзақтық",
      controlManualMinutesValue: "{n} мин",
      controlManualStart: "Бастау",
      controlStartBlocked: "Қазір бір реттік таймерді бастау мүмкін емес."
    }
  };

  const successKeys = {
    SAVED: "saved",
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
    RULES_OVERLAP: "rulesOverlap",
    RULES_TOO_MANY_WINDOWS: "rulesTooMany",
    RULES_INVALID_APPS: "rulesApps",
    RULES_INVALID_STUDY_APPS: "rulesApps",
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
    INVALID_ACTIVATION: "activationInvalid",
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
    INTRO_VIDEO_LIBRARY_FULL: "introVideoFull",
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
    MEDIA_LIBRARY_FULL: "mediaLibraryFull",
    MEDIA_NO_SPACE: "mediaNoSpace",
    MEDIA_DELETE_FAILED: "mediaDeleteFailed",
    MEDIA_DOWNLOAD_FAILED: "mediaDownloadFailed",
    MEDIA_BAD_FORMAT: "mediaBadFormat",
    MEDIA_TOO_LARGE: "mediaTooLarge",
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
  let subpage = null;
  let personalizeLoading = false;
  let personalizeRefreshing = false;
  let mediaPollTimer = 0;
  let mediaSheetMode = null;
  let mediaSheetId = null;
  let mediaPickerRole = "quiz";
  let mediaPickerChoice = "";
  let mediaPickerScrollPending = false;
  let mediaDownloadWatch = null;
  let mediaPosterEpoch = 0;
  let mediaKind = "video";
  let mediaSource = "cloud";
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
  let rootMode = "more";
  let leaseRecovering = false;
  let leaseRecoverySerial = 0;
  let controlBusy = null;
  let controlNotice = null;
  let controlNoticeError = false;
  let controlManualMinutes = 20;
  let controlRefs = null;
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

  async function open() {
    return openRoot("more");
  }

  async function openControl() {
    return openRoot("control");
  }

  async function openRoot(mode) {
    if (!host) return;
    rootMode = mode === "control" ? "control" : "more";
    controlNotice = null;
    controlNoticeError = false;
    if (rootOpen) {
      // Switching between More and Control on the same page reuses the editor lease, but
      // neither surface may inherit a proof from the other.
      releaseAllSensitive();
      clearSensitiveMemory();
      subpage = null;
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
        subpage = null;
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
    // entering Control never flashes "家长中心" and never draws a back button.
    const content = el("div", "parent-content parent-more-content");
    const head = el("div", "parent-more-head");
    head.appendChild(el(
      "h1",
      "parent-more-title",
      t(rootMode === "control" ? "controlTitle" : "moreTitle"),
    ));
    content.appendChild(head);
    content.appendChild(el("p", "parent-empty", t("loading")));
    shell.appendChild(content);
    root.appendChild(shell);
  }

  async function close() {
    stopHeartbeat();
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
    // A gate opened from the Control tab keeps that tab's identity instead of
    // showing the parent-center title and a back affordance that tab does not have.
    const barTitle = rootMode === "control" ? t("controlTitle") : t("parentCenterTitle");
    shell.appendChild(barNode(barTitle, statusLine(gate), rootMode === "control" ? null : confirmClose));
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
    if (rootMode === "control") return;
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
      if (rootMode === "control") {
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
    if (!rootOpen) return;
    if (rootMode === "control") return renderControlPage();
    if (subpage !== "personalize") stopMediaPoll();
    applyStaticText();
    if (!snap) return renderLoading();
    if (subpage === "apps") return renderAppPicker();
    if (subpage === "password") return renderPassword();
    if (subpage === "personalize") return renderPersonalize();
    if (subpage === "time") return renderTimeAndApps();
    renderMain();
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

  function renderMain() {
    const root = $(ROOT_ID);
    if (!root) return;
    clear(root);
    const shell = el("div", "parent-shell");
    const content = el("div", "parent-content parent-more-content");

    const head = el("div", "parent-more-head");
    head.appendChild(el("h1", "parent-more-title", t("moreTitle")));
    const status = el("p", "parent-more-status");
    status.appendChild(el("span", "parent-more-dot"));
    status.appendChild(el("span", null, moreStatusText()));
    head.appendChild(status);
    content.appendChild(head);
    content.appendChild(noticeArea());

    content.appendChild(sectionLabel(t("childMode")));
    const child = el("div", "parent-card");
    child.appendChild(row(
      t("timeAndApps"),
      t("timeAndAppsSummary", { apps: rulesAppSummary() }),
      null,
      enterTimeAndApps,
      !(snap.capabilities && snap.capabilities.canEditRules),
      "parent-row-data"
    ));
    // The quiz switch lives only inside Time & Apps in V32. Keeping a second copy here
    // would bypass the PIN gate and let a child re-enable the challenge.
    content.appendChild(child);

    content.appendChild(sectionLabel(t("localSettings")));
    const local = el("div", "parent-card");
    local.appendChild(row(
      t("parentPassword"),
      null,
      snap.pinConfigured ? t("pinSet") : t("pinUnset"),
      () => {
        subpage = "password";
        clearNotices();
        render();
        window.scrollTo({ top: 0 });
      },
      !(snap.capabilities && snap.capabilities.canChangePin)
    ));
    local.appendChild(row(
      t("personalize"),
      t("personalizeHint"),
      mediaSummary(),
      openPersonalize
    ));
    content.appendChild(local);

    content.appendChild(sectionLabel(t("quizAndAi")));
    const knowledge = el("div", "parent-card");
    knowledge.appendChild(row(t("aiTitle"), null, t("manageAction"), () => {
      if (host && host.openAi) host.openAi();
    }));
    knowledge.appendChild(row(
      t("bankMaintenance"),
      t("bankMaintenanceHint"),
      t("manageAction"),
      () => {
        if (host && host.openQuizBank) host.openQuizBank();
      }
    ));
    content.appendChild(knowledge);

    content.appendChild(sectionLabel(t("otherOptions")));
    const other = el("div", "parent-card");
    other.appendChild(row(
      t("downloadParentApp"),
      t("downloadParentAppHint"),
      t("viewAction"),
      () => {
        if (host && host.openParentAppDownload) host.openParentAppDownload();
      }
    ));
    other.appendChild(switchRow(
      t("autoUpdate"),
      t("autoUpdateHint"),
      snap.autoUpdate,
      false,
      (input) => saveAutoUpdateSwitch(input)
    ));
    other.appendChild(row(
      t("softwareUpdate"),
      updateSubtitle(),
      snap.update && snap.update.current ? "v" + snap.update.current : null,
      openUpdateSheet
    ));
    other.appendChild(row(
      t("privacyInfo"),
      t("privacyHint"),
      t("viewAction"),
      () => {
        if (host && host.openPrivacy) host.openPrivacy();
      }
    ));
    content.appendChild(other);

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

  function renderAppRows() {
    const card = el("div", "parent-card");
    const rules = snap.rules || {};
    const allowAll = Boolean(rules.allApps);
    const allowed = Array.isArray(rules.allowedPackages) ? rules.allowedPackages.length : 0;
    const study = Array.isArray(rules.studyPackages) ? rules.studyPackages.length : 0;
    const entValue = allowAll ? t("allApps")
      : allowed > 0 ? t("appsSelected", { n: allowed }) : t("noEntertainment");
    const studyValue = study > 0 ? t("appsSelected", { n: study }) : t("studyNone");
    card.appendChild(row(
      t("entertainmentApps"),
      null,
      entValue,
      () => openPicker("entertainment"),
      !(snap.capabilities && snap.capabilities.canEditRules)
    ));
    card.appendChild(row(
      t("studyApps"),
      null,
      studyValue,
      () => openPicker("study"),
      !(snap.capabilities && snap.capabilities.canEditRules)
    ));
    return card;
  }

  async function enterTimeAndApps() {
    if (!snap || !(snap.capabilities && snap.capabilities.canEditRules)) return;
    // Every visit starts with a fresh proof, even if an old tab still holds one.
    releaseSensitive("RULES_EDIT");
    const token = await requestSensitiveToken("RULES_EDIT", "sensitivePinLeadRules");
    if (!token) return;
    rulesHold = false;
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
    const content = el("div", "parent-content");
    content.appendChild(noticeArea());
    content.appendChild(el("p", "parent-hint", t("timePageHint")));

    content.appendChild(sectionLabel(t("timeRules")));
    content.appendChild(renderWindows());

    content.appendChild(sectionLabel(t("exemptSection")));
    content.appendChild(renderExemptWindows());

    content.appendChild(sectionLabel(t("limits")));
    content.appendChild(renderLimits());

    content.appendChild(sectionLabel(t("appScope")));
    content.appendChild(renderAppRows());

    content.appendChild(sectionLabel(t("quizChallenge")));
    content.appendChild(renderQuizChallenge());

    content.appendChild(sectionLabel(t("tvInputSources")));
    content.appendChild(renderInputSources());

    shell.appendChild(content);
    root.appendChild(shell);
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

  function mediaNeedsPoll(media) {
    return mediaActiveDownloads(media) ||
      Boolean(media && media.catalog && media.catalog.refreshing);
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
    const maxLocal = Math.max(1, Number(limits.maxLocalItems) || 6);
    const kindItems = items.filter((item) => mediaKindOf(item) === mediaKind);
    const officialItems = kindItems.filter((item) => item.source === "official");
    const localItems = kindItems.filter((item) => item.source === "local");
    const readyOfficial = officialItems.filter(mediaIsPlayable).length;
    const localTotal = items.filter((item) => item.source === "local").length;

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
    modulesHead.appendChild(el("span", "parent-media-head-note", t("mediaModulesNote")));
    content.appendChild(modulesHead);
    const current = el("div", "parent-card parent-media-modules");
    current.appendChild(mediaModuleRow("quiz"));
    current.appendChild(mediaModuleRow("lock"));
    content.appendChild(current);
    const timing = el("p", "parent-media-timing-note");
    timing.appendChild(el("span", "parent-media-note-icon", "◷"));
    const timingText = el("span", "parent-media-timing-text");
    timingText.appendChild(el("strong", "", t("mediaTimingLead")));
    timingText.appendChild(document.createTextNode(" " + t("mediaSingleTimingNote")));
    timing.appendChild(timingText);
    timing.appendChild(el("span", "parent-media-readonly", t("mediaReadonly")));
    content.appendChild(timing);
    content.appendChild(el("p", "parent-hint parent-media-isolation-note",
      t("mediaIsolationNote")));

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
      localItems.length + "/" + maxLocal
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
      library.appendChild(el("p", "parent-hint parent-media-library-hint",
        t("mediaOfficialHint")));
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
      upload.disabled = !canEdit || Boolean(uploadState) || localTotal >= maxLocal;
      input.addEventListener("change", () => {
        const files = input.files ? Array.prototype.slice.call(input.files) : [];
        input.value = "";
        if (files.length) uploadMediaFiles(files);
      });
      uploadWrap.appendChild(upload);
      uploadWrap.appendChild(input);
      uploadZone.appendChild(uploadWrap);
      uploadZone.appendChild(el("p", "parent-hint parent-media-upload-hint",
        t("mediaUploadHint")));
      uploadZone.appendChild(el("p", "parent-hint parent-media-local-hint",
        t("mediaLocalHint")));
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

    content.appendChild(el("p", "parent-fineprint", t("mediaLimit")));
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
    const maxLocal = Math.max(1, Number((media.limits || {}).maxLocalItems) || 6);
    return statusLine(gate) + " · " + t("mediaSourceOfficial") + " " + ready + "/" +
      official.length + " · " + t("mediaSourceLocal") + " " + local + "/" + maxLocal;
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

  /** Roles that currently point at this exact item, so a change never clears another film. */
  function mediaItemRoles(item) {
    const stored = mediaRolesState();
    return ["quiz", "lock"].filter((role) => stored[role] === item.id);
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
    const item = mediaItem(mediaRolesState()[role]);
    const node = el("button", "parent-row parent-media-module-row");
    node.type = "button";
    node.appendChild(item
      ? mediaCover(item, "parent-media-module-thumb")
      : mediaDefaultThumb("parent-media-module-thumb"));
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", mediaModuleTitle(role)));
    main.appendChild(el("span", "parent-row-sub", item
      ? mediaKindLabel(item) + " · " + mediaSourceText(item) + " · " + mediaName(item)
      : t("mediaKindImage") + " · " + t("mediaSourceDefault")));
    node.appendChild(main);
    const action = el("span", "parent-media-module-action");
    action.appendChild(el("span", "parent-media-module-action-label", t("mediaChange")));
    action.appendChild(el("span", "chevron", "›"));
    node.appendChild(action);
    node.addEventListener("click", () => openMediaPicker(role));
    return node;
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
    const stored = mediaRolesState()[mediaPickerRole] || "";
    const current = mediaItem(stored);
    mediaPickerChoice = current && mediaCanUseInRole(current, mediaPickerRole) &&
      mediaIsPlayable(current) ? stored : "";
    mediaPickerScrollPending = true;
    openSheet((sheet) => renderMediaSheetContent(sheet, closeMediaSheet));
  }

  function paintMediaSheet() {
    const root = $(SHEET_ID);
    const sheet = root ? root.querySelector(".parent-sheet") : null;
    if (resolveMediaDownloadWatch(Boolean(sheet))) return;
    if (!sheet) {
      mediaSheetMode = null;
      mediaSheetId = null;
      mediaPickerScrollPending = false;
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
    mediaDownloadWatch = null;
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

  function mediaPreviewHint(item, state) {
    if (state === "UPDATE_AVAILABLE") return t("mediaUpdateHint");
    if (item.source === "local") return t("mediaLocalReadyHint");
    if (mediaIsPlayable(item)) return t("mediaDownloadedHint");
    if (state === "FAILED") return t("mediaDownloadFailed");
    return t("mediaDownloadHint");
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
    if (item.source !== "local" && !mediaIsBusy(item)) {
      badges.appendChild(el("span", "parent-media-badge-inline " + mediaStateTone(item),
        mediaStateText(item)));
    }
    mediaItemRoles(item).forEach((role) => {
      badges.appendChild(el("span", "parent-media-badge-inline is-active", mediaModuleTitle(role)));
    });
    scroll.appendChild(badges);
    if (!mediaIsBusy(item)) {
      scroll.appendChild(el("p", "parent-hint parent-media-preview-hint",
        mediaPreviewHint(item, state)));
    }
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
      const remove = button(deleteLabel, "parent-media-text-action", () => {
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

  function mediaPickerOptions() {
    const options = [{ id: "", item: null }];
    const items = (snap && snap.media && snap.media.items) || [];
    items.filter((item) => mediaIsPlayable(item) && mediaCanUseInRole(item, mediaPickerRole))
      .forEach((item) => options.push({ id: item.id, item: item }));
    return options;
  }

  function mediaPickerOption(option, currentId) {
    const selected = option.id === mediaPickerChoice;
    const card = el("button", "parent-media-option" + (selected ? " is-selected" : ""));
    card.type = "button";
    const cover = option.item
      ? mediaCover(option.item, "parent-media-option-cover")
      : mediaDefaultThumb("parent-media-option-cover");
    if (option.item) {
      cover.appendChild(el("span", "parent-media-kind-badge parent-media-option-kind",
        mediaKindLabel(option.item)));
    }
    if (option.id && option.id === currentId) {
      cover.appendChild(el("span", "parent-media-option-current", t("mediaCurrentBadge")));
    }
    if (selected) {
      cover.appendChild(el("span", "parent-media-option-check", "✓"));
    }
    card.appendChild(cover);
    const title = option.item ? mediaName(option.item) : t("mediaSourceDefault");
    card.appendChild(el("span", "parent-media-option-title", title));
    card.title = title;
    card.addEventListener("click", () => {
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

  function renderMediaPickerSheet(sheet, close) {
    const canEdit = mediaCanEdit();
    const stored = mediaRolesState()[mediaPickerRole] || "";
    const options = mediaPickerOptions();
    if (mediaPickerChoice && !options.some((option) => option.id === mediaPickerChoice)) {
      mediaPickerChoice = options.some((option) => option.id === stored) ? stored : "";
    }
    const selected = options.find((option) => option.id === mediaPickerChoice) || options[0];
    const scroll = el("div", "parent-media-sheet-scroll");
    scroll.appendChild(el("h2", null,
      t("mediaPickerTitle", { module: mediaModuleTitle(mediaPickerRole) })));
    scroll.appendChild(el("p", "parent-hint parent-media-picker-note",
      mediaPickerRole === "quiz" ? t("mediaPickerHintQuiz") : t("mediaPickerHint")));

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

    if (selected.item && mediaPickerRole === "lock" && mediaKindOf(selected.item) === "image") {
      scroll.appendChild(el("p", "parent-hint", t("mediaImageRoleNote")));
    }

    const strip = el("div", "parent-media-strip");
    options.forEach((option) => strip.appendChild(mediaPickerOption(option, stored)));
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
    const same = mediaPickerChoice === (stored || "");
    const use = button(same ? t("mediaInUse") : t("mediaUse"), "parent-btn primary",
      applyMediaPickerChoice);
    use.disabled = !canEdit || same;
    footer.appendChild(use);
    if (!canEdit) footer.appendChild(el("p", "parent-hint", t("readOnlyCloud")));
    sheet.appendChild(footer);

    if (mediaPickerScrollPending) {
      mediaPickerScrollPending = false;
      requestAnimationFrame(() => {
        const active = strip.querySelector(".is-selected");
        if (active) strip.scrollLeft = Math.max(0, active.offsetLeft - 8);
      });
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
  }

  function isLeaseError(error) {
    const code = error && (error.code || error.message);
    return error && (error.status === 401 || code === "EDITOR_REQUIRED" ||
      code === "LEASE_EXPIRED" || code === "LEASE_TAKEN_OVER" || code === "QUIZ_TAKEN_OVER");
  }

  function renderActivation() {
    const card = el("div", "parent-card");
    const wrap = el("div", "parent-row");
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", t("timing")));
    main.appendChild(el("span", "parent-row-sub", t("activationHint")));
    wrap.appendChild(main);
    // The segmented control sits on its own row on every width; a single column keeps the
    // 9:16 phone and the 16:9 TV reading order identical without a two-pane layout.
    const segmented = el("div", "parent-seg");
    ["AUTO", "MANUAL"].forEach((value) => {
      const btn = button(t(value === "AUTO" ? "autoMode" : "manualMode"), null, () => {
        if (!snap.capabilities || !snap.capabilities.canEditRules) return;
        if (snap.activation === value) return;
        confirmSheet(t("activationConfirmTitle"), t("activationConfirmText"), t("activationConfirm"), () => {
          saveSimple("/api/parent/activation", {
            activation: value,
            expectedModeRevision: snap.modeRevision
          }, null, null);
        }, t("cancel"));
      });
      btn.setAttribute("aria-pressed", String(snap.activation === value));
      btn.disabled = !(snap.capabilities && snap.capabilities.canEditRules);
      segmented.appendChild(btn);
    });
    const segRow = el("div", "parent-row");
    segRow.appendChild(segmented);
    card.appendChild(wrap);
    card.appendChild(segRow);
    return card;
  }

  function renderWindows() {
    const card = el("div", "parent-card");
    if (!snap.capabilities || !snap.capabilities.canEditRules) {
      card.appendChild(el("div", "parent-empty", t("readOnlyCloud")));
      return card;
    }
    ["weekday", "weekend"].forEach((kind) => {
      card.appendChild(renderWindowGroup(kind === "weekday"));
    });
    card.appendChild(el("div", "parent-hint parent-window-note", t("singleWindowHint")));
    return card;
  }

  /**
   * V29 allows exactly one window per day. Old local documents may still carry two; only the
   * first is shown, and the next edit saves a one-element list, which the server now enforces.
   */
  function renderWindowGroup(weekday) {
    const group = el("div");
    group.appendChild(el("div", "parent-row", t(weekday ? "weekday" : "weekend")));
    const windows = (snap.rules && snap.rules[weekday ? "weekdayWindows" : "weekendWindows"]) || [];
    const window = windows[0];
    if (!window) {
      group.appendChild(el("div", "parent-empty", t("noWindows")));
      return group;
    }
    const wrap = el("div", "parent-window");
    const start = timeField(window.start, t("from"), false);
    const end = timeField(window.end, t("to"), true);
    let currentStart = window.start;
    let currentEnd = window.end;
    const commit = (nextStart, nextEnd) => {
      if (nextStart < 0 || nextEnd <= nextStart || nextEnd > 1440) {
        setNotice(t("rulesInvalid"), true);
        start.set(currentStart);
        end.set(currentEnd);
        return;
      }
      currentStart = nextStart;
      currentEnd = nextEnd;
      updateWindows(weekday, currentStart, currentEnd);
    };
    start.onchange(() => commit(start.get(), currentEnd));
    end.onchange(() => commit(currentStart, end.get()));
    wrap.appendChild(start.node);
    wrap.appendChild(el("span", "parent-time-sep", "—"));
    wrap.appendChild(end.node);
    group.appendChild(wrap);
    return group;
  }

  function updateWindows(weekday, start, end) {
    const next = clone(snap.rules);
    const key = weekday ? "weekdayWindows" : "weekendWindows";
    next[key] = [{ start: start, end: end }];
    saveRules(next);
  }

  /**
   * Exemption windows: wall-clock spans where child mode suspends entirely (grandparents'
   * time). 0–4 windows per day class; each row edits start/end and can be removed.
   */
  const EXEMPT_MAX = 4;

  function renderExemptWindows() {
    const card = el("div", "parent-card");
    if (!snap.capabilities || !snap.capabilities.canEditRules) {
      card.appendChild(el("div", "parent-empty", t("readOnlyCloud")));
      return card;
    }
    card.appendChild(el("div", "parent-hint parent-window-note", t("exemptHint")));
    ["weekday", "weekend"].forEach((kind) => {
      card.appendChild(renderExemptGroup(kind === "weekday"));
    });
    return card;
  }

  function renderExemptGroup(weekday) {
    const group = el("div");
    group.appendChild(el("div", "parent-row", t(weekday ? "weekday" : "weekend")));
    const key = weekday ? "exemptWeekdayWindows" : "exemptWeekendWindows";
    const windows = (snap.rules && snap.rules[key]) || [];
    if (windows.length === 0) {
      group.appendChild(el("div", "parent-empty", t("noExempt")));
    }
    windows.forEach((w, index) => {
      group.appendChild(renderExemptRow(weekday, w, index, windows.length));
    });
    if (windows.length < EXEMPT_MAX) {
      const add = button(t("exemptAdd"), "parent-tile-btn", () => {
        const next = clone(snap.rules);
        const list = ((next[key] || []).slice());
        list.push({ start: 9 * 60, end: 16 * 60 });
        next[key] = list;
        saveRules(next);
      });
      group.appendChild(add);
    }
    return group;
  }

  function renderExemptRow(weekday, window, index, total) {
    const wrap = el("div", "parent-window");
    const start = timeField(window.start, t("from"), false);
    const end = timeField(window.end, t("to"), true);
    let currentStart = window.start;
    let currentEnd = window.end;
    const key = weekday ? "exemptWeekdayWindows" : "exemptWeekendWindows";
    const commit = (nextStart, nextEnd) => {
      if (nextStart < 0 || nextEnd <= nextStart || nextEnd > 1440) {
        setNotice(t("rulesInvalid"), true);
        start.set(currentStart);
        end.set(currentEnd);
        return;
      }
      currentStart = nextStart;
      currentEnd = nextEnd;
      updateExemptWindow(key, index, nextStart, nextEnd);
    };
    start.onchange(() => commit(start.get(), currentEnd));
    end.onchange(() => commit(currentStart, end.get()));
    wrap.appendChild(start.node);
    wrap.appendChild(el("span", "parent-time-sep", "—"));
    wrap.appendChild(end.node);
    const remove = button("×", "parent-tile-btn", () => {
      removeExemptWindow(key, index);
    });
    wrap.appendChild(remove);
    return wrap;
  }

  function updateExemptWindow(key, index, start, end) {
    const next = clone(snap.rules);
    const list = ((next[key] || []).slice());
    if (index < 0 || index >= list.length) return;
    list[index] = { start: start, end: end };
    next[key] = list;
    saveRules(next);
  }

  function removeExemptWindow(key, index) {
    const next = clone(snap.rules);
    const list = ((next[key] || []).slice());
    if (index < 0 || index >= list.length) return;
    list.splice(index, 1);
    next[key] = list;
    saveRules(next);
  }

  function renderLimits() {
    const card = el("div", "parent-card");
    const canEdit = snap.capabilities && snap.capabilities.canEditRules;
    const rules = snap.rules || {};
    card.appendChild(limitRow(
      "daily", t("dailyLimit"), rules.autoDailyLimitMinutes,
      STEP_DAILY, MIN_EDIT_DAILY, MAX_EDIT_DAILY, canEdit, 15
    ));
    card.appendChild(limitRow(
      "rest", t("rest"), rules.restMinutes,
      STEP_REST, 0, MAX_EDIT_REST, canEdit, 5
    ));
    card.appendChild(limitRow(
      "buffer", t("buffer"), rules.sessionScreenOffBufferMinutes,
      STEP_BUFFER, MIN_BUFFER, MAX_BUFFER, canEdit, 5
    ));
    return card;
  }

  function limitRow(key, label, value, step, min, max, canEdit, optionStep) {
    const node = el("div", "parent-row");
    const main = el("div", "parent-row-main");
    main.appendChild(el("span", "parent-row-title", label));
    node.appendChild(main);
    const controls = el("div", "parent-stepper");
    const minus = button("−", "parent-step", () => adjustLimit(key, -step, min, max));
    const valueBtn = button(t("minutes", { n: value }), "parent-step-value", () => {
      openNumberSheet(key, label, value, min, max, optionStep);
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
    const current = Number(snap.rules && snap.rules[limitField(key)]) || 0;
    const next = Math.min(max, Math.max(min, current + delta));
    if (next === current) return;
    setLimit(key, next);
  }

  function limitField(key) {
    if (key === "daily") return "autoDailyLimitMinutes";
    if (key === "rest") return "restMinutes";
    return "sessionScreenOffBufferMinutes";
  }

  /** Repaints the rule controls already on screen; a save must not rebuild the whole page. */
  function paintRulesControls() {
    const rules = snap && snap.rules;
    if (!rules) return;
    document.querySelectorAll(".parent-step-value[data-limit-key]").forEach((node) => {
      const value = Number(rules[limitField(node.dataset.limitKey)]);
      if (Number.isFinite(value)) node.textContent = t("minutes", { n: value });
    });
  }

  function openNumberSheet(key, label, value, min, max, step) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    openSheet((sheet, close) => {
      sheet.appendChild(el("h2", null, label));
      const list = el("div", "parent-choice-list");
      for (let option = min; option <= max; option += step) {
        const choice = button(t("minutes", { n: option }), "parent-choice", () => {
          setLimit(key, option);
          close();
        });
        choice.setAttribute("aria-pressed", String(option === Number(value)));
        list.appendChild(choice);
      }
      sheet.appendChild(list);
      const actions = el("div", "parent-actions");
      actions.appendChild(button(t("cancel"), "parent-btn ghost", close));
      sheet.appendChild(actions);
    });
  }

  function setLimit(key, value) {
    const next = clone(snap.rules);
    next[limitField(key)] = value;
    if (!saveRules(next)) return;
    const node = document.querySelector(`.parent-step-value[data-limit-key="${key}"]`);
    if (node) node.textContent = t("minutes", { n: value });
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
    const items = Array.isArray(media.items) ? media.items : [];
    const localCount = items.filter((item) => item.source === "local").length;
    const maxVideos = Math.max(1, Number(limits.maxLocalItems) || 6);
    const room = Math.max(0, maxVideos - localCount);
    if (!room) {
      setNotice(t("introVideoFull"), true);
      render();
      return;
    }
    const chosen = files.slice(0, room);
    const skipped = files.length - chosen.length;
    const videoLimit = Number(limits.maxFileBytes) || 4 * 1024 * 1024;
    const imageLimit = Number(limits.maxUploadBytes) || 12 * 1024 * 1024;
    const firstImage = /\.(jpe?g|png|webp)$/i.test(chosen[0].name || "");
    mediaSource = "local";
    mediaKind = firstImage ? "image" : "video";
    uploadState = {
      index: 0,
      total: chosen.length,
      name: "",
      loaded: 0,
      totalBytes: 0,
      added: 0,
      errors: []
    };
    render();
    for (let i = 0; i < chosen.length; i++) {
      const file = chosen[i];
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
      let summary = t("introVideoBatchDone", { added: added });
      if (skipped > 0) {
        summary += " " + t("introVideoBatchFull", { max: maxVideos, n: chosen.length });
      }
      setNotice(summary, false);
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

  function saveRules(next) {
    if (!editorToken) return false;
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
      const data = await requestWithSensitive(
        "/api/parent/rules",
        {
          method: "POST",
          body: {
            expectedRulesRevision: snap.rulesRevision,
            rules: rules
          },
          editorToken: editorToken,
          quizAuth: true,
        },
        "RULES_EDIT",
        "sensitivePinLeadRules"
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
        error.code === "LOCAL_ONLY") {
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
        error.code === "RULES_INVALID") {
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
    // The app picker is only opened from Time & Apps, so finishing it returns there.
    subpage = "time";
    picker = null;
    render();
  }

  async function openPicker(kind) {
    if (!snap.capabilities || !snap.capabilities.canEditRules) return;
    subpage = "apps";
    picker = {
      kind: kind,
      apps: null,
      query: "",
      loading: true,
      allApps: Boolean(snap.rules && snap.rules.allApps),
      selected: new Set(kind === "study"
        ? ((snap.rules && snap.rules.studyPackages) || [])
        : ((snap.rules && !snap.rules.allApps && snap.rules.allowedPackages) || [])),
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
    const title = picker.kind === "study" ? t("appPickerStudy") : t("appPickerEntertainment");
    shell.appendChild(barNode(title, statusLine(gate), exitPicker));
    const content = el("div", "parent-subpage parent-picker-content");
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
    content.appendChild(el("p", "parent-hint", picker.kind === "study"
      ? t("appPickerStudyHint") : t("appPickerHint")));
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
    if (picker.kind !== "study") {
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
    apps.slice(0, 300).forEach((app) => {
      const pkg = String(app.packageName || "");
      const labelText = String(app.label || pkg);
      const tile = el("label", "parent-app-tile");
      tile.dataset.parentIcon = pkg;
      const check = el("input");
      check.type = "checkbox";
      check.checked = picker.selected.has(pkg);
      check.disabled = picker.kind !== "study" && picker.allApps;
      const icon = el("span", "parent-app-icon");
      icon.appendChild(el("span", "parent-app-initial",
        (labelText.trim()[0] || "?").toUpperCase()));
      const name = el("span", "parent-app-name", labelText);
      const mark = el("span", "parent-app-check", "✓");
      mark.setAttribute("aria-hidden", "true");
      const sync = () => tile.classList.toggle("selected", check.checked);
      sync();
      if (check.disabled) tile.classList.add("disabled");
      check.addEventListener("change", () => {
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
    if (picker.kind === "study") {
      next.studyPackages = Array.from(picker.selected);
    } else {
      next.allApps = picker.allApps;
      next.allowedPackages = picker.allApps ? [] : Array.from(picker.selected);
    }
    try {
      const data = await requestWithSensitive(
        "/api/parent/rules",
        {
          method: "POST",
          body: {
            expectedRulesRevision: snap.rulesRevision,
            rules: next
          },
          editorToken: editorToken,
          quizAuth: true,
        },
        "RULES_EDIT",
        "sensitivePinLeadRules"
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

  function controlStatusText(control) {
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

  function renderControlPage() {
    if (!snap) return renderLoading();
    const root = $(ROOT_ID);
    if (!root) return;
    clear(root);
    const shell = el("div", "parent-shell");
    // The control page is a top-level tab: like the other tabs it has no back
    // button, and its title uses the shared page-head spacing.
    const content = el("div", "parent-content parent-more-content parent-control-content");
    const head = el("div", "parent-more-head");
    head.appendChild(el("h1", "parent-more-title", t("controlTitle")));
    const status = el("p", "parent-more-status");
    const dot = el("span", "parent-more-dot");
    const statusText = el("span", null, "");
    status.appendChild(dot);
    status.appendChild(statusText);
    head.appendChild(status);
    content.appendChild(head);

    content.appendChild(sectionLabel(t("controlChildSection")));
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

    content.appendChild(sectionLabel(t("controlTimerSection")));
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

    const notice = el("p", "parent-hint parent-control-notice");
    notice.setAttribute("role", "status");
    content.appendChild(notice);
    content.appendChild(el("p", "parent-hint parent-control-note", t("controlLocalOnly")));

    shell.appendChild(content);
    root.appendChild(shell);
    controlRefs = {
      dot: dot,
      statusText: statusText,
      notice: notice,
      takeOver: takeOver,
      enterChild: enterChild,
      startManual: startManual,
      stopTimer: stopTimer
    };
    paintControl();
  }

  function paintControl() {
    const refs = controlRefs;
    if (!refs || !rootOpen || rootMode !== "control") return;
    const control = controlData();
    refs.statusText.textContent = controlStatusText(control);
    refs.dot.classList.toggle(
      "is-warn",
      Boolean(control && (control.guardState === "PARENT_OVERRIDE" ||
        (control.quiz && control.quiz.roundActive)))
    );

    const busy = Boolean(controlBusy);
    const takeOver = controlAction(control, "takeOver");
    const enterChild = controlAction(control, "enterChildMode");
    const start = controlAction(control, "startManual");
    const stop = controlAction(control, "stopTimer");

    refs.takeOver.button.disabled = !takeOver.enabled || busy;
    refs.takeOver.sub.textContent = takeOver.enabled
      ? t(takeOver.requiresConfirm ? "controlTakeOverConfirmHint" : "controlTakeOverHint")
      : t("controlTakeOverAlready");

    refs.enterChild.button.disabled = !enterChild.enabled || busy;
    refs.enterChild.sub.textContent = enterChild.enabled
      ? t("controlEnterHint")
      : t("controlEnterAlready");

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
    // V32: leaving child mode needs the parent PIN. The proof lasts 60s and survives a
    // cancelled confirmation, so a second click does not ask again until it expires.
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
    const min = Math.max(1, Number(manual.minMinutes) || 5);
    const max = Math.max(min, Number(manual.maxMinutes) || 120);
    const step = Math.max(1, Number(manual.stepMinutes) || 5);
    let minutes = Math.min(max, Math.max(min, Number(controlManualMinutes) || min));
    minutes = Math.min(max, min + Math.round((minutes - min) / step) * step);
    openSheet((sheet, close) => {
      let lockRemaining = 0;
      let lockTicker = 0;
      sheet.appendChild(el("h2", null, t("controlManualTitle")));
      sheet.appendChild(el("p", null, t("controlManualLead")));

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
      sheet.appendChild(field);

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
        const pin = input.value.replace(/\D/g, "");
        if (pin.length !== 6) {
          error.textContent = t("pinLength");
          error.classList.remove("hidden");
          input.focus();
          return;
        }
        start.disabled = true;
        minus.disabled = true;
        plus.disabled = true;
        error.classList.add("hidden");
        try {
          const requestId = newControlRequestId();
          const data = await host.request("/api/parent/control", {
            method: "POST",
            body: {
              action: "START_MANUAL",
              requestId: requestId,
              pin: pin,
              minutes: minutes
            },
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
      setTimeout(() => { try { input.focus(); } catch (_) { /* ignore */ } }, 60);
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

  window.ParentCenterUI = {
    init: init,
    open: open,
    openControl: openControl,
    close: close,
    isOpen: () => rootOpen,
    refreshEntry: refreshEntry,
    setLang: setLang,
  };
})();
