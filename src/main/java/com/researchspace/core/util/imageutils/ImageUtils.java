package com.researchspace.core.util.imageutils;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.researchspace.core.util.TransformerUtils.toList;
import static java.lang.String.format;

/**
 * Utility class for manipulation of images
 */
public class ImageUtils {

	public static final int DEFAULT_THUMBNAIL_DIMNSN = 76;

	public static final int MAX_PAGE_DISPLAY_WIDTH = 644;
	/**
	 * Array of possible TIFF extensions ( without the initial '.')
	 */
	public static final List<String> TIFF_EXTENSIONS = Collections.unmodifiableList(toList("tif", "tiff", "TIFF", "TIF"));

	private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);

	private ImageUtils() {

	}
	
	//support-150 - try to log classdefnotfound error
	static {
		try {
			long memory = IJ.currentMemory();
			log.info("Current IJ memory usage is {}", memory);
		} catch (Throwable t) {
			log.error("error loading IJ:{}", t.getMessage());
		}
	}

	/**
	 * Gets a buffered image from a standard image - jpeg, gif, png. NOT Tiff.
	 * <br/> If there is any possibility that this image might be a TIFF image, then
	 * use <code>getBufferedImageFromUploadedFile</code> instead.
	 * 
	 * @param is
	 *            an open {@link InputStream} to an image resource
	 * @return A {@link BufferedImage}
	 * @throws IOException
	 */
	public static Optional<BufferedImage> getBufferedImageFromInputImageStream(InputStream is) throws IOException {
		try {
			// Read buffered image using Thumbnailator package to maintain orientation from EXIF metadata
			BufferedImage bufferedImage = Thumbnails.of(is).scale(1).asBufferedImage();
			return Optional.ofNullable(bufferedImage);
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	/**
	 * Gets a buffered image from a tif file
	 * 
	 * @param tiffFile A TIFF file.
	 * @return an Optional {@link BufferedImage} or <code>Optional.empty</code> if the file could
	 *         not be read.
	 * @throws IOException
	 */
	public static Optional<BufferedImage> getBufferedImageFromTiffFile(File tiffFile)
			throws IOException {
		
		ImagePlus imp = IJ.openImage(tiffFile.getAbsolutePath());
		if(imp == null) {
			BufferedImage bi = null;
			try {
			 bi = ImageIO.read(tiffFile);
			} catch (IIOException | IndexOutOfBoundsException e) {
				log.warn("Couldn't parse file {}: {}", tiffFile.getAbsolutePath(), e.getMessage());
			}
			return Optional.ofNullable(bi);
		}
		return Optional.ofNullable(imp.getBufferedImage());
	}

	/**
	 * Boolean test for whether an extension implies the image is a TIFF file.
	 * 
	 * @param extension
	 * @return
	 */
	public static boolean isTiff(String extension) {
		return "tif".equalsIgnoreCase(extension) || "tiff".equalsIgnoreCase(extension);
	}
	
	/**
	 * Note- transfers file contents to new file, so file argument may be
	 * unavailable after calling this method. This is a convenience method to
	 * extract a BufferedImage from an uploaded image file. <br/>
	 * This method assumes that the file really is an image file.
	 * 
	 * @param file the file to extract the buffered image from
	 * @return BufferedImage
	 * @throws IOException
	 */
	public static Optional<BufferedImage> getBufferedImageFromUploadedFile(IMultipartFile file)
			throws IOException {
		String[] tempName = file.getOriginalFilename().split(Pattern.quote("."));
		String extensionType = tempName[tempName.length - 1];
		if (isTiff(extensionType)) {
			File tempTiff = File.createTempFile("original", "tif");
			file.transferTo(tempTiff);
			return getBufferedImageFromTiffFile(tempTiff);
		} else {
			return getBufferedImageFromInputImageStream(file.getInputStream());
		}
	}

	/**
	 * This is a convenience method to extract a BufferedImage from an
	 * InputStream object, whether jpeg,tiff, gif or png.  This method closes the InputSTream after reading.
	 * 
	 * @param extensionType
	 * @param stream
	 * @return BufferedImage
	 * @throws IOException
	 */
	public static Optional<BufferedImage> getBufferedImageFromUploadedFile(String extensionType,
			InputStream stream) throws IOException {

		if (isTiff(extensionType)) {
			File tempTiff = File.createTempFile("original", ".tif");
			try (stream; FileOutputStream fos = new FileOutputStream(tempTiff)) {
				byte[] buff = new byte[1024];
				while (stream.read(buff) != -1) {
					fos.write(buff);
				}
			}
			return getBufferedImageFromTiffFile(tempTiff);
		}
		return getBufferedImageFromInputImageStream(stream);
	}

	/**
	 * Generates thumbnail of BufferedImage. Client is responsible to close the
	 * ByteArrayOutputStream after this method returns.
	 * 
	 * @param image
	 * @param width
	 *            desired thumbnail width
	 * @param height
	 *            desired thumbnail height
	 * @param baos
	 *            An open ByteArrayOutputStream
	 * @param outputFormat
	 *            A standard image format - e.g., "png", "jpg" etc.,
	 * @throws IOException
	 * @throws IllegalArgumentException
	 *             if width or height are <=0
	 */
	public static void createThumbnail(BufferedImage image, int width, int height, 
			ByteArrayOutputStream baos, String outputFormat) throws IOException {

		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Dimensions must be > 0 but were [" + width + ","
					+ height + "]");
		}
		if (image == null) {
			throw new IllegalArgumentException("Can't create a thumbnail from a null image");
		}
		Thumbnails.of(image)
				.size(width, height)
				.outputFormat(outputFormat)
				.toOutputStream(baos);

	}

	/**
	 * Given a buffered image, scales down to the specified width, maintaining
	 * aspect ratio. <br>
	 * Can handle gif, png, jeg and tiff images <br>
	 * The returned image will be no wider than maxWidth pixels.
	 * 
	 * @param bufferedImage
	 *            A non-null image
	 * @param maxwidth
	 *            The maximum width of the desired scaled image
	 * @return A new scaled image, or the original scaled image if its width was
	 *         already smaller than <code>maxwidth</code>.
	 * @throws IllegalArgumentException
	 *             if <code>maxWidth</code> < 1
	 */
	public static BufferedImage scaleImageToWidthWithAspectRatio(BufferedImage bufferedImage,
			final int maxwidth) {
		if (maxwidth < 1) {
			throw new IllegalArgumentException("Max width must be > 1");
		}
		if (bufferedImage.getWidth() > maxwidth) {
			int imageType = bufferedImage.getType();

			// Hack to solve the issue with PNG files and tomcat server
			if (imageType == 0) {
				imageType = 5;
			}
			double aspectRatio = (bufferedImage.getWidth() * 1.0 / bufferedImage.getHeight());
			int newHeight = (int) (maxwidth / aspectRatio);

			BufferedImage resizedImage = new BufferedImage(maxwidth, newHeight, imageType);
			Graphics2D g = resizedImage.createGraphics();
			//RSPAC-867
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

			g.drawImage(bufferedImage, 0, 0, maxwidth, newHeight, null);
			g.dispose();
			return resizedImage;
		} else {
			return bufferedImage;
		}
	}

	/**
	 * Produces a scaled image from an input stream to an {@link BufferedImage}
	 * 
	 * @param inputStream
	 * @param maxWidth
	 * @param extension
	 * @return
	 * @throws IOException
	 * @see {@link ImageUtils#scaleImageToWidthWithAspectRatio(BufferedImage, int)}
	 */
	public static Optional<BufferedImage> scale(InputStream inputStream, int maxWidth, 
			String extension) throws IOException {
		Optional<BufferedImage> original = getBufferedImageFromUploadedFile(extension, inputStream);
		BufferedImage scaled = null;
		if (original.isPresent()) {
			scaled = scaleImageToWidthWithAspectRatio(original.get(), maxWidth);
		}

		return Optional.ofNullable(scaled);
	}
	
	/**
	 * Boolean test for whether the encoded image data accessed by the <code>inputInputStream</code> can be
	 *  scaled. This input stream should be closed after calling this method, and another used
	 *  to perform the actual scaling.
	 * @param imageInputStream
	 * @return <code>true</code> if it can, <code>false</code> otherwise.
	 * @throws IOException 
	 */
	public static boolean canScaleBySampling(InputStream imageInputStream) throws IOException {
		ImageInputStream iis = ImageIO.createImageInputStream(imageInputStream);
		Iterator<ImageReader> iter = ImageIO.getImageReaders(iis);
		return iter.hasNext();
	}
	/**
	 * Scales an image stream by sampling as it is read, not reading all into memory
	 * @param imageInputStream an input stream to image data
	 * @param maxWidth The max width of the resulting scaled image
	 * @param imageName The image file name (used for logging only)
	 * @return The scaled image
	 * @throws IOException
	 * @throws {@link UnsupportedOperationException} if it can't handle the image. Clients should test this beforehand by calling
	 *   <code>canScaleBySampling(InputStream imageInputStream)</code>
	 *   @throws IllegalArgumentException if : <br/>
	 *    - <code>maxWidth</code> < 1, <br/>
	 *    -  cannot create input stream into the image.
	 */
	public static BufferedImage scaleBySampling(InputStream imageInputStream, int maxWidth,
			String imageName) throws IOException {

		Validate.isTrue(imageInputStream != null, "Input stream must not be null");
		ImageInputStream iis = ImageIO.createImageInputStream(imageInputStream);
		Validate.isTrue(iis != null, "ImageInputstream must not be null");
		Iterator<ImageReader> iter = ImageIO.getImageReaders(iis);
		if (!iter.hasNext()) {
			String inputStreamAsString = imageInputStream.toString();
			log.warn("There is no image reader for image {} with inputstream [{}],", imageName, inputStreamAsString);
			throw new ImageProcessingFailureException(
					format("Can't read image %s from input Stream %s", imageName, inputStreamAsString));
		}
		ImageReader reader = (ImageReader) iter.next();
		reader.setInput(iis, true, true);

		ImageReadParam params = reader.getDefaultReadParam();
		int sampling = calculateSampling(reader.getWidth(0));
		params.setSourceSubsampling(sampling, sampling, 0, 0);

		return reader.read(0, params);
	}

	private static int calculateSampling(float width) {
		if (width < (float) ImageUtils.MAX_PAGE_DISPLAY_WIDTH) {
			return 1;// sample all pixels
		}
		return Double.valueOf(Math.ceil(width / (float) ImageUtils.MAX_PAGE_DISPLAY_WIDTH)).intValue();
	}

	/**
	 * Scales an image with the thumbnailator library maintaining ascpect ratio and ensuring the width is below/equal
	 * to the specified amount.
	 * @param imageInputStream the input stream of the original image
	 * @param maxWidth the max image the scaled image can be
	 * @return the {@link BufferedImage} of the scaled image
	 * @throws IOException
	 */
	public static BufferedImage scaleWithThumbnailator(InputStream imageInputStream, int maxWidth) throws IOException {
		return Thumbnails.of(imageInputStream)
				.width(maxWidth)
				.keepAspectRatio(true)
				.asBufferedImage();
	}
	/**
	 * Converts a tiff file to a png file
	 * @param tiffFileToConvert the Tiff file to convert
	 * @param destinationFolder The folder that we want to put the converted png into
	 * @param newFileName an optional file name ( no extension needed) for the new png. If not supplied, new name will
	 *  the same as the tiff file except for the extension, which will be 'tif'.
	 * @return The new png file
	 * @throws IOException 
	 * @throws IllegalArgumentException if :
	 *        <ul><li>
	 *         either file is null
	 *         <li> if destination folder is not a writeable folder
	 *         <li> if tiff file is not a tiff file.
	 *         </ul>
	 */
	public static  File convertTiffToPng(File tiffFileToConvert, File destinationFolder, String newFileName) throws IOException {
		if (tiffFileToConvert == null || destinationFolder == null) {
			throw new IllegalArgumentException("files can't be null");
		}
		if(!tiffFileToConvert.canRead()){
			throw new IllegalArgumentException("Can't read file:" +tiffFileToConvert);
		}
		if(!destinationFolder.exists() || !destinationFolder.isDirectory() || !destinationFolder.canWrite()) {
			throw new IllegalArgumentException("destinationFolder must be a readable directory" +destinationFolder);
		}
		if(!isTiff(FilenameUtils.getExtension(tiffFileToConvert.getName()))) {
			throw new IllegalArgumentException(tiffFileToConvert + "isn't a tiff file?");
		}
		ImagePlus imp = IJ.openImage(tiffFileToConvert.getAbsolutePath()); 
		FileSaver saver = new FileSaver(imp);
		String path = getNewImageFileName(tiffFileToConvert, destinationFolder, newFileName);
		boolean ok = saver.saveAsPng(path);
		if(ok){
			return new File(path);
		} else {
			throw new IOException("Could not convert " + tiffFileToConvert + " to  a png format");
		}
	}

	private static String getNewImageFileName(File tiffFileToConvert, File destinationFolder, String newFileName) {
		String path = "";
		if(StringUtils.isEmpty(newFileName) ) {
			path = destinationFolder.getAbsolutePath() + File.separator
		     + FilenameUtils.getBaseName(tiffFileToConvert.getAbsolutePath()) + ".png";
		} else {
			path = destinationFolder.getAbsolutePath() + File.separator + newFileName +".png";
		}
		return path;
	}
	/**
	 * Converts a {@link BufferedImage} to a byte []
	 * @param img
	 * @param outputFormat 'png, 'jpg' etc
	 * @return a byte []
	 * @throws IOException
	 */
	public static byte[] toBytes(BufferedImage img, String outputFormat) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(img, outputFormat, baos);
		return baos.toByteArray();
	}

	/**
	 * Rotates a tiff file by a multiple of Pi/2 radians (90 degrees).<br/>
	 * This uses the underlying ImageJ library to handle rotation. <br/>
	 * The originalFile <code>tiffFile</code> is unchanged by this method.
	 * @param tiffFile A tiff file
	 * @param rotation A multiple of Pi/2 to rotate by: 0,1,2 or3. Other values will be 
	 *    caclulated mod 4. If rotation is 0, this method does nothing and returns <code>false</code>.
	 * @param outfile an outfile to write the rotated tiff. If suffix is not 'tif' the suffix
	 *  will be modified to 'tif'.
	 * @return <code>true</code> if the image was rotated and saved OK
	 * @throws IllegalArgumentException if tiffFile does not exist or is unreadable
	 */
	public static boolean rotateTiff(File tiffFile, Integer rotation, File outfile) {
		Validate.noNullElements(new Object [] {tiffFile, rotation, outfile},"No arguments can be null");
		Validate.isTrue(tiffFile.exists() && tiffFile.canRead(), 
				 String.format("tiff file %s is not a readable file", tiffFile.getAbsolutePath()));
		ImagePlus imp = IJ.openImage(tiffFile.getAbsolutePath()); 
		if(imp == null) {
			log.error("Could not open tif file {} for reading by ImageJ - cannot rotated", tiffFile.getAbsolutePath());
			return false;
		}
		int toApply = rotation % 4;
		ImagePlus rotated = null;
		boolean saved = false;
		if(toApply == 1) {
			rotated = new ImagePlus("r", imp.getProcessor().rotateRight());		
		}
		else if(toApply == 2) {
			rotated = new ImagePlus("r", imp.getProcessor().rotateRight().rotateRight());
		} else if(toApply == 3) {
			rotated = new ImagePlus("r", imp.getProcessor().rotateLeft());
		}
		if(rotated != null) {
			saved = IJ.saveAsTiff(rotated, outfile.getAbsolutePath());
		}
		
		return saved;
	}
	
	public static BufferedImage rotateImage(BufferedImage image, double angle) {
		final int type = 5;
		double sin = Math.abs(Math.sin(angle)), cos = Math.abs(Math.cos(angle));
		int w = image.getWidth(), h = image.getHeight();
		int neww = (int) Math.floor(w * cos + h * sin), newh = (int) Math.floor(h * cos + w * sin);
		int imageType = image.getType();

		// Hack to solve the issue with PNG files and tomcat server
		if (imageType == 0) {
			imageType = type;
		}
		BufferedImage result = new BufferedImage(neww, newh, imageType);
		Graphics2D g = result.createGraphics();
		g.translate((neww - w) / 2, (newh - h) / 2);
		g.rotate(angle, w / (double) 2, h / (double) 2);
		g.drawRenderedImage(image, null);
		return result;
	}
	
	/**
	 * Calculate file extension of base64 image string received from the UI. 
	 * 
	 * @param base64WebImage
	 * @return png/jpg
	 */
	public static String getExtensionFromBase64DataImage(String base64WebImage) {
		validateIsBase64WebImageString(base64WebImage);
		return base64WebImage.split(",")[0].contains("image/jpeg") ? "jpg" : "png";
	}

	/**
	 * Returns bytes of base64 image string received from the UI
	 * @param base64WebImage
	 */
	public static byte[] getImageBytesFromBase64DataImage(String base64WebImage) {
		validateIsBase64WebImageString(base64WebImage);
		return Base64.decodeBase64(base64WebImage.split(",")[1]);
	}

	private static void validateIsBase64WebImageString(String base64WebImage) {
		Validate.notEmpty(base64WebImage, "Expected base64 web image string but was null or empty.");
		Validate.isTrue(base64WebImage.contains(","), "Expected base64 web image string to contain a ','.");
		Validate.isTrue(Base64.isBase64(base64WebImage.split(",")[1]), "Image string doesn't seem to be base64-encoded");
	}

	/**
	 * @param imageBytes
	 * @param fileExtension png/jpg
	 */
	public static String getBase64DataImageFromImageBytes(byte[] imageBytes, String fileExtension) {
		Validate.notNull(imageBytes, "Image bytes empty");
		Validate.notNull(fileExtension, "File extension (png/jpg) must be provided");

		String fileType = null;
		if (fileExtension.equalsIgnoreCase("jpg")) {
			fileType = "jpeg";
		} else if (fileExtension.equalsIgnoreCase("png")) {
			fileType = "png";
		} else {
			throw new IllegalArgumentException("unrecognized file extensions for base64 image conversion: " + fileExtension);
		}
		return "data:image/" + fileType + ";base64," + Base64.encodeBase64String(imageBytes);
	}

}
