package com.flipkart.drift.commons.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.drift.commons.model.node.Workflow;
import com.flipkart.drift.commons.utils.ObjectMapperUtil;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelWorkflowExampleDslTest {

    @Test
    void exampleParallelVendorNotifyWorkflow_parsesAndValidates() throws Exception {
        ObjectMapper mapper = ObjectMapperUtil.INSTANCE.getMapper();
        try (InputStream in = getClass().getResourceAsStream("/examples/parallel-vendor-notify-workflow.json")) {
            assert in != null : "test resource examples/parallel-vendor-notify-workflow.json missing";
            Workflow workflow = mapper.readValue(in, Workflow.class);
            assertEquals("parallel-vendor-notify-workflow", workflow.getId());
            assertTrue(workflow.isParallel());
            assertDoesNotThrow(() -> ParallelWorkflowValidator.validate(workflow));
        }
    }
}
