-- 秒杀库存原子预扣：限购判断 + 库存扣减 + 已购记录 三件事在一个脚本内完成
-- Redis 单线程执行 Lua 脚本期间不会被其他命令打断，等价于一次原子临界区
-- KEYS[1] 库存key   KEYS[2] 已购Hash key
-- ARGV[1] memberId  ARGV[2] quantity  ARGV[3] perLimit
-- 返回：1 预扣成功；0 售罄；-1 超出限购；-2 活动未预热
-- 注意：模板的 value 序列化器是 Jackson2Json，ARGV 里的数字一律 tonumber() 归一
local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or 0)
if bought + tonumber(ARGV[2]) > tonumber(ARGV[3]) then return -1 end
local stock = tonumber(redis.call('GET', KEYS[1]) or -1)
if stock < 0 then return -2 end
if stock < tonumber(ARGV[2]) then return 0 end
redis.call('DECRBY', KEYS[1], ARGV[2])
redis.call('HINCRBY', KEYS[2], ARGV[1], ARGV[2])
return 1
