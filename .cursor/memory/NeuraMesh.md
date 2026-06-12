# NeuraMesh 项目记忆（Memory）

> 本文件为 Cursor Agent 跨对话共享的项目记忆。新对话开始时优先阅读本文件 + user-memory 知识图谱（搜索 "NeuraMesh"）。

## 一、项目身份
- **名称**：NeuraMesh
- **定位**：去中心化边缘智算网络（DePIN）
- **团队**：4 人（CEO / CTO / 区块链工程师 / 全栈工程师）—— Agent 全权负责实现
- **工作目录**：`c:\dev\NeuraMesh`（Windows / PowerShell）
- **远程仓库**：https://github.com/TTgy22/NeuraMesh
- **当前进度**：P5 两段已提交（dbfa7c8/56e7d16）。P6 Step 0 真实化改造 + Step 0.5 "无合格节点"修复 + 设备指纹终身持久化已完成并提交推送。后端 123 测试全绿 + node 前端 Vitest 6 全绿 + bootRun 全流程 curl 验证通过。

## 二、技术栈（不可更改）
| 类别 | 选型 | 版本 |
|---|---|---|
| 语言 | Java | 17（运行时 JDK 21 兼容，Gradle 守护进程跑 JDK 17） |
| 构建 | Gradle | 8.10.2（Wrapper，distribution 指向腾讯云镜像） |
| 区块链 | 自研 | BFT-PoS（P2 实现），禁止调用任何公链 API |
| 存储 | RocksDB | rocksdbjni 8.11.4（锁定 8.x） |
| 网络 | Netty | 4.1.121.Final（netty-handler/codec/transport，异步 NIO） |
| 序列化 | Kryo + Protobuf | kryo 5.6.2（网络）、protobuf-java 3.25.5（持久化，尚未用） |
| 密码学 | BouncyCastle | bcprov-jdk18on 1.80（ECDSA secp256k1、SHA-256） |
| 测试 | JUnit + AssertJ | junit-bom 5.11.4、assertj-core 3.27.3，JaCoCo 0.8.12 聚合 |
| 日志 | SLF4J + Logback | slf4j-api 2.0.17、logback-classic 1.5.18 |
| 格式化 | Spotless | 6.25.0 |

## 三、模块结构（5 模块）
```
neuramesh/
├── neuramesh-core/        [P0] com.neuramesh.core
│     NeuraException, TxType, ByteUtils, CryptoUtils, MerkleTree, Transaction, Block
├── neuramesh-storage/     [P0] com.neuramesh.storage  (api core)
│     StorageException, ColumnFamilies, RocksDBStore
├── neuramesh-consensus/   [P1+P2] com.neuramesh.consensus  (api core)
│     [P1] TxPool, TxPoolListener, TxPoolFullException
│     [P2] bft/{BFTConsensus, ValidatorSet, Validator, ProposerSelector, Vote, VoteType,
│          VoteCollector, PrePrepare, BlockFinality, ConsensusState, ConsensusBroadcaster}
│          block/{BlockProducer, BlockStore, InMemoryBlockStore}  exception/ConsensusException
├── neuramesh-network/     [P1] com.neuramesh.network  (api core+storage+consensus)
│     NeuraMessage, MessageRegistry, MessageHandler, ChannelContext, KryoSerialization,
│     NodeId, NetworkException, Peer, PeerManager, P2PNetwork, GossipProtocol,
│     BlockSync, Heartbeat, BlockRepository, MemoryBlockRepository,
│     messages/{Hello,Ping,Pong,TransactionGossip,GetBlocksRequest,BlocksResponse}Message,
│     codec/{TransactionCodec, BlockCodec}
├── neuramesh-vm/          [P3] com.neuramesh.vm  (api core+storage+consensus)
│     StateMachine, TransactionProcessor, WeightUpdateValidator, Attestation
│     state/{GlobalState, AccountState, NodeState}  exception/VMException
│     payload/{NodeRegister, WeightUpdate, TaskSettle, TokenTransfer}Payload
│     processors/{NodeRegister, WeightUpdate, TaskSettle, TokenTransfer}Processor
├── neuramesh-benchmark/   [P3] com.neuramesh.benchmark  (api core)
│     DeviceBenchmark, Fingerprint, BenchmarkResult
├── neuramesh-api/         [P4] Spring Boot 3.2.10 网关  (api core+storage+consensus+vm+benchmark)
│     ApiApplication, common/ApiResponse, config/CorsConfig
│     controller/{Node,Vendor,Chain}Controller  service/{Chain,Node,Vendor}Service  dto/*
├── neuramesh-node/        [P4] Electron 28 + React 18 + Vite（节点客户端，独立前端工程）
│     src/main/main.ts  src/renderer/{App, components/{NodeDashboard,EarningsChart,DeviceScanner}}
├── neuramesh-dashboard/   [P4] React 18 + Vite + Recharts（控制台/浏览器，独立前端工程）
│     src/pages/{VendorConsole,TaskMonitor,BlockExplorer,HardwareWall}  components/{TaskForm,NodeMap,TransactionTable}
└── neuramesh-test/        集中 P0 测试 + jacoco-report-aggregation 聚合 core/storage/consensus/network/vm/benchmark
```

