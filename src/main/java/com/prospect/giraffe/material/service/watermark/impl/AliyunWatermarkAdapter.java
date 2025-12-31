//package com.prospect.giraffe.material.service.watermark.impl;
//
//import com.aliyun.imm20200930.Client;
//import com.aliyun.imm20200930.models.*;
//import com.aliyun.tea.TeaException;
//import com.aliyun.teaopenapi.models.Config;
//import com.google.gson.Gson;
//import com.google.gson.JsonObject;
//import com.prospect.giraffe.material.config.WatermarkRemovalConfig;
//import com.prospect.giraffe.material.service.watermark.IWatermarkRemovalAdapter;
//import lombok.extern.slf4j.Slf4j;
//import okhttp3.*;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.Resource;
//import java.io.File;
//import java.io.FileOutputStream;
//import java.nio.file.Files;
//import java.util.Base64;
//import java.util.concurrent.TimeUnit;
//
///**
// * 阿里云水印去除适配器
// *
// * @author giraffe
// */
//@Slf4j
//@Service("aliyunWatermarkAdapter")
//@ConditionalOnProperty(prefix = "watermark-removal.aliyun", name = "enabled", havingValue = "true")
//public class AliyunWatermarkAdapter implements IWatermarkRemovalAdapter {
//
//    @Resource
//    private WatermarkRemovalConfig config;
//
//    private OkHttpClient httpClient;
//    private final Gson gson = new Gson();
//
//    private OkHttpClient getHttpClient() {
//        if (httpClient == null) {
//            httpClient = new OkHttpClient.Builder()
//                    .connectTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
//                    .readTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
//                    .build();
//        }
//        return httpClient;
//    }
//
//    @Override
//    public File removeWatermark(File inputFile) throws Exception {
//        log.info("使用阿里云服务去除水印: {}", inputFile.getName());
//
//        try {
//            // 使用阿里云通用HTTP API方式调用
//            // 因为阿里云图像处理SDK版本较多且API变化较大，使用HTTP方式更稳定
//
//            // 读取图片并转换为 Base64
//            byte[] imageBytes = Files.readAllBytes(inputFile.toPath());
//            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
//
//            // 构建请求JSON
//            JsonObject requestJson = new JsonObject();
//            requestJson.addProperty("ImageURL", "data:image/jpeg;base64," + base64Image);
//
//            // 构建请求体
//            RequestBody body = RequestBody.create(
//                    requestJson.toString(),
//                    MediaType.parse("application/json; charset=utf-8")
//            );
//
//            // 构建请求URL（使用阿里云图像处理API）
//            String apiUrl = String.format("https://%s/RemoveImageWatermark",
//                    config.getAliyun().getEndpoint());
//
//            // 生成签名和请求头
//            String timestamp = String.valueOf(System.currentTimeMillis());
//
//            Request request = new Request.Builder()
//                    .url(apiUrl)
//                    .post(body)
//                    .addHeader("Content-Type", "application/json")
//                    .addHeader("x-acs-version", "2020-03-20")
//                    .addHeader("x-acs-action", "RemoveImageWatermark")
//                    .addHeader("AccessKeyId", config.getAliyun().getAccessKeyId())
//                    // 注意：实际生产环境需要完整的签名机制
//                    .build();
//
//            try (Response response = getHttpClient().newCall(request).execute()) {
//                if (response.isSuccessful() && response.body() != null) {
//                    String responseBody = response.body().string();
//                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
//
//                    // 检查是否有错误
//                    if (jsonResponse.has("Code")) {
//                        String errorCode = jsonResponse.get("Code").getAsString();
//                        String errorMsg = jsonResponse.has("Message")
//                            ? jsonResponse.get("Message").getAsString()
//                            : "未知错误";
//                        throw new Exception("阿里云API错误: [" + errorCode + "] " + errorMsg);
//                    }
//
//                    // 获取处理后的图片
//                    if (!jsonResponse.has("Data")) {
//                        throw new Exception("阿里云API返回数据中没有Data字段");
//                    }
//
//                    JsonObject data = jsonResponse.getAsJsonObject("Data");
//                    String resultImageUrl = data.get("ImageURL").getAsString();
//
//                    // 创建输出文件
//                    File outputFile = new File(inputFile.getParent(), "cleaned_" + inputFile.getName());
//
//                    if (resultImageUrl.startsWith("data:image")) {
//                        // Base64 格式
//                        String base64Data = resultImageUrl.substring(resultImageUrl.indexOf(",") + 1);
//                        byte[] resultBytes = Base64.getDecoder().decode(base64Data);
//                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
//                            fos.write(resultBytes);
//                        }
//                    } else if (resultImageUrl.startsWith("http")) {
//                        // URL 格式，下载图片
//                        downloadImage(resultImageUrl, outputFile);
//                    } else {
//                        throw new Exception("未知的返回格式: " + resultImageUrl);
//                    }
//
//                    log.info("阿里云去水印完成: {}", outputFile.getName());
//                    return outputFile;
//
//                } else {
//                    throw new Exception("阿里云API调用失败: HTTP " + response.code());
//                }
//            }
//
//        } catch (Exception e) {
//            log.error("阿里云去水印异常: {}", e.getMessage(), e);
//            throw new Exception("阿里云去水印失败: " + e.getMessage(), e);
//        }
//    }
//
//    /**
//     * 下载图片
//     */
//    private void downloadImage(String url, File outputFile) throws Exception {
//        Request request = new Request.Builder()
//                .url(url)
//                .get()
//                .build();
//
//        try (Response response = getHttpClient().newCall(request).execute()) {
//            if (response.isSuccessful() && response.body() != null) {
//                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
//                    fos.write(response.body().bytes());
//                }
//            } else {
//                throw new Exception("下载图片失败: HTTP " + response.code());
//            }
//        }
//    }
//
//    @Override
//    public String getProviderName() {
//        return "aliyun";
//    }
//
//    @Override
//    public boolean isAvailable() {
//        return config.getAliyun().getEnabled()
//                && config.getAliyun().getAccessKeyId() != null
//                && !config.getAliyun().getAccessKeyId().isEmpty()
//                && config.getAliyun().getAccessKeySecret() != null
//                && !config.getAliyun().getAccessKeySecret().isEmpty();
//    }
//}
//
