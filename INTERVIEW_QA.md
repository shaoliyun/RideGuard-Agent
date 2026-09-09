# TaxiAgent 面试问答准备

本文档围绕 TaxiAgent 项目整理 Agent 工程岗位常见追问。答案基于当前项目实现，适合用于复盘项目、准备面试和答辩。

## 一、项目整体理解

### 1. 你用 2 分钟介绍一下 TaxiAgent 的整体架构。

TaxiAgent 是一个基于 Spring Boot 3 和 Spring AI 的智能出租车预订与客服系统。整体架构可以分为 API 层、Agent 层、工具层、业务服务层和数据层。

API 层通过 Controller 暴露登录、聊天、订单、工单、用户 POI、RAG 等接口。Agent 层包含 OrderAgent、DailyAgent、SupportAgent、FallbackAgent 四类智能体，分别处理下单、日常出行咨询、售后客服和兜底问题。工具层通过 Spring AI `@Tool` 和 `ToolCallback` 暴露地点搜索、地理编码、订单参数保存、路线规划、价格计算、知识库搜索等能力。业务层负责订单、用户、工单等核心业务。数据层使用 MySQL 存结构化业务数据，Redis 存对话状态和订单草稿，MongoDB 存路线轨迹，Elasticsearch 存知识库索引用于 RAG 检索。

用户自然语言请求会先进入 `/chat/v2/c/{chatId}`，系统用模型分类，再路由到对应 Agent。下单场景中，OrderAgent 会通过工具逐步把用户语言转成订单参数，完成路线规划、估价、确认和最终落单。

### 2. 用户一句“从天府机场去成都东站，帮我叫车”，系统内部完整链路是什么？

请求先进入 `ChatController.chat()`，由 `ChatServiceImpl.chat()` 处理。系统先读取该 `chatId` 的历史消息和 Redis 中的分类状态。如果是新对话，会调用大模型分类器，把用户意图分类为 `ORDER`。

随后请求进入 `OrderAgent.invoke()`。OrderAgent 构造系统提示词，加入用户消息，然后调用大模型流式输出。模型根据提示词和可用工具调用 `searchPOIsByKeyword` 或 `geo` 获取地点和经纬度，再调用 `saveNewOrderParam` 把起点、终点、经纬度等槽位保存到 Redis 的 `chat:info:{chatId}`。之后调用 `isNewOrderReady` 检查字段是否齐全。如果缺车型，会追问用户。补全车型后调用 `getEstRouteAndPrice` 进行高德路线规划和价格计算，把路线轨迹保存到 MongoDB，把价格、距离、路线 ID 写回 Redis。最后调用 `notifyUser` 触发用户确认。用户确认后调用 `/chat/v2/r/{chatId}`，OrderAgent 继续执行 `markOrderReadyForCreate` 和 `createOrder`，最终通过 `RideOrderService` 把订单写入 MySQL。

### 3. Agent、Tool、Service、Memory 分别承担什么职责？

Agent 负责理解用户意图、组织对话流程、决定下一步调用什么工具。Tool 是暴露给大模型调用的后端能力，比如地理编码、搜索 POI、保存订单参数、创建工单、检索知识库。Service 是传统后端业务服务，真正执行订单创建、价格计算、用户查询、工单处理、路线保存等逻辑。Memory 负责保存和加载对话上下文，项目里包括 HeapMemory、RedisMemory、MysqlMemory。

简化理解：

```text
Agent = 决策者
Tool = Agent 可调用的动作
Service = 业务实现
Memory = 上下文和历史
```

### 4. 为什么拆成 DailyAgent、OrderAgent、SupportAgent、FallbackAgent？

这是按业务领域拆 Agent。OrderAgent 专注新订单下单流程，需要严格的槽位补全、路线规划、确认和落单。DailyAgent 处理天气、地点、出行估算、常用地点管理等日常咨询。SupportAgent 处理历史订单、取消、投诉、工单和知识库问答。FallbackAgent 处理和网约车业务无关的问题，并引导用户回到出行业务。

这样拆分的好处是每个 Agent 的 prompt 更短、更聚焦，工具集也更小，降低模型误调用工具和跑偏的概率，也方便后续单独评测和迭代。

### 5. 如果用户一句话里既有“叫车”又有“投诉”，系统会怎么路由？是否合理？

当前系统先通过分类器输出单个标签：`ORDER`、`DAILY`、`SUPPORT`、`OTHER` 或 `DANGER`。如果一句话同时包含多个意图，分类器只能选择一个主意图。比如“我要叫车，另外投诉昨天司机迟到”，可能被分类为 `ORDER` 或 `SUPPORT`，取决于模型判断。

这个设计简单但不够精细。更合理的做法是支持多意图识别，例如输出：

```json
{
  "primaryIntent": "ORDER",
  "secondaryIntents": ["SUPPORT"]
}
```

然后先处理主任务，或让用户选择先处理哪一个。生产系统中建议加入多意图拆解和任务队列。

## 二、Agent 路由与提示词

