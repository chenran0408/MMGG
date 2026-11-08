# MMGG 当前状态与接手入口

核对日期：2026-11-08。本文只维护当前事实；历史批次原文已归档到 `docs/archive/2026-10-30/CURRENT_STATUS_2026-10-30_before_compaction.md`，任务级证据仍保留在各任务目录。

## 工作线

- 开发线：`beta`，工作树 `D:/CODEX/Tasks/2026-10-22/new-chat-3/work/HyperDragShare`。
- 稳定线：`repair-from-189`，工作树 `D:/CODEX/Tasks/2026-10-20/new-chat-2/work/HyperDragShare`；本轮未修改，当前稳定工作树干净。
- 远程维护策略：GitHub 用户仓库只维护 Beta 与正式线；`upstream` 仅作原作者参考，不向其推送。
- Candidate A/B：继续作为冻结验证证据，不作为当前开发基线。
- 历史Beta43分支和候选tag已推送GitHub；本轮Beta49仅本地交付，未推送或创建在线Release。

## 当前代码事实

- Root evdev 仍是拖拽输入权威；真实 Root `UP/CANCEL` 决定结束。Phase 1A owner、Blocker、微信专用 Hook 和控件避让阈值保持现状。按压为组合触发：单指 fire=拖拽，双指 fire=分词；资格规则详见 AGENTS 第2条与 DECISIONS D19。
- `DragShareController` 仍负责手势协调、会话、目标选择和动作触发；`DragShareLifecycle` 为会话与预览退出 callback 提供 owner token。当前生命周期审计未发现稳定可复现的缺失保护，因此没有为拆分而改生产代码。
- FanRuntime、Freeform 几何/配置/目标、设置导航和生效提示已经有独立决策边界。
- Freeform 队列使用 generation、重试预算和 `QUEUED / REQUESTED / OBSERVED / UNKNOWN` 证据阶段。任务出现不等于最终启动成功。
- Activation 首页与诊断页共用 `ActivationPresentation`；`UNKNOWN` 不转换为权限缺失。
- 跨进程追踪只报告最后一个可观察阶段。`startActivity` 返回、拖放接受和 Provider descriptor 打开都不代表接收方已经读取或发送。
- 传送门原图候选现在直接下载 HTTP(S) `imgUrl` 解码，避免宿主 `IconManager` 返回预览尺寸；并接受 `content://`、`file://` 和绝对路径图片引用。传送门异步返回的媒体即使与预览同像素尺寸，也可替换，避免一直保留带圆角的宿主截图。若 `imgUrl` 本身指向缩略图，仍需来源专用的 URL/附件解析才能得到原媒体；不把未知 URI 或任意 Bitmap 当原图。

### 2026-11-08 Beta49 本地修复

