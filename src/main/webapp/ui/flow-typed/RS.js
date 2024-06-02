//@flow

declare var RS: {|
  trackEvent: (string, any) => void,
  showHelpButton: () => Promise<void>,
  hideHelpButton: () => Promise<void>,
  waitForLighthouse: () => Promise<void>,
  tinymceInsertContent: (string, any) => void,
  newFileStoresExportEnabled: boolean,
  asposeEnabled: boolean,
|};

// see src/main/webapp/scripts/pages/workspace/mediaGalleryManager.js
declare function gallery(): void;
