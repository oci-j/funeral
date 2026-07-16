# Funeral CLI 使用参考

[English](cli-usage.md) | [简体中文](cli-usage.zh-CN.md)

Funeral CLI 与 registry 服务端共用同一个二进制（JVM uber-jar 或 GraalVM native 可执行文件），
顶层命令名为 `funeral`。每个子命令都支持 `--help` 查看自身用法：

```shell
funeral --help
funeral <子命令> --help        # 例：funeral mirror helm --help
```

本文档与代码中的 picocli 注解一一对应；如有出入，以 `--help` 输出为准。

## 通用概念

### registry 参数解析

多数子命令带一个可选的 `[host:port]` 位置参数，解析顺序：

1. 命令行显式指定的 `host:port`（或 `--server` URL）
2. `~/.funeral/config.json` 中 `aliases` 的别名（映射到 `serverUrl`，可带独立 `authDomain`）
3. `defaultRegistry`（首次 `login` 成功时自动写入）

都未配置时命令报错 `Registry not specified and no default registry configured`。

### 配置文件与凭证

- 配置文件：`~/.funeral/config.json`，可用环境变量 `FUNERAL_CONFIG_DIR`
  （或 JVM 属性 `funeral.config.dir`）改到其它目录
- `config.json` 结构：`defaultRegistry`、`aliases`（`serverUrl` / `authDomain` / `protocol`）、
  `auths`（`username` / `password` / `keyring` 标记）
- `login` 成功后凭证写入系统钥匙串（java-keyring），不可用时回退到 `auths` 明文存储

### HTTP 端口占用注意

**任何** CLI 子命令（包括 `login`、`repo list`）都会启动一个 Quarkus 实例并绑定 HTTP 端口
（默认 8911）。与本机正在运行的 server、或并行执行的另一个 CLI 冲突时，给 CLI 换一个随机端口：

```shell
java -Dquarkus.http.port=0 -jar funeral.jar repo list
# native 二进制同理：
./funeral -Dquarkus.http.port=0 repo list
```

### 退出码

- `0` 成功
- `1` 运行期失败（网络错误、服务端错误、导出部分失败等）
- `2` 命令行用法错误（picocli 解析失败）

### 日志与 stdout

Quarkus 的 banner 和日志默认输出到 stdout。需要把命令输出喂给管道/文件时
（如 `generate-completion` 或 `__complete`），加环境变量保持 stdout 纯净：

```shell
QUARKUS_BANNER_ENABLED=false QUARKUS_LOG_CONSOLE_ENABLE=false funeral repo list
```

## serve — 启动 registry 服务端

```shell
funeral serve
```

阻塞运行，监听 8911 端口（`quarkus.http.port` 可改）。直接运行 `funeral` 而不带子命令时
打印用法；无参启动 server 用 `serve`。常用环境变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `NO_MONGO` | - | 非空时不用 MongoDB（元数据落本地文件存储） |
| `NO_MINIO` | - | 非空时不用 MinIO/S3（blob 落本地文件存储） |
| `LOCAL_STORAGE_PATH` | `/tmp/funeral-storage` | 本地文件存储路径 |
| `MONGO_URL` | `mongodb://192.168.8.9:27017` | MongoDB 连接串 |
| `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` | 见 application.yml | MinIO/S3 配置 |
| `AUTH_ENABLED` | `true` | 关闭后完全匿名读写 |
| `AUTH_ALLOW_ANONYMOUS_PULL` | `true` | 允许匿名 pull |
| `AUTH_REALM` | `http://192.168.8.9:8911/v2/token` | token 端点（外部访问地址，docker login 用） |
| `AUTH_AUTO_CREATE_*` | admin/password | 首次启动自动创建的管理员 |

## login / logout — 会话管理

```shell
funeral login [host:port] [-u|--username <name>] [-p|--password <pass>]
funeral logout [host:port]
```

- 不带 `-u/-p` 时交互式输入（`-u`/`-p` 后可紧跟值，也可留空进入交互）
- 登录成功：凭证入库；若 `defaultRegistry` 为空则写入当前 registry
- `logout` 删除该 registry 的已存凭证

示例：

```shell
funeral login 192.168.8.9:8911 -u admin -p password
funeral logout 192.168.8.9:8911
```

## repo — 仓库管理

```shell
funeral repo list [host:port]     # 列出仓库（名称/tag 数/更新时间）
funeral repo rm <repo> [host:port]
```