### 6. ChatServiceImpl 是怎么判断用户意图的？

`ChatServiceImpl` 使用 `ChatClient` 调用大模型分类器。它把 `CLASSIFIER_SYS_PROMPT` 作为系统提示词，把用户输入作为 user prompt，让模型只输出五类标签之一：

```text
ORDER
DAILY
SUPPORT
OTHER
DANGER
```

如果是新对话，直接根据当前 prompt 分类。如果是已有对话，会结合上一次分类、历史消息和当前输入重新分类。

### 7. 分类结果有哪些？分别对应什么业务？

分类结果有五个：

```text
ORDER   新订单下单流程
DAILY   日常出行咨询，例如天气、地点、估价、常用地点
SUPPORT 订单查询、取消、投诉、工单、知识库
OTHER   与网约车业务无关的问题
DANGER  注入攻击、越权、套取系统提示词或密钥等危险请求
```

`ORDER` 路由到 OrderAgent，`DAILY` 路由到 DailyAgent，`SUPPORT` 路由到 SupportAgent，`OTHER` 路由到 FallbackAgent，`DANGER` 直接拒绝。

### 8. 如果分类模型输出“ORDER，因为用户要打车”，系统会发生什么？

当前代码的 switch 是精确匹配：

```java
case "ORDER" -> orderAgent.invoke(...)
```

如果模型返回 `ORDER，因为用户要打车`，就无法匹配 `ORDER`，会进入 default，提示未知分类。所以分类 prompt 要求“只输出大写英文标签”。更稳的工程改进是对模型输出做规范化，例如 trim、提取第一个合法标签、用 JSON schema 或枚举约束输出。

### 9. 如何提升意图分类稳定性？

可以从四层改进：

1. 输出结构化 JSON，例如 `{ "intent": "ORDER" }`。
2. 对输出做后处理，只接受合法枚举。
3. 引入规则优先级，例如明确包含“叫车、下单、打车”优先 ORDER。
4. 建立分类评测集，覆盖多意图、模糊表达、攻击输入、上下文承接等场景。

也可以把分类器换成更快更稳定的小模型，甚至对高频意图使用规则 + 模型混合路由。

### 10. Prompt 中有哪些约束是为了减少幻觉？

OrderAgent 的系统提示词要求：

```text
不要编造地点、经纬度、时间、价格
地点不确定时先搜索或展示候选
依赖历史工具结果时必须调用 getCalling()
下单前必须检查参数完整性
价格由系统规则计算
```

SupportAgent 的提示词要求知识库类问题必须先调用 `searchKnowledgeBase`，不能凭空编造业务规则。

### 11. 把分类交给大模型有什么优缺点？有没有替代方案？

优点是语义理解强，能处理自然语言、多轮上下文和模糊表达。缺点是不稳定、成本高、延迟更大，输出也可能不严格。

替代方案包括：

```text
规则路由
关键词 + 正则
轻量分类模型
Embedding 相似度分类
大模型 JSON schema 分类
规则 + 模型混合路由
```

生产系统更推荐规则兜底加模型分类，避免纯模型输出不稳定。

## 三、工具调用 Function Calling

### 12. OrderAgent 注册了哪些工具？每个工具职责是什么？

OrderAgent 注册了：

```text
OrderTool              保存订单参数、检查完整性、路线估价、确认、创建订单、解释价格
POISearchTool          根据关键词搜索兴趣点
ToolRepPointerTool     根据 callId 查询历史工具结果
TrafficTool            查询航班信息
UserPOISearchTool      查询用户保存的常用地点
GeoRegeoTool           地址转坐标、坐标转地址
```

这些工具让模型只负责决策和语言交互，真实业务由后端执行。

### 13. saveNewOrderParam 如何把自然语言参数保存成结构化字段？

模型先根据用户语言和工具结果抽取槽位，例如：

```json
{
  "START_ADDRESS": "成都天府国际机场T2",
  "START_LNG": "104.445",
  "START_LAT": "30.3125",
  "END_ADDRESS": "成都东站",
  "END_LNG": "104.141",
  "END_LAT": "30.629"
}
```

然后调用 `saveNewOrderParam`。该工具接收 `Map<OrderInfoEnum, String>`，遍历每个字段并写入 Redis：

```text
chat:info:{chatId}
```

后续多轮对话都围绕这个 Redis 草稿继续补槽。

### 14. geo、regeo、searchPOIsByKeyword 的区别是什么？

`geo` 是地址转经纬度，比如“成都东站”转成 `lng,lat`。`regeo` 是经纬度转结构化地址。`searchPOIsByKeyword` 是根据关键词搜索 POI 候选，例如搜索“成都东站”可能返回多个地点候选，包括名称、地址、类型、经纬度。

下单时，地点模糊时优先搜索 POI；地址明确时可以用 geo；已有坐标但缺地址时用 regeo。

### 15. 为什么 Agent 不能直接创建订单，而要先调用工具？

因为创建订单是高风险业务动作，涉及用户、地点、价格、路线、支付状态等真实数据。Agent 直接创建容易产生幻觉和脏数据。通过工具可以把关键动作交给后端：

