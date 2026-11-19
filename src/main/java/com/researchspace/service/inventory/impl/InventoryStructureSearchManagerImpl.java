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
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.InventoryStructureHit;
import com.researchspace.service.inventory.InventoryStructureSearchManager;
import com.researchspace.service.inventory.SampleApiManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service("inventoryStructureSearchManager")
public class InventoryStructureSearchManagerImpl implements InventoryStructureSearchManager {

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

  /**
   * Most structure hits a search loads and returns, the same as the text search's Lucene limit.
   * Every hit is loaded and permission-checked before paging, so the limit bounds that work.
   */
  @Setter private int maxStructureHits = 1000;

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
      List<Long> chemIds,
      String textQuery,
      InventorySearchType resultType,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      Boolean bookable,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user) {

    Set<Long> bookableIds =
        bookable == null
            ? Set.of()
            : bookableInstrumentLookup.findReadableBookableInstrumentIds(user);
    List<InventoryRecord> found =
        readableStructureHits(chemIds, user)
            .filter(rec -> isMatchingDeletedOption(rec, deletedOption))
            .filter(rec -> isMatchingType(rec, resultType))
            .filter(
                rec -> StringUtils.isBlank(ownedBy) || ownedBy.equals(rec.getOwner().getUsername()))
            .filter(rec -> bookable == null || bookable == isBookable(rec, bookableIds))
            .limit(maxStructureHits + 1L)
            .toList();
    boolean limitReached = found.size() > maxStructureHits;
    List<InventoryRecord> hits = limitReached ? found.subList(0, maxStructureHits) : found;

    ApiInventorySearchResult result;
    if (StringUtils.isBlank(textQuery)) {
      result =
          sampleApiManager.sortRepaginateConvertToApiInventorySearchResult(
              pgCrit, new ArrayList<>(hits), user);
    } else if (hits.isEmpty()) {
      result = ApiInventorySearchResult.emptyResult();
    } else {
      List<String> hitGlobalIds = hits.stream().map(InventoryRecord::getGlobalIdentifier).toList();
      result =
          searchManager.searchInventoryWithSimpleQuery(
              textQuery,
              resultType,
              null,
              ownedBy,
              deletedOption,
              bookable,
              hitGlobalIds,
              pgCrit,
              user);
    }
    if (limitReached) {
      log.info("Inventory structure search: stopped at {} hits", maxStructureHits);
      result.setStructureSearchLimitReached(true);
    }
    return result;
  }

  @Override
  public List<InventoryStructureHit> findReadableStructureHits(List<Long> chemIds, User user) {
    return readableStructureHits(chemIds, user)
        .filter(Predicate.not(InventoryRecord::isDeleted))
        .limit(maxStructureHits)
        .map(
            rec ->
                new InventoryStructureHit(
                    rec.getGlobalIdentifier(),
                    rec.getName(),
                    rec.getType(),
                    rec.getOwner().getFullName(),
                    rec.getModificationDate()))
        .toList();
  }

  /**
   * Structure hits the user can read, without the records no Inventory search lists. Records are
   * loaded one batch of Gallery files at a time as the stream is consumed, so a limited stream
   * stops loading once it has enough.
   */
  private Stream<InventoryRecord> readableStructureHits(List<Long> chemIds, User user) {
    return findStructureHits(chemIds)
        .filter(rec -> invPermissions.canUserReadInventoryRecord(rec, user))
        .filter(Predicate.not(InventoryStructureSearchManagerImpl::isSubSampleOfTemplate))
        .filter(Predicate.not(InventoryStructureSearchManagerImpl::isWorkbench));
  }

  /** The records carrying a Gallery chemistry file among the given chem element ids, deduped. */
  private Stream<InventoryRecord> findStructureHits(List<Long> chemIds) {
    List<Long> galleryFileIds =
        batches(chemIds)
            .flatMap(batch -> rsChemElementDao.getGalleryChemFileIds(batch).stream())
            .distinct()
            .toList();
    Set<String> seenGlobalIds = new HashSet<>();
    // record attachments resolve their own record; attachment-field files resolve to null there and
    // are reached through their field instead
    return batches(galleryFileIds)
        .flatMap(
            batch ->
                Stream.concat(
                    inventoryFileDao.findByMediaFileIds(batch).stream()
                        .map(InventoryFile::getInventoryRecord),
                    inventoryFileDao.findAttachmentFieldsByMediaFileIds(batch).stream()
                        .map(InventoryAttachmentField::getInventoryRecord)))
        .filter(Objects::nonNull)
        .filter(rec -> seenGlobalIds.add(rec.getGlobalIdentifier()));
  }

  private Stream<List<Long>> batches(List<Long> ids) {
    return IntStream.iterate(0, from -> from < ids.size(), from -> from + idsBatchSize)
        .mapToObj(from -> ids.subList(from, Math.min(from + idsBatchSize, ids.size())));
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
