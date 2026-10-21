package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.CosUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/admin/common")
@Slf4j
public class CommonController {

    @Autowired
    private CosUtil cosUtil;

    /**
     * 文件上传
     * @param file
     * @return 文件可访问 URL
     */
    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file) {
        log.info("文件上传：{}", file.getOriginalFilename());
        try {
            // 获取文件的原始文件名
            String originalFilename = file.getOriginalFilename();
            // 获取文件的扩展名（后缀）
            String ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            // 生成一个随机的文件名，防止文件名冲突
            String objectName = UUID.randomUUID().toString() + ext;
            // 上传文件到 COS
            String url = cosUtil.upload(file.getBytes(), objectName);
            return Result.success(url);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error(MessageConstant.UPLOAD_FAILED + e.getMessage());
        }
    }
}