```text
参数保存有格式约束
完整性检查由程序执行
路线规划由高德 API 返回
价格由 Java 规则计算
创建订单由 Service 落库
```

这样模型负责“理解和调度”，系统负责“事实和执行”。

### 16. 如果模型重复调用 getEstRouteAndPrice，会有什么问题？如何优化？

重复调用会增加高德 API 调用次数、增加响应延迟，也可能重复写 MongoDB 路线数据。当前代码在重新规划时会删除旧的 `MONGO_TRACE_ID` 对应路线，但频繁调用仍然浪费资源。

优化方式：

```text
按 chatId + 起终点 + 车型 + 加急状态做缓存
工具调用加幂等判断
限制每轮最大工具调用次数
如果参数未变化，直接返回上次估价结果
```

### 17. 当前项目是 Spring AI ToolCallback，不是 MCP。二者区别是什么？

当前项目的工具是本地 Java Bean，通过 `@Tool` 和 `ToolCallbacks.from(...)` 注册给 Spring AI，模型调用的是本进程内方法。

MCP 是 Model Context Protocol，是跨进程、跨语言、可远程发现和调用工具的标准协议。MCP 模式通常是：

```text
Agent/MCP Client -> MCP Server -> Tool/Resource
```

本项目目前没有 MCP 依赖和 MCP Server 配置。

### 18. 如果要把工具改造成 MCP Server，怎么拆？

可以把工具按领域拆成多个 MCP Server：

```text
map-mcp-server       geo/regeo/POI/route
order-mcp-server     order draft/create/search/cancel
ticket-mcp-server    ticket create/query/reply
rag-mcp-server       knowledge search
user-poi-mcp-server  user POI CRUD
```

Agent 侧作为 MCP Client 动态发现工具。这样工具可以跨语言、跨服务复用，也便于权限隔离和独立部署。

## 四、订单状态与参数校验

### 19. 系统如何判断订单参数是否齐全？

通过 `OrderTool.isNewOrderReady()`。它遍历 `OrderInfoEnum`，从 Redis 的 `chat:info:{chatId}` 中读取字段，检查是否缺失或格式非法。

缺失或错误时返回缺少字段；全部合格时写入：

```text
ReadyforRoute = true
```

表示可以进行路线规划和估价。

### 20. 哪些字段是下单必需字段？

初始下单阶段主要需要：

```text
VEHICLE_TYPE
IS_RESERVATION
IS_EXPEDITED
START_ADDRESS
START_LAT
START_LNG
END_ADDRESS
END_LAT
END_LNG
```

如果是预约单，还需要：

```text
SCHEDULED_TIME
```

`EST_PRICE`、`EST_DISTANCE_KM`、`MONGO_TRACE_ID` 是路线规划和估价后生成的字段，不在初始完整性检查中强制要求。

### 21. 预约单和实时单在参数校验上有什么区别？

如果 `IS_RESERVATION = 1`，说明是预约单，`SCHEDULED_TIME` 必填，且格式必须是：

```text
yyyy-MM-dd HH:mm:ss
```

如果 `IS_RESERVATION = 0`，说明是实时单，`SCHEDULED_TIME` 可以为空。

### 22. 如果用户没说车型，系统为什么能追问车型？

因为 `isNewOrderReady()` 会发现 Redis 草稿里没有 `VEHICLE_TYPE`，返回缺少参数。OrderAgent 收到工具结果后，根据系统提示词要求追问缺失字段，于是询问用户选择：

```text
1 快车
2 优享
3 专车
```

### 23. VEHICLE_TYPE 为什么只能是 1/2/3？在哪里校验？

业务枚举约定：

```text
1 快车
2 优享
3 专车
```

在 `OrderTool.isNewOrderReady()` 中校验。如果不是 `1`、`2`、`3`，会加入错误列表并从 Redis 删除该字段，要求重新补充。

### 24. 经纬度格式校验现在够不够？

当前只校验是否匹配小数格式，例如 `\d+\.\d+`。这可以防止明显非法值，但不够完整。它没有校验经纬度范围，也不能判断坐标是否真实来自工具。

改进建议：

```text
经度范围 -180 到 180
纬度范围 -90 到 90
国内业务可限制在中国范围
关键坐标必须带来源工具 callId
创建订单前校验 mongoTraceId 确实存在
```

### 25. 如何设计“参数来源校验”，防止 Agent 编造坐标？

保存槽位时不只保存值，还保存来源：

```json
{
  "value": "104.445",
  "sourceTool": "searchPOIsByKeyword",
  "sourceCallId": "call_xxx",
  "createdAt": "..."
}
```

创建订单前要求：

```text
START_LAT/START_LNG 必须来自 geo/searchPOI/userPOI
END_LAT/END_LNG 必须来自 geo/searchPOI/userPOI
EST_PRICE 必须来自 getEstRouteAndPrice
MONGO_TRACE_ID 必须能在 MongoDB 查到
```

