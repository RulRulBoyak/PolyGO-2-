package com.poliku.polygoplus.util;

import java.util.List;

public final class CaCalculator {
    public static final double CA_PASS_PERCENT = 40.0;
    public static final double FE_PASS_PERCENT = 20.0;
    public static final double COURSE_PASS_PERCENT = 40.0;

    private CaCalculator() {
    }

    public static Result calculate(double caWeight, double feWeight, double target,
                                   List<Component> components) {
        if (caWeight < 0 || feWeight < 0 || Math.abs(caWeight + feWeight - 100.0) > 0.01) {
            throw new IllegalArgumentException("CA and FE weights must total 100%");
        }
        double configuredWeight = 0;
        double contribution = 0;
        for (Component item : components) {
            if (item.maximum <= 0 || item.obtained < 0 || item.obtained > item.maximum
                    || item.weight <= 0) {
                throw new IllegalArgumentException("Invalid assessment component");
            }
            configuredWeight += item.weight;
            contribution += item.obtained / item.maximum * item.weight;
        }
        if (Math.abs(configuredWeight - caWeight) > 0.01) {
            throw new IllegalArgumentException("Component weights must equal the CA weight");
        }
        double caPercent = caWeight == 0 ? 0 : contribution / caWeight * 100.0;
        if (feWeight == 0) {
            return new Result(contribution, caPercent, 0, contribution >= target,
                    caPercent >= CA_PASS_PERCENT, contribution >= COURSE_PASS_PERCENT);
        }
        double targetFe = Math.max(0, (target - contribution) / feWeight * 100.0);
        double requiredFe = Math.max(FE_PASS_PERCENT, targetFe);
        return new Result(contribution, caPercent, requiredFe, requiredFe <= 100.0,
                caPercent >= CA_PASS_PERCENT, contribution + feWeight >= COURSE_PASS_PERCENT);
    }

    public static final class Component {
        public final double obtained;
        public final double maximum;
        public final double weight;

        public Component(double obtained, double maximum, double weight) {
            this.obtained = obtained;
            this.maximum = maximum;
            this.weight = weight;
        }
    }

    public static final class Result {
        public final double caContribution;
        public final double caPercent;
        public final double requiredFePercent;
        public final boolean targetPossible;
        public final boolean caMinimumMet;
        public final boolean coursePassPossible;

        Result(double caContribution, double caPercent, double requiredFePercent,
               boolean targetPossible, boolean caMinimumMet, boolean coursePassPossible) {
            this.caContribution = caContribution;
            this.caPercent = caPercent;
            this.requiredFePercent = requiredFePercent;
            this.targetPossible = targetPossible;
            this.caMinimumMet = caMinimumMet;
            this.coursePassPossible = coursePassPossible;
        }
    }
}
