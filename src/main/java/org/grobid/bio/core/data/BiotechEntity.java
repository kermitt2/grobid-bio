package org.grobid.core.data;

import org.grobid.core.utilities.OffsetPosition;

/**
 * Class for managing biotech entities.
 *
 * @author Patrice Lopez
 */
public class BiotechEntity {
    // attribute
    String rawName = null;
	int conceptReference = -1;
	
	String originalScheme = null;
	String originalConcept = null;

    OffsetPosition offsets = null;

    public BiotechEntity() {
        offsets = new OffsetPosition();
    }

    public BiotechEntity(String raw) {
        offsets = new OffsetPosition();
        this.rawName = raw;
    }

    public String getRawName() {
        return rawName;
    }

	public void setRawName(String raw) {
        this.rawName = raw;
    }
	
	public void setOffsetStart(int start) {
        offsets.start = start;
    }

    public int getOffsetStart() {
        return offsets.start;
    }

    public void setOffsetEnd(int end) {
        offsets.end = end;
    }

    public int getOffsetEnd() {
        return offsets.end;
    }

	public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(rawName + "\t" + offsets.toString());
        return buffer.toString();
    }

}