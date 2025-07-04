import React from "react";
import Jove, { getSelectedResults, getOrder, getOrderBy } from "./Jove";
import { getArticle, type Article } from "./JoveClient";
import { getSorting, stableSort } from "../../util/table";
import { createRoot } from "react-dom/client";

document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("tinymce-jove");
  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(<Jove />);
  }
});

parent.tinymce.activeEditor?.on("jove-insert", function () {
  if (parent && parent.tinymce) {
    let selectedResults = stableSort<Article>(
      getSelectedResults(),
      // @ts-expect-error TS doesn't like indexing by ""
      getSorting(getOrder(), getOrderBy())
    );
    if (selectedResults.length > 0) {
      const ed = parent.tinymce.activeEditor;
      // Get each of the articles from the backend/jove API
      const promises = selectedResults.map((article) => {
        return getArticle(article.id);
      });
      // Once all the data is collected created the content to be inserted.

      void Promise.all(promises).then((data) => {
        let articles = data.map((index) => {
          return index.data;
        });
        let joveContentToInsert = createJoveContent(articles);
        // @ts-expect-error global
        RS.tinymceInsertContent(joveContentToInsert.outerHTML, ed); //eslint-disable-line
        ed?.windowManager.close();
      });
      // @ts-expect-error global
      RS.trackEvent("JoveContentInserted", { selectedResults }); //eslint-disable-line
    }
  }
});

function createJoveContent(articles: Array<Article>) {
  let pTag = document.createElement("p");
  articles.forEach((joveArticle) => {
    let embedDiv = document.createElement("div");
    embedDiv.classList.add("embedIframeDiv");
    embedDiv.classList.add("mceNonEditable");

    let iframe = getIframeAndInnerContent(joveArticle);
    embedDiv.appendChild(iframe);
    pTag.appendChild(embedDiv);
  });
  return pTag;
}

function getIframeAndInnerContent(joveArticle: Article) {
  var iframe = document.createElement("iframe");
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
