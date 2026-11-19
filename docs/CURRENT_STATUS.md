# MMGG 当前状态与接手入口

核对日期：2026-11-19。本文只维护当前事实；历史批次原文已归档到 `docs/archive/2026-10-30/CURRENT_STATUS_2026-10-30_before_compaction.md`，任务级证据仍保留在各任务目录。

## 工作线

- 开发线：`beta`，工作树 `D:/CODEX/Tasks/2026-10-22/new-chat-3/work/HyperDragShare`。
- 稳定线：`repair-from-189`，工作树 `D:/CODEX/Tasks/2026-10-20/new-chat-2/work/HyperDragShare`；本轮未修改，当前稳定工作树干净。
- 远程维护策略：GitHub 用户仓库只维护 Beta 与正式线；`upstream` 仅作原作者参考，不向其推送。
- Candidate A/B：继续作为冻结验证证据，不作为当前开发基线。
- 历史Beta43分支和候选tag已推送GitHub；本轮Beta49至Beta53候选仅本地交付，未推送或创建在线Release。

## 当前代码事实

### 2026-11-19 输入、取图与启动优化（Beta67 候选）

- Root 每个输入帧只投递一次，共用坐标转换，并通过显示变化更新几何缓存。长按已取消时跳过后续移动，候选定时器仅在截止时间变化时重排。MIUI 备用监听随 Root 可用性切换，保留已开始的手势直到结束。
- 原文件通过窗口、锁屏和遮挡检查后直接生成预览，原文件无效或不能生成预览时再截图。取消按压会移除尚未执行的原图请求并中止可关闭的读取。已进入 Root 助手的命令仍受原有超时约束。
- 蜂巢确认立即发起启动。需要关闭旧小窗时，监听匹配的任务消失回调，220 ms 为回调缺失时的上限。更多应用按入口预热名称，可见项按需加载图标，选颜色后补算匹配范围。快捷方式刷新与发布通知分开，图标版本按包、快捷方式和用户缓存。
- Beta67 为 `1.7.219-beta.67` / versionCode `309`。976 项单测、Lint、Debug 和 Beta 构建通过。APK 使用 Android Debug 证书，v2 签名验证通过。实机性能和功耗待对照。源码与 APK 见 [GitHub Beta67 预发布](https://github.com/chenran0408/MMGG/releases/tag/delivered/mmgg-1.7.219-beta.67)，本地校验与对照清单位于 `D:/CODEX/Tasks/2026-11-19/mmgg-beta67-performance/outputs`。

### 2026-11-19 中转站与手机管家适配（Beta66 候选）

- 独立中转站在拖出内容时让出屏幕，拖回边缘把手可在同一次拖拽中展开。其他来源的拖拽不改变托盘，释放未被接收时恢复原条目和选区。存入后短暂显示可点开的确认。Android 14 模拟器已验证存入回取、拖出缩边、拖回展开和拒绝原地放下。
- 原生侧栏按宿主包、进程、系统版本、侧栏接口及打开时的布局校验决定是否嵌入。手机管家 `40001365` 和 `40001381` 通过接口及布局静态比对。后续版本启动时也做探测，接口不符时保留独立中转站与原生侧栏。原生侧栏嵌入和对其他应用的实际拖放待 HyperOS 真机验收。
- 分享标题后缀取实际媒体类型。更多应用使用 Android 系统目录，安装、卸载及更新按包刷新，搜索与筛选复用缓存。
- 诊断 ZIP 新增 LSPosed API 102 宿主 PID、加载版本码和状态。原位热重载尚未启用，仍用原有重载入口。
- Beta66 为 `1.7.219-beta.66` / versionCode `308`。956 项单测、Debug Lint、Debug 与 Beta 构建通过。APK 使用 Android Debug 证书，v2 签名验证通过。交付见 `D:/CODEX/Tasks/2026-11-19/mmgg-stash-runtime/outputs`。

### 2026-11-19 运行时面板交互（Beta65 候选）

- 更多应用新增关闭按钮。键盘挤占空间时暂时收起筛选栏，极短横屏并排显示搜索和结果。关闭面板后恢复底角与窗外触摸捕获。
- 小窗菜单关闭时立即释放触摸层。静音绑定菜单打开时对应的任务与应用，旋转收起旧菜单。
- 现代风格拖拽预览就地换上晚到的原图，保持窗口及预览尺寸。分词滑选结束后才更改拖拽条布局，撤销编辑保留待拖模式。
- Beta65 为 `1.7.219-beta.65` / versionCode `307`。943 项单测、Lint、Debug 和 Beta 构建通过，APK v2 签名使用 Android Debug 证书。模拟器验证分词的打开、全选、取消、编辑、撤销、返回和再打开。更多应用、小窗与原图换帧的 HyperOS 宿主交互待真机。交付见 `D:/CODEX/Tasks/2026-11-19/mmgg-runtime-ui/outputs`。

### 2026-11-19 设置与目标编辑体验（Beta64 候选）

- 首页使用现有 Miuix 组件归组功能入口。运行检查开始后显示检查中，已完成和待确认状态去掉重复说明，仍可进入运行诊断。
- 底角与蜂巢的目标入口放到各自分类开头。目标选择支持横屏双栏，竖屏输入时为结果保留空间；旋转保留搜索和未保存的选择。明确区分取消与保存整组选择。
- 分享目标批量显隐只修改当前目录中的项。拖拽排序保留不可见目标的配置位置，取消拖动恢复拖动开始前的顺序。
- Android 14 模拟器验证浅色与深色、720×1400 与 1.3 倍字号下横竖屏键盘搜索、旋转草稿恢复、目标保存与取消、分享目标排序保存与取消，以及软重启确认返回。
- Beta64 为 `1.7.219-beta.64` / versionCode `306`。939 项单测、Lint、Debug 和 Beta 构建通过，APK v2 签名使用 Android Debug 证书。手势、传送门和小窗的 HyperOS 宿主交互由真机验收。交付见 `D:/CODEX/Tasks/2026-11-19/mmgg-ux-refinement/outputs`。

### 2026-11-19 全局流程回归（Beta63 候选）

- 更多应用组合筛选保留已选字母，只有符合当前搜索及字母的未加载图标会显示颜色识别等待。字母栏显示选中底色，无字母时网格可使用原留白。
- 小屏大字的分词页眉把标题和操作分开，并在安全高度内为文字保留空间。较宽的短屏把操作合为一行。Android 14 模拟器已检查 720×1400、1.3 倍字号的竖屏和横屏、深浅主题及键盘返回。
- 设置搜索结果只显示所属分组，手势分类去除项数。已核对重载范围、搜索打开确认及取消返回。
- Beta63 为 `1.7.219-beta.63` / versionCode `305`。936 项单测、Lint、Debug 和 Beta 构建通过。APK v2 签名使用 Android Debug 证书。审查与交付见 `D:/CODEX/Tasks/2026-11-19/mmgg-ux-flow-review`。

### 2026-11-19 设置生效与重启入口（Beta62 候选）

- 常用入口和运行诊断共用「重载传送门与 AI 引擎」。诊断中传送门未就绪会提示核对作用域和 Root 授权。
- 总开关的待生效快捷入口与高级组共用「软重启 Android」。确认时提示当前打开的应用会中断。重启系统界面和整机重启保留各自确认。
- 普通设置只提示立即生效或下一次操作生效。功能总开关已保存时显示待软重启，保存失败仍给出反馈。
- Beta62 为 `1.7.219-beta.62` / versionCode `304`。933 项单测、Lint、Debug 与 Beta 构建通过，APK v2 签名使用 Android Debug 证书。Android 14 模拟器验证了常用重载、高级重启折叠、软重启确认和取消返回。HyperOS 与 LSPosed 进程生效待真机验收。交付见 `D:/CODEX/Tasks/2026-11-19/mmgg-settings-runtime/outputs`。

### 2026-11-19 全局体验与重载入口（Beta61 候选）

- 通用设置保留「重载应用进程」，运行诊断直接打开同一确认。SystemUI 与侧栏重启、Android 软重启、整机重启收在「高级重启」。独立功能总开关保存后提供「软重启以应用」。
- 设置导航在配置变化后恢复当前页面。首次引导区分单双指入口，悬浮窗权限在返回设置页时刷新。分词编辑自动聚焦，诊断导出区分生成、位置选择和写入。
- 更多应用反馈区分加载与无结果，支持清除全部筛选和返回关闭。小窗启动失败给出反馈，迷你窗静音就地更新。图片暂存失败时拖放给出明确结果。
- Beta61 为 `1.7.219-beta.61` / versionCode `303`。932 项单测、Lint、Debug 和 Beta 构建通过，Beta v2 签名验证通过，证书为 Android Debug。体验检查与产物位于 `D:/CODEX/Tasks/2026-11-19/mmgg-global-ux/outputs`。

### 2026-11-19 本地审查与修复（Beta60 候选）

- 单指长按接管触摸前读取 Root 助手推送的录音状态快照，避让检查不再排在截图之后。Taplus 回调优先交付可用预览，媒体引用在后台按当前按压去重加载，原图读取失败保留当前预览。
- 横屏蜂巢遵从独立开关，配置更新时收起旧选区和蜂巢。应用作用域热重载按独立功能开关选择宿主。更多应用目录保留加载期间的多包变更。
- 中转站先提交条目删除再清理图片文件，继续使用数据库版本 2。迷你窗静音恢复保留原音量，播放器暂时消失时可延后恢复。
- Beta60 为 `1.7.219-beta.60` / versionCode `302`。审查记录与交付见 `D:/CODEX/Tasks/2026-11-19/mmgg-beta60-audit/outputs`。私聊语音、异步原图、迷你窗音量和应用作用域重载待设备验收。
- 928 项单测通过。Debug Lint 无错误，Debug 与 Beta 构建通过，Beta 包含 R8、资源收缩和发布 Lint。APK 使用 Android Debug 证书，v2 签名验证通过。SHA-256 为 `FCC1D1FB30B154C2069938E5312CBEE5DC718963B558F15AB67675E7440ABF79`。

### 2026-11-17 私聊语音避让（Beta59 候选）

- Beta55 真机复现抖音私聊按住说话时先启动麦克风，Root 取词随后返回文字，单指预览接管原触摸后录音停止。该场景绕过了已有的文字与控件语义避让，和传送门结果先到的竞态不同。
- Root 取词服务观察系统录音配置。单指确认后、取消原应用触摸及显示预览前，确认同一应用与用户在当前按压期间新启动且仍活跃的麦克风录音则放弃这次拖拽。已存在的录音、其他应用的录音以及双指分词不应用此规则；只读录音状态与归属。
- 已在原设备启动录音状态探针，未取得新的有效归属样本。用户将自行在 Beta59 上验收抖音私聊语音和普通文字的单指预览。
- Beta59 为 `1.7.219-beta.59` / versionCode `301`。920 项单测、Lint、Debug 与 Beta 构建通过。APK v2 签名通过，使用 Android Debug 证书。SHA-256 为 `4E3BB6D87E1933B22F37E5E8A5A8FEB70AE5C7EF0C164A2F81E983DE19F2DBF9`。

### 2026-11-17 迷你菜单跟随小窗（Beta58 候选）

- 对照 MeiWindow v1.4.7，菜单挂到目标迷你窗的 SurfaceControl 子层，继承小窗移动、缩放和圆角裁剪。使用上游两套普通和放大尺寸的图标及其按下资源。菜单以 180ms 淡入放大、120ms 淡出缩小，点击空白收起。
- 系统确认长按前仍处理普通点击和拖动。菜单只保护本次长按与菜单区域内的触摸序列，旧菜单的按钮或收起回调不会改变新菜单。调整尺寸按所在半屏锚定左右边缘。菜单显示时按当前应用音量刷新静音图标。
- Beta58 为 `1.7.219-beta.58` / versionCode `300`。918 项单测、Lint、Debug 与 Beta 构建通过，Beta APK 的 v2 签名通过。已连接设备仍安装 Beta55，Beta58 的系统触控和动画尚未真机验收。

### 2026-11-17 迷你小窗长按菜单（Beta57 候选）

- 对照 [MeiWindow v1.4.7](https://github.com/Leaf-lsgtky/MeiWindow/releases/tag/v1.4.7)，迷你窗状态回调只更新位置和生命周期，长按已命中的迷你任务才打开窗内菜单。短按、拖动超出阈值和任务离开迷你状态会取消待定菜单。
- 两个圆形按钮分别切换应用静音、缩放迷你窗。竖窗竖排、横窗横排，点击空白收起。普通小窗和全屏入口仍由原手势处理。缩放复用已核对的 HyperOS 迷你动画入口。
- 静音匹配应用 UID 的活跃播放器并读回验证，按播放器 ID 恢复已修改的播放器，删除全局媒体音量兜底。旧 ROM 的播放器代理兼容和播放开始前静音尚未接入。
- 917 项全量单测及追加的多播放器恢复用例通过，Lint、Debug 和 Beta 构建通过。Beta57 为 `1.7.219-beta.57` / versionCode `299`，v2 签名通过（Android Debug 证书）。ADB 可见设备但未获授权，未装机验证系统触控、缩放和旧 ROM 音频。

### 2026-11-16 独立功能开关（Beta56 候选）

- 传送门与大爆炸、手势增强、小窗增强分别在 LSPosed API 102 保存启动开关，重启手机后生效。首次升级沿用旧手势总开关，传送门和小窗默认开启。复制直达和 MiTrustService 保持独立设置。
- 关闭传送门后不安装 Taplus、宿主提取和对应框架 Hook；其他功能需要避让原生侧栏时只保留区域观察。关闭小窗增强后跳过限制、通知、窗口调整和小窗控件，手势仍可请求原生小窗。关闭手势增强后底角和蜂巢不抢输入，小窗的窗外操作独立生效。
- Beta56 为 `1.7.219-beta.56` / versionCode `298`。915 项单测、Lint、Debug 和 Beta 构建通过。Beta APK 的 v2 签名验证通过，证书为 Android Debug，SHA-256 为 `FF7A309D0F7AB98F049D8796E14F0313AFCB21E8A1D7CA084A93BF778EC8EF2F`。ADB 未发现设备，LSPosed 上的开关持久化和同作用域模块共存待真机确认。

### 2026-11-16 控件避让与小窗快捷菜单

- 单指按压的 Root 控件检查尚未返回时，暂存先到的传送门内容。Root 确认命中长按控件或排除应用后丢弃该内容。规则按当前触点与控件判断，不依赖微信包名。
- 小窗快捷菜单在首次布局前使用窗口参数中的尺寸更新位置；放大和关闭使用菜单所属的任务 ID，旧菜单回调不作用于另一只小窗。
- 913 项单测、lintDebug 和 assembleDebug 通过。ADB 未发现设备，抖音语音按钮以及多个小窗的交互仍待真机验证。

### 2026-11-09 Portal 按点提取与媒体文件保留（Beta55 候选）

- 继续复用系统 UIAgent 原始 View 导出：只有 MMGG 明确发起按点查询时，宿主现有 ContentCatcher 提取器才为确切命中的非标准文字 View 补 `flags=1` 原生文字；图片另用 `flags=2` 请求，避免 `flags=3` 覆盖映射结果。没有改微信手势输入规则、单／双指模式或传送门设置。
- 对选择出的准确 ImageView bounds，按同一触点再请求一次媒体来源。只读候选限于系统映射字段、标准 ImageView URI 及 Glide 的 ViewTarget/SingleRequest model；检查触点、bounds 和密码祖先。可打开的 URI/File/PFD 通过 UIAgent 回复回 Root，编码字节分块回模块后以现有受限 ShareImageProvider 原样暂存。预览 Bitmap 与原编码文件分开，分享 MIME 与动图格式不再经 PNG 重编码。
- 无可读引用、来源不是媒体文件、当前窗口/触点不匹配或超过现有32MB暂存上限时，不会把截图标成原文件；保留节点截图路径。原生 SystemUI/UIAgent 桥没有改变权限验证。
- 宿主提取 Hook 仍依赖当前 LSPosed 宿主作用域。此改动沿用现有微信／酷安作用域，**没有自动扩展到所有 App**；未 Hook 的应用不会运行媒体引用适配。本轮未安装设备或在线发布。
- Beta55 为 `1.7.219-beta.55` / versionCode `297`。908项单测、Lint、Debug/Beta构建通过；v2签名和SHA-256已核对。源码和候选 APK 交付见 `D:/CODEX/Tasks/2026-11-09/portal-hook-implementation/outputs/DELIVERY.md`。微信文字拖拽、双指分词、酷安原文件与 GIF 分享须在设备加载此候选后验收，源码测试不替代实机。

- Root evdev 仍是拖拽输入权威；真实 Root `UP/CANCEL` 决定结束。单指达到时长且内容节点合格后复用Blocker接管原应用触摸，不等慢速截图返回；仍保留控件避让。按压为组合触发：单指 fire=拖拽，双指 fire=分词，图片先OCR；用户最新规则优先于AGENTS中旧图片回退描述。
- `DragShareController` 仍负责手势协调、会话、目标选择和动作触发；`DragShareLifecycle` 为会话与预览退出 callback 提供 owner token。当前生命周期审计未发现稳定可复现的缺失保护，因此没有为拆分而改生产代码。
- FanRuntime、Freeform 几何/配置/目标、设置导航和生效提示已经有独立决策边界。
- Freeform 队列使用 generation、重试预算和 `QUEUED / REQUESTED / OBSERVED / UNKNOWN` 证据阶段。任务出现不等于最终启动成功。
- Activation 首页与诊断页共用 `ActivationPresentation`；`UNKNOWN` 不转换为权限缺失。
- 跨进程追踪只报告最后一个可观察阶段。`startActivity` 返回、拖放接受和 Provider descriptor 打开都不代表接收方已经读取或发送。
- 传送门图片引用优先直接下载HTTP(S)或读取content/file/绝对路径；宿主IconManager作为后备，本机实现用Glide原始尺寸，已核对且保留。引用加载成功即优先于渲染预览，不再因像素较少而丢弃；后备共享Bitmap独立复制。引用本身也可能指向缩略图，无引用的准确节点截图仍标为节点来源，不冒充原图。

### 2026-11-08 Beta53 自定义文字补齐候选（已安装，待微信新进程验收）

- Beta52探针已在用户重启宿主后实际命中：微信双指经系统ViewContentFetcher从MMNeat7extView取得文字，酷安也命中真实加载器。不能继续以安装后最初无日志为当前结论。
- 一次不注册UiAutomation的只读UIAgent请求返回101个节点。微信消息自定义View对外声明为TextView，却没有text属性；系统5.1.5的原始View导出仅为实际TextView写text，而原生内容提取器能读取这类自定义文字。这是微信依赖系统当前单/双指模式的已证实采集差异，模式选择本身仍是共用规则。
- 新增通用补齐：仅对MMGG明确按压查询、触点内、缺少text的非标准文字控件调用现有fetchViewContent(view,1,false)，再经系统XML净化写回导出记录。标准TextView、已有文字、图片、密码、其他触点和普通UIAgent请求保持原样。沿现有两个授权宿主的实际Interceptor/ClassLoader安装，不增加微信输入规则，不改系统模式、定时器或MotionEvent。
- Root在window_changed后转原始View路径时保留其window_id=-1及实际失败原因，不再被旧无障碍窗口编号或原因覆盖。
- 166个套件、897项测试全部通过；lintDebug为0 errors、200 warnings、2 hints；assembleDebug及assembleBeta含R8通过。Beta53为1.7.219-beta.53 / versionCode295，v2签名通过，Android Debug证书与已装候选一致。
- APK：`D:/CODEX/Tasks/2026-11-08/portal-source-investigation/outputs/beta53/MMGG-1.7.219-beta.53.apk`，5,028,063 bytes，SHA256 `E8A776947F2C54B4209ECEE0DB4F988724CF228A6B5A15C7E493EEEC2E7205C1`。已覆盖安装到测试设备，设备base.apk哈希一致；未发布、未批量提交现有工作树。
- 23:33:12重载Taplus原生前台服务，新PID23574，服务isForeground=true；Root输入23:33:13报告ready，取词reader也报告ready。Root helper已使用Beta53安装路径。微信仍为旧PID10659，尚未加载新文字适配器，不能宣称微信单指文字已修复。
- 23:33:33读取原生设置为support_long_press_mode=1、support_double_press_mode=1；此前23:17采样为mode2。此次未修改系统设置。待用户重开微信后，在固定模式下验单指文字拖拽、双指文字分词及单指图片，再切另一模式对照。
- 原图获取仍未修复；本批仅补自定义文字查询，不将截图或显示位图当作原文件。调查与验收入口：`D:/CODEX/Tasks/2026-11-08/portal-source-investigation/HANDOFF.md`。

### 2026-11-08 Beta52 内容来源调查候选（初装记录，后续采样见上）

- 按用户纠正撤回未交付的微信专用输入修改；不改系统模式、定时器或MotionEvent时间。微信/酷安仅安装同一只读探针，对照实际提取路径。
- 探针在宿主Application.attach后观察系统InterceptorProxy.setInterceptor收到的真实对象，再沿该对象的ClassLoader及实际提取器实例观察输入与提取分支。已按DEX核对字段r/f/o/i/j，不能使用JADX显示别名f81r/f467f。
- 原图缺失分层确认：Root节点图片是截图裁剪；系统5.1.5部分分支仅取当前Drawable位图或重绘View；模块下游Bitmap暂存会重新编码PNG。仅延长等待或修改Taplus消费者，不能从无源引用的缩略Bitmap恢复原文件。
- 下一实现方向是共用提取器及原媒体传递，而非微信专用输入或重写展示界面；原文件URI/流/PFD需要验证宿主访问与现有Provider授权边界。独立捕获请求通道尚未实现。
- Beta52（1.7.219-beta.52 / versionCode294）888项单测全部通过，lintDebug为0 errors、200 warnings、2 hints；assembleDebug、assembleBeta均通过。首次Beta的768MB堆在R8阶段GC抖动失败，提高至2GB后成功。
- APK SHA256 `D843B175D5C7ABACF0A1B665D38E7AB520A26662FC5E3489BC6D3FC05E28E060`，v2签名通过，Android Debug证书。23:13:34已覆盖安装到测试设备，设备base.apk哈希与构建一致；没有发布Release或提交整个未清工作树。
- 安装后酷安/微信PID仍为27539/17942，与安装前一致；logcat及当前LSPosed模块日志均未出现新探针记录。需宿主新进程取样，不能把安装成功说成探针命中或原图已修复。前台为酷安，未强停宿主或修改原生触发模式。调查报告：`D:/CODEX/Tasks/2026-11-08/portal-source-investigation/outputs/FINDINGS.md`。

### 2026-11-08 Beta50 通用长按菜单适配（验收中）

- 微信无障碍空根时，现有Root helper按明确按压请求调用已在本机验证的系统UIAgent只读原始View接口；不新增AccessibilityService、不新建UiAutomation会话。按真实包/UID/用户关联，前后核验WMS窗口、遮挡、锁屏与密码内容，复用通用分类和控件避让。
- 首个Beta50设备链路已经返回微信图片节点，但图片截图完成前原生长按菜单弹出，随后窗口校验拒绝。不能将该次节点可读当成拖拽已修复。
- 通用修复在合格文本/图片节点返回时先通知公共按压状态机；仅单指确认且仍按住时接管原应用触摸，不等图片解码/截图完成。未确认、双指、横屏禁止、被排除控件、泛OCR区域及旧代次均不从此入口接管。没有微信包名特判，没有放宽窗口校验。
- 用户之后手动确认微信图片单指可拖拽；微信文字与表情仍不行，表情拖出关联文字。本机脚本测试因测试窗口变化而未形成菜单冲突接管证据，不作为反证或成功证明。
- 通用长按菜单修复按内容候选可用、单指确认两个条件接管Root原触摸；确认先到/内容先到、短按、双指、旧代次均有测试。
- 表情包候选修复：仅当图片明确为强图片节点、并与普通描述文字重叠且允许拖图时，强图片优先；可编辑文字仍优先。根级同边界ImageView＋TextView测试通过；设备上尚未复测微信文字/表情节点，不能把节点用例说成实机结论。
- Beta51（1.7.219-beta.51 / versionCode293）`testDebugUnitTest lintDebug assembleDebug assembleBeta`通过，884项测试0失败/错误/跳过，Lint 0 errors、200 warnings、2 hints。APK已验v2签名，Android Debug证书与设备Beta49/50相同；SHA256 `A3D62C8046714464F969A61AA24542A241A1E41BA9F72238B8E6BCFF10B965CD`。本轮没有安装Beta51或在线发布，设备留给用户继续手测。
- 服务组合已补做UiAutomation＋Monica＋TalkBack，系统同时绑定MiuiEnhanceTalkback；微信窗口Id仍为-1。所有临时secure设置已恢复核对。此结果只限本机这些组合，不否定GKD Fork在其他设备的结果，不自动新增辅助服务开关。

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

当前开发源码版本为 `1.7.219-beta.55` / versionCode `297`；历史Beta38日志导出增量为 `d1f35d0`，此前Beta37功能提交为 `5bf92fe`、`4b7257a`。历史组合触发及模式移除后验证为876项单测0失败；当前验证另见上方最新条目。`d9d69ba` 清理恒真双指资格状态，`161a171` 固定单指=拖拽、双指=分词并删除模式选择。

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
- 当前排查入口为「运行诊断」或「通用设置 → 重载传送门与 AI 引擎」。先检查 MMGG 和传送门的 Root 授权。
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
