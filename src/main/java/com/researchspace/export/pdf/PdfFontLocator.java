package com.researchspace.export.pdf;

import org.apache.commons.lang3.ArrayUtils;

/**
 * Returns a font-name for a given Unicode code point. This class is purely a look-up class. It
 * performs no checks that the font exists on the system (these checks are performed internally by
 * itext library)
 */
public class PdfFontLocator {

  static final String MATH_FONT = "noto sans math";
  static final String STANDARD_FONT = "noto sans";
  static final String FALLBACK_FONT = "unifont";

  private static final Character.UnicodeBlock[] mathBlocks =
      new Character.UnicodeBlock[] {
        Character.UnicodeBlock.MATHEMATICAL_ALPHANUMERIC_SYMBOLS,
        Character.UnicodeBlock.MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A,
        Character.UnicodeBlock.MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B,
        Character.UnicodeBlock.SUPPLEMENTAL_MATHEMATICAL_OPERATORS,
        Character.UnicodeBlock.MATHEMATICAL_OPERATORS
      };

  /*
   * from http://zuga.net/articles/unicode-all-characters-supported-by-the-font-noto-sans/
   */
  private static final Character.UnicodeBlock[] notoSans =
      new Character.UnicodeBlock[] {
        Character.UnicodeBlock.LATIN_1_SUPPLEMENT,
        Character.UnicodeBlock.LATIN_EXTENDED_A,
        Character.UnicodeBlock.LATIN_EXTENDED_B,
        Character.UnicodeBlock.IPA_EXTENSIONS,
        Character.UnicodeBlock.SPACING_MODIFIER_LETTERS,
        Character.UnicodeBlock.GREEK,
        Character.UnicodeBlock.COPTIC,
        Character.UnicodeBlock.CYRILLIC,
        Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY,
        Character.UnicodeBlock.CYRILLIC_EXTENDED_C,
        Character.UnicodeBlock.COMBINING_DIACRITICAL_MARKS_EXTENDED,
        Character.UnicodeBlock.PHONETIC_EXTENSIONS,
        Character.UnicodeBlock.PHONETIC_EXTENSIONS_SUPPLEMENT,
        Character.UnicodeBlock.COMBINING_DIACRITICAL_MARKS_SUPPLEMENT,
        Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL,
        Character.UnicodeBlock.GREEK_EXTENDED,
        Character.UnicodeBlock.GENERAL_PUNCTUATION,
        Character.UnicodeBlock.SUPERSCRIPTS_AND_SUBSCRIPTS,
        Character.UnicodeBlock.LATIN_EXTENDED_C,
        Character.UnicodeBlock.CYRILLIC_EXTENDED_A,
        Character.UnicodeBlock.SUPPLEMENTAL_PUNCTUATION,
        Character.UnicodeBlock.CURRENCY_SYMBOLS,
        Character.UnicodeBlock.CYRILLIC_EXTENDED_B,
        Character.UnicodeBlock.MODIFIER_TONE_LETTERS,
        Character.UnicodeBlock.LATIN_EXTENDED_D,
        Character.UnicodeBlock.LATIN_EXTENDED_E,
        Character.UnicodeBlock.ALPHABETIC_PRESENTATION_FORMS,
        Character.UnicodeBlock.COMBINING_HALF_MARKS,
        Character.UnicodeBlock.SPECIALS
      };

  /*
   * Given a code point gets a suitable font.
   * Note these fonts must be  installed on the system
   * For efficiency just call with codePoints > 0xFF as PDF library
   * handles all chars <= this value.
   */
  String getReplacementFont(int codePointAt) {
    if (codePointAt <= 0x7F) { // ascii
      return "";
    }
    if (isMaths(codePointAt)) {
      return MATH_FONT; // maths and operator
    } else if (isNotoSans(codePointAt)) {
      return STANDARD_FONT; // most european languages / symbols
    } else {
      return FALLBACK_FONT; // bit-mapped fallback with all unicode chars
    }
  }

  private boolean isNotoSans(int codePointAt) {
    return fontBlockContains(codePointAt, notoSans);
  }

  private boolean fontBlockContains(int codePointAt, Character.UnicodeBlock[] unicodeBlock) {
    Character.UnicodeBlock charBlock = Character.UnicodeBlock.of(codePointAt);
    return ArrayUtils.contains(unicodeBlock, charBlock);
  }

  private boolean isMaths(int codePointAt) {
    return fontBlockContains(codePointAt, mathBlocks);
  }
}
