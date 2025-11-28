package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.model.node.InstructionNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "instruction")
public interface InstructionNodeActivity extends INodeActivity<InstructionNode> {
}
