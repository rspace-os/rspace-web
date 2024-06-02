// @flow

// this id should be part of a Context MenuModel after refactoring
const menuIDs = {
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

export { menuIDs };
