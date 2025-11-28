package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.model.node.FailureNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "failure")
public interface FailureNodeNodeActivity extends INodeActivity<FailureNode> {
}
