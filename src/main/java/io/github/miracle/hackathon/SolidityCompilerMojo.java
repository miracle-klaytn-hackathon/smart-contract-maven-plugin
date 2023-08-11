package io.github.miracle.hackathon;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.web3j.sokt.SolcArguments;
import org.web3j.sokt.SolcInstance;
import org.web3j.sokt.SolcOutput;
import org.web3j.sokt.SolidityFile;

import java.util.Arrays;

@Mojo(name = "compile-contract", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class SolidityCompilerMojo extends AbstractMojo {

    private static final String WEB3J_ROOT = ".web3j";

    @Parameter(name = "inputContract", required = true)
    private String inputContract;

    @Parameter(name = "outputDirectory", required = true)
    private String outputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Compile Contract  " + inputContract);
        SolidityFile contract = new SolidityFile(inputContract);
        getLog().info("Attempt to find a Compatible Solidity Compiler");
        SolcInstance contractCompiler = contract.getCompilerInstance(WEB3J_ROOT, false);
        SolcOutput compilationOutput = contractCompiler.execute(buildCompilerArguments());
        if (compilationOutput.getExitCode() == 0) {
            getLog().info(compilationOutput.getStdOut());
        } else {
            throw new MojoExecutionException(compilationOutput.getStdErr());
        }
    }

    private SolcArguments[] buildCompilerArguments() {
        return Arrays.asList(
                SolcArguments.BIN,
                SolcArguments.ABI,
                SolcArguments.OVERWRITE,
                SolcArguments.OUTPUT_DIR.param(() -> outputDirectory)
        ).toArray(new SolcArguments[0]);
    }

}
