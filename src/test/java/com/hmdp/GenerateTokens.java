package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

@SpringBootTest
public class GenerateTokens {

    @Resource

    private StringRedisTemplate stringRedisTemplate;


    @Resource

    private IUserService userService;

    @Test

    public void test() {

        FileWriter fr = null;

        try {

            fr = new FileWriter("E:\\file\\学习\\Redis-笔记资料\\02-实战篇\\资料\\tokens.txt");


            List<User> list = userService.list();

            System.out.println(list.size());

            for (User user : list) {

                String token = UUID.randomUUID().toString(true);

                fr.append(token);

                fr.append("\n");

                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

                Map<String, String> userMap = new HashMap<>();

                userMap.put("id", userDTO.getId().toString());

                userMap.put("nickName", userDTO.getNickName());

                userMap.put("icon", userDTO.getIcon());

                String tokenKey = LOGIN_USER_KEY + token;

                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

//                stringRedisTemplate.expire(tokenKey, -1, TimeUnit.MINUTES);

            }

        } catch (IOException e) {

            e.printStackTrace();

        } finally {

            try {

                fr.close();

            } catch (IOException e) {

                e.printStackTrace();

            }

        }

    }

}
