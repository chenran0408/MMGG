# MMGG 当前状态与接手入口

核对日期：2026-10-31。本文只维护当前事实；历史批次原文已归档到 `docs/archive/2026-10-30/CURRENT_STATUS_2026-10-30_before_compaction.md`，任务级证据仍保留在各任务目录。

## 工作线

- 开发线：`beta`，工作树 `D:/CODEX/Tasks/2026-10-22/new-chat-3/work/HyperDragShare`。
- 稳定线：`repair-from-189`，工作树 `D:/CODEX/Tasks/2026-10-20/new-chat-2/work/HyperDragShare`；本轮未修改，当前稳定工作树干净。
- 远程维护策略：GitHub 用户仓库只维护 Beta 与正式线；`upstream` 仅作原作者参考，不向其推送。
- Candidate A/B：继续作为冻结验证证据，不作为当前开发基线。
- 本轮没有 push、发布或改远程默认分支；与远程的领先数量请按实际 Git 状态读取。

## 当前代码事实

- Root evdev 仍是拖拽输入权威；真实 Root `UP/CANCEL` 决定结束。双指资格、Phase 1A owner、Blocker、微信专用 Hook 和控件避让阈值保持现状。
- `DragShareController` 仍负责手势协调、会话、目标选择和动作触发；`DragShareLifecycle` 为会话与预览退出 callback 提供 owner token。当前生命周期审计未发现稳定可复现的缺失保护，因此没有为拆分而改生产代码。
- FanRuntime、Freeform 几何/配置/目标、设置导航和生效提示已经有独立决策边界。
- Freeform 队列使用 generation、重试预算和 `QUEUED / REQUESTED / OBSERVED / UNKNOWN` 证据阶段。任务出现不等于最终启动成功。
- Activation 首页与诊断页共用 `ActivationPresentation`；`UNKNOWN` 不转换为权限缺失。
- 跨进程追踪只报告最后一个可观察阶段。`startActivity` 返回、拖放接受和 Provider descriptor 打开都不代表接收方已经读取或发送。

## 最新验证

当前源码版本为 `1.7.219-beta.37` / versionCode `279`；本轮版本和功能提交待从当前工作树提交后回填。

- 全量 `testDebugUnitTest`：877 项通过，0 failures、0 errors、0 skipped。
- `lintDebug`：0 errors、190 warnings、2 hints。
- `assembleDebug`：通过。
- `assembleBeta`（含 R8）：通过。
- Beta37 APK：`D:/CODEX/Tasks/2026-10-31/mmgg-long-press-drag-preview-tutorial/outputs/MMGG-1.7.219-beta.37.apk`，SHA-256 `7952285408D044FC596874C320C5832CE552136D2E8F0B00F9685B0551AA2243`；包元数据、R8和Android Debug签名核验通过。
- 本轮已在本人设备通过 `adb install -r` 安装并启动 MMGG；SystemUI/System Server 未重载，因此新增系统 Hook 的设备命中状态仍未知。

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

- 没有完成真实 HyperOS 设备的完整回归：微信缩略图、微信/QQ 中转站接收、长按菜单后的 MOVE/CANCEL、输入法与小窗、AICR、底角/侧滑、深浅色和玻璃背景仍需要设备证据。
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
