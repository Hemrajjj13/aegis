import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const rootDir = path.resolve(__dirname, '..');
const openApiYamlPath = path.resolve(rootDir, '../api/openapi.yaml');
const generatedSchemaPath = path.resolve(rootDir, 'src/api/schema.d.ts');
const tempSchemaPath = path.resolve(rootDir, 'src/api/schema.check.d.ts');

function checkSchema() {
  if (!fs.existsSync(openApiYamlPath)) {
    console.error(`[check:api] OpenAPI spec not found at: ${openApiYamlPath}`);
    process.exit(1);
  }

  if (!fs.existsSync(generatedSchemaPath)) {
    console.error(`[check:api] Schema file does not exist at: ${generatedSchemaPath}`);
    console.error('Run "npm run gen:api" to generate the schema types.');
    process.exit(1);
  }

  try {
    // Generate to temporary check file using the same CLI command
    execSync(`npx openapi-typescript ../api/openapi.yaml -o src/api/schema.check.d.ts`, {
      cwd: rootDir,
      stdio: 'pipe',
    });

    const existingContent = fs.readFileSync(generatedSchemaPath, 'utf-8');
    const freshContent = fs.readFileSync(tempSchemaPath, 'utf-8');

    // Clean up temp file
    if (fs.existsSync(tempSchemaPath)) {
      fs.unlinkSync(tempSchemaPath);
    }

    const normalize = (str) => str.replace(/\r\n/g, '\n').trim();

    if (normalize(existingContent) !== normalize(freshContent)) {
      console.error('[check:api] FAILED: src/api/schema.d.ts is out of date with api/openapi.yaml!');
      console.error('Run "npm run gen:api" to regenerate API types.');
      process.exit(1);
    }

    console.log('[check:api] PASSED: src/api/schema.d.ts is up to date with api/openapi.yaml.');
    process.exit(0);
  } catch (err) {
    if (fs.existsSync(tempSchemaPath)) {
      fs.unlinkSync(tempSchemaPath);
    }
    console.error('[check:api] Error executing schema check:', err);
    process.exit(1);
  }
}

checkSchema();
