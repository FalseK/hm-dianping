package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.apache.tomcat.jni.Local;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import javax.annotation.Resource;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    CacheClient cacheClient;

    @Autowired
    ShopServiceImpl shopService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    IShopTypeService shopTypeService;

    @Resource
    RedisTemplate<Object,Object> redisTemplate;

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

    @Test
    public void shopLogicalTest() throws InterruptedException {

        Shop shop = shopService.getById(1L);

        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L,shop,10L,TimeUnit.SECONDS);
    }

    @Test
    public void redisHSetTest(){
        class testObject implements Serializable {
            String data1;
            String data2;
            LocalDateTime time;

            public testObject(String data1, String data2,LocalDateTime time) {
                this.data1 = data1;
                this.data2 = data2;
                this.time = time;
            }


            public String getData1() {
                return data1;
            }

            public void setData1(String data1) {
                this.data1 = data1;
            }

            public String getData2() {
                return data2;
            }

            public void setData2(String data2) {
                this.data2 = data2;
            }

            public LocalDateTime getTime() {
                return time;
            }

            public void setTime(LocalDateTime time) {
                this.time = time;
            }
        }

        testObject testObject = new testObject("A", "B",LocalDateTime.now());

        System.out.println(testObject);

        Map<String, Object> testMap = BeanUtil.beanToMap(testObject);



        System.out.println(testMap);

        stringRedisTemplate.opsForHash().putAll("testData",testMap);


    }

    @Test
    public void timeStampToStringTest(){
        LocalDateTime now = LocalDateTime.now();
        long l = now.toEpochSecond(ZoneOffset.UTC);
        String s = String.valueOf(l);
        System.out.println(s);
    }

    @Test
    public void zSetAddTest(){
        stringRedisTemplate.opsForZSet().add("k1","m1",LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
    }

    @Test
    public void zSetScoreTest(){
        Double score = stringRedisTemplate.opsForZSet().score("k1", "m1");
        System.out.println(score);
    }




}
