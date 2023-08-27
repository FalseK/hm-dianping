package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redisClient(){

        // 创建配置类
        Config config = new Config();

        // 添加Redis地址，密码。 使用config.useClusterServers()配置集群地址
        config.useSingleServer().setAddress("redis://192.168.25.128:6379").setPassword("123456");

        return Redisson.create(config);
    }

}
