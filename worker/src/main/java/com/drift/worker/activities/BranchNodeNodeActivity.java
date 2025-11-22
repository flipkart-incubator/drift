package com.drift.worker.activities;

import com.drift.commons.model.node.BranchNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "branch")
public interface BranchNodeNodeActivity extends INodeActivity<BranchNode> {
}
