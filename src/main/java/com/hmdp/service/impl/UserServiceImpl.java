package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("请检查手机号码");
        }

        //生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码到Redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.debug("发送验证码成功，验证码{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //实现登录功能

        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        //验证手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("请检查手机号码");
        }

        // 根据手机号Redis中获取验证码

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

//
//        String cachePhone = (String) session.getAttribute("phone");
//        String cacheCode = (String) session.getAttribute("code");

        //验证验证码
        if (cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }

        //手机号验证码验证成功查询用户是否存在，不存在则注册并登陆
        User user = query().eq("phone",phone).one();

        if(user == null){
            //不存在，则创建
            user =  createUserWithPhone(phone);
        }

        // TODO 生成一个token存在Redis中并返回给前端，设置30分钟有效期

        String token = UUID.randomUUID().toString(true);

        // 转成Map存在Redis中

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // 自定义转换
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        String tokenKey = LOGIN_USER_KEY + token;

        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);

        //设置token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(8));
        user.setPhone(phone);

        //保存用户
        save(user);

        return user;
    }
}
