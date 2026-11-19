package com.axiope.webapp.jsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.field.ChoiceField;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.NumberField;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.RadioField;
import com.researchspace.model.field.RadioFieldForm;
import jakarta.el.ELProcessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders the document editor's field JSPs from source with the real Jakarta EL implementation and
 * real field entities, so the display-rule markup (picklist placeholder, stored choice and radio
 * data) is checked as the page emits it. Only the tags these JSPs use are supported; anything else
 * fails the render rather than being skipped.
 */
class EditorFieldJspRenderingTest {

  private static final Path INCLUDE_DIR =
      Path.of("src", "main", "webapp", "WEB-INF", "pages", "workspace", "editor", "include");

  @Test
  void picklistWithoutDataShowsASelectedEmptyPlaceholder() {
    Element select = renderPickList("").selectFirst("select.formPickList");

    assertNotNull(select);
    assertFalse(select.attr("data-placeholder").isBlank());
    Element first = select.child(0);
    assertEquals("", first.val());
    assertTrue(first.hasAttr("selected"));
    assertEquals(1, select.select("option[selected]").size());
  }

  @Test
  void picklistWithDataSelectsItAndHasNoPlaceholder() {
    Document page = renderPickList("No");
    Element select = page.selectFirst("select.formPickList");

    assertNotNull(select);
    assertFalse(select.hasAttr("data-placeholder"));
    Elements selected = select.select("option[selected]");
    assertEquals(1, selected.size());
    assertEquals("No", selected.first().val());
    assertEquals("No", page.selectFirst("tr.field").attr("data-field-value"));
  }

  @Test
  void choiceRowCarriesItsStoredDataForDisplayRules() {
    ChoiceFieldForm form = new ChoiceFieldForm("Controls");
    form.setChoiceOptions("fieldChoices=Gloves&fieldChoices=Goggles");
    ChoiceField field = new ChoiceField(form);
    field.setId(13L);
    field.setFieldData("fieldSelectedChoicesFinal_13=Removed");

    Element row = render("choiceField.jsp", field).selectFirst("tr.field");

    assertNotNull(row);
    assertEquals("CHOICE", row.attr("data-field-type"));
    assertEquals("fieldSelectedChoicesFinal_13=Removed", row.attr("data-field-value"));
  }

  @Test
  void numberRowHasNoStoredDataAttribute() {
    NumberField field = new NumberField(new NumberFieldForm("Dose"));
    field.setId(14L);
    field.setFieldData("5");

    Element row = render("numberField.jsp", field).selectFirst("tr.field");

    assertNotNull(row);
    assertFalse(row.hasAttr("data-field-value"));
  }

  private static Document renderPickList(String fieldData) {
    RadioFieldForm form = new RadioFieldForm("Hazard?");
    form.setRadioOption("fieldRadios=Yes&fieldRadios=No");
    form.setShowAsPickList(true);
    RadioField field = new RadioField(form);
    field.setId(11L);
    field.setFieldData(fieldData);
    return render("pickListField.jsp", field);
  }

  private static Document render(String jsp, Field field) {
    ELProcessor el = new ELProcessor();
    el.defineBean("field", field);
    String html = new Renderer(el).render(preprocess(read(INCLUDE_DIR.resolve(jsp))));
    return Jsoup.parse("<table>" + html + "</table>");
  }

