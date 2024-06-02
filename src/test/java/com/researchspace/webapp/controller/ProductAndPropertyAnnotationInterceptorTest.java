package com.researchspace.webapp.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.DeploymentPropertyType;
import com.researchspace.model.ProductType;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod;

public class ProductAndPropertyAnnotationInterceptorTest extends SpringTransactionalTest {

  private @Autowired ProductAndPropertyAnnotationInterceptor interceptor;
  private @Autowired IMutablePropertyHolder properties;

  @Product(ProductType.COMMUNITY)
  static class CLOUD {
    public void method() {}
  }

  @Product(ProductType.SSO)
  static class SSO {
    public void method() {}
  }

  @Product(ProductType.STANDALONE)
  static class STANDALONE {
    public void method() {}
  }

  @Product({ProductType.STANDALONE, ProductType.COMMUNITY})
  static class NOTSSO {
    public void method() {}
  }

  static class CLOUD_METHOD {
    public void method() {}

    @Product(ProductType.COMMUNITY)
    public void methodCloud() {}
  }

  static class EDIT_EMAIL_ENABLED {

    @DeploymentProperty(DeploymentPropertyType.PROFILE_EMAIL_EDITABLE)
    public void methodEmail() {}
  }

  @DeploymentProperty(DeploymentPropertyType.NET_FILE_STORES_ENABLED)
  static class NET_FILE_STORES_ENABLED_CLASS {
    public NET_FILE_STORES_ENABLED_CLASS() {}

    public void method() {}
  }

  @DeploymentProperty(DeploymentPropertyType.USER_SIGNUP_ENABLED)
  static class USERSIGNUP_ENABLED_CLASS {
    public USERSIGNUP_ENABLED_CLASS() {}

    public void method() {}
  }

  MockHttpServletRequest httpRequest;
  MockHttpServletResponse httpResponse;

  @Before
  public void setUp() throws Exception {
    httpRequest = new MockHttpServletRequest();
    httpResponse = new MockHttpServletResponse();
  }

  @After
  public void tearDown() throws Exception {
    properties.setStandalone("true");
  }

  @Test
  public void preHandleSSODeployment() throws Exception {
    SSO sso = new SSO();
    Method method = sso.getClass().getMethod("method");
    ServletInvocableHandlerMethod handler = new ServletInvocableHandlerMethod(sso, method);
    properties.setStandalone("false");
    assertHandledOK(handler);
    // should be rejected
    properties.setStandalone("true");
    assertRejected(handler);
  }

  @Test
  public void preHandleStandaloneDeployment() throws Exception {
    STANDALONE standalone = new STANDALONE();
    Method method = standalone.getClass().getMethod("method");
    ServletInvocableHandlerMethod handler = new ServletInvocableHandlerMethod(standalone, method);
    properties.setStandalone("true");
    assertHandledOK(handler);
    // should be rejected
    properties.setStandalone("false");
    assertRejected(handler);
  }

  @Test
  public void multipleProducts() throws Exception {

    ServletInvocableHandlerMethod handler = createHandler(NOTSSO.class, "method");
    properties.setStandalone("true");
    assertHandledOK(handler);

    properties.setCloud("true");
    assertHandledOK(handler);
    // this is SSO
    properties.setStandalone("false");
    properties.setCloud("false");
    assertRejected(handler);
  }

  @Test
  public void preHandleCloudDeployment() throws Exception {
    ServletInvocableHandlerMethod handler = createHandler(CLOUD.class, "method");
    properties.setCloud("true");

    assertHandledOK(handler);

    // should be rejected
    properties.setCloud("false");
    assertRejected(handler);
    assertTrue(httpResponse.isCommitted());
    assertTrue(httpResponse.getStatus() == HttpStatus.NOT_FOUND.value());
  }

  @Test
  public void methodOverride() throws Exception {
    CLOUD_METHOD dc = new CLOUD_METHOD();
    // this unannotated method should run whether or not isCloud == true
    Method method = dc.getClass().getMethod("method");
    ServletInvocableHandlerMethod handler = new ServletInvocableHandlerMethod(dc, method);
    properties.setCloud("true");
    assertHandledOK(handler);

    properties.setCloud("false");
    assertHandledOK(handler);

    Method methodCloud = dc.getClass().getMethod("methodCloud");
    // should work OK
    ServletInvocableHandlerMethod handler2 = new ServletInvocableHandlerMethod(dc, methodCloud);
    properties.setCloud("true");
    assertHandledOK(handler2);
    // should be rejected
    properties.setCloud("false");
    assertRejected(handler2);
    assertTrue(httpResponse.isCommitted());
    assertTrue(httpResponse.getStatus() == HttpStatus.NOT_FOUND.value());
  }

  @Test
  public void netFileStoresDeploymentProperty() throws Exception {
    ServletInvocableHandlerMethod handler =
        createHandler(NET_FILE_STORES_ENABLED_CLASS.class, "method");
    properties.setNetFileStoresEnabled("true");
    assertHandledOK(handler);
    // should be rejected
    properties.setNetFileStoresEnabled("false");
    assertRejected(handler);
  }

  @Test
  public void emailEnabledDeploymentProperty() throws Exception {
    ServletInvocableHandlerMethod handler = createHandler(EDIT_EMAIL_ENABLED.class, "methodEmail");
    properties.setProfileEmailEditable(true);
    assertHandledOK(handler);
    // should be rejected
    properties.setProfileEmailEditable(false);
    assertRejected(handler);
  }

  private void assertHandledOK(ServletInvocableHandlerMethod handler) throws IOException {
    assertTrue(interceptor.preHandle(httpRequest, httpResponse, handler));
    assertFalse(httpResponse.isCommitted());
  }

  @Test
  public void usersignupDeploymentProperty() throws Exception {

    ServletInvocableHandlerMethod handler = createHandler(USERSIGNUP_ENABLED_CLASS.class, "method");
    properties.setUserSignup("true");
    assertHandledOK(handler);
    // should be rejected
    properties.setUserSignup("false");
    assertRejected(handler);
  }

  <T> ServletInvocableHandlerMethod createHandler(Class<T> clazz, String methodName)
      throws InstantiationException,
          IllegalAccessException,
          NoSuchMethodException,
          SecurityException {
    T instance = clazz.newInstance();
    Method method = clazz.getMethod(methodName);
    ServletInvocableHandlerMethod handler = new ServletInvocableHandlerMethod(instance, method);
    return handler;
  }

  private void assertRejected(ServletInvocableHandlerMethod handler) throws IOException {
    assertFalse(interceptor.preHandle(httpRequest, httpResponse, handler));
  }
}
