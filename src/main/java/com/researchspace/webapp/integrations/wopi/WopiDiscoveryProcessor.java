package com.researchspace.webapp.integrations.wopi;

import com.researchspace.properties.IPropertyHolder;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiAction;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiApp;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiData;
import com.researchspace.webapp.integrations.wopi.WopiDiscoveryServiceHandler.WopiPublicKeys;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlAction;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlApp;
import com.researchspace.webapp.integrations.wopi.models.xml.XmlWopiDiscovery;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

/**
 * Helper class to process the Office Online WOPI Discovery XML, and convert it into the format we
 * need. Methods return futures because this processing might take some time. Methods are mostly
 * public so that they can be unit tested. The main method to use is the one retrieving WopiData.
 */
public class WopiDiscoveryProcessor {

  @Autowired private IPropertyHolder propertyHolder;

  @Value("${msoffice.wopi.discovery.url}")
  private String msOfficeWopiDiscoveryUrl;

  @Value("${collabora.wopi.discovery.url}")
  private String collaboraWopiDiscoveryUrl;

  @Autowired private WopiDiscoveryServiceHandler wopiHandler;

  /* discovery.xml may contain apps we don't want to show (see RSPAC-2066) */
  private static final List<String> IGNORED_APPS_LIST = Arrays.asList("WordPdf");

  private Logger log = LoggerFactory.getLogger(getClass());

  /** Function that updates the discovery XML from reading the version online */
  @Async
  @Scheduled(initialDelay = 1000L * 10, fixedRate = 1000L * 60 * 60 * 12)
  public void updateData() {
    if (!propertyHolder.isMsOfficeEnabled() && !propertyHolder.isCollaboraEnabled()) {
      return;
    }
    Reader reader;
    XmlWopiDiscovery discovery;
    WopiData data;
    try {
      if (propertyHolder.isCollaboraEnabled()) {
        log.info("Updating Collabora Online WOPI discovery data");
        reader = retrieveDiscoveryXml(collaboraWopiDiscoveryUrl).get();

      } else {
        log.info("Updating Office Online WOPI discovery data");
        reader = retrieveDiscoveryXml(msOfficeWopiDiscoveryUrl).get();
      }

      log.debug("Reader retrieved");
      discovery = parseDiscoveryXml(reader);
      log.debug("XML parsed");
      data = mapDiscoveryData(discovery);
      wopiHandler.setData(data);
      log.info("WOPI discovery data successfully updated.");
    } catch (Exception e) {
      log.error("Exception on updating WOPI discovery data", e);
    }
  }

  /**
   * Makes a GET request for the Office Online WOPI discovery XML file and returns a reader for it
   *
   * @param url of file to read from
   * @return String reader to read from the file (non XML characters in attributes already escaped)
   */
  @Async
  public Future<StringReader> retrieveDiscoveryXml(String url) {
    RestTemplate restTemplate = new RestTemplate();
    ResponseEntity<String> result = restTemplate.getForEntity(url, String.class);
    if (result.getStatusCode() != HttpStatus.OK) {
      throw new RuntimeException(
          "Office Online WOPI discovery URL returned status code "
              + result.getStatusCode().toString()
              + " Cache has not been updated.");
    }
    return new AsyncResult<>(new StringReader(result.getBody()));
  }

  /**
   * Given a reader to read the discovery XML file, will return it parsed into Java objects directly
   * matching the structure of the file Future can return JAXBException
   *
   * @param reader reader to read the XML discovery file. Invalid XML characters must be escaped
   *     beforehand. (Spring's RestTemplate does this)
   * @return The XML file parsed into a Java object matching the XML structure
   */
  public XmlWopiDiscovery parseDiscoveryXml(Reader reader) throws JAXBException {
    JAXBContext jc = JAXBContext.newInstance(XmlWopiDiscovery.class);
    Unmarshaller unmarshaller = jc.createUnmarshaller();
    return (XmlWopiDiscovery) unmarshaller.unmarshal(reader);
  }

  /**
   * Maps the data read from the XML discovery doc to the format we use in RSpace
   *
   * @param discovery The root XML node
   * @return the WOPI discovery data
   */
  public WopiData mapDiscoveryData(XmlWopiDiscovery discovery) {
    Map<String, Map<String, WopiAction>> availableActionsForExt = new HashMap<>();
    Map<String, WopiApp> supportedExtensions = new HashMap<>();

    List<XmlApp> apps = discovery.getApps();
    apps.removeIf(app -> IGNORED_APPS_LIST.contains(app.getName()));

    for (XmlApp xmlApp : apps) {
      WopiApp app = new WopiApp(xmlApp);
      for (XmlAction xmlAction : xmlApp.getActions()) {
        // We don't currently use actions with no file extension given
        if (xmlAction.getFileExtension() == null) continue;
        WopiAction action = new WopiAction(xmlAction, app);
        String ext = xmlAction.getFileExtension();
        availableActionsForExt.putIfAbsent(ext, new HashMap<>());
        availableActionsForExt.get(ext).put(action.getName(), action);
        // Sometimes there won't be a default app to open a extension, so we set it to the
        // first one we see, and override it only if we get an action that is marked as the
        // default.
        if (!supportedExtensions.containsKey(ext) || xmlAction.isAppDefault())
          supportedExtensions.put(ext, app);
      }
    }

    WopiPublicKeys keys = new WopiPublicKeys(discovery.getProofKey());
    return new WopiData(availableActionsForExt, supportedExtensions, keys);
  }
}
