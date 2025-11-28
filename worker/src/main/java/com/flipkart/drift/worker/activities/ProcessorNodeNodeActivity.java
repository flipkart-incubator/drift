package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.model.node.ProcessorNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "processor")
public interface ProcessorNodeNodeActivity extends INodeActivity<ProcessorNode> {
}
