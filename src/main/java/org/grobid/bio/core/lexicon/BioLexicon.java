package org.grobid.core.lexicon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.PatternSyntaxException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.exceptions.GrobidResourceException;
import org.grobid.core.lang.Language;
import org.grobid.core.sax.CountryCodeSaxParser;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.layout.LayoutToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.grobid.core.lexicon.FastMatcher;

/**
 * Class for managing the biomedicine lexical resources.
 *
 * @author Patrice Lopez
 */
public class BioLexicon {
	private static volatile BioLexicon instance;
	
	public static BioLexicon getInstance() {
        if (instance == null) {
            //double check idiom
            // synchronized (instanceController) {
                if (instance == null)
					getNewInstance();
            // }
        }
        return instance;
    }

    /**
     * Creates a new instance.
     */
	private static synchronized void getNewInstance() {
		//LOGGER.debug("Get new instance of Lexicon");
		//GrobidProperties.getInstance();
		instance = new BioLexicon();
	}
	
	private FastMatcher bioPattern = null;
	private Set<String> bioTokens = null;
	
	private BioLexicon() {}
	
	public void initBio() {
		File file = null;
		InputStream ist = null;
        InputStreamReader isr = null;
        BufferedReader dis = null;
        try {			
			bioTokens = new HashSet<String>();
			String path = "src/main/resources/lexicon/genetics.en.txt";
			file = new File(path);
	        if (!file.exists()) {
	            throw new GrobidResourceException("Cannot add entries to bio dictionary, because file '" 
					+ file.getAbsolutePath() + "' does not exists.");
	        }
	        if (!file.canRead()) {
	            throw new GrobidResourceException("Cannot add entries to bio dictionary, because cannot read file '" 
					+ file.getAbsolutePath() + "'.");
	        }

			bioPattern = new FastMatcher(file);
            ist = getClass().getResourceAsStream(path);
			if (ist == null) 
				ist = new FileInputStream(file);
            isr = new InputStreamReader(ist, "UTF8");
            dis = new BufferedReader(isr);
			
            String l = null;
            while ((l = dis.readLine()) != null) {
            	if (l.length() == 0) continue;
                StringTokenizer st = new StringTokenizer(l, TextUtilities.fullPunctuations);
                while (st.hasMoreTokens()) {
                 	String word = st.nextToken().trim().toLowerCase();
                    if ((word.length() > 1) && !bioTokens.contains(word))
                      	bioTokens.add(word);
                }
	        } 
		}	
		catch (PatternSyntaxException e) {
            throw new 
			GrobidResourceException("Error when compiling lexicon matcher for biomedicine vocabulary.", e);
        }
		catch (FileNotFoundException e) {
//	    	e.printStackTrace();
            throw new GrobidException("An exception occured while running Grobid.", e);
        } 
		catch (IOException e) {
//	    	e.printStackTrace();
            throw new GrobidException("An exception occured while running Grobid.", e);
        } 
		finally {
            try {
                if (ist != null)
                    ist.close();
                if (isr != null)
                    isr.close();
                if (dis != null)
                    dis.close();
            } catch (Exception e) {
                throw new GrobidResourceException("Cannot close all streams.", e);
            }
        }
    }
	
	/**
     * Soft look-up in biomedicine vocabulary gazetteer
     */
    public List<OffsetPosition> tokenPositionsBioNames(String s) {
        if (bioPattern == null) {
            initBio();
        }
        List<OffsetPosition> results = bioPattern.matchToken(s);
        return results;
    }

	public List<OffsetPosition> tokenPositionsBioNames(List<LayoutToken> s) {
        if (bioPattern == null) {
            initBio();
        }
        List<OffsetPosition> results = bioPattern.matchLayoutToken(s);
        return results;
    }
    
	public boolean inBioDictionary(String s) {
		return bioTokens.contains(s);
	}
	
}