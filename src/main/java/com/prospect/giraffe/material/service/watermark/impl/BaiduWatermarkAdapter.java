package com.prospect.giraffe.material.service.watermark.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.prospect.giraffe.material.config.WatermarkRemovalConfig;
import com.prospect.giraffe.material.service.watermark.IWatermarkRemovalAdapter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 百度云水印去除适配器
 *
 * @author giraffe
 */
@Slf4j
@Service("baiduWatermarkAdapter")
@ConditionalOnProperty(prefix = "watermark-removal.baidu", name = "enabled", havingValue = "true")
public class BaiduWatermarkAdapter implements IWatermarkRemovalAdapter {

    @Resource
    private WatermarkRemovalConfig config;

    private OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private String accessToken;
    private long tokenExpireTime = 0;

    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return httpClient;
    }

    /**
     * 获取 Access Token
     */
    private String getAccessToken() throws Exception {
        // 如果 token 未过期，直接返回
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
                return accessToken;
            }

            String url = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials"
                    + "&client_id=" + config.getBaidu().getApiKey()
                    + "&client_secret=" + config.getBaidu().getSecretKey();

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject result = gson.fromJson(response.body().string(), JsonObject.class);
                    accessToken = result.get("access_token").getAsString();
                    int expiresIn = result.get("expires_in").getAsInt();
                    // 提前5分钟过期
                    tokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L;
                    log.info("百度云Access Token获取成功");
                    return accessToken;
                } else {
                    throw new Exception("获取百度云Access Token失败: HTTP " + response.code());
                }
            }
        }
    }

    @Override
    public File removeWatermark(File inputFile) throws Exception {
        log.info("使用百度云服务去除水印: {}", inputFile.getName());

        try {
            // 获取 Access Token
            String token = getAccessToken();

            // 读取图片并转换为 Base64
            byte[] imageBytes = Files.readAllBytes(inputFile.toPath());
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // URL 编码
            String encodedImage = URLEncoder.encode(base64Image, StandardCharsets.UTF_8.name());

            // 构建请求
            String url = "https://aip.baidubce.com/rest/2.0/image-process/v1/remove_watermark?access_token=" + token;

            RequestBody body = RequestBody.create(
                    "image=" + encodedImage,
                    MediaType.parse("application/x-www-form-urlencoded")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonObject result = gson.fromJson(responseBody, JsonObject.class);

                    // 检查错误
                    if (result.has("error_code")) {
                        int errorCode = result.get("error_code").getAsInt();
                        String errorMsg = result.has("error_msg") ? result.get("error_msg").getAsString() : "未知错误";
                        throw new Exception("百度云API错误: [" + errorCode + "] " + errorMsg);
                    }

                    // 获取处理后的图片（Base64）
                    if (!result.has("image")) {
                        throw new Exception("百度云API返回数据中没有图片");
                    }

                    String resultImage = result.get("image").getAsString();
                    byte[] resultBytes = Base64.getDecoder().decode(resultImage);

                    // 保存文件
                    File outputFile = new File(inputFile.getParent(), "cleaned_" + inputFile.getName());
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        fos.write(resultBytes);
                    }

                    log.info("百度云去水印完成: {}", outputFile.getName());
                    return outputFile;

                } else {
                    throw new Exception("百度云API调用失败: HTTP " + response.code());
                }
            }

        } catch (Exception e) {
            log.error("百度云去水印异常", e);
            throw e;
        }
    }

    @Override
    public String getProviderName() {
        return "baidu";
    }

    @Override
    public boolean isAvailable() {
        return config.getBaidu().getEnabled()
                && config.getBaidu().getApiKey() != null
                && !config.getBaidu().getApiKey().isEmpty()
                && config.getBaidu().getSecretKey() != null
                && !config.getBaidu().getSecretKey().isEmpty();
    }
}

