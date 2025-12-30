package com.prospect.giraffe.material.service;

import com.prospect.giraffe.material.config.WatermarkRemovalConfig;
import com.prospect.giraffe.material.service.watermark.dto.WatermarkRemovalResult;
import com.prospect.giraffe.material.service.watermark.IWatermarkRemovalAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 水印去除统一服务
 *
 * @author giraffe
 */
@Slf4j
@Service
public class WatermarkRemovalService {

    @Autowired
    private WatermarkRemovalConfig config;

    @Autowired(required = false)
    private Map<String, IWatermarkRemovalAdapter> adapters = new ConcurrentHashMap<>();

    // 使用量统计
    private final Map<String, AtomicInteger> usageCount = new ConcurrentHashMap<>();

    /**
     * 去除水印
     *
     * @param inputFile     输入文件
     * @param outputDir     输出目录（去水印后的图片保存位置）
     * @param provider      指定的服务商（可选）
     * @param saveOriginal  是否保存原图（可选，此参数已不使用，原图由调用方管理）
     * @return 处理结果
     */
    public WatermarkRemovalResult removeWatermark(File inputFile, String outputDir, String provider, Boolean saveOriginal) {
        long startTime = System.currentTimeMillis();

        // 检查是否启用
        if (!config.getEnabled() && (provider == null || "disabled".equals(provider))) {
            log.debug("水印去除服务未启用，跳过处理");
            return WatermarkRemovalResult.builder()
                    .success(false)
                    .resultFile(inputFile)
                    .originalFile(inputFile)
                    .provider("disabled")
                    .errorMessage("服务未启用")
                    .duration(0L)
                    .build();
        }

        // 确定使用的服务商
        String actualProvider = provider != null ? provider : config.getDefaultProvider();
        if ("disabled".equals(actualProvider)) {
            log.debug("服务商设置为 disabled，跳过处理");
            return WatermarkRemovalResult.builder()
                    .success(false)
                    .resultFile(inputFile)
                    .originalFile(inputFile)
                    .provider("disabled")
                    .errorMessage("服务商设置为 disabled")
                    .duration(0L)
                    .build();
        }

        // 获取适配器
        IWatermarkRemovalAdapter adapter = getAdapter(actualProvider);
        if (adapter == null) {
            log.warn("未找到服务商 {} 的适配器或服务未启用", actualProvider);
            return WatermarkRemovalResult.builder()
                    .success(false)
                    .resultFile(inputFile)
                    .originalFile(inputFile)
                    .provider(actualProvider)
                    .errorMessage("服务商不可用: " + actualProvider)
                    .duration(System.currentTimeMillis() - startTime)
                    .build();
        }

        try {
            // 去除水印（带重试）- 传入输出目录
            File resultFile = removeWatermarkWithRetry(adapter, inputFile, outputDir);

            // 记录使用量
            recordUsage(actualProvider);

            long duration = System.currentTimeMillis() - startTime;
            log.info("水印去除成功: provider={}, file={}, duration={}ms",
                    actualProvider, inputFile.getName(), duration);

            return WatermarkRemovalResult.builder()
                    .success(true)
                    .resultFile(resultFile)
                    .originalFile(inputFile)
                    .provider(actualProvider)
                    .duration(duration)
                    .build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("水印去除失败: provider={}, file={}, error={}",
                    actualProvider, inputFile.getName(), e.getMessage());

            return WatermarkRemovalResult.builder()
                    .success(false)
                    .resultFile(inputFile)
                    .originalFile(inputFile)
                    .provider(actualProvider)
                    .errorMessage(e.getMessage())
                    .duration(duration)
                    .build();
        }
    }

    /**
     * 带重试的水印去除
     */
    private File removeWatermarkWithRetry(IWatermarkRemovalAdapter adapter, File inputFile, String outputDir) throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= config.getMaxRetry()) {
            try {
                // 调用适配器去除水印
                File tempResultFile = adapter.removeWatermark(inputFile);
                
                // 将结果文件移动到指定的输出目录
                File finalResultFile = new File(outputDir, inputFile.getName());
                if (!tempResultFile.equals(finalResultFile)) {
                    org.apache.commons.io.FileUtils.copyFile(tempResultFile, finalResultFile);
                    // 删除临时文件
                    if (tempResultFile.getParent().equals(inputFile.getParent())) {
                        tempResultFile.delete();
                    }
                }
                
                return finalResultFile;
            } catch (Exception e) {
                lastException = e;
                retryCount++;

                if (retryCount <= config.getMaxRetry()) {
                    log.warn("水印去除失败，正在重试 ({}/{}): {}",
                            retryCount, config.getMaxRetry(), e.getMessage());
                    try {
                        Thread.sleep(2000); // 等待2秒后重试
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("水印去除被中断", ie);
                    }
                }
            }
        }

        throw new Exception("水印去除失败，已重试" + config.getMaxRetry() + "次", lastException);
    }

    /**
     * 获取适配器
     */
    private IWatermarkRemovalAdapter getAdapter(String provider) {
        String beanName = provider + "WatermarkAdapter";
        IWatermarkRemovalAdapter adapter = adapters.get(beanName);

        if (adapter != null && adapter.isAvailable()) {
            return adapter;
        }

        return null;
    }

    /**
     * 记录使用量
     */
    private void recordUsage(String provider) {
        usageCount.computeIfAbsent(provider, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * 获取使用量统计
     */
    public int getUsageCount(String provider) {
        return usageCount.getOrDefault(provider, new AtomicInteger(0)).get();
    }

    /**
     * 获取所有服务商的使用量统计
     */
    public Map<String, Integer> getAllUsageCount() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        usageCount.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
}

