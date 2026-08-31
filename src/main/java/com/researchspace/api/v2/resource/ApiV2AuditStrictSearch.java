package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.service.audit.search.AuditTrailActorVisibility;
import com.researchspace.service.audit.search.AuditTrailSearchElement;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import com.researchspace.service.audit.search.IAuditTrailSearchConfig;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Performs a strict, bounded and retryable scan of the audit log for REST API v2. */
@Component
public final class ApiV2AuditStrictSearch {

  private static final String OPERATE_AS_DELIMITER = "->";
  private static final Pattern AUDIT_LINE =
      Pattern.compile(
          "^(.*?)- domain:(\\w+)\\s+action:(\\w+)\\s+\\[(.+)]\\s+"
              + "([A-Za-z0-9@.\\->]+)\\((.+)\\)\\s*(?:description:\\[(.*)])?$");
  private static final Pattern AUDIT_PREFIX = Pattern.compile("^.*?-\\s+domain:");
  private static final Pattern LOG_LINE_TIMESTAMP = Pattern.compile("^(.*?)-");
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      new DateTimeFormatterBuilder()
          .parseCaseSensitive()
          .appendPattern("dd MMM uuuu HH:mm:ss,SSS")
          .toFormatter(Locale.ENGLISH)
          .withResolverStyle(ResolverStyle.STRICT);
  private static final Set<OpenOption> READ_OPTIONS =
      Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

  private final Path logDirectory;
  private final String filePrefix;
  private final ZoneId logZone;
  private final AuditTrailActorVisibility actorVisibility;
  private final ReadObserver observer;

  @Autowired
  public ApiV2AuditStrictSearch(
      @Value("${logging.dir:.}") String loggingDirectory,
      AuditTrailActorVisibility actorVisibility) {
    this(
        Path.of(loggingDirectory),
        "RSLogs",
        ZoneId.systemDefault(),
        actorVisibility,
        ReadObserver.NONE);
  }

  ApiV2AuditStrictSearch(
      Path logDirectory,
      String filePrefix,
      ZoneId logZone,
      AuditTrailActorVisibility actorVisibility,
      ReadObserver observer) {
    this.logDirectory = logDirectory;
    this.filePrefix = filePrefix;
    this.logZone = logZone;
    this.actorVisibility = actorVisibility;
    this.observer = observer;
  }

  /**
   * Returns at most {@code ceiling + 1} authorized events from one consistent file-manifest
   * attempt. A second inconsistent attempt fails without returning accumulated results.
   */
  public synchronized List<AuditTrailSearchResult> search(Request request) {
    Objects.requireNonNull(request, "Audit search request");
    AuditTrailSearchElement restricted =
        actorVisibility.restrict(new VisibilityConfig(request), request.actor());
    if (restricted.getUsernames().isEmpty() && !actorVisibility.isSysAdmin(request.actor())) {
      return List.of();
    }

    StrictReadException firstFailure;
    try {
      return attempt(request, restricted, 1);
    } catch (StrictReadException ex) {
      firstFailure = ex;
    }
    try {
      return attempt(request, restricted, 2);
    } catch (StrictReadException ex) {
      throw new StrictReadException(ex.getSafeFile(), ex.getLineNumber(), firstFailure);
    }
  }

  private List<AuditTrailSearchResult> attempt(
      Request request, AuditTrailSearchElement restricted, int attemptNumber) {
    Map<Path, FileManifest> manifest = enumerate();
    observer.afterManifest(attemptNumber, List.copyOf(manifest.values()));
    List<AuditTrailSearchResult> results = new ArrayList<>();
    for (FileManifest file : manifest.values()) {
      if (!overlaps(file, request)) {
        continue;
      }
      observer.beforeOpen(attemptNumber, file);
      scan(file, request, restricted, results);
      if (results.size() > request.ceiling()) {
        return List.copyOf(results);
      }
    }
    observer.afterRead(attemptNumber, List.copyOf(manifest.values()));
    certify(manifest, request);
    return List.copyOf(results);
  }

