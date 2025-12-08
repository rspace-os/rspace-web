import TableCell from "@mui/material/TableCell";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import GlobalId from "../../../components/GlobalId";
import TagListing from "../../../components/Tags/TagListing";
import UserDetails from "../../../components/UserDetails";
import { RecordLink } from "../../../Inventory/components/RecordLink";
import RecordLocation from "../../../Inventory/components/RecordLocation";
import NavigateContext from "../../../stores/contexts/Navigate";
import type { AdjustableTableRow, AdjustableTableRowLabel, CellContent } from "../../../stores/definitions/Tables";

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
        dataSource.adjustableTableOptions().get(selectedOption) ?? (() => ({ renderOption: "node", data: null }))
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
