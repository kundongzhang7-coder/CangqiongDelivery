package com.sky.task;

import com.sky.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class WebSocketTask {
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 通过WebSocket每隔5秒向客户端发送消息
     * 【注意】这是课程里用来测试WebSocket连通性的演示任务！
     * 它会每5秒向所有客户端广播一条测试消息，管理端前端收到任何消息都会触发来单语音，
     * 所以只要它开着，商家端就会每5秒响一次，误以为“支付成功接口在反复调用”。
     * 测试完连通性后必须停掉（注释掉@Scheduled即可），生产环境绝对不能开启。
     */
    //@Scheduled(cron = "0/5 * * * * ?")
    public void sendMessageToClient() {
        webSocketServer.sendToAllClient("这是来自服务端的消息：" + DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now()));
    }
}
