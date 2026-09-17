import http from 'k6/http';

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export function createUser(baseUrl, email) {
  const res = http.post(
    `${baseUrl}/api/v1/users`,
    JSON.stringify({ displayName: 'Load Test', email }),
    JSON_HEADERS,
  );
  if (res.status !== 201) {
    throw new Error(`createUser failed: ${res.status} ${res.body}`);
  }
  return res.json('id');
}

export function openWallet(baseUrl, userId, currency) {
  const res = http.post(
    `${baseUrl}/api/v1/users/${userId}/wallets`,
    JSON.stringify({ currency }),
    JSON_HEADERS,
  );
  if (res.status !== 201) {
    throw new Error(`openWallet failed: ${res.status} ${res.body}`);
  }
  return res.json('id');
}

export function topUp(baseUrl, walletId, minorUnits, currency) {
  return http.post(
    `${baseUrl}/api/v1/wallets/${walletId}/topups`,
    JSON.stringify({ amount: { minorUnits, currency }, reference: 'k6' }),
    { ...JSON_HEADERS, tags: { name: 'POST /wallets/:id/topups' } },
  );
}

export function getWallet(baseUrl, walletId) {
  return http.get(`${baseUrl}/api/v1/wallets/${walletId}`, {
    tags: { name: 'GET /wallets/:id' },
  });
}

export function getEntries(baseUrl, walletId, page, size) {
  return http.get(
    `${baseUrl}/api/v1/wallets/${walletId}/entries?page=${page}&size=${size}`,
    { tags: { name: 'GET /wallets/:id/entries' } },
  );
}

/**
 * Refuses to run against the wrong target. Port 8080 on this machine is a Vite dev server that
 * answers 200 with HTML for every path, so a load test aimed there would report healthy reads
 * against a page that has nothing to do with this service.
 */
export function assertIsWalletService(baseUrl) {
  const res = http.get(`${baseUrl}/actuator/health`);
  const body = String(res.body || '');
  if (res.status !== 200 || !body.includes('"status"')) {
    throw new Error(
      `${baseUrl} is not the wallet service (status ${res.status}, body starts "${body.slice(0, 40)}"). ` +
        `Start it with: ./gradlew :services:wallet-service:bootRun --args='--server.port=8081'`,
    );
  }
}
