# Nimbus 云盘 — Java 技术栈企业级高可用架构方案

------

## 一、功能模块拆解

| 功能域     | 核心功能点                        | 技术难点                   |
| ---------- | --------------------------------- | -------------------------- |
| 用户中心   | 注册/登录、OAuth2/SSO、多设备管理 | 分布式会话、Token刷新      |
| 文件管理   | 上传/下载/重命名/移动/复制/删除   | 大文件分片、断点续传、秒传 |
| 文件夹管理 | 创建/删除、目录树、面包屑导航     | 树形结构高效查询、并发冲突 |
| 文件分享   | 短链、密码、有效期、权限控制      | 短链生成、防盗链、访问统计 |
| 回收站     | 软删除、恢复、过期清理            | 延迟删除、空间回收         |
| 搜索       | 文件名/全文/标签/高级过滤         | 海量数据实时索引           |
| 文件预览   | 图片/文档/视频/音频在线预览       | 异步转码、缩略图生成       |
| 存储配额   | 容量统计、配额管理、升级          | 实时计数、最终一致性       |
| 版本管理   | 历史版本、版本回滚、版本对比      | 增量存储、版本链           |
| 审计日志   | 操作审计、异常告警                | 高吞吐写入、实时分析       |

------

## 二、技术选型总览（纯 Java 生态）

| 层次              | 技术选型                            | 版本         | 用途                |
| ----------------- | ----------------------------------- | ------------ | ------------------- |
| **基础框架**      | Spring Boot                         | 3.3.x        | 微服务基座          |
| **微服务治理**    | Spring Cloud Alibaba                | 2023.x       | 全家桶              |
| **服务注册/配置** | Nacos                               | 2.3.x        | 注册中心 + 配置中心 |
| **API 网关**      | Spring Cloud Gateway                | 4.1.x        | 路由、限流、鉴权    |
| **服务调用**      | OpenFeign + Dubbo 3                 | —            | HTTP + RPC 双协议   |
| **负载均衡**      | Spring Cloud LoadBalancer           | —            | 客户端负载均衡      |
| **限流熔断**      | Sentinel                            | 1.8.x        | 流控、熔断、降级    |
| **分布式事务**    | Seata                               | 2.0.x        | AT/TCC/Saga 模式    |
| **数据库**        | MySQL                               | 8.0+         | 主存储              |
| **ORM**           | MyBatis-Plus                        | 3.5.x        | 数据访问            |
| **分库分表**      | ShardingSphere                      | 5.5.x        | 水平拆分            |
| **缓存**          | Redis + Redisson                    | 7.x          | 分布式缓存/锁       |
| **消息队列**      | Apache RocketMQ                     | 5.x          | 异步解耦、事件驱动  |
| **搜索引擎**      | Elasticsearch                       | 8.x          | 全文检索            |
| **对象存储**      | MinIO                               | RELEASE.2024 | 文件存储（S3 兼容） |
| **任务调度**      | XXL-Job                             | 2.4.x        | 分布式定时任务      |
| **分布式ID**      | 美团 Leaf / Snowflake               | —            | 全局唯一ID          |
| **安全框架**      | Spring Security + OAuth2            | 6.x          | 认证鉴权            |
| **JWT**           | jjwt / nimbus-jose-jwt              | —            | Token 生成校验      |
| **链路追踪**      | Apache SkyWalking                   | 9.x          | APM 全链路监控      |
| **日志收集**      | ELK (Filebeat+Logstash+ES+Kibana)   | 8.x          | 集中日志            |
| **监控告警**      | Prometheus + Grafana + AlertManager | —            | 指标监控            |
| **容器编排**      | Docker + Kubernetes                 | 1.29+        | 部署运行            |
| **服务网格**      | Spring Cloud Alibaba (替代Istio)    | —            | Java原生治理        |
| **CI/CD**         | GitLab CI + Jenkins + Harbor        | —            | 持续交付            |
| **文件处理**      | Apache Tika + FFmpeg + LibreOffice  | —            | 文档解析/转码       |
| **接口文档**      | SpringDoc (OpenAPI 3)               | 2.x          | API 文档            |

------

## 三、整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              客户端接入层                                         │
│         Web (Vue3)  │  iOS  │  Android  │  Desktop (JavaFX)  │  Open API       │
└────────────────────────────────────┬────────────────────────────────────────────┘
                                     │ HTTPS / WSS
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          流量接入 & 安全防护层                                    │
│  ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌────────────────────────┐ │
│  │ Nginx/SLB  │   │  WAF 防火墙│   │ DDoS 防护  │   │  CDN (静态资源加速)     │ │
│  └─────┬──────┘   └─────┬──────┘   └─────┬──────┘   └───────────┬────────────┘ │
└────────┼────────────────┼────────────────┼───────────────────────┼──────────────┘
         │                │                │                       │
         ▼                ▼                ▼                       ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     API 网关层 (Spring Cloud Gateway 集群)                        │
│                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │  • 统一路由 & 负载均衡                                                     │  │
│  │  • JWT Token 校验 (全局过滤器)                                             │  │
│  │  • Sentinel 网关限流 (QPS / 并发 / 热点参数)                               │  │
│  │  • 请求日志 & 链路追踪 (TraceId 注入)                                      │  │
│  │  • 灰度发布路由 (按 Header / Cookie)                                       │  │
│  │  • 跨域处理 & 协议转换                                                     │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────┬────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                                                                 │
│                    微服务业务层 (Spring Boot 3 + Spring Cloud Alibaba)            │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │ nimbus-gateway   │  │ nimbus-auth     │  │ nimbus-user                     │ │
│  │ API 网关服务     │  │ 认证鉴权服务    │  │ 用户中心服务                     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘ │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │ nimbus-file     │  │ nimbus-upload   │  │ nimbus-download                 │ │
│  │ 文件元数据服务  │  │ 上传服务        │  │ 下载服务                         │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘ │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │ nimbus-share    │  │ nimbus-search   │  │ nimbus-preview                  │ │
│  │ 分享服务        │  │ 搜索服务        │  │ 预览服务                         │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘ │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │ nimbus-recycle  │  │ nimbus-quota    │  │ nimbus-version                  │ │
│  │ 回收站服务      │  │ 配额管理服务    │  │ 版本管理服务                     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘ │
│                                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────────┐ │
│  │ nimbus-audit    │  │ nimbus-notify   │  │ nimbus-admin                    │ │
│  │ 审计日志服务    │  │ 通知服务        │  │ 管理后台服务                     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────────┘ │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                    公共模块 (nimbus-common)                               │   │
│  │  nimbus-common-core  │  nimbus-common-redis  │  nimbus-common-mq        │   │
│  │  nimbus-common-security  │  nimbus-common-log  │  nimbus-common-oss     │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
└────────────────────────────────────┬────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              数据层 & 存储层                                      │
│                                                                                 │
│  ┌───────────────┐  ┌───────────────┐  ┌─────────────────────────────────────┐ │
│  │  MySQL 8.0    │  │  Redis 7      │  │  Elasticsearch 8                    │ │
│  │  (ShardingSphere│  │  (Cluster)    │  │  (全文检索)                         │ │
│  │   分库分表)   │  │  缓存/锁/会话 │  │                                     │ │
│  └───────────────┘  └───────────────┘  └─────────────────────────────────────┘ │
│                                                                                 │
│  ┌───────────────┐  ┌───────────────┐  ┌─────────────────────────────────────┐ │
│  │  MinIO        │  │  RocketMQ 5   │  │  ClickHouse                         │ │
│  │  (对象存储)   │  │  (消息队列)   │  │  (日志分析/审计)                    │ │
│  └───────────────┘  └───────────────┘  └─────────────────────────────────────┘ │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           基础设施 & 可观测性层                                   │
│                                                                                 │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  ┌──────────────────────┐  │
│  │ Kubernetes  │  │ SkyWalking   │  │ Prometheus │  │ ELK Stack            │  │
│  │ 容器编排    │  │ 链路追踪     │  │ + Grafana  │  │ 日志平台             │  │
│  └─────────────┘  └──────────────┘  └────────────┘  └──────────────────────┘  │
│                                                                                 │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  ┌──────────────────────┐  │
│  │ GitLab CI   │  │ Harbor       │  │ Nacos      │  │ XXL-Job Admin        │  │
│  │ + Jenkins   │  │ 镜像仓库     │  │ 配置中心   │  │ 任务调度中心         │  │
│  └─────────────┘  └──────────────┘  └────────────┘  └──────────────────────┘  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

