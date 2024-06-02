//@flow

import React, { type Node, type ComponentType, useState } from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../../stores/use-stores";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import SummaryInfo from "../../../Template/SummaryInfo";
import TemplateModel from "../../../../stores/models/TemplateModel";
import { mkAlert } from "../../../../stores/contexts/Alert";
import SampleModel from "../../../../stores/models/SampleModel";
import docLinks from "../../../../assets/DocLinks";
import Divider from "@mui/material/Divider";
import Box from "@mui/material/Box";
import VersionInfo from "../../../Template/Fields/VersionInfo";
import TemplatePicker from "../../../components/Picker/TemplatePicker";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Collapse from "@mui/material/Collapse";
import Alert from "@mui/material/Alert";

function Template(): Node {
  const {
    searchStore: { activeResult },
    uiStore,
  } = useStores();
  if (!activeResult || !(activeResult instanceof SampleModel))
    throw new Error("ActiveResult must be a Sample");

  const [open, setOpen] = useState(true);

  const setTemplate = (t: TemplateModel) => {
    activeResult.setTemplate(t).catch((error: any) => {
      uiStore.addAlert(
        mkAlert({
          title: "Could not fetch template details.",
          message:
            error?.response?.data.message ?? error.message ?? "Unknown reason.",
          variant: "error",
        })
      );
      console.error("Could not set template", error);
    });
  };

  const template = activeResult.template;
  return (
    <>
      <InputWrapper
        label="Sample Template"
        dataTestId="ChooseTemplate"
        explanation={
          activeResult.isFieldEditable("template") ? (
            <>
              See the documentation for information on{" "}
              <a
                href={docLinks.createTemplate}
                target="_blank"
                rel="noreferrer"
              >
                how to create custom templates
              </a>
              .
            </>
          ) : null
        }
      >
        <>
          <Grid
            container
            direction="row"
            alignItems="center"
            justifyContent="space-between"
          >
            <Grid item style={{ flexGrow: 1 }}>
              <SummaryInfo
                template={template}
                loading={
                  activeResult.templateId !== null &&
                  typeof activeResult.templateId !== "undefined" &&
                  !activeResult.template
                }
                paddingless={!activeResult.isFieldEditable("template")}
              />
            </Grid>
            {activeResult.isFieldEditable("template") && (
              <Grid item>
                <IconButton
                  onClick={() => {
                    setOpen(!open);
                  }}
                  size="small"
                >
                  <ExpandCollapseIcon open={open} />
                </IconButton>
              </Grid>
            )}
          </Grid>
          {template && (
            <VersionInfo
              template={template}
              onUpdate={() => {
                activeResult.updateToLatestTemplate();
              }}
              disabled={activeResult.deleted || activeResult.editing}
            />
          )}
        </>
      </InputWrapper>
      {activeResult.isFieldEditable("template") && (
        <>
          {!activeResult.template && (
            <Alert severity="info" role="status">
              {open
                ? "Select a template from the list below."
                : "Expand to select a template."}
            </Alert>
          )}
          <Collapse in={open} component="div" collapsedSize={0}>
            <Box mb={1}>
              <Divider />
            </Box>
            <TemplatePicker
              disabled={!activeResult.isFieldEditable("template")}
              setTemplate={setTemplate}
            />
          </Collapse>
        </>
      )}
    </>
  );
}

export default (observer(Template): ComponentType<{||}>);
