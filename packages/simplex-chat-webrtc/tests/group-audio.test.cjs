const {test} = require("node:test")
const assert = require("node:assert/strict")
const vm = require("node:vm")
const fs = require("node:fs")
const {webcrypto} = require("node:crypto")

const key = (n) => Buffer.alloc(32, n).toString("base64url")
function setup({permission, encryption = true, sendError = false} = {}) {
  const pcs = [],
    sent = [],
    states = [],
    timers = new Map()
  let requests = 0,
    timerId = 0
  const track = {
    kind: "audio",
    enabled: true,
    stopped: false,
    stop() {
      this.stopped = true
    },
  }
  class Stream {
    constructor(tracks = [track]) {
      this.tracks = tracks
    }
    getTracks() {
      return this.tracks
    }
    getAudioTracks() {
      return this.tracks.filter((t) => t.kind === "audio")
    }
    getVideoTracks() {
      return this.tracks.filter((t) => t.kind === "video")
    }
  }
  class Endpoint {}
  if (encryption)
    Endpoint.prototype.createEncodedStreams = () => ({
      readable: new ReadableStream(),
      writable: new WritableStream(),
    })
  class PC {
    constructor(config) {
      this.config = config
      this.signalingState = "stable"
      this.ice = []
      pcs.push(this)
    }
    addTransceiver(t) {
      this.track = t
      return {sender: new Endpoint(), receiver: new Endpoint()}
    }
    async createOffer() {
      return {type: "offer", sdp: "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"}
    }
    async createAnswer() {
      return {...(await this.createOffer()), type: "answer"}
    }
    async setLocalDescription(d) {
      this.localDescription = d
      this.signalingState = d.type === "offer" ? "have-local-offer" : "stable"
    }
    async setRemoteDescription(d) {
      this.remoteDescription = d
      this.signalingState = d.type === "offer" ? "have-remote-offer" : "stable"
    }
    async addIceCandidate(c) {
      this.ice.push(c)
    }
    close() {
      this.closed = true
    }
  }
  const context = vm.createContext({
    console,
    crypto: webcrypto,
    Uint8Array,
    TransformStream,
    AbortController,
    RTCRtpSender: Endpoint,
    RTCRtpReceiver: Endpoint,
    RTCPeerConnection: PC,
    MediaStream: Stream,
    navigator: {
      mediaDevices: {
        getUserMedia: async () => {
          requests++
          return permission ? await permission : new Stream()
        },
      },
    },
    setTimeout: (f) => {
      timers.set(++timerId, f)
      return timerId
    },
    clearTimeout: (id) => timers.delete(id),
  })
  vm.runInContext(fs.readFileSync("dist/call.js", "utf8"), context)
  vm.runInContext(fs.readFileSync("dist/group-audio.js", "utf8") + "\nglobalThis.Factory = GroupAudioCall", context)
  const options = {
    callId: "session-1",
    self: "alice",
    members: ["alice", "bob", "carol"],
    keys: {bob: key(1), carol: key(2)},
    iceServers: [],
    send: (s) => {
      if (sendError) throw Error("transport")
      sent.push(s)
    },
    state: (p, s) => states.push([p, s]),
    stream() {},
  }
  const call = new context.Factory(options)
  const signal = (type, extra = {}) => ({callId: "session-1", to: "alice", type, ...extra})
  return {call, options, Factory: context.Factory, pcs, sent, states, timers, track, Stream, signal, requests: () => requests}
}

