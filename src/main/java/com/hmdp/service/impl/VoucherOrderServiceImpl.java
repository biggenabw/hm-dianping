package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
 @Autowired
 private ISeckillVoucherService seckillVoucherService;
 @Autowired
 private RedisWorker redisWorker;
 @Autowired
 private StringRedisTemplate stringRedisTemplate;
 @Autowired
 private RedissonClient redissonClient;

 ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
 @PostConstruct
    public void init() {

     SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

 public class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            String queueName = "stream.orders";
            while (true) {
                try {
                    //1获取订单
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(

                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())

                    );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(record.getValue(), new VoucherOrder(), true);
                    //2创建订单
                    handlerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                  handlerPendingList();
                }
            }
        }

     private void handlerPendingList() {
         String queueName = "stream.orders";
         while (true) {
             try {
                 //1获取订单
                 List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(

                         Consumer.from("g1", "c1"),
                         StreamReadOptions.empty().count(1),
                         StreamOffset.create(queueName, ReadOffset.from("0"))

                 );
                 if (list == null || list.isEmpty()) {
                     break;
                 }
                 MapRecord<String, Object, Object> record = list.get(0);
                 VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(record.getValue(), new VoucherOrder(), true);
                 //2创建订单
                 handlerVoucherOrder(voucherOrder);
             } catch (Exception e) {
                 log.error("处理订单失败", e);
             }
         }
     }


 }
    private IVoucherOrderService proxy;
 private void handlerVoucherOrder(VoucherOrder voucherOrder) {
     Long voucherId = voucherOrder.getVoucherId();
     //创建锁对象
     RLock lock = redissonClient.getLock("lock:order:" + voucherId);
     //  SimpleRedisLock lock = new SimpleRedisLock("lock:order:" + voucherId, stringRedisTemplate);
     //尝试获取锁
     boolean isSuccess = lock.tryLock();
     //判断是否获取成功
     if (!isSuccess) {
         //获取锁失败
       log.error("获取锁失败");
     }
     try {

         proxy. createVouvherOrder(voucherOrder);
     } finally {
         lock.unlock();
     }


 }
    private  static  final DefaultRedisScript<Long> SCKILL_SCRIPT;
    static {
        SCKILL_SCRIPT = new DefaultRedisScript<>();
        SCKILL_SCRIPT.setLocation(new ClassPathResource("sckill.lua"));
        SCKILL_SCRIPT.setResultType(long.class);
    }
    @Override
    public Result seckillVoucher(Long voucherId) {

        //1执行lua脚本
        Long userId = UserHolder.getUser().getId();
        long orderId = redisWorker.nextId("order:");
        // 调试: 查看Lua脚本执行前的Redis库存
        String stockKey = "seckill:stock:" + voucherId;
        String stockInRedis = stringRedisTemplate.opsForValue().get(stockKey);
        System.out.println("秒杀请求 - voucherId: " + voucherId + ", userId: " + userId + ", Redis库存: " + stockInRedis);

        //1执行lua脚本
        Long result = stringRedisTemplate.execute(
                SCKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)

        );
        //2判断返回值是否为0
        int r = result.intValue();
        System.out.println("Lua脚本执行结果: " + r + " (0=成功, 1=库存不足, 2=重复下单)");
        if (r != 0) {
            //3不为零返回错误信息
            return Result.fail(r == 1 ? "库存不足!" : "请勿重复下单!");
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }
   /* @Override
    public Result seckillVoucher(Long voucherId) {
    
      //1执行lua脚本
        Long userId = UserHolder.getUser().getId();
        long orderId = redisWorker.nextId("order:");
        // 调试: 查看Lua脚本执行前的Redis库存
        String stockKey = "seckill:stock:" + voucherId;
        String stockInRedis = stringRedisTemplate.opsForValue().get(stockKey);
        System.out.println("秒杀请求 - voucherId: " + voucherId + ", userId: " + userId + ", Redis库存: " + stockInRedis);
            
        //1执行lua脚本
        Long result = stringRedisTemplate.execute(
                SCKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
    
                );
        //2判断返回值是否为0
        int r = result.intValue();
        System.out.println("Lua脚本执行结果: " + r + " (0=成功, 1=库存不足, 2=重复下单)");
        if (r != 0) {
            //3不为零返回错误信息
            return Result.fail(r == 1 ? "库存不足!" : "请勿重复下单!");
        }
        //4,把订单id和优惠卷id封装后保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        orderTasks.add(voucherOrder);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }*/
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //1获取id查询秒杀卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2判断抢购是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        //3判断抢购是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        //4判断库存是否充足
        if (voucher.getStock() <= 0) {
            return Result.fail("库存不足！");
        }
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + voucherId);
        //  SimpleRedisLock lock = new SimpleRedisLock("lock:order:" + voucherId, stringRedisTemplate);
        //尝试获取锁
        boolean isSuccess = lock.tryLock();
        //判断是否获取成功
        if (!isSuccess) {
            //获取锁失败
            return Result.fail("请勿重复下单！");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy. createVouvherOrder(voucherId);
        } finally {
            lock.unlock();
        }


    }*/
    @Transactional
    public void createVouvherOrder(VoucherOrder voucherOrder) {
        //一人一单

        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已经购买过该优惠券！");
        }
        //5扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").gt("stock", 0).eq("voucher_id", voucherOrder).update();
        if (!success) {
            log.error("库存不足！");
        }
        save(voucherOrder);

    }
}
