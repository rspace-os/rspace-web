package com.axiope.service.cfg;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.snapgene.wclient.SnapgeneWSClientImpl;
import com.researchspace.webapp.integrations.snapgene.SnapgeneDummy;
import com.researchspace.webapp.integrations.snapgene.SnapgeneWSNoop;
import org.junit.jupiter.api.Test;

public class BaseConfigurationTest {

  @Test
  public void noSnapGeneUrlFailsGracefullyWithNoopImpl() {
    BaseConfig cfg = new RSDevConfig();
    cfg.baseDocConverterConfig = new DocConverterBaseConfig();
    cfg.setSnapgeneUrl(null);
    assertNotNull(cfg.snapgeneWSClient());
    assertInstanceOf(SnapgeneWSNoop.class, cfg.snapgeneWSClient());

    cfg.setSnapgeneUrl("http://some.valid.uri.com");
    assertNotNull(cfg.snapgeneWSClient());
    assertInstanceOf(SnapgeneWSClientImpl.class, cfg.snapgeneWSClient());
  }

  @Test
  public void noSnapGeneUrlFailsGracefullyWithDummyImplInRunProfile() {
    BaseConfig cfg = new TestAppConfig();
    cfg.baseDocConverterConfig = new DocConverterBaseConfig();
    cfg.setSnapgeneUrl(null);
    assertNotNull(cfg.snapgeneWSClient());
    assertInstanceOf(SnapgeneDummy.class, cfg.snapgeneWSClient());

    cfg.setSnapgeneUrl("http://some.valid.uri.com");
    assertNotNull(cfg.snapgeneWSClient());
    assertInstanceOf(SnapgeneWSClientImpl.class, cfg.snapgeneWSClient());
  }
}
