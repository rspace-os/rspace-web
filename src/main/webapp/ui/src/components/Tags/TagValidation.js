//@flow

/* ================
 * Validating a Tag
 *
 * The user should be prevented from selecting a given tag for various
 * reasons, whether they be choosing a tag from the options provided by the
 * API or typing in a new one of their own. This module encapsulates this
 * logic in one place to ensure that these rules are always correctly enforced.
 * ================
 */

import { lift2 } from "../../util/optional";
import { type Tag } from "../../stores/definitions/Tag";

/**
 * The max length of an individual tag. Chosen rather arbitrarily based on the
 * typically length of tags from the ontology file used for testing.
 */
export const MAX_LENGTH = 200;

const MIN_LENGTH = 2;

opaque type TagValidation =
  | {| reason: "NoIssues" |}
  | {| reason: "OntologiesAreEnforced" |}
  | {|
      reason: "NoIssuesWithSourceInformation",
      version: string,
      filename: string,
    |}
  | {| reason: "AlreadySelected" |}
  | {| reason: "InvalidChar", char: string |}
  | {|
      reason: "InvalidWhitespace",
      detail: "Prefix" | "Suffix" | "Consecutive",
    |}
  | {|
      reason: "TooLong",
    |}
  | {|
      reason: "TooShort",
    |};

const forbiddenCharacters = new Set("<>\\".split(""));

/**
 * Given a Tag, which is to say a simple string, there are some validations
 * that can be performed on it alone.
 */
export const checkTagString = (tag: string): TagValidation => {
  if (tag.length > MAX_LENGTH) return { reason: "TooLong" };
  if (tag.length < MIN_LENGTH) return { reason: "TooShort" };
  for (const char of tag.split("")) {
    if (forbiddenCharacters.has(char))
      return {
        reason: "InvalidChar",
        char,
      };
  }
  if (tag.startsWith(" "))
    return { reason: "InvalidWhitespace", detail: "Prefix" };
  if (tag.endsWith(" "))
    return { reason: "InvalidWhitespace", detail: "Suffix" };
  if (/ {2}/.test(tag))
    return { reason: "InvalidWhitespace", detail: "Consecutive" };
  return { reason: "NoIssues" };
};

/**
 * In addition to the restrictions above, a tag entered by the user also has
 * some additional checks
 */
export const checkUserInputString = (tag: string): TagValidation => {
  if (tag.includes("/"))
    return {
      reason: "InvalidChar",
      char: tag[tag.indexOf("/")],
    };
  if (tag.includes(","))
    return {
      reason: "InvalidChar",
      char: tag[tag.indexOf(",")],
    };
  return checkTagString(tag);
};

type InternalTag = {|
  ...Tag,
  selected: boolean,
|};
/**
 * Internal to the components for rendering the means with which a user may
 * choose a tag, additional state is used to richly render these tags. This
 * additional state gives us additional information with which to validate
 * that a tag can be chosen.
 */
export const checkInternalTag = (
  tag: InternalTag,
  { enforceOntologies }: { enforceOntologies: boolean }
): TagValidation => {
  const checkedName = checkTagString(tag.value);
  if (checkedName.reason !== "NoIssues") return checkedName;
  if (tag.selected) return { reason: "AlreadySelected" };
  return lift2(
    (version, filename) => ({
      reason: "NoIssuesWithSourceInformation",
      version,
      filename,
    }),
    tag.version,
    tag.vocabulary
  ).orElse(
    enforceOntologies
      ? { reason: "OntologiesAreEnforced" }
      : { reason: "NoIssues" }
  );
};

/* =======================
 * Using the TagValidation
 *
 * Once a tag has been validated, there are a number of changes to the UI that
 * may be desirable. These functions expose tweaks to the UI that can be made
 * to help a user understand that and why a tag is not allowed.
 * =======================
 */

/**
 * For disabling a tab based on the validation of a tag.
 */
export const isAllowed = (tagValidation: TagValidation): boolean => {
  return (
    tagValidation.reason === "NoIssues" ||
    tagValidation.reason === "NoIssuesWithSourceInformation"
  );
};

/**
 * For explaining why a tag is not allowed.
 */
export const helpText = (tagValidation: TagValidation): ?string => {
  if (tagValidation.reason === "NoIssues") return null;
  if (tagValidation.reason === "OntologiesAreEnforced")
    return "Not from an ontology";
  if (tagValidation.reason === "NoIssuesWithSourceInformation")
    return `From version ${tagValidation.version} of ${tagValidation.filename}`;
  if (tagValidation.reason === "TooLong")
    return `Tag cannot exceed ${MAX_LENGTH} characters.`;
  if (tagValidation.reason === "TooShort")
    return `Tag must be at least ${MIN_LENGTH} characters long.`;
  if (tagValidation.reason === "AlreadySelected")
    return "Tag is already selected.";
  if (tagValidation.reason === "InvalidChar")
    return `Tag contains invalid character "${tagValidation.char}".`;
  if (tagValidation.reason === "InvalidWhitespace") {
    if (tagValidation.detail === "Prefix")
      return "Tags cannot start with whitespace characters.";
    if (tagValidation.detail === "Suffix")
      return "Tags cannot end with whitespace characters.";
    if (tagValidation.detail === "Consecutive")
      return "Tags cannot contain consecutive whitespace characters.";
  }
};
