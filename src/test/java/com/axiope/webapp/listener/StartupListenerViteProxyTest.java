package com.axiope.webapp.listener;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axiope.webapp.dev.ViteDevServerProxyServlet;
import com.axiope.webapp.taglib.FrontendCacheVersion;
import java.lang.reflect.Field;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

public class StartupListenerViteProxyTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule().silent();

  @Mock private ApplicationContext applicationContext;
  @Mock private ServletContext servletContext;
  @Mock private ServletRegistration.Dynamic registration;

  private MockEnvironment environment;
  private StartupListener listener;

  @Before
  public void setUp() {
    environment = new MockEnvironment();
    environment.setProperty(FrontendCacheVersion.REACT_DEV_MODE_PROPERTY, "true");
    when(applicationContext.getEnvironment()).thenReturn(environment);
    when(servletContext.addServlet(
            eq("viteDevServerProxy"), org.mockito.ArgumentMatchers.any(Servlet.class)))
        .thenReturn(registration);
    listener = new StartupListener();
  }

  @Test
  public void usesDefaultOriginWhenViteDevServerOriginFlagIsUnset() throws Exception {
    listener.registerViteDevServerProxyIfEnabled(applicationContext, servletContext);

    assertEquals(
        ViteDevServerProxyServlet.DEFAULT_ORIGIN, upstreamOrigin(registeredProxyServlet()));
    verify(registration).addMapping("/ui/dist/*");
  }

  @Test
  public void usesViteDevServerOriginFlagWhenSet() throws Exception {
    environment.setProperty(
        StartupListener.VITE_DEV_SERVER_ORIGIN_PROPERTY, " http://localhost:5175/ ");

    listener.registerViteDevServerProxyIfEnabled(applicationContext, servletContext);

    assertEquals("http://localhost:5175", upstreamOrigin(registeredProxyServlet()));
  }

  private ViteDevServerProxyServlet registeredProxyServlet() {
    ArgumentCaptor<ViteDevServerProxyServlet> servletCaptor =
        ArgumentCaptor.forClass(ViteDevServerProxyServlet.class);
    verify(servletContext).addServlet(eq("viteDevServerProxy"), servletCaptor.capture());
    return servletCaptor.getValue();
  }

  private String upstreamOrigin(ViteDevServerProxyServlet servlet) throws Exception {
    Field upstreamOrigin = ViteDevServerProxyServlet.class.getDeclaredField("upstreamOrigin");
    upstreamOrigin.setAccessible(true);
    return (String) upstreamOrigin.get(servlet);
  }
}
