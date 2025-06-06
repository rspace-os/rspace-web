import TableCell from "@mui/material/TableCell";
import {
  type AdjustableTableRow,
  type AdjustableTableRowLabel,
  type CellContent,
} from "../../../stores/definitions/Tables";
import React, { useContext } from "react";
import { observer } from "mobx-react-lite";
import GlobalId from "../../../components/GlobalId";
import { RecordLink } from "../../../Inventory/components/RecordLink";
import UserDetails from "../../../Inventory/components/UserDetails";
import RecordLocation from "../../../Inventory/components/RecordLocation";
import TagListing from "../../../components/Tags/TagListing";
import NavigateContext from "../../../stores/contexts/Navigate";

type AdjustableCellArgs<T extends AdjustableTableRowLabel> = {
  dataSource: AdjustableTableRow<T>;
  selectedOption: T;
};

function AdjustableCell<T extends AdjustableTableRowLabel>({
  dataSource,
  selectedOption,
}: AdjustableCellArgs<T>): React.ReactNode {
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  const selectedContent = (
    dataSource.adjustableTableOptions().get(selectedOption) ??
    (() => ({ renderOption: "node", data: null }))
  )();
  let content;

  const cellContent: CellContent = selectedContent;
  if (cellContent.renderOption === "node") {
    content = cellContent.data ?? <>&mdash;</>;
  } else if (cellContent.renderOption === "name") {
    content = <RecordLink record={cellContent.data} overflow />;
  } else if (cellContent.renderOption === "globalId") {
    content = <GlobalId record={cellContent.data} />;
  } else if (cellContent.renderOption === "location") {
    content = <RecordLocation record={cellContent.data} />;
  } else if (cellContent.renderOption === "tags") {
    content = (
      <TagListing
        onClick={(tag) => {
          navigate(`/inventory/search?query=l: (tags:"${tag.value}")`);
        }}
        tags={cellContent.data}
        size="small"
      />
    );
  } else if (cellContent.renderOption === "owner") {
    content = cellContent.data ? (
      <UserDetails
        userId={cellContent.data.id}
        fullName={cellContent.data.fullName}
        position={["bottom", "right"]}
      />
    ) : (
      ""
    );
  } else {
    throw new Error("Unknown rendering specifier");
  }

  return <TableCell>{content}</TableCell>;
}

export default observer(AdjustableCell);
