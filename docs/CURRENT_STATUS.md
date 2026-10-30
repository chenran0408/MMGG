# MMGG 当前状态与接手入口

核对日期：2026-10-30。本文只维护当前事实；历史批次原文已归档到 `docs/archive/2026-10-30/CURRENT_STATUS_2026-10-30_before_compaction.md`，任务级证据仍保留在各任务目录。

## 工作线

- 开发线：`beta`，工作树 `D:/CODEX/Tasks/2026-10-22/new-chat-3/work/HyperDragShare`。
- 稳定线：`repair-from-189`，工作树 `D:/CODEX/Tasks/2026-10-20/new-chat-2/work/HyperDragShare`；本轮未修改，当前稳定工作树干净。
- 远程维护策略：GitHub 用户仓库只维护 Beta 与正式线；`upstream` 仅作原作者参考，不向其推送。
- Candidate A/B：继续作为冻结验证证据，不作为当前开发基线。
- 当前 Beta 分支相对本地 `origin/beta` 超前 33 个提交；本轮没有 push、发布或改远程默认分支。

## 当前代码事实

- Root evdev 仍是拖拽输入权威；真实 Root `UP/CANCEL` 决定结束。双指资格、Phase 1A owner、Blocker、微信专用 Hook 和控件避让阈值保持现状。
- `DragShareController` 仍负责手势协调、会话、目标选择和动作触发；`DragShareLifecycle` 为会话与预览退出 callback 提供 owner token。当前生命周期审计未发现稳定可复现的缺失保护，因此没有为拆分而改生产代码。
- FanRuntime、Freeform 几何/配置/目标、设置导航和生效提示已经有独立决策边界。
- Freeform 队列使用 generation、重试预算和 `QUEUED / REQUESTED / OBSERVED / UNKNOWN` 证据阶段。任务出现不等于最终启动成功。
- Activation 首页与诊断页共用 `ActivationPresentation`；`UNKNOWN` 不转换为权限缺失。
- 跨进程追踪只报告最后一个可观察阶段。`startActivity` 返回、拖放接受和 Provider descriptor 打开都不代表接收方已经读取或发送。

## 最新验证

当前源码版本为 `1.7.219-beta.34` / versionCode `276`。

- 全量 `testDebugUnitTest`：832 项通过，0 failures、0 errors、0 skipped。
- `lintDebug`：0 errors、181 warnings。
- `assembleDebug`：通过。
- `assembleBeta`（含 R8）：通过。
- Beta34 APK：`D:/CODEX/Tasks/2026-10-30/mmgg-meizu-freeform/outputs/MMGG-1.7.219-beta.34.apk`，SHA-256 `9473919A2A179C4B7390DB05F0671E9DFCDD70E36FFE845B7D257A3B28943ED0`；包元数据、R8 和签名已核对，使用 Android Debug 签名。
- Beta34 未安装或实机验收；ADB 当前无设备。

## 已知未完成

- 没有完成真实 HyperOS 设备的完整回归：微信缩略图、微信/QQ 中转站接收、长按菜单后的 MOVE/CANCEL、输入法与小窗、AICR、底角/侧滑、深浅色和玻璃背景仍需要设备证据。
- 设备端双指候选仍保留 `DEVICE NO-GO` 结论；Candidate B 不得直接当作当前修复已验收。
- `DragShareController` 和 `HyperOsFreeformBridge` 仍偏重；后续只在存在行为护栏时继续抽取，不做机械拆文件。
- UI 仍缺少稳定的真实渲染矩阵，尤其是窄屏、大字体、底部安全区、输入法和毛玻璃背景。
- AICR、Portal 和 HyperOS 私有接口的跨版本行为不能由本地测试完全证明。

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
