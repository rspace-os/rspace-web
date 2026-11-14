package com.researchspace.service.impl;

import com.researchspace.core.util.FieldParserConstants;
import com.researchspace.dao.NfsDao;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFieldLink;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NfsFieldLinkManager;
import jakarta.ws.rs.NotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

@Service("nfsFieldLinkManager")
@AllArgsConstructor
@Slf4j
public class NfsFieldLinkManagerImpl implements NfsFieldLinkManager {

  private final NfsDao nfsDao;
  private final FieldParser fieldParser;
  private final IPermissionUtils permUtils;
  private final FilestoreAclChecker aclChecker;
  private final MessageSourceUtils messages;

  @Override
  public void reindex(Field field, String content) {
    Set<Pair<Long, String>> wanted = linksIn(content);
    for (NfsFieldLink row : nfsDao.getFieldLinks(field.getId())) {
      if (!wanted.remove(Pair.of(row.getFileStore().getId(), row.getRelPath()))) {
        nfsDao.deleteFieldLink(row);
      }
    }
    for (Pair<Long, String> link : wanted) {
      NfsFileStore store = nfsDao.getNfsFileStore(link.getLeft());
      if (store == null) {
        log.debug("Not indexing link to unknown filestore {} in field {}", link, field.getId());
        continue;
      }
      NfsFieldLink row = new NfsFieldLink(field, store, link.getRight());
      if (row.getRelPath().length() > NfsFieldLink.MAX_PATH_LENGTH
          || row.getAbsolutePath().length() > NfsFieldLink.MAX_PATH_LENGTH) {
        log.warn("Not indexing link {} in field {}: path too long", link, field.getId());
        continue;
      }
      nfsDao.saveFieldLink(row);
    }
  }

  private Set<Pair<Long, String>> linksIn(String content) {
    Set<Pair<Long, String>> links = new HashSet<>();
    if (StringUtils.isBlank(content)) {
      return links;
    }
    FieldContents contents =
        fieldParser.findFieldElementsInContentForCssClass(
            new FieldContents(), content, FieldParserConstants.NET_FS_CLASSNAME);
    for (NfsElement element : contents.getElements(NfsElement.class).getElements()) {
      links.add(Pair.of(element.getFileStoreId(), element.getPath()));
    }
    return links;
  }

  @Override
  public List<RecordInformation> getLinkedDocuments(
      Long filestoreId, String remotePath, User user) {
    NfsFileStore filestore = nfsDao.getNfsFileStore(filestoreId);
    if (filestore == null) {
      throw new NotFoundException(
          messages.getMessage(
              "netFileStores.errors.fileStoreNotFound", new Object[] {filestoreId}));
    }
    aclChecker.assertCanRead(user, filestore.getFileSystem());
    String path = NfsFieldLink.normalisePath(filestore.getAbsolutePath(remotePath));
    return nfsDao.getDocumentsLinkingTo(filestore.getFileSystem().getId(), List.of(path)).stream()
        .map(doc -> toRecordInfoOrPlaceholder(doc, user))
        .toList();
  }

  private RecordInformation toRecordInfoOrPlaceholder(StructuredDocument doc, User user) {
    if (permUtils.isPermitted(doc, PermissionType.READ, user)) {
      return doc.toRecordInfo();
    }
    RecordInformation placeholder = new RecordInformation();
    placeholder.setOwnerFullName(doc.getOwner().getFullName());
    return placeholder;
  }
}
