# 在 Mac 上运行 SecondRound

## 用 Finder 启动

先装 JDK 21 和 Python 3。在 Finder 打开仓库的 `project/`，双击 [Start.command](../Start.command)。脚本会启动本地数据库、按需构建四个服务，然后打开 [localhost:8080](http://localhost:8080)。如果需要从源码编译 PostgreSQL，第一次运行还要安装 Apple Command Line Tools。

双击 [Stop.command](../Stop.command) 可以停掉网页和四个服务。PostgreSQL 会继续运行。如果从旧目录结构更新，先用这个脚本停掉旧进程，再启动新版。

数据库、日志和凭据保存在仓库根目录。更新时保留 `.local/`、`.env` 和 `.build-cache/`。其中 `.local/postgres` 是本地数据库，`.env` 保存连接密码等配置。新建账号或拍卖后，下次启动仍能看到这些记录。

## 在终端运行

从仓库根目录开始。

```sh
cd project
java -version
python3 --version
```

已有 PostgreSQL 时，可以把 `PG_BIN` 指向 PostgreSQL 可执行文件所在的目录。需要本地安装时，运行 `./scripts/bootstrap-postgres.sh`，它会把 PostgreSQL 17.6 装在仓库根目录的 `.build-cache/postgres/`。

```sh
python3 scripts/dev.py db-init
python3 scripts/dev.py build
python3 scripts/dev.py start
python3 scripts/dev.py status
```

`db-init` 会在第一次运行时生成本地 `.env`，并为 User、Item 和 Auction 准备三个数据库。脚本会沿用已有的密码和数据。启动后，Flyway 更新表结构，JPA 检查映射。网页用 8080，四个服务用 8081–8084，本地数据库用 55432。

```sh
python3 scripts/dev.py stop
python3 scripts/dev.py db-stop
```

可以用 `python3 scripts/dev.py stop notification` 单独停止通知服务，再用 `python3 scripts/dev.py start notification` 检查网页的重连。脚本默认管理自己启动的进程。

## 在 IntelliJ IDEA 里打开

1. 用 **File → Open** 打开仓库的 `project/` 目录，选择 JDK 21。
2. 在 Maven 工具窗口分别关联四个服务的 `pom.xml`，构建工具选择 Maven Wrapper。
3. 在终端先运行 `python3 scripts/dev.py db-init`。如果服务由 IDEA 启动，先停掉同端口的脚本进程，再在运行配置里提供对应的环境变量。也可以直接在 IDEA 的终端运行上面的脚本。
4. 网页通过 [localhost:8080](http://localhost:8080) 打开。用 IDEA 启动后端时，网页仍需要由这个端口提供。

## 配置和排查

- [`.env.example`](../.env.example) 是变量格式参考。脚本从仓库根目录读 `.env`；Spring Boot 应用单独运行时，需要从环境取得这些值。真实的 `.env` 留在本机。
- `JWT_SECRET` 用于登录 token，`INTERNAL_SECRET` 用于服务间的内部 HTTP 请求。User、Item 和 Auction 各用自己的数据库连接变量。
- 服务启动失败时，先看仓库根目录的 `.local/` 日志和对应端口。构建会停止脚本管理的 Java 服务，构建后要再启动。
- 页面显示离线时，可以重启 Notification 检查 WebSocket 是否恢复，再用 HTTP 页面确认最终价格。
