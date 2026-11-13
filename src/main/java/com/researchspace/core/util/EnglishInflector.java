/*
 * Ported to Java from pluralize 8.0.0 (pluralize.js): https://github.com/blakeembrey/pluralize
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Blake Embrey (hello@blakeembrey.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Changes from upstream:
 * - The rule tables are fixed: addPluralRule, addSingularRule, addUncountableRule and
 *   addIrregularRule are not ported, nor is the inclusive count prefix of pluralize(word, count,
 *   inclusive).
 * - JavaScript regular expressions are translated to java.util.regex with the same matches: "$"
 *   becomes "\z" (Java's "$" also matches before a final line terminator), "\b" becomes a lookbehind
 *   for a non-[A-Za-z0-9_] character, and "pok[eé]mon" lists "É" because CASE_INSENSITIVE folds only
 *   ASCII, which is otherwise what JavaScript's non-Unicode "i" flag does for these rules.
 * - A "$n" replacement reference to a group the rule does not have expands to "" (upstream would
 *   read the replace callback's offset or input arguments; no rule does this).
 * - A null word throws NullPointerException.
 */
package com.researchspace.core.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts English nouns between singular and plural forms with the rules of the npm package
 * pluralize 8.0.0. Rules match the end of the word, so compound words such as {@code
 * booking-configurations} only change their last segment, and the case of the input is restored on
 * the replaced part.
 */
public final class EnglishInflector {

  private static final Pattern REFERENCE = Pattern.compile("\\$(\\d{1,2})");

  private static final Map<String, String> IRREGULAR_SINGLES;
  private static final Map<String, String> IRREGULAR_PLURALS;

  static {
    String[][] irregulars = {
      // Pronouns.
      {"I", "we"},
      {"me", "us"},
      {"he", "they"},
      {"she", "they"},
      {"them", "them"},
      {"myself", "ourselves"},
      {"yourself", "yourselves"},
      {"itself", "themselves"},
      {"herself", "themselves"},
      {"himself", "themselves"},
      {"themself", "themselves"},
      {"is", "are"},
      {"was", "were"},
      {"has", "have"},
      {"this", "these"},
      {"that", "those"},
      // Words ending in with a consonant and `o`.
      {"echo", "echoes"},
      {"dingo", "dingoes"},
      {"volcano", "volcanoes"},
      {"tornado", "tornadoes"},
      {"torpedo", "torpedoes"},
      // Ends with `us`.
      {"genus", "genera"},
      {"viscus", "viscera"},
      // Ends with `ma`.
      {"stigma", "stigmata"},
      {"stoma", "stomata"},
      {"dogma", "dogmata"},
      {"lemma", "lemmata"},
      {"schema", "schemata"},
      {"anathema", "anathemata"},
      // Other irregular rules.
      {"ox", "oxen"},
      {"axe", "axes"},
      {"die", "dice"},
      {"yes", "yeses"},
      {"foot", "feet"},
      {"eave", "eaves"},
      {"goose", "geese"},
      {"tooth", "teeth"},
      {"quiz", "quizzes"},
      {"human", "humans"},
      {"proof", "proofs"},
      {"carve", "carves"},
      {"valve", "valves"},
      {"looey", "looies"},
      {"thief", "thieves"},
      {"groove", "grooves"},
      {"pickaxe", "pickaxes"},
      {"passerby", "passersby"}
    };
    // Later pairs overwrite earlier ones, as upstream's object assignments do.
    Map<String, String> singles = new HashMap<>();
    Map<String, String> plurals = new HashMap<>();
    for (String[] pair : irregulars) {
      String single = lower(pair[0]);
      String plural = lower(pair[1]);
      singles.put(single, plural);
      plurals.put(plural, single);
    }
    IRREGULAR_SINGLES = Map.copyOf(singles);
    IRREGULAR_PLURALS = Map.copyOf(plurals);
  }

