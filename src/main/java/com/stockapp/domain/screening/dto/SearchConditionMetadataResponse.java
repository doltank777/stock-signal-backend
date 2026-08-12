package com.stockapp.domain.screening.dto;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningRightType;
import com.stockapp.domain.screening.ScreeningStage;
import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@Builder
public class SearchConditionMetadataResponse {

    private List<MetricItem> metrics;

    private List<CodeLabelItem> operators;

    private List<CodeLabelItem> rightTypes;

    private List<CodeLabelItem> logicalOperators;

    private List<CodeLabelItem> stages;

    public static SearchConditionMetadataResponse create() {

        return SearchConditionMetadataResponse.builder()
                .metrics(
                        Arrays.stream(ScreeningMetric.values())
                                .map(MetricItem::from)
                                .toList())
                .operators(
                        Arrays.stream(ScreeningOperator.values())
                                .map(operator -> new CodeLabelItem(
                                        operator.name(),
                                        getOperatorLabel(operator)))
                                .toList())
                .rightTypes(
                        Arrays.stream(ScreeningRightType.values())
                                .map(rightType -> new CodeLabelItem(
                                        rightType.name(),
                                        getRightTypeLabel(rightType)))
                                .toList())
                .logicalOperators(
                        Arrays.stream(ScreeningLogicalOperator.values())
                                .map(logicalOperator -> new CodeLabelItem(
                                        logicalOperator.name(),
                                        logicalOperator.name()))
                                .toList())
                .stages(
                        Arrays.stream(ScreeningStage.values())
                                .map(stage -> new CodeLabelItem(
                                        stage.name(),
                                        getStageLabel(stage)))
                                .toList())
                .build();
    }

    private static String getMetricLabel(
            ScreeningMetric metric) {

        return switch (metric) {

            case CURRENT_PRICE ->
                "현재가";

            case CHANGE_RATE ->
                "등락률";

            case VOLUME ->
                "현재 거래량";

            case AVERAGE_VOLUME ->
                "평균 거래량";

            case VOLUME_RATIO ->
                "평균 거래량 대비 거래량 비율";

            case MOVING_AVERAGE ->
                "이동평균";
        };
    }

    private static boolean requiresPeriod(
            ScreeningMetric metric) {

        return switch (metric) {

            case AVERAGE_VOLUME,
                    VOLUME_RATIO,
                    MOVING_AVERAGE ->
                true;

            default -> false;
        };
    }

    private static String getOperatorLabel(
            ScreeningOperator operator) {

        return switch (operator) {

            case GREATER_THAN ->
                "초과";

            case GREATER_THAN_OR_EQUAL ->
                "이상";

            case LESS_THAN ->
                "미만";

            case LESS_THAN_OR_EQUAL ->
                "이하";

            case EQUAL ->
                "같음";
        };
    }

    private static String getRightTypeLabel(
            ScreeningRightType rightType) {

        return switch (rightType) {

            case VALUE ->
                "값";

            case METRIC ->
                "지표";
        };
    }

    private static String getStageLabel(
            ScreeningStage stage) {

        return switch (stage) {

            case SCREENING ->
                "1차 후보 조건";

            case SIGNAL ->
                "최종 Signal 조건";
        };
    }

    @Getter
    public static class MetricItem {

        private final String code;

        private final String label;

        private final boolean periodRequired;

        private MetricItem(
                String code,
                String label,
                boolean periodRequired) {
            this.code = code;
            this.label = label;
            this.periodRequired = periodRequired;
        }

        public static MetricItem from(
                ScreeningMetric metric) {

            return new MetricItem(
                    metric.name(),
                    getMetricLabel(metric),
                    requiresPeriod(metric));
        }
    }

    @Getter
    public static class CodeLabelItem {

        private final String code;

        private final String label;

        public CodeLabelItem(
                String code,
                String label) {
            this.code = code;
            this.label = label;
        }
    }
}