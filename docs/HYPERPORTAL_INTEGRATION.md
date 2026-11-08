# HyperPortal 参考融合记录

核对日期：2026-11-08。本文记录本地 HyperPortal 源码与 MMGG 当前 `beta` 工作树的对照结果，避免把参考项目的注入能力误写成 MMGG 已支持的功能。

## 已融合

- 传送门回调中的 `MotionEvent` 现在会进入 MIUI motion fallback。
- Root evdev 未就绪时，fallback 为按压和拖拽控制器提供输入；Root 输入恢复 `READY` 后，Root 继续是唯一权威来源。
- fallback 在传送门运行时销毁时注销监听并停止线程。
- 这部分实现位于 `MiuiMotionSource`、`RootGateway` 和 `PortalHooks`，提交为 `2f85d0e`。
- 图片来源适配直接下载 Taplus 给出的 HTTP(S) `imgUrl`，并尝试读取 `content://`、`file://` 与绝对路径；传送门原图即使与预览同尺寸也可替换宿主预览。

这条路径用于覆盖自绘聊天界面或 Root reader 尚未就绪时的输入缺口。它不改变真实 Root `UP/CANCEL` 的结束语义，也不把 Taplus 的控制码当成物理松手。

## 未直接移植

### 微信文件对象提取

HyperPortal 的 `Bridge.extractWeChatFile()` 运行在 AES 注入的微信进程中，依赖微信聊天气泡的真实 `View`、私有消息对象、XML 元数据和微信 `.ref` 缓存目录。MMGG 当前捕获链只接收 Taplus `CatcherResult`、Root `AccessibilityNodeInfo` 快照和经校验的图片，不能从这些数据可靠还原微信附件对象。

因此当前没有加入“扫描微信目录猜文件”的逻辑，也没有新增微信包专用 Hook。图片加载使用 Taplus 明确返回的当前媒体引用，不据 URL 猜造微信 CDN 原图地址；若该 URL 自身是缩略图，仍需要目标来源的可验证原图引用。

### AES View/Compose 穿透

HyperPortal 通过打包注入 AES 在目标应用进程内遍历 `View`/`ViewGroup` 和 Compose 的 `AndroidView` 子树。MMGG 的 Root-only 约束没有同等的目标进程对象访问入口，不能只复制规则表就获得同样的命中能力。

### 全局拖放回飞

HyperPortal 的回飞修复依赖 system_server 中真正的 Android 全局拖放状态（`DragState`、`endDragLocked` 等）。MMGG 传送门主链使用 `ACTION_SEND`，中转站和原生侧栏另有独立拖放链，不能把 HyperPortal 的 system_server Hook 直接接入主链。

## xhttp 节点协议

`xhttp` 是代理客户端的传输/节点协议，不是 Android 内容捕获或拖放协议。MMGG 没有代理节点配置、订阅解析、TLS/传输握手或代理运行时，因此当前不支持，也没有必要为 HyperPortal 融合增加该协议。若未来要在同一应用中支持它，应另立代理功能模块，至少定义节点配置模型、订阅解析、传输实现、密钥/证书存储和独立网络验证；这些内容与 MMGG 的手势、取词和小窗功能无关。

## 当前边界

微信聊天若仍与其他应用表现不同，优先检查三段证据：

1. Root 输入是否进入 `READY`，fallback 是否收到首个 `DOWN`；
2. Taplus `CatcherResult` 是否包含当前触点对应的文本或 `image_pick` 数据；
3. Root 节点是否提供可用文本/图片语义，还是仅有自绘画面。

只有在确认缺口来自目标应用对象访问时，才需要另行评估 AES 注入或微信专用 Hook；不能用本记录把它们视为已实现。
