package com.researchspace.export.pdf;

import static com.researchspace.core.util.FieldParserConstants.DATA_MATHID;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Document.OutputSettings.Syntax;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SvgToPngConverter {

  private ImageTranscoder transcoder;

  public SvgToPngConverter() {
    transcoder = new PNGTranscoder();

    // for better quality (transcoded image twice as large)
    Float pixelToMilli = 0.13f;
    transcoder.addTranscodingHint(ImageTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, pixelToMilli);
  }

  /**
   * Converts image to png, writing to <code>ostream</code>. Flushes. Client that created the <code>
   * ostream</code> should close it.
   *
   * @param svg
   * @param ostream
   * @throws IOException
   * @throws TranscoderException
   */
  public void convert(String svg, OutputStream ostream) throws IOException, TranscoderException {
    Reader reader = new StringReader(svg);
    TranscoderInput input = new TranscoderInput(reader);
    TranscoderOutput output = new TranscoderOutput(ostream);
    transcoder.transcode(input, output);
    ostream.flush();
  }

  public String replaceSvgObjectWithImg(String html) {
    Document jsoup = Jsoup.parse(html);
    Elements maths = jsoup.getElementsByAttribute(DATA_MATHID);

    for (int i = 0; i < maths.size(); i++) {
      Element rsEquation = maths.get(i);
      Element svgObject = rsEquation.getElementsByTag("object").first();
      svgObject
          .tagName("img")
          .attr("src", svgObject.attr("data"))
          .attr("width", convertDimensionFromExToPx(svgObject.attr("data-svgwidth")))
          .attr("height", convertDimensionFromExToPx(svgObject.attr("data-svgheight")))
          .removeAttr("data")
          .removeAttr("data-svgwidth")
          .removeAttr("data-svgheight")
          .removeAttr("type");

      // replace <a><object></a> with <img>
      rsEquation.children().get(0).replaceWith(svgObject);
    }
    OutputSettings output = new OutputSettings().syntax(Syntax.xml);
    jsoup.outputSettings(output);
    return jsoup.html();
  }

  private static final int EX_TO_PX_RATIO = 6;

  private String convertDimensionFromExToPx(String attr) {
    if (attr != null && attr.endsWith("ex")) {
      int pxValue = (int) Double.parseDouble(attr.substring(0, attr.length() - 2)) * EX_TO_PX_RATIO;
      return "" + pxValue;
    }
    return attr;
  }
}
