package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result isFollow(Long id) {

        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

//        Follow follow = getOne(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id));

        Integer count = lambdaQuery().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id).count();


        return Result.ok(count > 0);
    }

    @Override
    public Result follow(Long id, Boolean isNotFollow) {

        Long userId = UserHolder.getUser().getId();

        String key = "follows:" + userId;

        if (isNotFollow){
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(id);
            boolean isSuccess = save(follow);

//            if (isSuccess){
//                stringRedisTemplate.opsForSet().add(key,id.toString());
//            }

            return Result.ok("已关注");

        }else {
            boolean isSuccess = remove(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, id));

//            if (isSuccess){
//                stringRedisTemplate.opsForSet().remove(key);
//            }

            return Result.ok("取消关注");

        }

    }

    @Override
    public Result common(Long id) {

        UserDTO myUser = UserHolder.getUser();

        if (myUser == null){
            return Result.ok();
        }

        Long userId = myUser.getId();

//        String key1 = "follows:" + userId;
//
//        String key2 = "follows:" + id;
//
//        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
//
//        if (intersect == null || intersect.isEmpty()){
//            return Result.ok();
//        }

//        List<UserDTO> commonUsers = userService.listByIds(intersect)
//                .stream()
//                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
//                .collect(Collectors.toList());

        List<UserDTO> commonUsers = baseMapper.findCommon(userId, id)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());


        return Result.ok(commonUsers);

    }
}