  /** Inlines local includes and replaces the message and asset tags with their keys. */
  private static String preprocess(String source) {
    Matcher includes = Pattern.compile("<%@\\s*include\\s+file=\"([^\"]+)\"\\s*%>").matcher(source);
    StringBuilder inlined = new StringBuilder();
    while (includes.find()) {
      String file = includes.group(1);
      String replacement = file.startsWith("/") ? "" : preprocess(read(INCLUDE_DIR.resolve(file)));
      includes.appendReplacement(inlined, Matcher.quoteReplacement(replacement));
    }
    includes.appendTail(inlined);
    return inlined
        .toString()
        .replaceAll("(?s)<%--.*?--%>", "")
        .replaceAll("<spring:message\\s+code=['\"]([^'\"]+)['\"][^>]*/>", "[$1]")
        .replaceAll("<rst:assetUrl\\s+value=['\"]([^'\"]+)['\"]\\s*/>", "$1");
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read " + file, e);
    }
  }

  /** Evaluates c:if, c:forEach, c:set and ${...} as the JSTL tags would. */
  private static final class Renderer {
    private static final Pattern TAG =
        Pattern.compile("<(/?)c:(\\w+)((?:\\s+\\w+=\"[^\"]*\")*)\\s*(/?)>|<\\w+:\\w+");
    private static final Pattern ATTRIBUTE = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]*)}");

    private final ELProcessor el;

    Renderer(ELProcessor el) {
      this.el = el;
    }

    String render(String source) {
      StringBuilder out = new StringBuilder();
      Matcher tag = TAG.matcher(source);
      int position = 0;
      while (tag.find(position)) {
        out.append(substitute(source.substring(position, tag.start())));
        if (tag.group(2) == null) {
          throw new IllegalStateException("Unsupported tag at: " + tag.group());
        }
        String name = tag.group(2);
        Map<String, String> attributes = attributes(tag.group(3));
        if (name.equals("set")) {
          el.defineBean(attributes.get("var"), evaluateAttribute(attributes.get("value")));
          position = tag.end();
          continue;
        }
        int bodyEnd = matchingClose(source, tag.end(), name);
        String body = source.substring(tag.end(), bodyEnd);
        position = bodyEnd + ("</c:" + name + ">").length();
        if (name.equals("if")) {
          if (Boolean.TRUE.equals(evaluateAttribute(attributes.get("test")))) {
            out.append(render(body));
          }
        } else if (name.equals("forEach")) {
          Object items = evaluateAttribute(attributes.get("items"));
          for (Object item : (Iterable<?>) items) {
            el.defineBean(attributes.get("var"), item);
            out.append(render(body));
          }
        } else {
          throw new IllegalStateException("Unsupported tag c:" + name);
        }
      }
      return out.append(substitute(source.substring(position))).toString();
    }

    private static int matchingClose(String source, int from, String name) {
      Matcher tag = Pattern.compile("<(/?)c:" + name + "\\b[^>]*?(/?)>").matcher(source);
      Deque<Integer> open = new ArrayDeque<>();
      open.push(from);
      int position = from;
      while (tag.find(position)) {
        position = tag.end();
        if (!tag.group(1).isEmpty()) {
          open.pop();
          if (open.isEmpty()) {
            return tag.start();
          }
        } else if (tag.group(2).isEmpty()) {
          open.push(tag.start());
        }
      }
      throw new IllegalStateException("Unclosed c:" + name);
    }

    private static Map<String, String> attributes(String source) {
      Map<String, String> attributes = new LinkedHashMap<>();
      Matcher attribute = ATTRIBUTE.matcher(source);
      while (attribute.find()) {
        attributes.put(attribute.group(1), attribute.group(2));
      }
      return attributes;
    }

    private Object evaluateAttribute(String value) {
      Matcher expression = EXPRESSION.matcher(value);
      return expression.matches() ? el.eval(expression.group(1)) : value;
    }

    /** Like EscapeXmlELResolver, every value is escaped. */
    private String substitute(String text) {
      Matcher expression = EXPRESSION.matcher(text);
      StringBuilder out = new StringBuilder();
      while (expression.find()) {
        Object value = el.eval(expression.group(1));
        String escaped = value == null ? "" : HtmlUtils.htmlEscape(String.valueOf(value));
        expression.appendReplacement(out, Matcher.quoteReplacement(escaped));
      }
      return expression.appendTail(out).toString();
    }
  }
}
