package com.drift.worker.activities;

import com.drift.commons.model.node.ProcessorNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "processor")
public interface ProcessorNodeNodeActivity extends INodeActivity<ProcessorNode> {
}
