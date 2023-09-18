package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
public class ShopData2Redis {

    @Resource
    IShopService shopService;


    @Resource
    CacheClient cacheClient;

    @Test
    public void shopData2Redis(){
        List<Shop> shops = shopService.getBaseMapper().selectList(null);

        shops.forEach(shop -> cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + shop.getId(),shop,30L, TimeUnit.MINUTES));



    }

}
