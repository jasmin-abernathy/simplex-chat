/** Experimental three-person audio mesh. No signalling server or automatic admission.
 * The embedding client must authenticate signals and provision fresh pairwise keys
 * through SimpleX before constructing this object. Never take identity from a payload.
 * Load after call.js to reuse its existing encoded-frame encryption implementation.
 */
type GroupAudioPeerState = "waiting" | "connecting" | "connected" | "disconnected" | "left" | "failed"
type GroupAudioSignal = {
  callId: string
  to: string
} & (
  | {type: "ready" | "leave" | "failed"}
  | {type: "description"; description: RTCSessionDescriptionInit}
  | {type: "ice"; candidate: RTCIceCandidateInit}
)
interface GroupAudioOptions {
  callId: string
  self: string
  // Immutable, already approved roster, including self. This prototype permits 3 only.
  members: readonly string[]
  // A distinct, fresh 256-bit base64url key for each pair, supplied by the client.
  keys: Readonly<Record<string, string>>
  iceServers: RTCIceServer[]
  // Default true. Direct connectivity requires an explicit privacy decision.
  relay?: boolean
  send: (signal: GroupAudioSignal) => void
  state: (peer: string, state: GroupAudioPeerState) => void
  stream: (peer: string, stream: MediaStream) => void
}
interface GroupAudioPeer {
  id: string
  key: string
  ready: boolean
  state: GroupAudioPeerState
  pc?: RTCPeerConnection
  pendingIce: RTCIceCandidateInit[]
  timer?: ReturnType<typeof setTimeout>
  cryptoKey?: CryptoKey
  abort?: AbortController
}

class GroupAudioCall {
  private readonly peers = new Map<string, GroupAudioPeer>()
  private readonly options: GroupAudioOptions
  private readonly transformed = new WeakSet<object>()
  private local?: MediaStream
  private accepting = false
  private accepted = false
  private closed = false
  private muted = false
  private queue: Promise<void> = Promise.resolve()

