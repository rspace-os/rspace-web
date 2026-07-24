package com.researchspace.dao.hibernate;

import com.axiope.search.FieldNames;
import com.axiope.search.IFullTextSearcher;
import com.axiope.search.SearchConstants;
import com.axiope.search.SearchQueryParseException;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.search.impl.LuceneSrchCfg;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import org.apache.lucene.index.Term;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.mapper.orm.scope.SearchScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class responsible for creating the seqrch query to be executed in Lucene. */
class RSQueryBuilder {

  private static final int FUZZY_MAX_EDIT_DISTANCE = 2;
  private static final int FUZZY_PREFIX_LENGTH = 1;
  private static final int SLOP = 3;

  /**
   * Constants defining Lucene search strategies. A fuzzy query (based on the Levenshtein distance
   * algorithm), start like a keyword query and add the fuzzy flag.
   */
  private static final String FUZZY_LUCENE_INDICATOR = "~";

  /**
   * Wild card queries (queries where some of parts of the word are unknown). "*" represents any
   * character sequence. To replace multiple characters in your search, use an asterisk (*) as a
   * wild card. For example, to search for 'chicken' or 'chickpea': chick*
   */
  private static final String WILDCARD_LUCENE_INDICATOR = "*";

  /**
   * Wild card queries (queries where some of parts of the word are unknown). "?" represents a
   * single character. To replace a single character in your search, use a question mark (?) as a
   * wildcard. For example, to search for 'butter', 'bitter', 'better', or 'batter': b?tter
   */
  private static final String QUESTION_MARK_INDICATOR = "?";

  /**
   * Phrase queries, we have been looking for words or sets of words, you can also search exact or
   * approximate sentences. You can search approximate sentences by adding a slop factor. The slop
   * factor represents the number of other words permitted in the sentence: this works like a within
   * or near operator
   */
  private static final String PHRASE1_LUCENE_INDICATOR = "\'";

  private static final String PHRASE2_LUCENE_INDICATOR = "\"";
  public static final String LUCENE_TERM_DELIMITER = "<<>>";

  private static Logger log = LoggerFactory.getLogger(RSQueryBuilder.class);

  /**
   * @param scope The Hibernate Search scope for the indexed types to search over
   * @param cfg LuceneSrchConfig from the client
   * @return
   */
  protected SearchPredicate getSearchPredicate(SearchScope<?> scope, LuceneSrchCfg cfg) {

    String srchTerm = "";
    if (cfg.getTermList().size() > 0) {
      srchTerm = cfg.getAllTerms().get(0).text();
    } else {
      throw new SearchQueryParseException(
          new Exception("A search term is missing in the search config object"));
    }

    SearchPredicateFactory f = scope.predicate();
    SearchPredicate predicate = null;

    try {
      switch (cfg.getSearchStrategy()) {
        case IFullTextSearcher.ALL_LUCENE_SEARCH_STRATEGY:
          predicate = createShouldPredicate(f, cfg.getAllTerms());
          break;

        case IFullTextSearcher.ADVANCED_LUCENE_SEARCH_STRATEGY:
          if (cfg.getOperator().equals(SearchOperator.AND)) {
            predicate = createMustPredicate(f, cfg.getAllTerms());
          } else {
            predicate = createShouldPredicate(f, cfg.getAllTerms());
          }
          break;
        case IFullTextSearcher.SINGLE_LUCENE_SEARCH_STRATEGY:
          predicate = createMustPredicate(f, cfg.getAllTerms());
          break;

        default:
          String[] searchFields = expandFieldDataFields(cfg.getTermListFields());
          StringTokenizer tk = new StringTokenizer(srchTerm, ",");
          if (tk.countTokens() > 1) {
            predicate = andKeywords(f, searchFields, tk);
          } else {
            predicate = f.match().fields(searchFields).matching(srchTerm).toPredicate();
          }
      }

      predicate = addUserFilterInPredicate(cfg, f, predicate);
      predicate = addRecordFilterInPredicate(cfg, f, predicate);
      predicate = addParentIdFilterInPredicate(cfg, f, predicate);
    } catch (RuntimeException ex) {
      log.warn("Could not generate lucene query! : " + ex.getMessage());
      predicate = null;
    }
    return predicate;
  }

