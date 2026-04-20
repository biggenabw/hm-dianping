--1.voucherId
local voucherId=ARGV[1]
--2.userId
local userId=ARGV[2]
--3.orderId
local orderId=ARGV[3]

--3.stockKey库存
local stockKey="seckill:stock:"..voucherId
--4.orderKey订单
local orderKey="seckill:order:"..voucherId
--5.判断库存
local stock = redis.call('get', stockKey)
if (not stock) or (tonumber(stock) <= 0) then
    return 1
end
--6.判断用户是否已经购买
if redis.call("sismember", orderKey, userId) == 1 then
    return 2
end
--7.扣减库存
redis.call('incrby', stockKey, -1)
--8.记录订单
redis.call("sadd", orderKey, userId)
--9 发消息到redis stream
redis.call("xadd", "stream.orders", "*", "userId", userId, "voucherId", voucherId, "id", orderId)
return 0