package com.sky.utils;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;

@Data
@AllArgsConstructor
public class CosUtil {

    private static final Logger log = LoggerFactory.getLogger(CosUtil.class);

    private String secretId;
    private String secretKey;
    private String region;
    private String bucketName;

    /**
     * 文件上传
     *
     * @param bytes      文件字节数组
     * @param objectName COS 上的对象名（含路径，例如 "category/xxx.jpg"）
     * @return 可公开访问的 URL（要求桶权限为「公有读私有写」）
     */
    public String upload(byte[] bytes, String objectName) {

        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        COSClient cosClient = new COSClient(cred, clientConfig);

        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            PutObjectRequest putRequest = new PutObjectRequest(
                    bucketName, objectName, new ByteArrayInputStream(bytes), metadata);
            cosClient.putObject(putRequest);
        } catch (CosServiceException e) {
            log.error("上传到 COS 失败（服务端拒绝）: code={}, msg={}, reqId={}",
                    e.getErrorCode(), e.getErrorMessage(), e.getRequestId());
            throw new RuntimeException("COS 上传失败: " + e.getErrorMessage());
        } catch (CosClientException e) {
            log.error("上传到 COS 失败（客户端/网络错误）: {}", e.getMessage(), e);
            throw new RuntimeException("COS 上传失败: " + e.getMessage());
        } finally {
            cosClient.shutdown();
        }

        // 文件访问路径规则 https://BucketName.cos.Region.myqcloud.com/ObjectName
        String url = "https://" + bucketName + ".cos." + region + ".myqcloud.com/" + objectName;
        log.info("文件上传到:{}", url);

        return url;
    }
}
