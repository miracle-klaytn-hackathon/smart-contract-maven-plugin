package io.github.miracle.hackathon;

import org.web3j.sokt.*;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class ContractCompiler {

    private static final Logger LOGGER = Logger.getLogger(ContractCompiler.class.getName());

    private final String web3jLocation;

    private final SolcRelease releaseMetadata;

    public ContractCompiler(String web3jLocation,
                            SolcRelease releaseMetadata) throws IllegalStateException {
        this.web3jLocation = web3jLocation;
        this.releaseMetadata = releaseMetadata;
    }

    public static ContractCompiler createCompiler(String web3jLocation, String compilerVersion) {
        SolcRelease releaseMetadata = getCompatibleRelease(web3jLocation, compilerVersion);
        return new ContractCompiler(web3jLocation, releaseMetadata);
    }

    public File compile(
            List<SolidityFile> inputContracts,
            Set<SolcArguments> arguments) throws IllegalStateException {
        SolcInstance contractCompiler = getCompilerInstance(inputContracts);
        String solcRootPath = getSolcRootPath(web3jLocation);
        if (contractCompiler.installed()) {
            LOGGER.info("Found a compatible Solidity Compiler (" +
                    releaseMetadata.getVersion() + ")" + " in " + solcRootPath);
        } else {
            LOGGER.info("This process will install a compatible Solidity Compiler (" +
                    releaseMetadata.getVersion() + ")" + " in " + solcRootPath);
        }
        SolcOutput compilationOutput = contractCompiler.execute(
                arguments.toArray(new SolcArguments[0]));
        if (compilationOutput.getExitCode() != 0) {
            throw new IllegalStateException(compilationOutput.getStdErr());
        }
        LOGGER.fine(compilationOutput.getStdOut());
        return getOutputPath(arguments);
    }

    private SolcInstance getCompilerInstance(List<SolidityFile> inputContracts) {
        return new SolcInstance(
                releaseMetadata,
                web3jLocation,
                false,
                inputContracts.toArray(new SolidityFile[0]));
    }

    private static SolcRelease getCompatibleRelease(String web3jLocation, String solcVersion) throws IllegalStateException {
        SolcRelease solcRelease = new VersionResolver(web3jLocation)
                .getLatestCompatibleVersion(solcVersion);
        if (solcRelease == null)
            throw new IllegalStateException("Could not determine a compatible Solc version");
        return solcRelease;
    }

    private static String getSolcRootPath(String web3jLocation) {
        return Paths.get(
                System.getProperty("user.home"),
                web3jLocation,
                "solc").toString();
    }

    public static Set<SolcArguments> parseArguments(String overrideArgs, String outputPath) {
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

    private static File getOutputPath(Set<SolcArguments> compilerArguments) {
        for (SolcArguments argument : compilerArguments) {
            if (argument == SolcArguments.OUTPUT_DIR) {
                assert argument.getParams() != null;
                return new File(argument.getParams().invoke());
            }
        }
        return null;
    }


}
