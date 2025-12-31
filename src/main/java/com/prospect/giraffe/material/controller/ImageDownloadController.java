package com.prospect.giraffe.material.controller;

import com.prospect.giraffe.material.dto.ApiResponse;
import com.prospect.giraffe.material.dto.BatchDownloadRequest;
import com.prospect.giraffe.material.dto.BatchDownloadResponse;
import com.prospect.giraffe.material.dto.DownloadRequest;
import com.prospect.giraffe.material.dto.DownloadResponse;
import com.prospect.giraffe.material.service.ImageDownloadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * 图片下载控制器
 *
 * @author giraffe
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/download")
@Validated
public class ImageDownloadController {

    @Resource
    private ImageDownloadService imageDownloadService;

    /**
     * 下载图片接口
     *
     * @param request 下载请求
     * @return 下载结果
     */
    @PostMapping("/images")
    public ApiResponse<DownloadResponse> downloadImages(@Valid @RequestBody DownloadRequest request) {
        log.info("收到下载请求: {}", request.getUrl());
        try {
            DownloadResponse response = imageDownloadService.downloadImages(request);
            if (response.getSuccess()) {
                return ApiResponse.success("下载完成", response);
            } else {
                return ApiResponse.error(response.getMessage());
            }
        } catch (Exception e) {
            log.error("下载图片异常", e);
            return ApiResponse.error("下载失败: " + e.getMessage());
        }
    }

    /**
     * 批量下载图片接口
     *
     * @param request 批量下载请求
     * @return 批量下载结果
     */
    @PostMapping("/images/batch")
    public ApiResponse<BatchDownloadResponse> batchDownloadImages(@Valid @RequestBody BatchDownloadRequest request) {
        log.info("收到批量下载请求，页面数: {}", request.getUrls().size());
        try {
            BatchDownloadResponse response = imageDownloadService.batchDownloadImages(request);
            if (response.getSuccess()) {
                return ApiResponse.success("批量下载完成", response);
            } else {
                return ApiResponse.error(response.getMessage());
            }
        } catch (Exception e) {
            log.error("批量下载图片异常", e);
            return ApiResponse.error("批量下载失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查接口
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("服务运行正常");
    }
}

