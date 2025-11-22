package com.drift.worker.activities;

import com.drift.commons.model.node.GroovyNode;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "groovy")
public interface GroovyNodeNodeActivity extends INodeActivity<GroovyNode> {
}