这样即使模型编了格式正确的坐标，也不能通过最终校验。

## 五、记忆系统

### 26. 这个项目的记忆系统用了哪些存储？

主要有：

```text
HeapMemory   JVM 内存缓存
RedisMemory  Redis 短期缓存
MysqlMemory  MySQL 持久化
```

此外 `ESMemory` 目前是空实现，注释为规划中。

### 27. HeapMemory、RedisMemory、MysqlMemory 分别解决什么问题？

HeapMemory 存在 Java 进程内，速度最快，适合当前热点对话缓存。RedisMemory 跨请求共享，速度快，适合短期上下文缓存。MysqlMemory 持久化保存聊天历史，服务重启后仍可恢复。

三者是性能和持久性的分层。

### 28. 为什么先查 Heap，再查 Redis，再查 MySQL？

这是典型缓存分层：

```text
Heap 最快，但不持久
Redis 较快，跨请求
MySQL 最稳定，持久化
```

先查快缓存可以降低数据库压力和响应延迟。缓存 miss 时再向下查，并回填上层缓存。

### 29. 服务重启后哪些记忆会丢？哪些不会？

JVM 里的 HeapMemory 会丢。RedisMemory 如果 Redis 没重启且 key 未过期，可以保留。MysqlMemory 持久化保存，不会因为应用重启丢失。

### 30. 多实例部署时 HeapMemory 有什么问题？

HeapMemory 是单个 JVM 内存，不同服务实例之间不共享。如果请求被负载均衡到不同实例，某个实例的 Heap 里可能没有该对话上下文。因此多实例部署不能依赖 Heap 作为唯一记忆，必须以 Redis/MySQL 为准。

### 31. chatId 在记忆系统里起什么作用？

`chatId` 是一次对话的唯一标识。它用于区分不同对话的历史消息、Redis 草稿、订单流程状态和工具结果。

例如：

```text
chat:info:{chatId}
chat:history:{chatId}
```

同一个下单流程必须持续使用同一个 `chatId`。

### 32. 如果两个用户使用相同 chatId，会不会串上下文？

当前消息读取接口传了 `userId` 和 `chatId`，但 Redis key `chat:info:{chatId}` 只包含 `chatId`，没有包含 `userId`。如果两个用户真的使用同一个 `chatId`，理论上 Redis 草稿状态可能冲突。

更安全的设计是 Redis key 包含 userId：

```text
chat:info:{userId}:{chatId}
```

或者强制 chatId 由后端生成 UUID，避免用户自定义碰撞。

### 33. 如何设计长期记忆和短期记忆？

短期记忆保存当前会话上下文和订单草稿，适合 Redis + MySQL。长期记忆保存用户偏好、常用地点、历史行为摘要，适合 MySQL/MongoDB/向量库。

长期记忆需要权限控制、可删除、可解释，并且不能无限塞进 prompt，应通过检索相关记忆注入。

## 六、Redis 设计

### 34. chat:info:{chatId} 里存了哪些内容？

主要存：

```text
classification
订单槽位：START_ADDRESS、START_LAT、START_LNG、END_ADDRESS、END_LAT、END_LNG
VEHICLE_TYPE、IS_RESERVATION、IS_EXPEDITED、SCHEDULED_TIME
估价结果：EST_PRICE、EST_DISTANCE_KM、EST_TIME、PRICE_RADIO 等
流程标记：ReadyforRoute、ReadyforConfirm、ReadyForCreate、break、locked
OrderId
```

### 35. 为什么订单草稿先存 Redis，而不是直接存 MySQL？

下单是多轮对话流程，中间状态可能不完整，不适合直接写入正式订单表。Redis 适合保存临时草稿：

```text
读写快
支持过期
适合频繁更新
不会污染正式订单数据
```

用户确认后再写 MySQL 正式订单。

### 36. break、ReadyforRoute、ReadyforConfirm、ReadyForCreate 分别表示什么？

`ReadyforRoute` 表示订单参数已齐，可以路线规划。`ReadyforConfirm` 表示已完成路线和估价，可以让用户确认。`break=yes` 表示系统已经发出确认事件，正在等待用户确认，需要走 `/chat/v2/r/{chatId}`。`ReadyForCreate` 表示用户已确认，可以创建订单。

### 37. 如果 Redis 数据丢失，下单流程会怎样？

当前订单草稿和流程状态依赖 Redis。如果 Redis 数据丢失，Agent 可能无法知道之前已保存的起点、终点、车型，也无法继续确认流程。系统可能重新追问信息，或者恢复失败。

改进方式是把订单草稿也周期性持久化到 MySQL/MongoDB，或在 Redis 丢失时从消息历史中重新抽取。

### 38. 如何给 Redis 对话状态设计过期策略？

可以按状态设置 TTL：

```text
普通对话上下文：数小时到数天
未完成订单草稿：30 到 60 分钟
已完成订单流程：短期保留后清理
locked 状态：保留一段时间防止继续写入旧对话
```

同时关键业务数据不能只放 Redis，正式订单必须落 MySQL。

