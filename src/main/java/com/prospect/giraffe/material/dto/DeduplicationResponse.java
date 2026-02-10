package com.prospect.giraffe.material.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 图片去重处理响应
 *
 * @author giraffe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeduplicationResponse {
    /**
     * 是否处理成功
     */
    private Boolean success;
    
    /**
     * 处理后的图片路径
     */
    private String resultFile;
    
    /**
     * 原始图片路径
     */
    private String originalFile;
    
    /**
     * 成功保留的文字列表
     */
    private List<String> preservedTexts;
    
    /**
     * 已去除的内容类型
     */
    private List<String> removedItems;
    
    /**
     * 处理耗时（毫秒）
     */
    private Long duration;
    
    /**
     * 使用的AI服务商
     */
    private String aiProvider;
    
    /**
     * 图片大小信息
     */
    private ImageSize imageSize;
    
    /**
     * 错误信息（失败时）
     */
    private String errorMessage;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageSize {
        /**
         * 原始图片大小（字节）
         */
        private Long original;
        
        /**
         * 处理后图片大小（字节）
         */
        private Long processed;
    }
}

