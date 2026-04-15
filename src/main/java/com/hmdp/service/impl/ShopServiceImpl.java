package com.hmdp.service.impl;

import ch.qos.logback.core.joran.util.beans.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisExpireData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    CacheClient cacheClient; // Changed from @Resource to @Autowired

    @Override
    public Result queryById(Long id) {
        //缓存穿透
       // Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //基于互斥锁解决缓存击穿
        //Shop shop = queryWithPassMutex(id);
        //基于逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);

    }
   /* public Shop queryWithPassMutex(Long id) {
        //缓存击穿
        //1查询redis中是否有数据信息
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2有则返回
        Shop shop = new Shop();
        if (StrUtil.isNotBlank(shopJson)) {
            shop = JSONUtil.toBean(shopJson, Shop.class);
            //2有则返回
            return shop;
        }
        if (shopJson != null) {
            return null;
        }
        //3不存在，则根据数据库查询店铺信息
        //1获取互斥锁；
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            Boolean isLock = tryLock(lockKey);
            //2检查锁是存在；
            if (!isLock) {
                //3不能存在，则休眠短暂时间后，返回继续查询；
                Thread.sleep(30);
                return queryWithPassMutex(id);
            }
            //5获取成功，向数据库查询数据
            shop = getById(id);
            //4不存在，返回错误信息
            if (shop == null) {
                //返回空值
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //5存在，则向redis写入商铺信息
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {

        } finally {
            //6释放锁
            unLock(lockKey);
        }
        //6把数据信息返回
        return shop;
    }*/

   /* private void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisExpireData redisExpireData =new RedisExpireData();
        redisExpireData.setData(shop);
        redisExpireData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisExpireData));
    }
*/
  /*  public Shop queryWithPassThrough(Long id) {
        //1查询redis中是否有数据信息
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);

        //2有则返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            //2有则返回
            return shop;
        }
        if (shopJson != null) {
            return null;
        }
        //3不存在，则根据数据库查询店铺信息
        Shop shop = getById(id);

        //4不存在，返回错误信息
        if (shop == null) {
            //返回空值
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5存在，则向redis写入商铺信息
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6把数据信息返回
        return shop;
    }*/
   /* private boolean tryLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String lockKey) {
        log.debug("释放锁");
        stringRedisTemplate.delete(lockKey);
    }*/
    @Override
    public Result updateShop(Shop shop) {
        //1判断shop的id是否为空
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺Id不能为空");
        }
        //2更新数据库
        updateById(shop);
        //3删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
