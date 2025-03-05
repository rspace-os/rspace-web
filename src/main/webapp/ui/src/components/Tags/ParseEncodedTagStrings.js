//@flow

import { type Tag } from "../../stores/definitions/Tag";
import { Optional, lift3 } from "../../util/optional";
import * as ArrayUtils from "../../util/ArrayUtils";

/*
 * The backend encodes tags that have metadata extracted from ontology files as
 * a complex string with delimiters and escape codes. This module abstract
 * that logic away from the rest of the JS code that fetches data that is
 * encoded in this way.
 */

/**
 * This string, when it appears in the list of tags returned by the server,
 * signals that no more tags are available and that another page should not be
 * requested.
 */
export const SMALL_DATASET_SIGNAL =
  "=========SMALL_DATASET_IN_SINGLE_BLOCK=========";

/**
 * This string, when it appears in the list of tags returned by the server,
 * signals that no more tags are available and that another page should not be
 * requested.
 */
export const FINAL_DATA_SIGNAL = "=========FINAL_DATA=========";

/**
 * There are some idiosyncrasies of how the backend persists the tags in the
 * database that are carried over into the API. These include encoding commas
 * and forward slashes. This function decodes this encoding into plain strings.
 */
export const decodeTagString = (str: string): string =>
  str
    .replaceAll("__rspactags_forsl__", "/")
    .replaceAll("__rspactags_comma__", ",");

/**
 * There are some idiosyncrasies of how the backend persists the tags in the
 * database that are carried over into the API. These include encoding commas
 * and forward slashes. This function encodes plain strings into this encoding.
 */
export const encodeTagString = (str: string): string =>
  str
    .replaceAll("/", "__rspactags_forsl__")
    .replaceAll(",", "__rspactags_comma__");

/**
 * Encode a list of tags back into the encoded string that the API and database use.
 *
 * If a tag has a URI, vocabulary, and version then it is encoded with all
 * those values, plus the tag's value itself, separated by the delimiters. If
 * none of the three values are specified then it is treated as a simple tag
 * and just the value is included in the output.  If some of those three values
 * are specified then it causes the entire function to return Optional.empty.
 */
export function encodeTags(tags: Array<Tag>): Optional<string> {
  return ArrayUtils.all(
    tags.map((tag) =>
      lift3(
        (uri, voc, ver) =>
          `${encodeTagString(
            tag.value
          )}__RSP_EXTONT_URL_DELIM__${encodeTagString(
            uri
          )}__RSP_EXTONT_NAME_DELIM__${encodeTagString(
            voc
          )}__RSP_EXTONT_VERSION_DELIM__${encodeTagString(ver)}`,
        tag.uri,
        tag.vocabulary,
        tag.version
      ).orElseTry(() => {
        if (
          tag.vocabulary.isEmpty() &&
          tag.uri.isEmpty() &&
          tag.version.isEmpty()
        )
          return Optional.present(tag.value);
        return Optional.empty<string>();
      })
    )
  ).map((encodedTags) => encodedTags.join(","));
}

/**
 * Parse a list of encoded tags into a list of Tag objects.
 * The value of the tag can always be extracted from the encoded tag (although
 * it might be the empty string), and the other properties of Tag are optional.
 */
export function parseEncodedTags(encodedTags: Array<string>): Array<Tag> {
  return encodedTags
    .filter(
      (tag) =>
        tag !== "" && tag !== SMALL_DATASET_SIGNAL && tag !== FINAL_DATA_SIGNAL
    )
    .map((tag: string): Tag => {
      const groups =
        /(?<tagName>.*)__RSP_EXTONT_URL_DELIM__(?<uri>.*)__RSP_EXTONT_NAME_DELIM__(?<filename>.*)__RSP_EXTONT_VERSION_DELIM__(?<version>.*)/.exec(
          decodeTagString(tag)
        )?.groups;
      if (!groups) {
        // because the regex doesn't match, that means that tag isn't from an ontology
        return {
          value: tag,
          vocabulary: Optional.empty(),
          uri: Optional.empty(),
          version: Optional.empty(),
        };
      }
      const { tagName, filename, version, uri } = groups;
      return {
        value: tagName,
        vocabulary: Optional.fromNullable(filename),
        uri: Optional.fromNullable(uri),
        version: Optional.fromNullable(version),
      };
    });
}
