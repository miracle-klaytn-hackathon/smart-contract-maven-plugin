package io.github.miracle.hackathon;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.web3j.sokt.*;

import java.nio.file.Paths;
import java.util.Arrays;

@Mojo(name = "compile-contract", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class SolidityCompilerMojo extends AbstractMojo {

    @Parameter(name = "inputContract", required = true)
    private String inputContract;

    @Parameter(name = "outputFolder", required = true)
    private String outputFolder;

    @Parameter(name = "web3jLocation", defaultValue = ".web3j")
    private String web3jLocation;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Compile Contract  " + inputContract);
        SolidityFile contract = new SolidityFile(inputContract);
        SolcInstance contractCompiler = contract.getCompilerInstance(web3jLocation, false);
        SolcRelease releaseMetadata = contractCompiler.getSolcRelease();
        if (contractCompiler.installed()) {
            getLog().info("Found a compatible Solidity Compiler (" +
                    releaseMetadata.getVersion() + ")");
        } else {
            getLog().info("This process will install a compatible Solidity Compiler (" +
                    releaseMetadata.getVersion() + ")" + " in " + getSolcRootPath());
        }
        SolcOutput compilationOutput = contractCompiler.execute(buildCompilerArguments());
        if (compilationOutput.getExitCode() == 0) {
            getLog().info(compilationOutput.getStdOut());
        } else {
            throw new MojoExecutionException(compilationOutput.getStdErr());
        }
    }

    private String getSolcRootPath() {
        return Paths.get(
                System.getProperty("user.home"),
                web3jLocation,
                "solc").toString();
    }

    private SolcArguments[] buildCompilerArguments() {
        return Arrays.asList(
                SolcArguments.BIN,
                SolcArguments.ABI,
                SolcArguments.OVERWRITE,
                SolcArguments.OUTPUT_DIR.param(() -> outputFolder)
        ).toArray(new SolcArguments[0]);
    }

}
