package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    CacheClient cacheClient;


//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 添加商户查询缓存
    @Override
    public Result queryById(Long id) {

        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);

//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        // 逻辑过期结局解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);

        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, LOCK_SHOP_KEY, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        return Result.ok(shop);
    }

//    public Shop queryWithLogicalExpire(Long id){
//
//        // 1.接收id，到redis中查询缓存
//
//        String shopKey = CACHE_SHOP_KEY + id;
//
//        String shopJSON = stringRedisTemplate.opsForValue().get(shopKey);
//
//        // 2.缓存中取数据，未命中，返回空
//        if (StrUtil.isBlank(shopJSON)){
//            return null;
//        }
//
//        // 命中
//        RedisData redisData = JSONUtil.toBean(shopJSON, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//
//        // 判断是否过期
//        if (LocalDateTime.now().isBefore(redisData.getExpireTime())){
//
//            return shop;
//        }
//
//        String lockKey = LOCK_SHOP_KEY + id;
//
//        // 已过期，重建缓存
//        if (tryLock(lockKey)){
//            // 检测缓存是否过期
//            // 判断是否过期
//            shopJSON = stringRedisTemplate.opsForValue().get(shopKey);
//            redisData = JSONUtil.toBean(shopJSON,RedisData.class);
//            if (LocalDateTime.now().isBefore(redisData.getExpireTime())){
//                shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//                return shop;
//            }
//
//            // 没过期重建缓存
//            // 开启线程查询数据库
//            CACHE_REBUILD_EXECUTOR.submit(() ->{
//                try {
//                    this.saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    // 释放锁
//                    unLock(lockKey);
//                }
//            });
//
//        }
//
//
//        return shop;
//    }


//    public Shop queryWithMutex(Long id){
//        // 1.接收id，到redis中查询缓存
//
//        String shopKey = CACHE_SHOP_KEY + id;
//
//        String shopJSON = stringRedisTemplate.opsForValue().get(shopKey);
//
//        // 2.缓存中取到数据，返回，取不到查询数据库
//        if (StrUtil.isNotBlank(shopJSON)){
//            return JSONUtil.toBean(shopJSON, Shop.class);
//        }
//
//        if ("".equals(shopJSON)){
//            return null;
//        }
//
//        // 获取互斥锁
//
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop;
//
//        try {
//            if (!tryLock(lockKey)){
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//
//            shopJSON = stringRedisTemplate.opsForValue().get(shopKey);
//
//            if (StrUtil.isNotBlank(shopJSON)){
//                return JSONUtil.toBean(shopJSON, Shop.class);
//            }
//
//            if ("".equals(shopJSON)){
//                return null;
//            }
//
//            // 3.数据库中取到数据返回并添加到缓存，取不到返回错误
//            shop = getById(id);
//
//            // 模拟重建延迟
//            Thread.sleep(200);
//
//            if (shop == null){
//                // 空值写入redis
//                stringRedisTemplate.opsForValue().set(shopKey,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
//
//            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unLock(lockKey);
//        }
//
//        return shop;
//    }


//    public Shop queryWithPassThrough(Long id){
//
//        // 1.接收id，到redis中查询缓存
//
//        String shopKey = CACHE_SHOP_KEY + id;
//
//        String shopJSON = stringRedisTemplate.opsForValue().get(shopKey);
//
//        // 2.缓存中取到数据，返回，取不到查询数据库
//        if (StrUtil.isNotBlank(shopJSON)){
//            return JSONUtil.toBean(shopJSON, Shop.class);
//        }
//
//        if ("".equals(shopJSON)){
//            return null;
//        }
//
//
//        // 3.数据库中取到数据返回并添加到缓存，取不到返回错误
//        Shop shop = getById(id);
//
//
//        if (shop == null){
//            // 空值写入redis
//            stringRedisTemplate.opsForValue().set(shopKey,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        return shop;
//    }

//    private boolean tryLock(String key){
//
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//
//        return BooleanUtil.isTrue(flag);
//    }

//    private void unLock(String key){
//
//        stringRedisTemplate.delete(key);
//
//    }

//    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
//        Shop shop = getById(id);
//
//        Thread.sleep(200);
//
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
//
//
//    }


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();

        if (id == null){
            return Result.fail("店铺id为空");
        }

        // 一起成功一起失败，启用事务

        // 先操作数据库
        updateById(shop);

        // 删缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);


        return null;
    }
}
