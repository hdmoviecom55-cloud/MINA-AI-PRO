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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any MINA AI collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("chats").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      title: "Test",
      content: "Test",
      category: "PREFERENCE",
      encrypted: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner can create and partially update UserProfile; shadow fields and cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Aarav",
      bio: "Dreamer & Builder",
      privateNote: "",
      avatarUri: "",
      youtubeUrl: "",
      instagramUrl: "",
      socialUrl: "",
      musicUrl: "",
      personality: "CARING",
      emotionLevel: 4,
      language: "HINGLISH",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      personality: "BEST_FRIEND",
      emotionLevel: 5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot read or write Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: new Date(Date.now() - 1000) }));
});

test("Authenticated owner can CRUD memories, chats, journals, documents, and favorite places", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Memory
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      title: "Morning Chai",
      content: "User prefers masala chai at 8 AM",
      category: "PREFERENCE",
      encrypted: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());

  // Chat
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("msg_1").set({
      userId: ALICE_UID,
      role: "USER",
      text: "Hey Mina, kaise ho?",
      personality: "CARING",
      createdAt: now,
      updatedAt: now,
    })
  );
  // Extra @DocumentId 'id' field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("msg_2").set({
      id: "msg_2",
      userId: ALICE_UID,
      role: "MINA",
      text: "Main bilkul theek hoon!",
      personality: "CARING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Journal
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("journals").doc("j_1").set({
      userId: ALICE_UID,
      title: "Productive Day",
      content: "Built my Android app today.",
      mood: "HAPPY",
      encrypted: false,
      aiReflection: "Bahut badhiya! Keep going.",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Document
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_1").set({
      userId: ALICE_UID,
      title: "Software Engineer Resume",
      docType: "RESUME",
      content: "# Resume\nExperience...",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Favorite Place
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("places").doc("place_1").set({
      userId: ALICE_UID,
      name: "Home Studio",
      address: "Connaught Place, New Delhi",
      latitude: 28.6315,
      longitude: 77.2167,
      createdAt: now,
      updatedAt: now,
    })
  );
  // Invalid latitude rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("places").doc("place_bad").set({
      userId: ALICE_UID,
      name: "Bad Place",
      address: "Nowhere",
      latitude: 120.0,
      longitude: 77.2,
      createdAt: now,
      updatedAt: now,
    })
  );
});
