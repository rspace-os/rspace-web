package com.researchspace.extmessages.msteams;

import com.researchspace.analytics.service.AnalyticsEvent;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.extmessages.base.AbstractExternalWebhookMessageSender;
import com.researchspace.extmessages.base.ExternalMessageSender;
import com.researchspace.extmessages.base.MessageDetails;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.core.IRSpaceDoc;
import com.researchspace.properties.IPropertyHolder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

public class MsTeamsMessageSender extends AbstractExternalWebhookMessageSender
    implements ExternalMessageSender {

  private String webhookPropertyName = "MSTEAMS_WEBHOOK_URL";
  Logger log = LoggerFactory.getLogger(MsTeamsMessageSender.class);

  private @Autowired IPropertyHolder props;

  @Override
  public boolean supportsApp(App app) {
    return App.APP_MSTEAMS.equals(app.getName());
  }

  protected String getPostUrlSetting() {
    return webhookPropertyName;
  }

  protected String createMessage(MessageDetails message) {
    final int docCount = message.getRecords().size();
    if (docCount > 0) {
      MSCard card = new MSCard();
      card.setTitle("From " + message.getOriginator().getFullName());
      List<Section> sections = new ArrayList<>();

      if (docCount == 1) {
        IRSpaceDoc singleDoc = message.getRecords().iterator().next();
        String link = createDocumentLink(singleDoc);
        String summary = createSummary(singleDoc, link);
        card.setSummary(summary);
        card.setText(convert(message.getMessage()));
        Fact owner = getOwnerFact(singleDoc);
        Fact id = getIdFact(singleDoc);
        Section section = new Section();
        section.setFacts(TransformerUtils.toList(owner, id));
        section.setActivityTitle(summary);
        sections.add(section);
      } else {
        card.setSummary("Message about several documents");
        card.setText(convert(message.getMessage()));
        for (IRSpaceDoc doc : message.getRecords()) {

          Fact owner = getOwnerFact(doc);
          Fact id = getIdFact(doc);
          Section section = new Section();
          section.setFacts(TransformerUtils.toList(owner, id));
          // summaries to sections if > 1 document

          String link = createDocumentLink(doc);
          String summary = createSummary(doc, link);
          section.setActivityTitle(summary);
          sections.add(section);
        }
      }
      card.setSections(sections);

      return card.toJSON();

    } else {
      log.warn("No records, can't add attachment");
      MSCard card = new MSCard();
      card.setText(convert(message.getMessage()));
      card.setTitle("Message from " + message.getOriginator().getFullName());
      card.setSummary("Message from " + message.getOriginator().getFullName());
      return card.toJSON();
    }
  }

  @Override
  protected void postSendMessage(
      ResponseEntity<String> rc, URI uri, MessageDetails message, User subject) {
    analyticsMgr.trackChatApp(subject, "message_post", AnalyticsEvent.TEAMS_USED);
  }

  private Fact getIdFact(IRSpaceDoc singleDoc) {
    return new Fact("ID", singleDoc.getGlobalIdentifier());
  }

  private Fact getOwnerFact(IRSpaceDoc singleDoc) {
    return new Fact("Owner", singleDoc.getOwner().getFullName());
  }

  private String createSummary(IRSpaceDoc doc, String link) {
    return String.format("Message about [%s](%s)", doc.getName(), link);
  }

  private String createDocumentLink(IRSpaceDoc doc) {
    return String.format("%s/globalId/%s", props.getServerUrl(), doc.getGlobalIdentifier());
  }
}