## 四、交易类型（锁定 4 种，不可扩展）
NODE_REGISTER / WEIGHT_UPDATE（需 3 见证签名，P2/P3）/ TASK_SETTLE / TOKEN_TRANSFER。
txId = SHA-256(typeOrdinal||from||to||nonce||payloadLen||payload||timestamp)，不含 signature；签名后 txId 不变。地址 = 公钥未压缩点 SHA-256 前 20 字节。

## 五、里程碑状态
### Pause 0（创世脚手架，2026-05-31，commit 92ce36c 已推送）
- 37 测试通过；core LINE 76% / storage 84%；0 警告。
### Pause 1（P2P 网络 + Gossip + 交易池，2026-06-01，commit 031e318 已推送）
- 62 测试通过；network LINE 77.5% / consensus 85.3%。
### Pause 2（BFT-PoS 共识，2026-06-01，commit cb933c0 已推送）
- 77 测试通过；consensus LINE 81.3%。
### Pause 3（DePIN 状态机 + 权重共识 + 设备指纹，2026-06-05，commit 3dce812 已推送）
- 101 测试通过；vm LINE 83.1% / benchmark 89.5%。
### Pause 4（节点客户端 + 厂商控制台 + 区块链浏览器，2026-06-05，待提交）
- **后端 106 测试通过**（+api 5）+ **前端 Vitest 2 通过**；P0-P3 无回退。
- Spring Boot bootRun 启动成功（1.764s）；dashboard 与 node 两套前端 npm run build 成功。
- api 网关整合 P0-P3；node(Electron)/dashboard(React) 严格遵循 oklch 设计系统与反 AI 陈词。
- 提交信息预备：`[P4] feat: 节点客户端 + 厂商控制台 + 区块链浏览器`

## 六、技术债（按 Pause 推迟）
1. **P4**：无真实 AI 推理（DeviceBenchmark 用 SHA-256 模拟）；无厂商控制台/节点客户端 UI。
2. **P4/P5**：设备指纹无 TEE/证书链绑定，无法完全防虚拟机刷分。
3. **P5**：状态存储原子性/崩溃恢复未完全验证（vm 用 GlobalState 内存 + commit root，RocksDB 持久化为应用层装配）。
4. **P4+**：验证者集固定、无动态加入退出/质押惩罚；视图变更简化版（非完整 PBFT View Change）。
5. **P4+**：交易零手续费（守恒简化）；TxPool 纯 FIFO；WEIGHT_UPDATE 偏差降权仅对已注册节点生效。
6. **P4+**：共识三阶段真实 Netty 传输（ConsensusBroadcaster 适配器）+ 区块同步签名/Merkle 校验需装配。
7. 小：Kryo/Protobuf 持久化序列化器尚未编写。

