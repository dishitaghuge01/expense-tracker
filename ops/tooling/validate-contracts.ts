import SwaggerParser from "@apidevtools/swagger-parser";
import { Ajv2020, type AnySchema, type ValidateFunction } from "ajv/2020";
import formatsModule from "ajv-formats";
import { parse } from "yaml";

const contractUrl = new URL("../../contracts/openapi.yaml", import.meta.url);
const fixtureUrl = new URL("../../fixtures/release/local.json", import.meta.url);
const metadataUrl = new URL("../../supabase/functions/api/release.local.json", import.meta.url);

type ReleaseEnvelope = {
  data: Record<string, unknown>;
  requestId: string;
};

type ReleaseDocument = {
  openapi: string;
  paths: {
    "/release": {
      get: {
        responses: Record<string, { content: { "application/json": { schema: AnySchema } } }>;
      };
    };
  };
};

/** Structurally validate OpenAPI, then compile its actual response JSON schema.
 * Exported for independent fixture/negative tests; does not execute the CLI.
 */
export async function loadReleaseValidator(): Promise<ValidateFunction<ReleaseEnvelope>> {
  // This cast only supplies the parser's input type; validate() below is the
  // runtime authority for the untrusted parsed document, not the TypeScript type.
  const parsed = parse(await Deno.readTextFile(contractUrl)) as SwaggerParser["api"];
  // Input is checked-in YAML. Disallow external file/network $ref resolution.
  const document = await SwaggerParser.validate(parsed, {
    resolve: { external: false, file: false, http: false },
  }) as unknown as ReleaseDocument;
  if (!document.openapi.startsWith("3.1.")) {
    throw new Error("An OpenAPI 3.1 contract is required.");
  }
  const ajv = new Ajv2020({ allErrors: true, strict: true, validateFormats: true });
  // Deno types this CommonJS default import as a module namespace. The pinned
  // package explicitly exports its callable plugin on module.exports.default.
  formatsModule.default(ajv);
  const responses = document.paths["/release"].get.responses;
  // Parser validation covers the OpenAPI document structure; Ajv additionally
  // validates and compiles the JSON Schema objects (including all error schemas).
  for (const response of Object.values(responses)) {
    ajv.compile(response.content["application/json"].schema);
  }
  return ajv.compile<ReleaseEnvelope>(responses["200"].content["application/json"].schema);
}

if (import.meta.main) {
  const validate = await loadReleaseValidator();
  const fixture: unknown = JSON.parse(await Deno.readTextFile(fixtureUrl));
  if (!validate(fixture)) {
    console.error("Release fixture does not match the OpenAPI response schema.");
    // Do not print payloads/validator values into CI or reports.
    Deno.exit(1);
  }
  const metadata = JSON.parse(await Deno.readTextFile(metadataUrl));
  const data = fixture.data;
  if (
    Object.keys(data).length !== Object.keys(metadata).length ||
    Object.keys(metadata).some((key) => metadata[key] !== data[key])
  ) {
    throw new Error("Synthetic fixture and function-local release metadata differ.");
  }
  console.log("OpenAPI structure, response schemas, fixture and metadata alignment validated.");
}
