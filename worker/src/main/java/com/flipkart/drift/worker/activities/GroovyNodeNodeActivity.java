package com.flipkart.drift.worker.activities;

import com.flipkart.drift.commons.model.node.GroovyNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "groovy")
public interface GroovyNodeNodeActivity extends INodeActivity<GroovyNode> {
}
