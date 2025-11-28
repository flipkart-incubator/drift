package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.model.node.SuccessNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "success")
public interface SuccessNodeNodeActivity extends INodeActivity<SuccessNode> {
}
