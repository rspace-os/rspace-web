import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import React, { useEffect } from "react";
import axios from "@/common/axios";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
export default function UserSelect(props: any) {
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const [multi, setMulti] = React.useState<any[]>([]);
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const [suggestions, setSuggestions] = React.useState<any[]>([]);

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  function handleChangeMulti(value: any) {
    setMulti(value);
    props.updateSelected(value, "username");
  }

  useEffect(() => {
    const fetchData = async () => {
      const result = await axios("/workspace/ajax/getViewablePublicUserInfoList");

      setSuggestions(result.data.data);
      if (props.selected) {
        const selected = props.selected.split("<<>>");
        // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
        const local_selected: any[] = [];
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
        selected.map((s: any) => {
          // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
          // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
          const idx = result.data.data.findIndex((r: any) => r.username == s);
          local_selected.push(result.data.data[idx]);
        });
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
        `${typeof option.firstName !== "undefined" ? option.firstName : ""} ${
          typeof option.lastName !== "undefined" ? option.lastName : ""
        } - ${option.username}`
      }
      onChange={(_, selection) => handleChangeMulti(selection)}
      renderInput={(props) => (
        // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
        <TextField {...props} variant="standard" placeholder={(props as any).error ?? "Select owner(s)"} />
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
