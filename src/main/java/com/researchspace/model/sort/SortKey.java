package com.researchspace.model.sort;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * A sort key a client may request for one listing. Each listing has its own enum implementing this
 * interface, and the enum's {@code fromRequest} method is the only place a request's {@code
 * orderBy} string is compared. DAOs switch on the enum to build their order clause, so no request
 * text ever reaches query construction.
 */
public interface SortKey {

  /** The token a client sends as the {@code orderBy} request parameter. */
  String key();

  /**
   * Resolves a request token to one of the listing's sort keys.
   *
   * @param raw the {@code orderBy} request value, may be blank
   * @param defaultKey returned when {@code raw} is blank
   * @throws UnknownSortKeyException if {@code raw} is not a key of {@code type}
   */
  static <E extends Enum<E> & SortKey> E fromRequest(Class<E> type, String raw, E defaultKey) {
    if (StringUtils.isBlank(raw)) {
      return defaultKey;
    }
    for (E candidate : type.getEnumConstants()) {
      if (candidate.key().equals(raw)) {
        return candidate;
      }
    }
    throw new UnknownSortKeyException(raw, keysOf(type));
  }

  static <E extends Enum<E> & SortKey> List<String> keysOf(Class<E> type) {
    return Arrays.stream(type.getEnumConstants()).map(SortKey::key).toList();
  }
}
