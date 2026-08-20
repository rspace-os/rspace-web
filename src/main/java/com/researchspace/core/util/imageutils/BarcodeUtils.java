package com.researchspace.core.util.imageutils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.commons.lang3.Validate;

import java.awt.image.BufferedImage;

public class BarcodeUtils {

	private static final int DEFAULT_BARCODE_HEIGHT = 150;

	/**
	 * Generates one-dimentional barcode image in CODE_128 format.
	 */
	public static BufferedImage generateBarcodeImage(String barcodeText, Integer width, Integer height) throws WriterException {
		Validate.notEmpty(barcodeText, "Barcode text cannot be empty");
		
		int minimalWidth = width != null ? width : 0;
		int minimalHeight = height != null ? height : DEFAULT_BARCODE_HEIGHT;

		Code128Writer barcodeWriter = new Code128Writer();
		BitMatrix bitMatrix = barcodeWriter.encode(barcodeText, BarcodeFormat.CODE_128, minimalWidth, minimalHeight);

		return MatrixToImageWriter.toBufferedImage(bitMatrix);
	}

	/**
	 * Generates two-dimensional QR barcode.
	 */
	public static BufferedImage generateQRCodeImage(String barcodeText, Integer width, Integer height) throws WriterException {
		Validate.notEmpty(barcodeText, "QR code text cannot be empty");
		
		int minimalWidth = width != null ? width : DEFAULT_BARCODE_HEIGHT;
		int minimalHeight = height != null ? height : DEFAULT_BARCODE_HEIGHT;

		QRCodeWriter barcodeWriter = new QRCodeWriter();
		BitMatrix bitMatrix = barcodeWriter.encode(barcodeText, BarcodeFormat.QR_CODE, minimalWidth, minimalHeight);

		return MatrixToImageWriter.toBufferedImage(bitMatrix);
	}
}
