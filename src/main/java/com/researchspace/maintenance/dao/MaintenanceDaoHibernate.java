package com.researchspace.maintenance.dao;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.ResourceRequest.Page;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository("maintenanceDao")
public class MaintenanceDaoHibernate extends GenericDaoHibernate<ScheduledMaintenance, Long>
    implements MaintenanceDao {

  private static final String ALIAS = "maintenance";
  private static final CollectionQueryExecutor<ScheduledMaintenance> COLLECTION_QUERY =
      new CollectionQueryExecutor<>(
          ScheduledMaintenance.class, ApiV2MaintenanceResource.DESCRIPTION, ALIAS);

  @Autowired private CriteriaBuilderFactory criteriaBuilderFactory;

  public MaintenanceDaoHibernate() {
    super(ScheduledMaintenance.class);
  }

  public MaintenanceDaoHibernate(Class<ScheduledMaintenance> persistentClass) {
    super(persistentClass);
  }

  @Override
  public Optional<ScheduledMaintenance> getNextScheduledMaintenance() {
    return Optional.ofNullable(getResources(nextMaintenanceRequest()).getFirstResult());
  }

  @Override
  public List<ScheduledMaintenance> getAllFutureMaintenances() {
    return getAllFutureMaintenanceOrderedByDateAsc();
  }

  @Override
  public ISearchResults<ScheduledMaintenance> getResources(ResourceRequest request) {
    return COLLECTION_QUERY.page(criteriaBuilderFactory, getSession(), request);
  }

  @Override
  public long countResources(ResourceRequest request) {
    return COLLECTION_QUERY.count(criteriaBuilderFactory, getSession(), request);
  }

  @Override
  public List<ScheduledMaintenance> getResources(ResourceRequest request, int limit) {
    return COLLECTION_QUERY.listById(criteriaBuilderFactory, getSession(), request, limit);
  }

  private List<ScheduledMaintenance> getAllFutureMaintenanceOrderedByDateAsc() {
    return getSession()
        .createQuery(
            "from ScheduledMaintenance where endDate > :now order by startDate asc",
            ScheduledMaintenance.class)
        .setParameter("now", new Date())
        .list();
  }

  @Override
  public List<ScheduledMaintenance> getOldMaintenances() {
    return getSession()
        .createQuery(
            "from ScheduledMaintenance where endDate <= :now order by startDate asc",
            ScheduledMaintenance.class)
        .setParameter("now", new Date())
        .list();
  }

  private static ResourceRequest nextMaintenanceRequest() {
    return new ResourceRequest(
        new FilterExpression.Comparison(
            "endDate", Operator.GREATER_THAN, List.of(new Date()), false),
        ApiV2MaintenanceResource.DESCRIPTION.defaultSort(),
        new Page(1, 1),
        FieldSelection.all(),
        IncludeTree.empty());
  }
}
