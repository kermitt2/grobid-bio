package org.grobid.core.engines;

import org.grobid.core.GrobidModels;
import org.grobid.core.engines.AbstractParser;
import org.grobid.core.engines.tagging.GenericTaggerUtils;
import org.grobid.core.data.BiotechEntity;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.features.FeaturesVectorBiotechEntity;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.lexicon.BioLexicon;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.analyzers.GrobidAnalyzer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Biotech entities extraction.
 *
 * @author Patrice Lopez
 */
public class BiotechParser extends AbstractParser {

	private BioLexicon lexicon = BioLexicon.getInstance();

    public BiotechParser() {
        super(GrobidModels.ENTITIES_BIOTECH);
    }

    /**
     * Extract all entities from a simple piece of text.
     */
    public List<BiotechEntity> extractBiotechEntities(String text) throws Exception {
//        int nbRes = 0;
        if (text == null)
            return null;
        if (text.length() == 0)
            return null;
        List<BiotechEntity> entities;
        try {
            text = text.replace("\n", " ");
            List<LayoutToken> tokenizations = GrobidAnalyzer.getInstance().tokenizeWithLayoutToken(text);

            if (tokenizations.size() == 0)
                return null;

            List<String> textBlocks = new ArrayList<String>();

            for(LayoutToken token : tokenizations) {	
                String tok = token.getText();
                if (!tok.equals(" ")) {
                    textBlocks.add(tok + "\t<biotech>");
                }
            }
            StringBuilder ress = new StringBuilder();
            int posit = 0;
			int currentBioNameIndex = 0;
			List<OffsetPosition> positions = lexicon.tokenPositionsBioNames(tokenizations);
            for (String block : textBlocks) {
				Boolean bioToken = false;
				Boolean bioName = false;
				if (lexicon.inBioDictionary(block)) {
					bioToken = true;
				}
				// do we have a biomedicine term at position posit?				
				for(int mm = currentBioNameIndex; mm < positions.size(); mm++) {
					if ( (posit >= positions.get(mm).start) && (posit <= positions.get(mm).end) ) {
						bioName = true;
						currentBioNameIndex = mm;
						break;
					}
					else if (posit < positions.get(mm).start) {
						bioName = false;
						break;
					}
					else if (posit > positions.get(mm).end) {
						continue;
					}
				}
				
                ress.append(FeaturesVectorBiotechEntity
                        .addFeaturesBiotechEntities(block, textBlocks.size(), posit, bioToken, bioName)
                        .printVector());
                posit++;
				bioToken = false;
				bioName = false;
            }
            ress.append("\n");
			String res = label(ress.toString());
			
            entities = resultExtraction(text, res, tokenizations);
        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
        return entities;
    }

    /**
     * Extract results from a labelled header.
     */
    public List<BiotechEntity> resultExtraction(String text, 
												String result,
                                                List<LayoutToken> tokenizations) {
	
		List<BiotechEntity> entities = new ArrayList<BiotechEntity>();
        StringTokenizer stt = new StringTokenizer(result, "\n");
		String label = null; // label
        String actual = null; // token
		int offset = 0;
		int addedOffset = 0;
		int p = 0; // iterator for the tokenizations for restauring the original tokenization with
        // respect to spaces
		BiotechEntity currentEntity = null;
		while (stt.hasMoreTokens()) {
            String line = stt.nextToken();
            if (line.trim().length() == 0) {
                continue;
            }
			
			StringTokenizer st2 = new StringTokenizer(line, "\t");
            boolean start = true;
            label = null;
            actual = null;
            while (st2.hasMoreTokens()) {
                if (start) {
                    actual = st2.nextToken().trim();
                    start = false;

                    boolean strop = false;
                    while ((!strop) && (p < tokenizations.size())) {
                        String tokOriginal = tokenizations.get(p).getText();
						addedOffset += tokOriginal.length();
						if (tokOriginal.equals(actual)) {
                            strop = true;
                        }
                        p++;
                    }
                } else {
                    label = st2.nextToken().trim();
                }
            }

            if (label == null) {
				offset += addedOffset;
				addedOffset = 0;
                continue;
            }

			if (actual != null) {
				if (label.startsWith("B-")) {      
					if (currentEntity != null) {
						int localPos = currentEntity.getOffsetEnd();
						if (label.length() > 1) {  
							String subtag = label.substring(2,label.length()).toLowerCase();
							if (currentEntity.getRawName().equals(subtag) && 
							   ( (localPos == offset) ) ) {
								currentEntity.setOffsetEnd(offset+addedOffset);
								offset += addedOffset;
								addedOffset = 0;	
								continue;
							}														
							entities.add(currentEntity);
						}
					}
					if (label.length() > 1) {  
						String subtag = label.substring(2,label.length()).toLowerCase();
						currentEntity = new BiotechEntity(subtag);   
						//if (tokenizations.get(offset) == " ") {
						if ( text.charAt(offset) == ' ') {	
							currentEntity.setOffsetStart(offset+1);
						}
						else
							currentEntity.setOffsetStart(offset);
						currentEntity.setOffsetEnd(offset+addedOffset);
					}  
				}
				else if (label.startsWith("I-")) {  
					if (label.length() > 1) {  
						String subtag = label.substring(2,label.length()).toLowerCase();

					    if ( (currentEntity != null) && (currentEntity.getRawName().equals(subtag)) ) {
							currentEntity.setOffsetEnd(offset+addedOffset);		
						}
						else {
							// should not be the case, but we add the new entity, for robustness      
							if (currentEntity != null) 
								entities.add(currentEntity);
							currentEntity = new BiotechEntity(subtag);   
							currentEntity.setOffsetStart(offset);
							currentEntity.setOffsetEnd(offset+addedOffset);
						}
				   	}
				}
				
				offset += addedOffset;
				addedOffset = 0;
			}			
		}
		
		if (currentEntity != null) {
			entities.add(currentEntity);
		}
		
		return entities;
	}

}