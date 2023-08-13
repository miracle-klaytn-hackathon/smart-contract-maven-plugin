package io.github.miracle.hackathon;

import org.apache.commons.io.FileDeleteStrategy;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.web3j.sokt.SolcArguments;
import org.web3j.sokt.SolidityFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static io.github.miracle.hackathon.ContractCompiler.createCompiler;
import static io.github.miracle.hackathon.ContractCompiler.parseArguments;

@Mojo(name = "compile-contract", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class SolidityCompilerMojo extends AbstractMojo {

    private static final String DEFAULT_JAVA_OUTPUT =
            "${project.build.directory}/generate-sources/java/generated";
    private static final String DEFAULT_SOLIDITY_BIN_OUTPUT =
            "${project.build.directory}/generate-resources/solidity/generated/bin";
    private static final String DEFAULT_SOLIDITY_ABI_OUTPUT =
            "${project.build.directory}/generate-resources/solidity/generated/abi";
    private static final String DEFAULT_TEMP_OUTPUT =
            "${project.build.directory}/generate-resources/solidity/generated/temp";
    private static final String DEFAULT_WEB3J_FOLDER = ".web3j";

    private static final String ABI_EXTENSION = "abi";
    private static final String BIN_EXTENSION = "bin";

    @Parameter(name = "solcVersion")
    private String solcVersion;

    @Parameter(name = "inputContracts", required = true)
    protected List<String> inputContracts;

    @Parameter(name = "javaOutput", defaultValue = DEFAULT_JAVA_OUTPUT)
    private String javaOutput;

    @Parameter(name = "binOutput", defaultValue = DEFAULT_SOLIDITY_BIN_OUTPUT)
    private String binOutput;

    @Parameter(name = "abiOutput", defaultValue = DEFAULT_SOLIDITY_ABI_OUTPUT)
    private String abiOutput;

    @Parameter(name = "generateBin", defaultValue = "true")
    protected boolean generateBin;

    @Parameter(name = "generateAbi", defaultValue = "true")
    protected boolean generateAbi;

    @Parameter(name = "overrideSolcArgs")
    protected String overrideSolcArgs;

    @Parameter(name = "web3jLocation", defaultValue = DEFAULT_WEB3J_FOLDER)
    protected String web3jLocation;

    @Parameter(name = "tempLocation", defaultValue = DEFAULT_TEMP_OUTPUT)
    protected String tempLocation;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!generateAbi && !generateBin) return;
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
                overrideSolcArgs,
                inputSolidityContracts,
                tempLocation);
        getLog().info("Compiled Contract to " + compiledFolder);
        if (overrideSolcArgs == null) moveToOutputFolder(compiledFolder);
    }

    protected File compileSolidityContract(
            String solcVersion,
            String overrideArgs,
            List<SolidityFile> inputContracts,
            String outputPath) throws MojoExecutionException {
        try {
            ContractCompiler contractCompiler = createCompiler(web3jLocation, solcVersion);
            Set<SolcArguments> compilerArguments = parseArguments(overrideArgs, outputPath);
            return contractCompiler.compile(inputContracts, compilerArguments);
        } catch (Exception exception) {
            throw new MojoExecutionException(exception);
        }
    }

    protected void moveToOutputFolder(File compiledFolder) throws MojoExecutionException {
        if (generateAbi) moveSources(
                compiledFolder,
                ABI_EXTENSION,
                new File(abiOutput));
        if (generateBin) moveSources(
                compiledFolder,
                BIN_EXTENSION,
                new File(binOutput));
        forceDelete(compiledFolder);
    }

    private void moveSources(File root, String extension, File targetFolder) {
        Collection<File> matchExtension = FileUtils.listFiles(root, new String[]{extension}, true);
        matchExtension.forEach(file -> moveSource(file, targetFolder));
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
