package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.archive.ExportScope;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.SampleDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InventoryExportManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.ListOfMaterialsApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.service.inventory.csvexport.CsvContainerExporter;
import com.researchspace.service.inventory.csvexport.CsvContentToExport;
import com.researchspace.service.inventory.csvexport.CsvExportMode;
import com.researchspace.service.inventory.csvexport.CsvListOfMaterialsExporter;
import com.researchspace.service.inventory.csvexport.CsvSampleExporter;
import com.researchspace.service.inventory.csvexport.CsvSampleTemplateExporter;
import com.researchspace.service.inventory.csvexport.CsvSubSampleExporter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.NotFoundException;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** To deal with export into CSV. */
@Slf4j
@Setter
@Service("inventoryExportManager")
public class InventoryExportManagerImpl implements InventoryExportManager {

  @Autowired private ContainerApiManager containerManager;

  @Autowired private ContainerDao containerDao;

  @Autowired private SubSampleApiManager subSampleManager;

  @Autowired private SampleApiManager sampleManager;

  @Autowired private SampleDao sampleDao;

  @Autowired private CsvContainerExporter csvContainerExporter;

  @Autowired private CsvSampleExporter csvSampleExporter;

  @Autowired private CsvSampleTemplateExporter csvSampleTemplateExporter;

  @Autowired private CsvSubSampleExporter csvSubSampleExporter;

  @Autowired private CsvListOfMaterialsExporter csvLomExporter;

  @Autowired private ListOfMaterialsApiManager listOfMaterialsMgr;

  @Autowired protected InventoryPermissionUtils invPermissions;

  @Data
  private static class ItemsToExport {
    private final Set<Container> containersToExport;
    private final Set<Sample> samplesToExport;
    private final Set<Sample> templatesToExport;
    private final Set<SubSample> subSamplesToExport;
    private final Set<ListOfMaterials> lomsToExport;
  }

  @Override
  public CsvContentToExport exportSelectedItemsAsCsvContent(
      List<GlobalIdentifier> globalIdsToExport,
      CsvExportMode exportMode,
      boolean includeSampleContent,
      boolean includeContainerContent,
      User user)
      throws IOException {

    Set<Container> containersToExport = new LinkedHashSet<>();
    Set<Sample> samplesToExport = new LinkedHashSet<>();
    Set<Sample> templatesToExport = new LinkedHashSet<>();
    Set<SubSample> subSamplesToExport = new LinkedHashSet<>();
    Set<ListOfMaterials> lomsToExport = new LinkedHashSet<>();
    ItemsToExport itemsToExport =
        new ItemsToExport(
            containersToExport,
            samplesToExport,
            templatesToExport,
            subSamplesToExport,
            lomsToExport);

    if (globalIdsToExport != null) {
      List<GlobalIdentifier> uniqueGlobalIds =
          new ArrayList<>(new LinkedHashSet<>(globalIdsToExport));
      prevalidateGlobalIdsToExport(uniqueGlobalIds, user);

      for (GlobalIdentifier globalId : uniqueGlobalIds) {
        findItemByGlobalIdAndPutIntoSetToExport(
            globalId,
            containersToExport,
            samplesToExport,
            templatesToExport,
            subSamplesToExport,
            includeSampleContent,
            user);

        // for list of materials export the list and also all the items
        if (globalId.getPrefix().equals(GlobalIdPrefix.LM)) {
          ListOfMaterials lom = listOfMaterialsMgr.getIfExists(globalId.getDbId());
          lomsToExport.add(lom);

          for (MaterialUsage mu : lom.getMaterials()) {
            findItemByGlobalIdAndPutIntoSetToExport(
                mu.getInventoryRecord().getOid(),
                containersToExport,
                samplesToExport,
                templatesToExport,
                subSamplesToExport,
                includeSampleContent,
                user);
          }
        }
      }
    }

    if (includeContainerContent) {
      addAllContainerContentToExportSets(containersToExport, subSamplesToExport, user);
    }

    return getCsvContentForExportedItems(itemsToExport, ExportScope.SELECTION, exportMode, user);
  }