## tag — 标签管理

```shell
funeral tag list <repo> [host:port]          # 列出仓库全部 tag
funeral tag rm <repo> <tag|digest> [host:port]   # 删除指定 manifest
```

## mirror — 镜像 / Helm chart 搬运

把外部 registry 的镜像或 Helm chart 拉取并转存到 Funeral registry（服务端执行）。

```shell
funeral mirror image <源镜像引用> [--to <目标仓库>] [--target-tag <tag>] \
    [--username <u>] [--password <p>] [host:port]

funeral mirror helm <源仓库URL或名称> <chart名> [--version <版本>] \
    [--to <目标仓库>] [--target-version <版本>] [--username <u>] [--password <p>] \
    [--format oci|chartmuseum] [host:port]
```

- `--format` 默认 `oci`
- `--password` 省略值时交互式输入
- 未指定 `--to` / `--target-tag` / `--target-version` 时沿用源名称

## import — 从远程 registry 导入本地

```shell
funeral import <镜像引用> [-t|--to local|docker|oci] \
    [--oci-dir <目录>] [--storage <路径>] [--server <URL>] [--platform <os/arch>]
```

- `-t local`（默认）：写入本地文件存储（`--storage`，默认 `/tmp/funeral-storage`）
- `-t docker`：打包后经 `docker load` 导入本机 Docker
- `-t oci`：写成 OCI image layout 目录（需 `--oci-dir`）
- `--server`：覆盖镜像引用中的 registry 地址（如走 alias 之外的直连地址）
- `--platform`：多架构镜像选择平台，如 `linux/amd64`

## export — 从本地导出到多个 registry

```shell
funeral export <源镜像引用> --to <目标引用> [--to <目标引用>...] \
    [--from local|docker|oci] [--oci-dir <目录>] [--storage <路径>] \
    [--server <URL>] [--platform <os/arch>] [--use-docker] [--continue-on-error]
```

- `--to` **必填且可重复**，一次导出到多个目标
- `--from`（默认 `local`）：源为本地文件存储 / 本机 Docker / OCI layout 目录
- 目标是 Funeral registry 且配置了 alias（或给了 `--server`）时走服务端直传，
  否则（或 `--use-docker`）走 `docker push`
- `--continue-on-error`：部分目标失败后继续其余目标；有任何失败最终退出码为 1

## admin — 用户与权限管理

```shell
funeral admin user list [host:port]
funeral admin user create <用户名> [--password <p>] [--email <e>] \
    [--role <角色>...] [--repo <允许仓库>...] [--enabled] [host:port]
funeral admin user update <用户名> [--password <p>] [--email <e>] \
    [--role <角色>...] [--repo <允许仓库>...] [--enabled|--no-enabled] [host:port]
funeral admin user delete <用户名> [host:port]

funeral admin permission list [用户名] [host:port]
funeral admin permission set <用户名> <仓库> [--pull] [--push] [host:port]
funeral admin permission delete <用户名> <仓库> [host:port]
```

- `create` 的 `--password` 省略值时交互式输入；`--enabled` 默认 true
- `update` 的 `--enabled` 可否定（`--no-enabled` 禁用用户）
- `permission set` 未给 `--pull/--push` 时两者均为 false

## health / version — 其他

```shell
funeral health [host:port]    # 调 /funeral_addition/health，OK/UNHEALTHY
funeral version               # 打印版本（同 funeral --version）
```

## generate-completion — 生成静态补全脚本

```shell
QUARKUS_BANNER_ENABLED=false QUARKUS_LOG_CONSOLE_ENABLE=false \
  funeral generate-completion > ~/.local/share/bash-completion/completions/funeral
```

picocli 生成的纯静态 bash 补全（子命令/选项/枚举值）。推荐使用仓库自带的动态补全脚本
（含 repo/tag/host 实时候选），安装方式见 [README](../README.zh-CN.md) 的 *Shell 补全* 一节。

`__complete` 是该动态补全脚本内部调用的隐藏命令，不接受直接使用。

## 相关文档

- [README](../README.zh-CN.md)：启动、Shell 补全安装
- [cli-tab-completion-plan.md](cli-tab-completion-plan.md)：补全方案设计
- [cli-import-export-plan.md](cli-import-export-plan.md)：import/export 设计
- [DOCKER_DEPLOYMENT.md](../DOCKER_DEPLOYMENT.md)：Docker 部署
