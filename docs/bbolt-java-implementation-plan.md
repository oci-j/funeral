# bbolt-java 实现与 funeral 集成计划

## 目标
为 funeral 提供 Docker Engine 29+ containerd image store 的只读本地回退能力，
通过实现一个独立的 Java 版 bbolt 只读读取器来解析 containerd 的 BoltDB 元数据。

---

## 1. 新建独立 Maven 项目 `bbolt-java`

### 1.1 基本信息
- 仓库：`oci-j/bbolt-java`（GitHub）
- 本地位置：`../bbolt-java`（funeral 的同级目录）
- groupId：`com.xenoamess.oci-j`
- artifactId：`bbolt-java`
- version：`0.1.0-SNAPSHOT`
- JDK：17
- 依赖：仅 JUnit 5（测试），纯 Java NIO，不依赖 JNA / 原生库。

### 1.2 目录结构
```
../bbolt-java
├── pom.xml
├── src/main/java/com/xenoamess/bbolt/
│   ├── BboltDB.java
│   ├── ReadOnlyTransaction.java
│   ├── Bucket.java
│   ├── Cursor.java
│   ├── Page.java
│   ├── LeafPageElement.java
│   ├── BranchPageElement.java
│   ├── InBucket.java
│   ├── Meta.java
│   ├── BboltException.java
│   └── io/FilePageReader.java
└── src/test/java/com/xenoamess/bbolt/
    ├── BboltDBTest.java
    └── CursorTest.java
```

### 1.3 文件格式解析
- 小端字节序读取所有多字节字段。
- Page header：16 字节
  - `id` uint64
  - `flags` uint16
  - `count` uint16
  - `overflow` uint32
- `flags` 取值：
  - `0x01` branch
  - `0x02` leaf
  - `0x04` meta
  - `0x10` freelist
- Meta page：
  - magic `0xED0CDAED`
  - version `2`
  - pageSize uint32
  - flags uint32
  - root bucket（root uint64 + sequence uint64）
  - freelist uint64
  - pgid uint64
  - txid uint64
  - checksum uint64
  - checksum 用 FNV-1a 64 计算，覆盖 meta 结构 checksum 字段之前的内容。
- Leaf 元素：16 字节
  - flags uint32, pos uint32, ksize uint32, vsize uint32
- Branch 元素：16 字节
  - pos uint32, ksize uint32, pgid uint64
- key/value 起址 = 元素在页中的绝对起址 + pos

### 1.4 核心 API
#### 高层（只读事务 + bucket 链式访问）
```java
try (BboltDB db = BboltDB.open(path);
     ReadOnlyTransaction tx = db.beginReadOnly()) {
    byte[] digest = tx.getRootBucket()
        .getBucket("v1")
        .getBucket("moby")
        .getBucket("images")
        .getBucket("alpine:3.20")
        .getBucket("target")
        .get("digest");
}
```

#### 低层（Page + Cursor）
- `BboltDB.readPage(long id)`：返回 `Page`
- `Page` 暴露：`id()`、`flags()`、`count()`、`overflow()`、`isLeafPage()`、`isBranchPage()`、`isMetaPage()`、`meta()`、`leafElement(int)`、`branchElement(int)`
- `Cursor` 支持：`first()`、`last()`、`next()`、`prev()`、`seek(byte[])`

### 1.5 不实现的内容
- 写入、删除、事务提交/回滚
- freelist 解析与页回收
- node cache（只读场景不需要）
- 超大 overflow 页的完整处理（先覆盖普通 key/value）

---

## 2. funeral 后端改造

### 2.1 依赖变更
`funeral-backend/pom.xml`：
- **移除**：
  - `com.protonail.bolt-jna:bolt-jna-core`
  - `com.protonail.bolt-jna:bolt-jna-native`
  - `org.xerial:sqlite-jdbc`
  - `com.google.protobuf:protobuf-java`
- **新增**：
  - `com.xenoamess.oci-j:bbolt-java:0.1.0-SNAPSHOT`

### 2.2 重写 `MetadataDbImageIdFinder`
路径：`funeral-backend/src/main/java/io/oci/docker/containerd/MetadataDbImageIdFinder.java`

读取真实 containerd metadata schema：
```
v1
└── moby
    └── images
        └── <image name>
            ├── target
            │   ├── digest
            │   ├── mediatype
            │   └── size
            └── ...
```

新增方法签名：
```java
public Optional<String> findImageId(
    Path dockerRoot,
    Path containerdRoot,
    String repositoryName,
    String reference
)
```

查找顺序：
1. `containerdRoot/io.containerd.metadata.v1.bolt/meta.db`
2. `dockerRoot/containerd/daemon/io.containerd.metadata.v1.bolt/meta.db`

保留旧签名（`Path dockerRoot, String repositoryName, String reference`）作为兼容重载，
内部仅走 bbolt。