  private Map<Path, FileManifest> enumerate() {
    Map<Path, FileManifest> manifests = new LinkedHashMap<>();
    Set<Path> canonicalPaths = new HashSet<>();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(logDirectory)) {
      List<Path> matches = new ArrayList<>();
      for (Path entry : entries) {
        if (entry.getFileName().toString().startsWith(filePrefix)) {
          matches.add(entry);
        }
      }
      matches.sort(Comparator.comparing(path -> path.getFileName().toString()));
      for (Path path : matches) {
        BasicFileAttributes attributes = attributes(path);
        if (!attributes.isRegularFile()) {
          throw failure(path, 0);
        }
        Path canonical = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!canonicalPaths.add(canonical)) {
          throw failure(path, 0);
        }
        manifests.put(
            canonical,
            new FileManifest(
                canonical,
                canonical.getFileName().toString(),
                attributes.fileKey(),
                attributes.size(),
                attributes.lastModifiedTime().toMillis()));
      }
      return manifests;
    } catch (StrictReadException ex) {
      throw ex;
    } catch (IOException | SecurityException ex) {
      throw new StrictReadException(logDirectory.getFileName().toString(), 0, ex);
    }
  }

  private boolean overlaps(FileManifest file, Request request) {
    Optional<BoundaryLine> firstBoundary = firstNonblankLine(file);
    if (firstBoundary.isEmpty()) {
      return false;
    }
    Instant first = parseBoundaryTimestamp(firstBoundary.get(), file);
    Instant last = parseBoundaryTimestamp(lastNonblankLine(file), file);
    return last.compareTo(request.fromInclusive()) >= 0
        && first.compareTo(request.toExclusive()) < 0;
  }

  private Optional<BoundaryLine> firstNonblankLine(FileManifest file) {
    try (FileChannel channel = open(file);
        InputStream input = new BoundedInputStream(Channels.newInputStream(channel), file.size());
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      int lineNumber = 0;
      String line;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (!line.isBlank()) {
          return Optional.of(new BoundaryLine(line, lineNumber));
        }
      }
      return Optional.empty();
    } catch (StrictReadException ex) {
      throw ex;
    } catch (IOException | SecurityException ex) {
      throw failure(file, 0, ex);
    }
  }

  private BoundaryLine lastNonblankLine(FileManifest file) {
    if (file.size() == 0) {
      throw failure(file, 1);
    }
    try (FileChannel channel = open(file)) {
      if (readByte(channel, file.size() - 1) != '\n') {
        throw failure(file, 0);
      }
      long position = file.size() - 2;
      ByteArrayOutputStream reversed = new ByteArrayOutputStream();
      while (position >= 0) {
        byte value = readByte(channel, position--);
        if (value == '\n') {
          String candidate = reverseUtf8(reversed);
          if (!candidate.isBlank()) {
            return new BoundaryLine(candidate, countLines(file, position + 2));
          }
          reversed.reset();
        } else if (value != '\r') {
          reversed.write(value);
        }
      }
      String candidate = reverseUtf8(reversed);
      if (!candidate.isBlank()) {
        return new BoundaryLine(candidate, 1);
      }
      throw failure(file, 1);
    } catch (StrictReadException ex) {
      throw ex;
    } catch (IOException | SecurityException ex) {
      throw failure(file, 0, ex);
    }
  }

  private int countLines(FileManifest file, long endExclusive) throws IOException {
    try (FileChannel channel = open(file)) {
      ByteBuffer buffer = ByteBuffer.allocate(8192);
      long remaining = endExclusive;
      int count = 1;
      while (remaining > 0) {
        buffer.clear();
        buffer.limit((int) Math.min(buffer.capacity(), remaining));
        int read = channel.read(buffer);
        if (read < 0) {
          break;
        }
        remaining -= read;
        buffer.flip();
        while (buffer.hasRemaining()) {
          if (buffer.get() == '\n') {
            count++;
          }
        }
      }
      return count;
    }
  }

  private static String reverseUtf8(ByteArrayOutputStream reversed) {
    byte[] bytes = reversed.toByteArray();
    for (int left = 0, right = bytes.length - 1; left < right; left++, right--) {
      byte swap = bytes[left];
      bytes[left] = bytes[right];
      bytes[right] = swap;
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static byte readByte(FileChannel channel, long position) throws IOException {
    ByteBuffer one = ByteBuffer.allocate(1);
    int read = channel.read(one, position);
    if (read != 1) {
      throw new IOException("Audit file changed during read");
    }
    return one.array()[0];
  }

  private Instant parseBoundaryTimestamp(BoundaryLine boundary, FileManifest file) {
    Matcher matcher = LOG_LINE_TIMESTAMP.matcher(boundary.content());
    if (!matcher.find()) {
      throw failure(file, boundary.lineNumber());
    }
    try {
      return timestamp(matcher.group(1));
    } catch (DateTimeException ex) {
      throw failure(file, boundary.lineNumber(), ex);
    }
  }

  private void scan(
      FileManifest file,
      Request request,
      AuditTrailSearchElement restricted,
      List<AuditTrailSearchResult> results) {
    try (FileChannel channel = open(file);
        InputStream input = new BoundedInputStream(Channels.newInputStream(channel), file.size());
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      int lineNumber = 0;
      String line;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        Optional<ParsedLine> parsed = parseAuditLine(line, file, lineNumber);
        if (parsed.isPresent() && matches(parsed.get(), request, restricted)) {
          results.add(parsed.get().result());
          if (results.size() > request.ceiling()) {
            return;
          }
        }
      }
    } catch (StrictReadException ex) {
      throw ex;
    } catch (IOException | SecurityException ex) {
      throw failure(file, 0, ex);
    }
  }

  private Optional<ParsedLine> parseAuditLine(String line, FileManifest file, int lineNumber) {
    Matcher matcher = AUDIT_LINE.matcher(line);
    if (!matcher.matches()) {
      if (AUDIT_PREFIX.matcher(line).find()) {
        throw failure(file, lineNumber);
      }
      return Optional.empty();
    }
    try {
      Instant timestamp = timestamp(matcher.group(1));
      AuditDomain domain = AuditDomain.valueOf(matcher.group(2));
      String actionValue =
          "COPY".equalsIgnoreCase(matcher.group(3)) ? "DUPLICATE" : matcher.group(3);
      AuditAction action = AuditAction.valueOf(actionValue);
      AuditData data = AuditData.fromJson(matcher.group(4));
      if (data == null) {
        throw failure(file, lineNumber);
      }
      HistoricData event =
          new HistoricData(domain, action, matcher.group(6), data, matcher.group(5));
      event.setTimestamp(Date.from(timestamp));
      if (matcher.group(7) != null) {
        event.setDescription(matcher.group(7));
      }
      return Optional.of(
          new ParsedLine(
              timestamp,
              domain,
              action,
              matcher.group(4),
              matcher.group(5),
              new AuditTrailSearchResult(event, timestamp.toEpochMilli())));
    } catch (DateTimeException | IllegalArgumentException ex) {
      throw failure(file, lineNumber, ex);
    }
  }

  private Instant timestamp(String value) {
    return LocalDateTime.parse(value.trim(), TIMESTAMP_FORMAT).atZone(logZone).toInstant();
  }

  private static boolean matches(
      ParsedLine line, Request request, AuditTrailSearchElement restricted) {
    return line.timestamp().compareTo(request.fromInclusive()) >= 0
        && line.timestamp().compareTo(request.toExclusive()) < 0
        && restricted.getDomains().contains(line.domain())
        && restricted.getActions().contains(line.action())
        && (request.oid() == null || line.rawData().contains("\"" + request.oid() + "\""))
        && actorMatches(restricted, line.username());
  }

  private static boolean actorMatches(AuditTrailSearchElement restricted, String username) {
    if (restricted.getUsernames().isEmpty()) {
      return true;
    }
    if (restricted.getUsernames().contains(username)) {
      return true;
    }
    if (username.contains(OPERATE_AS_DELIMITER)) {
      String[] operatedAs = username.split(OPERATE_AS_DELIMITER, 2);
      return restricted.getUsernames().contains(operatedAs[0])
          || restricted.getUsernames().contains(operatedAs[1]);
    }
    return false;
  }

  private void certify(Map<Path, FileManifest> original, Request request) {
    Map<Path, FileManifest> current = enumerate();
    if (!original.keySet().equals(current.keySet())) {
      throw new StrictReadException("audit-manifest", 0, null);
    }
    for (Map.Entry<Path, FileManifest> entry : original.entrySet()) {
      FileManifest before = entry.getValue();
      FileManifest after = current.get(entry.getKey());
      if (!sameIdentity(before, after) || after.size() < before.size()) {
        throw failure(before, 0);
      }
      if (after.size() == before.size()) {
        if (after.modifiedMillis() != before.modifiedMillis()) {
          throw failure(before, 0);
        }
        continue;
      }
      validateAppend(before, after, request);
    }
  }

  private void validateAppend(FileManifest before, FileManifest after, Request request) {
    try (FileChannel channel = open(after)) {
      channel.position(before.size());
      long appendedSize = after.size() - before.size();
      try (InputStream input =
              new BoundedInputStream(Channels.newInputStream(channel), appendedSize);
          BufferedReader reader =
              new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
          lineNumber++;
          if (line.isBlank()) {
            continue;
          }
          Optional<ParsedLine> parsed = parseAuditLine(line, after, lineNumber);
          if (parsed.isPresent() && parsed.get().timestamp().isBefore(request.toExclusive())) {
            throw failure(after, lineNumber);
          }
        }
      }
      BasicFileAttributes finalAttributes = attributes(after.path());
      if (finalAttributes.size() != after.size()
          || finalAttributes.lastModifiedTime().toMillis() != after.modifiedMillis()
          || !Objects.equals(finalAttributes.fileKey(), after.fileKey())) {
        throw failure(after, 0);
      }
      if (appendedSize > 0 && readByte(channel, after.size() - 1) != '\n') {
        throw failure(after, 0);
      }
    } catch (StrictReadException ex) {
      throw ex;
    } catch (IOException | SecurityException ex) {
      throw failure(after, 0, ex);
    }
  }

  private static boolean sameIdentity(FileManifest before, FileManifest after) {
    return before.fileKey() != null
        && after.fileKey() != null
        && Objects.equals(before.fileKey(), after.fileKey());
  }

  private static BasicFileAttributes attributes(Path path) throws IOException {
    return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
  }

  private static FileChannel open(FileManifest file) throws IOException {
    return FileChannel.open(file.path(), READ_OPTIONS);
  }

  private static StrictReadException failure(Path path, int lineNumber) {
    return new StrictReadException(path.getFileName().toString(), lineNumber, null);
  }

  private static StrictReadException failure(FileManifest file, int lineNumber) {
    return new StrictReadException(file.safeName(), lineNumber, null);
  }

  private static StrictReadException failure(FileManifest file, int lineNumber, Throwable cause) {
    return new StrictReadException(file.safeName(), lineNumber, cause);
  }

  /** Exact filters for one bounded scan. */
  public record Request(
      Instant fromInclusive,
      Instant toExclusive,
      Set<AuditDomain> domains,
      Set<AuditAction> actions,
      String oid,
      Set<String> requestedUsernames,
      User actor,
      int ceiling) {

    public Request {
      Objects.requireNonNull(fromInclusive, "Audit start");
      Objects.requireNonNull(toExclusive, "Audit end");
      Objects.requireNonNull(domains, "Audit domains");
      Objects.requireNonNull(actions, "Audit actions");
      Objects.requireNonNull(requestedUsernames, "Audit usernames");
      Objects.requireNonNull(actor, "Audit actor");
      if (!fromInclusive.isBefore(toExclusive)) {
        throw new IllegalArgumentException("Audit interval must be non-empty");
      }
      if (ceiling < 1) {
        throw new IllegalArgumentException("Audit result ceiling must be positive");
      }
      domains = Set.copyOf(domains);
      actions = Set.copyOf(actions);
      requestedUsernames = Set.copyOf(requestedUsernames);
    }
  }

  record FileManifest(Path path, String safeName, Object fileKey, long size, long modifiedMillis) {}

  interface ReadObserver {
    ReadObserver NONE = new ReadObserver() {};

    default void afterManifest(int attempt, List<FileManifest> manifest) {}

    default void beforeOpen(int attempt, FileManifest file) {}

    default void afterRead(int attempt, List<FileManifest> manifest) {}
  }

  static final class StrictReadException extends RuntimeException {
    @Getter private final String safeFile;
    @Getter private final int lineNumber;

    StrictReadException(String safeFile, int lineNumber, Throwable cause) {
      super("Cannot consistently read audit file " + safeFile + " at line " + lineNumber, cause);
      this.safeFile = safeFile;
      this.lineNumber = lineNumber;
    }
  }

  private record BoundaryLine(String content, int lineNumber) {}

  private record ParsedLine(
      Instant timestamp,
      AuditDomain domain,
      AuditAction action,
      String rawData,
      String username,
      AuditTrailSearchResult result) {}

  private static final class VisibilityConfig implements IAuditTrailSearchConfig {
    private final Request request;

    private VisibilityConfig(Request request) {
      this.request = request;
    }

    @Override
    public Date getDateFrom() {
      return null;
    }

    @Override
    public void setDateFrom(Date dateFrom) {}

    @Override
    public Date getDateTo() {
      return null;
    }

    @Override
    public void setDateTo(Date dateTo) {}

    @Override
    public Set<String> getUsernames() {
      return request.requestedUsernames();
    }

    @Override
    public Set<AuditDomain> getDomains() {
      return request.domains();
    }

    @Override
    public Set<AuditAction> getActions() {
      return request.actions();
    }

    @Override
    public String getOid() {
      return request.oid();
    }
  }

  private static final class BoundedInputStream extends InputStream {
    private final InputStream delegate;
    private long remaining;

    private BoundedInputStream(InputStream delegate, long remaining) {
      this.delegate = delegate;
      this.remaining = remaining;
    }

    @Override
    public int read() throws IOException {
      if (remaining == 0) {
        return -1;
      }
      int value = delegate.read();
      if (value >= 0) {
        remaining--;
      }
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      if (remaining == 0) {
        return -1;
      }
      int read = delegate.read(buffer, offset, (int) Math.min(length, remaining));
      if (read > 0) {
        remaining -= read;
      }
      return read;
    }
  }
}