------

## 四、工程结构设计（Maven 多模块）

```
nimbus-cloud/
├── pom.xml                              # 父 POM（统一版本管理）
│
├── nimbus-common/                       # 公共模块
│   ├── nimbus-common-core/              # 工具类、异常、常量、Result
│   ├── nimbus-common-redis/             # Redis 封装（Redisson）
│   ├── nimbus-common-mq/               # RocketMQ 封装
│   ├── nimbus-common-oss/              # MinIO 操作封装
│   ├── nimbus-common-security/         # Security + JWT 封装
│   ├── nimbus-common-log/              # 操作日志注解 + AOP
│   ├── nimbus-common-swagger/          # SpringDoc 配置
│   └── nimbus-common-mybatis/          # MyBatis-Plus 配置
│
├── nimbus-gateway/                      # API 网关
│   └── src/main/java/
│       └── com/nimbus/gateway/
│           ├── GatewayApplication.java
│           ├── config/
│           │   ├── RouteConfig.java          # 动态路由（Nacos）
│           │   └── SentinelGatewayConfig.java
│           └── filter/
│               ├── AuthGlobalFilter.java     # JWT 鉴权
│               ├── RateLimitFilter.java      # 限流
│               ├── TraceIdFilter.java        # 链路追踪
│               └── RequestLogFilter.java     # 请求日志
│
├── nimbus-auth/                         # 认证鉴权服务
│   └── src/main/java/
│       └── com/nimbus/auth/
│           ├── AuthApplication.java
│           ├── controller/
│           │   └── AuthController.java       # 登录/注册/刷新/登出
│           ├── service/
│           │   ├── AuthService.java
│           │   ├── TokenService.java         # JWT 生成/校验/刷新
│           │   └── OAuth2Service.java        # 第三方登录
│           ├── security/
│           │   ├── SecurityConfig.java
│           │   ├── JwtAuthenticationFilter.java
│           │   └── UserDetailsServiceImpl.java
│           └── model/
│               ├── LoginRequest.java
│               ├── TokenResponse.java
│               └── OAuth2UserInfo.java
│
├── nimbus-user/                         # 用户中心服务
│   └── src/main/java/
│       └── com/nimbus/user/
│           ├── UserApplication.java
│           ├── controller/
│           │   └── UserController.java
│           ├── service/
│           │   ├── UserService.java
│           │   └── DeviceService.java        # 多设备管理
│           ├── mapper/
│           │   └── UserMapper.java
│           └── model/
│               ├── entity/User.java
│               └── dto/UserDTO.java
│
├── nimbus-file/                         # 文件元数据服务
│   └── src/main/java/
│       └── com/nimbus/file/
│           ├── FileApplication.java
│           ├── controller/
│           │   ├── FileController.java
│           │   └── FolderController.java
│           ├── service/
│           │   ├── FileService.java
│           │   ├── FolderService.java
│           │   └── FileTreeService.java      # 目录树操作
│           ├── mapper/
│           │   ├── FileMapper.java
│           │   └── FolderMapper.java
│           └── model/
│               ├── entity/FileEntity.java
│               ├── entity/FolderEntity.java
│               └── dto/FileTreeDTO.java
│
├── nimbus-upload/                       # 上传服务
│   └── src/main/java/
│       └── com/nimbus/upload/
│           ├── UploadApplication.java
│           ├── controller/
│           │   └── UploadController.java
│           ├── service/
│           │   ├── UploadService.java
│           │   ├── ChunkService.java         # 分片管理
│           │   ├── InstantUploadService.java # 秒传
│           │   └── MinioService.java         # MinIO 操作
│           └── model/
│               ├── UploadInitRequest.java
│               ├── ChunkUploadRequest.java
│               └── MergeRequest.java
│
├── nimbus-download/                     # 下载服务
├── nimbus-share/                        # 分享服务
├── nimbus-search/                       # 搜索服务
├── nimbus-preview/                      # 预览服务
├── nimbus-recycle/                      # 回收站服务
├── nimbus-quota/                        # 配额服务
├── nimbus-version/                      # 版本管理服务
├── nimbus-audit/                        # 审计日志服务
├── nimbus-notify/                       # 通知服务
├── nimbus-admin/                        # 管理后台服务
│
├── nimbus-job/                          # 定时任务（XXL-Job Handler）
│   └── src/main/java/
│       └── com/nimbus/job/
│           ├── handler/
│           │   ├── RecycleCleanJob.java      # 回收站过期清理
│           │   ├── QuotaSyncJob.java         # 配额对账
│           │   ├── ShareExpireJob.java       # 分享过期处理
│           │   └── AuditArchiveJob.java      # 日志归档
│           └── JobApplication.java
│
└── deploy/                              # 部署配置
    ├── docker/
    │   ├── Dockerfile
    │   └── docker-compose.yml
    ├── k8s/
    │   ├── namespace.yaml
    │   ├── deployment/
    │   ├── service/
    │   ├── ingress/
    │   └── hpa/
    └── nacos/
        └── configs/
```

------

## 五、各核心服务详细设计

### 5.1 认证鉴权服务（nimbus-auth）

