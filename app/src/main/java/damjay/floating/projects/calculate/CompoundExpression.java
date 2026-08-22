package damjay.floating.projects.calculate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Objects;

public class CompoundExpression extends Expression {
    public static final int DIVIDE_OPERATOR = 1;
    public static final int MINUS_OPERATOR = 4;
    public static final int MULTIPLY_OPERATOR = 2;
    public static final char[] OPERATORS = {'^', 247, 215, '+', '-'};
    public static final String[] OPERATOR_TYPE = {ExpressionType.PowerOperator, ExpressionType.DivideOperator, ExpressionType.MultiplyOperator, ExpressionType.PlusOperator, ExpressionType.MinusOperator};
    public static final int PLUS_OPERATOR = 3;
    public static final int POWER_OPERATOR = 0;
    private final ArrayList<Expression> expressions;

    public CompoundExpression(String input) {
        expressions = makeExpressions(input);
        setType(ExpressionType.CompoundExpression);
    }

    private ArrayList<Expression> makeExpressions(String input) {
        char currentChar;
        ArrayList<Expression> expressions = new ArrayList<>();
        int i = 0;
        while (i < input.length()) {
            String temp = "";
            char character = input.charAt(i);
            if (character == '+' || character == '-') {
                if (input.length() <= i + 1) {
                    return null;
                }
                char nextChar = input.charAt(i + 1);
                if ((nextChar < '0' || nextChar > '9') && nextChar != '.' && nextChar != 'E' && nextChar != '(') {
                    return null;
                }
                temp = "" + character;
                i++;
            }
            if (character == '(') {
                int otherPair = findOtherPair(i + 1, input);
                if (otherPair <= i + 1) {
                    return null;
                }
                expressions.add(new CompoundExpression(input.substring(i + 1, otherPair)));
                i = otherPair + 1;
            }
            if (i < input.length() && input.charAt(i) == 'E') {
                return null;
            }
            while (i < input.length() && (((currentChar = input.charAt(i)) >= '0' && currentChar <= '9') || currentChar == '.' || currentChar == 'E')) {
                if ((currentChar == '.' && temp.contains(".")) || (currentChar == 'E' && temp.contains("E"))) {
                    return null;
                }
                temp = temp + currentChar;
                if (currentChar != 'E' || (i = i + 1) >= input.length()) {
                    i++;
                } else {
                    if (input.charAt(i) == '-' || input.charAt(i) == '+') {
                        temp = temp + input.charAt(i);
                        i++;
                    }
                    if (i >= input.length() || input.charAt(i) < '0' || input.charAt(i) > '9') {
                        return null;
                    }
                }
            }
            if (!temp.isEmpty()) {
                expressions.add(Expression.createExact(temp, (temp.contains(".") || temp.contains("E")) ? ExpressionType.Decimal : ExpressionType.Integer));
            }
            if (i >= input.length()) {
                break;
            }
            if (input.charAt(i) == '(') {
                int otherPair2 = findOtherPair(i + 1, input);
                if (otherPair2 > i + 1) {
                    expressions.add(Expression.createExact(OPERATORS[2] + "", OPERATOR_TYPE[2]));
                    expressions.add(new CompoundExpression(input.substring(i + 1, otherPair2)));
                    i = otherPair2 + 1;
                    if (i >= input.length()) {
                        break;
                    }
                } else {
                    return null;
                }
            }
            char character2 = input.charAt(i);
            int index = getIndex(character2);
            if (index >= 0) {
                if (temp.isEmpty() && (i <= 0 || input.charAt(i - 1) != ')')) {
                    return null;
                }
                expressions.add(Expression.createExact(character2 + "", OPERATOR_TYPE[index]));
                i++;
                if (input.length() == i) {
                    return null;
                }
            } else {
                System.out.println("Invalid character: '" + character2 + "'");
                return null;
            }
        }
        return expressions;
    }

    private int findOtherPair(int start, String input) {
        int numUnpairedBrackets = 0;
        int otherPair = input.length();
        for (int j = start; j < input.length(); j++) {
            if (input.charAt(j) == '(') {
                numUnpairedBrackets++;
            } else if (input.charAt(j) != ')') {
                continue;
            } else if (numUnpairedBrackets > 0) {
                numUnpairedBrackets--;
            } else {
                int otherPair2 = j;
                return otherPair2;
            }
        }
        return otherPair;
    }

    private int getIndex(char character) {
        int i = 0;
        while (true) {
            char[] cArr = OPERATORS;
            if (i < cArr.length) {
                if (character == cArr[i]) {
                    return i;
                }
                i++;
            } else {
                return -1;
            }
        }
    }

    public ArrayList<Expression> getExpressions() {
        return expressions;
    }

