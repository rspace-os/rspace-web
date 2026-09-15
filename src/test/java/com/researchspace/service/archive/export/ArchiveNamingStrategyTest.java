package com.researchspace.service.archive.export;

import static com.researchspace.testutils.TestFactory.createAnyGroup;
import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.TestFactory;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ArchiveNamingStrategyTest {
  @Mock UserDao userDao;

  @Mock GroupDao grpDao;
  @Mock RecordDao recordDao;
  @InjectMocks private ArchiveNamingStrategy archiveNamingStrategy;

  @Test
  public void testGenerateArchiveNameSelection() {
    ArchiveExportConfig cfg = createXmlSelectionCfg();
    ExportContext context = createExportContext(null);
    String name = archiveNamingStrategy.generateArchiveName(cfg, context);
    Matcher m = ArchiveNamingStrategy.NAME_PATTERN.matcher(name);
    assertTrue(m.matches());
    assertTrue(name.contains(ExportScope.SELECTION.name()), name);
    assertTrue(name.contains("xml"));
  }

  private ArchiveExportConfig createXmlSelectionCfg() {
    ArchiveExportConfig cfg = new ArchiveExportConfig();
    cfg.setExportScope(ExportScope.SELECTION);
    cfg.setArchiveType("xml");
    return cfg;
  }

  @Test
  public void testGenerateArchiveNameSingleSelectionUsesDocName() {
    StructuredDocument expected = TestFactory.createAnySD();
    expected.setId(5L);
    final String docName = "Test Export of A-Cell_324 Experiment";
    final String docNameExpectedFileName = "Test-Export-of-A-Cell-324-Experiment";
    expected.setName(docName);
    when(recordDao.get(5L)).thenReturn(expected);

    ArchiveExportConfig cfg = createXmlSelectionCfg();
    ExportContext context = createExportContext(expected);

    String name = archiveNamingStrategy.generateArchiveName(cfg, context);
    Matcher m = ArchiveNamingStrategy.NAME_PATTERN.matcher(name);
    assertTrue(m.matches());
    assertFalse(name.contains(ExportScope.SELECTION.name()), name);
    assertTrue(name.contains("xml"), name);
    assertTrue(name.contains(docNameExpectedFileName), name);
    assertFalse(name.contains(docName));
  }

  private ExportContext createExportContext(StructuredDocument expected) {
    ExportContext context = new ExportContext();
    ExportRecordList el = new ExportRecordList();
    if (expected != null) {
      el.add(expected.getOid());
    }
    context.setExportRecordList(el);
    return context;
  }

  @Test
  public void testGenerateArchiveNameUser() {
    ArchiveExportConfig cfg = new ArchiveExportConfig();
    cfg.setExportScope(ExportScope.USER);
    cfg.setArchiveType(ArchiveExportConfig.HTML);
    final User any = TestFactory.createAnyUser("u1234");
    any.setId(123L);
    cfg.setUserOrGroupId(any.getOid());
    ExportContext context = new ExportContext();
    when(userDao.get(any.getOid().getDbId())).thenReturn(any);
    String name = archiveNamingStrategy.generateArchiveName(cfg, context);
    Matcher m = ArchiveNamingStrategy.NAME_PATTERN.matcher(name);
    assertTrue(m.matches());
    assertTrue(name.contains(ArchiveExportConfig.HTML));
    assertTrue(name.contains(any.getUsername()), name);
  }

  @Test
  public void testGenerateArchiveNameGroup() {
    ArchiveExportConfig cfg = new ArchiveExportConfig();
    cfg.setExportScope(ExportScope.GROUP);
    cfg.setArchiveType(ArchiveExportConfig.HTML);
    final Group anyGroup = createAGroup();
    anyGroup.setId(123L);
    cfg.setUserOrGroupId(anyGroup.getOid());
    ExportContext context = new ExportContext();
    Mockito.when(grpDao.get(anyGroup.getOid().getDbId())).thenReturn(anyGroup);
    String name = archiveNamingStrategy.generateArchiveName(cfg, context);
    Matcher m = ArchiveNamingStrategy.NAME_PATTERN.matcher(name);
    assertTrue(m.matches());
    assertTrue(name.contains(ArchiveExportConfig.HTML));
    assertTrue(name.contains(anyGroup.getDisplayName()), name);
  }

  private Group createAGroup() {
    User pi = createAnyUser("any");
    pi.addRole(Role.PI_ROLE);
    return createAnyGroup(pi);
  }
}