```java
/**
 * 认证流程：
 * 1. 用户登录 → 验证账密 → 生成 AccessToken(30min) + RefreshToken(7d)
 * 2. AccessToken 存 Redis，支持主动失效
 * 3. RefreshToken 用于无感刷新
 * 4. 支持 OAuth2.0（微信/钉钉/企业SSO）
 */

@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping("/login")
    public Result<TokenResponse> login(@RequestBody @Valid LoginRequest req) {
        // 1. 验证用户名密码
        // 2. 检查账号状态（锁定/禁用）
        // 3. 生成双 Token
        // 4. 记录登录日志（设备指纹、IP、地理位置）
        // 5. 异地登录检测 → 触发通知
    }

    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@RequestParam String refreshToken) {
        // 校验 RefreshToken → 签发新 AccessToken
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        // 将 Token 加入 Redis 黑名单
    }

    @GetMapping("/oauth2/{provider}/callback")
    public Result<TokenResponse> oauth2Callback(
            @PathVariable String provider,
            @RequestParam String code) {
        // OAuth2 授权码换 Token
    }
}
```

**Token 设计：**

```java
@Service
public class TokenService {

    private static final long ACCESS_TOKEN_EXPIRE = 30 * 60;  // 30分钟
    private static final long REFRESH_TOKEN_EXPIRE = 7 * 24 * 3600; // 7天

    @Autowired
    private StringRedisTemplate redisTemplate;

    public TokenResponse generateTokens(Long userId, String deviceId) {
        String accessToken = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("deviceId", deviceId)
                .claim("roles", getUserRoles(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE * 1000))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();

        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        // 存入 Redis，支持主动踢下线
        redisTemplate.opsForValue().set(
            "nimbus:token:access:" + userId + ":" + deviceId,
            accessToken, ACCESS_TOKEN_EXPIRE, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(
            "nimbus:token:refresh:" + userId,
            refreshToken, REFRESH_TOKEN_EXPIRE, TimeUnit.SECONDS);

        return new TokenResponse(accessToken, refreshToken, ACCESS_TOKEN_EXPIRE);
    }

    public boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("nimbus:token:blacklist:" + token));
    }
}
```

------

### 5.2 文件元数据服务（nimbus-file）

**数据库设计：**

```sql
-- 文件表（按 user_id 分库，4库 × 16表）
CREATE TABLE `nimbus_file` (
    `id`              BIGINT       NOT NULL COMMENT '雪花ID',
    `user_id`         BIGINT       NOT NULL COMMENT '所属用户',
    `folder_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '所属文件夹(0=根目录)',
    `file_name`       VARCHAR(255) NOT NULL COMMENT '文件名',
    `file_ext`        VARCHAR(20)  DEFAULT NULL COMMENT '扩展名',
    `file_size`       BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小(bytes)',
    `file_hash`       VARCHAR(64)  NOT NULL COMMENT 'SHA-256',
    `storage_key`     VARCHAR(512) NOT NULL COMMENT 'MinIO对象Key',
    `mime_type`       VARCHAR(128) DEFAULT NULL COMMENT 'MIME类型',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=正常 2=回收站 3=已删除',
    `version`         INT          NOT NULL DEFAULT 1 COMMENT '当前版本号',
    `is_starred`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否收藏',
    `delete_time`     DATETIME     DEFAULT NULL COMMENT '删除时间(回收站)',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_folder` (`user_id`, `folder_id`),
    INDEX `idx_file_hash` (`file_hash`),
    INDEX `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件表';

