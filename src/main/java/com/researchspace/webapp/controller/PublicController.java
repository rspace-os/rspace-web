package com.researchspace.webapp.controller;

import com.researchspace.core.util.ResponseUtil;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.service.archive.IExportUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URLConnection;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** This controller returns public information. */
@Controller
@RequestMapping("/public")
public class PublicController extends BaseController {

  @Autowired private IExportUtils exportUtils;

  @Autowired private ResourceLoader resourceLoader;

  @Autowired private MaintenanceManager maintenanceMgr;

  private String actualImageName;

  /**
   * @param filename the filename of an exported file as it is in the file0-store - NOT the original
   *     file name.
   * @param res
   * @throws URISyntaxException
   * @throws IOException
   * @throws Exception
   */
  @ResponseBody
  // All newly issued links are /publish/filename, but it supports previously issued
  // /publishpdf/filename links too.
  @GetMapping({"/publishpdf/{filename:.+}", "/publish/{filename:.+}"})
  public void getExportedPublishedFile(
      @PathVariable("filename") String filename, HttpServletResponse res)
      throws IOException, URISyntaxException {
    if (filename.endsWith(".doc") || filename.endsWith(".pdf")) {
      exportUtils.display(filename, null, res);
    } else {
      throw new IllegalArgumentException("Only .doc or .pdf files can be published.");
    }
  }

  /**
   * Gets a banner image according to the follwoing rules:
   *
   * <ul>
   *   <li>If banner image path is set in deployment property, and this is a gif,png or jpg, return
   *       this
   *   <li>If path is to any other file, or doesn't exist, return default
   *   <li>If deployment property doesn't exist, and if 'bannerName' request property is set, will
   *       try to load this image from the webapp's icons folder.
   *   <li>In any failure, return the default RSpace logo banner image.
   *
   * @param bannerName
   * @return
   * @throws IOException
   */
  @GetMapping("banner")
  public ResponseEntity<byte[]> getBanner(
      @RequestParam(value = "bannerName", required = false) String bannerName) throws IOException {
    String path = properties.getBannerImagePath();
    log.info("Banner image path is {}", path);

    byte[] image = null;
    // use default
    if (StringUtils.isEmpty(path)) {
      log.info("Banner image path is empty, returning default");
      return returnDefaultImage(bannerName);
    }
    Resource banner = resourceLoader.getResource(path);
    if (banner.exists()) {
      log.info("alternative banner image exists...");
      try {
        InputStream in = banner.getInputStream();
        final HttpHeaders headers = new HttpHeaders();
        String mimetype = URLConnection.guessContentTypeFromName(banner.getFilename());
        MediaType mediaType = MediaType.parseMediaType(mimetype);
        log.info("Guessed mimetype is {}", mimetype);
        if (!isValidImage(mediaType)) {
          return returnDefaultImage(bannerName);
        } else {
          headers.setContentType(mediaType);
          image = IOUtils.toByteArray(in);
          cacheResponseForADay(headers);
          actualImageName = banner.getFilename();
          return new ResponseEntity<byte[]>(image, headers, HttpStatus.OK);
        }
      } catch (IOException e) {
        log.warn("Custom banner at {} could not be read", path);
        throw e;
      }
    } else {
      log.info("alternative banner image could not be found, reloading default.");
      return returnDefaultImage(bannerName);
    }
  }

  void cacheResponseForADay(final HttpHeaders headers) {
    setCacheTimeInBrowser(ResponseUtil.DAY, null, headers);
  }

  private boolean isValidImage(MediaType mediaType) {
    return MediaType.IMAGE_GIF.equals(mediaType)
        || MediaType.IMAGE_JPEG.equals(mediaType)
        || MediaType.IMAGE_PNG.equals(mediaType);
  }

  InputStream getIconImageFromFolder(String pathRelativeToImages) {
    return servletContext.getResourceAsStream("/images/" + pathRelativeToImages);
  }

  private ResponseEntity<byte[]> returnDefaultImage(String bannerName) throws IOException {
    byte[] image;
    String fallBackName = "mainLogoN2.png";
    String nameToLoad = null;
    if (!StringUtils.isBlank(bannerName)) {
      nameToLoad = bannerName;
    }
    InputStream in = getIconImageFromFolder(nameToLoad);
    actualImageName = nameToLoad;
    if (in == null) {
      in = getIconImageFromFolder(fallBackName);
      actualImageName = fallBackName;
    }
    image = IOUtils.toByteArray(in);
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);

    cacheResponseForADay(headers);
    return new ResponseEntity<byte[]>(image, headers, HttpStatus.OK);
  }

  /**
   * Simple status returning message if server is running
   *
   * @return
   */
  @ResponseBody
  @GetMapping("/status")
  public String status() {
    return "RSpace OK";
  }

  /**
   * Simple check if server is in maintenance mode (i.e. users can't log in)
   *
   * @return
   */
  @ResponseBody
  @GetMapping("/maintenanceStatus")
  public String maintenanceStatus() {
    ScheduledMaintenance nextMaintenance = maintenanceMgr.getNextScheduledMaintenance();
    boolean maintenanceInProgress =
        nextMaintenance != null && !nextMaintenance.getCanUserLoginNow();
    return maintenanceInProgress ? "Maintenance in progress" : "No maintenance";
  }

  @ResponseBody
  @GetMapping("/version")
  public String version() {
    return getText("webapp.version");
  }

  @ResponseBody
  @GetMapping("/bannerImageName")
  public String bannerImgName() {
    return actualImageName;
  }

  /*
   * ======================
   *     for testing
   * ======================
   */

  public void setExportUtils(IExportUtils exportUtils) {
    this.exportUtils = exportUtils;
  }

  protected void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  protected void setMaintenanceManager(MaintenanceManager maintenanceMgr) {
    this.maintenanceMgr = maintenanceMgr;
  }
}
