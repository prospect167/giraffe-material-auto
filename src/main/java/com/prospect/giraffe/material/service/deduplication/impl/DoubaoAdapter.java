package com.prospect.giraffe.material.service.deduplication.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.prospect.giraffe.material.config.AiImageProcessingConfig;
import com.prospect.giraffe.material.config.AiImageProcessingConfig.DoubaoConfig;
import com.prospect.giraffe.material.constants.PromptConstants;
import com.prospect.giraffe.material.service.deduplication.IAIImageAdapter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 豆包图像处理适配器（主方案）
 * 使用火山引擎方舟平台 - 豆包 Seedream 4.5 图片生成模型
 * 参考文档：https://www.volcengine.com/docs/82379/1824121?lang=zh
 *         https://www.volcengine.com/docs/82379/1541523?lang=zh
 * 
 * 采用【图生图】模式：传入原始图片 + 提示词，基于原图进行编辑（去水印/去文字/去logo）
 * 
 * API调用方式等效于官方SDK：
 * <pre>
 * ArkService service = ArkService.builder()
 *         .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
 *         .apiKey(apiKey)
 *         .build();
 * GenerateImagesRequest request = GenerateImagesRequest.builder()
 *         .model("doubao-seedream-4-5-251128")
 *         .prompt("...")
 *         .image("https://xxx.com/original.png")  // 传入原始图片URL或data URI
 *         .size("2K")
 *         .sequentialImageGeneration("disabled")
 *         .responseFormat(ResponseFormat.Url)
 *         .stream(false)
 *         .watermark(false)
 *         .build();
 * ImagesResponse response = service.generateImages(request);
 * String imageUrl = response.getData().get(0).getUrl();
 * </pre>
 *
 * @author giraffe
 */
@Slf4j
@Service("doubaoAdapter")
public class DoubaoAdapter implements IAIImageAdapter {
    
    @Autowired
    private AiImageProcessingConfig config;
    
    private OkHttpClient httpClient;
    private final Gson gson = new Gson();
    
