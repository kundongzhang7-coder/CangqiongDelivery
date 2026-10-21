package com.sky.config;

import com.sky.properties.CosProperties;
import com.sky.utils.CosUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 COS 配置类：把 sky.cos.* 注入成 CosUtil Bean
 */
@Slf4j
@Configuration
public class CosConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CosUtil cosUtil(CosProperties cosProperties) {
        log.info("开始创建腾讯云文件上传工具类对象：");
        return new CosUtil(
                cosProperties.getSecretId(),
                cosProperties.getSecretKey(),
                cosProperties.getRegion(),
                cosProperties.getBucketName());
    }
}
