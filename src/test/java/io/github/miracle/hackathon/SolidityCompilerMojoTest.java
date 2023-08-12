package io.github.miracle.hackathon;

import org.apache.commons.io.FileDeleteStrategy;
import org.apache.maven.plugin.testing.MojoRule;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class SolidityCompilerMojoTest {

    private static final String SRC_TEST_RESOURCES_POMS = "src/test/resources/poms/";

    private static final String WEB3J_ROOT = ".web3j";

    private static final String CONTRACT_OUT = "src/test/resources/contracts/out";
    public static final String CONTRACT_OVERRIDE = "src/test/resources/override";

    @Rule
    public MojoRule mojoRule = new MojoRule();

    @AfterClass
    public static void teardown() throws IOException {
        FileDeleteStrategy.FORCE.delete(new File(System.getProperty("user.home"), WEB3J_ROOT));
    }

    @After
    public void cleanUpOutput() throws IOException {
        FileDeleteStrategy.FORCE.delete(new File(CONTRACT_OUT));
        FileDeleteStrategy.FORCE.delete(new File(CONTRACT_OVERRIDE));
    }

    @Test
    public void shouldCompileContract() throws Exception {
        File file = new File(SRC_TEST_RESOURCES_POMS, "compile-contract.xml");
        assertTrue(file.exists());
        SolidityCompilerMojo mojo = (SolidityCompilerMojo) mojoRule.lookupMojo("compile-contract", file);
        assertNotNull(mojo);
        mojo.execute();
        assertContractCompiled(CONTRACT_OUT, 2);
    }

    @Test
    public void shouldOverrideDefaultCompilerArguments() throws Exception {
        File file = new File(SRC_TEST_RESOURCES_POMS, "override-compiler-arguments.xml");
        assertTrue(file.exists());
        SolidityCompilerMojo mojo = (SolidityCompilerMojo) mojoRule.lookupMojo("compile-contract", file);
        assertNotNull(mojo);
        mojo.execute();
        assertContractCompiled(CONTRACT_OVERRIDE, 1);
    }

    @Test
    public void shouldOverrideCompilerVersion() throws Exception {
        File file = new File(SRC_TEST_RESOURCES_POMS, "override-solc-version.xml");
        assertTrue(file.exists());
        SolidityCompilerMojo mojo = (SolidityCompilerMojo) mojoRule.lookupMojo("compile-contract", file);
        assertNotNull(mojo);
        mojo.execute();
        assertContractCompiled(CONTRACT_OUT, 2);
    }

    private void assertContractCompiled(String outputFolder, int expectAmount) {
        File compiledFolder = new File(outputFolder);
        File[] files = compiledFolder.listFiles((dir, name) ->
                name.endsWith(".bin") || name.endsWith(".abi"));
        assertNotNull(files);
        assertEquals(expectAmount, files.length);
    }

}