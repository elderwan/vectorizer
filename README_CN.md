# 🖼️ Vectorizer — 项目说明文档 (Project Documentation)

> **项目名称**: Vectorizer  
> **面向平台**: JavaFX 桌面客户端  
> **核心功能**: 批量将位图 (JPG / PNG) 或压缩包 (ZIP) 转换为分层矢量图 (SVG)  
> **技术架构**: Java 24 (兼容 LTS) · JavaFX · Potrace CLI · Log4j2 · Maven

---

## 1. 项目整体架构与设计模式

本项目严格遵循 **MVC (Model-View-Controller)** 软件架构设计模式，并配合实用工具包 (Util) 实现高内聚、低耦合：

```
se233.vectorizer
├── model          // 数据模型与核心转换服务 (业务逻辑层)
├── view           // JavaFX 视图组件与 UI 布局 (展示层)
├── controller     // 业务控制器与事件调度器 (控制层)
├── util           // 工具辅助类 (文件校验、ZIP 解压、颜色统计)
└── exception      // 自定义异常体系
```

* **Model (模型层)**:
  * `VectorizationService`: 负责图像的采样、K-Means 颜色量化聚类、生成 PPM 蒙版、调用外部 Potrace CLI、多层 SVG 标签合并。
  * `ImageItem`: 单张图像实体，封装源文件、原图对象、矢量化结果路径以及个性化配置。
  * `ConversionSettings`: 转换配置实体（清晰度阈值、颜色模式、输出重命名规则等）。
* **View (视图层)**:
  * `MainView`: 主界面布局，组合左右预览区、参数配置区、缩放工具栏与底部状态栏。
  * `DropZonePane`: 拖放交互区域，负责监听并接收用户拖拽的文件或 ZIP。
  * `PreviewPane`: 左右对比预览区，使用 `ImageView` 展示原图，使用内置 WebKit 内核的 `WebView` 实时无损渲染矢量 SVG。
  * `ConfigPane`: 参数控制面板，负责展示与修改图像处理设置。
* **Controller (控制层)**:
  * `MainViewController`: 主控制器，负责图像队列管理、线程调度、防抖预览、异步结果监听。
  * `BatchProcessor`: 批处理器，负责批量并发预处理 (Preprocessing) 与最终批量并行导出 (Export)。
  * `AllEventHandlers`: 事件接线员，将视图各控件的触发事件与控制器的具体操作绑定，实现视图与控制器的解耦。

---

## 2. 核心功能及代码实现详解

### 功能 1：文件与压缩包导入 (Drag & Drop / ZIP Handler)
* **涉及类**: 
  * `DropZonePane.java` (拖拽事件接收)
  * `FileValidator.java` (文件扩展名校验)
  * `ZipHandler.java` (ZIP 解压与安全防御)
  * `MainViewController.java#loadFiles()` (加载与去重)
* **原理与知识点**:
  * **JavaFX 拖拽事件机制**: 监听 `DRAG_OVER` 并在拖动经过时设置 `TransferMode.COPY`，在 `DRAG_DROPPED` 中通过 `Dragboard.getFiles()` 提取文件列表。
  * **流式解压**: 使用 `java.util.zip.ZipInputStream` 遍历 Entry，解压到系统临时目录。
  * **ZIP Slip 漏洞安全防御**: 针对恶意构造的压缩文件（如包含 `../../` 路径 traversal 攻击），在解压每个文件前校验 `output.startsWith(extractionRoot)`，若越界则抛出异常阻断。
  * **易懂的代码结构**: 文件格式与扩展名判定改用直观的 `for` 循环遍历，去除了难以溯源的复杂 Stream 流。

---

### 功能 2：快速颜色数量分析 (Color Analyzer)
* **涉及类**: `ColorAnalyzer.java`
* **原理与知识点**:
  * **像素采样优化**: 对于超大图片（避免数百万像素点遍历卡死主线程），根据图片总像素计算采样步长 `step`，按网格步长进行抽样。
  * **位运算与颜色提取**: `image.getRGB(x, y) & 0x00FFFFFF`，通过位与操作清除高 8 位的 Alpha 透明通道，提取纯 RGB 数值。
  * **哈希去重与快速熔断**: 将 RGB 数值添加至 `HashSet` 中自动去重。一旦发现不同颜色数超过支持上限（5 种），立刻中止扫描并返回，保证瞬间响应。

---

### 功能 3：矢量化转换核心 (Vectorization Core)
* **涉及类**: `VectorizationService.java`
* **核心转换流程**:
  1. **K-Means 颜色量化聚类**:
     * 位图色彩繁多，直接描边无法分层。程序采用 **K-Means 聚类算法**（迭代 8 次），把全图所有像素聚类为用户指定的 $K$ 种主色调（1~5 色）。
     * **颜色距离算法**: 计算欧氏几何距离的平方 $(R_1-R_2)^2 + (G_1-G_2)^2 + (B_1-B_2)^2$（省去开平方运算以提升性能）。
  2. **生成单色蒙版 (PPM 格式)**:
     * 针对每种聚类出的颜色层，生成一张只包含该颜色的黑白 PPM (P6 二进制) 文件，符合 Potrace 命令行工具的输入格式。
  3. **调用外部 Potrace CLI (进程通信)**:
     * 使用 Java 原生 `ProcessBuilder` 执行外部命令：
       ```text
       potrace -s -o <output.svg> -t <turdsize> -C <hexColor> <input.ppm>
       ```
     * 读取命令退出码 `waitFor()`，若非 0 则精准抛出异常；捕获系统找不到程序的情况并提示 `CliToolNotFoundException`。
  4. **多层 SVG 合并 (Layer Merge)**:
     * Potrace 默认仅生成单层黑白 SVG。程序通过读取每个单层 SVG 的 `<g>...</g>` 路径标签，把颜色属性替换为对应真实聚类色，并按照图层顺序拼接进同一个完整 SVG 文件的 XML 结构中。
  5. **自动背景去除 (Background Removal)**:
     * 若用户勾选去除背景，算法会检测图像四个角落的像素分布，若属于相同主色层且占比超过 5%，则跳过该图层的绘制，实现背景镂空。

