import React, { useContext } from "react";
import { observer } from "mobx-react-lite";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Link from "@mui/material/Link";
import NavigateContext from "../../stores/contexts/Navigate";
import InventoryBaseRecord from "../../stores/models/InventoryBaseRecord";
import ContainerModel from "../../stores/models/ContainerModel";

type HistoricalVersionAlertArgs = {
  record: InventoryBaseRecord;
};

/**
 * Shown above the form of a historical (read-only) version of an inventory
 * record, mirroring the template version banner: states which version is
 * being viewed and links back to the latest state of the record.
 */
function HistoricalVersionAlert({
  record,
}: HistoricalVersionAlertArgs): React.ReactNode {
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  if (!record.historicalVersion) return null;
  if (!record.id) return null;

  const latestUrl = `/inventory/${record.recordType.toLowerCase()}/${
    record.id
  }`;
  const typeLabel = record.recordTypeLabel.toLowerCase() || "record";

  return (
    <Alert severity="info">
      <AlertTitle>
        This is version {record.version} of the {typeLabel}.
      </AlertTitle>
      <div>
        It is read-only.{" "}
        <Link
          href={latestUrl}
          onClick={(e: React.MouseEvent) => {
            e.preventDefault();
            navigate(latestUrl);
          }}
        >
          View the latest version
        </Link>
      </div>
      {record instanceof ContainerModel && (
        <div>
          Contents are not part of the historical snapshot, so they are not
          shown.
        </div>
      )}
    </Alert>
  );
}

export default observer(HistoricalVersionAlert);
