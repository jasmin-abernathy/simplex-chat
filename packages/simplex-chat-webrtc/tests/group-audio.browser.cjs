// Real Chromium media test. Signalling is an in-process authenticated test adapter,
// NOT the SimpleX transport. No network service or keys are published.
const {chromium} = require("playwright")
const {createServer} = require("node:http")
const fs = require("node:fs")
const assert = require("node:assert/strict")
const {randomBytes} = require("node:crypto")

;(async () => {
  const files = new Map(["/call.js", "/group-audio.js"].map((p) => [p, fs.readFileSync(`dist${p}`)]))
  const server = createServer((req, res) => {
    res.setHeader("Content-Type", files.has(req.url) ? "text/javascript" : "text/html")
    res.end(
      files.get(req.url) ||
        '<!doctype html><title>Local group audio test</title><script src="/call.js"></script><script src="/group-audio.js"></script>'
    )
  })
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve))
  let browser
  try {
    browser = await chromium.launch({
      executablePath: process.env.CHROMIUM || undefined,
      args: [
        "--no-sandbox",
        "--allow-loopback-in-peer-connection",
        "--disable-features=WebRtcHideLocalIpsWithMdns",
        "--use-fake-device-for-media-stream",
        "--use-fake-ui-for-media-stream",
        "--autoplay-policy=no-user-gesture-required",
      ],
    })
    const members = ["alice", "bob", "carol"],
      pages = new Map(),
      deliveries = [],
      errors = []
    const pairKeys = Object.fromEntries(["alice:bob", "alice:carol", "bob:carol"].map((p) => [p, randomBytes(32).toString("base64url")]))
    for (const self of members) {
      const context = await browser.newContext({permissions: ["microphone"]})
      const page = await context.newPage()
      pages.set(self, page)
      page.on("pageerror", (e) => errors.push(e.message))
      await page.exposeFunction("sendSignal", (signal) => {
        // Bind sender to its page. Do not accept a payload's claimed sender.
        const target = pages.get(signal.to)
        const delivery = target.evaluate(({sender, signal}) => window.call.receive(sender, signal), {sender: self, signal})
        deliveries.push(delivery.catch((e) => errors.push(e.message)))
      })
      await page.goto(`http://127.0.0.1:${server.address().port}`)
    }
    async function init(wrongKey = false) {
      for (const [self, page] of pages) {
        const keys = Object.fromEntries(members.filter((p) => p !== self).map((p) => [p, pairKeys[[p, self].sort().join(":")]]))
        if (wrongKey && self === "alice") keys.bob = randomBytes(32).toString("base64url")
        await page.evaluate(
          ({self, members, keys}) => {
            window.states = {}
            window.streams = {}
            window.frames = {encrypt: 0, decrypt: 0}
            // Observe successful transforms without replacing cryptography or media.
            if (!window.instrumented) {
              for (const op of ["encrypt", "decrypt"]) {
                const original = callCrypto.transformFrame[op]
                callCrypto.transformFrame[op] = (key) => {
                  const transform = original(key)
                  return async (frame, controller) => {
                    await transform(frame, controller)
                    window.frames[op]++
                  }
                }
              }
              window.instrumented = true
            }
            window.call = new GroupAudioCall({
              callId: "local-test-session",
              self,
              members,
              keys,
              iceServers: [],
              relay: false,
              send: (s) => {
                window.sendSignal(s).catch((e) => {
                  throw e
                })
              },
              state: (peer, state) => {
                window.states[peer] = state
              },
              stream: (peer, stream) => {
                window.streams[peer] = stream
                const audio = document.createElement("audio")
                audio.autoplay = true
                audio.srcObject = stream
                document.body.append(audio)
                audio.play()
              },
            })
          },
          {self, members, keys}
        )
      }
    }
    await init()
    await pages.get("alice").evaluate(() => window.call.accept())
    await pages.get("bob").evaluate(() => window.call.accept())
    await pages.get("alice").waitForFunction(() => window.states.bob === "connected")
    assert.equal(await pages.get("carol").evaluate(() => Object.keys(window.streams).length), 0)
    await pages.get("carol").evaluate(() => window.call.accept())
    for (const page of pages.values()) {
      await page.waitForFunction(() => Object.values(window.states).filter((s) => s === "connected").length === 2)
      await page.waitForFunction(() => window.frames.encrypt > 10 && window.frames.decrypt > 10)
      assert.equal(await page.evaluate(() => Object.keys(window.streams).length), 2)
    }
    // Verify actual decoded audio on EACH inbound connection, not just SDP/ICE state.
    for (const page of pages.values()) {
      await page.waitForFunction(async () => {
        const pcs = [...window.call.peers.values()].map((p) => p.pc)
        const stats = await Promise.all(pcs.map((pc) => pc.getStats()))
        return stats.every((report) =>
          [...report.values()].some((s) => s.type === "inbound-rtp" && s.kind === "audio" && s.totalAudioEnergy > 0)
        )
      })
    }
    await pages.get("alice").evaluate(() => window.call.setMuted(true))
    assert.equal(await pages.get("alice").evaluate(() => window.call.local.getAudioTracks()[0].enabled), false)
    await pages.get("alice").evaluate(() => window.call.setMuted(false))
    await pages.get("carol").evaluate(() => window.call.end())
    for (const p of ["alice", "bob"]) {
      await pages.get(p).waitForFunction(() => window.states.carol === "left")
      const other = p === "alice" ? "bob" : "alice"
      assert.equal(await pages.get(p).evaluate((id) => window.states[id], other), "connected")
    }
    for (const page of pages.values()) await page.evaluate(() => window.call.end())
    await Promise.all(deliveries)
    console.log("PASS: three real browser participants, six encrypted audio directions, mute and independent departure")

    // A key mismatch must fail the affected link, never silently send plain audio.
    await init(true)
    for (const page of pages.values()) await page.evaluate(() => window.call.accept())
    await pages.get("alice").waitForFunction(() => window.states.bob === "failed")
    await pages.get("bob").waitForFunction(() => window.states.alice === "failed")
    for (const page of pages.values()) await page.evaluate(() => window.call.end())
    await Promise.all(deliveries)
    assert.deepEqual(errors, [])
    console.log("PASS: incorrect pairwise key fails closed")
  } finally {
    await browser?.close()
    await new Promise((resolve) => server.close(resolve))
  }
})().catch((e) => {
  console.error(e)
  process.exitCode = 1
})
