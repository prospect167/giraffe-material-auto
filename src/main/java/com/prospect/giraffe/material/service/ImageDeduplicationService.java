package com.prospect.giraffe.material.service;

import com.prospect.giraffe.material.config.AiImageProcessingConfig;
import com.prospect.giraffe.material.dto.BatchDeduplicationRequest;
import com.prospect.giraffe.material.dto.BatchDeduplicationResponse;
import com.prospect.giraffe.material.dto.DeduplicationRequest;
import com.prospect.giraffe.material.dto.DeduplicationResponse;
import com.prospect.giraffe.material.service.deduplication.IAIImageAdapter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图片去重处理服务
 *
 * @author giraffe
 */
@Slf4j
@Service
public class ImageDeduplicationService implements ApplicationContextAware {
    
    @Resource
    private AiImageProcessingConfig config;
    
    private ApplicationContext applicationContext;
    
    /**
     * 设置ApplicationContext，用于手动获取适配器
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    /**
     * 初始化时打印所有可用的适配器
     */
    @PostConstruct
    public void init() {
        // 从ApplicationContext获取所有适配器
        Map<String, IAIImageAdapter> adapters = applicationContext.getBeansOfType(IAIImageAdapter.class);
        log.info("图片去重服务初始化，从ApplicationContext找到 {} 个适配器", adapters.size());
        for (Map.Entry<String, IAIImageAdapter> entry : adapters.entrySet()) {
            log.info("适配器Bean名称: {}, 提供者: {}, 可用性: {}", 
                entry.getKey(), entry.getValue().getProviderName(), entry.getValue().isAvailable());
        }
        log.info("默认服务商: {}, 备选服务商: {}", config.getDefaultProvider(), config.getFallbackProvider());
    }
    
