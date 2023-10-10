-- 用户id
local userId = ARGV[1]

-- blogId
local id = ARGV[2]

-- 现在时间戳
local nowTimeStamp = ARGV[3]

local key = "blog:liked:" .. id

if (redis.call('zscore', key, userId) == nil) then
    -- 点赞成功
    redis.call('zadd', key, userId)
    return 1



end