package com.drift.worker.activities;

import com.drift.commons.model.node.HttpNode;
import com.drift.commons.model.value.Value;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "http")
public interface HttpNodeNodeActivity extends INodeActivity<HttpNode> {
}
