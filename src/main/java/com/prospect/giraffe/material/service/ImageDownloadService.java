package com.prospect.giraffe.material.service;

import com.prospect.giraffe.material.config.DownloadConfig;
import com.prospect.giraffe.material.dto.DownloadRequest;
import com.prospect.giraffe.material.dto.DownloadResponse;
import com.prospect.giraffe.material.service.watermark.dto.WatermarkRemovalResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 图片下载服务
 *
 * @author giraffe
 */
@Slf4j
@Service
public class ImageDownloadService {

    @Resource
    private DownloadConfig downloadConfig;

    @Resource
    private WatermarkRemovalService watermarkRemovalService;

    /**
     * 下载图片
     *
     * @param request 下载请求
     * @return 下载结果
     */
    public DownloadResponse downloadImages(DownloadRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("开始下载图片，URL: {}, 爬取所有分页: {}", request.getUrl(), request.getCrawlAllPages());

        DownloadResponse.DownloadResponseBuilder builder = DownloadResponse.builder();
        List<String> failedUrls = new ArrayList<>();

        try {
            // 1. 解析HTML页面，提取图片URL
            Set<String> imageUrls;
            if (request.getCrawlAllPages() != null && request.getCrawlAllPages()) {
                // 爬取所有分页
                imageUrls = parseAllPagesImageUrls(request.getUrl(), request.getMaxPages());
            } else {
                // 只爬取当前页
                imageUrls = parseImageUrls(request.getUrl());
            }
            log.info("从页面中提取到 {} 个图片URL", imageUrls.size());

            if (imageUrls.isEmpty()) {
                return builder
                        .success(false)
                        .message("未找到任何图片")
                        .totalCount(0)
                        .successCount(0)
                        .failCount(0)
                        .duration(System.currentTimeMillis() - startTime)
                        .build();
            }

            // 2. 创建目标目录
            String baseSavePath = createTargetDirectory(request.getTargetDir());
            
            // 如果启用了水印去除，创建两个子目录
            boolean watermarkEnabled = request.getRemoveWatermark() != null && request.getRemoveWatermark();
            String originalPath = baseSavePath;
            String cleanedPath = baseSavePath;
            
            if (watermarkEnabled) {
                originalPath = baseSavePath + File.separator + "original";
                cleanedPath = baseSavePath + File.separator + "cleaned";
                Files.createDirectories(Paths.get(originalPath));
                Files.createDirectories(Paths.get(cleanedPath));
                log.info("原图保存到: {}", originalPath);
                log.info("去水印图片保存到: {}", cleanedPath);
            } else {
                log.info("图片将保存到: {}", baseSavePath);
            }

            // 3. 下载图片
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // 水印去除统计
            AtomicInteger watermarkProcessedCount = new AtomicInteger(0);
            AtomicInteger watermarkSuccessCount = new AtomicInteger(0);
            AtomicInteger watermarkFailCount = new AtomicInteger(0);
            AtomicLong watermarkTotalTime = new AtomicLong(0);
            List<String> watermarkFailureReasons = new ArrayList<>();

            for (String imageUrl : imageUrls) {
                try {
                    // 下载到原图目录
                    File downloadedFile = downloadSingleImage(imageUrl, originalPath, request.getConvertToJpeg());

                    // 去除水印（如果启用）
                    if (watermarkEnabled && downloadedFile != null) {
                        watermarkProcessedCount.incrementAndGet();
                        
                        // 调用水印去除服务，指定输出到 cleaned 目录
                        WatermarkRemovalResult watermarkResult = watermarkRemovalService.removeWatermark(
                                downloadedFile,
                                cleanedPath,
                                request.getWatermarkProvider(),
                                request.getSaveOriginal()
                        );

                        if (watermarkResult.getSuccess()) {
                            watermarkSuccessCount.incrementAndGet();
                            watermarkTotalTime.addAndGet(watermarkResult.getDuration());
                        } else {
                            watermarkFailCount.incrementAndGet();
                            watermarkFailureReasons.add(watermarkResult.getErrorMessage());
                        }
                    }

                    successCount.incrementAndGet();
                    log.debug("成功下载: {}", imageUrl);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    failedUrls.add(imageUrl);
                    log.error("下载失败: {}, 错误: {}", imageUrl, e.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("下载完成，总数: {}, 成功: {}, 失败: {}, 耗时: {}ms",
                    imageUrls.size(), successCount.get(), failCount.get(), duration);

            // 构建水印处理统计
            DownloadResponse.WatermarkRemovalStats watermarkStats = null;
            if (watermarkEnabled) {
                long avgTime = watermarkProcessedCount.get() > 0
                        ? watermarkTotalTime.get() / watermarkProcessedCount.get()
                        : 0;

                watermarkStats = DownloadResponse.WatermarkRemovalStats.builder()
                        .enabled(true)
                        .provider(request.getWatermarkProvider())
                        .processedCount(watermarkProcessedCount.get())
                        .successCount(watermarkSuccessCount.get())
                        .failCount(watermarkFailCount.get())
                        .avgProcessTime(avgTime)
                        .failureReasons(watermarkFailureReasons)
                        .build();

                log.info("水印去除统计: 处理={}, 成功={}, 失败={}, 平均耗时={}ms",
                        watermarkProcessedCount.get(), watermarkSuccessCount.get(),
                        watermarkFailCount.get(), avgTime);
            }

            // 返回结果，根据是否启用水印去除返回不同的路径信息
            String resultPath = watermarkEnabled 
                    ? baseSavePath + " (原图: original/, 去水印: cleaned/)"
                    : baseSavePath;

            return builder
                    .success(true)
                    .message("下载完成")
                    .totalCount(imageUrls.size())
                    .successCount(successCount.get())
                    .failCount(failCount.get())
                    .savePath(resultPath)
                    .failedUrls(failedUrls)
                    .duration(duration)
                    .watermarkStats(watermarkStats)
                    .build();

        } catch (Exception e) {
            log.error("下载过程发生错误", e);
            return builder
                    .success(false)
                    .message("下载失败: " + e.getMessage())
                    .totalCount(0)
                    .successCount(0)
                    .failCount(0)
                    .failedUrls(failedUrls)
                    .duration(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * 爬取所有分页的图片URL
     *
     * @param firstPageUrl 第一页URL
     * @param maxPages     最大页数
     * @return 所有图片URL集合
     * @throws IOException IO异常
     */
    private Set<String> parseAllPagesImageUrls(String firstPageUrl, Integer maxPages) throws IOException {
        Set<String> allImageUrls = new HashSet<>();
        Set<String> visitedPages = new HashSet<>();
        
        // 获取第一页
        log.info("开始爬取第 1 页: {}", firstPageUrl);
        Document firstDoc = Jsoup.connect(firstPageUrl)
                .userAgent(downloadConfig.getUserAgent())
                .timeout(downloadConfig.getTimeout())
                .get();
        
        // 提取第一页的图片
        Set<String> firstPageImages = extractImagesFromDocument(firstDoc, firstPageUrl);
        allImageUrls.addAll(firstPageImages);
        visitedPages.add(firstPageUrl);
        log.info("第 1 页找到 {} 个图片", firstPageImages.size());
        
        // 查找所有分页链接
        List<String> pageUrls = extractPaginationUrls(firstDoc, firstPageUrl);
        log.info("找到 {} 个分页链接", pageUrls.size());
        
        // 限制最大页数
        int actualMaxPages = maxPages != null ? maxPages : 50;
        int pageCount = 1;
        
        // 爬取其他页面
        for (String pageUrl : pageUrls) {
            if (pageCount >= actualMaxPages) {
                log.warn("已达到最大页数限制: {}", actualMaxPages);
                break;
            }
            
            if (visitedPages.contains(pageUrl)) {
                continue;
            }
            
            try {
                pageCount++;
                log.info("开始爬取第 {} 页: {}", pageCount, pageUrl);
                
                // 添加延迟，避免请求过快
                Thread.sleep(1000);
                
                Document doc = Jsoup.connect(pageUrl)
                        .userAgent(downloadConfig.getUserAgent())
                        .timeout(downloadConfig.getTimeout())
                        .get();
                
                Set<String> pageImages = extractImagesFromDocument(doc, pageUrl);
                allImageUrls.addAll(pageImages);
                visitedPages.add(pageUrl);
                
                log.info("第 {} 页找到 {} 个图片，累计 {} 个", pageCount, pageImages.size(), allImageUrls.size());
                
            } catch (Exception e) {
                log.error("爬取页面失败: {}, 错误: {}", pageUrl, e.getMessage());
            }
        }
        
        log.info("所有分页爬取完成，共爬取 {} 页，总计 {} 个图片", pageCount, allImageUrls.size());
        return allImageUrls;
    }
    
    /**
     * 提取分页链接
     *
     * @param doc     文档对象
     * @param baseUrl 基础URL
     * @return 分页URL列表
     */
    private List<String> extractPaginationUrls(Document doc, String baseUrl) {
        List<String> pageUrls = new ArrayList<>();
        Set<String> uniqueUrls = new HashSet<>();
        
        // 方法1: 查找常见的分页选择器
        String[] paginationSelectors = {
                "div.paginator a",           // 豆瓣风格
                "div.pagination a",          // 通用
                "ul.pagination a",
                "div.page a",
                "div.pages a",
                "a[href*=page]",
                "a[href*=start]",
                "a.next",                    // 下一页链接
                ".page-link"
        };
        
        for (String selector : paginationSelectors) {
            Elements links = doc.select(selector);
            for (Element link : links) {
                String href = link.absUrl("href");
                if (href != null && !href.isEmpty() && uniqueUrls.add(href)) {
                    // 过滤掉无效链接
                    if (!href.equals(baseUrl) && href.startsWith("http")) {
                        pageUrls.add(href);
                    }
                }
            }
        }
        
        // 方法2: 智能生成分页URL（针对豆瓣等网站）
        if (pageUrls.isEmpty()) {
            pageUrls.addAll(generatePaginationUrls(doc, baseUrl));
        }
        
        return pageUrls;
    }
    
    /**
     * 智能生成分页URL
     *
     * @param doc     文档对象
     * @param baseUrl 基础URL
     * @return 分页URL列表
     */
    private List<String> generatePaginationUrls(Document doc, String baseUrl) {
        List<String> urls = new ArrayList<>();
        
        // 尝试从HTML中获取总页数或总数量
        Elements paginator = doc.select("div.paginator, div.pagination");
        if (!paginator.isEmpty()) {
            String text = paginator.text();
            // 尝试提取数字，比如 "共243张"
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("共(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                int total = Integer.parseInt(matcher.group(1));
                int perPage = 30; // 豆瓣默认每页30张
                int totalPages = (int) Math.ceil(total / (double) perPage);
                
                log.info("检测到总图片数: {}, 预计页数: {}", total, totalPages);
                
                // 生成分页URL
                for (int i = 1; i < totalPages; i++) {
                    String pageUrl;
                    if (baseUrl.contains("?")) {
                        pageUrl = baseUrl + "&start=" + (i * perPage);
                    } else {
                        pageUrl = baseUrl + "?start=" + (i * perPage);
                    }
                    urls.add(pageUrl);
                }
            }
        }
        
        return urls;
    }
    
    /**
     * 从Document中提取图片URL
     *
     * @param doc     文档对象
     * @param pageUrl 页面URL
     * @return 图片URL集合
     */
    private Set<String> extractImagesFromDocument(Document doc, String pageUrl) {
        Set<String> imageUrls = new HashSet<>();
        
        // 提取所有img标签的src属性
        Elements imgElements = doc.select("img[src]");
        for (Element img : imgElements) {
            String src = img.absUrl("src");
            if (src != null && !src.isEmpty() && isValidImageUrl(src)) {
                imageUrls.add(src);
            }
        }
        
        // 提取所有data-src属性（懒加载图片）
        Elements lazyImgElements = doc.select("img[data-src]");
        for (Element img : lazyImgElements) {
            String dataSrc = img.absUrl("data-src");
            if (dataSrc != null && !dataSrc.isEmpty() && isValidImageUrl(dataSrc)) {
                imageUrls.add(dataSrc);
            }
        }
        
        // 提取所有data-original属性（另一种懒加载方式）
        Elements originalImgElements = doc.select("img[data-original]");
        for (Element img : originalImgElements) {
            String dataOriginal = img.absUrl("data-original");
            if (dataOriginal != null && !dataOriginal.isEmpty() && isValidImageUrl(dataOriginal)) {
                imageUrls.add(dataOriginal);
            }
        }
        
        // 提取div的background-image（如果有）
        Elements bgElements = doc.select("[style*=background-image]");
        for (Element element : bgElements) {
            String style = element.attr("style");
            String url = extractUrlFromStyle(style);
            if (url != null && !url.isEmpty() && isValidImageUrl(url)) {
                if (url.startsWith("http")) {
                    imageUrls.add(url);
                } else {
                    String baseUrl = pageUrl.substring(0, pageUrl.indexOf("/", 8));
                    imageUrls.add(baseUrl + url);
                }
            }
        }
        
        return imageUrls;
    }
    
    /**
     * 验证是否为有效的图片URL（过滤掉图标等小图）
     *
     * @param url 图片URL
     * @return 是否有效
     */
    private boolean isValidImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        // 过滤掉常见的图标和小图
        String lowerUrl = url.toLowerCase();
        return !lowerUrl.contains("icon") && 
               !lowerUrl.contains("logo") && 
               !lowerUrl.contains("avatar") &&
               !lowerUrl.endsWith(".gif") || lowerUrl.contains("photo");
    }

    /**
     * 解析HTML页面，提取图片URL
     *
     * @param pageUrl 页面URL
     * @return 图片URL集合
     * @throws IOException IO异常
     */
    private Set<String> parseImageUrls(String pageUrl) throws IOException {
        // 使用Jsoup解析HTML
        Document doc = Jsoup.connect(pageUrl)
                .userAgent(downloadConfig.getUserAgent())
                .timeout(downloadConfig.getTimeout())
                .get();

        return extractImagesFromDocument(doc, pageUrl);
    }

    /**
     * 从style属性中提取URL
     *
     * @param style style属性值
     * @return URL
     */
    private String extractUrlFromStyle(String style) {
        if (style == null || !style.contains("url(")) {
            return null;
        }
        int start = style.indexOf("url(") + 4;
        int end = style.indexOf(")", start);
        if (end > start) {
            String url = style.substring(start, end);
            return url.replaceAll("['\"]", "").trim();
        }
        return null;
    }

    /**
     * 创建目标目录
     *
     * @param targetDir 目标目录
     * @return 完整路径
     * @throws IOException IO异常
     */
    private String createTargetDirectory(String targetDir) throws IOException {
        String basePath = downloadConfig.getBasePath();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        String fullPath;
        if (targetDir != null && !targetDir.isEmpty()) {
            fullPath = basePath + File.separator + targetDir + File.separator + timestamp;
        } else {
            fullPath = basePath + File.separator + timestamp;
        }

        Path path = Paths.get(fullPath);
        Files.createDirectories(path);

        return fullPath;
    }

    /**
     * 下载单个图片
     *
     * @param imageUrl      图片URL
     * @param savePath      保存路径
     * @param convertToJpeg 是否转换为JPEG
     * @return 下载的文件
     * @throws IOException IO异常
     */
    private File downloadSingleImage(String imageUrl, String savePath, Boolean convertToJpeg) throws IOException {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < downloadConfig.getMaxRetry()) {
            try {
                // 获取文件名
                String fileName = extractFileName(imageUrl);

                // 创建连接
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", downloadConfig.getUserAgent());
                connection.setConnectTimeout(downloadConfig.getTimeout());
                connection.setReadTimeout(downloadConfig.getTimeout());
                connection.connect();

                // 检查响应码
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP响应码: " + responseCode);
                }

                // 下载并保存
                File outputFile;
                if (convertToJpeg != null && convertToJpeg) {
                    // 转换为JPEG格式
                    try (InputStream inputStream = connection.getInputStream()) {
                        BufferedImage image = ImageIO.read(inputStream);
                        if (image != null) {
                            // 移除原扩展名，添加.jpg
                            String jpegFileName = removeExtension(fileName) + ".jpg";
                            outputFile = new File(savePath, jpegFileName);

                            // 保存为JPEG
                            ImageIO.write(image, "JPEG", outputFile);
                        } else {
                            throw new IOException("无法读取图片内容");
                        }
                    }
                } else {
                    // 直接保存原格式
                    try (InputStream inputStream = connection.getInputStream()) {
                        outputFile = new File(savePath, fileName);
                        FileUtils.copyInputStreamToFile(inputStream, outputFile);
                    }
                }

                return outputFile; // 成功，返回下载的文件

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("下载失败，正在重试 ({}/{}): {}", retryCount, downloadConfig.getMaxRetry(), imageUrl);
                try {
                    Thread.sleep(1000); // 重试前等待1秒
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("下载被中断", ie);
                }
            }
        }

        // 所有重试都失败
        throw new IOException("下载失败，已重试" + downloadConfig.getMaxRetry() + "次", lastException);
    }

    /**
     * 从URL中提取文件名
     *
     * @param imageUrl 图片URL
     * @return 文件名
     */
    private String extractFileName(String imageUrl) {
        String fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);

        // 移除URL参数
        int queryIndex = fileName.indexOf('?');
        if (queryIndex > 0) {
            fileName = fileName.substring(0, queryIndex);
        }

        // 如果文件名为空或无扩展名，使用时间戳
        if (fileName.isEmpty() || !fileName.contains(".")) {
            fileName = System.currentTimeMillis() + ".jpg";
        }

        // 清理文件名中的特殊字符
        fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");

        return fileName;
    }

    /**
     * 移除文件扩展名
     *
     * @param fileName 文件名
     * @return 无扩展名的文件名
     */
    private String removeExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
}

