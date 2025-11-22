package com.drift.worker.activities;

import com.drift.commons.model.node.InstructionNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "instruction")
public interface InstructionNodeActivity extends INodeActivity<InstructionNode> {
}