## 七、关键设计决定 & 踩坑（务必记住）
1. **api vs implementation**：根用 `java-library` 插件，模块间依赖与公共库通过 `api` 暴露。
2. **不可变对象 + Kryo**：Transaction/Block 不可变无 setter，不走 Kryo 字段复制；网络层用 `codec/TransactionCodec`、`codec/BlockCodec` 显式字节编解码，重建时重算 txId/hash。
3. **【Kryo + Java 17 踩坑】**：Kryo 无法反射访问 `java.util.UUID` 私有字段（JPMS 强封装），必须在 `KryoSerialization` 为 UUID 注册自定义 `Serializer`（写两个 long）。否则报 `CachedFields.rebuild`。
4. **帧协议**：`[1字节 typeId][Kryo body]`，外层 `LengthFieldPrepender(4)` / `LengthFieldBasedFrameDecoder` 加 4 字节长度头；MAX_FRAME=16MB。
5. **异步网络**：业务处理派发到 `businessPool`，禁止阻塞 Netty I/O 线程；NioEventLoopGroup boss/worker/client 三组，shutdown 优雅关闭。
6. **握手**：连接建立双方互发 Hello（NodeId/端口/高度），登记 PeerManager；Ping 自动回 Pong；channelInactive 即时移除 Peer，Heartbeat 负责超时（默认 15s）兜底。
7. **【Write 工具踩坑 / Windows】**：对**不存在的新文件**，Write 会写成 UTF-16 → Java 编译失败。解决：先用 PowerShell `[System.IO.File]::WriteAllText($path,$content,(New-Object System.Text.UTF8Encoding $false))` 预创建占位（UTF-8 无 BOM），之后 Write/StrReplace 即正常。
8. **RocksDB 分区**：单 ColumnFamily + ASCII 前缀（`blocks:` 等），同步 WAL（setSync(true)）。

## 八、镜像与网络配置（中国大陆）
- Maven：`https://mirrors.cloud.tencent.com/nexus/repository/maven-public/`（优先）+ mavenCentral 兜底。
- Gradle 插件：`https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugins/`（settings.gradle pluginManagement）。
- Gradle 发行版：Wrapper distributionUrl 指向腾讯云。
- gradle.properties：`org.gradle.java.installations.auto-download=false`（用本机 JDK 17）。

## 九、Agent 协作规则（每个 Pause 必读）
1. 核心三条：①提示词与实际代码冲突以代码为准；②用户选择优先于提示词（修改前），Opus 修改结果优先于提示词（修改后）；③此规则加入以后每个提示词。
2. PowerShell 不支持 `&&`，用 `;` 或拆分命令。
3. 修改代码前评估风险；用户确认稳定后第一时间 git commit（不主动 push 到 main 之外、不主动配 remote 之外操作）。
4. 避免不必要测试脚本，优先自动检查（lints/编译/已有测试）。
5. 包命名 com.neuramesh.{module}；自定义异常继承 NeuraException；禁止吞异常；禁止 System.out.println；并发优先 ConcurrentHashMap/BlockingQueue/AtomicLong；测试类 *Test.java，集成测试 *IT.java，测试间禁止数据依赖。

## 十、命令速查
```powershell
.\gradlew.bat test                 # 全部测试 + 聚合覆盖率
# 覆盖率报告：neuramesh-test\build\reports\jacoco\testCodeCoverageReport\
.\gradlew.bat :neuramesh-network:test --tests "*GossipTest" --no-daemon --console=plain
.\gradlew.bat spotlessApply
```
> 注意：网络测试若因端口冲突失败，改用固定端口范围 30000-30100；测试用 `new ServerSocket(0)` 取随机空闲端口。

## 十一、共识关键设计（P2，务必记住）
- 节点身份在 consensus 内用 20 字节地址 byte[]（consensus 不能依赖 network 的 NodeId）。
- 区块哈希与签名解耦：提案区块 validatorSig 置空，提案人对 block.getHash() 的签名随 PrePrepare 单独传播。
- 消息 ID：0x07=PrePrepare / 0x08=Prepare / 0x09=Commit（0x06 已被 P1 HELLO 占用）。
- quorum=floor(2n/3)+1；提案人加权轮询 pos=floorMod(height+view+seed, totalWeight)。
- 共识传输经 ConsensusBroadcaster 抽象；测试用确定性内存总线 InMemoryConsensusCluster（避免真实网络 flaky）。