-- 文件夹表
CREATE TABLE `nimbus_folder` (
    `id`              BIGINT       NOT NULL COMMENT '雪花ID',
    `user_id`         BIGINT       NOT NULL,
    `parent_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '父文件夹(0=根)',
    `folder_name`     VARCHAR(255) NOT NULL,
    `folder_path`     VARCHAR(1024) NOT NULL DEFAULT '/' COMMENT '物化路径 /a/b/c/',
    `depth`           INT          NOT NULL DEFAULT 1 COMMENT '层级深度',
    `status`          TINYINT      NOT NULL DEFAULT 1,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_parent` (`user_id`, `parent_id`),
    INDEX `idx_folder_path` (`user_id`, `folder_path`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件夹表';

-- 文件版本表
CREATE TABLE `nimbus_file_version` (
    `id`              BIGINT       NOT NULL,
    `file_id`         BIGINT       NOT NULL,
    `version_no`      INT          NOT NULL,
    `file_size`       BIGINT       NOT NULL,
    `file_hash`       VARCHAR(64)  NOT NULL,
    `storage_key`     VARCHAR(512) NOT NULL,
    `operator_id`     BIGINT       NOT NULL COMMENT '操作人',
    `remark`          VARCHAR(500) DEFAULT NULL,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_file_version` (`file_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件版本表';
```

**目录树查询（物化路径方案）：**

```java
@Service
public class FolderService {

    @Autowired
    private FolderMapper folderMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 获取目录树（缓存 + 懒加载）
     */
    public List<FolderTreeDTO> getFolderTree(Long userId) {
        String cacheKey = "nimbus:folder:tree:" + userId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseArray(cached, FolderTreeDTO.class);
        }

        List<FolderEntity> allFolders = folderMapper.selectList(
            new LambdaQueryWrapper<FolderEntity>()
                .eq(FolderEntity::getUserId, userId)
                .eq(FolderEntity::getStatus, 1)
                .orderByAsc(FolderEntity::getFolderName)
        );

        List<FolderTreeDTO> tree = buildTree(allFolders, 0L);

        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(tree), 30, TimeUnit.MINUTES);
        return tree;
    }

    /**
     * 获取面包屑路径
     */
    public List<BreadcrumbDTO> getBreadcrumb(Long userId, Long folderId) {
        FolderEntity folder = folderMapper.selectById(folderId);
        // 通过 folder_path 解析: /1/5/12/ → 逐级查询
        String[] ids = folder.getFolderPath().split("/");
        // 批量查询并组装
    }

    /**
     * 移动文件夹（更新物化路径）
     */
    @Transactional(rollbackFor = Exception.class)
    public void moveFolder(Long folderId, Long targetParentId) {
        FolderEntity folder = folderMapper.selectById(folderId);
        FolderEntity target = folderMapper.selectById(targetParentId);

        // 循环引用检测
        if (target.getFolderPath().startsWith(folder.getFolderPath())) {
            throw new BizException("不能将文件夹移动到其子目录下");
        }

        String oldPath = folder.getFolderPath();
        String newPath = target.getFolderPath() + folderId + "/";

        // 批量更新所有子路径
        folderMapper.updatePathPrefix(folder.getUserId(), oldPath, newPath);
    }
}
```

------

### 5.3 上传服务（nimbus-upload）— 核心链路

```java
@RestController
@RequestMapping("/upload")
public class UploadController {

    @Autowired
    private UploadService uploadService;

    /**
     * Step 1: 初始化上传（秒传检测 + 获取上传凭证）
     */
    @PostMapping("/init")
    public Result<UploadInitResponse> initUpload(@RequestBody @Valid UploadInitRequest req) {
        return Result.ok(uploadService.initUpload(req));
    }

    /**
     * Step 2: 获取分片上传预签名URL
     */
    @PostMapping("/chunk-url")
    public Result<List<ChunkUrlResponse>> getChunkUrls(@RequestBody ChunkUrlRequest req) {
        return Result.ok(uploadService.generateChunkUrls(req));
    }

    /**
     * Step 3: 合并分片
     */
    @PostMapping("/merge")
    public Result<FileDTO> mergeChunks(@RequestBody @Valid MergeRequest req) {
        return Result.ok(uploadService.mergeChunks(req));
    }
}
@Service
@Slf4j
public class UploadService {

    @Autowired private MinioClient minioClient;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private FileFeignClient fileFeignClient;
    @Autowired private QuotaFeignClient quotaFeignClient;
    @Autowired private RocketMQTemplate rocketMQTemplate;

    private static final String BUCKET = "nimbus-files";
    private static final int CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * 初始化上传
     */
    public UploadInitResponse initUpload(UploadInitRequest req) {
        // 1. 秒传检测：通过 SHA-256 查询是否已存在
        String existingKey = redisTemplate.opsForValue()
            .get("nimbus:file:hash:" + req.getFileHash());
        if (existingKey != null) {
            // 秒传成功，直接创建元数据引用
            return UploadInitResponse.instantUpload(existingKey);
        }

        // 2. 配额检查
        quotaFeignClient.checkQuota(req.getUserId(), req.getFileSize());

        // 3. 计算分片数
        int chunkCount = (int) Math.ceil((double) req.getFileSize() / CHUNK_SIZE);

        // 4. 生成上传任务ID
        String uploadId = UUID.randomUUID().toString().replace("-", "");

        // 5. 在 MinIO 初始化 Multipart Upload
        String objectKey = buildObjectKey(req.getUserId(), req.getFileName());
        CreateMultipartUploadResponse initResp = minioClient.createMultipartUpload(
            CreateMultipartUploadArgs.builder()
                .bucket(BUCKET)
                .object(objectKey)
                .build()
        );

        // 6. 缓存上传任务信息
        UploadTask task = new UploadTask(uploadId, initResp.uploadId(),
            objectKey, chunkCount, req);
        redisTemplate.opsForValue().set(
            "nimbus:upload:task:" + uploadId,
            JSON.toJSONString(task), 24, TimeUnit.HOURS);

        return UploadInitResponse.needUpload(uploadId, chunkCount, CHUNK_SIZE);
    }

    /**
     * 生成分片预签名URL（客户端直传MinIO，不经过业务服务器）
     */
    public List<ChunkUrlResponse> generateChunkUrls(ChunkUrlRequest req) {
        UploadTask task = getUploadTask(req.getUploadId());
        List<ChunkUrlResponse> urls = new ArrayList<>();

        for (int i = req.getStartChunk(); i <= req.getEndChunk(); i++) {
            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(BUCKET)
                    .object(task.getObjectKey())
                    .expiry(15, TimeUnit.MINUTES)
                    .extraQueryParams(Map.of(
                        "partNumber", String.valueOf(i + 1),
                        "uploadId", task.getMinioUploadId()))
                    .build()
            );
            urls.add(new ChunkUrlResponse(i, url));
        }
        return urls;
    }

    /**
     * 合并分片
     */
    @Transactional(rollbackFor = Exception.class)
    public FileDTO mergeChunks(MergeRequest req) {
        UploadTask task = getUploadTask(req.getUploadId());

        // 1. 校验所有分片是否上传完成
        List<Integer> uploadedChunks = getUploadedChunks(req.getUploadId());
        if (uploadedChunks.size() != task.getChunkCount()) {
            throw new BizException("分片未全部上传完成");
        }

        // 2. MinIO 合并分片
        minioClient.completeMultipartUpload(
            CompleteMultipartUploadArgs.builder()
                .bucket(BUCKET)
                .object(task.getObjectKey())
                .uploadId(task.getMinioUploadId())
                .parts(buildParts(uploadedChunks, req.getEtags()))
                .build()
        );

        // 3. 写入文件元数据
        FileDTO fileDTO = fileFeignClient.createFile(buildFileCreateReq(task));

        // 4. 发送异步事件
        rocketMQTemplate.syncSend("nimbus-file-uploaded",
            new FileUploadedEvent(fileDTO));

        // 5. 记录文件哈希（用于秒传）
        redisTemplate.opsForValue().set(
            "nimbus:file:hash:" + task.getFileHash(),
            task.getObjectKey());

        // 6. 清理上传任务
        redisTemplate.delete("nimbus:upload:task:" + req.getUploadId());

        return fileDTO;
    }

    private String buildObjectKey(Long userId, String fileName) {
        // 按用户ID散列 + 日期分层，避免热点
        return String.format("u/%d/%s/%s",
            userId % 1024,
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
            UUID.randomUUID() + "_" + fileName);
    }
}
```

**上传时序图：**

```
客户端                    Upload Service              MinIO              File Service
  │                           │                        │                     │
  │── 1.计算文件SHA-256 ──→  │                        │                     │
  │── 2.POST /upload/init ──→│                        │                     │
  │                           │── 3.秒传检查(Redis) ──→│                     │
  │                           │── 4.配额检查 ─────────→│                     │
  │                           │── 5.初始化分片上传 ───→│                     │
  │←── 6.返回uploadId+分片数─│                        │                     │
  │                           │                        │                     │
  │── 7.请求预签名URL ──────→│                        │                     │
  │←── 8.返回各分片URL ──────│                        │                     │
  │                           │                        │                     │
  │── 9.并发PUT分片 ─────────────────────────────────→│                     │
  │←── 10.各分片上传成功 ────────────────────────────│                     │
  │                           │                        │                     │
  │── 11.POST /upload/merge─→│                        │                     │
  │                           │── 12.CompleteMultipart→│                     │
  │                           │── 13.写入元数据 ──────────────────────────→│
  │                           │── 14.发送MQ事件 ──────→│                     │
  │←── 15.返回文件信息 ──────│                        │                     │
```

------

### 5.4 下载服务（nimbus-download）

```java
@Service
public class DownloadService {

    @Autowired private MinioClient minioClient;
    @Autowired private FileFeignClient fileFeignClient;
    @Autowired private StringRedisTemplate redisTemplate;

    /**
     * 生成临时下载链接
     */
    public DownloadUrlResponse generateDownloadUrl(Long fileId, Long userId) {
        FileDTO file = fileFeignClient.getFile(fileId, userId);

        // 生成预签名下载URL（有效期10分钟）
        String url = minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket("nimbus-files")
                .object(file.getStorageKey())
                .expiry(10, TimeUnit.MINUTES)
                .extraQueryParams(Map.of(
                    "response-content-disposition",
                    "attachment; filename=\"" + URLEncoder.encode(file.getFileName(), UTF_8) + "\""))
                .build()
        );

