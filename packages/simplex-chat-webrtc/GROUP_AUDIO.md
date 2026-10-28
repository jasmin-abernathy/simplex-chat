# Experimental three-person audio mesh

This is the first implementation milestone for the Android group-call work in
`jasmin-abernathy/simplex-chat`. It is **not an Android group-call release**.
No existing call screen, notification, SimpleX protocol message, or production
one-to-one call behaviour is changed. No official upstream PR is part of this work.

## Implemented and tested

`src/group-audio.ts` implements a fixed three-person full mesh: each participant
owns two RTCPeerConnections and shares one local microphone track between them.
It reuses `call.ts`'s AES-GCM encoded-frame transforms. There is no media server,
no central mixer, and no new cryptographic primitive.

- Explicit local `accept()` before microphone access or peer connection creation.
- An immutable roster and a distinct, fresh 256-bit key for each pair.
- Authenticated sender supplied separately from the signalling payload.
- Session/recipient checks, bounded pre-description ICE queue, deterministic
  offerer ordering, and no automatic rejoin after a terminal state.
- Audio only; unsupported frame encryption fails closed without microphone access.
- Relay policy by default. Supplying `relay: false` is an explicit client decision.
- Independent participant departure/failure; the remaining pair stays connected.
- Mute, waiting/connection deadlines, and microphone/connection cleanup, including
  ending a call while microphone permission is still pending.
- No fallback to unencrypted audio after a key mismatch or transform failure.

The answerer uses the transceiver created by the remote offer. Creating a second
local transceiver before applying the offer produced connected peers without
working audio; the real-browser test caught this and now guards against it.

## Contract for native integration

Load compiled `call.js`, then `group-audio.js`; construct `GroupAudioCall` only
from trusted client state. Construction is not acceptance. Supply callbacks for
outbound signalling, peer states and remote audio streams, then call `accept()`
only after the user approves the roster. Calling `end()` also declines an invitation.

The caller must transport `ready`, `description`, `ice`, `leave` and `failed`
messages over authenticated, encrypted SimpleX channels. `receive(sender, signal)`
requires **the transport-authenticated identity**, never an identity read out of
`signal`. Delivery should be ordered per sender and scoped to the current session.
The synchronous `send` callback must enqueue messages; asynchronous delivery
failures must be handled by the embedding client (e.g. ending the call).

The client must generate a unique, unpredictable call ID, establish fresh pairwise
keys for that session, verify roster membership, and supply the user's configured
ICE/TURN servers. Keys and SDP must not appear in logs. The browser test's in-process
signalling is a test adapter, not an implementation of these security requirements.
`connected` reports transport connectivity, not a verified human identity or an
independently audited group encryption protocol.

## Validation

From `packages/simplex-chat-webrtc`:

```sh
npm install --ignore-scripts
npm run test:group-audio
# Install a compatible Playwright in the development environment, then:
npx playwright install chromium
npm run test:group-audio:browser
```

The browser test also accepts `CHROMIUM=/path/to/chromium`. It starts an HTTP server
on loopback only, uses three isolated browser contexts, and fake microphone input.
It authenticates test senders by their context and explicitly opts into direct
loopback connectivity. No external signalling service, microphone recording or
TURN credentials are used.

Local result on 2026-10-28:

- TypeScript compilation passed.
- 14 state/lifecycle tests passed.
- Real Chromium: all three participants connected; actual decoded audio energy
  observed on all six inbound directions, with successful encoded-frame crypto.
- Mute control and departure preserving the remaining pair passed.
- An incorrect pairwise key caused the affected connection to fail closed.

These results do **not** establish Android WebView compatibility, mobile audio
routing, background behaviour, network traversal via TURN, or real-device quality.

## Remaining before an Android test APK

1. Add and test authenticated group-call signalling in the SimpleX core. Define
   invitation/acceptance, immutable roster binding, version negotiation, per-pair
   key exchange and expiry. Older clients must not interpret this as an ordinary
   one-to-one call or join automatically.
2. Wire the native call model and WebView bridge to this engine, with a visible
   roster/consent screen and participant states. Keep the existing one-to-one call
   lifecycle separate so accepting a group call cannot silently end another call.
3. Integrate Android microphone permissions, foreground call service, audio focus,
   routing, notifications, screen lock and teardown with the group lifecycle.
4. Build an APK and test on three Android devices: consent/refusal, six audio
   directions, mute, Bluetooth/speaker routing, departure, app backgrounding,
   interrupted network and TURN-only connectivity. Verify ciphertext/key failures.

Video, dynamic rosters, reconnection and cross-version interoperability are outside
this first fixed-roster audio engine. No release or usability claim should be made
until the native integration and device checks above pass.
