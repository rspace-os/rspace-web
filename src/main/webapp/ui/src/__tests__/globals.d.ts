declare global {
  interface RSGlobal {
    /** Enables the file store export UI in tests that exercise sysadmin export flows. */
    newFileStoresExportEnabled?: boolean;
  }
}

export {};