        // 记录下载日志
        rocketMQTemplate.syncSend("nimbus-file-download",
            new FileDownloadEvent(fileId, userId));

        return new DownloadUrlResponse(url, file.getFileName(), file.getFileSize());
    }

    /**
     * 批量下载（打包ZIP）
     */
    public String batchDownload(List<Long> fileIds, Long userId) {
        String taskId = UUID.randomUUID().toString();

        // 异步打包任务
        rocketMQTemplate.syncSend("nimbus-batch-download",
            new BatchDownloadEvent(taskId, fileIds, userId));

        return taskId; // 客户端轮询任务状态
    }
}
```

------

### 5.5 分享服务（nimbus-share）

```java
@Service
public class ShareService {

    @Autowired private ShareMapper shareMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private SnowflakeIdGenerator idGenerator;

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**
     * 创建分享
     */
    public ShareDTO createShare(ShareCreateRequest req) {
        Long shareId = idGenerator.nextId();
        String shortCode = generateShortCode(shareId);

        ShareEntity share = new ShareEntity();
        share.setId(shareId);
        share.setUserId(req.getUserId());
        share.setFileIds(req.getFileIds());
        share.setShortCode(shortCode);
        share.setShareType(req.getShareType());     // PUBLIC / PASSWORD / SPECIFIED
        share.setPassword(req.getPassword());
        share.setPermission(req.getPermission());   // VIEW / DOWNLOAD / SAVE
        share.setExpireTime(calcExpireTime(req.getExpireType()));
        share.setMaxDownloadCount(req.getMaxDownloadCount());

        shareMapper.insert(share);

        // 短链映射缓存
        redisTemplate.opsForValue().set(
            "nimbus:share:" + shortCode,
            JSON.toJSONString(share),
            calcTtl(req.getExpireType()), TimeUnit.SECONDS);

        return convertToDTO(share);
    }

    /**
     * 访问分享（校验 + 统计）
     */
    public ShareAccessResponse accessShare(String shortCode, String password) {
        String cached = redisTemplate.opsForValue().get("nimbus:share:" + shortCode);
        ShareEntity share = cached != null ?
            JSON.parseObject(cached, ShareEntity.class) :
            shareMapper.selectByShortCode(shortCode);

        if (share == null) throw new BizException("分享不存在或已取消");
        if (share.getExpireTime() != null && share.getExpireTime().before(new Date()))
            throw new BizException("分享已过期");
        if (share.getShareType() == ShareType.PASSWORD &&
            !share.getPassword().equals(password))
            throw new BizException("提取码错误");

        // 浏览计数 +1
        redisTemplate.opsForValue().increment("nimbus:share:view:" + shortCode);

        return buildAccessResponse(share);
    }

    private String generateShortCode(Long id) {
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(BASE62.charAt((int)(id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }
}
```

------

### 5.6 搜索服务（nimbus-search）

```java
@Service
public class SearchService {

    @Autowired private ElasticsearchClient esClient;
    @Autowired private FileFeignClient fileFeignClient;

    /**
     * 文件搜索
     */
    public PageResult<FileSearchDTO> search(FileSearchRequest req) throws IOException {
        SearchResponse<FileSearchDoc> response = esClient.search(s -> s
            .index("nimbus_file_index")
            .query(q -> q
                .bool(b -> {
                    // 文件名模糊搜索
                    if (StringUtils.isNotBlank(req.getKeyword())) {
                        b.must(m -> m
                            .multiMatch(mm -> mm
                                .query(req.getKeyword())
                                .fields("fileName^3", "fileNamePinyin", "tags")
                                .type(TextQueryType.BestFields)
                                .fuzziness("AUTO")
                            )
                        );
                    }
                    // 限定用户
                    b.filter(f -> f.term(t -> t.field("userId").value(req.getUserId())));
                    // 文件类型过滤
                    if (req.getFileType() != null) {
                        b.filter(f -> f.term(t -> t.field("fileExt").value(req.getFileType())));
                    }
                    // 时间范围
                    if (req.getStartTime() != null) {
                        b.filter(f -> f.range(r -> r
                            .field("createTime")
                            .gte(JsonData.of(req.getStartTime()))));
                    }
                    return b;
                })
            )
            .highlight(h -> h
                .fields("fileName", hf -> hf
                    .preTags("<em>")
                    .postTags("</em>")))
            .from((req.getPage() - 1) * req.getSize())
            .size(req.getSize())
            .sort(sort -> sort.field(f -> f.field("_score").order(SortOrder.Desc))),
            FileSearchDoc.class
        );

        return convertToPageResult(response, req);
    }
}

/**
 * 数据同步：监听 RocketMQ 事件，更新 ES 索引
 */
@Component
@RocketMQMessageListener(
    topic = "nimbus-file-uploaded",
    consumerGroup = "search-consumer-group"
)
public class FileIndexConsumer implements RocketMQListener<FileUploadedEvent> {

    @Autowired private ElasticsearchClient esClient;

    @Override
    public void onMessage(FileUploadedEvent event) {
        try {
            FileSearchDoc doc = buildSearchDoc(event);
            esClient.index(i -> i
                .index("nimbus_file_index")
                .id(String.valueOf(event.getFileId()))
                .document(doc));
        } catch (IOException e) {
            log.error("ES索引更新失败", e);
            // 重试或进入死信队列
        }
    }
}
```

------

### 5.7 预览服务（nimbus-preview）

```java
@Service
public class PreviewService {

    @Autowired private MinioClient minioClient;
    @Autowired private RocketMQTemplate rocketMQTemplate;
    @Autowired private StringRedisTemplate redisTemplate;

    /**
     * 获取预览信息
     */
    public PreviewResponse getPreview(Long fileId, Long userId) {
        FileDTO file = fileFeignClient.getFile(fileId, userId);
        String ext = file.getFileExt().toLowerCase();

        return switch (getFileCategory(ext)) {
            case IMAGE -> buildImagePreview(file);     // 缩略图 + 原图
            case VIDEO -> buildVideoPreview(file);     // HLS 流地址
            case AUDIO -> buildAudioPreview(file);     // 流式播放
            case DOCUMENT -> buildDocPreview(file);    // 转PDF预览
            case CODE -> buildCodePreview(file);       // 语法高亮
            default -> PreviewResponse.notSupported();
        };
    }

    /**
     * 图片缩略图（多尺寸）
     */
    private PreviewResponse buildImagePreview(FileDTO file) {
        String baseKey = file.getStorageKey();
        return PreviewResponse.builder()
            .type("IMAGE")
            .originalUrl(getPresignedUrl(baseKey))
            .thumbnails(Map.of(
                "small", getPresignedUrl(baseKey + "_thumb_200"),
                "medium", getPresignedUrl(baseKey + "_thumb_800"),
                "large", getPresignedUrl(baseKey + "_thumb_1920")
            ))
            .build();
    }

    /**
     * 视频预览（HLS）
     */
    private PreviewResponse buildVideoPreview(FileDTO file) {
        String hlsKey = file.getStorageKey().replaceAll("\\.[^.]+$", "") + "/index.m3u8";
        return PreviewResponse.builder()
            .type("VIDEO")
            .hlsUrl(getPresignedUrl(hlsKey))
            .build();
    }
}

/**
 * 异步转码 Worker（消费 MQ 消息）
 */
@Component
@RocketMQMessageListener(topic = "nimbus-file-uploaded", consumerGroup = "preview-worker-group")
public class PreviewWorker implements RocketMQListener<FileUploadedEvent> {

    @Override
    public void onMessage(FileUploadedEvent event) {
        String ext = event.getFileExt().toLowerCase();

        if (isImage(ext)) {
            generateThumbnails(event);       // 生成 200/800/1920 缩略图
        } else if (isVideo(ext)) {
            transcodeToHls(event);           // FFmpeg 转码为 HLS
        } else if (isDocument(ext)) {
            convertToPdf(event);             // LibreOffice 转 PDF
        }
    }

    private void generateThumbnails(FileUploadedEvent event) {
        // 使用 Thumbnailator 库生成缩略图
        // 上传回 MinIO 对应路径
    }

    private void transcodeToHls(FileUploadedEvent event) {
        // 调用 FFmpeg:
        // ffmpeg -i input.mp4 -c:v libx264 -c:a aac
        //        -hls_time 10 -hls_list_size 0 -f hls output.m3u8
    }
}
```

------

### 5.8 配额管理服务（nimbus-quota）

```java
@Service
public class QuotaService {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private QuotaMapper quotaMapper;

    /**
     * 检查配额（上传前调用）
     */
    public void checkQuota(Long userId, long fileSize) {
        String usedKey = "nimbus:quota:used:" + userId;
        String totalKey = "nimbus:quota:total:" + userId;

        Long used = Long.parseLong(
            Optional.ofNullable(redisTemplate.opsForValue().get(usedKey)).orElse("0"));
        Long total = Long.parseLong(
            Optional.ofNullable(redisTemplate.opsForValue().get(totalKey)).orElse("0"));

        if (total == 0) {
            // 缓存未命中，从DB加载
            QuotaEntity quota = quotaMapper.selectByUserId(userId);
            used = quota.getUsedSize();
            total = quota.getTotalSize();
            redisTemplate.opsForValue().set(usedKey, String.valueOf(used));
            redisTemplate.opsForValue().set(totalKey, String.valueOf(total));
        }

        if (used + fileSize > total) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED,
                "存储空间不足，请升级或清理文件");
        }
    }

    /**
     * 增加用量（上传成功后）
     */
    public void increaseUsage(Long userId, long fileSize) {
        redisTemplate.opsForValue().increment("nimbus:quota:used:" + userId, fileSize);
        // 异步持久化到DB
        rocketMQTemplate.syncSend("nimbus-quota-update",
            new QuotaUpdateEvent(userId, fileSize));
    }

    /**
     * 减少用量（删除文件后）
     */
    public void decreaseUsage(Long userId, long fileSize) {
        redisTemplate.opsForValue().decrement("nimbus:quota:used:" + userId, fileSize);
    }
}
```

------

### 5.9 审计日志服务（nimbus-audit）

```java
/**
 * 操作日志注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String module();
    OperationType operation();
    String description() default "";
}

/**
 * AOP 切面：自动采集操作日志
 */
@Aspect
@Component
public class AuditLogAspect {

