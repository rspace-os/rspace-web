/*
 * Ported to Java from hypertino/inflector 1.0.14 (commit
 * 89d1bcd2ef81dc8e7c208bd217900bb87311b501), files EnglishInflector.scala and
 * TwoFormInflector.scala: https://github.com/hypertino/inflector
 *
 * Copyright (c) 2016-2017 Magomed Abdurakhmanov
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The upstream files are themselves modified from https://github.com/atteo/evo-inflector:
 *
 * Copyright 2016 Magomed Abdurakhmanov
 *
 * Modified from original sources located at: https://github.com/atteo/evo-inflector
 *
 * Copyright 2011 Atteo.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Changes from upstream: ported from Scala to Java; only the anglicized English inflector is kept
 * (no classical variant and its different-first-letter irregulars, naming-convention converters
 * or Scala.js code); suffix matching lowercases with Locale.ROOT; the "(ar)ves" singular
 * replacement no longer ends in a stray "$", which made singular("scarves") throw.
 */
package com.researchspace.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts English nouns between singular and plural forms using the anglicized rules of Damian
 * Conway's "An Algorithmic Approach to English Pluralization". Rules match the end of the word, so
 * compound words such as {@code booking-configurations} only change their last segment, and the
 * case of the unchanged prefix is preserved.
 */
public final class EnglishInflector {

  private static final List<String> CATEGORY_EX_ICES = List.of("codex", "murex", "silex");
  private static final List<String> CATEGORY_IX_ICES = List.of("radix", "helix");
  private static final List<String> CATEGORY_UM_A =
      List.of(
          "bacterium",
          "agendum",
          "desideratum",
          "erratum",
          "stratum",
          "datum",
          "ovum",
          "extremum",
          "candelabrum");
  private static final List<String> CATEGORY_US_I =
      List.of(
          "alumnus",
          "alveolus",
          "bacillus",
          "bronchus",
          "locus",
          "nucleus",
          "stimulus",
          "meniscus",
          "thesaurus");
  private static final List<String> CATEGORY_ON_A =
      List.of(
          "criterion",
          "perihelion",
          "aphelion",
          "phenomenon",
          "prolegomenon",
          "noumenon",
          "organon",
          "asyndeton",
          "hyperbaton");
  private static final List<String> CATEGORY_A_AE =
      List.of("alumna", "alga", "vertebra", "persona");
  private static final List<String> CATEGORY_O_OS =
      List.of(
          "albino",
          "archipelago",
          "armadillo",
          "commando",
          "crescendo",
          "fiasco",
          "ditto",
          "dynamo",
          "embryo",
          "ghetto",
          "guano",
          "inferno",
          "jumbo",
          "lumbago",
          "magneto",
          "manifesto",
          "medico",
          "octavo",
          "photo",
          "pro",
          "quarto",
          "canto",
          "lingo",
          "generalissimo",
          "stylo",
          "rhino",
          "casino",
          "auto",
          "macro",
          "zero",
          "todo");
  private static final List<String> CATEGORY_O_I =
      List.of("solo", "soprano", "basso", "alto", "contralto", "tempo", "piano", "virtuoso");
  private static final List<String> CATEGORY_A_ATA =
      List.of(
          "anathema",
          "enema",
          "oedema",
          "bema",
          "enigma",
          "sarcoma",
          "carcinoma",
          "gumma",
          "schema",
          "charisma",
          "lemma",
          "soma",
          "diploma",
          "lymphoma",
          "stigma",
          "dogma",
          "magma",
          "stoma",
          "drama",
          "melisma",
          "trauma",
          "edema",
          "miasma");
  private static final List<String> CATEGORY_IS_IDES = List.of("iris", "clitoris");
  private static final List<String> CATEGORY_US_US =
      List.of(
          "apparatus",
          "impetus",
          "prospectus",
          "cantus",
          "nexus",
          "sinus",
          "coitus",
          "plexus",
          "status",
          "hiatus");
  private static final List<String> CATEGORY_S_ES =
      List.of(
          "acropolis",
          "chaos",
          "lens",
          "aegis",
          "cosmos",
          "mantis",
          "alias",
          "dais",
          "marquis",
          "asbestos",
          "digitalis",
          "metropolis",
          "atlas",
          "epidermis",
          "pathos",
          "bathos",
          "ethos",
          "pelvis",
          "bias",
          "gas",
          "polis",
          "caddis",
          "glottis",
          "rhinoceros",
          "cannabis",
          "glottis",
          "sassafras",
          "canvas",
          "ibis",
          "trellis");
  private static final List<String> CATEGORY_MAN_MANS =
      List.of(
          "human",
          "Alabaman",
          "Bahaman",
          "Burman",
          "German",
          "Hiroshiman",
          "Liman",
          "Nakayaman",
          "Oklahoman",
          "Panaman",
          "Selman",
          "Sonaman",
          "Tacoman",
          "Yakiman",
          "Yokohaman",
          "Yuman");

