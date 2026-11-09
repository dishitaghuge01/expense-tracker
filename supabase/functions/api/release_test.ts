import { handleRequest } from "./release.ts";
import { assert, assertEquals, assertRecord, assertUuid } from "../../../tests/helpers/assert.ts";

// Deliberately independent of implementation metadata: do not import its defaults.
const expectedData = {
  latestBuild: 3,
  minBuild: 3,
  versionName: "0.0.3-prototype",
  distribution: "google_play",
  playStoreUrl: null,
  checkoutState: "closed",
  supportUrl: null,
};
const origin = "http://127.0.0.1:54321";
const marker = "synthetic-untrusted-release-input";
const paths = ["/v1/release", "/api/v1/release"];

function responseFor(path: string, init?: RequestInit): Response {
  const response = handleRequest(new Request(`${origin}${path}`, init));
  assert(response instanceof Response, "Handler must synchronously return a Response");
  return response;
}

function assertJsonHeaders(response: Response): void {
  assert(
    /^application\/json(?:\s*;|$)/i.test(response.headers.get("content-type") ?? ""),
    "Responses must be application/json",
  );
  assert(response.headers.get("access-control-allow-credentials")?.toLowerCase() !== "true");
  assert(response.headers.get("access-control-allow-origin") !== `https://${marker}.invalid`);
}

async function assertSuccess(response: Response): Promise<Record<string, unknown>> {
  assertEquals(response.status, 200);
  assertJsonHeaders(response);
  const cache = response.headers.get("cache-control")?.split(",").map((part) => part.trim()).sort();
  assertEquals(cache, ["max-age=300", "public"]);
  const body: unknown = await response.json();
  assertRecord(body);
  assertEquals(Object.keys(body).sort(), ["data", "requestId"]);
  assertUuid(body.requestId);
  // Exact equality also excludes prices, bank/raw fields, keys and legacy APK metadata.
  assertEquals(body.data, expectedData);
  return body;
}

async function assertError(response: Response, status: number, code?: string): Promise<void> {
  assertEquals(response.status, status);
  assertJsonHeaders(response);
  const text = await response.text();
  assert(!text.includes(marker), "Error must not echo attacker-controlled input");
  assert(!JSON.stringify([...response.headers]).includes(marker), "Headers must not echo input");
  const body: unknown = JSON.parse(text);
  assertRecord(body);
  assertEquals(Object.keys(body).sort(), ["error", "requestId"]);
  assertUuid(body.requestId);
  assertRecord(body.error);
  const error = body.error;
  assertEquals(
    Object.keys(error).sort(),
    ("field" in error ? ["code", "field", "message", "retryable"] : ["code", "message", "retryable"]),
  );
  assert(typeof error.code === "string" && /^[A-Z][A-Z0-9_]+$/.test(error.code));
  if (code !== undefined) assertEquals(error.code, code);
  assert(typeof error.message === "string" && error.message.length > 0);
  if ("field" in error) assert(typeof error.field === "string");
  assertEquals(error.retryable, false);
  if (status === 405) assertEquals(response.headers.get("allow"), "GET");
}

for (const path of paths) {
  Deno.test(`release: public GET ${path} has exact closed local metadata`, async () => {
    await assertSuccess(responseFor(path));
  });

  for (const method of ["HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]) {
    Deno.test(`release: ${method} ${path} is 405, never an auth or billing route`, async () => {
      await assertError(responseFor(path, { method }), 405);
    });
  }

  for (const query of [
    "?latestBuild=999&minBuild=0&checkoutState=open",
    "?supportUrl=https%3A%2F%2Fsynthetic-untrusted-release-input.invalid",
    "?unknown=synthetic-untrusted-release-input",
    "?rawSms=synthetic-untrusted-release-input&amountPaise=49900",
    "?__proto__[checkoutState]=open",
    "?flag",
    "?=",
  ]) {
    Deno.test(`release: rejects query ${path}${query}`, async () => {
      await assertError(responseFor(`${path}${query}`), 400, "INVALID_INPUT");
      await assertSuccess(responseFor(path));
    });
  }
}

for (const path of [
  "/", "/release", "/v1", "/v1/release/", "/api/v1/release/",
  "/v1/releases", "/v1/release/extra", "/api/v2/release", "/V1/release",
  "/v1/checkout/orders", "/v1/me", "/functions/v1/api/v1/release",
  `/v1/${marker}`, "/v1/%72elease", "/v1/release%2fextra",
]) {
  Deno.test(`release: unrecognized path ${path} is 404`, async () => {
    await assertError(responseFor(path), 404, "NOT_FOUND");
  });
}

Deno.test("release: hostile headers cannot change defaults, reflect origins or authorize anything", async () => {
  const body = await assertSuccess(responseFor("/v1/release", {
    headers: {
      authorization: `Bearer ${marker}`,
      apikey: marker,
      origin: `https://${marker}.invalid`,
      "x-request-id": marker,
      "x-app-build": "0",
      "x-device-session": marker,
      "x-forwarded-host": `${marker}.invalid`,
      "x-forwarded-proto": "https",
      "x-checkout-state": "open",
      "content-type": "application/json",
      cookie: `checkoutState=open; marker=${marker}`,
    },
  }));
  assert(!JSON.stringify(body).includes(marker));
});

for (const payload of [
  JSON.stringify({ ...expectedData, latestBuild: 999, checkoutState: "open", rawSms: marker }),
  `{"__proto__":{"checkoutState":"open"},"marker":"${marker}"}`,
  `<script>${marker}</script>`,
]) {
  Deno.test(`release: rejected POST body cannot poison later GET (${payload.slice(0, 30)})`, async () => {
    await assertError(responseFor("/api/v1/release", {
      method: "POST",
      headers: { "content-type": "application/json", origin: `https://${marker}.invalid` },
      body: payload,
    }), 405);
    await assertSuccess(responseFor("/api/v1/release"));
  });
}

Deno.test("release: caller mutation of a parsed response cannot mutate server defaults", async () => {
  const first = await assertSuccess(responseFor("/v1/release"));
  assertRecord(first.data);
  first.data.checkoutState = "open";
  first.data.minBuild = 0;
  first.data.playStoreUrl = `https://${marker}.invalid`;
  await assertSuccess(responseFor("/api/v1/release"));
});

Deno.test("release: fresh server request IDs are not copied from callers or reused", async () => {
  const supplied = "11111111-1111-4111-8111-111111111111";
  const first = await assertSuccess(responseFor("/v1/release", { headers: { "x-request-id": supplied } }));
  const second = await assertSuccess(responseFor("/v1/release"));
  assert(first.requestId !== supplied);
  assert(first.requestId !== second.requestId);
});