  private void findItemByGlobalIdAndPutIntoSetToExport(
      GlobalIdentifier globalId,
      Set<Container> containersToExport,
      Set<Sample> samplesToExport,
      Set<Sample> templatesToExport,
      Set<SubSample> subSamplesToExport,
      boolean includeSampleContent,
      User user) {

    if (globalId.getPrefix().equals(GlobalIdPrefix.SS)) {
      subSamplesToExport.add(subSampleManager.assertUserCanReadSubSample(globalId.getDbId(), user));
    } else if (globalId.getPrefix().equals(GlobalIdPrefix.SA)) {
      Sample sample = sampleManager.assertUserCanReadSample(globalId.getDbId(), user);
      samplesToExport.add(sample);
      if (includeSampleContent) {
        subSamplesToExport.addAll(sample.getActiveSubSamples());
      }
    } else if (globalId.getPrefix().equals(GlobalIdPrefix.IT)) {
      Sample template = sampleManager.assertUserCanReadSample(globalId.getDbId(), user);
      templatesToExport.add(template);
    } else if (globalId.getPrefix().equals(GlobalIdPrefix.IC)) {
      Container container = containerManager.assertUserCanReadContainer(globalId.getDbId(), user);
      containersToExport.add(container);
    }
  }

  private void addAllContainerContentToExportSets(
      Set<Container> containersToExport, Set<SubSample> subSamplesToExport, User user) {

    Deque<Container> containersToProcess = new ArrayDeque<>(containersToExport);
    while (!containersToProcess.isEmpty()) {
      Container container = containersToProcess.removeFirst();
      for (Container subContainer : container.getStoredContainers()) {
        if (!containersToExport.contains(subContainer)) {
          if (invPermissions.canUserReadInventoryRecord(subContainer, user)) {
            containersToExport.add(subContainer);
            containersToProcess.addLast(subContainer);
          }
        }
      }
      for (SubSample subSample : container.getStoredSubSamples()) {
        if (invPermissions.canUserReadInventoryRecord(subSample, user)) {
          subSamplesToExport.add(subSample);
        }
      }
    }
  }

  private void prevalidateGlobalIdsToExport(List<GlobalIdentifier> globalIds, User currentUser) {
    List<String> unexportableGlobalIds = new ArrayList<>();
    for (GlobalIdentifier globalId : globalIds) {
      boolean canRead = false;
      try {
        canRead =
            isReadableListOfMaterials(globalId, currentUser)
                || invPermissions.canUserReadInventoryRecord(globalId, currentUser);
      } catch (NotFoundException nfe) {
      }

      if (!canRead) {
        unexportableGlobalIds.add(globalId.getIdString());
      }
    }
    if (!unexportableGlobalIds.isEmpty()) {
      String msg =
          String.format(
              "Cannot export items with global id [%s] - items not found, or no permission",
              StringUtils.join(unexportableGlobalIds, ", "));
      throw new IllegalArgumentException(msg);
    }
  }

  private boolean isReadableListOfMaterials(GlobalIdentifier globalId, User user) {
    if (!GlobalIdPrefix.LM.equals(globalId.getPrefix())) {
      return false;
    }
    ApiListOfMaterials lom = listOfMaterialsMgr.getListOfMaterialsById(globalId.getDbId(), user);
    return listOfMaterialsMgr.canUserAccessApiLom(lom.getElnFieldId(), user, PermissionType.READ);
  }

  @Override
  public CsvContentToExport exportUserItemsAsCsvContent(
      List<String> usersToExport,
      CsvExportMode exportMode,
      boolean includeContainerContent,
      User user)
      throws IOException {
    Set<Container> containersToExport = new LinkedHashSet<>();
    Set<Sample> samplesToExport = new LinkedHashSet<>();
    Set<SubSample> subSamplesToExport = new LinkedHashSet<>();
    Set<Sample> templatesToExport = new LinkedHashSet<>();

    if (usersToExport != null) {
      List<String> uniqueUsernames = new ArrayList<>(new LinkedHashSet<>(usersToExport));
      prevalidateUsernamesToExport(uniqueUsernames, user);

      PaginationCriteria<Container> containerPgCrit =
          PaginationCriteria.createDefaultForClass(Container.class);
      containerPgCrit.setResultsPerPage(Integer.MAX_VALUE);
      PaginationCriteria<Sample> samplePgCrit =
          PaginationCriteria.createDefaultForClass(Sample.class);
      samplePgCrit.setResultsPerPage(Integer.MAX_VALUE);
      for (String username : uniqueUsernames) {
        ISearchResults<Container> dbContainers =
            containerDao.getAllContainersForUser(containerPgCrit, username, null, user);
        containersToExport.addAll(dbContainers.getResults());
        ISearchResults<Sample> dbSamples =
            sampleDao.getSamplesForUser(samplePgCrit, null, username, null, user);
        samplesToExport.addAll(dbSamples.getResults());
        for (Sample sample : samplesToExport) {
          subSamplesToExport.addAll(sample.getActiveSubSamples());
        }

        if (includeContainerContent) {
          addAllContainerContentToExportSets(containersToExport, subSamplesToExport, user);
        }
        ISearchResults<Sample> dbTemplates =
            sampleDao.getTemplatesForUser(samplePgCrit, username, null, user);
        templatesToExport.addAll(dbTemplates.getResults());
      }
    }

    return getCsvContentForExportedItems(
        new ItemsToExport(
            containersToExport,
            samplesToExport,
            templatesToExport,
            subSamplesToExport,
            new HashSet<>()),
        ExportScope.USER,
        exportMode,
        user);
  }

