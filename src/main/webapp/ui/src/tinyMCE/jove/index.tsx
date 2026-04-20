import React from "react";
import Jove, { getSelectedResults, getOrder, getOrderBy } from "./Jove";
import { getArticle, type Article } from "./JoveClient";
import { getSorting, stableSort } from "../../util/table";
import { createRoot } from "react-dom/client";
import Analytics from "../../components/Analytics";
import AnalyticsContext from "../../stores/contexts/Analytics";

function JoveApp() {
  const { trackEvent } = React.useContext(AnalyticsContext);

  React.useEffect(() => {
    const editor = parent.tinymce.activeEditor;
    const handleInsert = () => {
      if (parent && parent.tinymce) {
        const selectedResults = stableSort<Article>(
          getSelectedResults(),
          // @ts-expect-error TS doesn't like indexing by ""
          getSorting(getOrder(), getOrderBy())
        );
        if (selectedResults.length > 0) {
          const ed = parent.tinymce.activeEditor;
          const promises = selectedResults.map((article) => {
            return getArticle(article.id);
          });

          void Promise.all(promises).then((data) => {
            const articles = data.map((index) => {
              return index.data;
            });
            const joveContentToInsert = createJoveContent(articles);
            // @ts-expect-error global
            RS.tinymceInsertContent(joveContentToInsert.outerHTML, ed); //eslint-disable-line
            ed?.windowManager.close();
          });
          trackEvent("JoveContentInserted", { selectedResults });
        }
      }
    };

    editor?.on("jove-insert", handleInsert);

    return () => {
      editor?.off("jove-insert", handleInsert);
    };
  }, [trackEvent]);

  return <Jove />;
}

document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("tinymce-jove");
  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(
      <Analytics>
        <JoveApp />
      </Analytics>,
    );
  }
});

function createJoveContent(articles: Array<Article>) {
  const pTag = document.createElement("p");
  articles.forEach((joveArticle) => {
    const embedDiv = document.createElement("div");
    embedDiv.classList.add("embedIframeDiv");
    embedDiv.classList.add("mceNonEditable");

    let iframe = getIframeAndInnerContent(joveArticle);
    embedDiv.appendChild(iframe);
    pTag.appendChild(embedDiv);
  });
  return pTag;
}

function getIframeAndInnerContent(joveArticle: Article) {
  const iframe = document.createElement("iframe");
  iframe.setAttribute("id", "embed-iframe");
  iframe.allow = "encrypted-media *";
  iframe.src = joveArticle.embed_url;
  iframe.scrolling = "no";
  iframe.setAttribute("height", "440");
  iframe.setAttribute("width", "460");
  iframe.setAttribute("border", "0");
  iframe.setAttribute("frameborder", "0");
  iframe.setAttribute("marginwidth", "0");
  iframe.setAttribute("marginwheight", "0");
  iframe.setAttribute("allowTransparency", "true");
  iframe.setAttribute("allowFullScreen", "");
  return iframe;
}
