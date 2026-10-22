import SwaggerParser from '@apidevtools/swagger-parser';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const openApiPath = path.resolve(__dirname, '..', 'api', 'openapi.yaml');

console.log(`[check-openapi] Validating OpenAPI contract: ${openApiPath}`);

try {
  const api = await SwaggerParser.validate(openApiPath);
  console.log(`[check-openapi] PASS: OpenAPI contract is valid. Title: "${api.info.title}", Version: "${api.info.version}"`);
  console.log(`[check-openapi] Paths validated: ${Object.keys(api.paths).length} endpoint paths.`);
  process.exit(0);
} catch (err) {
  console.error(`[check-openapi] FAIL: OpenAPI contract validation failed:\n`, err.message);
  if (err.details) {
    console.error(JSON.stringify(err.details, null, 2));
  }
  process.exit(1);
}
