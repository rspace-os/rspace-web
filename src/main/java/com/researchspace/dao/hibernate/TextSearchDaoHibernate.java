package com.researchspace.dao.hibernate;

import com.axiope.search.IFullTextSearcher;
import com.axiope.search.InventorySearchConfig;
import com.axiope.search.SearchConfig;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.TextSearchDao;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import java.io.IOException;
import java.util.List;
import org.apache.lucene.search.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** Implementation of textSearchDao should be used with createTransction/closeSession first */
@Repository("textSearchDao")
public class TextSearchDaoHibernate implements TextSearchDao {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final int DEFAULT_BATCH_SIZE = 100;

  private @Autowired SessionFactory sessionFactory;
  private @Autowired IFullTextSearcher fullTextSearcher;
  private @Autowired BaseRecordAdaptable baseRecordAdapter;

  public TextSearchDaoHibernate() {}

  @Override
  public void indexText() throws InterruptedException {
    Session ssn = sessionFactory.getCurrentSession();
    if (ssn == null) {
      throw new IllegalStateException("Error: sessionFactory cannot create session");
    }

    FullTextSession fullTxt = Search.getFullTextSession(ssn);
    log.info("Starting to rebuild text index.");
    fullTxt.createIndexer().batchSizeToLoadObjects(DEFAULT_BATCH_SIZE).startAndWait();
    log.info("Finished rebuilding text index.");
  }

  @Override
  public ISearchResults<BaseRecord> getSearchedElnResults(SearchConfig srchCfg) throws IOException {
    fullTextSearcher.setBaseRecordAdaptable(baseRecordAdapter);
    if (srchCfg.getSearchStrategy() == IFullTextSearcher.ADVANCED_LUCENE_SEARCH_STRATEGY) {
      srchCfg.setAdvancedSearch(true);
    }
    return fullTextSearcher.getSearchedElnRecords(srchCfg);
  }

  @Override
  public ISearchResults<InventoryRecord> getSearchedInventoryResults(
      InventorySearchConfig searchConfig) {
    return fullTextSearcher.getSearchedInventoryRecords(searchConfig);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public List searchText(String flds[], String match, Class<?> persistentClass) {
    Session ssn = sessionFactory.getCurrentSession();

    FullTextSession fullTxt = Search.getFullTextSession(ssn);
    log.debug("Transaction start");
    QueryBuilder qb =
        fullTxt.getSearchFactory().buildQueryBuilder().forEntity(persistentClass).get();
    log.debug("qb: " + qb.toString());
    org.apache.lucene.search.Query query =
        (Query) (qb.keyword().onFields(flds).matching(match).createQuery());
    log.debug("query: " + query.toString());

    // has to wrapp into HQL
    org.hibernate.query.Query hibQuery = fullTxt.createFullTextQuery(query, persistentClass);
    List results = hibQuery.list();

    log.debug("Transaction commit");
    return results;
  }
}