    private Expression finalCompute() {
        if (expressions == null) {
            return null;
        }
        for (int i = 0; i < 3; i++) {
            int index = getIndex(OPERATOR_TYPE[i]);
            while (index >= 0) {
                Expression left = expressions.remove(index - 1);
                Expression right = expressions.remove(index);
                expressions.remove(index - 1);
                String[] strArr = OPERATOR_TYPE;
                expressions.add(index - 1, evaluate(left, right, strArr[i]));
                index = getIndex(strArr[i]);
            }
        }
        String[] strArr2 = OPERATOR_TYPE;
        int plusIndex = getIndex(strArr2[3]);
        int minusIndex = getIndex(strArr2[4]);
        int operatorType = (plusIndex != -1 && (minusIndex == -1 || plusIndex <= minusIndex)) ? 3 : 4;
        int index2 = operatorType == 3 ? plusIndex : minusIndex;
        while (index2 >= 0) {
            Expression left2 = expressions.remove(index2 - 1);
            Expression right2 = expressions.remove(index2);
            expressions.remove(index2 - 1);
            String[] strArr3 = OPERATOR_TYPE;
            expressions.add(index2 - 1, evaluate(left2, right2, strArr3[operatorType]));
            int plusIndex2 = getIndex(strArr3[3]);
            int minusIndex2 = getIndex(strArr3[4]);
            operatorType = (plusIndex2 != -1 && (minusIndex2 == -1 || plusIndex2 <= minusIndex2)) ? 3 : 4;
            index2 = operatorType == 3 ? plusIndex2 : minusIndex2;
        }
        if (expressions.size() <= 1 && !expressions.isEmpty()) {
            return expressions.get(0);
        }
        return null;
    }

    private Expression evaluate(Expression left, Expression right, String operatorType) {
        switch (operatorType) {
            case "DivideOperator":
                left.setType(ExpressionType.Decimal);
                right.setType(ExpressionType.Decimal);
                double divideLeftValue = Double.parseDouble(left.getExact());
                double divideRightValue = Double.parseDouble(right.getExact());
                return getExpression(divideLeftValue / divideRightValue);
            case "MultiplyOperator":
                if (!Objects.equals(left.getType(), right.getType()) || Objects.equals(left.getType(), ExpressionType.Decimal)) {
                    left.setType(ExpressionType.Decimal);
                    right.setType(ExpressionType.Decimal);
                    double leftValue = Double.parseDouble(left.getExact());
                    double rightValue = Double.parseDouble(right.getExact());
                    return getExpression(leftValue * rightValue);
                }
                long leftValue2 = Long.parseLong(left.getExact());
                long rightValue2 = Long.parseLong(right.getExact());
                return Expression.createExact(String.valueOf(leftValue2 * rightValue2), ExpressionType.Integer);
            case "PowerOperator":
                left.setType(ExpressionType.Decimal);
                right.setType(ExpressionType.Decimal);
                double powerLeftValue = Double.parseDouble(left.getExact());
                double powerRightValue = Double.parseDouble(right.getExact());
                return getExpression(Math.pow(powerLeftValue, powerRightValue));
            case "PlusOperator":
                if (!Objects.equals(left.getType(), right.getType()) || Objects.equals(left.getType(), ExpressionType.Decimal)) {
                    left.setType(ExpressionType.Decimal);
                    right.setType(ExpressionType.Decimal);
                    double leftValue3 = Double.parseDouble(left.getExact());
                    double rightValue3 = Double.parseDouble(right.getExact());
                    return getExpression(leftValue3 + rightValue3);
                }
                long leftValue4 = Long.parseLong(left.getExact());
                long rightValue4 = Long.parseLong(right.getExact());
                return Expression.createExact(String.valueOf(leftValue4 + rightValue4), ExpressionType.Integer);
            case "MinusOperator":
                if (!Objects.equals(left.getType(), right.getType()) || Objects.equals(left.getType(), ExpressionType.Decimal)) {
                    left.setType(ExpressionType.Decimal);
                    right.setType(ExpressionType.Decimal);
                    double leftValue5 = Double.parseDouble(left.getExact());
                    double rightValue5 = Double.parseDouble(right.getExact());
                    return getExpression(leftValue5 - rightValue5);
                }
                long leftValue6 = Long.parseLong(left.getExact());
                long rightValue6 = Long.parseLong(right.getExact());
                return Expression.createExact(String.valueOf(leftValue6 - rightValue6), ExpressionType.Integer);
            default:
                return null;
        }
    }

    private Expression getExpression(double doubleValue) {
        BigDecimal decimal = new BigDecimal(doubleValue).setScale(12, RoundingMode.HALF_UP);
        double result = decimal.doubleValue();
        if (result - Math.floor(result) == 0.0d || result - Math.ceil(result) == 0.0d) {
            return Expression.createExact(String.valueOf((long) result), ExpressionType.Integer);
        }
        return Expression.createExact(String.valueOf(result), ExpressionType.Decimal);
    }

    private int getIndex(String type) {
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