- “更多应用”的创建、目录刷新和关闭统一到同一 UI Looper。此前设备 DropBox 已证实跨 `wmshell.main` / `main` 更新 Adapter 导致 SystemUI 崩溃回锁屏。窗口恢复可聚焦的应用浮窗，搜索不再依赖第二次点击切焦点。
- 移除“最近”分类及记录；“全部应用”只显示应用，应用内入口留在“快捷方式”。冷目录先发布应用名称，再查询快捷方式；首屏图标先于全量图标补齐，拼音转换器按工作线程复用。
- “更多应用”作为可配置目标加入底角和蜂巢选择/排序/位置，旧配置只迁移一次；用户删除后不重新添加。面板沿用深浅主题。
- 新增“横屏时禁用单指长按”，默认开启。横屏保留第二指升级资格；关闭开关恢复横屏单指拖拽。竖屏单指与双指分词保持原时长语义。
- 微信与全局统一结果接入：Root 空结果不再直接丢弃仍按住的候选；明确控件避让及应用黑名单继续退出。传送门回调在主队列交给按压检测器，组合触发开启时不再通过未归属的原生结果自行创建拖拽。备用 MIUI 输入仍需 Root 取词租约，但不再被 evdev 未就绪条件自相矛盾地拒绝。
- 定向回归覆盖空捕获后Portal接入、Binder回调、MIUI备用输入、迟到图片丢弃、横屏默认/关闭/双指晚加入，以及面板线程和应用/快捷方式分组。设备触发的最终结论以本轮交付记录为准，源码复现不等于微信现场根因已全部确认。
- 最终验证：870项单测全部通过；Lint 0 errors、197 warnings、2 hints；assembleDebug与assembleBeta（R8、资源收缩、lintVitalBeta）通过。修正两条仍按legacy stub断言的历史API102反射测试，没有改媒体功能生产逻辑。
- 本地提交：`d216d87`（反射测试）、`f2740bc`（更多应用）、`540979b`（按压链和横屏开关）。APK：`D:/CODEX/Tasks/2026-11-08/mmgg-more-apps-fix/outputs/MMGG-1.7.219-beta.49.apk`，SHA-256 `E9B3E7983DC3AD0D88D65F6EDD47EADF1FB4793310AB2D171D2D101A1981146F`，v2签名通过、Android Debug证书，与Beta48证书一致。
- 已在b0ba578f覆盖安装并重载SystemUI/传送门；新SystemUI诊断明确version291，传送门注入、Root输入和节点连接均报告291/ready。逐包核对SystemUI PID=23895、Portal PID=23813；任务早期记录写反的PID已纠正。横屏和面板完整交互尚未验收。旧版缓存窗口创建15/12/11ms，缺少新旧冷目录对比，不宣称提升百分比。完整记录在上述任务outputs/DELIVERY.md。
- 追加现场验收：19:30:58微信双指图片已完成原生viewBitmap回调→OCR→分词窗口accepted；微信单指仍失败，Root输入和时长正常，但节点查询连续window_not_ready。微信前台时WMS可见窗口存在，无障碍窗口却无应用窗口、Active/Top Focused Window Id=-1；酷安前台可列出应用窗口。原生support_long_press_mode=2只提供双指内容；不能把切换成1当组合手势完整修复。
- 用户提供的432×432圆角裁剪图与1200×2608完整图片来自酷安，不能归为微信图片证据。19:34:44酷安实际捕获为com.coolapk.market_image_node_capture；源码captureImageRegion调用takeScreenshot再裁剪，不含原媒体引用，因此延长imgUrl等待不会修复该路径。此项与微信单指均尚未修复，Beta49不得标为全部验收通过。现场调查见上述任务outputs/WECHAT_COOLAPK_FINDINGS.md。用户已确认另一个微信模块不影响，未修改其设置。
- 用户补充多个App都有原图问题，酷安仅为已抓到日志的样本，后续按共用图片来源处理。微信无障碍公开资料发现GKD维护者2026-04-17答复提及特定服务筛选；Andee记录MIUI/微信8.0.78在MiuiEnhanceTBService开关前后的节点差异。本机已安装该系统辅助服务，但当前仅Monica启用；服务身份/启用状态是待验证线索，尚未做本机开关对照，也未修改系统服务。2026-08-27澎湃4微信误封报道是涉及模拟点击类第三方App的媒体转述，不能据此判定节点缺失原因或MMGG账号风险。来源与边界见上述任务outputs/WECHAT_ACCESSIBILITY_RESEARCH.md。

### 2026-11-06 手势优先路由与 Beta44 交付

