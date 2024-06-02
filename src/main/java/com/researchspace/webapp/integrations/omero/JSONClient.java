package com.researchspace.webapp.integrations.omero;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import lombok.SneakyThrows;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * An example for using the OMERO JSON API with Java
 *
 * <p>Run it with command line parameters: --omero.webhost=http://[OMERO.Web URL]
 * --omero.servername=[SERVER_NAME] --omero.user=[USERNAME] --omero.pass=[PASSWORD]
 *
 * <p>This example client needs additional dependencies: Java API for JSON Processing
 * (https://javaee.github.io/jsonp/): <dependency org="org.glassfish" name="javax.json"
 * rev="1.0.4"/> Apache HTTPComponents (https://hc.apache.org/index.html): <dependency
 * org="org.apache.httpcomponents" name="httpcore" rev="4.4.6"> <dependency
 * org="org.apache.httpcomponents" name="httpclient" rev="4.5.3"/> <dependency
 * org="org.apache.httpcomponents" name="httpmime" rev="4.5.3">
 *
 * @author Dominik Lindner &nbsp;&nbsp;&nbsp;&nbsp; <a
 *     href="mailto:d.lindner@dundee.ac.uk">d.lindner@dundee.ac.uk</a>
 */
public class JSONClient {

  private static final Logger LOG = LoggerFactory.getLogger(JsonObject.class);

  /** The base API URL */
  private String baseURL;

  /** The base URL used for requests, including API version */
  private String requestURL;

  /** The URLs the API provides */
  private Map<String, String> serviceURLs;

  /** The http client */
  private HttpClient httpClient;

  /** The http context */
  private BasicHttpContext httpContext;

  /** The CSRF token * */
  private String token;

  /**
   * Creates a new JSON client
   *
   * @param baseURL The base API URL
   */
  @SneakyThrows
  public JSONClient(String baseURL) {
    this.baseURL = baseURL + "/api";
    BasicCookieStore cookieStore = new BasicCookieStore();
    cookieStore.clear();
    this.httpClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();
    this.httpContext = new BasicHttpContext();
    this.httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
    getVersion();
    getURLs();
  }

  /**
   * Get the available API versions
   *
   * @return See above
   * @throws Exception If something went wrong
   */
  public List<JsonObject> getVersion() throws Exception {
    JsonObject json = (JsonObject) get(baseURL);
    JsonArray jarray = json.getJsonArray("data");
    List<JsonObject> result = new ArrayList<JsonObject>();
    for (JsonValue value : jarray) {
      result.add((JsonObject) value);
    }

    JsonObject server = result.get(result.size() - 1);
    this.requestURL = server.getJsonString("url:base").getString();

    return result;
  }

  @SneakyThrows
  public Map<String, String> getURLs() {
    JsonObject json = (JsonObject) get(requestURL);

    this.serviceURLs = new HashMap<String, String>();

    for (Entry<String, JsonValue> entry : json.entrySet()) {
      this.serviceURLs.put(entry.getKey(), ((JsonString) entry.getValue()).getString());
    }

    return this.serviceURLs;
  }

  /**
   * Get the accessible servers
   *
   * @return See above
   * @throws Exception If something went wrong
   */
  public Map<String, Integer> getServers() throws Exception {
    Map<String, Integer> result = new HashMap<String, Integer>();
    String url = serviceURLs.get("url:servers");
    JsonObject json = (JsonObject) get(url);
    JsonArray data = json.getJsonArray("data");
    for (int i = 0; i < data.size(); i++) {
      JsonObject server = data.getJsonObject(i);
      result.put(server.getString("server"), server.getInt("id"));
    }
    return result;
  }

  /**
   * Request a CSRF token
   *
   * @return The CSRF token
   * @throws Exception If something went wrong
   */
  private String getCSRFToken() throws Exception {
    String url = serviceURLs.get("url:token");
    JsonObject json = (JsonObject) get(url);
    return json.getJsonString("data").getString();
  }

  /**
   * Log in a server
   *
   * @param username The username
   * @param password The password
   * @param serverId The server id
   * @return See above
   * @throws Exception If something went wrong
   */
  public JsonObject login(String username, String password, int serverId) throws Exception {
    // make sure we have all the necessary URLs
    getVersion();
    getURLs();
    this.token = getCSRFToken();
    String rootUrl = getRootUrl().replace("https://", "");
    BasicClientCookie cookie = new BasicClientCookie("csrftoken", this.token);
    cookie.setDomain(rootUrl);
    cookie.setPath("/");
    ((BasicCookieStore) this.httpContext.getAttribute(HttpClientContext.COOKIE_STORE))
        .addCookie(cookie);
    String url = serviceURLs.get("url:login");
    Map<String, String> params = new HashMap<String, String>();
    params.put("server", "" + serverId);
    params.put("username", username);
    params.put("password", password);
    try {
      JsonObject response = (JsonObject) post(url, params);
      return response.getJsonObject("eventContext");
    } catch (Exception e) {
      LOG.error("Error performing POST for login for user: {}", username, e);
    }
    return null;
  }

  @SneakyThrows(Exception.class)
  public Collection<JsonObject> listDatasetsForProject(JsonObject project) {
    List<JsonObject> result = new ArrayList<JsonObject>();
    String rootUrl = getRootUrl();
    // Gets all datasets, not limited to batch of 200 max, this call is faster than using standard
    // JSON API
    // Note that projects can have thousands of datasets and React performance degrades when that is
    // the case.
    String datasetUrl =
        rootUrl + "/webclient/api/datasets/?id=" + project.getInt("@id") + "&page=0";
    JsonObject json = (JsonObject) get(datasetUrl);
    JsonArray data = json.getJsonArray("datasets");
    for (int i = 0; i < data.size(); i++) {
      result.add(data.getJsonObject(i));
    }
    return result;
  }

  @SneakyThrows(Exception.class)
  public Collection<JsonObject> listPlatesForScreen(JsonObject screen) {
    String plateID = "" + screen.getInt("@id");
    String root = getRootUrl();
    String url = root + "/webclient/api/plates/?id=" + plateID + "&page=0";
    return getBatchesOfData(url, 0, "plates");
  }

  @SneakyThrows(Exception.class)
  public JsonObject getDataSetWithId(String id) {
    String datasetUrl = serviceURLs.get("url:datasets");
    JsonObject json = (JsonObject) get(datasetUrl + id + "?childCount=true");
    return json.getJsonObject("data");
  }

  @SneakyThrows(Exception.class)
  public JsonObject getPlateWithId(String id) {
    String plateUrl = serviceURLs.get("url:plates");
    JsonObject json = (JsonObject) get(plateUrl + id + "?childCount=true");
    return json.getJsonObject("data");
  }

  @SneakyThrows(Exception.class)
  public JsonObject getPlateAcquisitionWithId(String id) {
    String plateUrl = serviceURLs.get("url:plates");
    JsonObject json = (JsonObject) get(plateUrl + id + "?childCount=true");
    return json.getJsonObject("data");
  }

  public List<String> getAnnotations(String rootUrl, String type, Long id) {
    Map<Long, List<String>> results = getAnnotations(rootUrl, type, List.of(id));
    return results.get(id) != null ? results.get(id) : Collections.EMPTY_LIST;
  }

  @SneakyThrows(Exception.class)
  public Map<Long, List<String>> getAnnotations(String rootUrl, String type, List<Long> ids) {
    Map<Long, List<String>> results = new HashMap<>();
    if (ids.size() > 50) { // 50 appears to be MAX PARAMS value in Omero Python app src code.
      List<Long> subsetParams = new ArrayList<>();
      for (Long id : ids) {
        subsetParams.add(id);
        if (subsetParams.size() == 50) {
          results.putAll(getAnnotations(rootUrl, type, subsetParams));
          subsetParams.clear();
        }
      }
      if (subsetParams.size() > 0) {
        results.putAll(getAnnotations(rootUrl, type, subsetParams));
      }
      return results;
    }
    List<String> params = new ArrayList<>();
    for (long id : ids) {
      params.add(type + "=" + id);
    }
    String annotationsUrl = rootUrl + "/webclient/api/annotations/";
    JsonObject annotations = (JsonObject) get(annotationsUrl, params);
    return getDisplayString(annotations);
  }

  private static Map<Long, List<String>> getDisplayString(JsonObject annotationData) {
    Map<Long, List<String>> results = new HashMap<>();
    JsonArray annotations = annotationData.getJsonArray("annotations");
    for (int i = 0; i < annotations.size(); i++) {
      JsonObject value = (JsonObject) annotations.get(i);
      Long parentID =
          value.getJsonObject("link").getJsonObject("parent").getJsonNumber("id").longValue();
      if (value.get("ns") != null && !value.get("ns").toString().equals("null")) {
        String nameSpace = value.getString("ns").toLowerCase();
        if (nameSpace.contains("rating")) {
          String ratingStr = "Rating = " + value.get("longValue").toString();
          addToMapAndMakeNewEntryIfNeeded(results, parentID, ratingStr);
          continue;
        }
      }
      String jsonClass = value.getString("class").toLowerCase();
      if (jsonClass.contains("mapannotation")) {
        JsonArray data = value.getJsonArray("values");
        for (int j = 0; j < data.size(); j++) {
          JsonArray valuePair = (JsonArray) data.get(j);
          String vPair = valuePair.getString(0) + " = " + valuePair.getString(1);
          addToMapAndMakeNewEntryIfNeeded(results, parentID, vPair);
        }
      } else if (jsonClass.contains("longannotation")) {
        addToMapAndMakeNewEntryIfNeeded(results, parentID, value.get("longValue").toString());
      } else if (jsonClass.contains("doubleannotation")) {
        addToMapAndMakeNewEntryIfNeeded(results, parentID, value.get("doubleValue").toString());
      } else if (jsonClass.contains("booleanannotation")) {
        addToMapAndMakeNewEntryIfNeeded(results, parentID, value.get("boolValue").toString());
      } else if (jsonClass.contains("termannotation")) {
        addToMapAndMakeNewEntryIfNeeded(
            results, parentID, "Term = " + value.get("termValue").toString());
      } else if (jsonClass.contains("comment")) {
        addToMapAndMakeNewEntryIfNeeded(
            results, parentID, "Comment = " + value.get("textValue").toString());
      } else if (jsonClass.contains("tag")) {
        addToMapAndMakeNewEntryIfNeeded(
            results, parentID, "Tag = " + value.get("textValue").toString());
      } else if (jsonClass.contains("XmlAnnotation")) {
        addToMapAndMakeNewEntryIfNeeded(
            results, parentID, "Xml = " + value.get("textValue").toString());
      } else if (jsonClass.contains("file")) {
        addToMapAndMakeNewEntryIfNeeded(
            results, parentID, "File = " + value.getJsonObject("file").get("path").toString());
      } else if (jsonClass.contains("time")) {
        addToMapAndMakeNewEntryIfNeeded(
            results, parentID, "Time = " + value.get("timeValue").toString());
      }
    }
    return results;
  }

  private static void addToMapAndMakeNewEntryIfNeeded(
      Map<Long, List<String>> target, Long key, String value) {
    if (target.get(key) == null) {
      List<String> entryList = new ArrayList<>();
      entryList.add(value);
      target.put(key, entryList);
    } else {
      target.get(key).add(value);
    }
  }

  public Collection<JsonObject> listProjects() throws Exception {
    String url = serviceURLs.get("url:projects") + "?childCount=true";
    return getBatchesOfData(url, 0);
  }

  public JsonObject getProjectWithId(long projectid) throws Exception {
    String url = serviceURLs.get("url:projects");
    JsonObject json = (JsonObject) get(url + "/" + projectid);
    return json.getJsonObject("data");
  }

  public JsonObject getScreenWithId(long screenid) throws Exception {
    String url = serviceURLs.get("url:screens");
    JsonObject json = (JsonObject) get(url + "/" + screenid);
    return json.getJsonObject("data");
  }

  public Collection<JsonObject> listScreens() throws Exception {
    String url = serviceURLs.get("url:screens") + "?childCount=true";
    return getBatchesOfData(url, 0);
  }

  @SneakyThrows
  public Map<Long, String> getThumbnails(String urlString, List<String> params) throws Exception {
    HttpGet httpGet = null;
    Map<Long, String> results = new HashMap<>();
    if (params != null
        && params.size() > 50) { // 50 appears to be MAX PARAMS value in Omero Python app src code.
      List<String> subsetParams = new ArrayList<>();
      for (String param : params) {
        subsetParams.add(param);
        if (subsetParams.size() == 50) {
          results.putAll(getThumbnails(urlString, subsetParams));
          subsetParams.clear();
        }
      }
      if (subsetParams.size() > 0) {
        results.putAll(getThumbnails(urlString, subsetParams));
      }
      return results;
    }
    if (params == null || params.isEmpty()) httpGet = new HttpGet(urlString);
    else {
      URIBuilder builder = new URIBuilder(urlString);
      for (String param : params) {
        String[] keyValPair = param.split("=");
        builder.addParameter(keyValPair[0], keyValPair[1]);
      }
      builder.addParameter("_", "" + System.currentTimeMillis());
      httpGet = new HttpGet(builder.build());
    }
    HttpResponse res = httpClient.execute(httpGet);

    try (JsonReader reader =
        Json.createReader(
            new BufferedReader(new InputStreamReader(res.getEntity().getContent())))) {
      JsonObject resultsJson = (JsonObject) reader.read();
      for (String key : resultsJson.keySet()) {
        results.put(
            Long.parseLong(key),
            !resultsJson.get(key).toString().equalsIgnoreCase("null")
                ? resultsJson.getString(key)
                : "");
      }
    }
    return results;
  }

  public String getRootUrl() {
    return this.getURLs().get("url:servers").replace("/api/v0/servers/", "");
  }

  public Collection<JsonObject> listImagesForDataset(JsonObject jsonDataset) throws Exception {
    String url = jsonDataset.getString("url:images");
    return getBatchesOfData(url, 0);
  }

  private Collection<JsonObject> getBatchesOfData(String url, int offset) throws Exception {
    return getBatchesOfData(url, offset, "");
  }

  private Collection<JsonObject> getBatchesOfData(String url, int offset, String dataName)
      throws Exception {
    List<JsonObject> result = new ArrayList<JsonObject>();
    String toAdd = url.contains("?") ? "&" : "?";
    JsonObject json = (JsonObject) get(url + toAdd + "offset=" + offset);
    String dataKey = StringUtils.hasText(dataName) ? dataName : "data";
    JsonArray data = json.getJsonArray(dataKey);
    for (int i = 0; i < data.size(); i++) {
      result.add(data.getJsonObject(i));
    }
    JsonObject meta = json.getJsonObject("meta");
    if (meta != null) {
      int actualFetched = data.size();
      int actualTotal = meta.getInt("totalCount");
      if (actualTotal > (actualFetched + offset)) {
        int numLeftToFetch = actualTotal - (actualFetched + offset);
        if (numLeftToFetch > 0) {
          result.addAll(getBatchesOfData(url, (actualTotal - numLeftToFetch)));
        }
      }
    }
    return result;
  }

  public Collection<JsonObject> listWellsForPlate(JsonObject jsonPlate) throws Exception {
    String url = jsonPlate.getString("url:wells");
    return getBatchesOfData(url, 0);
  }

  public Collection<JsonObject> listWellsForPlateAcquisition(
      JsonObject jsonPlate, long plateAcquisitionID, int wellIndex) throws Exception {
    String url = jsonPlate.getString("url:plateacquisitions");
    if (plateAcquisitionID
        == jsonPlate
            .getJsonNumber("@id")
            .longValue()) { // fake plateAcquisitionID used when plate links directly to wells
      JsonArray wellsUrlArray = jsonPlate.getJsonArray("url:wellsampleindex_wells");
      String wellsURL = wellsUrlArray.getString(wellIndex);
      return getBatchesOfData(wellsURL, 0);
    } else {
      JsonObject json = (JsonObject) get(url);
      JsonArray data = json.getJsonArray("data");
      for (int i = 0; i < data.size(); i++) {
        JsonObject jsonPlateAcquisition = data.getJsonObject(i);
        if (jsonPlateAcquisition.getInt("@id") == plateAcquisitionID) {
          JsonArray wellsUrlArray = jsonPlateAcquisition.getJsonArray("url:wellsampleindex_wells");
          String wellsURL = wellsUrlArray.getString(wellIndex);
          return getBatchesOfData(wellsURL, 0);
        }
      }
    }
    return null;
  }

  @SneakyThrows
  public Collection<JsonObject> listAcquisitionsForPlate(JsonObject jsonPlate) throws Exception {
    String url = jsonPlate.getString("url:plateacquisitions");
    return getBatchesOfData(url, 0);
  }

  @SneakyThrows // contains more data than images retrieved in batch
  public JsonObject getSingleImage(JsonObject jsonImage) throws Exception {
    String url = jsonImage.getString("url:image");
    JsonObject json = (JsonObject) get(url);
    return json.getJsonObject("data");
  }

  @SneakyThrows // contains more data than images retrieved in batch
  public JsonObject getSingleImage(long imageID) throws Exception {
    String imageUrl = serviceURLs.get("url:images");
    JsonObject json = (JsonObject) get(imageUrl + imageID);
    return json.getJsonObject("data");
  }

  @SneakyThrows
  public Collection<JsonObject> getRoisForImage(JsonObject imageJson) {
    String roisUrl = imageJson.get("url:rois") != null ? imageJson.getString("url:rois") : "";
    if (StringUtils.hasText(roisUrl)) {
      return getBatchesOfData(roisUrl, 0);
    }
    return null;
  }

  /**
   * Update an object
   *
   * @param object The JSON object
   * @return The updated object
   * @throws Exception If something went wrong
   */
  public JsonObject update(JsonObject object) throws Exception {
    String url = serviceURLs.get("url:save");
    JsonObject updatedObject = (JsonObject) put(url, object);
    return updatedObject;
  }

  /**
   * Perform a get request
   *
   * @param urlString The request URL
   * @return The response
   * @throws Exception Exception If something went wrong
   */
  private JsonStructure get(String urlString) throws Exception {
    return get(urlString, Collections.EMPTY_LIST);
  }

  private JsonStructure get(String urlString, List<String> params) throws Exception {
    long currTime = System.currentTimeMillis();
    URIBuilder builder = new URIBuilder(urlString);
    HttpGet httpGet = null;
    if (params != null) {
      for (String param : params) {
        String[] keyValPair = param.split("=");
        builder.addParameter(keyValPair[0], keyValPair[1]);
      }
    }
    builder.addParameter("_", "" + currTime);
    httpGet = new HttpGet(builder.build());

    HttpResponse res = httpClient.execute(httpGet);
    try (JsonReader reader =
        Json.createReader(
            new BufferedReader(new InputStreamReader(res.getEntity().getContent())))) {
      return reader.read();
    }
  }

  /**
   * Perform a PUT request
   *
   * @param url The request URL
   * @param data The JSON data
   * @return The response
   * @throws HttpException If something went wrong
   * @throws ClientProtocolException If something went wrong
   * @throws IOException If something went wrong
   */
  private JsonStructure put(String url, JsonObject data)
      throws HttpException, ClientProtocolException, IOException {
    HttpPut httpPut = new HttpPut(url);
    if (data != null) {
      StringEntity requestEntity = new StringEntity(data.toString(), ContentType.APPLICATION_JSON);
      httpPut.setEntity(requestEntity);
    }
    httpPut.addHeader("X-CSRFToken", this.token);
    httpPut.addHeader("Referer", url);
    HttpResponse res = httpClient.execute(httpPut);
    if (res.getStatusLine().getStatusCode() != 200)
      throw new HttpException("PUT failed. URL: " + url + " Status:" + res.getStatusLine());

    try (JsonReader reader =
        Json.createReader(
            new BufferedReader(new InputStreamReader(res.getEntity().getContent())))) {
      return reader.read();
    }
  }

  /**
   * Perform a POST request
   *
   * @param url The request URL
   * @param params The paramters
   * @return The response
   * @throws HttpException If something went wrong
   * @throws ClientProtocolException If something went wrong
   * @throws IOException If something went wrong
   */
  private JsonStructure post(String url, Map<String, String> params)
      throws HttpException, ClientProtocolException, IOException {

    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    if (params != null && !params.isEmpty()) {
      for (Entry<String, String> entry : params.entrySet()) {
        nvps.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
      }
    }
    HttpUriRequest request =
        RequestBuilder.post()
            .setUri(url)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
            .setHeader("X-CSRFToken", this.token)
            .setHeader("Referer", url)
            .setEntity(new UrlEncodedFormEntity(nvps))
            .build();
    HttpResponse res = httpClient.execute(request);
    if (res.getStatusLine().getStatusCode() != 200)
      throw new HttpException("POST failed. URL: " + url + " Status:" + res.getStatusLine());

    try (JsonReader reader =
        Json.createReader(
            new BufferedReader(new InputStreamReader(res.getEntity().getContent())))) {
      return reader.read();
    }
  }

  @SneakyThrows
  public byte[] getRenderedThumbnail(String urlString, long imageID, int size) {
    URIBuilder builder = new URIBuilder(urlString + imageID + "/" + size + "/");
    HttpGet httpGet = new HttpGet(builder.build());
    HttpResponse res = httpClient.execute(httpGet);
    InputStream is = res.getEntity().getContent();
    return is.readAllBytes();
  }
}
