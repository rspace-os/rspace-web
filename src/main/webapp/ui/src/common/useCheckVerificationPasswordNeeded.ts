import React from "react";
import axios from "@/common/axios";
import * as FetchingData from "../util/fetchingData";

export default function useCheckVerificationPasswordNeeded(): FetchingData.Fetched<boolean> {
  const [isNeeded, setIsNeeded] = React.useState<FetchingData.Fetched<boolean>>(
    { tag: "loading" }
  );

  React.useEffect(() => {
    void (async () => {
      try {
        const { data } = await axios.get<{ data: boolean }>(
          "/vfpwd/ajax/checkVerificationPasswordNeeded"
        );
        setIsNeeded({ tag: "success", value: data.data });
      } catch (error) {
        if (error instanceof Error) {
          setIsNeeded({ tag: "error", error: error.message });
        }
      }
    })();
  }, []);

  return isNeeded;
}
