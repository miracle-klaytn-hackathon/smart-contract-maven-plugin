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
import java.nio.file.Paths;
import java.util.*;

@Mojo(name = "compile-contract", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class SolidityCompilerMojo extends AbstractMojo {

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

    @Parameter(name = "solcArguments")
    protected Map<String, String> solcArguments = new HashMap<>();

    @Parameter(name = "web3jLocation", defaultValue = ".web3j")
    protected String web3jLocation;

    @Parameter(name = "tempLocation", defaultValue = "temp")
    protected String tempLocation;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!generateAbi && !generateBin) return;
        List<SolidityFile> solidityFiles = new ArrayList<>();
        for (String path : inputContracts) {
            solidityFiles.add(new SolidityFile(path));
        }
        if (solcVersion == null) {
            solcVersion = solidityFiles.get(0).getVersionPragma();
        }
        compileSolidityContract(solcVersion, solidityFiles, tempLocation);
        moveToOutputFolder();
    }

    private void moveToOutputFolder() throws MojoExecutionException {
        try {
            if (generateAbi) {
                Files.move(Path.of(tempLocation), Path.of(outputConfig.getAbiOutput()));
            }
            if (generateBin) {
                Files.move(Path.of(tempLocation), Path.of(outputConfig.getBinOutput()));
            }
        } catch (IOException exception) {
            throw new MojoExecutionException(exception);
        }
    }

    protected void compileSolidityContract(
            String solcVersion,
            List<SolidityFile> inputContracts,
            String outputPath) throws MojoExecutionException {
        getLog().info("Compile Contract(s)  " + inputContracts);
        SolcInstance contractCompiler = new SolcInstance(
                getSolcRelease(solcVersion),
                web3jLocation,
                false,
                inputContracts.toArray(new SolidityFile[0]));
        SolcRelease releaseMetadata = contractCompiler.getSolcRelease();
        if (contractCompiler.installed()) {
            getLog().info("Found a compatible Solidity Compiler (" +
                    releaseMetadata.getVersion() + ")");
        } else {
            getLog().info("This process will install a compatible Solidity Compiler (" +
                    releaseMetadata.getVersion() + ")" + " in " + getSolcRootPath());
        }
        Set<SolcArguments> compilerArguments = getCompilerArguments(outputPath);
        SolcOutput compilationOutput = contractCompiler.execute(compilerArguments.toArray(new SolcArguments[0]));
        if (compilationOutput.getExitCode() == 0) {
            getLog().info(compilationOutput.getStdOut());
        } else {
            throw new MojoExecutionException(compilationOutput.getStdErr());
        }
    }

    private SolcRelease getSolcRelease(String solcVersion) throws MojoExecutionException {
        SolcRelease solcRelease = new VersionResolver(web3jLocation)
                .getLatestCompatibleVersion(solcVersion);
        if (solcRelease == null)
            throw new MojoExecutionException("Could not determine a compatible Solc version ");
        return solcRelease;
    }

    private Set<SolcArguments> getCompilerArguments(String outputPath) {
        Set<SolcArguments> compilerArguments = getCompilerArguments(solcArguments);
        List<SolcArguments> requiredArguments = List.of(
                SolcArguments.BIN,
                SolcArguments.ABI,
                SolcArguments.OVERWRITE,
                SolcArguments.OUTPUT_DIR.param(() -> outputPath));
        for (SolcArguments argument : requiredArguments) {
            if (!compilerArguments.add(argument)) {
                getLog().debug(argument.getBaseArg() + "is override by compiler argument");
            }
        }
        return compilerArguments;
    }

    private String getSolcRootPath() {
        return Paths.get(
                System.getProperty("user.home"),
                web3jLocation,
                "solc").toString();
    }

    private static Set<SolcArguments> getCompilerArguments(Map<String, String> solcArguments) {
        Set<SolcArguments> compilerArguments = new HashSet<>();
        Map<String, SolcArguments> simplifiedBaseArgs = getSimplifiedBaseArgs();
        solcArguments.forEach((argName, argValue) -> {
            if (simplifiedBaseArgs.containsKey(argName)) {
                SolcArguments argument = simplifiedBaseArgs.get(argName);
                compilerArguments.add(argument.param(() -> argValue));
            }
        });
        return compilerArguments;
    }

    private static Map<String, SolcArguments> getSimplifiedBaseArgs() {
        Map<String, SolcArguments> simplifiedBaseArgs = new HashMap<>();
        for (SolcArguments arg : SolcArguments.values()) {
            String simplified = simplifyBaseArg(arg.getBaseArg());
            simplifiedBaseArgs.put(simplified, arg);
        }
        return simplifiedBaseArgs;
    }

    private static String simplifyBaseArg(String baseArg) {
        // Remove "--" from base arg
        return baseArg.substring(2);
    }

}