  @NotNull
  private CsvContentToExport getCsvContentForExportedItems(
      ItemsToExport itemsToExport, ExportScope exportScope, CsvExportMode exportMode, User user)
      throws IOException {

    CsvContentToExport contentToExport = new CsvContentToExport();

    String lomData =
        csvLomExporter
            .getCsvFragmentForListsOfMaterials(List.copyOf(itemsToExport.getLomsToExport()))
            .toString();
    if (StringUtils.isNotBlank(lomData)) {
      String lomFragment =
          csvLomExporter.getCsvCommentFragmentForLom(exportScope, exportMode, user) + lomData;
      contentToExport.setListsOfMaterials(lomFragment);
    }

    String containerData =
        csvContainerExporter
            .getCsvFragmentForContainers(
                List.copyOf(itemsToExport.getContainersToExport()), exportMode)
            .toString();
    if (StringUtils.isNotBlank(containerData)) {
      String containerFragment =
          csvContainerExporter.getCsvCommentFragmentForContainers(exportScope, exportMode, user)
              + containerData;
      contentToExport.setContainers(containerFragment);
    }

    String sampleData =
        csvSampleExporter
            .getCsvFragmentForSamples(List.copyOf(itemsToExport.getSamplesToExport()), exportMode)
            .toString();
    if (StringUtils.isNotBlank(sampleData)) {
      String sampleFragment =
          csvSampleExporter.getCsvCommentFragmentForSamples(exportScope, exportMode, user)
              + sampleData;
      contentToExport.setSamples(sampleFragment);
    }

    String subSampleData =
        csvSubSampleExporter
            .getCsvFragmentForSubSamples(
                List.copyOf(itemsToExport.getSubSamplesToExport()), exportMode)
            .toString();
    if (StringUtils.isNotBlank(subSampleData)) {
      String subSampleFragment =
          csvSubSampleExporter.getCsvCommentFragmentForSubSamples(exportScope, exportMode, user)
              + subSampleData;
      contentToExport.setSubSamples(subSampleFragment);
    }

    String templateData =
        csvSampleTemplateExporter
            .getCsvFragmentForSamples(List.copyOf(itemsToExport.getTemplatesToExport()), exportMode)
            .toString();
    if (StringUtils.isNotBlank(templateData)) {
      String templateFragment =
          csvSampleTemplateExporter.getCsvCommentFragmentForSamples(exportScope, exportMode, user)
              + templateData;
      contentToExport.setSampleTemplates(templateFragment);
    }

    return contentToExport;
  }

  protected String generateExportMetadataComment(
      ExportScope selection, boolean includeSampleContent, CsvExportMode exportMode, User user) {
    return null;
  }

  private void prevalidateUsernamesToExport(List<String> usersToExport, User currentUser) {
    List<String> unexportableUsers = new ArrayList<>();
    for (String usernameToExport : usersToExport) {
      if (!invPermissions.isInventoryOwnerReadableByUser(usernameToExport, currentUser)) {
        unexportableUsers.add(usernameToExport);
      }
    }
    if (!unexportableUsers.isEmpty()) {
      String msg =
          String.format(
              "Cannot export data of users [%s] - users not found, or no permission",
              StringUtils.join(unexportableUsers, ", "));
      throw new IllegalArgumentException(msg);
    }
  }
}
