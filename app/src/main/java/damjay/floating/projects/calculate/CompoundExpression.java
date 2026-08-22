package damjay.floating.projects.calculate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Objects;

public class CompoundExpression extends Expression {
    public static final int POWER_OPERATOR = 0;
    public static final int DIVIDE_OPERATOR = 1;
    public static final int MULTIPLY_OPERATOR = 2;
    public static final int PLUS_OPERATOR = 3;
    public static final int MINUS_OPERATOR = 4;

    public static final char[] OPERATORS = {'^', '\u00F7', '\u00D7', '+', '-'};
    public static final String[] OPERATOR_TYPE = {
            ExpressionType.PowerOperator,
            ExpressionType.DivideOperator,
            ExpressionType.MultiplyOperator,
            ExpressionType.PlusOperator,
            ExpressionType.MinusOperator
    };

    private final ArrayList<Expression> expressions;

    public CompoundExpression(String input) {
        expressions = makeExpressions(input);
        setType(ExpressionType.CompoundExpression);
    }

    private ArrayList<Expression> makeExpressions(String input) {
        ArrayList<Expression> expressions = new ArrayList<>();
        int i = 0;

        while (i < input.length()) {
            String temp = "";
            char character = input.charAt(i);

            // Handle leading sign
            if (character == '+' || character == '-') {
                if (i + 1 >= input.length()) {
                    return null;
                }
                char nextChar = input.charAt(i + 1);
                if ((nextChar < '0' || nextChar > '9') && nextChar != '.' && nextChar != 'E' && nextChar != '(') {
                    return null;
                }
                temp = String.valueOf(character);
                i++;
            }

            // Handle parenthesized sub-expression
            if (character == '(') {
                int closeParen = findMatchingParen(i + 1, input);
                if (closeParen <= i + 1) {
                    return null;
                }
                expressions.add(new CompoundExpression(input.substring(i + 1, closeParen)));
                i = closeParen + 1;
            }

            if (i < input.length() && input.charAt(i) == 'E') {
                return null;
            }

            // Parse numeric literal
            while (i < input.length()) {
                char currentChar = input.charAt(i);
                if (!((currentChar >= '0' && currentChar <= '9') || currentChar == '.' || currentChar == 'E')) {
                    break;
                }
                if ((currentChar == '.' && temp.contains(".")) || (currentChar == 'E' && temp.contains("E"))) {
                    return null;
                }
                temp = temp + currentChar;
                if (currentChar == 'E') {
                    i++;
                    if (i < input.length() && (input.charAt(i) == '-' || input.charAt(i) == '+')) {
                        temp = temp + input.charAt(i);
                        i++;
                    }
                    if (i >= input.length() || input.charAt(i) < '0' || input.charAt(i) > '9') {
                        return null;
                    }
                } else {
                    i++;
                }
            }

            if (!temp.isEmpty()) {
                String type = (temp.contains(".") || temp.contains("E"))
                        ? ExpressionType.Decimal : ExpressionType.Integer;
                expressions.add(Expression.createExact(temp, type));
            }

            if (i >= input.length()) {
                break;
            }

            // Implicit multiplication: number followed by '('
            if (input.charAt(i) == '(') {
                int closeParen = findMatchingParen(i + 1, input);
                if (closeParen <= i + 1) {
                    return null;
                }
                expressions.add(Expression.createExact(
                        String.valueOf(OPERATORS[MULTIPLY_OPERATOR]),
                        OPERATOR_TYPE[MULTIPLY_OPERATOR]));
                expressions.add(new CompoundExpression(input.substring(i + 1, closeParen)));
                i = closeParen + 1;
                if (i >= input.length()) {
                    break;
                }
            }

            // Parse operator
            char operatorChar = input.charAt(i);
            int operatorIndex = findOperatorIndex(operatorChar);
            if (operatorIndex < 0) {
                System.out.println("Invalid character: '" + operatorChar + "'");
                return null;
            }
            if (temp.isEmpty() && (i <= 0 || input.charAt(i - 1) != ')')) {
                return null;
            }
            expressions.add(Expression.createExact(
                    String.valueOf(operatorChar), OPERATOR_TYPE[operatorIndex]));
            i++;
            if (i >= input.length()) {
                return null;
            }
        }
        return expressions;
    }

    private int findMatchingParen(int start, String input) {
        int depth = 0;
        for (int j = start; j < input.length(); j++) {
            if (input.charAt(j) == '(') {
                depth++;
            } else if (input.charAt(j) == ')') {
                if (depth == 0) {
                    return j;
                }
                depth--;
            }
        }
        return input.length();
    }

    private int findOperatorIndex(char character) {
        for (int i = 0; i < OPERATORS.length; i++) {
            if (character == OPERATORS[i]) {
                return i;
            }
        }
        return -1;
    }

    public ArrayList<Expression> getExpressions() {
        return expressions;
    }

