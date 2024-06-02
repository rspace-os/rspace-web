package com.researchspace.dao.customliquibaseupdates.v27;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.NfsDao;
import com.researchspace.model.netfiles.NfsFileSystem;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;

public class NfsFileStoreToFileSystemLinker0_27Test {

  private NfsFileStoreToFileSystemLinker nfsLinker;

  @Before
  public void before() {
    nfsLinker = new NfsFileStoreToFileSystemLinker();
  }

  @Test
  public void checkDefaultFileSystemRetrieval() {

    NfsDao nfsDaoMock = mock(NfsDao.class);
    nfsLinker.setNfsDao(nfsDaoMock);

    NfsFileSystem defaultFileSystem = nfsLinker.getDefaultFileSystem();
    assertEquals(
        NfsFileStoreToFileSystemLinker.FILE_SYSTEM_DEFAULT_NAME, defaultFileSystem.getName());
    assertEquals(null, defaultFileSystem.getId());
  }

  @Test
  public void checkOnlyFileSystemRetrieval() {

    NfsDao nfsDaoMock = mock(NfsDao.class);
    nfsLinker.setNfsDao(nfsDaoMock);

    NfsFileSystem system1 = new NfsFileSystem();
    system1.setId(1L);
    system1.setDisabled(true);

    ArrayList<NfsFileSystem> oneFileSystemList = new ArrayList<NfsFileSystem>();
    oneFileSystemList.add(system1);

    when(nfsDaoMock.getFileSystems()).thenReturn(oneFileSystemList);

    NfsFileSystem defaultFileSystem = nfsLinker.getDefaultFileSystem();
    assertEquals(system1.getId(), defaultFileSystem.getId());
  }

  @Test
  public void checkActiveFileSystemRetrieval() {

    NfsDao nfsDaoMock = mock(NfsDao.class);
    nfsLinker.setNfsDao(nfsDaoMock);

    NfsFileSystem system1 = new NfsFileSystem();
    system1.setId(1L);
    system1.setDisabled(true);

    NfsFileSystem system2enabled = new NfsFileSystem();
    system2enabled.setId(2L);

    NfsFileSystem system3 = new NfsFileSystem();
    system3.setId(3L);

    ArrayList<NfsFileSystem> threeFileSystemOneEnabledList = new ArrayList<NfsFileSystem>();
    threeFileSystemOneEnabledList.add(system1);
    threeFileSystemOneEnabledList.add(system2enabled);
    threeFileSystemOneEnabledList.add(system3);

    when(nfsDaoMock.getFileSystems()).thenReturn(threeFileSystemOneEnabledList);

    NfsFileSystem defaultFileSystem = nfsLinker.getDefaultFileSystem();
    assertEquals(system2enabled.getId(), defaultFileSystem.getId());
  }
}