## 十二、VM/状态机关键设计（P3，务必记住）
- StateMachine.apply 同步串行（BFT 顺序），快照→nonce 校验→处理器→递增 nonce→commit root；失败 restoreFrom 回滚。
- 处理器纯逻辑只碰 GlobalState 内存，不碰 RocksDB（持久化为应用层装配）。
- 权重公式 totalWeight=hw*0.3+quality*0.4+uptime*0.2+bw*0.1；NodeRegister 初始权重 0。
- WeightUpdate 需 ≥2 个不同验证者对同一分数一致背书；TaskSettle 整数比例分配+余数补齐零误差守恒；零手续费保证总额守恒。
- ColumnFamilies 现有 BLOCKS/TRANSACTIONS/STATE/NODES/META；RocksDBStore 提供 putState/getState/putNode/getNode。

## 十三、API/前端关键设计（P4，务必记住）
- neuramesh-api：Spring Boot 3.2.10；ApiResponse<T>(code/data/message)；ChainService 单例整合 ValidatorSet+StateMachine+GlobalState+内存区块链（每交易一区块）。
- 节点身份：NodeService 注册时生成密钥对（地址=NodeID），先 NODE_REGISTER 再 WEIGHT_UPDATE（用 ChainService 验证者见证签名）赋权重；VendorService 首次注资 1_000_000，按在线节点权重 TASK_SETTLE 分配。
- logback 踩坑：api 模块 resolutionStrategy 强制 ch.qos.logback 回落 1.4.14（Spring Boot 3.2.x 需 1.4.x）。
- 前端：npm install 用 npmmirror；Electron 用 ELECTRON_SKIP_BINARY_DOWNLOAD=1；测试用 vi.mock 桩替 recharts。
- 前端命令：dashboard `npm run build`/`npm test`；node `npm run build:renderer`+`build:main`+`test`。

## 十四之二、Pause 5 进度（务必记住）
- **环境实况**：JDK 21 运行时/构建 Java 17；Gradle 8.10.2；**Docker 未安装**（docker/docker-compose 命令不存在 → Step 6 仅产出文件，无法本地 `docker-compose up`）。JMH 用 `me.champeau.jmh` 0.7.2。
- **P4 实况修正**：`[P4]` 已 commit+push（739dc09）。工作区 4 文件（TxPool/VoteCollectorTest/GossipProtocol/StateMachineTest）仅 CRLF/LF 行尾噪声，无实质改动，勿提交噪声。
- **资源组架构（Step 2 已完成，测试全绿）**：
  - 新增 `com.neuramesh.vm.group`：`ResourceGroup`(groupId/region/minBenchmarkScore/requiredHttp2/nodeIds)、`GroupMembership`(record: nodeIdHex/groupId/joinedAt/verified)、`GroupValidator`(性能门槛真实校验；HTTP2/GPS/IP 占位返回 true，TODO P6)。
  - 新增 `state/ResourceGroupState`：groups+memberships 两个 ConcurrentMap，getGroupByRegion/addNodeToGroup(含组间迁移)/removeNodeFromGroup/commitLeaves/copy/restoreFrom。
  - `GlobalState` 持有 `resourceGroups`，纳入 commit() Merkle 叶子（G:/M: 前缀）、snapshot/restoreFrom。**状态仍是内存模型，非 RocksDB ColumnFamily**（提示词"扩展 CF"不适用）。
  - `TaskSettlePayload` +可选 `resourceGroupId`（4 字段，保留 3 参委托构造器；encode/decode 用 writeUTF/available()>0 向后兼容）。`NodeRegisterPayload` 同理 +`resourceGroupId`（3 字段，2 参委托构造器）。**所有旧调用点零改动**。
  - `TaskSettleProcessor`：allocations 空且 groupId 非空 → resolveFromGroup（组内 weight>0 节点按 round(totalWeight) 分配）。`NodeRegisterProcessor`：groupId 非空 → GroupValidator 校验 → addNodeToGroup（失败抛 INVALID_PAYLOAD 整体回滚）。
  - `CryptoUtils` 新增 `fromHex(String)`（toHex 逆操作，支持 0x 前缀）。
  - 测试：`neuramesh-vm/.../ResourceGroupTest`（8 用例，全绿）。
