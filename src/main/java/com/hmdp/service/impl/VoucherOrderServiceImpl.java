package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import io.lettuce.core.RedisClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jni.Local;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.LOCK_ORDER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_VOUCHER_KEY;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    private IVoucherOrderService proxy;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

//    private BlockingQueue<VoucherOrder> orderTasks;
//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//    }


    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";

        @Override
        public void run() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    if (list == null || list.isEmpty()) {
                        continue;
                    }

                    MapRecord<String, Object, Object> record = list.get(0);

                    Map<Object, Object> value = record.getValue();

                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                    handleVoucherOrder(voucherOrder);

                    // 订单确认

                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());


                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    if (list == null || list.isEmpty()) {
                        break;
                    }

                    MapRecord<String, Object, Object> record = list.get(0);

                    Map<Object, Object> value = record.getValue();

                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                    handleVoucherOrder(voucherOrder);

                    // 订单确认

                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());


                } catch (Exception e) {
                    log.error("处理PendingList异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }
            }
        }

    }


    // lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @PostConstruct
    private void init() {
//        orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());

    }

    @Override
    public Result seckillVoucher(Long voucherId) {

        Boolean hasVoucher = stringRedisTemplate.hasKey(SECKILL_VOUCHER_KEY + voucherId);

        if (BooleanUtil.isFalse(hasVoucher)) {
            return Result.fail("优惠券不存在");
        }

        Long userId = UserHolder.getUser().getId();

        //生成订单id
        Long orderId = redisIdWorker.nextId("order");


        // 获取当前时间
        long nowTimeStamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);


        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(nowTimeStamp), orderId.toString());


        int r = result.intValue();

        switch (r) {
            case 1:
                return Result.fail("秒杀未开始");
            case 2:
                return Result.fail("秒杀已结束");
            case 3:
                return Result.fail("库存不足");
            case 4:
                return Result.fail("你已下过单");
        }

//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setVoucherId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);

        proxy = (IVoucherOrderService) AopContext.currentProxy();

//        orderTasks.add(voucherOrder);


        return Result.ok(orderId);
    }


    /*
     * 同一个用户同时多次请求购买一个秒杀券，会产生线程安全问题。
     * 因为查找数据库时，如果多个线程同时查找数据库，会出现都返回count<0的结果
     * 因此需要保证进行查询数据库判断是否下过单操作时不让其他线程来访问这段代码
     * 所以需要使用悲观锁。以用户的Id作为获取锁的标志，实现一个用户一把锁。
     *
     * 若仅仅在查询count数据时加锁，查询完毕后释放锁，再添加订单
     * 会因为@Transactional声明式事务是在函数调用结束后才会提交事务修改数据库
     * 查询完count后释放锁的话，函数未调用结束，此时订单数据并未提交到数据库
     * 其他线程进入查询count还是会得到<0的结果，然后再次下单，出现一个用户购买多张券的情况
     * 因此需要给整个函数上锁，在事务提交之后才释放锁。
     *
     */

    private void handleVoucherOrder(VoucherOrder voucherOrder) {

        Long userId = voucherOrder.getUserId();

        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);

        boolean isLock = lock.tryLock();

        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }

        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }

    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        Long userId = voucherOrder.getUserId();

        Long voucherId = voucherOrder.getVoucherId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        //查找该用户有没有重复下单
        if (count > 0) {
            log.error("你已下过单");
            return;
        }

        // 修改库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            log.error("库存不足");
            return;
        }

        save(voucherOrder);
    }




//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//
//        Long userId = UserHolder.getUser().getId();
//
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//
//        //查找该用户有没有重复下单
//        if (count > 0){
//            return Result.fail("你已经下过一次单");
//        }
//
//        // 修改库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .gt("stock",0)
//                .update();
//
//        if (!success){
//            return Result.fail("库存不足");
//        }
//
//
//        //下订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setUserId(userId);
//
//        save(voucherOrder);
//
//
//        return Result.ok(orderId);
//    }


//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        // 查询优惠券信息
//        SeckillVoucher secKillVoucher = seckillVoucherService.getById(voucherId);
//
//        // 获取当前时间
//        LocalDateTime now = LocalDateTime.now();
//
//        // 判断秒杀是否开始
//
//        if (secKillVoucher == null){
//            return Result.fail("库存不足");
//        }
//
//        if (secKillVoucher.getBeginTime().isAfter(now)) {
//            return Result.fail("秒杀还未开始");
//        }
//
//        if (secKillVoucher.getEndTime().isBefore(now)){
//            return Result.fail("秒杀已经结束");
//        }
//
//        if (secKillVoucher.getStock() < 1){
//            return Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//
//
//
////        // toString()方法会new 一个字符串，造成同一个用户ID每次都是获取新的锁
////        // 需要用intern()方法在常量池中创建字符串，保证一个用户一把锁。
////        synchronized (userId.toString().intern()){
////            /*
////             * 这样调用会导致事务失效，原因是这里调用的方式是 this.createVoucherOrder();
////             * this是这个VoucherOrderServiceImpl类，而Spring声明式事务是需要拿到这个类的代理对象才能实现
////             * 需要用spring创建的代理类才会具有事务功能
////             */
////
////            // 这个Api可以获取当前类的代理对象
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//
//
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        boolean isLock = lock.tryLock();
//
//        if (!isLock){
//            return Result.fail("不允许重复下单");
//        }
//
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//            return proxy.createVoucherOrder(voucherId);
//        }
//        finally {
//            lock.unlock();
//        }
//
//    }


}