## 七、MySQL / MongoDB / Elasticsearch

### 39. MySQL 在项目里主要存什么？

MySQL 存结构化业务数据，例如：

```text
用户
订单主体
工单
聊天消息
工具响应记录
知识库元数据
```

这些数据关系明确、需要事务和索引查询，适合 MySQL。

### 40. MongoDB 在项目里主要存什么？

MongoDB 主要存订单路线轨迹，集合为 `order_routes`，字段包括：

```text
mongoTraceId
estRoute
estPolyline
realPolyline
```

MySQL 订单表只保存 `mongo_trace_id` 作为关联。

### 41. 为什么路线轨迹适合放 MongoDB？

路线轨迹是大块半结构化数据，包含 polyline、路线详情、真实轨迹等，字段可能长且结构会变化。MongoDB 对文档型数据更自然，可以避免 MySQL 订单主表变胖，也方便后续扩展多条候选路线、路段状态、轨迹点数组等。

### 42. MySQL 支持 JSON，为什么不直接用 MySQL JSON 存路线？

MySQL 支持 JSON，也可以存路线。但路线轨迹数据体积大、结构变化多、和订单主流程访问频率不同。用 MongoDB 可以把高频订单核心字段和低频大轨迹文档解耦。

如果系统规模小，用 MySQL JSON 也能做；但从扩展性和职责分离角度，MongoDB 更适合当前路线文档。

### 43. Elasticsearch 当前在项目里做了什么？

当前真正实现的是 RAG 知识库检索，索引为：

```text
qa_knowledge_base
```

项目还准备了聊天全文检索配置 `chat-es-setting.json` 和空的 `ESMemory`，但聊天全文检索目前没有真正落地。

### 44. RAG 检索为什么要做向量检索 + BM25？

向量检索擅长语义相似，例如用户问法和知识库问题用词不同也能匹配。BM25 擅长关键词精确匹配，例如订单、退款、发票等关键词。二者结合可以提高召回率和准确性。

### 45. RRF 融合排序是什么？为什么用它？

RRF 是 Reciprocal Rank Fusion，用于融合多个检索结果排序。它不直接依赖不同检索器的原始分数，而是按排名计算：

```text
score = 1 / (k + rank)
```

项目中分别取向量检索和 BM25 检索结果，然后用 RRF 合并排序，减少单一检索方式的偏差。

### 46. QaDocument 里的 groupId 有什么作用？

同一个标准答案可能对应多个不同问法。每个问法是一条文档，但共享同一个 `groupId`。检索后按 `groupId` 去重，避免 Top 5 都是同一答案的不同问法。

### 47. chat-es-setting.json 目前有没有真正用起来？

从当前代码看，它只是配置文件，`ESMemory` 也是空实现。项目当前真正使用 Elasticsearch 的地方是 `RagService` 对 `qa_knowledge_base` 的写入、删除、更新和检索。

## 八、RAG 与知识库

### 48. SupportAgent 什么时候调用 RagTool？

当用户询问业务规则、操作流程、费用规则、退款、投诉处理等静态知识时，SupportAgent 应优先调用 `RagTool.searchKnowledgeBase`。

例如：

```text
取消订单会收费吗？
如何开发票？
为什么扣取消费？
投诉流程是什么？
```

### 49. RagTool 如何保证回答基于知识库？

RagTool 调用 `RagService.searchAnswers()`，返回知识库中的参考问题和标准答案。SupportAgent 的 prompt 要求知识类问题必须先检索知识库，并基于检索结果回答。

但严格来说，当前主要是 prompt 约束。更强的方式是让后端对知识类问题强制检索，并把回答限制在引用内容范围内。

### 50. 如果知识库没搜到结果，Agent 应该怎么回答？

应该诚实说明未找到明确规则，并引导用户创建工单或联系人工客服，而不是编造规则。

示例：

```text
我暂时没有检索到相关规则。为了避免误导您，我可以帮您创建工单交由客服核实。
```

### 51. 如何评估 RAG 答案质量？

可以从几个指标评估：

```text
检索召回率
Top1/Top5 命中率
答案是否忠实于知识库
是否引用了正确条目
用户问题是否被正确改写
无结果时是否拒绝编造
```

还可以建立一组标准问答集，定期跑自动化评测。

### 52. 为什么用户问“怎么退”需要 query rewrite？

“怎么退”脱离上下文语义不完整。RAG 检索需要清晰完整的问题，例如：

```text
网约车订单退款流程是什么？
网约车订单取消后如何退款？
```

query rewrite 能提高 BM25 和向量检索的匹配质量。

### 53. 如何避免 RAG 返回知识过期？

可以给知识库加版本、有效期、更新时间、发布状态字段。检索时只查有效版本。后台提供知识审核流程，旧规则下线，新规则上线。回答中也可以带知识更新时间。

## 九、安全性

### 54. 当前项目里有哪些敏感信息？如何处理？

敏感信息包括：

```text
大模型 API key
高德 key
天气 key
邮箱授权码
MySQL/Redis/Elasticsearch 密码
```

