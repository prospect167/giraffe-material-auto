package com.prospect.giraffe.material.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 图片去重处理请求
 *
 * @author giraffe
 */
@Data
public class DeduplicationRequest {
    /**
     * 图片路径（必填）
     */
    @NotNull(message = "图片路径不能为空")
    private String imagePath;
    
    /**
     * 需要保留的文字列表（可选，为空则不保留任何文字）
     */
    private List<String> preserveTexts;
    
    /**
     * 是否去除水印（默认true）
     */
    private Boolean removeWatermarks = true;
    
    /**
     * 是否去除logo（默认true）
     */
    private Boolean removeLogos = true;
    
    /**
     * 是否去除多余文字（默认true）
     */
    private Boolean removeExtraTexts = true;
    
    /**
     * 输出目录（可选，默认与原图同目录）
     */
    private String outputDir;
    
    /**
     * 输出文件名（可选，默认"cleaned_原文件名"）
     */
    private String outputFileName;
}

