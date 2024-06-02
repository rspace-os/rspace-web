package com.axiope.service.cfg;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import com.researchspace.snapgene.wclient.SnapgeneWSClientImpl;
import com.researchspace.webapp.integrations.snapgene.SnapgeneDummy;
import com.researchspace.webapp.integrations.snapgene.SnapgeneWSNoop;
import org.hamcrest.Matchers;
import org.junit.Test;

public class BaseConfigurationTest {

  @Test
  public void noSnapGeneUrlFailsGracefullyWithNoopImpl() {
    BaseConfig cfg = new RSDevConfig();
    cfg.baseDocConverterConfig = new DocConverterBaseConfig();
    cfg.setSnapgeneUrl(null);
    assertNotNull(cfg.snapgeneWSClient());
    assertThat(cfg.snapgeneWSClient(), Matchers.instanceOf(SnapgeneWSNoop.class));

    cfg.setSnapgeneUrl("http://some.valid.uri.com");
    assertNotNull(cfg.snapgeneWSClient());
    assertThat(cfg.snapgeneWSClient(), Matchers.instanceOf(SnapgeneWSClientImpl.class));
  }

  @Test
  public void noSnapGeneUrlFailsGracefullyWithDummyImplInRunProfile() {
    BaseConfig cfg = new TestAppConfig();
    cfg.baseDocConverterConfig = new DocConverterBaseConfig();
    cfg.setSnapgeneUrl(null);
    assertNotNull(cfg.snapgeneWSClient());
    assertThat(cfg.snapgeneWSClient(), Matchers.instanceOf(SnapgeneDummy.class));

    cfg.setSnapgeneUrl("http://some.valid.uri.com");
    assertNotNull(cfg.snapgeneWSClient());
    assertThat(cfg.snapgeneWSClient(), Matchers.instanceOf(SnapgeneWSClientImpl.class));
  }
}
