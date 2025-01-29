//@flow strict

import { type URL } from "../util/types";

const mkDocLink = (pageId: string, hash?: string = ""): URL =>
  `https://researchspace.helpdocs.io/article/${pageId}#${hash}`;

const docLinks: { [string]: URL } = {
  listOfMaterials: mkDocLink("cdrc4ed67l"),
  attachments: mkDocLink("ory7fu1uw9"),
  gettingStarted: mkDocLink("tffkwcpizj"),
  moving: mkDocLink("dncoti2i4t"),
  editLocationsInVisualContainers: mkDocLink("jya8j336dt"),
  createContainer: mkDocLink("e5v4bvcl61"),
  createTemplate: mkDocLink("c8sxesdqpy"),
  updateAllSamplesOfTemplate: mkDocLink(
    "c8sxesdqpy",
    "update_all_of_your_samples_to_latest_template_version"
  ),
  createDialog: mkDocLink("x4y02hje72-edit-a-sample-or-container", "create"),
  createSample: mkDocLink("gb3r1lgm5g"),
  createTemplateFromSample: mkDocLink(
    "c8sxesdqpy",
    "create_a_template_from_a_sample"
  ),
  search: mkDocLink("e0fngo8a5s"),
  import: mkDocLink("a5zm2c3vtw"),
  barcodes: mkDocLink("nr29uf0fdr-scan-and-use-barcodes"),
  permissions: mkDocLink("n09nmg4ax7"),
  barcodesPrinting: mkDocLink(
    "nr29uf0fdr-scan-and-use-barcodes",
    "barcode_printing"
  ),
  zebraPrinter: mkDocLink(
    "nr29uf0fdr-scan-and-use-barcodes",
    "using_a_zebra_printer"
  ),
  luceneSyntax: mkDocLink("k919di8naq", "expert_lucene_query_syntax"),
  controlledVocabularies: mkDocLink("8ujmvpa1no"),
  IGSNIdentifiers: mkDocLink(
    "0wh5ziurr5",
    "add-igsn-identifiers-to-your-samples"
  ),
  pyratCors: mkDocLink("9kkeooveia", "cors"),
  panelAdjuster: mkDocLink("bt6kx098eq", "panel_adjuster"),
  appsIntroduction: mkDocLink("08ky7o0l1y"),
  argos: mkDocLink("vkd8mt2ffb"),
  clustermarket: mkDocLink("e6pb7y8ak1"),
  dataverse: mkDocLink("h14qd5tvjj"),
  dmptool: mkDocLink("o0wlhlgxnr"),
  dryad: mkDocLink("i1xvubndhm"),
  evernote: mkDocLink("9ckpmfdq8m"),
  figshare: mkDocLink("ir4ybsamcn"),
  github: mkDocLink("y2080yw30x"),
  cloudstorage: mkDocLink("j2z5f5r90q"),
  jove: mkDocLink("mopbqzzdf5"),
  nextcloud: mkDocLink("na3hn8ilee"),
  omero: mkDocLink("bwwbpkll90"),
  owncloud: mkDocLink("v8ss2uso0a"),
  protocolsio: mkDocLink("nid9q64pas"),
  pyrat: mkDocLink("9kkeooveia"),
  slack: mkDocLink("74r6scvv8g"),
  teams: mkDocLink("i95u9itfgu"),
  zenodo: mkDocLink("8i37k8kjqz"),
  tags: mkDocLink("/8ujmvpa1no"),
  dmponline: mkDocLink("pd84qoylzy"),
  taggingUsers: mkDocLink("zw6o5uh4qv"),
  irods: mkDocLink("xt21074dln"),
  dcd: mkDocLink("jj6grnzbdl"),
  ascenscia: mkDocLink("8ftzyor8mi"),
  fieldmark: mkDocLink("idbaaggghu"),
  orcid: mkDocLink("yhkbtnj61a"),
};

/**
 * This object is a mapping of logical names for pages of user documentation to
 * URLs for those pages. This is so that if the documentation changes then the
 * link only needs to be updated here and to make the code generally more
 * readable.
 */
export default docLinks;
