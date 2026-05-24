import React, { useEffect } from "react";
import axios from "@/common/axios";
import TextField from "@mui/material/TextField";
import Autocomplete from "@mui/material/Autocomplete";

export default function UserSelect(props) {
  const [multi, setMulti] = React.useState([]);
  const [suggestions, setSuggestions] = React.useState([]);

  function handleChangeMulti(value) {
    setMulti(value);
    props.updateSelected(value, "username");
  }


  useEffect(() => {
    const fetchData = async () => {
      const result = await axios(
        "/workspace/ajax/getViewablePublicUserInfoList",
      );

      setSuggestions(result.data.data);
      if (props.selected) {
        const selected = props.selected.split("<<>>");
        const local_selected = [];
        selected.map((s) => {
          const idx = result.data.data.findIndex((r) => r.username == s);
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
        <TextField
          {...props}
          variant="standard"
          placeholder={props.error ?? "Select owner(s)"}
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
      dataTestId={props.testId}
    />
  );
}
