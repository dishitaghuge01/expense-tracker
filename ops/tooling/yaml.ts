// Keep a single parser interface for tooling and independent tests. js-yaml's
// library has no environment reads; npm yaml's Node entrypoint reads debug env
// variables and would require widening Deno's read-only permission boundary.
export { load as parse } from "js-yaml";
