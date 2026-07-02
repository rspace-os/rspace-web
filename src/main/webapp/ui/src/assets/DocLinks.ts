import i18n from "@/modules/common/i18n";
import commonEnUs from "@/modules/common/i18n/locales/en-US/common.json";
import type { URL } from "../util/types";

const HELP_DOCS_ARTICLE_BASE = "https://researchspace.helpdocs.io/article";

export type DocLinkName = keyof typeof commonEnUs.helpDocs.docLinks;

const docLinks = Object.defineProperties(
  {} as Record<DocLinkName, URL>,
  Object.fromEntries(
    Object.keys(commonEnUs.helpDocs.docLinks).map((name) => {
      const docLinkName = name as DocLinkName;
      const key = `helpDocs.docLinks.${docLinkName}`;
      return [
        docLinkName,
        {
          enumerable: true,
          get: () =>
            `${HELP_DOCS_ARTICLE_BASE}/${
              i18n.getResource(i18n.resolvedLanguage ?? "en-US", "common", key) ??
              i18n.getResource("en-US", "common", key) ??
              commonEnUs.helpDocs.docLinks[docLinkName]
            }`,
        },
      ];
    }),
  ),
);

export default docLinks;