- **资源组命名**：groupId 小写+连字符（north-china-qingdao / east-china-shanghai）；region 中文（华北-青岛）。
- **Step 3 api（完成）**：`ResourceGroupController`(/groups, /groups/{id}, /groups/{id}/nodes, POST /groups/{id}/join, POST /groups/{id}/allocate)、`ResourceGroupService`(@PostConstruct 播种 3 组：north-china-qingdao/east-china-shanghai/south-china-shenzhen；join 软验证直写 state；allocateTask 构造空分配+groupId 的 TASK_SETTLE)、`ResourceGroupDTO`。`ChainController` 注入 NodeService 新增 `GET /chain/nodes?groupBy=level|sortBy=weight`。api 5 测试含 contextLoads 全绿。
- **Step 4 dashboard（完成）**：`chartConfig.ts`（oklch 调色板/tooltip/axis 统一）+ 5 组件 `LevelDistributionChart`(环形)/`ThroughputChart`(面积+折线,5s 采样差分 TPS)/`ActivityStream`(终端绿字像素风)/`KPICard`+`NetworkKPIs`(6 卡)/`WeightEarningsChart`(双 Y 柱)。新增页面 `NetworkMonitor`(路由 /network)，`HardwareWall` 加 6 卡+活动流，`BlockExplorer` 加权重柱。`api.ts` 加 nodesByLevel/nodesByWeight/groups/groupNodes/joinGroup/allocateGroupTask。`npm run build`(tsc+vite) 通过。
- **Step 5 JMH（完成）**：`neuramesh-test` 应用 `me.champeau.jmh` 0.7.2，JMH 1.37，源集 src/jmh/java/com/neuramesh/jmh，结果 JSON+human 输出 build/reports/jmh/。**实测 JDK17：StateMachine 8013 tx/s；共识单轮(8 验证者全 PBFT+真实 ECDSA) 1.697 ms；共识 590 轮/s；Gossip 扇出 177k/s；单跳解码 0.667µs**。全部达标（TPS≥1000、最终化≤1.5s）。
- **Step 6 Docker（完成，未本机验证—Docker 未装）**：`neuramesh-network` 应用 `application` 插件 + 新增 `SeedNode`(main，env NEURA_PORT/NEURA_PEERS/NEURA_NODE_HEIGHT，连 bootstrap 重试)；`docker/` 下 docker-compose.yml(4 种子+api+dashboard)、seed-node.Dockerfile(installDist→neura-seed)、api.Dockerfile(bootJar)、dashboard.Dockerfile(npm build→nginx)、nginx.conf(/api 反代 api:8080)、init.sh、README.md。`installDist`+`bootJar` 本机构建通过。
- **测试与覆盖率**：clean test 114 测试 0 失败 0 错误（106 P4 + 8 ResourceGroupTest）；聚合 LINE 78.5%（vm.group 84.6/state 83.5/processors 85.8/payload 81.5）≥70%。
- **提交信息预备（上半，已提交 dbfa7c8）**：`[P5] feat: Demo集成 + 资源组架构 + 可视化增强 + JMH压测`。

