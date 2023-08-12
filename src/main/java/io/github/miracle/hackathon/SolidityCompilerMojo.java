package io.github.miracle.hackathon;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.web3j.sokt.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Mojo(name = "compile-contract", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class SolidityCompilerMojo extends AbstractMojo {

    private static final String ABI_EXTENSION = "abi";
    private static final String BIN_EXTENSION = "bin";

    @Parameter(name = "solcVersion")
    private String solcVersion;

    @Parameter(name = "inputContracts", required = true)
    protected List<String> inputContracts;

    @Parameter(name = "outputConfig", required = true)
    protected GeneratorOutputConfig outputConfig;

    @Parameter(name = "generateBin", defaultValue = "true")
    protected boolean generateBin;

    @Parameter(name = "generateAbi", defaultValue = "true")
    protected boolean generateAbi;

    @Parameter(name = "overrideSolcArgs")
    protected String overrideSolcArgs;

    @Parameter(name = "web3jLocation", defaultValue = ".web3j")
    protected String web3jLocation;

    @Parameter(name = "tempLocation", defaultValue = "temp")
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
        Path compiledFolder = compileSolidityContract(solcVersion, overrideSolcArgs, inputSolidityContracts, tempLocation);
        getLog().info("Compiled Contract to " + compiledFolder);
        if (overrideSolcArgs == null) moveToOutputFolder(compiledFolder);
    }

    protected Path compileSolidityContract(
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

    private static Path getCompiledPath(Set<SolcArguments> compilerArguments) throws MojoExecutionException {
        for (SolcArguments argument : compilerArguments) {
            if (argument == SolcArguments.OUTPUT_DIR) {
                assert argument.getParams() != null;
                return Path.of(argument.getParams().invoke());
            }
        }
        throw new MojoExecutionException("Could not get Compiled Path");
    }

    protected void moveToOutputFolder(Path compiledFolder) throws MojoExecutionException {
        if (generateAbi) moveSources(
                compiledFolder,
                ABI_EXTENSION,
                Path.of(outputConfig.getAbiOutput()));
        if (generateBin) moveSources(
                compiledFolder,
                BIN_EXTENSION,
                Path.of(outputConfig.getBinOutput()));
    }

    private void moveSources(Path root, String extension, Path targetFolder) throws MojoExecutionException {
        PathMatcher matcher = path -> path.toString().endsWith(extension);
        try (Stream<Path> found = Files.find(root, Integer.MAX_VALUE,
                (path, attr) -> attr.isRegularFile() && matcher.matches(path))) {
            found.forEach(source -> moveSource(source, targetFolder));
        } catch (IOException exception) {
            throw new MojoExecutionException(exception);
        }
    }

    private void moveSource(Path sourceFile, Path targetFolder) {
        try {
            Files.createDirectories(targetFolder);
            Files.move(sourceFile, targetFolder.resolve(sourceFile.getFileName()));
        } catch (IOException e) {
            getLog().error("Could not move "
                    + sourceFile.toString() + " Reason " + e.getMessage());
        }
    }

}
