local voucherId = ARGV[1]
local userId = ARGV[2]
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then return 1 end
-- 判断用户是否重复下单
if(redis.call('sismember', orderKey, userId) == 1) then return 2 end

-- 扣库存，加用户到 Set
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)

-- 移除原有的 redis.call('xadd', 'stream.orders', ...)
return 0