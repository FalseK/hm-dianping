package com.hmdp.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    IBlogService blogService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {

        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {

        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        return blogService.queryMyBlog(current);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        return blogService.queryHotBlog(current);
    }

    @GetMapping("{id}")
    public Result queryBlogById(@PathVariable Long id){

        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result getBlogLikes(@PathVariable Long id){
        return blogService.getBlogLikes(id);
    }

    @GetMapping("/of/user")
    public Result blogOfUser(@RequestParam(name = "id") Long id,@RequestParam(name = "current") Long current){

        Page<Blog> page = new Page<>();
        page.setCurrent(current);
        page.setSize(SystemConstants.MAX_PAGE_SIZE);

        Page<Blog> result = blogService.page(page, new LambdaQueryWrapper<Blog>().eq(Blog::getUserId, id));



        return Result.ok(result.getRecords());
    }

    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId")Long max ,@RequestParam(value = "offset", defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max,offset);
    }

}
