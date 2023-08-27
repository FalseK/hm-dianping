package com.hmdp.service.impl;

import cn.hutool.core.lang.UUID;
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
import org.apache.tomcat.jni.Local;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {

        // 查询优惠券信息
        SeckillVoucher secKillVoucher = seckillVoucherService.getById(voucherId);

        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 判断秒杀是否开始

        if (secKillVoucher == null){
            return Result.fail("库存不足");
        }

        if (secKillVoucher.getBeginTime().isAfter(now)) {
            return Result.fail("秒杀还未开始");
        }

        if (secKillVoucher.getEndTime().isBefore(now)){
            return Result.fail("秒杀已经结束");
        }

        if (secKillVoucher.getStock() < 1){
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();



//        // toString()方法会new 一个字符串，造成同一个用户ID每次都是获取新的锁
//        // 需要用intern()方法在常量池中创建字符串，保证一个用户一把锁。
//        synchronized (userId.toString().intern()){
//            /*
//             * 这样调用会导致事务失效，原因是这里调用的方式是 this.createVoucherOrder();
//             * this是这个VoucherOrderServiceImpl类，而Spring声明式事务是需要拿到这个类的代理对象才能实现
//             * 需要用spring创建的代理类才会具有事务功能
//             */
//
//            // 这个Api可以获取当前类的代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//            return proxy.createVoucherOrder(voucherId);
//        }



        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock();

        if (!isLock){
            return Result.fail("不允许重复下单");
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();

            return proxy.createVoucherOrder(voucherId);
        }
        finally {
            lock.unlock();
        }

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

    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        Long userId = UserHolder.getUser().getId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        //查找该用户有没有重复下单
        if (count > 0){
            return Result.fail("你已经下过一次单");
        }

        // 修改库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();

        if (!success){
            return Result.fail("库存不足");
        }


        //下订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);

        save(voucherOrder);


        return Result.ok(orderId);
    }
}
