# Figma 设计 → 代码 实现工作流（非原生模型 PAT 方案）

> 适用对象：用 **Codex 跑非官方/第三方模型** 时，Figma 原生 MCP 连接器不可用，改用
> **Figma REST API + Personal Access Token（PAT）** 读取设计、导出截图，并继续严格执行
> “Figma 为准 + 真机验收”的工作流。

---

## 1. 背景与触发条件

Codex 应用的 Figma 能力依赖一个 **connector**（插件）：

- 插件 id：`plugin_connector_68df038e0ba48191908c8434991bbac2`
- 必装 connector：`connector_68df038e0ba48191908c8434991bbac2`
- 鉴权：OAuth，scope = `mcp:connect`，配置见 `~/.codex/config.toml` 的 `[mcp_servers.figma]`
- 依赖的官方工具：`get_design_context` / `get_screenshot` / `use_figma` / `get_metadata` 等

这些工具只在 **Codex 原生模型 + 应用内登录 Figma** 时才会注入到会话里。当出现以下任一情况，
就用本文的 PAT 方案替代：

1. 当前会话运行的是**非 Codex 原生模型**（第三方模型），Figma 连接器工具没有被注入。
2. 在 Codex 应用里安装/连接 Figma 插件提示“插件安装失败”：
   - 常见原因：OAuth 授权没走完（弹窗被拦/被关），或 Figma 账号套餐不支持该 connector
     （通常需 Professional 及以上）。
3. 会话里看不到 `figma.get_design_context`、`figma.get_screenshot` 这类工具函数。

判定方法：在会话里试试 `list_mcp_resources`。若能看到 `server="figma"` 的 `skill://`/`file://` 资源，
说明插件已装但**工具没有被授权注入**；此时工具调用不可用，走本文流程。

---

## 2. 前置条件

- 一个能访问目标文件的 Figma 账号，并拿到 **Personal Access Token（PAT）**。
- PAT 权限至少勾选 **File content: read**（读节点 + 导出图）。
- 目标设备已通过 `adb` 连接（本项目用 `80bd525f`）。
- 现成的 Windows 构建环境（见 `CLAUDE.md` 的“构建环境与工作流”）。

### 获取 PAT

1. 打开 Figma → 头像 → **Settings** → **Security** → **Personal access tokens**。
2. **Generate new token**，权限勾 **File content: read**。
3. 复制生成的 `figd_...`。
4. 只用在本机，**用完后立即在 Figma 里吊销**；不要把令牌写进仓库或提交。

---

## 3. 标准步骤

### 3.1 解析 Figma 链接

形如：

```
https://www.figma.com/design/{FILE_KEY}/{文件名}?node-id={NNN}-{NNN}&m=dev
```

提取：
- `FILE_KEY`（把 `/design/` 后那段取出来）。
- `node-id`：把短横线转成冒号，REST 里用 `493:1386` 形式。

从本项目中识别节点对应页面：在 `Front/app/src/main/java/com/qiuzhao/flashcards/ui/` 下用
`rg "Figma NODE:ID"` 找 `Figma 493:1386` 这类注释，即可定位代码文件。

### 3.2 用 PAT 读取节点

所有请求带 `X-Figma-Token: figd_...`。

```bash
# 1) 校验令牌 / 看账号
curl -s -H "X-Figma-Token: $TOK" "https://api.figma.com/v1/me"

# 2) 读节点树（含子元素、尺寸、填充、文字样式；id 里的冒号用 %3A）
curl -s -H "X-Figma-Token: $TOK" \
  "https://api.figma.com/v1/files/$FILE_KEY/nodes?ids=493%3A1386&depth=4"

# 3) 导出该节点渲染图（scale 可选，2x 更清晰）
curl -s -H "X-Figma-Token: $TOK" \
  "https://api.figma.com/v1/images/$FILE_KEY?ids=493%3A1386&format=png&scale=2"
# 返回 { "images": { "493:1386": "https://...png" } }，再 curl 该 URL 下载。
```

### 3.3 解析颜色 / 字体 / 几何

用 `ConvertFrom-Json`（PowerShell）把节点树转成对象后，逐层取：