### Pause 5 下半（用户系统 + 安全组 + 购买界面，本地完成待提交）
- **环境**：Spring Boot 3.2.10 → Spring Security 6.2.x；JWT 用 `io.jsonwebtoken jjwt 0.12.6`（HS256）。bootRun 测试前需停（Windows 文件锁）。
- **vm 扩展**：新增 `state/UserState`（userId/username/passwordHash/role/publicKey/encryptedPrivKey/address，不可变，余额取链上）。`GlobalState` +`users: Map`，纳入 commit() "U:" 叶子 + snapshot/restore。`ResourceGroup` +groupPublicKey/encryptedGroupPrivKey/pricePerHour（保留 4 参构造器）。`GroupMembership` +membershipCertificate（保留 4 参构造器）。`ResourceGroupState.addNodeToGroup` 加 5 参重载（带 cert）。commitLeaves 的 G:/M: 叶子已含新字段。
- **api 安全**：`build.gradle` +spring-boot-starter-security +jjwt(api/impl/jackson) +spring-security-test。`security/`：`JwtUtil`(HS256，access 15m/refresh 7d，密钥读 `neuramesh.jwt.secret` 默认值)、`JwtFilter`(OncePerRequest，Bearer→UserPrincipal→SecurityContext，ROLE_前缀)、`UserPrincipal`(record userId/username/role/address)、`CryptoBox`(AES-256-GCM + PBKDF2，加密私钥)。`config/SecurityConfig`(无状态，permitAll 除 /user/** 、/vendor/groups/** 、POST /market/groups/*/buy|renew 需认证；OPTIONS 全放行；CORS 用 CorsConfigurationSource)。**删除旧 CorsConfig（WebMvc）避免重复 CORS 头**。
- **api 认证/用户**：`AuthService`(register: BCrypt(强度10)+ECDSA keypair+CryptoBox 加密私钥+VENDOR 初始注资 5,000,000；login/refresh)、`UserService`(profile/balance 取链上)、`AuthController`(/auth/register|login|refresh)、`UserController`(/user/me|balance 需认证)。DTO: LoginRequest/RegisterRequest/TokenResponse/PurchaseRequest/PurchaseReceiptDTO。
- **api 市场/购买**：`ResourceGroupService` seed 给 3 组生成安全组密钥对+价格(qingdao 20k/shanghai 50k/shenzhen 35k/h)，join 签发成员凭证；新增 buy（校验余额→TOKEN_TRANSFER 到金库 treasuryKey→发放明文 groupPrivateKey+记录购买）、renew、myGroups。`ResourceGroupController` 去掉类级 @RequestMapping，方法用绝对路径，新增 /market/groups、/market/groups/{id}、POST /market/groups/{id}/buy|renew、GET /vendor/groups。`ResourceGroupDTO` +pricePerHour+groupPublicKey。
- **关键**：StateMachine.apply 不验签（仅 nonce），故购买扣款构造无签名 TOKEN_TRANSFER 即可由状态机扣款守恒。groupPrivateKey 明文交付（TODO P6 加密）。
- **dashboard**：`api.ts` +auth(localStorage token/Authorization 头)+register/login/me/myBalance/market/buyGroup/renewGroup/myGroups。新增页 `LoginPage`(登录/注册)、`MarketPage`(卡片网格+排序)、`BuyModal`(时长/费用/余额/凭证)、`MyGroupsPage`(已购+续费)。`App.tsx` +登录态/退出/侧栏用户信息+路由(/market /vendor-groups /login)。`npm run build` 通过。
- **测试**：新增 `AuthPurchaseTest`(4 用例：注册登录余额购买全流程/重复用户名/错密码/余额不足)。clean test **118 测试 0 失败**（106 P4 +8 资源组 +4 auth）。聚合 LINE 77.1%（vm.state 70.1/group 81.5）≥70%。
- **提交信息预备（下半）**：`[P5] feat: 用户系统 + 安全组公钥私钥 + 厂商购买界面`（已提交 56e7d16，含角色权限/阿里云式市场/总览动态化）。

