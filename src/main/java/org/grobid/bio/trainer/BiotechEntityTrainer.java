package org.grobid.trainer;

import org.grobid.core.GrobidModels;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.features.FeaturesVectorBiotechEntity;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.trainer.sax.*;
import org.grobid.core.main.GrobidHomeFinder;

import org.grobid.trainer.evaluation.EvaluationUtilities;
import org.grobid.core.engines.BiotechParser;
import org.grobid.core.lexicon.BioLexicon;
import org.grobid.core.layout.LayoutToken;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.io.FileUtils;

/**
 * @author Patrice Lopez
 */
public class BiotechEntityTrainer extends AbstractTrainer {

	private BioLexicon lexicon = BioLexicon.getInstance();

    public BiotechEntityTrainer() {
        super(GrobidModels.ENTITIES_BIOTECH);
    }

	@Override
	protected final File getCorpusPath() {
		// training resources are not located under grobid-home for the additional standalone models
		return GrobidProperties.getCorpusPath(new File(new File("resources").getAbsolutePath()), model);
	}

	@Override
	public int createCRFPPData(final File corpusDir, final File trainingOutputPath) {
		return createCRFPPData(corpusDir, trainingOutputPath, null, 1.0);
	}

    /**
     * Add the selected features to the model training for bio entities
     */
	@Override
    public int createCRFPPData(final File corpusDir, 
							final File trainingOutputPath, 
							final File evalOutputPath, 
							double splitRatio) {
        int totalExamples = 0;
        try {
			if (corpusDir == null) {
				throw new IllegalStateException("Training folder does not seem valid");
			}
			else
				System.out.println("corpusDir: " + corpusDir.getPath());
			if (trainingOutputPath != null)
           	 	System.out.println("trainingOutputPath: " + trainingOutputPath.getPath());
			if (evalOutputPath != null)
				System.out.println("evalOutputPath: " + evalOutputPath.getPath());

            // then we convert the tei files into the usual CRF label format
            // we process all tei files in the output directory
            File[] refFiles = corpusDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith("1.iob2") && name.startsWith("Genia");
                }
            });

            if (refFiles == null) {
                return 0;
            }

            System.out.println(refFiles.length + " files");

            // the file for writing the training data
            Writer writer2 = new OutputStreamWriter(new FileOutputStream(trainingOutputPath), "UTF8");

            String name;
            int n = 0;
            for (; n < refFiles.length; n++) {
                File thefile = refFiles[n];				
				
				// to store biomedicine term positions
                List<List<OffsetPosition>> bioNamesTokenPositions = new ArrayList<List<OffsetPosition>>();
				
				// we simply need to retokenize the token following Grobid approach										
				List<String> labeled = new ArrayList<String>();
			  	BufferedReader br = new BufferedReader(new InputStreamReader(
					new DataInputStream(new FileInputStream(thefile))));
			  	String line;
				List<LayoutToken> input = new ArrayList<LayoutToken>();
			  	while ((line = br.readLine()) != null)   {
					if (line.trim().length() == 0) {
						labeled.add("@newline");
						bioNamesTokenPositions.add(lexicon.tokenPositionsBioNames(input));
						input = new ArrayList<LayoutToken>();
						continue;
					}
					int ind = line.indexOf("\t");
					if (ind == -1) {
						continue;
					}
					// we take the standard Grobid tokenizer
					StringTokenizer st2 = new StringTokenizer(line.substring(0, ind), 
						TextUtilities.fullPunctuations, true);
					while(st2.hasMoreTokens()) {
						String tok = st2.nextToken();
						if (tok.trim().length() == 0)
							continue;
						String label = line.substring(ind+1, line.length());
						if (label.equals("O")) {
							label = "<other>";
						}
						else if (label.startsWith("I-")) {
							label = "I-<" + label.substring(2,label.length()) + ">";
						}
						else if (label.startsWith("B-")) {
							label = "B-<" + label.substring(2,label.length()) + ">";
						}
						labeled.add(tok + "\t" + label);
						input.add(new LayoutToken(tok));
					}										
				}
				//labeled.add("@newline");				                 				
				bioNamesTokenPositions.add(lexicon.tokenPositionsBioNames(input));
				 
                addFeatures(labeled, writer2, bioNamesTokenPositions);
                writer2.write("\n");
				br.close();                
            }

            writer2.close();
        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
        return totalExamples;
    }


    @SuppressWarnings({"UnusedParameters"})
    public void addFeatures(List<String> texts,
                            Writer writer,
                            List<List<OffsetPosition>> bioNamesTokenPositions) {
        int totalLine = texts.size();
        int posit = 0;
		int sentence = 0;
		int currentBioNameIndex = 0;
		List<OffsetPosition> localPositions = bioNamesTokenPositions.get(sentence);
        boolean isBioToken = false;
        boolean isBioNameToken = false;
        try {
            for (String line : texts) {
				if (line.trim().equals("@newline")) {
					writer.write("\n");
	                writer.flush();	
					sentence++;				
					localPositions = bioNamesTokenPositions.get(sentence);
				}
	
				int ind = line.indexOf("\t");
				if (ind == -1) 
				 	ind = line.indexOf(" ");
				if (ind != -1) {
					if (lexicon.inBioDictionary(line.substring(0,ind))) {
						isBioToken = true;
					}					
				}
				
				// do we have a biomedicine term at position posit?				
				for(int mm = currentBioNameIndex; mm < localPositions.size(); mm++) {
					if ( (posit >= localPositions.get(mm).start) && (posit <= localPositions.get(mm).end) ) {
						isBioNameToken = true;
						currentBioNameIndex = mm;
						break;
					}
					else if (posit < localPositions.get(mm).start) {
						isBioNameToken = false;
						break;
					}
					else if (posit > localPositions.get(mm).end) {
						continue;
					}
				}
				
                FeaturesVectorBiotechEntity featuresVector =
                        FeaturesVectorBiotechEntity.addFeaturesBiotechEntities(line,
                                totalLine,
                                posit,
                                isBioToken,
                                isBioNameToken);
                if (featuresVector.label == null)
                    continue;
                writer.write(featuresVector.printVector());
                writer.flush();
                posit++;
				isBioToken = false;
				isBioNameToken = false;
            }
        } catch (Exception e) {
            throw new GrobidException("An exception occured while running Grobid.", e);
        }
    }

	/**
	 *  Standard evaluation via the the usual Grobid evaluation framework.
	 */
	public String evaluate() {
		File evalDataF = GrobidProperties.getInstance().getEvalCorpusPath(
			new File(new File("resources").getAbsolutePath()), model);
		
		File tmpEvalPath = getTempEvaluationDataPath();		
		createCRFPPData(evalDataF, tmpEvalPath);
			
        return EvaluationUtilities.evaluateStandard(tmpEvalPath.getAbsolutePath(), getTagger());
	}

	/**
	 *  Evaluation based on the Genia Entity Recognition task of the International Joint 
	 *  Workshop on Natural Language Processing in Biomedicine and its Applications in 2004.
	 *  This is the event corresponding to the JNLPBA corpus. 
	 */
	public String evaluate_jnlpba() {
		StringBuffer report = new StringBuffer();
		try {
			BiotechParser parser = new BiotechParser();
			
			File evalDataF = GrobidProperties.getInstance().getEvalCorpusPath(
				new File(new File("resources").getAbsolutePath()), model);
		
			File tmpEvalPath = getTempEvaluationDataPath();		
			createCRFPPData(evalDataF, tmpEvalPath);			
		
			BufferedReader bufReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(tmpEvalPath), "UTF-8"));

			// the parser needs to be applied on a sentence level as it has been trained as such
            String line;
            StringBuffer ress = new StringBuffer();
			StringBuffer theResultBuffer = new StringBuffer();
            while ((line = bufReader.readLine()) != null) {
                ress.append(line+"\n");
				if ( (line.trim().length() == 0) && (ress.length() > 10) ) {
					theResultBuffer.append(parser.label(ress.toString()) + "\n");	
					ress = new StringBuffer();
				}
            }

			String theResult = theResultBuffer.toString();
			File rawGeniaInput = new File(evalDataF.getAbsolutePath() + "/NLPBA-Genia4ERtest/Genia4EReval1.raw");
			
			bufReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(rawGeniaInput), "UTF-8"));

            ArrayList<String> blocks = new ArrayList<String>();
            while ((line = bufReader.readLine()) != null) {
                blocks.add(line);
            }
            bufReader.close();

			StringBuffer finalRes = new StringBuffer();

            StringTokenizer stt = new StringTokenizer(theResult, "\n");

			// the Genia task result tokenisation is follow the unlabelled raw data - 
			// Grobid segments more than what provided, so we have to adjust Grobid
			// output
			int n = 0;
			boolean previousB = false;
			boolean previousI = false;
			String previousLabel = "O";
			while(n < blocks.size()) {
				boolean consumed = false;
				String line2 = blocks.get(n).trim();
				
				if (line2.length() == 0) {
					finalRes.append("\n");
					n++;
					previousB = false;
					previousI = false;
					continue;			
				}
				else {
					finalRes.append(line2);
				}
				int ind = 0;
				
				while(!consumed) {					
          	  		if (stt.hasMoreTokens()) {									
		                line = stt.nextToken();
					}
					else
						break;									
			
	                if (line.length() == 0) {
						break;
					}
					StringTokenizer st = new StringTokenizer(line, "\t");
	                String currentToken = null;
	                String previousToken = null;
					String token = null;
					boolean first = true;
	                while (st.hasMoreTokens()) {
	                    currentToken = st.nextToken();
						if (first) {
							token = currentToken;
							first = false;
						}

	                    if (currentToken != null) {
	                        if (currentToken.startsWith("I-") || currentToken.startsWith("E-")
								|| currentToken.startsWith("B-")) {
	                            currentToken = currentToken.substring(2, currentToken.length());
	                        }
	                    }
	                    if (st.hasMoreTokens()) {
	                        previousToken = currentToken;
	                    }
	                }
					
	                if ((token == null) || (previousToken == null) || (currentToken == null)) {						
	                    continue;
	                }
					// token: token labelled
	                // previousToken: expected label
	                // currentToken: obtained label

					ind = line2.indexOf(token, ind);
					if (ind == -1) {
						System.out.println(n);	
						System.out.println(line2);
						System.out.println(token);
						throw new GrobidException("Warning: tokenisation adjustment failure!");
					}
				 	else if (ind == 0) {
						String label = null;
						if (currentToken.equals("<other>")) {
							label = "O";
							finalRes.append("\t"+label+"\n");
							previousB = false;
							previousI = false;
						}
						else if (previousB) {
							label = currentToken.replace("<","").replace(">","");
							if (label.equals(previousLabel)) {
								finalRes.append("\t"+"I-"+label+"\n");
								previousB = false;
								previousI = true;
							}
							else {
								finalRes.append("\t"+"B-"+label+"\n");
								previousB = true;
								previousI = false;
							}														
						}
						else if (previousI) {
							label = currentToken.replace("<","").replace(">","");
							if (label.equals(previousLabel)) {								
								finalRes.append("\t"+"I-"+label+"\n");
								previousB = false;
								previousI = true;
							}
							else {
								finalRes.append("\t"+"B-"+label+"\n");
								previousB = true;
								previousI = false;
							}
						}
						else {
							label = currentToken.replace("<","").replace(">","");
							finalRes.append("\t"+"B-"+label+"\n");
							previousB = true;
							previousI = false;
						}
						previousLabel = label;
					}					
					if (ind + token.length() == line2.length())
						consumed = true;
					ind = ind+1;	
				}
				n++;
			}

			FileUtils.writeStringToFile(tmpEvalPath, finalRes.toString());

			// we call the evaluation script as external process
			try {
				String cmd = evalDataF.getAbsolutePath() + "/NLPBA-Genia4ERtest/evalIOB2.pl";
				ProcessBuilder builder = new ProcessBuilder(cmd, 
					evalDataF.getAbsolutePath()+"/Genia4EReval1.iob2", 
					tmpEvalPath.getAbsolutePath());
					
				System.out.println(cmd + " " + evalDataF.getAbsolutePath()+"/Genia4EReval1.iob2" + " " 
					+ tmpEvalPath.getAbsolutePath());	
					 
				builder.redirectErrorStream(true);
				Process process = builder.start();
				final BufferedReader reader = 
					new BufferedReader(new InputStreamReader(process.getInputStream()));
				line = null;
				while ((line = reader.readLine()) != null) {
					report.append(line + "\n");
				}
			}
			catch (IOException e) {
				throw new GrobidException("Failed to run the external JNLPBA evaluation script.", e);		
			}

			return report.toString();
		}
		catch (Exception e) {
            throw new GrobidException("An exception occurred while evaluating Grobid.", e);
        }		 
	}

    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
    	try {
            String pGrobidHome = "../grobid-home";

            GrobidHomeFinder grobidHomeFinder = new GrobidHomeFinder(Arrays.asList(pGrobidHome));
            GrobidProperties.getInstance(grobidHomeFinder);
    
            System.out.println(">>>>>>>> GROBID_HOME="+GrobidProperties.get_GROBID_HOME_PATH());
        } catch (final Exception exp) {
            System.err.println("GROBID astro initialisation failed: " + exp);
            exp.printStackTrace();
        }
		try {
	        Trainer trainer = new BiotechEntityTrainer();
	        //AbstractTrainer.runTraining(trainer);
	        //AbstractTrainer.runEvaluation(trainer);
	
			System.out.println( ((BiotechEntityTrainer) trainer).evaluate_jnlpba() );
		}
		catch (Exception e) {
		    e.printStackTrace();
		}
    }
}