  private static final Set<String> UNCOUNTABLES =
      Set.of(
          "adulthood",
          "advice",
          "agenda",
          "aid",
          "aircraft",
          "alcohol",
          "ammo",
          "analytics",
          "anime",
          "athletics",
          "audio",
          "bison",
          "blood",
          "bream",
          "buffalo",
          "butter",
          "carp",
          "cash",
          "chassis",
          "chess",
          "clothing",
          "cod",
          "commerce",
          "cooperation",
          "corps",
          "debris",
          "diabetes",
          "digestion",
          "elk",
          "energy",
          "equipment",
          "excretion",
          "expertise",
          "firmware",
          "flounder",
          "fun",
          "gallows",
          "garbage",
          "graffiti",
          "hardware",
          "headquarters",
          "health",
          "herpes",
          "highjinks",
          "homework",
          "housework",
          "information",
          "jeans",
          "justice",
          "kudos",
          "labour",
          "literature",
          "machinery",
          "mackerel",
          "mail",
          "media",
          "mews",
          "moose",
          "music",
          "mud",
          "manga",
          "news",
          "only",
          "personnel",
          "pike",
          "plankton",
          "pliers",
          "police",
          "pollution",
          "premises",
          "rain",
          "research",
          "rice",
          "salmon",
          "scissors",
          "series",
          "sewage",
          "shambles",
          "shrimp",
          "software",
          "species",
          "staff",
          "swine",
          "tennis",
          "traffic",
          "transportation",
          "trout",
          "tuna",
          "wealth",
          "welfare",
          "whiting",
          "wildebeest",
          "wildlife",
          "you");

  /** Uncountable patterns; upstream adds each as both a plural and a singular rule. */
  private static final List<Rule> UNCOUNTABLE_RULES =
      List.of(
          rule("pok[eéÉ]mon$", "$0"),
          rule("[^aeiou]ese$", "$0"), // "chinese", "japanese"
          rule("deer$", "$0"), // "deer", "reindeer"
          rule("fish$", "$0"), // "fish", "blowfish", "angelfish"
          rule("measles$", "$0"),
          rule("o[iu]s$", "$0"), // "carnivorous"
          rule("pox$", "$0"), // "chickpox", "smallpox"
          rule("sheep$", "$0"));

  /** Plural rules in upstream order; the last matching rule wins. */
  private static final List<Rule> PLURAL_RULES =
      withUncountables(
          rule("s?$", "s"),
          rule("[^\\u0000-\\u007F]$", "$0"),
          rule("([^aeiou]ese)$", "$1"),
          rule("(ax|test)is$", "$1es"),
          rule("(alias|[^aou]us|t[lm]as|gas|ris)$", "$1es"),
          rule("(e[mn]u)s?$", "$1s"),
          rule("([^l]ias|[aeiou]las|[ejzr]as|[iu]am)$", "$1"),
          rule(
              "(alumn|syllab|vir|radi|nucle|fung|cact|stimul|termin|bacill|foc|uter|loc|strat)"
                  + "(?:us|i)$",
              "$1i"),
          rule("(alumn|alg|vertebr)(?:a|ae)$", "$1ae"),
          rule("(seraph|cherub)(?:im)?$", "$1im"),
          rule("(her|at|gr)o$", "$1oes"),
          rule(
              "(agend|addend|millenni|dat|extrem|bacteri|desiderat|strat|candelabr|errat|ov|symposi"
                  + "|curricul|automat|quor)(?:a|um)$",
              "$1a"),
          rule(
              "(apheli|hyperbat|periheli|asyndet|noumen|phenomen|criteri|organ|prolegomen|hedr"
                  + "|automat)(?:a|on)$",
              "$1a"),
          rule("sis$", "ses"),
          rule("(?:(kni|wi|li)fe|(ar|l|ea|eo|oa|hoo)f)$", "$1$2ves"),
          rule("([^aeiouy]|qu)y$", "$1ies"),
          rule("([^ch][ieo][ln])ey$", "$1ies"),
          rule("(x|ch|ss|sh|zz)$", "$1es"),
          rule("(matr|cod|mur|sil|vert|ind|append)(?:ix|ex)$", "$1ices"),
          rule("\\b((?:tit)?m|l)(?:ice|ouse)$", "$1ice"),
          rule("(pe)(?:rson|ople)$", "$1ople"),
          rule("(child)(?:ren)?$", "$1ren"),
          rule("eaux$", "$0"),
          rule("m[ae]n$", "men"),
          rule("^thou$", "you"));

