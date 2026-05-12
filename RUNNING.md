## 本项目本地运行步骤与注意事项

下面按命令逐步列出在 Windows（PowerShell）环境下把 TaxiAgent 项目跑起来的具体流程与常见问题说明。

1) 检查环境

- Java 版本（需 Java 21）

```powershell
java -version
```

- Maven 版本（建议 3.9.x）

```powershell
mvn -v
```

2) 检查并启动依赖服务（MySQL、Redis、MongoDB、Elasticsearch）

- 检查端口是否打开（示例检测多个端口）

```powershell
$ports = @(3306,6379,27017,9200,8080,8081)
foreach ($port in $ports) {
  $result = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue
  if ($result.TcpTestSucceeded) { Write-Output "$port open" } else { Write-Output "$port closed" }
}
```

- Redis 简要命令（测试 & 设置临时密码）

```powershell
# 测试（无密码）
redis-cli PING
# 如果需要设置运行时密码（临时生效）
redis-cli CONFIG SET requirepass taxiagent123
# 使用密码验证
redis-cli -a taxiagent123 PING
```

注意：上面 `CONFIG SET` 只会临时生效（重启 Redis 后失效）。若要持久化，请编辑 Redis 配置文件（`redis.conf`）并设置 `requirepass taxiagent123`，然后重启 Redis 服务。

- MySQL 导入 schema 与 citycode 数据

```powershell
# 创建数据库（若未创建）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS taxiagent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
# 导入 schema
mysql -u root -p taxiagent < src/main/resources/sql/init.sql
# 导入城市编码（确保文件编码为 utf8 无 BOM）
mysql -u root -p taxiagent < src/main/resources/sql/bus_citycode.sql
```

注意：若 SQL 文件有中文注释或非 UTF-8 编码，先用编辑器转为 UTF-8 无 BOM 再导入。

- MongoDB / Elasticsearch

```powershell
# MongoDB: 确认运行
Get-NetTCPConnection -LocalPort 27017
# Elasticsearch: 确认运行（默认 9200）
Get-NetTCPConnection -LocalPort 9200
```

3) 准备与检查 `src/main/resources/application.yaml`

- 需确认并填写：MySQL 连接、Redis host/port/password、MongoDB URI、Elasticsearch URI/用户名/密码、邮件 SMTP（用于发送验证码）、Amap 与 QWeather Key、以及 `spring.ai` 的 API key 与 base-url。

示例关键配置片段：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/taxiagent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
  data:
    redis:
      host: localhost
      port: 6379
      password: taxiagent123
  data:
    mongodb:
      uri: mongodb://localhost:27017/taxiagent
  elasticsearch:
    uris: https://localhost:9200
    username: elastic
    password: <elastic-password>
# spring.ai keys
spring:
  ai:
    openai:
      api-key: <your-ecnu-key>
      base-url: https://chat.ecnu.edu.cn/open/api/v1
    dashscope:
      api-key: <your-ecnu-key>
```

4) 编译项目

```powershell
mvn compile
```

5) 启动项目（避免与系统上已有 8080 冲突，示例使用 8081）

推荐在 PowerShell 中使用环境变量来传端口，避免 mvn 参数解析问题：

```powershell
$env:SERVER_PORT=8081
mvn spring-boot:run
```

成功启动时日志会显示：

```
Tomcat initialized with port 8081 (http)
Started TaxiAgentApplication in ...
```

6) 常见启动问题与排查命令

- Redis 认证失败（Redisson 报错）
  - 错误示例：`ERR AUTH <password> called without any password configured for the default user`
  - 解决：确认 `application.yaml` 中 `spring.data.redis.password` 与 Redis 的实际密码一致；若没有在 Redis 配置文件设置密码，请设置并重启 Redis，或在运行时使用 `redis-cli CONFIG SET requirepass <pass>`（临时）。

- MySQL 表/数据缺失（如 `bus_citycode`）
  - 解决：导入 `bus_citycode.sql` 并确认表中行数，例如 694 行。

- SMTP 发送失败导致 `/auth/email-code` 返回 500
  - 检查 `spring.mail` 的配置，确认 SMTP 主机、账号与授权码（多数邮箱需开启“SMTP/授权码”）。

- 端口占用（例如已有本机 Tomcat 在 8080）
  - 查看 8080 的进程：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -eq <pid> } | Select ProcessId,Name,ExecutablePath,CommandLine
```

7) 启动后验证常用接口（示例 curl / Postman）

- 发送邮箱验证码（POST JSON）

```powershell
curl -X POST http://localhost:8081/auth/email-code -H "Content-Type: application/json" -d '{"email":"you@example.com","scene":"REGISTER"}'
```

- 密码登录（POST JSON）

```powershell
curl -X POST http://localhost:8081/auth/login/password -H "Content-Type: application/json" -d '{"login":"you@example.com","password":"yourpwd"}'
```

- 使用返回的 `token` 访问受保护接口（示例 `GET /user/current`）

```powershell
curl -H "Authorization: Bearer <token>" http://localhost:8081/user/current
```

8) 生产/持久化 Redis 密码（建议）

- 编辑 Redis 配置文件（`redis.conf`）并设置：

```
requirepass taxiagent123
```

- 然后重启 Redis 服务：

```powershell
Restart-Service redis
```

9) 日志与调试

- Spring Boot 控制台输出为主要日志，若需要将日志写文件，可在启动时添加 JVM 参数或在 `application.yaml` 中配置 `logging.file.name`。

10) 其他注意点

- `scene` 字段取值只能是 `REGISTER`、`LOGIN` 或 `RESET_PASSWORD`。
- 受保护接口必须在 Header 中包含 `Authorization: Bearer <token>` 或 `X-Auth-Token: <token>`。
- 若 Elasticsearch 未启或认证不对，RAG/检索相关功能会受影响，但核心登录/订单功能在多数场景仍可工作。

---

如果你需要，我可以把这些命令做成一个 PowerShell 脚本（`run-local.ps1`）或生成 `docker-compose.yml` 来本地启动依赖服务并运行应用。
