package com.hmdp;

import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

@SpringBootTest
public class followMapperTest {

    @Resource
    FollowMapper followMapper;

    @Test
    public void findCommonTest(){

        List<User> common = followMapper.findCommon(1L, 5L);
        System.out.println(common);


    }

}