  /** Singular rules in upstream order; the last matching rule wins. */
  private static final List<Rule> SINGULAR_RULES =
      withUncountables(
          rule("s$", ""),
          rule("(ss)$", "$1"),
          rule("(wi|kni|(?:after|half|high|low|mid|non|night|[^\\w]|^)li)ves$", "$1fe"),
          rule("(ar|(?:wo|[ae])l|[eo][ao])ves$", "$1f"),
          rule("ies$", "y"),
          rule(
              "\\b([pl]|zomb|(?:neck|cross)?t|coll|faer|food|gen|goon|group|lass|talk|goal|cut)ies$",
              "$1ie"),
          rule("\\b(mon|smil)ies$", "$1ey"),
          rule("\\b((?:tit)?m|l)ice$", "$1ouse"),
          rule("(seraph|cherub)im$", "$1"),
          rule(
              "(x|ch|ss|sh|zz|tto|go|cho|alias|[^aou]us|t[lm]as|gas|(?:her|at|gr)o|[aeiou]ris)"
                  + "(?:es)?$",
              "$1"),
          rule("(analy|diagno|parenthe|progno|synop|the|empha|cri|ne)(?:sis|ses)$", "$1sis"),
          rule("(movie|twelve|abuse|e[mn]u)s$", "$1"),
          rule("(test)(?:is|es)$", "$1is"),
          rule(
              "(alumn|syllab|vir|radi|nucle|fung|cact|stimul|termin|bacill|foc|uter|loc|strat)"
                  + "(?:us|i)$",
              "$1us"),
          rule(
              "(agend|addend|millenni|dat|extrem|bacteri|desiderat|strat|candelabr|errat|ov|symposi"
                  + "|curricul|quor)a$",
              "$1um"),
          rule(
              "(apheli|hyperbat|periheli|asyndet|noumen|phenomen|criteri|organ|prolegomen|hedr"
                  + "|automat)a$",
              "$1on"),
          rule("(alumn|alg|vertebr)ae$", "$1a"),
          rule("(cod|mur|sil|vert|ind)ices$", "$1ex"),
          rule("(matr|append)ices$", "$1ix"),
          rule("(pe)(rson|ople)$", "$1rson"),
          rule("(child)ren$", "$1"),
          rule("(eau)x?$", "$1"),
          rule("men$", "man"));

  private EnglishInflector() {}

  /**
   * Returns the plural form of an English noun, or the word unchanged when it is uncountable.
   *
   * @param word a singular noun
   * @return the plural noun
   */
  public static String plural(String word) {
    return replaceWord(word, IRREGULAR_SINGLES, IRREGULAR_PLURALS, PLURAL_RULES);
  }

  /**
   * Returns the singular form of {@code word} when {@code count} is 1, otherwise its plural form.
   *
   * @param word a noun in either form
   * @param count how many of the noun are meant
   * @return the noun inflected for {@code count}
   */
  public static String plural(String word, int count) {
    return count == 1 ? singular(word) : plural(word);
  }

  /**
   * Returns the singular form of an English noun, or the word unchanged when no rule applies.
   *
   * @param word a plural noun
   * @return the singular noun
   */
  public static String singular(String word) {
    return replaceWord(word, IRREGULAR_PLURALS, IRREGULAR_SINGLES, SINGULAR_RULES);
  }

  /**
   * Returns whether {@code word} is already plural, that is, pluralizing it changes nothing.
   *
   * @param word a noun
   * @return whether the noun is plural
   */
  public static boolean isPlural(String word) {
    return checkWord(word, IRREGULAR_SINGLES, IRREGULAR_PLURALS, PLURAL_RULES);
  }

