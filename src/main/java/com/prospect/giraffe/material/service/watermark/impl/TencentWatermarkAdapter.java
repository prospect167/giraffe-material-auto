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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 腾讯云水印去除适配器
 *
 * @author giraffe
 */
@Slf4j
@Service("tencentWatermarkAdapter")
@ConditionalOnProperty(prefix = "watermark-removal.tencent", name = "enabled", havingValue = "true")
public class TencentWatermarkAdapter implements IWatermarkRemovalAdapter {

    @Resource
    private WatermarkRemovalConfig config;

    private OkHttpClient httpClient;
    private final Gson gson = new Gson();

    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return httpClient;
    }

    @Override
    public File removeWatermark(File inputFile) throws Exception {
        log.info("使用腾讯云服务去除水印: {}", inputFile.getName());

        try {
            // 读取图片并转换为 Base64
            byte[] imageBytes = Files.readAllBytes(inputFile.toPath());
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 构建请求参数
            JsonObject params = new JsonObject();
            params.addProperty("Image", base64Image);

            // 调用腾讯云 API
            String result = callTencentAPI("RemoveWatermark", params.toString());

            // 解析响应
            JsonObject response = gson.fromJson(result, JsonObject.class);

            if (response.has("Response")) {
                JsonObject responseData = response.getAsJsonObject("Response");

                if (responseData.has("Error")) {
                    JsonObject error = responseData.getAsJsonObject("Error");
                    throw new Exception("腾讯云API错误: " + error.get("Message").getAsString());
                }

                // 获取处理后的图片（Base64）
                String resultImage = responseData.get("WatermarkFreeImage").getAsString();
                byte[] resultBytes = Base64.getDecoder().decode(resultImage);

                // 保存文件
                File outputFile = new File(inputFile.getParent(), "cleaned_" + inputFile.getName());
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(resultBytes);
                }

                log.info("腾讯云去水印完成: {}", outputFile.getName());
                return outputFile;
            } else {
                throw new Exception("腾讯云API返回格式错误");
            }

        } catch (Exception e) {
            log.error("腾讯云去水印异常", e);
            throw e;
        }
    }

    /**
     * 调用腾讯云 API
     */
    private String callTencentAPI(String action, String payload) throws Exception {
        String service = "tiia";
        String host = service + ".tencentcloudapi.com";
        String endpoint = "https://" + host;
        String region = config.getTencent().getRegion();
        String version = "2019-05-29";

        // 生成时间戳
        long timestamp = System.currentTimeMillis() / 1000;
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(timestamp * 1000));

        // 构建 Canonical Request
        String httpRequestMethod = "POST";
        String canonicalUri = "/";
        String canonicalQueryString = "";
        String canonicalHeaders = "content-type:application/json\nhost:" + host + "\n";
        String signedHeaders = "content-type;host";
        String hashedRequestPayload = sha256Hex(payload);
        String canonicalRequest = httpRequestMethod + "\n"
                + canonicalUri + "\n"
                + canonicalQueryString + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + hashedRequestPayload;

        // 构建 String to Sign
        String algorithm = "TC3-HMAC-SHA256";
        String credentialScope = date + "/" + service + "/tc3_request";
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign = algorithm + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + hashedCanonicalRequest;

        // 计算签名
        byte[] secretDate = hmac256(("TC3" + config.getTencent().getSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac256(secretDate, service);
        byte[] secretSigning = hmac256(secretService, "tc3_request");
        String signature = bytesToHex(hmac256(secretSigning, stringToSign));

        // 构建 Authorization
        String authorization = algorithm + " "
                + "Credential=" + config.getTencent().getSecretId() + "/" + credentialScope + ", "
                + "SignedHeaders=" + signedHeaders + ", "
                + "Signature=" + signature;

        // 发送请求
        RequestBody body = RequestBody.create(payload, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(endpoint)
                .post(body)
                .addHeader("Authorization", authorization)
                .addHeader("Content-Type", "application/json")
                .addHeader("Host", host)
                .addHeader("X-TC-Action", action)
                .addHeader("X-TC-Timestamp", String.valueOf(timestamp))
                .addHeader("X-TC-Version", version)
                .addHeader("X-TC-Region", region)
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                throw new Exception("腾讯云API调用失败: HTTP " + response.code());
            }
        }
    }

    /**
     * SHA256 哈希
     */
    private String sha256Hex(String s) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(d);
    }

    /**
     * HMAC-SHA256
     */
    private byte[] hmac256(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, mac.getAlgorithm());
        mac.init(secretKeySpec);
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    @Override
    public String getProviderName() {
        return "tencent";
    }

    @Override
    public boolean isAvailable() {
        return config.getTencent().getEnabled()
                && config.getTencent().getSecretId() != null
                && !config.getTencent().getSecretId().isEmpty()
                && config.getTencent().getSecretKey() != null
                && !config.getTencent().getSecretKey().isEmpty();
    }
}

