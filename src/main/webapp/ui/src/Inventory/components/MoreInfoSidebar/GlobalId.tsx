import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import GlobalIdLink from "../../../components/GlobalId";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";

type GlobalIdArgs = {
  record: InventoryRecord;
};

function GlobalId({ record }: GlobalIdArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  return (
    <FormControl component="fieldset" sx={{ alignItems: "flex-start" }}>
      <FormLabel component="legend">{t("moreInfo.globalId")}</FormLabel>
      <GlobalIdLink record={record} />
    </FormControl>
  );
}

export default observer(GlobalId);
