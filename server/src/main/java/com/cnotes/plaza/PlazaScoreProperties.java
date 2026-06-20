package com.cnotes.plaza;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 广场质量分权重(绑定 plaza.score.*)。 */
@ConfigurationProperties(prefix = "plaza.score")
@Data
public class PlazaScoreProperties {
    private double collect = 3;
    private double bookmark = 2;
    private double like = 2;
    private double comment = 1;
    private double degree = 1;
    private double aiWeight = 1.0;
    private double freshnessHalflifeDays = 7;
    private int candidateCap = 500;
}
