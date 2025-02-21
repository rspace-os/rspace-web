// @flow

/**
 * This is a bit of a hack to allow parts of the context menus in Inventory to
 * know which context menu they are being rendered within. They should be
 * avoided where at all possible, instead prefering an approach that uses
 * dynamic dispatch, dependency injection, or other techniques that allow the
 * context menu to adapt to its environment without hard coding the specific
 * parts of the UI, as it makes the code more brittle and harder to maintain.
 */
export const menuIDs = {
  // For plain listings, like main search, list view of containers, and sample's subsamples
  RESULTS: "results",

  // For active result's main context menu
  STEPPER: "stepper",

  // For image and visual view of container content
  CONTENT: "content",

  // For picker
  PICKER: "picker",

  // For card view
  CARD: "card",

  // For cases where the context menu is never shown, such as move dialog's preview
  NONE: "none",
};
