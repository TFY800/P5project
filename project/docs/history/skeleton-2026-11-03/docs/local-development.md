# 本地开发与启动

本机仓库路径：`/Users/shilo/Projects/P5project`。GitHub 远程仓库：
[TFY800/P5project](https://github.com/TFY800/P5project)。

## 当前范围

当前仅完成骨架，各服务只提供 `GET /api/health`。
数据库、注册登录、拍卖竞价、通知业务和 WebSocket 尚未实现。
健康接口仅表示该服务进程能响应请求，不检查数据库或其他服务。
四个服务可分别构建、启动和停止，无启动顺序要求。

## 环境与版本

- JDK 21，执行 `java -version` 确认。
- Spring Boot 4.1.2，四个服务保持一致。
- Maven Wrapper 3.3.4（only-script），固定下载 Maven 3.10.0。
- 首次构建需要连接 Maven Central，下载 Maven 和依赖，无须安装全局 Maven。
- macOS 使用 `./mvnw`，已设置可执行权限；Windows 使用 `mvnw.cmd`。

版本依据：[Spring Boot 官方项目页](https://spring.io/projects/spring-boot/)及
[4.1.2 系统要求](https://docs.spring.io/spring-boot/4.1/system-requirements.html)。
该稳定版本支持 Java 21。Wrapper 脚本来自 [Spring Initializr](https://start.spring.io/)。

macOS 如需选择 JDK，可在当前终端执行：

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

## 命令行启动

从仓库根目录出发，在四个独立终端中分别执行：

```sh
cd user-service
./mvnw spring-boot:run
```

```sh
cd item-service
./mvnw spring-boot:run
```

```sh
cd auction-service
./mvnw spring-boot:run
```

```sh
cd notification-service
./mvnw spring-boot:run
```

每个终端按 `Ctrl+C` 停止对应服务。若端口被占用，先停止占用进程。

也可以进入任意服务目录后构建并运行可执行 JAR，例如：

```sh
./mvnw clean package
java -jar target/user-service-0.0.1-SNAPSHOT.jar
```

其他服务将 JAR 文件名替换为对应服务名。
当前没有自动化测试；`package` 成功仅说明构建成功，仍需启动后检查接口。

## 健康检查

```sh
curl -fsS http://localhost:8081/api/health
curl -fsS http://localhost:8082/api/health
curl -fsS http://localhost:8083/api/health
curl -fsS http://localhost:8084/api/health
```

均应返回 HTTP 200 和 JSON，例如：

```json
{"service":"user-service","status":"UP"}
```

其余接口中的 `service` 分别为 `item-service`、`auction-service`、`notification-service`。

## IntelliJ IDEA

1. 用 **File → Open** 打开 `/Users/shilo/Projects/P5project` 仓库根目录，设置 **Project SDK** 和语言级别为 **21**。
2. 根目录没有聚合 `pom.xml`；在 Maven 工具窗口选择 **Link Maven Projects / +**，分别添加四个服务的 `pom.xml`。也可以将四个服务目录分别作为独立项目打开。
3. Maven 设置中选择 **Maven Wrapper**，将 Maven importer 和 runner 的 JDK 均设为 **21**，等待依赖导入。
4. 分别运行以下启动类的 `main` 方法：

| 服务 | 启动类 | 端口 |
| --- | --- | --- |
| user-service | `com.p5auction.user.UserServiceApplication` | 8081 |
| item-service | `com.p5auction.item.ItemServiceApplication` | 8082 |
| auction-service | `com.p5auction.auction.AuctionServiceApplication` | 8083 |
| notification-service | `com.p5auction.notification.NotificationServiceApplication` | 8084 |

只关联上述四个 `pom.xml`；如果看到 `.build-cache`，将其标为 **Mark Directory as → Excluded**，不要导入缓存中的 Maven 项目。

普通 Java Application 运行配置即可启动，无须依赖 IDE 的 Spring 专用功能。
通过运行窗口的停止按钮结束各服务。

## 网页客户端

直接用浏览器打开 `client/index.html`，或在 IDEA 中选择 **Open in Browser**。
页面只展示项目名称及四个服务名称，没有后端请求，也不需要 Node.js。

## 配置与版本控制

服务名称和固定端口位于各自的 `src/main/resources/application.properties`。
不要将密码、令牌或本地凭据写入这些受版本控制的文件；以后可通过环境变量或被忽略的 `application-local.properties` 配置。
本地 profile 文件需要通过 `SPRING_PROFILES_ACTIVE=local` 显式启用。
`.gitignore` 已排除 IDEA 文件、Maven 构建目录、macOS 文件及常见本地配置和密钥文件；仍需在提交前检查内容。

## 本仓库验证记录

2026-11-03，在 `/Users/shilo/Projects/P5project` 中使用 Temurin JDK 21.0.10 验证：

- 四个服务通过各自的 Maven Wrapper 执行 `clean package`，全部构建成功。
- 同时启动四个可执行 JAR，8081–8084 的 `/api/health` 均返回 HTTP 200、对应服务名及 `UP`。
- 验证后已停止全部验证进程。没有自动提交或推送。
- `origin` 为 `https://github.com/TFY800/P5project.git`，`git ls-remote origin HEAD` 访问成功。

本次构建将缓存限定在本仓库内，以用户服务为例：

```sh
MAVEN_USER_HOME="$PWD/.build-cache/maven" ./user-service/mvnw \
  -f user-service/pom.xml \
  -Dmaven.repo.local="$PWD/.build-cache/repository" clean package
```

`.build-cache/`、各服务的 `build.log`、`runtime.log` 和 `target/` 均被忽略。
日常直接执行 `./mvnw` 默认使用用户目录下的 Maven 缓存。
