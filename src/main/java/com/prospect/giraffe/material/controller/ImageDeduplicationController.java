package com.prospect.giraffe.material.controller;

import com.prospect.giraffe.material.dto.ApiResponse;
import com.prospect.giraffe.material.dto.BatchDeduplicationRequest;
import com.prospect.giraffe.material.dto.BatchDeduplicationResponse;
import com.prospect.giraffe.material.dto.DeduplicationRequest;
import com.prospect.giraffe.material.dto.DeduplicationResponse;
import com.prospect.giraffe.material.service.ImageDeduplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * 图片去重处理控制器
 *
 * @author giraffe
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/deduplication")
@Validated
public class ImageDeduplicationController {
    
    @Resource
    private ImageDeduplicationService deduplicationService;
    
    /**
     * 图片去重处理接口
     *
     * @param request 去重请求
     * @return 处理结果
     */
    @PostMapping("/process")
    public ApiResponse<DeduplicationResponse> processImage(@Valid @RequestBody DeduplicationRequest request) {
        log.info("收到图片去重请求: {}, 保留文字: {}", 
                request.getImagePath(), request.getPreserveTexts());
        
        try {
            DeduplicationResponse response = deduplicationService.processImage(request);
            if (response.getSuccess()) {
                return ApiResponse.success("处理成功", response);
            } else {
                return ApiResponse.error(response.getErrorMessage() != null 
                        ? response.getErrorMessage() 
                        : "处理失败");
            }
        } catch (Exception e) {
            log.error("图片去重处理异常", e);
            return ApiResponse.error("处理失败: " + e.getMessage());
        }
    }

    /**
     * 批量图片去重处理接口
     * 处理指定目录下的所有图片，结果保存到该目录下的子目录中
     *
     * @param request 批量去重请求
     * @return 批量处理结果
     */
    @PostMapping("/process-batch")
    public ApiResponse<BatchDeduplicationResponse> processBatchImages(@Valid @RequestBody BatchDeduplicationRequest request) {
        log.info("收到批量图片去重请求: 目录={}, 保留文字={}, 输出子目录={}",
                request.getDirectoryPath(), request.getPreserveTexts(), request.getOutputDirName());

        try {
            BatchDeduplicationResponse response = deduplicationService.processBatchImages(request);
            if (response.getSuccess() != null && response.getSuccess()) {
                return ApiResponse.success(
                        String.format("批量处理完成: 成功%d张, 失败%d张", response.getSuccessCount(), response.getFailCount()),
                        response);
            } else if (response.getSuccessCount() != null && response.getSuccessCount() > 0) {
                // 部分成功
                return ApiResponse.success(
                        String.format("批量处理部分完成: 成功%d张, 失败%d张", response.getSuccessCount(), response.getFailCount()),
                        response);
            } else {
                return ApiResponse.error(response.getErrorMessage() != null
                        ? response.getErrorMessage()
                        : "批量处理失败");
            }
        } catch (Exception e) {
            log.error("批量图片去重处理异常", e);
            return ApiResponse.error("批量处理失败: " + e.getMessage());
        }
    }
}

