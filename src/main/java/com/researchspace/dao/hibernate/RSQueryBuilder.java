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
import java.util.List;
import java.util.StringTokenizer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.exception.SearchException;
import org.hibernate.search.query.dsl.BooleanJunction;
import org.hibernate.search.query.dsl.MustJunction;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class responsible for creating the seqrch query to be executed in Lucene. */
class RSQueryBuilder {

  private static final float THRESHOLD = .5f;
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
   * @param fssn The Hibernate full text session the indexed fields to search over
   * @param cfg LuceneSrchConfig from the client
   * @param clazz The class whose index we're searching over.
   * @return
   */
  protected <T> Query getLuceneQuery(FullTextSession fssn, LuceneSrchCfg cfg, Class<T> clazz) {

    String srchTerm = "";
    if (cfg.getTermList().size() > 0) {
      srchTerm = cfg.getAllTerms().get(0).text();
    } else {
      throw new SearchQueryParseException(
          new Exception("A search term is missing in the search config object"));
    }

    QueryBuilder qb = fssn.getSearchFactory().buildQueryBuilder().forEntity(clazz).get();
    Query luceneQuery = null;

    try {
      switch (cfg.getSearchStrategy()) {
        case IFullTextSearcher.ALL_LUCENE_SEARCH_STRATEGY:
          luceneQuery = createShouldLuceneQuery(qb, fssn, cfg.getAllTerms());
          break;

        case IFullTextSearcher.ADVANCED_LUCENE_SEARCH_STRATEGY:
          if (cfg.getOperator().equals(SearchOperator.AND)) {
            luceneQuery = createMustLuceneQuery(qb, fssn, cfg.getAllTerms());
          } else {
            luceneQuery = createShouldLuceneQuery(qb, fssn, cfg.getAllTerms());
          }
          break;
        case IFullTextSearcher.SINGLE_LUCENE_SEARCH_STRATEGY:
          luceneQuery = createMustLuceneQuery(qb, fssn, cfg.getAllTerms());
          break;

        default:
          StringTokenizer tk = new StringTokenizer(srchTerm, ",");
          if (tk.countTokens() > 1) {
            luceneQuery = andKeywords(qb, cfg.getTermListFields().toArray(new String[0]), tk);
          } else {
            luceneQuery =
                qb.keyword()
                    .onFields(cfg.getTermListFields().toArray(new String[0]))
                    .matching(srchTerm)
                    .createQuery();
          }
      }

      luceneQuery = addUserFilterInQuery(cfg, qb, luceneQuery);
      luceneQuery = addRecordFilterInQuery(cfg, qb, luceneQuery);
      luceneQuery = addParentIdFilterInQuery(cfg, qb, luceneQuery);

    } catch (java.text.ParseException pe) {
      log.warn("Unable to create native lucene query", pe);
      luceneQuery = null;
    } catch (SearchException ex) {
      log.warn("Could not generate lucene query! : " + ex.toString());
      luceneQuery = null;
    }
    return luceneQuery;
  }

  /**
   * @param cfg
   * @param qb
   * @param luceneQuery
   * @return
   */
  private Query addRecordFilterInQuery(LuceneSrchCfg cfg, QueryBuilder qb, Query luceneQuery) {

    if (cfg.isRecordFilterListUsableInLucene()) {
      BooleanJunction<?> baseRecordFilter = qb.bool();
      for (BaseRecord bs : cfg.getRecordFilterList()) {
        Query q = qb.keyword().onField("id").matching(bs.getId()).createQuery();
        baseRecordFilter = baseRecordFilter.should(q);
      }
      BooleanJunction<?> recordsBooleanQuery = qb.bool();
      luceneQuery =
          recordsBooleanQuery.must(baseRecordFilter.createQuery()).must(luceneQuery).createQuery();
    }

    return luceneQuery;
  }

