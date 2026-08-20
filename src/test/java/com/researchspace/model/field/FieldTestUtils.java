package com.researchspace.model.field;

import java.util.Date;

public class FieldTestUtils {
	 static final String DEFAULT = "default";
	 public static final String OLD_FLD_NAME = "XXX";
	 static final String RTFDATA = "RTFDATA";
	
	public static TextField createTextField() {
		TextFieldForm ft = new TextFieldForm();
		ft.setDefaultValue(DEFAULT);
		ft.setName("A template");
		TextField tf = new TextField(ft);
		tf.setName(OLD_FLD_NAME);

		// tf.setRtfData(RTFDATA);
		tf.setFieldData(RTFDATA);
		tf.setColumnIndex(1);
		tf.setModificationDate(new Date().getTime());
		return tf;
	}
	
	/**
	 * Creates a populated StringFieldForm for tests
	 */
	public static StringFieldForm createStringForm() {
		StringFieldForm sft= new StringFieldForm();
		sft.setDefaultStringValue("x");
		sft.setIfPassword(true);
		sft.setName("stringForm");
		return sft;
	}
	
	public static NumberFieldForm createANumberFieldForm() {
		NumberFieldForm nft = new NumberFieldForm();
		nft.setMaxNumberValue(1000d);
		nft.setMinNumberValue(10d);
		nft.setDecimalPlaces((byte) 2);
		nft.setDefaultNumberValue(23d);
		nft.setName("numberField");
		return nft;
	}

}