### 2.3 调整 `ContainerdFileResolver`
路径：`funeral-backend/src/main/java/io/oci/docker/ContainerdFileResolver.java`
- `containerdRoot` 默认值：`/var/lib/docker/containerd/daemon`
- `resolveManifest` 调用新的 `findImageId(dockerRoot, containerdRoot, ...)`
- blob 读取路径仍为 `containerdRoot/io.containerd.content.v1.content/blobs`

### 2.4 调整 `DockerStorageModeDetector`
路径：`funeral-backend/src/main/java/io/oci/docker/DockerStorageModeDetector.java`
`isContainerdImageStore()` 判断：
- 优先检查 `dockerRoot/containerd/daemon/io.containerd.content.v1.content/blobs`
- 其次检查 `containerdRoot/io.containerd.content.v1.content/blobs`

### 2.5 测试更新
- 删除 `MetadataDbImageIdFinderTest` 中的 SQLite 测试。
- 新增 bbolt 测试 fixture：`src/test/resources/io/oci/docker/containerd/meta.db`，
  包含 bucket 路径 `v1/moby/images/alpine:3.20/target/digest = sha256:abc123`，
  并包含 `docker.io/library/alpine:3.20` 的 bucket。
- fixture 用 Go 的 `bbolt` 命令行一次性生成并提交。
- `ContainerdFileResolverTest` 改为使用 bbolt fixture。
- `ContainerdFileResolverIntegrationTest` 中 `dockerRoot` 与 `containerdRoot` 的赋值基于新的 metadata 路径。

---

## 3. CI 更新

文件：`.github/workflows/build.yml`

### 3.1 `Containerd Image Store Integration Test` job
拉取镜像后，检查新路径：
```bash
METADATA_DB="/var/lib/docker/containerd/daemon/io.containerd.metadata.v1.bolt/meta.db"
CONTENT_DIR="/var/lib/docker/containerd/daemon/io.containerd.content.v1.content"
```

复制到 runner：
```bash
mkdir -p ./funeral-backend/target/real-docker-root/containerd/daemon/io.containerd.metadata.v1.bolt
docker cp dind:"$METADATA_DB" \
  ./funeral-backend/target/real-docker-root/containerd/daemon/io.containerd.metadata.v1.bolt/meta.db

mkdir -p ./funeral-backend/target/real-containerd-blobs
docker cp dind:"$CONTENT_DIR" ./funeral-backend/target/real-containerd-blobs/
```

运行测试：
```bash
mvn -B test -Dtest=ContainerdFileResolverIntegrationTest \
  -Doci.docker-local.real-metadata-db=target/real-docker-root/containerd/daemon/io.containerd.metadata.v1.bolt/meta.db \
  -Doci.docker-local.real-containerd-root=target/real-containerd-blobs
```

### 3.2 build-jvm / build-native job
由于 `bbolt-java` 在 `../bbolt-java`，在 funeral 构建前执行：
```bash
if [ ! -d ../bbolt-java ]; then
    git clone https://github.com/oci-j/bbolt-java.git ../bbolt-java
fi
mvn -B -f ../bbolt-java/pom.xml install -DskipTests
```

---

## 4. 验证步骤

1. `cd ../bbolt-java && mvn -B test`
2. `cd funeral-backend && mvn -B test -Dtest=MetadataDbImageIdFinderTest,ContainerdFileResolverTest`
3. `cd funeral-backend && mvn -B test`（完整 JVM 测试）
4. `cd funeral-backend && mvn -B clean package -Pnative -DskipTests`（native 构建）

---

## 5. 风险与注意事项

- bbolt 文件读取必须严格按小端和无符号字节比较，否则 cursor 会定位失败。
- `bbolt-java` 初始版本只读，不保证覆盖所有 bbolt 特性（overflow、freelist 等），但足够解析 containerd image metadata。
- Quarkus native 编译中 `bbolt-java` 使用纯 Java NIO，理论上不需要额外 reflection 配置；
  若使用 `MappedByteBuffer` 可能需 runtime 初始化，因此采用 `RandomAccessFile` / `FileChannel` 读取。
- GitHub 仓库 `oci-j/bbolt-java` 尚未创建，需要在本地初始化后推送。

---

## 执行记录（2026-07-17 补记）

本计划已执行完成：

- `oci-j/bbolt-java` 仓库已创建并推送，`0.1.0-SNAPSHOT` 纯 Java NIO 只读实现。
- funeral 端 `MetadataDbImageIdFinder` / `ContainerdFileResolver` / `DockerStorageModeDetector`
  已按 bbolt 方案落地（详见 `docs/metadata-db-integration-plan.md`），
  bolt-jna / sqlite-jdbc / protobuf-java 依赖均已移除。
- CI（`build.yml`）在 funeral 构建前 clone 并 `mvn install` bbolt-java；
  `containerd-image-store-integration` job 与 native 构建均通过。
