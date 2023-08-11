package io.github.miracle.hackathon;

import org.apache.commons.io.FileDeleteStrategy;
import org.apache.maven.plugin.testing.MojoRule;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SolidityCompilerMojoTest {

    private static final String SRC_TEST_RESOURCES_POMS = "src/test/resources/poms/";

    private static final String WEB3J_ROOT = ".web3j";

    private static final String COMPILED_CONTRACT = "src/test/resources/contracts/compiled";

    @Rule
    public MojoRule mojoRule = new MojoRule();

    @AfterClass
    public static void cleanUp() throws IOException {
        FileDeleteStrategy.FORCE.delete(new File(System.getProperty("user.home"), WEB3J_ROOT));
        FileDeleteStrategy.FORCE.delete(new File(COMPILED_CONTRACT));
    }

    @Test
    public void shouldCompileContract() throws Exception {
        File file = new File(SRC_TEST_RESOURCES_POMS, "compile-contract.xml");
        assertTrue(file.exists());
        SolidityCompilerMojo mojo = (SolidityCompilerMojo) mojoRule.lookupMojo("compile-contract", file);
        assertNotNull(mojo);
        mojo.execute();
        assertContractCompiled();
    }

    private void assertContractCompiled() {
        File file = new File(COMPILED_CONTRACT);
        assertTrue(file.exists());
    }

}