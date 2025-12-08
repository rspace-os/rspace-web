import FormLabel from "@mui/material/FormLabel";
import { observer } from "mobx-react-lite";
import type React from "react";
import FormControl from "../../../../components/Inputs/FormControl";
import type SubSampleModel from "../../../../stores/models/SubSampleModel";
import NewNote from "./NewNote";
import NotesList from "./NotesList";

type NotesArgs = {
    record: SubSampleModel;
    onErrorStateChange: (hasError: boolean) => void;
    hideLabel?: boolean;
};

function Notes({ record, onErrorStateChange, hideLabel = false }: NotesArgs): React.ReactNode {
    return (
        <FormControl>
            {!hideLabel && <FormLabel>Notes</FormLabel>}
            {record.isFieldEditable("notes") && record.isFieldVisible("notes") && (
                <NewNote record={record} onErrorStateChange={onErrorStateChange} />
            )}
            {record.isFieldVisible("notes") && <NotesList record={record} />}
        </FormControl>
    );
}

export default observer(Notes);
