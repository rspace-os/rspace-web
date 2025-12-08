import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useEffect, useState } from "react";
import InputWrapper from "../../../components/Inputs/InputWrapper";
import type FieldModel from "../../../stores/models/FieldModel";
import { match } from "../../../util/Util";

type NameFieldArgs = {
    field: FieldModel;
    editing: boolean;
    onErrorStateChange: (error: boolean) => void;
    id: string;
};

function NameField({ field, editing, onErrorStateChange, id }: NameFieldArgs): React.ReactNode {
    const [duplicateFieldError, setDuplicateFieldError] = useState(false);

    useEffect(() => {
        setDuplicateFieldError(field.owner.fieldNamesInUse.filter((n) => n === field.name).length > 1);
    }, [field.owner.fieldNamesInUse, field.name]);

    const emptyName = field.name === "";
    const tooLongName = field.name.length > 50;

    const helperText = match<void, string>([
        [() => emptyName, "Name cannot be empty"],
        [() => duplicateFieldError, "You either already have a field with that name or that name is not permitted"],
        [() => tooLongName, "Name cannot be longer than 50 characters"],
        [() => true, ""],
    ])();

    return editing ? (
        <InputWrapper
            label="Name"
            error={emptyName || tooLongName || duplicateFieldError}
            helperText={helperText}
            maxLength={50}
            value={field.name}
        >
            <TextField
                variant="standard"
                value={field.name}
                name="name"
                disabled={!editing}
                onChange={({ target: { value } }: React.ChangeEvent<HTMLInputElement>) => {
                    field.setAttributesDirty({ name: value });
                    onErrorStateChange(value === "" || value.length > 50);
                }}
                id={id}
            />
        </InputWrapper>
    ) : (
        <Typography variant="h6" component="h6" style={{ overflowWrap: "anywhere" }} id={id}>
            {field.name}
        </Typography>
    );
}

export default observer(NameField);
