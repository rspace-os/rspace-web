import { Optional, lift2 } from "../../util/optional";

/**
 * The definition of a tag in RSpace. If the tag is describing a term from a
 * controlled vocabulary (an ontologoy file) then the version, vocabulary, and
 * uri will be present. If it is a term that the user has simply added without
 * reference to a controlled vocabulary then it will not have those properties
 * present and they will be Optional.empty. Typically, a tag should not have a
 * mixture of the version, vocabulary, and uri present -- it's all or nothing
 * -- but this type is not modelled as a disjoint union to keep the code
 * simpler. If flow cannot reason about this difference then using a disjoint
 * union may become necessary.
 */
export type Tag = {
  value: string;
  version: Optional<string>;
  vocabulary: Optional<string>;
  uri: Optional<string>;
};

/**
 * Checks if two tags are describing the same term. If they are both from a
 * controlled vocabulary, and thus have a URI, then those URIs are compared.
 * Otherwise, if they both do not have URIs then the value strings are
 * compared. If one has a URI and the other does not, then they are never the
 * same.
 */
export const areSameTag = (tagA: Tag, tagB: Tag): boolean =>
  lift2((uriA, uriB) => uriA === uriB, tagA.uri, tagB.uri).orElse(
    !tagA.uri.isPresent() && !tagB.uri.isPresent() && tagA.value === tagB.value
  );
