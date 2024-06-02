package com.researchspace.service.archive.export;

import com.researchspace.archive.model.ArchiveMessages;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.ObjectToStringPropertyTransformer;
import com.researchspace.core.util.SortOrder;
import com.researchspace.core.util.XMLReadWriteUtils;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.service.archive.ExportImport;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class MessageArchiveDataHandler extends AbstractDataHandler implements ArchiveDataHandler {

  private static final Logger LOG =
      LoggerFactory.getLogger(MessageArchiveDataHandler.class.getName());
  private @Autowired CommunicationDao commDao;

  @Override
  public void archiveData(IArchiveExportConfig aconfig, File archiveAssmblyFlder)
      throws JAXBException, IOException {
    Set<User> usersToInclude = new TreeSet<User>();
    if (aconfig.isUserScope()) {
      Long id = getDBId(aconfig);
      User toExport = userDao.get(id);
      usersToInclude.add(toExport);
    } else if (aconfig.isGroupScope()) {
      Long id = getDBId(aconfig);
      Group grp = grpDao.get(id);
      usersToInclude.addAll(grp.getMembers());
    } else {
      return; // don't generate at all for selection-based export
    }

    List<MessageOrRequest> allMsges = new ArrayList<MessageOrRequest>();
    PaginationCriteria<CommunicationTarget> pgCrit =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    pgCrit.setGetAllResults();
    pgCrit.setOrderBy("communication.creationTime");
    pgCrit.setSortOrder(SortOrder.ASC);
    for (User user : usersToInclude) {
      ISearchResults<MessageOrRequest> messages =
          commDao.getAllSentAndReceivedSimpleMessagesForUser(user, pgCrit);
      allMsges.addAll(messages.getResults());
    }

    generateMessagesXML(archiveAssmblyFlder, allMsges);
  }

  public ArchiveMessages generateMessagesXML(
      File archiveAssmblyFlder, List<MessageOrRequest> allMsges) throws JAXBException, IOException {
    ArchiveMessages archiveMsges = new ArchiveMessages();
    Set<User> allUsersInMessages = new TreeSet<User>();
    archiveMsges.getMessages().addAll(allMsges);

    for (MessageOrRequest mor : allMsges) {
      allUsersInMessages.add(mor.getOriginator());
      for (CommunicationTarget ct : mor.getRecipients()) {
        allUsersInMessages.add(ct.getRecipient());
      }
    }
    archiveMsges
        .getUsernames()
        .addAll(
            allUsersInMessages.stream()
                .map(new ObjectToStringPropertyTransformer<User>("username"))
                .collect(Collectors.toList()));

    writeMessageXML(archiveAssmblyFlder, archiveMsges);
    return archiveMsges;
  }

  private void writeMessageXML(File archiveAssmblyFlder, ArchiveMessages messages)
      throws JAXBException, IOException {
    File messagesXML = new File(archiveAssmblyFlder, ExportImport.MESSAGES);
    try {
      XMLReadWriteUtils.toXML(messagesXML, messages, ArchiveMessages.class);
    } catch (Exception e) {
      LOG.warn("Unable to write messages: ", e);
      throw new ExportFailureException("Failure writing " + ExportImport.MESSAGES);
    }
    File messagesXSD = new File(archiveAssmblyFlder, ExportImport.MESSAGES_SCHEMA);
    XMLReadWriteUtils.generateSchemaFromXML(messagesXSD, ArchiveMessages.class);
  }
}
