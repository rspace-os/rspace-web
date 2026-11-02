import { createConfirmationDialog } from "./ConfirmationDialog";

window.addEventListener("load", () => {
  window.RS ??= {};
  window.RS.createConfirmationDialog = createConfirmationDialog;
});