### Pause 6 Step 0（真实化改造，本地完成待提交）
- **背景**：评委需看到每步真实上链。规则①——提示词的 `Transaction.builder()`/`txPool.addTransaction` 等与实际不符，以实际 API 为准（`Transaction.create`/`TxPool.addTransaction` 确存在）。
- **核心改造 `ChainService` 真实管线**：原 `applyTx` 直接 stateMachine.apply + 内存 List<Block>（绕过 TxPool/BFT）。**改为**：`txPool.addTransaction` → 单验证者 `BFTConsensus`（独立 bftKey，quorum=1 自达共识，同一调用栈同步走完 PrePrepare/Prepare/Commit 真实 ECDSA+VoteCollector）→ `finalizeBlock` 写 `InMemoryBlockStore` → `onFinalized` 回调里 `StateMachine.apply` 执行。**同步**返回保持调用方语义。
- **两套验证者集解耦**：BFT 出块用 1 验证者（自达共识）；StateMachine WEIGHT_UPDATE 见证仍用 4 验证者。互不冲突。
- **交易生命周期**：txStatus map（pending→finalized→executed/rejected）；`ChainService.txLifecycle(hash)`；`GET /chain/tx/{hash}/status` 端点。执行失败经 `pendingError` 回传 applyTx 抛出（状态机已回滚）。
- **区块/交易真实读取**：latestBlocks 从 BlockStore 倒序、findTx 从 txIndex（onFinalized 填充）、blockHeight=currentHeight+1。
- **前端**：核查无数据 mock（仅 PixelIntro 动画用 Math.random、VendorConsole.test 桩）。VendorConsole 历史项加 `TxChainStatus` 轮询 `/chain/tx/{hash}/status` 显示真实链上状态。api.ts +txStatus。
- **测试**：新增 `RealEndToEndTest.fullDemoScenario`（注册→节点上链→购买扣款→任务结算，断言真实区块/64hex 哈希/txLifecycle=executed/余额变更/节点收益）。**全量 BUILD SUCCESSFUL**，api 10 测试全绿，前端 build+test 通过。
- **提交信息预备**：`[P6] fix: 真实化改造 - API真实调用链 + 前端真实数据 + 端到端测试`。
- **债务**：funding 仍直写（创世简化，非 tx）；单验证者本地共识（多节点 P2P 共识装配待赛后）；RocksDB 持久化未接（InMemoryBlockStore）。
- **CRLF 噪声**：TxPool/VoteCollectorTest/GossipProtocol/StateMachineTest 4 文件为行尾噪声，已 `git checkout --` 还原，未纳入提交。

### Pause 6 Step 0.6（权重派生 + 演示流量 + 幽灵登录修复 + 打包发布，2026-06-12 完成）
- **权重派生**：NodeService WEIGHT_UPDATE 的质量/在线/带宽由跑分按 0.9/0.95/0.8 派生 → totalWeight=0.93*hw（跑分被 normalizedScore cap 在 1000，本机恒 930 属正常）。
- **演示流量**：`DemoTrafficService`（默认关，4s tick 提交 1~2 笔无签名 TOKEN_TRANSFER 演示账户互转，真实出块）+ `GET/POST /chain/demo-traffic` + ThroughputChart 右上开关。测试 `DemoTrafficTest`（tickOnce 确定性断言）。
- **幽灵登录修复**：内存链重启后旧 JWT 仍验签通过 → /user/me|balance 链上查无用户返回 401；前端 call() 遇 401 自动 auth.clear()+广播 `neura:logout`（App 监听同步 UI）；BuyModal 余额未知禁购（按钮"余额不可用"）。
- **吞吐切页清零修复**：采样移入 `dashboard/src/throughputStore.ts` 模块级单例（定时器首订阅后常驻），组件只订阅渲染；React 挂载初始帧为空属正常，等 effect 完成。
- **【打包关键坑】ESM 主进程相对导入必须带 .js 扩展名**（`from "./fingerprintManager.js"`），否则打包后 ERR_MODULE_NOT_FOUND——dev 模式从不跑 Electron 主进程（npm run dev 只起 vite），此坑只在打包后暴露。
- **打包方法（零下载，国内最稳）**：本地已有 electron 二进制（node_modules/electron/dist）→ robocopy 整目录到 release/NeuraMesh-Node-win32-x64 → electron.exe 改名 NeuraMesh-Node.exe → resources/app 放精简 package.json(name/version/type:module/main)+dist+dist-main → 删 default_app.asar（曾被进程锁住：Compress-Archive 失败，改用 `tar -a -c -f xx.zip --exclude ... -C release .` 成功）。
- **发布物**：`NeuraMesh-v0.6-win64.zip`(198MB)=bootJar(98.6MB)+dashboard 静态+Node 客户端免安装+启动说明.txt；release/ 与 *.zip 已 gitignore。打包后 exe 烟雾测试通过（4 进程+窗口标题正常）。
- 提交：4df257d/60df62f（昨日）+ 第一笔权重/流量 + 107aea5 幽灵登录 + 21bde5a ESM/gitignore，全部已推送。

