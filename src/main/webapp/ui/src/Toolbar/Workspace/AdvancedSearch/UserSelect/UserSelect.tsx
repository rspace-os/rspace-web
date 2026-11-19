import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function UserSelect(props: any) {
  const { t } = useTranslation("workspace");
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [multi, setMulti] = React.useState<any[]>([]);
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [suggestions, setSuggestions] = React.useState<any[]>([]);

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function handleChangeMulti(value: any) {
    setMulti(value);
    props.updateSelected(value, "username");
  }

  useEffect(() => {
    const fetchData = async () => {
      const result = await axios("/workspace/ajax/getViewablePublicUserInfoList");

      setSuggestions(result.data.data);
      if (props.selected) {
        const usernames: string[] = props.selected.split("<<>>");
        // A link can name a user this viewer cannot see. Keep them, so that running the search again is unchanged
        const local_selected = usernames.map(
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          (username) => result.data.data.find((r: any) => r.username === username) ?? { username },
        );
        handleChangeMulti(local_selected);
      }
    };

    fetchData();
  }, []);

  return (
    <Autocomplete
      sx={{ flexGrow: 1, mt: "9px" }}
      options={suggestions}
      getOptionLabel={(option) =>
        option.firstName === undefined && option.lastName === undefined
          ? option.username
          : `${option.firstName ?? ""} ${option.lastName ?? ""} - ${option.username}`
      }
      isOptionEqualToValue={(option, value) => option.username === value.username}
      onChange={(_, selection) => handleChangeMulti(selection)}
      renderInput={(props) => (
        <TextField
          {...props}
          variant="standard"
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          placeholder={(props as any).error ?? t("toolbar.userSelect.placeholder")}
        />
      )}
      slotProps={{
        popupIndicator: {
          sx: {
            height: "unset !important",
            width: "unset !important",
          },
        },
      }}
      size="small"
      multiple
      value={multi}
      data-test-id={props.testId}
    />
  );
}
