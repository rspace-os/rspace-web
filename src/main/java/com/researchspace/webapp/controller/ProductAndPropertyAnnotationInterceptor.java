package com.researchspace.webapp.controller;

import com.researchspace.model.DeploymentPropertyType;
import com.researchspace.model.ProductType;
import com.researchspace.properties.IPropertyHolder;
import java.io.IOException;
import java.lang.annotation.Annotation;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * Interceptor that handles Product and DeploymentProperty annotations. If called Controller (or
 * method) is not enabled in given deployment the 404 http response is returned.
 */
public class ProductAndPropertyAnnotationInterceptor extends HandlerInterceptorAdapter {

  private @Autowired IPropertyHolder properties;

  /**
   * Checks whether request target is annotated with Product or DeploymentProperty annotations and
   * if the annotations are matching current deployment.
   */
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {

    Product product = getAnnotationOfGivenType(handler, Product.class);
    boolean productMatched = checkProductMatching(product);

    DeploymentProperty deploymentProperty =
        getAnnotationOfGivenType(handler, DeploymentProperty.class);
    boolean deploymentPropertyMatched = checkDeploymentPropertyMatching(deploymentProperty);

    if (productMatched && deploymentPropertyMatched) {
      return true;
    } else {
      response.sendError(HttpStatus.NOT_FOUND.value());
      return false;
    }
  }

  private boolean checkProductMatching(Product product) {
    if (product == null) {
      return true;
    }

    ProductType[] products = product.value();
    for (ProductType type : products) {
      if (ProductType.COMMUNITY.equals(type)) {
        if (properties.isCloud()) {
          return true;
        }
      } else if (ProductType.SSO.equals(type)) {
        if (properties.isSSO()) {
          return true;
        }
      } else if (ProductType.STANDALONE.equals(type)) {
        if (properties.isStandalone() && !properties.isCloud()) {
          return true;
        }
      } else {
        throw new IllegalArgumentException("object annotated with unknown ProductType: " + type);
      }
    }
    return false;
  }

  private boolean checkDeploymentPropertyMatching(DeploymentProperty deploymentProperty) {
    if (deploymentProperty == null) {
      return true;
    }

    DeploymentPropertyType[] propertyTypes = deploymentProperty.value();
    for (DeploymentPropertyType type : propertyTypes) {
      switch (type) {
        case NET_FILE_STORES_ENABLED:
          return properties.isNetFileStoresEnabled();
        case USER_SIGNUP_ENABLED:
          return properties.isUserSignup();
        case PROFILE_EMAIL_EDITABLE:
          return properties.isProfileEmailEditable();
        case API_BETA_ENABLED:
          return properties.isApiBetaEnabled();
        default:
          throw new IllegalArgumentException(
              "object annotated with unknown DeploymentPropertyType" + type);
      }
    }
    return false;
  }

  private <T extends Annotation> T getAnnotationOfGivenType(Object handler, Class<T> clazz) {
    T annot = null;
    if (handler instanceof HandlerMethod) {
      HandlerMethod handlerNtv = (HandlerMethod) handler;
      annot = handlerNtv.getMethodAnnotation(clazz);
      if (annot == null) {
        annot = handlerNtv.getMethod().getDeclaringClass().getAnnotation(clazz);
      }
    }
    return annot;
  }
}
