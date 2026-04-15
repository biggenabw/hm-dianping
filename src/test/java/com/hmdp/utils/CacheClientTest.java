package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class CacheClientTest {
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private ShopServiceImpl shopService;


    @Test
    void set() {
    }

    @Test
    void setWithLogicalExpire() {
       for (long i = 1; i < 15; i++){
           Shop shop = shopService.getById(i);
           cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + shop.getId(), JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

       }
}}