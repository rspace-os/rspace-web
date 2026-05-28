package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsFileSystem;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Setter;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Per-filesystem read/write authorization based on username whitelists.
 *
 * <p>Whitelists are only consulted for filesystems with {@link NfsAuthenticationType#NONE} (i.e.
 * server-wide credentials, no per-user identity at the storage layer). For other auth types both
 * checks short-circuit to {@code true}, since per-user authentication already gates access.
 */
@Component
public class FilestoreAclChecker {

  public static final String EVERYONE = "*";

  @Autowired @Setter private MessageSourceUtils messages;

  /**
   * Parses a whitelist string into a trimmed, deduplicated set of tokens. Whitespace is stripped
   * around each token; empty tokens are dropped. Returns an empty set for {@code null} or blank
   * input.
   */
  public static Set<String> parseList(String whitelist) {
    if (whitelist == null) {
      return Collections.emptySet();
    }
    Set<String> tokens = new LinkedHashSet<>();
    for (String raw : whitelist.split(",")) {
      String trimmed = raw.trim();
      if (!trimmed.isEmpty()) {
        tokens.add(trimmed);
      }
    }
    return tokens;
  }

  public boolean canRead(User user, NfsFileSystem fs) {
    if (fs == null || fs.getAuthType() == null) {
      return false;
    }
    if (!isGated(fs)) {
      return true;
    }
    return inList(user, fs.getReadWhitelist()) || inList(user, fs.getWriteWhitelist());
  }

  public boolean canWrite(User user, NfsFileSystem fs) {
    if (fs == null || fs.getAuthType() == null) {
      return false;
    }
    if (!isGated(fs)) {
      return true;
    }
    return inList(user, fs.getWriteWhitelist());
  }

  public void assertCanRead(User user, NfsFileSystem fs) {
    if (!canRead(user, fs)) {
      throw denied(user, fs, "read");
    }
  }

  public void assertCanWrite(User user, NfsFileSystem fs) {
    if (!canWrite(user, fs)) {
      throw denied(user, fs, "write");
    }
  }

  private AuthorizationException denied(User user, NfsFileSystem fs, String op) {
    return new AuthorizationException(
        messages.getMessage(
            "netfilestores.acl.denied." + op, new Object[] {user.getUsername(), fs.getName()}));
  }

  private static boolean isGated(NfsFileSystem fs) {
    return NfsAuthenticationType.NONE.equals(fs.getAuthType());
  }

  private static boolean inList(User user, String whitelist) {
    Set<String> tokens = parseList(whitelist);
    return tokens.contains(EVERYONE) || tokens.contains(user.getUsername());
  }
}
