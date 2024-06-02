package com.researchspace.webapp.integrations.jove;

import com.researchspace.integrations.jove.client.JoveClient;
import com.researchspace.integrations.jove.model.JoveArticle;
import com.researchspace.integrations.jove.model.JoveSearchRequest;
import com.researchspace.integrations.jove.model.JoveSearchResult;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpStatusCodeException;

@Slf4j
@Controller
@RequestMapping("/apps/jove")
public class JoveController {

  @Autowired private JoveClient joveClient;
  @Autowired private UserManager userManager;

  @Autowired private JoveExceptionHandler joveExceptionHandler;

  @ExceptionHandler(HttpStatusCodeException.class)
  public ResponseEntity<String> handleExceptions(
      HttpServletRequest request, HttpServletResponse response, HttpStatusCodeException e) {
    return joveExceptionHandler.handle(request, response, e);
  }

  // Search via query string
  @PostMapping("/search")
  public JoveSearchResult search(@RequestBody JoveSearchRequest searchRequest)
      throws URISyntaxException, MalformedURLException {
    User user = userManager.getAuthenticatedUserInSession();
    JoveSearchResult searchResult = joveClient.search(user, searchRequest);
    log.info("Search Result: {}", searchResult);
    return searchResult;
  }

  // Get article and details
  @GetMapping("/article/{articleId}")
  public JoveArticle article(@PathVariable Long articleId)
      throws MalformedURLException, URISyntaxException {
    User user = userManager.getAuthenticatedUserInSession();
    return joveClient.getArticle(user, articleId);
  }
}
