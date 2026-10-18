// Offline contract checks only, not a substitute for an OpenAPI validator or API security tests.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const contract = JSON.parse(readFileSync(new URL('../docs/care-api.openapi.json', import.meta.url), 'utf8'));
assert.equal(contract.openapi, '3.1.0');
assert.ok(contract.servers.every(server => new URL(server.url).hostname.endsWith('.invalid')));
const schemas = contract.components.schemas;
const forbiddenFields = new Set(['name', 'phoneNumber', 'guardianPhone', 'message', 'messageBody', 'location',
  'latitude', 'longitude', 'rawSensorData', 'healthDiagnosis', 'actorIdFromClient', 'tenantId']);
let references = 0;
function visit(node) {
  if (!node || typeof node !== 'object') return;
  if (node.$ref) {
    assert.ok(node.$ref.startsWith('#/'), 'Only local references in this offline scaffold');
    const target = node.$ref.slice(2).split('/').reduce((value, key) => value?.[key], contract);
    assert.ok(target, `Unresolved reference: ${node.$ref}`);
    references++;
  }
  if (node.type === 'object') {
    assert.equal(node.additionalProperties, false, 'DTOs must reject extra personal-data fields');
    for (const key of Object.keys(node.properties ?? {})) assert.ok(!forbiddenFields.has(key), `Forbidden field: ${key}`);
    for (const key of node.required ?? []) assert.ok(key in node.properties, `Missing property: ${key}`);
  }
  for (const value of Object.values(node)) visit(value);
}
visit(contract);
let operations = 0;
for (const [path, methods] of Object.entries(contract.paths)) {
  for (const [method, operation] of Object.entries(methods)) {
    const security = operation.security ?? contract.security;
    assert.ok(security?.length > 0, `${method} ${path} must require authentication`);
    assert.ok(operation.responses['401']);
    if (method === 'post') {
      assert.ok(operation.parameters.some(p => p.$ref === '#/components/parameters/IdempotencyKey'));
    }
    if (method === 'get' || path.endsWith('/actions')) assert.ok(operation['x-required-roles']?.length);
    operations++;
  }
}
assert.equal(operations, 8);
assert.equal(schemas.EventBatch.properties.events.minItems, 1);
assert.equal(schemas.EventBatch.properties.events.maxItems, 50);
assert.equal(schemas.LinkRedemption.properties.userConsented.const, true);
assert.equal(schemas.LinkStatus.properties.status.const, 'PENDING_APPROVAL');
assert.deepEqual(schemas.HumanAction.then.required, ['humanConfirmed', 'contactMethod']);
assert.equal(schemas.HumanAction.properties.humanConfirmed.const, true);
assert.ok(contract.paths['/v1/incidents/{id}/actions'].post.responses['409']);
assert.ok(schemas.SmsState.enum.every(state => !schemas.CareStatus.enum.includes(state)));
assert.deepEqual(contract.paths['/v1/audit-events'].get['x-required-roles'], ['ORG_ADMIN']);
console.log(`PASS: ${operations} API operations, ${references} local references; bounded/consented, non-operational contract. No server or network requests executed.`);
