package com.prospect.giraffe.material.service.watermark.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 水印去除结果
 *
 * @author giraffe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatermarkRemovalResult {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 处理后的文件
     */
    private java.io.File resultFile;

    /**
     * 原始文件
     */
    private java.io.File originalFile;

    /**
     * 服务商名称
     */
    private String provider;

    /**
     * 处理耗时（毫秒）
     */
    private Long duration;

    /**
     * 错误信息
     */
    private String errorMessage;
}

