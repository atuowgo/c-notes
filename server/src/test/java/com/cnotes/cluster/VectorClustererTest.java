package com.cnotes.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 纯算法测试(无 IO):DBSCAN 在余弦距离上把方向相近的向量聚成簇,把孤立向量判为噪声。
 * 用低维手工向量,确定性验证「两簇 + 一个噪声」的切分。
 */
class VectorClustererTest {

    private final VectorClusterer clusterer = new VectorClusterer();

    @Test
    void groupsNearbyVectorsAndDropsNoise() {
        // 簇A:沿 x 轴的三条;簇B:沿 y 轴的两条;噪声:对角线孤点。
        List<float[]> vecs = List.of(
            new float[]{1f, 0f},      // 0  A
            new float[]{0.99f, 0.05f},// 1  A
            new float[]{0.98f, 0.02f},// 2  A
            new float[]{0f, 1f},      // 3  B
            new float[]{0.03f, 0.99f},// 4  B
            new float[]{0.7f, 0.7f}   // 5  噪声(与两簇都约 45°,余弦距离 ~0.29)
        );

        List<List<Integer>> clusters = clusterer.dbscan(vecs, 0.05, 2);

        assertThat(clusters).hasSize(2);
        // 找到包含 0 的簇 = A(含 0,1,2),包含 3 的簇 = B(含 3,4)。
        List<Integer> a = clusters.stream().filter(c -> c.contains(0)).findFirst().orElseThrow();
        List<Integer> b = clusters.stream().filter(c -> c.contains(3)).findFirst().orElseThrow();
        assertThat(a).containsExactlyInAnyOrder(0, 1, 2);
        assertThat(b).containsExactlyInAnyOrder(3, 4);
        // 噪声点 5 不属于任何簇。
        assertThat(clusters.stream().anyMatch(c -> c.contains(5))).isFalse();
    }

    @Test
    void emptyInputReturnsNoClusters() {
        assertThat(clusterer.dbscan(List.of(), 0.3, 2)).isEmpty();
    }

    @Test
    void allIsolatedPointsAreNoise() {
        List<float[]> vecs = List.of(
            new float[]{1f, 0f},
            new float[]{0f, 1f},
            new float[]{-1f, 0f}
        );
        // eps 很小,彼此都不相邻 → 无簇。
        assertThat(clusterer.dbscan(vecs, 0.05, 2)).isEmpty();
    }

    @Test
    void cosineDistanceBasics() {
        assertThat(VectorClusterer.cosineDistance(new float[]{1, 0}, new float[]{1, 0}))
            .isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(VectorClusterer.cosineDistance(new float[]{1, 0}, new float[]{0, 1}))
            .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        // 零向量 → 最远。
        assertThat(VectorClusterer.cosineDistance(new float[]{0, 0}, new float[]{1, 1}))
            .isEqualTo(1.0);
    }
}
