package com.prospect.giraffe.material.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 批量下载请求
 *
 * @author giraffe
 */
@Data
public class BatchDownloadRequest {

    /**
     * 页面URL列表（必填）
     */
    @NotEmpty(message = "URL列表不能为空")
    @NotNull(message = "URL列表不能为null")
    private List<String> urls;

    /**
     * 目标目录（可选）
     * - 相对路径：相对于 basePath，如 "my_photos"
     * - 绝对路径：以 / 开头，如 "/Users/xxx/photos"
     * - null: 直接使用 basePath
     */
    private String targetDir;

    /**
     * 完整保存路径（可选，优先级最高）
     * 如果指定此参数，将忽略 targetDir 和 basePath
     * 支持绝对路径和相对路径
     */
    private String savePath;

    /**
     * 是否添加时间戳子目录（默认true）
     */
    private Boolean useTimestamp = true;

    /**
     * 自定义时间戳格式（可选）
     * 默认：yyyyMMdd_HHmmss
     */
    private String timestampFormat;

    /**
     * 是否转换为JPEG格式（默认true）
     */
    private Boolean convertToJpeg = true;

    /**
     * 是否自动爬取所有分页（默认false）
     */
    private Boolean crawlAllPages = false;

    /**
     * 最大爬取页数（防止无限爬取，默认50）
     */
    private Integer maxPages = 50;

    /**
     * 是否去除水印（默认false）
     */
    private Boolean removeWatermark = false;

    /**
     * 水印去除服务商（可选）
     */
    private String watermarkProvider;

    /**
     * 是否保存原图（可选）
     */
    private Boolean saveOriginal;

    /**
     * 是否并发下载（默认true）
     * - true: 多个页面并发下载
     * - false: 串行下载
     */
    private Boolean concurrent = true;

    /**
     * 最大并发数（默认3）
     * 仅在 concurrent=true 时生效
     */
    private Integer maxConcurrency = 3;
}