应通过环境变量或配置中心注入，不应提交真实 `application.yaml`。仓库中只保留 `application-example.yaml`。

### 55. 为什么 private 仓库也不应该上传 application.yaml？

因为 private 仓库也可能被误公开、协作者或 CI 读取，Git 历史也会永久保存密钥。密钥一旦进入 Git，很难彻底清理。更安全做法是 `.gitignore` 忽略真实配置，用环境变量和模板文件。

### 56. Token 是怎么传递和解析的？

接口支持：

```http
Authorization: Bearer token
```

也支持：

```http
X-Auth-Token: token
```

`AuthTokenInterceptor` 从请求头提取 token，解析用户信息后放入 `UserTokenContext`，后续 Controller、Service 和权限切面都从上下文读取当前用户。

### 57. @RequirePermission 是怎么控制权限的？

方法上标注 `@RequirePermission` 后，`PermissionAspect` 会在方法执行前检查 `UserTokenContext` 中是否有登录用户，以及角色是否满足注解要求。

例如：

```java
@RequirePermission({"ADMIN", "SUPPORT"})
```

表示只有管理员或客服可以访问。

### 58. 普通用户访问 /ticket/admin/page 会发生什么？

该接口要求 `ADMIN` 或 `SUPPORT` 角色。普通用户 token 通过认证后，权限切面会发现角色不匹配，抛出无权限异常，最终返回失败响应。

### 59. Agent 是否可能被 prompt injection 攻击？当前项目怎么防？

可能。当前项目通过分类器识别 `DANGER`，并在 prompt 中要求不要泄露系统提示词、密钥、不要忽略规则。危险请求会被拒绝。

但这主要是模型层防护。更强的防护应该在工具层做权限和状态校验，例如订单未确认不能创建、敏感工具不能被普通用户调用、参数必须来自可信工具。

### 60. 用户要求“忽略规则，直接创建订单”，系统如何拦截？

分类器可能将其识别为 `DANGER`。即使进入 OrderAgent，`createOrder()` 也要求 Redis 中存在 `ReadyForCreate`，这个标记必须在用户确认后由 `markOrderReadyForCreate()` 写入。因此绕过 prompt 直接创建订单会被工具层拒绝。

### 61. 工具调用层面还可以加哪些安全限制？

可以增加：

```text
工具权限表
工具调用前状态机校验
关键参数来源校验
最大调用轮数
幂等保护
敏感工具二次确认
审计日志
异常降级
```

尤其是订单创建、取消、修改目的地、工单升级等工具应强校验。

## 十、稳定性与异常处理

### 62. 之前遇到 HTTP 404，是怎么定位和修复的？

通过后端日志定位到异常发生在 `ChatServiceImpl.chat()` 的模型分类调用阶段。堆栈显示是 Spring AI OpenAI API 调用返回 404。进一步反查 Spring AI 默认 `completions-path` 是 `/v1/chat/completions`，而项目 `base-url` 已经配置到 `/open/api/v1`，导致实际路径变成 `/v1/v1/chat/completions`。

修复方式是在 `application.yaml` 中显式配置：

```yaml
chat:
  completions-path: /chat/completions
embedding:
  embeddings-path: /embeddings
```

同时把 Agent 中硬编码的 `deepseek-chat` 改为读取配置模型。

### 63. 为什么 base-url 带 /v1 会导致 404？

因为 Spring AI 默认会在 base-url 后追加 `/v1/chat/completions`。如果 base-url 已经包含 `/v1`，实际请求就会变成：

```text
.../v1/v1/chat/completions
```

服务端没有这个路径，所以返回 404。

### 64. Spring AI 默认 completions-path 是什么？

当前本地依赖版本中，OpenAI Chat 默认路径是：

```text
/v1/chat/completions
```

Embedding 默认路径是：

```text
/v1/embeddings
```

### 65. 异步 Flux 接口如果内部异常没有 complete，会出现什么问题？

前端或 Postman 会一直等待，直到 Servlet 异步请求超时，后端可能报 `AsyncRequestTimeoutException`。所以异步任务出错时必须：

```text
sink.tryEmitNext(error)
sink.tryEmitComplete()
```

或统一封装异常处理，保证流结束。

### 66. 如何设计 Agent 调用超时和重试？

可以对模型调用、工具调用分别设置超时。模型调用失败可以有限重试，工具调用失败则根据工具类型决定是否重试。高德 API 这类外部服务可以重试 2-3 次；订单创建这类非幂等操作必须谨慎重试。

还应记录超时日志和 traceId，方便排查。

### 67. 如何避免工具递归调用死循环？

给每次 Agent 执行增加最大工具轮数，例如：

```text
maxToolRounds = 8
```

超过后终止，并提示用户重新确认信息。还可以检测连续重复工具调用，如果同一工具同一参数重复调用多次，直接返回缓存结果或中断。

### 68. 如果高德 API 失败，系统应该怎么降级？

可以：

