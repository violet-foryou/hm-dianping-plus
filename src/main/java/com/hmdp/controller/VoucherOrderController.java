package com.hmdp.controller;


import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    @SentinelResource(value = "seckillVoucher", fallback = "handleSeckillFallback")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) throws InterruptedException {
        return voucherOrderService.seckillVoucher(voucherId);
    }
    /**
     * 熔断降级/异常兜底方法
     * 参数类型最后加上 Throwable，用来捕获所有异常
     */
    public Result handleSeckillFallback(@PathVariable("id") Long voucherId, Throwable throwable) {
        // 打印异常信息方便排查代码错误
        throwable.printStackTrace();
        return Result.fail("抢券系统开小差了，当前服务不可用，请稍后重试！");
    }
}
