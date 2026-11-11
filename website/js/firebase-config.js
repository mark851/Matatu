import { initializeApp } from "https://www.gstatic.com/firebasejs/11.6.1/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut as firebaseSignOut,
  onAuthStateChanged,
  connectAuthEmulator,
} from "https://www.gstatic.com/firebasejs/11.6.1/firebase-auth.js";
import {
  initializeFirestore,
  connectFirestoreEmulator,
  doc,
  collection,
  query,
  where,
  orderBy,
  limit,
  getDoc,
  getDocs,
  onSnapshot,
  writeBatch,
  runTransaction,
  serverTimestamp,
} from "https://www.gstatic.com/firebasejs/11.6.1/firebase-firestore.js";

export const DATABASE_ID = "ai-studio-android-amatatu-62169e37-c47a-4d67-8810-00cb6776f596";

// Shared Firebase project configuration synced with app/google-services.json
// and app/src/main/res/values/firebase_applet_config.xml
export const FIREBASE_CONFIG = {
  apiKey: "AIzaSyB-B9qD-RBV5qhjwI2-GeVpdeZ23YnagMg",
  authDomain: "gen-lang-client-0429387427.firebaseapp.com",
  projectId: "gen-lang-client-0429387427",
  storageBucket: "gen-lang-client-0429387427.firebasestorage.app",
  messagingSenderId: "594172231853",
  appId: "1:594172231853:web:amatatukampala",
};

export const fsOps = {
  doc,
  collection,
  query,
  where,
  orderBy,
  limit,
  getDoc,
  getDocs,
  onSnapshot,
  writeBatch,
  runTransaction,
  serverTimestamp,
};

let initialized = null;

export function getFirebaseServices() {
  if (initialized) return initialized;
  const app = initializeApp(FIREBASE_CONFIG);
  const auth = getAuth(app);
  const db = initializeFirestore(app, {}, DATABASE_ID);

  if (typeof window !== "undefined") {
    const params = new URLSearchParams(window.location.search);
    if (params.get("emulator") === "1") {
      connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
      connectFirestoreEmulator(db, "127.0.0.1", 8085);
    }
  }

  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: "select_account" });

  initialized = {
    app,
    auth,
    db,
    provider,
    signInWithPopup: () => signInWithPopup(auth, provider),
    signInWithRedirect: () => signInWithRedirect(auth, provider),
    getRedirectResult: () => getRedirectResult(auth),
    signOut: () => firebaseSignOut(auth),
    onAuthStateChanged: (cb) => onAuthStateChanged(auth, cb),
  };
  return initialized;
}
