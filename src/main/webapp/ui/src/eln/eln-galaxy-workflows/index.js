import React from "react";
import { createRoot } from "react-dom/client";
import GalaxyWorkflowInvocations
  from "@/eln/eln-galaxy-workflows/GalaxyWorkflowInvocations";
window.addEventListener("galaxy-used", function (e) {
  const FAB_SIZE = 48;
  // const domContainer = document.getElementsByClassName("galaxy-textfield")[0];
  const fieldId = e.detail.fieldId.substring(4);
  const containerRoot =
      document.querySelector(`.galaxy-textfield[data-field-id="${fieldId}"]`);
  // Array.from(document.getElementsByClassName("galaxy-textfield")).forEach(domContainer=>{
  //   const root = createRoot(domContainer);
  //   root.render(
  //       <GalaxyWorkflowInvocations/>
  //   );
  // })
  const root = createRoot(containerRoot);
  root.render(
    <GalaxyWorkflowInvocations/>
    //   <MaterialsListing
    //       elnFieldId={fieldId}
    //       canEdit={false}
    //       fabRightPadding={FAB_SIZE}
    //   />
  );
});
