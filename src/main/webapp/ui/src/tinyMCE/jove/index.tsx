import { createRoot } from "react-dom/client";
import { getSorting, stableSort } from "../../util/table";
import Jove, { getOrder, getOrderBy, getSelectedResults } from "./Jove";
import { type Article, getArticle } from "./JoveClient";

document.addEventListener("DOMContentLoaded", () => {
    const domContainer = document.getElementById("tinymce-jove");
    if (domContainer) {
        const root = createRoot(domContainer);
        root.render(<Jove />);
    }
});

parent.tinymce.activeEditor?.on("jove-insert", () => {
    if (parent?.tinymce) {
        const selectedResults = stableSort<Article>(
            getSelectedResults(),
            // @ts-expect-error TS doesn't like indexing by ""
            getSorting(getOrder(), getOrderBy()),
        );
        if (selectedResults.length > 0) {
            const ed = parent.tinymce.activeEditor;
            // Get each of the articles from the backend/jove API
            const promises = selectedResults.map((article) => {
                return getArticle(article.id);
            });
            // Once all the data is collected created the content to be inserted.

            void Promise.all(promises).then((data) => {
                const articles = data.map((index) => {
                    return index.data;
                });
                const joveContentToInsert = createJoveContent(articles);
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
    const pTag = document.createElement("p");
    articles.forEach((joveArticle) => {
        const embedDiv = document.createElement("div");
        embedDiv.classList.add("embedIframeDiv");
        embedDiv.classList.add("mceNonEditable");

        const iframe = getIframeAndInnerContent(joveArticle);
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