  /**
   * @param cfg
   * @param qb
   * @param luceneQuery
   * @return
   */
  private SearchPredicate addRecordFilterInPredicate(
      LuceneSrchCfg cfg, SearchPredicateFactory f, SearchPredicate predicate) {

    if (cfg.isRecordFilterListUsableInLucene()) {
      BooleanPredicateClausesStep<?> baseRecordFilter = f.bool();
      for (BaseRecord bs : cfg.getRecordFilterList()) {
        baseRecordFilter = baseRecordFilter.should(f.match().field("id").matching(bs.getId()));
      }
      // Hibernate Search 7 requires explicit minimumShouldMatch for SHOULD-only boolean queries
      baseRecordFilter = baseRecordFilter.minimumShouldMatchNumber(1);
      BooleanPredicateClausesStep<?> recordsBooleanQuery = f.bool();
      predicate = recordsBooleanQuery.must(baseRecordFilter).must(predicate).toPredicate();
    }

    return predicate;
  }

  private SearchPredicate addParentIdFilterInPredicate(
      LuceneSrchCfg cfg, SearchPredicateFactory f, SearchPredicate predicate) {
    if (cfg.getParentId() != null) {
      SearchPredicate q =
          f.match().field(FieldNames.PARENT_ID).matching(cfg.getParentId()).toPredicate();
      predicate = f.bool().must(q).must(predicate).toPredicate();
    }
    if (cfg.getParentTemplateId() != null) {
      SearchPredicate q =
          f.match()
              .field(FieldNames.PARENT_TEMPLATE_ID)
              .matching(cfg.getParentTemplateId())
              .toPredicate();
      predicate = f.bool().must(q).must(predicate).toPredicate();
    }
    if (cfg.getParentSampleId() != null) {
      SearchPredicate q =
          f.match()
              .field(FieldNames.PARENT_SAMPLE_ID)
              .matching(cfg.getParentSampleId())
              .toPredicate();
      predicate = f.bool().must(q).must(predicate).toPredicate();
    }
    return predicate;
  }

  /**
   * Adds 'must' queries to filter results to only those that the user has permission to view. Also
   * see `restrictByUser`.
   *
   * @param cfg search config
   * @param qb query builder
   * @param luq query being built
   * @return the query with user filter added
   */
  private SearchPredicate addUserFilterInPredicate(
      LuceneSrchCfg cfg, SearchPredicateFactory f, SearchPredicate predicate) {
    if (cfg.isRestrictByUser()) {
      BooleanPredicateClausesStep<?> userFilter = f.bool();
      for (String username : cfg.getUsernameFilterList()) {
        userFilter = userFilter.should(ownerUsernameMatch(f, username));
      }
      for (String sharedWith : cfg.getSharedWithFilterList()) {
        // sharedWith is a @KeywordField storing a comma-joined string of group unique names.
        // Use wildcard to match a single group name within the potentially multi-group string.
        userFilter =
            userFilter.should(f.wildcard().field("sharedWith").matching("*" + sharedWith + "*"));
      }
      // Hibernate Search 7 requires explicit minimumShouldMatch for SHOULD-only boolean queries
      userFilter = userFilter.minimumShouldMatchNumber(1);
      BooleanPredicateClausesStep<?> allUsers = f.bool();
      predicate = allUsers.must(userFilter).must(predicate).toPredicate();
    }
    return predicate;
  }

