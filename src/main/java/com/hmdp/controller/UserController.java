package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

    /**
     * 修改昵称
     */
    @PutMapping("/nickname")
    public Result updateNickName(@RequestParam("nickname") String nickname, HttpServletRequest request) {
        if (StrUtil.isBlank(nickname) || nickname.length() > 32) {
            return Result.fail("昵称不能为空且不超过32个字符");
        }
        Long userId = UserHolder.getUser().getId();
        User user = userService.getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        user.setNickName(nickname);
        userService.updateById(user);
        // 同步登录缓存中的昵称，否则刷新后 /user/me 还是旧值
        String token = request.getHeader("authorization");
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.opsForHash().put(LOGIN_USER_KEY + token, "nickName", nickname);
        }
        return Result.ok();
    }

    /**
     * 修改头像（icon 为上传接口返回的图片地址，如 /imgs/blogs/...）
     */
    @PutMapping("/icon")
    public Result updateIcon(@RequestParam("icon") String icon, HttpServletRequest request) {
        if (StrUtil.isBlank(icon)) {
            return Result.fail("头像地址不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        User user = userService.getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        user.setIcon(icon);
        userService.updateById(user);
        // 同步登录缓存中的头像
        String token = request.getHeader("authorization");
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.opsForHash().put(LOGIN_USER_KEY + token, "icon", icon);
        }
        return Result.ok();
    }

    /**
     * 修改个人资料：介绍、性别、城市、生日（传什么改什么，不存在则新建）
     */
    @PutMapping("/info")
    public Result updateInfo(@RequestBody UserInfo info) {
        Long userId = UserHolder.getUser().getId();
        info.setUserId(userId);
        // 主键由后端控制，时间字段交给数据库默认值
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 主键为 userId：有记录则更新，没有则新增
        userInfoService.saveOrUpdate(info);
        return Result.ok();
    }
}