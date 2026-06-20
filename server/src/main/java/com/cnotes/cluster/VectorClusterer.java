package com.cnotes.cluster;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯向量自动聚簇:对文章内容 embedding 跑 <b>DBSCAN</b>(基于余弦距离),
 * 在「受控标签集」之外按语义把相近文章聚成主题簇 —— 不依赖 LLM,只靠向量。
 *
 * <p>选 DBSCAN 而非 K-means:无需预设簇数 k,且天然把不相近的文章判为噪声(不强行归簇),
 * 契合「只在确有语义聚集时才建议新主题」。距离用 1 − 余弦相似度,对 embedding 的尺度更稳健。
 *
 * <p>纯算法、无 IO,便于离线确定性测试;向量由上层(Ark embedding)注入。
 */
@Component
public class VectorClusterer {

    /** DBSCAN 结果:每个簇是一组「输入向量下标」;噪声点不出现在任何簇里。 */
    public List<List<Integer>> dbscan(List<float[]> vectors, double eps, int minPts) {
        int n = vectors == null ? 0 : vectors.size();
        List<List<Integer>> clusters = new ArrayList<>();
        if (n == 0) return clusters;

        int[] label = new int[n];           // 0=未访问, -1=噪声, >0=簇号
        int clusterId = 0;
        for (int i = 0; i < n; i++) {
            if (label[i] != 0) continue;    // 已访问
            List<Integer> neighbors = regionQuery(vectors, i, eps);
            if (neighbors.size() < minPts) {
                label[i] = -1;              // 暂判噪声(后续可能被吸收为边界点)
                continue;
            }
            clusterId++;
            List<Integer> members = new ArrayList<>();
            expandCluster(vectors, label, i, neighbors, clusterId, eps, minPts, members);
            clusters.add(members);
        }
        return clusters;
    }

    private void expandCluster(List<float[]> vectors, int[] label, int core,
                               List<Integer> neighbors, int clusterId,
                               double eps, int minPts, List<Integer> members) {
        label[core] = clusterId;
        members.add(core);
        // 用可增长的队列做广度扩张(neighbors 会在过程中追加)。
        for (int q = 0; q < neighbors.size(); q++) {
            int p = neighbors.get(q);
            if (label[p] == -1) {           // 之前的噪声,作为边界点纳入
                label[p] = clusterId;
                members.add(p);
            }
            if (label[p] != 0) continue;    // 已属某簇
            label[p] = clusterId;
            members.add(p);
            List<Integer> pNeighbors = regionQuery(vectors, p, eps);
            if (pNeighbors.size() >= minPts) {
                neighbors.addAll(pNeighbors); // p 也是核心点,继续扩张
            }
        }
    }

    /** 与点 i 的余弦距离 <= eps 的所有点(含自身)。 */
    private List<Integer> regionQuery(List<float[]> vectors, int i, double eps) {
        List<Integer> out = new ArrayList<>();
        float[] vi = vectors.get(i);
        for (int j = 0; j < vectors.size(); j++) {
            if (cosineDistance(vi, vectors.get(j)) <= eps) out.add(j);
        }
        return out;
    }

    /** 余弦距离 = 1 − 余弦相似度;任一向量为零向量时距离取 1(最远)。 */
    static double cosineDistance(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 1.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 1.0;
        double sim = dot / (Math.sqrt(na) * Math.sqrt(nb));
        return 1.0 - sim;
    }
}
