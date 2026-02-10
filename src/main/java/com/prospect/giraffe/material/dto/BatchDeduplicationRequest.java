package com.prospect.giraffe.material.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 批量图片去重处理请求
 *
 * @author giraffe
 */
@Data
public class BatchDeduplicationRequest {
    /**
     * 图片目录路径（必填）
     */
    @NotBlank(message = "图片目录路径不能为空")
    private String directoryPath;

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
     * 输出子目录名称（可选，默认"cleaned"）
     * 会在 directoryPath 下创建该子目录存放处理后的图片
     */
    private String outputDirName = "cleaned";
}

