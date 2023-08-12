package io.github.miracle.hackathon;

import org.apache.maven.plugins.annotations.Parameter;

public class GeneratorOutputConfig {

    public static final String DEFAULT_JAVA_OUTPUT = "src/main/java/generated";
    public static final String DEFAULT_SOLIDITY_BIN_OUTPUT = "src/main/resources/contracts/generated/bin";
    public static final String DEFAULT_SOLIDITY_ABI_OUTPUT = "src/main/resources/contracts/generated/abi";

    @Parameter(name = "javaOutput", defaultValue = DEFAULT_JAVA_OUTPUT)
    private String javaOutput;

    @Parameter(name = "binOutput", defaultValue = DEFAULT_SOLIDITY_BIN_OUTPUT)
    private String binOutput;

    @Parameter(name = "abiOutput", defaultValue = DEFAULT_SOLIDITY_ABI_OUTPUT)
    private String abiOutput;

    public String getJavaOutput() {
        return javaOutput;
    }

    public String getBinOutput() {
        return binOutput;
    }

    public String getAbiOutput() {
        return abiOutput;
    }

}
