package com.cnotes.chat.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 火山引擎 Ark 多模态 embedding 的 Spring AI {@link EmbeddingModel} 实现。
 *
 * <p>POST {base-url}/embeddings/multimodal,请求体:
 * <pre>{"model":..,"input":[{"type":"text","text":".."}]}</pre>
 * 纯文本走 {@code /embeddings} 会 400,故必须用 multimodal 端点 + 带 type 的 input 数组。
 * 响应 {@code data} 为<b>单个对象</b> {@code {embedding:[...2048...]}}(非 OpenAI 的数组)。
 *
 * <p>{@code @Primary} 以避免与 OpenAI 自动配置的 EmbeddingModel 歧义;
 * 并在 yml 关掉 openai 的 embedding 自动配置(spring.ai.openai.embedding.enabled=false)。
 */
@Component
@Primary
public class ArkEmbeddingModel implements EmbeddingModel {

    private final RestClient restClient;
    private final ArkEmbeddingProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ArkEmbeddingModel(RestClient.Builder restClientBuilder, ArkEmbeddingProperties props) {
        this.props = props;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(callOne(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return callOne(document.getText());
    }

    @Override
    public int dimensions() {
        return props.getDim();
    }

    private float[] callOne(String text) {
        Map<String, Object> body = Map.of(
                "model", props.getModel(),
                "input", List.of(Map.of("type", "text", "text", text)));

        String json = restClient.post()
                .uri(props.getBaseUrl() + "/embeddings/multimodal")
                .header("Authorization", "Bearer " + props.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(json);
        JsonNode arr = root.path("data").path("embedding");
        if (!arr.isArray()) {
            throw new IllegalStateException("Ark embedding 响应缺少 data.embedding 数组: " + json);
        }
        float[] vec = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            vec[i] = (float) arr.get(i).asDouble();
        }
        return vec;
    }
}
