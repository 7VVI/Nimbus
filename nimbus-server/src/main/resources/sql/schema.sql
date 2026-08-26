-- nimbus-cloud 建表脚本, 由 spring.sql.init 启动时执行, 需保持幂等
CREATE TABLE IF NOT EXISTS nimbus_user (
    id          BIGINT       NOT NULL COMMENT '用户id',
    username    VARCHAR(30)  NOT NULL COMMENT '登录账号',
    nickname    VARCHAR(30)  NULL COMMENT '用户昵称',
    password    VARCHAR(100) NULL COMMENT '密码(BCrypt)',
    email       VARCHAR(64)  NULL COMMENT '邮箱',
    phone       VARCHAR(20)  NULL COMMENT '手机号',
    avatar      VARCHAR(255) NULL COMMENT '头像地址',
    role_key    VARCHAR(30)  NOT NULL DEFAULT 'netdisk' COMMENT '角色权限字符串',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1正常 0停用',
    login_ip    VARCHAR(128) NULL COMMENT '最后登录IP',
    login_date  DATETIME     NULL COMMENT '最后登录时间',
    remark      VARCHAR(500) NULL COMMENT '备注',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_time DATETIME     NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (id),
    KEY idx_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS nimbus_quota (
    id          BIGINT       NOT NULL COMMENT '配额id',
    user_id     BIGINT       NOT NULL COMMENT '用户id',
    total_size  BIGINT       NOT NULL DEFAULT 137438953472 COMMENT '总容量(bytes), 默认128GB',
    used_size   BIGINT       NOT NULL DEFAULT 0 COMMENT '已用容量(bytes)',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_time DATETIME     NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (id),
    KEY idx_quota_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户存储配额表';

CREATE TABLE IF NOT EXISTS nimbus_oper_log (
    id            BIGINT       NOT NULL COMMENT '日志id',
    title         VARCHAR(50)  NULL COMMENT '操作模块标题',
    business_type VARCHAR(32)  NULL COMMENT '业务操作类型',
    method        VARCHAR(200) NULL COMMENT '操作方法',
    request_method VARCHAR(16) NULL COMMENT '请求方式',
    oper_url      VARCHAR(255) NULL COMMENT '请求地址',
    oper_name     VARCHAR(30)  NULL COMMENT '操作人账号',
    oper_user_id  BIGINT       NULL COMMENT '操作人用户id',
    oper_ip       VARCHAR(128) NULL COMMENT '操作人IP',
    oper_param    TEXT         NULL COMMENT '请求参数',
    json_result   TEXT         NULL COMMENT '响应结果',
    status        TINYINT      NULL COMMENT '状态: 0成功 1失败',
    error_msg     VARCHAR(2000) NULL COMMENT '错误信息',
    oper_time     DATETIME     NULL COMMENT '操作时间',
    cost_time     BIGINT       NULL COMMENT '耗时(毫秒)',
    create_time   DATETIME     NULL COMMENT '创建时间',
    update_time   DATETIME     NULL COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (id),
    KEY idx_operlog_time (oper_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作审计日志表';

CREATE TABLE IF NOT EXISTS nimbus_folder (
    id          BIGINT        NOT NULL COMMENT '文件夹id',
    user_id     BIGINT        NOT NULL COMMENT '所属用户',
    parent_id   BIGINT        NOT NULL DEFAULT 0 COMMENT '父文件夹id, 根为0',
    folder_name VARCHAR(255)  NOT NULL COMMENT '文件夹名称',
    folder_path VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '物化路径, 如 /1/5/',
    depth       INT           NOT NULL DEFAULT 1 COMMENT '层级深度',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态: 1正常 2回收站',
    delete_time DATETIME      NULL COMMENT '删除时间(回收站)',
    create_time DATETIME      NULL COMMENT '创建时间',
    update_time DATETIME      NULL COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (id),
    KEY idx_folder_user_parent (user_id, parent_id),
    KEY idx_folder_user_path (user_id, folder_path(255))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件夹表';

CREATE TABLE IF NOT EXISTS nimbus_file (
    id          BIGINT       NOT NULL COMMENT '文件id',
    user_id     BIGINT       NOT NULL COMMENT '所属用户',
    folder_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '所属文件夹, 根为0',
    file_name   VARCHAR(255) NOT NULL COMMENT '文件名(含扩展名)',
    file_ext    VARCHAR(20)  NULL COMMENT '扩展名(小写, 不含点)',
    file_size   BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小(bytes)',
    file_hash   VARCHAR(64)  NOT NULL COMMENT '内容SHA-256',
    storage_key VARCHAR(512) NOT NULL COMMENT '存储对象key',
    mime_type   VARCHAR(128) NULL COMMENT 'MIME类型',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1正常 2回收站 3已彻底删除',
    is_starred  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否收藏: 0否 1是',
    version     INT          NOT NULL DEFAULT 1 COMMENT '当前版本号',
    delete_time DATETIME     NULL COMMENT '删除时间(回收站)',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_time DATETIME     NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (id),
    KEY idx_file_user_folder (user_id, folder_id),
    KEY idx_file_user_hash (user_id, file_hash),
    KEY idx_file_user_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件表';

CREATE TABLE IF NOT EXISTS nimbus_file_version (
    id          BIGINT       NOT NULL COMMENT '版本id',
    file_id     BIGINT       NOT NULL COMMENT '文件id',
    version_no  INT          NOT NULL COMMENT '版本号',
    file_size   BIGINT       NOT NULL COMMENT '文件大小(bytes)',
    file_hash   VARCHAR(64)  NOT NULL COMMENT '内容SHA-256',
    storage_key VARCHAR(512) NOT NULL COMMENT '存储对象key',
    operator_id BIGINT       NOT NULL COMMENT '操作人用户id',
    remark      VARCHAR(500) NULL COMMENT '版本备注',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_time DATETIME     NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (id),
    KEY idx_version_file (file_id, version_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件版本表';

CREATE TABLE IF NOT EXISTS nimbus_share (
    id          BIGINT       NOT NULL COMMENT '分享id',
    user_id     BIGINT       NOT NULL COMMENT '分享人用户id',
    short_code  VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '短链码(插入后按主键生成)',
    share_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '分享类型: 1公开 2密码',
    password    VARCHAR(32)  NULL COMMENT '提取码',
    permission  TINYINT      NOT NULL DEFAULT 7 COMMENT '权限位掩码: 1可预览 2可下载 4可转存(可组合, 默认全选)',
    expire_type TINYINT      NOT NULL DEFAULT 1 COMMENT '有效期类型: 1永久 2按天数',
    expire_time DATETIME     NULL COMMENT '过期时间, 永久为null',
    view_count  INT          NOT NULL DEFAULT 0 COMMENT '浏览次数',
    save_count  INT          NOT NULL DEFAULT 0 COMMENT '转存次数',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1有效 0已取消',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_time DATETIME     NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (id),
    KEY idx_share_code (short_code),
    KEY idx_share_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '分享表';

CREATE TABLE IF NOT EXISTS nimbus_share_item (
    id          BIGINT  NOT NULL COMMENT '关联id',
    share_id    BIGINT  NOT NULL COMMENT '分享id',
    target_type TINYINT NOT NULL COMMENT '目标类型: 1文件 2文件夹',
    target_id   BIGINT  NOT NULL COMMENT '目标id',
    create_time DATETIME NULL COMMENT '创建时间',
    update_time DATETIME NULL COMMENT '更新时间',
    deleted     TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
    PRIMARY KEY (id),
    KEY idx_share_item_share (share_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '分享目标关联表';