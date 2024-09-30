//@flow strict

/**
 * This function takes a string and removes any diacritics (accents). This is
 * useful where we want to compare people's names with a query string inputted
 * using anglo-centric keyboard, and any other use cases should be carefully
 * considered to make sure that the resulting string is used correctly as it
 * will no longer match the original.

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
