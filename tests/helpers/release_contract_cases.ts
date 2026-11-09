// Synthetic mutations for the real OpenAPI response validator, not a replacement validator.
export type JsonObject = Record<string, unknown>;
export type ContractCase = {
  name: string;
  mutate: (envelope: JsonObject, data: JsonObject) => unknown;
};

export const invalidReleaseCases: ContractCase[] = [
  { name: "null envelope", mutate: () => null },
  { name: "array envelope", mutate: () => [] },
  { name: "string envelope", mutate: () => "synthetic" },
  { name: "missing data", mutate: (body) => { delete body.data; return body; } },
  { name: "missing requestId", mutate: (body) => { delete body.requestId; return body; } },
  { name: "null data", mutate: (body) => { body.data = null; return body; } },
  { name: "array data", mutate: (body) => { body.data = []; return body; } },
  ...["", "synthetic-not-a-uuid", "11111111-1111-4111-8111-11111111111Z", 3, null].map((id) => ({
    name: `invalid requestId ${JSON.stringify(id)}`,
    mutate: (body: JsonObject) => { body.requestId = id; return body; },
  })),
  ...["latestBuild", "minBuild", "versionName", "distribution", "playStoreUrl", "checkoutState", "supportUrl"].map((key) => ({
    name: `missing required data.${key}`,
    mutate: (body: JsonObject, data: JsonObject) => { delete data[key]; return body; },
  })),
  ...["latestBuild", "minBuild"].flatMap((key) =>
    [0, -1, 2, 4, 3.5, 2147483648, "3", null, true, {}, []].map((value) => ({
      name: `invalid ${key} ${JSON.stringify(value)}`,
      mutate: (body: JsonObject, data: JsonObject) => { data[key] = value; return body; },
    }))
  ),
  ...[
    ["versionName", 3], ["versionName", null], ["versionName", ""], ["versionName", "0.0.4-invented"],
    ["distribution", "website_apk"], ["distribution", "google_play "], ["distribution", null],
    ["checkoutState", "open"], ["checkoutState", "public"], ["checkoutState", "smoke"],
    ["checkoutState", true], ["checkoutState", null],
  ].map(([key, value]) => ({
    name: `invalid ${key} ${JSON.stringify(value)}`,
    mutate: (body: JsonObject, data: JsonObject) => { data[String(key)] = value; return body; },
  })),
  ...["playStoreUrl", "supportUrl"].flatMap((key) =>
    ["", "not a URI", "www.synthetic.invalid/support", "/relative/path", "https://synthetic.invalid/%GG", "https://bad host.invalid/", 3, false, {}, []].map((value) => ({
      name: `invalid ${key} ${JSON.stringify(value)}`,
      mutate: (body: JsonObject, data: JsonObject) => { data[key] = value; return body; },
    }))
  ),
  // Strict objects exclude both obsolete commercial fields and sensitive/raw data.
  ...[
    "price", "pricePaise", "amountPaise", "currency", "payment", "payments", "billing",
    "providerOrderId", "checkoutKeyId", "razorpayKeyId", "purchaseToken", "licence",
    "rawSms", "rawNotification", "statement", "accountNumber", "bankAlias", "accountSuffix",
    "sha256", "bytes", "downloadUrl", "downloadPage", "apkUrl", "releaseDate", "unexpected",
  ].flatMap((key) => [
    {
      name: `extra data.${key}`,
      mutate: (body: JsonObject, data: JsonObject) => { data[key] = "synthetic-only"; return body; },
    },
    {
      name: `extra envelope.${key}`,
      mutate: (body: JsonObject) => { body[key] = "synthetic-only"; return body; },
    },
  ]),
  {
    name: "success cannot also contain an error",
    mutate: (body) => { body.error = { code: "SYNTHETIC", message: "synthetic", retryable: false }; return body; },
  },
];
