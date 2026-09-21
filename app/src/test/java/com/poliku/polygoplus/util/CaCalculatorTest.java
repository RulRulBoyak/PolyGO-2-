package com.poliku.polygoplus.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class CaCalculatorTest {
    @Test
    public void mixedMaximumsCalculateContributionAndTarget() {
        CaCalculator.Result result = CaCalculator.calculate(60, 40, 50,
                Arrays.asList(new CaCalculator.Component(8, 10, 20),
                        new CaCalculator.Component(30, 50, 40)));
        assertEquals(40, result.caContribution, 0.001);
        assertEquals(66.667, result.caPercent, 0.001);
        assertEquals(25, result.requiredFePercent, 0.001);
        assertTrue(result.targetPossible);
    }

    @Test
    public void finalExamMinimumIsAlwaysRespected() {
        CaCalculator.Result result = CaCalculator.calculate(60, 40, 40,
                Collections.singletonList(new CaCalculator.Component(60, 60, 60)));
        assertEquals(20, result.requiredFePercent, 0.001);
    }

    @Test
    public void impossibleTargetIsFlagged() {
        CaCalculator.Result result = CaCalculator.calculate(60, 40, 90,
                Collections.singletonList(new CaCalculator.Component(10, 100, 60)));
        assertFalse(result.targetPossible);
    }

    @Test
    public void continuousAssessmentOnlyCourseHasNoExamTarget() {
        CaCalculator.Result result = CaCalculator.calculate(100, 0, 40,
                Collections.singletonList(new CaCalculator.Component(50, 100, 100)));
        assertEquals(0, result.requiredFePercent, 0.001);
        assertTrue(result.targetPossible);
    }

    @Test
    public void componentWeightsMustMatchCaWeight() {
        assertThrows(IllegalArgumentException.class, () ->
                CaCalculator.calculate(60, 40, 40,
                        Collections.singletonList(new CaCalculator.Component(10, 10, 20))));
    }
}
