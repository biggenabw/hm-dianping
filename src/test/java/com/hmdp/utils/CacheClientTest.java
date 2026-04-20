package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class CacheClientTest {
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private RedisWorker redisWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void getId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
         for (int i = 0; i < 100; i++) {
             Long id = redisWorker.nextId("order");
             System.out.println("id=" + id);
         }
         latch.countDown();
     };
        long startTime = System.currentTimeMillis();
     for (int i = 0; i < 300; i++){
         es.submit(task);
     }
        latch.await();
        long endTime = System.currentTimeMillis();
        System.out.println("time: " + (endTime - startTime));
    }

    @Test
    void setWithLogicalExpire() {
       for (long i = 1; i < 15; i++){
           Shop shop = shopService.getById(i);
           cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + shop.getId(), JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

       }
}}