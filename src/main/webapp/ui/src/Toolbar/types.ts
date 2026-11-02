export type ToolbarConditionalRender = {
  print: boolean;
  export: boolean;
};

export type ToolbarEventHandlers = {
  onCreateRequest: () => void;
  onExportDocument: () => void;
  onCanSign: () => void;
};

export type ToolbarUpdate = {
  canSign?: boolean;
  conditionalRender?: Partial<ToolbarConditionalRender>;
  eventHandlers?: Partial<ToolbarEventHandlers>;
};

declare global {
  interface Window {
    renderToolbar: (newProps?: ToolbarUpdate) => void;
  }
}