---

### 功能 4：多线程并发处理与防抖机制 (Concurrency & Debounce)
* **涉及类**:
  * `BatchProcessor.java` (多线程批处理)
  * `MainViewController.java` (调度器与防抖)
* **原理与知识点**:
  * **任务拆解 (Single Responsibility)**:
    * 拆分独立的 `PreprocessSingleTask` 与 `ExportSingleTask`（均实现 `Callable`），杜绝大段混乱的匿名函数。
    * 调试时可以直接在任务类和单张图片转换方法中打断点逐行进入。
  * **线程池与 CompletionService**:
    * 采用 `ExecutorService` 线程池（根据当前机器 CPU 核心数自动分配线程）。
    * 配合 `ExecutorCompletionService` 实现“哪张图片先转好就先回调哪张”，实时且平滑地刷新界面进度条。
  * **防抖优化 (Debounce 300ms)**:
    * 用户在调节颜色数、平滑度等参数时，使用 `ScheduledExecutorService` 延迟 300ms 执行刷新。若用户在 300ms 内再次更改参数，立即取消上一次任务，避免频繁重试导致卡顿。
  * **版本号机制 (AtomicLong Generation)**:
    * 使用原子类 `AtomicLong` 作为任务版本控制。当用户在异步预处理过程中切换图片或重置时，递增版本号；过期旧任务即便完成后也会因版本号不匹配直接丢弃，彻底消除多线程竞态条件。
  * **UI 线程隔离**: 所有耗时操作均在后台守护线程 (Daemon Thread) 运行，所有刷新界面操作均包裹在 `Platform.runLater()` 中交还给 JavaFX Application Thread 执行。

---

### 功能 5：实时左右对比与缩放预览 (Side-by-Side Preview)
* **涉及类**: `PreviewPane.java`
* **原理与知识点**:
  * **SplitPane 分割面板**: 原图与矢量结果各占 50%，用户可自由拖动分割线。
  * **WebView 渲染矢量 SVG**: 利用 JavaFX 内置的 WebKit 引擎，直接加载并高质量矢量渲染生成的 `.svg` 文件，支持无级缩放不模糊。
  * **程序化棋盘格背景 (Transparency Grid)**:
    * 使用 `WritableImage` 与 `PixelWriter` 在内存中交替绘制深浅色方格，以背景图案形式铺在预览区下方，直观展示透明区域。
  * **同步缩放与自适应**:
    * 放大 (1.2x)、缩小 (0.8x) 以及适应窗口大小 (Zoom to Fit)，两侧视图同步联动。

---

### 功能 6：批量重命名与结果导出 (Batch Export)
* **涉及类**: `BatchProcessor.java`, `MainViewController.java`
* **原理与知识点**:
  * **命名规则支持**:
    * 保留原名模式：例如 `photo.jpg` -> `photo.svg`。
    * 前缀序号模式：例如输入 `icon` -> `icon_1.svg`, `icon_2.svg`。
  * **文件名非法字符清洗 (Sanitization)**:
    * 过滤操作系统不允许的文件名特殊字符（`\ / : * ? " < > |`）自动替换为下划线 `_`，防止因文件重名或非法字符导致写盘崩溃。
  * **容错机制**: 批量处理中若某张图片损坏，错误会被捕获并统计到失败列表，其余正常图片继续完成导出，最终弹出统一结算弹窗。

---

## 3. 技术栈与知识点总结表

| 领域 | 核心技术点 | 应用位置 |
|------|-----------|---------|
| **架构与模式** | MVC 模式、职责单一原则、回调机制、依赖注入 | 整体工程代码 |
| **图形与界面** | JavaFX 26, FXML, SplitPane, WebView, WritableImage | `view` 包 |
| **并发多线程** | `ExecutorService`, `CompletionService`, `ScheduledExecutorService`, `AtomicLong` | `BatchProcessor`, `MainViewController` |
| **图像算法** | K-Means 聚类量化、RGB 采样抽样、色彩欧式空间距离、PPM P6 生成 | `VectorizationService`, `ColorAnalyzer` |
| **系统交互** | `ProcessBuilder` 启动外部进程、标准流管道重定向、退出码处理 | `VectorizationService` |
| **IO 与安全** | `ZipInputStream` 流处理、Path Traversal (ZIP Slip) 路径越界防护 | `ZipHandler` |
| **代码规范** | 清晰命名、无 `::` 双冒号易于调试、无晦涩 Stream 管道、结构扁平分类 | 全局重构代码 |

---

## 4. 演示与答辩重点建议 (Presentation Tips)

1. **直观演示**:
   * 拖入一张彩色图片，展示左侧原图与右侧矢量图的无缝对比。
   * 演示在右侧调节颜色数量（2色 -> 4色），展示实时防抖与 SVG 分层合并效果。
   * 演示拖入包含多张图片的 `.zip` 压缩包，观察进度条平滑并发处理，并导出到本地文件夹。
2. **回答架构与代码优势**:
   * 强调 **MVC 架构清晰**，界面展示和后台图像算法完全解耦。
   * 强调 **Clean Code 与高可维护性**：主流程方法简短明了，大任务拆解为具名 Task 类，方便在 IDE 中设置断点单步调试分析。
   * 强调 **多线程安全性与鲁棒性**：包含防抖机制、线程安全的版本号机制、ZIP Slip 攻击安全防御与非法文件名过滤。
