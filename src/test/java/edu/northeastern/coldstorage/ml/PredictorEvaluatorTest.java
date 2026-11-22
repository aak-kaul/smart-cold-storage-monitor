package edu.northeastern.coldstorage.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PredictorEvaluatorTest {

    private static final double EPS = 1e-9;

    @Test
    void perfectModelHasZeroError() {
        PredictorEvaluator evaluator = new PredictorEvaluator(3);
        List<Double> line = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        assertEquals(0.0, evaluator.meanAbsoluteError(new LinearRegressionPredictor(), line), EPS);
    }

    @Test
    void errorMatchesHandCalculation() {
        // window 2 moving average: predicts 1.5 for 3.0 (error 1.5), 2.5 for 4.0 (error 1.5)
        PredictorEvaluator evaluator = new PredictorEvaluator(2);
        List<Double> temps = List.of(1.0, 2.0, 3.0, 4.0);
        assertEquals(1.5, evaluator.meanAbsoluteError(new MovingAveragePredictor(2), temps), EPS);
    }

    @Test
    void tooFewReadingsGiveNaN() {
        PredictorEvaluator evaluator = new PredictorEvaluator(2);
        assertTrue(Double.isNaN(evaluator.meanAbsoluteError(new LinearRegressionPredictor(), List.of(1.0, 2.0))));
    }

    @Test
    void averageErrorSkipsSeriesThatAreTooShort() {
        PredictorEvaluator evaluator = new PredictorEvaluator(2);
        List<List<Double>> series = List.of(List.of(1.0, 2.0, 3.0, 4.0), List.of(5.0));
        assertEquals(1.5, evaluator.averageError(new MovingAveragePredictor(2), series), EPS);
    }

    @Test
    void compareScoresEveryModelInOrder() {
        PredictorEvaluator evaluator = new PredictorEvaluator(3);
        TemperaturePredictor regression = new LinearRegressionPredictor();
        TemperaturePredictor average = new MovingAveragePredictor(3);
        Map<TemperaturePredictor, Double> scores =
                evaluator.compare(List.of(regression, average), List.of(List.of(1.0, 2.0, 3.0, 4.0, 5.0)));

        assertEquals(List.of(regression, average), List.copyOf(scores.keySet()));
        assertEquals(0.0, scores.get(regression), EPS);
        // moving average of 3 lags a rising line by 2.0 every time
        assertEquals(2.0, scores.get(average), EPS);
    }

    @Test
    void findBestPicksTrendModelForTrendingData() {
        PredictorEvaluator evaluator = new PredictorEvaluator(3);
        TemperaturePredictor regression = new LinearRegressionPredictor();
        List<List<Double>> rising = List.of(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0));
        assertSame(regression, evaluator.findBest(List.of(new MovingAveragePredictor(3), regression), rising));
    }

    @Test
    void findBestPicksAverageForSpikyData() {
        PredictorEvaluator evaluator = new PredictorEvaluator(4);
        TemperaturePredictor average = new MovingAveragePredictor(4);
        List<List<Double>> spiky = List.of(List.of(3.0, 3.0, 3.0, 9.0, 3.0, 3.0, 3.0, 3.0));
        assertSame(average, evaluator.findBest(List.of(new LinearRegressionPredictor(), average), spiky));
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PredictorEvaluator(0));
        PredictorEvaluator evaluator = new PredictorEvaluator(2);
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.findBest(List.of(), List.of(List.of(1.0, 2.0, 3.0))));
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.findBest(List.of(new LinearRegressionPredictor()), List.of(List.of(1.0))));
    }
}