  private Query addParentIdFilterInQuery(LuceneSrchCfg cfg, QueryBuilder qb, Query luceneQuery) {
    if (cfg.getParentId() != null) {
      Query q =
          qb.keyword().onField(FieldNames.PARENT_ID).matching(cfg.getParentId()).createQuery();
      luceneQuery = qb.bool().must(q).must(luceneQuery).createQuery();
    }
    if (cfg.getParentTemplateId() != null) {
      Query q =
          qb.keyword()
              .onField(FieldNames.PARENT_TEMPLATE_ID)
              .matching(cfg.getParentTemplateId())
              .createQuery();
      luceneQuery = qb.bool().must(q).must(luceneQuery).createQuery();
    }
    if (cfg.getParentSampleId() != null) {
      Query q =
          qb.keyword()
              .onField(FieldNames.PARENT_SAMPLE_ID)
              .matching(cfg.getParentSampleId())
              .createQuery();
      luceneQuery = qb.bool().must(q).must(luceneQuery).createQuery();
    }
    return luceneQuery;
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
  private Query addUserFilterInQuery(LuceneSrchCfg cfg, QueryBuilder qb, Query luq) {
    if (cfg.isRestrictByUser()) {
      BooleanJunction<?> userFilter = qb.bool();
      for (String username : cfg.getUsernameFilterList()) {
        Query q = qb.keyword().onField("owner.username").matching(username).createQuery();
        userFilter = userFilter.should(q);
      }
      for (String sharedWith : cfg.getSharedWithFilterList()) {
        Query q = qb.keyword().onField("sharedWith").matching(sharedWith).createQuery();
        userFilter = userFilter.should(q);
      }
      BooleanJunction<?> allUsers = qb.bool();
      luq = allUsers.must(userFilter.createQuery()).must(luq).createQuery();
    }
    return luq;
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
  protected Query createMustLuceneQuery(QueryBuilder qb, FullTextSession fssn, List<Term> termList)
      throws java.text.ParseException {
    Query luq = null;
    MustJunction mj = null;
    List<Query> ql = getQueryList(qb, fssn, termList);
    for (int i = 0; i < ql.size(); i++) {
      if (i == 0) {
        mj = qb.bool().must(ql.get(i));
      } else {
        mj = mj.must(ql.get(i));
      }
    }

    luq = mj.createQuery();
    return luq;
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
  protected Query createShouldLuceneQuery(
      QueryBuilder qb, FullTextSession fssn, List<Term> termList) throws java.text.ParseException {
    Query luq;
    BooleanJunction<?> booleanJunction = null;
    List<Query> ql = getQueryList(qb, fssn, termList);
    for (int i = 0; i < ql.size(); i++) {
      if (i == 0) {
        booleanJunction = qb.bool().should(ql.get(i));
      } else {
        booleanJunction = booleanJunction.should(ql.get(i));
      }
    }

    luq = booleanJunction.createQuery();
    return luq;
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
  private List<Query> getQueryList(QueryBuilder qb, FullTextSession fssn, List<Term> termList)
      throws java.text.ParseException {
    List<Query> ql = new ArrayList<>();

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
        ql.add(qb.range().onField(term.field()).from(dateFrom).to(dateTo).createQuery());

      } else if (term.field().equals(FieldNames.TEMPLATE)) {
        /**
         * In case when the field is a template, we are indexing by name and OID so we need to add
         * those like so: some_conditions && ( templateOid = "whatever" || temapleteName =
         * "Whatever" )
         */
        BooleanJunction conjunction =
            qb.bool()
                .should(createQueryAnalyzingTerm(qb, fssn, "templateName", term.text()))
                .should(createQueryAnalyzingTerm(qb, fssn, "templateOid", term.text()));

        ql.add(conjunction.createQuery());

      } else if (term.field().equals("owner.username")) {
        BooleanJunction conjunction = qb.bool();
        for (String userName : term.text().split(",")) {
          Query q = qb.keyword().onField("owner.username").matching(userName).createQuery();
          conjunction = conjunction.should(q);
        }
        ql.add(conjunction.createQuery());

      } else {
        /** If the field term is fields.fieldData, docTag, name, formName, globalIdentifier */
        ql.add(createQueryAnalyzingTerm(qb, fssn, term.field(), term.text()));
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
  private Query createQueryAnalyzingTerm(
      QueryBuilder qb, FullTextSession fssn, String field, String term) {
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
        matchingBuilder.append("(\"" + phrase + "\") | ");
      }
      String matchingText = matchingBuilder.toString().trim();
      matchingText = matchingText.substring(0, matchingText.length() - 1);
      return qb.simpleQueryString().onField("docTag").matching(matchingText).createQuery();
    }
    if (term.startsWith(SearchConstants.NATIVE_LUCENE_PREFIX)) {
      term = term.substring(2);
      org.apache.lucene.queryparser.classic.QueryParser parser =
          new QueryParser(field, fssn.getSearchFactory().getAnalyzer("structureAnalyzer"));
      try {
        return parser.parse(term);
      } catch (ParseException e) {
        log.warn("Unable to create native query");
        return null;
      }
    } else if (term.endsWith(FUZZY_LUCENE_INDICATOR)) {
      return qb.keyword()
          .fuzzy()
          .withThreshold(THRESHOLD)
          .withPrefixLength(1)
          .onField(field)
          .matching(term)
          .createQuery();
    } else if (term.contains(WILDCARD_LUCENE_INDICATOR) || term.contains(QUESTION_MARK_INDICATOR)) {

      return qb.keyword().wildcard().onField(field).matching(term.toLowerCase()).createQuery();

    } else if ((term.startsWith(PHRASE1_LUCENE_INDICATOR)
            && term.endsWith(PHRASE1_LUCENE_INDICATOR))
        || (term.startsWith(PHRASE2_LUCENE_INDICATOR) && term.endsWith(PHRASE2_LUCENE_INDICATOR))) {
      return qb.phrase().withSlop(SLOP).onField(field).sentence(term).createQuery();
    } else {
      StringTokenizer tk;
      if (term.indexOf(',') >= 0) {
        tk = new StringTokenizer(term, ",");
      } else {
        tk = new StringTokenizer(term);
      }
      if (tk.countTokens() > 1) {
        return andKeywords(qb, new String[] {field}, tk);
      } else {
        return qb.keyword().onField(field).matching(term).createQuery();
      }
    }
  }

  /**
   * Create a Lucene Query used by searching multiple terms in a kind of field.
   *
   * @param qb
   * @param flds
   * @param tk
   * @return
   */
  protected Query andKeywords(QueryBuilder qb, String flds[], StringTokenizer tk) {
    Query luq = null;
    MustJunction mj =
        qb.bool().must(qb.keyword().onFields(flds).matching(tk.nextToken().trim()).createQuery());
    while (tk.hasMoreTokens()) {
      mj = mj.must(qb.keyword().onFields(flds).matching(tk.nextToken().trim()).createQuery());
    }
    luq = mj.createQuery();
    return luq;
  }
}
