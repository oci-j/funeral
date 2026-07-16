# Funeral CLI Tab 补全方案

## 背景

项目中的"命令行"是内嵌在后端二进制中的 picocli CLI（`io.oci.cli.FuneralCommand`，命令名 `funeral`，11 个顶层子命令）。现状：

- 无任何 tab 补全机制（无 jline、无 shell 补全脚本、未使用 picocli 的补全生成能力）
- picocli 4.7.7 已在依赖中（`quarkus-picocli` + provided 的 `picocli-codegen`）
- 前端无命令行式输入界面，不涉及

## 总体设计

采用 cobra/kubectl 风格的"瘦 shell 脚本 + 隐藏补全命令"架构：

- bash 脚本只做一件事：把当前命令行单词转发给 `funeral __complete`，把输出作为候选
- 全部补全逻辑在 Java 侧（`CompletionEngine`），遍历 picocli 的 `CommandSpec` 模型树决定上下文，可被 JUnit 完整覆盖
- 新增子命令/选项时无需改 shell 脚本，picocli 模型自动反映

同时注册 picocli 内置的 `AutoComplete.GenerateCompletion` 子命令，作为纯静态补全的备选路径（不需要服务端的用户可直接使用其生成物）。

## 阶段 1 — 静态补全

- `FuneralCommand.subcommands` 追加 `picocli.AutoComplete.GenerateCompletion.class`
- 用法：`funeral generate-completion > ~/.local/share/bash-completion/completions/funeral`
- 覆盖：全部子命令名、选项名、枚举值（通过给选项加 `completionCandidates`：`--format oci|chartmuseum`、`-t/--to local|docker|oci`、`--from local|docker|oci`）
- 脚本按命令名 `funeral` 注册；实际二进制名是 `funeral-x.y.z-runner` 时需建 symlink/alias

## 阶段 2 — 动态候选（核心）

### 组件

| 组件 | 说明 |
|---|---|
| `io.oci.cli.CompleteCommand` | 隐藏子命令 `__complete`（`hidden = true`）。参数为当前命令行全部单词（最后一个为正在补全的前缀，可为空串），每行输出一个候选，任何错误静默返回 0。调用时用 `--` 分隔符引导（`funeral __complete -- <words...>`），使 `--format` 等以 `-` 开头的单词被 picocli 按位置参数处理 |
| `io.oci.cli.complete.CompletionEngine` | 纯逻辑。遍历 `CommandSpec` 树：子命令位置补子命令名；`-` 开头补选项名；`--opt=` 或选项后补选项值（读 `OptionSpec.completionCandidates()`）；位置参数按下表补动态候选 |
| `io.oci.cli.complete.CompletionEngine.CandidateSource` | 候选数据源接口（hosts/repositories/tags/users），便于测试注入 fake |
| `io.oci.cli.complete.DefaultCandidateSource` | 生产实现：hosts 来自 `~/.funeral/config.json`（defaultRegistry + aliases + auths）；repos/tags/users 经 `FuneralClient` 调 registry API（复用已存凭证） |
| `io.oci.cli.complete.StaticCandidates` | 选项静态候选类（OutputType / Format） |
| `funeral-backend/src/main/scripts/funeral-completion.bash` | 瘦 bash 脚本，处理 `COMP_WORDBREAKS` 中 `:` 的分词问题（host:port、repo:tag），`-o default` 兜底文件名补全 |

### 动态候选矩阵

| 命令路径 | 位置参数 index | 候选 |
|---|---|---|
| `login` / `logout` / `health` / `repo list` | 0 | hosts |
| `repo rm` | 0 → repos；1 → hosts | |
| `tag list` | 0 → repos；1 → hosts | |
| `tag rm` | 0 → repos；1 → tags(repo)；2 → hosts | |
| `mirror image` | 1 → hosts | |
| `mirror helm` | 2 → hosts | |
| `admin user list` / `admin user create` | 0/1 → hosts | |
| `admin user update` / `admin user delete` | 0 → users；1 → hosts | |
| `admin permission list` | 0 → users；1 → hosts | |
| `admin permission set` / `admin permission delete` | 0 → users；1 → repos；2 → hosts | |

### 超时与降级策略

- 补全触发的 HTTP 调用使用短超时（connect 500ms / request 800ms，`FuneralClient` 新增可传超时的构造器，默认行为不变）
- 任何异常（无默认 registry、未登录、网络失败、超时）→ 静默输出空，bash 侧 `-o default` 退化为文件名补全
- 永不向 stdout 打印错误（会污染补全结果）
- Quarkus banner/日志默认输出到 stdout，瘦脚本调用时注入 `QUARKUS_BANNER_ENABLED=false QUARKUS_LOG_CONSOLE_ENABLE=false` 保证 stdout 只含候选（`generate-completion` 重定向使用时同理）

## 阶段 3 — 构建与分发

- `maven-antrun-plugin` 在 package 阶段把 `funeral-completion.bash` 复制到 `target/`
- CI native smoke test 增加：`generate-completion` 输出语法检查（`bash -n`）、`__complete` 基本调用断言
- release 产物附带 `funeral-completion.bash`
- README 增加 Shell Completion 安装说明

## 安装方式（用户侧）

```bash
# 推荐：动态补全（含 repo/tag/host 实时候选）
sudo cp funeral-completion.bash /etc/bash_completion.d/funeral
# 或
mkdir -p ~/.local/share/bash-completion/completions
cp funeral-completion.bash ~/.local/share/bash-completion/completions/funeral

# 纯静态备选（picocli 生成）
funeral generate-completion > ~/.local/share/bash-completion/completions/funeral

# 若二进制名为 funeral-x.y.z-runner，建议：
sudo ln -s "$PWD/funeral-x.y.z-runner" /usr/local/bin/funeral

# zsh 用户：
#   autoload -U +X bashcompinit && bashcompinit
#   source funeral-completion.bash
```

## 测试

- `CompletionEngineTest`（纯 JUnit + picocli，注入 fake CandidateSource）：
  - 根位置补全部子命令、前缀过滤、隐藏命令不出现
  - 多级子命令（`repo` → `list/rm`）
  - 选项名补全（`mirror helm --` → `--format` 等）
  - 选项值补全（`--format` → `oci/chartmuseum`，含 `--format=o` 形式；`import -t` → `local/docker/oci`）
  - 动态候选（repos/tags/users/hosts，含 `tag rm <repo>` 第二参数带 repo 上下文）
  - 选项值消费（`--format oci` 后下一个词不当作 `--format` 的值）
- `CompleteCommandTest`（`@QuarkusMainTest`）：`__complete` 启动冒烟，exit code 0，静态候选正确，无环境依赖的断言
- CI：`bash -n` 语法检查 + native smoke

## 后续可选工作

- zsh/fish 原生脚本（当前 zsh 走 bashcompinit）
- `export <ref>` 补全本地存储镜像（LocalStorageAdapter 列举 + `--storage` 感知）
- `import` 镜像引用补全（远端 registry catalog）
- 候选结果短缓存（同一 repo 的 tags 列表缓存几秒，减少连续 TAB 的 API 调用）
