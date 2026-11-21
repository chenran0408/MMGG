# 拖拽动作执行与资源边界

当前提交、测试结果和功能待办见 [CURRENT_STATUS](CURRENT_STATUS.md)。本文维护代码地图与资源约定。

## 代码地图

```mermaid
flowchart TD
    ROOT[本次手势的 Root 或 MIUI 输入 UP / CANCEL] --> CTRL[DragShareController 协调结束与目标选择]
    STAGE[ImageStagingClient 暂存回调] --> PENDING[Controller 原 Session.pendingTarget]
    CTRL --> ACCEPT[launchShare 捕获内容/目标/URI/坐标]
    PENDING --> ACCEPT
    ACCEPT --> ACTION[DragShareActions.execute]
    ACTION --> SHARE[ShareLauncher 外部分享]
    ACTION --> COPY[ClipboardWriter 复制]
    ACTION --> SAVE[LocalImageSaver 保存图片]
    ACTION --> STASH[StashClient 中转站]
    ACTION --> SEG[Provider 分词请求]
    ACTION --> OCR[ImageOcrActivity 图片识字/扫码]
```

- `DragShareController` 负责协调输入、查询和选择目标以及清理预览和菜单。图片暂存归属发起拖动的会话。未选目标就取消时，队列会跳过该会话的待处理任务；已开始的文件传输会完成并忽略迟到结果。选定目标后，即使开始下一次拖动或原窗口关闭，仍由原会话取走目标并执行一次。
- `DragShareActions`：只分派已选动作并执行原动作代码；构造参数是 Context、控制器原 main Handler、同一 DragShareToast、固定的 accessibility 来源标记。入口接收 `CapturedContent payload, ShareTarget target, Uri stagedUri, float x, float y, String operation`，旧签名兼容包装保留；不持有控制器、Session、当前设置或访问内部字段的回调。operation是可选日志关联，不决定是否执行。
- `ShareLauncher`：文字/图片分享 Intent、按目标包授权和启动失败撤销授权/提示；调用启动成功只表示提交成功，不表示目标已经接收或发送。
- `ClipboardWriter`、`LocalImageSaver`、`StashClient`：继续处理已有平台调用、线程/流和跨进程存储职责，没有新增重复的执行端体系。

## 线程、输入和所有权

| 动作 | 输入与执行线程 | 资源与完成 |
|---|---|---|
| 外部分享 | payload、目标、staged URI；主线程 | 保持ACTION_SEND/MIME/Component/NEW_TASK；图片保持data、EXTRA_STREAM、ClipData、read grant。失败由ShareLauncher按原规则撤销本次目标授权并提示 |
| 复制文字/图片 | 文本或URI；ClipboardWriter原daemon/Root路径，完成回到MAIN | 回调只记日志/提示；Root超时/AppOp/URI导入保持原行为 |
| 保存图片 | `drag-share-local-save` 将原文件或已暂存的预览 PNG 复制到图库。预览暂存失败时才直接编码位图。 | 有源文件时保留格式与字节，暂存失败会提示用户。写入失败会清理未完成的图库文件。 |
| 中转站 | payload和URI；`drag-share-stash-save`，main Handler提示 | Provider打开并关闭暂存图流，StashStore复制为持久文件；动作不删除输入URI |
| 分词 | text/before/after及x/y数值；主线程Provider call | Bundle保存调用数据；仍检查accepted，拒绝/异常提示沿原路径 |
| OCR/扫码 | URI、qr标记；主线程startActivity | 维持显式Activity、hosted_uri、qr、image/*、ClipData与read flag；Activity与overlay接续原输入处理 |

执行器借用该次操作的 Bitmap 或 URI。异步动作持有已选内容和目标，后续手势不会覆盖它。控制器根据本次物理按压的归属决定是否接管预览或结束拖拽。两条链路的完成条件见[操作追踪](OPERATION_TRACING.md)。

模块 Provider 持有暂存文件，导入失败时删除本次临时文件。系统每 12 小时安排清理满 24 小时的暂存文件并撤销关联分享授权；应用启动时检测到上次清理已超过一天，会在后台补做。中转站持久文件和用户图库不参与清理。用户把有源文件的图片拖到“保存到本地”后，动作等待暂存完成，再将原文件写入图库一次。

异步完成仅输出原提示，不取消/清理新会话。Controller继续在execute正常返回后按原时机设置传入的shareSession.cancelled；未改成finally，也没有增加重试或全局去重框架。至多一次由原结束协调、pendingTarget清空和staging回调的Session检查保证，execute本身不是可去重的事务入口。

## 修改与验证

### Exit / Destroy Resource Ownership

窗口/面板的创建、持有、异步借用和退出释放点见 [退出资源归属](EXIT_LIFECYCLE.md)。Controller仍协调手势和窗口；动作类未重设计。销毁窗口不能提前释放已接受动作借用的Bitmap、文件或URI。退出清理撤销本窗口的监听/回调，旧回调先核对生命周期和owner，不影响新会话。

新增动作前核对现有ShareTarget与执行工具，明确是否需要URI/Bitmap/坐标及原线程。不能把整个Controller或可随意读取字段的回调交给动作类。窗口生命周期、会话模型和输入权威的变更须另批处理。

`DragShareActionsTest`覆盖真实ShareLauncher/Provider的Intent与grant/revoke调用、剪贴板/中转站与内置目标输入/提示；存图用测试专用工具shadow核对Bitmap、原工作线程和结果。`DragShareActionLifecycleTest`保留真实控制器与动作类，通过可控stage/clipboard边界验证结束、取消、重复旧回调、会话替换与失败隔离。已有`DragSharePreviewLifecycleTest`、`ShareImageProviderTest`、`SharedImageCleanupTest`、`LocalImageSaverTest`、`StashStoreTest`继续保留原断言。

文字分享的Intent需在`startActivity`进入框架之前取快照；框架可能把EXTRA_TEXT自动迁入ClipData。Robolectric环境中的URI权限查询在未grant和revoke后均可能返回允许，不能据此断言系统撤销已生效；本层只确认真实Provider调用Context时的包名/URI/read flags，设备执行结果单列验证。

替身只能证明对应逻辑边界；真实系统grant、目标应用读取/发送、Root剪贴板、AICR与宿主窗口限制仍需设备验证。检查命令见根 [AGENTS](../AGENTS.md)，当前实际结果只在CURRENT_STATUS和当轮交付记录维护。
