package com.researchspace.service.inventory.impl;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.axiope.search.SearchManager;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.booking.service.BookableInstrumentLookup;
import com.researchspace.dao.InventoryFileDao;
import com.researchspace.dao.RSChemElementDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecord.InventoryRecordType;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.InventoryStructureSearchManager;
import com.researchspace.service.inventory.SampleApiManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service("inventoryStructureSearchManager")
public class InventoryStructureSearchManagerImpl implements InventoryStructureSearchManager {

  private @Autowired ChemistryProvider chemistryProvider;
  private @Autowired RSChemElementDao rsChemElementDao;
  private @Autowired InventoryFileDao inventoryFileDao;
  private @Autowired InventoryPermissionUtils invPermissions;
  private @Autowired BookableInstrumentLookup bookableInstrumentLookup;
  private @Autowired SampleApiManager sampleApiManager;
  private @Autowired SearchManager searchManager;
  private @Autowired IPropertyHolder properties;
  private @Autowired IntegrationsHandler integrationsHandler;

  static final String CHEMISTRY_INTEGRATION = "CHEMISTRY";
  static final String INDIGO_PROVIDER = "indigo";

  /** Same batch size the Workspace structure search uses for chem id queries. */
  @Value("${chemistry.search.chemIdsDbQuery.pageSize:1000}")
  @Setter
  private Integer idsBatchSize = 1000;

  @Override
  public boolean isStructureSearchAvailable(User user) {
    if (!INDIGO_PROVIDER.equals(properties.getChemistryProvider())) {
      return false;
    }
    IntegrationInfo chemistry = integrationsHandler.getIntegration(user, CHEMISTRY_INTEGRATION);
    return chemistry.isAvailable() && chemistry.isEnabled();
  }

  @Override
  public ApiInventorySearchResult searchByStructure(
      String smiles,
      String chemSearchType,
      String textQuery,
      InventorySearchType resultType,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      Boolean bookable,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user) {

    List<Long> chemIds = chemistryProvider.search(smiles, chemSearchType).getChemicalHits();
    log.info("Inventory structure search: chemistry service reported {} hits", chemIds.size());

    Set<Long> bookableIds =
        bookable == null
            ? Set.of()
            : bookableInstrumentLookup.findReadableBookableInstrumentIds(user);
    List<InventoryRecord> hits =
        findStructureHits(chemIds).stream()
            .filter(rec -> invPermissions.canUserReadInventoryRecord(rec, user))
            .filter(rec -> isMatchingDeletedOption(rec, deletedOption))
            .filter(rec -> isMatchingType(rec, resultType))
            .filter(
                rec -> StringUtils.isBlank(ownedBy) || ownedBy.equals(rec.getOwner().getUsername()))
            .filter(rec -> bookable == null || bookable == isBookable(rec, bookableIds))
            .filter(Predicate.not(InventoryStructureSearchManagerImpl::isSubSampleOfTemplate))
            .filter(Predicate.not(InventoryStructureSearchManagerImpl::isWorkbench))
            .toList();

    if (StringUtils.isBlank(textQuery)) {
      return sampleApiManager.sortRepaginateConvertToApiInventorySearchResult(
          pgCrit, new ArrayList<>(hits), user);
    }
    if (hits.isEmpty()) {
      return ApiInventorySearchResult.emptyResult();
    }
    List<String> hitGlobalIds = hits.stream().map(InventoryRecord::getGlobalIdentifier).toList();
    return searchManager.searchInventoryWithSimpleQuery(
        textQuery, resultType, null, ownedBy, deletedOption, bookable, hitGlobalIds, pgCrit, user);
  }

  /** The records carrying a Gallery chemistry file among the given chem element ids, deduped. */
  private Collection<InventoryRecord> findStructureHits(List<Long> chemIds) {
    Set<Long> galleryFileIds = new LinkedHashSet<>();
    forEachBatch(
        chemIds, batch -> galleryFileIds.addAll(rsChemElementDao.getGalleryChemFileIds(batch)));

    Map<String, InventoryRecord> recordsByGlobalId = new LinkedHashMap<>();
    forEachBatch(
        new ArrayList<>(galleryFileIds),
        batch -> {
          // record attachments resolve their own record; attachment-field files resolve to null
          // here and are reached through their field instead
          for (InventoryFile file : inventoryFileDao.findByMediaFileIds(batch)) {
            addRecord(recordsByGlobalId, file.getInventoryRecord());
          }
          for (InventoryAttachmentField field :
              inventoryFileDao.findAttachmentFieldsByMediaFileIds(batch)) {
            addRecord(recordsByGlobalId, field.getInventoryRecord());
          }
        });
    return recordsByGlobalId.values();
  }

  private void forEachBatch(List<Long> ids, Consumer<List<Long>> action) {
    for (int from = 0; from < ids.size(); from += idsBatchSize) {
      action.accept(ids.subList(from, Math.min(from + idsBatchSize, ids.size())));
    }
  }

  private static void addRecord(Map<String, InventoryRecord> records, InventoryRecord record) {
    if (record != null) {
      records.putIfAbsent(record.getGlobalIdentifier(), record);
    }
  }

  private static boolean isMatchingDeletedOption(
      InventoryRecord record, InventorySearchDeletedOption option) {
    if (option == null) {
      return !record.isDeleted();
    }
    return switch (option) {
      case DELETED_ONLY -> record.isDeleted();
      case EXCLUDE -> !record.isDeleted();
      case INCLUDE -> true;
    };
  }

  private static boolean isMatchingType(InventoryRecord record, InventorySearchType type) {
    return type == null
        || type == InventorySearchType.ALL
        || record.getType() == InventoryRecordType.valueOf(type.name());
  }

  private static boolean isBookable(InventoryRecord record, Set<Long> bookableInstrumentIds) {
    return record.isInstrument() && bookableInstrumentIds.contains(record.getId());
  }

  private static boolean isSubSampleOfTemplate(InventoryRecord record) {
    return record.isSubSample() && ((SubSample) record).getSample().isTemplate();
  }

  private static boolean isWorkbench(InventoryRecord record) {
    return record.isContainer() && ((Container) record).isWorkbench();
  }
}
