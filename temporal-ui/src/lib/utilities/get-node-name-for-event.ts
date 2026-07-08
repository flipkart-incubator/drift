import type { CommonHistoryEvent, Payload } from '$lib/types/events';

import { decodePayload } from './decode-payload';
import { isActivityTaskScheduledEvent } from './is-event-type';
import { isObject } from './is';

type DriftActivityInput = {
  workflowNode?: {
    instanceName?: string;
  };
};

/**
 * Drift-specific: every node activity is scheduled with an ActivityThinRequest
 * whose first input payload carries `workflowNode.instanceName`. Activity
 * types are static per NodeType (e.g. "httpExecute" for every HTTP node), so
 * this is the only place the specific node instance is recoverable from.
 */
export const getNodeNameForActivityScheduledEvent = (
  event: CommonHistoryEvent,
): string | undefined => {
  if (!isActivityTaskScheduledEvent(event)) return undefined;

  const payload =
    event.activityTaskScheduledEventAttributes?.input?.payloads?.[0];
  if (!payload) return undefined;

  const decoded = decodePayload(payload as unknown as Payload);
  if (!isObject(decoded)) return undefined;

  const { workflowNode } = decoded as DriftActivityInput;
  return workflowNode?.instanceName;
};
