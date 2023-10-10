package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Resource
    IUserService userService;

    @Resource
    IFollowService followService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;

    @Override
    public Result queryBlogById(Long id) {

        Blog blog = getById(id);

        if (blog == null) {
            return Result.fail("笔记不存在！");
        }

        queryBlogUser(blog);

        return Result.ok(blog);

    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        blogIsLiked(blog);
    }

    private void blogIsLiked(Blog blog) {
        Long blogId = blog.getId();

        UserDTO user = UserHolder.getUser();

        if (user == null) {
            return;
        }

        Long userId = user.getId();


        String key = BLOG_LIKED_KEY + blogId;

        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        blog.setIsLike(score != null);


    }


    @Override
    public Result queryHotBlog(Integer current) {

        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.blogIsLiked(blog);
            this.queryBlogUser(blog);
        });

        return Result.ok(records);
    }

    @Override
    public Result queryMyBlog(Integer current) {

        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();

        return Result.ok(records);
    }

//    @Override
//    public Result likeBlog(Long id) {
//        // 修改点赞数量
//        update().setSql("liked = liked + 1").eq("id", id).update();
//        return Result.ok();
//    }

//    @Override
//    public Result likeBlog(Long id) {
//
//        Long userId = UserHolder.getUser().getId();
//
//        String key = BLOG_LIKED_KEY + id;
//
//        RLock lock = redissonClient.getLock(LOCK_LIKE_KEY + userId);
//
//        boolean isLock = lock.tryLock();
//
//        if (!isLock){
//            return Result.fail("点赞失败");
//        }
//
//        try {
//            Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//
//            if (BooleanUtil.isFalse(isMember)){
//                boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
//
//                if (isSuccess){
//                    stringRedisTemplate.opsForSet().add(key,userId.toString());
//                }
//
//            }else {
//                boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
//
//                if (isSuccess){
//                    stringRedisTemplate.opsForSet().remove(key,userId.toString());
//                }
//            }
//        } finally {
//            lock.unlock();
//        }
//
//
//        return Result.ok();
//    }


    @Override
    public Result likeBlog(Long id) {

        Long userId = UserHolder.getUser().getId();

        String key = BLOG_LIKED_KEY + id;

        RLock lock = redissonClient.getLock(LOCK_LIKE_KEY + userId);

        try {
            boolean isLock;
            isLock = lock.tryLock(0, 1, TimeUnit.SECONDS);


            if (!isLock) {
                return Result.fail("点赞失败");
            }


            Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

            if (score == null) {
                boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();

                if (isSuccess) {
                    stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
                }

            } else {
                boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();

                if (isSuccess) {
                    stringRedisTemplate.opsForZSet().remove(key, userId.toString());
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }


        return Result.ok();
    }


    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        blog.setUserId(userId);
        // 保存探店博文
        save(blog);

        Long blogId = blog.getId();

        // 获取关注我的用户
//        List<Long> fansIdList = followService.list(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, userId))
//                .stream()
//                .map(Follow::getUserId)
//                .collect(Collectors.toList());
//
//        fansIdList.forEach(fansId -> stringRedisTemplate.opsForZSet().add(FEED_KEY + fansId, blogId.toString(),System.currentTimeMillis()));

        List<Follow> follows = followService.lambdaQuery().eq(Follow::getFollowUserId, userId).list();

        follows.forEach(follow -> stringRedisTemplate.opsForZSet().
                add(FEED_KEY + follow.getUserId(),blogId.toString(),System.currentTimeMillis()));


        // 返回id
        return Result.ok(blogId);
    }

    @Override
    public Result getBlogLikes(Long id) {

        String key = BLOG_LIKED_KEY + id;

        Set<String> blogLikes = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        if (blogLikes == null || blogLikes.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = blogLikes.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);

//        List<User> users = userService.listByIds(ids);


        List<User> users = userService.query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();


        List<UserDTO> userDTOS = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {

        // 每次读取的条数
        int count = 3;

        // 获取登录用户Id
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.ok();
        }

        Long userId = user.getId();

        Set<ZSetOperations.TypedTuple<String>> typedTuples
                = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(FEED_KEY + userId,0,max,offset,count);

        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }

        List<Long> blogIdList = new ArrayList<>(typedTuples.size());

        long min = 0;
        int os = 1;

        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {

            blogIdList.add(Long.valueOf(typedTuple.getValue()));

            long time = typedTuple.getScore().longValue();

            if (time == min){
                os++;
            }
            else {
                min = time;
                os = 1;
            }

        }

        String idStr = StringUtil.join(blogIdList, ",");

        List<Blog> blogs = query().in("id", blogIdList).last("ORDER BY FIELD(id," + idStr + ")").list();

        blogs.forEach(this::queryBlogUser);

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(min);

        return Result.ok(scrollResult);


    }
}
