package io.github.miracle.hackathon;

import org.apache.commons.io.FileDeleteStrategy;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.web3j.sokt.SolidityFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.github.miracle.hackathon.ContractCompiler.createCompiler;

@Mojo(name = "compile-contract", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class SolidityCompilerMojo extends AbstractMojo {

    private static final String DEFAULT_JAVA_OUTPUT =
            "${project.build.directory}/generate-sources/java/generated";
    private static final String DEFAULT_GENERATED_OUTPUT =
            "${project.build.directory}/generate-resources/solidity/generated";
    private static final String DEFAULT_WEB3J_FOLDER = ".web3j";

    private static final String ABI_EXTENSION = "abi";
    private static final String BIN_EXTENSION = "bin";

    @Parameter(name = "solcVersion")
    private String solcVersion;

    @Parameter(name = "inputContracts", required = true)
    protected List<String> inputContracts;

    @Parameter(name = "javaOutput", defaultValue = DEFAULT_JAVA_OUTPUT)
    private String javaOutput;

    @Parameter(name = "binOutput")
    private String binOutput;

    @Parameter(name = "abiOutput")
    private String abiOutput;

    @Parameter(name = "solcArguments", required = true)
    protected String solcArguments;

    @Parameter(name = "web3jLocation", defaultValue = DEFAULT_WEB3J_FOLDER)
    protected String web3jLocation;

    @Parameter(name = "generatedLocation", defaultValue = DEFAULT_GENERATED_OUTPUT)
    protected String generatedLocation;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Compile Contract(s)  " + inputContracts);
        compileContractsToOutput();
    }

    protected void compileContractsToOutput() throws MojoExecutionException {
        List<SolidityFile> inputSolidityContracts = new ArrayList<>();
        for (String path : inputContracts) {
            inputSolidityContracts.add(new SolidityFile(path));
        }
        if (solcVersion == null) {
            solcVersion = inputSolidityContracts.get(0).getVersionPragma();
        }
        File compiledFolder = compileSolidityContract(
                solcVersion,
                solcArguments,
                inputSolidityContracts,
                generatedLocation);
        getLog().info("Compiled Contract to " + compiledFolder);
        moveFromGeneratedLocation(compiledFolder);
    }

    protected File compileSolidityContract(
            String solcVersion,
            String overrideArgs,
            List<SolidityFile> inputContracts,
            String outputPath) throws MojoExecutionException {
        try {
            ContractCompiler contractCompiler = createCompiler(web3jLocation, solcVersion);
            List<String> arguments = parseArguments(overrideArgs, outputPath);
            return contractCompiler.compile(arguments, inputContracts);
        } catch (Exception exception) {
            throw new MojoExecutionException(exception.getMessage());
        }
    }

    private List<String> parseArguments(String solcArguments, String outputPath) {
        List<String> compilerArgument = new ArrayList<>();
        compilerArgument.add("--output-dir");
        compilerArgument.add(outputPath);
        String[] argumentArr = solcArguments.split("\\s+");
        for (String argument : argumentArr) {
            compilerArgument.add(argument);
            if ("-o".equals(argument) || "--output-dir".equals(argument)) {
                compilerArgument.remove("--output-dir");
                compilerArgument.remove(outputPath);
            }
        }
        return compilerArgument;
    }

    protected void moveFromGeneratedLocation(File generatedLocation) throws MojoExecutionException {
        boolean sourcesMoved = false;
        if (abiOutput != null)
            sourcesMoved = moveSources(
                generatedLocation,
                ABI_EXTENSION,
                new File(abiOutput));
        if (binOutput != null)
            sourcesMoved = moveSources(
                generatedLocation,
                BIN_EXTENSION,
                new File(binOutput));
        if (sourcesMoved) forceDelete(generatedLocation);
    }

    private boolean moveSources(File root, String extension, File targetFolder) {
        Collection<File> matchExtension = FileUtils.listFiles(root, new String[]{extension}, true);
        matchExtension.forEach(file -> moveSource(file, targetFolder));
        return true;
    }

    private void moveSource(File sourceFile, File targetFolder) {
        try {
            FileUtils.moveFileToDirectory(sourceFile, targetFolder, true);
        } catch (IOException e) {
            getLog().error("Could not move "
                    + sourceFile.toString() + " Reason " + e.getMessage());
        }
    }

    private void forceDelete(File compiledFolder) throws MojoExecutionException {
        try {
            FileDeleteStrategy.FORCE.delete(compiledFolder);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

}
