# Mac 本地开发与 IntelliJ IDEA

## 环境与目录

应用在仓库的 `project/` 目录。脚本在 `project/` 下运行，运行数据、凭据和缓存仍在仓库根目录的 `.local/`、`.env` 和 `.build-cache/`。 [项目仓库](https://github.com/TFY800/P5project)

- JDK 21；本机实际验证为 Temurin 21.0.10 arm64。
- Python 3（脚本仅使用标准库）。
- Spring Boot 4.1.2。已验证 Maven Central 的发布包，兼容 Java 21；保留原版。
- Maven Wrapper 3.3.4，固定 Maven 3.10.0；首次下载需要网络。
- PostgreSQL 17；本次通过官方 17.6 源码在 `.build-cache/postgres/` 编译，无须管理员权限。源码 SHA-256 已验证。可改用 Homebrew / Postgres.app 的兼容版本。

官方资料：[Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[Boot 模块变化及 Flyway starter](https://spring.io/blog/2025/10/28/modularizing-spring-boot/)、[PostgreSQL 源码](https://ftp.postgresql.org/pub/source/v17.6/)。Boot 4 使用 `spring-boot-starter-webmvc`、`spring-boot-starter-flyway` 和 `flyway-database-postgresql`；依赖版本由 Boot BOM 管理。

## 初始化数据库（先审查脚本）

```sh
cd project  # 从仓库根目录进入
java -version
python3 --version
# 有 PostgreSQL 时可设置 PG_BIN=/your/postgresql/bin，无须编译。
# 没有时：需要 cc、make（Apple Command Line Tools）和网络。
./scripts/bootstrap-postgres.sh
python3 scripts/dev.py db-init
```

`bootstrap-postgres.sh` 只下载、校验 SHA-256 摘要并编译到仓库缓存目录。发现已有本地二进制就复用。不会安装 Homebrew 或执行 sudo。

`dev.py db-init` 的操作可在 `scripts/dev.py` 中完整审查：

1. 仅在 `.env` 不存在时，用系统安全随机数生成不同的 JWT、内部通信、数据库密码，权限为 0600。不会打印值或覆盖已有文件。
2. 仅在 `.local/postgres` 不存在时 `initdb`，使用 SCRAM 密码认证，只监听 127.0.0.1:55432，socket 也在 `.local/`。
3. 创建不存在的角色/数据库：`p5_user`、`p5_item`、`p5_auction`。角色没有超级用户、建库或建角色权限。数据库归对应角色所有，新建数据库撤销 PUBLIC 的 CONNECT，仅授权对应服务角色（超级用户仍可维护）。
4. 已有角色密码不修改；已有同名数据库若 owner 不符合预期则停止，不重置。端口占用时停止并提示，绝不接管外部数据库。
5. 各服务启动后由 Flyway 按版本建表，JPA 只 `validate`，不执行 `create/update`。

不要删除 `.local/postgres` 或 `.env` 作为“修复”步骤；它们分别保存数据和访问凭证。先备份并检查实际错误。

## 一键启动（推荐）

在 Finder 中打开仓库的 `project/` 目录，双击 `Start.command`。如果此前运行的是旧目录结构，先双击 `project/Stop.command` 停止旧的应用进程，然后再启动。它会自动选择已安装的 JDK 21，检查依赖，启动/初始化本仓库数据库，按源码与 JAR 摘要判断是否需要构建，然后启动四个服务和固定端口网页，健康检查通过后自动打开 `http://localhost:8080`。

首次缺少 PostgreSQL 时，会尝试调用现有 `bootstrap-postgres.sh` 在仓库内编译，需要网络和 Apple 编译工具；缺少 Python 3/JDK 21/工具时会明确提示，不会进行系统安装。第一次 Maven 下载可能较慢，请保留终端窗口等待完成。

成功后可关闭终端窗口，服务继续后台运行。重复双击会复用未变化的构建和已运行进程。源码变化时会停止脚本管理的 JVM，重新构建并启动，不会停止 IDEA 或其他程序的进程。启动失败会保留窗口提示，不打印秘密，也不删除数据或覆盖 `.env`。

双击 `Stop.command` 停止网页和四个服务；PostgreSQL 保留运行，避免误停其他数据库客户端。需要停止本仓库数据库时另行执行 `python3 scripts/dev.py db-stop`。

也可以在终端执行 `./Start.command`；自动化检查可用 `./Start.command --no-browser`，仅省略打开浏览器。

## 构建、启动、停止

```sh
python3 scripts/dev.py build
python3 scripts/dev.py start
python3 scripts/dev.py status
```

浏览器访问 [http://localhost:8080](http://localhost:8080)。静态客户端由固定端口 Python HTTP server 提供，Java 服务使用 8081–8084。脚本等待 health 返回后才报告 ready；health 仅证明进程响应，数据库/跨服务故障仍需业务检查。

```sh
python3 scripts/dev.py stop notification
python3 scripts/dev.py start notification
python3 scripts/dev.py stop       # 四个服务和网页，不停止数据库
python3 scripts/dev.py db-stop    # 仅本仓库 cluster
python3 scripts/dev.py db-start
```

PID 记录包含 PID、进程启动时间与完整命令；停止前再次核验，只发送 SIGTERM，不使用 `pkill java` 或按端口误杀进程。构建前先停止脚本管理的 JVM，避免替换运行中的 Spring Boot JAR。构建保存源码/JAR 摘要；启动拒绝过期或未经该脚本验证的 JAR。

运行日志 `.local/{user,item,auction,notification,client}.log`、PostgreSQL 日志及 `.pid.json` 均被忽略。脚本不会打印配置秘密或 token；不要开启 HTTP Authorization 调试日志。

## 环境变量与独立运行

`.env.example` 仅为无真实秘密的格式参考。推荐直接 `python3 scripts/dev.py env` 安全生成 `.env`。脚本读取 `KEY=value`，不执行 shell 表达式；进程环境变量优先于 `.env`。

| 变量 | 用途 |
| --- | --- |
| `JWT_SECRET` | 所有服务共享的至少 32 字节随机 HS256 密钥 |
| `INTERNAL_SECRET` | 至少 32 字符，与 JWT 密钥不同，仅内部请求使用 |
| `USER_DB_*` / `ITEM_DB_*` / `AUCTION_DB_*` | 各自 `URL`、`USER`、`PASSWORD` |
| `CLIENT_ORIGIN` | 默认 `http://localhost:8080`，CORS 与 WebSocket 允许的精确来源 |
| `ITEM_API_URL` / `NOTIFICATION_API_URL` | 拍卖服务的跨服务 HTTP 地址 |
| `PG_BIN` | 可选，PostgreSQL bin 目录 |
| `PG_ADMIN_PASSWORD` | 脚本专用本地 cluster 初始化账号密码，Java 服务不使用 |

Java 服务只需要其实际配置变量；Notification 不需要数据库变量。`.env` 文件不会由 Spring Boot 自动加载，脚本负责传入。

也可以在任意服务目录独立构建：

```sh
./mvnw verify
# 通过 IDEA 或当前终端环境提供必要变量后：
./mvnw spring-boot:run
# 或 java -jar target/user-service-0.0.1-SNAPSHOT.jar
```

直接 Wrapper 默认使用用户 Maven 缓存；`dev.py build` 把缓存放在仓库 `.build-cache/`。要完全限制缓存目录，使用 `MAVEN_USER_HOME` 和 `-Dmaven.repo.local`，参见脚本。

使用现有外部 PostgreSQL 时，手动创建不同角色和数据库并设置三个 JDBC URL；不要运行本地 cluster 初始化器来管理外部实例。角色隔离 SQL 可参照 `db_init()`，秘密通过管理员安全方式设置。Flyway 会在各自数据库中执行迁移。

## IDEA

1. **File → Open** 仓库的 `project/` 目录，SDK 与语言级别均选 **21**。
2. 在 Maven 工具窗口 **Link Maven Projects / +** 关联四个服务的 `pom.xml`；`project/` 没有聚合 POM。Maven 选择 **Wrapper**，importer / runner JDK 选 21。
3. 将 `.build-cache`、`.local` 标记为 **Excluded**，不要导入缓存中的 POM；不要提交 `.idea` 或 `*.iml`。
4. 先在终端运行 `db-init`。为各个 Java Application 配置环境变量（从本地 `.env` 手动复制，不写进受版本控制的共享运行配置）；或直接在 IDEA Terminal 使用 `dev.py`。
5. 分别运行 `com.p5auction.user.UserServiceApplication`、`com.p5auction.item.ItemServiceApplication`、`com.p5auction.auction.AuctionServiceApplication`、`com.p5auction.notification.NotificationServiceApplication`。普通 `main` 即可，无须 Spring 专用 IDE 功能。
6. 如果改由 IDEA 管理服务，先 `dev.py stop`，避免端口冲突。脚本不负责停止 IDEA 启动的进程。
7. 网页仍通过 8080 HTTP server 运行；不要用 IDEA 自带随机端口预览或 file URL。

## 常见问题

- 端口被占用：查看占用进程后决定由其原启动方式停止，脚本不会自动杀进程。
- 服务退出：检查对应 `.local/*.log`；不要把含敏感配置的完整日志公开粘贴。
- 登录失效：一小时到期，重新登录。修改 JWT 密钥也会使旧 token 失效。
- 发布显示 PREPARING：商品服务暂不可达，后台每三秒重试；恢复商品服务后查看详情或 My auctions。请求保持原幂等键。
- 页面显示 Offline：价格仍可能已提交；不要重复更换幂等键盲目出价。HTTP 快照、重连以及十秒兜底轮询会恢复状态。
- 修改前端地址：同时修改 `client/config.js`、`index.html` CSP 和 `CLIENT_ORIGIN`。后端地址集中在 config.js；不要在页面代码散落 URL。
