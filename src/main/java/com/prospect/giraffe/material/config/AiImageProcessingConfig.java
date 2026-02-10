package com.prospect.giraffe.material.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI图像处理配置
 *
 * @author giraffe
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai-image-processing")
public class AiImageProcessingConfig {
    /**
     * 是否启用
     */
    private Boolean enabled = true;
    
    /**
     * 默认服务商（主方案）
     */
    private String defaultProvider = "doubao";
    
    /**
     * 降级服务商（备选方案）
     */
    private String fallbackProvider = "aliyun";
    
    /**
     * 是否启用自动降级
     */
    private Boolean enableAutoFallback = true;
    
    /**
     * 超时时间（毫秒）
     */
    private Long timeout = 60000L;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetry = 2;
    
    /**
     * 豆包配置（主方案）- 火山引擎方舟平台 / Seedream 4.5
     */
    private DoubaoConfig doubao = new DoubaoConfig();
    
    /**
     * 阿里云配置（备选方案）
     */
    private AliyunConfig aliyun = new AliyunConfig();
    
    // 显式添加getter方法（确保Lombok正确生成）
    public DoubaoConfig getDoubao() {
        return doubao;
    }
    
    public AliyunConfig getAliyun() {
        return aliyun;
    }
    
    /**
     * 豆包配置 - 火山引擎方舟平台 / Seedream 4.5 模型
     */
    @Data
    public static class DoubaoConfig {
        /**
         * 是否启用
         */
        private Boolean enabled = true;
        
        /**
         * API密钥（从火山引擎方舟控制台获取）
         * 获取地址：https://console.volcengine.com/ark/region:ark+cn-beijing/apikey
         * 使用 Bearer Token 认证，只需要 API Key
         */
        private String apiKey;
        
        /**
         * API基础地址（火山引擎方舟平台）
         * 参考文档：https://www.volcengine.com/docs/82379/1824121?lang=zh
         * 图片生成接口：{baseUrl}/images/generations
         */
        private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
        
        /**
         * 使用的模型ID
         * 豆包 Seedream 4.5 模型ID：doubao-seedream-4-5-251128
         * 也可以使用模型端点ID：ep-{your-endpoint-id}
         * 参考文档：https://www.volcengine.com/docs/82379/1824121?lang=zh
         */
        private String model = "doubao-seedream-4-5-251128";
        
        /**
         * 生成图片尺寸（如：2K, 1024x1024 等）
         */
        private String size = "2K";
        
        /**
         * 图生图强度（0.0-1.0）
         * 值越小越接近原图（人物失真越小），值越大模型自由度越高（水印去更干净）
         * 去水印推荐 0.45~0.55，兼顾去水印效果和人物保真
         * 如果水印去不干净可调到 0.55-0.65，如果人物失真可调到 0.35-0.45
         */
        private Double strength = 0.50;
        
        /**
         * 区域（用于构建API地址）
         */
        private String region = "cn-beijing";
    }
    
    @Data
    public static class AliyunConfig {
        /**
         * 是否启用
         */
        private Boolean enabled = true;
        
        /**
         * AccessKey ID
         */
        private String accessKeyId;
        
        /**
         * AccessKey Secret
         */
        private String accessKeySecret;
        
        /**
         * 端点地址
         */
        private String endpoint = "dashscope.aliyuncs.com";
        
        /**
         * 使用的模型
         */
        private String model = "qwen-vl-plus";
    }
}
