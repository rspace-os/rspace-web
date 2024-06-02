package com.researchspace.dao.hibernate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserDeletionQueryBuilder {

  private Logger log = LoggerFactory.getLogger(UserDeletionQueryBuilder.class);

  protected String generateDeleteByRecordOwnerQuery(
      String tableToDel, String tableToDelRecordIdColumn) {

    String deleteQuery =
        String.format(
            "delete %1$s from %1$s left join BaseRecord br "
                + "on %1$s.%2$s = br.id where br.owner_id=:id",
            tableToDel, tableToDelRecordIdColumn);

    log.debug("generated sql: " + deleteQuery);
    return deleteQuery;
  }

  protected String generateDeleteByRecordOwnerQuery(
      String tableToDel,
      String tableToDelJoinColumn,
      String joinTable,
      String joinTableJoinColumn,
      String joinTableRecordIdColumn) {

    String deleteQuery =
        String.format(
            "delete %1$s from %1$s left join %3$s on %1$s.%2$s = %3$s.%4$s "
                + "left join BaseRecord br on %3$s.%5$s = br.id where br.owner_id=:id",
            tableToDel,
            tableToDelJoinColumn,
            joinTable,
            joinTableJoinColumn,
            joinTableRecordIdColumn);

    log.debug("generated sql with two join tables: " + deleteQuery);
    return deleteQuery;
  }
}
