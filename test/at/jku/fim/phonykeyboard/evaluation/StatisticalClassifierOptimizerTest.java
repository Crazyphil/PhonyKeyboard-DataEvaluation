package at.jku.fim.phonykeyboard.evaluation;

import at.jku.fim.phonykeyboard.latin.utils.Log;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class StatisticalClassifierOptimizerTest {
    StatisticalClassifierOptimizer optimizer;

    /**
     * Sets up the test fixture.
     * (Called before every test case method.)
     */
    @Before
    public void setUp() {
        optimizer = new StatisticalClassifierOptimizer(".", false);
    }

    /**
     * Tears down the test fixture.
     * (Called after every test case method.)
     */
    @After
    public void tearDown() {

    }

    @Test
    public void testCalcEER() {
        List<Double> p = new ArrayList<>(10);
        List<Double> n = new ArrayList<>(10);
        double result = optimizer.calcEER(p, n);
        assertTrue(Double.isNaN(result));

        p.add(0.1);
        n.add(0.2);
        assertEquals(0, optimizer.calcEER(p, n), 0);

        p.add(0.2);
        n.add(0.1);
        assertEquals(0.5, optimizer.calcEER(p, n), 0);

        for (int i = 1; i <= 2; i++) {
            p.add(0.1);
            n.add(0.2);
        }
        assertEquals(0.25, optimizer.calcEER(p, n), 0);

        for (int i = 1; i <= 2; i++) {
            p.add(0.2);
            n.add(0.1);
        }
        assertEquals(0.5, optimizer.calcEER(p, n), 0);
    }
}
