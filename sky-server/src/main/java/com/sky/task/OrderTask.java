package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，用于订单支付超时取消和用户收货超时自动确认
 */
@Component
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;

    /**
     * 定时任务，用于处理订单支付超时取消
     */
    @Scheduled(cron = "0 * * * * ?")// 每分钟执行一次
    public void processTimeoutOrder() {
        log.info("订单支付超时取消任务执行...{}", LocalDateTime.now());

        LocalDateTime time =  LocalDateTime.now().plusMinutes(-15);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT, time);

        if (ordersList != null && ordersList.size() > 0){
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelTime(LocalDateTime.now());
                orders.setCancelReason("订单支付超时，系统自动取消");
                orderMapper.update(orders);
            }
        }
    }

    /**
     * 定时任务，用于处理用户收货超时自动确认
     */
    @Scheduled(cron = "0 0 1 * * ?")// 每天凌晨1点执行一次
    public void processTimeoutDelivery() {
        log.info("用户收货超时自动确认任务执行...{}", LocalDateTime.now());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS, time);

        if (ordersList != null && ordersList.size() > 0){
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }
}
