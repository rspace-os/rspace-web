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
 * Per-filesystem read/write authorization based on username allowlists.
 *
 * <p>Allowlists are only consulted for filesystems with {@link NfsAuthenticationType#NONE} (i.e.
 * server-wide credentials, no per-user identity at the storage layer). For other auth types both
 * checks short-circuit to {@code true}, since per-user authentication already gates access.
 */
@Component
public class FilestoreAclChecker {

  public static final String EVERYONE = "*";

  private @Autowired @Setter MessageSourceUtils messages;

  /** Splits a comma-separated allowlist into trimmed, non-empty tokens. */
  public static Set<String> parseList(String allowlist) {
    if (allowlist == null) {
      return Collections.emptySet();
    }
    Set<String> tokens = new LinkedHashSet<>();
    for (String raw : allowlist.split(",")) {
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
    return inList(user, fs.getReadAllowlist()) || inList(user, fs.getWriteAllowlist());
  }

  public boolean canWrite(User user, NfsFileSystem fs) {
    if (fs == null || fs.getAuthType() == null) {
      return false;
    }
    if (!isGated(fs)) {
      return true;
    }
    return inList(user, fs.getWriteAllowlist());
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
    String fsName = fs == null ? "(unknown)" : fs.getName();
    return new AuthorizationException(
        messages.getMessage(
            "netfilestores.acl.denied." + op, new Object[] {user.getUsername(), fsName}));
  }

  private static boolean isGated(NfsFileSystem fs) {
    return NfsAuthenticationType.NONE.equals(fs.getAuthType());
  }

  private static boolean inList(User user, String allowlist) {
    Set<String> tokens = parseList(allowlist);
    return tokens.contains(EVERYONE) || tokens.contains(user.getUsername());
  }
}
