//@flow

import React from "react";
import axios from "axios";
import { getByKey } from "../../../util/optional";
import { Result } from "../../../util/result";

type GalleryFile = {|
  id: number,
  name: string,
  modificationDate: number,
  type: string,
  thumbnailUrl: string,
|};

export default function useGalleryListing({
  section,
}: {|
  section: string,
|}): {|
  galleryListing: $ReadOnlyArray<GalleryFile>,
|} {
  const [galleryListing, setGalleryListing] = React.useState<
    $ReadOnlyArray<GalleryFile>
  >([]);

  async function getGalleryFiles(params: {| section: string |}): Promise<void> {
    try {
      const { data } = await axios.get<mixed>(`/gallery/getUploadedFiles`, {
        params: new URLSearchParams({
          mediatype: params.section,
          currentFolderId: "0",
          name: "",
          pageNumber: "0",
          sortOrder: "DESC",
          orderBy: "",
        }),
      });

      const isObject = (m: mixed): Result<{ ... } | null> =>
        typeof m === "object"
          ? Result.Ok(m)
          : Result.Error([new TypeError("Not an object")]);
      const isNotNull = <T>(b: T | null): Result<T> =>
        b === null ? Result.Error<T>([new TypeError("Is null")]) : Result.Ok(b);
      const isArray = (m: mixed): Result<$ReadOnlyArray<mixed>> =>
        Array.isArray(m)
          ? Result.Ok(m)
          : Result.Error([new TypeError("Is not an array")]);
      const isString = (m: mixed): Result<string> =>
        typeof m === "string"
          ? Result.Ok(m)
          : Result.Error([new TypeError("Is not a string")]);
      const isNumber = (m: mixed): Result<number> =>
        typeof m === "number"
          ? Result.Ok(m)
          : Result.Error([new TypeError("Is not a number")]);
      const getValueWithKey = (key: string) => (obj: { ... }) =>
        getByKey(key, obj).toResult(() => new Error(`key '${key}' is missing`));

      setGalleryListing(
        isObject(data)
          .flatMap(isNotNull)
          .flatMap(getValueWithKey("data"))
          .flatMap(isObject)
          .flatMap(isNotNull)
          .flatMap(getValueWithKey("items"))
          .flatMap(isObject)
          .flatMap(isNotNull)
          .flatMap(getValueWithKey("results"))
          .flatMap(isArray)
          .flatMap((array) => {
            if (array.length === 0)
              return Result.Ok<$ReadOnlyArray<GalleryFile>>([]);
            return Result.all(
              ...array.map((m) =>
                isObject(m)
                  .flatMap(isNotNull)
                  .map((obj) => {
                    const idR = getValueWithKey("name")(obj).flatMap(isNumber);
                    const nameR =
                      getValueWithKey("name")(obj).flatMap(isString);
                    const modificationDateR =
                      getValueWithKey("modificationDate")(obj).flatMap(
                        isNumber
                      );
                    const typeR =
                      getValueWithKey("type")(obj).flatMap(isString);
                    return {
                      id: idR.orElse(0),
                      name: nameR.orElse(""),
                      modificationDate: modificationDateR.orElse(0),
                      type: typeR.orElse(""),
                    };
                  })
              )
            );
          })
          .orElseGet((errors) => {
            errors.forEach((e) => {
              console.error(e);
            });
            return [];
          })
      );

      // setGalleryListing(foo7);
    } catch (e) {
      console.error(e);
    }
  }

  React.useEffect(() => {
    void getGalleryFiles({ section });
  }, [section]);

  return {
    galleryListing,
  };
}