    private Expression finalCompute() {
        if (expressions == null) {
            return null;
        }

        // First pass: evaluate power, divide, multiply (left to right)
        for (int op = POWER_OPERATOR; op <= MULTIPLY_OPERATOR; op++) {
            int index = findTypeIndex(OPERATOR_TYPE[op]);
            while (index >= 0) {
                Expression left = expressions.remove(index - 1);
                Expression right = expressions.remove(index);  // was index, now shifted
                expressions.remove(index - 1);  // remove the operator
                expressions.add(index - 1, evaluate(left, right, OPERATOR_TYPE[op]));
                index = findTypeIndex(OPERATOR_TYPE[op]);
            }
        }

        // Second pass: evaluate plus and minus (left to right, respecting precedence)
        int plusIndex = findTypeIndex(OPERATOR_TYPE[PLUS_OPERATOR]);
        int minusIndex = findTypeIndex(OPERATOR_TYPE[MINUS_OPERATOR]);
        int operatorType = chooseOperator(plusIndex, minusIndex);
        int index = (operatorType == PLUS_OPERATOR) ? plusIndex : minusIndex;

        while (index >= 0) {
            Expression left = expressions.remove(index - 1);
            Expression right = expressions.remove(index);
            expressions.remove(index - 1);
            expressions.add(index - 1, evaluate(left, right, OPERATOR_TYPE[operatorType]));

            plusIndex = findTypeIndex(OPERATOR_TYPE[PLUS_OPERATOR]);
            minusIndex = findTypeIndex(OPERATOR_TYPE[MINUS_OPERATOR]);
            operatorType = chooseOperator(plusIndex, minusIndex);
            index = (operatorType == PLUS_OPERATOR) ? plusIndex : minusIndex;
        }

        if (expressions.size() <= 1 && !expressions.isEmpty()) {
            return expressions.get(0);
        }
        return null;
    }

    private int chooseOperator(int plusIndex, int minusIndex) {
        if (plusIndex != -1 && (minusIndex == -1 || plusIndex <= minusIndex)) {
            return PLUS_OPERATOR;
        }
        return MINUS_OPERATOR;
    }

    private Expression evaluate(Expression left, Expression right, String operatorType) {
        switch (operatorType) {
            case ExpressionType.DivideOperator:
                left.setType(ExpressionType.Decimal);
                right.setType(ExpressionType.Decimal);
                return getExpression(
                        Double.parseDouble(left.getExact()) / Double.parseDouble(right.getExact()));

            case ExpressionType.MultiplyOperator:
                if (!Objects.equals(left.getType(), right.getType())
                        || Objects.equals(left.getType(), ExpressionType.Decimal)) {
                    left.setType(ExpressionType.Decimal);
                    right.setType(ExpressionType.Decimal);
                    return getExpression(
                            Double.parseDouble(left.getExact()) * Double.parseDouble(right.getExact()));
                }
                return Expression.createExact(
                        String.valueOf(Long.parseLong(left.getExact()) * Long.parseLong(right.getExact())),
                        ExpressionType.Integer);

            case ExpressionType.PowerOperator:
                left.setType(ExpressionType.Decimal);
                right.setType(ExpressionType.Decimal);
                return getExpression(
                        Math.pow(Double.parseDouble(left.getExact()), Double.parseDouble(right.getExact())));

            case ExpressionType.PlusOperator:
                if (!Objects.equals(left.getType(), right.getType())
                        || Objects.equals(left.getType(), ExpressionType.Decimal)) {
                    left.setType(ExpressionType.Decimal);
                    right.setType(ExpressionType.Decimal);
                    return getExpression(
                            Double.parseDouble(left.getExact()) + Double.parseDouble(right.getExact()));
                }
                return Expression.createExact(
                        String.valueOf(Long.parseLong(left.getExact()) + Long.parseLong(right.getExact())),
                        ExpressionType.Integer);

            case ExpressionType.MinusOperator:
                if (!Objects.equals(left.getType(), right.getType())
                        || Objects.equals(left.getType(), ExpressionType.Decimal)) {
                    left.setType(ExpressionType.Decimal);
                    right.setType(ExpressionType.Decimal);
                    return getExpression(
                            Double.parseDouble(left.getExact()) - Double.parseDouble(right.getExact()));
                }
                return Expression.createExact(
                        String.valueOf(Long.parseLong(left.getExact()) - Long.parseLong(right.getExact())),
                        ExpressionType.Integer);

            default:
                return null;
        }
    }

    private Expression getExpression(double doubleValue) {
        BigDecimal decimal = new BigDecimal(doubleValue).setScale(12, RoundingMode.HALF_UP);
        double result = decimal.doubleValue();
        if (result - Math.floor(result) == 0.0 || result - Math.ceil(result) == 0.0) {
            return Expression.createExact(String.valueOf((long) result), ExpressionType.Integer);
        }
        return Expression.createExact(String.valueOf(result), ExpressionType.Decimal);
    }

    private int findTypeIndex(String type) {
        for (int i = 0; i < expressions.size(); i++) {
            if (Objects.equals(expressions.get(i).getType(), type)) {
                return i;
            }
        }
        return -1;
    }

    public Expression compute() {
        if (expressions == null) {
            return null;
        }
        for (int i = 0; i < expressions.size(); i++) {
            Expression expression = expressions.get(i);
            if (expression == null) {
                return null;
            }
            if (expression instanceof CompoundExpression) {
                Expression computed = ((CompoundExpression) expression).compute();
                if (computed == null) {
                    return null;
                }
                expressions.set(i, computed);
            }
        }
        return finalCompute();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (Expression expression : expressions) {
            if (expression instanceof CompoundExpression) {
                builder.append(expression);
            } else {
                builder.append(expression.getExact());
            }
        }
        return builder.toString();
    }
}
