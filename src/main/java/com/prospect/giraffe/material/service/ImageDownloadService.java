package com.prospect.giraffe.material.service;

import com.prospect.giraffe.material.config.DownloadConfig;
import com.prospect.giraffe.material.dto.BatchDownloadRequest;
import com.prospect.giraffe.material.dto.BatchDownloadResponse;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
            String baseSavePath = createTargetDirectory(request);
            
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

            // 失败原因统计
            java.util.Map<String, Integer> failureReasons = new java.util.HashMap<>();
            
            for (String imageUrl : imageUrls) {
                try {
                    // 添加请求间隔，避免请求过快被限流
                    if (downloadConfig.getRequestInterval() != null && downloadConfig.getRequestInterval() > 0) {
                        Thread.sleep(downloadConfig.getRequestInterval());
                    }
                    
                    // 下载到原图目录
                    File downloadedFile = downloadSingleImage(imageUrl, originalPath, request.getUrl(), request.getConvertToJpeg());

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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failCount.incrementAndGet();
                    failedUrls.add(imageUrl);
                    String reason = "下载被中断";
                    failureReasons.put(reason, failureReasons.getOrDefault(reason, 0) + 1);
                    log.error("下载失败: {}, 原因: {}", imageUrl, reason, e);
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    failedUrls.add(imageUrl);
                    String reason = extractFailureReason(e);
                    failureReasons.put(reason, failureReasons.getOrDefault(reason, 0) + 1);
                    log.error("下载失败: {}, 原因: {}, 错误详情", imageUrl, reason, e);
                }
            }
            
            // 打印失败原因统计
            if (!failureReasons.isEmpty()) {
                log.warn("下载失败原因统计:");
                failureReasons.forEach((reason, count) -> 
                    log.warn("  {}: {} 次", reason, count)
                );
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
//                "div.pagination a",          // 通用
//                "ul.pagination a",
//                "div.page a",
//                "div.pages a",
//                "a[href*=page]",
//                "a[href*=start]",
//                "a.next",                    // 下一页链接
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
     * 从Document中提取图片URL（优先提取高清图）
     *
     * @param doc     文档对象
     * @param pageUrl 页面URL
     * @return 图片URL集合
     */
    private Set<String> extractImagesFromDocument(Document doc, String pageUrl) {
        Set<String> imageUrls = new HashSet<>();
        
        // V2.x 详情页提取已禁用
        // 原因：虽然访问详情页看起来能获取更高清的图片，但实际测试发现：
        // 1. 清晰度没有提升（详情页的图片最终也是升级到 /raw/ 路径）
        // 2. 失败率更高（某些图片的 /raw/ 版本返回404）
        // 3. 耗时增加 2-3 倍（需要额外访问每个详情页）
        // 4. 复杂度增加（更容易出错）
        // 
        // 结论：回退到 V1.0 的简单模式，直接从相册页提取图片并升级到 /raw/
        //
        // 如需启用详情页提取，请取消下面代码的注释：
        /*
        boolean isDoubanAlbum = pageUrl.contains("douban.com") && 
                               (pageUrl.contains("/photos") || pageUrl.contains("/all_photos"));
        
        if (isDoubanAlbum) {
            log.info("检测到豆瓣相册页面: {}", pageUrl);
            Set<String> detailPageUrls = extractDoubanPhotoDetailUrls(doc);
            
            if (!detailPageUrls.isEmpty()) {
                log.info("找到 {} 个图片详情页，开始提取超高清图", detailPageUrls.size());
                int successCount = 0;
                
                for (String detailUrl : detailPageUrls) {
                    try {
                        Thread.sleep(500);
                        String ultraHdUrl = extractUltraHdImageFromDoubanDetail(detailUrl);
                        
                        if (ultraHdUrl != null && !ultraHdUrl.isEmpty()) {
                            if (isValidImageUrl(ultraHdUrl)) {
                                imageUrls.add(ultraHdUrl);
                                successCount++;
                                log.debug("✓ 从详情页提取到超高清图: {}", ultraHdUrl);
                            } else {
                                log.warn("✗ 提取的不是图片URL: {}", ultraHdUrl);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("线程被中断: {}", e.getMessage());
                        break;
                    } catch (Exception e) {
                        log.warn("✗ 从详情页提取图片失败: {}, 错误: {}", detailUrl, e.getMessage());
                    }
                }
                
                if (!imageUrls.isEmpty()) {
                    log.info("成功从 {} 个详情页提取到超高清图", successCount);
                    return imageUrls;
                }
                
                log.warn("从详情页提取失败（成功0个），回退到普通模式");
            } else {
                log.warn("未能提取到详情页链接，回退到普通模式");
            }
        }
        */
        
        // 优先级2: 提取 img 标签的高清属性（data-rawurl, data-highres 等）
        Elements imgElements = doc.select("img");
        for (Element img : imgElements) {
            // 尝试多种高清图属性（按优先级顺序）
            String[] highResAttrs = {
                "data-rawurl",      // 豆瓣等网站的原图URL
                "data-raw",
                "data-highres",
                "data-original-url",
                "data-large",
                "data-hd"
            };
            
            boolean foundHighRes = false;
            for (String attr : highResAttrs) {
                String highResUrl = img.absUrl(attr);
                if (highResUrl != null && !highResUrl.isEmpty() && isValidImageUrl(highResUrl)) {
                    imageUrls.add(highResUrl);
                    log.debug("提取到高清图片（{}）: {}", attr, highResUrl);
                    foundHighRes = true;
                    break;  // 找到高清版本就跳出
                }
            }
            
            // 如果没有找到高清版本，尝试从普通src中升级为高清
            if (!foundHighRes) {
                String src = img.absUrl("src");
                if (src != null && !src.isEmpty() && isValidImageUrl(src)) {
                    String upgradedUrl = upgradeToHighResolution(src);
                    imageUrls.add(upgradedUrl);
                    if (!upgradedUrl.equals(src)) {
                        log.debug("升级图片URL为高清: {} -> {}", src, upgradedUrl);
                    }
                }
            }
        }
        
        // 优先级3: 提取所有data-src属性（懒加载图片）
        Elements lazyImgElements = doc.select("img[data-src]");
        for (Element img : lazyImgElements) {
            String dataSrc = img.absUrl("data-src");
            if (dataSrc != null && !dataSrc.isEmpty() && isValidImageUrl(dataSrc)) {
                String upgradedUrl = upgradeToHighResolution(dataSrc);
                imageUrls.add(upgradedUrl);
            }
        }
        
        // 优先级4: 提取所有data-original属性（另一种懒加载方式）
        Elements originalImgElements = doc.select("img[data-original]");
        for (Element img : originalImgElements) {
            String dataOriginal = img.absUrl("data-original");
            if (dataOriginal != null && !dataOriginal.isEmpty() && isValidImageUrl(dataOriginal)) {
                String upgradedUrl = upgradeToHighResolution(dataOriginal);
                imageUrls.add(upgradedUrl);
            }
        }
        
        // 优先级5: 提取div的background-image（如果有）
        Elements bgElements = doc.select("[style*=background-image]");
        for (Element element : bgElements) {
            String style = element.attr("style");
            String url = extractUrlFromStyle(style);
            if (url != null && !url.isEmpty() && isValidImageUrl(url)) {
                if (url.startsWith("http")) {
                    String upgradedUrl = upgradeToHighResolution(url);
                    imageUrls.add(upgradedUrl);
                } else {
                    String baseUrl = pageUrl.substring(0, pageUrl.indexOf("/", 8));
                    String fullUrl = baseUrl + url;
                    String upgradedUrl = upgradeToHighResolution(fullUrl);
                    imageUrls.add(upgradedUrl);
                }
            }
        }
        
        log.info("提取到 {} 个图片URL（已优先使用高清版本）", imageUrls.size());
        return imageUrls;
    }
    
    /**
     * 将图片URL升级为高清版本
     * 通过替换URL中的尺寸参数来获取高清图
     *
     * @param url 原始URL
     * @return 高清URL
     */
    private String upgradeToHighResolution(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        String upgradedUrl = url;
        
        // 豆瓣图片：替换尺寸参数
        // 例如: https://img9.doubanio.com/view/photo/s_ratio_poster/public/p2895695254.jpg
        // -> https://img9.doubanio.com/view/photo/raw/public/p2895695254.jpg
        if (url.contains("doubanio.com") || url.contains("douban.com")) {
            upgradedUrl = url.replaceAll("/s_ratio_poster/", "/raw/")
                            .replaceAll("/m_ratio_poster/", "/raw/")
                            .replaceAll("/l_ratio_poster/", "/raw/")
                            .replaceAll("/photo/s/", "/photo/raw/")
                            .replaceAll("/photo/m/", "/photo/raw/")
                            .replaceAll("/photo/l/", "/photo/raw/");
        }
        
        // 通用规则1: 替换常见的尺寸标识
        upgradedUrl = upgradedUrl.replaceAll("_thumb\\.", "_large.")
                                .replaceAll("_small\\.", "_large.")
                                .replaceAll("_medium\\.", "_large.")
                                .replaceAll("_s\\.", "_l.")
                                .replaceAll("_m\\.", "_l.")
                                .replaceAll("/thumb/", "/large/")
                                .replaceAll("/small/", "/large/")
                                .replaceAll("/medium/", "/large/");
        
        // 通用规则2: 移除或替换查询参数中的尺寸限制
        if (upgradedUrl.contains("?")) {
            String[] parts = upgradedUrl.split("\\?");
            if (parts.length == 2) {
                String queryString = parts[1];
                // 移除常见的尺寸限制参数
                queryString = queryString.replaceAll("&?w=\\d+", "")
                                        .replaceAll("&?h=\\d+", "")
                                        .replaceAll("&?width=\\d+", "")
                                        .replaceAll("&?height=\\d+", "")
                                        .replaceAll("&?size=\\w+", "")
                                        .replaceAll("&?quality=\\d+", "")
                                        .replaceAll("^&", "");
                
                if (!queryString.isEmpty()) {
                    upgradedUrl = parts[0] + "?" + queryString;
                } else {
                    upgradedUrl = parts[0];
                }
            }
        }
        
        return upgradedUrl;
    }
    
    /**
     * 判断是否为高清图片URL
     * 通过URL特征判断是否为高清版本
     *
     * @param url 图片URL
     * @return 是否为高清图
     */
    private boolean isHighResolutionImage(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        String lowerUrl = url.toLowerCase();
        
        // 排除明显的缩略图标识
        if (lowerUrl.contains("thumb") || lowerUrl.contains("small") || 
            lowerUrl.contains("_s.") || lowerUrl.contains("_m.") ||
            lowerUrl.contains("/s/") || lowerUrl.contains("/m/")) {
            return false;
        }
        
        // 常见图片扩展名
        return lowerUrl.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp)(\\?.*)?$");
    }
    
    /**
     * 从豆瓣相册页面提取图片详情页URL
     *
     * @param doc 文档对象
     * @return 详情页URL集合
     */
    private Set<String> extractDoubanPhotoDetailUrls(Document doc) {
        Set<String> detailUrls = new HashSet<>();
        
        // 豆瓣相册页面的图片详情链接格式：
        // <a href="/photos/photo/2541307071/" ...>
        // 或 <a href="https://movie.douban.com/photos/photo/2541307071/" ...>
        Elements photoLinks = doc.select("a[href*=/photos/photo/]");
        
        for (Element link : photoLinks) {
            String href = link.absUrl("href");
            
            if (href != null && !href.isEmpty()) {
                // 移除锚点（#...）
                if (href.contains("#")) {
                    href = href.substring(0, href.indexOf("#"));
                }
                
                // 移除查询参数（?...）
                if (href.contains("?")) {
                    href = href.substring(0, href.indexOf("?"));
                }
                
                // 确保以 / 结尾
                if (!href.endsWith("/")) {
                    href = href + "/";
                }
                
                // 确保是图片详情页（以数字ID结尾）
                if (href.matches(".*\\/photos\\/photo\\/\\d+\\/?$")) {
                    detailUrls.add(href);
                    log.debug("提取到详情页链接: {}", href);
                }
            }
        }
        
        log.info("从相册页面提取到 {} 个详情页链接", detailUrls.size());
        return detailUrls;
    }
    
    /**
     * 从豆瓣图片详情页提取超高清图URL
     *
     * @param detailPageUrl 详情页URL
     * @return 超高清图URL，如果提取失败返回null
     * @throws IOException IO异常
     */
    private String extractUltraHdImageFromDoubanDetail(String detailPageUrl) throws IOException {
        log.debug("正在访问详情页: {}", detailPageUrl);
        
        Document detailDoc = Jsoup.connect(detailPageUrl)
                .userAgent(downloadConfig.getUserAgent())
                .timeout(downloadConfig.getTimeout())
                .referrer(detailPageUrl)  // 添加 Referer
                .get();
        
        // 策略1: 查找最大的 img 标签（通常在 div.photo-wp 中）
        Elements mainImages = detailDoc.select("div.photo-wp img, div.mainphoto img, img#mainpic, img.view_photo");
        if (!mainImages.isEmpty()) {
            Element mainImg = mainImages.first();
            log.debug("找到主图片标签: {}", mainImg.tagName());
            
            // 尝试多个可能包含高清图的属性（按优先级排序）
            String[] hdAttrs = {
                "data-rawurl",      // 豆瓣原图属性（最高优先级！）
                "data-original",    // 懒加载原图
                "data-highres",     // 高清属性
                "data-large",       // 大图属性
                "data-src",         // 懒加载
                "src"               // 最后尝试 src（可能只是 l 或 m）
            };
            
            for (String attr : hdAttrs) {
                String imgUrl = mainImg.absUrl(attr);
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    log.debug("检查属性 [{}]: {}", attr, imgUrl);
                    
                    // 确保是有效的图片URL
                    if (isValidImageUrl(imgUrl)) {
                        // 升级为最高清版本（统一升级到 raw）
                        String ultraHdUrl = upgradeToUltraHighResolution(imgUrl);
                        
                        // 如果是 data-rawurl，直接使用不需要升级
                        if ("data-rawurl".equals(attr) && imgUrl.contains("/raw/")) {
                            log.info("✓ 直接获取到豆瓣原图（data-rawurl）: {}", imgUrl);
                            return imgUrl;
                        }
                        
                        log.info("✓ 成功提取图片URL（属性: {}）: {}", attr, ultraHdUrl);
                        return ultraHdUrl;
                    } else {
                        log.debug("不是有效的图片URL: {}", imgUrl);
                    }
                }
            }
        } else {
            log.warn("未找到主图片标签");
        }
        
        // 策略2: 查找 "查看原图" 链接
        Elements viewOriginalLinks = detailDoc.select("a.mainphoto, a[href*=view/photo]");
        if (!viewOriginalLinks.isEmpty()) {
            String largePhotoUrl = viewOriginalLinks.first().absUrl("href");
            if (largePhotoUrl != null && !largePhotoUrl.isEmpty() && isValidImageUrl(largePhotoUrl)) {
                String ultraHdUrl = upgradeToUltraHighResolution(largePhotoUrl);
                log.info("✓ 从'查看原图'链接获取: {}", ultraHdUrl);
                return ultraHdUrl;
            }
        }
        
        // 策略3: 从页面中查找所有图片URL，选择最大的（通常是原图）
        Elements allImages = detailDoc.select("img[src]");
        String largestUrl = null;
        int maxLength = 0;
        
        for (Element img : allImages) {
            String src = img.absUrl("src");
            if (src != null && isValidImageUrl(src)) {
                // 排除小图标和头像
                if (!src.contains("icon") && !src.contains("avatar") && src.length() > maxLength) {
                    largestUrl = src;
                    maxLength = src.length();
                }
            }
        }
        
        if (largestUrl != null) {
            String ultraHdUrl = upgradeToUltraHighResolution(largestUrl);
            log.info("✓ 选择最大的图片URL: {}", ultraHdUrl);
            return ultraHdUrl;
        }
        
        log.error("✗ 无法从详情页提取超高清图: {}", detailPageUrl);
        return null;
    }
    
    /**
     * 将图片URL升级为超高清版本（优先raw路径）
     * 专门针对豆瓣等网站的最高清晰度版本
     *
     * @param url 原始URL
     * @return 超高清URL
     */
    private String upgradeToUltraHighResolution(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        String ultraHdUrl = url;
        
        // 豆瓣图片：使用 raw 路径（这才是真正的高清原图）
        if (url.contains("doubanio.com") || url.contains("douban.com")) {
            String oldUrl = url;
            
            // 统一升级到 raw 路径（豆瓣的最高清版本）
            ultraHdUrl = url.replaceAll("/view/photo/s/public/", "/view/photo/raw/public/")
                            .replaceAll("/view/photo/m/public/", "/view/photo/raw/public/")
                            .replaceAll("/view/photo/l/public/", "/view/photo/raw/public/")
                            .replaceAll("/view/photo/photo/public/", "/view/photo/raw/public/");
            
            if (!ultraHdUrl.equals(oldUrl)) {
                log.debug("升级到豆瓣高清(raw): {} -> {}", oldUrl, ultraHdUrl);
            }
        } else {
            // 非豆瓣网站使用普通升级
            ultraHdUrl = upgradeToHighResolution(url);
        }
        
        return ultraHdUrl;
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
     * @param request 下载请求
     * @return 完整路径
     * @throws IOException IO异常
     */
    private String createTargetDirectory(DownloadRequest request) throws IOException {
        String fullPath;

        // 优先级1: 使用 savePath（最高优先级）
        if (request.getSavePath() != null && !request.getSavePath().isEmpty()) {
            fullPath = resolvePath(request.getSavePath());
            log.info("使用指定的保存路径: {}", fullPath);
        }
        // 优先级2: 使用 targetDir
        else if (request.getTargetDir() != null && !request.getTargetDir().isEmpty()) {
            String targetDir = request.getTargetDir();
            
            // 判断是绝对路径还是相对路径
            if (targetDir.startsWith("/") || targetDir.matches("^[a-zA-Z]:.*")) {
                // 绝对路径
                fullPath = targetDir;
                log.info("使用绝对路径: {}", fullPath);
            } else {
                // 相对路径，拼接 basePath
                fullPath = downloadConfig.getBasePath() + File.separator + targetDir;
                log.info("使用相对路径，基于 basePath: {}", fullPath);
            }
        }
        // 优先级3: 使用默认 basePath
        else {
            fullPath = downloadConfig.getBasePath();
            log.info("使用默认 basePath: {}", fullPath);
        }

        // 是否添加时间戳子目录
        boolean useTimestamp = request.getUseTimestamp() != null ? request.getUseTimestamp() : true;
        if (useTimestamp) {
            String timestampFormat = request.getTimestampFormat();
            if (timestampFormat == null || timestampFormat.isEmpty()) {
                timestampFormat = "yyyyMMdd_HHmmss";  // 默认格式
            }
            
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(timestampFormat));
                fullPath = fullPath + File.separator + timestamp;
                log.info("添加时间戳子目录: {}", timestamp);
            } catch (Exception e) {
                log.warn("时间戳格式错误: {}, 使用默认格式", timestampFormat);
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                fullPath = fullPath + File.separator + timestamp;
            }
        }

        // 创建目录
        Path path = Paths.get(fullPath);
        Files.createDirectories(path);
        log.info("目标目录已创建: {}", fullPath);

        return fullPath;
    }

    /**
     * 解析路径（处理相对路径和绝对路径）
     *
     * @param path 路径
     * @return 解析后的路径
     */
    private String resolvePath(String path) {
        if (path.startsWith("~")) {
            // 处理 ~ 开头的路径
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    /**
     * 下载单个图片
     *
     * @param imageUrl      图片URL
     * @param savePath      保存路径
     * @param refererUrl    来源URL（用于设置Referer请求头）
     * @param convertToJpeg 是否转换为JPEG
     * @return 下载的文件
     * @throws IOException IO异常
     */
    private File downloadSingleImage(String imageUrl, String savePath, String refererUrl, Boolean convertToJpeg) throws IOException {
        int retryCount = 0;
        Exception lastException = null;
        int connectTimeout = downloadConfig.getConnectTimeout() != null ? downloadConfig.getConnectTimeout() : 10000;
        int readTimeout = downloadConfig.getReadTimeout() != null ? downloadConfig.getReadTimeout() : 60000;

        while (retryCount < downloadConfig.getMaxRetry()) {
            HttpURLConnection connection = null;
            try {
                // 获取文件名
                String fileName = extractFileName(imageUrl);

                // 创建连接
                URL url = new URL(imageUrl);
                connection = (HttpURLConnection) url.openConnection();
                
                // 设置请求头
                connection.setRequestProperty("User-Agent", downloadConfig.getUserAgent());
                if (refererUrl != null && !refererUrl.isEmpty()) {
                    connection.setRequestProperty("Referer", refererUrl);
                }
                connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
                connection.setRequestProperty("Connection", "keep-alive");
                connection.setRequestProperty("Cache-Control", "no-cache");
                
                // 设置超时
                connection.setConnectTimeout(connectTimeout);
                connection.setReadTimeout(readTimeout);
                
                // 允许重定向
                connection.setInstanceFollowRedirects(true);
                
                connection.connect();

                // 检查响应码
                int responseCode = connection.getResponseCode();
                
                // 处理重定向
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    String redirectUrl = connection.getHeaderField("Location");
                    if (redirectUrl != null) {
                        log.debug("图片URL重定向: {} -> {}", imageUrl, redirectUrl);
                        connection.disconnect();
                        // 递归下载重定向后的URL
                        return downloadSingleImage(redirectUrl, savePath, refererUrl, convertToJpeg);
                    }
                }
                
                // 处理各种HTTP状态码
                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    connection.disconnect();
                    throw new IOException("HTTP 403 禁止访问，可能被服务器拒绝");
                } else if (responseCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                    connection.disconnect();
                    throw new IOException("HTTP 503 服务不可用，服务器可能过载");
                } else if (responseCode == 429) {
                    // Too Many Requests
                    String retryAfter = connection.getHeaderField("Retry-After");
                    int waitSeconds = retryAfter != null ? Integer.parseInt(retryAfter) : (int) Math.pow(2, retryCount);
                    log.warn("HTTP 429 请求过多，等待 {} 秒后重试", waitSeconds);
                    connection.disconnect();
                    Thread.sleep(waitSeconds * 1000L);
                    throw new IOException("HTTP 429 请求过多，需要等待");
                } else if (responseCode != HttpURLConnection.HTTP_OK) {
                    connection.disconnect();
                    throw new IOException("HTTP响应码: " + responseCode);
                }

                // 检查Content-Type
                String contentType = connection.getContentType();
                if (contentType != null && !contentType.startsWith("image/")) {
                    log.warn("URL返回的不是图片类型: {}, Content-Type: {}", imageUrl, contentType);
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
                            connection.disconnect();
                            throw new IOException("无法读取图片内容，可能不是有效的图片格式");
                        }
                    }
                } else {
                    // 直接保存原格式
                    try (InputStream inputStream = connection.getInputStream()) {
                        outputFile = new File(savePath, fileName);
                        FileUtils.copyInputStreamToFile(inputStream, outputFile);
                    }
                }

                connection.disconnect();
                return outputFile; // 成功，返回下载的文件

            } catch (InterruptedException e) {
                if (connection != null) {
                    connection.disconnect();
                }
                Thread.currentThread().interrupt();
                throw new IOException("下载被中断", e);
            } catch (java.net.SocketTimeoutException e) {
                if (connection != null) {
                    connection.disconnect();
                }
                lastException = e;
                retryCount++;
                log.warn("下载超时，正在重试 ({}/{}): {}", retryCount, downloadConfig.getMaxRetry(), imageUrl);
                // 指数退避：第1次重试等1秒，第2次等2秒，第3次等4秒
                try {
                    Thread.sleep((long) Math.pow(2, retryCount - 1) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("下载被中断", ie);
                }
            } catch (java.net.ConnectException e) {
                if (connection != null) {
                    connection.disconnect();
                }
                lastException = e;
                retryCount++;
                log.warn("连接失败，正在重试 ({}/{}): {}", retryCount, downloadConfig.getMaxRetry(), imageUrl);
                // 指数退避
                try {
                    Thread.sleep((long) Math.pow(2, retryCount - 1) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("下载被中断", ie);
                }
            } catch (Exception e) {
                if (connection != null) {
                    connection.disconnect();
                }
                lastException = e;
                retryCount++;
                log.warn("下载失败，正在重试 ({}/{}): {}, 错误: {}", 
                        retryCount, downloadConfig.getMaxRetry(), imageUrl, e.getMessage());
                // 指数退避
                try {
                    Thread.sleep((long) Math.pow(2, retryCount - 1) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("下载被中断", ie);
                }
            }
        }

        // 所有重试都失败
        throw new IOException("下载失败，已重试" + downloadConfig.getMaxRetry() + "次: " + 
                (lastException != null ? lastException.getMessage() : "未知错误"), lastException);
    }
    
    /**
     * 提取失败原因
     *
     * @param e 异常
     * @return 失败原因描述
     */
    private String extractFailureReason(Exception e) {
        if (e == null) {
            return "未知错误";
        }
        
        String message = e.getMessage();
        if (message == null) {
            message = e.getClass().getSimpleName();
        }
        
        // 根据异常类型和消息提取原因
        if (e instanceof java.net.SocketTimeoutException) {
            return "连接超时";
        } else if (e instanceof java.net.ConnectException) {
            return "连接失败";
        } else if (message.contains("HTTP 403")) {
            return "HTTP 403 禁止访问";
        } else if (message.contains("HTTP 429")) {
            return "HTTP 429 请求过多";
        } else if (message.contains("HTTP 503")) {
            return "HTTP 503 服务不可用";
        } else if (message.contains("HTTP响应码")) {
            return message;
        } else if (message.contains("无法读取图片内容")) {
            return "图片格式无效";
        } else if (message.contains("超时")) {
            return "请求超时";
        } else if (message.contains("连接")) {
            return "网络连接问题";
        }
        
        return message.length() > 50 ? message.substring(0, 50) + "..." : message;
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

    /**
     * 批量下载多个页面的图片
     *
     * @param request 批量下载请求
     * @return 批量下载结果
     */
    public BatchDownloadResponse batchDownloadImages(BatchDownloadRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("开始批量下载，页面数: {}, 并发模式: {}, 最大并发数: {}", 
                request.getUrls().size(), request.getConcurrent(), request.getMaxConcurrency());

        List<BatchDownloadResponse.PageDownloadResult> pageResults = new ArrayList<>();
        AtomicInteger totalImages = new AtomicInteger(0);
        AtomicInteger successImages = new AtomicInteger(0);
        AtomicInteger failImages = new AtomicInteger(0);

        if (request.getConcurrent() != null && request.getConcurrent()) {
            // 并发下载模式
            pageResults = batchDownloadConcurrent(request, totalImages, successImages, failImages);
        } else {
            // 串行下载模式
            pageResults = batchDownloadSequential(request, totalImages, successImages, failImages);
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        
        // 统计成功和失败的页面数
        int successPages = (int) pageResults.stream()
                .filter(r -> r.getSuccess() != null && r.getSuccess())
                .count();
        int failPages = pageResults.size() - successPages;

        // 汇总统计
        boolean overallSuccess = failPages == 0;

        log.info("批量下载完成，总页面数: {}, 成功: {}, 失败: {}, 总图片数: {}, 成功: {}, 失败: {}, 总耗时: {}ms",
                request.getUrls().size(), successPages, failPages, 
                totalImages.get(), successImages.get(), failImages.get(), totalDuration);

        return BatchDownloadResponse.builder()
                .success(overallSuccess)
                .message(overallSuccess ? "所有页面下载完成" : String.format("部分页面下载失败，成功: %d, 失败: %d", successPages, failPages))
                .totalPages(request.getUrls().size())
                .successPages(successPages)
                .failPages(failPages)
                .totalImages(totalImages.get())
                .successImages(successImages.get())
                .failImages(failImages.get())
                .totalDuration(totalDuration)
                .pageResults(pageResults)
                .build();
    }

    /**
     * 并发下载多个页面
     *
     * @param request 批量下载请求
     * @param totalImages 总图片数统计
     * @param successImages 成功图片数统计
     * @param failImages 失败图片数统计
     * @return 每个页面的下载结果
     */
    private List<BatchDownloadResponse.PageDownloadResult> batchDownloadConcurrent(
            BatchDownloadRequest request,
            AtomicInteger totalImages,
            AtomicInteger successImages,
            AtomicInteger failImages) {
        
        int maxConcurrency = request.getMaxConcurrency() != null ? request.getMaxConcurrency() : 3;
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);
        List<Future<BatchDownloadResponse.PageDownloadResult>> futures = new ArrayList<>();

        try {
            // 提交所有下载任务
            for (String url : request.getUrls()) {
                Future<BatchDownloadResponse.PageDownloadResult> future = executor.submit(() -> {
                    return downloadSinglePage(url, request, totalImages, successImages, failImages);
                });
                futures.add(future);
            }

            // 收集结果
            List<BatchDownloadResponse.PageDownloadResult> results = new ArrayList<>();
            for (Future<BatchDownloadResponse.PageDownloadResult> future : futures) {
                try {
                    BatchDownloadResponse.PageDownloadResult result = future.get();
                    results.add(result);
                } catch (Exception e) {
                    log.error("获取页面下载结果异常", e);
                    // 创建一个失败的结果
                    results.add(BatchDownloadResponse.PageDownloadResult.builder()
                            .url("未知")
                            .success(false)
                            .message("获取下载结果异常: " + e.getMessage())
                            .totalCount(0)
                            .successCount(0)
                            .failCount(0)
                            .duration(0L)
                            .build());
                }
            }
            return results;
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 串行下载多个页面
     *
     * @param request 批量下载请求
     * @param totalImages 总图片数统计
     * @param successImages 成功图片数统计
     * @param failImages 失败图片数统计
     * @return 每个页面的下载结果
     */
    private List<BatchDownloadResponse.PageDownloadResult> batchDownloadSequential(
            BatchDownloadRequest request,
            AtomicInteger totalImages,
            AtomicInteger successImages,
            AtomicInteger failImages) {
        
        List<BatchDownloadResponse.PageDownloadResult> results = new ArrayList<>();
        
        for (int i = 0; i < request.getUrls().size(); i++) {
            String url = request.getUrls().get(i);
            log.info("开始下载第 {}/{} 个页面: {}", i + 1, request.getUrls().size(), url);
            
            BatchDownloadResponse.PageDownloadResult result = downloadSinglePage(
                    url, request, totalImages, successImages, failImages);
            results.add(result);
            
            // 页面之间添加间隔，避免请求过快
            if (i < request.getUrls().size() - 1 && downloadConfig.getRequestInterval() != null) {
                try {
                    Thread.sleep(downloadConfig.getRequestInterval() * 2); // 页面间隔是图片间隔的2倍
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("页面间隔等待被中断");
                }
            }
        }
        
        return results;
    }

    /**
     * 下载单个页面
     *
     * @param url 页面URL
     * @param batchRequest 批量下载请求（包含配置）
     * @param totalImages 总图片数统计
     * @param successImages 成功图片数统计
     * @param failImages 失败图片数统计
     * @return 该页面的下载结果
     */
    private BatchDownloadResponse.PageDownloadResult downloadSinglePage(
            String url,
            BatchDownloadRequest batchRequest,
            AtomicInteger totalImages,
            AtomicInteger successImages,
            AtomicInteger failImages) {
        
        long pageStartTime = System.currentTimeMillis();
        
        try {
            // 构建单个页面的下载请求
            DownloadRequest singleRequest = new DownloadRequest();
            singleRequest.setUrl(url);
            singleRequest.setTargetDir(batchRequest.getTargetDir());
            singleRequest.setSavePath(batchRequest.getSavePath());
            singleRequest.setUseTimestamp(batchRequest.getUseTimestamp());
            singleRequest.setTimestampFormat(batchRequest.getTimestampFormat());
            singleRequest.setConvertToJpeg(batchRequest.getConvertToJpeg());
            singleRequest.setCrawlAllPages(batchRequest.getCrawlAllPages());
            singleRequest.setMaxPages(batchRequest.getMaxPages());
            singleRequest.setRemoveWatermark(batchRequest.getRemoveWatermark());
            singleRequest.setWatermarkProvider(batchRequest.getWatermarkProvider());
            singleRequest.setSaveOriginal(batchRequest.getSaveOriginal());

            // 调用单个页面下载方法
            DownloadResponse response = downloadImages(singleRequest);

            // 更新统计
            if (response.getTotalCount() != null) {
                totalImages.addAndGet(response.getTotalCount());
            }
            if (response.getSuccessCount() != null) {
                successImages.addAndGet(response.getSuccessCount());
            }
            if (response.getFailCount() != null) {
                failImages.addAndGet(response.getFailCount());
            }

            long pageDuration = System.currentTimeMillis() - pageStartTime;

            return BatchDownloadResponse.PageDownloadResult.builder()
                    .url(url)
                    .success(response.getSuccess())
                    .message(response.getMessage())
                    .totalCount(response.getTotalCount())
                    .successCount(response.getSuccessCount())
                    .failCount(response.getFailCount())
                    .savePath(response.getSavePath())
                    .failedUrls(response.getFailedUrls())
                    .duration(pageDuration)
                    .watermarkStats(response.getWatermarkStats())
                    .build();

        } catch (Exception e) {
            log.error("下载页面失败: {}", url, e);
            long pageDuration = System.currentTimeMillis() - pageStartTime;
            
            return BatchDownloadResponse.PageDownloadResult.builder()
                    .url(url)
                    .success(false)
                    .message("下载失败: " + e.getMessage())
                    .totalCount(0)
                    .successCount(0)
                    .failCount(0)
                    .duration(pageDuration)
                    .build();
        }
    }
}

