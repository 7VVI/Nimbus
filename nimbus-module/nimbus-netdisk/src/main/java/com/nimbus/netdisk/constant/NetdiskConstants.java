package com.nimbus.netdisk.constant;

/**
 * 网盘业务常量
 */
public final class NetdiskConstants {

    private NetdiskConstants() {
    }

    /** 根目录文件夹 id */
    public static final long ROOT_FOLDER_ID = 0L;

    /** 文件夹状态: 正常 */
    public static final int FOLDER_STATUS_NORMAL = 1;
    /** 文件夹状态: 回收站 */
    public static final int FOLDER_STATUS_RECYCLED = 2;

    /** 文件状态: 正常 */
    public static final int FILE_STATUS_NORMAL = 1;
    /** 文件状态: 回收站 */
    public static final int FILE_STATUS_RECYCLED = 2;
    /** 文件状态: 已彻底删除 */
    public static final int FILE_STATUS_DELETED = 3;

    /** 收藏: 否 */
    public static final int STAR_NO = 0;
    /** 收藏: 是 */
    public static final int STAR_YES = 1;

    /** 分享类型: 公开 */
    public static final int SHARE_TYPE_PUBLIC = 1;
    /** 分享类型: 密码 */
    public static final int SHARE_TYPE_PASSWORD = 2;

    /** 分享权限(位掩码, 可组合): 可预览 */
    public static final int SHARE_PERMISSION_VIEW = 1;
    /** 分享权限(位掩码, 可组合): 可下载 */
    public static final int SHARE_PERMISSION_DOWNLOAD = 2;
    /** 分享权限(位掩码, 可组合): 可转存 */
    public static final int SHARE_PERMISSION_SAVE = 4;
    /** 分享权限: 全部(预览|下载|转存) */
    public static final int SHARE_PERMISSION_ALL = SHARE_PERMISSION_VIEW | SHARE_PERMISSION_DOWNLOAD | SHARE_PERMISSION_SAVE;

    /** 分享有效期: 永久 */
    public static final int SHARE_EXPIRE_FOREVER = 1;
    /** 分享有效期: 按天数 */
    public static final int SHARE_EXPIRE_DAYS = 2;

    /** 分享状态: 有效 */
    public static final int SHARE_STATUS_VALID = 1;
    /** 分享状态: 已取消 */
    public static final int SHARE_STATUS_CANCELED = 0;

    /** 分享目标类型: 文件 */
    public static final int TARGET_TYPE_FILE = 1;
    /** 分享目标类型: 文件夹 */
    public static final int TARGET_TYPE_FOLDER = 2;

    /** 文件分类: 图片 */
    public static final String CATEGORY_IMAGE = "IMAGE";
    /** 文件分类: 视频 */
    public static final String CATEGORY_VIDEO = "VIDEO";
    /** 文件分类: 音频 */
    public static final String CATEGORY_AUDIO = "AUDIO";
    /** 文件分类: 文档 */
    public static final String CATEGORY_DOCUMENT = "DOCUMENT";
    /** 文件分类: 压缩包 */
    public static final String CATEGORY_ARCHIVE = "ARCHIVE";
    /** 文件分类: 代码 */
    public static final String CATEGORY_CODE = "CODE";
    /** 文件分类: 其他 */
    public static final String CATEGORY_OTHER = "OTHER";
}