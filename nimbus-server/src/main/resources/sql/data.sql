-- nimbus-cloud 种子数据, 由 spring.sql.init 启动时执行, 需保持幂等
-- 密码均为 admin123(BCrypt)
INSERT IGNORE INTO nimbus_user (id, username, nickname, password, role_key, status)
VALUES (1, 'admin', '管理员', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'admin', 1),
       (2, 'nimbus', '网盘用户', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'netdisk', 1);

-- 默认配额 128GB
INSERT IGNORE INTO nimbus_quota (id, user_id, total_size, used_size)
VALUES (1, 1, 137438953472, 0),
       (2, 2, 137438953472, 0);