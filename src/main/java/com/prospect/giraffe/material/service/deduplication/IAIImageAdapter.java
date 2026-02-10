package com.prospect.giraffe.material.service.deduplication;

import java.util.List;

/**
 * AI图像处理适配器接口
 *
 * @author giraffe
 */
public interface IAIImageAdapter {
    /**
     * 处理图片去重
     *
     * @param imageBytes 图片字节数组
     * @param preserveTexts 需要保留的文字列表
     * @param removeWatermarks 是否去除水印
     * @param removeLogos 是否去除logo
     * @param removeExtraTexts 是否去除多余文字
     * @return 处理后的图片字节数组
     * @throws Exception 处理异常
     */
    byte[] processImage(byte[] imageBytes, List<String> preserveTexts, 
                       Boolean removeWatermarks, Boolean removeLogos, 
                       Boolean removeExtraTexts) throws Exception;
    
    /**
     * 获取服务商名称
     *
     * @return 服务商名称
     */
    String getProviderName();
    
    /**
     * 检查服务是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();
}

