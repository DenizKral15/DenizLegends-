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
const CHARLIE_UID = "charlie_789";
const OFFICIAL_ADMIN_EMAIL = "hediyeergezer4@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validMemberPayload(uid, networkId, rank = "OYUNCU") {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    networkId,
    username: "Player_" + uid.slice(0, 5),
    rank,
    level: 1,
    xp: 0,
    coins: 500,
    gems: 10,
    gameMode: "SURVIVAL",
    inventoryJson: "[]",
    banned: false,
    banReason: "",
    banUntilMillis: 0,
    muted: false,
    muteReason: "",
    muteUntilMillis: 0,
    warningsCount: 0,
    skinColor: 1,
    createdAt: now,
    updatedAt: now,
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

test("Unauthenticated users cannot read or write any DenizCraft collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("networks").where("active", "==", true).get());
  await assertFails(unauth.collection("networks").doc("official").collection("rooms").where("networkId", "==", "official").get());
});

test("User accounts enforce strict PII isolation and block shadow fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "DenizKral",
      email: "alice@example.com",
      skinColor: 2,
      hatId: "crown",
      globalCoins: 1000,
      globalGems: 25,
      globalXp: 120,
      globalLevel: 3,
      preferredNetworkId: "official",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot read Alice's private account
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  // Shadow field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Valid partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "DenizEfsane",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Independent networks can be created by players and founder gets Kurucu rank", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Regular player cannot create "official" network
  await assertFails(
    bobDb.collection("networks").doc("official").set({
      name: "Fake Official",
      description: "Hijack",
      policyText: "None",
      founderUid: BOB_UID,
      founderName: "Bob",
      official: true,
      active: true,
      playerCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates her own independent network
  await assertSucceeds(
    aliceDb.collection("networks").doc("net_korsan").set({
      name: "KorsanCraft Network",
      description: "Bagimsiz korsan sunucusu",
      policyText: "Saygili olun",
      founderUid: ALICE_UID,
      founderName: "Alice",
      official: false,
      active: true,
      playerCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Query alignment test
  await assertSucceeds(bobDb.collection("networks").where("active", "==", true).get());
  await assertFails(bobDb.collection("networks").get());

  // Founder can register as KURUCU; non-founder Bob cannot self-assign KURUCU
  await assertSucceeds(
    aliceDb.collection("networks").doc("net_korsan").collection("members").doc(ALICE_UID).set(validMemberPayload(ALICE_UID, "net_korsan", "KURUCU"))
  );
  await assertFails(
    bobDb.collection("networks").doc("net_korsan").collection("members").doc(BOB_UID).set(validMemberPayload(BOB_UID, "net_korsan", "KURUCU"))
  );
  await assertSucceeds(
    bobDb.collection("networks").doc("net_korsan").collection("members").doc(BOB_UID).set(validMemberPayload(BOB_UID, "net_korsan", "OYUNCU"))
  );
});

test("Staff RBAC hierarchy enforces Yonetici/Moderator/Yardimci boundaries and Kurucu protection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(); // Founder / Kurucu
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();     // Yonetici -> Moderator -> Yardimci
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore(); // Oyuncu
  const now = new Date(Date.now() - 5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("networks").doc("net_1").set({
      name: "Test Net",
      description: "Test",
      policyText: "Rules",
      founderUid: ALICE_UID,
      founderName: "Alice",
      official: false,
      active: true,
      playerCount: 3,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("networks").doc("net_1").collection("members").doc(ALICE_UID).set(validMemberPayload(ALICE_UID, "net_1", "KURUCU"));
    await db.collection("networks").doc("net_1").collection("members").doc(BOB_UID).set(validMemberPayload(BOB_UID, "net_1", "YONETICI"));
    await db.collection("networks").doc("net_1").collection("members").doc(CHARLIE_UID).set(validMemberPayload(CHARLIE_UID, "net_1", "OYUNCU"));
  });

  // Yonetici (Bob) CANNOT assign KURUCU or YONETICI, and CANNOT punish Kurucu (Alice)
  await assertFails(
    bobDb.collection("networks").doc("net_1").collection("members").doc(CHARLIE_UID).update({
      rank: "YONETICI",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("networks").doc("net_1").collection("members").doc(ALICE_UID).update({
      banned: true,
      banReason: "Test",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Yonetici CAN assign MODERATOR to Charlie
  await assertSucceeds(
    bobDb.collection("networks").doc("net_1").collection("members").doc(CHARLIE_UID).update({
      rank: "MODERATOR",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Charlie (now MODERATOR) can mute Bob, CANNOT change Bob's rank or coins
  await assertSucceeds(
    charlieDb.collection("networks").doc("net_1").collection("members").doc(BOB_UID).update({
      muted: true,
      muteReason: "Spam",
      muteUntilMillis: Date.now() + 60000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    charlieDb.collection("networks").doc("net_1").collection("members").doc(BOB_UID).update({
      coins: 999999,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Muted Bob CANNOT send chat message in room
  await assertSucceeds(
    aliceDb.collection("networks").doc("net_1").collection("rooms").doc("room_1").set({
      networkId: "net_1",
      serverType: "BEDWARS",
      mapName: "Zumrut Adalar",
      waitingMapName: "Gokyuzu Iskelesi",
      status: "WAITING",
      hostUid: ALICE_UID,
      maxPlayers: 8,
      playerCount: 1,
      theme: "",
      winnerName: "",
      createdAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  await assertFails(
    bobDb.collection("networks").doc("net_1").collection("rooms").doc("room_1").collection("chat").doc("msg_1").set({
      roomId: "room_1",
      networkId: "net_1",
      senderUid: BOB_UID,
      senderName: "Bob",
      senderRank: "YONETICI",
      senderLevel: 1,
      text: "Hello while muted",
      createdAt: new Date(Date.now() - 1000),
    })
  );
});

test("Rooms, players, blocks, tickets, reports, and plots enforce validation and alignment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: OFFICIAL_ADMIN_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("networks").doc("official").set({
      name: "DenizCraft Resmi Network",
      description: "Resmi Sunucu",
      policyText: "Hile yasaktir",
      founderUid: ALICE_UID,
      founderName: "Kurucu",
      official: true,
      active: true,
      playerCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("networks").doc("official").collection("members").doc(ALICE_UID).set(validMemberPayload(ALICE_UID, "official", "KURUCU"))
  );

  await assertSucceeds(
    bobDb.collection("networks").doc("official").collection("tickets").doc("t1").set({
      networkId: "official",
      authorUid: BOB_UID,
      authorName: "Bob",
      subject: "Ada yardimi",
      message: "Skyblock adamda bug var",
      status: "OPEN",
      staffReply: "",
      repliedBy: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot answer his own ticket; Kurucu Alice can
  await assertFails(
    bobDb.collection("networks").doc("official").collection("tickets").doc("t1").update({
      status: "ANSWERED",
      staffReply: "Kendim cozdum",
      repliedBy: "Bob",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("networks").doc("official").collection("tickets").doc("t1").update({
      status: "ANSWERED",
      staffReply: "Incelendi ve duzeltildi",
      repliedBy: "Kurucu",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Creative plot save scoped to owner
  await assertSucceeds(
    bobDb.collection("networks").doc("official").collection("plots").doc("plot_bob").set({
      ownerUid: BOB_UID,
      networkId: "official",
      mode: "CREATIVE",
      plotName: "Deniz Evim",
      blocksData: "5,1,5:WOOD;",
      islandLevel: 1,
      updatedAt: now,
    })
  );
  await assertSucceeds(bobDb.collection("networks").doc("official").collection("plots").where("ownerUid", "==", BOB_UID).get());

  // Kurucu (Alice) CANNOT demote or ban her own Kurucu account
  await assertFails(
    aliceDb.collection("networks").doc("official").collection("members").doc(ALICE_UID).update({
      rank: "OYUNCU",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("networks").doc("official").collection("members").doc(ALICE_UID).update({
      banned: true,
      banReason: "Self ban",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Room player interactions (PvP hit / gift / wave)
  await assertSucceeds(
    bobDb.collection("networks").doc("official").collection("rooms").doc("r_pvp").collection("interactions").doc("int_1").set({
      roomId: "r_pvp",
      networkId: "official",
      fromUid: BOB_UID,
      fromName: "Bob",
      targetUid: ALICE_UID,
      actionType: "HIT",
      amount: 6,
      itemId: "iron_sword",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("networks").doc("official").collection("rooms").doc("r_pvp").collection("interactions").where("roomId", "==", "r_pvp").get()
  );
  await assertSucceeds(
    aliceDb.collection("networks").doc("official").collection("rooms").doc("r_pvp").collection("interactions").doc("int_1").delete()
  );
});

test("Banned player is blocked across entire network and can be evicted by staff while other networks remain accessible", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();   // Kurucu
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();       // Moderator
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore(); // Target Player
  const now = new Date(Date.now() - 5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const netId of ["net_1", "net_2"]) {
      await db.collection("networks").doc(netId).set({
        name: "Network " + netId,
        description: "Test",
        policyText: "Rules",
        founderUid: ALICE_UID,
        founderName: "Alice",
        official: false,
        active: true,
        playerCount: 3,
        createdAt: now,
        updatedAt: now,
      });
      await db.collection("networks").doc(netId).collection("members").doc(ALICE_UID).set(validMemberPayload(ALICE_UID, netId, "KURUCU"));
      await db.collection("networks").doc(netId).collection("members").doc(BOB_UID).set(validMemberPayload(BOB_UID, netId, "MODERATOR"));
      await db.collection("networks").doc(netId).collection("members").doc(CHARLIE_UID).set(validMemberPayload(CHARLIE_UID, netId, "OYUNCU"));
    }
    await db.collection("networks").doc("net_1").collection("rooms").doc("room_lobi").set({
      networkId: "net_1",
      serverType: "LOBI",
      mapName: "DenizLimanı Ana Meydan",
      waitingMapName: "DenizLimanı İskelesi",
      status: "PLAYING",
      hostUid: ALICE_UID,
      maxPlayers: 24,
      playerCount: 2,
      theme: "",
      winnerName: "",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("networks").doc("net_1").collection("rooms").doc("room_lobi").collection("players").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      roomId: "room_lobi",
      networkId: "net_1",
      username: "Charlie",
      rank: "OYUNCU",
      level: 1,
      team: "NONE",
      x: 0,
      y: 2,
      z: 0,
      yaw: 0,
      hp: 20,
      alive: true,
      skinColor: 1,
      updatedAt: now,
    });
  });

  // Moderator Bob bans Charlie permanently on net_1
  await assertSucceeds(
    bobDb.collection("networks").doc("net_1").collection("members").doc(CHARLIE_UID).update({
      banned: true,
      banReason: "Hile kullanimi",
      banUntilMillis: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Moderator Bob CAN immediately delete Charlie's presence doc in net_1 room/lobby
  await assertSucceeds(
    bobDb.collection("networks").doc("net_1").collection("rooms").doc("room_lobi").collection("players").doc(CHARLIE_UID).delete()
  );

  // Banned Charlie CANNOT rejoin/update player presence, create rooms, place blocks, chat, interact, or save plots in net_1
  await assertFails(
    charlieDb.collection("networks").doc("net_1").collection("rooms").doc("room_lobi").collection("players").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      roomId: "room_lobi",
      networkId: "net_1",
      username: "Charlie",
      rank: "OYUNCU",
      level: 1,
      team: "NONE",
      x: 1,
      y: 2,
      z: 1,
      yaw: 0,
      hp: 20,
      alive: true,
      skinColor: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    charlieDb.collection("networks").doc("net_1").collection("rooms").doc("room_bw_new").set({
      networkId: "net_1",
      serverType: "BEDWARS",
      mapName: "Zumrut Adalar",
      waitingMapName: "Gokyuzu Iskelesi",
      status: "WAITING",
      hostUid: CHARLIE_UID,
      maxPlayers: 8,
      playerCount: 1,
      theme: "",
      winnerName: "",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    charlieDb.collection("networks").doc("net_1").collection("rooms").doc("room_lobi").collection("blocks").doc("1_2_1").set({
      roomId: "room_lobi",
      x: 1,
      y: 2,
      z: 1,
      blockType: "WOOD",
      placedBy: CHARLIE_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    charlieDb.collection("networks").doc("net_1").collection("rooms").doc("room_lobi").collection("chat").doc("c1").set({
      roomId: "room_lobi",
      networkId: "net_1",
      senderUid: CHARLIE_UID,
      senderName: "Charlie",
      senderRank: "OYUNCU",
      senderLevel: 1,
      text: "Banned user msg",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    charlieDb.collection("networks").doc("net_1").collection("rooms").doc("room_lobi").collection("interactions").doc("i1").set({
      roomId: "room_lobi",
      networkId: "net_1",
      fromUid: CHARLIE_UID,
      fromName: "Charlie",
      targetUid: ALICE_UID,
      actionType: "WAVE",
      amount: 0,
      itemId: "",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    charlieDb.collection("networks").doc("net_1").collection("plots").doc("charlie_creative").set({
      ownerUid: CHARLIE_UID,
      networkId: "net_1",
      mode: "CREATIVE",
      plotName: "Evim",
      blocksData: "1,1,1:WOOD;",
      islandLevel: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Charlie CAN still join rooms on an independent network net_2 where he is not banned
  await assertSucceeds(
    charlieDb.collection("networks").doc("net_2").collection("rooms").doc("room_net2").set({
      networkId: "net_2",
      serverType: "LOBI",
      mapName: "Mercan Adası Hub",
      waitingMapName: "DenizLimanı İskelesi",
      status: "PLAYING",
      hostUid: CHARLIE_UID,
      maxPlayers: 24,
      playerCount: 1,
      theme: "",
      winnerName: "",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Once Charlie's ban is expired in the past, Charlie can rejoin net_1
  await assertSucceeds(
    bobDb.collection("networks").doc("net_1").collection("members").doc(CHARLIE_UID).update({
      banned: true,
      banReason: "Expired",
      banUntilMillis: Date.now() - 60000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    charlieDb.collection("networks").doc("net_1").collection("rooms").doc("room_lobi").collection("players").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      roomId: "room_lobi",
      networkId: "net_1",
      username: "Charlie",
      rank: "OYUNCU",
      level: 1,
      team: "NONE",
      x: 1,
      y: 2,
      z: 1,
      yaw: 0,
      hp: 20,
      alive: true,
      skinColor: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Maintenance mode, network deletion, protected LOBI/WAITING blocks, and matchmaking matchStartAtMillis", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: OFFICIAL_ADMIN_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("networks").doc("official").set({
      name: "DenizCraft Resmi Network",
      description: "Resmi",
      policyText: "Kurallar",
      founderUid: ALICE_UID,
      founderName: "Kurucu",
      official: true,
      active: true,
      maintenanceMode: false,
      playerCount: 3,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("networks").doc("net_custom").set({
      name: "Ozel Ag",
      description: "Test Agi",
      policyText: "Kurallar",
      founderUid: ALICE_UID,
      founderName: "Alice",
      official: false,
      active: true,
      maintenanceMode: false,
      playerCount: 3,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("networks").doc("net_custom").collection("members").doc(ALICE_UID).set(validMemberPayload(ALICE_UID, "net_custom", "KURUCU"));
    await db.collection("networks").doc("net_custom").collection("members").doc(BOB_UID).set(validMemberPayload(BOB_UID, "net_custom", "YONETICI"));
    await db.collection("networks").doc("net_custom").collection("members").doc(CHARLIE_UID).set(validMemberPayload(CHARLIE_UID, "net_custom", "OYUNCU"));

    await db.collection("networks").doc("net_custom").collection("rooms").doc("r_lobi").set({
      networkId: "net_custom",
      serverType: "LOBI",
      mapName: "DenizLimanı Ana Meydan",
      waitingMapName: "DenizLimanı İskelesi",
      status: "PLAYING",
      hostUid: ALICE_UID,
      maxPlayers: 24,
      playerCount: 2,
      theme: "",
      winnerName: "",
      matchStartAtMillis: 0,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("networks").doc("net_custom").collection("rooms").doc("r_bw_wait").set({
      networkId: "net_custom",
      serverType: "BEDWARS",
      mapName: "Zumrut Adalar",
      waitingMapName: "Gokyuzu Iskelesi",
      status: "WAITING",
      hostUid: ALICE_UID,
      maxPlayers: 8,
      playerCount: 2,
      theme: "",
      winnerName: "",
      matchStartAtMillis: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  // 1. Non-staff Charlie CANNOT modify blocks in LOBI or WAITING rooms
  await assertFails(
    charlieDb.collection("networks").doc("net_custom").collection("rooms").doc("r_lobi").collection("blocks").doc("0_1_0").set({
      roomId: "r_lobi",
      x: 0, y: 1, z: 0,
      blockType: "AIR",
      placedBy: CHARLIE_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    charlieDb.collection("networks").doc("net_custom").collection("rooms").doc("r_bw_wait").collection("blocks").doc("1_1_1").set({
      roomId: "r_bw_wait",
      x: 1, y: 1, z: 1,
      blockType: "WOOD",
      placedBy: CHARLIE_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Staff Yonetici Bob CAN modify LOBI blocks
  await assertSucceeds(
    bobDb.collection("networks").doc("net_custom").collection("rooms").doc("r_lobi").collection("blocks").doc("0_1_0").set({
      roomId: "r_lobi",
      x: 0, y: 1, z: 0,
      blockType: "GOLD",
      placedBy: BOB_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 2. Matchmaking: updating matchStartAtMillis and transitioning to PLAYING lets Charlie place blocks in Bedwars match
  await assertSucceeds(
    aliceDb.collection("networks").doc("net_custom").collection("rooms").doc("r_bw_wait").update({
      status: "PLAYING",
      matchStartAtMillis: Date.now() + 30000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    charlieDb.collection("networks").doc("net_custom").collection("rooms").doc("r_bw_wait").collection("blocks").doc("2_5_2").set({
      roomId: "r_bw_wait",
      x: 2, y: 5, z: 2,
      blockType: "WOOL_CYAN",
      placedBy: CHARLIE_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 3. Regular player CANNOT toggle maintenance mode; Yonetici CAN
  await assertFails(
    charlieDb.collection("networks").doc("net_custom").update({
      maintenanceMode: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("networks").doc("net_custom").update({
      maintenanceMode: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // While maintenanceMode == true, non-staff Charlie is blocked from joining/updating players, while staff Bob succeeds
  await assertFails(
    charlieDb.collection("networks").doc("net_custom").collection("rooms").doc("r_lobi").collection("players").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      roomId: "r_lobi",
      networkId: "net_custom",
      username: "Charlie",
      rank: "OYUNCU",
      level: 1,
      team: "NONE",
      x: 0, y: 2, z: 0, yaw: 0, hp: 20, alive: true, skinColor: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("networks").doc("net_custom").collection("rooms").doc("r_lobi").collection("players").doc(BOB_UID).set({
      userId: BOB_UID,
      roomId: "r_lobi",
      networkId: "net_custom",
      username: "Bob",
      rank: "YONETICI",
      level: 1,
      team: "NONE",
      x: 0, y: 2, z: 0, yaw: 0, hp: 20, alive: true, skinColor: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 4. Deleting networks: Yonetici & Charlie cannot delete; official network cannot be deleted; Founder/Kurucu CAN delete net_custom
  await assertFails(bobDb.collection("networks").doc("net_custom").delete());
  await assertFails(charlieDb.collection("networks").doc("net_custom").delete());
  await assertFails(aliceDb.collection("networks").doc("official").delete());
  await assertSucceeds(aliceDb.collection("networks").doc("net_custom").delete());
});

test("Daily bonus enforces 24h cooldown and blocks repeated claims or timestamp spoofing", async () => {
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("networks").doc("net_bonus").set({
      name: "Bonus Net",
      description: "Test",
      policyText: "Rules",
      founderUid: ALICE_UID,
      founderName: "Alice",
      official: false,
      active: true,
      maintenanceMode: false,
      playerCount: 2,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("networks").doc("net_bonus").collection("members").doc(ALICE_UID).set(validMemberPayload(ALICE_UID, "net_bonus", "KURUCU"));
    await db.collection("networks").doc("net_bonus").collection("members").doc(CHARLIE_UID).set(validMemberPayload(CHARLIE_UID, "net_bonus", "OYUNCU"));
  });

  const charlieRef = charlieDb.collection("networks").doc("net_bonus").collection("members").doc(CHARLIE_UID);

  // Backdated lastDailyBonusAt spoofing is rejected
  await assertFails(
    charlieRef.update({
      coins: 750,
      gems: 15,
      xp: 60,
      level: 1,
      lastDailyBonusAt: new Date(Date.now() - 90000000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Over-inflated coins during daily bonus is rejected
  await assertFails(
    charlieRef.update({
      coins: 5000,
      gems: 15,
      xp: 60,
      level: 1,
      lastDailyBonusAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // First valid daily bonus claim succeeds
  await assertSucceeds(
    charlieRef.update({
      coins: 750,
      gems: 15,
      xp: 60,
      level: 1,
      lastDailyBonusAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immediate repeat daily bonus claim within 24h is rejected
  await assertFails(
    charlieRef.update({
      coins: 1000,
      gems: 20,
      xp: 120,
      level: 1,
      lastDailyBonusAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Normal non-bonus progress update (without touching lastDailyBonusAt) still succeeds
  await assertSucceeds(
    charlieRef.update({
      coins: 700,
      inventoryJson: "iron_sword:1",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Member whose lastDailyBonusAt is > 24h ago can claim again
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("networks").doc("net_bonus").collection("members").doc(ALICE_UID).update({
      lastDailyBonusAt: new Date(Date.now() - 86405000),
    });
  });
  await assertSucceeds(
    aliceDb.collection("networks").doc("net_bonus").collection("members").doc(ALICE_UID).update({
      coins: 750,
      gems: 15,
      xp: 60,
      level: 1,
      lastDailyBonusAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});



