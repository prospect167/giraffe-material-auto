package com.prospect.giraffe.material.service.watermark;

import java.io.File;

/**
 * 水印去除适配器接口
 *
 * @author giraffe
 */
public interface IWatermarkRemovalAdapter {

    /**
     * 去除水印
     *
     * @param inputFile 输入文件
     * @return 处理后的文件
     * @throws Exception 异常
     */
    File removeWatermark(File inputFile) throws Exception;

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

