package org.grobid.core.test;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.grobid.core.data.BiotechEntity;
import org.grobid.core.engines.BiotechParser;
import org.grobid.core.exceptions.GrobidException;
import org.junit.Ignore;
import org.junit.Test;

/**
 *  @author Patrice Lopez
 */
//@Ignore
public class TestBiotechEntityParser extends EngineTest {

	public File getResourceDir(String resourceDir) {
		File file = new File(resourceDir);
		if (!file.exists()) {
			if (!file.mkdirs()) {
				throw new GrobidException("Cannot start test, because test resource folder is not correctly set.");
			}
		}
		return(file);
	}
		
	@Test
	public void testBiotechEntityParser() throws Exception {
		File textFile = 
			new File(this.getResourceDir("./src/test/resources/").getAbsoluteFile()+"/PubMedAbstract.txt");
		if (!textFile.exists()) {
			throw new GrobidException("Cannot start test, because test resource folder is not correctly set.");
		}
		String text = FileUtils.readFileToString(textFile);	
		
		BiotechParser parser = new BiotechParser();
		
		List<BiotechEntity> entities = parser.extractBiotechEntities(text); 
		if (entities != null) {
			for(BiotechEntity entity : entities) {
				System.out.println(entity.toString());
			}
		}
		else {
			System.out.println("No biotech entity found.");
		}
	}
	
}