package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.researchspace.dao.RSMetaDataDao;
import com.researchspace.model.RSMetaData;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.impl.GlobalInitManagerImpl;
import java.util.Arrays;
import java.util.List;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
public class GlobalInitTest {

  class GlobalInitManagerImplTSS extends GlobalInitManagerImpl {
    protected Subject createSubject() {
      return subject;
    }
  }

  // set up mock objects

  @Mock ApplicationContext ac;
  @Mock Environment env;
  @Mock Subject subject;

  @Mock RSMetaDataDao metadao;
  @Mock IApplicationInitialisor mockInitializor;

  @Test
  void testInitialisorOnInitialDeployment() throws IllegalAddChildOperation {
    GlobalInitManagerImpl mgr = new GlobalInitManagerImplTSS();
    mgr.setApplicationInitialisors(Arrays.asList(new IApplicationInitialisor[] {mockInitializor}));
    mgr.setMetadataDao(metadao);
    ContextRefreshedEvent event = new ContextRefreshedEvent(ac);
    final RSMetaData meta = new RSMetaData();
    final List<RSMetaData> metas = Arrays.asList(new RSMetaData[] {meta});
    final MockEnvironment mockEnv = new MockEnvironment();
    meta.setInitialized(false);
    Mockito.when(metadao.getAll()).thenReturn(metas);
    Mockito.when(ac.getEnvironment()).thenReturn(mockEnv);

    mgr.onApplicationEvent(event);
    verify(mockInitializor, times(1)).onInitialAppDeployment();
    verify(mockInitializor, times(1)).onAppStartup(ac);
    verify(metadao, times(1)).save(meta);
    assertTrue(meta.isInitialized()); // should still be initialised
  }

  @Test
  void testInitialisorOnSubsequentStartup() throws IllegalAddChildOperation {
    GlobalInitManagerImpl mgr = new GlobalInitManagerImplTSS();
    mgr.setApplicationInitialisors(Arrays.asList(new IApplicationInitialisor[] {mockInitializor}));
    mgr.setMetadataDao(metadao);
    ContextRefreshedEvent event = new ContextRefreshedEvent(ac);
    final RSMetaData meta = new RSMetaData();
    final List<RSMetaData> metas = Arrays.asList(new RSMetaData[] {meta});
    final MockEnvironment mockEnv = new MockEnvironment();
    meta.setInitialized(true); // i.,e this is not the first startup
    Mockito.when(metadao.getAll()).thenReturn(metas);
    Mockito.when(ac.getEnvironment()).thenReturn(mockEnv);

    mgr.onApplicationEvent(event);
    verify(mockInitializor, never()).onInitialAppDeployment();
    verify(mockInitializor, times(1)).onAppStartup(ac);

    assertTrue(meta.isInitialized());
  }
}
