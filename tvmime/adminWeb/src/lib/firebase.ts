import { initializeApp, getApps, getApp } from 'firebase/app';
import { 
  getAuth, 
  signInAnonymously, 
  signInWithEmailAndPassword, 
  createUserWithEmailAndPassword,
  signOut, 
  onAuthStateChanged,
  type User 
} from 'firebase/auth';
import { 
  getFirestore, 
  collection, 
  doc, 
  addDoc, 
  updateDoc, 
  deleteDoc, 
  onSnapshot, 
  query, 
  where,
  type Unsubscribe
} from 'firebase/firestore';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyDhPNLT3YUqW6I6KVIt5-Kbop9mlaSRufw",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "tvmime-65909.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "tvmime-65909",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "tvmime-65909.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "996872768680",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:996872768680:web:0d06f0f8c6d4d572b2069d"
};

// Initialize Firebase safely
export const app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);

export interface XtreamPortal {
  id?: string;
  userId: string;
  name: string;
  serverUrl: string;
  username: string;
  password: string;
  m3uUrl?: string;
  type: 'xtream' | 'stalker' | 'm3u';
  isActive: boolean;
  syncLive?: boolean;
  syncMovies?: boolean;
  syncSeries?: boolean;
  createdAt: number;
  lastVerifiedAt?: number;
  status?: 'unknown' | 'online' | 'unauthorized' | 'offline';
  channelCount?: number;
  vodCount?: number;
  expiryDate?: string;
}


// Firestore operations for portals
export function subscribeToUserPortals(userId: string, callback: (portals: XtreamPortal[]) => void): Unsubscribe {
  const portalsRef = collection(db, 'user_portals');
  const q = query(portalsRef, where('userId', '==', userId));
  
  return onSnapshot(q, (snapshot) => {
    const portals: XtreamPortal[] = snapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data()
    } as XtreamPortal));
    
    // Sort client-side by creation date descending
    portals.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
    callback(portals);
  }, (error) => {
    console.error("Error subscribing to portals:", error);
  });
}

function cleanUndefined<T extends Record<string, any>>(obj: T): Partial<T> {
  const cleaned: any = {};
  for (const [key, value] of Object.entries(obj)) {
    if (value !== undefined) {
      cleaned[key] = value;
    }
  }
  return cleaned;
}

export async function addPortal(portal: Omit<XtreamPortal, 'id'>): Promise<string> {
  const portalsRef = collection(db, 'user_portals');
  const cleaned = cleanUndefined({
    ...portal,
    createdAt: portal.createdAt || Date.now()
  });
  const docRef = await addDoc(portalsRef, cleaned);
  return docRef.id;
}

export async function updatePortal(id: string, updates: Partial<XtreamPortal>): Promise<void> {
  const docRef = doc(db, 'user_portals', id);
  const cleaned = cleanUndefined(updates);
  await updateDoc(docRef, cleaned);
}

export async function deletePortal(id: string): Promise<void> {
  const docRef = doc(db, 'user_portals', id);
  await deleteDoc(docRef);
}


export {
  signInAnonymously,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut,
  onAuthStateChanged,
  type User
};
