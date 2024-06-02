package com.researchspace.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.properties.IPropertyHolder;
import java.util.ArrayList;
import java.util.List;

/** Java representation of a SlackAttachment */
public class SlackAttachment {
  private String fallback;
  private String color;
  private String pretext;

  @JsonProperty("author_name")
  private String authorName;

  @JsonProperty("author_link")
  private String authorLink;

  @JsonProperty("author_icon")
  private String authorIcon;

  private String title;

  @JsonProperty("title_link")
  private String titleLink;

  private String text;
  private String footer;

  @JsonProperty("footer_icon")
  private String footerIcon;

  public SlackAttachment() {}

  public SlackAttachment(IPropertyHolder props, IRSpaceDoc document) {
    super();

    setTitle(document.getName());
    setTitleLink(props.getServerUrl() + "/globalId/" + document.getGlobalIdentifier());
    setColor(ExternalMessageSender.RSPACE_BLUE);
    AttachmentField field = new AttachmentField("owner", document.getOwner().getFullName());
    AttachmentField field2 = new AttachmentField("ID", document.getGlobalIdentifier());
    addField(field);
    addField(field2);
  }

  private List<AttachmentField> fields = new ArrayList<>();

  public void addField(AttachmentField field) {
    this.fields.add(field);
  }

  public List<AttachmentField> getFields() {
    return fields;
  }

  public void setFields(List<AttachmentField> fields) {
    this.fields = fields;
  }

  public String getAuthorName() {
    return authorName;
  }

  public void setAuthorName(String authorName) {
    this.authorName = authorName;
  }

  public String getAuthorLink() {
    return authorLink;
  }

  public void setAuthorLink(String authorLink) {
    this.authorLink = authorLink;
  }

  public String getAuthorIcon() {
    return authorIcon;
  }

  public void setAuthorIcon(String authorIcon) {
    this.authorIcon = authorIcon;
  }

  public String getFooterIcon() {
    return footerIcon;
  }

  public void setFooterIcon(String footerIcon) {
    this.footerIcon = footerIcon;
  }

  private String ts;

  public String getFallback() {
    return fallback;
  }

  public void setFallback(String fallback) {
    this.fallback = fallback;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public String getPretext() {
    return pretext;
  }

  public void setPretext(String pretext) {
    this.pretext = pretext;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getTitleLink() {
    return titleLink;
  }

  public void setTitleLink(String titleLink) {
    this.titleLink = titleLink;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getFooter() {
    return footer;
  }

  public void setFooter(String footer) {
    this.footer = footer;
  }

  public String getTs() {
    return ts;
  }

  public void setTs(String ts) {
    this.ts = ts;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((text == null) ? 0 : text.hashCode());
    result = prime * result + ((title == null) ? 0 : title.hashCode());
    result = prime * result + ((ts == null) ? 0 : ts.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    SlackAttachment other = (SlackAttachment) obj;
    if (text == null) {
      if (other.text != null) return false;
    } else if (!text.equals(other.text)) return false;
    if (title == null) {
      if (other.title != null) return false;
    } else if (!title.equals(other.title)) return false;
    if (ts == null) {
      if (other.ts != null) return false;
    } else if (!ts.equals(other.ts)) return false;
    return true;
  }

  @Override
  public String toString() {
    return "SlackAttachment [fallback="
        + fallback
        + ", color="
        + color
        + ", pretext="
        + pretext
        + ", authorName="
        + authorName
        + ", authorLink="
        + authorLink
        + ", authorIcon="
        + authorIcon
        + ", title="
        + title
        + ", titleLink="
        + titleLink
        + ", text="
        + text
        + ", footer="
        + footer
        + ", footerIcon="
        + footerIcon
        + ", ts="
        + ts
        + "]";
  }

  /**
   * Formats Slack attachment as HTML
   *
   * @return
   */
  public String toHTML() {
    StringBuilder htmlMessage = new StringBuilder();

    // Add title
    if (title != null) {
      htmlMessage.append("<h5>");
      if (titleLink != null)
        htmlMessage.append(String.format("<a href=\"%s\">%s</a>", titleLink, title));
      else htmlMessage.append(title);
      htmlMessage.append("</h5>");
    }

    // Add fields
    for (AttachmentField field : fields) {
      if (field.getTitle() != null)
        htmlMessage.append(String.format("<h5>%s</h5>\n", field.getTitle()));
      if (field.getValue() != null)
        htmlMessage.append(String.format("<div>%s</div>\n", field.getValue()));
    }

    return htmlMessage.toString();
  }
}
