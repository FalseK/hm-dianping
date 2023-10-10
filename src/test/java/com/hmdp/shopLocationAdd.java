package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
public class shopLocationAdd {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    IShopService shopService;

    @Test
    public void shopLocationAddTest(){

        List<Shop> shopList = shopService.list();

        Map<Long, List<Shop>> groupByTypeId = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        for (Map.Entry<Long, List<Shop>> entry : groupByTypeId.entrySet()) {

            String typeId = entry.getKey().toString();
            String key = SHOP_GEO_KEY + typeId;

            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> geoLocations = shops.stream()
                    .map(shop ->
                            new RedisGeoCommands.GeoLocation<>
                                    (shop.getId().toString(), new Point(shop.getX(), shop.getY()))
            ).collect(Collectors.toList());

            stringRedisTemplate.opsForGeo().add(key,geoLocations);
        }


    }

}
