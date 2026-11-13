const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function validProfile(uid) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    displayName: "Alice Motion",
    subscriptionTier: "free",
    preferredExportQuality: "1080p",
    preferredFps: 60,
    cloudBackupEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validProject(uid) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    title: "Tokyo Drift Reel",
    aspectRatio: "9:16",
    resolution: "4K",
    fps: 60,
    durationMs: 12000,
    layerCount: 4,
    layersJson: '{"layers":[]}',
    createdAt: ts,
    updatedAt: ts,
  };
}

function validPreset(uid, published = true) {
  const ts = pastTimestamp();
  return {
    authorId: uid,
    authorName: "AliceVFX",
    title: "Neon Velocity Glow",
    category: "Velocity",
    description: "Sharp ease-out speed ramp with cyan glow",
    isPublished: published,
    isPremium: false,
    effectSummary: "Glow 0.7 + Speed Ramp",
    presetPayloadJson: '{"glow":0.7,"curve":"expoOut"}',
    likesCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write profiles, projects, or presets", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb.collection("presets").where("isPublished", "==", true).get()
  );
});

test("Owner can create and update their own user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(ref.set(validProfile(ALICE_UID)));
  await assertSucceeds(
    ref.update({
      preferredExportQuality: "4K",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Cross-user access to user profile and projects is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .set(validProject(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_1")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
});

test("Owner can list, update, and delete their own projects; ghost field rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects")
    .doc("proj_1");

  await assertSucceeds(projRef.set(validProject(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    projRef.set({ ...validProject(ALICE_UID), ghostAdmin: true })
  );
  await assertSucceeds(
    projRef.update({ title: "Updated Reel", updatedAt: pastTimestamp() })
  );
  await assertSucceeds(projRef.delete());
});

test("Presets: unfiltered list fails; published query succeeds; cross-user update restricted to +1 like", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const presetRef = aliceDb.collection("presets").doc("preset_1");
  await assertSucceeds(presetRef.set(validPreset(ALICE_UID, true)));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("presets").get());
  await assertSucceeds(
    bobDb.collection("presets").where("isPublished", "==", true).get()
  );
  await assertFails(
    bobDb.collection("presets").doc("preset_1").update({
      title: "Hacked",
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("presets").doc("preset_1").update({
      likesCount: 1,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("presets").doc("preset_1").update({
      likesCount: 6,
      updatedAt: pastTimestamp(),
    })
  );
});
