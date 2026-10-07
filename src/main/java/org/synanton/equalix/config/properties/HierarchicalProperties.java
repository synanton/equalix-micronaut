package org.synanton.equalix.config.properties;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.validation.Validated;

/** Tree definition for {@code app.queue.fairness-mode: hierarchical} (EQX-7). */
@Validated
@ConfigurationProperties("app.hierarchical")
@Introspected
public class HierarchicalProperties {

    /** Separates the segments of a fairness key, e.g. {@code acme/sales}. */
    @NotBlank
    private String separator;

    /**
     * Layers from the root down. A key with more segments than layers folds the remaining segments into the last
     * layer; a key with fewer segments is a leaf at a higher layer.
     */
    @Valid
    @NotEmpty
    private List<Layer> layers = new ArrayList<>();

    /**
     * Weight overrides by node path without a trailing separator, e.g. {@code "[acme]": 2.0} or
     * {@code "[acme/sales]": 3.0}. Keys containing the separator need bracket notation in YAML.
     */
    private Map<String, @Positive Double> weights = new LinkedHashMap<>();

    /** Layers, from the root, whose nodes get a {@code equalix.hierarchy.dispatches} counter; 0 disables. */
    @PositiveOrZero
    private int metricsDepth;

    @Introspected
    public static class Layer {

        @NotBlank
        private String name;

        /** Weight of nodes in this layer that have no override. Leaves use the task weight instead. */
        @Positive
        private double defaultWeight;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getDefaultWeight() {
            return defaultWeight;
        }

        public void setDefaultWeight(double defaultWeight) {
            this.defaultWeight = defaultWeight;
        }
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public List<Layer> getLayers() {
        return layers;
    }

    public void setLayers(List<Layer> layers) {
        this.layers = layers;
    }

    public Map<String, @Positive Double> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, @Positive Double> weights) {
        this.weights = weights;
    }

    public int getMetricsDepth() {
        return metricsDepth;
    }

    public void setMetricsDepth(int metricsDepth) {
        this.metricsDepth = metricsDepth;
    }
}