- `type`：FRAME / TEXT / RECTANGLE / INSTANCE…
- `absoluteBoundingBox`：`{x,y,width,height}`（设计像素）。
- `fills[].color`：转成十六进制 `#RRGGBBAA`。
- `style`：`fontSize`、`fontWeight`、`lineHeightPx`、`letterSpacing`（单位 PIXELS）。
- `characters`：TEXT 内容（占位符/标签）。

PowerShell 坑：
- 颜色要**先 `[int][math]::Round(c.r*255)` 再 `ToString('X2')`**（double 不能直接 `X2`）。
- 查询串里的 `:` 要用 `%3A`，否则 Figma 会报“file_key/ids 缺失”。

### 3.4 定位并修改代码

1. 用节点 id 注释定位文件（见 3.1）。
2. 复用项目已有的 `DeckTheme`、`AppColors`、`AppText`、`MaterialSymbol`、`Surface` 等设计令牌；
   不要手写颜色或用 Agent 自创样式。
3. 遵循 `AGENTS.md`：
   - **402dp** 设计尺度（`scale = screenWidthDp / 402f`）。
   - Figma 是唯一视觉真相，复现其层级、间距、字号、圆角、颜色、图标与交互态。
   - 真机验收强制：改完必须 build + 安装到**真机**截图比对。
4. 区分**全局页面**与**项目主题页**：
   - 若 Figma 节点是全局蓝白（如“资料管理”类），不要硬套项目主题。
   - 若 Figma 节点本身带主题色，才跟随项目主题。

### 3.5 构建 & 真机验收

```powershell
# WSL 无 Linux JDK，先同步 Front 到 Windows 侧再构建
Copy-Item "\\wsl.localhost\Ubuntu-D\home\jiangyou3\jiangyou3_5\shanka_app\Front" "C:\Users\admin\Documents\ChatGPT\闪卡app\Front" -Recurse -Force
$env:JAVA_HOME = "C:\Users\admin\.gradle\jdks\jdk-17.0.20+8"
& ".\gradlew.bat" :app:assembleDebug :app:testDebugUnitTest --console=plain --no-daemon

# 真机安装 + 截图
adb -s 80bd525f install -r "C:\Users\admin\Documents\ChatGPT\闪卡app\Front\app\build\outputs\apk\debug\app-debug.apk"
adb -s 80bd525f exec-out screencap -p > <截图路径>.png
```

导航到目标页面后用 `adb shell input tap` + `uiautomator dump`（读 `bounds`）精确定位，再逐项比对。

### 3.6 记录结论

- 列出从 Figma 读到的关键值（色值、字号、几何）。
- 列出代码修正点（哪几处偏离、改了什么）。
- 给出验证证据：`BUILD SUCCESSFUL`、单测通过、真机截图。

---

## 4. 参考示例（已完成）

- 节点 `493:1386` = `资料管理-导入/编辑资料-导入文本`（402×874）。
- 实现位置：`Front/app/src/main/java/com/qiuzhao/flashcards/ui/ProjectScreen.kt`
  → `ProjectTextEditorScreen` / `ProjectTextField`。
- Figma 关键值：
  - 画布：白 `#FFFFFF`；输入框：`#EEF4FA`；顶栏返回钮：`#CCE6FF`；完成按钮：`#389DFF`。
  - 标签“文件标题/文本输入”：20px / 27px / weight 630。
  - 占位符：`#242436`；内容输入框高 **453dp**。
- 修正：全局蓝白配色 + 453dp 内容框 + 占位符 `#242436`；该页按 Figma 不继承项目主题。

---

## 5. 安全与边界

- PAT 属敏感凭据：只在本机会话使用、不落盘、用完吊销。
- 目标文件需对账号有访问权限；无权限会得到 403/404。
- 该方案是**读图/导出**通道；`use_figma` 之类的“写入 Figma”能力仍依赖原生连接器。
- 原生模型 + 已连接 Figma 时，优先用官方工具（`get_design_context` / `get_screenshot`），
  比 PAT 更省事、更准；本文只作为 fallback。

---

相关文件：
- `AGENTS.md`（Figma 保真 & 真机验收硬规则）
- `CLAUDE.md`（构建环境绕法、项目记忆）
- `docs/design-system/qiuzhao-flashcards/MASTER.md`（设计系统索引）
