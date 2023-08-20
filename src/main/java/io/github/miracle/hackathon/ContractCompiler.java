package io.github.miracle.hackathon;

import org.web3j.sokt.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

    public File compile(List<String> arguments,
                        List<SolidityFile> inputContracts) throws IOException, InterruptedException {
        SolcInstance contractCompiler = getCompilerInstance(inputContracts);
        String solcRootPath = getSolcRootPath(web3jLocation);
        if (contractCompiler.installed()) {
            LOGGER.info("Found a compatible Solidity Compiler (" +
                    releaseMetadata.getVersion() + ")" + " in " + solcRootPath);
        } else if (contractCompiler.install()) {
            LOGGER.info("Installed a compatible Solidity Compiler (" +
                    releaseMetadata.getVersion() + ")" + " in " + solcRootPath);
        }
        List<String> contractPaths = new ArrayList<>();
        for (SolidityFile contract: inputContracts) {
            String contractPath = contract.getSourceFile().toAbsolutePath().toString();
            contractPaths.add(contractPath);
        }
        SolcOutput compilationOutput = doCompile(
                contractCompiler.getSolcFile().getAbsolutePath(),
                arguments,
                contractPaths);
        if (compilationOutput.getExitCode() != 0) {
            throw new IllegalStateException(compilationOutput.getStdErr());
        }
        LOGGER.fine(compilationOutput.getStdOut());
        return getOutputFile(arguments);
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

    private static File getOutputFile(List<String> compilerArguments) {
        int argsLength = compilerArguments.size();
        for (int i = 0; i < argsLength; i++) {
            String argument = compilerArguments.get(i);
            if ("-o".equals(argument) || "--output-dir".equals(argument)) {
                if (i + 1 < argsLength) {
                    return new File(compilerArguments.get(i + 1));
                }
            }
        }
        throw new IllegalArgumentException("Failed to get Output File");
    }

    protected SolcOutput doCompile(
            String solcPath,
            List<String> args,
            List<String> sourcePaths) throws IOException, InterruptedException {
        List<String> commands = new ArrayList<>();
        commands.add(solcPath);
        commands.addAll(args);
        commands.addAll(sourcePaths);
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(commands);
        Process process = processBuilder.start();
        process.waitFor(30, TimeUnit.SECONDS);
        return new SolcOutput(
                process.exitValue(),
                new String(process.getInputStream().readAllBytes()),
                new String(process.getErrorStream().readAllBytes()));
    }

}
