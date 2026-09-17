import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
  assertIsWalletService,
  createUser,
  getEntries,
  getWallet,
  openWallet,
  topUp,
} from './lib/api.js';

/*
 * Three scenarios, run one after another, each isolating one hypothesis:
 *
 *   hot_wallet       many VUs top up ONE wallet          -> contention on that wallet's row
 *   distinct_wallets many VUs top up their OWN wallets   -> no shared wallet, yet both sides of
 *                                                           every entry touch EXTERNAL_FUNDING_JPY
 *   reads            GET wallet and entries under load   -> the Week 3 latency baseline
 *
 * The comparison is the point. If distinct_wallets conflicts as much as hot_wallet, the
 * bottleneck is not the user's wallet at all -- it is the single system account every top-up
 * writes to.
 *
 * The pass/fail gate is NOT "no errors". Failed top-ups are expected right now and are the
 * finding. The gate is that no money was lost: for every wallet, the balance must equal the
 * number of journal entries times the top-up amount.
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const CURRENCY = __ENV.CURRENCY || 'JPY';
const AMOUNT = Number(__ENV.AMOUNT || 100);
const VUS = Number(__ENV.VUS || 20);
const ITERATIONS = Number(__ENV.ITERATIONS || 200);

const hotCreated = new Counter('hot_topup_created');
const hotConflict = new Counter('hot_topup_conflict');
const hotOther = new Counter('hot_topup_other');

const ownCreated = new Counter('own_topup_created');
const ownConflict = new Counter('own_topup_conflict');
const ownOther = new Counter('own_topup_other');

const ledgerConsistent = new Rate('ledger_consistent');
const moneyLostMinor = new Counter('money_lost_minor');
const readLatency = new Trend('read_latency', true);

export const options = {
  scenarios: {
    hot_wallet: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '60s',
      exec: 'topUpHotWallet',
      startTime: '0s',
    },
    distinct_wallets: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '60s',
      exec: 'topUpOwnWallet',
      startTime: '65s',
    },
    reads: {
      executor: 'constant-vus',
      vus: 10,
      duration: '20s',
      exec: 'readWallet',
      startTime: '130s',
    },
  },
  thresholds: {
    // The only hard gate: concurrency may fail requests, but it must never lose money.
    ledger_consistent: ['rate==1'],
    money_lost_minor: ['count==0'],
    'read_latency{endpoint:wallet}': ['p(95)<500'],
    // http_req_failed is deliberately NOT gated: 5xx from write conflicts is the thing under study.
  },
};

export function setup() {
  assertIsWalletService(BASE_URL);

  const stamp = Date.now();
  const hotWalletId = openWallet(
    BASE_URL,
    createUser(BASE_URL, `hot-${stamp}@loadtest.local`),
    CURRENCY,
  );

  const ownWalletIds = [];
  for (let i = 0; i < VUS; i++) {
    ownWalletIds.push(
      openWallet(BASE_URL, createUser(BASE_URL, `own-${stamp}-${i}@loadtest.local`), CURRENCY),
    );
  }

  return { hotWalletId, ownWalletIds };
}

export function topUpHotWallet(data) {
  record(topUp(BASE_URL, data.hotWalletId, AMOUNT, CURRENCY), hotCreated, hotConflict, hotOther);
}

export function topUpOwnWallet(data) {
  const walletId = data.ownWalletIds[(__VU - 1) % data.ownWalletIds.length];
  record(topUp(BASE_URL, walletId, AMOUNT, CURRENCY), ownCreated, ownConflict, ownOther);
}

export function readWallet(data) {
  const wallet = getWallet(BASE_URL, data.hotWalletId);
  readLatency.add(wallet.timings.duration, { endpoint: 'wallet' });
  check(wallet, { 'wallet read is 200': (r) => r.status === 200 });

  const entries = getEntries(BASE_URL, data.hotWalletId, 0, 20);
  readLatency.add(entries.timings.duration, { endpoint: 'entries' });
  check(entries, { 'entries read is 200': (r) => r.status === 200 });
}

function record(res, created, conflict, other) {
  if (res.status === 201) {
    created.add(1);
  } else if (res.status >= 500) {
    conflict.add(1);
  } else {
    other.add(1);
  }
}

/**
 * The real assertion. Every entry on a wallet's account is one successful top-up of AMOUNT, so
 * balance must equal entries * AMOUNT. A shortfall means a balance update was lost while its
 * journal entry survived -- money vanished.
 */
export function teardown(data) {
  const walletIds = [data.hotWalletId, ...data.ownWalletIds];

  for (const walletId of walletIds) {
    const balance = getWallet(BASE_URL, walletId).json('balance.minorUnits');
    const entryCount = getEntries(BASE_URL, walletId, 0, 1).json('totalElements');
    const expected = entryCount * AMOUNT;

    ledgerConsistent.add(balance === expected);
    if (balance !== expected) {
      moneyLostMinor.add(expected - balance);
      console.error(
        `LEDGER MISMATCH wallet=${walletId} entries=${entryCount} expected=${expected} actual=${balance}`,
      );
    }
  }
}

export function handleSummary(data) {
  const count = (name) => (data.metrics[name] ? data.metrics[name].values.count || 0 : 0);
  const pct = (created, conflict) => {
    const total = created + conflict;
    return total === 0 ? '0.0' : ((conflict / total) * 100).toFixed(1);
  };

  const hotOk = count('hot_topup_created');
  const hotKo = count('hot_topup_conflict');
  const ownOk = count('own_topup_created');
  const ownKo = count('own_topup_conflict');
  const lost = count('money_lost_minor');

  const report = [
    '',
    '================ kessai concurrency probe ================',
    `target        ${BASE_URL}   ${VUS} VUs, ${ITERATIONS} iterations per write scenario`,
    '',
    'scenario            created   failed   failure%',
    `one shared wallet   ${String(hotOk).padStart(7)}  ${String(hotKo).padStart(7)}   ${pct(hotOk, hotKo)}%`,
    `separate wallets    ${String(ownOk).padStart(7)}  ${String(ownKo).padStart(7)}   ${pct(ownOk, ownKo)}%`,
    '',
    `money lost          ${lost} minor units  (must be 0)`,
    `read p95            wallet ${p95(data, 'read_latency')} ms`,
    '',
    'Separate wallets failing too means the contention is the shared EXTERNAL_FUNDING account,',
    'not the user wallet. That is the Week 2 problem.',
    '==========================================================',
    '',
  ].join('\n');

  return {
    stdout: report,
    'docs/benchmarks/concurrency-latest.json': JSON.stringify(data, null, 2),
  };
}

function p95(data, metric) {
  const m = data.metrics[metric];
  if (!m || !m.values) {
    return 'n/a';
  }
  const value = m.values['p(95)'];
  return value === undefined ? 'n/a' : value.toFixed(1);
}
