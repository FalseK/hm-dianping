package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {

        // 从缓存中取
        List<String> JSONShopTypeList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);

        // 缓存中取到了，返回
        if (CollUtil.isNotEmpty(JSONShopTypeList)){

            List<ShopType> shopTypeList = JSONShopTypeList.stream()
                    .map(item -> JSONUtil.toBean(item, ShopType.class))
                    .collect(Collectors.toList());

            return Result.ok(shopTypeList);
        }

        // 缓存中取不到，到数据库中查询

        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        if (CollUtil.isEmpty(shopTypeList)){
            return Result.fail("图标不存在");
        }

        // 集合中对象转成JSON字符串存入Redis中
        List<String> result = shopTypeList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());

        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY, result);

        return Result.ok(shopTypeList);

    }
}
