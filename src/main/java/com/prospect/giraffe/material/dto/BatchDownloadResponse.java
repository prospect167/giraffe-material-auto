package com.prospect.giraffe.material.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量下载响应
 *
 * @author giraffe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDownloadResponse {

    /**
     * 是否成功（所有页面都成功才算成功）
     */
    private Boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 总页面数
     */
    private Integer totalPages;

    /**
     * 成功页面数
     */
    private Integer successPages;

    /**
     * 失败页面数
     */
    private Integer failPages;

    /**
     * 总图片数（所有页面）
     */
    private Integer totalImages;

    /**
     * 成功下载图片数
     */
    private Integer successImages;

    /**
     * 失败图片数
     */
    private Integer failImages;

    /**
     * 总耗时（毫秒）
     */
    private Long totalDuration;

    /**
     * 每个页面的下载结果详情
     */
    private List<PageDownloadResult> pageResults;

    /**
     * 单个页面的下载结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageDownloadResult {
        /**
         * 页面URL
         */
        private String url;

        /**
         * 是否成功
         */
        private Boolean success;

        /**
         * 消息
         */
        private String message;

        /**
         * 该页面的图片总数
         */
        private Integer totalCount;

        /**
         * 该页面的成功数
         */
        private Integer successCount;

        /**
         * 该页面的失败数
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
         * 该页面的耗时（毫秒）
         */
        private Long duration;

        /**
         * 水印处理统计
         */
        private DownloadResponse.WatermarkRemovalStats watermarkStats;
    }
}

