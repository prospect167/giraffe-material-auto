package com.prospect.giraffe.material.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 水印去除配置
 *
 * @author giraffe
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "watermark-removal")
public class WatermarkRemovalConfig {

    /**
     * 全局开关（默认关闭）
     */
    private Boolean enabled = false;

    /**
     * 默认使用的厂商：aliyun, tencent, baidu, disabled
     */
    private String defaultProvider = "disabled";

    /**
     * 是否保存原图
     */
    private Boolean saveOriginal = false;

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout = 30000;

    /**
     * 最大重试次数
     */
    private Integer maxRetry = 2;

    /**
     * 阿里云配置
     */
    private AliyunConfig aliyun = new AliyunConfig();

    /**
     * 腾讯云配置
     */
    private TencentConfig tencent = new TencentConfig();

    /**
     * 百度云配置
     */
    private BaiduConfig baidu = new BaiduConfig();

    /**
     * 阿里云配置
     */
    @Data
    public static class AliyunConfig {
        /**
         * 是否启用
         */
        private Boolean enabled = false;

        /**
         * Access Key ID
         */
        private String accessKeyId;

        /**
         * Access Key Secret
         */
        private String accessKeySecret;

        /**
         * 服务端点
         */
        private String endpoint = "imageprocess.cn-shanghai.aliyuncs.com";

        /**
         * 每月免费额度提示
         */
        private Integer freeQuota = 1000;
    }

    /**
     * 腾讯云配置
     */
    @Data
    public static class TencentConfig {
        /**
         * 是否启用
         */
        private Boolean enabled = false;

        /**
         * Secret ID
         */
        private String secretId;

        /**
         * Secret Key
         */
        private String secretKey;

        /**
         * 地域
         */
        private String region = "ap-shanghai";

        /**
         * COS存储桶
         */
        private String bucket;

        /**
         * 每月免费额度提示
         */
        private Integer freeQuota = 1000;
    }

    /**
     * 百度云配置
     */
    @Data
    public static class BaiduConfig {
        /**
         * 是否启用
         */
        private Boolean enabled = false;

        /**
         * API Key
         */
        private String apiKey;

        /**
         * Secret Key
         */
        private String secretKey;

        /**
         * 每日免费额度提示
         */
        private Integer freeQuota = 500;
    }
}

