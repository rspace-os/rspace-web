import path from 'node:path';
import webpack from "webpack";
import { BundleAnalyzerPlugin } from "webpack-bundle-analyzer";

const isCi = process.env.CI === "true" || process.env.CI === "1";

/** @type {import('webpack').Configuration} */
const config = {
  entry: {
    /*
     * Inventory is a Single Page Application written entirely using React.
     * The main Inventory UI (`/inventory`) is deployed as the `inventoryEntry`
     * bundle, with some additional JavaScript assets for other standalone
     * pages.
     */
    inventoryEntry: "./src/App.tsx",
    inventoryRecordIdentifierPublicPage:
      "./src/components/PublicPages/IdentifierPublicPage.tsx",

    /*
     * Some of the ELN pages are Single Page Applications too. These JS bundles
     * implement each of those pages.
     */
    apps: "./src/eln/apps/index.tsx",
    gallery: "./src/eln/gallery/index.tsx",
    about: "./src/eln/about/index.tsx",

    /*
     * The Electronic Notebook (ELN) is a traditional multipage application
     * written mostly using JSPs, jQuery, and a variety of other technologies.
     * There are, however, some islands of React where particular features have
     * been implemented using the more modern stack. The JS assets listed below
     * are each of these UI elements; loaded by the pages that depend on them,
     * with each rendering its React component when loaded by the browser.
     * Without a single React component that defines the whole application,
     * each island of React must be defined as a separate JS asset.
     */
    appBar: "./src/eln/AppBar.tsx",
    memberAutoshareStatusWrapper:
      "./src/my-rspace/directory/groups/Autoshare/MemberAutoshareStatusWrapper.js",
    createGroup: "./src/CreateGroup/CreateGroup.js",
    myLabGroups: "./src/my-rspace/directory/groups/MyLabGroups.js",
    rorIntegration: "./src/system-ror/RoRIntegration.tsx",
    exportModal: "./src/Export/ExportModal.js",
    groupEditBar: "./src/my-rspace/directory/groups/GroupEditBar.js",
    workspaceToolbar: "./src/Toolbar/Workspace/Toolbar.js",
    notebookToolbar: "./src/Toolbar/Notebook/Toolbar.js",
    structuredDocumentToolbar: "./src/Toolbar/StructuredDocument/Toolbar.js",
    fileTreeToolbar: "./src/Toolbar/FileTreeToolbar.js",
    galleryToolbar: "./src/Toolbar/Gallery/Toolbar.tsx",
    newLabGroup: "./src/system-groups/NewLabGroup.js",
    tinymceSidebarInfo: "./src/tinyMCE/sidebarInfo.js",
    previewInfo: "./src/tinyMCE/previewInfo.js",
    userDetails: "./src/components/UserDetails_deprecated.js",
    groupUserActivity: "./src/my-rspace/directory/groups/GroupUserActivity.js",
    groupActivity: "./src/my-rspace/profile/GroupActivity.js",
    accountActivity: "./src/my-rspace/profile/AccountActivity.js",
    oAuth: "./src/my-rspace/profile/OAuthTrigger.js",
    connectedApps: "./src/my-rspace/profile/ConnectedAppsTrigger.js",
    labgroupsTable: "./src/my-rspace/profile/GroupsTable.js",
    snapGeneDialog: "./src/tinyMCE/SnapGene/snapGeneDialog.js",
    toastMessage: "./src/components/ToastMessage.js",
    internalLink: "./src/tinyMCE/internallink.js",
    tinymceShortcuts: "./src/tinyMCE/shortcutsPlugin/shortcuts.js",
    tinymcePyrat: "./src/tinyMCE/pyrat/Pyrat.js",
    tinymceClustermarket: "./src/tinyMCE/clustermarket/index.js",
    tinymceGalaxy: "./src/tinyMCE/galaxy/index.tsx",
    tinymceOmero: "./src/tinyMCE/omero/index.js",
    tinymceJove: "./src/tinyMCE/jove/index.tsx",
    tinymceKetcher: "./src/tinyMCE/ketcher/KetcherTinyMce.js",
    ketcherViewer: "./src/tinyMCE/ketcher/KetcherViewer.tsx",
    tinymceIdentifiers: "./src/tinyMCE/inventory/identifiers/index.tsx",
    tinymcePubchem: "./src/tinyMCE/pubchem/index.tsx",
    tinymceStoichiometry: "./src/tinyMCE/stoichiometry/index.tsx",
    tinymceGallery: "./src/tinyMCE/gallery/index.tsx",
    baseSearch: "./src/components/BaseSearch.tsx",
    confirmationDialog: "./src/components/ConfirmationDialog.tsx",
    imageEditor: "./src/Gallery/imageEditorDialog.js",
    materialsListing:
      "./src/eln-inventory-integration/MaterialsListing/MaterialsListing.tsx",
    externalWorkFlows: "./src/eln/eln-external-workflows/index.tsx",
    jupyterNotebooks: "./src/eln/jupyter-notebooks/index.tsx",
    associatedInventoryRecords:
      "./src/eln-inventory-integration/AssociatedInventoryRecords/index.tsx",
    sysadminUsers: "./src/eln/sysadmin/users/index.tsx",
    raidConnections:
      "./src/my-rspace/profile/RaIDConnections/RaIDConnectionsEntrypoint.tsx",
  },
  output: {
    filename: "[name].js",
    chunkFilename: "[name].js",
    path: path.resolve(import.meta.dirname, "dist"),
    publicPath: "/ui/dist/",
    clean: true,
  },
  plugins: [
    //mocks process object, required by the ketcher-react package which would
    // fail otherwise
    new webpack.DefinePlugin({
      process: { env: {} },
    }),
    new BundleAnalyzerPlugin({
      analyzerMode: Boolean(process.env.FRONTEND_BUILD_STATS)
        ? "server"
        : "disabled",
      generateStatsFile: isCi,
    }),
  ],
  optimization: {
    runtimeChunk: "single",
  },
  resolve: {
    extensions: [".js", ".jsx", ".ts", ".tsx", ".json"],
    alias: {
      Styles: path.resolve(import.meta.dirname, "src/util/styles"),
      "@": path.resolve(import.meta.dirname, "src"),
    },
    fallback: {
      url: import.meta.resolve("url/"),
    },
  },
  module: {
    rules: [
      {
        test: /\.css$/,
        use: ["style-loader", "css-loader"],
      },
      {
        test: /\.m?jsx?$/,
        exclude: /node_modules/,
        use: {
          loader: "babel-loader",
        },
      },
      {
        test: /\.(ts|tsx)$/,
        exclude: /node_modules/,
        use: {
          loader: "ts-loader",
          options: {
            /*
             * By only transpiling and not also doing a type check, this allows
             * webpack to build code that might fail at runtime with errors that
             * could be caught by the type checker. This is useful when
             * developing code but it is important that the Jenkins build runs
             * the TypeScript compiler separately to ensure that those errors
             * don't make it into the production code. Devs should also run
             * `npm run tsc` or rely their editor's LSP integration before
             * committing.
             */
            transpileOnly: true,
          },
        },
      },
      {
        test: /\.(png|jpe?g|gif|svg)$/i,
        type: "asset/resource",
      },
    ],
  },
};

export default config;
