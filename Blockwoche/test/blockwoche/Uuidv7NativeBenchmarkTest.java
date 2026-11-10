package blockwoche;


import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Uuidv7NativeBenchmarkTest extends BaseTestCase {

    @Override
    protected String getDatabaseName() {
        return "benchmark_uuidv7_native";
    }

    @Test
    public void testQuery1_SimpleSelect() {
        runBenchmark("Q1_SimpleSelect", getQuery1());
    }

    @Test
    public void testQuery2_CityStationJoin() {
        runBenchmark("Q2_CityStationJoin", getQuery2());
    }

    @Test
    public void testQuery3_TripRouteJoin() {
        runBenchmark("Q3_TripRouteJoin", getQuery3());
    }

    @Test
    public void testQuery4_TelemetryAggregation() {
        runBenchmark("Q4_TelemetryAggregation", getQuery4());
    }

    @Test
    public void testQuery5_HeavyJoinIntensive() {
        runBenchmark("Q5_HeavyJoinIntensive", getQuery5());
    }
}