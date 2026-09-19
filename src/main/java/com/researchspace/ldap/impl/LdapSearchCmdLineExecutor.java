package com.researchspace.ldap.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ldap.support.LdapEncoder;

/** Runs the `ldapsearch` command to retrieve user's LDAP details. */
@Slf4j
public class LdapSearchCmdLineExecutor {

  private String searchBase;
  private String host;
  private String dnField;

  public LdapSearchCmdLineExecutor(String ldapBaseSuffix, String ldapUrl, String dnField) {
    searchBase = ldapBaseSuffix;
    host = ldapUrl;
    this.dnField = dnField;
  }

  /**
   * Runs the ldapsearch command as an argument array (no shell) and finds the dn attribute in the
   * response.
   *
   * @param ldapUsername uid to match in ldapsearch
   * @return dn or null if not found
   */
  public String findDnForUid(String ldapUsername) {

    List<String> command = getLdapSearchCommand(ldapUsername);
    log.debug("running: {}", command);

    String foundDn = null;
    try {
      ProcessBuilder builder = new ProcessBuilder(command);
      // Discard stderr rather than merging it: keeps server-controlled text out of the dn scan and
      // avoids the stderr pipe buffer filling and deadlocking against our stdout read.
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      Process process = builder.start();
      foundDn = readDnFromProcessOutput(process.getInputStream());
      log.info("ldapsearch found dn: {}", foundDn);
      if (StringUtils.isBlank(foundDn)) {
        log.warn("dn not found by ldapsearch command: {}", command);
      }
      if (!process.waitFor(30, TimeUnit.SECONDS)) {
        log.warn("ldapsearch command timed out, destroying process: {}", command);
        process.destroyForcibly();
      }
    } catch (IOException e) {
      log.warn("error when executing ldapsearch command: {}", command, e);
    } catch (InterruptedException e) {
      log.warn("interrupted when executing ldapsearch command: {}", command, e);
      Thread.currentThread().interrupt();
    }

    return foundDn;
  }

  protected List<String> getLdapSearchCommand(String ldapUsername) {
    return List.of(
        "ldapsearch",
        "-x",
        "-H",
        host,
        "-b",
        searchBase,
        "uid=" + LdapEncoder.filterEncode(ldapUsername));
  }

  private String readDnFromProcessOutput(InputStream inputStream) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith(dnField + ": ")) {
          log.debug("found dn line: " + line);
          String[] dnLineTokens = line.split(" ");
          return dnLineTokens[1];
        }
      }
    }
    log.info("no line starting with \"" + dnField + ": \" found");
    return null;
  }
}
