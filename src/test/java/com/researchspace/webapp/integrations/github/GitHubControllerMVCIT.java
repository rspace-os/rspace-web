package com.researchspace.webapp.integrations.github;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.researchspace.Constants;
import com.researchspace.model.User;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.webapp.controller.MVCTestBase;
import com.researchspace.webapp.integrations.github.GitHubController.Repository;
import com.researchspace.webapp.integrations.github.GitHubController.TreeNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

@WebAppConfiguration
public class GitHubControllerMVCIT extends MVCTestBase {

  private @Autowired GitHubController gitHubController;
  private @Autowired IntegrationsHandler integrationsHandler;

  @Value("${github.client.id}")
  private String githubClientId;

  @Value("${github.secret}")
  private String githubSecret;

  private static final String GITHUB_ACCESS_TOKEN = RandomStringUtils.randomAlphabetic(20);

  private MockRestServiceServer server;

  @Before
  public void setUp() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();

    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    gitHubController.setRestTemplate(restTemplate);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private void addExampleRepositories(User user) {
    Map<String, String> propertySetTest1 = new HashMap<>();
    propertySetTest1.put("GITHUB_REPOSITORY_FULL_NAME", "rspace-integration-test-user/test1");
    propertySetTest1.put("GITHUB_ACCESS_TOKEN", GITHUB_ACCESS_TOKEN);
    integrationsHandler.saveAppOptions(null, propertySetTest1, "GITHUB", true, user);

    Map<String, String> propertySetTest2 = new HashMap<>();
    propertySetTest2.put("GITHUB_REPOSITORY_FULL_NAME", "rspace-integration-test-user/test2");
    propertySetTest2.put("GITHUB_ACCESS_TOKEN", GITHUB_ACCESS_TOKEN);
    integrationsHandler.saveAppOptions(null, propertySetTest2, "GITHUB", true, user);
  }

  @Test
  public void testAuthorizationFlowHappyCase() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(user);
    logoutAndLoginAs(user);

    String authorizationCode = RandomStringUtils.randomAlphabetic(20);

    server
        .expect(requestTo("https://github.com/login/oauth/access_token"))
        .andExpect(jsonPath("$.code", is(authorizationCode)))
        .andExpect(jsonPath("$.client_id", is(githubClientId)))
        .andExpect(jsonPath("$.client_secret", is(githubSecret)))
        .andRespond(
            withSuccess(
                "{\"access_token\":\""
                    + GITHUB_ACCESS_TOKEN
                    + "\""
                    + ", \"scope\":\"user,repo\""
                    + ", \"token_type\":\"bearer\"}",
                MediaType.APPLICATION_JSON));
    String content =
        IOUtils.toString(
            this.getClass()
                .getResourceAsStream("/TestResources/github_user_repository_list_response.json"),
            "UTF-8");
    server
        .expect(requestTo("https://api.github.com/user/repos"))
        .andExpect(header("Authorization", "token " + GITHUB_ACCESS_TOKEN))
        .andRespond(withSuccess(content, MediaType.APPLICATION_JSON));
    MvcResult result =
        this.mockMvc
            .perform(
                get("/github/redirect_uri")
                    .param("code", authorizationCode)
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andExpect(view().name("connect/github/connected"))
            .andReturn();
    assertThat(
        result.getModelAndView().getModel().get("gitHubAccessToken"), is(GITHUB_ACCESS_TOKEN));

    @SuppressWarnings("unchecked")
    List<GitHubController.Repository> repositories =
        (List<Repository>) result.getModelAndView().getModel().get("gitHubRepositories");
    assertThat(
        repositories,
        hasItems(
            new GitHubController.Repository(
                "rspace-integration-test-user/test1", "First Test Repository"),
            new GitHubController.Repository(
                "rspace-integration-test-user/test2", "Second Test Repository")));
  }

  @Test
  public void testGetTreeRootFolder() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(user);
    logoutAndLoginAs(user);

    addExampleRepositories(user);

    MvcResult result =
        this.mockMvc
            .perform(
                post("/github/ajax/get_repository_tree")
                    .param("dir", "/")
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andExpect(view().name(GitHubController.GITHUB_VIEW_NAME))
            .andReturn();
    @SuppressWarnings("unchecked")
    List<GitHubController.TreeNode> nodes =
        (List<TreeNode>) result.getModelAndView().getModel().get("treeNodes");

    assertThat(nodes.size(), is(2));

    // Initialize expected node 1
    TreeNode expectedNode1 = new TreeNode();
    expectedNode1.setPath("rspace-integration-test-user/test1");
    expectedNode1.setRepository("rspace-integration-test-user/test1");
    expectedNode1.setFullPath(null);
    expectedNode1.setSha("master");
    expectedNode1.setType("tree");
    // Initialize expected node 2
    TreeNode expectedNode2 = new TreeNode();
    expectedNode2.setPath("rspace-integration-test-user/test2");
    expectedNode2.setRepository("rspace-integration-test-user/test2");
    expectedNode2.setFullPath(null);
    expectedNode2.setSha("master");
    expectedNode2.setType("tree");

    assertThat(nodes, hasItems(expectedNode1, expectedNode2));
  }

  @Test
  public void testGetTreeSubfolder() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(user);
    logoutAndLoginAs(user);

    addExampleRepositories(user);

    String content =
        IOUtils.toString(
            this.getClass().getResourceAsStream("/TestResources/github_tree_api_response.json"),
            "UTF-8");
    server
        .expect(
            requestTo(
                "https://api.github.com/repos/rspace-integration-test-user/test1/git/trees/227cf132af95d6bb43ad9119b879e19e3f209901"))
        .andExpect(header("Authorization", "token " + GITHUB_ACCESS_TOKEN))
        .andRespond(withSuccess(content, MediaType.APPLICATION_JSON));

    // URL-encoded dir string 'repository#sha hash of the folder#path to folder
    String dir =
        "rspace-integration-test-user/test1%23227cf132af95d6bb43ad9119b879e19e3f209901%23/folder/subfolder/";
    MvcResult result =
        this.mockMvc
            .perform(
                post("/github/ajax/get_repository_tree")
                    .param("dir", dir)
                    .principal(user::getUsername))
            .andExpect(status().isOk())
            .andExpect(view().name(GitHubController.GITHUB_VIEW_NAME))
            .andReturn();
    @SuppressWarnings("unchecked")
    List<GitHubController.TreeNode> nodes =
        (List<TreeNode>) result.getModelAndView().getModel().get("treeNodes");

    assertThat(nodes.size(), is(1));

    // Initialize expected node 1
    TreeNode expectedNode1 = new TreeNode();
    expectedNode1.setPath("file2");
    expectedNode1.setRepository("rspace-integration-test-user/test1");
    expectedNode1.setFullPath("/folder/subfolder/file2");
    expectedNode1.setSha("3afd81aae1693c6782a2a4329516045bc6708592");
    expectedNode1.setType("blob");

    assertThat(nodes, hasItems(expectedNode1));
  }
}
