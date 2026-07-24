# Codey

> 🧠 AI Coding Agent · 一个住在你终端里的 AI 程序员
>
> *An agentic CLI that lives in your terminal, drives your shell, and edits your files.*

![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?logo=apachemaven&logoColor=white)
![LangChain4j](https://img.shields.io/badge/langchain4j-1.18-1A73E8)
![License](https://img.shields.io/badge/license-Internal-lightgrey)

Codey 是一个用 Java 17 编写的命令行 AI 编程 Agent。它通过 [LangChain4j](https://github.com/langchain4j/langchain4j) 接入 OpenAI 兼容的对话模型（默认指向 `https://api.minimaxi.com/v1` 的 `MiniMax-M3`），按 **工具调用（Tool Use）** 循环驱动本地 Shell 和文件系统，让模型像 Claude Code / Codex CLI 一样自主完成编码任务。

设计目标：

- **Agentic** — 模型自己决定调什么工具、调用几次、何时停止。
- **可控** — 危险命令、写入工作区之外的文件会被 `SecurityHook` 拦截并向用户确认。
- **可读** — 终端输出统一用 `ConsoleRenderer` 渲染成彩色卡片，CJK / Emoji 自动对齐。
- **可扩展** — `Tool` 接口 + `Hook` 事件系统，新增工具和拦截点只需几行代码。

---

## ✨ 核心特性

| 能力 | 说明 |
| --- | --- |
| 🛠️ **内置 6 个工具** | `bash` · `read_file` · `write_file` · `edit_file` · `glob` · `task` |
| 🪝 **Hook 事件总线** | `UserPromptSubmit` / `PreToolUse` / `PostToolUse` / `Stop` 四类拦截点 |
| 🔐 **安全沙箱** | `SecurityHook` 内置 deny list + 危险命令确认 + 工作区外写入保护 |
| 🎨 **统一终端 UI** | 圆角边框、配色、自动折行、CJK/Emoji 宽度适配、输出截断 |
| 🔁 **多轮会话** | 完整保留 `System / User / AI / Tool` 消息历史，支持工具循环 |
| ⚙️ **超时控制** | Bash 命令 120 秒硬超时，强制回收子进程 |

---

## 🧱 架构总览

```
┌──────────────────────────────────────────────────────────────┐
│                          Codey.java                          │
│                  (主循环 · 工具调用循环)                       │
└──────────────┬─────────────────────────────┬─────────────────┘
               │                             │
               ▼                             ▼
   ┌───────────────────────┐     ┌──────────────────────────┐
   │    HookDispatcher     │     │  ToolManager (注册表)     │
   │  ┌────────────────┐   │     │  ┌──────────────────┐    │
   │  │ SecurityHook   │   │     │  │  BashTool        │    │
   │  │ ContextInject… │   │     │  │  ReadFileTool    │    │
   │  │ SummeryHook    │   │     │  │  WriteFileTool   │    │
   │  └────────────────┘   │     │  │  EditFileTool    │    │
   └─────────┬─────────────┘     │  │  GlobTool        │    │
             │                   │  │  TodoWriteTool*  │    │
             ▼                   │  │  SpawnSubagent…  │    │
   ┌───────────────────────┐     └──────────┬───────────────┘
   │   ConsoleRenderer     │                │
   │  (卡片 / 颜色 / 宽度) │                ▼
   └───────────────────────┘     ┌──────────────────────────┐
                                 │   OpenAiChatModel        │
                                 │  (langchain4j-open-ai)   │
                                 └──────────────────────────┘
                                          │
                                          ▼
                                 ┌──────────────────────────┐
                                 │   MiniMax / OpenAI 兼容 API│
                                 └──────────────────────────┘
```

**工具调用循环**（位于 `Codey.toolAgent`）：

1. 携带历史消息 + 工具 schema 发送给模型。
2. 若 `finishReason != TOOL_EXECUTION` → 触发 `Stop` Hook，输出 AI 回复并结束。
3. 否则对每个 `toolExecutionRequest`：触发 `PreToolUse` Hook → 调工具 → 把 `ToolExecutionResultMessage` 写回历史 → 回到步骤 1。

---

## 🛠️ 工具矩阵

所有工具都实现 `com.vanilla.tool.Tool` 接口，由 `ToolManager` 静态注册。

| 名称 | 用途 | 关键参数 | 备注 |
| --- | --- | --- | --- |
| `bash` | 在子进程中执行 shell 命令 | `command: string` | 使用 `bash -c`，合并 stdout/stderr，**120s 强制超时** |
| `read_file` | 读取 UTF-8 文本文件 | `path: string`（兼容 `file_path`） | 仅返回原文，不做包装 |
| `write_file` | 写入 / 覆盖文件 | `path`, `content` | 自动创建父目录，`content` 允许空字符串 |
| `edit_file` | 精确文本替换 | `path`, `old_string`, `new_string`, `replace_all?` | 默认 `old_string` 必须只出现一次 |
| `glob` | 按 glob 模式查找文件 | `pattern`, `path?` | 默认跳过 `.git / node_modules / target / build / dist / .idea / .vscode` |
| `todo_write` | 维护任务清单 | `todos: [{content, status}]` | ⚠️ Schema 已定义，**执行逻辑尚未实现** |
| `task` | 派生子 Agent 处理复杂子任务 | `description: string` | 独立会话，最长 30 轮，只回传最终结论 |

要新增工具？实现 `Tool` 接口，在 `ToolManager` 的 `static {}` 里 `register(new MyTool())` 即可。

---

## 🪝 Hook 系统

```java
public interface Hook {
    String id();
    HookEvent support();   // 订阅哪个事件
    default int order() { return 0; }  // 同事件内执行顺序
    HookResult execute(HookContext context);
}
```

| 事件 | 触发时机 | 典型用途 |
| --- | --- | --- |
| `UserPromptSubmit` | 用户输入提交后、发给模型前 | 注入上下文、改写 prompt、拒绝敏感词 |
| `PreToolUse` | 工具调用前 | **安全检查 / 权限弹窗 / 审计日志** |
| `PostToolUse` | 工具调用完成后 | 记录执行结果、转换输出、脱敏 |
| `Stop` | 模型一轮对话结束（无更多工具调用） | 会话总结、清理资源 |

`HookDispatcher.dispatch` 会按 `order()` 升序串行执行；任一 Hook 返回 `HookResult.block(msg)` 即短路终止，并把 `msg` 作为工具结果回填给模型。

内置 Hook：

- `SecurityHook`（已启用）— deny list 硬拦截、destructive list 弹 `y/N` 确认、写入工作区外弹确认。
- `ContextInjectHook`（已 `@Deprecated`）— 事件占位，留作未来扩展。
- `SummeryHook`（已 `@Deprecated`）— 在 `Stop` 时打印对话摘要。

---

## 🚀 快速开始

### 环境要求

- **JDK 17+**（`maven.compiler.source/target` 已固定为 17）
- **Maven 3.8+**
- 一个能访问 OpenAI 兼容 API 的网络环境

### 构建

```bash
git clone <your-repo-url> codey
cd codey
mvn -q -DskipTests package
```

构建产物：`target/codey-1.0-SNAPSHOT.jar`（含依赖可通过 `mvn package` 默认打包，或自行加 `maven-shade-plugin` 输出 fat jar）。

### 运行

```bash
# 直接通过 Maven 跑
mvn -q exec:java -Dexec.mainClass=com.vanilla.Codey

# 或者构建后跑
java -cp target/codey-1.0-SNAPSHOT.jar com.vanilla.Codey
```

进入 REPL 后直接输入自然语言任务，例如：

```
你 › 帮我看看 src/main/java 下有哪些类，并把它们列成表格
你 › 修复 AppTest 里的 shouldAnswerWithTrue
你 › exit
```

输入 `q` / `quit` / `exit` 退出。

### 首次运行会发生什么

1. 打印欢迎卡片，介绍模型与退出方式。
2. 加载 `Prompt.SYSTEM`（告知模型它是一个在 `<工作目录>` 下的 coding agent，"Use bash to solve tasks. Act, don't explain."）。
3. 等待你的输入。

---

## ⚙️ 配置

> ⚠️ **安全提醒**：当前 `Codey.java` 把 `apiKey` 与 `baseUrl` 写死在源码里。
> 在推到远端前请改为从环境变量读取（例如 `OPENAI_API_KEY`），或通过 `application.yml` 注入。
> 不要把任何真实 key 提交到 git 历史。

推荐改造方式：

```java
String apiKey  = System.getenv().getOrDefault("OPENAI_API_KEY", "");
String baseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com/v1");
String model   = System.getenv().getOrDefault("OPENAI_MODEL",   "gpt-4o-mini");

OpenAiChatModel client = OpenAiChatModel.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .modelName(model)
        .build();
```

可调环境变量：

| 变量 | 作用 | 默认 |
| --- | --- | --- |
| `OPENAI_API_KEY` | 模型 API Key | **必填**（当前硬编码） |
| `OPENAI_BASE_URL` | OpenAI 兼容网关地址 | 当前 `https://api.minimaxi.com/v1` |
| `OPENAI_MODEL` | 模型名 | 当前 `MiniMax-M3` |
| `NO_COLOR` | 设为非空时关闭终端颜色 | 关闭 |
| `-Dcodey.noColor=true` | JVM 级别关闭颜色 | 关闭 |
| `COLUMNS` | 终端宽度（覆盖默认值 88） | — |

---

## 🎮 使用示例

**1. 浏览项目结构**

```
你 › 列出 src/main/java 下的所有 .java 文件
```

模型会调用 `glob`，然后整理结果输出。

**2. 重构一个文件**

```
你 › 把 ConsoleRenderer 里所有 System.out.println 改成 slf4j
```

模型会循环：`read_file` → `edit_file`（多次）→ 最终回复。

**3. 触发安全拦截**

```
你 › 执行 rm -rf /tmp/test
```

`SecurityHook` 命中 deny list，弹出红色 `✋ blocked` 卡片，工具结果以 `"Permission denied by deny list"` 回填给模型，模型会自我修正。

**4. 危险命令二次确认**

```
你 › chmod 777 ~/.ssh/id_rsa
```

弹出紫色 `⚙ hook · SecurityHook` 卡片要求 `[y/N]` 确认。

---

## 🗂️ 目录结构

```
codey/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/vanilla/
│   │   │   ├── Codey.java                 # 入口 · 工具循环
│   │   │   ├── content/Prompt.java        # System prompt
│   │   │   ├── util/ConsoleRenderer.java  # 终端 UI
│   │   │   ├── tool/                      # 工具实现
│   │   │   │   ├── Tool.java              # 工具接口
│   │   │   │   ├── ToolManager.java       # 工具注册表
│   │   │   │   ├── BashTool.java
│   │   │   │   ├── ReadFileTool.java
│   │   │   │   ├── WriteFileTool.java
│   │   │   │   ├── EditFileTool.java
│   │   │   │   ├── GlobTool.java
│   │   │   │   ├── TodoWriteTool.java     # ⚠️ 未实现
│   │   │   │   └── SpawnSubagentTool.java # 派生 subagent
│   │   │   └── hook/                      # Hook 系统
│   │   │       ├── Hook.java
│   │   │       ├── HookEvent.java
│   │   │       ├── HookContext.java
│   │   │       ├── HookDispatcher.java
│   │   │       ├── HookRegistery.java
│   │   │       ├── HookResult.java
│   │   │       ├── SecurityHook.java
│   │   │       ├── ContextInjectHook.java # @Deprecated 占位
│   │   │       └── SummeryHook.java       # @Deprecated
│   │   └── resources/
│   │       └── tools.json                 # ⚠️ 当前未被加载
│   └── test/java/com/vanilla/
│       └── AppTest.java                   # 桩测试
└── target/                                # 构建产物
```

---

## 🛣️ 路线图 / 已知问题

- [ ] **API Key 外置** — 把 `Codey.java` 里的硬编码 key 改为环境变量 / 配置文件读取。
- [ ] **`todo_write` 工具执行实现** — 当前只定义了 schema，调用会抛 `UnsupportedOperationException`。
- [ ] **`tools.json` 加载** — `src/main/resources/tools.json` 尚未被任何代码读取。
- [ ] **`picocli` 实际接入** — pom 中已声明，但入口仍是手写 `Scanner`；可改为 `picocli` 启动。
- [ ] **`PostToolUse` 事件** — 已定义但当前没有任何订阅者。
- [ ] **单元测试** — 目前只有 `AppTest` 一个桩测试；为 `ToolManager` / `SecurityHook` / `ConsoleRenderer` 补充覆盖。
- [ ] **会话存档** — 把 `history` 序列化到磁盘，支持 `/resume`。
- [ ] **多模型路由** — 通过 `OPENAI_MODEL` 切换模型 + 不同工具集。

---

## 🤝 贡献

欢迎提 Issue / PR：

1. Fork & 创建特性分支：`git checkout -b feat/awesome-tool`
2. 保持风格一致：4 空格缩进、Lombok + hutool-json、Java 17 语法。
3. 新增工具时务必在 `ToolManager` 注册，并补充参数校验与错误信息。
4. 新增 Hook 时给出明确 `id()`，避免重复注册。
5. 提交前跑：`mvn -q test`。

---

## 📄 许可证

内部项目，暂未指定开源许可证。如需对外发布请补充 `LICENSE` 文件。