  /**
   * Builds a SHOULD-over-both-fields owner match for a single username. Owner is indexed two ways:
   *
   * <ul>
   *   <li>{@code owner.username}: embedded path on BaseRecord subtypes (StructuredDocument,
   *       EcatMedia, etc.) and on InventoryRecord via {@code @IndexedEmbedded owner}
   *   <li>{@code owner_username}: flat keyword on EcatCommentItem (indexed from lastUpdater)
   * </ul>
   *
   * Either field may be absent from a given scope, so each clause is added defensively. Used by
   * both the permission filter ({@link #addUserFilterInPredicate}) and the explicit owner search
   * term ({@link #getPredicateList}) so they stay in step.
   */
  private BooleanPredicateClausesStep<?> ownerUsernameMatch(
      SearchPredicateFactory f, String username) {
    BooleanPredicateClausesStep<?> ownerMatch = f.bool();
    try {
      ownerMatch = ownerMatch.should(f.match().field("owner.username").matching(username));
    } catch (RuntimeException e) {
      log.debug("owner.username not in scope: {}", e.getMessage());
    }
    try {
      ownerMatch = ownerMatch.should(f.match().field("owner_username").matching(username));
    } catch (RuntimeException e) {
      log.debug("owner_username not in scope: {}", e.getMessage());
    }
    return ownerMatch.minimumShouldMatchNumber(1);
  }

  /**
   * Create a multiple query used by advanced search. We use "MUST" = "AND" to create the query, in
   * other words the query must contain the matching elements of the sub query.
   *
   * @param qb
   * @param fssn
   * @param termList
   * @return Query
   * @throws java.text.ParseException
   */
  protected SearchPredicate createMustPredicate(SearchPredicateFactory f, List<Term> termList) {
    BooleanPredicateClausesStep<?> mustClauses = f.bool();
    List<SearchPredicate> predicates = getPredicateList(f, termList);
    for (SearchPredicate predicate : predicates) {
      mustClauses = mustClauses.must(predicate);
    }
    return mustClauses.toPredicate();
  }

  /**
   * Create a multiple query used by advanced search. We use "SHOULD" = "OR" to create the query, in
   * other words the query should contain the matching elements of the sub query.
   *
   * @param qb
   * @param fssn
   * @param termList
   * @return
   * @throws java.text.ParseException
   */
  protected SearchPredicate createShouldPredicate(SearchPredicateFactory f, List<Term> termList) {
    BooleanPredicateClausesStep<?> shouldClauses = f.bool();
    List<SearchPredicate> predicates = getPredicateList(f, termList);
    for (SearchPredicate predicate : predicates) {
      shouldClauses = shouldClauses.should(predicate);
    }
    // Hibernate Search 7 requires explicit minimumShouldMatch for SHOULD-only boolean queries
    // (OR semantics)
    return shouldClauses.minimumShouldMatchNumber(1).toPredicate();
  }

