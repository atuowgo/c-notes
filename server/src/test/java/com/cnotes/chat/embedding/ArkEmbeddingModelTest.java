package com.cnotes.chat.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 离线单测:用 Spring 的 {@link MockRestServiceServer} 绑定到注入的 RestClient.Builder,
 * 喂火山引擎 Ark 多模态 embedding 的真实响应形状(data 为单个对象 {embedding:[...]}),
 * 断言 ArkEmbeddingModel 正确解析为 2048 维 float[] 并命中 /embeddings/multimodal。
 * 门控真实调用见 {@link #realArkReturns2048Dim()}。
 */
class ArkEmbeddingModelTest {

    private static String fakeEmbeddingJson(int dim) {
        String nums = java.util.stream.IntStream.range(0, dim)
                .mapToObj(i -> "0.01")
                .collect(Collectors.joining(","));
        return "{\"data\":{\"embedding\":[" + nums + "]},\"model\":\"ep-test\"}";
    }

    @Test
    void parsesSingleObjectEmbeddingTo2048Dim() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        ArkEmbeddingProperties props = new ArkEmbeddingProperties();
        props.setBaseUrl("https://ark.example.com/api/v3");
        props.setApiKey("test-key");
        props.setModel("ep-20260617000458-2mslf");
        props.setDim(2048);

        server.expect(requestTo("https://ark.example.com/api/v3/embeddings/multimodal"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("ep-20260617000458-2mslf"))
                .andExpect(jsonPath("$.input[0].type").value("text"))
                .andExpect(jsonPath("$.input[0].text").value("天很蓝，海很深"))
                .andRespond(withSuccess(fakeEmbeddingJson(2048),
                        org.springframework.http.MediaType.APPLICATION_JSON));

        ArkEmbeddingModel model = new ArkEmbeddingModel(builder, props);

        assertThat(model.dimensions()).isEqualTo(2048);

        float[] vec = model.embed("天很蓝，海很深");
        assertThat(vec).hasSize(2048);
        assertThat(vec[0]).isEqualTo(0.01f);

        server.verify();
    }

    @Test
    void callReturnsEmbeddingResponseForEachInput() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder)
                .ignoreExpectOrder(true).build();

        ArkEmbeddingProperties props = new ArkEmbeddingProperties();
        props.setBaseUrl("https://ark.example.com/api/v3");
        props.setApiKey("test-key");
        props.setModel("ep-x");
        props.setDim(2048);

        server.expect(org.springframework.test.web.client.ExpectedCount.times(2),
                        requestTo("https://ark.example.com/api/v3/embeddings/multimodal"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(fakeEmbeddingJson(2048),
                        org.springframework.http.MediaType.APPLICATION_JSON));

        ArkEmbeddingModel model = new ArkEmbeddingModel(builder, props);
        EmbeddingResponse resp = model.call(new EmbeddingRequest(List.of("a", "b"), null));

        assertThat(resp.getResults()).hasSize(2);
        assertThat(resp.getResults().get(0).getOutput()).hasSize(2048);
        assertThat(resp.getResults().get(1).getOutput()).hasSize(2048);

        server.verify();
    }

    /** 门控真实调用:本机带 ARK_API_KEY 时验证 200 + 2048 维。 */
    @Test
    @EnabledIfEnvironmentVariable(named = "ARK_API_KEY", matches = ".+")
    void realArkReturns2048Dim() {
        ArkEmbeddingProperties props = new ArkEmbeddingProperties();
        props.setBaseUrl(System.getenv().getOrDefault("ARK_EMBEDDING_BASE_URL",
                "https://ark.cn-beijing.volces.com/api/v3"));
        props.setApiKey(System.getenv("ARK_API_KEY"));
        props.setModel(System.getenv().getOrDefault("ARK_EMBEDDING_MODEL",
                "ep-20260617000458-2mslf"));
        props.setDim(2048);

        ArkEmbeddingModel model = new ArkEmbeddingModel(RestClient.builder(), props);
        float[] vec = model.embed("天很蓝，海很深");
        assertThat(vec).hasSize(2048);
    }
}
