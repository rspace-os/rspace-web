package com.researchspace.dao.hibernate;

import com.researchspace.model.inventory.Instrument;
import com.researchspace.search.customfield.RuntimeFieldTextSearch;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Setter;
import org.hibernate.SessionFactory;
import org.hibernate.search.engine.search.predicate.dsl.SimpleBooleanPredicateClausesStep;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/** Lucene-backed narrowing for word filters over indexed text. */
@Repository("runtimeFieldTextSearch")
public class RuntimeFieldTextSearchImpl implements RuntimeFieldTextSearch {

  private static final Logger log = LoggerFactory.getLogger(RuntimeFieldTextSearchImpl.class);

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final String COMPLETE_MARKER = ".instrument-runtime-fields.complete";

  @Autowired private SessionFactory sessionFactory;

  @Value("${collections.textSearch.enabled}")
  private boolean enabled;

  @Value("${collections.textSearch.debugLogTerms:false}")
  private boolean debugLogTerms;

  @Value("${rs.hibernate.searchIndex.folder}")
  @Setter(AccessLevel.PACKAGE)
  private String indexRoot;

  private volatile boolean complete;

  @PostConstruct
  void readCompletenessMarker() {
    complete = Files.isRegularFile(completenessMarker());
  }

  @Override
  public Optional<List<Long>> matchingIds(
      Class<?> indexedType, String indexField, String text, int maxMatches) {
    if (!enabled
        || !complete
        || !Files.isRegularFile(completenessMarker())
        || indexedType == null
        || indexField == null
        || text == null
        || text.isBlank()) {
      log.debug(
          "Not asking the index for {} (enabled={}, complete={})", indexField, enabled, complete);
      return Optional.empty();
    }
    try {
      SearchSession search = Search.session(sessionFactory.getCurrentSession());
      List<Long> ids =
          search
              .search(indexedType)
              .select(step -> step.id(Long.class))
              .where(
                  step -> {
                    SimpleBooleanPredicateClausesStep<?> all = step.and();
                    for (String word : WHITESPACE.split(text.trim())) {
                      all.add(step.match().field(indexField).matching(word));
                    }
                    return all;
                  })
              .fetchHits(maxMatches + 1);
      if (ids.size() > maxMatches) {
        log.info(
            "Text filter on {} matched more than {} records; using the database instead",
            indexField,
            maxMatches);
        return Optional.empty();
      }
      if (debugLogTerms) {
        log.debug("Index answered {} for '{}' with {} ids", indexField, text, ids.size());
      } else {
        log.debug("Index answered {} with {} ids", indexField, ids.size());
      }
      return Optional.of(ids);
    } catch (RuntimeException ex) {
      log.warn("Text search unavailable for {}, using the database", indexField, ex);
      return Optional.empty();
    }
  }

  @Override
  public void reindexAll() throws InterruptedException {
    invalidateCompletenessMarker();
    Search.session(sessionFactory.getCurrentSession()).massIndexer(Instrument.class).startAndWait();
    writeCompletenessMarker();
  }

  private Path completenessMarker() {
    return Path.of(indexRoot).resolve(COMPLETE_MARKER);
  }

  private void invalidateCompletenessMarker() {
    complete = false;
    try {
      Files.deleteIfExists(completenessMarker());
    } catch (IOException e) {
      throw new IllegalStateException("Could not invalidate the runtime-field index marker", e);
    }
  }

  private void writeCompletenessMarker() {
    Path marker = completenessMarker();
    Path temporary = marker.resolveSibling(marker.getFileName() + ".tmp");
    try {
      Files.createDirectories(marker.getParent());
      Files.writeString(
          temporary,
          "complete\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      try {
        Files.move(
            temporary, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING);
      }
      complete = true;
    } catch (IOException e) {
      complete = false;
      throw new IllegalStateException("Could not mark the runtime-field index complete", e);
    }
  }
}