  private static final List<Rule> RULES = rules();

  private EnglishInflector() {}

  /**
   * Returns the plural form of an English noun, or the word unchanged when it is uncountable.
   *
   * @param word a singular noun
   * @return the plural noun
   */
  public static String plural(String word) {
    Objects.requireNonNull(word, "word");
    for (Rule rule : RULES) {
      Optional<String> result = rule.plural(word);
      if (result.isPresent()) {
        return result.get();
      }
    }
    return word;
  }

  /**
   * Returns {@code word} when {@code count} is 1, otherwise its plural form.
   *
   * @param word a singular noun
   * @param count how many of the noun are meant
   * @return the noun inflected for {@code count}
   */
  public static String plural(String word, int count) {
    return count == 1 ? Objects.requireNonNull(word, "word") : plural(word);
  }

  /**
   * Returns the singular form of an English noun, or the word unchanged when no rule applies.
   *
   * @param word a plural noun
   * @return the singular noun
   */
  public static String singular(String word) {
    Objects.requireNonNull(word, "word");
    for (Rule rule : RULES) {
      Optional<String> result = rule.singular(word);
      if (result.isPresent()) {
        return result.get();
      }
    }
    return word;
  }

  private static List<Rule> rules() {
    List<Rule> rules = new ArrayList<>();
    rules.add(
        new CategoryRule(
            List.of(
                "fish",
                "ois",
                "sheep",
                "deer",
                "pox",
                "itis",
                "bison",
                "flounder",
                "pliers",
                "bream",
                "gallows",
                "proceedings",
                "breeches",
                "graffiti",
                "rabies",
                "britches",
                "headquarters",
                "salmon",
                "carp",
                "herpes",
                "scissors",
                "chassis",
                "high-jinks",
                "sea-bass",
                "clippers",
                "homework",
                "series",
                "cod",
                "innings",
                "shears",
                "contretemps",
                "jackanapes",
                "species",
                "corps",
                "mackerel",
                "swine",
                "debris",
                "measles",
                "trout",
                "diabetes",
                "mews",
                "tuna",
                "djinn",
                "mumps",
                "whiting",
                "eland",
                "news",
                "wildebeest",
                "elk",
                "pincers",
                "sugar",
                "each"),
            "",
            ""));
    rules.add(irregular("child", "children"));
    rules.add(irregular("ephemeris", "ephemerides"));
    rules.add(irregular("mongoose", "mongoose"));
    rules.add(irregular("mythos", "mythoi"));
    rules.add(irregular("soliloquy", "soliloquies"));
    rules.add(irregular("trilby", "trilbys"));
    rules.add(irregular("genus", "genera"));
    rules.add(irregular("quiz", "quizzes"));
    rules.add(irregular("basis", "bases"));
    rules.add(irregular("slice", "slices"));
    rules.add(irregular("cookie", "cookies"));
    rules.add(irregular("response", "responses"));
    rules.add(irregular("case", "cases"));
    rules.add(irregular("license", "licenses"));
    rules.add(irregular("licence", "licences"));
    rules.add(irregular("beef", "beefs"));
    rules.add(irregular("brother", "brothers"));
    rules.add(irregular("cow", "cows"));
    rules.add(irregular("genie", "genies"));
    rules.add(irregular("money", "moneys"));
    rules.add(irregular("octopus", "octopuses"));
    rules.add(irregular("opus", "opuses"));
    rules.add(new CategoryRule(CATEGORY_MAN_MANS, "", "s"));
    rules.add(rule("man$", "man", "men$", "men"));
    rules.add(rule("([lm])ouse$", "$1ouse", "([lm])ice", "$1ice"));
    rules.add(rule("tooth$", "tooth", "teeth$", "teeth"));
    rules.add(rule("goose$", "goose", "geese$", "geese"));
    rules.add(rule("foot$", "foot", "feet$", "feet"));
    rules.add(rule("zoon$", "zoon", "zoa$", "zoa"));
    rules.add(pluralOnly("([csx])is$", "$1es"));
    rules.add(new CategoryRule(CATEGORY_EX_ICES, "ex", "ices"));
    rules.add(new CategoryRule(CATEGORY_IX_ICES, "ix", "ices"));
    rules.add(new CategoryRule(CATEGORY_UM_A, "um", "a"));
    rules.add(new CategoryRule(CATEGORY_ON_A, "on", "a"));
    rules.add(new CategoryRule(CATEGORY_A_AE, "a", "ae"));
    rules.add(new CategoryRule(CATEGORY_US_I, "us", "i"));
    rules.add(rule("([cs]h)$", "$1", "([cs]h)es$", "$1es"));
    rules.add(rule("([zx])$", "$1", "([zx])es$", "$1es"));
    rules.add(new CategoryRule(CATEGORY_S_ES, "", "es"));
    rules.add(new CategoryRule(CATEGORY_IS_IDES, "", "es"));
    rules.add(new CategoryRule(CATEGORY_US_US, "", "es"));
    rules.add(rule("(us)$", "$1x", "xes($)", "$1es"));
    rules.add(new CategoryRule(CATEGORY_A_ATA, "", "s"));
    rules.add(pluralOnly("([cs])h$", "$1hes"));
    rules.add(rule("ss$", "ss", "sses$", "sses"));
    rules.add(rule("([aeo]l)f$", "$1f", "([aeo]l)ves$", "$1ves"));
    rules.add(rule("([^d]ea)f$", "$1f", "([^d]ea)ves$", "$1ves"));
    rules.add(rule("(ar)f$", "$1f", "(ar)ves$", "$1ves"));
    rules.add(rule("([nlw]i)fe$", "$1fe", "([nlw]i)ves$", "$1ves"));
    rules.add(rule("([aeiou])y$", "$1y", "([aeiou])ys$", "$1ys"));
    rules.add(rule("y$", "y", "ies$", "ies"));
    rules.add(new CategoryRule(CATEGORY_O_I, "o", "os"));
    rules.add(new CategoryRule(CATEGORY_O_OS, "o", "os"));
    rules.add(rule("([aeiou])o$", "$1o", "([aeiou])os$", "$1os"));
    rules.add(rule("o$", "o", "oes$", "oes"));
    rules.add(rule("ulum", "ulum", "ula", "ula"));
    rules.add(new CategoryRule(CATEGORY_A_ATA, "", "es"));
    rules.add(rule("s$", "s", "ses$", "ses"));
    rules.add(rule("$", "", "s$", "s"));
    return List.copyOf(rules);
  }

