package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryById(Long id) {
        //1查询redis中是否有数据信息
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);

        //2有则返回
        if (shopJson != null) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            //2有则返回
            return Result.ok(shop);
        }
        //3不存在，则根据数据库查询店铺信息
        Shop shop = getById(id);

        //4不存在，返回错误信息
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //5存在，则向redis写入商铺信息
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));

        //6把数据信息返回
        return Result.ok(shop);

    }
}
