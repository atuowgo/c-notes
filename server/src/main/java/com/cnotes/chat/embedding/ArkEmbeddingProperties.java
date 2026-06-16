package com.cnotes.chat.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 火山引擎 Ark 多模态 embedding 配置。密钥仅来自环境变量,严禁写入仓库。
 * endpoint = {base-url}/embeddings/multimodal,响应 data 为单个对象 {embedding:[...2048...]}。
 */
@Data
@ConfigurationProperties(prefix = "ark.embedding")
public class ArkEmbeddingProperties {
    /** Ark OpenAI 兼容 base-url,如 https://ark.cn-beijing.volces.com/api/v3 */
    private String baseUrl;
    /** API Key(env: ARK_API_KEY) */
    private String apiKey;
    /** 接入点/模型名,如 ep-20260617000458-2mslf */
    private String model;
    /** 向量维度,固定 2048 */
    private int dim = 2048;
}
