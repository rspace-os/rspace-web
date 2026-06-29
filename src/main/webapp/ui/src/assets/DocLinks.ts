import i18n from "@/modules/common/i18n";
import commonEnUs from "@/modules/common/i18n/locales/en-US/common.json";
import type { URL } from "../util/types";

const HELP_DOCS_ARTICLE_BASE = "https://researchspace.helpdocs.io/article";

type HashSeparator = "-" | "_";

type DocLinkDefinition = {
  articleId: string;
  hashSeparator?: HashSeparator;
};

const docLinkDefinitions = {
  listOfMaterials: { articleId: "cdrc4ed67l" },
  attachments: { articleId: "ory7fu1uw9" },
  gettingStarted: { articleId: "tffkwcpizj" },
  moving: { articleId: "dncoti2i4t" },
  editLocationsInVisualContainers: { articleId: "jya8j336dt" },
  createContainer: { articleId: "e5v4bvcl61" },
  createTemplate: { articleId: "c8sxesdqpy" },
  updateAllSamplesOfTemplate: { articleId: "c8sxesdqpy", hashSeparator: "_" },
  createDialog: { articleId: "x4y02hje72", hashSeparator: "-" },
  createSample: { articleId: "gb3r1lgm5g" },
  createTemplateFromSample: { articleId: "c8sxesdqpy", hashSeparator: "_" },
  search: { articleId: "e0fngo8a5s" },
  import: { articleId: "a5zm2c3vtw" },
  barcodes: { articleId: "nr29uf0fdr" },
  permissions: { articleId: "n09nmg4ax7" },
  barcodesPrinting: { articleId: "nr29uf0fdr", hashSeparator: "_" },
  zebraPrinter: { articleId: "nr29uf0fdr", hashSeparator: "_" },
  luceneSyntax: { articleId: "k919di8naq", hashSeparator: "_" },
  controlledVocabularies: { articleId: "8ujmvpa1no" },
  IGSNIdentifiers: { articleId: "0wh5ziurr5", hashSeparator: "-" },
  pyratCors: { articleId: "9kkeooveia", hashSeparator: "-" },
  panelAdjuster: { articleId: "bt6kx098eq", hashSeparator: "_" },
  appsIntroduction: { articleId: "08ky7o0l1y" },
  apiDirect: { articleId: "v0dxtfvj7u" },
  argos: { articleId: "vkd8mt2ffb" },
  clustermarket: { articleId: "e6pb7y8ak1" },
  dataverse: { articleId: "h14qd5tvjj" },
  dmptool: { articleId: "o0wlhlgxnr" },
  dmptoolImportingDmps: { articleId: "o0wlhlgxnr", hashSeparator: "_" },
  dryad: { articleId: "i1xvubndhm" },
  dsw: { articleId: "6adimrmy9m" },
  evernote: { articleId: "9ckpmfdq8m" },
  figshare: { articleId: "ir4ybsamcn" },
  github: { articleId: "y2080yw30x" },
  galaxy: { articleId: "zzsl46jo5y" },
  cloudstorage: { articleId: "j2z5f5r90q" },
  videoIntegration: { articleId: "cdzdub1ykw" },
  jupyter: { articleId: "gg0ao0rqpt" },
  chemistry: { articleId: "wfxm4xwtio" },
  pubchem: { articleId: "wfxm4xwtio", hashSeparator: "_" },
  nextcloud: { articleId: "na3hn8ilee" },
  omero: { articleId: "bwwbpkll90" },
  owncloud: { articleId: "v8ss2uso0a" },
  protocolsio: { articleId: "nid9q64pas" },
  pyrat: { articleId: "9kkeooveia" },
  slack: { articleId: "74r6scvv8g" },
  teams: { articleId: "i95u9itfgu" },
  zenodo: { articleId: "8i37k8kjqz" },
  tags: { articleId: "8ujmvpa1no" },
  changelog: { articleId: "mx11qvqg0i" },
  dmpassistant: { articleId: "n88a3g86e0" },
  dmponline: { articleId: "pd84qoylzy" },
  taggingUsers: { articleId: "zw6o5uh4qv" },
  irods: { articleId: "xt21074dln" },
  dcd: { articleId: "jj6grnzbdl" },
  fieldmark: { articleId: "idbaaggghu" },
  orcid: { articleId: "yhkbtnj61a" },
  gallery: { articleId: "sl6mo1i9do" },
  raid: { articleId: "zb4c2c8a4b" },
} as const satisfies Record<string, DocLinkDefinition>;

export type DocLinkName = keyof typeof docLinkDefinitions;

function slugifyForHelpDocs(value: string, separator: HashSeparator): string {
  const separatorPattern = separator === "-" ? "\\-" : "_";
  return value
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/['’]/g, "")
    .replace(/[^\p{Letter}\p{Number}]+/gu, separator)
    .replace(new RegExp(`${separatorPattern}{2,}`, "g"), separator)
    .replace(new RegExp(`^${separatorPattern}|${separatorPattern}$`, "g"), "");
}

function getFallbackTranslation(key: string): string | null {
  const value = key.split(".").reduce<unknown>((currentValue, currentKey) => {
    if (currentValue && typeof currentValue === "object" && currentKey in currentValue) {
      return currentValue[currentKey as keyof typeof currentValue];
    }
    return null;
  }, commonEnUs);

  return typeof value === "string" ? value : null;
}

function getTranslation(key: string): string {
  const resource =
    i18n.getResource(i18n.resolvedLanguage ?? "en-US", "common", key) ?? i18n.getResource("en-US", "common", key);

  if (typeof resource === "string") {
    return resource;
  }

  return getFallbackTranslation(key) ?? key;
}

function buildDocLink(name: DocLinkName, definition: DocLinkDefinition): URL {
  const articleTitle = getTranslation(`helpDocs.docLinks.${name}.articleTitle`);
  const articleSlug = slugifyForHelpDocs(articleTitle, "-");
  const hash =
    definition.hashSeparator === undefined
      ? ""
      : `#${slugifyForHelpDocs(getTranslation(`helpDocs.docLinks.${name}.hash`), definition.hashSeparator)}`;

  return `${HELP_DOCS_ARTICLE_BASE}/${definition.articleId}-${articleSlug}${hash}`;
}

const docLinks = Object.defineProperties(
  {} as Record<DocLinkName, URL>,
  Object.fromEntries(
    Object.entries(docLinkDefinitions).map(([name, definition]) => [
      name,
      {
        enumerable: true,
        get: () => buildDocLink(name as DocLinkName, definition),
      },
    ]),
  ),
);

export default docLinks;
