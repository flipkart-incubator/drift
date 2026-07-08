import type { CommonHistoryEvent, Payload } from '$lib/types/events';

import { decodePayload } from './decode-payload';
import {
  isActivityTaskScheduledEvent,
  isLocalActivityMarkerEvent,
  isMarkerRecordedEvent,
} from './is-event-type';
import { isObject } from './is';

type DriftActivityInput = {
  workflowNode?: {
    instanceName?: string;
  };
};

/**
 * Drift-specific: every node activity (local or remote) is invoked with an
 * ActivityThinRequest whose first input payload carries
 * `workflowNode.instanceName`. Activity types are static per NodeType (e.g.
 * "httpExecute" for every HTTP node), so this is the only place the specific
 * node instance is recoverable from.
 */
// `payload` comes in as the raw proto IPayload shape (metadata/data typed as
// Uint8Array), but at runtime, over this app's REST/JSON gateway, it's
// actually base64 strings — matching the UI's own Payload type. Same
// pre-existing mismatch `decodePayload`'s other callers already cast through.
const getNodeNameFromPayload = (payload: unknown): string | undefined => {
  if (!payload) return undefined;

  const decoded = decodePayload(payload as Payload);
  if (!isObject(decoded)) return undefined;

  const { workflowNode } = decoded as DriftActivityInput;
  return workflowNode?.instanceName;
};

// Remote activities (HTTP, WAIT, PROCESSOR, DELEGATE, CHILD, ...): the
// input lives on the ActivityTaskScheduled event itself.
export const getNodeNameForActivityScheduledEvent = (
  event: CommonHistoryEvent,
): string | undefined => {
  if (!isActivityTaskScheduledEvent(event)) return undefined;

  return getNodeNameFromPayload(
    event.activityTaskScheduledEventAttributes?.input?.payloads?.[0],
  );
};

// Local activities (BRANCH, SUCCESS, FAILURE, GROOVY, INSTRUCTION): these
// never produce an ActivityTaskScheduled event — they run inline and are
// recorded as a single MarkerRecorded event, with the same input payload
// nested under `details.input` instead.
export const getNodeNameForLocalActivityMarkerEvent = (
  event: CommonHistoryEvent,
): string | undefined => {
  // isLocalActivityMarkerEvent isn't declared as a type predicate, so
  // isMarkerRecordedEvent (which is) is what actually narrows `event` below.
  if (!isLocalActivityMarkerEvent(event) || !isMarkerRecordedEvent(event)) {
    return undefined;
  }

  return getNodeNameFromPayload(
    event.markerRecordedEventAttributes?.details?.input?.payloads?.[0],
  );
};