    @Autowired private RocketMQTemplate rocketMQTemplate;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint point, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception error = null;

        try {
            result = point.proceed();
            return result;
        } catch (Exception e) {
            error = e;
            throw e;
        } finally {
            // 异步发送日志事件
            AuditEvent event = AuditEvent.builder()
                .userId(SecurityUtils.getUserId())
                .module(auditLog.module())
                .operation(auditLog.operation())
                .description(auditLog.description())
                .ip(RequestUtils.getClientIp())
                .userAgent(RequestUtils.getUserAgent())
                .duration(System.currentTimeMillis() - startTime)
                .success(error == null)
                .errorMsg(error != null ? error.getMessage() : null)
                .timestamp(LocalDateTime.now())
                .build();

            rocketMQTemplate.asyncSend("nimbus-audit-log", event, new SendCallback() {
                @Override public void onSuccess(SendResult r) {}
                @Override public void onException(Throwable e) {
                    log.warn("审计日志发送失败", e);
                }
            });
        }
    }
}

/**
 * 审计日志消费者 → 写入 ClickHouse
 */
@Component
@RocketMQMessageListener(topic = "nimbus-audit-log", consumerGroup = "audit-consumer")
public class AuditLogConsumer implements RocketMQListener<AuditEvent> {

    @Autowired private ClickHouseJdbcTemplate clickHouseTemplate;

    @Override
    public void onMessage(AuditEvent event) {
        clickHouseTemplate.update(
            "INSERT INTO nimbus_audit_log VALUES (?,?,?,?,?,?,?,?,?,?)",
            event.getUserId(), event.getModule(), event.getOperation().name(),
            event.getDescription(), event.getIp(), event.getUserAgent(),
            event.getDuration(), event.isSuccess(), event.getErrorMsg(),
            event.getTimestamp()
        );
    }
}
```

------

## 六、网关层核心配置

```java
/**
 * Spring Cloud Gateway 全局鉴权过滤器
 */
@Component
@Order(-1)
public class AuthGlobalFilter implements GlobalFilter {

    @Autowired private StringRedisTemplate redisTemplate;

