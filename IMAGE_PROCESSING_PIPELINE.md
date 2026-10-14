# 图像处理全流程与代码调用链路解析 (Image Processing Pipeline)

本文档详细梳理了当用户将一张图片拖拽进软件窗口后，**程序首先进入哪个方法，整个处理流程依次经过了哪些类、方法和代码，以及各自负责的核心逻辑**。方便在答辩与 Presentation 时向导师和评委老师进行专业、清晰的代码级讲解。

---

## 一、 全流程宏观时序图 (Pipeline Overview)

```mermaid
sequenceDiagram
    autonumber
    actor User as "User"
    participant EH as "AllEventHandlers"
    participant FV as "FileValidator / ZipHandler"
    participant MC as "MainViewController"
    participant BP as "BatchProcessor"
    participant VS as "VectorizationService"
    participant CLI as "Potrace CLI"
    participant PP as "PreviewPane"

    User->>EH: 1. 拖入图片或 ZIP 文件 (Drag and Drop)
    EH->>EH: 首先进入 handleDrop 方法
    EH->>FV: 2. 校验文件格式 / 解压 ZIP (防范 ZIP Slip)
    FV-->>EH: 返回合法的文件列表
    EH->>MC: 3. 调用 loadImages(validFiles)
    MC->>MC: 生成版本号自增 (generation.incrementAndGet)
    MC->>BP: 4. 提交后台并发分析 preprocessAsync
    BP-->>MC: 分析完成回调 (图片尺寸与色彩数)
    MC->>PP: 5. 左侧显示原图，显示 Loading 动画
    MC->>MC: 6. 防抖延时 300ms 调度
    MC->>VS: 7. 异步提交矢量化 convertAsync
    Note over VS: 图像核心算法处理阶段
    VS->>VS: A. K-Means 颜色量化聚类
    VS->>VS: B. 智能多图层渐变背景检测
    VS->>VS: C. 生成单色掩码 PPM
    VS->>CLI: D. ProcessBuilder 调用外部 Potrace
    CLI-->>VS: 返回各图层单色 SVG
    VS->>VS: E. 提取路径合并为多层 SVG
    VS-->>MC: 8. 返回生成好的 SVG 文件路径
    MC->>MC: 9. 写入当前设置缓存
    MC->>PP: 10. WebView 加载渲染矢量图并关闭 Loading
```

---

## 二、 详细代码执行链路逐级剖析 (Step-by-Step Code Walkthrough)

