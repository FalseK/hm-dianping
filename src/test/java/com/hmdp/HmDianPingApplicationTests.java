package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import javax.annotation.Resource;
import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    IShopTypeService shopTypeService;

    @Test
    public void zSetSaveTest(){

        List<ShopType> shopTypes = shopTypeService.getBaseMapper().selectList(null);

        Set<ZSetOperations.TypedTuple<String>> shopTypeSet = new HashSet<>();

        shopTypes.forEach( shopType -> shopTypeSet.add(
                new DefaultTypedTuple<>(JSONUtil.toJsonStr(shopType), shopType.getSort().doubleValue())));

        stringRedisTemplate.opsForZSet().add(CACHE_SHOP_TYPE_KEY,shopTypeSet);

    }

    @Test
    public void zSetGetTest(){
        Set<String> range = stringRedisTemplate.opsForZSet().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        List<ShopType> collect = range.stream().map(item -> JSONUtil.toBean(item, ShopType.class)).collect(Collectors.toList());
        System.out.println(collect);
    }


    @Test
    public void shopTypeQueryTest(){
        Result result = shopTypeService.queryTypeList();
        result.getData().toString();
    }


}
