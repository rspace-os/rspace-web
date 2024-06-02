package com.researchspace.testutils;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/** Utility class for evaluating SpringExpressionLanguage expressions in annotations. */
public class SpringELTestUtils {

  /**
   * EValuates a String of SprEL against a target object, returning result as a string
   *
   * @param rootObject
   * @param spel
   * @return
   */
  public static String evaluateSpel(Object rootObject, String spel) {
    return evaluateSpel(rootObject, spel, String.class);
  }

  /**
   * EValuates a SprEl expression, returning result as a <code>clazz</code>
   *
   * @param rootObject
   * @param spel
   * @param clazz
   * @return
   */
  public static <T> T evaluateSpel(Object rootObject, String spel, Class<T> clazz) {
    StandardEvaluationContext itemContext = new StandardEvaluationContext(rootObject);
    ExpressionParser parser = new SpelExpressionParser();
    // replace root object in expression.
    Expression exp = parser.parseExpression(spel);
    T evaluatedSpel = exp.getValue(itemContext, clazz);
    return evaluatedSpel;
  }
}