    private static final List<String> WHITE_LIST = List.of(
        "/auth/login", "/auth/register", "/auth/refresh",
        "/share/access", "/health", "/actuator/**"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 提取 Token
        String token = extractToken(exchange.getRequest());
        if (token == null) {
            return unauthorized(exchange, "缺少认证令牌");
        }

        // 检查黑名单
        if (Boolean.TRUE.equals(redisTemplate.hasKey("nimbus:token:blacklist:" + token))) {
            return unauthorized(exchange, "令牌已失效");
        }

        // 解析 JWT
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            // 将用户信息注入下游 Header
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Roles", claims.get("roles", String.class))
                .header("X-Trace-Id", generateTraceId())
                .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, "令牌已过期");
        } catch (JwtException e) {
            return unauthorized(exchange, "令牌无效");
        }
    }
}
```

**Gateway 路由配置（Nacos 动态路由）：**

```yaml
# nacos-config: nimbus-gateway.yaml
spring:
  cloud:
    gateway:
      routes:
        - id: nimbus-user
          uri: lb://nimbus-user
          predicates:
            - Path=/api/user/**
          filters:
            - StripPrefix=2
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200

        - id: nimbus-file
          uri: lb://nimbus-file
          predicates:
            - Path=/api/file/**,/api/folder/**
          filters:
            - StripPrefix=2

        - id: nimbus-upload
          uri: lb://nimbus-upload
          predicates:
            - Path=/api/upload/**
          filters:
            - StripPrefix=2
          metadata:
            connect-timeout: 5000
            response-timeout: 300s    # 上传超时放宽

        - id: nimbus-share
          uri: lb://nimbus-share
          predicates:
            - Path=/api/share/**,/s/**
          filters:
            - StripPrefix=1

        - id: nimbus-search
          uri: lb://nimbus-search
          predicates:
            - Path=/api/search/**
          filters:
            - StripPrefix=2
```

------

## 七、高可用保障策略

### 7.1 多级容错设计

```
┌─────────────────────────────────────────────────────────────────────┐
│                        高可用保障全景                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  【接入层】                                                         │
│   ├── Nginx 双主 + Keepalived (VIP 漂移)                           │
│   ├── 多可用区 SLB 负载均衡                                         │
│   └── CDN 静态资源加速 + 回源容灾                                   │
│                                                                     │
│  【网关层】                                                         │
│   ├── Gateway 多副本 (≥3)，无状态水平扩展                           │
│   ├── Sentinel 网关限流（QPS/并发/热点参数）                        │
│   └── 熔断降级：下游不可用时返回友好提示                             │
│                                                                     │
│  【服务层】                                                         │
│   ├── 每个微服务 ≥ 3 副本，跨 AZ 部署                               │
│   ├── Sentinel 服务级熔断（慢调用比例/异常比例）                     │
│   ├── OpenFeign 超时 + 重试（幂等接口）                             │
│   ├── 线程池隔离（Bulkhead 模式）                                   │
│   └── 优雅停机：preStop Hook + 30s 排水期                           │
│                                                                     │
│  【数据层】                                                         │
│   ├── MySQL：1主2从 + 半同步复制 + 自动故障转移 (MHA/Orchestrator) │
│   ├── Redis：6节点 Cluster (3主3从)，跨AZ                          │
│   ├── RocketMQ：DLedger 模式，3 Broker 自动选主                     │
│   ├── Elasticsearch：3主3副，跨AZ                                   │
│   └── MinIO：分布式模式 8节点，纠删码 EC:4+2                        │
│                                                                     │
│  【全局】                                                           │
│   ├── K8s HPA：CPU > 70% 自动扩容                                  │
│   ├── PDB：保证滚动更新时最少可用副本数                             │
│   ├── 混沌工程：定期 ChaosBlade 故障注入演练                        │
│   └── 异地容灾：核心数据异步复制到备用机房                           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 7.2 Sentinel 限流熔断规则

```java
@Configuration
public class SentinelConfig {

    @PostConstruct
    public void initRules() {
        // 网关限流规则
        List<FlowRule> flowRules = new ArrayList<>();

        // 上传接口：单机 200 QPS
        flowRules.add(new FlowRule("nimbus-upload:/upload/init")
            .setCount(200).setGrade(RuleConstant.FLOW_GRADE_QPS));

        // 下载接口：单机 500 QPS
        flowRules.add(new FlowRule("nimbus-download:/download/url")
            .setCount(500).setGrade(RuleConstant.FLOW_GRADE_QPS));

        // 搜索接口：单机 300 QPS
        flowRules.add(new FlowRule("nimbus-search:/search")
            .setCount(300).setGrade(RuleConstant.FLOW_GRADE_QPS));

        FlowRuleManager.loadRules(flowRules);

        // 熔断规则
        List<DegradeRule> degradeRules = new ArrayList<>();

        // 文件服务：慢调用比例 > 50%，熔断 10s
        degradeRules.add(new DegradeRule("nimbus-file:getFile")
            .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
            .setCount(0.5)
            .setSlowRatioThreshold(1000)  // 1s 算慢调用
            .setTimeWindow(10)
            .setMinRequestAmount(10));

        DegradeRuleManager.loadRules(degradeRules);
    }
}
```

### 7.3 分布式事务（Seata）

```java
/**
 * 文件删除场景：需要同时更新元数据 + 释放配额 + 移入回收站
 * 使用 Seata AT 模式保证一致性
 */
@Service
public class FileDeleteService {

    @Autowired private FileMapper fileMapper;
    @Autowired private QuotaFeignClient quotaFeignClient;
    @Autowired private RocketMQTemplate rocketMQTemplate;

    @GlobalTransactional(rollbackFor = Exception.class)
    public void deleteFile(Long fileId, Long userId) {
        // 1. 更新文件状态为回收站
        FileEntity file = fileMapper.selectById(fileId);
        file.setStatus(FileStatus.RECYCLED);
        file.setDeleteTime(LocalDateTime.now());
        fileMapper.updateById(file);

        // 2. 释放配额（Feign 远程调用，Seata 自动管理）
        quotaFeignClient.decreaseUsage(userId, file.getFileSize());

        // 3. 异步事件：更新搜索索引、记录审计日志
        rocketMQTemplate.syncSend("nimbus-file-deleted",
            new FileDeletedEvent(fileId, userId, file.getFileSize()));
    }
}
```

------

## 八、可观测性体系

### 8.1 SkyWalking 链路追踪集成

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-logback-1.x</artifactId>
    <version>9.1.0</version>
</dependency>
# JVM 启动参数
-javaagent:/opt/skywalking/agent/skywalking-agent.jar
-Dskywalking.agent.service_name=nimbus-file-service
-Dskywalking.collector.backend_service=skywalking-oap:11800
```

### 8.2 Prometheus 自定义指标

```java
@Component
public class UploadMetrics {

    private final Counter uploadSuccessCounter;
    private final Counter uploadFailCounter;
    private final Histogram uploadDurationHistogram;
    private final Gauge activeUploadGauge;

    public UploadMetrics(MeterRegistry registry) {
        this.uploadSuccessCounter = Counter.builder("nimbus.upload.success.total")
            .description("上传成功总数").register(registry);
        this.uploadFailCounter = Counter.builder("nimbus.upload.fail.total")
            .description("上传失败总数").register(registry);
        this.uploadDurationHistogram = Histogram.builder("nimbus.upload.duration")
            .description("上传耗时分布")
            .buckets(0.5, 1, 2, 5, 10, 30, 60)
            .register(registry);
        this.activeUploadGauge = Gauge.builder("nimbus.upload.active", () -> activeCount)
            .description("当前活跃上传数").register(registry);
    }
}
```

### 8.3 Grafana 告警规则

```yaml
# alertmanager-rules.yaml
groups:
  - name: nimbus-alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 10
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "5xx 错误率过高"

      - alert: UploadServiceDown
        expr: up{job="nimbus-upload"} == 0
        for: 30s
        labels:
          severity: critical

      - alert: RedisMemoryHigh
        expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.9
        for: 5m
        labels:
          severity: warning

      - alert: DiskUsageHigh
        expr: node_filesystem_avail_bytes{mountpoint="/data"} / node_filesystem_size_bytes < 0.1
        for: 5m
        labels:
          severity: critical
```

