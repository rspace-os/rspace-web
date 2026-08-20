package com.researchspace.core.testutil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import lombok.extern.slf4j.Slf4j;
/**
 * Utility class for generating random text files.
 *
 */
@Slf4j
public class RandomTextFileGenerator {

	private static final int WORDS_PER_LINE = 15;
	static final int COUNT = 30987908; // max index
	static final int MOST_FREQUENT_TERM = 29709477;// index of 500th most popular words
	
	public class FileSearchTerms {
		@Override
		public String toString() {
			return "FileSearchTerms [file=" + file + ", terms=" + Arrays.toString(terms) + "]";
		}
		File file;
		 FileSearchTerms(File file, String[] terms) {
			super();
			this.file = file;
			this.terms = terms;
		}
		public File getFile() {
			return file;
		}
		public String[] getTerms() {
			return terms;
		}
		String [] terms;
	}

	/**
	 * Generates <code>numFiles</code> files of <code>linesPerFile</code> lines of random text, each with 15
	 *  words perline, based on naturally occurring word frequency.
	 *  In addition, creates a file called textAttachmentstoLoad.csv which lists file names and 2 suitable
	 *   search terms that appear in the file
	 * @param pathToFiles The folder in which the random files will be placed
	 * @param numFiles
	 * @param linesPerFile
	 * @return A {@link List} of {@link FileSearchTerms} objects in the order they were created
	 * @throws IOException
	 */
	public List<FileSearchTerms> generate(File pathToFiles, int numFiles, int linesPerFile) throws IOException {
		List<FileSearchTerms> rc = new ArrayList<>(numFiles);
		InputStream is = CoreTestUtils.getWordFrequencyFile();

		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		Random random = new Random();
		Map<String, Long> wordToCount = outputWordsToFile(br);
		Map<Long, String> countTowrd = invert(wordToCount);
		File srchTermsFile = new File(pathToFiles, "/textAttachmentstoLoad.csv");
		FileWriter srchTermsFileWriter = new FileWriter(srchTermsFile);
		for (int i = 0; i < numFiles; i++) {
			if( i % 50 == 0) {
        log.info("creating file {}/{}", i, numFiles);
			}
			String fName = i + ".txt";
			File textFile = new File(pathToFiles, fName);
			String[] terms = new String[2];
			boolean searchTermsWritten = false;
			FileWriter fw = new FileWriter(textFile);
			for (int lines = 0; lines < linesPerFile; lines++) {
				int searchCount = 0;
				for (int j = 0; j < WORDS_PER_LINE; j++) {
					int next = random.nextInt(COUNT);
					String nextWord = "";
					for (Long cumTotal : countTowrd.keySet()) {
						if (next <= cumTotal) {
							nextWord = countTowrd.get(cumTotal);
							fw.write(nextWord + " ");
							break;
						}
					}
					// we don't want to search with too common a term, choose at
					// least 200th most popular
					if (next > MOST_FREQUENT_TERM && searchCount < 2) {
						terms[searchCount] = nextWord;
						searchCount++;
					}
					if (searchCount == 2 && !searchTermsWritten) {
						srchTermsFileWriter.write(i + ".txt," + terms[0] + "," + terms[1] + "\n");
						searchTermsWritten = true;
						rc.add(new FileSearchTerms(textFile, terms));
					}
				}
				fw.write(".\n");
			}
			if (!searchTermsWritten) {
			    System.err.println("search terms not written into " + i + ".txt");
			}
			fw.close();
		}
		srchTermsFileWriter.close();
		return rc;
	}

	private static Map<Long, String> invert(Map<String, Long> wordToCount) {
		Map<Long, String> rc = new TreeMap<>();
		for (String key : wordToCount.keySet()) {
			rc.put(wordToCount.get(key), key);
		}
		return rc;
	}

	private static Map<String, Long> outputWordsToFile(BufferedReader br) throws IOException {
		String line;
		Map<String, Long> rc = new HashMap<>();
		while ((line = br.readLine()) != null) {
			String[] fields = line.split("\\s+");
			rc.put(fields[0], Long.parseLong(fields[1]));
		}
		return rc;
	}

}
