package com.artezio.forms.formio;

import com.artezio.forms.formio.exceptions.FormioProcessorException;
import org.apache.commons.io.IOUtils;

import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Named
public class NodeJsProcessor {

    private final static File NODE_MODULES_DIR = new File(System.getProperty("NODE_MODULES_PATH"));

    public InputStream executeScript(String scriptName, String... args) throws IOException {
        String script = loadScript(scriptName);
        List<String> commands = new ArrayList<>(Arrays.asList(args));
        commands.add(0, script);

        Process nodeJs = runNodeJs(commands);
        checkErrors(nodeJs);

        return readFromStdout(nodeJs);
    }

    private String loadScript(String scriptName) throws IOException {
        try(InputStream scriptResource = getClass().getClassLoader().getResourceAsStream("formio-scripts/" + scriptName)) {
            return IOUtils.toString(scriptResource, StandardCharsets.UTF_8);
        }
    }

    private Process runNodeJs(List<String> commands) throws IOException {
        List<String> commandList = Stream
                .concat(Stream.of("node", "-e"), commands.stream())
                .collect(Collectors.toList());
        return new ProcessBuilder(commandList)
                .directory(NODE_MODULES_DIR)
                .start();
    }

    private void checkErrors(Process process) throws IOException {
        try (InputStream stderr = readFromStderr(process)) {
            String stderrContent = IOUtils.toString(stderr, StandardCharsets.UTF_8);
            if (!stderrContent.isEmpty()) {
                throw new FormioProcessorException(stderrContent);
            }
        }
    }

    private InputStream readFromStderr(Process process) {
        return process.getErrorStream();
    }

    private InputStream readFromStdout(Process process) {
        return process.getInputStream();
    }

}
