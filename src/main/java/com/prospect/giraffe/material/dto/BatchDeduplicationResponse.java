package com.prospect.giraffe.material.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 批量图片去重处理响应
 *
 * @author giraffe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeduplicationResponse {
    /**
     * 是否全部处理成功
     */
    private Boolean success;

    /**
     * 源目录路径
     */
    private String sourceDirectory;

    /**
     * 输出目录路径
     */
    private String outputDirectory;

    /**
     * 总图片数
     */
    private Integer totalCount;

    /**
     * 处理成功数
     */
    private Integer successCount;

    /**
     * 处理失败数
     */
    private Integer failCount;

    /**
     * 跳过数（非图片文件）
     */
    private Integer skipCount;

    /**
     * 总耗时（毫秒）
     */
    private Long duration;

    /**
     * 使用的AI服务商
     */
    private String aiProvider;

    /**
     * 每张图片的处理详情
     */
    private List<ImageResult> results;

    /**
     * 错误信息（整体失败时）
     */
    private String errorMessage;

    /**
     * 单张图片处理结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageResult {
        /**
         * 原始文件名
         */
        private String fileName;

        /**
         * 处理是否成功
         */
        private Boolean success;

        /**
         * 处理后的文件路径
         */
        private String resultFile;

        /**
         * 原始文件大小（字节）
         */
        private Long originalSize;

        /**
         * 处理后文件大小（字节）
         */
        private Long processedSize;

        /**
         * 单张图片处理耗时（毫秒）
         */
        private Long duration;

        /**
         * 错误信息（失败时）
         */
        private String errorMessage;
    }
}

