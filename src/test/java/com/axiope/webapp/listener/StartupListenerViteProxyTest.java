package com.axiope.webapp.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axiope.webapp.dev.ViteDevServerProxyServlet;
import com.axiope.webapp.taglib.FrontendCacheVersion;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class StartupListenerViteProxyTest {

  @Mock private ApplicationContext applicationContext;
  @Mock private ServletContext servletContext;
  @Mock private ServletRegistration.Dynamic registration;

  private MockEnvironment environment;
  private StartupListener listener;

  @BeforeEach
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
