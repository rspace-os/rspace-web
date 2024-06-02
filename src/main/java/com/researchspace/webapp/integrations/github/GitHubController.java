package com.researchspace.webapp.integrations.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.User;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError;
import com.researchspace.webapp.integrations.helper.OauthAuthorizationError.OauthAuthorizationErrorBuilder;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Class responsible for handling connection between RSpace and GitHub */
@Controller
@RequestMapping("/github")
@Slf4j
public class GitHubController {

  protected static final String GITHUB_VIEW_NAME = "connect/github/gitHubTreeView";

  private static String GITHUB_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
  private static String GITHUB_API_URL = "https://api.github.com";
  private static String GITHUB_API_USER_REPOS = String.format("%s/user/repos", GITHUB_API_URL);

  @Value("${github.client.id}")
  private String clientId;

  @Value("${github.secret}")
  private String clientSecret;

  private @Autowired IntegrationsHandler integrationsHandler;
  private @Autowired UserManager userManager;

  private RestTemplate restTemplate;

  private static class AccessDeniedException extends Exception {
    private static final long serialVersionUID = -4859611690834326921L;

    public AccessDeniedException(String message) {
      super(message);
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Repository {
    private @JsonProperty("full_name") String fullName;
    private String description;
  }

  @Data
  public static class TreeNode {
    private String repository;
    private String fullPath;

    // Raw values from JSON response
    private String type;
    private String path;
    private String sha;

    public boolean isFolder() {
      return type.equals("tree");
    }

    public boolean isFile() {
      return type.equals("blob");
    }
  }

  @Data
  public static class TreeApiResponse {
    private String sha;
    private String url;
    private List<TreeNode> tree;
  }

  @Data
  public static class AccessToken {
    private @JsonProperty("access_token") String accessToken;
    private String scope;
    private @JsonProperty("token_type") String type;
    private String error;
    private @JsonProperty("error_description") String errorDescription;
    private @JsonProperty("error_uri") String errorUri;
  }

  public GitHubController() {
    this.restTemplate = new RestTemplate();
  }

  public void setRestTemplate(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  private HttpHeaders getApiHeaders(@Nullable String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    if (accessToken != null) headers.add("Authorization", String.format("token %s", accessToken));
    return headers;
  }

  private String getAccessToken(String authorizationCode)
      throws RestClientException, AccessDeniedException {
    log.error("-" + authorizationCode + "-"); // this is correct
    // Set required post parameters
    Map<String, String> kv = new HashMap<>();
    kv.put("client_id", clientId);
    kv.put("client_secret", clientSecret);
    kv.put("code", authorizationCode);

    HttpEntity<Map<String, String>> accessTokenRequestEntity =
        new HttpEntity<>(kv, getApiHeaders(null));

    ResponseEntity<AccessToken> accessToken =
        restTemplate.exchange(
            GITHUB_ACCESS_TOKEN_URL, HttpMethod.POST, accessTokenRequestEntity, AccessToken.class);

    // Check if error is not set
    if (accessToken.hasBody() && accessToken.getBody().getError() != null) {
      log.error(
          String.format(
              "GitHub returned an error: %s, %s, %s",
              accessToken.getBody().getError(),
              accessToken.getBody().getErrorDescription(),
              accessToken.getBody().getErrorUri()));
      throw new AccessDeniedException(accessToken.getBody().getErrorDescription());
    }

    // Check if all expected fields are set
    if (!accessToken.hasBody()
        || accessToken.getBody().getAccessToken() == null
        || accessToken.getBody().getType() == null
        || accessToken.getBody().getScope() == null) {
      log.error(
          String.format(
              "Either 'access_token', 'token_type' or 'scope' was missing (%b, %b, %b)",
              accessToken.getBody().getAccessToken() == null,
              accessToken.getBody().getType() == null,
              accessToken.getBody().getScope() == null));
      throw new IllegalArgumentException("some access token fields were not filled");
    }

    if (accessToken.getBody().scope.contains("user")
        && accessToken.getBody().scope.contains("repo")) {
      return accessToken.getBody().getAccessToken();
    } else {
      throw new AccessDeniedException("either 'user' or 'repo' access was not granted");
    }
  }

  private List<Repository> getUserRepositories(String accessToken) throws RestClientException {
    return restTemplate
        .exchange(
            GITHUB_API_USER_REPOS,
            HttpMethod.GET,
            new HttpEntity<>(getApiHeaders(accessToken)),
            new ParameterizedTypeReference<List<Repository>>() {})
        .getBody();
  }

  @GetMapping("/oauthUrl")
  @ResponseBody
  public AjaxReturnObject<String> oauthUrl() {
    var url = "https://github.com/login/oauth/authorize?scope=repo,user&client_id=" + this.clientId;
    return new AjaxReturnObject<>(url, null);
  }

  @GetMapping("/allRepositories")
  @ResponseBody
  public AjaxReturnObject<List<Repository>> allRepositories(
      @RequestParam Map<String, String> params) {
    try {
      return new AjaxReturnObject<>(getUserRepositories(params.get("authToken")), null);
    } catch (RestClientException e) {
      log.error("Getting GitHub repositories list failed", e);
      return new AjaxReturnObject<List<Repository>>(null, ErrorList.of(e.getMessage()));
    }
  }

  @GetMapping("/redirect_uri")
  public String onAuthorization(
      @RequestParam Map<String, String> params, Model model, Principal principal) {
    String authorizationCode = params.get("code");
    String accessToken = null;
    try {
      accessToken = getAccessToken(authorizationCode);
    } catch (AccessDeniedException e) {
      log.error("GitHub access denied", e);
      OauthAuthorizationError error =
          getAuthorizationBuilder()
              .errorMsg("Github access denied")
              .errorDetails(e.getMessage())
              .build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    } catch (Exception e) {
      log.error("Exception during GitHub token exchange", e);
      OauthAuthorizationError error =
          getAuthorizationBuilder()
              .errorMsg("Exception during token exchange")
              .errorDetails(e.getMessage())
              .build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    }

    List<Repository> repositories = null;
    try {
      repositories = getUserRepositories(accessToken);
    } catch (Exception e) {
      log.error("Getting GitHub repositories list failed", e);
      OauthAuthorizationError error =
          getAuthorizationBuilder()
              .errorMsg("Getting repositories list failed")
              .errorDetails(e.getMessage())
              .build();
      model.addAttribute("error", error);
      return "connect/authorizationError";
    }

    log.info(String.format("User %s successfully authenticated with GitHub", principal.getName()));
    model.addAttribute("gitHubAccessToken", accessToken);
    model.addAttribute("gitHubRepositories", repositories);

    return "connect/github/connected";
  }

  private OauthAuthorizationErrorBuilder getAuthorizationBuilder() {
    return OauthAuthorizationError.builder().appName("Github");
  }

  // Map is from repository name to access code
  @SuppressWarnings("unchecked")
  private Map<String, String> getConfiguredRepositories(Principal principal) {
    Map<String, String> hashMap = new HashMap<>();
    User user = userManager.getUserByUsername(principal.getName());
    IntegrationInfo integration = integrationsHandler.getIntegration(user, "GITHUB");
    for (Object propertySetObject : integration.getOptions().values()) {
      Map<String, String> propertySet = (Map<String, String>) propertySetObject;
      String repositoryName = propertySet.get("GITHUB_REPOSITORY_FULL_NAME");
      String accessCode = propertySet.get("GITHUB_ACCESS_TOKEN");
      hashMap.put(repositoryName, accessCode);
    }
    return hashMap;
  }

  private List<TreeNode> getNodesFromGitHubApi(
      String fullRepoName, String fullPath, String sha, String accessToken)
      throws RestClientException {
    String url = String.format("%s/repos/%s/git/trees/%s", GITHUB_API_URL, fullRepoName, sha);
    TreeApiResponse treeApiResponse =
        restTemplate
            .exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(getApiHeaders(accessToken)),
                TreeApiResponse.class)
            .getBody();
    for (TreeNode node : treeApiResponse.tree) {
      node.setRepository(fullRepoName);
      node.setFullPath(Paths.get(fullPath, node.path).toString());
    }
    return treeApiResponse.getTree();
  }

  @RequestMapping(value = "/ajax/get_repository_tree", method = RequestMethod.POST)
  public String getTree(@RequestParam("dir") String dir, Model model, Principal principal) {
    List<TreeNode> nodes = new ArrayList<TreeNode>();
    Map<String, String> repositories = getConfiguredRepositories(principal);

    try {
      dir = java.net.URLDecoder.decode(dir, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      log.error("Error while parsing a GitHub tree view request", e);
      model.addAttribute("error", "Error while parsing the request");
      model.addAttribute("treeNodes", nodes);
      return GITHUB_VIEW_NAME;
    }

    // Root folder, showing all repository names
    if (dir.equals("/")) {
      for (String repositoryName : repositories.keySet()) {
        TreeNode node = new TreeNode();
        node.setPath(repositoryName);
        node.setRepository(repositoryName);
        node.setType("tree");
        node.setSha("master");
        nodes.add(node);
      }
    } else {
      try {
        String[] splitDir = dir.split("#");
        String repositoryName = splitDir[0];
        String sha = splitDir[1];
        String fullPath = splitDir[2];
        nodes =
            getNodesFromGitHubApi(repositoryName, fullPath, sha, repositories.get(repositoryName));
        // No error
        model.addAttribute("error", "");
      } catch (Exception e) {
        log.error("GitHub tree view request failed", e);
        model.addAttribute("error", "Error while getting GitHub folder contents");
      }
    }
    model.addAttribute("treeNodes", nodes);
    return GITHUB_VIEW_NAME;
  }
}