```text
告知用户当前路线规划不可用
保留已填写订单草稿
允许稍后重试
如果只有估价失败，不创建订单
如果地点搜索失败，要求用户换更明确地址
```

不能在没有路线和价格的情况下编造结果。

## 十一、性能与响应速度

### 69. 项目哪些地方最耗时？

主要是：

```text
大模型调用
高德 POI/geo/route API
Elasticsearch 向量检索
数据库和 Redis 访问
```

其中大模型和外部地图 API 通常是最大延迟来源。

### 70. 地点搜索、路线规划、模型调用哪个延迟更高？

一般模型调用最慢，其次是路线规划和 POI 搜索。实际延迟取决于网络、模型服务、第三方 API 状态和返回大小。

### 71. 如何减少重复工具调用？

可以对工具结果做缓存：

```text
geo: address + city
searchPOI: keyword + city
route: startLng/startLat/endLng/endLat
price: route + vehicleType + isExpedited + isReservation
```

同时在 Agent 执行上下文中保存已调用工具结果，重复调用时直接返回。

### 72. geo/searchPOI/route 缓存 key 怎么设计？

示例：

```text
geo:{city}:{address}
poi:{city}:{keyword}
route:{startLng}:{startLat}:{endLng}:{endLat}:{strategy}
price:{routeHash}:{vehicleType}:{isExpedited}:{isReservation}
```

路线缓存可以设置较短 TTL，地点缓存可以更长。

### 73. 为什么流式返回对用户体验有帮助？

模型生成和工具调用可能耗时较长。流式返回可以让用户先看到“正在规划路线”“正在计算价格”等中间状态，而不是长时间空白等待。对 Agent 产品来说，这能明显降低等待焦虑。

### 74. 同时 100 个用户下单，瓶颈可能在哪里？

瓶颈可能在：

```text
模型服务并发限制
高德 API QPS
Redis 热 key 或连接池
MySQL 连接池
线程池/ForkJoinPool
流式响应占用连接
```

需要加入限流、队列、连接池调优和异步线程池隔离。

## 十二、Agent 评测与观测

### 75. 如何设计 Agent 评测集？

按场景构建测试集：

```text
完整下单
缺车型
缺起点
预约单
地点歧义
修改目的地
取消订单
费用投诉
prompt injection
无关问题
```

每条样例定义期望分类、期望工具调用序列、期望槽位、最终状态。

### 76. 下单 Agent 的成功率如何定义？

可以定义为：

```text
成功识别 ORDER
正确抽取起终点
正确补全车型等必要槽位
成功调用路线规划
生成合理确认单
用户确认后成功创建订单
无编造关键参数
```

### 77. 会记录哪些观测指标？

包括：

```text
意图分类分布和准确率
每次对话工具调用次数
每个工具成功率和耗时
下单完成率
用户补槽次数
模型调用耗时和错误率
RAG 命中率
异常和超时次数
```

### 78. 如何发现 Agent 经常在哪个槽位失败？

记录 `isNewOrderReady()` 每次返回的缺失字段，按字段聚合统计。例如如果 `VEHICLE_TYPE` 缺失率高，说明用户常不说车型，可以优化默认追问或前端提供车型按钮。

### 79. 如何评估幻觉率？

定义关键事实字段，如地址、坐标、价格、订单号、规则答案。检查这些字段是否来自可信工具或数据库。如果回答中出现未由工具或知识库支持的关键事实，就计为幻觉。

### 80. 如何做回归测试，防止改 prompt 后能力退化？

建立固定评测集，每次改 prompt 或工具逻辑后自动跑，比较：

```text
分类是否一致
工具调用序列是否合理
槽位是否正确
最终是否能下单
是否出现危险输出
```

### 81. 如何追踪一次完整 Agent 工具调用链？

为每次请求生成 traceId，记录：

```text
chatId
userId
agentName
modelName
prompt version
toolName
tool args
tool result summary
latency
error
```

这样可以在日志或观测平台中还原完整链路。

## 十三、Agent 注册、管理、发现

### 82. 如果未来有 20 个 Agent，怎么管理？

不能继续硬编码注入和 switch。应设计 Agent Registry，统一注册每个 Agent 的元数据、能力、可用工具、版本、状态和路由条件。

### 83. 如何设计 Agent Registry？

可以定义：

```text
agentId
agentName
description
supportedIntents
tools
model
promptVersion
status
owner
version
priority
```

启动时注册本地 Agent，或从配置中心/数据库加载。

### 84. 一个 Agent 的元数据应该包含哪些字段？

至少包括：

```text
名称
职责描述
支持意图
可调用工具
模型配置
prompt 版本
权限要求
是否启用
超时配置
最大工具轮数
评测指标
```

### 85. 如何根据用户意图动态发现合适 Agent？

路由层先识别意图，然后查询 Agent Registry，找到支持该意图且状态可用的 Agent。如果多个 Agent 匹配，可以按优先级、版本、灰度策略选择。

### 86. 如果某个 Agent 下线，路由层如何降级？

Registry 标记该 Agent 不可用。路由层发现不可用后可以：