### Pause 6 Step 0.5（"无合格节点"修复 + 设备指纹终身持久化，2026-06-11 完成）
- **根因**：节点注册（NodeService.register）从不传 resourceGroupId → 节点不属于任何组；VendorConsole 选中已购组下发任务 → 组内无成员 → `ResourceGroupService.allocateTask` 报"无合格节点" FAILED。
- **VM 修复（共识语义变更，确定性）**：
  - `NodeRegisterProcessor`：①注册即赋初始权重 `setScores(max(hw,1),0,0,0)` → totalWeight=max(hw,1)*0.3>0，即时具备结算资格（WEIGHT_UPDATE 随后覆盖四项分数）；②未指定组兜底加入 `DEFAULT_GROUP_ID="general-purpose"`（不存在则确定性自动创建，门槛 0/无 HTTP2，永不拒绝）。常量暴露在处理器上。
  - `TaskSettleProcessor.resolveFromGroup`：精确报因三分支——组不存在/组为空（"为空"）/全员无有效权重（"无有效权重"），不再返回空列表落入笼统报错。
  - **测试影响**：NodeRegisterTest 权重断言 0→1500（hw5000*0.3）；WeightUpdateTest 回滚基准 0→0.3；ResourceGroupTest +4 修复用例（兜底分组/默认组结算守恒/组空报因/零权重报因）。
- **API**：seed 增播 `general-purpose`（通用-全局，门槛 0，8000/h，"默认组"标签）；`allocateTask` 失败日志精确区分组空/未注册/离线/权重 0；`NodeService.register(model, groupId)` 重载（旧 1 参委托）；`NodeRegisterRequest`+resourceGroupId；`NodeStatusDTO`+fingerprint(hex)。
- **节点客户端（Electron）指纹终身持久化**（用户 Patch 1/2 按实际架构适配：指纹由后端生成，客户端持久化的是注册回执身份）：
  - `renderer/utils/fingerprintStorage.ts`：NodeIdentity{nodeId,fingerprint,deviceModel,resourceGroupId,registeredAt}，key=`neuramesh_device_fingerprint_v1`，双层存储——主进程文件优先 + localStorage 兜底，损坏自清理。
  - `main/fingerprintManager.ts`：userData/neuramesh-fingerprint.json；`preload/preload.ts`（contextBridge 暴露 window.neuraIdentity，**CJS 编译** tsconfig.preload.json，sandbox 预加载不受 type:module 影响）；main.ts +ipcMain.handle(identity:load/save/clear)+启动日志；**ESM 主进程无 __dirname，已用 fileURLToPath(import.meta.url) 修复**（连带修复了原 loadFile 的潜在崩溃）。
  - `DeviceScanner`：强制资源组选择（/groups 下拉，默认选中 general-purpose，未选禁用按钮），注册成功即 saveIdentity。`NodeDashboard`：挂载先恢复持久化身份（api.status），"已永久绑定/首次生成"徽标 + 指纹展示；仅当后端明确"节点不存在"才清身份。
  - package.json build:main 追加 preload 编译；NodeDashboard.test 更新 mock（groups/status）+1 恢复用例；新增 fingerprintStorage.test（4 用例）。
- **后端去重**（用户 Patch 3）：实际代码早已实现且更优（GlobalState 以 hex 字符串存指纹集合，无 byte[] 身份相等坑），无需改动。
- **验证实录**：`gradlew test` 全绿（vm 12 资源组用例 + api RealEndToEndTest 含组任务断言）；node `npm test` 6 绿 + `npm run build` 通过（dist-main 含 main/preload/fingerprintManager）；bootRun curl 全流程——7 组播种、双节点注册（指定组/兜底组）、组内节点查询、两组 allocate 均 SETTLED、节点 totalEarned=30000、交易 executed、JWT 余额 5,000,000。

## 十四、下一步（Pause 5 入口条件）
- 阅读 Pause 5 提示词，确认 P4 债务（真实 AI 推理 TFLite/ONNX、移动端、排行榜、WebSocket、真实设备、RocksDB 持久化装配）。
- 在 Pause 5 提示词中重申核心三规则。
- 入口检查：P4 后端测试全绿、bootRun 可启动、前端可构建、git 已提交、工作区 clean。