### 阶段 1：拖拽事件捕获与视图层转发 (View Layer Event Capture)
* **用户拖拽松手时，首先触发的 UI 方法**：
  * **当处于初始无图界面时**：[`DropZonePane.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/view/DropZonePane.java#L52-L61) 第 52 行的 `setOnDragDropped` 事件监听器：
    ```java
    setOnDragDropped(event -> {
        Dragboard board = event.getDragboard();
        boolean accepted = board.hasFiles();
        if (accepted) {
            filesConsumer.accept(board.getFiles()); // 触发回调转发文件列表
        }
        event.setDropCompleted(accepted);
        event.consume();
    });
    ```
  * **当处于已加载图片的编辑界面时**：[`MainView.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/view/MainView.java#L73-L82) 第 73 行的 `editorPane` 拖拽事件过滤器：
    ```java
    editorPane.addEventFilter(javafx.scene.input.DragEvent.DRAG_DROPPED, event -> {
        Dragboard board = event.getDragboard();
        if (board.hasFiles() && onFilesAccepted != null) {
            onFilesAccepted.accept(board.getFiles());
        }
        event.setDropCompleted(true);
        event.consume();
    });
    ```

---

### 阶段 2：事件总线连接与控制器业务入口 (Wiring to Controller)
* **事件绑定位置**：[`AllEventHandlers.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/controller/AllEventHandlers.java#L21-L25) 第 22 行：
  ```java
  public static void install(MainViewController controller, MainView view) {
      view.setOnFilesAccepted(files -> controller.loadFiles(files)); // 将视图拖拽事件接入控制器
      installNavigationHandlers(controller, view);
      installActionHandlers(controller, view);
  }
  ```
* **控制器的真正业务入口方法**：
  👉 [`MainViewController.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/controller/MainViewController.java#L93-L131) 第 93 行的 **`public void loadFiles(List<File> files)`**！

---

### 阶段 3：文件识别、安全解压与并发预处理 (Validation, ZIP Slip & Preprocessing)
* **涉及类**：[`MainViewController.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/controller/MainViewController.java), [`FileValidator.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/util/FileValidator.java), [`ZipHandler.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/util/ZipHandler.java), [`BatchProcessor.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/controller/BatchProcessor.java)
* **执行步骤**：
  1. `loadFiles` 第 97 行首先调用内部私有方法 `resolveImageFiles(files)`（第 484 行）：
     * 若为单 `.zip` 文件：调用 `ZipHandler.extractImages(files.get(0))` 解压，内含 **ZIP Slip 漏洞安全防御**（校验解压目标路径必须位于系统安全临时目录内，阻断 `../../` 恶意路径逃逸）。
     * 若为普通图片：调用 `FileValidator.isSupportedImage(file)`，仅允许合法且可读取的 `.jpg / .png` 图片通过。
  2. 遍历校验通过的图片，去重后实例化为 `ImageItem` 数据对象：`ImageItem item = new ImageItem(file);`。
  3. 第 124~126 行：
     * `mainView.showEditor()`：界面从初始拖拽框切换为主编辑工作台。
     * `refreshPreview()`：刷新左侧原图显示。
     * `startInitialPreprocessing(loadedItems)`：向后台提交并发分析任务。
  4. **并发线程池分析 (`BatchProcessor.preprocessAsync`)**：
     * 利用 `ExecutorService` 线程池（按照机器 CPU 核心数调度），并发分析每张图片的像素尺寸以及调用 `ColorAnalyzer.countDistinctColors` 快速采样统计颜色数，并通过 `Platform.runLater()` 平滑更新底部进度条。

---

### 阶段 4：防抖调度与缓存检测 (Debounce Scheduling & Cache Hit)
* **涉及类**：[`MainViewController.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/controller/MainViewController.java#L275-L335)
* **执行方法**：`schedulePreviewConversion()` -> `startPreviewConversion(generation)`
* **核心代码逻辑**：
  1. **300ms 防抖倒计时**：
     * 调用 `previewScheduler.schedule(..., 300, TimeUnit.MILLISECONDS)`。
     * 若用户在 300ms 内再次改动颜色或切换图片，旧任务会被直接取消（`pendingPreview.cancel(false)`），杜绝频繁请求卡顿。
  2. **缓存快速命中 (Cache Hit)**：
     * 当倒计时结束进入 `startPreviewConversion` 时，首先检查 `item.getCachedSvg(settings)`。
     * 若该图片在当前相同设置下之前已转换过，直接取用缓存路径，**零延迟跳过重新运算**！
  3. **未命中缓存时**：
     * 调用 `mainView.getPreviewPane().setBusy(true)` 展示转圈加载指示器。
     * 包装后台 JavaFX `Task<Void>` 提交至 `previewExecutor` 线程池，调用底层算法服务：
       ```java
       Path svg = vectorizationService.convertAsync(item, settings).join();
       item.setVectorizedPath(svg);
       ```

---

### 阶段 5：矢量化核心算法管道 (Vectorization Algorithm Core)
* **涉及类**：[`VectorizationService.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/model/VectorizationService.java)
* **执行方法**：`convertAsync(item, settings)` -> `convert(...)` -> `convertColorLayers(...)`

整个矢量化计算在后台守护线程运行，依次经历 5 个精密子步骤：

#### 步骤 A：K-Means 颜色量化 (`quantize`)
* 位图拥有成千上万种颜色，无法直接用于矢量描摹。
* 算法以均匀网格对图像像素进行抽样（上限限制在 100,000 像素以保障秒级性能）。
* 执行 **K-Means 聚类迭代（8 次循环）**，通过计算 RGB 欧氏空间距离将整张图片所有像素点分配到 $K$ 种最具代表性的主色调调色板中。

#### 步骤 B：智能多图层渐变背景检测 (`detectBackgroundLayers`)
若用户勾选了 `Remove background`：
1. **边框四角采样 (`countEdgeAndCornerPixels`)**：统计全图上下左右 4 条外边框与 4 个顶角像素，计算每个颜色层的外边缘占据率（Edge Ratio）与角点数（Corner Count）。
2. **主背景种子确定 (`findInitialBackgroundSeeds`)**：将边缘占比 $\ge 20\%$ 的绝对主导图层作为基准背景种子。
3. **色彩空间渐变扩展 (`expandGradientBackgroundLayers`)**：
   * 采用迭代传播算法（Propagation），针对同样触及外边缘的相邻色阶，计算其与背景种子之间的 RGB 欧式几何色彩距离：
     $$\Delta C = \sqrt{\Delta R^2 + \Delta G^2 + \Delta B^2}$$
   * 若距离在渐变过渡容差范围内（$\le 95 \sim 125$），判定为同一种天空或环境渐变底色，自动并入待移除背景集合，完美解决深浅渐变天空扣不干净的问题。
4. **前景安全兜底**：总背景移除上限不得超过全图的 $88\%$，强制保留中心主体，杜绝输出空白文件。

#### 步骤 C：生成单色二值化掩码文件 (`writeMaskPnm`)
* 针对每一个需要保留的非背景图层，将其单独提取为一个二值化黑白掩码（当前层颜色像素置为 0 黑色，其余像素置为 255 白色）。
* 将其按标准二进制格式写入临时磁盘文件 `layer-X.ppm`（P6 二进制 PPM 格式）。

#### 步骤 D：调用外部 Potrace CLI 进行单色描边 (`runPotrace`)
* 使用 Java 原生 `ProcessBuilder` 启动外部进程：
  ```bash
  potrace -s -o layer-X.svg -t <turdSize> -C <#HEXColor> layer-X.ppm
  ```
* **Detail 参数的映射控制**：
  * Low -> `-t 10`（过滤微噪点，保留最细微边缘与文字）
  * Medium -> `-t 50`（默认平衡档位）
  * High -> `-t 100`（强力滤波，平滑轮廓，消除噪点）
* 通过 `process.waitFor()` 严密监控进程退出状态码。

#### 步骤 E：提取路径与多层合并 (`mergeSvgLayers`)
* 读取各个单层导出的 `layer-X.svg` 文件。
* 解析并提取出每个文件内部的 `<g>...</g>` 路径矢量节点，将占位黑色替换为该图层真实的 RGB 十六进制颜色代码。
* 按图层层叠顺序组装写入最终生成的统一 `.svg` 文件中。

---

### 阶段 6：界面渲染与缓存呈现 (UI Display & Cache)
* **涉及类**：[`MainViewController.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/controller/MainViewController.java), [`PreviewPane.java`](file:///d:/workspace/idea/CMU/Adwance%20program/term%20project%201/src/main/java/se233/vectorizer/view/PreviewPane.java)
* **执行逻辑**：
  1. `VectorizationService` 的 `CompletableFuture` 完成回调。
  2. `MainViewController` 调用 `Platform.runLater()` 回到 **JavaFX Application Thread**。
  3. 将生成的 SVG 文件路径存入当前图片的设置缓存：`currentItem.putCachedSvg(currentSettings, svgPath)`。
  4. `previewPane.hideLoading()`：关闭转圈 Loading 动画。
  5. `previewPane.displaySvg(svgPath)`：
     * 利用 JavaFX 内置的 WebKit 引擎（`WebView` / `WebEngine`）直接无损矢量渲染该 `.svg` 文件。
     * 底层铺设有 `WritableImage` 与 `PixelWriter` 绘制的程序化黑白相间棋盘格透明背景图案，让抠图镂空效果清晰可见。

---

## 三、 答辩/面试老师常问问题速查表 (Q&A Guide)

| 老师提问方向 | 建议回答核心话术 |
| :--- | :--- |
| **Q1: 为什么拖入多张大图时，界面完全不卡顿？** | “因为我们严格遵守了 **UI 线程隔离**。文件拖入后，只在 UI 线程做轻量级状态更新；所有的图片读取、K-Means 颜色聚类、Potrace 进程调用全部交由 `BatchProcessor` 的 `ExecutorService` 守护线程池在后台并发执行，完成计算后再通过 `Platform.runLater()` 回传 UI 刷新。” |
| **Q2: 如果用户快速拖动滑动条调节参数，会不会触发成百上千次无效运算导致程序崩溃？** | “不会。我们在 `MainViewController` 中设计了 **300ms 的防抖机制 (Debounce)**。通过 `ScheduledExecutorService`，用户每次改动都会重置延时计时器，只有在用户手停下 300 毫秒后，才会真正向后台派发一次最新的矢量化计算。” |
| **Q3: 为什么你以前的背景去除遇到渐变天空删不干净，现在能删干净了？** | “传统算法只找出现次数最多的一层颜色，而渐变天空在量化后会被拆成 2~3 个深浅不同的色阶图层。我们优化为了**多图层渐变边界检测算法**：以图片四周边缘和四角作为种子，结合 **RGB 空间欧氏色差距离（Euclidean Distance）进行空间连续性传播扩展**，将属于同一个连续天空色系的所有图层全部识别并安全剔除，同时设置了 $88\%$ 的上限保护前景。” |
| **Q4: 如果用户拖入包含恶意路径的压缩包，系统会受到攻击吗？** | “不会。我们在 `ZipHandler` 中实现了针对 **ZIP Slip 路径穿越漏洞（Path Traversal）的强制安全防护**，在写入磁盘前严格校验解压目标路径的前缀必须位于临时安全目录内，如果发现诸如 `../../` 等非法字符，会直接抛出安全异常并阻断解压。” |
