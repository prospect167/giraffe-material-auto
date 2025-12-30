package com.prospect.giraffe.material.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 下载配置
 *
 * @author giraffe
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "material.download")
public class DownloadConfig {

    /**
     * 基础下载路径
     */
    private String basePath = "./downloads";

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout = 30000;

    /**
     * 最大重试次数
     */
    private Integer maxRetry = 3;

    /**
     * User-Agent
     */
    private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
}

