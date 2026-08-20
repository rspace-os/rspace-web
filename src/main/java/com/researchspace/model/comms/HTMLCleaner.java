package com.researchspace.model.comms;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

class HTMLCleaner {

	// this covers the url either being at the start of a string, or separated
	// by a space
	private Pattern URL_WORD = Pattern.compile("(\\s|>)(https?://[^<>\\s]+)");
	private Pattern URL_WORD2 = Pattern.compile("^(https?://[^<>\\s]+)");

	private Pattern NEW_LINE = Pattern.compile("\r\n|\n|\r");

	String cleanHTMLStrict(String text, boolean wrapURLSInATags) {
		if (StringUtils.isEmpty(text)) {
			return text;
		}
		text = text.replaceAll(NEW_LINE.pattern(), "<br/> ");

		text = Jsoup.clean(text, Safelist.basic());
		Document d = Jsoup.parse(text);
		Elements aTags = d.select("a");
		for (Element el : aTags) {
			el.attr("target", "_blank");
			el.addClass("word-wrap");
		}
		text = d.select("body").html();
		if (wrapURLSInATags) {
			Matcher m2 = URL_WORD2.matcher(text);
			text = m2.replaceAll(" <a href='$1' rel='nofollow' class='word-wrap' target='_blank'>$1</a>");
			Matcher m = URL_WORD.matcher(text);
			text = m.replaceAll("$1<a href='$2' rel='nofollow' class='word-wrap' target='_blank'>$2</a>");
		}
		return text;
	}

}