  /**
   * Returns whether {@code word} is already singular, that is, singularizing it changes nothing.
   *
   * @param word a noun
   * @return whether the noun is singular
   */
  public static boolean isSingular(String word) {
    return checkWord(word, IRREGULAR_PLURALS, IRREGULAR_SINGLES, SINGULAR_RULES);
  }

  private static String replaceWord(
      String word, Map<String, String> replaceMap, Map<String, String> keepMap, List<Rule> rules) {
    String token = lower(Objects.requireNonNull(word, "word"));
    if (keepMap.containsKey(token)) {
      return restoreCase(word, token);
    }
    String replacement = replaceMap.get(token);
    if (replacement != null) {
      return restoreCase(word, replacement);
    }
    return sanitizeWord(token, word, rules);
  }

  private static boolean checkWord(
      String word, Map<String, String> replaceMap, Map<String, String> keepMap, List<Rule> rules) {
    String token = lower(Objects.requireNonNull(word, "word"));
    if (keepMap.containsKey(token)) {
      return true;
    }
    if (replaceMap.containsKey(token)) {
      return false;
    }
    return sanitizeWord(token, token, rules).equals(token);
  }

  private static String sanitizeWord(String token, String word, List<Rule> rules) {
    if (token.isEmpty() || UNCOUNTABLES.contains(token)) {
      return word;
    }
    for (int i = rules.size() - 1; i >= 0; i--) {
      Matcher matcher = rules.get(i).pattern().matcher(word);
      if (matcher.find()) {
        return replace(word, matcher, rules.get(i).replacement());
      }
    }
    return word;
  }

  /** Replaces the first match, restoring the case of the matched text (or the preceding char). */
  private static String replace(String word, Matcher matcher, String replacement) {
    String match = matcher.group();
    String result = interpolate(replacement, matcher);
    String cased =
        match.isEmpty()
            ? restoreCase(String.valueOf(word.charAt(matcher.start() - 1)), result)
            : restoreCase(match, result);
    return word.substring(0, matcher.start()) + cased + word.substring(matcher.end());
  }

  private static String interpolate(String replacement, Matcher match) {
    Matcher reference = REFERENCE.matcher(replacement);
    StringBuilder result = new StringBuilder();
    while (reference.find()) {
      int group = Integer.parseInt(reference.group(1));
      String value = group <= match.groupCount() ? match.group(group) : null;
      reference.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
    }
    reference.appendTail(result);
    return result.toString();
  }

  private static String restoreCase(String word, String token) {
    if (word.equals(token)) {
      return token;
    }
    if (word.equals(lower(word))) {
      return lower(token);
    }
    if (word.equals(upper(word))) {
      return upper(token);
    }
    String first = word.substring(0, 1);
    if (first.equals(upper(first))) {
      return token.isEmpty() ? token : upper(token.substring(0, 1)) + lower(token.substring(1));
    }
    return lower(token);
  }

  private static String lower(String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  private static String upper(String value) {
    return value.toUpperCase(Locale.ROOT);
  }

  private static List<Rule> withUncountables(Rule... rules) {
    List<Rule> result = new ArrayList<>(List.of(rules));
    result.addAll(UNCOUNTABLE_RULES);
    return List.copyOf(result);
  }

  /** Compiles a pluralize.js rule; see the file header for the regular expression translation. */
  private static Rule rule(String javascript, String replacement) {
    if (!javascript.endsWith("$")) {
      throw new IllegalArgumentException("Rules match the end of the word: " + javascript);
    }
    String java =
        javascript.substring(0, javascript.length() - 1).replace("\\b", "(?<![A-Za-z0-9_])")
            + "\\z";
    return new Rule(Pattern.compile(java, Pattern.CASE_INSENSITIVE), replacement);
  }

  private record Rule(Pattern pattern, String replacement) {}
}