  /**
   * Create a list of Query from a list of Term.
   *
   * @param qb
   * @param fssn
   * @param termList
   * @return
   * @throws java.text.ParseException
   */
  private List<SearchPredicate> getPredicateList(SearchPredicateFactory f, List<Term> termList) {
    List<SearchPredicate> ql = new ArrayList<>();

    for (int i = 0; i < termList.size(); i++) {
      Term term = termList.get(i);
      if (term.field().equals(FieldNames.MODIFICATION_DATE)
          || term.field().equals(FieldNames.CREATION_DATE)) {
        // Validator should have already checked that the string contains ';'
        String[] toAndFrom = term.text().split("\\s*[,;]\\s*", -1);
        Date dateTo, dateFrom;

        if (toAndFrom[0].isEmpty() || toAndFrom[0].equals("null"))
          dateFrom = Date.from(Instant.EPOCH); // start of the universe aka. Jan 1st 1970
        else dateFrom = Date.from(OffsetDateTime.parse(toAndFrom[0]).toInstant());

        if (toAndFrom[1].isEmpty() || toAndFrom[1].equals("null") || toAndFrom[1].equals("now"))
          dateTo = Date.from(Instant.now());
        else dateTo = Date.from(OffsetDateTime.parse(toAndFrom[1]).toInstant());
        ql.add(f.range().field(term.field()).between(dateFrom, dateTo).toPredicate());

      } else if (term.field().equals(FieldNames.TEMPLATE)) {
        /**
         * In case when the field is a template, we are indexing by name and OID so we need to add
         * those like so: some_conditions && ( templateOid = "whatever" || temapleteName =
         * "Whatever" )
         */
        BooleanPredicateClausesStep<?> conjunction =
            f.bool()
                .should(createQueryAnalyzingTerm(f, "templateName", term.text()))
                .should(createQueryAnalyzingTerm(f, "templateOid", term.text()))
                .minimumShouldMatchNumber(1);

        ql.add(conjunction.toPredicate());

      } else if (term.field().equals(FieldNames.OWNER)) {
        // Mirror addUserFilterInPredicate: owner lives at owner.username on most types and at the
        // flat owner_username keyword on EcatCommentItem, so an owner search must cover both or it
        // misses comments authored by the searched-for user.
        BooleanPredicateClausesStep<?> conjunction = f.bool();
        for (String userName : term.text().split(",")) {
          conjunction = conjunction.should(ownerUsernameMatch(f, userName));
        }
        ql.add(conjunction.minimumShouldMatchNumber(1).toPredicate());

      } else {
        // For fields.fieldData, also search fields_fieldData (contains description + globalId on
        // BaseRecord, and itemContent on EcatCommentItem): Hibernate Search 7 uses two separate
        // index fields where Hibernate Search 5 used one field name for both embedded and flat
        // content.
        // Wrap in try-catch: fields may not exist on all entity types (e.g. fields.fieldData
        // doesn't exist on inventory entities) — skip gracefully rather than failing the whole
        // query.
        try {
          if (term.field().equals(FieldNames.FIELD_DATA)) {
            // Try each sub-predicate independently so that fields.fieldData on Sample scope
            // can be searched even when fields_fieldData doesn't exist in the scope (or vice
            // versa).
            int clauseCount = 0;
            BooleanPredicateClausesStep<?> fieldDataPred = f.bool();
            try {
              fieldDataPred =
                  fieldDataPred.should(
                      createQueryAnalyzingTerm(f, FieldNames.FIELD_DATA, term.text()));
              clauseCount++;
            } catch (RuntimeException e) {
              log.debug("Skipping '{}' not in scope: {}", FieldNames.FIELD_DATA, e.getMessage());
            }
            try {
              fieldDataPred =
                  fieldDataPred.should(
                      createQueryAnalyzingTerm(f, FieldNames.FLAT_FIELD_DATA, term.text()));
              clauseCount++;
            } catch (RuntimeException e) {
              log.debug(
                  "Skipping '{}' not in scope: {}", FieldNames.FLAT_FIELD_DATA, e.getMessage());
            }
            if (clauseCount > 0) {
              ql.add(fieldDataPred.minimumShouldMatchNumber(1).toPredicate());
            }
          } else {
            ql.add(createQueryAnalyzingTerm(f, term.field(), term.text()));
          }
        } catch (RuntimeException e) {
          log.debug("Skipping field '{}' not in scope: {}", term.field(), e.getMessage());
        }
      }
    }

    return ql;
  }

