export type RevisionIdentifier = string | number;

export type TinyMceEditor = {
  id: string;
  execCommand: (...args: Array<unknown>) => void;
  remove?: () => void;
};

/** The parts of a TinyMCE editor that the React-based document editor plugins register against. */
export type TinyMcePluginEditor = {
  id: string; // the id of the current document field, prefixed with "rtf_"
  execCommand: (command: string, ui: boolean, value?: string) => void;
  ui: {
    registry: {
      addButton: (name: string, config: { tooltip: string; icon: string; onAction: () => void }) => void;
      addMenuItem: (name: string, config: { text: string; icon: string; onAction: () => void }) => void;
    };
  };
};

export type InternalLinkInsertParams = [RevisionIdentifier, string, string, TinyMceEditor];
