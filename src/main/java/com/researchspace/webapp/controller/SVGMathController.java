package com.researchspace.webapp.controller;

import static org.apache.commons.lang.StringUtils.isBlank;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.RSMath;
import com.researchspace.model.User;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RSMathManager;
import java.io.IOException;
import java.io.StringReader;
import javax.servlet.http.HttpServletResponse;
import javax.xml.XMLConstants;
import org.apache.commons.lang.StringUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The controller for testing SVG image retrieval (RSPAC-779). It stores posted data, and provides
 * it (as 'image/svg+xml' content) when queried.
 */
@Controller
@RequestMapping({"/svg", "/public/publicView/svg"})
public class SVGMathController extends BaseController {

  private static final String SVG_NS = "http://www.w3.org/2000/svg";

  private @Autowired MediaManager mediaMgr;
  private @Autowired RSMathManager mathMgr;

  @GetMapping("/{mathId}")
  public void getSvgImage(
      @PathVariable Long mathId,
      @RequestParam(value = "revision", required = false) Integer revision,
      HttpServletResponse response)
      throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    try {
      RSMath math = mathMgr.get(mathId, revision, subject, true);
      byte[] data = math.getMathSvg().getData();
      response.setContentType(MediaUtils.SVG_XML);
      response.setContentLength(data.length);
      response.getOutputStream().write(data);
    } catch (DataAccessException e) {
      response.sendError(HttpStatus.NOT_FOUND.value());
    }
  }

  @PostMapping
  @ResponseBody
  public Long saveSvg(
      @RequestParam("svg") String svg,
      @RequestParam("fieldId") Long fieldId,
      @RequestParam("latex") String latex,
      @RequestParam(name = "mathId", required = false) Long mathId) {
    User subject = userManager.getAuthenticatedUserInSession();
    validate(svg, latex);
    RSMath math = mediaMgr.saveMath(svg, fieldId, latex, mathId, subject);
    return math.getId();
  }

  private void validate(String svg, String latex) {
    String msg = null;
    if (isBlank(latex)) {
      msg = getText("errors.required", new Object[] {"Latex"});
      throw new IllegalArgumentException(msg);
    }
    if (isBlank(svg)) {
      msg = getText("errors.required", new Object[] {"SVG content"});
      throw new IllegalArgumentException(msg);
    }
    if (latex.length() > RSMath.LATEX_COLUMN_SIZE) {
      msg = getText("errors.maxlength", new Object[] {"Latex", RSMath.LATEX_COLUMN_SIZE + ""});
      throw new IllegalArgumentException(msg);
    }
    msg = validateSVGXML(svg, msg);
    if (msg != null) {
      log.warn(msg);
      throw new IllegalArgumentException(msg);
    }
  }

  private String validateSVGXML(String svg, String msg) {
    SAXBuilder builder = new SAXBuilder();
    builder.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    builder.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    // build a JDOM2 Document using the SAXBuilder.
    try {
      Document jdomDoc = builder.build(new StringReader(svg));
      Namespace ns = jdomDoc.getRootElement().getNamespace();
      if (!SVG_NS.equals(ns.getURI())) {
        msg = getText("errors.invalidxml.ns", new Object[] {"SVG", SVG_NS, ns.getURI()});
      }
    } catch (JDOMException | IOException e) {
      log.warn(e.getMessage());
      msg = getText("errors.invalidxml", new Object[] {"SVG", StringUtils.abbreviate(svg, 255)});
    }
    return msg;
  }
}
