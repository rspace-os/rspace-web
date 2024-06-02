package com.researchspace.webapp.filter;

import com.researchspace.properties.IPropertyHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public class OriginRefererCheckerImpl implements OriginRefererChecker {

  @Autowired private IPropertyHolder properties;

  @Value("${csrf.filters.acceptedDomains}")
  private String acceptedDomainsDeploymentProp;

  @PostConstruct
  public void init() {
    log.info("Additional Accepted Domains: [{}]", acceptedDomainsDeploymentProp);
    log.info("All accepted domains : [{}]", StringUtils.join(listAcceptedDomains(), ","));
  }

  private List<String> acceptedDomains;

  /**
   * Validates incoming request origin against expected
   *
   * @param request
   * @param response
   * @return An empty optional if OK, otherwise an error message
   */
  @Override
  public Optional<String> checkOriginReferer(
      HttpServletRequest request, HttpServletResponse response) {
    Optional<String> errMsg = Optional.empty();
    // allow get requests from any origin
    if ("GET".equals(request.getMethod())) {
      return errMsg;
    }
    if (acceptedDomains == null) {
      acceptedDomains = listAcceptedDomains();
    }

    String origin = request.getHeader("Origin");
    String referer = request.getHeader("Referer");
    log.info("method: " + request.getMethod() + ", origin: " + origin + ", referer: " + referer);

    if (origin != null) {
      for (String domain : acceptedDomains) {
        if (origin.startsWith(domain)) {
          return Optional.empty();
        }
      }
      errMsg = Optional.of("mismatched origin '" + origin + "'");
    } else if (referer != null) {
      for (String domain : acceptedDomains) {
        if (referer.startsWith(domain)) {
          return Optional.empty();
        }
      }
      errMsg = Optional.of("mismatched referer '" + referer + "'");
    } else {
      errMsg = Optional.of("no origin or referer");
    }
    return errMsg;
  }

  protected List<String> listAcceptedDomains() {
    List<String> domains = new ArrayList<String>();

    // add server.urls.prefix, strip trailing slash if present
    String serverUrl = properties.getServerUrl();
    if (serverUrl.endsWith("/")) {
      serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
    }
    domains.add(serverUrl);

    // add additional domains set through csrf.filters.acceptedDomains
    if (StringUtils.isNotEmpty(acceptedDomainsDeploymentProp)) {
      String[] extraDomains = acceptedDomainsDeploymentProp.split(",");
      for (String dom : extraDomains) {
        if (StringUtils.isNotBlank(dom)) {
          domains.add(dom.trim());
        }
      }
    }

    // finally, accepting tunnelled localhost connections for Ops
    domains.add("http://localhost:");
    domains.add("https://localhost:");

    return domains;
  }

  /*
   * ===========
   *  for tests
   * ===========
   */

  protected void setProperties(IPropertyHolder properties) {
    this.properties = properties;
  }

  protected void setAcceptedDomainsDeploymentProp(String acceptedDomainsProp) {
    this.acceptedDomainsDeploymentProp = acceptedDomainsProp;
  }
}