------

## 九、Kubernetes 部署方案

### 9.1 Deployment 示例

```yaml
# nimbus-file-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nimbus-file
  namespace: nimbus
  labels:
    app: nimbus-file
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0    # 保证零停机
  selector:
    matchLabels:
      app: nimbus-file
  template:
    metadata:
      labels:
        app: nimbus-file
    spec:
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchLabels:
                    app: nimbus-file
                topologyKey: kubernetes.io/hostname
      containers:
        - name: nimbus-file
          image: harbor.nimbus.com/nimbus/nimbus-file:2.1.0
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: NACOS_SERVER_ADDR
              valueFrom:
                configMapKeyRef:
                  name: nimbus-config
                  key: nacos-addr
          resources:
            requests:
              cpu: "500m"
              memory: "512Mi"
            limits:
              cpu: "2000m"
              memory: "1Gi"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 15
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 15"]  # 优雅停机
      terminationGracePeriodSeconds: 45
```

### 9.2 HPA 自动扩缩容

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: nimbus-upload-hpa
  namespace: nimbus
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: nimbus-upload
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Pods
          value: 2
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
```

------

## 十、CI/CD 流水线

```yaml
# .gitlab-ci.yml
stages:
  - build
  - test
  - docker
  - deploy

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=.m2/repository"
  HARBOR_REGISTRY: "harbor.nimbus.com"

build:
  stage: build
  image: maven:3.9-eclipse-temurin-21
  script:
    - mvn clean package -DskipTests -pl nimbus-${SERVICE_NAME} -am
  artifacts:
    paths:
      - nimbus-${SERVICE_NAME}/target/*.jar

unit-test:
  stage: test
  image: maven:3.9-eclipse-temurin-21
  script:
    - mvn test -pl nimbus-${SERVICE_NAME}
    - mvn jacoco:report
  coverage: '/Total.*?([0-9]{1,3})%/'

docker-build:
  stage: docker
  script:
    - docker build -t ${HARBOR_REGISTRY}/nimbus/nimbus-${SERVICE_NAME}:${CI_COMMIT_TAG} .
    - docker push ${HARBOR_REGISTRY}/nimbus/nimbus-${SERVICE_NAME}:${CI_COMMIT_TAG}

deploy-staging:
  stage: deploy
  script:
    - kubectl set image deployment/nimbus-${SERVICE_NAME} \
        nimbus-${SERVICE_NAME}=${HARBOR_REGISTRY}/nimbus/nimbus-${SERVICE_NAME}:${CI_COMMIT_TAG} \
        -n nimbus-staging
  environment:
    name: staging

deploy-production:
  stage: deploy
  script:
    - kubectl set image deployment/nimbus-${SERVICE_NAME} \
        nimbus-${SERVICE_NAME}=${HARBOR_REGISTRY}/nimbus/nimbus-${SERVICE_NAME}:${CI_COMMIT_TAG} \
        -n nimbus
  environment:
    name: production
  when: manual    # 生产环境手动确认
```

------

## 十一、容量规划（百万用户级）

| 资源          | 规格            | 数量     | 说明                |
| ------------- | --------------- | -------- | ------------------- |
| K8s Master    | 4C 16G          | 3        | 跨 AZ               |
| K8s Worker    | 8C 32G          | 12       | 跨 3 AZ，每 AZ 4 台 |
| MySQL         | 8C 32G 500G SSD | 1主2从   | ShardingSphere 4库  |
| Redis         | 8C 32G          | 6 节点   | Cluster 3主3从      |
| RocketMQ      | 4C 16G 1T       | 3 Broker | DLedger 模式        |
| Elasticsearch | 8C 32G 1T SSD   | 6 节点   | 3主3副              |
| MinIO         | 4C 16G 4×4T HDD | 8 节点   | EC:4+2，跨 AZ       |
| ClickHouse    | 8C 32G 2T       | 3 节点   | 审计日志分析        |
| Nacos         | 4C 8G           | 3 节点   | 集群模式            |
| XXL-Job       | 2C 4G           | 2 节点   | 主备                |
| SkyWalking    | 4C 16G          | 3 节点   | OAP + ES + UI       |
| CDN           | —               | 按量     | 峰值 10Gbps         |

------

## 十二、核心文件上传完整链路（端到端）

```
用户点击上传
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ [客户端]                                                         │
│  1. 文件切片（5MB/片）                                          │
│  2. 计算整体 SHA-256                                            │
│  3. 请求 /upload/init                                           │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ [Gateway] 鉴权 → 限流 → 路由到 nimbus-upload                    │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ [Upload Service]                                                 │
│  4. Redis 查 SHA-256 → 命中则秒传返回                           │
│  5. 未命中 → 调 Quota Service 检查配额                          │
│  6. 调 MinIO 初始化 MultipartUpload                             │
│  7. 返回 uploadId + 分片数                                      │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ [客户端] 并发请求预签名URL → 直传分片到 MinIO                    │
│  （不经过业务服务器，带宽零消耗）                                 │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ [Upload Service] /upload/merge                                   │
│  8. 校验分片完整性                                              │
│  9. MinIO CompleteMultipartUpload                                │
│  10. 调 File Service 写入元数据 (Seata 分布式事务)               │
│  11. 调 Quota Service 累加用量                                  │
│  12. 发送 RocketMQ 事件                                         │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                    ┌──────────┼──────────┬──────────────┐
                    ▼          ▼          ▼              ▼
              ┌─────────┐ ┌────────┐ ┌────────┐  ┌──────────┐
              │ Search  │ │Preview │ │ Audit  │  │  Notify  │
              │ ES索引  │ │缩略图  │ │ 日志   │  │ 上传通知 │
              └─────────┘ └────────┘ └────────┘  └──────────┘
```

------

## 十三、总结

本方案基于 **纯 Java 技术栈（Spring Boot 3 + Spring Cloud Alibaba）** 构建，具备以下核心能力：

| 维度         | 能力                                      |
| ------------ | ----------------------------------------- |
| **高可用**   | 多副本 + 跨AZ + 自动故障转移 + 熔断降级   |
| **高性能**   | 客户端直传对象存储 + Redis缓存 + CDN加速  |
| **高扩展**   | 微服务独立扩缩容 + ShardingSphere分库分表 |
| **安全**     | JWT + OAuth2 + RBAC + WAF + 审计日志      |
| **可观测**   | SkyWalking + Prometheus + ELK 三位一体    |
| **持续交付** | GitLab CI + Harbor + K8s 滚动更新         |
| **容灾**     | 数据多副本 + 异地备份 + 混沌工程演练      |

该架构可支撑 **百万级用户、PB 级文件存储、万级并发上传**，并可根据业务增长弹性扩展。