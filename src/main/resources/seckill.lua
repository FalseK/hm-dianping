-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 现在时间戳
local nowTimeStamp = tonumber(ARGV[3])
-- 订单id
local orderId = ARGV[4]
-- 库存key
local voucherKey = 'seckill:voucher:' .. voucherId
-- 2.2.订单key
local orderKey = 'seckill:order:' .. voucherId

if nowTimeStamp < tonumber(redis.call('hget', voucherKey, "beginTime")) then
    -- 秒杀未开始
    return 1
end

if nowTimeStamp > tonumber(redis.call('hget', voucherKey, "endTime")) then
    -- 秒杀已结束
    return 2
end

if (tonumber(redis.call('hget', voucherKey, "stock")) <= 0) then
    -- 库存不足
    return 3
end

if (redis.call('sismember', orderKey, userId) == 1) then
    -- 已经下过单
    return 4
end

-- 库存减少1
redis.call('hincrby', voucherKey, "stock", -1)
-- 添加到订单
redis.call('sadd', orderKey, userId)

-- 发消息到队列
redis.call('xadd',"stream.orders","*","userId",userId,"voucherId",voucherId,"id",orderId)

return 0