- 修复确认后的手势分流：单指长按统一进入拖拽预览，双指长按统一进入分词；双指按到图片时先 OCR，再打开分词窗口。
- 单指文字也使用通用拖拽预览，松手会回收预览；不再按图片/文字类型决定单指和双指行为。
- 源码提交：`6026b74`、`b7d2283`；候选 tag：`delivered/mmgg-1.7.219-beta.44`。
- `assembleBeta`（R8、资源收缩）通过；版本 `1.7.219-beta.44` / versionCode `286`；APK SHA-256 `2AF222871E72F28D9189A1DE3EEFA07D94A6E355FDD25B9D479BD0624030BA66`，v2 签名通过，Android Debug 证书。
- GitHub Beta 预发布：[MMGG 1.7.219-beta.44](https://github.com/chenran0408/MMGG/releases/tag/delivered/mmgg-1.7.219-beta.44)。本轮未执行设备验收。

### 2026-11-06 设置页精简与 Beta43 交付

- 设置页把单指拖拽确认时间与双指分词确认时间放在同一组，日志等级与保存位置选项移除写死的“当前”标记。
- 源码提交：`fff44ae`、`12ea168`；候选 tag：`delivered/mmgg-1.7.219-beta.43`。
- `assembleBeta`（R8、资源收缩）通过；版本 `1.7.219-beta.43` / versionCode `285`；APK SHA-256 `F4EE0D26E3C44A538AB920EBF58A339FAD307B33B3707AD713B9F34E684CC887`，v2 签名通过，Android Debug 证书。
- GitHub Beta 预发布：[MMGG 1.7.219-beta.43](https://github.com/chenran0408/MMGG/releases/tag/delivered/mmgg-1.7.219-beta.43)。本轮未执行设备验收。

### 2026-11-06 分词面板精简与 Beta42 交付

- 分词面板移除重复的“全文操作/选中内容操作”状态标题；无拖拽预览或选区时不显示多余分隔线。
- 窗口外观弹窗直接使用“窗口外观”标题，去掉重复的小标题；识别提示改为通用“正在识别文字…”。
- 源码提交：`12b47b0`、`b6664a5`、`4fcc79b`；候选 tag：`delivered/mmgg-1.7.219-beta.42`。
- `assembleBeta`（R8、资源收缩）通过；版本 `1.7.219-beta.42` / versionCode `284`；APK SHA-256 `4C3DF565B74D41785B89AEEB3F8030E626271E18DEEACDA516991433EFB0D972`，v2 签名通过，Android Debug 证书。
- GitHub Beta 预发布：[MMGG 1.7.219-beta.42](https://github.com/chenran0408/MMGG/releases/tag/delivered/mmgg-1.7.219-beta.42)。本轮未执行设备验收。

## 最新验证

### 2026-11-05 工程化解耦轮（E1–E6）

- E1 `0992141`：原微信按压候选状态机已整理为通用 `PressOcrFlow`（候选、文字交付、截屏拒绝）；generation、Root 权威与资源所有权留在 `PressCaptureSource`。修复了 `PressPreviewHandoffTest` 反射辅助读错对象的 3 个用例失败。
- E2 `5568fd4`：`SystemRecentsWatch` 承接系统最近任务可见性轮询（探测窗口、退避节奏、硬超时、版本失效），决策逻辑有纯逻辑测试。
- E3 `046cecb`：`QueuedLaunchQueue` 统一小窗排队启动负载、taskId 与 generation，`begin/current/consume/clear/nextRetry` 原子操作；重试策略、任务匹配、超时与私有 HyperOS 调用未改。
- E6 `7e50e03`：`ModernOverlayPrewarm` 承接预热窗口生命周期（调度、首帧收尾、销毁清理、失败回收）；销毁时释放顺序与日志保持，`DragShareExitLifecycleTest` 为护栏。
- E5 `74abeec`：`FreeformHookProbe` 承接 FanFreeformHook 的类探测与反射摘要工具，附单元测试。
- E4 复核：诊断能力采集、证据解读与用户呈现已分属独立类，无需抽取。
- 每轮独立提交，均通过全量验证：最终 894 项单测 0 失败、Lint 0 errors、assembleDebug 通过。行为语义未变；FanRuntime 触发/资格、Phase 1A、Blocker、微信 Hook、控件避让阈值未触碰。未操作设备、未 push、未发布。

### 2026-11-05 组合按压触发

- 触发方式为单指/双指结合且无模式开关（`161a171`）：首指 DOWN 即按单指启动；第二指在确认前加入升级为双指（时长从第二指起算）。出口由手势类型决定：单指=拖拽（文字分词面板 drag_preview=true、图片待拖预览），双指=分词（drag_preview=false，图片回退现有菜单）；松手时长不分流出口，`<T` 仍不触发。用户随后决定取消“全部单指/全部双指”模式选择：`PortalPressSettings`、`two_finger_long_press_enabled` 配置键、EvdevTouchParser/RootTouchSource 开关与设置页双指开关已删除；双指独立时长（250–3000ms，默认600）保留。Pointer 退化拒绝、迟到第二指拒绝、Phase 1A/Blocker 未改。
- `PressTriggerDetector` 删除候选等待态 API；`PressReleaseMode.classify(duration)` 替换为 `resolve(twoFinger)`。`TwoFingerPressTest` 已按组合语义重写（升级竞态、不退化、双指/单指 drag_preview 断言）；定向触发器/两指/RootTouchSource/PortalPressSettings/预览交接测试通过。全量回归与构建见本轮任务记录。
- 未操作设备；双指升级竞态、混合触发与真实 HyperOS 表现待实机验证。历史“T..2T/>=2T 分流”描述（2026-10-31 段）已被本节替代。

### 2026-11-05 单指按住直接拖拽

- 组合触发落地后，微信专用拖拽接管链已删除；`PressCaptureSource` 的单指/双指路径只保留通用 Root 捕获、OCR、图片预览和拖拽。后续清理移除了缩略图来源标记、徽标、专用来源日志及微信窗口回退；Root 节点图片统一按通用节点图片处理。
- 替代行为对所有应用一致：单指确认（fire）后移动超过 36dp 即武装 `directDragArmed`；若已持有图片内容且来源合规，按住期间直接经 `showContentForPressTakeover` 建立拖拽会话，无需松手后二次按住。捕获回调迟到返回时同样检查该状态。文字保持松手开分词面板；双指路径不变；图片待拖预览保留为松手不动的兜底与拖拽取消后的恢复路径。
- 微信 OCR 区域截屏在按住直接拖拽分支仍被拒绝（`direct_drag` 阶段），不把截屏冒充原图。
- `PressPreviewHandoffTest` 删除 3 个 `dragCandidate` 用例，新增"松手开分词窗口（drag_preview=true、截图回收、候选清空）"与"按住直接接管建立会话"两个行为测试。
- 全量验证：893 项单测 0 失败、Lint 0 errors/190 warnings/2 hints、assembleDebug、assembleBeta（R8）通过。未操作设备；微信按住直接拖拽、非微信应用直接拖拽与真实触摸窗口路由待实机验证。

当前开发源码版本为 `1.7.219-beta.49` / versionCode `291`；历史Beta38日志导出增量为 `d1f35d0`，此前Beta37功能提交为 `5bf92fe`、`4b7257a`。历史组合触发及模式移除后验证为876项单测0失败；本轮验证另见上方最新条目。`d9d69ba` 清理恒真双指资格状态，`161a171` 固定单指=拖拽、双指=分词并删除模式选择。

### 2026-11-02 诊断打码红线与 FanRuntime 拆分

- 诊断导出按 DIAGNOSTICS 双清单落地单一出口过滤器 `DiagnosticRedactionFilter`：默认模式仅脱敏凭据/令牌/密码字段、正文/剪贴板字段和URI查询参数；完整堆栈、异常消息、包名/类名、URI路径、时间戳原样进入runtime-log.txt。旧`filterRuntimeLine` allowlist、`redactSensitive`、`sanitizeStateSummary`、`sanitizeCompatibility`、`GestureDiagnostics.filterLine`已删除收敛。
- 问题反馈与诊断页新增“完整诊断模式”开关（默认关闭，`full_diagnostics`私有偏好）；开启后除凭据模式匹配外全部原样记录，manifest.txt列出实际应用的打码规则并明示“此文件包含操作细节”。导出失败原因不再折叠为异常类名，写入完整`error.toString()`。
- Beta39 `6ce51eb`（feat(diagnostic)）+ `e38af0f`（build，versionCode 281）；全量882项单测、Lint 0 errors/190 warnings/2 hints通过；Beta APK与SHA-256见交付任务 `D:/CODEX/Tasks/2026-11-02/mmgg-hyperdragshare-4-d-codex-tasks/outputs/DELIVERY.md`。设备验收未执行。
- FanRuntime迷你媒体控制块按窄接口搬移为独立`MiniMediaControls`（FanRuntime 2436→2307行），方法体逐字搬移无逻辑修改；`8bad1b3`后全量887项单测、Lint同上通过。Xposed API在JVM是编译期stub，vendor解析成功路径仍只能设备验证。

- 全量 `testDebugUnitTest`：877 项通过，0 failures、0 errors、0 skipped。
- `lintDebug`：0 errors、190 warnings、2 hints。
- `assembleDebug`：通过。
- `assembleBeta`（含 R8）：通过。
- Beta37 APK：`D:/CODEX/Tasks/2026-10-31/mmgg-long-press-drag-preview-tutorial/outputs/MMGG-1.7.219-beta.37.apk`，SHA-256 `7952285408D044FC596874C320C5832CE552136D2E8F0B00F9685B0551AA2243`；包元数据、R8和Android Debug签名核验通过。
- 本轮已在本人设备通过 `adb install -r` 安装并启动 MMGG；SystemUI/System Server 未重载，因此新增系统 Hook 的设备命中状态仍未知。

### 2026-10-31 一键诊断导出

- 日志设为关闭或系统日志时，用户仍可直接导出诊断 ZIP；新增 `operation-trace.txt`，保存当前 MMGG 应用进程内最近160条受控阶段摘要。它与跨进程 `root-connection.txt` 分开，不声称是完整原始运行日志。
- 诊断页不再要求普通用户修改日志等级或目的地；ZIP注明原始系统日志是否不可读，同时照常包含现有安全摘要。配置不会被导出流程修改。
- 新反馈包 `diagnostic-bundle-20261031-204508-501.zip` 自报 Beta36/278、Xiaomi 2210132C（nuwa）、OS4.0.27.0.XEOCNXM；`portal_root=READY`、`root_input=READY`、整体NORMAL，Root尝试完成到`lease_ready`。这不是两名反馈用户的设备包，不能据此判定其问题已解决。
- Beta38：879项单测、Lint 0 errors/190 warnings/2 hints、Debug和Beta/R8构建通过。APK哈希和交付记录见本轮任务目录 `D:/CODEX/Tasks/2026-10-31/mmgg-diagnostic-export-simple/outputs/DELIVERY.md`。
- 未操作设备、未安装、未push。两名用户的Root授权虽已由用户确认，旧包状态仍不足以归因；下次只需直接复现并导出，不需要调整日志设置。

### 2026-10-31 OS4 小窗接入增量

- 通知栏小窗：仅改写 SystemUI 已存在的 Xiaomi 小窗 Action，受独立开关控制，默认关闭。
- 迷你小窗媒体控件：按 mini task 和 MediaSession 显示播放/暂停、上一首/下一首，可选静音租约，默认关闭；媒体会话或 Xiaomi 音量接口不可用时保持原小窗。
- 黑名单和数量限制：仅在 `android/android` system_server 安装精确方法 Hook，两个开关独立控制，读取配置失败保持关闭。
- 微信底部特殊菜单避让已由 `2ddc316` 移除；这不等同于微信最终接收或发送成功。

## 2026-10-31 传送门 Root 提示调查

- 新反馈包自报Beta33/275；MMGG Root、注入和服务READY，Portal Root/输入UNAVAILABLE；完整运行日志缺失，设备上su失败原因未知。
- `ModuleActivation`原本将任意非零退出码视为DENIED，现改为UNKNOWN并只记安全退出码；未改变su命令、4秒超时与实际权限边界。
- 按压设置不再用缓存诊断拦截保存；订阅已有状态报告并在退出页时注销，未知不提示未授权。保存开关不代表输入已经连接。
- 新增6项测试；定向29项通过，随后本轮全量838项及Lint/Debug/Beta验证通过。
- 旧版可先分别允许MMGG和传送门Root，再“通用设置→重载模块→热重载”，离开并重进按压设置页；不能保证解决真实Root命令失败，不要求清数据或反复整机重启。
- 本轮证据与恢复入口：`D:/CODEX/Tasks/2026-10-31/mmgg-portal-root-diagnosis/`。原始私人诊断包未入Git；微信、双指与小窗待办未改。

## 已知未完成

- 没有完成真实 HyperOS 设备的完整回归：微信/QQ 中转站接收、长按菜单后的 MOVE/CANCEL、输入法与小窗、AICR、底角、深浅色和玻璃背景仍需要设备证据。
- 设备端双指候选仍保留 `DEVICE NO-GO` 结论；Candidate B 不得直接当作当前修复已验收。
- `DragShareController` 和 `HyperOsFreeformBridge` 仍偏重；后续只在存在行为护栏时继续抽取，不做机械拆文件。
- UI 仍缺少稳定的真实渲染矩阵，尤其是窄屏、大字体、底部安全区、输入法和毛玻璃背景。
- AICR、Portal 和 HyperOS 私有接口的跨版本行为不能由本地测试完全证明。

### 2026-10-31 松手分流与待拖预览

- 开发仍在 `beta` 基线 `0cd3141` 上进行；稳定线与 Candidate A/B 未改。此增量尚未提交或发布。
- 按 Root 手势起点至真实松手时间分流：`<T` 不触发；`T.. <2T` 显示原分词/图片操作；`>=2T` 打开待拖预览。单指和双指共用各自已有合法时长配置，不增加第二个设置。双指第一指仍只等待第二指，资格确认后按现有 pointer 语义续行。
- 文字待拖把手位于分词面板底部，载荷取当前选区；未选词时不可拖全文。移除正文词块的长按直接拖拽，保留滑选。系统拖放取消由现有 BigBang 窗口保留选区。
- 图片 `>=2T` 使用有界触摸预览；预览窗口只消费自身 View 触摸，Root 仍独占手势判定。第二次按下并超过 touch slop 后进入现有图片拖放；外部轻点需 DOWN/UP 都在预览外且未超 slop。取消/未命中目标返回待拖预览。
- 设置加入可重看的“操作教程”，演示文字、图片和页面内模拟接收区；没有连接外部 App、Root 或真实剪贴板。
- 本轮测试：定向 32 项及最终全量 877 项均通过（0 failure/error/skipped）；Lint 0 errors/190 warnings；Debug/Beta(R8) 通过。Beta37 `1.7.219-beta.37` / `279` APK SHA-256 为 `7952285408D044FC596874C320C5832CE552136D2E8F0B00F9685B0551AA2243`，交付记录在 `D:/CODEX/Tasks/2026-10-31/mmgg-long-press-drag-preview-tutorial/outputs/DELIVERY.md`。
- 设备验证未执行。真实触摸窗口路由、跨应用拖放接受及不同 ROM 的视觉效果待验证；不宣称实机已解决。

## 继续入口

1. 先读取本文件、`docs/ARCHITECTURE_ROADMAP.md`、`docs/BRANCHES.md`、`docs/DECISIONS.md`。
2. 核对两个工作树的 `git status`、当前分支和最新提交；不要仅按版本号判断新旧。
3. 无设备轮次优先按路线文档推进：控制器职责地图、Freeform 请求边界、能力诊断投影、验证记录收口。
4. 需要改生产代码时先增加行为测试；所有 Gradle 任务串行执行。
5. 需要设备时只执行高价值矩阵，记录设备、版本、日志和最后确认阶段；未知结果保持未知。

## 历史索引

## 2026-10-30 HyperFreeformX 参考增量

- 提交 `7d4ca1e` 为窗外长按动作的最小增量：新增 `outside_long_action` 配置，默认无操作；启用后复用已有关闭、迷你、全屏、贴边路径。
- 长按在移动超过触控阈值、取消或输入法显示时不会执行；原窗外单击/双击行为和旧配置键保持不变。
- 该增量只针对小米 ROM Root 路径，不引入 Flyme/AOSP 通用小窗 fallback；通知栏小窗、媒体控件、全局限制修改仍待独立契约审查。
- 定向 `ImeFreeformLifecycleTest`、配置兼容测试：32 项通过；全量测试 832 项通过。
- Beta/R8、Debug、Lint 已通过；当前 ADB 无设备，实机安装与 OS4 行为验证未完成。
- 用户补充确认：参考模块原面向 OS3，但绝大部分功能在 OS4 已正常使用；后续按可复用的 Xiaomi/HyperOS 契约推进，不因 Launcher Rust 重构而默认放弃功能。用户同时授权扩大 SystemUI、MiuiHome 与 Xiaomi Freeform 相关 Hook 范围，但仍按包、进程、类和方法精确限制。

- 分支与归档决策：`docs/BRANCHES.md`、`docs/DECISIONS.md`。
- 动作与资源：`docs/DRAG_ACTIONS.md`。
- 退出与销毁：`docs/EXIT_LIFECYCLE.md`。
- 诊断与导出：`docs/DIAGNOSTICS.md`、`docs/OPERATION_TRACING.md`。
- 手势输入调查：`docs/TOUCH_STREAM_DIAGNOSTICS.md`、`docs/NATIVE_PRESS_INPUT.md`。
- 当前离线路线：`docs/ARCHITECTURE_ROADMAP.md`。
- 完整交接和实际命令：`D:/CODEX/Tasks/2026-10-30/mmgg-offline-stability-followup/`。
- 原始历史状态：`docs/archive/2026-10-30/CURRENT_STATUS_2026-10-30_before_compaction.md`。
