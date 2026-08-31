package damjay.floating.projects.calculate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CompoundExpressionTest {

    @Test
    public void testSimpleAddition() {
        CompoundExpression expression = new CompoundExpression("10+20");
        Expression result = expression.compute();
        assertNotNull("compute() must return a result", result);
        assertEquals("30", result.getExact());
    }

    @Test
    public void testImplicitMultiplication() {
        CompoundExpression expression = new CompoundExpression("5(6)");
        Expression result = expression.compute();
        assertNotNull("compute() must return a result", result);
        assertEquals("30", result.getExact());
    }
}
