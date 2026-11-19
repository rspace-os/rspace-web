/**
 * This function takes a string and removes any diacritics (accents). This is
 * useful where we want to compare people's names with a query string inputted
 * using anglo-centric keyboard, and any other use cases should be carefully
 * considered to make sure that the resulting string is used correctly as it
 * will no longer match the original.
 *
 * It does this by performing a Unicode decomposition to separate any code
 * points that are latin characters and a diacritic into two code points, the
 * character followed by the diacritic, and removing all of the code points
 * within the Comblining Diacritical Marks Unicode block, leaving just the
 * latin characters.
 *
 * Note that the resulting string may have a greater length than the original
 * as the Unicode decomposition will convert ligatures into a sequence of latin
 * characters.
 */
export function stripDiacritics(str: string): string {
  return str.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
}

/*
 * Java's whitespace (`Character.isWhitespace`): the Unicode space, line and
 * paragraph separators other than the no-break spaces, plus U+0009 to U+000D
 * and U+001C to U+001F. It differs from what `String.prototype.trim` strips.
 */
// biome-ignore lint/suspicious/noControlCharactersInRegex: Java counts these control characters as whitespace
const JAVA_BLANK = /^[\t\n\u000B\f\r\u001C-\u001F \u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]*$/;

/** Whether the server reads the string as blank, as Apache Commons `StringUtils.isBlank` does. */
export function isBlankToServer(str: string): boolean {
  return JAVA_BLANK.test(str);
}