```text
转 FallbackAgent
提示用户稍后重试
转人工客服
使用旧版本 Agent
```

### 87. 如何支持 Agent 版本管理和灰度发布？

给 Agent 配置 `version` 和 `promptVersion`，按用户比例或白名单路由到新版本。记录每个版本的成功率、错误率、用户满意度，稳定后再全量发布。

## 十四、工程能力与代码质量

### 88. 这个项目现在最大的技术债是什么？

主要技术债：

```text
Agent 工具调用参数解析较手工
prompt 和业务逻辑耦合较重
订单草稿缺少参数来源校验
ESMemory 规划未落地
敏感配置曾放在 application.yaml
接口文档和测试覆盖不足
```

### 89. 哪个模块最应该重构？为什么？

优先重构 OrderAgent + OrderTool。因为它承载核心下单流程，既有模型调度、工具解析、Redis 状态机、路线规划、价格计算、订单创建。建议拆成：

```text
OrderDraftService
OrderDraftValidator
RouteEstimateService
OrderConfirmationService
OrderCreationGuard
```

让工具层更薄，业务规则集中到 Service。

### 90. OrderAgent 手动解析 tool arguments 有什么风险？

风险包括：

```text
JSON 结构变化导致解析失败
字段缺失导致空指针
模型输出格式不稳定
同一个工具支持多种参数格式，维护成本高
缺少统一参数校验
```

可以改用更强类型的工具参数 DTO 和统一校验。

### 91. 为什么 Controller 修改后要同步 API 文档？

因为 Controller 是前后端契约。如果接口路径、请求字段、响应结构变化但文档不更新，会导致联调失败、测试用例过期、前端误用接口。Agent 项目接口多且状态复杂，更需要文档同步。

### 92. 如果补测试，优先测哪些类？

优先测：

```text
OrderTool
ChatServiceImpl 路由逻辑
RideOrderServiceImpl
RagService
Auth/Permission
TicketService
```

其中 OrderTool 是核心，应重点覆盖缺字段、非法字段、估价、确认、创建订单。

### 93. 如何把 OrderTool 拆得更清晰？

可以拆为：

```text
OrderDraftTool       保存/读取草稿
OrderValidationTool  检查完整性
RouteTool            路线规划
PriceTool            估价和解释价格
OrderCreateTool      创建订单
```

底层共用 `OrderDraftService` 管理 Redis 状态。

### 94. 当前项目有哪些地方不适合生产上线？

主要包括：

```text
密钥配置需要环境变量化
Agent 工具调用缺少最大轮数
参数来源校验不足
异常时 Flux 需要统一兜底
缺少系统性测试和观测
chatId 应限制格式
ES 聊天检索未完成
多实例下 HeapMemory 需要注意一致性
```

## 十五、开放设计题

### 95. 如果支持“多平台叫车比价”，怎么改架构？

新增 Provider 层：

```text
DidiProvider
AmapProvider
TaxiProvider
```

定义统一接口：

```text
estimate
createOrder
cancelOrder
queryStatus
```

Agent 只调用统一工具，多平台结果由后端聚合和排序。

### 96. 如果支持语音输入和地图选点，后端怎么扩展？

语音输入需要 ASR 服务先转文字，再进入现有 Chat 流程。地图选点可以直接传经纬度和地址，减少 Agent 地点解析压力。后端可以新增结构化下单接口，允许前端把地图选点结果写入订单草稿。

### 97. 如果支持司机端实时位置上报，用什么技术？

可以用：

```text
WebSocket/SSE 推送位置
Redis GEO 存附近司机位置
Kafka/RabbitMQ 接收高频位置流
MongoDB/时序库保存轨迹
```

订单匹配和轨迹展示可基于 Redis GEO 和 WebSocket。

### 98. 如果支持 Agent 长期记忆，怎么设计数据模型？

长期记忆可以分为：

```text
用户偏好：车型、是否加急、常用地点
历史摘要：常去目的地、投诉偏好
显式记忆：用户主动保存的信息
```

每条记忆包含 `userId`、`memoryType`、`content`、`source`、`confidence`、`createdAt`、`updatedAt`、`deleted`。检索时只取与当前任务相关的记忆注入 prompt。

### 99. 如果上线给真实用户，上线前必须补哪些能力？

必须补：

```text
密钥安全
参数来源校验
完整权限控制
异常兜底
接口测试
Agent 评测
日志和链路追踪
限流和熔断
隐私合规
监控告警
灰度发布
```

### 100. 如果用 LangChain/LlamaIndex/CrewAI 重做，如何映射当前架构？

OrderAgent、DailyAgent、SupportAgent 可以映射为不同 Agent。`agentbase/tool` 下的 Java 工具可以映射为 LangChain Tools 或 CrewAI tools。RagService 可以映射为 LlamaIndex Retriever。Memory 层可以映射为短期 memory + 长期 vector memory。路由器可以用 Router Chain 或自定义 intent classifier。

不过本项目是 Java/Spring 生态，用 Spring AI 更贴合现有后端服务和企业工程化。

