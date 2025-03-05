// @flow

import axios, { type AxiosPromise } from "@/common/axios";
import { SearchParam } from "./Enums";

export type ArticleId = any;

export type Article = {|
  id: ArticleId,
  hasvideo: boolean,
  section: string,
  thumbnail: string,
  title: string,
  url: string,
  embed_url: string,
|};

export type JoveSearchResult = {|
  articlelist: Array<Article>,
  countall: number,
  pagecount: number,
  pagesize: number,
|};

export const search = async (
  searchParam: $Values<typeof SearchParam>,
  query: string,
  page: number,
  pageSize: number
): Promise<?{|
  data: JoveSearchResult,
|}> => {
  let request = {};
  if (searchParam === SearchParam.queryString) {
    request = { queryString: query, pageNumber: page, pageSize: pageSize };
  } else if (searchParam === SearchParam.author) {
    request = { author: query, pageNumber: page, pageSize: pageSize };
  } else if (searchParam === SearchParam.institution) {
    request = { institution: query, pageNumber: page, pageSize: pageSize };
  }
  RS.trackEvent("JoveSearchRequest", request); //eslint-disable-line
  const result = await makeSearchRequest(request);
  if (result.data.joveSearchResult) {
    return { data: result.data.joveSearchResult };
  } else {
    return null;
  }
};

export const getArticle = async (
  articleId: ArticleId
): Promise<{| data: Article |}> => {
  const article = await makeGetArticleRequest(articleId);
  return { data: article.data.joveArticle };
};

const makeSearchRequest = (
  joveSearchRequest: mixed
): AxiosPromise<mixed, {| joveSearchResult: JoveSearchResult |}> => {
  return axios.post("/apps/jove/search", joveSearchRequest);
};

const makeGetArticleRequest = (articleId: ArticleId) => {
  return axios.get<ArticleId>("/apps/jove/article/" + articleId);
};