test("no microphone or connection before explicit local acceptance, including remote ready", async () => {
  const s = setup()
  await s.call.receive("bob", s.signal("ready"))
  await s.call.receive("bob", s.signal("description", {description: {type: "offer", sdp: "m=audio 9"}}))
  assert.equal(s.requests(), 0)
  assert.equal(s.pcs.length, 0)
  s.call.end()
})
test("three-person mesh uses a single microphone and two encrypted relay connections", async () => {
  const s = setup()
  await s.call.accept()
  assert.equal(s.requests(), 1)
  assert.equal(s.pcs.length, 0)
  await Promise.all(["bob", "carol"].map((p) => s.call.receive(p, s.signal("ready"))))
  assert.equal(s.pcs.length, 2)
  assert.equal(s.pcs[0].track, s.pcs[1].track)
  assert(s.pcs.every((p) => p.config.encodedInsertableStreams && p.config.iceTransportPolicy === "relay"))
  s.call.setMuted(true)
  assert.equal(s.track.enabled, false)
  s.call.setMuted(false)
  assert.equal(s.track.enabled, true)
  s.call.end()
  assert(s.track.stopped)
  assert(s.pcs.every((p) => p.closed))
  assert.equal(s.timers.size, 0)
})
test("unknown senders, wrong session and wrong recipient never join", async () => {
  const s = setup()
  await s.call.accept()
  await s.call.receive("mallory", s.signal("ready"))
  await s.call.receive("bob", s.signal("ready", {callId: "old-session"}))
  await s.call.receive("bob", s.signal("ready", {to: "carol"}))
  assert.equal(s.pcs.length, 0)
  s.call.end()
})
test("remote departure preserves the other link and cannot be replayed into a rejoin", async () => {
  const s = setup()
  await s.call.accept()
  for (const p of ["bob", "carol"]) await s.call.receive(p, s.signal("ready"))
  await s.call.receive("bob", s.signal("leave"))
  assert(s.pcs[0].closed)
  assert(!s.pcs[1].closed)
  assert(!s.track.stopped)
  await s.call.receive("bob", s.signal("ready"))
  assert.equal(s.pcs.length, 2)
  await s.call.receive("carol", s.signal("leave"))
  assert(s.track.stopped)
})
test("end while microphone permission is pending stops the late stream without signalling ready", async () => {
  let resolve
  const permission = new Promise((r) => {
    resolve = r
  })
  const s = setup({permission})
  const accepted = s.call.accept()
  s.call.end()
  resolve(new s.Stream())
  await accepted
  assert(s.track.stopped)
  assert(!s.sent.some((m) => m.type === "ready"))
  assert.equal(s.pcs.length, 0)
})
test("unsupported frame encryption fails closed before requesting microphone", async () => {
  const s = setup({encryption: false})
  await assert.rejects(s.call.accept(), /encryption unavailable/)
  assert.equal(s.requests(), 0)
  assert.equal(s.pcs.length, 0)
  s.call.end()
})
test("rejects invalid roster and missing, reused or malformed keys", () => {
  const s = setup()
  for (const patch of [
    {members: ["alice", "bob"]},
    {members: ["alice", "bob", "bob"]},
    {keys: {bob: key(1)}},
    {keys: {bob: key(1), carol: key(1) + "="}},
    {keys: {bob: "bad", carol: key(2)}},
  ]) {
    assert.throws(() => new s.Factory({...s.options, ...patch}))
  }
  s.call.end()
})
test("buffers early ICE, ignores duplicate ready and description replay", async () => {
  const s = setup()
  await s.call.accept()
  await s.call.receive("bob", s.signal("ready"))
  await s.call.receive("bob", s.signal("ready"))
  assert.equal(s.pcs.length, 1)
  await s.call.receive("bob", s.signal("ice", {candidate: {candidate: "candidate:test"}}))
  assert.equal(s.pcs[0].ice.length, 0)
  const answer = s.signal("description", {description: {type: "answer", sdp: "v=0\r\nm=audio 9"}})
  await s.call.receive("bob", answer)
  assert.equal(s.pcs[0].ice.length, 1)
  await s.call.receive("bob", answer)
  assert(!s.pcs[0].closed)
  s.call.end()
})
test("video negotiation and candidate flood terminate only the offending peer", async () => {
  for (const attack of ["video", "flood"]) {
    const s = setup()
    await s.call.accept()
    for (const p of ["bob", "carol"]) await s.call.receive(p, s.signal("ready"))
    if (attack === "video") await s.call.receive("bob", s.signal("description", {description: {type: "answer", sdp: "v=0\r\nm=video 9"}}))
    else for (let i = 0; i < 65; i++) await s.call.receive("bob", s.signal("ice", {candidate: {candidate: "candidate:test"}}))
    assert(s.pcs[0].closed)
    assert(!s.pcs[1].closed)
    s.call.end()
  }
})
test("connection timeout releases resources", async () => {
  const s = setup()
  await s.call.accept()
  for (const p of ["bob", "carol"]) await s.call.receive(p, s.signal("ready"))
  for (const f of [...s.timers.values()]) f()
  assert(s.track.stopped)
  assert(s.pcs.every((p) => p.closed))
  assert.equal(s.timers.size, 0)
})
test("transport errors do not leak microphone or create peers", async () => {
  const s = setup({sendError: true})
  await s.call.accept()
  assert(s.track.stopped)
  assert.equal(s.pcs.length, 0)
})
test("admission roster and keys cannot be changed after construction", async () => {
  const s = setup()
  s.options.members.push("mallory")
  s.options.keys.bob = "bad"
  await s.call.accept()
  await s.call.receive("bob", s.signal("ready"))
  await s.call.receive("mallory", s.signal("ready"))
  assert.equal(s.pcs.length, 1)
  s.call.end()
})
test("unanswered invitations expire and release the microphone", async () => {
  const s = setup()
  await s.call.accept()
  assert.equal(s.timers.size, 2)
  for (const expire of [...s.timers.values()]) expire()
  assert(s.track.stopped)
  assert.equal(s.timers.size, 0)
})
test("observer errors cannot prevent resource cleanup", async () => {
  const s = setup()
  s.options.state = () => {
    throw Error("UI unavailable")
  }
  const call = new s.Factory(s.options)
  await call.accept()
  await call.receive("bob", s.signal("ready"))
  call.end()
  assert(s.track.stopped)
  assert(s.pcs.every((p) => p.closed))
  s.call.end()
})
