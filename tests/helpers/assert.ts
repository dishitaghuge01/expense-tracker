// Test-only assertions: no external packages or runtime permissions.
export function assert(condition: unknown, message = "Assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

export function assertEquals(actual: unknown, expected: unknown, message = "Values differ"): void {
  if (Object.is(actual, expected)) return;
  if (Array.isArray(actual) && Array.isArray(expected)) {
    assert(actual.length === expected.length, `${message}: array lengths differ`);
    actual.forEach((value, index) => assertEquals(value, expected[index], `${message}[${index}]`));
    return;
  }
  if (isRecord(actual) && isRecord(expected)) {
    assertEquals(Object.keys(actual).sort(), Object.keys(expected).sort(), `${message}: keys`);
    for (const key of Object.keys(expected)) {
      assertEquals(actual[key], expected[key], `${message}.${key}`);
    }
    return;
  }
  throw new Error(`${message}: ${JSON.stringify(actual)} !== ${JSON.stringify(expected)}`);
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function assertRecord(value: unknown): asserts value is Record<string, unknown> {
  assert(isRecord(value), "Expected a JSON object");
}

export function assertUuid(value: unknown): asserts value is string {
  assert(
    typeof value === "string" &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value),
    "Expected an RFC UUID request ID",
  );
}