  /** The singular and plural must share their first letter, whose case is preserved. */
  private static Rule irregular(String singular, String plural) {
    return new RegExpRule(
        Pattern.compile("(?i)(" + singular.charAt(0) + ")" + singular.substring(1) + "$"),
        "$1" + singular.substring(1),
        Pattern.compile("(?i)(" + plural.charAt(0) + ")" + plural.substring(1) + "$"),
        "$1" + plural.substring(1));
  }

  private static Rule rule(
      String singularPattern, String singular, String pluralPattern, String plural) {
    return new RegExpRule(
        Pattern.compile("(?i)" + singularPattern),
        singular,
        Pattern.compile("(?i)" + pluralPattern),
        plural);
  }

  private static Rule pluralOnly(String singularPattern, String plural) {
    return new RegExpRule(Pattern.compile("(?i)" + singularPattern), null, null, plural);
  }

  private sealed interface Rule permits RegExpRule, CategoryRule {
    Optional<String> plural(String word);

    Optional<String> singular(String word);
  }

  /** A suffix rule; a rule without {@code pluralRegex} only pluralizes. */
  private record RegExpRule(
      Pattern singularRegex, String singular, Pattern pluralRegex, String plural) implements Rule {

    @Override
    public Optional<String> plural(String word) {
      Matcher matcher = singularRegex.matcher(word);
      return matcher.find() ? Optional.of(matcher.replaceFirst(plural)) : Optional.empty();
    }

    @Override
    public Optional<String> singular(String word) {
      if (pluralRegex == null) {
        return Optional.empty();
      }
      Matcher matcher = pluralRegex.matcher(word);
      return matcher.find() ? Optional.of(matcher.replaceFirst(singular)) : Optional.empty();
    }
  }

  /** Words ending in one of {@code singularList} swap the {@code singular} suffix for plural. */
  private record CategoryRule(
      List<String> singularList, String singular, String plural, List<String> pluralList)
      implements Rule {

    CategoryRule(List<String> singularList, String singular, String plural) {
      this(
          singularList,
          singular,
          plural,
          singularList.stream()
              .map(s -> s.substring(0, s.length() - singular.length()) + plural)
              .toList());
    }

    @Override
    public Optional<String> plural(String word) {
      return swapSuffix(word, singularList, singular, plural);
    }

    @Override
    public Optional<String> singular(String word) {
      return swapSuffix(word, pluralList, plural, singular);
    }

    private static Optional<String> swapSuffix(
        String word, List<String> endings, String from, String to) {
      String lowerWord = word.toLowerCase(Locale.ROOT);
      if (endings.stream().noneMatch(lowerWord::endsWith)) {
        return Optional.empty();
      }
      if (!lowerWord.endsWith(from)) {
        throw new IllegalStateException(
            "Internal error: " + lowerWord + " doesn't end with " + from);
      }
      return Optional.of(word.substring(0, word.length() - from.length()) + to);
    }
  }
}
