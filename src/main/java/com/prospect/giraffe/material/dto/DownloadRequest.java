package com.prospect.giraffe.material.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 下载请求
 *
 * @author giraffe
 */
@Data
public class DownloadRequest {

    /**
     * HTML页面URL
     */
    @NotBlank(message = "URL不能为空")
    private String url;

    /**
     * 目标目录（相对于basePath，可选）
     */
    private String targetDir;

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
     * - true: 启用水印去除
     * - false: 不处理水印
     * - null: 使用全局配置
     */
    private Boolean removeWatermark = false;

    /**
     * 水印去除服务商（可选）
     * - aliyun: 阿里云
     * - tencent: 腾讯云
     * - baidu: 百度智能云
     * - disabled: 禁用
     * - null: 使用全局默认配置
     */
    private String watermarkProvider;

    /**
     * 是否保存原图（可选）
     * - true: 保存原图和处理后的图片
     * - false: 只保存处理后的图片
     * - null: 使用全局配置
     */
    private Boolean saveOriginal;
}

