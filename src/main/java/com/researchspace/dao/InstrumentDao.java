package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.ResourceSlice;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentReadSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** For DAO operations specific to Inventory {@link Instrument} (not templates). */
public interface InstrumentDao extends InstrumentEntityDao<Instrument> {

  /**
   * Returns a paginated list of instruments visible to the given user, excluding instrument
   * templates. When {@code searchTerm} is non-blank, results are filtered to records whose name
   * contains the term (case-insensitive).
   */
  ISearchResults<Instrument> getInstrumentsForUser(
      PaginationCriteria<Instrument> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      String searchTerm,
      User user);

  /**
   * Returns one REST API v2 collection page of the instruments the user may read.
   *
   * <p>The filter, the sort, the permission rules, and the page are all applied by the database, so
   * the page and the total agree and no caller has to read the whole collection.
   */
  default ResourcePage<Instrument> getReadableResources(
      ResourceRequest request, AccessResult access) {
    return getReadableResources(request, access, RelationshipReadAccess.none());
  }

  /** Applies registered target visibility while compiling relationship selectors. */
  ResourcePage<Instrument> getReadableResources(
      ResourceRequest request, AccessResult access, RelationshipReadAccess relationships);

  /** Returns the same authorized page as scalar rows, without Inventory aggregate hydration. */
  default ResourcePage<InstrumentReadSummary> getReadableResourceSummaries(
      ResourceRequest request, AccessResult access) {
    return getReadableResourceSummaries(request, access, RelationshipReadAccess.none());
  }

  /** Applies registered target visibility while compiling relationship selectors. */
  ResourcePage<InstrumentReadSummary> getReadableResourceSummaries(
      ResourceRequest request, AccessResult access, RelationshipReadAccess relationships);

  /** Reads a count-free scalar window under the ordinary read policy. */
  default ResourceSlice<InstrumentReadSummary> getReadableResourceSummarySlice(
      ResourceRequest request, AccessResult access) {
    return getReadableResourceSummarySlice(request, access, RelationshipReadAccess.none());
  }

  /** Applies registered target visibility while compiling relationship selectors. */
  ResourceSlice<InstrumentReadSummary> getReadableResourceSummarySlice(
      ResourceRequest request, AccessResult access, RelationshipReadAccess relationships);

  /** Counts the instruments the user may read that match a REST API v2 collection request. */
  default long countReadableResources(ResourceRequest request, AccessResult access) {
    return countReadableResources(request, access, RelationshipReadAccess.none());
  }

  /** Applies registered target visibility while compiling relationship selectors. */
  long countReadableResources(
      ResourceRequest request, AccessResult access, RelationshipReadAccess relationships);

  /** Returns readable active instrument scalars for relationship expansion. */
  Map<Long, InstrumentReadSummary> getReadableSummaries(Set<Long> instrumentIds, User user);

  /** Returns names for the requested instruments without applying current read permissions. */
  Map<Long, String> getNamesByIds(Set<Long> instrumentIds);

  /**
   * Returns active Instrument identifiers stored directly in readable Containers whose database
   * type matches the supplied global-ID prefix.
   */
  Set<Long> findByReadableImmediateParentIds(
      Set<Long> containerIds, Set<Long> workbenchIds, User caller);

  /**
   * Locks and reloads one concrete Instrument, without locking its eager associations. Callers
   * acquiring several instruments must lock them in id order before locking owners.
   */
  Optional<Instrument> lockById(Long id);

  /**
   * Locks and checks the current group memberships that allow {@code subject} to transfer an
   * instrument owned by {@code owner}. Call this only after locking the instrument.
   */
  boolean hasLockedTransferAuthority(User subject, User owner);

  /** Returns all instruments whose image or thumbnail is the given file property. */
  List<Instrument> getAllUsingImage(FileProperty fileProperty);

  /** Returns all instruments with the given name owned by the given user. */
  List<Instrument> findInstrumentsByName(String name, User user);

  /**
   * Returns a paginated list of instruments visible to the given user that were created from the
   * given template.
   */
  ISearchResults<Instrument> getInstrumentsForTemplate(
      PaginationCriteria<Instrument> pgCrit,
      Long templateId,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user);

  /**
   * Returns instruments owned by the given user that were created from the given template and whose
   * tracked template version is older than {@code version}.
   */
  List<Instrument> getInstrumentsLinkingOlderTemplateVersionForUser(
      Long templateId, Long version, User user);
}
