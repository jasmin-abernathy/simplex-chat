I would like to discuss a small first step towards #5138, with #2398 as a separate, later scope: an on-demand three-person audio call inside the Android client, followed by interoperability and video. No external conferencing app would be required.

Would a small WebRTC mesh be acceptable as the first milestone, retaining the relay policy and authenticated pairwise media associations? The current CallManager and WebRTC activeCall state are one-to-one, so this needs a session model with independent peer states, explicit admission and a consistent participant list. Group membership alone should not activate media or silently admit someone into a call. I have not established whether existing member connections cover all relevant group configurations.

For larger calls, an SFU would reduce client upload, but transport encryption terminating at the SFU would not meet the privacy requirement. That path would need an established end-to-end media format, reviewed key management and removal/rekey behavior, and verified support across Android, iOS and desktop before implementation.

I noticed #7711 is merged and would build on the existing reconnection work. The current desktop specification still describes browser-based calls; I also noticed sh/desktop-native-calls, so desktop integration needs coordination with that work.

Could you advise on the intended scope, participant authentication/admission model and preferred architecture? I have prepared a feasibility note, but no group-call prototype or security claim. I would prefer to agree on these points before changing the protocol.
