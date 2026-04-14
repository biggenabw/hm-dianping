package com.hmdp.utils;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
   private  StringRedisTemplate stringRedisTemplate;
   public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
       this.stringRedisTemplate=stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1获取请求头token
        String token = request.getHeader("authorization");
        //2判断为空，拦截
        if(StrUtil.isBlank(token)){
           return true;
        }
        //获取token的用户
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        //map转user
        UserDTO user = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        //3用户为空则返回错误
        if (user == null) {
           return true;
        }
        //把用户存到ThreadLocal中
        UserHolder.saveUser( user);
        //刷新token的有效期
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, map);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //放行
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }

}
