package scripts
// Test Groovy Script for Drift Worker
// This script demonstrates a simple groovy execution in the worker

import com.flipkart.drift.sdk.model.response.ViewResponse

def workflowId = context?.workflowId ?: "unknown"
def incidentId = context?.incidentId ?: "unknown"
// Return a simple result
return [
        status: "success",
        message: "Test script executed successfully",
        timestamp: System.currentTimeMillis()
]


