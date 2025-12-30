package com.prospect.giraffe.material.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 下载响应
 *
 * @author giraffe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 总图片数
     */
    private Integer totalCount;

    /**
     * 成功下载数
     */
    private Integer successCount;

    /**
     * 失败数
     */
    private Integer failCount;

    /**
     * 保存路径
     */
    private String savePath;

    /**
     * 失败的图片URL列表
     */
    private List<String> failedUrls;

    /**
     * 耗时（毫秒）
     */
    private Long duration;

    /**
     * 水印处理统计
     */
    private WatermarkRemovalStats watermarkStats;

    /**
     * 水印去除统计信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WatermarkRemovalStats {
        /**
         * 是否启用了水印去除
         */
        private Boolean enabled;

        /**
         * 使用的服务商
         */
        private String provider;

        /**
         * 处理的图片数量
         */
        private Integer processedCount;

        /**
         * 成功数量
         */
        private Integer successCount;

        /**
         * 失败数量
         */
        private Integer failCount;

        /**
         * 平均处理时间（毫秒）
         */
        private Long avgProcessTime;

        /**
         * 失败的原因列表
         */
        private List<String> failureReasons;
    }
}

