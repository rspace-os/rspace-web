package com.researchspace.core.util;

import org.apache.commons.lang3.ArrayUtils;

/**
 * Constants of CSS classnames and other text-field related attributes
 * 
 * <h3>Maintenance notes</h3>
 * <p>
 * If a new CSS class is added that identifies an Rspace -generated link, image
 * or other HTML element then we need to add this to
 * {@link #RSPACE_IMAGE_CSSCLASSES}.
 *
 */
public class FieldParserConstants {

	/**
	 * CSS classname for attachment file-type icons. See attachmentLink.vm
	 */
	public static final String ATTACHMENTICON_CLASSNAME = "attachmentIcon";

	public static final String ANNOTATION_IMG_CLASSNAME = "annotation";

	/**
	 * CSS classname for attachment file-type icons. See attachmentLink.vm
	 */
	public static final String ATTACHMENTINFOICON_CLASSNAME = "attachmentInfoIcon";

	public static final String IMAGE_DROPPED_CLASS_NAME = "imageDropped";
	
    public static final String NET_FS_CLASSNAME = "nfs_file";

	public static final String SKETCH_IMG_CLASSNAME = "sketch";
	/**
	 * CSS class name for chemistry images
	 */
	public static final String CHEM_IMG_CLASSNAME = "chem";

	public static final String CHEM_ATTACHMENT_CLASSNAME = "chemAttachmentLinked";
	/**
	 * CSS class name for Math elements
	 */
	public static final String MATH_CLASSNAME = "rsEquation";

	public static final String VIDEO_CLASSNAME = "videoDropped";

	public static final String AUDIO_CLASSNAME = "audioDropped";

	public static final String ATTACHMENT_CLASSNAME = "attachmentLinked";

	public static final String EXTERNALLINK_BADGE_CLASSNAME = "externalLinkBadge";

	public static final String COMMENT_CLASS_NAME = "commentIcon";

	public static final String LINKEDRECORD_CLASS_NAME = "linkedRecord";

	public static final String IMAGE_THMNAIL_DROPPED_CLASS_NAME = "inlineImageThumbnail";

	public static final String ATTACH_ELEMENTS_KEY = "Attachments";

	public static final String VIDEOS_ELEMENTS_KEY = "Videos";

	public static final String AUDIOS_ELEMENTS_KEY = "Audios";

	public static final String IMAGES_ELEMENTS_KEY = "Images";

	public static final String IMAGEANNOTATIONS_ELEMENTS_KEY = "ImageAnnotations";

	public static final String SKETCHES_ELEMENTS_KEY = "Sketches";

	public static final String CHEM_ELEMENTS_KEY = "ChemElements";

	public static final String CHEM_ATTACHMENT_KEY = "Chemistry";
	
	public static final String MATH_ELEMENTS_KEY = "MathElements";

	public static final String THUMBNAIL_ELEMENT_KEY = "Thumbnails";
	
	public static final String LINKEDRECORDS_ELEMENTS_KEY = "LinkedRecords";
	

	public static final String COMMENTS_ELEMENTS_KEY = "Comments";
	
	private static final String[] FIELDPARSER_KEYS = new String[] { ATTACH_ELEMENTS_KEY, VIDEOS_ELEMENTS_KEY,
			AUDIOS_ELEMENTS_KEY, IMAGES_ELEMENTS_KEY, IMAGEANNOTATIONS_ELEMENTS_KEY, SKETCHES_ELEMENTS_KEY,
			CHEM_ELEMENTS_KEY, CHEM_ATTACHMENT_KEY, MATH_ELEMENTS_KEY, LINKEDRECORDS_ELEMENTS_KEY, COMMENTS_ELEMENTS_KEY };

	/**
	 * Identifies a link as a link to an external file, not an internal Gallery
	 * file.
	 */
	public static final String EXTERNALFILESTORELINK_KEY = "data-externalfilestore";

	public static final String DATA_CHEM_FILE_ID = "data-chemfileid";

	public static final String COMMENT_ICON_URL = "/images/commentIcon.gif";

	public static final String TAG_IMG = "img";

	public static final String TAG_LINK = "a";

	public static final String EXPORT_AV_REPLACEMENT = "mediaPlayerHTMLReplacement";

	public static final String DATA_TYPE_ANNOTATION = "annotation";
	
	/**
	 * The name of attribute in a Mathelement containing the id of the math item
	 */
	public static final String DATA_MATHID = "data-mathid";

	/**
	 * CSS class names of links / images inserted into text fields
	 */
	private static final String[] RSPACE_IMAGE_CSSCLASSES = new String[] { ATTACHMENT_CLASSNAME, AUDIO_CLASSNAME,
			CHEM_IMG_CLASSNAME, CHEM_ATTACHMENT_CLASSNAME, COMMENT_CLASS_NAME, IMAGE_DROPPED_CLASS_NAME, LINKEDRECORD_CLASS_NAME,
			SKETCH_IMG_CLASSNAME, VIDEO_CLASSNAME, ANNOTATION_IMG_CLASSNAME, MATH_CLASSNAME };

	

	/**
	 * Gets an array of CSS classes that are used for content inserted into
	 * TinyMCE by RSpace operations.
	 * 
	 * @return
	 */
	public static String[] getRSpaceImageCSSClasses() {
		return RSPACE_IMAGE_CSSCLASSES.clone();
	}
	
	/**
	 * Gets an array of CSS classes that are used for content inserted into
	 * TinyMCE by RSpace operations.
	 * 
	 * @return
	 */
	public static String[] getFieldParseKeys() {
		return FIELDPARSER_KEYS.clone();
	}


	/**
	 * Boolean test fo whether the argument is a known RSpace css class name
	 * that identifies linked objects
	 * 
	 * @param toTest
	 * @return
	 */
	public static boolean isRSpaceCSSClassname(String toTest) {
		return ArrayUtils.contains(RSPACE_IMAGE_CSSCLASSES, toTest);
	}
}
