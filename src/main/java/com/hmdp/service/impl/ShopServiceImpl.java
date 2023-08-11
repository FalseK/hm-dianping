package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

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

    // 添加商户查询缓存
    @Override
    public Result queryById(Long id) {

        // 1.接收id，到redis中查询缓存

        String shopKey = CACHE_SHOP_KEY + id;

        String shopJSON = stringRedisTemplate.opsForValue().get(shopKey);

        // 2.缓存中取到数据，返回，取不到查询数据库
        if (StrUtil.isNotBlank(shopJSON)){
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return Result.ok(shop);
        }

        if ("".equals(shopJSON)){
            return Result.fail("店铺不存在");
        }


        // 3.数据库中取到数据返回并添加到缓存，取不到返回错误
        Shop shop = getById(id);


        if (shop == null){
            // 空值写入redis
            stringRedisTemplate.opsForValue().set(shopKey,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);


        return Result.ok(shop);
    }

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
