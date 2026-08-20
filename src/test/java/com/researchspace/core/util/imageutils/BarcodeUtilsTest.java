package com.researchspace.core.util.imageutils;

import com.google.zxing.WriterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class BarcodeUtilsTest {

	@Test
	public void testBarcodeGeneration() throws WriterException, IOException {
		// short content barcode, default size
		BufferedImage shortContentBarcode = BarcodeUtils.generateBarcodeImage("SA1", null, null);
		assertNotNull(shortContentBarcode);
		assertEquals(78, shortContentBarcode.getWidth());
		assertEquals(150, shortContentBarcode.getHeight());

		File outputfile = Files.createTempFile("shortContentBarcode", ".png").toFile();
		ImageIO.write(shortContentBarcode, "png", outputfile);
		assertEquals(91, outputfile.length());

		// custom size
		shortContentBarcode = BarcodeUtils.generateBarcodeImage("SA1", 100, 200);
		assertNotNull(shortContentBarcode);
		assertEquals(100, shortContentBarcode.getWidth());
		assertEquals(200, shortContentBarcode.getHeight());

		outputfile = Files.createTempFile("shortContentBarcode2", ".png").toFile();
		ImageIO.write(shortContentBarcode, "png", outputfile);
		assertEquals(102, outputfile.length());

		// long barcode, default size
		BufferedImage longContentBarcode = BarcodeUtils.generateBarcodeImage("SS123456789012345678901234567890", null,
				null);
		assertNotNull(longContentBarcode);
		assertEquals(243, longContentBarcode.getWidth());
		assertEquals(150, longContentBarcode.getHeight());

		outputfile = Files.createTempFile("longContentBarcode", ".png").toFile();
		ImageIO.write(longContentBarcode, "png", outputfile);
		assertEquals(138, outputfile.length());

		// empty content rejected
		try {
			BarcodeUtils.generateBarcodeImage("", null, null);
			fail("exception expected on empty content");
		} catch (IllegalArgumentException iae) {
			assertEquals("Barcode text cannot be empty", iae.getMessage());
		}
	}

	@Test
	@EnabledOnJre(JRE.JAVA_8)
	public void testQRCodeGeneration() throws WriterException, IOException {
		// short content QR, default size
		BufferedImage shortContentQR = BarcodeUtils.generateQRCodeImage("SA1", null, null);
		assertNotNull(shortContentQR);
		assertEquals(150, shortContentQR.getWidth());
		assertEquals(150, shortContentQR.getHeight());

		File outputfile = Files.createTempFile("shortContentQR", ".png").toFile();
		ImageIO.write(shortContentQR, "png", outputfile);
		assertEquals(286, outputfile.length());

		// custom size
		shortContentQR = BarcodeUtils.generateQRCodeImage("SA1", 200, 250);
		assertNotNull(shortContentQR);
		assertEquals(200, shortContentQR.getWidth());
		assertEquals(250, shortContentQR.getHeight());

		outputfile = Files.createTempFile("shortContentQR2", ".png").toFile();
		ImageIO.write(shortContentQR, "png", outputfile);
		 assertEquals(297, outputfile.length());

		// long content QR, default size
		BufferedImage longContentQR = BarcodeUtils.generateQRCodeImage("SS123456789012345678901234567890", null, null);
		assertNotNull(longContentQR);
		assertEquals(150, longContentQR.getWidth());
		assertEquals(150, longContentQR.getHeight());

		outputfile = Files.createTempFile("longContentQR", ".png").toFile();
		ImageIO.write(longContentQR, "png", outputfile);
		assertEquals(285, outputfile.length());
		// empty content rejected
		try {
			BarcodeUtils.generateQRCodeImage("", null, null);
			fail("exception expected on empty content");
		} catch (IllegalArgumentException iae) {
			assertEquals("QR code text cannot be empty", iae.getMessage());
		}
	}

	@Test
	@EnabledOnJre(JRE.JAVA_11)
	public void testQRCodeGeneration_JDK11() throws WriterException, IOException {
		// short content QR, default size
		BufferedImage shortContentQR = BarcodeUtils.generateQRCodeImage("SA1", null, null);
		assertNotNull(shortContentQR);
		assertEquals(150, shortContentQR.getWidth());
		assertEquals(150, shortContentQR.getHeight());

		File outputfile = Files.createTempFile("shortContentQR", ".png").toFile();
		ImageIO.write(shortContentQR, "png", outputfile);
		assertEquals(287, outputfile.length());

		// custom size
		shortContentQR = BarcodeUtils.generateQRCodeImage("SA1", 200, 250);
		assertNotNull(shortContentQR);
		assertEquals(200, shortContentQR.getWidth());
		assertEquals(250, shortContentQR.getHeight());

		outputfile = Files.createTempFile("shortContentQR2", ".png").toFile();
		ImageIO.write(shortContentQR, "png", outputfile);
		// assertEquals(297, outputfile.length());
		assertEquals(303, outputfile.length());

		// long content QR, default size
		BufferedImage longContentQR = BarcodeUtils.generateQRCodeImage("SS123456789012345678901234567890", null, null);
		assertNotNull(longContentQR);
		assertEquals(150, longContentQR.getWidth());
		assertEquals(150, longContentQR.getHeight());

		outputfile = Files.createTempFile("longContentQR", ".png").toFile();
		ImageIO.write(longContentQR, "png", outputfile);
		// assertEquals(285, outputfile.length());
		assertEquals(292, outputfile.length());

		// empty content rejected
		try {
			BarcodeUtils.generateQRCodeImage("", null, null);
			fail("exception expected on empty content");
		} catch (IllegalArgumentException iae) {
			assertEquals("QR code text cannot be empty", iae.getMessage());
		}
	}

}
