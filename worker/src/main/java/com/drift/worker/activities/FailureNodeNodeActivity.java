package com.drift.worker.activities;

import com.drift.commons.model.node.FailureNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "failure")
public interface FailureNodeNodeActivity extends INodeActivity<FailureNode> {
}
