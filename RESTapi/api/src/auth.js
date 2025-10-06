import { createRemoteJWKSet, jwtVerify } from "jose";

// Keycloak realm issuer
const ISSUER = "https://keycloak.10-0-2-2.sslip.io/realms/WeightTrackerAPI";

const JWKS_URL = 'http://keycloak:8080/realms/WeightTrackerAPI/protocol/openid-connect/certs';

// JWKS endpoint (Keycloak’s signing keys). jose caches/refreshes automatically.
const JWKS = createRemoteJWKSet(new URL(JWKS_URL), { timeoutDuration: 5000 }); // 5s

// Express middleware: requires a valid Bearer token, verifies signature + issuer.
export async function requireAuth(req, res, next) {
  try {
    const hdr = req.headers.authorization || '';
    const m = hdr.match(/^Bearer\s+(.+)/i);
    if (!m) return res.status(401).json({ error: 'missing_bearer' });

    const token = m[1];

    // Verify signature + issuer. // Skipping audience enforcement
    const { payload } = await jwtVerify(token, JWKS, { issuer: ISSUER });

    req.user = payload;     // make claims available to handlers
    next();
  } catch (err) {
    console.log('JWT verify failed:', err.message || err);
    res.status(401).json({ error: 'invalid_token' });
  }
}