    @PostConstruct
    public void init() {
        log.info("豆包适配器初始化完成（Seedream 4.5 - 图生图模式）");
        DoubaoConfig doubaoConfig = config.getDoubao();
        log.info("豆包配置详情:");
        log.info("  - enabled: {}", doubaoConfig.getEnabled());
        log.info("  - apiKey: {} (长度: {})", 
            doubaoConfig.getApiKey() != null && !doubaoConfig.getApiKey().isEmpty() ? "已配置" : "未配置",
            doubaoConfig.getApiKey() != null ? doubaoConfig.getApiKey().length() : 0);
        log.info("  - baseUrl: {}", doubaoConfig.getBaseUrl());
        log.info("  - model: {}", doubaoConfig.getModel());
        log.info("  - size: {}", doubaoConfig.getSize());
        log.info("  - strength: {}", doubaoConfig.getStrength());
        log.info("  - 最终可用性: {}", isAvailable());
    }
    
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                    .writeTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return httpClient;
    }
    
    @Override
    public byte[] processImage(byte[] imageBytes, List<String> preserveTexts, 
                              Boolean removeWatermarks, Boolean removeLogos, 
                              Boolean removeExtraTexts) throws Exception {
        String apiKey = config.getDoubao().getApiKey();
        
        if (apiKey == null || apiKey.isEmpty()) {
            String errorMsg = "豆包API密钥未配置，请设置环境变量 ARK_API_KEY 或在配置文件中配置 ai-image-processing.doubao.api-key\n" +
                    "获取地址：https://console.volcengine.com/ark/region:ark+cn-beijing/apikey";
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        // 1. 构建图片编辑提示词
        String prompt = buildPrompt(preserveTexts, removeWatermarks, removeLogos, removeExtraTexts);
        
        // 2. 将原始图片编码为Base64 data URI
        String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
        String imageDataUri = detectMimeType(imageBytes) + imageBase64;
        log.info("原始图片编码完成，Base64长度: {}", imageBase64.length());
        
        // 3. 调用 Seedream 4.5 图生图 API（传入原始图片 + 提示词）
        String resultImageUrl = callImageEditApi(apiKey, prompt, imageDataUri);
        
        // 4. 从返回的URL下载生成的图片
        byte[] resultBytes = downloadImage(resultImageUrl);
        
        log.info("豆包API（Seedream 4.5 图生图）处理成功，生成图片大小: {} bytes", resultBytes.length);
        return resultBytes;
    }
    
    /**
     * 检测图片MIME类型，返回data URI前缀
     */
    private String detectMimeType(byte[] imageBytes) {
        // 通过文件头字节判断图片类型
        if (imageBytes.length >= 3 && imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8) {
            return "data:image/jpeg;base64,";
        } else if (imageBytes.length >= 8 && imageBytes[0] == (byte) 0x89 && imageBytes[1] == 0x50
                && imageBytes[2] == 0x4E && imageBytes[3] == 0x47) {
            return "data:image/png;base64,";
        } else if (imageBytes.length >= 4 && imageBytes[0] == 0x47 && imageBytes[1] == 0x49
                && imageBytes[2] == 0x46) {
            return "data:image/gif;base64,";
        } else if (imageBytes.length >= 4 && imageBytes[0] == 0x52 && imageBytes[1] == 0x49
                && imageBytes[2] == 0x46 && imageBytes[3] == 0x46) {
            return "data:image/webp;base64,";
        }
        // 默认当作JPEG
        return "data:image/jpeg;base64,";
    }
    
    /**
     * 调用火山引擎方舟平台 图生图API（基于原图编辑）
     * POST {baseUrl}/images/generations
     * 
     * 关键区别：通过 image 字段传入原始图片，实现图生图而非纯文生图
     * 
     * 等效于官方SDK调用：
     * GenerateImagesRequest.builder()
     *     .model("doubao-seedream-4-5-251128")
     *     .prompt(prompt)
     *     .image(imageDataUri)                   // 关键：传入原始图片
     *     .size("2K")
     *     .sequentialImageGeneration("disabled")
     *     .responseFormat(ResponseFormat.Url)
     *     .stream(false)
     *     .watermark(false)
     *     .build();
     *
     * @param apiKey     API密钥
     * @param prompt     编辑提示词（描述要做什么修改）
     * @param imageDataUri 原始图片的 data URI（data:image/jpeg;base64,...）或URL
     * @return 生成图片的URL或base64标识
     */
    private String callImageEditApi(String apiKey, String prompt, String imageDataUri) throws Exception {
        DoubaoConfig doubaoConfig = config.getDoubao();
        String apiUrl = doubaoConfig.getBaseUrl() + "/images/generations";
        String model = doubaoConfig.getModel();
        String size = doubaoConfig.getSize();
        Double strength = doubaoConfig.getStrength();
        
        log.info("调用豆包 Seedream 4.5 图生图API:");
        log.info("  - URL: {}", apiUrl);
        log.info("  - model: {}", model);
        log.info("  - size: {}", size);
        log.info("  - strength: {} (值越低越接近原图)", strength);
        log.info("  - prompt: {}", prompt);
        log.info("  - image: 已传入原始图片（长度: {}）", imageDataUri.length());
        
        // 构建请求JSON（与官方SDK GenerateImagesRequest 格式一致）
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model);
        requestJson.addProperty("prompt", prompt);
        
        // 【核心】image 字段：传入原始图片（URL或data URI），实现图生图
        requestJson.addProperty("image", imageDataUri);
        
        // 【关键】strength：控制对原图的修改幅度（0.0~1.0）
        // 值越低越接近原图，0.50 在去水印和保真之间取平衡
        requestJson.addProperty("strength", strength);
        
        requestJson.addProperty("size", size);
        requestJson.addProperty("sequential_image_generation", "disabled");
        requestJson.addProperty("response_format", "url");
        requestJson.addProperty("stream", false);
        requestJson.addProperty("watermark", false);
        
        String requestBodyStr = requestJson.toString();
        // 不打印完整请求体（包含base64图片，太长）
        log.debug("请求体大小: {} bytes", requestBodyStr.length());
        
        // 构建HTTP请求
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(requestBodyStr, mediaType);
        
        // 认证方式：Bearer Token
        Request request = new Request.Builder()
                .url(apiUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        
        log.info("开始调用豆包 Seedream 4.5 图生图API: {}", apiUrl);
        
        try (Response response = getHttpClient().newCall(request).execute()) {
            int code = response.code();
            String responseBody = response.body() != null ? response.body().string() : "";
            
            log.info("豆包 Seedream 4.5 API 响应码: {}", code);
            log.debug("豆包 Seedream 4.5 API 响应体: {}", responseBody);
            
            if (!response.isSuccessful()) {
                log.error("豆包 Seedream 4.5 API 调用失败: HTTP {}, 响应: {}", code, responseBody);
                throw new Exception("豆包 Seedream 4.5 API 调用失败: HTTP " + code + ", " + responseBody);
            }
            
            // 解析响应
            // 格式: {"created": 1234567890, "data": [{"url": "https://..."}]}
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            // 检查错误
            if (jsonResponse.has("error")) {
                JsonObject error = jsonResponse.getAsJsonObject("error");
                String errorMsg = error.has("message") ? error.get("message").getAsString() : jsonResponse.get("error").toString();
                log.error("豆包 Seedream 4.5 API 返回错误: {}", errorMsg);
                throw new Exception("豆包 Seedream 4.5 API 错误: " + errorMsg);
            }
            
            // 提取图片URL
            String imageUrl = extractImageUrl(jsonResponse);
            log.info("豆包 Seedream 4.5 图生图成功，结果URL: {}", imageUrl);
            return imageUrl;
            
        } catch (IOException e) {
            String errorMsg = String.format("豆包 Seedream 4.5 API 网络错误: %s, URL: %s", e.getMessage(), apiUrl);
            log.error(errorMsg, e);
            throw new Exception(errorMsg, e);
        }
    }
    
    /**
     * 从API响应中提取图片URL
     * 等效于SDK: imagesResponse.getData().get(0).getUrl()
     * 
     * 响应格式:
     * {
     *   "created": 1234567890,
     *   "data": [
     *     {
     *       "url": "https://..."
     *     }
     *   ]
     * }
     */
    private String extractImageUrl(JsonObject response) {
        if (response.has("data")) {
            try {
                JsonArray dataArray = response.getAsJsonArray("data");
                if (dataArray != null && dataArray.size() > 0) {
                    JsonObject firstItem = dataArray.get(0).getAsJsonObject();
                    if (firstItem.has("url")) {
                        return firstItem.get("url").getAsString();
                    }
                    if (firstItem.has("b64_json")) {
                        log.info("豆包 Seedream 4.5 返回了base64格式图片");
                        return "base64:" + firstItem.get("b64_json").getAsString();
                    }
                }
            } catch (Exception e) {
                log.warn("解析data数组失败，尝试其他格式: {}", e.getMessage());
            }
        }
        
        log.error("无法从豆包 Seedream 4.5 API 响应中提取图片，响应: {}", response.toString());
        throw new RuntimeException("无法从豆包 Seedream 4.5 API 响应中提取图片，请检查API返回格式");
    }
    
    /**
     * 从URL下载图片
     */
    private byte[] downloadImage(String imageUrl) throws Exception {
        // 如果是base64格式，直接解码
        if (imageUrl.startsWith("base64:")) {
            String base64Data = imageUrl.substring("base64:".length());
            return Base64.getDecoder().decode(base64Data);
        }
        
        log.info("开始下载生成的图片: {}", imageUrl);
        
        Request request = new Request.Builder()
                .url(imageUrl)
                .get()
                .build();
        
        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("下载图片失败: HTTP " + response.code());
            }
            
            byte[] imageBytes;
            try (InputStream is = response.body().byteStream()) {
                imageBytes = toByteArray(is);
            }
            
            log.info("图片下载完成，大小: {} bytes", imageBytes.length);
            return imageBytes;
            
        } catch (IOException e) {
            log.error("下载图片失败: {}, URL: {}", e.getMessage(), imageUrl, e);
            throw new Exception("下载生成的图片失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * InputStream 转 byte[]
     */
    private byte[] toByteArray(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
    
    /**
     * 构建提示词
     * 
     * 从 PromptConstants 常量类读取当前提示词模板，替换变量后生成最终提示词
     * 所有历史版本的提示词都保留在常量类中，方便对比和回退
     * 
     * 模板支持的变量：
     *   {removeTargets} - 要去除的内容，如"水印、logo、多余文字"
     *   {preserveTexts} - 要保留的文字，如"，保留「生命树」"（无保留文字时为空字符串）
     * 
     * @see com.prospect.giraffe.material.constants.PromptConstants
     */
    private String buildPrompt(List<String> preserveTexts, Boolean removeWatermarks, 
                              Boolean removeLogos, Boolean removeExtraTexts) {
        // 1. 构建 {removeTargets} 变量值
        List<String> removeTargetList = new ArrayList<>();
        if (removeWatermarks != null && removeWatermarks) {
            removeTargetList.add("水印");
        }
        if (removeLogos != null && removeLogos) {
            removeTargetList.add("logo");
        }
        if (removeExtraTexts != null && removeExtraTexts) {
            removeTargetList.add("多余文字");
        }
        String removeTargets = removeTargetList.isEmpty() 
                ? "水印和多余文字" 
                : String.join("、", removeTargetList);
        
        // 2. 构建 {preserveTexts} 变量值
        String preserveTextsStr = "";
        if (preserveTexts != null && !preserveTexts.isEmpty()) {
            preserveTextsStr = "，保留「" + String.join("」「", preserveTexts) + "」";
        }
        
        // 3. 从常量类读取当前提示词模板，替换变量
        String template = PromptConstants.CURRENT_PROMPT;
        String prompt = template
                .replace("{removeTargets}", removeTargets)
                .replace("{preserveTexts}", preserveTextsStr);
        
        log.info("提示词模板（{}）: {}", "PromptConstants.CURRENT_PROMPT", template);
        log.info("最终提示词: {}", prompt);
        
        return prompt;
    }
    
    @Override
    public String getProviderName() {
        return "doubao";
    }
    
    @Override
    public boolean isAvailable() {
        DoubaoConfig doubaoConfig = config.getDoubao();
        return doubaoConfig.getEnabled() 
                && doubaoConfig.getApiKey() != null
                && !doubaoConfig.getApiKey().isEmpty();
    }
}
