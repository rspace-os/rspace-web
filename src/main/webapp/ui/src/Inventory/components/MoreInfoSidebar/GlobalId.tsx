import React from "react";
import { observer } from "mobx-react-lite";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import GlobalIdLink from "../../../components/GlobalId";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";

type GlobalIdArgs = {
  record: InventoryRecord;
};

function GlobalId({ record }: GlobalIdArgs): React.ReactNode {
  return (
    <FormControl component="fieldset" sx={{ alignItems: "flex-start" }}>
      <FormLabel component="legend">Global ID</FormLabel>
      <GlobalIdLink record={record} />
    </FormControl>
  );
}

export default observer(GlobalId);
