import { afterEach, describe, expect, test } from "vitest";
import i18n from "@/modules/common/i18n";
import docLinks from "./DocLinks";

const teamsArticleTitleKey = "helpDocs.docLinks.teams.articleTitle";
const updateSamplesHashKey = "helpDocs.docLinks.updateAllSamplesOfTemplate.hash";
const originalTeamsArticleTitle = i18n.t(teamsArticleTitleKey);
const originalUpdateSamplesHash = i18n.t(updateSamplesHashKey);

afterEach(() => {
  i18n.addResource("en-US", "common", teamsArticleTitleKey, originalTeamsArticleTitle);
  i18n.addResource("en-US", "common", updateSamplesHashKey, originalUpdateSamplesHash);
});

describe("docLinks", () => {
  test("uses the translated article title in the article slug", () => {
    i18n.addResource("en-US", "common", teamsArticleTitleKey, "Teams integration translated");

    expect(docLinks.teams).toBe("https://researchspace.helpdocs.io/article/i95u9itfgu-teams-integration-translated");
  });

  test("uses the translated heading for section anchors", () => {
    i18n.addResource("en-US", "common", updateSamplesHashKey, "Refresh all samples to the latest template version");

    expect(docLinks.updateAllSamplesOfTemplate).toBe(
      "https://researchspace.helpdocs.io/article/c8sxesdqpy-create-a-template#refresh_all_samples_to_the_latest_template_version",
    );
  });
});
