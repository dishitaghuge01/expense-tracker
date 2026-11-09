import { parse } from "yaml";
import { loadReleaseValidator } from "../ops/tooling/validate-contracts.ts";
import { handleRequest } from "../supabase/functions/api/release.ts";
import { assert, assertEquals, assertRecord, assertUuid } from "../tests/helpers/assert.ts";
import { invalidReleaseCases, type JsonObject } from "../tests/helpers/release_contract_cases.ts";

const fixtureUrl = new URL("../fixtures/release/local.json", import.meta.url);
let validatorPromise: ReturnType<typeof loadReleaseValidator> | undefined;
function validator() {
  return validatorPromise ??= loadReleaseValidator();
}

async function fixture(): Promise<JsonObject> {
  const value: unknown = JSON.parse(await Deno.readTextFile(fixtureUrl));
  assertRecord(value);
  assertRecord(value.data);
  return value;
}

function objectAt(parent: JsonObject, key: string): JsonObject {
  const value = parent[key];
  assertRecord(value);
  return value;
}

Deno.test("release contract: checked-in fixture is exact synthetic closed metadata", async () => {
  const body = await fixture();
  const validate = await validator();
  assert(validate(body), "Checked-in release fixture must validate against the actual response schema");
  assertEquals(Object.keys(body).sort(), ["data", "requestId"]);
  assertUuid(body.requestId);
  assertEquals(body.data, {
    latestBuild: 3,
    minBuild: 3,
    versionName: "0.0.3-prototype",
    distribution: "google_play",
    playStoreUrl: null,
    checkoutState: "closed",
    supportUrl: null,
  });
});

Deno.test("release contract: both handler routing prefixes conform to schema and fixture", async () => {
  const validate = await validator();
  const expected = await fixture();
  for (const path of ["/v1/release", "/api/v1/release"]) {
    const response = handleRequest(new Request(`http://127.0.0.1:54321${path}`));
    assertEquals(response.status, 200);
    const body: unknown = await response.json();
    assert(validate(body), "Handler output must validate against the actual response schema");
    assertRecord(body);
    assertEquals(body.data, expected.data);
  }
});

for (const testCase of invalidReleaseCases) {
  Deno.test(`release contract rejects: ${testCase.name}`, async () => {
    const validate = await validator();
    // Always start from an independently loaded, valid fixture; never let one
    // mutation contaminate another or let a broken baseline make negatives pass.
    const body = await fixture();
    assert(validate(body), "Negative case requires a valid baseline fixture");
    assertRecord(body.data);
    const invalid = testCase.mutate(body, body.data);
    const beforeValidation = structuredClone(invalid);
    assertEquals(validate(invalid), false, `Schema accepted ${testCase.name}`);
    assert(validate.errors !== null && validate.errors !== undefined && validate.errors.length > 0);
    assertEquals(invalid, beforeValidation, "Validation must reject, not strip/coerce/default the input");
  });
}

Deno.test("release contract: minimal public OpenAPI surface and documented error responses", async () => {
  const document: unknown = parse(await Deno.readTextFile(new URL("./openapi.yaml", import.meta.url)));
  assertRecord(document);
  assert(typeof document.openapi === "string" && document.openapi.startsWith("3.1."));
  assertEquals(document.security, []);
  assert(Array.isArray(document.servers) && document.servers.length === 1);
  assertRecord(document.servers[0]);
  assertEquals(document.servers[0].url, "/functions/v1/api/v1");
  const paths = objectAt(document, "paths");
  assertEquals(Object.keys(paths), ["/release"]);
  const releasePath = objectAt(paths, "/release");
  assertEquals(Object.keys(releasePath), ["get"]);
  const get = objectAt(releasePath, "get");
  assertEquals(get.parameters ?? [], [], "Public release accepts no parameters");
  assertEquals(get.security ?? [], [], "Public release requires no authentication");
  const responses = objectAt(get, "responses");
  for (const status of ["200", "400", "404", "405"]) {
    const response = objectAt(responses, status);
    assert(typeof response.description === "string" && response.description.length > 0);
    const content = objectAt(response, "content");
    objectAt(objectAt(content, "application/json"), "schema");
  }
  const methodResponse = objectAt(responses, "405");
  const allow = objectAt(objectAt(methodResponse, "headers"), "Allow");
  assertEquals(allow.required, true);
  assertEquals(objectAt(allow, "schema").enum, ["GET"]);
});

Deno.test("release contract: URI format is active, not an ignored schema annotation", async () => {
  const validate = await validator();
  for (const key of ["playStoreUrl", "supportUrl"]) {
    // A syntactically valid reserved-domain URI is only a schema capability
    // check, never a deployment identifier, real listing or handler override.
    const body = await fixture();
    assertRecord(body.data);
    body.data[key] = "https://synthetic.example.invalid/local-only";
    assert(validate(body), "Nullable URI fields must support valid absolute HTTPS URIs");
    body.data[key] = "https://bad host.invalid/local-only";
    assertEquals(validate(body), false, "Malformed absolute URI must fail format validation");
  }
});

Deno.test("local config source guard: private schema is not exposed in Data API", async () => {
  // Deliberately a source guard, not a claim of live PostgREST verification.
  // The inspected local config uses JSON-compatible TOML string arrays.
  const source = await Deno.readTextFile(new URL("../supabase/config.toml", import.meta.url));
  assert(/^project_id\s*=\s*"expense-tracker-local"\s*$/m.test(source));
  const api = source.split(/^\[api\][ \t]*\r?$/m)[1]?.split(/^\[/m)[0];
  assert(api !== undefined, "Local configuration must have an API section");
  const arrayText = api.match(/^schemas\s*=\s*(\[[\s\S]*?\])/m)?.[1];
  assert(arrayText !== undefined, "API schema allowlist must be explicit");
  const schemas: unknown = JSON.parse(arrayText);
  assert(Array.isArray(schemas) && schemas.every((schema) => typeof schema === "string"));
  assert(!schemas.includes("app_private"), "Private schema must not be exposed in Data API");
});
