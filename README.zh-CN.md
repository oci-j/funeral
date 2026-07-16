# FUNERAL

[English](README.md) | [简体中文](README.zh-CN.md)

[![Backend Coverage](https://img.shields.io/endpoint?url=https://oci-j.github.io/funeral/coverage-backend.json)](https://oci-j.github.io/funeral/backend/coverage.html)
[![Frontend Coverage](https://img.shields.io/endpoint?url=https://oci-j.github.io/funeral/coverage-frontend.json)](https://oci-j.github.io/funeral/frontend/coverage.html)

FUNERAL 是一个遵循 [oci-distribution-spec](https://github.com/opencontainers/distribution-spec.git) 的 OCI 镜像仓库。

使用 Java 实现。

# Demo

演示站点：https://funeral.xenoamess.com ，运行在 AUTH_ENABLED=false、NO_MONGO=true、NO_MINIO=true 模式。

相关 Docker 镜像：https://hub.docker.com/r/xenoamess/funeral

# 使用

无认证模式启动：

```shell
export AUTH_ENABLED=false
./funeral-0.2.0-runner
```

或，默认认证模式启动：

```shell
export AUTH_REALM=http://your-local-ip:8911/v2/token
./funeral-0.2.0-runner
```

也可以复制并修改 [application.yml](funeral-backend/src/main/resources/application.yml) 来配置 minio、mongo 连接及其他配置。

像这样：

```shell
./funeral-0.2.0-runner -Dquarkus.config.locations=file:/home/xenoamess/funeral/application.yml
```

# Shell 补全（bash）

CLI 内置 tab 补全：子命令、选项、枚举值，以及由隐藏命令 `__complete` 驱动的动态候选
（仓库、标签、用户、registry 主机）。

```shell
# 1. 让二进制以 `funeral` 命名可用（补全脚本按此命令名注册）
sudo ln -s "$PWD/funeral-0.2.0-runner" /usr/local/bin/funeral

# 2. 安装补全脚本
sudo cp funeral-backend/src/main/scripts/funeral-completion.bash /etc/bash_completion.d/funeral
# 或者，按用户安装：
# mkdir -p ~/.local/share/bash-completion/completions
# cp funeral-backend/src/main/scripts/funeral-completion.bash ~/.local/share/bash-completion/completions/funeral
```

也有 picocli 生成的纯静态补全可选：

```shell
QUARKUS_BANNER_ENABLED=false QUARKUS_LOG_CONSOLE_ENABLE=false \
  funeral generate-completion > ~/.local/share/bash-completion/completions/funeral
```

zsh 用户可先执行 `autoload -U +X bashcompinit && bashcompinit`，再
`source funeral-completion.bash` 加载该 bash 脚本。

# CLI 命令

速查表（完整文档含全部选项与示例见
[docs/cli-usage.zh-CN.md](docs/cli-usage.zh-CN.md)，或执行 `funeral <命令> --help`）：

```shell
# 会话
funeral login 192.168.8.9:8911 -u admin -p password
funeral logout 192.168.8.9:8911

# 仓库与标签
funeral repo list
funeral repo rm myrepo
funeral tag list myrepo
funeral tag rm myrepo latest

# 搬运
funeral mirror image docker.io/library/alpine:3.20 --to library/alpine
funeral mirror helm https://charts.bitnami.com/bitnami nginx --version 15.4.0

# 导入与导出
funeral import docker.io/library/alpine:3.20 -t local
funeral export library/alpine:3.20 --to registry2.example.com/library/alpine:3.20

# 管理
funeral admin user list
funeral admin user create alice --email alice@example.com --role USER
funeral admin permission set alice myrepo --pull --push

# 其他
funeral health
funeral version
funeral serve
```

注意：每个 CLI 子命令都会启动一个 Quarkus 实例并绑定 HTTP 端口；若本机已有 server
在运行，请用 `-Dquarkus.http.port=0` 给 CLI 分配随机端口。

# 开发

1. 初始化

```shell
cd funeral-frontend
pnpm install
pnpm build
cd ../funeral-backend
mvn quarkus:dev
```

2. 运行测试

```shell
cd funeral-frontend
pnpm run test:coverage

cd ../funeral-backend
mvn test
```

3. 使用本地磁盘存储运行（无需 MongoDB/MinIO）

```shell
export NO_MONGO=true
export NO_MINIO=true
export LOCAL_STORAGE_PATH=/tmp/funeral-storage
./funeral-0.2.0-runner
```

# 当前状态

项目拥有完整的测试套件与 CI 流水线：

- 后端：`mvn test` 通过；OCI Distribution Spec 一致性测试通过（79 个可运行 spec 中 74 通过，5 跳过）
- 前端：`pnpm run test:coverage` 通过，高覆盖率
- CI：GitHub Actions 构建 JVM 与 native 二进制、运行单元测试、执行 native 冒烟测试，并对 Funeral registry 做 Docker push/pull 验证

```
xenoamess@xenoamessum890pro:~/workspace/distribution-spec/conformance$ ./conformance.test
Running Suite: conformance tests - /home/xenoamess/workspace/distribution-spec/conformance
==========================================================================================
Random Seed: 1755533753

Will run 79 of 79 specs
•••••S••••••••••••••••••••••••••••••••••••S•SS••••••••••S•••••••••••••••••••••••••••••••
HTML report was created: /home/xenoamess/workspace/distribution-spec/conformance/report.html

Ran 74 of 79 Specs in 1.636 seconds
SUCCESS! -- 74 Passed | 0 Failed | 0 Pending | 5 Skipped
PASS
```

注：按 `artifact-type` 过滤尚未实现（属于被跳过的 spec 之一）。

# 短期目标

本仓库是一个更大项目的组成部分：以更少的带宽改进 OCI。
[为 OCI 镜像格式增加 import 机制，降低镜像升级的带宽成本](https://github.com/users/XenoAmess/projects/1)

主要努力方向：

1. 提升测试覆盖率与 CI 可靠性。
2. 完善 OCI Pull/Push API 一致性。
3. 让 registry 可用于 `image import block` 研究。

# 终极目标

本仓库的终极目标是成为一个轻量级镜像仓库。

重点关注易用性，做到开箱即用、易于搭建。

1. 中间件可选（已完成：`NO_MONGO=true` 与 `NO_MINIO=true` 的本地磁盘存储）
2. 更轻量（已完成：GraalVM native 二进制发布）
3. 稳定性打磨
4. 存储压缩（长期研究目标）