  /**
   * Create Lucene Query checking the term.
   *
   * @param qb
   * @param fssn
   * @param field
   * @param term
   * @return
   */
  private SearchPredicate createQueryAnalyzingTerm(
      SearchPredicateFactory f, String field, String term) {
    boolean isApiPartialMatchQuery =
        term.indexOf(WILDCARD_LUCENE_INDICATOR) != -1
            || term.indexOf(QUESTION_MARK_INDICATOR) != -1
            || term.indexOf(FUZZY_LUCENE_INDICATOR) != -1;
    if (!isApiPartialMatchQuery && field.equals(FieldNames.DOC_TAG)) {
      List<String> allTerms = new ArrayList<>();
      if (term.contains(LUCENE_TERM_DELIMITER)) {
        allTerms = Arrays.asList(term.split(LUCENE_TERM_DELIMITER));
      } else {
        allTerms.add(term);
      }
      StringBuilder matchingBuilder = new StringBuilder();
      for (String phrase : allTerms) {
        if (matchingBuilder.length() > 0) {
          matchingBuilder.append(" | ");
        }
        matchingBuilder.append("(\"").append(phrase).append("\")");
      }
      String matchingText = matchingBuilder.toString().trim();
      return f.simpleQueryString().field("docTag").matching(matchingText).toPredicate();
    }
    if (term.startsWith(SearchConstants.NATIVE_LUCENE_PREFIX)) {
      term = term.substring(SearchConstants.NATIVE_LUCENE_PREFIX.length());
      // Use Lucene's QueryParser for full native syntax support (negation, AND, OR, etc.)
      try {
        org.apache.lucene.queryparser.classic.QueryParser parser =
            new org.apache.lucene.queryparser.classic.QueryParser(
                field, new org.apache.lucene.analysis.standard.StandardAnalyzer());
        org.apache.lucene.search.Query luceneQuery = parser.parse(term);
        return ((org.hibernate.search.backend.lucene.search.predicate.dsl
                    .LuceneSearchPredicateFactory)
                f.extension(org.hibernate.search.backend.lucene.LuceneExtension.get()))
            .fromLuceneQuery(luceneQuery)
            .toPredicate();
      } catch (org.apache.lucene.queryparser.classic.ParseException e) {
        throw new SearchQueryParseException(e);
      }
    } else if (term.endsWith(FUZZY_LUCENE_INDICATOR)) {
      String fuzzyTerm = term.substring(0, term.length() - FUZZY_LUCENE_INDICATOR.length());
      return f.match()
          .field(field)
          .matching(fuzzyTerm)
          .fuzzy(FUZZY_MAX_EDIT_DISTANCE, FUZZY_PREFIX_LENGTH)
          .toPredicate();
    } else if (term.contains(WILDCARD_LUCENE_INDICATOR) || term.contains(QUESTION_MARK_INDICATOR)) {

      return f.wildcard().field(field).matching(term.toLowerCase()).toPredicate();

    } else if ((term.startsWith(PHRASE1_LUCENE_INDICATOR)
            && term.endsWith(PHRASE1_LUCENE_INDICATOR))
        || (term.startsWith(PHRASE2_LUCENE_INDICATOR) && term.endsWith(PHRASE2_LUCENE_INDICATOR))) {
      String sentence = term.substring(1, term.length() - 1);
      return f.phrase().field(field).matching(sentence).slop(SLOP).toPredicate();
    } else {
      StringTokenizer tk;
      if (term.indexOf(',') >= 0) {
        tk = new StringTokenizer(term, ",");
      } else {
        tk = new StringTokenizer(term);
      }
      if (tk.countTokens() > 1) {
        return andKeywords(f, new String[] {field}, tk);
      } else {
        return f.match().field(field).matching(term).toPredicate();
      }
    }
  }

  /**
   * Expands the field set to include FLAT_FIELD_DATA alongside FIELD_DATA when the latter is
   * present. This ensures description/globalId (indexed flat as fields_fieldData) and
   * EcatCommentItem content are found alongside embedded field data (fields.fieldData).
   */
  private String[] expandFieldDataFields(Set<String> termFields) {
    if (termFields.contains(FieldNames.FIELD_DATA)) {
      Set<String> expanded = new LinkedHashSet<>(termFields);
      expanded.add(FieldNames.FLAT_FIELD_DATA);
      return expanded.toArray(new String[0]);
    }
    return termFields.toArray(new String[0]);
  }

  /**
   * Create a Lucene Query used by searching multiple terms in a kind of field.
   *
   * @param qb
   * @param flds
   * @param tk
   * @return
   */
  protected SearchPredicate andKeywords(
      SearchPredicateFactory f, String flds[], StringTokenizer tk) {
    BooleanPredicateClausesStep<?> mustClauses = f.bool();
    while (tk.hasMoreTokens()) {
      mustClauses = mustClauses.must(f.match().fields(flds).matching(tk.nextToken().trim()));
    }
    return mustClauses.toPredicate();
  }
}
