/**
 * Reads a plugin setting from the active TinyMCE editor in the parent frame,
 * i.e. the document editor that opened this plugin's dialog.
 */
export function getActiveEditorSetting(name: string): string {
  const activeEditor = parent.tinymce.activeEditor;
  if (!activeEditor) {
    throw new Error(`Active TinyMCE editor not found when reading ${name}`);
  }
  return (activeEditor as typeof activeEditor & { settings: Record<string, string> }).settings[name];
}
