package io.github.miracle.hackathon;

import org.apache.commons.io.FileDeleteStrategy;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.web3j.sokt.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

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
        SolcInstance contractCompiler = new SolcInstance(
                getSolcRelease(solcVersion),
                web3jLocation,
                false,
                inputContracts.toArray(new SolidityFile[0]));
        SolcRelease releaseMetadata = contractCompiler.getSolcRelease();
        String solcRootPath = getSolcRootPath(web3jLocation);
        if (contractCompiler.installed()) {
            getLog().info("Found a compatible Solidity Compiler (" +
                    releaseMetadata.getVersion() + ")" + " in " + solcRootPath);
        } else {
            getLog().info("This process will install a compatible Solidity Compiler (" +
                    releaseMetadata.getVersion() + ")" + " in " + solcRootPath);
        }
        Set<SolcArguments> compilerArguments = getCompilerArguments(overrideArgs, outputPath);
        SolcOutput compilationOutput = contractCompiler.execute(compilerArguments.toArray(new SolcArguments[0]));
        if (compilationOutput.getExitCode() != 0) {
            throw new MojoExecutionException(compilationOutput.getStdErr());
        }
        getLog().debug(compilationOutput.getStdOut());
        return getCompiledPath(compilerArguments);
    }

    private SolcRelease getSolcRelease(String solcVersion) throws MojoExecutionException {
        SolcRelease solcRelease = new VersionResolver(web3jLocation)
                .getLatestCompatibleVersion(solcVersion);
        if (solcRelease == null)
            throw new MojoExecutionException("Could not determine a compatible Solc version");
        return solcRelease;
    }

    private static String getSolcRootPath(String web3jLocation) {
        return Paths.get(
                System.getProperty("user.home"),
                web3jLocation,
                "solc").toString();
    }

    private static Set<SolcArguments> getCompilerArguments(String overrideArgs, String outputPath) {
        if (overrideArgs != null) return getOverrideArguments(overrideArgs);
        return getDefaultArguments(outputPath);
    }

    private static Set<SolcArguments> getOverrideArguments(String solcArguments) {
        String[] args = solcArguments.split("\\s+");
        return parseArguments(args);
    }

    private static Set<SolcArguments> parseArguments(String[] args) {
        Set<SolcArguments> arguments = new HashSet<>();
        Map<String, SolcArguments> knownArguments = getKnownArguments();
        SolcArguments currentArgument = null;
        for (String arg : args) {
            if (arg.startsWith("--")
                    && (currentArgument = knownArguments.get(arg)) != null) {
                arguments.add(currentArgument);
            } else if (currentArgument != null) {
                currentArgument.param(() -> arg); // Add value to the argument
                currentArgument = null;
            }
        }
        return arguments;
    }

    private static Map<String, SolcArguments> getKnownArguments() {
        Map<String, SolcArguments> knownArguments = new HashMap<>();
        for (SolcArguments solcArg : SolcArguments.values()) {
            knownArguments.put(solcArg.getBaseArg(), solcArg);
        }
        return knownArguments;
    }

    private static Set<SolcArguments> getDefaultArguments(String outputPath) {
        return Set.of(
                SolcArguments.BIN,
                SolcArguments.ABI,
                SolcArguments.OVERWRITE,
                SolcArguments.OUTPUT_DIR.param(() -> outputPath));
    }

    private static File getCompiledPath(Set<SolcArguments> compilerArguments) throws MojoExecutionException {
        for (SolcArguments argument : compilerArguments) {
            if (argument == SolcArguments.OUTPUT_DIR) {
                assert argument.getParams() != null;
                return new File(argument.getParams().invoke());
            }
        }
        throw new MojoExecutionException("Could not get Compiled Path");
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
