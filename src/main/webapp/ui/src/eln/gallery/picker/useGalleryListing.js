//@flow

import React from "react";
import axios from "axios";
import { Optional, getByKey } from "../../../util/optional";
import { Result } from "../../../util/result";

type GalleryFile = {|
  id: number,
  name: string,
  modificationDate: number,
  type: string,
  thumbnailUrl: string,
|};

const check =
  <T>(predicate: (T) => boolean): ((T) => Optional<T>) =>
  (t: T): Optional<T> =>
    predicate(t) ? Optional.present(t) : Optional.empty();

export default function useGalleryListing({
  section,
}: {|
  section: string,
|}): {|
  galleryListing: Array<GalleryFile>,
|} {
  const [galleryListing, setGalleryListing] = React.useState<
    Array<GalleryFile>
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
      const getValueWithKey = (key: string) => (obj: { ... }) =>
        getByKey(key, obj).toResult(() => new Error(`key '${key}' is missing`));

      const arrayOfThings: Result<$ReadOnlyArray<{ ... }>> = isObject(data)
        .flatMap(isNotNull)
        .flatMap(getValueWithKey("data"))
        .flatMap(isObject)
        .flatMap(isNotNull)
        .flatMap(getValueWithKey("items"))
        .flatMap(isObject)
        .flatMap(isNotNull)
        .flatMap(getValueWithKey("results"))
        .flatMap(isArray)
        .flatMap((array) =>
          Result.all(...array.map((m) => isObject(m).flatMap(isNotNull)))
        );

      arrayOfThings
        .map((x) => console.debug(x))
        .orElseGet((errors) => {
          errors.forEach((e) => {
            console.error(e);
          });
        });

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
