import { afterEach, describe, expect, test } from "vitest";
import i18n from "@/modules/common/i18n";
import commonEnUs from "@/modules/common/i18n/locales/en-US/common.json";
import docLinks from "./DocLinks";

const teamsArticleIdKey = "helpDocs.docLinks.teams";
const updateSamplesArticleIdKey = "helpDocs.docLinks.updateAllSamplesOfTemplate";
const originalTeamsArticleId = commonEnUs.helpDocs.docLinks.teams;
const originalUpdateSamplesArticleId = commonEnUs.helpDocs.docLinks.updateAllSamplesOfTemplate;

afterEach(() => {
  i18n.addResource("en-US", "common", teamsArticleIdKey, originalTeamsArticleId);
  i18n.addResource("en-US", "common", updateSamplesArticleIdKey, originalUpdateSamplesArticleId);
});

describe("docLinks", () => {
  test("uses the translated article path", () => {
    i18n.addResource("en-US", "common", teamsArticleIdKey, "translated-id");

    expect(docLinks.teams).toBe("https://researchspace.helpdocs.io/article/translated-id");
  });

  test("uses an anchor embedded in the translated article id", () => {
    i18n.addResource(
      "en-US",
      "common",
      updateSamplesArticleIdKey,
      "c8sxesdqpy-create-a-template#refresh_all_samples_to_the_latest_template_version",
    );

    expect(docLinks.updateAllSamplesOfTemplate).toBe(
      "https://researchspace.helpdocs.io/article/c8sxesdqpy-create-a-template#refresh_all_samples_to_the_latest_template_version",
    );
  });
});
