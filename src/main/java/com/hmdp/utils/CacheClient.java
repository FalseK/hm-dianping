package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    // 插入数据到Redis，设置缓存TTL
    public void set(String key, Object value, Long time, TimeUnit unit) {

        if (value == null) {
            return;
        }

        String dataJSON = JSONUtil.toJsonStr(value);

        stringRedisTemplate.opsForValue().set(key, dataJSON, time, unit);


    }

    //插入数据到Redis并设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) throws InterruptedException {

        if (value == null) {
            return;
        }

        Thread.sleep(200);

        RedisData redisData = new RedisData();
        redisData.setData(value);

        // 时间操作
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        String dataJSON = JSONUtil.toJsonStr(redisData);

        stringRedisTemplate.opsForValue().set(key, dataJSON);

    }


    //查询，解决缓存穿透
    public <T, ID> T queryWithPassThrough(String keyPrefix, ID id, Class<T> dataType, Function<ID, T> dbFallback,
                                          Long time, TimeUnit unit) {
        // 1.接收id，到redis中查询缓存

        String key = keyPrefix + id;

        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.缓存中取到数据，返回，取不到查询数据库
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, dataType);
        }

        if ("".equals(json)) {
            return null;
        }


        // 3.数据库中取到数据返回并添加到缓存，取不到返回错误
        T data = dbFallback.apply(id);


        if (data == null) {
            // 空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", time, unit);
            return null;
        }

        this.set(key, data, time, unit);

        return data;
    }



    // 解决缓存击穿并且设置热点key的逻辑过期时间
    public <T, ID> T queryWithLogicalExpire(String keyPrefix, ID id, String lockKey, Class<T> dataType, Function<ID, T> dbFallback,
                                            Long time, TimeUnit unit) {

        // 1.接收id，到redis中查询缓存

        String key = keyPrefix + id;

        String dataJSON = stringRedisTemplate.opsForValue().get(key);

        // 2.缓存中取数据，未命中，返回空
        if (StrUtil.isBlank(dataJSON)) {
            return null;
        }

        // 命中
        RedisData redisData = JSONUtil.toBean(dataJSON, RedisData.class);
        T data = JSONUtil.toBean((JSONObject) redisData.getData(), dataType);

        // 判断是否过期
        if (LocalDateTime.now().isBefore(redisData.getExpireTime())) {

            return data;
        }

//        String lockKey = LOCK_SHOP_KEY + id;

        // 已过期，重建缓存
        if (tryLock(lockKey)) {
            // 检测缓存是否过期
            // 判断是否过期
            dataJSON = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(dataJSON, RedisData.class);
            if (LocalDateTime.now().isBefore(redisData.getExpireTime())) {
                data = JSONUtil.toBean((JSONObject) redisData.getData(), dataType);
                return data;
            }

            // 过期重建缓存
            // 开启线程查询数据库
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.setWithLogicalExpire(key, dbFallback.apply(id), 20L,TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });

        }


        return data;
    }


    private boolean tryLock(String key) {

        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {

        stringRedisTemplate.delete(key);

    }
}
