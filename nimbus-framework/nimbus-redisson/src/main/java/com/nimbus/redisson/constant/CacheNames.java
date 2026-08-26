package com.nimbus.redisson.constant;

/**
 * Spring Cache 缓存名常量, ttl 在 CacheAutoConfiguration 统一约定
 */
public interface CacheNames {

    /** 系统参数(按参数键), ttl 60min */
    String SYS_CONFIG = "sys_config";

    /** 用户 id -> 昵称(数据翻译), ttl 30min */
    String SYS_USER_NAME = "sys_user_name";

    /** 文件哈希 -> 存储 key(秒传), ttl 30min */
    String NETDISK_FILE_HASH = "netdisk_file_hash";

    /** 分享短码 -> 分享信息, ttl 由分享有效期决定 */
    String NETDISK_SHARE = "netdisk_share";

    /** 用户配额(used/total), ttl 60s */
    String NETDISK_QUOTA = "netdisk_quota";
}