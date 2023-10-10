package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable Long id){
        return followService.isFollow(id);
    }

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable Long id, @PathVariable Boolean isFollow){

        return followService.follow(id,isFollow);
    }

    @GetMapping("/common/{id}")
    public Result common(@PathVariable Long id){
        return followService.common(id);
    }


}
