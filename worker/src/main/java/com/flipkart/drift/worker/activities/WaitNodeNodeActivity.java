package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.model.node.WaitNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "wait")
public interface WaitNodeNodeActivity extends INodeActivity<WaitNode> {
}
