package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Resource
    StringRedisTemplate stringRedisTemplate;


    // 开始时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    // 序列号位数
    private static final int COUNT_BITS = 32;

    public long nextId(String keyPrefix){
        // 获取日期时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);

        long timestamp = nowSeconds - BEGIN_TIMESTAMP;

        // 获取序列号
        // 自增长
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);

        return timestamp << COUNT_BITS | count;
    }

}
