package com.researchspace.service.archive.export;

import com.researchspace.archive.model.ArchiveUsers;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.core.util.XMLReadWriteUtils;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.service.archive.ExportImport;
import java.io.File;
import java.io.IOException;
import javax.xml.bind.JAXBException;

/** Archive handler for exporting User and Group data */
public class UserArchiveDataHandler extends AbstractDataHandler implements ArchiveDataHandler {

  @Override
  public void archiveData(IArchiveExportConfig aconfig, File archiveAssmblyFlder)
      throws JAXBException, IOException {
    if (aconfig.isUserScope()) {
      ArchiveUsers users = new ArchiveUsers();
      Long id = getDBId(aconfig);
      User toExport = userDao.get(id);
      users.setUsers(TransformerUtils.toSet(toExport));
      writeUserXML(archiveAssmblyFlder, users);
    } else if (aconfig.isGroupScope()) {
      ArchiveUsers users = new ArchiveUsers();
      Long id = getDBId(aconfig);
      Group toExport = grpDao.get(id);
      users.setGroups(TransformerUtils.toSet(toExport));
      users.setUsers(toExport.getMembers());
      users.setUserGroups(toExport.getUserGroups());
      writeUserXML(archiveAssmblyFlder, users);
    }
  }

  private void writeUserXML(File archiveAssmblyFlder, ArchiveUsers users)
      throws JAXBException, IOException {
    File usersXML = new File(archiveAssmblyFlder, ExportImport.USERS);
    try {
      XMLReadWriteUtils.toXML(usersXML, users, ArchiveUsers.class);
    } catch (Exception e) {
      throw new ExportFailureException("Failure writing " + ExportImport.USERS);
    }
    File usersXSD = new File(archiveAssmblyFlder, ExportImport.USERS_SCHEMA);
    XMLReadWriteUtils.generateSchemaFromXML(usersXSD, ArchiveUsers.class);
  }
}
