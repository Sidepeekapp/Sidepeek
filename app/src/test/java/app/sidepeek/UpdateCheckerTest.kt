package app.sidepeek

import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private fun cmp(a: String, b: String) = UpdateChecker.compareVersions(a, b)

    @Test fun newerVersions() {
        assertTrue(cmp("v1.0.3", "1.0.2") > 0)
        assertTrue(cmp("1.0.10", "1.0.9") > 0)
        assertTrue(cmp("v1.1", "1.0.2") > 0)
        assertTrue(cmp("2.0", "1.9.9") > 0)
    }

    @Test fun sameOrOlder() {
        assertTrue(cmp("v1.0.2", "1.0.2") == 0)
        assertTrue(cmp("1.0", "1.0.0") == 0)
        assertTrue(cmp("v1.0.1", "1.0.2") < 0)
        assertTrue(cmp("ci-9a66f11", "1.0.2") < 0)   // not a version tag: never "newer"
    }
}
