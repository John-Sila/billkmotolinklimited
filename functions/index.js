const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// Example function to notify admins
exports.notifyAdmins = functions.https.onCall(async (data) => {
  const message = data.message;

  const snapshot = await admin.firestore()
      .collection("users")
      .where("userRank", "in", ["Admin", "CEO"])
      .get();

  const tokens = [];
  snapshot.forEach((doc) => {
    const token = doc.get("fcmToken");
    if (token) tokens.push(token);
  });

  if (tokens.length === 0) {
    return {success: false, reason: "No tokens found"};
  }

  const response = await admin.messaging().sendMulticast({
    tokens,
    notification: {
      title: "Admin Alert",
      body: message,
    },
  });

  return {
    success: true,
    sent: response.successCount,
    failed: response.failureCount,
  };
});
