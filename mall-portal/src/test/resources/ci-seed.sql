-- CI 环境种子数据：集成测试依赖固定 id 的会员与收货地址
-- （本地开发机此前已手工创建；CI 的全新 MySQL 由 workflow 导入本文件，INSERT IGNORE 保证本地重复执行安全）
-- 密码为 test123456 的 BCrypt 哈希（与本地 tester01 相同）
INSERT IGNORE INTO ums_member (id, username, password, nickname, phone, status, create_time)
VALUES (12, 'tester01', '$2a$10$xYQV44CWO5AC51KsETYhZO0uUWgj3GZCIqgZ34aal7eD8sihKQV8y', 'tester01', '15900000012', 1, NOW());

INSERT IGNORE INTO ums_member_receive_address (id, member_id, name, phone_number, default_status, post_code, province, city, region, detail_address)
VALUES (7, 12, 'Tester', '15900000012', 1, '100000', 'Beijing', 'Beijing', 'Chaoyang', 'Street 1');
