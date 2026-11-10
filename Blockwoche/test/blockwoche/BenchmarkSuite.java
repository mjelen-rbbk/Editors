package blockwoche;


import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        AutoIncrementBenchmarkTest.class,
        Uuidv4ByteaBenchmarkTest.class,
        Uuidv4NativeBenchmarkTest.class,
        Uuidv4VarcharBenchmarkTest.class,
        Uuidv7NativeBenchmarkTest.class
})
public class BenchmarkSuite {
    // Diese Klasse bleibt absichtlich leer.
    // JUnit nutzt nur die Annotationen oben, um die Testklassen nacheinander auszuführen.
}