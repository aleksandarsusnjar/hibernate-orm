package org.hibernate.test.bytecode.enhancement.lazy;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.bytecode.enhancement.BytecodeEnhancerRunner;
import org.junit.runner.RunWith;

@TestForIssue( jiraKey = "HHH-14500" )
@RunWith(BytecodeEnhancerRunner.class)
public class LazyToOnesControlWithProxyEnhancementTest extends AbstractLazyToOnesControlTestBase {

    public LazyToOnesControlWithProxyEnhancementTest() {
        super(true);
    }
}
