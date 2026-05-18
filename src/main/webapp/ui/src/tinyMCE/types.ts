export type RevisionIdentifier = string | number;

export type TinyMceEditor = {
  id: string;
  execCommand: (...args: Array<unknown>) => void;
  remove?: () => void;
};

export type InternalLinkInsertParams = [
  RevisionIdentifier,
  string,
  string,
  TinyMceEditor,
];

