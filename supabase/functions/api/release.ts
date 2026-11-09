import localRelease from "./release.local.json" with { type: "json" };

// All fields are scalar. Keep the imported defaults private and immutable;
// requests never supply or merge release configuration.
const release = Object.freeze({ ...localRelease });

function errorResponse(status: number, code: string, message: string): Response {
  return Response.json(
    { error: { code, message, retryable: false }, requestId: crypto.randomUUID() },
    {
      status,
      headers: {
        "Cache-Control": "no-store",
        ...(status === 405 ? { Allow: "GET" } : {}),
      },
    },
  );
}

/** Public local metadata only. No auth, env reads, I/O, logs or network calls. */
export function handleRequest(request: Request): Response {
  const url = new URL(request.url);
  if (url.pathname !== "/v1/release" && url.pathname !== "/api/v1/release") {
    return errorResponse(404, "NOT_FOUND", "Resource not found.");
  }
  if (request.method !== "GET") {
    return errorResponse(405, "METHOD_NOT_ALLOWED", "Only GET is supported.");
  }
  // URL.search drops a bare '?'; reject that marker as well as named params.
  if (url.href.split("#", 1)[0].includes("?")) {
    return errorResponse(400, "INVALID_INPUT", "Query parameters are not supported.");
  }
  return Response.json(
    { data: release, requestId: crypto.randomUUID() },
    { headers: { "Cache-Control": "public, max-age=300" } },
  );
}
