// @flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import Grid from "@mui/material/Grid";
import BatchGridContainer from "./BatchGridContainer";
import { sleep } from "../../../util/Util";
import { take, incrementForever } from "../../../util/iterators";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";

type LoadingListArgs = {|
  onVisible: () => void,
  count: number,
  pageNumber: number,
  loading: boolean,
  placeholder: Node,
|};

function LoadingList({
  onVisible,
  count,
  placeholder,
  pageNumber,
  loading,
}: LoadingListArgs): Node {
  const ref = React.useRef<any>(null);
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const [intObs] = React.useState(
    new IntersectionObserver(([{ isIntersecting }]) => {
      if (isIntersecting) onVisible();
    })
  );

  React.useEffect(() => {
    void (async () => {
      if (ref.current) intObs.unobserve(ref.current);
      await sleep(100); // give React a chance to re-render
      if (ref.current && !loading) intObs.observe(ref.current);
    })();
  }, [pageNumber, loading]);

  return (
    <BatchGridContainer ref={ref} style={{ marginTop: 0 }}>
      {[...take(incrementForever(), count)].map((i) => (
        <Grid
          item
          xs={12}
          md={isSingleColumnLayout ? 6 : 12}
          xl={isSingleColumnLayout ? 4 : 12}
          key={i}
        >
          {placeholder}
        </Grid>
      ))}
    </BatchGridContainer>
  );
}

export default (observer(LoadingList): ComponentType<LoadingListArgs>);
