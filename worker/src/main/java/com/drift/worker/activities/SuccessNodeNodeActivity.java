package com.drift.worker.activities;

import com.drift.commons.model.node.SuccessNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "success")
public interface SuccessNodeNodeActivity extends INodeActivity<SuccessNode> {
}
