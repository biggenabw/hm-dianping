package com.hmdp.utils;


import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private static final String LOCK_KEY = "lock:";
    private static final String ID_KEY = UUID.randomUUID().toString(true);
    private  static  final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(long.class);
    }
    private String name;
    @Resource
    StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_KEY + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(LOCK_KEY + name), ID_KEY + Thread.currentThread().getId());
    }
        /*String threadId = ID_KEY + Thread.currentThread().getId();
        String currentValue = stringRedisTemplate.opsForValue().get(LOCK_KEY + name);
        if (threadId.equals(currentValue)) {
            stringRedisTemplate.delete(LOCK_KEY + name);
        }
    }*/
}
