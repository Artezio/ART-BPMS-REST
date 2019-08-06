package com.artezio.bpm.services.integration.cdi;

import com.artezio.bpm.services.integration.FileStorage;
import com.artezio.logging.Log;

import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import javax.inject.Named;

import static com.artezio.logging.Log.Level.CONFIG;

@Named
public class FileStorageProducer {
    @Inject
    @DefaultImplementation
    private FileStorage defaultImplementation;

    @Inject
    private Instance<FileStorage> nonDefaultImplementations;

    @Produces
    @ConcreteImplementation
    @Log(level = CONFIG, beforeExecuteMessage = "Getting file storage")
    public FileStorage getFileStorage() {
        Instance<FileStorage> nonDefaultImplementation = nonDefaultImplementations.select(new AnnotationLiteral<Default>() {
        });
        return nonDefaultImplementation.isUnsatisfied()
                ? defaultImplementation
                : nonDefaultImplementation.get();
    }
}