    /**
     * 处理图片去重
     *
     * @param request 去重请求
     * @return 处理结果
     */
    public DeduplicationResponse processImage(DeduplicationRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 验证图片文件存在
            File imageFile = new File(request.getImagePath());
            if (!imageFile.exists() || !imageFile.isFile()) {
                return DeduplicationResponse.builder()
                        .success(false)
                        .errorMessage("图片文件不存在: " + request.getImagePath())
                        .duration(System.currentTimeMillis() - startTime)
                        .build();
            }
            
            // 2. 读取图片文件
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            long originalSize = imageBytes.length;
            log.info("读取图片文件: {}, 大小: {} bytes", request.getImagePath(), originalSize);
            
            // 3. 获取适配器（支持自动降级）
            Map<String, IAIImageAdapter> allAdapters = applicationContext.getBeansOfType(IAIImageAdapter.class);
            log.info("开始查找适配器，从ApplicationContext找到 {} 个适配器", allAdapters.size());
            log.info("已注册的适配器Bean名称: {}", allAdapters.keySet());
            for (Map.Entry<String, IAIImageAdapter> entry : allAdapters.entrySet()) {
                log.info("适配器: {} -> 提供者: {}, 可用性: {}", 
                    entry.getKey(), entry.getValue().getProviderName(), entry.getValue().isAvailable());
            }
            log.info("配置的默认服务商: {}, 备选服务商: {}", config.getDefaultProvider(), config.getFallbackProvider());
            
            IAIImageAdapter adapter = getAdapterWithFallback();
            if (adapter == null) {
                String errorMsg = String.format("未找到可用的AI服务适配器。ApplicationContext中的适配器: %s, 默认服务商: %s, 备选服务商: %s", 
                    allAdapters.keySet(), config.getDefaultProvider(), config.getFallbackProvider());
                log.error(errorMsg);
                return DeduplicationResponse.builder()
                        .success(false)
                        .errorMessage(errorMsg)
                        .duration(System.currentTimeMillis() - startTime)
                        .build();
            }
            
            String usedProvider = adapter.getProviderName();
            log.info("使用AI服务商: {}", usedProvider);
            
            // 4. 调用AI处理（带重试和降级）
            byte[] processedBytes = null;
            
            try {
                processedBytes = adapter.processImage(
                        imageBytes,
                        request.getPreserveTexts(),
                        request.getRemoveWatermarks(),
                        request.getRemoveLogos(),
                        request.getRemoveExtraTexts()
                );
                log.info("主服务商 {} 处理成功", usedProvider);
            } catch (Exception e) {
                log.warn("主服务商 {} 处理失败: {}", usedProvider, e.getMessage());
                
                // 如果启用自动降级，尝试备选服务商
                if (config.getEnableAutoFallback() && !usedProvider.equals(config.getFallbackProvider())) {
                    log.info("尝试切换到备选服务商: {}", config.getFallbackProvider());
                    IAIImageAdapter fallbackAdapter = getAdapter(config.getFallbackProvider());
                    if (fallbackAdapter != null && fallbackAdapter.isAvailable()) {
                        try {
                            processedBytes = fallbackAdapter.processImage(
                                    imageBytes,
                                    request.getPreserveTexts(),
                                    request.getRemoveWatermarks(),
                                    request.getRemoveLogos(),
                                    request.getRemoveExtraTexts()
                            );
                            usedProvider = fallbackAdapter.getProviderName();
                            log.info("备选服务商 {} 处理成功", usedProvider);
                        } catch (Exception fallbackException) {
                            log.error("备选服务商也处理失败: {}", fallbackException.getMessage());
                            return DeduplicationResponse.builder()
                                    .success(false)
                                    .errorMessage("所有AI服务商处理失败: " + fallbackException.getMessage())
                                    .duration(System.currentTimeMillis() - startTime)
                                    .build();
                        }
                    } else {
                        return DeduplicationResponse.builder()
                                .success(false)
                                .errorMessage("主服务商失败且备选服务商不可用: " + e.getMessage())
                                .duration(System.currentTimeMillis() - startTime)
                                .build();
                    }
                } else {
                    return DeduplicationResponse.builder()
                            .success(false)
                            .errorMessage("处理失败: " + e.getMessage())
                            .duration(System.currentTimeMillis() - startTime)
                            .build();
                }
            }
            
            // 5. 保存处理后的图片
            String outputDir = request.getOutputDir() != null 
                    ? request.getOutputDir() 
                    : imageFile.getParent();
            String outputFileName = request.getOutputFileName() != null
                    ? request.getOutputFileName()
                    : "cleaned_" + imageFile.getName();
            
            File outputDirFile = new File(outputDir);
            if (!outputDirFile.exists()) {
                outputDirFile.mkdirs();
            }
            
            File outputFile = new File(outputDir, outputFileName);
            FileUtils.writeByteArrayToFile(outputFile, processedBytes);
            log.info("处理后的图片已保存: {}", outputFile.getAbsolutePath());
            
            // 6. 构建响应
            List<String> removedItems = new ArrayList<>();
            if (request.getRemoveWatermarks()) removedItems.add("水印");
            if (request.getRemoveLogos()) removedItems.add("logo");
            if (request.getRemoveExtraTexts()) removedItems.add("多余文字");
            
            long duration = System.currentTimeMillis() - startTime;
            
            return DeduplicationResponse.builder()
                    .success(true)
                    .resultFile(outputFile.getAbsolutePath())
                    .originalFile(imageFile.getAbsolutePath())
                    .preservedTexts(request.getPreserveTexts())
                    .removedItems(removedItems)
                    .duration(duration)
                    .aiProvider(usedProvider)
                    .imageSize(DeduplicationResponse.ImageSize.builder()
                            .original(originalSize)
                            .processed((long) processedBytes.length)
                            .build())
                    .build();
                    
        } catch (Exception e) {
            log.error("图片去重处理失败: {}", e.getMessage(), e);
            return DeduplicationResponse.builder()
                    .success(false)
                    .errorMessage("处理异常: " + e.getMessage())
                    .duration(System.currentTimeMillis() - startTime)
                    .build();
        }
    }
    
    /**
     * 支持的图片文件扩展名
     */
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp")
    );

    /**
     * 判断文件是否为图片
     */
    private boolean isImageFile(File file) {
        if (!file.isFile()) return false;
        String name = file.getName().toLowerCase();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx < 0) return false;
        return IMAGE_EXTENSIONS.contains(name.substring(dotIdx + 1));
    }

    /**
     * 批量处理目录下的所有图片
     *
     * @param request 批量去重请求
     * @return 批量处理结果
     */
    public BatchDeduplicationResponse processBatchImages(BatchDeduplicationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. 验证目录
            File sourceDir = new File(request.getDirectoryPath());
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                return BatchDeduplicationResponse.builder()
                        .success(false)
                        .errorMessage("目录不存在或不是有效目录: " + request.getDirectoryPath())
                        .duration(System.currentTimeMillis() - startTime)
                        .build();
            }

            // 2. 扫描目录下的图片文件
            File[] allFiles = sourceDir.listFiles();
            if (allFiles == null || allFiles.length == 0) {
                return BatchDeduplicationResponse.builder()
                        .success(false)
                        .errorMessage("目录为空: " + request.getDirectoryPath())
                        .duration(System.currentTimeMillis() - startTime)
                        .build();
            }

            List<File> imageFiles = Arrays.stream(allFiles)
                    .filter(this::isImageFile)
                    .sorted(Comparator.comparing(File::getName))
                    .collect(Collectors.toList());

            int skipCount = allFiles.length - imageFiles.size();
            log.info("目录扫描完成: {} 个文件, 其中 {} 个图片文件, {} 个非图片文件跳过",
                    allFiles.length, imageFiles.size(), skipCount);

            if (imageFiles.isEmpty()) {
                return BatchDeduplicationResponse.builder()
                        .success(false)
                        .errorMessage("目录下没有找到图片文件（支持格式: " + IMAGE_EXTENSIONS + "）")
                        .sourceDirectory(sourceDir.getAbsolutePath())
                        .totalCount(0)
                        .skipCount(skipCount)
                        .duration(System.currentTimeMillis() - startTime)
                        .build();
            }

            // 3. 创建输出子目录
            String outputDirName = request.getOutputDirName() != null ? request.getOutputDirName() : "cleaned";
            File outputDir = new File(sourceDir, outputDirName);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
                log.info("创建输出目录: {}", outputDir.getAbsolutePath());
            }

            // 4. 逐张处理
            List<BatchDeduplicationResponse.ImageResult> results = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;
            String usedProvider = null;

            for (int i = 0; i < imageFiles.size(); i++) {
                File imgFile = imageFiles.get(i);
                log.info("处理进度: [{}/{}] 文件: {}", i + 1, imageFiles.size(), imgFile.getName());

                long imgStartTime = System.currentTimeMillis();

                // 复用单张处理逻辑
                DeduplicationRequest singleRequest = new DeduplicationRequest();
                singleRequest.setImagePath(imgFile.getAbsolutePath());
                singleRequest.setPreserveTexts(request.getPreserveTexts());
                singleRequest.setRemoveWatermarks(request.getRemoveWatermarks());
                singleRequest.setRemoveLogos(request.getRemoveLogos());
                singleRequest.setRemoveExtraTexts(request.getRemoveExtraTexts());
                singleRequest.setOutputDir(outputDir.getAbsolutePath());
                singleRequest.setOutputFileName(imgFile.getName()); // 保持原文件名

                DeduplicationResponse singleResponse = processImage(singleRequest);
                long imgDuration = System.currentTimeMillis() - imgStartTime;

                if (singleResponse.getSuccess()) {
                    successCount++;
                    if (usedProvider == null) {
                        usedProvider = singleResponse.getAiProvider();
                    }
                    results.add(BatchDeduplicationResponse.ImageResult.builder()
                            .fileName(imgFile.getName())
                            .success(true)
                            .resultFile(singleResponse.getResultFile())
                            .originalSize(singleResponse.getImageSize() != null ? singleResponse.getImageSize().getOriginal() : null)
                            .processedSize(singleResponse.getImageSize() != null ? singleResponse.getImageSize().getProcessed() : null)
                            .duration(imgDuration)
                            .build());
                    log.info("图片处理成功: {} (耗时 {}ms)", imgFile.getName(), imgDuration);
                } else {
                    failCount++;
                    results.add(BatchDeduplicationResponse.ImageResult.builder()
                            .fileName(imgFile.getName())
                            .success(false)
                            .errorMessage(singleResponse.getErrorMessage())
                            .duration(imgDuration)
                            .build());
                    log.warn("图片处理失败: {} - {}", imgFile.getName(), singleResponse.getErrorMessage());
                }
            }

            long totalDuration = System.currentTimeMillis() - startTime;
            log.info("批量处理完成: 总计 {} 张, 成功 {} 张, 失败 {} 张, 耗时 {}ms",
                    imageFiles.size(), successCount, failCount, totalDuration);

            return BatchDeduplicationResponse.builder()
                    .success(failCount == 0)
                    .sourceDirectory(sourceDir.getAbsolutePath())
                    .outputDirectory(outputDir.getAbsolutePath())
                    .totalCount(imageFiles.size())
                    .successCount(successCount)
                    .failCount(failCount)
                    .skipCount(skipCount)
                    .duration(totalDuration)
                    .aiProvider(usedProvider)
                    .results(results)
                    .build();

        } catch (Exception e) {
            log.error("批量图片去重处理失败: {}", e.getMessage(), e);
            return BatchDeduplicationResponse.builder()
                    .success(false)
                    .errorMessage("批量处理异常: " + e.getMessage())
                    .duration(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * 获取适配器（优先使用默认服务商）
     * 注意：即使isAvailable()返回false，也允许使用适配器（在实际调用时再检查API密钥）
     */
    private IAIImageAdapter getAdapterWithFallback() {
        // 优先使用默认服务商
        IAIImageAdapter adapter = getAdapter(config.getDefaultProvider());
        if (adapter != null) {
            if (adapter.isAvailable()) {
                log.info("使用默认服务商: {} (已配置API密钥)", adapter.getProviderName());
                return adapter;
            } else {
                log.warn("默认服务商 {} 已注册但API密钥未配置，仍将尝试使用（调用时可能失败）", adapter.getProviderName());
                // 即使API密钥未配置，也允许使用适配器（在实际调用时再检查）
                return adapter;
            }
        } else {
            log.warn("默认服务商 {} 未找到适配器", config.getDefaultProvider());
        }
        
        // 如果默认服务商不可用，使用备选服务商
        if (config.getEnableAutoFallback()) {
            adapter = getAdapter(config.getFallbackProvider());
            if (adapter != null) {
                if (adapter.isAvailable()) {
                    log.warn("默认服务商不可用，使用备选服务商: {} (已配置API密钥)", adapter.getProviderName());
                    return adapter;
                } else {
                    log.warn("备选服务商 {} 已注册但API密钥未配置，仍将尝试使用（调用时可能失败）", adapter.getProviderName());
                    // 即使API密钥未配置，也允许使用适配器（在实际调用时再检查）
                    return adapter;
                }
            } else {
                log.warn("备选服务商 {} 未找到适配器", config.getFallbackProvider());
            }
        }
        
        return null;
    }
    
    /**
     * 根据服务商名称获取适配器
     */
    private IAIImageAdapter getAdapter(String provider) {
        String beanName = provider + "Adapter";
        log.debug("查找适配器: Bean名称 = {}", beanName);
        
        // 方法1: 直接通过Bean名称获取
        try {
            IAIImageAdapter adapter = applicationContext.getBean(beanName, IAIImageAdapter.class);
            log.debug("通过Bean名称找到适配器: {}", beanName);
            return adapter;
        } catch (BeansException e) {
            log.debug("通过Bean名称未找到适配器: {}, 错误: {}", beanName, e.getMessage());
        }
        
        // 方法2: 遍历所有适配器，通过provider名称匹配
        Map<String, IAIImageAdapter> allAdapters = applicationContext.getBeansOfType(IAIImageAdapter.class);
        for (Map.Entry<String, IAIImageAdapter> entry : allAdapters.entrySet()) {
            if (entry.getValue().getProviderName().equals(provider) || 
                entry.getValue().getProviderName().equals(provider + "-tongyi") ||
                entry.getKey().equals(beanName)) {
                log.debug("通过provider名称找到适配器: {} -> {}", entry.getKey(), entry.getValue().getProviderName());
                return entry.getValue();
            }
        }
        
        log.warn("未找到适配器: provider={}, beanName={}, 当前ApplicationContext中的适配器: {}", 
            provider, beanName, allAdapters.keySet());
        return null;
    }
}

