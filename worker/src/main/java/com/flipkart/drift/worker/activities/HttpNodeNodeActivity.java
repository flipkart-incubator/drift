package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.model.node.HttpNode;
import com.flipkart.drift.commons.model.value.Value;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "http")
public interface HttpNodeNodeActivity extends INodeActivity<HttpNode> {
}
