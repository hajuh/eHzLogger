package de.diesner.ehzlogger.source;

import org.openmuc.jsml.structures.SML_File;

import java.io.IOException;

public interface SMLSource {

    public void close();
    public SML_File read();

}