  constructor(options: GroupAudioOptions) {
    if (
      !options.callId ||
      !options.self ||
      options.members.length !== 3 ||
      new Set(options.members).size !== 3 ||
      !options.members.includes(options.self) ||
      options.members.some((id) => !id || id.length > 128)
    )
      throw Error("Invalid three-person roster")
    const keys = new Set<string>()
    for (const id of options.members) {
      if (id === options.self) continue
      const key = options.keys[id]
      // Accept only canonical 32-byte base64url keys (with optional padding).
      if (!key || !/^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]=?$/.test(key)) throw Error("Invalid pairwise key")
      const canonical = key.replace(/=$/, "")
      if (keys.has(canonical)) throw Error("Pairwise keys must be distinct")
      keys.add(canonical)
      this.peers.set(id, {id, key: canonical + "=", ready: false, state: "waiting", pendingIce: []})
    }
    // Copy trusted configuration so mutation by the caller cannot change admission.
    this.options = {
      ...options,
      members: [...options.members],
      keys: {...options.keys},
      iceServers: options.iceServers.map((s) => ({...s, urls: Array.isArray(s.urls) ? [...s.urls] : s.urls})),
    }
  }

  /** Must only be called after the local user explicitly accepts this roster. */
  async accept(): Promise<void> {
    if (this.closed) throw Error("Call ended")
    if (this.accepted || this.accepting) throw Error("Already accepted")
    if (!("createEncodedStreams" in RTCRtpSender.prototype) || !("createEncodedStreams" in RTCRtpReceiver.prototype))
      throw Error("Encoded audio encryption unavailable")
    this.accepting = true
    try {
      const stream = await navigator.mediaDevices.getUserMedia({audio: true, video: false})
      if (this.closed) {
        stream.getTracks().forEach((t) => t.stop())
        return
      }
      if (stream.getAudioTracks().length !== 1 || stream.getVideoTracks().length) {
        stream.getTracks().forEach((t) => t.stop())
        throw Error("Expected one microphone track")
      }
      this.local = stream
      stream.getAudioTracks()[0].enabled = !this.muted
      stream.getAudioTracks()[0].onended = () => this.end()
      this.accepted = true
      for (const peer of this.peers.values()) {
        if (this.terminal(peer)) continue
        this.timeout(peer, 30000)
        this.send(peer, {callId: this.options.callId, to: peer.id, type: "ready"})
      }
      await this.enqueue(async () => {
        for (const peer of this.peers.values()) {
          if (peer.ready && !this.terminal(peer)) await this.connect(peer)
        }
      })
    } catch (e) {
      this.end()
      throw e
    } finally {
      this.accepting = false
    }
  }

  /** authenticatedSender MUST come from the trusted SimpleX transport, not JSON. */
  receive(authenticatedSender: string, signal: GroupAudioSignal): Promise<void> {
    return this.enqueue(async () => {
      const peer = this.peers.get(authenticatedSender)
      if (this.closed || !peer || signal.callId !== this.options.callId || signal.to !== this.options.self) return
      if (this.terminal(peer)) return
      if (signal.type === "leave" || signal.type === "failed") {
        this.closePeer(peer, signal.type === "leave" ? "left" : "failed", false)
        return
      }
      if (signal.type === "ready") {
        peer.ready = true
        if (this.accepted) await this.connect(peer)
        return
      }
      // Neither a description nor an ICE candidate can admit a participant.
      if (!this.accepted || !peer.ready || !peer.pc) return
      const pc = peer.pc
      try {
        if (signal.type === "description") {
          const d = signal.description
          const offerer = this.options.self < peer.id
          if (
            d.type !== (offerer ? "answer" : "offer") ||
            pc.signalingState !== (offerer ? "have-local-offer" : "stable") ||
            pc.remoteDescription
          )
            return
          if (!d.sdp || d.sdp.length > 65536 || (d.sdp.match(/^m=/gm) || []).length !== 1 || /(?:^|\r?\n)m=(?!audio )/.test(d.sdp))
            throw Error("Invalid audio description")
          await pc.setRemoteDescription(d)
          if (this.closed || this.terminal(peer)) return
          for (const candidate of peer.pendingIce.splice(0)) await pc.addIceCandidate(candidate)
          if (!offerer) {
            const transceiver = pc.getTransceivers()[0]
            if (pc.getTransceivers().length !== 1 || !this.local) throw Error("Expected one audio transceiver")
            transceiver.direction = "sendrecv"
            transceiver.sender.setStreams(this.local)
            await transceiver.sender.replaceTrack(this.local.getAudioTracks()[0])
            this.transform(peer, transceiver.sender, peer.cryptoKey!, TransformOperation.Encrypt)
            await pc.setLocalDescription(await pc.createAnswer())
            if (!this.closed && !this.terminal(peer)) this.sendDescription(peer)
          }
        } else if (signal.type === "ice") {
          if (!signal.candidate || typeof signal.candidate.candidate !== "string" || signal.candidate.candidate.length > 4096) return
          if (pc.remoteDescription) await pc.addIceCandidate(signal.candidate)
          else if (peer.pendingIce.length < 64) peer.pendingIce.push(signal.candidate)
          else throw Error("Too many early ICE candidates")
        }
      } catch (_) {
        this.closePeer(peer, "failed")
      }
    })
  }

  setMuted(muted: boolean): void {
    this.muted = muted
    this.local?.getAudioTracks().forEach((t) => {
      t.enabled = !muted
    })
  }

  /** Ends both links and releases the single microphone, including pending permission. */
  end(): void {
    if (this.closed) return
    this.closed = true
    for (const peer of this.peers.values()) {
      if (!this.terminal(peer)) {
        this.send(peer, {callId: this.options.callId, to: peer.id, type: "leave"})
        this.closePeer(peer, "left")
      }
    }
    this.local?.getTracks().forEach((t) => {
      t.onended = null
      t.stop()
    })
    this.local = undefined
  }

  private enqueue(action: () => Promise<void>): Promise<void> {
    const result = this.queue.then(action)
    this.queue = result.catch(() => {})
    return result
  }

  private async connect(peer: GroupAudioPeer): Promise<void> {
    if (this.closed || peer.pc || this.terminal(peer) || !this.local) return
    try {
      const key = await callCrypto.decodeAesKey(peer.key)
      if (this.closed || this.terminal(peer)) return
      const config: RTCConfiguration & {encodedInsertableStreams: boolean} = {
        iceServers: this.options.iceServers,
        iceTransportPolicy: this.options.relay === false ? "all" : "relay",
        encodedInsertableStreams: true,
      }
      const pc = new RTCPeerConnection(config)
      peer.pc = pc
      peer.cryptoKey = key
      peer.abort = new AbortController()
      this.changeState(peer, "connecting")
      this.timeout(peer, 30000)
      pc.onicecandidate = ({candidate}) => {
        if (candidate && !this.closed && !this.terminal(peer))
          this.send(peer, {callId: this.options.callId, to: peer.id, type: "ice", candidate: candidate.toJSON()})
      }
      pc.onconnectionstatechange = () => {
        if (this.closed || this.terminal(peer)) return
        switch (pc.connectionState) {
          case "connected":
            clearTimeout(peer.timer)
            this.changeState(peer, "connected")
            break
          case "disconnected":
            this.changeState(peer, "disconnected")
            this.timeout(peer, 10000)
            break
          case "failed":
          case "closed":
            this.closePeer(peer, "failed")
            break
        }
      }
      pc.ontrack = ({track, receiver}) => {
        if (this.closed || this.terminal(peer)) return
        if (track.kind !== "audio") {
          this.closePeer(peer, "failed")
          return
        }
        try {
          this.transform(peer, receiver, key, TransformOperation.Decrypt)
          this.options.stream(peer.id, new MediaStream([track]))
        } catch (_) {
          this.closePeer(peer, "failed")
        }
      }
      // Only the offerer creates a transceiver. The answerer must use the one
      // created by setRemoteDescription, otherwise it creates an unused sender.
      if (this.options.self < peer.id) {
        const transceiver = pc.addTransceiver(this.local.getAudioTracks()[0], {direction: "sendrecv", streams: [this.local]})
        this.transform(peer, transceiver.sender, key, TransformOperation.Encrypt)
        this.transform(peer, transceiver.receiver, key, TransformOperation.Decrypt)
        await pc.setLocalDescription(await pc.createOffer())
        if (!this.closed && !this.terminal(peer)) this.sendDescription(peer)
      }
    } catch (_) {
      this.closePeer(peer, "failed")
    }
  }

  private transform(peer: GroupAudioPeer, endpoint: RTCRtpSender | RTCRtpReceiver, key: CryptoKey, operation: TransformOperation): void {
    if (this.transformed.has(endpoint)) return
    this.transformed.add(endpoint)
    const encrypted = endpoint as typeof endpoint & {createEncodedStreams: () => TransformStream}
    if (typeof encrypted.createEncodedStreams !== "function") throw Error("Encoded audio encryption unavailable")
    const {readable, writable} = encrypted.createEncodedStreams()
    readable
      .pipeThrough(new TransformStream({transform: callCrypto.transformFrame[operation](key)}))
      .pipeTo(writable, {signal: peer.abort!.signal})
      .catch(() => {
        if (!this.closed && !this.terminal(peer)) this.closePeer(peer, "failed")
      })
  }

  private sendDescription(peer: GroupAudioPeer): void {
    const d = peer.pc!.localDescription!
    this.send(peer, {callId: this.options.callId, to: peer.id, type: "description", description: {type: d.type, sdp: d.sdp}})
  }

  private send(peer: GroupAudioPeer, signal: GroupAudioSignal): void {
    try {
      this.options.send(signal)
    } catch (_) {
      this.closePeer(peer, "failed")
    }
  }

  private timeout(peer: GroupAudioPeer, ms: number): void {
    clearTimeout(peer.timer)
    peer.timer = setTimeout(() => this.closePeer(peer, "failed"), ms)
  }

  private terminal(peer: GroupAudioPeer): boolean {
    return peer.state === "left" || peer.state === "failed"
  }

  private changeState(peer: GroupAudioPeer, state: GroupAudioPeerState): void {
    peer.state = state
    // A UI observer must not prevent cleanup or change protocol state.
    try {
      this.options.state(peer.id, state)
    } catch (_) {}
  }

  private closePeer(peer: GroupAudioPeer, state: "left" | "failed", notify = true): void {
    if (this.terminal(peer)) return
    this.changeState(peer, state)
    if (state === "failed" && notify && !this.closed) {
      try {
        this.options.send({callId: this.options.callId, to: peer.id, type: "failed"})
      } catch (_) {}
    }
    clearTimeout(peer.timer)
    peer.abort?.abort()
    peer.pc?.close()
    peer.pc = undefined
    peer.cryptoKey = undefined
    peer.pendingIce = []
    if ([...this.peers.values()].every((p) => this.terminal(p))) this.end()
  }
}
